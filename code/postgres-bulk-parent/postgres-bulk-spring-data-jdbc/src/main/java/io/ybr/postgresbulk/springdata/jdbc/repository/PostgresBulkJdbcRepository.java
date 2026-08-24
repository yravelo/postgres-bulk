package io.ybr.postgresbulk.springdata.jdbc.repository;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkOperations;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in Spring Data JDBC repository fragment for PostgreSQL bulk operations.
 *
 * <p>The fragment writes and reads only the aggregate root table. It does not persist child
 * entities, invoke Spring Data callbacks, auditing, or domain events, update version properties, or
 * synchronize database-generated identifiers with input objects. Methods use Spring {@code
 * REQUIRED}, read-write transaction semantics and join an existing transaction when one is present.
 * {@code NESTED} is supported only when the enclosing boundary is owned by a {@code
 * JdbcTransactionManager} or {@code DataSourceTransactionManager} for the same data source; the
 * manager, not this fragment, owns the savepoint. Applications with multiple JDBC operations, data
 * sources, or transaction managers must select them explicitly. The fragment never retries a failed
 * operation.
 *
 * @param <T> repository aggregate-root type
 */
public interface PostgresBulkJdbcRepository<T> extends BulkOperations<T> {

  /**
   * Inserts aggregate-root rows through PostgreSQL COPY with the default batching policy.
   *
   * <p>Input is consumed once and need not be a collection. Empty input is a no-op. Assigned
   * identifiers are copied; database-generated identifiers are omitted and are not populated on
   * input objects. A single call must not mix assigned and generated identifiers.
   *
   * @param items aggregate roots consumed sequentially
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if {@code items} is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the current transaction
   *     is read-only or the mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Override
  @Transactional
  default BulkWriteResult bulkInsert(Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults());
  }

  /**
   * Inserts aggregate-root rows through PostgreSQL COPY with an explicit batching policy.
   *
   * <p>Lifecycle, transaction, identifier, and empty-input semantics are the same as {@link
   * #bulkInsert(Iterable)}. A batch is a COPY execution boundary, not a transaction boundary.
   *
   * @param items aggregate roots consumed sequentially
   * @param options validated logical COPY batching policy
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the current transaction
   *     is read-only or the mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Override
  @Transactional
  BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options);

  /**
   * Inserts aggregate-root rows into one explicit operation-scoped target.
   *
   * <p>The target must be schema-qualified, retain the mapped table name, and agree with an
   * explicitly mapped schema. It is supplied by the application for this call only: the fragment
   * does not resolve tenants, retain target state, mutate Spring Data JDBC metadata, or change
   * connection schema/search-path state. The target applies only to the aggregate root table; child
   * collections and graphs are not redirected.
   *
   * <p>This target-first signature deliberately preserves source compatibility for historical calls
   * such as {@code bulkInsert(items, null)}, which continue to select the options overload.
   * Assigned and generated identifier policies remain unchanged, including the rejection of mixed
   * policies. The operation uses {@code REQUIRED} transaction semantics, rejects an outer read-only
   * transaction, and otherwise has the lifecycle, empty-input, and thread-safety semantics of
   * {@link #bulkInsert(Iterable)}.
   *
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @param items aggregate roots consumed sequentially
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the implementation is
   *     called without transaction infrastructure, the current transaction is read-only, or the
   *     mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Transactional
  default BulkWriteResult bulkInsert(TableName runtimeTarget, Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults(), runtimeTarget);
  }

  /**
   * Inserts aggregate-root rows with explicit batching into one operation-scoped target.
   *
   * <p>The target contract, root-only behavior, identifier policies, transaction requirements,
   * absence of tenant resolution, and thread-safety guarantees are identical to {@link
   * #bulkInsert(TableName, Iterable)}. A batch remains a COPY execution boundary, not a transaction
   * boundary.
   *
   * @param items aggregate roots consumed sequentially
   * @param options validated logical COPY batching policy
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the implementation is
   *     called without transaction infrastructure, the current transaction is read-only, or the
   *     mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Transactional
  BulkWriteResult bulkInsert(
      Iterable<? extends T> items, BulkInsertOptions options, TableName runtimeTarget);

  /**
   * Finds aggregate roots matching simple or composite keys through a temporary-table join.
   *
   * <p>Duplicate input keys do not duplicate results, missing keys are omitted, null keys or key
   * components are rejected, and result order is unspecified. The complete result is materialized
   * before the temporary table is removed. Empty input is a no-op.
   *
   * @param keys simple or composite key values consumed sequentially
   * @param keyMetadata exact ordered physical columns and accessors for each key
   * @param <K> logical key type
   * @return matching aggregate roots, possibly empty and with no guaranteed order
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if a key or key component is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the current transaction
   *     is read-only or the mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if temporary-table, COPY, query, or cleanup work
   *     cannot complete
   */
  @Transactional
  <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata);

  /**
   * Finds aggregate roots matching bulk keys in one explicit operation-scoped target.
   *
   * <p>The target must be schema-qualified, retain the mapped table name, and agree with an
   * explicitly mapped schema. It is propagated to the existing pgJDBC temporary-table lookup and
   * materialized with the configured Spring Data JDBC {@code EntityRowMapper} while the temporary
   * table and same transaction-bound physical connection remain active. No tenant is resolved and
   * no target state is retained, so one repository proxy may safely serve concurrent calls with
   * different targets.
   *
   * <p>The target applies only to the aggregate root table. Child collections and graphs are not
   * redirected. Non-empty input requires a write-capable transaction; duplicate, missing-key,
   * empty-input, ordering, identifier, and exception semantics match {@link
   * #findAllByBulkKey(Iterable, BulkKeyMetadata)}.
   *
   * @param keys simple or composite key values consumed sequentially
   * @param keyMetadata exact ordered physical columns and accessors for each key
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @param <K> logical key type
   * @return matching aggregate roots, possibly empty and with no guaranteed order
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     a key or key component is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the implementation is
   *     called without transaction infrastructure, the current transaction is read-only, or the
   *     mapping is outside the supported root-only contract
   * @throws io.ybr.postgresbulk.core.BulkException if temporary-table, COPY, query, or cleanup work
   *     cannot complete
   */
  @Transactional
  <K> List<T> findAllByBulkKey(
      Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget);
}
