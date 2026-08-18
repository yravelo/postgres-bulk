package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TemporaryTableBulkLookupTest {

  private static final String TEMPORARY_TABLE = "pgbulk_keys_test";

  @Test
  void emptyInputReturnsExplicitResultWithoutAnyJdbcOrQueryWork() {
    RecordingConnection jdbc = new RecordingConnection(false);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();
    OneShotIterable keys = new OneShotIterable(0, new AtomicInteger());
    List<String> empty = List.of("explicit-empty");
    AtomicBoolean queried = new AtomicBoolean();

    List<String> result =
        lookup(copy)
            .lookup(
                jdbc.connection,
                keys,
                empty,
                (connection, sql, count) -> {
                  queried.set(true);
                  return List.of();
                });

    assertSame(empty, result);
    assertEquals(1, keys.iteratorCalls);
    assertEquals(0, jdbc.autoCommitReads);
    assertEquals(0, jdbc.statementCalls);
    assertEquals(0, copy.calls);
    assertFalse(queried.get());
  }

  @Test
  void streamsTwentyThousandKeysThroughOneCopyAndTheSameConnection() {
    AtomicInteger generated = new AtomicInteger();
    OneShotIterable keys = new OneShotIterable(20_000, generated);
    RecordingConnection jdbc = new RecordingConnection(false);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();
    copy.beforeWrite = () -> assertEquals(1, generated.get());

    long result =
        lookup(copy)
            .lookup(
                jdbc.connection,
                keys,
                -1L,
                (connection, sql, copiedKeys) -> {
                  assertSame(jdbc.connection, connection);
                  assertTrue(sql.contains("SELECT DISTINCT \"id\""));
                  return copiedKeys;
                });

    assertEquals(20_000L, result);
    assertEquals(20_000, generated.get());
    assertEquals(1, keys.iteratorCalls);
    assertEquals(1, copy.calls);
    assertSame(jdbc.connection, copy.connection);
    assertEquals(20_000, copy.content.lines().count());
    assertEquals(
        List.of(
            "CREATE TEMP TABLE \"pgbulk_keys_test\" ON COMMIT DROP AS SELECT \"id\" FROM \"lookup_rows\" WITH NO DATA",
            "DROP TABLE IF EXISTS \"pgbulk_keys_test\""),
        jdbc.executedSql);
  }

  @Test
  void copiesDuplicateKeysAndReliesOnDistinctJoinSemantics() {
    RecordingConnection jdbc = new RecordingConnection(false);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();

    String select =
        lookup(copy)
            .lookup(
                jdbc.connection,
                List.of(7, 7, 9),
                "empty",
                (connection, sql, copiedKeys) -> {
                  assertEquals(3, copiedKeys);
                  return sql;
                });

    assertEquals("7\n7\n9\n", copy.content);
    assertTrue(select.contains("JOIN (SELECT DISTINCT \"id\""));
  }

  @Test
  void rejectsAutocommitBeforeDdlOrCopy() {
    RecordingConnection jdbc = new RecordingConnection(true);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> lookup(copy).lookup(jdbc.connection, List.of(1), List.of(), unusedQuery()));

    assertTrue(thrown.getMessage().contains("autoCommit=false"));
    assertEquals(1, jdbc.autoCommitReads);
    assertEquals(0, jdbc.statementCalls);
    assertEquals(0, copy.calls);
  }

  @Test
  void rejectsNullElementAtFirstPositionBeforeJdbcAndLaterPositionWithCleanup() {
    RecordingConnection firstJdbc = new RecordingConnection(false);
    RecordingCopyExecutor firstCopy = new RecordingCopyExecutor();
    List<Integer> firstNull = listIncludingNull(null, 2);

    IllegalArgumentException firstFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                lookup(firstCopy)
                    .lookup(firstJdbc.connection, firstNull, List.of(), unusedQuery()));

    assertTrue(firstFailure.getMessage().contains("position 1"));
    assertEquals(0, firstJdbc.autoCommitReads);
    assertEquals(0, firstJdbc.statementCalls);
    assertEquals(0, firstCopy.calls);

    RecordingConnection laterJdbc = new RecordingConnection(false);
    RecordingCopyExecutor laterCopy = new RecordingCopyExecutor();
    List<Integer> laterNull = listIncludingNull(1, null);

    IllegalArgumentException laterFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                lookup(laterCopy)
                    .lookup(laterJdbc.connection, laterNull, List.of(), unusedQuery()));

    assertTrue(laterFailure.getMessage().contains("position 2"));
    assertEquals(1, laterCopy.calls);
    assertEquals("1\n", laterCopy.content);
    assertTrue(laterJdbc.executedSql.get(1).startsWith("DROP TABLE"));
  }

  @Test
  void rejectsNullCompositeComponentWithColumnAndPositionWithoutLeakingItsValue() {
    RecordingConnection jdbc = new RecordingConnection(false);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();
    BulkKeyMetadata<CompositeKey> metadata =
        BulkKeyMetadata.of(
            CompositeKey.class,
            List.of(
                ColumnMetadata.of("tenant", String.class, CompositeKey::tenant),
                ColumnMetadata.of("secret_code", Integer.class, CompositeKey::code)));
    TemporaryTableBulkLookup<CompositeKey> lookup =
        TemporaryTableBulkLookup.prepare(
            TableName.of("lookup_rows"), metadata, copy, () -> TEMPORARY_TABLE);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                lookup.lookup(
                    jdbc.connection,
                    List.of(new CompositeKey("tenant-secret", null)),
                    List.of(),
                    unusedQuery()));

    assertTrue(thrown.getMessage().contains("position 1"));
    assertTrue(thrown.getMessage().contains("secret_code"));
    assertFalse(thrown.getMessage().contains("tenant-secret"));
    assertEquals(2, jdbc.executedSql.size());
  }

  @Test
  void countMismatchFailsAndDropsTemporaryTable() {
    RecordingConnection jdbc = new RecordingConnection(false);
    RecordingCopyExecutor copy = new RecordingCopyExecutor();
    copy.reportedAdjustment = -1;

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () -> lookup(copy).lookup(jdbc.connection, List.of(1, 2), List.of(), unusedQuery()));

    assertTrue(thrown.getMessage().contains("produced 2"));
    assertTrue(thrown.getMessage().contains("reported 1"));
    assertEquals(2, jdbc.executedSql.size());
  }

  @Test
  void copyFailurePreservesCauseAndSuppressesCleanupFailure() {
    RecordingConnection jdbc = new RecordingConnection(false);
    SQLException sqlFailure = new SQLException("domain rejected key", "23514");
    jdbc.dropFailure = new SQLException("transaction aborted", "25P02");
    RecordingCopyExecutor copy = new RecordingCopyExecutor();
    CopyExecutionException copyFailure = new CopyExecutionException("COPY failed", sqlFailure);
    copy.failure = copyFailure;

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () -> lookup(copy).lookup(jdbc.connection, List.of(1), List.of(), unusedQuery()));

    assertSame(copyFailure, thrown.getCause());
    assertSame(sqlFailure, thrown.getCause().getCause());
    assertEquals("23514", ((SQLException) thrown.getCause().getCause()).getSQLState());
    assertEquals(1, thrown.getSuppressed().length);
    assertSame(jdbc.dropFailure, thrown.getSuppressed()[0]);
  }

  @Test
  void sqlQueryFailureAndRuntimeQueryFailurePreservePrimaryIdentityAndCleanup() {
    RecordingConnection sqlJdbc = new RecordingConnection(false);
    SQLException sqlFailure = new SQLException("query failed", "42601");

    BulkException wrapped =
        assertThrows(
            BulkException.class,
            () ->
                lookup(new RecordingCopyExecutor())
                    .lookup(
                        sqlJdbc.connection,
                        List.of(1),
                        List.of(),
                        (connection, sql, count) -> {
                          throw sqlFailure;
                        }));

    assertSame(sqlFailure, wrapped.getCause());
    assertEquals(2, sqlJdbc.executedSql.size());

    RecordingConnection runtimeJdbc = new RecordingConnection(false);
    IllegalStateException runtimeFailure = new IllegalStateException("mapper failed");
    IllegalStateException unchanged =
        assertThrows(
            IllegalStateException.class,
            () ->
                lookup(new RecordingCopyExecutor())
                    .lookup(
                        runtimeJdbc.connection,
                        List.of(1),
                        List.of(),
                        (connection, sql, count) -> {
                          throw runtimeFailure;
                        }));

    assertSame(runtimeFailure, unchanged);
    assertEquals(2, runtimeJdbc.executedSql.size());
  }

  @Test
  void cleanupFailureAfterSuccessIsReported() {
    RecordingConnection jdbc = new RecordingConnection(false);
    jdbc.dropFailure = new SQLException("drop failed", "42501");

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () ->
                lookup(new RecordingCopyExecutor())
                    .lookup(
                        jdbc.connection,
                        List.of(1),
                        List.of(),
                        (connection, sql, count) -> List.of(1)));

    assertSame(jdbc.dropFailure, thrown.getCause());
    assertTrue(thrown.getMessage().contains("drop temporary key table"));
  }

  @Test
  void createFailureIsPrimaryAndDoesNotAttemptDropOrCopy() {
    RecordingConnection jdbc = new RecordingConnection(false);
    jdbc.createFailure = new SQLException("create denied", "42501");
    RecordingCopyExecutor copy = new RecordingCopyExecutor();

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () -> lookup(copy).lookup(jdbc.connection, List.of(1), List.of(), unusedQuery()));

    assertSame(jdbc.createFailure, thrown.getCause());
    assertEquals(1, jdbc.executedSql.size());
    assertTrue(jdbc.executedSql.get(0).startsWith("CREATE TEMP TABLE"));
    assertEquals(0, copy.calls);
  }

  @Test
  void iteratorFailuresBeforeAndDuringCopyPreserveIdentityAndCleanupBoundaries() {
    IllegalStateException beforeFailure = new IllegalStateException("initial hasNext failed");
    RecordingConnection beforeJdbc = new RecordingConnection(false);
    RecordingCopyExecutor beforeCopy = new RecordingCopyExecutor();
    Iterable<Integer> before =
        () ->
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                throw beforeFailure;
              }

              @Override
              public Integer next() {
                throw new AssertionError("next must not be called");
              }
            };

    assertSame(
        beforeFailure,
        assertThrows(
            IllegalStateException.class,
            () ->
                lookup(beforeCopy)
                    .lookup(beforeJdbc.connection, before, List.of(), unusedQuery())));
    assertEquals(0, beforeJdbc.statementCalls);
    assertEquals(0, beforeCopy.calls);

    IllegalArgumentException duringFailure = new IllegalArgumentException("next failed");
    RecordingConnection duringJdbc = new RecordingConnection(false);
    RecordingCopyExecutor duringCopy = new RecordingCopyExecutor();
    Iterable<Integer> during =
        () ->
            new Iterator<>() {
              private int nextCalls;

              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public Integer next() {
                if (++nextCalls == 2) {
                  throw duringFailure;
                }
                return 1;
              }
            };

    assertSame(
        duringFailure,
        assertThrows(
            IllegalArgumentException.class,
            () ->
                lookup(duringCopy)
                    .lookup(duringJdbc.connection, during, List.of(), unusedQuery())));
    assertEquals(1, duringCopy.calls);
    assertTrue(duringJdbc.executedSql.get(1).startsWith("DROP TABLE"));
  }

  @Test
  void rejectsNullArgumentsAndPreparationDependencies() {
    TemporaryTableBulkLookup<Integer> lookup = lookup(new RecordingCopyExecutor());
    RecordingConnection jdbc = new RecordingConnection(false);

    assertThrows(
        NullPointerException.class, () -> lookup.lookup(null, List.of(), List.of(), unusedQuery()));
    assertThrows(
        NullPointerException.class,
        () -> lookup.lookup(jdbc.connection, null, List.of(), unusedQuery()));
    assertThrows(
        NullPointerException.class,
        () -> lookup.lookup(jdbc.connection, List.of(), null, unusedQuery()));
    assertThrows(
        NullPointerException.class,
        () -> lookup.lookup(jdbc.connection, List.of(), List.of(), null));
    assertThrows(
        NullPointerException.class,
        () -> lookup.lookup(jdbc.connection, () -> null, List.of(), unusedQuery()));
    assertThrows(
        NullPointerException.class, () -> TemporaryTableBulkLookup.prepare(null, simpleKey()));
    assertThrows(
        NullPointerException.class,
        () -> TemporaryTableBulkLookup.prepare(TableName.of("lookup_rows"), null));
    assertThrows(
        NullPointerException.class,
        () ->
            TemporaryTableBulkLookup.prepare(
                TableName.of("lookup_rows"), simpleKey(), null, () -> TEMPORARY_TABLE));
    assertThrows(
        NullPointerException.class,
        () ->
            TemporaryTableBulkLookup.prepare(
                TableName.of("lookup_rows"), simpleKey(), new RecordingCopyExecutor(), null));
  }

  private static TemporaryTableBulkLookup<Integer> lookup(CopyExecutor executor) {
    return TemporaryTableBulkLookup.prepare(
        TableName.of("lookup_rows"), simpleKey(), executor, () -> TEMPORARY_TABLE);
  }

  private static BulkKeyMetadata<Integer> simpleKey() {
    return BulkKeyMetadata.of(
        Integer.class, List.of(ColumnMetadata.of("id", Integer.class, value -> value)));
  }

  private static <R> LookupQuery<R> unusedQuery() {
    return (connection, sql, copiedKeys) -> {
      throw new AssertionError("query must not be called");
    };
  }

  @SafeVarargs
  private static <T> List<T> listIncludingNull(T... values) {
    return Arrays.asList(values);
  }

  private record CompositeKey(String tenant, Integer code) {}

  private static final class OneShotIterable implements Iterable<Integer> {

    private final int count;
    private final AtomicInteger generated;
    private int iteratorCalls;

    private OneShotIterable(int count, AtomicInteger generated) {
      this.count = count;
      this.generated = generated;
    }

    @Override
    public Iterator<Integer> iterator() {
      iteratorCalls++;
      if (iteratorCalls > 1) {
        throw new IllegalStateException("iterator requested more than once");
      }
      return new Iterator<>() {
        private int next = 1;

        @Override
        public boolean hasNext() {
          return next <= count;
        }

        @Override
        public Integer next() {
          generated.incrementAndGet();
          return next++;
        }
      };
    }
  }

  private static final class RecordingCopyExecutor implements CopyExecutor {

    private Runnable beforeWrite = () -> {};
    private int reportedAdjustment;
    private CopyExecutionException failure;
    private Connection connection;
    private String content = "";
    private int calls;

    @Override
    public long execute(Connection connection, String copySql, CopyDataWriter producer) {
      calls++;
      this.connection = connection;
      if (failure != null) {
        throw failure;
      }
      beforeWrite.run();
      StringWriter destination = new StringWriter();
      try {
        producer.writeTo(destination);
      } catch (IOException failure) {
        throw new AssertionError(failure);
      } finally {
        content = destination.toString();
      }
      return content.lines().count() + reportedAdjustment;
    }
  }

  private static final class RecordingConnection {

    private final List<String> executedSql = new ArrayList<>();
    private final boolean autoCommit;
    private final Connection connection;
    private SQLException createFailure;
    private SQLException dropFailure;
    private int autoCommitReads;
    private int statementCalls;

    private RecordingConnection(boolean autoCommit) {
      this.autoCommit = autoCommit;
      connection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, arguments) -> {
                    return switch (method.getName()) {
                      case "getAutoCommit" -> {
                        autoCommitReads++;
                        yield this.autoCommit;
                      }
                      case "createStatement" -> {
                        statementCalls++;
                        yield statement();
                      }
                      case "isClosed" -> false;
                      case "toString" -> "recording-connection";
                      case "unwrap" -> null;
                      case "isWrapperFor" -> false;
                      case "close", "commit", "rollback", "setAutoCommit", "setReadOnly" ->
                          throw new AssertionError(
                              "connection ownership violation: " + method.getName());
                      default ->
                          throw new AssertionError("Unexpected JDBC call: " + method.getName());
                    };
                  });
    }

    private Statement statement() {
      return (Statement)
          Proxy.newProxyInstance(
              Statement.class.getClassLoader(),
              new Class<?>[] {Statement.class},
              (proxy, method, arguments) -> {
                return switch (method.getName()) {
                  case "execute" -> {
                    String sql = (String) arguments[0];
                    executedSql.add(sql);
                    if (sql.startsWith("CREATE TEMP TABLE") && createFailure != null) {
                      throw createFailure;
                    }
                    if (sql.startsWith("DROP TABLE") && dropFailure != null) {
                      throw dropFailure;
                    }
                    yield true;
                  }
                  case "close" -> null;
                  case "isClosed" -> false;
                  case "toString" -> "recording-statement";
                  default ->
                      throw new AssertionError("Unexpected Statement call: " + method.getName());
                };
              });
    }
  }
}
