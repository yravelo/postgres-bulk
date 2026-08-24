package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.PooledConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGConnectionPoolDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresBulkJdbcOperationsMultiSchemaLookupIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_ms3")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  @AfterEach
  void cleanDatabase() throws SQLException {
    execute(
        "DROP SCHEMA IF EXISTS tenant_a CASCADE",
        "DROP SCHEMA IF EXISTS tenant_b CASCADE",
        "DROP SCHEMA IF EXISTS tenant_empty CASCADE",
        "DROP SCHEMA IF EXISTS \"Tenant Space\" CASCADE",
        "DROP SCHEMA IF EXISTS restricted_target CASCADE",
        "DROP TABLE IF EXISTS public.product");
    execute("DROP ROLE IF EXISTS ms3_no_select");
  }

  @Test
  void looksUpAAndBWithTheSameMetadataAndPreservesInsertIsolation() throws Exception {
    createProductSchemas();
    EntityMetadata<Product> metadata = productMetadata();
    TableName mappedTable = metadata.table();
    List<ColumnMetadata<Product>> mappedColumns = metadata.insertColumns();
    BulkKeyMetadata<Integer> keys = simpleKeys();
    List<ColumnMetadata<Integer>> keyComponents = keys.components();
    PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);

    try (Connection connection = manualConnection()) {
      operations.bulkInsert(
          connection,
          List.of(new Product(1, "A", "A-1"), new Product(1, "A", "A-1-duplicate")),
          BulkInsertOptions.defaults(),
          target("tenant_a"));
      operations.bulkInsert(
          connection,
          List.of(new Product(1, "B", "B-1"), new Product(2, "B", "B-2")),
          BulkInsertOptions.defaults(),
          target("tenant_b"));

      assertEquals(
          List.of("A-1", "A-1-duplicate"),
          lookupValues(operations, connection, List.of(1, 1, 99), keys, target("tenant_a")));
      assertEquals(
          List.of("B-1", "B-2"),
          lookupValues(operations, connection, List.of(1, 2, 2, 99), keys, target("tenant_b")));
      assertEquals(
          List.of(), lookupValues(operations, connection, List.of(2), keys, target("tenant_a")));
      connection.commit();
    }

    assertSame(mappedTable, metadata.table());
    assertSame(mappedColumns, metadata.insertColumns());
    assertSame(keyComponents, keys.components());
  }

  @Test
  void reusesOnePooledPhysicalConnectionAThenBWithoutTargetStateLeakage() throws Exception {
    createProductSchemas();
    execute(
        "INSERT INTO tenant_a.product VALUES (1, 'A', 'A-1')",
        "INSERT INTO tenant_b.product VALUES (1, 'B', 'B-1')");
    PostgresBulkJdbcOperations<Product> operations = operations();
    PGConnectionPoolDataSource dataSource = pooledDataSource();

    PooledConnection pooled = dataSource.getPooledConnection();
    try {
      long backend;
      String schema;
      String searchPath;
      try (Connection logicalA = pooled.getConnection()) {
        logicalA.setAutoCommit(false);
        backend = scalarLong(logicalA, "SELECT pg_backend_pid()");
        schema = logicalA.getSchema();
        searchPath = scalarString(logicalA, "SHOW search_path");
        assertEquals(
            List.of("A-1"),
            lookupValues(operations, logicalA, List.of(1), simpleKeys(), target("tenant_a")));
        logicalA.commit();
      }

      try (Connection logicalB = pooled.getConnection()) {
        logicalB.setAutoCommit(false);
        assertEquals(backend, scalarLong(logicalB, "SELECT pg_backend_pid()"));
        assertEquals(schema, logicalB.getSchema());
        assertEquals(searchPath, scalarString(logicalB, "SHOW search_path"));
        assertEquals(
            List.of("B-1"),
            lookupValues(operations, logicalB, List.of(1), simpleKeys(), target("tenant_b")));
        assertEquals(schema, logicalB.getSchema());
        assertEquals(searchPath, scalarString(logicalB, "SHOW search_path"));
        logicalB.commit();
      }
    } finally {
      pooled.close();
    }
  }

  @Test
  void executesConcurrentAAndBLookupsWithoutCrossResultsOrTemporaryCollisions() throws Exception {
    createProductSchemas();
    execute(
        "INSERT INTO tenant_a.product SELECT value, 'A', 'A-' || value FROM generate_series(1, 1000) value",
        "INSERT INTO tenant_b.product SELECT value, 'B', 'B-' || value FROM generate_series(1, 1000) value");
    PostgresBulkJdbcOperations<Product> operations = operations();
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<List<String>> tenantA =
          threads.submit(
              () -> {
                try (Connection connection = manualConnection()) {
                  return lookupValues(
                      operations, connection, range(1, 1_000), simpleKeys(), target("tenant_a"));
                }
              });
      Future<List<String>> tenantB =
          threads.submit(
              () -> {
                try (Connection connection = manualConnection()) {
                  return lookupValues(
                      operations, connection, range(1, 1_000), simpleKeys(), target("tenant_b"));
                }
              });

      assertEquals(1_000, tenantA.get(30, TimeUnit.SECONDS).size());
      assertTrue(tenantA.get().stream().allMatch(value -> value.startsWith("A-")));
      assertEquals(1_000, tenantB.get(30, TimeUnit.SECONDS).size());
      assertTrue(tenantB.get().stream().allMatch(value -> value.startsWith("B-")));
    } finally {
      threads.shutdownNow();
    }
  }

  @Test
  void looksUpMultipleSchemasInOneCommittedAndRolledBackTransaction() throws Exception {
    createProductSchemas();
    PostgresBulkJdbcOperations<Product> operations = operations();

    try (Connection connection = manualConnection()) {
      insertOne(operations, connection, "tenant_a", 1, "committed-A");
      insertOne(operations, connection, "tenant_b", 1, "committed-B");
      assertEquals(
          List.of("committed-A"),
          lookupValues(operations, connection, List.of(1), simpleKeys(), target("tenant_a")));
      assertEquals(
          List.of("committed-B"),
          lookupValues(operations, connection, List.of(1), simpleKeys(), target("tenant_b")));
      connection.commit();

      insertOne(operations, connection, "tenant_a", 2, "rolled-back-A");
      insertOne(operations, connection, "tenant_b", 2, "rolled-back-B");
      assertEquals(
          List.of("rolled-back-A"),
          lookupValues(operations, connection, List.of(2), simpleKeys(), target("tenant_a")));
      assertEquals(
          List.of("rolled-back-B"),
          lookupValues(operations, connection, List.of(2), simpleKeys(), target("tenant_b")));
      connection.rollback();
      assertEquals(0, temporaryTableCount(connection));
    }

    assertEquals(List.of("committed-A"), values("tenant_a"));
    assertEquals(List.of("committed-B"), values("tenant_b"));
  }

  @Test
  void supportsCompositeKeysAndQuotedRuntimeIdentifiers() throws Exception {
    createProductSchemas();
    execute(
        "INSERT INTO tenant_a.product VALUES (1, 'north', 'north-1'), (1, 'south', 'south-1'), (2, 'north', 'north-2')",
        "CREATE SCHEMA \"Tenant Space\"",
        "CREATE TABLE \"Tenant Space\".\"Order Items\" (\"customer Code\" text, \"a\"\"b\" integer, \"payload Value\" text)",
        "INSERT INTO \"Tenant Space\".\"Order Items\" VALUES ('C-42', 42, 'quoted')");
    PostgresBulkJdbcOperations<Product> operations = operations();
    BulkKeyMetadata<ProductKey> composite =
        BulkKeyMetadata.of(
            ProductKey.class,
            List.of(
                ColumnMetadata.of("category", String.class, ProductKey::category),
                ColumnMetadata.of("id", Integer.class, ProductKey::id)));

    try (Connection connection = manualConnection()) {
      assertEquals(
          List.of("north-2", "south-1"),
          lookupValues(
              operations,
              connection,
              List.of(new ProductKey(1, "south"), new ProductKey(2, "north")),
              composite,
              target("tenant_a")));

      EntityMetadata<QuotedProduct> quotedMetadata =
          EntityMetadata.of(
              QuotedProduct.class,
              TableName.of("Order Items"),
              List.of(
                  ColumnMetadata.of("customer Code", String.class, QuotedProduct::customer),
                  ColumnMetadata.of("a\"b", Integer.class, QuotedProduct::number),
                  ColumnMetadata.of("payload Value", String.class, QuotedProduct::payload)));
      PostgresBulkJdbcOperations<QuotedProduct> quoted =
          PostgresBulkJdbcOperations.prepare(quotedMetadata);
      BulkKeyMetadata<Integer> quotedKey =
          BulkKeyMetadata.of(
              Integer.class, List.of(ColumnMetadata.of("a\"b", Integer.class, value -> value)));
      List<String> result =
          quoted.findAllByBulkKey(
              connection,
              List.of(42),
              quotedKey,
              List.of(),
              (sameConnection, sql, copiedKeys) -> strings(sameConnection, sql, "payload Value"),
              TableName.of("Tenant Space", "Order Items"));
      assertEquals(List.of("quoted"), result);
    }
  }

  @Test
  void targetAwareEmptyAndTwentyThousandOneShotInputsPreserveStreamingContracts() throws Exception {
    createProductSchemas();
    execute(
        "INSERT INTO tenant_a.product SELECT value, 'A', 'row-' || value FROM generate_series(1, 20000) value");
    PostgresBulkJdbcOperations<Product> operations = operations();
    AtomicInteger emptyIterators = new AtomicInteger();
    Iterable<Integer> empty =
        () -> {
          if (emptyIterators.incrementAndGet() > 1) {
            throw new IllegalStateException("iterator requested more than once");
          }
          return List.<Integer>of().iterator();
        };

    try (Connection autocommit = connection()) {
      List<String> explicitEmpty = List.of("explicit-empty");
      assertSame(
          explicitEmpty,
          operations.findAllByBulkKey(
              autocommit,
              empty,
              simpleKeys(),
              explicitEmpty,
              (sameConnection, sql, copiedKeys) -> {
                throw new AssertionError("query must not run");
              },
              target("tenant_a")));
      assertEquals(1, emptyIterators.get());
      assertTrue(autocommit.getAutoCommit());
    }

    AtomicInteger generated = new AtomicInteger();
    OneShotKeys keys = new OneShotKeys(20_000, generated);
    try (Connection connection = manualConnection()) {
      long matches =
          operations.findAllByBulkKey(
              connection,
              keys,
              simpleKeys(),
              -1L,
              (sameConnection, sql, copiedKeys) -> {
                assertEquals(20_000, copiedKeys);
                return scalarLong(sameConnection, "SELECT count(*) FROM (" + sql + ") rows");
              },
              target("tenant_a"));
      assertEquals(20_000, matches);
      assertEquals(20_000, generated.get());
      assertEquals(1, keys.iteratorCalls);
    }
  }

  @Test
  void rejectsTargetConflictsBeforeKeysOrSqlAndPreservesDefaultPath() throws Exception {
    execute(
        "CREATE TABLE public.product (id integer, category text, value text)",
        "INSERT INTO public.product VALUES (1, 'public', 'default')");
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class, TableName.of("public", "product"), productMetadata().insertColumns());
    PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);
    AtomicInteger iteratorCalls = new AtomicInteger();
    Iterable<Integer> keys =
        () -> {
          iteratorCalls.incrementAndGet();
          return List.of(1).iterator();
        };

    try (Connection connection = manualConnection()) {
      assertEquals(
          List.of("default"),
          operations.findAllByBulkKey(
              connection,
              List.of(1),
              simpleKeys(),
              List.of(),
              (sameConnection, sql, copiedKeys) -> strings(sameConnection, sql, "value")));
      assertEquals(
          List.of("default"),
          lookupValues(
              operations, connection, List.of(1), simpleKeys(), TableName.of("public", "product")));

      assertThrows(
          NullPointerException.class, () -> targetLookup(operations, connection, keys, null));
      assertThrows(
          IllegalArgumentException.class,
          () -> targetLookup(operations, connection, keys, TableName.of("product")));
      assertThrows(
          IllegalArgumentException.class,
          () -> targetLookup(operations, connection, keys, TableName.of("tenant_a", "product")));
      assertThrows(
          IllegalArgumentException.class,
          () -> targetLookup(operations, connection, keys, TableName.of("public", "archive")));
      assertEquals(0, iteratorCalls.get());
      assertEquals(0, temporaryTableCount(connection));
    }
  }

  @Test
  void preservesMissingObjectPermissionAutocommitAndReadOnlyFailures() throws Exception {
    execute(
        "CREATE SCHEMA tenant_empty",
        "CREATE ROLE ms3_no_select LOGIN PASSWORD 'ms3_no_select'",
        "CREATE SCHEMA restricted_target",
        "CREATE TABLE restricted_target.product (id integer, category text, value text)",
        "INSERT INTO restricted_target.product VALUES (1, 'restricted', 'secret')",
        "GRANT USAGE ON SCHEMA restricted_target TO ms3_no_select");
    PostgresBulkJdbcOperations<Product> operations = operations();

    try (Connection missing = manualConnection()) {
      BulkException missingSchema =
          assertThrows(
              BulkException.class,
              () -> targetLookup(operations, missing, List.of(1), target("missing_schema")));
      assertEquals("42P01", sqlCause(missingSchema).getSQLState());
      assertEquals(
          "25P02",
          assertThrows(SQLException.class, () -> scalarLong(missing, "SELECT 1")).getSQLState());
      missing.rollback();

      BulkException missingTable =
          assertThrows(
              BulkException.class,
              () -> targetLookup(operations, missing, List.of(1), target("tenant_empty")));
      assertEquals("42P01", sqlCause(missingTable).getSQLState());
      missing.rollback();
      assertEquals(0, temporaryTableCount(missing));
    }

    try (Connection restricted = restrictedConnection()) {
      restricted.setAutoCommit(false);
      BulkException denied =
          assertThrows(
              BulkException.class,
              () -> targetLookup(operations, restricted, List.of(1), target("restricted_target")));
      assertEquals("42501", sqlCause(denied).getSQLState());
      restricted.rollback();
      assertFalse(restricted.isClosed());
    }

    createProductSchemas();
    try (Connection autocommit = connection()) {
      assertThrows(
          IllegalStateException.class,
          () -> targetLookup(operations, autocommit, List.of(1), target("tenant_a")));
      assertEquals(0, temporaryTableCount(autocommit));
    }
    try (Connection readOnly = manualConnection()) {
      readOnly.setReadOnly(true);
      BulkException failure =
          assertThrows(
              BulkException.class,
              () -> targetLookup(operations, readOnly, List.of(1), target("tenant_a")));
      assertEquals("25006", sqlCause(failure).getSQLState());
      assertTrue(readOnly.isReadOnly());
      readOnly.rollback();
    }
  }

  @Test
  void preservesCallbackAndSelectFailuresAndRecoversPooledBackendAfterRollback() throws Exception {
    createProductSchemas();
    execute(
        "INSERT INTO tenant_a.product VALUES (1, 'A', 'A-1')",
        "INSERT INTO tenant_b.product VALUES (1, 'B', 'B-1')");
    PostgresBulkJdbcOperations<Product> operations = operations();
    IllegalStateException callbackFailure = new IllegalStateException("materialization failed");

    try (Connection connection = manualConnection()) {
      assertSame(
          callbackFailure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  operations.findAllByBulkKey(
                      connection,
                      List.of(1),
                      simpleKeys(),
                      List.of(),
                      (sameConnection, sql, copiedKeys) -> {
                        throw callbackFailure;
                      },
                      target("tenant_a"))));
      assertEquals(0, temporaryTableCount(connection));

      BulkException selectFailure =
          assertThrows(
              BulkException.class,
              () ->
                  operations.findAllByBulkKey(
                      connection,
                      List.of(1),
                      simpleKeys(),
                      List.of(),
                      (sameConnection, sql, copiedKeys) -> {
                        try (Statement statement = sameConnection.createStatement()) {
                          statement.executeQuery("SELECT definitely_invalid_ms3_column");
                        }
                        return List.of();
                      },
                      target("tenant_a")));
      assertEquals("42703", sqlCause(selectFailure).getSQLState());
      assertTrue(selectFailure.getSuppressed().length >= 1);
      assertEquals(
          "25P02",
          assertThrows(SQLException.class, () -> scalarLong(connection, "SELECT 1")).getSQLState());
      connection.rollback();
      assertEquals(0, temporaryTableCount(connection));
    }

    PGConnectionPoolDataSource dataSource = pooledDataSource();
    PooledConnection pooled = dataSource.getPooledConnection();
    try {
      long backend;
      try (Connection failing = pooled.getConnection()) {
        failing.setAutoCommit(false);
        backend = scalarLong(failing, "SELECT pg_backend_pid()");
        List<Integer> nullKeys = Arrays.asList(1, null);
        assertThrows(
            IllegalArgumentException.class,
            () -> targetLookup(operations, failing, nullKeys, target("tenant_a")));
        assertEquals(
            "25P02",
            assertThrows(SQLException.class, () -> scalarLong(failing, "SELECT 1")).getSQLState());
        failing.rollback();
      }
      try (Connection recovered = pooled.getConnection()) {
        recovered.setAutoCommit(false);
        assertEquals(backend, scalarLong(recovered, "SELECT pg_backend_pid()"));
        assertEquals(0, temporaryTableCount(recovered));
        assertEquals(
            List.of("B-1"),
            lookupValues(operations, recovered, List.of(1), simpleKeys(), target("tenant_b")));
        recovered.commit();
      }
    } finally {
      pooled.close();
    }
  }

  private static <K> List<String> lookupValues(
      PostgresBulkJdbcOperations<Product> operations,
      Connection connection,
      Iterable<? extends K> keys,
      BulkKeyMetadata<K> keyMetadata,
      TableName target) {
    return operations.findAllByBulkKey(
        connection,
        keys,
        keyMetadata,
        List.of(),
        (sameConnection, sql, copiedKeys) -> strings(sameConnection, sql, "value"),
        target);
  }

  private static void targetLookup(
      PostgresBulkJdbcOperations<Product> operations,
      Connection connection,
      Iterable<Integer> keys,
      TableName target) {
    lookupValues(operations, connection, keys, simpleKeys(), target);
  }

  private static void insertOne(
      PostgresBulkJdbcOperations<Product> operations,
      Connection connection,
      String schema,
      int id,
      String value) {
    operations.bulkInsert(
        connection,
        List.of(new Product(id, schema, value)),
        BulkInsertOptions.defaults(),
        target(schema));
  }

  private static PostgresBulkJdbcOperations<Product> operations() {
    return PostgresBulkJdbcOperations.prepare(productMetadata());
  }

  private static EntityMetadata<Product> productMetadata() {
    return EntityMetadata.of(
        Product.class,
        TableName.of("product"),
        List.of(
            ColumnMetadata.of("id", Integer.class, Product::id),
            ColumnMetadata.of("category", String.class, Product::category),
            ColumnMetadata.of("value", String.class, Product::value)));
  }

  private static BulkKeyMetadata<Integer> simpleKeys() {
    return BulkKeyMetadata.of(
        Integer.class, List.of(ColumnMetadata.of("id", Integer.class, value -> value)));
  }

  private static TableName target(String schema) {
    return TableName.of(schema, "product");
  }

  private static List<Integer> range(int first, int last) {
    return java.util.stream.IntStream.rangeClosed(first, last).boxed().toList();
  }

  private static void createProductSchemas() throws SQLException {
    execute(
        "CREATE SCHEMA tenant_a",
        "CREATE SCHEMA tenant_b",
        "CREATE TABLE tenant_a.product (id integer, category text, value text)",
        "CREATE TABLE tenant_b.product (id integer, category text, value text)");
  }

  private static PGConnectionPoolDataSource pooledDataSource() {
    PGConnectionPoolDataSource dataSource = new PGConnectionPoolDataSource();
    dataSource.setServerNames(new String[] {POSTGRES.getHost()});
    dataSource.setPortNumbers(new int[] {POSTGRES.getFirstMappedPort()});
    dataSource.setDatabaseName(POSTGRES.getDatabaseName());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    return dataSource;
  }

  private static Connection restrictedConnection() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ms3_no_select", "ms3_no_select");
  }

  private static Connection manualConnection() throws SQLException {
    Connection connection = connection();
    connection.setAutoCommit(false);
    return connection;
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static void execute(String... statements) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    }
  }

  private static List<String> values(String schema) throws SQLException {
    try (Connection connection = connection()) {
      return strings(connection, "SELECT * FROM \"" + schema + "\".product ORDER BY id", "value");
    }
  }

  private static List<String> strings(Connection connection, String sql, String column)
      throws SQLException {
    List<String> values = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getString(column));
      }
    }
    return values.stream().sorted().toList();
  }

  private static long temporaryTableCount(Connection connection) throws SQLException {
    return scalarLong(
        connection,
        "SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace = pg_my_temp_schema() AND relname LIKE 'pgbulk_keys_%'");
  }

  private static long scalarLong(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static String scalarString(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      return result.getString(1);
    }
  }

  private static SQLException sqlCause(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException;
      }
      current = current.getCause();
    }
    throw new AssertionError("SQLException not found in cause chain", failure);
  }

  private record Product(int id, String category, String value) {}

  private record ProductKey(int id, String category) {}

  private record QuotedProduct(String customer, int number, String payload) {}

  private static final class OneShotKeys implements Iterable<Integer> {

    private final int count;
    private final AtomicInteger generated;
    private int iteratorCalls;

    private OneShotKeys(int count, AtomicInteger generated) {
      this.count = count;
      this.generated = generated;
    }

    @Override
    public Iterator<Integer> iterator() {
      iteratorCalls++;
      if (iteratorCalls > 1) {
        throw new IllegalStateException("iterator requested more than once");
      }
      return new Iterator<>() {
        private int next = 1;

        @Override
        public boolean hasNext() {
          return next <= count;
        }

        @Override
        public Integer next() {
          generated.incrementAndGet();
          return next++;
        }
      };
    }
  }
}
