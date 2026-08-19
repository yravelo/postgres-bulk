package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

/** Prepared coordinator for temporary-table bulk lookup on a caller-owned connection. */
final class TemporaryTableBulkLookup<K> {

  private final BulkLookupSql sql;
  private final PreparedCopyCsvRowEncoder<K> keyEncoder;
  private final CopyExecutor copyExecutor;
  private final Supplier<String> temporaryTableNames;

  private TemporaryTableBulkLookup(
      TableName targetTable,
      BulkKeyMetadata<K> keyMetadata,
      CopyExecutor copyExecutor,
      Supplier<String> temporaryTableNames) {
    Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    this.copyExecutor = Objects.requireNonNull(copyExecutor, "copyExecutor must not be null");
    this.temporaryTableNames =
        Objects.requireNonNull(temporaryTableNames, "temporaryTableNames must not be null");
    this.sql = BulkLookupSql.prepare(targetTable, keyMetadata);
    this.keyEncoder = PreparedCopyCsvRowEncoder.prepare(keyMetadata);
  }

  static <K> TemporaryTableBulkLookup<K> prepare(
      TableName targetTable, BulkKeyMetadata<K> keyMetadata) {
    return new TemporaryTableBulkLookup<>(
        targetTable,
        keyMetadata,
        new PostgresCopyExecutor(),
        TemporaryTableNameGenerator::randomName);
  }

  static <K> TemporaryTableBulkLookup<K> prepare(
      TableName targetTable,
      BulkKeyMetadata<K> keyMetadata,
      CopyExecutor copyExecutor,
      Supplier<String> temporaryTableNames) {
    return new TemporaryTableBulkLookup<>(
        targetTable, keyMetadata, copyExecutor, temporaryTableNames);
  }

  <R> R lookup(
      Connection connection, Iterable<? extends K> keys, R emptyResult, LookupQuery<R> query) {
    Objects.requireNonNull(connection, "connection must not be null");
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(emptyResult, "emptyResult must not be null");
    Objects.requireNonNull(query, "query must not be null");

    Iterator<? extends K> iterator =
        Objects.requireNonNull(keys.iterator(), "keys.iterator() must not return null");
    if (!iterator.hasNext()) {
      return emptyResult;
    }

    K firstKey = requireKey(iterator.next(), 1);
    requireManualTransaction(connection);
    String temporaryTable = TemporaryTableNameGenerator.next(temporaryTableNames);
    boolean temporaryTableCreated = false;
    Throwable operationFailure = null;
    try {
      executeStatement(
          connection, sql.createTemporaryTable(temporaryTable), "create temporary key table");
      temporaryTableCreated = true;

      KeyWriter keyWriter = new KeyWriter(firstKey, iterator, keyEncoder);
      long copiedKeys;
      try {
        copiedKeys = copyExecutor.execute(connection, sql.copyKeys(temporaryTable), keyWriter);
      } catch (CopyExecutionException failure) {
        throw lookupFailure("copy keys", failure);
      }
      verifyCopyCount(keyWriter.producedKeys(), copiedKeys);

      try {
        return query.execute(connection, sql.selectMatches(temporaryTable), copiedKeys);
      } catch (SQLException failure) {
        throw lookupFailure("consume lookup result", failure);
      }
    } catch (RuntimeException | Error failure) {
      operationFailure = failure;
      throw failure;
    } finally {
      if (temporaryTableCreated) {
        cleanup(connection, sql.dropTemporaryTable(temporaryTable), operationFailure);
      }
    }
  }

  private static void requireManualTransaction(Connection connection) {
    try {
      if (connection.getAutoCommit()) {
        throw new IllegalStateException(
            "temporary-table bulk lookup requires autoCommit=false on the caller-owned connection");
      }
    } catch (SQLException failure) {
      throw lookupFailure("validate transaction state", failure);
    }
  }

  private static void executeStatement(Connection connection, String statementSql, String stage) {
    try (Statement statement = connection.createStatement()) {
      statement.execute(statementSql);
    } catch (SQLException failure) {
      throw lookupFailure(stage, failure);
    }
  }

  private static void cleanup(Connection connection, String dropSql, Throwable operationFailure) {
    try (Statement statement = connection.createStatement()) {
      statement.execute(dropSql);
    } catch (SQLException cleanupFailure) {
      if (operationFailure != null) {
        operationFailure.addSuppressed(cleanupFailure);
      } else {
        throw lookupFailure("drop temporary key table", cleanupFailure);
      }
    }
  }

  private static void verifyCopyCount(long producedKeys, long copiedKeys) {
    if (copiedKeys != producedKeys) {
      throw new BulkException(
          "COPY key count mismatch: produced "
              + producedKeys
              + " but PostgreSQL reported "
              + copiedKeys);
    }
  }

  private static <K> K requireKey(K key, long position) {
    if (key == null) {
      throw new IllegalArgumentException(
          "keys must not contain null elements; null found at position " + position);
    }
    return key;
  }

  private static BulkException lookupFailure(String stage, Throwable cause) {
    return new BulkException(
        "Temporary-table bulk lookup failed while attempting to " + stage, cause);
  }

  private final class KeyWriter implements CopyDataWriter {

    private final K firstKey;
    private final Iterator<? extends K> iterator;
    private final PreparedCopyCsvRowEncoder<K> encoder;
    private long producedKeys;

    private KeyWriter(
        K firstKey, Iterator<? extends K> iterator, PreparedCopyCsvRowEncoder<K> encoder) {
      this.firstKey = firstKey;
      this.iterator = iterator;
      this.encoder = encoder;
    }

    @Override
    public void writeTo(Writer destination) throws IOException {
      writeKey(firstKey, destination);
      while (iterator.hasNext()) {
        long position = nextPosition(producedKeys);
        K key = requireKey(iterator.next(), position);
        encoder.writeRowRejectingNulls(key, destination, position);
        producedKeys = incrementCount(producedKeys);
      }
    }

    private void writeKey(K key, Writer destination) throws IOException {
      long position = nextPosition(producedKeys);
      encoder.writeRowRejectingNulls(key, destination, position);
      producedKeys = incrementCount(producedKeys);
    }

    private long incrementCount(long completedKeys) {
      try {
        return Math.incrementExact(completedKeys);
      } catch (ArithmeticException failure) {
        throw new BulkException("Bulk lookup key count overflow", failure);
      }
    }

    private long producedKeys() {
      return producedKeys;
    }

    private long nextPosition(long completedKeys) {
      try {
        return Math.incrementExact(completedKeys);
      } catch (ArithmeticException failure) {
        throw new BulkException("Bulk lookup key position overflow", failure);
      }
    }
  }
}
