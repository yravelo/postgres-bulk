package io.ybr.postgresbulk.pgjdbc.copy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/** Generates bounded, collision-resistant names for session-local lookup tables. */
final class TemporaryTableNameGenerator {

  static final int POSTGRES_IDENTIFIER_MAX_BYTES = 63;
  private static final String PREFIX = "pgbulk_keys_";

  private TemporaryTableNameGenerator() {}

  static String randomName() {
    return PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  static String next(Supplier<String> names) {
    String name = names.get();
    if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
      throw new IllegalStateException(
          "temporary table name must be lowercase ASCII identifier text");
    }
    if (name.getBytes(StandardCharsets.UTF_8).length > POSTGRES_IDENTIFIER_MAX_BYTES) {
      throw new IllegalStateException(
          "temporary table name must not exceed " + POSTGRES_IDENTIFIER_MAX_BYTES + " UTF-8 bytes");
    }
    return name;
  }
}
