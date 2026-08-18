package io.github.postgresbulk.core;

import java.util.Objects;

/**
 * Immutable, mechanism-independent options for a bulk insert.
 *
 * <p>The batch size bounds how many values belong to one logical execution batch. Driver buffers,
 * transfer formats, SQL, and temporary-table settings are deliberately not part of this type.
 * Instances are thread-safe and have value semantics.
 */
public final class BulkInsertOptions {

  private static final int DEFAULT_BATCH_SIZE = 1_000;
  private static final BulkInsertOptions DEFAULTS = new BulkInsertOptions(DEFAULT_BATCH_SIZE);

  private final int batchSize;

  private BulkInsertOptions(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be greater than zero: " + batchSize);
    }
    this.batchSize = batchSize;
  }

  /**
   * Returns the default batching policy, currently 1,000 values per batch.
   *
   * @return immutable default options
   */
  public static BulkInsertOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Creates options with an explicit logical batch size.
   *
   * @param batchSize maximum number of input values in one batch
   * @return immutable options containing the requested batch size
   * @throws IllegalArgumentException if {@code batchSize} is not greater than zero
   */
  public static BulkInsertOptions ofBatchSize(int batchSize) {
    return new BulkInsertOptions(batchSize);
  }

  /**
   * Returns the maximum number of input values in one logical batch.
   *
   * @return a strictly positive batch size
   */
  public int batchSize() {
    return batchSize;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof BulkInsertOptions that && batchSize == that.batchSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(batchSize);
  }

  @Override
  public String toString() {
    return "BulkInsertOptions[batchSize=" + batchSize + ']';
  }
}
