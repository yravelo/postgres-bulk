package io.github.postgresbulk.starter.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.postgresbulk.autoconfigure.PostgresBulkProperties;
import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresBulkStarterIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:15.18-alpine");

  @Autowired private ProductRepository products;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JpaEntityMetadataResolver metadataResolver;
  @Autowired private PostgresBulkProperties properties;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @BeforeEach
  void cleanTable() {
    products.deleteAllInBatch();
  }

  @Test
  void starterAutoConfiguresInsertAndTypedLookup() {
    assertThat(metadataResolver).isNotNull();
    assertThat(properties.enabled()).isTrue();

    BulkWriteResult result =
        products.bulkInsert(
            List.of(new Product(1L, "SKU-1", "one"), new Product(2L, "SKU-2", "two")),
            BulkInsertOptions.ofBatchSize(1));
    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    assertThat(result).isEqualTo(new BulkWriteResult(2, 2));
    assertThat(products.findAllByBulkKey(List.of("SKU-2", "missing", "SKU-2"), sku))
        .extracting(product -> product.sku)
        .containsExactly("SKU-2");
  }

  @Test
  void outerRollbackRemainsAuthoritative() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      products.bulkInsert(List.of(new Product(3L, "ROLLBACK", "rollback")));
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    assertThat(products.findById(3L)).isEmpty();
  }

  @Test
  void readOnlyTransactionIsNotSilentlyChanged() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status ->
                        products.bulkInsert(List.of(new Product(4L, "READ-ONLY", "read-only")))))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("read-only");
  }

  private static final class DeliberateRollback extends RuntimeException {}
}
