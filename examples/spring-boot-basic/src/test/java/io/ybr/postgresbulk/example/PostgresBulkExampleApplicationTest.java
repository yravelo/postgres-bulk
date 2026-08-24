package io.ybr.postgresbulk.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.MeterRegistry;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.example.ProductImportService.IntentionalRollbackException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcOperations;
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
  @Autowired JdbcOperations jdbc;

  @BeforeEach
  void clearProducts() {
    products.deleteAllInBatch();
    for (String schema : List.of("example_jpa_a", "example_jpa_b")) {
      jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
      jdbc.execute(
          "CREATE TABLE IF NOT EXISTS " + schema + ".product (LIKE public.product INCLUDING ALL)");
      jdbc.execute("TRUNCATE " + schema + ".product");
    }
  }

  @Test
  void externalJpaStarterConsumerUsesRuntimeTargets() {
    TableName a = TableName.of("example_jpa_a", "product");
    TableName b = TableName.of("example_jpa_b", "product");
    Product onlyA = product("TARGET-A", "only a");
    Product onlyB = product("TARGET-B", "only b");
    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    products.bulkInsert(a, List.of(onlyA));
    products.bulkInsert(b, List.of(onlyB));

    assertEquals(1, products.findAllByBulkKey(List.of("TARGET-A", "TARGET-B"), sku, a).size());
    assertEquals("TARGET-A", products.findAllByBulkKey(List.of("TARGET-A"), sku, a).get(0).sku());
    assertEquals("TARGET-B", products.findAllByBulkKey(List.of("TARGET-B"), sku, b).get(0).sku());
  }

  @Test
  void exercisesInsertLookupRollbackAndObservabilityThroughPublicApi() {
    double rowsBefore = counterValue("postgres.bulk.rows");
    double batchesBefore = counterValue("postgres.bulk.batches");
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
        InvalidDataAccessApiUsageException.class,
        () -> service.findBySkusInReadOnlyTransaction(List.of("SKU-001")));

    assertThrows(
        IntentionalRollbackException.class,
        () -> service.importThenRollback(List.of(product("SKU-rollback", "Rolled back"))));
    assertEquals(5, products.count());

    // Result counters are recorded when the bulk call succeeds. A later outer rollback does not
    // rewrite them, so the attempted row and COPY batch remain observable.
    assertEquals(rowsBefore + 6.0, counterValue("postgres.bulk.rows"));
    assertEquals(batchesBefore + 4.0, counterValue("postgres.bulk.batches"));
  }

  private double counterValue(String name) {
    io.micrometer.core.instrument.Counter counter =
        meters.find(name).tag("operation", "insert").counter();
    return counter == null ? 0.0 : counter.count();
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
