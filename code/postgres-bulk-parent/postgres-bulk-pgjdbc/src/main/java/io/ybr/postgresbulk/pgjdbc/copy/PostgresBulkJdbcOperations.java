package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Prepared pgJDBC bulk engine operating on a caller-owned JDBC connection.
 *
 * <p>This type never acquires, closes, commits, rolls back, or changes the supplied connection.
 * Callers own the complete connection and transaction scope. Prepared instances have no mutable
 * per-invocation state and may be shared when metadata accessors are thread-safe and each call uses
 * its own valid connection scope.
 *
 * @param <T> logical row type described by the prepared entity metadata
 */
public final class PostgresBulkJdbcOperations<T> {

  private final EntityMetadata<T> metadata;
  private final PostgresBulkInserter<T> inserter;

  private PostgresBulkJdbcOperations(EntityMetadata<T> metadata) {
    this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    this.inserter = PostgresBulkInserter.prepare(metadata);
  }

  /**
   * Creates a prepared engine for one entity mapping.
   *
   * @param metadata final ordered physical mapping for {@code T}
   * @param <T> logical row type
   * @return a reusable prepared engine
   * @throws NullPointerException if {@code metadata} is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if a relational Java type has no supported COPY
   *     encoder
   */
  public static <T> PostgresBulkJdbcOperations<T> prepare(EntityMetadata<T> metadata) {
    return new PostgresBulkJdbcOperations<>(metadata);
  }

  /**
   * Executes a direct PostgreSQL COPY insert with the default batching policy.
   *
   * <p>The operation is not an ORM persist/save operation: it does not run lifecycle callbacks,
   * synchronize generated identifiers, or make values managed. Empty input returns {@link
   * BulkWriteResult#empty()} without opening COPY. With autocommit disabled, the caller decides
   * commit or rollback; with autocommit enabled, earlier COPY batches may remain committed after a
   * later failure.
   *
   * @param connection open caller-owned pgJDBC connection
   * @param items rows consumed sequentially, possibly from a one-shot iterable
   * @return server row count and completed COPY batch count
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  public BulkWriteResult bulkInsert(Connection connection, Iterable<? extends T> items) {
    return inserter.insert(connection, items);
  }

  /**
   * Executes a direct PostgreSQL COPY insert with an explicit batching policy.
   *
   * <p>Transaction, lifecycle, empty-input, null-input, and failure semantics are identical to
   * {@link #bulkInsert(Connection, Iterable)}. One completed batch represents one COPY execution,
   * not one transaction.
   *
   * @param connection open caller-owned pgJDBC connection
   * @param items rows consumed sequentially, possibly from a one-shot iterable
   * @param options validated logical COPY batch policy
   * @return server row count and completed COPY batch count
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
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
   * @param <K> logical simple or composite key type
   * @param <R> fully materialized result type
   * @return {@code emptyResult} for empty input, otherwise the callback result
   * @throws NullPointerException if connection, keys, key metadata, or mapper is {@code null}
   * @throws IllegalArgumentException if a key or key component is {@code null}
   * @throws IllegalStateException if autocommit is enabled for non-empty input
   * @throws io.ybr.postgresbulk.core.BulkException if temporary-table creation, COPY, query, or
   *     cleanup fails
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

  /**
   * Materializes a lookup result while the temporary relation is visible.
   *
   * @param <R> fully materialized result type
   */
  @FunctionalInterface
  public interface LookupResultMapper<R> {

    /**
     * Maps the generated select while using the same physical connection scope.
     *
     * <p>The mapper must consume and close all JDBC resources before returning. The SQL contains
     * quoted identifiers generated by the library; key values are already stored in the temporary
     * relation. The mapper must not retain the connection or SQL for later execution.
     *
     * @param connection the same caller-owned connection used to create and load the temporary
     *     table
     * @param selectSql generated SELECT/JOIN SQL valid only during this callback
     * @param copiedKeys number of key rows copied, including duplicate input keys
     * @return a result detached from JDBC resources
     * @throws SQLException if result execution or materialization fails
     */
    R map(Connection connection, String selectSql, long copiedKeys) throws SQLException;
  }
}
