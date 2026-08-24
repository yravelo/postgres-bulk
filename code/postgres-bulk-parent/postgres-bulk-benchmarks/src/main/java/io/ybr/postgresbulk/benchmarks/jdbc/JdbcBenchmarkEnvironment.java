package io.ybr.postgresbulk.benchmarks.jdbc;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcBenchmarkEnvironment {

  static final TableName PUBLIC_TARGET = TableName.of("public", "benchmark_row");
  static final TableName TARGET_A = TableName.of("benchmark_a", "benchmark_row");
  static final TableName TARGET_B = TableName.of("benchmark_b", "benchmark_row");
  static final TableName TARGET_C = TableName.of("benchmark_c", "benchmark_row");
  static final TableName QUOTED_TARGET = TableName.of("Benchmark Quoted", "benchmark_row");

  static final BulkKeyMetadata<String> CODE_KEY =
      BulkKeyMetadata.of(
          String.class, List.of(ColumnMetadata.of("code", String.class, value -> value)));

  static final BulkKeyMetadata<JdbcCompositeKey> CODE_AND_ACTIVE_KEY =
      BulkKeyMetadata.of(
          JdbcCompositeKey.class,
          List.of(
              ColumnMetadata.of("code", String.class, JdbcCompositeKey::code),
              ColumnMetadata.of("active", Boolean.class, JdbcCompositeKey::active)));

  private static final JdbcBenchmarkEnvironment INSTANCE = new JdbcBenchmarkEnvironment();

  private final JdbcBenchmarkRepository repository;
  private final DataSource dataSource;
  private final JdbcOperations jdbcOperations;
  private final TransactionTemplate transactionTemplate;
  private final RowMapper<JdbcBenchmarkRow> rowMapper;
  private final PostgresBulkJdbcOperations<JdbcBenchmarkRow> preparedCopy;

  private JdbcBenchmarkEnvironment() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(JdbcBenchmarkConfiguration.class);
    repository = context.getBean(JdbcBenchmarkRepository.class);
    dataSource = context.getBean(DataSource.class);
    jdbcOperations = context.getBean(JdbcOperations.class);
    transactionTemplate =
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

    JdbcConverter converter = context.getBean(JdbcConverter.class);
    RelationalPersistentEntity<JdbcBenchmarkRow> persistentEntity = persistentEntity(converter);
    rowMapper = new EntityRowMapper<>(persistentEntity, converter);

    SpringDataJdbcEntityMetadataResolver resolver =
        context.getBean(SpringDataJdbcEntityMetadataResolver.class);
    EntityMetadata<JdbcBenchmarkRow> metadata = resolver.resolveFor(JdbcBenchmarkDataset.row(0));
    preparedCopy = PostgresBulkJdbcOperations.prepare(metadata);
    createSchema();
  }

  static JdbcBenchmarkEnvironment instance() {
    return INSTANCE;
  }

  int springDataSaveAll(List<JdbcBenchmarkRow> rows) {
    repository.saveAll(rows);
    return rows.size();
  }

  int jdbcBatch(List<JdbcBenchmarkRow> rows, int batchSize) {
    String sql =
        "INSERT INTO benchmark_row"
            + " (id, code, description, amount, active, business_date, created_at, note)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      connection.setAutoCommit(false);
      try {
        int pending = 0;
        for (JdbcBenchmarkRow row : rows) {
          bind(statement, row);
          statement.addBatch();
          pending++;
          if (pending == batchSize) {
            statement.executeBatch();
            pending = 0;
          }
        }
        if (pending > 0) {
          statement.executeBatch();
        }
        connection.commit();
        return rows.size();
      } catch (SQLException | RuntimeException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("JDBC batch benchmark failed", failure);
    }
  }

  int postgresBulkJdbc(List<JdbcBenchmarkRow> rows, int batchSize) {
    return Math.toIntExact(
        repository.bulkInsert(rows, BulkInsertOptions.ofBatchSize(batchSize)).affectedRows());
  }

  int postgresBulkJdbcRuntimeTarget(List<JdbcBenchmarkRow> rows, int batchSize) {
    return Math.toIntExact(
        repository
            .bulkInsert(rows, BulkInsertOptions.ofBatchSize(batchSize), PUBLIC_TARGET)
            .affectedRows());
  }

  int lowLevelCopy(List<JdbcBenchmarkRow> rows, int batchSize) {
    return lowLevelCopy(rows, batchSize, null);
  }

  int lowLevelCopyRuntimeTarget(List<JdbcBenchmarkRow> rows, int batchSize) {
    return lowLevelCopy(rows, batchSize, PUBLIC_TARGET);
  }

  int lowLevelCopy(List<JdbcBenchmarkRow> rows, int batchSize, TableName runtimeTarget) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        int affected =
            Math.toIntExact(
                (runtimeTarget == null
                        ? preparedCopy.bulkInsert(
                            connection, rows, BulkInsertOptions.ofBatchSize(batchSize))
                        : preparedCopy.bulkInsert(
                            connection,
                            rows,
                            BulkInsertOptions.ofBatchSize(batchSize),
                            runtimeTarget))
                    .affectedRows());
        connection.commit();
        return affected;
      } catch (RuntimeException | SQLException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Low-level COPY benchmark failed", failure);
    }
  }

  String sqlIn(int size) {
    return "SELECT id, code, description, amount, active, business_date, created_at, note"
        + " FROM benchmark_row WHERE code IN ("
        + String.join(",", Collections.nCopies(size, "?"))
        + ")";
  }

  List<JdbcBenchmarkRow> sqlIn(String sql, List<String> codes) {
    List<JdbcBenchmarkRow> result =
        transactionTemplate.execute(
            status -> jdbcOperations.query(sql, rowMapper, codes.toArray(Object[]::new)));
    if (result == null) {
      throw new IllegalStateException("TransactionTemplate returned null lookup rows");
    }
    return result;
  }

  List<JdbcBenchmarkRow> temporaryCopyJoin(List<String> codes) {
    return repository.findAllByBulkKey(codes, CODE_KEY);
  }

  List<JdbcBenchmarkRow> temporaryCopyJoinRuntimeTarget(List<String> codes) {
    return repository.findAllByBulkKey(codes, CODE_KEY, PUBLIC_TARGET);
  }

  List<JdbcBenchmarkRow> lowLevelTemporaryCopyJoin(List<String> codes) {
    return lowLevelTemporaryCopyJoin(codes, null);
  }

  List<JdbcBenchmarkRow> lowLevelTemporaryCopyJoinRuntimeTarget(List<String> codes) {
    return lowLevelTemporaryCopyJoin(codes, PUBLIC_TARGET);
  }

  List<JdbcBenchmarkRow> lowLevelTemporaryCopyJoin(List<String> codes, TableName runtimeTarget) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        List<JdbcBenchmarkRow> rows =
            runtimeTarget == null
                ? preparedCopy.findAllByBulkKey(
                    connection, codes, CODE_KEY, List.of(), this::materialize)
                : preparedCopy.findAllByBulkKey(
                    connection, codes, CODE_KEY, List.of(), this::materialize, runtimeTarget);
        connection.commit();
        return rows;
      } catch (RuntimeException | SQLException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Low-level lookup benchmark failed", failure);
    }
  }

  List<JdbcBenchmarkRow> temporaryCompositeCopyJoin(List<JdbcCompositeKey> keys) {
    return repository.findAllByBulkKey(keys, CODE_AND_ACTIVE_KEY);
  }

  void seedLookupRows(int targetRows) {
    truncate();
    lowLevelCopy(JdbcBenchmarkDataset.rows(targetRows), 10_000);
  }

  void truncate() {
    jdbcOperations.execute("TRUNCATE TABLE benchmark_row");
  }

  void truncateAllTargets() {
    jdbcOperations.execute(
        "TRUNCATE TABLE benchmark_row, benchmark_a.benchmark_row,"
            + " benchmark_b.benchmark_row, benchmark_c.benchmark_row,"
            + " \"Benchmark Quoted\".benchmark_row");
  }

  long rowCount(TableName target) {
    String table =
        target.schema().map(schema -> quote(schema) + ".").orElse("") + quote(target.table());
    Long count = jdbcOperations.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return count == null ? 0 : count;
  }

  void verifyRows(int expected) {
    Long actual = jdbcOperations.queryForObject("SELECT count(*) FROM benchmark_row", Long.class);
    if (actual == null || actual != expected) {
      throw new IllegalStateException("expected " + expected + " rows but found " + actual);
    }
    verifyRepresentative(JdbcBenchmarkDataset.row(0));
    if (expected > 1) {
      verifyRepresentative(JdbcBenchmarkDataset.row(expected - 1));
    }
  }

  void verifyLookup(List<JdbcBenchmarkRow> rows, int expected) {
    if (rows.size() != expected) {
      throw new IllegalStateException(
          "expected " + expected + " lookup rows but found " + rows.size());
    }
    HashSet<String> actualCodes = new HashSet<>(rows.size());
    for (JdbcBenchmarkRow row : rows) {
      actualCodes.add(row.code());
    }
    if (!actualCodes.equals(new HashSet<>(JdbcBenchmarkDataset.codes(expected)))) {
      throw new IllegalStateException("lookup result did not contain the expected key set");
    }
  }

  private void createSchema() {
    jdbcOperations.execute("CREATE SCHEMA IF NOT EXISTS benchmark_a");
    jdbcOperations.execute("CREATE SCHEMA IF NOT EXISTS benchmark_b");
    jdbcOperations.execute("CREATE SCHEMA IF NOT EXISTS benchmark_c");
    jdbcOperations.execute("CREATE SCHEMA IF NOT EXISTS \"Benchmark Quoted\"");
    createBenchmarkTable("benchmark_row");
    createBenchmarkTable("benchmark_a.benchmark_row");
    createBenchmarkTable("benchmark_b.benchmark_row");
    createBenchmarkTable("benchmark_c.benchmark_row");
    createBenchmarkTable("\"Benchmark Quoted\".benchmark_row");
  }

  private void createBenchmarkTable(String table) {
    jdbcOperations.execute(
        "CREATE TABLE IF NOT EXISTS "
            + table
            + " ("
            + "id uuid PRIMARY KEY,"
            + "code varchar(32) NOT NULL UNIQUE,"
            + "description varchar(160) NOT NULL,"
            + "amount numeric(19,2) NOT NULL,"
            + "active boolean NOT NULL,"
            + "business_date date NOT NULL,"
            + "created_at timestamptz NOT NULL,"
            + "note varchar(80))");
  }

  private List<JdbcBenchmarkRow> materialize(
      Connection connection, String selectSql, long copiedKeys) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(selectSql);
        ResultSet result = statement.executeQuery()) {
      java.util.ArrayList<JdbcBenchmarkRow> rows = new java.util.ArrayList<>();
      int rowNumber = 0;
      while (result.next()) {
        rows.add(rowMapper.mapRow(result, rowNumber++));
      }
      return List.copyOf(rows);
    }
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private void verifyRepresentative(JdbcBenchmarkRow expected) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT id, code, description, amount, active, business_date, created_at, note"
                    + " FROM benchmark_row WHERE id = ?")) {
      statement.setObject(1, expected.id());
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new IllegalStateException("representative row not found: " + expected.id());
        }
        JdbcBenchmarkRow actual = rowMapper.mapRow(result, 0);
        if (!expected.equals(actual)) {
          throw new IllegalStateException(
              "representative row did not round-trip: " + expected.id());
        }
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not verify representative benchmark row", failure);
    }
  }

  private static void bind(PreparedStatement statement, JdbcBenchmarkRow row) throws SQLException {
    statement.setObject(1, row.id());
    statement.setString(2, row.code());
    statement.setString(3, row.description());
    statement.setBigDecimal(4, row.amount());
    statement.setBoolean(5, row.active());
    statement.setObject(6, row.businessDate());
    statement.setTimestamp(7, Timestamp.from(row.createdAt()));
    statement.setString(8, row.note());
  }

  @SuppressWarnings("unchecked")
  private static RelationalPersistentEntity<JdbcBenchmarkRow> persistentEntity(
      JdbcConverter converter) {
    return (RelationalPersistentEntity<JdbcBenchmarkRow>)
        converter.getMappingContext().getRequiredPersistentEntity(JdbcBenchmarkRow.class);
  }
}
