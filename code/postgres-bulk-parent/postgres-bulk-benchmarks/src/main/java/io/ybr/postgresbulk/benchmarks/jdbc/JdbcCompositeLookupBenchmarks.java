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
public class JdbcCompositeLookupBenchmarks {

  @Benchmark
  public int temporaryCompositeCopyJoin(CompositeLookupState state) {
    List<JdbcBenchmarkRow> rows = state.environment.temporaryCompositeCopyJoin(state.keys);
    state.record(rows);
    return rows.size();
  }

  @State(Scope.Benchmark)
  public static class CompositeLookupState {

    private static final int TARGET_ROWS = 100_000;

    @Param({"100", "1000", "10000"})
    int size;

    private JdbcBenchmarkEnvironment environment;
    private List<JdbcCompositeKey> keys;
    private List<JdbcBenchmarkRow> found;

    @Setup(Level.Trial)
    public void seedTargetTable() {
      environment = JdbcBenchmarkEnvironment.instance();
      environment.seedLookupRows(TARGET_ROWS);
      keys = JdbcBenchmarkDataset.compositeKeys(size);
    }

    @TearDown(Level.Invocation)
    public void verifyLookup() {
      environment.verifyLookup(found, size);
    }

    void record(List<JdbcBenchmarkRow> result) {
      found = result;
    }
  }
}
