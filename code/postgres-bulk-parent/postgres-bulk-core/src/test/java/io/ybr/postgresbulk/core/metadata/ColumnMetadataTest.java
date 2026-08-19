package io.ybr.postgresbulk.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ColumnMetadataTest {

  @Test
  void readsAValueWithoutReflection() {
    ColumnMetadata<Product> column = ColumnMetadata.of("physical_sku", String.class, Product::sku);

    assertEquals("physical_sku", column.columnName());
    assertEquals(String.class, column.javaType());
    assertEquals("SKU-42", column.read(new Product("SKU-42", null, 3)));
  }

  @Test
  void retainsDeclaredTypeWhenAccessorReturnsNull() {
    ColumnMetadata<Product> column =
        ColumnMetadata.of("description", String.class, Product::description);

    assertEquals(String.class, column.javaType());
    assertNull(column.read(new Product("SKU-42", null, 3)));
  }

  @Test
  void normalizesPrimitiveTypeToWrapper() {
    ColumnMetadata<Product> column = ColumnMetadata.of("quantity", int.class, Product::quantity);

    assertEquals(Integer.class, column.javaType());
    assertEquals(3, column.read(new Product("SKU-42", null, 3)));
  }

  @Test
  void propagatesAccessorFailureUnchanged() {
    IllegalStateException failure = new IllegalStateException("cannot read mapping");
    ColumnMetadata<Product> column =
        ColumnMetadata.of(
            "sku",
            String.class,
            product -> {
              throw failure;
            });

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> column.read(new Product("SKU-42", null, 3)));

    assertSame(failure, thrown);
  }

  @Test
  void rejectsInvalidConstructionArguments() {
    assertThrows(
        NullPointerException.class, () -> ColumnMetadata.of(null, String.class, Product::sku));
    assertThrows(
        IllegalArgumentException.class, () -> ColumnMetadata.of(" ", String.class, Product::sku));
    assertThrows(NullPointerException.class, () -> ColumnMetadata.of("sku", null, Product::sku));
    assertThrows(
        IllegalArgumentException.class,
        () -> ColumnMetadata.of("sku", void.class, product -> null));
    assertThrows(NullPointerException.class, () -> ColumnMetadata.of("sku", String.class, null));
  }

  @Test
  void rejectsNullSourceBeforeCallingAccessor() {
    ColumnMetadata<Product> column = ColumnMetadata.of("sku", String.class, Product::sku);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> column.read(null));

    assertEquals("source must not be null", exception.getMessage());
  }

  private record Product(String sku, String description, int quantity) {}
}
