package io.ybr.postgresbulk.springdata.jdbc.repository;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkOperations;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
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
}
