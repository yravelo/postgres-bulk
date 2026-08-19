package io.github.postgresbulk.starter.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.postgresbulk.autoconfigure.PostgresBulkProperties;
import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import io.github.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.ObservationRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
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
@AutoConfigureObservability
@Testcontainers(disabledWithoutDocker = true)
class PostgresBulkStarterIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"));

  @Autowired private ProductRepository products;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JpaEntityMetadataResolver metadataResolver;
  @Autowired private PostgresBulkProperties properties;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private ObservationRegistry observationRegistry;
  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    registry.add("spring.datasource.hikari.connection-timeout", () -> "5000");
  }

  @BeforeEach
  void cleanTable() {
    products.deleteAllInBatch();
  }

  @Test
  void starterAutoConfiguresInsertAndTypedLookup() {
    assertThat(metadataResolver).isNotNull();
    assertThat(properties.enabled()).isTrue();
    assertThat(properties.observability().enabled()).isTrue();
    assertThat(observationRegistry.isNoop()).isFalse();

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
  void actuatorInfrastructureReceivesOneObservationAndFinalTotalsPerCall() {
    long insertSuccessBefore = operationCount("insert", "success");
    long lookupSuccessBefore = operationCount("lookup", "success");
    double insertRowsBefore = counterValue("postgres.bulk.rows", "insert");
    double lookupRowsBefore = counterValue("postgres.bulk.rows", "lookup");
    double batchesBefore = counterValue("postgres.bulk.batches", "insert");

    products.bulkInsert(
        List.of(new Product(20L, "OBS-1", "one"), new Product(21L, "OBS-2", "two")),
        BulkInsertOptions.ofBatchSize(1));
    List<Product> found = products.findAllByBulkKey(List.of("OBS-2", "missing"), skuMetadata());

    assertThat(found).hasSize(1);
    assertThat(operationCount("insert", "success")).isEqualTo(insertSuccessBefore + 1);
    assertThat(operationCount("lookup", "success")).isEqualTo(lookupSuccessBefore + 1);
    assertThat(counterValue("postgres.bulk.rows", "insert")).isEqualTo(insertRowsBefore + 2);
    assertThat(counterValue("postgres.bulk.rows", "lookup")).isEqualTo(lookupRowsBefore + 1);
    assertThat(counterValue("postgres.bulk.batches", "insert")).isEqualTo(batchesBefore + 2);
    assertThat(
            operationTimer("insert", "success")
                .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
        .isPositive();
  }

  @Test
  void outerRollbackRemainsAuthoritative() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    long successBefore = operationCount("insert", "success");
    double rowsBefore = counterValue("postgres.bulk.rows", "insert");

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      products.bulkInsert(List.of(new Product(3L, "ROLLBACK", "rollback")));
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    assertThat(products.findById(3L)).isEmpty();
    assertThat(operationCount("insert", "success")).isEqualTo(successBefore + 1);
    assertThat(counterValue("postgres.bulk.rows", "insert")).isEqualTo(rowsBefore + 1);
  }

  @Test
  void readOnlyTransactionIsNotSilentlyChanged() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    long errorsBefore = operationCount("insert", "error");
    long lookupErrorsBefore = operationCount("lookup", "error");
    double rowsBefore = counterValue("postgres.bulk.rows", "insert");
    double lookupRowsBefore = counterValue("postgres.bulk.rows", "lookup");

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status ->
                        products.bulkInsert(List.of(new Product(4L, "READ-ONLY", "read-only")))))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("read-only");
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> products.findAllByBulkKey(List.of("READ-ONLY"), skuMetadata())))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("read-only");
    assertThat(operationCount("insert", "error")).isEqualTo(errorsBefore + 1);
    assertThat(operationCount("lookup", "error")).isEqualTo(lookupErrorsBefore + 1);
    assertThat(counterValue("postgres.bulk.rows", "insert")).isEqualTo(rowsBefore);
    assertThat(counterValue("postgres.bulk.rows", "lookup")).isEqualTo(lookupRowsBefore);
    assertThat(
            meterRegistry
                .find("postgres.bulk.operation")
                .tags("operation", "insert", "outcome", "error")
                .timers())
        .allSatisfy(timer -> assertThat(timer.getId().getTag("error")).isEqualTo("error"));
  }

  @Test
  void databaseFailureIsObservedWithoutPublishingPartialRows() {
    long errorsBefore = operationCount("insert", "error");
    double rowsBefore = counterValue("postgres.bulk.rows", "insert");
    double batchesBefore = counterValue("postgres.bulk.batches", "insert");

    assertThatThrownBy(
            () ->
                products.bulkInsert(
                    List.of(
                        new Product(30L, "OBS-DUP", "one"), new Product(31L, "OBS-DUP", "two"))))
        .isInstanceOf(BulkException.class);

    assertThat(operationCount("insert", "error")).isEqualTo(errorsBefore + 1);
    assertThat(counterValue("postgres.bulk.rows", "insert")).isEqualTo(rowsBefore);
    assertThat(counterValue("postgres.bulk.batches", "insert")).isEqualTo(batchesBefore);
    assertThat(products.findById(30L)).isEmpty();
  }

  @Test
  void singleConnectionPoolRemainsCleanAcrossSuccessAndRollbackFailures() throws Exception {
    ConnectionState before = connectionState();
    BulkKeyMetadata<String> sku = skuMetadata();

    products.bulkInsert(List.of(new Product(10L, "POOL-1", "insert success")));
    assertThat(products.findAllByBulkKey(List.of("POOL-1"), sku)).hasSize(1);

    assertThatThrownBy(
            () -> products.findAllByBulkKey(Arrays.asList("POOL-1", null, "POOL-2"), sku))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                products.bulkInsert(
                    List.of(
                        new Product(11L, "POOL-DUP", "one"), new Product(12L, "POOL-DUP", "two"))))
        .isInstanceOf(BulkException.class);

    ConnectionState after = connectionState();
    assertThat(after.backendPid()).isEqualTo(before.backendPid());
    assertThat(after.autoCommit()).isTrue();
    assertThat(after.readOnly()).isFalse();
    assertThat(after.isolation()).isEqualTo(before.isolation());
    assertThat(after.schema()).isEqualTo(before.schema());
    assertThat(after.searchPath()).isEqualTo(before.searchPath());
    assertThat(after.temporaryTables()).isZero();
    assertThat(after.probe()).isEqualTo(1);

    assertThat(products.bulkInsert(List.of(new Product(13L, "POOL-2", "after failures"))))
        .isEqualTo(new BulkWriteResult(1, 1));
  }

  @Test
  void oneHundredSequentialOperationsDoNotAccumulateSessionState() throws Exception {
    for (long id = 100; id < 200; id++) {
      String sku = "SEQUENTIAL-" + id;
      products.bulkInsert(List.of(new Product(id, sku, "sequential")));
      if (id % 10 == 0) {
        assertThat(products.findAllByBulkKey(List.of(sku), skuMetadata())).hasSize(1);
      }
    }

    assertThat(products.count()).isEqualTo(100);
    ConnectionState state = connectionState();
    assertThat(state.temporaryTables()).isZero();
    assertThat(state.autoCommit()).isTrue();
    assertThat(state.readOnly()).isFalse();
    assertThat(state.probe()).isEqualTo(1);
  }

  @Test
  void terminatedBackendIsDiscardedAndNextBulkOperationUsesHealthyConnection() throws Exception {
    long terminatedPid;
    RuntimeException visibleFailure;
    try (Connection pooled = dataSource.getConnection()) {
      pooled.setAutoCommit(false);
      terminatedPid = scalarLong(pooled, "SELECT pg_backend_pid()");
      try (Connection killer =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          Statement statement = killer.createStatement()) {
        statement.execute("SELECT pg_terminate_backend(" + terminatedPid + ")");
      }

      EntityMetadata<Product> metadata =
          metadataResolver.resolve(entityManagerFactory, Product.class);
      PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);
      visibleFailure =
          org.junit.jupiter.api.Assertions.assertThrows(
              RuntimeException.class,
              () ->
                  operations.bulkInsert(
                      pooled, List.of(new Product(300L, "TERMINATED", "must fail"))));
      SQLException rollbackFailure =
          org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, pooled::rollback);
      visibleFailure.addSuppressed(rollbackFailure);
    }

    SQLException sqlFailure = findCause(visibleFailure, SQLException.class);
    assertNotNull(sqlFailure);
    assertThat(sqlFailure.getSQLState()).isNotBlank();

    products.bulkInsert(List.of(new Product(301L, "HEALTHY", "replacement connection")));
    ConnectionState replacement = connectionState();
    assertThat(replacement.backendPid()).isNotEqualTo(terminatedPid);
    assertThat(replacement.probe()).isEqualTo(1);
    assertThat(products.findById(301L)).isPresent();
  }

  private BulkKeyMetadata<String> skuMetadata() {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of("sku", String.class, value -> value)));
  }

  private long operationCount(String operation, String outcome) {
    return meterRegistry
        .find("postgres.bulk.operation")
        .tags("operation", operation, "outcome", outcome)
        .timers()
        .stream()
        .mapToLong(Timer::count)
        .sum();
  }

  private Timer operationTimer(String operation, String outcome) {
    return meterRegistry
        .get("postgres.bulk.operation")
        .tags("operation", operation, "outcome", outcome)
        .timer();
  }

  private double counterValue(String name, String operation) {
    Counter counter = meterRegistry.find(name).tag("operation", operation).counter();
    return counter == null ? 0 : counter.count();
  }

  private ConnectionState connectionState() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return new ConnectionState(
          scalarLong(connection, "SELECT pg_backend_pid()"),
          connection.getAutoCommit(),
          connection.isReadOnly(),
          connection.getTransactionIsolation(),
          connection.getSchema(),
          scalarString(connection, "SHOW search_path"),
          scalarLong(
              connection,
              "SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace = pg_my_temp_schema() AND relname LIKE 'pgbulk_keys_%'"),
          scalarLong(connection, "SELECT 1"));
    }
  }

  private static long scalarLong(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String scalarString(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
    }
    return null;
  }

  private record ConnectionState(
      long backendPid,
      boolean autoCommit,
      boolean readOnly,
      int isolation,
      String schema,
      String searchPath,
      long temporaryTables,
      long probe) {}

  private static final class DeliberateRollback extends RuntimeException {}
}
