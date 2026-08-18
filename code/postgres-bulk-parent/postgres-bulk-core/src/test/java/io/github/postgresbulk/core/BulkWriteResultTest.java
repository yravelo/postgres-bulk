package io.github.postgresbulk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BulkWriteResultTest {

  @Test
  void representsSuccessfulBatchedWrite() {
    BulkWriteResult result = new BulkWriteResult(2_500, 3);

    assertEquals(2_500, result.affectedRows());
    assertEquals(3, result.batches());
    assertEquals(new BulkWriteResult(2_500, 3), result);
  }

  @Test
  void emptyResultRepresentsNoOp() {
    BulkWriteResult result = BulkWriteResult.empty();

    assertEquals(0, result.affectedRows());
    assertEquals(0, result.batches());
  }

  @Test
  void rejectsNegativeAffectedRows() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BulkWriteResult(-1, 0));

    assertTrue(exception.getMessage().contains("affectedRows"));
  }

  @Test
  void rejectsNegativeBatches() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BulkWriteResult(0, -1));

    assertTrue(exception.getMessage().contains("batches"));
  }

  @Test
  void rejectsRowsWithoutBatches() {
    assertThrows(IllegalArgumentException.class, () -> new BulkWriteResult(1, 0));
  }

  @Test
  void rejectsBatchesWithoutRows() {
    assertThrows(IllegalArgumentException.class, () -> new BulkWriteResult(0, 1));
  }

  @Test
  void rejectsMoreBatchesThanRows() {
    assertThrows(IllegalArgumentException.class, () -> new BulkWriteResult(1, 2));
  }
}
