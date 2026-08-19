package io.ybr.postgresbulk.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class BulkKeyMetadataTest {

  @Test
  void representsSimpleKey() {
    ColumnMetadata<String> sku = ColumnMetadata.of("sku", String.class, Function.identity());

    BulkKeyMetadata<String> metadata = BulkKeyMetadata.of(String.class, List.of(sku));

    assertEquals(String.class, metadata.javaType());
    assertEquals(List.of(sku), metadata.components());
    assertEquals("SKU-42", metadata.components().get(0).read("SKU-42"));
  }

  @Test
  void representsCompositeKeyInExplicitOrder() {
    ColumnMetadata<ProductKey> country =
        ColumnMetadata.of("country", String.class, ProductKey::country);
    ColumnMetadata<ProductKey> externalId =
        ColumnMetadata.of("external_id", String.class, ProductKey::externalId);

    BulkKeyMetadata<ProductKey> metadata =
        BulkKeyMetadata.of(ProductKey.class, List.of(country, externalId));
    ProductKey key = new ProductKey("ES", "EXT-7");

    assertEquals(List.of(country, externalId), metadata.components());
    assertEquals("ES", metadata.components().get(0).read(key));
    assertEquals("EXT-7", metadata.components().get(1).read(key));
  }

  @Test
  void makesDefensiveUnmodifiableCopyOfComponents() {
    ColumnMetadata<ProductKey> country =
        ColumnMetadata.of("country", String.class, ProductKey::country);
    List<ColumnMetadata<ProductKey>> supplied = new ArrayList<>();
    supplied.add(country);

    BulkKeyMetadata<ProductKey> metadata = BulkKeyMetadata.of(ProductKey.class, supplied);
    supplied.clear();

    assertEquals(List.of(country), metadata.components());
    assertThrows(UnsupportedOperationException.class, () -> metadata.components().add(country));
  }

  @Test
  void rejectsDuplicatePhysicalComponents() {
    ColumnMetadata<ProductKey> first =
        ColumnMetadata.of("country", String.class, ProductKey::country);
    ColumnMetadata<ProductKey> duplicate =
        ColumnMetadata.of("country", String.class, ProductKey::country);

    assertThrows(
        IllegalArgumentException.class,
        () -> BulkKeyMetadata.of(ProductKey.class, List.of(first, duplicate)));
  }

  @Test
  void rejectsMissingOrNullMetadata() {
    ColumnMetadata<ProductKey> country =
        ColumnMetadata.of("country", String.class, ProductKey::country);

    assertThrows(NullPointerException.class, () -> BulkKeyMetadata.of(null, List.of(country)));
    assertThrows(NullPointerException.class, () -> BulkKeyMetadata.of(ProductKey.class, null));
    assertThrows(
        IllegalArgumentException.class, () -> BulkKeyMetadata.of(ProductKey.class, List.of()));

    List<ColumnMetadata<ProductKey>> withNull = new ArrayList<>();
    withNull.add(null);
    assertThrows(NullPointerException.class, () -> BulkKeyMetadata.of(ProductKey.class, withNull));
  }

  private record ProductKey(String country, String externalId) {}
}
