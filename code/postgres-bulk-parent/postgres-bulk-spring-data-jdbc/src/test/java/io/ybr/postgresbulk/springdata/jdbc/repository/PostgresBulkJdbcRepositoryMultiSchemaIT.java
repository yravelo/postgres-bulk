package io.ybr.postgresbulk.springdata.jdbc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresBulkJdbcRepositoryMultiSchemaIT {

  private static final TableName TENANT_A = TableName.of("tenant_a", "repository_products");
  private static final TableName TENANT_B = TableName.of("tenant_b", "repository_products");
  private static final UUID PARENT_ID = UUID.fromString("527a0525-b6d7-46c6-abd1-6415557ea870");

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_repository_multi_schema")
          .withUsername("postgres_bulk_repository_multi_schema")
          .withPassword("postgres_bulk_repository_multi_schema");

  private static AnnotationConfigApplicationContext context;
  private static JdbcOperations jdbc;
  private static ProductRepository products;
  private static RichProductRepository richProducts;
  private static StaticProductRepository staticProducts;
  private static QuotedProductRepository quotedProducts;
  private static MissingProductRepository missingProducts;
  private static PlatformTransactionManager transactionManager;
  private static SpringDataJdbcEntityMetadataResolver metadataResolver;

  @BeforeAll
  static void createContext() {
    context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    jdbc = context.getBean(JdbcOperations.class);
    products = context.getBean(ProductRepository.class);
    richProducts = context.getBean(RichProductRepository.class);
    staticProducts = context.getBean(StaticProductRepository.class);
    quotedProducts = context.getBean(QuotedProductRepository.class);
    missingProducts = context.getBean(MissingProductRepository.class);
    transactionManager = context.getBean(PlatformTransactionManager.class);
    metadataResolver = context.getBean(SpringDataJdbcEntityMetadataResolver.class);

    jdbc.execute("CREATE SCHEMA tenant_a");
    jdbc.execute("CREATE SCHEMA tenant_b");
    jdbc.execute("CREATE SCHEMA static_schema");
    jdbc.execute("CREATE SCHEMA \"Bulk Schema\"");
    for (String schema : List.of("tenant_a", "tenant_b")) {
      jdbc.execute(
          "CREATE TABLE "
              + schema
              + ".repository_products ("
              + "id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
              + "code text NOT NULL UNIQUE, category text NOT NULL, amount numeric(12,2), "
              + "backend_pid integer NOT NULL DEFAULT pg_backend_pid())");
      jdbc.execute(
          "CREATE TABLE "
              + schema
              + ".rich_products ("
              + "id uuid PRIMARY KEY, code text NOT NULL UNIQUE, status text NOT NULL, "
              + "amount numeric(12,2), address_city text, address_geo_latitude double precision, "
              + "address_geo_longitude double precision, parent_id uuid)");
    }
    jdbc.execute(
        "CREATE TABLE static_schema.static_products (id bigint PRIMARY KEY, code text NOT NULL)");
    jdbc.execute(
        "CREATE TABLE \"Bulk Schema\".\"Order Rows\" "
            + "(\"Id\" bigint PRIMARY KEY, \"Value\" text NOT NULL UNIQUE)");
  }

  @BeforeEach
  void cleanTables() {
    jdbc.execute(
        "TRUNCATE tenant_a.repository_products, tenant_b.repository_products, "
            + "tenant_a.rich_products, tenant_b.rich_products, "
            + "static_schema.static_products, \"Bulk Schema\".\"Order Rows\" RESTART IDENTITY");
  }

  @AfterAll
  static void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void sameRepositoryInsertsAndLooksUpIsolatedTargetsOnSamePhysicalConnection() {
    int repositoryIdentity = System.identityHashCode(products);
    TransactionTemplate transaction = transaction();
    transaction.executeWithoutResult(
        status -> {
          Integer pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          assertEquals(
              new BulkWriteResult(2, 2),
              products.bulkInsert(
                  List.of(product(1L, "a-one"), product(2L, "a-two")),
                  BulkInsertOptions.ofBatchSize(1),
                  TENANT_A));
          assertEquals(
              new BulkWriteResult(1, 1),
              products.bulkInsert(TENANT_B, List.of(product(3L, "b-one"))));

          assertEquals(
              List.of("a-one", "a-two"),
              products.findAllByBulkKey(List.of("a-two", "a-one"), codeKey(), TENANT_A).stream()
                  .map(Product::code)
                  .sorted()
                  .toList());
          assertEquals(
              List.of("b-one"),
              products.findAllByBulkKey(List.of("a-one", "b-one"), codeKey(), TENANT_B).stream()
                  .map(Product::code)
                  .toList());
          assertEquals(
              List.of(pid),
              jdbc.queryForList(
                  "SELECT DISTINCT backend_pid FROM tenant_a.repository_products "
                      + "UNION SELECT DISTINCT backend_pid FROM tenant_b.repository_products",
                  Integer.class));
          assertEquals(0L, temporaryTables());
        });

    assertEquals(repositoryIdentity, System.identityHashCode(products));
    assertEquals(2L, count("tenant_a.repository_products"));
    assertEquals(1L, count("tenant_b.repository_products"));
  }

  @Test
  void generatedAssignedMixedIdsAndPoolReuseRemainTargetIndependent() {
    Product generatedA = product(null, "generated-a");
    products.bulkInsert(TENANT_A, List.of(generatedA));
    assertNull(generatedA.id());
    Integer pidA =
        jdbc.queryForObject(
            "SELECT backend_pid FROM tenant_a.repository_products WHERE code = 'generated-a'",
            Integer.class);

    Product generatedB = product(null, "generated-b");
    products.bulkInsert(TENANT_B, List.of(generatedB));
    Integer pidB =
        jdbc.queryForObject(
            "SELECT backend_pid FROM tenant_b.repository_products WHERE code = 'generated-b'",
            Integer.class);
    assertEquals(pidA, pidB);
    assertNull(generatedB.id());

    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () ->
            products.bulkInsert(
                List.of(product(null, "mixed-generated"), product(90L, "mixed-assigned")),
                BulkInsertOptions.defaults(),
                TENANT_A));
  }

  @Test
  void concurrentCallsOnOneProxyDoNotLeakTargets() {
    CompletableFuture<Void> tenantA =
        CompletableFuture.runAsync(
            () ->
                products.bulkInsert(
                    TENANT_A,
                    List.of(product(101L, "concurrent-a-1"), product(102L, "concurrent-a-2"))));
    CompletableFuture<Void> tenantB =
        CompletableFuture.runAsync(
            () ->
                products.bulkInsert(
                    TENANT_B,
                    List.of(product(201L, "concurrent-b-1"), product(202L, "concurrent-b-2"))));
    CompletableFuture.allOf(tenantA, tenantB).join();

    assertEquals(List.of("concurrent-a-1", "concurrent-a-2"), codes(TENANT_A, "concurrent"));
    assertEquals(List.of("concurrent-b-1", "concurrent-b-2"), codes(TENANT_B, "concurrent"));
  }

  @Test
  void multiSchemaCommitRollbackRequiredAndRequiresNewPreserveBoundaries() {
    transaction()
        .executeWithoutResult(
            status -> {
              products.bulkInsert(TENANT_A, List.of(product(301L, "commit-a")));
              products.bulkInsert(TENANT_B, List.of(product(302L, "commit-b")));
            });
    assertEquals(1L, countWhere("tenant_a.repository_products", "code = 'commit-a'"));
    assertEquals(1L, countWhere("tenant_b.repository_products", "code = 'commit-b'"));

    transaction()
        .executeWithoutResult(
            status -> {
              products.bulkInsert(TENANT_A, List.of(product(303L, "rollback-a")));
              products.bulkInsert(TENANT_B, List.of(product(304L, "rollback-b")));
              status.setRollbackOnly();
            });
    assertEquals(0L, countWhere("tenant_a.repository_products", "code = 'rollback-a'"));
    assertEquals(0L, countWhere("tenant_b.repository_products", "code = 'rollback-b'"));

    TransactionTemplate inner = transaction();
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction()
        .executeWithoutResult(
            outer -> {
              products.bulkInsert(TENANT_A, List.of(product(305L, "outer-a")));
              inner.executeWithoutResult(
                  ignored -> products.bulkInsert(TENANT_B, List.of(product(306L, "inner-b"))));
              outer.setRollbackOnly();
            });
    assertEquals(0L, countWhere("tenant_a.repository_products", "code = 'outer-a'"));
    assertEquals(1L, countWhere("tenant_b.repository_products", "code = 'inner-b'"));
  }

  @Test
  void nestedReadOnlyAndAbortedTransactionsKeepExistingSemantics() {
    TransactionTemplate nested = transaction();
    nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    transaction()
        .executeWithoutResult(
            outer -> {
              products.bulkInsert(TENANT_A, List.of(product(401L, "outer-kept")));
              assertThrows(
                  DeliberateFailure.class,
                  () ->
                      nested.executeWithoutResult(
                          inner -> {
                            products.bulkInsert(TENANT_B, List.of(product(402L, "nested-gone")));
                            products.findAllByBulkKey(List.of("nested-gone"), codeKey(), TENANT_B);
                            throw new DeliberateFailure();
                          }));
              assertEquals(0L, temporaryTables());
            });
    assertEquals(1L, countWhere("tenant_a.repository_products", "code = 'outer-kept'"));
    assertEquals(0L, countWhere("tenant_b.repository_products", "code = 'nested-gone'"));

    TransactionTemplate readOnly = transaction();
    readOnly.setReadOnly(true);
    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () ->
            readOnly.executeWithoutResult(
                status -> products.bulkInsert(TENANT_A, List.of(product(403L, "read-only")))));
    assertThrows(
        InvalidDataAccessApiUsageException.class,
        () ->
            readOnly.execute(
                status -> products.findAllByBulkKey(List.of("outer-kept"), codeKey(), TENANT_A)));

    products.bulkInsert(TENANT_A, List.of(product(404L, "duplicate")));
    AtomicReference<String> abortedState = new AtomicReference<>();
    transaction()
        .executeWithoutResult(
            status -> {
              assertThrows(
                  BulkException.class,
                  () -> products.bulkInsert(TENANT_A, List.of(product(405L, "duplicate"))));
              DataAccessException aborted =
                  assertThrows(
                      DataAccessException.class,
                      () -> jdbc.queryForObject("SELECT 1", Integer.class));
              abortedState.set(sqlState(aborted));
              status.setRollbackOnly();
            });
    assertEquals("25P02", abortedState.get());
  }

  @Test
  void convertersEnumNestedEmbeddedsAndAggregateReferenceWorkPerTarget() {
    RichProduct rich =
        new RichProduct(
            UUID.fromString("4f0919a8-652f-4a17-b090-e9d5395b3598"),
            "rich-a",
            Status.ACTIVE,
            new Money("19.75"),
            new Address("Madrid", new GeoPoint(40.4, -3.7)),
            AggregateReference.to(PARENT_ID));
    RichProduct nullable =
        new RichProduct(
            UUID.fromString("1943f91b-98c9-43ce-9899-eecb4c38087a"),
            "rich-null",
            Status.DISABLED,
            new Money("2.50"),
            null,
            AggregateReference.to(PARENT_ID));
    TableName target = TableName.of("tenant_a", "rich_products");

    richProducts.bulkInsert(target, List.of(rich, nullable));
    List<RichProduct> found =
        richProducts.findAllByBulkKey(List.of("rich-a", "rich-null"), richCodeKey(), target);
    assertEquals(
        List.of("rich-a", "rich-null"), found.stream().map(RichProduct::code).sorted().toList());
    RichProduct mapped =
        found.stream().filter(row -> row.code().equals("rich-a")).findFirst().orElseThrow();
    assertEquals(Status.ACTIVE, mapped.status());
    assertEquals(new Money("19.75"), mapped.amount());
    assertEquals("Madrid", mapped.address().city());
    assertEquals(new GeoPoint(40.4, -3.7), mapped.address().geo());
    assertEquals(PARENT_ID, mapped.parent().getId());
    assertNull(
        found.stream()
            .filter(row -> row.code().equals("rich-null"))
            .findFirst()
            .orElseThrow()
            .address());
    assertEquals(0L, count("tenant_b.rich_products"));
  }

  @Test
  void emptyTargetsStaticConflictsAndCacheIdentityAreDeterministic() {
    EntityMetadata<Product> before = metadataResolver.resolve(Product.class);
    assertEquals(
        BulkWriteResult.empty(),
        products.bulkInsert(TableName.of("missing_schema", "repository_products"), List.of()));
    assertEquals(List.of(), products.findAllByBulkKey(List.of(), codeKey(), TENANT_A));
    assertThrows(
        IllegalArgumentException.class,
        () -> products.bulkInsert(TableName.of("repository_products"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            products.bulkInsert(
                List.of(), BulkInsertOptions.defaults(), TableName.of("tenant_a", "other_table")));

    TableName staticTarget = TableName.of("static_schema", "static_products");
    staticProducts.bulkInsert(staticTarget, List.of(new StaticProduct(1L, "same")));
    assertEquals(
        List.of(new StaticProduct(1L, "same")),
        staticProducts.findAllByBulkKey(List.of("same"), staticCodeKey(), staticTarget));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            staticProducts.bulkInsert(
                TableName.of("tenant_a", "static_products"),
                List.of(new StaticProduct(2L, "schema-conflict"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            staticProducts.bulkInsert(
                TableName.of("static_schema", "different_table"),
                List.of(new StaticProduct(3L, "table-conflict"))));

    products.bulkInsert(TENANT_A, List.of(product(501L, "cache-a")));
    products.bulkInsert(TENANT_B, List.of(product(502L, "cache-b")));
    EntityMetadata<Product> after = metadataResolver.resolve(Product.class);
    assertSame(before, after);
    assertEquals(TableName.of("repository_products"), after.table());
  }

  @Test
  void quotedTargetAndMissingRelationsPreserveIdentifiersSqlStateAndCleanup() {
    TableName quoted = TableName.of("Bulk Schema", "Order Rows");
    QuotedProduct row = new QuotedProduct(1L, "quoted");
    quotedProducts.bulkInsert(quoted, List.of(row));
    assertEquals(
        List.of(row), quotedProducts.findAllByBulkKey(List.of("quoted"), quotedValueKey(), quoted));

    BulkException missingSchema =
        assertThrows(
            BulkException.class,
            () ->
                products.bulkInsert(
                    TableName.of("missing_schema", "repository_products"),
                    List.of(product(601L, "missing-schema"))));
    assertEquals("3F000", sqlState(missingSchema));
    BulkException missingTable =
        assertThrows(
            BulkException.class,
            () ->
                missingProducts.findAllByBulkKey(
                    List.of("missing"),
                    missingValueKey(),
                    TableName.of("tenant_a", "missing_products")));
    assertEquals("42P01", sqlState(missingTable));

    transaction()
        .executeWithoutResult(
            status -> {
              products.findAllByBulkKey(List.of("absent"), codeKey(), TENANT_A);
              assertEquals(0L, temporaryTables());
              status.setRollbackOnly();
            });
    transaction().executeWithoutResult(status -> assertEquals(0L, temporaryTables()));
  }

  private static Product product(Long id, String code) {
    return new Product(id, code, "tools", new Money("1.00"));
  }

  private static List<String> codes(TableName target, String prefix) {
    return products
        .findAllByBulkKey(
            List.of(prefix + "-a-1", prefix + "-a-2", prefix + "-b-1", prefix + "-b-2"),
            codeKey(),
            target)
        .stream()
        .map(Product::code)
        .sorted()
        .toList();
  }

  private static BulkKeyMetadata<String> codeKey() {
    return stringKey("code");
  }

  private static BulkKeyMetadata<String> richCodeKey() {
    return stringKey("code");
  }

  private static BulkKeyMetadata<String> staticCodeKey() {
    return stringKey("code");
  }

  private static BulkKeyMetadata<String> missingValueKey() {
    return stringKey("value");
  }

  private static BulkKeyMetadata<String> quotedValueKey() {
    return stringKey("Value");
  }

  private static BulkKeyMetadata<String> stringKey(String column) {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of(column, String.class, value -> value)));
  }

  private static TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
  }

  private static long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private static long countWhere(String table, String condition) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + condition, Long.class);
  }

  private static long temporaryTables() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM pg_catalog.pg_class "
            + "WHERE relnamespace = pg_my_temp_schema() AND relname LIKE 'pgbulk_keys_%'",
        Long.class);
  }

  private static String sqlState(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
        return sqlException.getSQLState();
      }
    }
    throw new AssertionError("No SQLException with SQLState", failure);
  }

  interface ProductRepository
      extends CrudRepository<Product, Long>, PostgresBulkJdbcRepository<Product> {}

  interface RichProductRepository
      extends CrudRepository<RichProduct, UUID>, PostgresBulkJdbcRepository<RichProduct> {}

  interface StaticProductRepository
      extends CrudRepository<StaticProduct, Long>, PostgresBulkJdbcRepository<StaticProduct> {}

  interface QuotedProductRepository
      extends CrudRepository<QuotedProduct, Long>, PostgresBulkJdbcRepository<QuotedProduct> {}

  interface MissingProductRepository
      extends CrudRepository<MissingProduct, Long>, PostgresBulkJdbcRepository<MissingProduct> {}

  @Table("repository_products")
  record Product(@Id Long id, String code, String category, Money amount) {}

  @Table("rich_products")
  record RichProduct(
      @Id UUID id,
      String code,
      Status status,
      Money amount,
      @Embedded.Nullable(prefix = "address_") Address address,
      @Column("parent_id") AggregateReference<Parent, UUID> parent) {}

  record Address(String city, @Embedded.Nullable(prefix = "geo_") GeoPoint geo) {}

  record GeoPoint(Double latitude, Double longitude) {}

  record Parent(@Id UUID id) {}

  enum Status {
    ACTIVE,
    DISABLED
  }

  @Table(name = "static_products", schema = "static_schema")
  record StaticProduct(@Id Long id, String code) {}

  @Table(name = "Order Rows", schema = "Bulk Schema")
  record QuotedProduct(@Id @Column("Id") Long id, @Column("Value") String value) {}

  @Table("missing_products")
  record MissingProduct(@Id Long id, String value) {}

  record Money(BigDecimal value) {
    Money(String value) {
      this(new BigDecimal(value));
    }
  }

  @WritingConverter
  enum MoneyWritingConverter implements Converter<Money, BigDecimal> {
    INSTANCE;

    @Override
    public BigDecimal convert(Money source) {
      return source.value();
    }
  }

  @ReadingConverter
  enum MoneyReadingConverter implements Converter<BigDecimal, Money> {
    INSTANCE;

    @Override
    public Money convert(BigDecimal source) {
      return new Money(source);
    }
  }

  private static final class DeliberateFailure extends RuntimeException {}

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EnableJdbcRepositories(
      basePackageClasses = PostgresBulkJdbcRepositoryMultiSchemaIT.class,
      considerNestedRepositories = true)
  static class TestConfiguration extends AbstractJdbcConfiguration {

    @Bean(destroyMethod = "close")
    DataSource dataSource() {
      HikariConfig pool = new HikariConfig();
      pool.setJdbcUrl(POSTGRES.getJdbcUrl());
      pool.setUsername(POSTGRES.getUsername());
      pool.setPassword(POSTGRES.getPassword());
      pool.setMaximumPoolSize(4);
      pool.setMinimumIdle(0);
      return new HikariDataSource(pool);
    }

    @Bean
    NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    JdbcOperations jdbcOperations(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new JdbcTransactionManager(dataSource);
    }

    @Bean
    SpringDataJdbcEntityMetadataResolver postgresBulkJdbcMetadataResolver(
        JdbcConverter converter, JdbcCustomConversions conversions) {
      return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
    }

    @Override
    protected List<?> userConverters() {
      return List.of(MoneyWritingConverter.INSTANCE, MoneyReadingConverter.INSTANCE);
    }
  }
}
