package io.ybr.postgresbulk.verification.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = ConsumerApplication.class)
@Testcontainers
class JdbcStarterConsumerIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:15.18-alpine");

  @Autowired private ConsumerProductRepository products;
  @Autowired private SpringDataJdbcEntityMetadataResolver metadataResolver;
  @Autowired private JdbcOperations jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void resetTable() {
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS consumer_products ("
            + "id bigint PRIMARY KEY, sku text NOT NULL UNIQUE)");
    jdbc.execute("TRUNCATE consumer_products");
    for (String schema : List.of("consumer_a", "consumer_b")) {
      jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
      jdbc.execute(
          "CREATE TABLE IF NOT EXISTS "
              + schema
              + ".consumer_products (LIKE public.consumer_products INCLUDING ALL)");
      jdbc.execute("TRUNCATE " + schema + ".consumer_products");
    }
  }

  @Test
  void installedJdbcStarterPropagatesExplicitTargets() {
    TableName a = TableName.of("consumer_a", "consumer_products");
    TableName b = TableName.of("consumer_b", "consumer_products");
    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    products.bulkInsert(a, List.of(new ConsumerProduct(10L, "only-a")));
    products.bulkInsert(b, List.of(new ConsumerProduct(11L, "only-b")));

    assertThat(products.findAllByBulkKey(List.of("only-a", "only-b"), sku, a))
        .containsExactly(new ConsumerProduct(10L, "only-a"));
    assertThat(products.findAllByBulkKey(List.of("only-a", "only-b"), sku, b))
        .containsExactly(new ConsumerProduct(11L, "only-b"));
  }

  @Test
  void bootsFromInstalledStarterAndExecutesInsertLookupRollbackAndReadOnly() {
    assertThat(metadataResolver).isNotNull();
    products.bulkInsert(List.of(new ConsumerProduct(1L, "one"), new ConsumerProduct(2L, "two")));

    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));
    assertThat(products.findAllByBulkKey(List.of("two", "missing"), sku))
        .containsExactly(new ConsumerProduct(2L, "two"));

    TransactionTemplate rollback = new TransactionTemplate(transactionManager);
    rollback.executeWithoutResult(
        status -> {
          products.bulkInsert(List.of(new ConsumerProduct(3L, "rollback")));
          status.setRollbackOnly();
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM consumer_products WHERE sku = 'rollback'", Long.class))
        .isZero();

    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);
    assertThatThrownBy(
            () ->
                readOnly.executeWithoutResult(
                    status -> products.bulkInsert(List.of(new ConsumerProduct(4L, "read-only")))))
        .isInstanceOf(InvalidDataAccessApiUsageException.class);
  }
}

@SpringBootApplication
class ConsumerApplication {}

interface ConsumerProductRepository
    extends CrudRepository<ConsumerProduct, Long>,
        PostgresBulkJdbcRepository<ConsumerProduct> {}

@Table("consumer_products")
record ConsumerProduct(@Id Long id, String sku) {}
