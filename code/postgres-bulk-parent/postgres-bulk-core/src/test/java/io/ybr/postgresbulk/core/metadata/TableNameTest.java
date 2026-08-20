package io.ybr.postgresbulk.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class TableNameTest {

  @Test
  void representsTableWithoutSchema() {
    TableName table = TableName.of("product");

    assertFalse(table.schema().isPresent());
    assertEquals("product", table.table());
    assertEquals(TableName.of("product"), table);
    assertEquals(TableName.of("product").hashCode(), table.hashCode());
  }

  @Test
  void representsSchemaAndTableWithoutParsingOrNormalization() {
    TableName table = TableName.of("Sales.Schema", "Order Items");

    assertEquals("Sales.Schema", table.schema().orElseThrow());
    assertEquals("Order Items", table.table());
    assertEquals("TableName[schema=Sales.Schema, table=Order Items]", table.toString());
  }

  @Test
  void rejectsNullComponents() {
    assertThrows(NullPointerException.class, () -> TableName.of((String) null));
    assertThrows(NullPointerException.class, () -> TableName.of(null, "product"));
    assertThrows(NullPointerException.class, () -> TableName.of("sales", null));
  }

  @Test
  void rejectsBlankComponents() {
    assertThrows(IllegalArgumentException.class, () -> TableName.of(" \t"));
    assertThrows(IllegalArgumentException.class, () -> TableName.of("", "product"));
    assertThrows(IllegalArgumentException.class, () -> TableName.of("sales", "\n"));
  }

  @Test
  void resolvesQualifiedRuntimeTargetForUnqualifiedMapping() {
    TableName mapped = TableName.of("product");
    TableName runtimeTarget = TableName.of("tenant_a", "product");

    assertSame(runtimeTarget, mapped.resolveRuntimeTarget(runtimeTarget));
    assertFalse(mapped.schema().isPresent());
    assertEquals("product", mapped.table());
  }

  @Test
  void keepsMappedTargetAsDefaultWhenNoRuntimeTargetIsSupplied() {
    TableName unqualified = TableName.of("product");
    TableName qualified = TableName.of("public", "product");
    EntityMetadata<Product> unqualifiedMetadata =
        EntityMetadata.of(
            Product.class, unqualified, List.of(ColumnMetadata.of("id", Long.class, Product::id)));
    EntityMetadata<Product> qualifiedMetadata =
        EntityMetadata.of(
            Product.class, qualified, List.of(ColumnMetadata.of("id", Long.class, Product::id)));

    assertSame(unqualified, unqualifiedMetadata.table());
    assertSame(qualified, qualifiedMetadata.table());
  }

  @Test
  void resolvesIdenticalRuntimeTargetForQualifiedMapping() {
    TableName mapped = TableName.of("public", "product");
    TableName runtimeTarget = TableName.of("public", "product");

    assertSame(runtimeTarget, mapped.resolveRuntimeTarget(runtimeTarget));
  }

  @Test
  void preservesExactQuotedAndMixedCaseComponents() {
    TableName runtimeTarget = TableName.of("Tenant.Schema", "Order Items");

    TableName resolved = TableName.of("Order Items").resolveRuntimeTarget(runtimeTarget);

    assertSame(runtimeTarget, resolved);
    assertEquals("Tenant.Schema", resolved.schema().orElseThrow());
    assertEquals("Order Items", resolved.table());
  }

  @Test
  void rejectsUnqualifiedRuntimeTarget() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TableName.of("product").resolveRuntimeTarget(TableName.of("product")));
  }

  @Test
  void rejectsRuntimeTargetThatChangesMappedTable() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TableName.of("product")
                .resolveRuntimeTarget(TableName.of("tenant_a", "product_archive")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TableName.of("public", "product")
                .resolveRuntimeTarget(TableName.of("public", "product_archive")));
  }

  @Test
  void rejectsRuntimeTargetThatConflictsWithMappedSchema() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TableName.of("public", "product")
                .resolveRuntimeTarget(TableName.of("tenant_a", "product")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TableName.of("public", "product")
                .resolveRuntimeTarget(TableName.of("tenant_a", "product_archive")));
  }

  @Test
  void rejectsNullRuntimeTarget() {
    assertThrows(
        NullPointerException.class, () -> TableName.of("product").resolveRuntimeTarget(null));
  }

  @Test
  void doesNotMutateEntityMetadataWhenResolvingTargets() {
    TableName mapped = TableName.of("product");
    ColumnMetadata<Product> id = ColumnMetadata.of("id", Long.class, Product::id);
    EntityMetadata<Product> metadata = EntityMetadata.of(Product.class, mapped, List.of(id));

    mapped.resolveRuntimeTarget(TableName.of("tenant_a", "product"));
    mapped.resolveRuntimeTarget(TableName.of("tenant_b", "product"));

    assertSame(mapped, metadata.table());
    assertSame(id, metadata.insertColumns().get(0));
  }

  @Test
  void resolvesDifferentSchemasConcurrentlyWithoutRetainingTarget() throws Exception {
    TableName mapped = TableName.of("product");
    TableName tenantA = TableName.of("tenant_a", "product");
    TableName tenantB = TableName.of("tenant_b", "product");
    List<Callable<TableName>> operations = new ArrayList<>();
    for (int index = 0; index < 200; index++) {
      TableName target = index % 2 == 0 ? tenantA : tenantB;
      operations.add(() -> mapped.resolveRuntimeTarget(target));
    }

    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<TableName>> results = executor.invokeAll(operations);
      for (int index = 0; index < results.size(); index++) {
        assertSame(index % 2 == 0 ? tenantA : tenantB, results.get(index).get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private record Product(long id) {}
}
