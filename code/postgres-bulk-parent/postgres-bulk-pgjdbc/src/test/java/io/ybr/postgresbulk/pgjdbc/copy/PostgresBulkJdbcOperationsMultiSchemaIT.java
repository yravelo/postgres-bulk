package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.PooledConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGConnectionPoolDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresBulkJdbcOperationsMultiSchemaIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_ms2")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  @AfterEach
  void cleanDatabase() throws SQLException {
    execute(
        "DROP SCHEMA IF EXISTS tenant_a CASCADE",
        "DROP SCHEMA IF EXISTS tenant_b CASCADE",
        "DROP SCHEMA IF EXISTS \"Tenant Space\" CASCADE",
        "DROP SCHEMA IF EXISTS restricted_target CASCADE",
        "DROP TABLE IF EXISTS public.product");
    execute("DROP ROLE IF EXISTS ms2_no_insert");
  }

  @Test
  void insertsIntoSchemasAAndBWithTheSameMetadataAndPreparedOperations() throws Exception {
    createProductSchemas(false);
    EntityMetadata<Product> metadata = productMetadata();
    TableName mappedTable = metadata.table();
    List<ColumnMetadata<Product>> mappedColumns = metadata.insertColumns();
    PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);

    try (Connection connection = connection()) {
      assertEquals(
          new BulkWriteResult(2, 1),
          operations.bulkInsert(
              connection,
              List.of(new Product(1, "A-1"), new Product(2, "A-2")),
              BulkInsertOptions.defaults(),
              TableName.of("tenant_a", "product")));
      assertEquals(
          new BulkWriteResult(2, 1),
          operations.bulkInsert(
              connection,
              List.of(new Product(1, "B-1"), new Product(2, "B-2")),
              BulkInsertOptions.defaults(),
              TableName.of("tenant_b", "product")));

      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
    }

    assertSame(mappedTable, metadata.table());
    assertSame(mappedColumns, metadata.insertColumns());
    assertEquals(List.of("A-1", "A-2"), values("tenant_a"));
    assertEquals(List.of("B-1", "B-2"), values("tenant_b"));
  }

  @Test
  void insertsIntoSchemasAAndBConcurrentlyWithoutCrossWrites() throws Exception {
    createProductSchemas(false);
    PostgresBulkJdbcOperations<Product> operations =
        PostgresBulkJdbcOperations.prepare(productMetadata());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<BulkWriteResult> tenantA =
          executor.submit(
              () -> {
                try (Connection connection = connection()) {
                  return operations.bulkInsert(
                      connection,
                      products("A", 1_000),
                      BulkInsertOptions.ofBatchSize(127),
                      TableName.of("tenant_a", "product"));
                }
              });
      Future<BulkWriteResult> tenantB =
          executor.submit(
              () -> {
                try (Connection connection = connection()) {
                  return operations.bulkInsert(
                      connection,
                      products("B", 1_000),
                      BulkInsertOptions.ofBatchSize(113),
                      TableName.of("tenant_b", "product"));
                }
              });

      assertEquals(new BulkWriteResult(1_000, 8), tenantA.get());
      assertEquals(new BulkWriteResult(1_000, 9), tenantB.get());
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1_000, count("tenant_a"));
    assertEquals(1_000, count("tenant_b"));
    assertEquals(0, countValuesOutsidePrefix("tenant_a", "A-%"));
    assertEquals(0, countValuesOutsidePrefix("tenant_b", "B-%"));
  }

  @Test
  void reusesOnePooledPhysicalConnectionAThenBWithoutSchemaStateLeakage() throws Exception {
    createProductSchemas(false);
    PostgresBulkJdbcOperations<Product> operations =
        PostgresBulkJdbcOperations.prepare(productMetadata());
    PGConnectionPoolDataSource dataSource = pooledDataSource();

    PooledConnection pooled = dataSource.getPooledConnection();
    try {
      int firstBackend;
      String initialSchema;
      String initialSearchPath;
      try (Connection logicalA = pooled.getConnection()) {
        firstBackend = (int) scalarLong(logicalA, "SELECT pg_backend_pid()");
        initialSchema = logicalA.getSchema();
        initialSearchPath = scalarString(logicalA, "SHOW search_path");
        operations.bulkInsert(
            logicalA,
            List.of(new Product(1, "A")),
            BulkInsertOptions.defaults(),
            TableName.of("tenant_a", "product"));
      }

      try (Connection logicalB = pooled.getConnection()) {
        assertEquals(firstBackend, scalarLong(logicalB, "SELECT pg_backend_pid()"));
        assertEquals(initialSchema, logicalB.getSchema());
        assertEquals(initialSearchPath, scalarString(logicalB, "SHOW search_path"));
        operations.bulkInsert(
            logicalB,
            List.of(new Product(1, "B")),
            BulkInsertOptions.defaults(),
            TableName.of("tenant_b", "product"));
        assertEquals(initialSchema, logicalB.getSchema());
        assertEquals(initialSearchPath, scalarString(logicalB, "SHOW search_path"));
      }
    } finally {
      pooled.close();
    }

    assertEquals(List.of("A"), values("tenant_a"));
    assertEquals(List.of("B"), values("tenant_b"));
  }

  @Test
  void commitsAndRollsBackMultipleSchemasInTheSameTransaction() throws Exception {
    createProductSchemas(false);
    PostgresBulkJdbcOperations<Product> operations =
        PostgresBulkJdbcOperations.prepare(productMetadata());

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      insertOne(operations, connection, "tenant_a", 1, "committed-A");
      insertOne(operations, connection, "tenant_b", 1, "committed-B");
      assertEquals(1, count(connection, "tenant_a"));
      assertEquals(1, count(connection, "tenant_b"));
      connection.commit();

      insertOne(operations, connection, "tenant_a", 2, "rolled-back-A");
      insertOne(operations, connection, "tenant_b", 2, "rolled-back-B");
      assertEquals(2, count(connection, "tenant_a"));
      assertEquals(2, count(connection, "tenant_b"));
      connection.rollback();

      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
    }

    assertEquals(List.of("committed-A"), values("tenant_a"));
    assertEquals(List.of("committed-B"), values("tenant_b"));
  }

  @Test
  void quotesRuntimeSchemaTableAndColumnsStructurally() throws Exception {
    execute("CREATE SCHEMA \"Tenant Space\"");
    execute(
        "CREATE TABLE \"Tenant Space\".\"Order Items\" (\"customer Code\" text, \"a\"\"b\" integer)");
    EntityMetadata<QuotedProduct> metadata =
        EntityMetadata.of(
            QuotedProduct.class,
            TableName.of("Order Items"),
            List.of(
                ColumnMetadata.of("a\"b", Integer.class, QuotedProduct::number),
                ColumnMetadata.of("customer Code", String.class, QuotedProduct::customer)));
    PostgresBulkJdbcOperations<QuotedProduct> operations =
        PostgresBulkJdbcOperations.prepare(metadata);

    try (Connection connection = connection()) {
      assertEquals(
          new BulkWriteResult(1, 1),
          operations.bulkInsert(
              connection,
              List.of(new QuotedProduct("C-42", 42)),
              BulkInsertOptions.defaults(),
              TableName.of("Tenant Space", "Order Items")));
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT \"customer Code\", \"a\"\"b\" FROM \"Tenant Space\".\"Order Items\"")) {
        assertTrue(result.next());
        assertEquals("C-42", result.getString(1));
        assertEquals(42, result.getInt(2));
      }
    }
  }

  @Test
  void permitsIdenticalStaticTargetAndRejectsEveryConflictBeforeCopy() throws Exception {
    execute("CREATE TABLE public.product (id integer PRIMARY KEY, value text)");
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class, TableName.of("public", "product"), productMetadata().insertColumns());
    PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);

    try (Connection connection = connection()) {
      insertOne(operations, connection, "public", 1, "allowed");
      assertThrows(
          IllegalArgumentException.class,
          () ->
              operations.bulkInsert(
                  connection,
                  List.of(new Product(2, "wrong-schema")),
                  BulkInsertOptions.defaults(),
                  TableName.of("tenant_a", "product")));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              operations.bulkInsert(
                  connection,
                  List.of(new Product(2, "wrong-table")),
                  BulkInsertOptions.defaults(),
                  TableName.of("public", "archive")));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              operations.bulkInsert(
                  connection,
                  List.of(new Product(2, "unqualified")),
                  BulkInsertOptions.defaults(),
                  TableName.of("product")));

      assertEquals(1, scalarLong(connection, "SELECT count(*) FROM public.product"));
      assertFalse(connection.isClosed());
    }
  }

  @Test
  void preservesSqlStateForMissingSchemaAndMissingTable() throws Exception {
    execute("CREATE SCHEMA tenant_a");
    PostgresBulkJdbcOperations<Product> productOperations =
        PostgresBulkJdbcOperations.prepare(productMetadata());
    EntityMetadata<Product> missingTableMetadata =
        EntityMetadata.of(
            Product.class, TableName.of("missing_product"), productMetadata().insertColumns());
    PostgresBulkJdbcOperations<Product> missingTableOperations =
        PostgresBulkJdbcOperations.prepare(missingTableMetadata);

    try (Connection connection = connection()) {
      BulkException missingSchema =
          assertThrows(
              BulkException.class,
              () ->
                  productOperations.bulkInsert(
                      connection,
                      List.of(new Product(1, "missing-schema")),
                      BulkInsertOptions.defaults(),
                      TableName.of("missing_schema", "product")));
      assertEquals("3F000", sqlCause(missingSchema).getSQLState());

      BulkException missingTable =
          assertThrows(
              BulkException.class,
              () ->
                  missingTableOperations.bulkInsert(
                      connection,
                      List.of(new Product(1, "missing-table")),
                      BulkInsertOptions.defaults(),
                      TableName.of("tenant_a", "missing_product")));
      assertEquals("42P01", sqlCause(missingTable).getSQLState());
      assertFalse(connection.isClosed());
    }
  }

  @Test
  void preservesPermissionDeniedSqlStateWithoutFallback() throws Exception {
    execute(
        "CREATE ROLE ms2_no_insert LOGIN PASSWORD 'ms2_no_insert'",
        "CREATE SCHEMA restricted_target",
        "CREATE TABLE restricted_target.product (id integer PRIMARY KEY, value text)",
        "GRANT USAGE ON SCHEMA restricted_target TO ms2_no_insert");
    PostgresBulkJdbcOperations<Product> operations =
        PostgresBulkJdbcOperations.prepare(productMetadata());

    try (Connection restricted = restrictedConnection()) {
      BulkException failure =
          assertThrows(
              BulkException.class,
              () ->
                  operations.bulkInsert(
                      restricted,
                      List.of(new Product(1, "denied")),
                      BulkInsertOptions.defaults(),
                      TableName.of("restricted_target", "product")));

      assertEquals("42501", sqlCause(failure).getSQLState());
      assertFalse(restricted.isClosed());
    }
    assertEquals(0, count("restricted_target"));
  }

  @Test
  void laterRuntimeTargetBatchFailurePreservesAutocommitAndManualSemantics() throws Exception {
    createProductSchemas(true);
    PostgresBulkJdbcOperations<Product> operations =
        PostgresBulkJdbcOperations.prepare(productMetadata());
    List<Product> rows =
        List.of(
            new Product(1, "one"),
            new Product(2, "two"),
            new Product(3, "three"),
            new Product(4, "four"),
            new Product(-5, "invalid"));
    TableName target = TableName.of("tenant_a", "product");

    try (Connection autocommit = connection()) {
      BulkException failure =
          assertThrows(
              BulkException.class,
              () ->
                  operations.bulkInsert(
                      autocommit, rows, BulkInsertOptions.ofBatchSize(2), target));
      assertNotNull(sqlCause(failure));
      assertTrue(autocommit.getAutoCommit());
      assertEquals(4, count(autocommit, "tenant_a"));
    }

    execute("TRUNCATE tenant_a.product");
    try (Connection manual = connection()) {
      manual.setAutoCommit(false);
      BulkException failure =
          assertThrows(
              BulkException.class,
              () -> operations.bulkInsert(manual, rows, BulkInsertOptions.ofBatchSize(2), target));
      assertEquals("23514", sqlCause(failure).getSQLState());
      assertEquals(
          "25P02",
          assertThrows(SQLException.class, () -> scalarLong(manual, "SELECT 1")).getSQLState());
      manual.rollback();
      assertEquals(0, count(manual, "tenant_a"));
      assertFalse(manual.getAutoCommit());
    }
  }

  @Test
  void producerFailureOnRuntimeTargetKeepsIdentityAndCallerOwnership() throws Exception {
    createProductSchemas(false);
    IllegalStateException failure = new IllegalStateException("accessor unavailable");
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class,
            TableName.of("product"),
            List.of(
                ColumnMetadata.of("id", Integer.class, Product::id),
                ColumnMetadata.of(
                    "value",
                    String.class,
                    product -> {
                      if (product.id() == 2) {
                        throw failure;
                      }
                      return product.value();
                    })));
    PostgresBulkJdbcOperations<Product> operations = PostgresBulkJdbcOperations.prepare(metadata);

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  operations.bulkInsert(
                      connection,
                      List.of(new Product(1, "one"), new Product(2, "not exposed")),
                      BulkInsertOptions.ofBatchSize(2),
                      TableName.of("tenant_a", "product"))));
      assertFalse(failure.getMessage().contains("not exposed"));
      assertEquals(
          "25P02",
          assertThrows(SQLException.class, () -> scalarLong(connection, "SELECT 1")).getSQLState());
      connection.rollback();
      assertEquals(0, count(connection, "tenant_a"));
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
    }
  }

  private static void insertOne(
      PostgresBulkJdbcOperations<Product> operations,
      Connection connection,
      String schema,
      int id,
      String value) {
    operations.bulkInsert(
        connection,
        List.of(new Product(id, value)),
        BulkInsertOptions.defaults(),
        TableName.of(schema, "product"));
  }

  private static EntityMetadata<Product> productMetadata() {
    return EntityMetadata.of(
        Product.class,
        TableName.of("product"),
        List.of(
            ColumnMetadata.of("id", Integer.class, Product::id),
            ColumnMetadata.of("value", String.class, Product::value)));
  }

  private static List<Product> products(String prefix, int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(id -> new Product(id, prefix + '-' + id))
        .toList();
  }

  private static void createProductSchemas(boolean constrained) throws SQLException {
    String constraint = constrained ? " CHECK (id > 0)" : "";
    execute(
        "CREATE SCHEMA tenant_a",
        "CREATE SCHEMA tenant_b",
        "CREATE TABLE tenant_a.product (id integer PRIMARY KEY" + constraint + ", value text)",
        "CREATE TABLE tenant_b.product (id integer PRIMARY KEY" + constraint + ", value text)");
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

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection restrictedConnection() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ms2_no_insert", "ms2_no_insert");
  }

  private static void execute(String... statements) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    }
  }

  private static long count(String schema) throws SQLException {
    try (Connection connection = connection()) {
      return count(connection, schema);
    }
  }

  private static long count(Connection connection, String schema) throws SQLException {
    return scalarLong(connection, "SELECT count(*) FROM \"" + schema + "\".product");
  }

  private static long countValuesOutsidePrefix(String schema, String prefix) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM \""
                    + schema
                    + "\".product WHERE value NOT LIKE '"
                    + prefix
                    + "'")) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static List<String> values(String schema) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT value FROM \"" + schema + "\".product ORDER BY id")) {
      java.util.ArrayList<String> values = new java.util.ArrayList<>();
      while (result.next()) {
        values.add(result.getString(1));
      }
      return List.copyOf(values);
    }
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

  private record Product(int id, String value) {}

  private record QuotedProduct(String customer, int number) {}
}
