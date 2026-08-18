package io.github.postgresbulk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BulkOperationsTest {

  @Test
  void shortOverloadDelegatesWithDefaultsAndPreservesCovariantInput() {
    RecordingOperations<Number> operations = new RecordingOperations<>();
    List<Integer> items = List.of(10, 20);

    BulkWriteResult result = operations.bulkInsert(items);

    assertSame(items, operations.items);
    assertEquals(BulkInsertOptions.defaults(), operations.options);
    assertEquals(new BulkWriteResult(2, 1), result);
  }

  @Test
  void shortOverloadRejectsNullInputBeforeDelegating() {
    RecordingOperations<String> operations = new RecordingOperations<>();

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> operations.bulkInsert(null));

    assertEquals("items must not be null", exception.getMessage());
    assertEquals(0, operations.invocations);
  }

  private static final class RecordingOperations<T> implements BulkOperations<T> {

    private Iterable<? extends T> items;
    private BulkInsertOptions options;
    private int invocations;

    @Override
    public BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
      this.items = items;
      this.options = options;
      invocations++;
      return new BulkWriteResult(2, 1);
    }
  }
}
