package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.BulkException;

/** Internal failure to resolve or apply the deterministic COPY encoding contract. */
final class BulkEncodingException extends BulkException {

  private static final long serialVersionUID = 1L;

  BulkEncodingException(String message) {
    super(message);
  }
}
