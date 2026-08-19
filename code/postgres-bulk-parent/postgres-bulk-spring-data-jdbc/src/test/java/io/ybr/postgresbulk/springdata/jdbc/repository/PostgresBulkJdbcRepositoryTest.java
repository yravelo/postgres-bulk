package io.ybr.postgresbulk.springdata.jdbc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PostgresBulkJdbcRepositoryTest {

  @Test
  void defaultInsertDelegatesWithDefaultOptions() {
    AtomicReference<Iterable<? extends String>> actualItems = new AtomicReference<>();
    AtomicReference<BulkInsertOptions> actualOptions = new AtomicReference<>();
    BulkWriteResult expected = new BulkWriteResult(2, 1);
    PostgresBulkJdbcRepository<String> repository =
        new PostgresBulkJdbcRepository<>() {
          @Override
          public BulkWriteResult bulkInsert(
              Iterable<? extends String> items, BulkInsertOptions options) {
            actualItems.set(items);
            actualOptions.set(options);
            return expected;
          }

          @Override
          public <K> List<String> findAllByBulkKey(
              Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
            throw new AssertionError("lookup must not be called");
          }
        };
    List<String> items = List.of("a", "b");

    assertSame(expected, repository.bulkInsert(items));
    assertSame(items, actualItems.get());
    assertEquals(BulkInsertOptions.defaults(), actualOptions.get());
  }
}
