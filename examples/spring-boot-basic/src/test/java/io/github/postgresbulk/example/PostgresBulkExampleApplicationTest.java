package io.github.postgresbulk.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.example.ProductImportService.IntentionalRollbackException;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "example.demo.enabled=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresBulkExampleApplicationTest {

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:15.18-alpine"));

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRESQL::getUsername);
    properties.add("spring.datasource.password", POSTGRESQL::getPassword);
  }

  @Autowired ProductImportService service;
  @Autowired ProductRepository products;
  @Autowired MeterRegistry meters;

  @BeforeEach
  void clearProducts() {
    products.deleteAllInBatch();
  }

  @Test
  void exercisesInsertLookupRollbackAndObservabilityThroughPublicApi() {
    BulkWriteResult defaults =
        service.importProducts(
            List.of(product("SKU-001", "Keyboard"), product("SKU-002", "Mouse")));
    BulkWriteResult explicitBatch =
        service.importProducts(
            List.of(
                product("SKU-003", "Display"),
                product("SKU-004", "Dock"),
                product("SKU-005", "Headset")),
            2);

    assertEquals(new BulkWriteResult(2, 1), defaults);
    assertEquals(new BulkWriteResult(3, 2), explicitBatch);
    assertEquals(2, service.findBySkus(List.of("SKU-001", "SKU-001", "missing", "SKU-005")).size());
    assertEquals(
        1,
        service
            .findBySkuAndName(
                List.of(
                    new ProductLookupKey("SKU-003", "Display"),
                    new ProductLookupKey("missing", "Display")))
            .size());

    assertThrows(
        IntentionalRollbackException.class,
        () -> service.importThenRollback(List.of(product("SKU-rollback", "Rolled back"))));
    assertEquals(5, products.count());

    // Result counters are recorded when the bulk call succeeds. A later outer rollback does not
    // rewrite them, so the attempted row and COPY batch remain observable.
    assertEquals(
        6.0, meters.find("postgres.bulk.rows").tag("operation", "insert").counter().count());
    assertEquals(
        4.0, meters.find("postgres.bulk.batches").tag("operation", "insert").counter().count());
  }

  private static Product product(String sku, String name) {
    return new Product(
        UUID.randomUUID(),
        sku,
        name,
        new BigDecimal("19.95"),
        Instant.parse("2026-08-19T00:00:00Z"));
  }
}
