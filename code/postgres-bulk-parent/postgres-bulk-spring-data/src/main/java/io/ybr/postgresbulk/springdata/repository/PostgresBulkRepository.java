package io.ybr.postgresbulk.springdata.repository;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkOperations;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
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
}
