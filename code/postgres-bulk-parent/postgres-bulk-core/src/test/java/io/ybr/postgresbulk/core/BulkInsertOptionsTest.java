package io.ybr.postgresbulk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BulkInsertOptionsTest {

  @Test
  void defaultsUseAValidStableBatchSize() {
    assertEquals(1_000, BulkInsertOptions.defaults().batchSize());
  }

  @Test
  void explicitBatchSizeHasValueSemantics() {
    BulkInsertOptions first = BulkInsertOptions.ofBatchSize(250);
    BulkInsertOptions second = BulkInsertOptions.ofBatchSize(250);

    assertEquals(250, first.batchSize());
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals("BulkInsertOptions[batchSize=250]", first.toString());
  }

  @Test
  void rejectsZeroBatchSize() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> BulkInsertOptions.ofBatchSize(0));

    assertTrue(exception.getMessage().contains("batchSize"));
  }

  @Test
  void rejectsNegativeBatchSize() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> BulkInsertOptions.ofBatchSize(-1));

    assertTrue(exception.getMessage().contains("-1"));
  }
}
