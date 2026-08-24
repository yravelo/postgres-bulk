package io.ybr.postgresbulk.benchmarks.jdbc;

import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;

/** Runs target-isolation checks before multi-schema measurements begin. */
public final class MultiSchemaCorrectnessVerifier {

  private MultiSchemaCorrectnessVerifier() {}

  public static void verify() {
    JdbcBenchmarkEnvironment environment = JdbcBenchmarkEnvironment.instance();
    environment.truncateAllTargets();
    try {
      insert(environment, null, 0);
      insert(environment, JdbcBenchmarkEnvironment.TARGET_A, 1);
      insert(environment, null, 2);
      insert(environment, JdbcBenchmarkEnvironment.TARGET_B, 3);
      insert(environment, null, 4);
      insert(environment, JdbcBenchmarkEnvironment.TARGET_C, 5);
      insert(environment, JdbcBenchmarkEnvironment.QUOTED_TARGET, 6);

      assertCount(environment, JdbcBenchmarkEnvironment.PUBLIC_TARGET, 3);
      assertCount(environment, JdbcBenchmarkEnvironment.TARGET_A, 1);
      assertCount(environment, JdbcBenchmarkEnvironment.TARGET_B, 1);
      assertCount(environment, JdbcBenchmarkEnvironment.TARGET_C, 1);
      assertCount(environment, JdbcBenchmarkEnvironment.QUOTED_TARGET, 1);

      verifyLookup(environment, null, 0);
      verifyLookup(environment, JdbcBenchmarkEnvironment.TARGET_A, 1);
      verifyLookup(environment, JdbcBenchmarkEnvironment.TARGET_B, 3);
      verifyLookup(environment, JdbcBenchmarkEnvironment.TARGET_C, 5);
      verifyLookup(environment, JdbcBenchmarkEnvironment.QUOTED_TARGET, 6);
    } finally {
      environment.truncateAllTargets();
    }
  }

  private static void insert(JdbcBenchmarkEnvironment environment, TableName target, int rowIndex) {
    environment.lowLevelCopy(List.of(JdbcBenchmarkDataset.row(rowIndex)), 1, target);
  }

  private static void verifyLookup(
      JdbcBenchmarkEnvironment environment, TableName target, int rowIndex) {
    JdbcBenchmarkRow expected = JdbcBenchmarkDataset.row(rowIndex);
    List<JdbcBenchmarkRow> found =
        environment.lowLevelTemporaryCopyJoin(List.of(expected.code()), target);
    if (!found.equals(List.of(expected))) {
      throw new IllegalStateException("multi-schema lookup isolation check failed");
    }
  }

  private static void assertCount(
      JdbcBenchmarkEnvironment environment, TableName target, long expected) {
    long actual = environment.rowCount(target);
    if (actual != expected) {
      throw new IllegalStateException(
          "multi-schema insert isolation check expected " + expected + " rows but found " + actual);
    }
  }
}
