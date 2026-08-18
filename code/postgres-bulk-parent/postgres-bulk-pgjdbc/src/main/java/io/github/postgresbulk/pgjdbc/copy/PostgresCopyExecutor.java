package io.github.postgresbulk.pgjdbc.copy;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;

/** Executes one caller-supplied COPY command against a caller-owned JDBC connection. */
final class PostgresCopyExecutor {

  private static final int COPY_BUFFER_SIZE = 64 * 1024;

  long execute(Connection connection, String copySql, CopyDataWriter producer) {
    Objects.requireNonNull(connection, "connection must not be null");
    Objects.requireNonNull(copySql, "copySql must not be null");
    Objects.requireNonNull(producer, "producer must not be null");
    if (copySql.isBlank()) {
      throw new IllegalArgumentException("copySql must not be blank");
    }

    PGConnection pgConnection;
    try {
      pgConnection = connection.unwrap(PGConnection.class);
    } catch (SQLException failure) {
      throw new CopyExecutionException("Could not unwrap JDBC connection as PGConnection", failure);
    }
    if (pgConnection == null) {
      throw new CopyExecutionException(
          "JDBC connection returned null when unwrapped as PGConnection");
    }

    CopyIn copy;
    try {
      copy = pgConnection.getCopyAPI().copyIn(copySql);
    } catch (SQLException failure) {
      throw new CopyExecutionException("Could not start PostgreSQL COPY FROM STDIN", failure);
    }

    PGCopyOutputStream byteDestination = new PGCopyOutputStream(copy, COPY_BUFFER_SIZE);
    Writer characterDestination = new OutputStreamWriter(byteDestination, StandardCharsets.UTF_8);
    try {
      producer.writeTo(characterDestination);
      characterDestination.flush();
      return byteDestination.endCopy();
    } catch (IOException | SQLException | RuntimeException | Error failure) {
      cancel(copy, failure);
      throw new CopyExecutionException("PostgreSQL COPY FROM STDIN failed", failure);
    }
  }

  private static void cancel(CopyIn copy, Throwable primaryFailure) {
    try {
      if (copy.isActive()) {
        copy.cancelCopy();
      }
    } catch (SQLException cleanupFailure) {
      primaryFailure.addSuppressed(cleanupFailure);
    }
  }
}
