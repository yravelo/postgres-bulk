package io.ybr.postgresbulk.benchmarks;

import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms1g", "-Xmx3g"})
@Threads(1)
public class RuntimeTargetResolutionBenchmarks {

  @Benchmark
  public int samePrebuiltTarget(TargetState state) {
    int hash = 1;
    for (int index = 0; index < state.cardinality; index++) {
      hash =
          31 * hash
              + System.identityHashCode(state.mappedTable.resolveRuntimeTarget(state.sameTarget));
    }
    return hash;
  }

  @Benchmark
  public int manyPrebuiltTargets(TargetState state) {
    int hash = 1;
    for (TableName target : state.targets) {
      hash = 31 * hash + System.identityHashCode(state.mappedTable.resolveRuntimeTarget(target));
    }
    return hash;
  }

  @State(Scope.Benchmark)
  public static class TargetState {

    @Param({"100", "1000", "10000"})
    int cardinality;

    private final TableName mappedTable = TableName.of("benchmark_row");
    private final TableName sameTarget = TableName.of("schema_0", "benchmark_row");
    private TableName[] targets;

    @Setup
    public void createTargets() {
      targets = new TableName[cardinality];
      for (int index = 0; index < cardinality; index++) {
        targets[index] = TableName.of("schema_" + index, "benchmark_row");
      }
      for (TableName target : targets) {
        if (mappedTable.resolveRuntimeTarget(target) != target) {
          throw new IllegalStateException(
              "runtime target resolution retained or replaced a target");
        }
      }
    }
  }
}
