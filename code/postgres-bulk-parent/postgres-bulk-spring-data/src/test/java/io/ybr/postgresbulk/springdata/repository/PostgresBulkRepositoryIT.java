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
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.sql.Connection;
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
  private static GeneratedProductRepository generatedProducts;
  private static StaticProductRepository staticProducts;
  private static FeatureProductRepository featureProducts;
  private static CategoryRepository categories;
  private static PlatformTransactionManager transactionManager;
  private static EntityManagerFactory entityManagerFactory;
  private static DataSource dataSource;

  private static final TableName TENANT_A = TableName.of("tenant_a", "phase9_product");
  private static final TableName TENANT_B = TableName.of("tenant_b", "phase9_product");
  private static final TableName QUOTED_TARGET = TableName.of("Tenant A", "phase9_product");

  @BeforeAll
  static void startContext() {
    context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    products = context.getBean(ProductRepository.class);
    warehouses = context.getBean(WarehouseRepository.class);
    convertedProducts = context.getBean(ConvertedProductRepository.class);
    generatedProducts = context.getBean(GeneratedProductRepository.class);
    staticProducts = context.getBean(StaticProductRepository.class);
    featureProducts = context.getBean(FeatureProductRepository.class);
    categories = context.getBean(CategoryRepository.class);
    transactionManager = context.getBean(PlatformTransactionManager.class);
    entityManagerFactory = context.getBean(EntityManagerFactory.class);
    dataSource = context.getBean(DataSource.class);
    executeSql(
        "CREATE SCHEMA tenant_a",
        "CREATE SCHEMA tenant_b",
        "CREATE SCHEMA empty_schema",
        "CREATE SCHEMA \"Tenant A\"",
        "CREATE TABLE tenant_a.phase9_product (LIKE phase9_product INCLUDING ALL)",
        "CREATE TABLE tenant_b.phase9_product (LIKE phase9_product INCLUDING ALL)",
        "CREATE TABLE \"Tenant A\".phase9_product (LIKE phase9_product INCLUDING ALL)",
        "ALTER TABLE tenant_a.phase9_product ADD backend_pid integer DEFAULT pg_backend_pid()",
        "ALTER TABLE tenant_b.phase9_product ADD backend_pid integer DEFAULT pg_backend_pid()",
        "CREATE TABLE tenant_a.phase11_converted_product "
            + "(LIKE phase11_converted_product INCLUDING ALL)",
        "CREATE TABLE tenant_b.phase11_converted_product "
            + "(LIKE phase11_converted_product INCLUDING ALL)",
        "CREATE TABLE tenant_a.ms4_generated_product "
            + "(LIKE ms4_generated_product INCLUDING ALL)",
        "CREATE TABLE tenant_a.ms4_feature_product " + "(LIKE ms4_feature_product INCLUDING ALL)");
  }

  @BeforeEach
  void cleanTables() {
    products.deleteAllInBatch();
    warehouses.deleteAllInBatch();
    convertedProducts.deleteAllInBatch();
    generatedProducts.deleteAllInBatch();
    staticProducts.deleteAllInBatch();
    featureProducts.deleteAllInBatch();
    categories.deleteAllInBatch();
    executeSql(
        "TRUNCATE tenant_a.phase9_product, tenant_b.phase9_product, "
            + "\"Tenant A\".phase9_product, tenant_a.phase11_converted_product, "
            + "tenant_b.phase11_converted_product, tenant_a.ms4_generated_product, "
            + "tenant_a.ms4_feature_product");
  }

  @AfterAll
  static void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void jpaFragmentRemainsOperationalWhenJdbcFragmentJarIsPresent() throws ClassNotFoundException {
    assertNotNull(
        Class.forName("io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository"));
    assertEquals(
        new BulkWriteResult(1, 1),
        products.bulkInsert(List.of(new Product(77L, "both-jars", "coexistence"))));
    assertEquals("both-jars", products.findById(77L).orElseThrow().sku);
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
  void targetAwareRepositoryInsertAndLookupIsolateSchemasOnTheSameProxy() {
    ProductRepository sameRepository = products;

    assertEquals(
        new BulkWriteResult(2, 2),
        sameRepository.bulkInsert(
            List.of(new Product(801L, "A-801", "alpha"), new Product(802L, "A-802", "alpha two")),
            BulkInsertOptions.ofBatchSize(1),
            TENANT_A));
    assertEquals(
        new BulkWriteResult(1, 1),
        sameRepository.bulkInsert(TENANT_B, List.of(new Product(803L, "B-803", "beta"))));

    List<Product> foundA =
        sameRepository.findAllByBulkKey(List.of("A-801", "B-803"), skuMetadata(), TENANT_A);
    List<Product> foundB =
        sameRepository.findAllByBulkKey(List.of("A-801", "B-803"), skuMetadata(), TENANT_B);

    assertEquals(List.of("A-801"), foundA.stream().map(product -> product.sku).toList());
    assertEquals(List.of("B-803"), foundB.stream().map(product -> product.sku).toList());
    assertEquals(0, products.count());
  }

  @Test
  void targetAwareInsertLookupAndJpaMaterializationUseOneTransactionConnection() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          products.bulkInsert(
              TENANT_A, List.of(new Product(811L, "PID-A", "same physical connection")));
          var entityManager =
              SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
          long sessionPid =
              entityManager
                  .unwrap(Session.class)
                  .doReturningWork(PostgresBulkRepositoryIT::backendPid);
          long copyPid =
              ((Number)
                      entityManager
                          .createNativeQuery(
                              "select backend_pid from tenant_a.phase9_product where id = 811")
                          .getSingleResult())
                  .longValue();

          List<Product> found =
              products.findAllByBulkKey(List.of("PID-A"), skuMetadata(), TENANT_A);
          assertEquals(sessionPid, copyPid);
          assertEquals("PID-A", found.get(0).sku);
          assertEquals(
              sessionPid,
              ((Number)
                      entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult())
                  .longValue());
        });
  }

  @Test
  void targetAwareCallsAcrossSchemasCommitAndRollbackTogether() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          products.bulkInsert(TENANT_A, List.of(new Product(821L, "TX-A", "commit A")));
          products.bulkInsert(TENANT_B, List.of(new Product(822L, "TX-B", "commit B")));
          assertEquals(
              1, products.findAllByBulkKey(List.of("TX-A"), skuMetadata(), TENANT_A).size());
          assertEquals(
              1, products.findAllByBulkKey(List.of("TX-B"), skuMetadata(), TENANT_B).size());
        });
    assertEquals(1L, targetCount("tenant_a.phase9_product"));
    assertEquals(1L, targetCount("tenant_b.phase9_product"));

    assertThrows(
        DeliberateRollback.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  products.bulkInsert(
                      TENANT_A, List.of(new Product(823L, "ROLLBACK-A", "rollback A")));
                  products.bulkInsert(
                      TENANT_B, List.of(new Product(824L, "ROLLBACK-B", "rollback B")));
                  throw new DeliberateRollback();
                }));
    assertEquals(1L, targetCount("tenant_a.phase9_product"));
    assertEquals(1L, targetCount("tenant_b.phase9_product"));
  }

  @Test
  void sameRepositoryProxyKeepsConcurrentTargetsIsolated() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> a =
          executor.submit(() -> targetRoundTrip(ready, start, TENANT_A, 831L, "PARALLEL-A"));
      Future<String> b =
          executor.submit(() -> targetRoundTrip(ready, start, TENANT_B, 832L, "PARALLEL-B"));
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      start.countDown();
      assertEquals("PARALLEL-A", a.get(30, TimeUnit.SECONDS));
      assertEquals("PARALLEL-B", b.get(30, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1L, targetCount("tenant_a.phase9_product"));
    assertEquals(1L, targetCount("tenant_b.phase9_product"));
  }

  @Test
  void targetAwareRequiresNewCommitsIndependentlyOfOuterRollback() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate inner = new TransactionTemplate(transactionManager);
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    assertThrows(
        DeliberateRollback.class,
        () ->
            outer.executeWithoutResult(
                outerStatus -> {
                  products.bulkInsert(
                      TENANT_A, List.of(new Product(841L, "OUTER-A", "rolled back")));
                  inner.executeWithoutResult(
                      innerStatus ->
                          products.bulkInsert(
                              TENANT_B, List.of(new Product(842L, "INNER-B", "committed"))));
                  throw new DeliberateRollback();
                }));

    assertEquals(0L, targetCount("tenant_a.phase9_product"));
    assertEquals(1L, targetCount("tenant_b.phase9_product"));
  }

  @Test
  void targetAwareCallsPreserveReadOnlyAndEmptyInputContracts() {
    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);

    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () ->
            readOnly.executeWithoutResult(
                status ->
                    products.bulkInsert(
                        TENANT_A, List.of(new Product(851L, "READ-ONLY-A", "blocked")))));
    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () ->
            readOnly.executeWithoutResult(
                status ->
                    products.findAllByBulkKey(List.of("READ-ONLY-A"), skuMetadata(), TENANT_A)));

    assertEquals(BulkWriteResult.empty(), products.bulkInsert(TENANT_A, List.of()));
    assertEquals(List.of(), products.findAllByBulkKey(List.of(), skuMetadata(), TENANT_A));
    InvalidDataAccessApiUsageException unqualified =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> products.bulkInsert(TableName.of("phase9_product"), List.of()));
    InvalidDataAccessApiUsageException wrongTable =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                products.findAllByBulkKey(
                    List.of(), skuMetadata(), TableName.of("tenant_a", "wrong_table")));
    assertNotNull(findCause(unqualified, IllegalArgumentException.class));
    assertNotNull(findCause(wrongTable, IllegalArgumentException.class));
    assertEquals(0L, targetCount("tenant_a.phase9_product"));
  }

  @Test
  void targetAwareCallsSupportQuotedSchemaAndPreserveServerSqlStates() {
    products.bulkInsert(QUOTED_TARGET, List.of(new Product(861L, "QUOTED", "quoted schema")));
    assertEquals(
        "QUOTED",
        products.findAllByBulkKey(List.of("QUOTED"), skuMetadata(), QUOTED_TARGET).get(0).sku);

    BulkException missingSchema =
        assertThrows(
            BulkException.class,
            () ->
                products.bulkInsert(
                    TableName.of("missing_schema", "phase9_product"),
                    List.of(new Product(862L, "MISSING-SCHEMA", "failure"))));
    assertEquals("3F000", findCause(missingSchema, SQLException.class).getSQLState());

    BulkException missingTable =
        assertThrows(
            BulkException.class,
            () ->
                products.findAllByBulkKey(
                    List.of("MISSING-TABLE"),
                    skuMetadata(),
                    TableName.of("empty_schema", "phase9_product")));
    assertEquals("42P01", findCause(missingTable, SQLException.class).getSQLState());
  }

  @Test
  void staticSchemaCompatibilityAndStructuralMetadataCacheRemainAuthoritative() {
    TableName sameTarget = TableName.of("public", "ms4_static_product");
    assertEquals(
        new BulkWriteResult(1, 1),
        staticProducts.bulkInsert(
            sameTarget, List.of(new StaticProduct(871L, "STATIC", "same mapping"))));
    assertEquals(
        "STATIC",
        staticProducts
            .findAllByBulkKey(List.of("STATIC"), staticSkuMetadata(), sameTarget)
            .get(0)
            .sku);

    InvalidDataAccessApiUsageException conflict =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                staticProducts.bulkInsert(
                    TableName.of("tenant_a", "ms4_static_product"),
                    List.of(new StaticProduct(872L, "CONFLICT", "blocked"))));
    assertNotNull(findCause(conflict, IllegalArgumentException.class));

    JpaEntityMetadataResolver resolver = context.getBean(JpaEntityMetadataResolver.class);
    EntityMetadata<Product> before = resolver.resolve(entityManagerFactory, Product.class);
    products.bulkInsert(TENANT_A, List.of(new Product(873L, "CACHE-A", "cache")));
    products.bulkInsert(TENANT_B, List.of(new Product(874L, "CACHE-B", "cache")));
    EntityMetadata<Product> after = resolver.resolve(entityManagerFactory, Product.class);
    assertSame(before, after);
    assertEquals(TableName.of("phase9_product"), after.table());
  }

  @Test
  void targetAwareInsertPreservesGeneratedIdsAndHibernateMappingConversions() {
    GeneratedProduct generated = new GeneratedProduct(null, "GENERATED");
    assertEquals(
        new BulkWriteResult(1, 1),
        generatedProducts.bulkInsert(
            TableName.of("tenant_a", "ms4_generated_product"), List.of(generated)));
    assertEquals(null, generated.id);

    Category category = categories.saveAndFlush(new Category(881L, "category"));
    FeatureProduct feature =
        new FeatureProduct(
            882L,
            "FEATURE",
            new Label("converted"),
            ProductState.ACTIVE,
            new Location("Madrid", "28001"),
            category);
    TableName featureTarget = TableName.of("tenant_a", "ms4_feature_product");
    featureProducts.bulkInsert(featureTarget, List.of(feature));

    FeatureProduct found =
        featureProducts
            .findAllByBulkKey(List.of("FEATURE"), featureSkuMetadata(), featureTarget)
            .get(0);
    assertEquals(new Label("converted"), found.label);
    assertEquals(ProductState.ACTIVE, found.state);
    assertEquals("Madrid", found.location.city);
    assertEquals(881L, targetLong("tenant_a.ms4_feature_product", "category_id"));
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
    assertThrows(
        IllegalStateException.class,
        () ->
            direct.bulkInsert(
                List.of(new Product(651L, "DIRECT-TARGET", "direct target")),
                BulkInsertOptions.defaults(),
                TENANT_A));
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

  interface GeneratedProductRepository
      extends JpaRepository<GeneratedProduct, Long>,
          PostgresBulkRepository<GeneratedProduct, Long> {}

  interface StaticProductRepository
      extends JpaRepository<StaticProduct, Long>, PostgresBulkRepository<StaticProduct, Long> {}

  interface FeatureProductRepository
      extends JpaRepository<FeatureProduct, Long>, PostgresBulkRepository<FeatureProduct, Long> {}

  interface CategoryRepository extends JpaRepository<Category, Long> {}

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

  @Entity
  @Table(name = "ms4_generated_product")
  static class GeneratedProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String sku;

    GeneratedProduct() {}

    GeneratedProduct(Long id, String sku) {
      this.id = id;
      this.sku = sku;
    }
  }

  @Entity
  @Table(schema = "public", name = "ms4_static_product")
  static class StaticProduct {
    @Id Long id;

    @Column(nullable = false, unique = true)
    String sku;

    @Column(nullable = false)
    String name;

    StaticProduct() {}

    StaticProduct(Long id, String sku, String name) {
      this.id = id;
      this.sku = sku;
      this.name = name;
    }
  }

  @Entity
  @Table(name = "ms4_category")
  static class Category {
    @Id Long id;

    @Column(nullable = false)
    String name;

    Category() {}

    Category(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  @Entity
  @Table(name = "ms4_feature_product")
  static class FeatureProduct {
    @Id Long id;

    @Column(nullable = false, unique = true)
    String sku;

    @Convert(converter = LabelConverter.class)
    @Column(nullable = false)
    Label label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProductState state;

    @Embedded Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    Category category;

    FeatureProduct() {}

    FeatureProduct(
        Long id,
        String sku,
        Label label,
        ProductState state,
        Location location,
        Category category) {
      this.id = id;
      this.sku = sku;
      this.label = label;
      this.state = state;
      this.location = location;
      this.category = category;
    }
  }

  @Embeddable
  static class Location {
    @Column(name = "location_city", nullable = false)
    String city;

    @Column(name = "location_postcode", nullable = false)
    String postcode;

    Location() {}

    Location(String city, String postcode) {
      this.city = city;
      this.postcode = postcode;
    }
  }

  enum ProductState {
    ACTIVE,
    INACTIVE
  }

  record Label(String value) {}

  @Converter
  public static class LabelConverter implements AttributeConverter<Label, String> {

    @Override
    public String convertToDatabaseColumn(Label attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public Label convertToEntityAttribute(String value) {
      return value == null ? null : new Label(value);
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

  private static BulkKeyMetadata<String> staticSkuMetadata() {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));
  }

  private static BulkKeyMetadata<String> featureSkuMetadata() {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));
  }

  private static String targetRoundTrip(
      CountDownLatch ready, CountDownLatch start, TableName target, long id, String sku)
      throws Exception {
    ready.countDown();
    assertTrue(start.await(20, TimeUnit.SECONDS));
    products.bulkInsert(target, List.of(new Product(id, sku, "parallel target")));
    return products.findAllByBulkKey(List.of(sku), skuMetadata(), target).get(0).sku;
  }

  private static long targetCount(String qualifiedTable) {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT count(*) FROM " + qualifiedTable)) {
      result.next();
      return result.getLong(1);
    } catch (SQLException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static long targetLong(String qualifiedTable, String column) {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT " + column + " FROM " + qualifiedTable)) {
      result.next();
      return result.getLong(1);
    } catch (SQLException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static long backendPid(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var result = statement.executeQuery("select pg_backend_pid()")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void executeSql(String... statements) {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    } catch (SQLException failure) {
      throw new IllegalStateException(failure);
    }
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
