package io.github.postgresbulk.springdata.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.github.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgresBulkRepositoryIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:15.18-alpine");

  private static AnnotationConfigApplicationContext context;
  private static ProductRepository products;
  private static WarehouseRepository warehouses;
  private static PlatformTransactionManager transactionManager;
  private static EntityManagerFactory entityManagerFactory;

  @BeforeAll
  static void startContext() {
    context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    products = context.getBean(ProductRepository.class);
    warehouses = context.getBean(WarehouseRepository.class);
    transactionManager = context.getBean(PlatformTransactionManager.class);
    entityManagerFactory = context.getBean(EntityManagerFactory.class);
  }

  @BeforeEach
  void cleanTables() {
    products.deleteAllInBatch();
    warehouses.deleteAllInBatch();
  }

  @AfterAll
  static void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void externalFragmentSupportsTwoRepositoriesAndExplicitBatching() {
    BulkWriteResult productResult =
        products.bulkInsert(
            List.of(new Product(101L, "A-101", "alpha"), new Product(102L, "A-102", "beta")),
            BulkInsertOptions.ofBatchSize(1));
    BulkWriteResult warehouseResult = warehouses.bulkInsert(List.of(new Warehouse(201L, "north")));

    assertEquals(new BulkWriteResult(2, 2), productResult);
    assertEquals(new BulkWriteResult(1, 1), warehouseResult);
    assertEquals(2, products.count());
    assertEquals(1, warehouses.count());
  }

  @Test
  void emptyInputsReturnEmptyResults() {
    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    assertEquals(BulkWriteResult.empty(), products.bulkInsert(List.of()));
    assertEquals(List.of(), products.findAllByBulkKey(List.of(), sku));
    assertEquals(0, products.count());
  }

  @Test
  void lookupMaterializesEntitiesThroughJpaWhileTemporaryTableIsAlive() {
    products.bulkInsert(List.of(new Product(301L, "K-1", "one"), new Product(302L, "K-2", "two")));
    BulkKeyMetadata<String> sku =
        BulkKeyMetadata.of(
            String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

    List<Product> found = products.findAllByBulkKey(List.of("K-2", "missing", "K-2"), sku);

    assertEquals(1, found.size());
    assertEquals("K-2", found.get(0).sku);
  }

  @Test
  void repositoryMethodCreatesTransactionAndOuterRollbackRemainsAuthoritative() {
    long id = 401L;
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThrows(
        DeliberateRollback.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  products.bulkInsert(List.of(new Product(id, "ROLLBACK", "rollback")));
                  throw new DeliberateRollback();
                }));

    assertTrue(products.findById(id).isEmpty());
  }

  @Test
  void readOnlyTransactionFailsBeforeCopyAndIsNeverMutated() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);

    InvalidDataAccessApiUsageException failure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                transaction.executeWithoutResult(
                    status ->
                        products.bulkInsert(List.of(new Product(501L, "READ-ONLY", "read-only")))));

    assertTrue(failure.getMessage().contains("read-only"));
  }

  @Test
  void jdbcCopyAndJpaMaterializationObserveTheSameBackendPid() {
    products.bulkInsert(List.of(new Product(551L, "PID", "same backend")));
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          var entityManager =
              SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
          EntityMetadata<Product> metadata =
              new HibernateEntityMetadataResolver(entityManagerFactory).resolve(Product.class);
          PostgresBulkJdbcOperations<Product> operations =
              PostgresBulkJdbcOperations.prepare(metadata);
          BulkKeyMetadata<String> sku =
              BulkKeyMetadata.of(
                  String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));

          entityManager
              .unwrap(Session.class)
              .doWork(
                  connection ->
                      operations.findAllByBulkKey(
                          connection,
                          List.of("PID"),
                          sku,
                          List.of(),
                          (jdbcConnection, selectSql, copiedKeys) -> {
                            long jdbcPid;
                            try (var statement = jdbcConnection.createStatement();
                                var result = statement.executeQuery("select pg_backend_pid()")) {
                              result.next();
                              jdbcPid = result.getLong(1);
                            }
                            long jpaPid =
                                ((Number)
                                        entityManager
                                            .createNativeQuery("select pg_backend_pid()")
                                            .getSingleResult())
                                    .longValue();
                            assertEquals(jdbcPid, jpaPid);
                            return entityManager
                                .createNativeQuery(selectSql, Product.class)
                                .getResultList();
                          }));
        });
  }

  @Test
  void requiresNewUsesItsOwnTransactionBoundary() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate inner = new TransactionTemplate(transactionManager);
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    assertThrows(
        DeliberateRollback.class,
        () ->
            outer.executeWithoutResult(
                outerStatus -> {
                  inner.executeWithoutResult(
                      innerStatus ->
                          products.bulkInsert(
                              List.of(new Product(601L, "REQUIRES-NEW", "committed"))));
                  throw new DeliberateRollback();
                }));

    assertTrue(products.findById(601L).isPresent());
  }

  @Test
  void nestedPropagationIsRejectedByJpaTransactionManagerDefault() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate nested = new TransactionTemplate(transactionManager);
    nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

    assertThrows(
        NestedTransactionNotSupportedException.class,
        () ->
            outer.executeWithoutResult(
                outerStatus ->
                    nested.executeWithoutResult(
                        nestedStatus ->
                            products.bulkInsert(
                                List.of(new Product(701L, "NESTED", "not inserted"))))));

    assertTrue(products.findById(701L).isEmpty());
  }

  interface ProductRepository
      extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}

  interface WarehouseRepository
      extends JpaRepository<Warehouse, Long>, PostgresBulkRepository<Warehouse, Long> {}

  @Entity
  @Table(name = "phase9_product")
  static class Product {
    @Id Long id;

    @Column(nullable = false, unique = true)
    String sku;

    @Column(nullable = false)
    String name;

    Product() {}

    Product(Long id, String sku, String name) {
      this.id = id;
      this.sku = sku;
      this.name = name;
    }
  }

  @Entity
  @Table(name = "phase9_warehouse")
  static class Warehouse {
    @Id Long id;

    @Column(nullable = false)
    String name;

    Warehouse() {}

    Warehouse(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = PostgresBulkRepositoryIT.class,
      considerNestedRepositories = true)
  static class TestConfiguration {

    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan(PostgresBulkRepositoryIT.class.getPackageName());
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      Properties properties = new Properties();
      properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
      properties.setProperty("hibernate.show_sql", "false");
      factory.setJpaProperties(properties);
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    JpaEntityMetadataResolver bulkMetadataResolver() {
      return JpaEntityMetadataResolver.caching(HibernateEntityMetadataResolver::new);
    }
  }

  private static final class DeliberateRollback extends RuntimeException {}
}
