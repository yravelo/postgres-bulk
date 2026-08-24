package io.ybr.postgresbulk.springdata.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DefaultSpringDataJdbcBulkOperationsTest {

  @AfterEach
  void clearTransactionState() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void emptyInputUsesOneIteratorWithoutTransactionMetadataFactoryOrConnection() {
    AtomicInteger iterators = new AtomicInteger();
    Iterable<GeneratedRow> empty =
        () -> {
          iterators.incrementAndGet();
          return List.<GeneratedRow>of().iterator();
        };
    AtomicBoolean factoryCalled = new AtomicBoolean();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        operations(failingJdbcOperations(), failingFactory(factoryCalled));

    assertEquals(BulkWriteResult.empty(), operations.bulkInsert(empty));
    assertEquals(1, iterators.get());
    assertFalse(factoryCalled.get());
  }

  @Test
  void requiresActiveWritableLogicalTransactionBeforeMetadataOrConnection() {
    AtomicBoolean factoryCalled = new AtomicBoolean();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        operations(failingJdbcOperations(), failingFactory(factoryCalled));

    InvalidDataAccessApiUsageException missing =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> operations.bulkInsert(List.of(new GeneratedRow(null, "x"))));
    assertTrue(missing.getMessage().contains("active JDBC transaction"));

    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    InvalidDataAccessApiUsageException readOnly =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> operations.bulkInsert(List.of(new GeneratedRow(null, "x"))));
    assertTrue(readOnly.getMessage().contains("read-only transaction"));
    assertFalse(factoryCalled.get());
  }

  @Test
  void passesSameConnectionOptionsAndSinglePassRowsToPreparedEngine() {
    inWritableTransaction();
    ConnectionState connectionState = new ConnectionState(false, false);
    Connection connection = connectionState.proxy();
    AtomicInteger callbackCount = new AtomicInteger();
    JdbcOperations jdbcOperations = jdbcOperations(connection, callbackCount);
    AtomicInteger iterators = new AtomicInteger();
    List<GeneratedRow> rows =
        List.of(
            new GeneratedRow(null, "a"), new GeneratedRow(null, "b"), new GeneratedRow(null, "c"));
    Iterable<GeneratedRow> oneShot = oneShot(rows, iterators);
    BulkInsertOptions options = BulkInsertOptions.ofBatchSize(2);
    BulkWriteResult expected = new BulkWriteResult(3, 2);
    AtomicInteger factoryCount = new AtomicInteger();

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        operations(
            jdbcOperations,
            new DefaultSpringDataJdbcBulkOperations.BulkOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                factoryCount.incrementAndGet();
                assertEquals(List.of("value"), columnNames(metadata));
                return (actualConnection, actualItems, actualOptions) -> {
                  assertSame(connection, actualConnection);
                  assertSame(options, actualOptions);
                  assertEquals(rows, consume(actualItems));
                  return expected;
                };
              }
            });

    assertSame(expected, operations.bulkInsert(oneShot, options));
    assertEquals(1, iterators.get());
    assertEquals(1, factoryCount.get());
    assertEquals(1, callbackCount.get());
    assertEquals(0, connectionState.forbiddenCalls.get());
  }

  @Test
  void targetInsertPropagatesDomainOptionsTargetRowsAndSameConnection() {
    inWritableTransaction();
    ConnectionState connectionState = new ConnectionState(false, false);
    Connection connection = connectionState.proxy();
    AtomicInteger callbacks = new AtomicInteger();
    AtomicInteger iterators = new AtomicInteger();
    List<GeneratedRow> rows = List.of(new GeneratedRow(null, "target"));
    BulkInsertOptions options = BulkInsertOptions.ofBatchSize(17);
    TableName target = TableName.of("tenant_a", "generated_row");
    BulkWriteResult expected = new BulkWriteResult(1, 1);

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        operations(
            jdbcOperations(connection, callbacks),
            new DefaultSpringDataJdbcBulkOperations.BulkOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                assertEquals("generated_row", metadata.table().table());
                return new DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<>() {
                  @Override
                  public BulkWriteResult bulkInsert(
                      Connection actualConnection,
                      Iterable<? extends E> actualItems,
                      BulkInsertOptions actualOptions) {
                    throw new AssertionError("default insert must not be called");
                  }

                  @Override
                  public BulkWriteResult bulkInsert(
                      Connection actualConnection,
                      Iterable<? extends E> actualItems,
                      BulkInsertOptions actualOptions,
                      TableName actualTarget) {
                    assertSame(connection, actualConnection);
                    assertSame(options, actualOptions);
                    assertSame(target, actualTarget);
                    assertEquals(rows, consume(actualItems));
                    return expected;
                  }
                };
              }
            });

    assertSame(
        expected,
        operations.bulkInsert(GeneratedRow.class, oneShot(rows, iterators), options, target));
    assertEquals(1, iterators.get());
    assertEquals(1, callbacks.get());
    assertEquals(0, connectionState.forbiddenCalls.get());
  }

  @Test
  void targetEmptyInsertValidatesMappingWithoutTransactionFactoryOrConnection() {
    AtomicInteger iterators = new AtomicInteger();
    AtomicBoolean factoryCalled = new AtomicBoolean();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        operations(failingJdbcOperations(), failingFactory(factoryCalled));

    assertEquals(
        BulkWriteResult.empty(),
        operations.bulkInsert(
            GeneratedRow.class,
            oneShot(List.of(), iterators),
            BulkInsertOptions.defaults(),
            TableName.of("tenant_a", "generated_row")));
    assertEquals(1, iterators.get());
    assertFalse(factoryCalled.get());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            operations.bulkInsert(
                GeneratedRow.class,
                List.of(),
                BulkInsertOptions.defaults(),
                TableName.of("different_table")));
  }

  @Test
  void targetNonEmptyOperationsRejectMissingLogicalTransactionBeforeConnection() {
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> inserts =
        operations(failingJdbcOperations(), unusedFactory());
    InvalidDataAccessApiUsageException insertFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                inserts.bulkInsert(
                    GeneratedRow.class,
                    List.of(new GeneratedRow(1L, "value")),
                    BulkInsertOptions.defaults(),
                    TableName.of("tenant_a", "generated_row")));
    assertTrue(insertFailure.getMessage().contains("active JDBC transaction"));

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> lookups =
        lookupOperations(failingJdbcOperations(), unusedLookupFactory());
    InvalidDataAccessApiUsageException lookupFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                lookups.findAllByBulkKey(
                    GeneratedRow.class,
                    List.of("value"),
                    simpleKeyMetadata(),
                    TableName.of("tenant_a", "generated_row")));
    assertTrue(lookupFailure.getMessage().contains("active JDBC transaction"));
  }

  @Test
  void rejectsGeneratedThenAssignedAndAssignedThenGeneratedAtOneBasedPosition() {
    inWritableTransaction();
    assertMixedIds(List.of(new GeneratedRow(null, "generated"), new GeneratedRow(9L, "assigned")));
    assertMixedIds(List.of(new GeneratedRow(9L, "assigned"), new GeneratedRow(null, "generated")));
  }

  @Test
  void reportsNullPositionBeforeAndDuringConsumption() {
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> beforeOperations =
        operations(failingJdbcOperations(), unusedFactory());
    IllegalArgumentException first =
        assertThrows(
            IllegalArgumentException.class,
            () -> beforeOperations.bulkInsert(java.util.Collections.singletonList(null)));
    assertTrue(first.getMessage().contains("position 1"));

    inWritableTransaction();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> duringOperations =
        consumingOperations(connection(false, false));
    List<GeneratedRow> withLaterNull = new ArrayList<>();
    withLaterNull.add(new GeneratedRow(null, "first"));
    withLaterNull.add(null);
    IllegalArgumentException second =
        assertThrows(
            IllegalArgumentException.class, () -> duringOperations.bulkInsert(withLaterNull));
    assertTrue(second.getMessage().contains("position 2"));
  }

  @Test
  void preservesProducerFailureIdentityBeforeAndDuringEngineConsumption() {
    RuntimeException before = new RuntimeException("before");
    Iterable<GeneratedRow> failsBefore =
        () ->
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                throw before;
              }

              @Override
              public GeneratedRow next() {
                throw new NoSuchElementException();
              }
            };
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> beforeOperations =
        operations(failingJdbcOperations(), unusedFactory());
    assertSame(
        before,
        assertThrows(RuntimeException.class, () -> beforeOperations.bulkInsert(failsBefore)));

    RuntimeException during = new RuntimeException("during");
    Iterable<GeneratedRow> failsDuring =
        () ->
            new Iterator<>() {
              private boolean first = true;

              @Override
              public boolean hasNext() {
                if (first) {
                  return true;
                }
                throw during;
              }

              @Override
              public GeneratedRow next() {
                first = false;
                return new GeneratedRow(null, "first");
              }
            };
    inWritableTransaction();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> duringOperations =
        consumingOperations(connection(false, false));
    assertSame(
        during,
        assertThrows(RuntimeException.class, () -> duringOperations.bulkInsert(failsDuring)));
  }

  @Test
  void preservesNextFailureIdentityBeforeAndDuringEngineConsumption() {
    RuntimeException before = new RuntimeException("next before");
    Iterable<GeneratedRow> failsBefore =
        () ->
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public GeneratedRow next() {
                throw before;
              }
            };
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> beforeOperations =
        operations(failingJdbcOperations(), unusedFactory());
    assertSame(
        before,
        assertThrows(RuntimeException.class, () -> beforeOperations.bulkInsert(failsBefore)));

    RuntimeException during = new RuntimeException("next during");
    Iterable<GeneratedRow> failsDuring =
        () ->
            new Iterator<>() {
              private int nextCalls;

              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public GeneratedRow next() {
                if (++nextCalls == 2) {
                  throw during;
                }
                return new GeneratedRow(null, "first");
              }
            };
    inWritableTransaction();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> duringOperations =
        consumingOperations(connection(false, false));
    assertSame(
        during,
        assertThrows(RuntimeException.class, () -> duringOperations.bulkInsert(failsDuring)));
  }

  @Test
  void rejectsAutocommitAndPhysicalReadOnlyConnectionsWithoutCallingEngine() {
    inWritableTransaction();
    AtomicInteger engineCalls = new AtomicInteger();
    DefaultSpringDataJdbcBulkOperations.BulkOperationFactory factory =
        new DefaultSpringDataJdbcBulkOperations.BulkOperationFactory() {
          @Override
          public <E> DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<E> prepare(
              EntityMetadata<E> metadata) {
            return (connection, items, options) -> {
              engineCalls.incrementAndGet();
              return BulkWriteResult.empty();
            };
          }
        };

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> autocommit =
        operations(jdbcOperations(connection(true, false), new AtomicInteger()), factory);
    InvalidDataAccessApiUsageException autoCommitFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> autocommit.bulkInsert(List.of(new GeneratedRow(null, "x"))));
    assertTrue(autoCommitFailure.getMessage().contains("autoCommit disabled"));

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> readOnly =
        operations(jdbcOperations(connection(false, true), new AtomicInteger()), factory);
    InvalidDataAccessApiUsageException readOnlyFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> readOnly.bulkInsert(List.of(new GeneratedRow(null, "x"))));
    assertTrue(readOnlyFailure.getMessage().contains("read-only Connection"));
    assertEquals(0, engineCalls.get());
  }

  @Test
  void emptyLookupUsesOneIteratorWithoutTransactionMetadataFactoryOrConnection() {
    AtomicInteger iterators = new AtomicInteger();
    Iterable<String> empty = oneShot(List.of(), iterators);
    AtomicBoolean lookupFactoryCalled = new AtomicBoolean();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        lookupOperations(
            failingJdbcOperations(),
            new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                lookupFactoryCalled.set(true);
                throw new AssertionError("lookup factory must not be called");
              }
            });

    assertEquals(
        List.of(), operations.findAllByBulkKey(GeneratedRow.class, empty, simpleKeyMetadata()));
    assertEquals(1, iterators.get());
    assertFalse(lookupFactoryCalled.get());
  }

  @Test
  void lookupDelegatesExplicitMetadataOneShotKeysAndSameConnection() {
    inWritableTransaction();
    ConnectionState connectionState = new ConnectionState(false, false);
    Connection connection = connectionState.proxy();
    AtomicInteger callbacks = new AtomicInteger();
    AtomicInteger iterators = new AtomicInteger();
    List<String> keys = List.of("a", "b", "a");
    List<GeneratedRow> expected = List.of(new GeneratedRow(7L, "a"));
    BulkKeyMetadata<String> keyMetadata = simpleKeyMetadata();

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        lookupOperations(
            jdbcOperations(connection, callbacks),
            new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                assertEquals("generated_row", metadata.table().table());
                return new DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<>() {
                  @SuppressWarnings("unchecked")
                  @Override
                  public <K> List<E> findAllByBulkKey(
                      Connection actualConnection,
                      Iterable<? extends K> actualKeys,
                      BulkKeyMetadata<K> actualMetadata,
                      DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E>
                          materializer) {
                    assertSame(connection, actualConnection);
                    assertSame(keyMetadata, actualMetadata);
                    assertEquals(keys, consume(actualKeys));
                    return (List<E>) expected;
                  }
                };
              }
            });

    assertSame(
        expected,
        operations.findAllByBulkKey(GeneratedRow.class, oneShot(keys, iterators), keyMetadata));
    assertEquals(1, iterators.get());
    assertEquals(1, callbacks.get());
    assertEquals(0, connectionState.forbiddenCalls.get());
  }

  @Test
  void targetLookupPropagatesTargetMetadataKeysAndSameConnection() {
    inWritableTransaction();
    ConnectionState connectionState = new ConnectionState(false, false);
    Connection connection = connectionState.proxy();
    AtomicInteger callbacks = new AtomicInteger();
    AtomicInteger iterators = new AtomicInteger();
    List<String> keys = List.of("target");
    BulkKeyMetadata<String> keyMetadata = simpleKeyMetadata();
    TableName target = TableName.of("tenant_b", "generated_row");
    List<GeneratedRow> expected = List.of(new GeneratedRow(9L, "target"));

    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        lookupOperations(
            jdbcOperations(connection, callbacks),
            new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                return new DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<>() {
                  @Override
                  public <K> List<E> findAllByBulkKey(
                      Connection actualConnection,
                      Iterable<? extends K> actualKeys,
                      BulkKeyMetadata<K> actualMetadata,
                      DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E>
                          materializer) {
                    throw new AssertionError("default lookup must not be called");
                  }

                  @SuppressWarnings("unchecked")
                  @Override
                  public <K> List<E> findAllByBulkKey(
                      Connection actualConnection,
                      Iterable<? extends K> actualKeys,
                      BulkKeyMetadata<K> actualMetadata,
                      DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> materializer,
                      TableName actualTarget) {
                    assertSame(connection, actualConnection);
                    assertSame(keyMetadata, actualMetadata);
                    assertSame(target, actualTarget);
                    assertEquals(keys, consume(actualKeys));
                    return (List<E>) expected;
                  }
                };
              }
            });

    assertSame(
        expected,
        operations.findAllByBulkKey(
            GeneratedRow.class, oneShot(keys, iterators), keyMetadata, target));
    assertEquals(1, iterators.get());
    assertEquals(1, callbacks.get());
    assertEquals(0, connectionState.forbiddenCalls.get());
  }

  @Test
  void targetEmptyLookupValidatesMappingWithoutTransactionFactoryOrConnection() {
    AtomicInteger iterators = new AtomicInteger();
    AtomicBoolean lookupFactoryCalled = new AtomicBoolean();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        new DefaultSpringDataJdbcBulkOperations<>(
            failingJdbcOperations(),
            resolver(),
            unusedFactory(),
            new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
                  EntityMetadata<E> metadata) {
                lookupFactoryCalled.set(true);
                throw new AssertionError("lookup factory must not be called");
              }
            },
            new DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory() {
              @Override
              public <E> DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> prepare(
                  org.springframework.data.relational.core.mapping.RelationalPersistentEntity<E>
                      entity,
                  org.springframework.data.jdbc.core.convert.JdbcConverter converter) {
                throw new AssertionError("materializer must not be created");
              }
            });

    assertEquals(
        List.of(),
        operations.findAllByBulkKey(
            GeneratedRow.class,
            oneShot(List.of(), iterators),
            simpleKeyMetadata(),
            TableName.of("tenant_a", "generated_row")));
    assertEquals(1, iterators.get());
    assertFalse(lookupFactoryCalled.get());
  }

  @Test
  void lookupRejectsLogicalAndPhysicalTransactionViolations() {
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> missing =
        lookupOperations(failingJdbcOperations(), unusedLookupFactory());
    InvalidDataAccessApiUsageException missingFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> missing.findAllByBulkKey(GeneratedRow.class, List.of("x"), simpleKeyMetadata()));
    assertTrue(missingFailure.getMessage().contains("bulk lookup"));
    assertTrue(missingFailure.getMessage().contains("active JDBC transaction"));

    inWritableTransaction();
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> autocommit =
        lookupOperations(
            jdbcOperations(connection(true, false), new AtomicInteger()), consumingLookupFactory());
    InvalidDataAccessApiUsageException physicalFailure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () ->
                autocommit.findAllByBulkKey(GeneratedRow.class, List.of("x"), simpleKeyMetadata()));
    assertTrue(physicalFailure.getMessage().contains("autoCommit disabled"));
  }

  @Test
  void lookupReportsNullKeyPositionWithoutPreScanning() {
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> before =
        lookupOperations(failingJdbcOperations(), unusedLookupFactory());
    IllegalArgumentException first =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                before.findAllByBulkKey(
                    GeneratedRow.class,
                    java.util.Collections.singletonList(null),
                    simpleKeyMetadata()));
    assertTrue(first.getMessage().contains("position 1"));

    inWritableTransaction();
    List<String> keys = new ArrayList<>();
    keys.add("first");
    keys.add(null);
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> during =
        lookupOperations(
            jdbcOperations(connection(false, false), new AtomicInteger()),
            consumingLookupFactory());
    IllegalArgumentException second =
        assertThrows(
            IllegalArgumentException.class,
            () -> during.findAllByBulkKey(GeneratedRow.class, keys, simpleKeyMetadata()));
    assertTrue(second.getMessage().contains("position 2"));
  }

  private void assertMixedIds(List<GeneratedRow> rows) {
    DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations =
        consumingOperations(connection(false, false));
    InvalidDataAccessApiUsageException failure =
        assertThrows(InvalidDataAccessApiUsageException.class, () -> operations.bulkInsert(rows));
    assertTrue(failure.getMessage().contains("position 2"));
    assertTrue(failure.getMessage().contains(GeneratedRow.class.getName()));
    assertTrue(failure.getMessage().contains("assigned/generated"));
    assertFalse(failure.getMessage().contains("generated" + '"'));
  }

  private DefaultSpringDataJdbcBulkOperations<GeneratedRow> consumingOperations(
      Connection connection) {
    return operations(
        jdbcOperations(connection, new AtomicInteger()),
        new DefaultSpringDataJdbcBulkOperations.BulkOperationFactory() {
          @Override
          public <E> DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<E> prepare(
              EntityMetadata<E> metadata) {
            return (actualConnection, items, options) -> {
              long count = consume(items).size();
              return count == 0 ? BulkWriteResult.empty() : new BulkWriteResult(count, 1);
            };
          }
        });
  }

  private DefaultSpringDataJdbcBulkOperations<GeneratedRow> operations(
      JdbcOperations jdbcOperations,
      DefaultSpringDataJdbcBulkOperations.BulkOperationFactory factory) {
    return new DefaultSpringDataJdbcBulkOperations<>(jdbcOperations, resolver(), factory);
  }

  private DefaultSpringDataJdbcBulkOperations<GeneratedRow> lookupOperations(
      JdbcOperations jdbcOperations,
      DefaultSpringDataJdbcBulkOperations.LookupOperationFactory lookupFactory) {
    return new DefaultSpringDataJdbcBulkOperations<>(
        jdbcOperations,
        resolver(),
        unusedFactory(),
        lookupFactory,
        new DefaultSpringDataJdbcBulkOperations.ResultMaterializerFactory() {
          @Override
          public <E> DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> prepare(
              org.springframework.data.relational.core.mapping.RelationalPersistentEntity<E> entity,
              org.springframework.data.jdbc.core.convert.JdbcConverter converter) {
            return (connection, sql, copiedKeys) -> {
              throw new AssertionError("materializer must not be called by the fake lookup");
            };
          }
        });
  }

  private static DefaultSpringDataJdbcBulkOperations.LookupOperationFactory
      consumingLookupFactory() {
    return new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
      @Override
      public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
          EntityMetadata<E> metadata) {
        return new DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<>() {
          @Override
          public <K> List<E> findAllByBulkKey(
              Connection connection,
              Iterable<? extends K> keys,
              BulkKeyMetadata<K> keyMetadata,
              DefaultSpringDataJdbcBulkOperations.LookupResultMaterializer<E> materializer) {
            consume(keys);
            return List.of();
          }
        };
      }
    };
  }

  private static DefaultSpringDataJdbcBulkOperations.LookupOperationFactory unusedLookupFactory() {
    return new DefaultSpringDataJdbcBulkOperations.LookupOperationFactory() {
      @Override
      public <E> DefaultSpringDataJdbcBulkOperations.PreparedLookupOperation<E> prepare(
          EntityMetadata<E> metadata) {
        throw new AssertionError("lookup factory must not be called");
      }
    };
  }

  private static BulkKeyMetadata<String> simpleKeyMetadata() {
    return BulkKeyMetadata.of(
        String.class, List.of(ColumnMetadata.of("value", String.class, value -> value)));
  }

  private static DefaultSpringDataJdbcBulkOperations.BulkOperationFactory unusedFactory() {
    return failingFactory(new AtomicBoolean());
  }

  private static DefaultSpringDataJdbcBulkOperations.BulkOperationFactory failingFactory(
      AtomicBoolean called) {
    return new DefaultSpringDataJdbcBulkOperations.BulkOperationFactory() {
      @Override
      public <E> DefaultSpringDataJdbcBulkOperations.PreparedBulkOperation<E> prepare(
          EntityMetadata<E> metadata) {
        called.set(true);
        throw new AssertionError("factory must not be called");
      }
    };
  }

  private static void inWritableTransaction() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
  }

  private static <E> Iterable<E> oneShot(List<E> values, AtomicInteger iterators) {
    return () -> {
      if (iterators.incrementAndGet() != 1) {
        throw new IllegalStateException("second iterator");
      }
      return values.iterator();
    };
  }

  private static <E> List<E> consume(Iterable<? extends E> values) {
    List<E> result = new ArrayList<>();
    values.forEach(result::add);
    return result;
  }

  private static <E> List<String> columnNames(EntityMetadata<E> metadata) {
    return metadata.insertColumns().stream().map(column -> column.columnName()).toList();
  }

  private static JdbcOperations failingJdbcOperations() {
    return (JdbcOperations)
        Proxy.newProxyInstance(
            JdbcOperations.class.getClassLoader(),
            new Class<?>[] {JdbcOperations.class},
            (proxy, method, arguments) -> {
              throw new AssertionError("JdbcOperations must not be called: " + method.getName());
            });
  }

  @SuppressWarnings("unchecked")
  private static JdbcOperations jdbcOperations(Connection connection, AtomicInteger callbacks) {
    return (JdbcOperations)
        Proxy.newProxyInstance(
            JdbcOperations.class.getClassLoader(),
            new Class<?>[] {JdbcOperations.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("execute")
                  && arguments != null
                  && arguments.length == 1
                  && arguments[0] instanceof ConnectionCallback<?> callback) {
                callbacks.incrementAndGet();
                return ((ConnectionCallback<Object>) callback).doInConnection(connection);
              }
              throw new AssertionError("Unexpected JdbcOperations call: " + method);
            });
  }

  private static Connection connection(boolean autoCommit, boolean readOnly) {
    return new ConnectionState(autoCommit, readOnly).proxy();
  }

  private static SpringDataJdbcEntityMetadataResolver resolver() {
    JdbcCustomConversions conversions = new JdbcCustomConversions();
    JdbcMappingContext context = new JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    context.setForceQuote(true);
    context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    context.afterPropertiesSet();
    MappingJdbcConverter converter =
        new MappingJdbcConverter(
            context, (identifier, path) -> List.of(), conversions, JdbcTypeFactory.unsupported());
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  private static final class ConnectionState {

    private final boolean autoCommit;
    private final boolean readOnly;
    private final AtomicInteger forbiddenCalls = new AtomicInteger();

    private ConnectionState(boolean autoCommit, boolean readOnly) {
      this.autoCommit = autoCommit;
      this.readOnly = readOnly;
    }

    private Connection proxy() {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, arguments) -> {
                return switch (method.getName()) {
                  case "getAutoCommit" -> autoCommit;
                  case "isReadOnly" -> readOnly;
                  case "toString" -> "test-connection";
                  case "close",
                      "commit",
                      "rollback",
                      "setAutoCommit",
                      "setReadOnly",
                      "setTransactionIsolation",
                      "setSchema",
                      "setSavepoint",
                      "releaseSavepoint" -> {
                    forbiddenCalls.incrementAndGet();
                    throw new AssertionError("connection ownership violation: " + method.getName());
                  }
                  default -> throw new AssertionError("Unexpected Connection call: " + method);
                };
              });
    }
  }

  static class GeneratedRow {
    @Id Long id;
    String value;

    GeneratedRow(Long id, String value) {
      this.id = id;
      this.value = value;
    }
  }
}
