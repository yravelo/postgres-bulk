package io.ybr.postgresbulk.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityMetadataTest {

  @Test
  void preservesExplicitColumnOrderAndSupportsMultipleProjections() {
    ColumnMetadata<Product> amount =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());
    ColumnMetadata<Product> currency =
        ColumnMetadata.of("currency", String.class, product -> product.price().currency());
    ColumnMetadata<Product> customerId =
        ColumnMetadata.of("customer_id", Long.class, product -> product.customer().id());

    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class, TableName.of("sales", "product"), List.of(amount, currency, customerId));
    Product product = new Product(new Price(new BigDecimal("12.50"), "EUR"), new Customer(7L));

    assertEquals(Product.class, metadata.javaType());
    assertEquals(TableName.of("sales", "product"), metadata.table());
    assertEquals(List.of(amount, currency, customerId), metadata.insertColumns());
    assertEquals(new BigDecimal("12.50"), metadata.insertColumns().get(0).read(product));
    assertEquals("EUR", metadata.insertColumns().get(1).read(product));
    assertEquals(7L, metadata.insertColumns().get(2).read(product));
  }

  @Test
  void makesDefensiveUnmodifiableCopyOfColumns() {
    ColumnMetadata<Product> amount =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());
    List<ColumnMetadata<Product>> supplied = new ArrayList<>();
    supplied.add(amount);

    EntityMetadata<Product> metadata =
        EntityMetadata.of(Product.class, TableName.of("product"), supplied);
    supplied.clear();

    assertEquals(List.of(amount), metadata.insertColumns());
    assertThrows(UnsupportedOperationException.class, () -> metadata.insertColumns().add(amount));
  }

  @Test
  void retainsColumnInstancesInsteadOfCopyingExecutableMappings() {
    ColumnMetadata<Product> amount =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());

    EntityMetadata<Product> metadata =
        EntityMetadata.of(Product.class, TableName.of("product"), List.of(amount));

    assertSame(amount, metadata.insertColumns().get(0));
  }

  @Test
  void rejectsDuplicatePhysicalColumns() {
    ColumnMetadata<Product> first =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());
    ColumnMetadata<Product> duplicate =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());

    assertThrows(
        IllegalArgumentException.class,
        () -> EntityMetadata.of(Product.class, TableName.of("product"), List.of(first, duplicate)));
  }

  @Test
  void rejectsMissingOrNullMetadata() {
    ColumnMetadata<Product> amount =
        ColumnMetadata.of("amount", BigDecimal.class, product -> product.price().amount());

    assertThrows(
        NullPointerException.class,
        () -> EntityMetadata.of(null, TableName.of("product"), List.of(amount)));
    assertThrows(
        NullPointerException.class, () -> EntityMetadata.of(Product.class, null, List.of(amount)));
    assertThrows(
        NullPointerException.class,
        () -> EntityMetadata.of(Product.class, TableName.of("product"), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> EntityMetadata.of(Product.class, TableName.of("product"), List.of()));

    List<ColumnMetadata<Product>> withNull = new ArrayList<>();
    withNull.add(null);
    assertThrows(
        NullPointerException.class,
        () -> EntityMetadata.of(Product.class, TableName.of("product"), withNull));
  }

  private record Product(Price price, Customer customer) {}

  private record Price(BigDecimal amount, String currency) {}

  private record Customer(Long id) {}
}
