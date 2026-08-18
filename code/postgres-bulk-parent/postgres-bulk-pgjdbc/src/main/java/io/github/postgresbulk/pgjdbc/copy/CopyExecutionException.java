package io.github.postgresbulk.pgjdbc.copy;

import io.github.postgresbulk.core.BulkException;

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
