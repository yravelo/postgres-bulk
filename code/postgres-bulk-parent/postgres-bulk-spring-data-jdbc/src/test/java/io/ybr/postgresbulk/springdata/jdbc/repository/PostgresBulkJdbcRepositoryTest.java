package io.ybr.postgresbulk.springdata.jdbc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
          public BulkWriteResult bulkInsert(
              Iterable<? extends String> items,
              BulkInsertOptions options,
              TableName runtimeTarget) {
            throw new AssertionError("target insert must not be called");
          }

          @Override
          public <K> List<String> findAllByBulkKey(
              Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
            throw new AssertionError("lookup must not be called");
          }

          @Override
          public <K> List<String> findAllByBulkKey(
              Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget) {
            throw new AssertionError("target lookup must not be called");
          }
        };
    List<String> items = List.of("a", "b");

    assertSame(expected, repository.bulkInsert(items));
    assertSame(items, actualItems.get());
    assertEquals(BulkInsertOptions.defaults(), actualOptions.get());
  }

  @Test
  void targetFirstDefaultDelegatesWithoutBreakingHistoricalNullCall() {
    CapturingRepository repository = new CapturingRepository();
    TableName target = TableName.of("tenant_a", "value");

    BiFunction<Iterable<? extends String>, BulkInsertOptions, BulkWriteResult> historical =
        repository::bulkInsert;
    BiFunction<TableName, Iterable<? extends String>, BulkWriteResult> targetAware =
        repository::bulkInsert;

    historical.apply(List.of("legacy"), null);
    assertNull(repository.options);
    assertNull(repository.target);

    assertEquals(new BulkWriteResult(1, 1), targetAware.apply(target, List.of("target")));
    assertEquals(BulkInsertOptions.defaults(), repository.options);
    assertEquals(target, repository.target);
  }

  @Test
  void targetAwareLookupDelegatesEveryArgument() {
    CapturingRepository repository = new CapturingRepository();
    TableName target = TableName.of("tenant_b", "value");
    BulkKeyMetadata<String> keyMetadata =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("value", String.class, value -> value)));

    assertEquals(
        List.of("matched"), repository.findAllByBulkKey(List.of("key"), keyMetadata, target));
    assertEquals(target, repository.target);
    assertSame(keyMetadata, repository.keyMetadata);
  }

  @Test
  void singletonFragmentHasNoTableNameField() throws ClassNotFoundException {
    assertFalse(
        Arrays.stream(
                Class.forName(
                        "io.ybr.postgresbulk.springdata.jdbc.DefaultPostgresBulkJdbcOperations")
                    .getDeclaredFields())
            .anyMatch(field -> field.getType().equals(TableName.class)));
  }

  private static final class CapturingRepository implements PostgresBulkJdbcRepository<String> {

    private BulkInsertOptions options;
    private TableName target;
    private BulkKeyMetadata<?> keyMetadata;

    @Override
    public BulkWriteResult bulkInsert(Iterable<? extends String> items, BulkInsertOptions options) {
      this.options = options;
      target = null;
      return new BulkWriteResult(1, 1);
    }

    @Override
    public BulkWriteResult bulkInsert(
        Iterable<? extends String> items, BulkInsertOptions options, TableName runtimeTarget) {
      this.options = options;
      target = runtimeTarget;
      return new BulkWriteResult(1, 1);
    }

    @Override
    public <K> List<String> findAllByBulkKey(
        Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
      this.keyMetadata = keyMetadata;
      target = null;
      return List.of("matched");
    }

    @Override
    public <K> List<String> findAllByBulkKey(
        Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget) {
      this.keyMetadata = keyMetadata;
      target = runtimeTarget;
      return List.of("matched");
    }
  }
}
