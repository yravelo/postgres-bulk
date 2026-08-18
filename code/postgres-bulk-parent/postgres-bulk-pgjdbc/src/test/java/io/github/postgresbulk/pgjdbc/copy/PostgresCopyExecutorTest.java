package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

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
}
