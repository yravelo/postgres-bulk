package io.github.postgresbulk.springdata.repository;

import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkOperations;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in Spring Data repository fragment for PostgreSQL bulk operations. */
public interface PostgresBulkRepository<T, ID> extends BulkOperations<T> {

  /** Inserts values through PostgreSQL COPY with the default batching policy. */
  @Override
  @Transactional
  default BulkWriteResult bulkInsert(Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults());
  }

  /** Inserts values through PostgreSQL COPY with an explicit batching policy. */
  @Override
  @Transactional
  BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options);

  /**
   * Finds all target rows matching simple or composite keys using a temporary-table join.
   *
   * <p>Duplicate input keys are deduplicated, missing keys are omitted, null keys/components are
   * rejected, and result ordering is unspecified.
   */
  @Transactional
  <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata);
}
