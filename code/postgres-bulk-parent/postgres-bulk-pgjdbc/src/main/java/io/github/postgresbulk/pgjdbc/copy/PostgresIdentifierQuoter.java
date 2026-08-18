package io.github.postgresbulk.pgjdbc.copy;

import java.util.Objects;

/** Quotes one structured PostgreSQL identifier component without interpreting SQL. */
final class PostgresIdentifierQuoter {

  private PostgresIdentifierQuoter() {}

  static String quote(String identifier) {
    Objects.requireNonNull(identifier, "identifier must not be null");
    if (identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    if (identifier.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("identifier must not contain NUL");
    }
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }
}
