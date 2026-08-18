package io.github.postgresbulk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class BulkExceptionTest {

  @Test
  void retainsMessage() {
    BulkException exception = new BulkException("bulk insert failed");

    assertEquals("bulk insert failed", exception.getMessage());
  }

  @Test
  void retainsRootCause() {
    IllegalStateException cause = new IllegalStateException("driver failure");

    BulkException exception = new BulkException("bulk insert failed", cause);

    assertEquals("bulk insert failed", exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
