package io.ybr.postgresbulk.springdata.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.JpaContext;

class PostgresBulkObservabilityTest {

  @Test
  void recordsOneInsertObservationAndAuthoritativeTotals() {
    Fixture fixture = fixture();

    BulkWriteResult result =
        fixture.observability().observeInsert(() -> new BulkWriteResult(20_000, 26));

    assertEquals(new BulkWriteResult(20_000, 26), result);
    Timer timer = timer(fixture.meters(), "insert", "success");
    assertEquals(1, timer.count());
    assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) >= 0);
    assertEquals(
        Set.of("error", "operation", "outcome"),
        timer.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        20_000,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
    assertEquals(
        26,
        fixture
            .meters()
            .get(PostgresBulkObservability.BATCHES_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
  }

  @Test
  void recordsLookupMatchesWithoutCountingInputKeys() {
    Fixture fixture = fixture();

    List<String> result = fixture.observability().observeLookup(() -> List.of("one", "two"));

    assertEquals(List.of("one", "two"), result);
    assertEquals(1, timer(fixture.meters(), "lookup", "success").count());
    assertEquals(
        2,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "lookup")
            .counter()
            .count());
  }

  @Test
  void emptyCallsAreSuccessfulOperationsWithZeroTotals() {
    Fixture fixture = fixture();

    assertEquals(
        BulkWriteResult.empty(), fixture.observability().observeInsert(BulkWriteResult::empty));
    assertEquals(List.of(), fixture.observability().observeLookup(List::of));

    assertEquals(1, timer(fixture.meters(), "insert", "success").count());
    assertEquals(1, timer(fixture.meters(), "lookup", "success").count());
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "lookup")
            .counter()
            .count());
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.BATCHES_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
  }

  @Test
  void failureRecordsErrorWithoutRowsAndPreservesIdentityAndSuppressed() {
    Fixture fixture = fixture();
    IllegalStateException primary = new IllegalStateException("producer failed");
    IllegalArgumentException cleanup = new IllegalArgumentException("cleanup failed");
    primary.addSuppressed(cleanup);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                fixture
                    .observability()
                    .observeInsert(
                        () -> {
                          throw primary;
                        }));

    assertSame(primary, thrown);
    assertSame(cleanup, thrown.getSuppressed()[0]);
    assertSame(primary, fixture.handler().contexts.peek().getError());
    Timer timer = timer(fixture.meters(), "insert", "error");
    assertEquals(1, timer.count());
    assertTrue(!"none".equals(timer.getId().getTag("error")));
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.BATCHES_NAME)
            .tag("operation", "insert")
            .counter()
            .count());
  }

  @Test
  void lookupFailureRecordsNoMatchedRowsAndPreservesIdentity() {
    Fixture fixture = fixture();
    IllegalArgumentException failure = new IllegalArgumentException("lookup callback failed");

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                fixture
                    .observability()
                    .observeLookup(
                        () -> {
                          throw failure;
                        }));

    assertSame(failure, thrown);
    assertEquals(1, timer(fixture.meters(), "lookup", "error").count());
    assertEquals(
        0,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "lookup")
            .counter()
            .count());
  }

  @Test
  void disabledObservabilityLeavesOperationUntouched() {
    IllegalStateException failure = new IllegalStateException("same instance");

    assertEquals(
        new BulkWriteResult(1, 1),
        PostgresBulkObservability.disabled().observeInsert(() -> new BulkWriteResult(1, 1)));
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () ->
                PostgresBulkObservability.disabled()
                    .observeLookup(
                        () -> {
                          throw failure;
                        })));
  }

  @Test
  void propertyOptOutPreventsObservationsAndMeters() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    ObservationRegistry observations = ObservationRegistry.create();
    observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
    DefaultPostgresBulkOperations<Object, Object> operations = operations();
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    beans.registerSingleton("observationRegistry", observations);
    beans.registerSingleton("meterRegistry", meters);

    operations.configureObservability(
        beans.getBeanProvider(ObservationRegistry.class),
        beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
        environmentWith("postgres-bulk.observability.enabled", "false"));

    assertEquals(
        BulkWriteResult.empty(),
        operations.bulkInsert(List.of(), io.ybr.postgresbulk.core.BulkInsertOptions.defaults()));
    assertTrue(meters.find(PostgresBulkObservability.OPERATION_NAME).meters().isEmpty());
  }

  @Test
  void missingObservationRegistryUsesNoopPath() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    DefaultPostgresBulkOperations<Object, Object> operations = operations();
    DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    beans.registerSingleton("meterRegistry", meters);

    operations.configureObservability(
        beans.getBeanProvider(ObservationRegistry.class),
        beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
        new StandardEnvironment());

    assertEquals(
        BulkWriteResult.empty(),
        operations.bulkInsert(List.of(), io.ybr.postgresbulk.core.BulkInsertOptions.defaults()));
    assertTrue(meters.find(PostgresBulkObservability.ROWS_NAME).meters().isEmpty());
  }

  @Test
  void telemetryHandlerFailureCannotChangeSuccessfulBulkResult() {
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public void onStart(Observation.Context context) {
                throw new IllegalStateException("telemetry unavailable");
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }
            });
    PostgresBulkObservability observability = new PostgresBulkObservability(registry, null);

    assertEquals(
        new BulkWriteResult(3, 1), observability.observeInsert(() -> new BulkWriteResult(3, 1)));
  }

  @Test
  void sharedRegistryIsSafeForConcurrentOperations() throws Exception {
    Fixture fixture = fixture();
    int operations = 8;
    CountDownLatch ready = new CountDownLatch(operations);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(operations);
    try {
      List<Future<List<Integer>>> futures =
          java.util.stream.IntStream.range(0, operations)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            start.await();
                            return fixture.observability().observeLookup(() -> List.of(1));
                          }))
              .toList();
      ready.await();
      start.countDown();
      for (Future<List<Integer>> future : futures) {
        assertEquals(List.of(1), future.get());
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(operations, timer(fixture.meters(), "lookup", "success").count());
    assertEquals(
        operations,
        fixture
            .meters()
            .get(PostgresBulkObservability.ROWS_NAME)
            .tag("operation", "lookup")
            .counter()
            .count());
  }

  private static Fixture fixture() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    ObservationRegistry observations = ObservationRegistry.create();
    StoringHandler handler = new StoringHandler();
    observations
        .observationConfig()
        .observationHandler(handler)
        .observationHandler(new DefaultMeterObservationHandler(meters));
    return new Fixture(new PostgresBulkObservability(observations, meters), meters, handler);
  }

  private static DefaultPostgresBulkOperations<Object, Object> operations() {
    JpaContext jpaContext = managedType -> null;
    JpaEntityMetadataResolver resolver =
        new JpaEntityMetadataResolver() {
          @Override
          public <T> EntityMetadata<T> resolve(
              EntityManagerFactory entityManagerFactory, Class<T> entityType) {
            return null;
          }
        };
    return new DefaultPostgresBulkOperations<>(jpaContext, resolver);
  }

  private static StandardEnvironment environmentWith(String name, Object value) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(name, value)));
    return environment;
  }

  private static Timer timer(SimpleMeterRegistry meters, String operation, String outcome) {
    return meters
        .get(PostgresBulkObservability.OPERATION_NAME)
        .tags("operation", operation, "outcome", outcome)
        .timer();
  }

  private record Fixture(
      PostgresBulkObservability observability,
      SimpleMeterRegistry meters,
      StoringHandler handler) {}

  private static final class StoringHandler implements ObservationHandler<Observation.Context> {

    private final ConcurrentLinkedQueue<Observation.Context> contexts =
        new ConcurrentLinkedQueue<>();

    @Override
    public void onStop(Observation.Context context) {
      contexts.add(context);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
      return true;
    }
  }
}
