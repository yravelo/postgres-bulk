package io.ybr.postgresbulk.springdata.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgresBulkRepositoryIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"));

  private static AnnotationConfigApplicationContext context;
  private static ProductRepository products;
  private static WarehouseRepository warehouses;
  private static ConvertedProductRepository convertedProducts;
  private static PlatformTransactionManager transactionManager;
  private static EntityManagerFactory entityManagerFactory;

  @BeforeAll
  static void startContext() {
    context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    products = context.getBean(ProductRepository.class);
    warehouses = context.getBean(WarehouseRepository.class);
    convertedProducts = context.getBean(ConvertedProductRepository.class);
    transactionManager = context.getBean(PlatformTransactionManager.class);
    entityManagerFactory = context.getBean(EntityManagerFactory.class);
  }

  @BeforeEach
  void cleanTables() {
    products.deleteAllInBatch();
    warehouses.deleteAllInBatch();
    convertedProducts.deleteAllInBatch();
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
                  products.bulkInsert(
                      List.of(
                          new Product(id, "ROLLBACK-1", "rollback one"),
                          new Product(id + 1, "ROLLBACK-2", "rollback two"),
                          new Product(id + 2, "ROLLBACK-3", "rollback three")),
                      BulkInsertOptions.ofBatchSize(1));
                  throw new DeliberateRollback();
                }));

    assertTrue(products.findById(id).isEmpty());
    assertTrue(products.findById(id + 1).isEmpty());
    assertTrue(products.findById(id + 2).isEmpty());
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
    BulkKeyMetadata<String> sku = skuMetadata();
    InvalidDataAccessApiUsageException lookupFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                transaction.executeWithoutResult(
                    status -> products.findAllByBulkKey(List.of("READ-ONLY"), sku)));
    assertTrue(lookupFailure.getMessage().contains("read-only"));
    assertEquals(0, products.count());
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
  void failedRequiresNewRollsBackInnerAndOuterCanCommit() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate inner = new TransactionTemplate(transactionManager);
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    outer.executeWithoutResult(
        outerStatus -> {
          products.saveAndFlush(new Product(610L, "OUTER-A", "outer before"));
          BulkException failure =
              assertThrows(
                  BulkException.class,
                  () ->
                      inner.executeWithoutResult(
                          innerStatus ->
                              products.bulkInsert(
                                  List.of(
                                      new Product(611L, "INNER-DUP", "inner one"),
                                      new Product(612L, "INNER-DUP", "inner two")))));
          assertNotNull(findCause(failure, SQLException.class));
          products.save(new Product(613L, "OUTER-B", "outer after"));
        });

    assertTrue(products.findById(610L).isPresent());
    assertTrue(products.findById(613L).isPresent());
    assertTrue(products.findById(611L).isEmpty());
    assertTrue(products.findById(612L).isEmpty());
  }

  @Test
  void caughtRequiredFailureMarksRollbackOnlyAndLeavesPostgresqlAborted() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    List<Throwable> observed = new ArrayList<>();

    UnexpectedRollbackException completionFailure =
        assertThrows(
            UnexpectedRollbackException.class,
            () ->
                outer.executeWithoutResult(
                    status -> {
                      BulkException bulkFailure =
                          assertThrows(
                              BulkException.class,
                              () ->
                                  products.bulkInsert(
                                      List.of(
                                          new Product(620L, "ROLLBACK-ONLY", "one"),
                                          new Product(621L, "ROLLBACK-ONLY", "two"))));
                      observed.add(bulkFailure);
                      assertTrue(status.isRollbackOnly());

                      RuntimeException aborted =
                          assertThrows(RuntimeException.class, () -> products.count());
                      observed.add(aborted);
                    }));

    assertNotNull(completionFailure);
    assertEquals("23505", findCause(observed.get(0), SQLException.class).getSQLState());
    assertEquals("25P02", findCause(observed.get(1), SQLException.class).getSQLState());
    assertTrue(products.findById(620L).isEmpty());
  }

  @Test
  void iteratorFailureInsideSpringTransactionRemainsCauseAndRollsBackPriorBatch() {
    IllegalStateException iteratorFailure = new IllegalStateException("source unavailable");
    Iterable<Product> source =
        () ->
            new Iterator<>() {
              private int hasNextCalls;

              @Override
              public boolean hasNext() {
                if (++hasNextCalls == 4) {
                  throw iteratorFailure;
                }
                return true;
              }

              @Override
              public Product next() {
                return new Product(
                    630L + hasNextCalls, "ITERATOR-" + hasNextCalls, "iterator value");
              }
            };

    InvalidDataAccessApiUsageException thrown =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> products.bulkInsert(source, BulkInsertOptions.ofBatchSize(2)));

    assertSame(iteratorFailure, thrown.getCause());
    assertEquals(0, products.count());
  }

  @Test
  void attributeConverterFailureRemainsReachableAndRollsBackActiveCopy() {
    ConverterFailure converterFailure = new ConverterFailure("converter unavailable");
    ExplosiveValue failing = new ExplosiveValue("secret-converter-value", converterFailure);

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                convertedProducts.bulkInsert(
                    List.of(
                        new ConvertedProduct(640L, new ExplosiveValue("ok", null)),
                        new ConvertedProduct(641L, failing)),
                    BulkInsertOptions.ofBatchSize(2)));

    assertTrue(hasCause(thrown, converterFailure));
    assertFalse(String.valueOf(thrown.getMessage()).contains("secret-converter-value"));
    assertEquals(0, convertedProducts.count());
  }

  @Test
  void directFragmentDelegateWithoutRepositoryProxyIsRejected() {
    DefaultPostgresBulkOperations<Product, Long> direct =
        new DefaultPostgresBulkOperations<>(
            context.getBean(org.springframework.data.jpa.repository.JpaContext.class),
            context.getBean(JpaEntityMetadataResolver.class));

    assertThrows(
        IllegalStateException.class,
        () -> direct.bulkInsert(List.of(new Product(650L, "DIRECT", "direct"))));
    assertTrue(products.findById(650L).isEmpty());
  }

  @Test
  void singletonRepositorySupportsEightIndependentConcurrentTransactions() throws Exception {
    int threadCount = 8;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Future<String>> futures = new ArrayList<>();
    try {
      for (int index = 0; index < threadCount; index++) {
        int current = index;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  assertTrue(start.await(20, TimeUnit.SECONDS));
                  long id = 700L + current;
                  String sku = "CONCURRENT-" + current;
                  products.bulkInsert(List.of(new Product(id, sku, "parallel")));
                  return products.findAllByBulkKey(List.of(sku), skuMetadata()).get(0).sku;
                }));
      }
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      start.countDown();
      for (int index = 0; index < threadCount; index++) {
        assertEquals("CONCURRENT-" + index, futures.get(index).get(30, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(threadCount, products.count());
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

  @Test
  void nestedRemainsUnsupportedWhenJpaManagerFlagIsEnabled() {
    JpaTransactionManager manager = (JpaTransactionManager) transactionManager;
    manager.setNestedTransactionAllowed(true);
    try {
      TransactionTemplate outer = new TransactionTemplate(manager);
      TransactionTemplate nested = new TransactionTemplate(manager);
      nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

      outer.executeWithoutResult(
          outerStatus -> {
            products.saveAndFlush(new Product(710L, "NESTED-OUTER", "outer"));

            assertThrows(
                NestedTransactionNotSupportedException.class,
                () ->
                    nested.executeWithoutResult(
                        nestedStatus ->
                            products.bulkInsert(
                                List.of(
                                    new Product(711L, "NESTED-DUP", "one"),
                                    new Product(712L, "NESTED-DUP", "two")))));

            assertEquals(
                1, products.findAllByBulkKey(List.of("NESTED-OUTER"), skuMetadata()).size());
            products.save(new Product(713L, "NESTED-AFTER", "after savepoint rollbacks"));
          });

      assertTrue(products.findById(710L).isPresent());
      assertTrue(products.findById(713L).isPresent());
      assertTrue(products.findById(711L).isEmpty());
      assertTrue(products.findById(712L).isEmpty());
    } finally {
      manager.setNestedTransactionAllowed(false);
    }
  }

  interface ProductRepository
      extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}

  interface WarehouseRepository
      extends JpaRepository<Warehouse, Long>, PostgresBulkRepository<Warehouse, Long> {}

  interface ConvertedProductRepository
      extends JpaRepository<ConvertedProduct, Long>,
          PostgresBulkRepository<ConvertedProduct, Long> {}

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

  @Entity
  @Table(name = "phase11_converted_product")
  static class ConvertedProduct {
    @Id Long id;

    @Convert(converter = ExplosiveValueConverter.class)
    @Column(nullable = false)
    ExplosiveValue code;

    ConvertedProduct() {}

    ConvertedProduct(Long id, ExplosiveValue code) {
      this.id = id;
      this.code = code;
    }
  }

  record ExplosiveValue(String value, RuntimeException failure) {}

  @Converter
  public static class ExplosiveValueConverter
      implements AttributeConverter<ExplosiveValue, String> {

    @Override
    public String convertToDatabaseColumn(ExplosiveValue attribute) {
      if (attribute != null && attribute.failure() != null) {
        throw attribute.failure();
      }
      return attribute == null ? null : attribute.value();
    }

    @Override
    public ExplosiveValue convertToEntityAttribute(String value) {
      return value == null ? null : new ExplosiveValue(value, null);
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

  private static final class ConverterFailure extends RuntimeException {
    private ConverterFailure(String message) {
      super(message);
    }
  }

  private static BulkKeyMetadata<String> skuMetadata() {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));
  }

  private static boolean hasCause(Throwable failure, Throwable expected) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current == expected) {
        return true;
      }
    }
    return false;
  }

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
    }
    return null;
  }
}
