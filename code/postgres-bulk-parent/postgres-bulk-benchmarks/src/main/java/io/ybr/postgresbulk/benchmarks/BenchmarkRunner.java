package io.ybr.postgresbulk.benchmarks;

import io.ybr.postgresbulk.benchmarks.jdbc.MultiSchemaCorrectnessVerifier;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Starts one real PostgreSQL container and keeps it alive across all JMH forks. */
public final class BenchmarkRunner {

  private static final String DEFAULT_POSTGRES_IMAGE = "15.18-alpine";

  private BenchmarkRunner() {}

  public static void main(String[] args) throws Exception {
    String image = System.getProperty("benchmark.postgres.image", DEFAULT_POSTGRES_IMAGE);
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:" + image);
    postgres.start();
    try {
      String jdbcUrl = withRewriteBatchedInserts(postgres.getJdbcUrl());
      System.setProperty("benchmark.jdbc.url", jdbcUrl);
      System.setProperty("benchmark.jdbc.username", postgres.getUsername());
      System.setProperty("benchmark.jdbc.password", postgres.getPassword());

      if (Boolean.getBoolean("benchmark.multi-schema")) {
        MultiSchemaCorrectnessVerifier.verify();
      }

      List<String> forkProperties = new ArrayList<>();
      forkProperties.add("-Dbenchmark.jdbc.url=" + jdbcUrl);
      forkProperties.add("-Dbenchmark.jdbc.username=" + postgres.getUsername());
      forkProperties.add("-Dbenchmark.jdbc.password=" + postgres.getPassword());
      forkProperties.add("-Dbenchmark.postgres.image=" + image);
      forkProperties.add("-Dlogging.level.root=WARN");

      Options commandLine = new CommandLineOptions(args);
      Options options =
          new OptionsBuilder()
              .parent(commandLine)
              .jvmArgsAppend(forkProperties.toArray(String[]::new))
              .build();
      new Runner(options).run();
    } finally {
      postgres.stop();
    }
  }

  private static String withRewriteBatchedInserts(String jdbcUrl) {
    String separator = jdbcUrl.contains("?") ? "&" : "?";
    return jdbcUrl + separator + "reWriteBatchedInserts=true";
  }
}
