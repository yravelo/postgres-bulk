package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresCopyExecutorIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:15.18-alpine")
          .withDatabaseName("postgres_bulk")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  private final PostgresCopyExecutor executor = new PostgresCopyExecutor();

  @AfterEach
  void cleanDatabase() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS \"Sales Space\" CASCADE");
      statement.execute("DROP TABLE IF EXISTS copy_strings");
      statement.execute("DROP TABLE IF EXISTS copy_numbers");
      statement.execute("DROP TABLE IF EXISTS copy_times");
      statement.execute("DROP TABLE IF EXISTS copy_binary_enum");
      statement.execute("DROP TABLE IF EXISTS copy_transactions");
      statement.execute("DROP TABLE IF EXISTS copy_failures");
      statement.execute("DROP TABLE IF EXISTS copy_streaming");
      statement.execute("DROP TABLE IF EXISTS bulk_insert_rows");
    }
  }

  @Test
  void roundTripsNullEmptyMarkersSpecialTextAndUtf8() throws Exception {
    createTable("CREATE TABLE copy_strings (id integer PRIMARY KEY, value text)");
    List<String> values =
        listIncludingNull(
            null,
            "",
            "\\N",
            "\\.",
            "with,comma",
            "with\"quote",
            "line\nfeed",
            "carriage\rreturn",
            "windows\r\nline",
            " leading",
            "trailing ",
            "café 東京",
            "emoji 😀",
            "path\\segment");
    List<StringRow> rows = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      rows.add(new StringRow(index + 1, values.get(index)));
    }

    try (Connection connection = connection()) {
      long affected = copyRows(connection, stringMetadata(), rows);

      assertEquals(rows.size(), affected);
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals("UTF8", scalarString(connection, "SHOW server_encoding"));
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery("SELECT id, value FROM copy_strings ORDER BY id")) {
        for (StringRow expected : rows) {
          assertTrue(result.next());
          assertEquals(expected.id(), result.getInt("id"));
          assertEquals(expected.value(), result.getString("value"));
          assertEquals(expected.value() == null, result.wasNull());
        }
        assertFalse(result.next());
      }
    }
  }

  @Test
  void roundTripsNumericFamiliesAndSpecialFloatingPointValues() throws Exception {
    createTable(
        "CREATE TABLE copy_numbers (id integer PRIMARY KEY, long_value bigint, integral numeric(50,0), decimal_value numeric(50,20), real_value real, double_value double precision)");
    List<NumericRow> rows =
        List.of(
            new NumericRow(
                1,
                Long.MAX_VALUE,
                new BigInteger("1234567890123456789012345678901234567890"),
                new BigDecimal("-1234567890.12345678901234567890"),
                1.25f,
                -2.5d),
            new NumericRow(
                2,
                Long.MIN_VALUE,
                new BigInteger("-999999999999999999999999999999"),
                new BigDecimal("0.00000000000000000001"),
                Float.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY),
            new NumericRow(
                3, 0L, BigInteger.ZERO, BigDecimal.ZERO, Float.NaN, Double.POSITIVE_INFINITY));

    try (Connection connection = connection()) {
      assertEquals(rows.size(), copyRows(connection, numericMetadata(), rows));
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT id, long_value, integral, decimal_value, real_value, double_value FROM copy_numbers ORDER BY id")) {
        for (NumericRow expected : rows) {
          assertTrue(result.next());
          assertEquals(expected.longValue(), result.getLong("long_value"));
          assertEquals(expected.integral(), result.getBigDecimal("integral").toBigIntegerExact());
          assertEquals(0, expected.decimal().compareTo(result.getBigDecimal("decimal_value")));
          assertFloatingPointEquals(expected.realValue(), result.getFloat("real_value"));
          assertFloatingPointEquals(expected.doubleValue(), result.getDouble("double_value"));
        }
        assertFalse(result.next());
      }
    }
  }

  @Test
  void roundTripsTemporalFamilies() throws Exception {
    createTable(
        "CREATE TABLE copy_times (id integer PRIMARY KEY, local_date date, local_time time(6), local_date_time timestamp(6), offset_date_time timestamptz(6), instant_value timestamptz(6))");
    TimeRow expected =
        new TimeRow(
            1,
            LocalDate.of(2026, 8, 18),
            LocalTime.of(12, 34, 56, 123_456_000),
            LocalDateTime.of(2026, 8, 18, 12, 34, 56, 654_321_000),
            OffsetDateTime.of(
                2026, 8, 18, 12, 34, 56, 111_222_000, ZoneOffset.ofHoursMinutes(5, 30)),
            Instant.parse("2026-08-18T10:20:30.333444Z"));

    try (Connection connection = connection()) {
      assertEquals(1, copyRows(connection, timeMetadata(), List.of(expected)));
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT * FROM copy_times")) {
        assertTrue(result.next());
        assertEquals(expected.localDate(), result.getObject("local_date", LocalDate.class));
        assertEquals(expected.localTime(), result.getObject("local_time", LocalTime.class));
        assertEquals(
            expected.localDateTime(), result.getObject("local_date_time", LocalDateTime.class));
        assertEquals(
            expected.offsetDateTime().toInstant(),
            result.getObject("offset_date_time", OffsetDateTime.class).toInstant());
        assertEquals(
            expected.instant(),
            result.getObject("instant_value", OffsetDateTime.class).toInstant());
      }
    }
  }

  @Test
  void roundTripsByteaIncludingHighBitsAndEnums() throws Exception {
    createTable(
        "CREATE TABLE copy_binary_enum (id integer PRIMARY KEY, payload bytea NOT NULL, state text NOT NULL)");
    List<BinaryRow> rows =
        List.of(
            new BinaryRow(1, new byte[0], State.READY),
            new BinaryRow(2, new byte[] {0, 1, 127, (byte) 128, (byte) 255}, State.DONE));

    try (Connection connection = connection()) {
      assertEquals(rows.size(), copyRows(connection, binaryMetadata(), rows));
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT id, payload, state FROM copy_binary_enum ORDER BY id")) {
        for (BinaryRow expected : rows) {
          assertTrue(result.next());
          assertArrayEquals(expected.payload(), result.getBytes("payload"));
          assertEquals(expected.state().name(), result.getString("state"));
        }
      }
    }
  }

  @Test
  void quotesSchemaTableAndColumnsAndKeepsColumnOrderAligned() throws Exception {
    createTable("CREATE SCHEMA \"Sales Space\"");
    createTable(
        "CREATE TABLE \"Sales Space\".\"Order\" (\"customer Code\" text, \"a\"\"b\" integer)");
    EntityMetadata<QuotedRow> metadata =
        EntityMetadata.of(
            QuotedRow.class,
            TableName.of("Sales Space", "Order"),
            List.of(
                ColumnMetadata.of("a\"b", Integer.class, QuotedRow::number),
                ColumnMetadata.of("customer Code", String.class, QuotedRow::customer)));

    try (Connection connection = connection()) {
      assertEquals(1, copyRows(connection, metadata, List.of(new QuotedRow("C-42", 42))));
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT \"customer Code\", \"a\"\"b\" FROM \"Sales Space\".\"Order\"")) {
        assertTrue(result.next());
        assertEquals("C-42", result.getString(1));
        assertEquals(42, result.getInt(2));
      }
    }
  }

  @Test
  void leavesAutocommitEnabledConnectionOpenReusableAndCommitted() throws Exception {
    createTable("CREATE TABLE copy_transactions (id integer PRIMARY KEY)");

    try (Connection connection = connection()) {
      assertTrue(connection.getAutoCommit());
      assertEquals(1, copyRows(connection, transactionMetadata(), List.of(new IdRow(1))));
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(1, scalarLong(connection, "SELECT count(*) FROM copy_transactions"));
    }
    try (Connection observer = connection()) {
      assertEquals(1, scalarLong(observer, "SELECT count(*) FROM copy_transactions"));
    }
  }

  @Test
  void leavesManualTransactionUnderCallerCommitAndRollbackControl() throws Exception {
    createTable("CREATE TABLE copy_transactions (id integer PRIMARY KEY)");

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      assertEquals(1, copyRows(connection, transactionMetadata(), List.of(new IdRow(1))));
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(1, scalarLong(connection, "SELECT count(*) FROM copy_transactions"));
      connection.rollback();
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM copy_transactions"));

      assertEquals(1, copyRows(connection, transactionMetadata(), List.of(new IdRow(2))));
      assertFalse(connection.getAutoCommit());
      connection.commit();
    }
    try (Connection observer = connection()) {
      assertEquals(1, scalarLong(observer, "SELECT count(*) FROM copy_transactions"));
      assertEquals(2, scalarLong(observer, "SELECT id FROM copy_transactions"));
    }
  }

  @Test
  void cancelsOnServerFailureAndPreservesSqlCause() throws Exception {
    createTable("CREATE TABLE copy_failures (id integer PRIMARY KEY CHECK (id > 0))");

    try (Connection connection = connection()) {
      CopyExecutionException thrown =
          assertThrows(
              CopyExecutionException.class,
              () -> copyRows(connection, failureMetadata(), List.of(new IdRow(-1))));

      assertNotNull(findCause(thrown, SQLException.class));
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(1, scalarLong(connection, "SELECT 1"));
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM copy_failures"));
    }
  }

  @Test
  void cancelsOnMidStreamProducerFailureAndPreservesOriginalCause() throws Exception {
    createTable("CREATE TABLE copy_failures (id integer PRIMARY KEY)");
    IOException failure = new IOException("producer stopped");

    try (Connection connection = connection()) {
      CopyExecutionException thrown =
          assertThrows(
              CopyExecutionException.class,
              () ->
                  executor.execute(
                      connection,
                      "COPY \"copy_failures\" (\"id\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
                      writer -> {
                        writer.write("1\n");
                        writer.flush();
                        throw failure;
                      }));

      assertSame(failure, thrown.getCause());
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(1, scalarLong(connection, "SELECT 1"));
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM copy_failures"));
    }
  }

  @Test
  void streamsGeneratedRowsAndReturnsServerCountWithoutMaterializingThem() throws Exception {
    createTable("CREATE TABLE copy_streaming (id integer PRIMARY KEY)");
    int rowCount = 20_000;

    try (Connection connection = connection()) {
      long affected =
          executor.execute(
              connection,
              "COPY \"copy_streaming\" (\"id\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
              writer -> {
                for (int id = 1; id <= rowCount; id++) {
                  writer.write(Integer.toString(id));
                  writer.write('\n');
                }
              });

      assertEquals(rowCount, affected);
      assertEquals(rowCount, scalarLong(connection, "SELECT count(*) FROM copy_streaming"));
    }
  }

  @Test
  void finishesAnEmptyLowLevelCopyWithZeroServerRows() throws Exception {
    createTable("CREATE TABLE copy_streaming (id integer PRIMARY KEY)");

    try (Connection connection = connection()) {
      long affected =
          executor.execute(
              connection,
              "COPY \"copy_streaming\" (\"id\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
              writer -> {});

      assertEquals(0, affected);
      assertFalse(connection.isClosed());
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM copy_streaming"));
    }
  }

  @Test
  void bulkInsertHandlesEmptySingleExactAndMultipleBatches() throws Exception {
    createBulkInsertTable();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());

    try (Connection connection = connection()) {
      assertEquals(
          BulkWriteResult.empty(),
          inserter.insert(connection, List.of(), BulkInsertOptions.ofBatchSize(3)));
      assertEquals(
          new BulkWriteResult(1, 1), inserter.insert(connection, List.of(new BulkRow(1, null))));
      assertEquals(
          new BulkWriteResult(3, 1),
          inserter.insert(connection, bulkRows(2, 4), BulkInsertOptions.ofBatchSize(3)));
      assertEquals(
          new BulkWriteResult(5, 3),
          inserter.insert(connection, bulkRows(5, 9), BulkInsertOptions.ofBatchSize(2)));

      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(9, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));
      assertNull(scalarString(connection, "SELECT value FROM bulk_insert_rows WHERE id = 1"));
    }
  }

  @Test
  void bulkInsertConsumesOneShotIterableExactlyOnce() throws Exception {
    createBulkInsertTable();
    AtomicInteger iteratorCalls = new AtomicInteger();
    Iterable<BulkRow> rows = oneShotRows(7, iteratorCalls);
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());

    try (Connection connection = connection()) {
      assertEquals(
          new BulkWriteResult(7, 3),
          inserter.insert(connection, rows, BulkInsertOptions.ofBatchSize(3)));
      assertEquals(1, iteratorCalls.get());
      assertEquals(7, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));
    }
  }

  @Test
  void bulkInsertLeavesAllBatchesUnderCallerCommitAndRollbackControl() throws Exception {
    createBulkInsertTable();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());
    List<BulkRow> rows = bulkRows(1, 2_500);

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      assertEquals(new BulkWriteResult(2_500, 3), inserter.insert(connection, rows));
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(2_500, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));

      connection.rollback();
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));

      assertEquals(new BulkWriteResult(2_500, 3), inserter.insert(connection, rows));
      connection.commit();
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
    }
    try (Connection observer = connection()) {
      assertEquals(2_500, scalarLong(observer, "SELECT count(*) FROM bulk_insert_rows"));
    }
  }

  @Test
  void laterBulkBatchFailureWithAutocommitKeepsCompletedBatches() throws Exception {
    createConstrainedBulkInsertTable();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());
    List<BulkRow> rows =
        List.of(new BulkRow(1, "one"), new BulkRow(2, "two"), new BulkRow(-3, "invalid"));

    try (Connection connection = connection()) {
      BulkException thrown =
          assertThrows(
              BulkException.class,
              () -> inserter.insert(connection, rows, BulkInsertOptions.ofBatchSize(2)));

      assertTrue(thrown.getMessage().contains("batch 2"));
      assertNotNull(findCause(thrown, SQLException.class));
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(2, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));
      assertEquals(1, scalarLong(connection, "SELECT 1"));
    }
  }

  @Test
  void laterBulkBatchFailureInManualTransactionCanRollBackAllBatches() throws Exception {
    createConstrainedBulkInsertTable();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());
    List<BulkRow> rows =
        List.of(new BulkRow(1, "one"), new BulkRow(2, "two"), new BulkRow(-3, "invalid"));

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      BulkException thrown =
          assertThrows(
              BulkException.class,
              () -> inserter.insert(connection, rows, BulkInsertOptions.ofBatchSize(2)));

      assertTrue(thrown.getMessage().contains("batch 2"));
      assertNotNull(findCause(thrown, SQLException.class));
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      connection.rollback();
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));

      assertEquals(
          new BulkWriteResult(1, 1), inserter.insert(connection, List.of(new BulkRow(4, "four"))));
      connection.commit();
    }
    try (Connection observer = connection()) {
      assertEquals(1, scalarLong(observer, "SELECT count(*) FROM bulk_insert_rows"));
      assertEquals(4, scalarLong(observer, "SELECT id FROM bulk_insert_rows"));
    }
  }

  @Test
  void nullItemInsideActiveBulkCopyCancelsItAndLeavesConnectionReusable() throws Exception {
    createBulkInsertTable();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());
    List<BulkRow> rows = Arrays.asList(new BulkRow(1, "one"), null, new BulkRow(3, "three"));

    try (Connection connection = connection()) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> inserter.insert(connection, rows, BulkInsertOptions.ofBatchSize(3)));

      assertTrue(thrown.getMessage().contains("position 2"));
      assertTrue(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      assertEquals(0, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));
      assertEquals(1, scalarLong(connection, "SELECT 1"));
    }
  }

  @Test
  void bulkInsertStreamsTwentyThousandRowsAcrossRealCopyBatches() throws Exception {
    createBulkInsertTable();
    AtomicInteger iteratorCalls = new AtomicInteger();
    PostgresBulkInserter<BulkRow> inserter = PostgresBulkInserter.prepare(bulkMetadata());

    try (Connection connection = connection()) {
      BulkWriteResult result =
          inserter.insert(
              connection, oneShotRows(20_000, iteratorCalls), BulkInsertOptions.ofBatchSize(777));

      assertEquals(new BulkWriteResult(20_000, 26), result);
      assertEquals(1, iteratorCalls.get());
      assertFalse(connection.isClosed());
      assertEquals(20_000, scalarLong(connection, "SELECT count(*) FROM bulk_insert_rows"));
    }
  }

  private static EntityMetadata<StringRow> stringMetadata() {
    return EntityMetadata.of(
        StringRow.class,
        TableName.of("copy_strings"),
        List.of(
            ColumnMetadata.of("id", Integer.class, StringRow::id),
            ColumnMetadata.of("value", String.class, StringRow::value)));
  }

  private static EntityMetadata<NumericRow> numericMetadata() {
    return EntityMetadata.of(
        NumericRow.class,
        TableName.of("copy_numbers"),
        List.of(
            ColumnMetadata.of("id", Integer.class, NumericRow::id),
            ColumnMetadata.of("long_value", Long.class, NumericRow::longValue),
            ColumnMetadata.of("integral", BigInteger.class, NumericRow::integral),
            ColumnMetadata.of("decimal_value", BigDecimal.class, NumericRow::decimal),
            ColumnMetadata.of("real_value", Float.class, NumericRow::realValue),
            ColumnMetadata.of("double_value", Double.class, NumericRow::doubleValue)));
  }

  private static EntityMetadata<TimeRow> timeMetadata() {
    return EntityMetadata.of(
        TimeRow.class,
        TableName.of("copy_times"),
        List.of(
            ColumnMetadata.of("id", Integer.class, TimeRow::id),
            ColumnMetadata.of("local_date", LocalDate.class, TimeRow::localDate),
            ColumnMetadata.of("local_time", LocalTime.class, TimeRow::localTime),
            ColumnMetadata.of("local_date_time", LocalDateTime.class, TimeRow::localDateTime),
            ColumnMetadata.of("offset_date_time", OffsetDateTime.class, TimeRow::offsetDateTime),
            ColumnMetadata.of("instant_value", Instant.class, TimeRow::instant)));
  }

  private static EntityMetadata<BinaryRow> binaryMetadata() {
    return EntityMetadata.of(
        BinaryRow.class,
        TableName.of("copy_binary_enum"),
        List.of(
            ColumnMetadata.of("id", Integer.class, BinaryRow::id),
            ColumnMetadata.of("payload", byte[].class, BinaryRow::payload),
            ColumnMetadata.of("state", State.class, BinaryRow::state)));
  }

  private static EntityMetadata<IdRow> transactionMetadata() {
    return EntityMetadata.of(
        IdRow.class,
        TableName.of("copy_transactions"),
        List.of(ColumnMetadata.of("id", Integer.class, IdRow::id)));
  }

  private static EntityMetadata<IdRow> failureMetadata() {
    return EntityMetadata.of(
        IdRow.class,
        TableName.of("copy_failures"),
        List.of(ColumnMetadata.of("id", Integer.class, IdRow::id)));
  }

  private static EntityMetadata<BulkRow> bulkMetadata() {
    return EntityMetadata.of(
        BulkRow.class,
        TableName.of("bulk_insert_rows"),
        List.of(
            ColumnMetadata.of("id", Integer.class, BulkRow::id),
            ColumnMetadata.of("value", String.class, BulkRow::value)));
  }

  private static List<BulkRow> bulkRows(int firstId, int lastId) {
    return IntStream.rangeClosed(firstId, lastId)
        .mapToObj(id -> new BulkRow(id, "value-" + id))
        .toList();
  }

  private static Iterable<BulkRow> oneShotRows(int count, AtomicInteger iteratorCalls) {
    return () -> {
      if (iteratorCalls.incrementAndGet() != 1) {
        throw new IllegalStateException("iterator requested more than once");
      }
      Iterator<BulkRow> iterator =
          IntStream.rangeClosed(1, count).mapToObj(id -> new BulkRow(id, "value-" + id)).iterator();
      return iterator;
    };
  }

  private <T> long copyRows(Connection connection, EntityMetadata<T> metadata, Iterable<T> rows) {
    PreparedCopyCsvRowEncoder<T> encoder = PreparedCopyCsvRowEncoder.prepare(metadata);
    return executor.execute(
        connection,
        CopySqlBuilder.insert(metadata),
        writer -> {
          for (T row : rows) {
            encoder.writeRow(row, writer);
          }
        });
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static void createTable(String ddl) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(ddl);
    }
  }

  private static void createBulkInsertTable() throws SQLException {
    createTable("CREATE TABLE bulk_insert_rows (id integer PRIMARY KEY, value text)");
  }

  private static void createConstrainedBulkInsertTable() throws SQLException {
    createTable(
        "CREATE TABLE bulk_insert_rows (id integer PRIMARY KEY CHECK (id > 0), value text)");
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

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    Throwable current = failure;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }

  @SafeVarargs
  private static <T> List<T> listIncludingNull(T... values) {
    return java.util.Arrays.asList(values);
  }

  private static void assertFloatingPointEquals(float expected, float actual) {
    if (Float.isNaN(expected)) {
      assertTrue(Float.isNaN(actual));
    } else {
      assertEquals(expected, actual);
    }
  }

  private static void assertFloatingPointEquals(double expected, double actual) {
    if (Double.isNaN(expected)) {
      assertTrue(Double.isNaN(actual));
    } else {
      assertEquals(expected, actual);
    }
  }

  private record StringRow(int id, String value) {}

  private record NumericRow(
      int id,
      Long longValue,
      BigInteger integral,
      BigDecimal decimal,
      Float realValue,
      Double doubleValue) {}

  private record TimeRow(
      int id,
      LocalDate localDate,
      LocalTime localTime,
      LocalDateTime localDateTime,
      OffsetDateTime offsetDateTime,
      Instant instant) {}

  private record BinaryRow(int id, byte[] payload, State state) {}

  private record QuotedRow(String customer, int number) {}

  private record IdRow(int id) {}

  private record BulkRow(int id, String value) {}

  private enum State {
    READY,
    DONE
  }
}
