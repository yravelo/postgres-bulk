package io.ybr.postgresbulk.benchmarks.jdbc;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms1g", "-Xmx3g"})
@Threads(1)
public class JdbcInsertBenchmarks {

  @Benchmark
  public int springDataJdbcSaveAll(InsertState state) {
    return state.environment.springDataSaveAll(state.rows);
  }

  @Benchmark
  public int jdbcBatch(InsertState state) {
    return state.environment.jdbcBatch(state.rows, 1_000);
  }

  @Benchmark
  public int postgresBulkJdbc(InsertState state) {
    return state.environment.postgresBulkJdbc(state.rows, 1_000);
  }

  @Benchmark
  public int lowLevelCopy(InsertState state) {
    return state.environment.lowLevelCopy(state.rows, 1_000);
  }

  @State(Scope.Benchmark)
  public static class InsertState {

    @Param({"10", "100", "1000", "10000", "100000"})
    int size;

    private JdbcBenchmarkEnvironment environment;
    private List<JdbcBenchmarkRow> rows;

    @Setup(Level.Trial)
    public void createDataset() {
      environment = JdbcBenchmarkEnvironment.instance();
      rows = JdbcBenchmarkDataset.rows(size);
    }

    @Setup(Level.Invocation)
    public void resetTable() {
      environment.truncate();
    }

    @TearDown(Level.Invocation)
    public void verifyRows() {
      environment.verifyRows(size);
    }
  }
}
