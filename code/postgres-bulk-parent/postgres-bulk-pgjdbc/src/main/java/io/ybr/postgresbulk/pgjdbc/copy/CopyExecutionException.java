package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.BulkException;

/** Internal failure to start, stream, or finish a pgJDBC COPY operation. */
final class CopyExecutionException extends BulkException {

  private static final long serialVersionUID = 1L;

  CopyExecutionException(String message) {
    super(message);
  }

  CopyExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
