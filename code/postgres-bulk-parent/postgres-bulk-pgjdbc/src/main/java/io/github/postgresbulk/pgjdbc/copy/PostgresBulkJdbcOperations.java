package io.github.postgresbulk.pgjdbc.copy;

import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Prepared pgJDBC bulk engine operating on a caller-owned JDBC connection.
 *
 * <p>This type never acquires, closes, commits, rolls back, or changes the supplied connection.
 * Callers own the complete connection and transaction scope.
 */
public final class PostgresBulkJdbcOperations<T> {

  private final EntityMetadata<T> metadata;
  private final PostgresBulkInserter<T> inserter;

  private PostgresBulkJdbcOperations(EntityMetadata<T> metadata) {
    this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    this.inserter = PostgresBulkInserter.prepare(metadata);
  }

  /** Creates a prepared engine for one entity mapping. */
  public static <T> PostgresBulkJdbcOperations<T> prepare(EntityMetadata<T> metadata) {
    return new PostgresBulkJdbcOperations<>(metadata);
  }

  /** Executes a bulk insert with default batching on the supplied connection. */
  public BulkWriteResult bulkInsert(Connection connection, Iterable<? extends T> items) {
    return inserter.insert(connection, items);
  }

  /** Executes a bulk insert with explicit batching on the supplied connection. */
  public BulkWriteResult bulkInsert(
      Connection connection, Iterable<? extends T> items, BulkInsertOptions options) {
    return inserter.insert(connection, items, options);
  }

  /**
   * Looks up mapped entities through a temporary key table while it remains connection-local.
   *
   * @param connection caller-owned connection with {@code autoCommit=false}
   * @param keys simple or composite key values
   * @param keyMetadata ordered physical key mapping
   * @param emptyResult result returned without JDBC work when {@code keys} is empty
   * @param query result materializer invoked before the temporary table is dropped
   */
  public <K, R> R findAllByBulkKey(
      Connection connection,
      Iterable<? extends K> keys,
      BulkKeyMetadata<K> keyMetadata,
      R emptyResult,
      LookupResultMapper<R> query) {
    Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    Objects.requireNonNull(query, "query must not be null");
    return TemporaryTableBulkLookup.prepare(metadata.table(), keyMetadata)
        .lookup(connection, keys, emptyResult, query::map);
  }

  /** Materializes a lookup result while the temporary relation is visible. */
  @FunctionalInterface
  public interface LookupResultMapper<R> {

    /** Maps the generated select while using the same physical connection scope. */
    R map(Connection connection, String selectSql, long copiedKeys) throws SQLException;
  }
}
