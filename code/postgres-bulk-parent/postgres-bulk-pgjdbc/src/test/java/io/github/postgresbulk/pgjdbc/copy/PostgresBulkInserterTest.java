package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PostgresBulkInserterTest {

  private static final Connection CONNECTION = noInteractionConnection();

  @ParameterizedTest
  @MethodSource("batchBoundaries")
  void partitionsBoundariesIntoExactlyOneCopyPerNonEmptyBatch(
      int itemCount, int batchSize, int expectedBatches) {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkWriteResult result =
        inserter.insert(CONNECTION, rows(itemCount), BulkInsertOptions.ofBatchSize(batchSize));

    assertEquals(new BulkWriteResult(itemCount, expectedBatches), result);
    assertEquals(expectedBatches, executor.batches.size());
  }

  @Test
  void usesDefaultOptionsAndPreservesRowOrderAcrossBatches() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkWriteResult result = inserter.insert(CONNECTION, rows(1_001));

    assertEquals(new BulkWriteResult(1_001, 2), result);
    assertTrue(executor.batches.get(0).startsWith("1,value-1\n2,value-2\n"));
    assertTrue(executor.batches.get(0).endsWith("1000,value-1000\n"));
    assertEquals("1001,value-1001\n", executor.batches.get(1));
  }

  @Test
  void obtainsExactlyOneIteratorAndConsumesItLazilyWithoutBatchLists() {
    AtomicInteger generated = new AtomicInteger();
    OneShotIterable source = new OneShotIterable(8, generated);
    List<Integer> generatedAtCopyStart = new ArrayList<>();
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    executor.beforeCopy = ignored -> generatedAtCopyStart.add(generated.get());
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkWriteResult result = inserter.insert(CONNECTION, source, BulkInsertOptions.ofBatchSize(3));

    assertEquals(new BulkWriteResult(8, 3), result);
    assertEquals(1, source.iteratorCalls());
    assertEquals(List.of(1, 4, 7), generatedAtCopyStart);
    assertEquals(8, generated.get());
  }

  @Test
  void returnsCanonicalEmptyResultWithoutCallingCopy() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkWriteResult result =
        inserter.insert(CONNECTION, List.of(), BulkInsertOptions.ofBatchSize(2));

    assertEquals(BulkWriteResult.empty(), result);
    assertEquals(0, executor.calls);
  }

  @Test
  void rejectsNullArgumentsAndNullIteratorExplicitly() {
    PostgresBulkInserter<Row> inserter =
        PostgresBulkInserter.prepare(metadata(), new RecordingCopyExecutor());

    assertThrows(
        NullPointerException.class, () -> inserter.insert(null, List.of(new Row(1, "value"))));
    assertThrows(NullPointerException.class, () -> inserter.insert(CONNECTION, null));
    assertThrows(
        NullPointerException.class,
        () -> inserter.insert(CONNECTION, List.of(new Row(1, "value")), null));
    assertThrows(
        NullPointerException.class,
        () -> inserter.insert(CONNECTION, () -> null, BulkInsertOptions.defaults()));
  }

  @Test
  void rejectsNullMetadataAndExecutorBeforeAnyOperation() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();

    assertThrows(NullPointerException.class, () -> PostgresBulkInserter.prepare(null, executor));
    assertThrows(NullPointerException.class, () -> PostgresBulkInserter.prepare(metadata(), null));
  }

  @Test
  void preparesUnsupportedMetadataBeforeConsumingAnyIterable() {
    EntityMetadata<UnsupportedRow> unsupported =
        EntityMetadata.of(
            UnsupportedRow.class,
            TableName.of("unsupported_rows"),
            List.of(ColumnMetadata.of("value", CharSequence.class, UnsupportedRow::value)));

    assertThrows(
        BulkEncodingException.class,
        () -> PostgresBulkInserter.prepare(unsupported, new RecordingCopyExecutor()));
  }

  @Test
  void rejectsNullAtFirstPositionBeforeOpeningCopy() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);
    List<Row> items = listIncludingNull(null, new Row(2, "value-2"));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> inserter.insert(CONNECTION, items, BulkInsertOptions.ofBatchSize(2)));

    assertTrue(thrown.getMessage().contains("position 1"));
    assertEquals(0, executor.calls);
  }

  @Test
  void rejectsNullAtNextBatchBoundaryWithoutOpeningAnEmptyCopy() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);
    List<Row> items = listIncludingNull(new Row(1, "value-1"), new Row(2, "value-2"), null);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> inserter.insert(CONNECTION, items, BulkInsertOptions.ofBatchSize(2)));

    assertTrue(thrown.getMessage().contains("position 3"));
    assertEquals(1, executor.calls);
    assertEquals("1,value-1\n2,value-2\n", executor.batches.get(0));
  }

  @Test
  void rejectsNullAtDefaultBatchBoundaryWithOneBasedPosition() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);
    List<Row> items = new ArrayList<>(rows(1_001));
    items.set(1_000, null);

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> inserter.insert(CONNECTION, items));

    assertTrue(thrown.getMessage().contains("position 1001"));
    assertEquals(1, executor.calls);
    assertEquals(1_000, executor.batches.get(0).lines().count());
  }

  @Test
  void permitsNullColumnValues() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkWriteResult result =
        inserter.insert(CONNECTION, List.of(new Row(1, null)), BulkInsertOptions.defaults());

    assertEquals(new BulkWriteResult(1, 1), result);
    assertEquals("1,\\N\n", executor.batches.get(0));
  }

  @Test
  void usesTheSameConnectionAndPreparedSqlForEveryBatch() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    inserter.insert(CONNECTION, rows(5), BulkInsertOptions.ofBatchSize(2));

    assertEquals(3, executor.connections.size());
    executor.connections.forEach(connection -> assertSame(CONNECTION, connection));
    assertSame(executor.copySql.get(0), executor.copySql.get(1));
    assertSame(executor.copySql.get(0), executor.copySql.get(2));
  }

  @Test
  void rejectsServerCountMismatchWithoutReplacingTheReportedValue() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    executor.reportedCountAdjustment = -1;
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () -> inserter.insert(CONNECTION, rows(2), BulkInsertOptions.ofBatchSize(2)));

    assertTrue(thrown.getMessage().contains("produced 2"));
    assertTrue(thrown.getMessage().contains("reported 1"));
  }

  @Test
  void laterCopyFailureReturnsNoResultAndPreservesCauseWithBatchContext() {
    RecordingCopyExecutor executor = new RecordingCopyExecutor();
    SQLException sqlFailure = new SQLException("constraint rejected row", "23514");
    CopyExecutionException copyFailure = new CopyExecutionException("COPY failed", sqlFailure);
    executor.failureBatch = 2;
    executor.failure = copyFailure;
    PostgresBulkInserter<Row> inserter = PostgresBulkInserter.prepare(metadata(), executor);

    BulkException thrown =
        assertThrows(
            BulkException.class,
            () -> inserter.insert(CONNECTION, rows(5), BulkInsertOptions.ofBatchSize(2)));

    assertTrue(thrown.getMessage().contains(Row.class.getName()));
    assertTrue(thrown.getMessage().contains("batch 2"));
    assertSame(copyFailure, thrown.getCause());
    assertSame(sqlFailure, thrown.getCause().getCause());
    assertEquals(2, executor.calls);
    assertEquals(1, executor.batches.size());
  }

  @Test
  void accessorRuntimeFailurePropagatesUnchanged() {
    IllegalStateException failure = new IllegalStateException("mapping unavailable");
    EntityMetadata<Row> failingMetadata =
        EntityMetadata.of(
            Row.class,
            TableName.of("rows"),
            List.of(
                ColumnMetadata.of(
                    "id",
                    Integer.class,
                    row -> {
                      throw failure;
                    })));
    PostgresBulkInserter<Row> inserter =
        PostgresBulkInserter.prepare(failingMetadata, new RecordingCopyExecutor());

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> inserter.insert(CONNECTION, List.of(new Row(1, "secret"))));

    assertSame(failure, thrown);
  }

  private static Stream<Arguments> batchBoundaries() {
    int batchSize = 3;
    return Stream.of(
        Arguments.of(0, batchSize, 0),
        Arguments.of(1, batchSize, 1),
        Arguments.of(batchSize - 1, batchSize, 1),
        Arguments.of(batchSize, batchSize, 1),
        Arguments.of(batchSize + 1, batchSize, 2),
        Arguments.of(2 * batchSize, batchSize, 2),
        Arguments.of(2 * batchSize + 1, batchSize, 3));
  }

  private static EntityMetadata<Row> metadata() {
    return EntityMetadata.of(
        Row.class,
        TableName.of("rows"),
        List.of(
            ColumnMetadata.of("id", Integer.class, Row::id),
            ColumnMetadata.of("value", String.class, Row::value)));
  }

  private static List<Row> rows(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(id -> new Row(id, "value-" + id)).toList();
  }

  @SafeVarargs
  private static <T> List<T> listIncludingNull(T... values) {
    return Arrays.asList(values);
  }

  private static Connection noInteractionConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("toString")) {
                return "no-interaction-connection";
              }
              throw new AssertionError("Unexpected JDBC call: " + method.getName());
            });
  }

  private record Row(int id, String value) {}

  private record UnsupportedRow(CharSequence value) {}

  private static final class OneShotIterable implements Iterable<Row> {

    private final int count;
    private final AtomicInteger generated;
    private int iteratorCalls;

    private OneShotIterable(int count, AtomicInteger generated) {
      this.count = count;
      this.generated = generated;
    }

    @Override
    public Iterator<Row> iterator() {
      iteratorCalls++;
      if (iteratorCalls > 1) {
        throw new IllegalStateException("iterator requested more than once");
      }
      return new Iterator<>() {
        private int nextId = 1;

        @Override
        public boolean hasNext() {
          return nextId <= count;
        }

        @Override
        public Row next() {
          int id = nextId++;
          generated.incrementAndGet();
          return new Row(id, "value-" + id);
        }
      };
    }

    private int iteratorCalls() {
      return iteratorCalls;
    }
  }

  private static final class RecordingCopyExecutor implements CopyExecutor {

    private final List<String> batches = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();
    private final List<String> copySql = new ArrayList<>();
    private IntConsumer beforeCopy = ignored -> {};
    private int reportedCountAdjustment;
    private int failureBatch = -1;
    private CopyExecutionException failure;
    private int calls;

    @Override
    public long execute(Connection connection, String sql, CopyDataWriter producer) {
      calls++;
      beforeCopy.accept(calls);
      if (calls == failureBatch) {
        throw failure;
      }

      StringWriter destination = new StringWriter();
      try {
        producer.writeTo(destination);
      } catch (IOException exception) {
        throw new AssertionError(exception);
      }
      String content = destination.toString();
      batches.add(content);
      connections.add(connection);
      copySql.add(sql);
      long produced = content.lines().count();
      return produced + reportedCountAdjustment;
    }
  }
}
