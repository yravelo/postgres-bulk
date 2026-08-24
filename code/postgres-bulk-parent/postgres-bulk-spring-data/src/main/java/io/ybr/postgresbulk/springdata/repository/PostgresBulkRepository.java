package io.ybr.postgresbulk.springdata.repository;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkOperations;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in Spring Data repository fragment for PostgreSQL bulk operations.
 *
 * <p>Methods use Spring {@code REQUIRED} transaction semantics. A repository proxy creates a
 * read-write transaction when none exists and joins an existing one otherwise. An outer read-only
 * transaction is rejected. COPY bypasses ORM persist callbacks, generated-identifier
 * synchronization, dirty checking, and automatic persistence-context clearing.
 *
 * @param <T> repository domain type
 * @param <ID> repository identifier type
 */
public interface PostgresBulkRepository<T, ID> extends BulkOperations<T> {

  /**
   * Inserts values through PostgreSQL COPY with the default batching policy.
   *
   * <p>Empty input is a no-op. The returned values are row/COPY-batch counts, not managed entities.
   * Assigned identifiers are supported; generated identifiers are neither returned nor populated.
   * Existing managed state may be stale because the fragment does not flush or clear the
   * persistence context.
   *
   * @param items entities or values consumed sequentially
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if {@code items} is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if the operation cannot complete
   */
  @Override
  @Transactional
  default BulkWriteResult bulkInsert(Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults());
  }

  /**
   * Inserts values through PostgreSQL COPY with an explicit batching policy.
   *
   * <p>Lifecycle, transaction, empty-input, generated-identifier, and persistence-context semantics
   * are the same as {@link #bulkInsert(Iterable)}. A batch is a COPY execution boundary, not a
   * transaction boundary.
   *
   * @param items entities or values consumed sequentially
   * @param options validated logical COPY batch policy
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if an input element is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if the operation cannot complete
   */
  @Override
  @Transactional
  BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options);

  /**
   * Inserts values through PostgreSQL COPY into one explicit operation-scoped target.
   *
   * <p>The target must be schema-qualified, retain the mapped table name, and agree with an
   * explicitly mapped schema. It is supplied by the application for this call only: the fragment
   * does not resolve tenants, retain target state, mutate Hibernate metadata, or change connection
   * schema/search-path state. The target is the root entity table only; associations and secondary
   * tables are not redirected.
   *
   * <p>This target-first signature deliberately preserves source compatibility for historical calls
   * such as {@code bulkInsert(items, null)}, which continue to select the options overload.
   * Generated identifiers remain omitted and are not copied back. The operation uses {@code
   * REQUIRED} transaction semantics, rejects an outer read-only transaction, and otherwise has the
   * lifecycle, empty-input, persistence-context, and thread-safety semantics of {@link
   * #bulkInsert(Iterable)}.
   *
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @param items entities or values consumed sequentially
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the implementation is
   *     called without repository transaction infrastructure or the current transaction is
   *     read-only
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Transactional
  default BulkWriteResult bulkInsert(TableName runtimeTarget, Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults(), runtimeTarget);
  }

  /**
   * Inserts values through PostgreSQL COPY with explicit batching and an operation-scoped target.
   *
   * <p>The target contract, root-only behavior, generated-identifier behavior, transaction
   * requirement, persistence-context limitations, absence of tenant resolution, and thread-safety
   * guarantees are identical to {@link #bulkInsert(TableName, Iterable)}. A batch remains a COPY
   * execution boundary rather than a transaction boundary.
   *
   * @param items entities or values consumed sequentially
   * @param options validated logical COPY batch policy
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @return affected rows and completed COPY batches
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     an input element is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if the implementation is
   *     called without repository transaction infrastructure or the current transaction is
   *     read-only
   * @throws io.ybr.postgresbulk.core.BulkException if COPY cannot complete
   */
  @Transactional
  BulkWriteResult bulkInsert(
      Iterable<? extends T> items, BulkInsertOptions options, TableName runtimeTarget);

  /**
   * Finds all target rows matching simple or composite keys using a temporary-table join.
   *
   * <p>Duplicate input keys are deduplicated, missing keys are omitted, null keys/components are
   * rejected, and result ordering is unspecified. Non-empty input requires a write-capable
   * transaction because the implementation creates and loads a temporary table. Results are
   * materialized as JPA entities before that table is removed; pending persistence-context changes
   * are not flushed automatically.
   *
   * @param keys simple or composite key values consumed sequentially
   * @param keyMetadata exact ordered physical columns and accessors for each key
   * @param <K> logical key type
   * @return materialized matching entities, possibly empty and with no guaranteed order
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if a key or component is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if no transaction is active
   *     at the implementation boundary or the current transaction is read-only
   * @throws io.ybr.postgresbulk.core.BulkException if temporary-table, COPY, query, or cleanup work
   *     fails
   */
  @Transactional
  <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata);

  /**
   * Finds rows matching bulk keys in one explicit operation-scoped target root table.
   *
   * <p>The target must be schema-qualified, retain the mapped table name, and agree with an
   * explicitly mapped schema. It is propagated to the existing pgJDBC temporary-table lookup; JPA
   * materializes the native SELECT produced for that target while the temporary table and same
   * transaction-bound physical connection remain active. The fragment performs no tenant resolution
   * and retains no target state, so one repository proxy may safely serve concurrent calls with
   * different targets.
   *
   * <p>The target applies only to the root entity table. Association tables and secondary-table
   * mappings are not redirected. Non-empty input requires a write-capable transaction; duplicate,
   * missing-key, empty-input, persistence-context, ordering, and exception semantics match {@link
   * #findAllByBulkKey(Iterable, BulkKeyMetadata)}.
   *
   * @param keys simple or composite key values consumed sequentially
   * @param keyMetadata exact ordered physical columns and accessors for each key
   * @param runtimeTarget complete schema-qualified root-table target for this invocation
   * @param <K> logical key type
   * @return materialized matching entities, possibly empty and with no guaranteed order
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalArgumentException if the target is unqualified or conflicts with the mapping, or
   *     a key or component is {@code null}
   * @throws org.springframework.dao.InvalidDataAccessApiUsageException if no transaction is active
   *     at the implementation boundary or the current transaction is read-only
   * @throws io.ybr.postgresbulk.core.BulkException if temporary-table, COPY, query, or cleanup work
   *     fails
   */
  @Transactional
  <K> List<T> findAllByBulkKey(
      Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget);
}
