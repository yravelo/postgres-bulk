package io.github.postgresbulk.core;

import java.util.Objects;

/**
 * Bulk write capabilities bound to one logical value type.
 *
 * <p>Implementations consume input sequentially and must not require a known size or a reusable
 * iterable. The contract does not imply ORM lifecycle callbacks, managed entity state, generated
 * identifier retrieval, streaming, or parallel consumption.
 *
 * <p>An implementation may be shared between threads only when that implementation documents that
 * it is thread-safe. The interface itself does not hold mutable state.
 *
 * @param <T> logical type accepted by this operation instance
 */
public interface BulkOperations<T> {

  /**
   * Inserts all supplied values using {@link BulkInsertOptions#defaults()}.
   *
   * <p>An empty iterable is a successful no-op and returns {@link BulkWriteResult#empty()}. The
   * iterable must not be {@code null} and must not produce {@code null} elements.
   *
   * @param items values to insert; may be a one-shot iterable
   * @return counts for the completed operation
   * @throws NullPointerException if {@code items} is {@code null}
   * @throws IllegalArgumentException if a produced element is {@code null}
   * @throws BulkException if the operation cannot be completed
   */
  default BulkWriteResult bulkInsert(Iterable<? extends T> items) {
    Objects.requireNonNull(items, "items must not be null");
    return bulkInsert(items, BulkInsertOptions.defaults());
  }

  /**
   * Inserts all supplied values using the specified batching policy.
   *
   * <p>An empty iterable is a successful no-op: implementations must return {@link
   * BulkWriteResult#empty()} without starting a batch or invoking infrastructure. Input is consumed
   * sequentially and may be consumed only once. Implementations must not assume a known size,
   * random access, or the ability to obtain a second iterator.
   *
   * <p>Successful completion reports every inserted row. This operation does not promise generated
   * identifiers, ORM callbacks, persistence-context synchronization, or managed state. Failure and
   * atomicity across batches depend on the transaction boundary supplied by the adapter; a failed
   * operation returns no partial result.
   *
   * @param items values to insert; may be a one-shot iterable
   * @param options mechanism-independent insertion options
   * @return counts for the completed operation
   * @throws NullPointerException if {@code items} or {@code options} is {@code null}
   * @throws IllegalArgumentException if a produced element is {@code null}
   * @throws BulkException if the operation cannot be completed
   */
  BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options);
}
