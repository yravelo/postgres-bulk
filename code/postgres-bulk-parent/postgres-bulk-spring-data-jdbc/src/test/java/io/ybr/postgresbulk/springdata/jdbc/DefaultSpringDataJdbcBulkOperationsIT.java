package io.ybr.postgresbulk.springdata.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DefaultSpringDataJdbcBulkOperationsIT {

  private static final UUID PARENT_ID = UUID.fromString("a20b679e-1864-4811-af0d-37fce096dca4");

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_jdbc")
          .withUsername("postgres_bulk_jdbc")
          .withPassword("postgres_bulk_jdbc");

  private static HikariDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static JdbcTransactionManager transactionManager;
  private static SpringDataJdbcEntityMetadataResolver metadataResolver;

  @BeforeAll
  static void createInfrastructure() {
    HikariConfig pool = new HikariConfig();
    pool.setJdbcUrl(POSTGRES.getJdbcUrl());
    pool.setUsername(POSTGRES.getUsername());
    pool.setPassword(POSTGRES.getPassword());
    pool.setMaximumPoolSize(2);
    pool.setMinimumIdle(0);
    pool.setConnectionTimeout(10_000);
    dataSource = new HikariDataSource(pool);
    jdbc = new JdbcTemplate(dataSource);
    transactionManager = new JdbcTransactionManager(dataSource);
    metadataResolver = resolver();

    jdbc.execute("CREATE SCHEMA \"Bulk Schema\"");
    jdbc.execute("CREATE TABLE parent_rows (id uuid PRIMARY KEY)");
    jdbc.update("INSERT INTO parent_rows (id) VALUES (?)", PARENT_ID);
    jdbc.execute(
        "CREATE TABLE root_rows ("
            + "id bigint PRIMARY KEY, value text NOT NULL UNIQUE, status text, priority integer, "
            + "amount numeric(12,2), addr_city text, addr_geo_latitude double precision, "
            + "addr_geo_longitude double precision, parent_id uuid REFERENCES parent_rows(id), "
            + "backend_pid integer NOT NULL DEFAULT pg_backend_pid())");
    jdbc.execute(
        "CREATE TABLE generated_rows ("
            + "id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, value text NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE uuid_rows (id uuid PRIMARY KEY, value text NOT NULL)");
    jdbc.execute("CREATE TABLE batch_rows (id bigint PRIMARY KEY, value text NOT NULL)");
    jdbc.execute(
        "CREATE TABLE converter_failure_rows (id bigint PRIMARY KEY, value text NOT NULL)");
    jdbc.execute("CREATE TABLE accessor_failure_rows (id bigint PRIMARY KEY, value text NOT NULL)");
    jdbc.execute(
        "CREATE TABLE \"Bulk Schema\".\"Order Rows\" (\"Id\" bigint PRIMARY KEY, \"Value\" text NOT NULL)");
  }

  @BeforeEach
  void cleanTables() {
    jdbc.execute(
        "TRUNCATE root_rows, generated_rows, uuid_rows, batch_rows, converter_failure_rows, "
            + "accessor_failure_rows, "
            + "\"Bulk Schema\".\"Order Rows\" RESTART IDENTITY");
  }

  @AfterAll
  static void closePool() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @Test
  void insertsConvertedEmbeddedReferenceSchemaAndAssignedIdsThroughCopy() {
    RichRow populated =
        new RichRow(
            1L,
            "rich",
            Status.ACTIVE,
            Priority.HIGH,
            new Money(new BigDecimal("19.95")),
            new Address("Madrid", new GeoPoint(40.4168, -3.7038)),
            AggregateReference.to(PARENT_ID));
    RichRow nullEmbedded =
        new RichRow(
            2L,
            "null-embedded",
            Status.DISABLED,
            Priority.LOW,
            new Money(new BigDecimal("1.25")),
            null,
            AggregateReference.to(PARENT_ID));
    UUID assignedUuid = UUID.fromString("c99316e3-bca3-4d08-bb37-31f6dcbe61c1");

    TransactionTemplate transaction = transaction();
    transaction.executeWithoutResult(
        status -> {
          assertEquals(
              new BulkWriteResult(2, 1), operations().bulkInsert(List.of(populated, nullEmbedded)));
          assertEquals(
              new BulkWriteResult(1, 1),
              operations().bulkInsert(List.of(new UuidRow(assignedUuid, "uuid"))));
          assertEquals(
              new BulkWriteResult(1, 1),
              operations().bulkInsert(List.of(new QuotedRow(11L, "quoted"))));
        });

    assertEquals(
        List.of("ACTIVE", "9", "19.95", "Madrid", "40.4168", "-3.7038", PARENT_ID.toString()),
        jdbc.queryForObject(
            "SELECT status, priority::text, amount::text, addr_city, addr_geo_latitude::text, "
                + "addr_geo_longitude::text, parent_id::text FROM root_rows WHERE id = 1",
            (result, row) ->
                List.of(
                    result.getString(1),
                    result.getString(2),
                    result.getString(3),
                    result.getString(4),
                    result.getString(5),
                    result.getString(6),
                    result.getString(7))));
    List<String> embeddedNulls =
        jdbc.queryForObject(
            "SELECT addr_city, addr_geo_latitude::text, addr_geo_longitude::text "
                + "FROM root_rows WHERE id = 2",
            (result, row) ->
                java.util.Arrays.asList(
                    result.getString(1), result.getString(2), result.getString(3)));
    assertEquals(java.util.Arrays.asList(null, null, null), embeddedNulls);
    assertEquals(assignedUuid, jdbc.queryForObject("SELECT id FROM uuid_rows", UUID.class));
    assertEquals(
        "quoted",
        jdbc.queryForObject("SELECT \"Value\" FROM \"Bulk Schema\".\"Order Rows\"", String.class));
  }

  @Test
  void handlesEmptySingleExactBatchBatchPlusOneAndTwoThousandFiveHundred() {
    assertEquals(BulkWriteResult.empty(), operations().bulkInsert(List.<BatchRow>of()));

    assertBatch(1, 1, 1, 0);
    assertBatch(1_000, 1_000, 1, 10_000);
    assertBatch(1_001, 1_001, 2, 20_000);
    assertBatch(2_500, 2_500, 3, 30_000);
  }

  @Test
  void consumesOneShotIterableExactlyOnce() {
    AtomicInteger iteratorCalls = new AtomicInteger();
    List<BatchRow> rows = batchRows(7, 40_000);
    Iterable<BatchRow> oneShot =
        () -> {
          if (iteratorCalls.incrementAndGet() != 1) {
            throw new IllegalStateException("iterator requested more than once");
          }
          return rows.iterator();
        };

    BulkWriteResult result =
        transaction()
            .execute(status -> operations().bulkInsert(oneShot, BulkInsertOptions.ofBatchSize(3)));

    assertEquals(new BulkWriteResult(7, 3), result);
    assertEquals(1, iteratorCalls.get());
    assertEquals(7, count("batch_rows"));
  }

  @Test
  void omitsGeneratedIdWithoutMutatingEntityAndPersistsAssignedLongAndUuid() {
    GeneratedRow generated = new GeneratedRow(null, "generated");
    GeneratedRow assigned = new GeneratedRow(99L, "assigned");

    transaction()
        .executeWithoutResult(
            status -> {
              assertEquals(new BulkWriteResult(1, 1), operations().bulkInsert(List.of(generated)));
              assertEquals(new BulkWriteResult(1, 1), operations().bulkInsert(List.of(assigned)));
            });

    assertNull(generated.id);
    assertEquals(2, count("generated_rows"));
    assertEquals(
        99L,
        jdbc.queryForObject("SELECT id FROM generated_rows WHERE value = 'assigned'", Long.class));
  }

  @Test
  void rejectsMixedIdPoliciesInBothOrdersAndRollbackLeavesNoRows() {
    assertMixedIds(
        List.of(new GeneratedRow(null, "generated-first"), new GeneratedRow(91L, "assigned")));
    assertMixedIds(
        List.of(new GeneratedRow(92L, "assigned-first"), new GeneratedRow(null, "generated")));
    assertEquals(0, count("generated_rows"));
  }

  @Test
  void nullItemReportsOneBasedPositionAndRollsBackCopy() {
    List<BatchRow> rows = new ArrayList<>();
    rows.add(new BatchRow(95_001L, "before-null"));
    rows.add(null);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> transaction().execute(status -> operations().bulkInsert(rows)));

    assertTrue(failure.getMessage().contains("position 2"));
    assertEquals(0, count("batch_rows"));
  }

  @Test
  void requiredCommitOuterRollbackReadOnlyAndSameBackendConnectionAreEnforced() {
    TransactionTemplate transaction = transaction();
    transaction.executeWithoutResult(
        status -> {
          Integer beforePid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          operations().bulkInsert(List.of(simpleRichRow(100L, "committed")));
          Integer copyPid =
              jdbc.queryForObject(
                  "SELECT backend_pid FROM root_rows WHERE id = 100", Integer.class);
          Integer afterPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          assertEquals(beforePid, copyPid);
          assertEquals(beforePid, afterPid);
        });
    assertEquals(1, count("root_rows"));

    assertThrows(
        DeliberateFailure.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  operations().bulkInsert(List.of(simpleRichRow(101L, "rolled-back")));
                  throw new DeliberateFailure();
                }));
    assertEquals(1, count("root_rows"));

    TransactionTemplate readOnly = transaction();
    readOnly.setReadOnly(true);
    InvalidDataAccessApiUsageException failure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                readOnly.executeWithoutResult(
                    status -> operations().bulkInsert(List.of(simpleRichRow(102L, "read-only")))));
    assertTrue(failure.getMessage().contains("read-only"));
    assertEquals(1, count("root_rows"));
  }

  @Test
  void requiresNewUsesIndependentPhysicalTransactionAndNestedIsOnlyCharacterized() {
    TransactionTemplate outer = transaction();
    TransactionTemplate requiresNew = transaction();
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    List<Integer> pids = new ArrayList<>();

    outer.executeWithoutResult(
        outerStatus -> {
          pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
          requiresNew.executeWithoutResult(
              innerStatus -> {
                pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                operations().bulkInsert(List.of(new BatchRow(50_001L, "requires-new")));
              });
          outerStatus.setRollbackOnly();
        });

    assertEquals(2, pids.size());
    assertFalse(pids.get(0).equals(pids.get(1)));
    assertEquals(1, count("batch_rows"));

    TransactionTemplate nested = transaction();
    nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    outer.executeWithoutResult(
        outerStatus -> {
          operations().bulkInsert(List.of(new BatchRow(50_002L, "nested-outer")));
          assertThrows(
              DeliberateFailure.class,
              () ->
                  nested.executeWithoutResult(
                      nestedStatus -> {
                        operations().bulkInsert(List.of(new BatchRow(50_003L, "nested-inner")));
                        throw new DeliberateFailure();
                      }));
        });
    assertEquals(2, count("batch_rows"));
    assertEquals(0, countWhere("batch_rows", "id = 50003"));
  }

  @Test
  void producerAndConverterFailuresRollbackAndPoolRemainsReusable() {
    RuntimeException producerFailure = new IllegalStateException("producer failed");
    Iterable<BatchRow> failingProducer =
        () ->
            new Iterator<>() {
              private boolean first = true;

              @Override
              public boolean hasNext() {
                if (first) {
                  return true;
                }
                throw producerFailure;
              }

              @Override
              public BatchRow next() {
                if (!first) {
                  throw new NoSuchElementException();
                }
                first = false;
                return new BatchRow(60_001L, "first");
              }
            };
    RuntimeException observed =
        assertThrows(
            RuntimeException.class,
            () -> transaction().execute(status -> operations().bulkInsert(failingProducer)));
    assertSame(producerFailure, observed);
    assertEquals(0, count("batch_rows"));

    BulkException converterFailure =
        assertThrows(
            BulkException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            operations()
                                .bulkInsert(
                                    List.of(
                                        new ConverterFailureRow(
                                            1L, new FailingValue("good", false)),
                                        new ConverterFailureRow(
                                            2L, new FailingValue("hidden", true))))));
    assertTrue(converterFailure.getMessage().contains("Could not read"));
    assertEquals(0, count("converter_failure_rows"));

    transaction()
        .executeWithoutResult(
            status ->
                operations()
                    .bulkInsert(
                        List.of(new ConverterFailureRow(3L, new FailingValue("reused", false)))));
    assertEquals(1, count("converter_failure_rows"));
    assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
  }

  @Test
  void accessorFailureDuringCopyPreservesCauseRollsBackAndAllowsReuse() {
    IllegalStateException accessorFailure = new IllegalStateException("intentional getter failure");

    BulkException observed =
        assertThrows(
            BulkException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            operations()
                                .bulkInsert(
                                    List.of(
                                        new AccessorFailureRow(1L, "good", null),
                                        new AccessorFailureRow(2L, "hidden", accessorFailure)))));

    assertSame(accessorFailure, rootCause(observed));
    assertEquals(0, count("accessor_failure_rows"));
    transaction()
        .executeWithoutResult(
            status ->
                operations()
                    .bulkInsert(List.of(new AccessorFailureRow(3L, "after-rollback", null))));
    assertEquals(1, count("accessor_failure_rows"));
  }

  @Test
  void sqlFailurePreservesSqlStateExposesAbortedTransactionAndPoolRecoversAfterRollback() {
    List<Throwable> failures = new ArrayList<>();
    transaction()
        .executeWithoutResult(
            status -> {
              BulkException primary =
                  assertThrows(
                      BulkException.class,
                      () ->
                          operations()
                              .bulkInsert(
                                  List.of(
                                      new BatchRow(70_001L, "duplicate"),
                                      new BatchRow(70_001L, "duplicate-again"))));
              failures.add(primary);
              DataAccessException aborted =
                  assertThrows(
                      DataAccessException.class,
                      () -> jdbc.queryForObject("SELECT count(*) FROM batch_rows", Long.class));
              failures.add(aborted);
              status.setRollbackOnly();
            });

    assertEquals("23505", sqlState(failures.get(0)));
    assertEquals("25P02", sqlState(failures.get(1)));
    assertEquals(0, count("batch_rows"));

    transaction()
        .executeWithoutResult(
            status -> operations().bulkInsert(List.of(new BatchRow(70_002L, "healthy"))));
    assertEquals(1, count("batch_rows"));
    assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
  }

  @Test
  void lookupMaterializesRootRowsWithSimpleCompositeAndConvertedKeysInOneSelect() {
    RichRow alpha =
        new RichRow(
            801L,
            "alpha",
            Status.ACTIVE,
            Priority.HIGH,
            new Money(new BigDecimal("12.50")),
            new Address("Madrid", new GeoPoint(40.4, -3.7)),
            AggregateReference.to(PARENT_ID));
    RichRow beta =
        new RichRow(
            802L,
            "beta",
            Status.DISABLED,
            Priority.LOW,
            new Money(new BigDecimal("7.25")),
            null,
            AggregateReference.to(PARENT_ID));
    RichRow activeDuplicate =
        new RichRow(
            803L,
            "active-duplicate",
            Status.ACTIVE,
            Priority.HIGH,
            new Money(new BigDecimal("12.50")),
            null,
            AggregateReference.to(PARENT_ID));
    AtomicInteger selects = new AtomicInteger();

    transaction()
        .executeWithoutResult(
            status -> {
              DefaultSpringDataJdbcBulkOperations<RichRow> operations = operations();
              operations.bulkInsert(List.of(alpha, beta, activeDuplicate));

              List<RichRow> simple =
                  countingOperations(selects)
                      .findAllByBulkKey(
                          RichRow.class,
                          List.of("beta", "missing", "alpha", "alpha"),
                          stringKey("value"));
              assertEquals(1, selects.get());
              assertEquals(List.of(801L, 802L), sortedIds(simple));
              RichRow mapped =
                  simple.stream().filter(row -> row.id.equals(801L)).findFirst().orElseThrow();
              assertEquals(Status.ACTIVE, mapped.status);
              assertEquals(Priority.HIGH, mapped.priority);
              assertEquals(new Money(new BigDecimal("12.50")), mapped.amount);
              assertEquals("Madrid", mapped.address.city);
              assertEquals(PARENT_ID, mapped.parent.getId());

              List<RichRow> composite =
                  operations.findAllByBulkKey(
                      RichRow.class,
                      List.of(
                          new ValueStatusKey("alpha", "ACTIVE"),
                          new ValueStatusKey("beta", "ACTIVE")),
                      compositeKey());
              assertEquals(List.of(801L), sortedIds(composite));

              List<RichRow> converted =
                  operations.findAllByBulkKey(
                      RichRow.class, List.of(new MoneyKey(new BigDecimal("12.50"))), moneyKey());
              assertEquals(List.of(801L, 803L), sortedIds(converted));
              assertEquals(0L, currentSessionTemporaryTables());
            });
  }

  @Test
  void lookupSupportsQuotedRecordMappingOneShotDuplicatesMissingAndLargeInput() {
    AtomicInteger iterators = new AtomicInteger();
    List<String> keys = new ArrayList<>();
    keys.add("quoted");
    keys.add("quoted");
    keys.add("missing");
    for (int index = 0; index < 2_500; index++) {
      keys.add("absent-" + index);
    }
    Iterable<String> oneShot =
        () -> {
          if (iterators.incrementAndGet() != 1) {
            throw new IllegalStateException("iterator requested more than once");
          }
          return keys.iterator();
        };

    transaction()
        .executeWithoutResult(
            status -> {
              DefaultSpringDataJdbcBulkOperations<QuotedRow> operations = operations();
              operations.bulkInsert(List.of(new QuotedRow(91L, "quoted")));
              List<QuotedRow> rows =
                  operations.findAllByBulkKey(QuotedRow.class, oneShot, stringKey("Value"));
              assertEquals(List.of(new QuotedRow(91L, "quoted")), rows);
              assertEquals(1, iterators.get());
              assertEquals(0L, currentSessionTemporaryTables());
            });

    assertEquals(
        List.of(),
        DefaultSpringDataJdbcBulkOperationsIT.<QuotedRow>operations()
            .findAllByBulkKey(QuotedRow.class, List.<String>of(), stringKey("Value")));
  }

  @Test
  void lookupInteroperatesInBothDirectionsAndConcurrentCallsKeepTemporaryStateIsolated() {
    transaction()
        .executeWithoutResult(
            status -> {
              DefaultSpringDataJdbcBulkOperations<RichRow> operations = operations();
              operations.bulkInsert(List.of(simpleRichRow(871L, "insert-then-lookup")));
              assertEquals(
                  List.of(871L),
                  sortedIds(
                      operations.findAllByBulkKey(
                          RichRow.class, List.of("insert-then-lookup"), stringKey("value"))));
              operations.bulkInsert(List.of(simpleRichRow(872L, "lookup-then-insert")));
              assertEquals(
                  List.of(872L),
                  sortedIds(
                      operations.findAllByBulkKey(
                          RichRow.class, List.of("lookup-then-insert"), stringKey("value"))));
            });

    List<CompletableFuture<List<Long>>> lookups =
        List.of(concurrentLookup("insert-then-lookup"), concurrentLookup("lookup-then-insert"));
    assertEquals(List.of(871L), lookups.get(0).join());
    assertEquals(List.of(872L), lookups.get(1).join());
    assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
  }

  @Test
  void lookupEnforcesWritableTransactionsAndCharacterizesRequiresNewAndNested() {
    InvalidDataAccessApiUsageException missing =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                    .findAllByBulkKey(RichRow.class, List.of("x"), stringKey("value")));
    assertTrue(missing.getMessage().contains("active JDBC transaction"));

    TransactionTemplate readOnly = transaction();
    readOnly.setReadOnly(true);
    InvalidDataAccessApiUsageException readOnlyFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                readOnly.execute(
                    status ->
                        DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                            .findAllByBulkKey(RichRow.class, List.of("x"), stringKey("value"))));
    assertTrue(readOnlyFailure.getMessage().contains("read-only"));

    TransactionTemplate outer = transaction();
    TransactionTemplate requiresNew = transaction();
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    List<Integer> pids = new ArrayList<>();
    outer.executeWithoutResult(
        outerStatus -> {
          Integer outerPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          pids.add(outerPid);
          operations().bulkInsert(List.of(simpleRichRow(901L, "outer")));
          assertEquals(
              List.of(901L),
              sortedIds(
                  DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                      .findAllByBulkKey(RichRow.class, List.of("outer"), stringKey("value"))));
          List<PidRow> pidRows =
              DefaultSpringDataJdbcBulkOperationsIT.<PidRow>operations()
                  .findAllByBulkKey(PidRow.class, List.of("outer"), stringKey("value"));
          assertEquals(outerPid, pidRows.get(0).backendPid);
          requiresNew.executeWithoutResult(
              innerStatus -> {
                pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                operations().bulkInsert(List.of(simpleRichRow(902L, "inner")));
                assertEquals(
                    List.of(902L),
                    sortedIds(
                        DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                            .findAllByBulkKey(
                                RichRow.class, List.of("inner"), stringKey("value"))));
              });
          outerStatus.setRollbackOnly();
        });
    assertFalse(pids.get(0).equals(pids.get(1)));
    assertEquals(1, countWhere("root_rows", "id = 902"));
    assertEquals(0, countWhere("root_rows", "id = 901"));

    TransactionTemplate nested = transaction();
    nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    outer.executeWithoutResult(
        outerStatus -> {
          operations().bulkInsert(List.of(simpleRichRow(903L, "nested-root")));
          assertThrows(
              DeliberateFailure.class,
              () ->
                  nested.executeWithoutResult(
                      nestedStatus -> {
                        assertEquals(
                            List.of(903L),
                            sortedIds(
                                DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                                    .findAllByBulkKey(
                                        RichRow.class,
                                        List.of("nested-root"),
                                        stringKey("value"))));
                        throw new DeliberateFailure();
                      }));
        });
    assertEquals(1, countWhere("root_rows", "id = 903"));
  }

  @Test
  void lookupNullAndMaterializationFailuresRollbackPreserveCausesAndPoolReuse() {
    List<String> nullKeys = new ArrayList<>();
    nullKeys.add("present");
    nullKeys.add(null);
    transaction()
        .executeWithoutResult(
            status -> operations().bulkInsert(List.of(simpleRichRow(951L, "present"))));
    IllegalArgumentException nullFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                                .findAllByBulkKey(RichRow.class, nullKeys, stringKey("value"))));
    assertTrue(nullFailure.getMessage().contains("position 2"));

    IllegalArgumentException nullComponent =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                                .findAllByBulkKey(
                                    RichRow.class,
                                    java.util.Collections.singletonList(
                                        new ValueStatusKey("present", null)),
                                    compositeKey())));
    assertTrue(nullComponent.getMessage().contains("column 'status'"));
    assertTrue(nullComponent.getMessage().contains("position 1"));

    RuntimeException mapperFailure = new IllegalStateException("intentional mapper failure");
    DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory runtimeMaterializer =
        new DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory() {
          @Override
          public <E> DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> prepare(
              org.springframework.data.relational.core.mapping.RelationalPersistentEntity<E> entity,
              org.springframework.data.jdbc.core.convert.JdbcConverter converter) {
            return (connection, sql, copiedKeys) -> {
              throw mapperFailure;
            };
          }
        };
    RuntimeException observed =
        assertThrows(
            RuntimeException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            new DefaultSpringDataJdbcBulkOperations<RichRow>(
                                    jdbc, metadataResolver, runtimeMaterializer)
                                .findAllByBulkKey(
                                    RichRow.class, List.of("present"), stringKey("value"))));
    assertSame(mapperFailure, observed);

    DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory sqlMaterializer =
        new DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory() {
          @Override
          public <E> DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> prepare(
              org.springframework.data.relational.core.mapping.RelationalPersistentEntity<E> entity,
              org.springframework.data.jdbc.core.convert.JdbcConverter converter) {
            return (connection, sql, copiedKeys) -> {
              try (java.sql.PreparedStatement statement =
                  connection.prepareStatement("SELECT * FROM missing_lookup_relation")) {
                statement.executeQuery();
              }
              return List.of();
            };
          }
        };
    BulkException sqlFailure =
        assertThrows(
            BulkException.class,
            () ->
                transaction()
                    .execute(
                        status ->
                            new DefaultSpringDataJdbcBulkOperations<RichRow>(
                                    jdbc, metadataResolver, sqlMaterializer)
                                .findAllByBulkKey(
                                    RichRow.class, List.of("present"), stringKey("value"))));
    assertEquals("42P01", sqlState(sqlFailure));
    assertTrue(
        java.util.Arrays.stream(sqlFailure.getSuppressed())
            .anyMatch(failure -> "25P02".equals(sqlStateOrNull(failure))));

    transaction()
        .executeWithoutResult(
            status -> {
              assertEquals(
                  List.of(951L),
                  sortedIds(
                      DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                          .findAllByBulkKey(
                              RichRow.class, List.of("present"), stringKey("value"))));
              assertEquals(0L, currentSessionTemporaryTables());
            });
    assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
  }

  private void assertBatch(int size, long affectedRows, int batches, long offset) {
    BulkWriteResult result =
        transaction()
            .execute(
                status ->
                    operations()
                        .bulkInsert(batchRows(size, offset), BulkInsertOptions.ofBatchSize(1_000)));
    assertEquals(new BulkWriteResult(affectedRows, batches), result);
    assertEquals(size, countWhere("batch_rows", "id > " + offset));
  }

  private void assertMixedIds(List<GeneratedRow> rows) {
    InvalidDataAccessApiUsageException failure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> transaction().execute(status -> operations().bulkInsert(rows)));
    assertTrue(failure.getMessage().contains("position 2"));
    assertTrue(failure.getMessage().contains(GeneratedRow.class.getName()));
  }

  private static List<BatchRow> batchRows(int size, long offset) {
    List<BatchRow> rows = new ArrayList<>(size);
    for (int index = 1; index <= size; index++) {
      rows.add(new BatchRow(offset + index, "value-" + (offset + index)));
    }
    return rows;
  }

  private static RichRow simpleRichRow(long id, String value) {
    return new RichRow(
        id,
        value,
        Status.ACTIVE,
        Priority.LOW,
        new Money(BigDecimal.ONE),
        null,
        AggregateReference.to(PARENT_ID));
  }

  private static long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private static long countWhere(String table, String predicate) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + predicate, Long.class);
  }

  private static String sqlState(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException.getSQLState();
      }
      current = current.getCause();
    }
    throw new AssertionError("SQLException not found", failure);
  }

  private static String sqlStateOrNull(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException.getSQLState();
      }
      current = current.getCause();
    }
    return null;
  }

  private static List<Long> sortedIds(List<RichRow> rows) {
    return rows.stream().map(row -> row.id).sorted().toList();
  }

  private static BulkKeyMetadata<String> stringKey(String column) {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of(column, String.class, value -> value)));
  }

  private static BulkKeyMetadata<ValueStatusKey> compositeKey() {
    return BulkKeyMetadata.of(
        ValueStatusKey.class,
        List.of(
            ColumnMetadata.of("value", String.class, ValueStatusKey::value),
            ColumnMetadata.of("status", String.class, ValueStatusKey::status)));
  }

  private static BulkKeyMetadata<MoneyKey> moneyKey() {
    return BulkKeyMetadata.of(
        MoneyKey.class,
        List.of(ColumnMetadata.of("amount", BigDecimal.class, MoneyKey::relationalValue)));
  }

  private static long currentSessionTemporaryTables() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM pg_class WHERE relnamespace = pg_my_temp_schema()", Long.class);
  }

  private static CompletableFuture<List<Long>> concurrentLookup(String key) {
    return CompletableFuture.supplyAsync(
        () ->
            transaction()
                .execute(
                    status -> {
                      List<Long> result =
                          sortedIds(
                              DefaultSpringDataJdbcBulkOperationsIT.<RichRow>operations()
                                  .findAllByBulkKey(
                                      RichRow.class, List.of(key), stringKey("value")));
                      assertEquals(0L, currentSessionTemporaryTables());
                      return result;
                    }));
  }

  @SuppressWarnings("unchecked")
  private static DefaultSpringDataJdbcBulkOperations<RichRow> countingOperations(
      AtomicInteger selectStatements) {
    org.springframework.jdbc.core.JdbcOperations countingJdbc =
        (org.springframework.jdbc.core.JdbcOperations)
            Proxy.newProxyInstance(
                org.springframework.jdbc.core.JdbcOperations.class.getClassLoader(),
                new Class<?>[] {org.springframework.jdbc.core.JdbcOperations.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("execute")
                      && arguments != null
                      && arguments.length == 1
                      && arguments[0]
                          instanceof org.springframework.jdbc.core.ConnectionCallback<?> callback) {
                    return jdbc.execute(
                        (org.springframework.jdbc.core.ConnectionCallback<Object>)
                            connection ->
                                ((org.springframework.jdbc.core.ConnectionCallback<Object>)
                                        callback)
                                    .doInConnection(
                                        countingConnection(connection, selectStatements)));
                  }
                  try {
                    return method.invoke(jdbc, arguments);
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    return new DefaultSpringDataJdbcBulkOperations<>(countingJdbc, metadataResolver);
  }

  private static Connection countingConnection(
      Connection delegate, AtomicInteger selectStatements) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("prepareStatement")
                  && arguments != null
                  && arguments.length > 0
                  && arguments[0] instanceof String sql
                  && sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                selectStatements.incrementAndGet();
              }
              try {
                return method.invoke(delegate, arguments);
              } catch (InvocationTargetException failure) {
                throw failure.getCause();
              }
            });
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
  }

  private static <E> DefaultSpringDataJdbcBulkOperations<E> operations() {
    return new DefaultSpringDataJdbcBulkOperations<>(jdbc, metadataResolver);
  }

  private static SpringDataJdbcEntityMetadataResolver resolver() {
    JdbcCustomConversions conversions =
        new JdbcCustomConversions(
            List.of(
                MoneyConverter.INSTANCE,
                PriorityConverter.INSTANCE,
                MoneyReadingConverter.INSTANCE,
                PriorityReadingConverter.INSTANCE,
                FailingValueConverter.INSTANCE));
    JdbcMappingContext context = new JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    context.setForceQuote(true);
    context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    context.afterPropertiesSet();
    MappingJdbcConverter converter =
        new MappingJdbcConverter(
            context, (identifier, path) -> List.of(), conversions, JdbcTypeFactory.unsupported());
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  enum Status {
    ACTIVE,
    DISABLED
  }

  enum Priority {
    LOW,
    HIGH
  }

  record Money(BigDecimal value) {}

  record MoneyKey(BigDecimal relationalValue) {}

  record ValueStatusKey(String value, String status) {}

  record GeoPoint(Double latitude, Double longitude) {}

  static class Address {
    String city;

    @Embedded.Nullable(prefix = "geo_") GeoPoint point;

    Address(String city, GeoPoint point) {
      this.city = city;
      this.point = point;
    }
  }

  static class ParentRow {
    @Id UUID id;
  }

  @Table("root_rows")
  static class RichRow {
    @Id Long id;
    String value;
    Status status;
    Priority priority;
    Money amount;

    @Embedded.Nullable(prefix = "addr_") Address address;

    @Column("parent_id")
    AggregateReference<ParentRow, UUID> parent;

    RichRow(
        Long id,
        String value,
        Status status,
        Priority priority,
        Money amount,
        Address address,
        AggregateReference<ParentRow, UUID> parent) {
      this.id = id;
      this.value = value;
      this.status = status;
      this.priority = priority;
      this.amount = amount;
      this.address = address;
      this.parent = parent;
    }
  }

  @Table("root_rows")
  static class PidRow {
    @Id Long id;
    String value;

    @Column("backend_pid")
    Integer backendPid;

    PidRow(Long id, String value, Integer backendPid) {
      this.id = id;
      this.value = value;
      this.backendPid = backendPid;
    }
  }

  @Table("generated_rows")
  static class GeneratedRow {
    @Id Long id;
    String value;

    GeneratedRow(Long id, String value) {
      this.id = id;
      this.value = value;
    }
  }

  @Table("uuid_rows")
  record UuidRow(@Id UUID id, String value) {}

  @Table("batch_rows")
  record BatchRow(@Id Long id, String value) {}

  @Table(name = "Order Rows", schema = "Bulk Schema")
  record QuotedRow(@Id @Column("Id") Long id, @Column("Value") String value) {}

  record FailingValue(String value, boolean fail) {}

  @Table("converter_failure_rows")
  record ConverterFailureRow(@Id Long id, FailingValue value) {}

  @Table("accessor_failure_rows")
  @AccessType(AccessType.Type.PROPERTY)
  static class AccessorFailureRow {
    private final Long id;
    private final String value;
    @Transient private final RuntimeException failure;

    AccessorFailureRow(Long id, String value, RuntimeException failure) {
      this.id = id;
      this.value = value;
      this.failure = failure;
    }

    @Id
    public Long getId() {
      return id;
    }

    public String getValue() {
      if (failure != null) {
        throw failure;
      }
      return value;
    }
  }

  @WritingConverter
  enum MoneyConverter implements Converter<Money, BigDecimal> {
    INSTANCE;

    @Override
    public BigDecimal convert(Money source) {
      return source.value();
    }
  }

  @WritingConverter
  enum PriorityConverter implements Converter<Priority, Integer> {
    INSTANCE;

    @Override
    public Integer convert(Priority source) {
      return source == Priority.HIGH ? 9 : 1;
    }
  }

  @ReadingConverter
  enum MoneyReadingConverter implements Converter<BigDecimal, Money> {
    INSTANCE;

    @Override
    public Money convert(BigDecimal source) {
      return new Money(source);
    }
  }

  @ReadingConverter
  enum PriorityReadingConverter implements Converter<Integer, Priority> {
    INSTANCE;

    @Override
    public Priority convert(Integer source) {
      return source == 9 ? Priority.HIGH : Priority.LOW;
    }
  }

  @WritingConverter
  enum FailingValueConverter implements Converter<FailingValue, String> {
    INSTANCE;

    @Override
    public String convert(FailingValue source) {
      if (source.fail()) {
        throw new IllegalStateException("intentional converter failure");
      }
      return source.value();
    }
  }

  private static final class DeliberateFailure extends RuntimeException {}
}
