package io.ybr.postgresbulk.springdata.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class PostgresBulkRepositoryTest {

  @Test
  void targetFirstDefaultMethodDelegatesDefaultsWithoutBreakingHistoricalNullCall() {
    CapturingRepository repository = new CapturingRepository();
    TableName target = TableName.of("tenant_a", "product");

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
    TableName target = TableName.of("tenant_b", "product");
    BulkKeyMetadata<String> keyMetadata =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    assertEquals(
        List.of("matched"), repository.findAllByBulkKey(List.of("key"), keyMetadata, target));
    assertEquals(target, repository.target);
    assertEquals(keyMetadata, repository.keyMetadata);
  }

  @Test
  void singletonFragmentHasNoTableNameField() {
    assertFalse(
        Arrays.stream(DefaultPostgresBulkOperations.class.getDeclaredFields())
            .anyMatch(field -> field.getType().equals(TableName.class)));
  }

  private static final class CapturingRepository implements PostgresBulkRepository<String, Long> {

    private BulkInsertOptions options;
    private TableName target;
    private BulkKeyMetadata<?> keyMetadata;

    @Override
    public BulkWriteResult bulkInsert(Iterable<? extends String> items, BulkInsertOptions options) {
      this.options = options;
      this.target = null;
      return new BulkWriteResult(1, 1);
    }

    @Override
    public BulkWriteResult bulkInsert(
        Iterable<? extends String> items, BulkInsertOptions options, TableName runtimeTarget) {
      this.options = options;
      this.target = runtimeTarget;
      return new BulkWriteResult(1, 1);
    }

    @Override
    public <K> List<String> findAllByBulkKey(
        Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
      this.keyMetadata = keyMetadata;
      this.target = null;
      return List.of("matched");
    }

    @Override
    public <K> List<String> findAllByBulkKey(
        Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget) {
      this.keyMetadata = keyMetadata;
      this.target = runtimeTarget;
      return List.of("matched");
    }
  }
}
