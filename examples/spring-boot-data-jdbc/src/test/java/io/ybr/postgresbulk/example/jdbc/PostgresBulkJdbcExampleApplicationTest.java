package io.ybr.postgresbulk.example.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.example.jdbc.ProductImportService.IntentionalRollbackException;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "example.demo.enabled=false")
@Testcontainers
class PostgresBulkJdbcExampleApplicationTest {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version", "15.18-alpine"));

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductImportService service;
  @Autowired private ProductRepository products;
  @Autowired private SpringDataJdbcEntityMetadataResolver metadataResolver;
  @Autowired private JdbcOperations jdbc;

  @BeforeEach
  void clearProducts() {
    jdbc.execute("TRUNCATE jdbc_product");
  }

  @Test
  void verifiesStarterDiscoveryInsertLookupRollbackAndReadOnly() {
    Product keyboard = product("SKU-1", "peripherals", "Keyboard");
    Product mouse = product("SKU-2", "peripherals", "Mouse");
    Product book = product("SKU-3", "books", "PostgreSQL handbook");

    assertThat(metadataResolver).isNotNull();
    assertThat(products).isNotNull();
    assertThat(service.importProducts(List.of(keyboard))).isEqualTo(new BulkWriteResult(1, 1));
    assertThat(service.importProducts(List.of(mouse, book), 1))
        .isEqualTo(new BulkWriteResult(2, 2));

    assertThat(service.findBySkus(List.of("SKU-1", "missing", "SKU-2")))
        .containsExactlyInAnyOrder(keyboard, mouse);
    assertThat(
            service.findBySkuAndCategory(
                List.of(
                    new ProductLookupKey("SKU-3", "books"),
                    new ProductLookupKey("SKU-2", "wrong"))))
        .containsExactly(book);

    Product rolledBack = product("ROLLBACK", "test", "Rolled back");
    assertThatThrownBy(() -> service.importThenRollback(List.of(rolledBack)))
        .isInstanceOf(IntentionalRollbackException.class);
    assertThat(products.findById(rolledBack.id())).isEmpty();

    assertThatThrownBy(
            () ->
                service.importInsideReadOnlyTransaction(
                    List.of(product("READ-ONLY", "test", "Rejected"))))
        .isInstanceOf(InvalidDataAccessApiUsageException.class);
    assertThat(products.count()).isEqualTo(3L);
  }

  private static Product product(String sku, String category, String name) {
    return new Product(
        UUID.randomUUID(), sku, category, name, new BigDecimal("19.95"), new Address("Madrid"));
  }
}
