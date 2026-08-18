package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.Encoding;
import org.postgresql.util.ByteStreamWriter;

class PostgresCopyExecutorTest {

  private final PostgresCopyExecutor executor = new PostgresCopyExecutor();

  @Test
  void validatesArgumentsBeforeInteractingWithJdbc() {
    Connection connection = connectionReturning(null);

    assertThrows(NullPointerException.class, () -> executor.execute(null, "COPY x", writer -> {}));
    assertThrows(
        NullPointerException.class, () -> executor.execute(connection, null, writer -> {}));
    assertThrows(NullPointerException.class, () -> executor.execute(connection, "COPY x", null));
    assertThrows(
        IllegalArgumentException.class, () -> executor.execute(connection, " \t", writer -> {}));
  }

  @Test
  void retainsUnwrapFailureAsCause() {
    SQLException failure = new SQLException("not a PostgreSQL connection");
    Connection connection = connectionThrowing(failure);

    CopyExecutionException thrown =
        assertThrows(
            CopyExecutionException.class,
            () -> executor.execute(connection, "COPY x FROM STDIN", writer -> {}));

    assertSame(failure, thrown.getCause());
  }

  @Test
  void rejectsNullUnwrapResult() {
    Connection connection = connectionReturning(null);

    assertThrows(
        CopyExecutionException.class,
        () -> executor.execute(connection, "COPY x FROM STDIN", writer -> {}));
  }

  @Test
  void retainsCopyStartupFailureAndSqlState() throws Exception {
    SQLException startupFailure = new SQLException("missing table", "42P01");
    Connection connection = copyConnection(new StubCopyManager(startupFailure));

    CopyExecutionException thrown =
        assertThrows(
            CopyExecutionException.class,
            () -> executor.execute(connection, "COPY missing FROM STDIN", writer -> {}));

    assertSame(startupFailure, thrown.getCause());
    assertTrue(thrown.getMessage().contains("start"));
  }

  @Test
  void endCopyFailureIsPrimaryAndTriggersCancellation() throws Exception {
    SQLException endFailure = new SQLException("end failed", "23514");
    StubCopyIn copy = new StubCopyIn();
    copy.endFailure = endFailure;

    CopyExecutionException thrown =
        assertThrows(
            CopyExecutionException.class,
            () ->
                executor.execute(
                    copyConnection(new StubCopyManager(copy)),
                    "COPY x FROM STDIN",
                    writer -> writer.write("1\n")));

    assertSame(endFailure, thrown.getCause());
    assertTrue(copy.cancelled);
  }

  @Test
  void cancellationFailureIsSuppressedWithoutReplacingProducerFailure() throws Exception {
    IllegalStateException producerFailure = new IllegalStateException("iterator failed");
    SQLException cancelFailure = new SQLException("cancel failed", "08006");
    StubCopyIn copy = new StubCopyIn();
    copy.cancelFailure = cancelFailure;

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                executor.execute(
                    copyConnection(new StubCopyManager(copy)),
                    "COPY x FROM STDIN",
                    writer -> {
                      writer.write("1\n");
                      throw producerFailure;
                    }));

    assertSame(producerFailure, thrown);
    assertSame(cancelFailure, thrown.getSuppressed()[0]);
  }

  @Test
  void writeFailureRetainsSqlExceptionInCauseChainAndCancels() throws Exception {
    SQLException writeFailure = new SQLException("backend lost", "08006");
    StubCopyIn copy = new StubCopyIn();
    copy.writeFailure = writeFailure;

    CopyExecutionException thrown =
        assertThrows(
            CopyExecutionException.class,
            () ->
                executor.execute(
                    copyConnection(new StubCopyManager(copy)),
                    "COPY x FROM STDIN",
                    writer -> writer.write("1\n")));

    assertTrue(hasCause(thrown, writeFailure));
    assertTrue(copy.cancelled);
  }

  private static Connection connectionThrowing(SQLException failure) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("unwrap")) {
                throw failure;
              }
              throw new AssertionError("Unexpected JDBC call: " + method.getName());
            });
  }

  private static Connection connectionReturning(Object unwrapped) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("unwrap")) {
                return unwrapped;
              }
              throw new AssertionError("Unexpected JDBC call: " + method.getName());
            });
  }

  private static Connection copyConnection(CopyManager copyManager) {
    PGConnection pgConnection =
        (PGConnection)
            Proxy.newProxyInstance(
                PGConnection.class.getClassLoader(),
                new Class<?>[] {PGConnection.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("getCopyAPI")) {
                    return copyManager;
                  }
                  throw new AssertionError("Unexpected PGConnection call: " + method.getName());
                });
    return connectionReturning(pgConnection);
  }

  private static BaseConnection baseConnection() {
    return (BaseConnection)
        Proxy.newProxyInstance(
            BaseConnection.class.getClassLoader(),
            new Class<?>[] {BaseConnection.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getEncoding" -> Encoding.defaultEncoding();
                  case "getQueryExecutor" -> null;
                  default -> throw new AssertionError("Unexpected BaseConnection call");
                });
  }

  private static boolean hasCause(Throwable failure, Throwable expected) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current == expected) {
        return true;
      }
    }
    return false;
  }

  private static final class StubCopyManager extends CopyManager {

    private final CopyIn copy;
    private final SQLException failure;

    private StubCopyManager(CopyIn copy) throws SQLException {
      super(baseConnection());
      this.copy = copy;
      this.failure = null;
    }

    private StubCopyManager(SQLException failure) throws SQLException {
      super(baseConnection());
      this.copy = null;
      this.failure = failure;
    }

    @Override
    public CopyIn copyIn(String sql) throws SQLException {
      if (failure != null) {
        throw failure;
      }
      return copy;
    }
  }

  private static final class StubCopyIn implements CopyIn {

    private SQLException writeFailure;
    private SQLException endFailure;
    private SQLException cancelFailure;
    private boolean active = true;
    private boolean cancelled;

    @Override
    public void writeToCopy(byte[] bytes, int offset, int size) throws SQLException {
      if (writeFailure != null) {
        throw writeFailure;
      }
    }

    @Override
    public void writeToCopy(ByteStreamWriter writer) throws SQLException {
      throw new AssertionError("Unexpected ByteStreamWriter call");
    }

    @Override
    public void flushCopy() throws SQLException {
      if (writeFailure != null) {
        throw writeFailure;
      }
    }

    @Override
    public long endCopy() throws SQLException {
      if (endFailure != null) {
        throw endFailure;
      }
      active = false;
      return 1;
    }

    @Override
    public int getFieldCount() {
      return 1;
    }

    @Override
    public int getFormat() {
      return 0;
    }

    @Override
    public int getFieldFormat(int field) {
      return 0;
    }

    @Override
    public boolean isActive() {
      return active;
    }

    @Override
    public void cancelCopy() throws SQLException {
      cancelled = true;
      if (cancelFailure != null) {
        throw cancelFailure;
      }
      active = false;
    }

    @Override
    public long getHandledRowCount() {
      return -1;
    }
  }
}
