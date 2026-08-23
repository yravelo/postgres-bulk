package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.Iterator;
import java.util.Objects;

/** Prepared, stateless coordinator for batched inserts over a caller-owned connection. */
final class PostgresBulkInserter<T> {

  private final Class<T> javaType;
  private final EntityMetadata<T> metadata;
  private final String mappedCopySql;
  private final PreparedCopyCsvRowEncoder<T> rowEncoder;
  private final CopyExecutor copyExecutor;

  private PostgresBulkInserter(EntityMetadata<T> metadata, CopyExecutor copyExecutor) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    this.copyExecutor = Objects.requireNonNull(copyExecutor, "copyExecutor must not be null");
    this.metadata = metadata;
    this.javaType = metadata.javaType();
    this.mappedCopySql = CopySqlBuilder.insert(metadata);
    this.rowEncoder = PreparedCopyCsvRowEncoder.prepare(metadata);
  }

  static <T> PostgresBulkInserter<T> prepare(EntityMetadata<T> metadata) {
    return new PostgresBulkInserter<>(metadata, new PostgresCopyExecutor());
  }

  static <T> PostgresBulkInserter<T> prepare(
      EntityMetadata<T> metadata, CopyExecutor copyExecutor) {
    return new PostgresBulkInserter<>(metadata, copyExecutor);
  }

  BulkWriteResult insert(Connection connection, Iterable<? extends T> items) {
    return insert(connection, items, BulkInsertOptions.defaults());
  }

  BulkWriteResult insert(
      Connection connection, Iterable<? extends T> items, BulkInsertOptions options) {
    validateArguments(connection, items, options);
    return insertValidated(connection, items, options, null);
  }

  BulkWriteResult insert(
      Connection connection,
      Iterable<? extends T> items,
      BulkInsertOptions options,
      TableName runtimeTarget) {
    validateArguments(connection, items, options);
    Objects.requireNonNull(runtimeTarget, "runtimeTarget must not be null");
    TableName effectiveTarget = metadata.table().resolveRuntimeTarget(runtimeTarget);
    return insertValidated(connection, items, options, effectiveTarget);
  }

  private static void validateArguments(
      Connection connection, Iterable<?> items, BulkInsertOptions options) {
    Objects.requireNonNull(connection, "connection must not be null");
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(options, "options must not be null");
  }

  private BulkWriteResult insertValidated(
      Connection connection,
      Iterable<? extends T> items,
      BulkInsertOptions options,
      TableName effectiveTarget) {
    Iterator<? extends T> iterator =
        Objects.requireNonNull(items.iterator(), "items.iterator() must not return null");
    if (!iterator.hasNext()) {
      return BulkWriteResult.empty();
    }

    String copySql =
        effectiveTarget == null ? mappedCopySql : CopySqlBuilder.insert(metadata, effectiveTarget);
    long affectedRows = 0;
    int completedBatches = 0;
    long producedRows = 0;
    T firstItem = requireItem(iterator.next(), 1);

    while (true) {
      int batchNumber = incrementBatchNumber(completedBatches);
      BatchWriter<T> batchWriter =
          new BatchWriter<>(
              firstItem, nextPosition(producedRows), iterator, options.batchSize(), rowEncoder);
      long reportedRows;
      try {
        reportedRows = copyExecutor.execute(connection, copySql, batchWriter);
      } catch (CopyExecutionException failure) {
        throw new BulkException(
            "Bulk insert for " + javaType.getName() + " failed in batch " + batchNumber, failure);
      }

      int batchRows = batchWriter.producedRows();
      verifyServerCount(batchNumber, batchRows, reportedRows);
      affectedRows = addAffectedRows(affectedRows, reportedRows);
      producedRows = addProducedRows(producedRows, batchRows);
      completedBatches = batchNumber;

      if (!iterator.hasNext()) {
        return new BulkWriteResult(affectedRows, completedBatches);
      }
      firstItem = requireItem(iterator.next(), nextPosition(producedRows));
    }
  }

  private static int incrementBatchNumber(int completedBatches) {
    try {
      return Math.incrementExact(completedBatches);
    } catch (ArithmeticException failure) {
      throw new BulkException("Bulk insert batch count overflow", failure);
    }
  }

  private static long addAffectedRows(long total, long batchRows) {
    try {
      return Math.addExact(total, batchRows);
    } catch (ArithmeticException failure) {
      throw new BulkException("Bulk insert affected row count overflow", failure);
    }
  }

  private static long addProducedRows(long total, int batchRows) {
    try {
      return Math.addExact(total, batchRows);
    } catch (ArithmeticException failure) {
      throw new BulkException("Bulk insert produced row count overflow", failure);
    }
  }

  private static long nextPosition(long producedRows) {
    try {
      return Math.incrementExact(producedRows);
    } catch (ArithmeticException failure) {
      throw new BulkException("Bulk insert item position overflow", failure);
    }
  }

  private static void verifyServerCount(int batchNumber, int producedRows, long reportedRows) {
    if (reportedRows != producedRows) {
      throw new BulkException(
          "COPY row count mismatch in batch "
              + batchNumber
              + ": produced "
              + producedRows
              + " but PostgreSQL reported "
              + reportedRows);
    }
  }

  private static <T> T requireItem(T item, long position) {
    if (item == null) {
      throw new IllegalArgumentException(
          "items must not contain null elements; null found at position " + position);
    }
    return item;
  }

  private static final class BatchWriter<T> implements CopyDataWriter {

    private final T firstItem;
    private final long firstPosition;
    private final Iterator<? extends T> iterator;
    private final int batchSize;
    private final PreparedCopyCsvRowEncoder<T> rowEncoder;
    private int producedRows;

    private BatchWriter(
        T firstItem,
        long firstPosition,
        Iterator<? extends T> iterator,
        int batchSize,
        PreparedCopyCsvRowEncoder<T> rowEncoder) {
      this.firstItem = firstItem;
      this.firstPosition = firstPosition;
      this.iterator = iterator;
      this.batchSize = batchSize;
      this.rowEncoder = rowEncoder;
    }

    @Override
    public void writeTo(Writer destination) throws IOException {
      rowEncoder.writeRow(firstItem, destination);
      producedRows = 1;
      while (producedRows < batchSize && iterator.hasNext()) {
        long position = itemPosition(firstPosition, producedRows);
        T item = requireItem(iterator.next(), position);
        rowEncoder.writeRow(item, destination);
        producedRows++;
      }
    }

    private int producedRows() {
      return producedRows;
    }
  }

  private static long itemPosition(long firstPosition, int offset) {
    try {
      return Math.addExact(firstPosition, offset);
    } catch (ArithmeticException failure) {
      throw new BulkException("Bulk insert item position overflow", failure);
    }
  }
}
