package io.github.postgresbulk.springdata.repository;

import io.github.postgresbulk.core.BulkWriteResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Package-private operation-level instrumentation for the Spring Data fragment. */
final class PostgresBulkObservability {

  static final String OPERATION_NAME = "postgres.bulk.operation";
  static final String ROWS_NAME = "postgres.bulk.rows";
  static final String BATCHES_NAME = "postgres.bulk.batches";

  private static final PostgresBulkObservability DISABLED =
      new PostgresBulkObservability(ObservationRegistry.NOOP, null);

  private final ObservationRegistry observationRegistry;
  private final Counter insertRows;
  private final Counter lookupRows;
  private final Counter insertBatches;

  PostgresBulkObservability(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
    this.observationRegistry = observationRegistry;
    this.insertRows = counter(meterRegistry, ROWS_NAME, "rows", Operation.INSERT);
    this.lookupRows = counter(meterRegistry, ROWS_NAME, "rows", Operation.LOOKUP);
    this.insertBatches = counter(meterRegistry, BATCHES_NAME, "batches", Operation.INSERT);
  }

  static PostgresBulkObservability disabled() {
    return DISABLED;
  }

  BulkWriteResult observeInsert(Supplier<BulkWriteResult> action) {
    return observe(
        Operation.INSERT,
        action,
        result -> {
          increment(insertRows, result.affectedRows());
          increment(insertBatches, result.batches());
        });
  }

  <T> List<T> observeLookup(Supplier<List<T>> action) {
    return observe(Operation.LOOKUP, action, result -> increment(lookupRows, result.size()));
  }

  private <R> R observe(Operation operation, Supplier<R> action, Consumer<R> successRecorder) {
    Observation observation = start(operation);
    Observation.Scope scope = openScope(observation);
    try {
      R result = action.get();
      addOutcome(observation, "success");
      recordSafely(successRecorder, result);
      return result;
    } catch (RuntimeException | Error failure) {
      addOutcome(observation, "error");
      recordError(observation, failure);
      throw failure;
    } finally {
      closeSafely(scope);
      stopSafely(observation);
    }
  }

  private Observation start(Operation operation) {
    Observation observation;
    try {
      observation =
          Observation.createNotStarted(OPERATION_NAME, observationRegistry)
              .contextualName("postgres bulk " + operation.value)
              .lowCardinalityKeyValue("operation", operation.value);
    } catch (RuntimeException | Error instrumentationFailure) {
      return Observation.NOOP;
    }
    try {
      return observation.start();
    } catch (RuntimeException | Error instrumentationFailure) {
      stopSafely(observation);
      return Observation.NOOP;
    }
  }

  private static Observation.Scope openScope(Observation observation) {
    try {
      return observation.openScope();
    } catch (RuntimeException | Error instrumentationFailure) {
      return null;
    }
  }

  private static void addOutcome(Observation observation, String outcome) {
    try {
      observation.lowCardinalityKeyValue("outcome", outcome);
    } catch (RuntimeException | Error instrumentationFailure) {
      // Observability is fail-open and must not alter the bulk result.
    }
  }

  private static void recordError(Observation observation, Throwable failure) {
    try {
      observation.error(failure);
    } catch (RuntimeException | Error instrumentationFailure) {
      // Preserve the original failure without adding telemetry failures as suppressed.
    }
  }

  private static <R> void recordSafely(Consumer<R> recorder, R result) {
    try {
      recorder.accept(result);
    } catch (RuntimeException | Error instrumentationFailure) {
      // Successful work must remain successful when a metrics backend misbehaves.
    }
  }

  private static void closeSafely(Observation.Scope scope) {
    if (scope == null) {
      return;
    }
    try {
      scope.close();
    } catch (RuntimeException | Error instrumentationFailure) {
      // Scope cleanup is telemetry-only and cannot replace an operation result.
    }
  }

  private static void stopSafely(Observation observation) {
    try {
      observation.stop();
    } catch (RuntimeException | Error instrumentationFailure) {
      // Observation completion is fail-open by contract.
    }
  }

  private static Counter counter(
      MeterRegistry registry, String name, String baseUnit, Operation operation) {
    if (registry == null) {
      return null;
    }
    try {
      return Counter.builder(name)
          .baseUnit(baseUnit)
          .description("Successful PostgreSQL bulk " + baseUnit)
          .tag("operation", operation.value)
          .register(registry);
    } catch (RuntimeException | Error instrumentationFailure) {
      return null;
    }
  }

  private static void increment(Counter counter, long amount) {
    if (counter != null && amount > 0) {
      counter.increment(amount);
    }
  }

  private enum Operation {
    INSERT("insert"),
    LOOKUP("lookup");

    private final String value;

    Operation(String value) {
      this.value = value;
    }
  }
}
