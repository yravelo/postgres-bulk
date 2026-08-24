package io.ybr.postgresbulk.benchmarks;

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
public class MultiSchemaJpaInsertBenchmarks {

  @Benchmark
  public int defaultTarget(InsertState state) {
    return state.environment.copy(state.rows, 1_000, true);
  }

  @Benchmark
  public int runtimeTarget(InsertState state) {
    return state.environment.copyToRuntimeTarget(state.rows, 1_000);
  }

  @State(Scope.Benchmark)
  public static class InsertState {

    @Param({"10", "100", "1000", "10000", "100000"})
    int size;

    private BenchmarkEnvironment environment;
    private List<BenchmarkRow> rows;

    @Setup(Level.Trial)
    public void createDataset() {
      environment = BenchmarkEnvironment.instance();
      rows = BenchmarkDataset.rows(size);
    }

    @Setup(Level.Invocation)
    public void resetTable() {
      environment.truncate();
    }

    @TearDown(Level.Invocation)
    public void verifyRows() {
      if (environment.rowCount() != size) {
        throw new IllegalStateException("multi-schema JPA insert row count mismatch");
      }
    }
  }
}
