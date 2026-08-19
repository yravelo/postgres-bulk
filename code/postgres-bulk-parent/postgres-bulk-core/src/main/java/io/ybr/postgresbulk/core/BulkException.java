package io.ybr.postgresbulk.core;

/**
 * Unchecked root exception for failures while performing a bulk operation.
 *
 * <p>Invalid caller arguments use the standard argument/null exceptions documented by each API.
 * Adapter-specific subclasses should be introduced only when their failure category has concrete,
 * tested behavior. Causes should be retained so infrastructure diagnostics are not lost.
 */
public class BulkException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a bulk failure with a descriptive message.
   *
   * @param message description of the failed operation
   */
  public BulkException(String message) {
    super(message);
  }

  /**
   * Creates a bulk failure while retaining its originating cause.
   *
   * @param message description of the failed operation
   * @param cause originating failure
   */
  public BulkException(String message, Throwable cause) {
    super(message, cause);
  }
}
