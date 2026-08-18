package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.TableName;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TemporaryTableBulkLookupIT {

  private static final String FIXED_TEMPORARY_TABLE = "pgbulk_keys_integration";

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:15.18-alpine")
          .withDatabaseName("postgres_bulk")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  @AfterEach
  void cleanDatabase() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS \"Sales Space\" CASCADE");
      statement.execute("DROP TABLE IF EXISTS p7_lookup_rows");
      statement.execute("DROP TABLE IF EXISTS p7_composite_rows");
      statement.execute("DROP TABLE IF EXISTS p7_typed_rows");
      statement.execute("DROP DOMAIN IF EXISTS p7_sku CASCADE");
      statement.execute("DROP DOMAIN IF EXISTS p7_positive CASCADE");
    }
  }

  @Test
  void returnsAllTargetMatchesOnceForDuplicateAndMissingSimpleKeys() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one-a'), (1, 'one-b'), (2, 'two')");

    try (Connection connection = manualConnection()) {
      List<String> rows =
          simpleLookup()
              .lookup(
                  connection,
                  List.of(1, 1, 99),
                  List.of(),
                  (sameConnection, sql, copiedKeys) -> {
                    assertSame(connection, sameConnection);
                    assertEquals(3, copiedKeys);
                    return strings(sameConnection, sql, "payload");
                  });

      assertEquals(List.of("one-a", "one-b"), rows.stream().sorted().toList());
      assertFalse(connection.isClosed());
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
    }
  }

  @Test
  void matchesCompositeKeysWithCustomSchemaAndQuotedIdentifiersInMetadataOrder() throws Exception {
    execute(
        "CREATE SCHEMA \"Sales Space\"",
        "CREATE TABLE \"Sales Space\".\"Order\" (\"tenant Code\" text, \"order\"\"id\" integer, payload text)",
        "INSERT INTO \"Sales Space\".\"Order\" VALUES ('north', 1, 'n-1'), ('north', 2, 'n-2'), ('south', 1, 's-1')");
    BulkKeyMetadata<CompositeKey> metadata =
        BulkKeyMetadata.of(
            CompositeKey.class,
            List.of(
                ColumnMetadata.of("order\"id", Integer.class, CompositeKey::orderId),
                ColumnMetadata.of("tenant Code", String.class, CompositeKey::tenant)));
    TemporaryTableBulkLookup<CompositeKey> lookup =
        TemporaryTableBulkLookup.prepare(
            TableName.of("Sales Space", "Order"),
            metadata,
            new PostgresCopyExecutor(),
            () -> FIXED_TEMPORARY_TABLE);

    try (Connection connection = manualConnection()) {
      List<String> rows =
          lookup.lookup(
              connection,
              List.of(new CompositeKey("south", 1), new CompositeKey("north", 2)),
              List.of(),
              (sameConnection, sql, copiedKeys) -> strings(sameConnection, sql, "payload"));

      assertEquals(List.of("n-2", "s-1"), rows.stream().sorted().toList());
    }
  }

  @Test
  void emptyOneShotInputPerformsNoJdbcWorkEvenWithAutocommitAndMissingTarget() throws Exception {
    AtomicInteger iteratorCalls = new AtomicInteger();
    Iterable<Integer> empty =
        () -> {
          if (iteratorCalls.incrementAndGet() > 1) {
            throw new IllegalStateException("iterator requested more than once");
          }
          return List.<Integer>of().iterator();
        };
    TemporaryTableBulkLookup<Integer> missingTarget =
        TemporaryTableBulkLookup.prepare(TableName.of("does_not_exist"), simpleMetadata());

    try (Connection connection = connection()) {
      List<Integer> explicitEmpty = List.of();
      List<Integer> result =
          missingTarget.lookup(
              connection,
              empty,
              explicitEmpty,
              (sameConnection, sql, copiedKeys) -> {
                throw new AssertionError("query must not run");
              });

      assertSame(explicitEmpty, result);
      assertEquals(1, iteratorCalls.get());
      assertTrue(connection.getAutoCommit());
    }
  }

  @Test
  void rejectsAutocommitBeforeDdlAndUsesTheSameManualConnectionThroughout() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");

    try (Connection autocommit = connection()) {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> simpleLookup().lookup(autocommit, List.of(1), List.of(), unusedQuery()));
      assertTrue(thrown.getMessage().contains("autoCommit=false"));
      assertEquals(0, countTemporaryTables(autocommit));
    }

    try (Connection manual = manualConnection()) {
      CopyExecutor sameConnectionExecutor =
          (actual, sql, producer) -> {
            assertSame(manual, actual);
            return new PostgresCopyExecutor().execute(actual, sql, producer);
          };
      TemporaryTableBulkLookup<Integer> lookup =
          TemporaryTableBulkLookup.prepare(
              TableName.of("p7_lookup_rows"),
              simpleMetadata(),
              sameConnectionExecutor,
              () -> FIXED_TEMPORARY_TABLE);

      List<Integer> rows =
          lookup.lookup(
              manual,
              List.of(1),
              List.of(),
              (actual, sql, copiedKeys) -> {
                assertSame(manual, actual);
                return ints(actual, sql, "id");
              });

      assertEquals(List.of(1), rows);
      assertFalse(temporaryTableExists(manual, FIXED_TEMPORARY_TABLE));
    }
  }

  @Test
  void streamsTwentyThousandOneShotKeysAndReturnsEveryMatch() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows SELECT value, 'row-' || value FROM generate_series(1, 20000) value");
    AtomicInteger generated = new AtomicInteger();
    OneShotKeys keys = new OneShotKeys(20_000, generated);

    try (Connection connection = manualConnection()) {
      long matches =
          simpleLookup()
              .lookup(
                  connection,
                  keys,
                  -1L,
                  (sameConnection, sql, copiedKeys) -> {
                    assertEquals(20_000, generated.get());
                    assertEquals(20_000, copiedKeys);
                    return scalarLong(sameConnection, "SELECT count(*) FROM (" + sql + ") rows");
                  });

      assertEquals(20_000, matches);
      assertEquals(1, keys.iteratorCalls);
    }
  }

  @Test
  void explicitCleanupPrecedesCallerCommitAndLeavesConnectionReusable() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");

    try (Connection connection = manualConnection()) {
      assertEquals(List.of(1), lookupIds(simpleLookup(), connection, List.of(1)));
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
      assertFalse(connection.isClosed());
      connection.commit();
      assertEquals(1, scalarLong(connection, "SELECT count(*) FROM p7_lookup_rows"));
      assertFalse(connection.getAutoCommit());
    }
  }

  @Test
  void callerRollbackRemainsAvailableAndRemovesTransactionState() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");

    try (Connection connection = manualConnection()) {
      assertEquals(List.of(1), lookupIds(simpleLookup(), connection, List.of(1)));
      connection.rollback();
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
      assertEquals(1, scalarLong(connection, "SELECT count(*) FROM p7_lookup_rows"));
    }
  }

  @Test
  void nullKeyAndCompositeComponentFailWithPositionsAndCleanUp() throws Exception {
    execute("CREATE TABLE p7_lookup_rows (id integer, tenant text)");

    try (Connection connection = manualConnection()) {
      List<Integer> keys = listIncludingNull(1, null);
      IllegalArgumentException nullKey =
          assertThrows(
              IllegalArgumentException.class,
              () -> simpleLookup().lookup(connection, keys, List.of(), unusedQuery()));
      assertTrue(nullKey.getMessage().contains("position 2"));
      assertTrue(nullKey.getSuppressed().length >= 1);
      connection.rollback();
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));

      BulkKeyMetadata<CompositeKey> metadata =
          BulkKeyMetadata.of(
              CompositeKey.class,
              List.of(
                  ColumnMetadata.of("tenant", String.class, CompositeKey::tenant),
                  ColumnMetadata.of("id", Integer.class, CompositeKey::orderId)));
      TemporaryTableBulkLookup<CompositeKey> composite =
          TemporaryTableBulkLookup.prepare(
              TableName.of("p7_lookup_rows"),
              metadata,
              new PostgresCopyExecutor(),
              () -> FIXED_TEMPORARY_TABLE);
      IllegalArgumentException nullComponent =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  composite.lookup(
                      connection, List.of(new CompositeKey(null, 1)), List.of(), unusedQuery()));
      assertTrue(nullComponent.getMessage().contains("tenant"));
      assertTrue(nullComponent.getMessage().contains("position 1"));
      assertTrue(nullComponent.getSuppressed().length >= 1);
      connection.rollback();
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
      assertEquals(1, scalarLong(connection, "SELECT 1"));
    }
  }

  @Test
  void serverCopyFailurePreservesSqlCauseAndRollbackRemovesTemporaryTable() throws Exception {
    execute(
        "CREATE DOMAIN p7_positive AS integer CHECK (VALUE > 0)",
        "CREATE TABLE p7_lookup_rows (id p7_positive, payload text)");

    try (Connection connection = manualConnection()) {
      BulkException thrown =
          assertThrows(
              BulkException.class,
              () -> simpleLookup().lookup(connection, List.of(-1), List.of(), unusedQuery()));

      assertTrue(thrown.getCause() instanceof CopyExecutionException);
      assertTrue(thrown.getCause().getCause() instanceof SQLException);
      assertEquals("23514", ((SQLException) thrown.getCause().getCause()).getSQLState());
      assertEquals(1, thrown.getSuppressed().length);
      assertEquals("25P02", ((SQLException) thrown.getSuppressed()[0]).getSQLState());
      connection.rollback();
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
      assertEquals(1, scalarLong(connection, "SELECT 1"));
    }
  }

  @Test
  void selectSqlFailurePreservesPrimaryCauseAndRequiresCallerRollback() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");

    try (Connection connection = manualConnection()) {
      BulkException thrown =
          assertThrows(
              BulkException.class,
              () ->
                  simpleLookup()
                      .lookup(
                          connection,
                          List.of(1),
                          List.of(),
                          (sameConnection, sql, copiedKeys) -> {
                            try (Statement statement = sameConnection.createStatement()) {
                              statement.executeQuery("SELECT definitely_invalid_phase_7_sql");
                            }
                            return List.of();
                          }));

      assertTrue(thrown.getCause() instanceof SQLException);
      assertEquals("42703", ((SQLException) thrown.getCause()).getSQLState());
      assertEquals(1, thrown.getSuppressed().length);
      assertEquals("25P02", ((SQLException) thrown.getSuppressed()[0]).getSQLState());
      connection.rollback();
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
    }
  }

  @Test
  void callbackRuntimeFailureIsUnchangedAndCleanupKeepsTransactionUsable() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");
    IllegalStateException failure = new IllegalStateException("mapping failed");

    try (Connection connection = manualConnection()) {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  simpleLookup()
                      .lookup(
                          connection,
                          List.of(1),
                          List.of(),
                          (sameConnection, sql, copiedKeys) -> {
                            throw failure;
                          }));

      assertSame(failure, thrown);
      assertFalse(temporaryTableExists(connection, FIXED_TEMPORARY_TABLE));
      assertEquals(1, scalarLong(connection, "SELECT 1"));
    }
  }

  @Test
  void supportsSequentialAndNestedLookupsOnOneConnectionWithDistinctNames() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one'), (2, 'two')");
    AtomicInteger names = new AtomicInteger();
    TemporaryTableBulkLookup<Integer> lookup =
        TemporaryTableBulkLookup.prepare(
            TableName.of("p7_lookup_rows"),
            simpleMetadata(),
            new PostgresCopyExecutor(),
            () -> "pgbulk_keys_nested_" + names.incrementAndGet());

    try (Connection connection = manualConnection()) {
      assertEquals(List.of(1), lookupIds(lookup, connection, List.of(1)));
      assertEquals(List.of(2), lookupIds(lookup, connection, List.of(2)));
      List<Integer> outer =
          lookup.lookup(
              connection,
              List.of(1),
              List.of(),
              (sameConnection, outerSql, copiedKeys) -> {
                assertEquals(List.of(2), lookupIds(lookup, sameConnection, List.of(2)));
                return ints(sameConnection, outerSql, "id");
              });

      assertEquals(List.of(1), outer);
      assertEquals(4, names.get());
      assertEquals(0, countTemporaryTables(connection));
    }
  }

  @Test
  void concurrentConnectionsCanUseTheSameSessionLocalTemporaryName() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one'), (2, 'two')");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);

    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<List<Integer>> first = threads.submit(() -> concurrentLookup(1, ready, release));
      Future<List<Integer>> second = threads.submit(() -> concurrentLookup(2, ready, release));
      assertTrue(ready.await(20, TimeUnit.SECONDS));
      release.countDown();

      assertEquals(List.of(1), first.get(20, TimeUnit.SECONDS));
      assertEquals(List.of(2), second.get(20, TimeUnit.SECONDS));
    } finally {
      threads.shutdownNow();
    }
  }

  @Test
  void derivesOnlySelectedPhysicalTypesWithoutCopyingColumnProperties() throws Exception {
    execute(
        "CREATE DOMAIN p7_sku AS text CHECK (VALUE <> '')",
        "CREATE TABLE p7_typed_rows (sku p7_sku NOT NULL DEFAULT 'default', amount numeric(12,3) NOT NULL, label varchar(20) COLLATE \"C\", generated_value integer GENERATED ALWAYS AS (length(sku)) STORED, payload text)",
        "INSERT INTO p7_typed_rows (sku, amount, label, payload) VALUES ('A-1', 12.345, 'label', 'payload')");
    BulkKeyMetadata<TypedKey> metadata =
        BulkKeyMetadata.of(
            TypedKey.class,
            List.of(
                ColumnMetadata.of("sku", String.class, TypedKey::sku),
                ColumnMetadata.of("amount", BigDecimal.class, TypedKey::amount),
                ColumnMetadata.of("label", String.class, TypedKey::label),
                ColumnMetadata.of("generated_value", Integer.class, TypedKey::generatedValue)));
    TemporaryTableBulkLookup<TypedKey> lookup =
        TemporaryTableBulkLookup.prepare(
            TableName.of("p7_typed_rows"),
            metadata,
            new PostgresCopyExecutor(),
            () -> FIXED_TEMPORARY_TABLE);

    try (Connection connection = manualConnection()) {
      List<ColumnShape> shapes =
          lookup.lookup(
              connection,
              List.of(new TypedKey("A-1", new BigDecimal("12.345"), "label", 3)),
              List.of(),
              (sameConnection, sql, copiedKeys) -> {
                assertEquals(1, scalarLong(sameConnection, "SELECT count(*) FROM (" + sql + ") r"));
                return columnShapes(sameConnection, FIXED_TEMPORARY_TABLE);
              });

      assertEquals(4, shapes.size());
      assertEquals("p7_sku", shapes.get(0).type());
      assertEquals("numeric(12,3)", shapes.get(1).type());
      assertEquals("character varying(20)", shapes.get(2).type());
      assertEquals("C", shapes.get(2).collation());
      assertEquals("integer", shapes.get(3).type());
      shapes.forEach(
          shape -> {
            assertFalse(shape.notNull());
            assertEquals(null, shape.defaultExpression());
            assertEquals("", shape.identity());
            assertEquals("", shape.generated());
          });
    }
  }

  @Test
  void readOnlyTransactionFailsWithoutEngineReconfigurationAndCallerCanRollback() throws Exception {
    execute(
        "CREATE TABLE p7_lookup_rows (id integer PRIMARY KEY, payload text)",
        "INSERT INTO p7_lookup_rows VALUES (1, 'one')");

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      connection.setReadOnly(true);

      BulkException thrown =
          assertThrows(
              BulkException.class, () -> lookupIds(simpleLookup(), connection, List.of(1)));

      assertTrue(thrown.getCause() instanceof SQLException);
      assertEquals("25006", ((SQLException) thrown.getCause()).getSQLState());
      assertTrue(connection.isReadOnly());
      assertFalse(connection.getAutoCommit());
      connection.rollback();
      assertFalse(connection.isClosed());
    }
  }

  private static List<Integer> concurrentLookup(
      int id, CountDownLatch ready, CountDownLatch release) throws Exception {
    try (Connection connection = manualConnection()) {
      return simpleLookup()
          .lookup(
              connection,
              List.of(id),
              List.of(),
              (sameConnection, sql, copiedKeys) -> {
                ready.countDown();
                assertTrue(await(release));
                assertTrue(temporaryTableExists(sameConnection, FIXED_TEMPORARY_TABLE));
                return ints(sameConnection, sql, "id");
              });
    }
  }

  private static TemporaryTableBulkLookup<Integer> simpleLookup() {
    return TemporaryTableBulkLookup.prepare(
        TableName.of("p7_lookup_rows"),
        simpleMetadata(),
        new PostgresCopyExecutor(),
        () -> FIXED_TEMPORARY_TABLE);
  }

  private static boolean await(CountDownLatch latch) throws SQLException {
    try {
      return latch.await(20, TimeUnit.SECONDS);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new SQLException("Interrupted while coordinating concurrent lookup", failure);
    }
  }

  private static BulkKeyMetadata<Integer> simpleMetadata() {
    return BulkKeyMetadata.of(
        Integer.class, List.of(ColumnMetadata.of("id", Integer.class, value -> value)));
  }

  private static List<Integer> lookupIds(
      TemporaryTableBulkLookup<Integer> lookup, Connection connection, Iterable<Integer> keys) {
    return lookup.lookup(
        connection,
        keys,
        List.of(),
        (sameConnection, sql, copiedKeys) -> ints(sameConnection, sql, "id"));
  }

  private static List<Integer> ints(Connection connection, String sql, String column)
      throws SQLException {
    List<Integer> values = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getInt(column));
      }
    }
    return values;
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
    return values;
  }

  private static List<ColumnShape> columnShapes(Connection connection, String temporaryTable)
      throws SQLException {
    String sql =
        "SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod), a.attnotnull, pg_catalog.pg_get_expr(d.adbin, d.adrelid), a.attidentity, a.attgenerated, c.collname "
            + "FROM pg_catalog.pg_attribute a "
            + "JOIN pg_catalog.pg_class t ON t.oid = a.attrelid "
            + "LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum "
            + "LEFT JOIN pg_catalog.pg_collation c ON c.oid = a.attcollation "
            + "WHERE t.relnamespace = pg_my_temp_schema() AND t.relname = '"
            + temporaryTable
            + "' AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum";
    List<ColumnShape> shapes = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        shapes.add(
            new ColumnShape(
                result.getString(1),
                result.getString(2),
                result.getBoolean(3),
                result.getString(4),
                result.getString(5),
                result.getString(6),
                result.getString(7)));
      }
    }
    return shapes;
  }

  private static boolean temporaryTableExists(Connection connection, String tableName)
      throws SQLException {
    return scalarLong(
            connection,
            "SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace = pg_my_temp_schema() AND relname = '"
                + tableName
                + "'")
        == 1;
  }

  private static long countTemporaryTables(Connection connection) throws SQLException {
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

  private static void execute(String... statements) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    }
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

  private static <R> LookupQuery<R> unusedQuery() {
    return (connection, sql, copiedKeys) -> {
      throw new AssertionError("query must not be called");
    };
  }

  @SafeVarargs
  private static <T> List<T> listIncludingNull(T... values) {
    return Arrays.asList(values);
  }

  private record CompositeKey(String tenant, Integer orderId) {}

  private record TypedKey(String sku, BigDecimal amount, String label, Integer generatedValue) {}

  private record ColumnShape(
      String name,
      String type,
      boolean notNull,
      String defaultExpression,
      String identity,
      String generated,
      String collation) {}

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
