package io.github.postgresbulk.benchmarks;

import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

final class BenchmarkEnvironment {

  static final BulkKeyMetadata<String> CODE_KEY =
      BulkKeyMetadata.of(
          String.class, List.of(ColumnMetadata.of("code", String.class, value -> value)));

  private static final BenchmarkEnvironment INSTANCE = new BenchmarkEnvironment();

  private final ConfigurableApplicationContext tunedContext;
  private final ConfigurableApplicationContext defaultContext;
  private final BenchmarkRepository tunedRepository;
  private final BenchmarkRepository defaultRepository;
  private final DataSource dataSource;

  private BenchmarkEnvironment() {
    String url = requiredProperty("benchmark.jdbc.url");
    String username = requiredProperty("benchmark.jdbc.username");
    String password = requiredProperty("benchmark.jdbc.password");

    tunedContext =
        application(url, username, password)
            .properties(
                Map.of(
                    "spring.jpa.hibernate.ddl-auto", "create",
                    "spring.jpa.properties.hibernate.jdbc.batch_size", "1000",
                    "spring.jpa.properties.hibernate.order_inserts", "true",
                    "postgres-bulk.observability.enabled", "true"))
            .run();
    defaultContext =
        application(url, username, password)
            .properties(
                Map.of(
                    "spring.jpa.hibernate.ddl-auto", "none",
                    "postgres-bulk.observability.enabled", "false"))
            .run();
    tunedRepository = tunedContext.getBean(BenchmarkRepository.class);
    defaultRepository = defaultContext.getBean(BenchmarkRepository.class);
    dataSource = tunedContext.getBean(DataSource.class);
  }

  static BenchmarkEnvironment instance() {
    return INSTANCE;
  }

  BenchmarkRepository tunedRepository() {
    return tunedRepository;
  }

  BenchmarkRepository defaultRepository() {
    return defaultRepository;
  }

  int jdbcBatch(List<BenchmarkRow> rows, int batchSize) {
    String sql =
        "INSERT INTO benchmark_row"
            + " (id, code, description, amount, active, business_date, created_at, note)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      connection.setAutoCommit(false);
      try {
        int pending = 0;
        for (BenchmarkRow row : rows) {
          statement.setObject(1, row.getId());
          statement.setString(2, row.code());
          statement.setString(3, row.description());
          statement.setBigDecimal(4, row.amount());
          statement.setBoolean(5, row.active());
          statement.setObject(6, row.businessDate());
          statement.setTimestamp(7, Timestamp.from(row.createdAt()));
          statement.setString(8, row.note());
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

  int copy(List<BenchmarkRow> rows, int batchSize, boolean observabilityEnabled) {
    BenchmarkRepository repository = observabilityEnabled ? tunedRepository : defaultRepository;
    return Math.toIntExact(
        repository.bulkInsert(rows, BulkInsertOptions.ofBatchSize(batchSize)).affectedRows());
  }

  void truncate() {
    execute("TRUNCATE TABLE benchmark_row");
  }

  void seedLookupRows(int targetRows) {
    truncate();
    copy(BenchmarkDataset.rows(targetRows), 10_000, true);
  }

  long rowCount() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT count(*) FROM benchmark_row")) {
      result.next();
      return result.getLong(1);
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not count benchmark rows", failure);
    }
  }

  private void execute(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not reset benchmark table", failure);
    }
  }

  private static SpringApplicationBuilder application(
      String url, String username, String password) {
    return new SpringApplicationBuilder(BenchmarkApplication.class)
        .web(WebApplicationType.NONE)
        .properties(
            Map.ofEntries(
                Map.entry("spring.datasource.url", url),
                Map.entry("spring.datasource.username", username),
                Map.entry("spring.datasource.password", password),
                Map.entry("spring.datasource.hikari.maximum-pool-size", "4"),
                Map.entry("spring.datasource.hikari.minimum-idle", "1"),
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.jpa.open-in-view", "false"),
                Map.entry("spring.jpa.properties.hibernate.generate_statistics", "false"),
                Map.entry("logging.level.root", "WARN")));
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be supplied by BenchmarkRunner");
    }
    return value;
  }
}
