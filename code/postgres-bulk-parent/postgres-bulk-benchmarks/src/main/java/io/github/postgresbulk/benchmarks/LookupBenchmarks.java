package io.github.postgresbulk.benchmarks;

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
public class LookupBenchmarks {

  @Benchmark
  public int sqlIn(LookupState state) {
    int found = state.environment.tunedRepository().findAllByCodeIn(state.codes).size();
    state.record(found);
    return found;
  }

  @Benchmark
  public int temporaryCopyJoin(LookupState state) {
    int found =
        state
            .environment
            .tunedRepository()
            .findAllByBulkKey(state.codes, BenchmarkEnvironment.CODE_KEY)
            .size();
    state.record(found);
    return found;
  }

  @State(Scope.Benchmark)
  public static class LookupState {

    private static final int TARGET_ROWS = 100_000;

    @Param({"10", "100", "1000", "10000"})
    int size;

    private BenchmarkEnvironment environment;
    private List<String> codes;
    private int found;

    @Setup(Level.Trial)
    public void seedTargetTable() {
      environment = BenchmarkEnvironment.instance();
      environment.seedLookupRows(TARGET_ROWS);
      codes = BenchmarkDataset.codes(size);
    }

    @TearDown(Level.Invocation)
    public void verifyLookup() {
      if (found != size) {
        throw new IllegalStateException("expected " + size + " rows but found " + found);
      }
    }

    void record(int result) {
      found = result;
    }
  }
}
