package io.ybr.postgresbulk.core;

/**
 * Counts produced by a successfully completed bulk write.
 *
 * <p>The value contains only deterministic operation facts. It deliberately excludes elapsed time,
 * generated identifiers, and ORM lifecycle information. Instances are immutable, thread-safe, and
 * have value semantics.
 *
 * @param affectedRows number of rows reported as written by the underlying operation
 * @param batches number of non-empty COPY executions completed; a batch is not a transaction
 */
public record BulkWriteResult(long affectedRows, int batches) {

  /**
   * Creates a result and validates that its counts can describe completed non-empty batches.
   *
   * @param affectedRows number of rows reported as written by the underlying operation
   * @param batches number of non-empty COPY executions completed; a batch is not a transaction
   * @throws IllegalArgumentException if either count is negative, only one count is zero, or the
   *     number of batches exceeds the number of affected rows
   */
  public BulkWriteResult {
    if (affectedRows < 0) {
      throw new IllegalArgumentException("affectedRows must not be negative: " + affectedRows);
    }
    if (batches < 0) {
      throw new IllegalArgumentException("batches must not be negative: " + batches);
    }
    if ((affectedRows == 0) != (batches == 0)) {
      throw new IllegalArgumentException(
          "affectedRows and batches must either both be zero or both be positive");
    }
    if (batches > affectedRows) {
      throw new IllegalArgumentException(
          "batches must not exceed affectedRows: " + batches + " > " + affectedRows);
    }
  }

  /**
   * Returns the canonical result for an empty-input no-op.
   *
   * @return a result with zero affected rows and zero batches
   */
  public static BulkWriteResult empty() {
    return new BulkWriteResult(0, 0);
  }
}
