package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporaryTableNameGeneratorTest {

  @Test
  void createsBoundedLowercaseAsciiCollisionResistantNames() {
    Set<String> names = new HashSet<>();

    for (int index = 0; index < 1_000; index++) {
      String name = TemporaryTableNameGenerator.randomName();
      assertTrue(name.matches("pgbulk_keys_[0-9a-f]{32}"));
      assertEquals(44, name.getBytes(StandardCharsets.UTF_8).length);
      assertTrue(names.add(name));
    }
  }

  @Test
  void acceptsThePostgresByteLimit() {
    String name = "a".repeat(TemporaryTableNameGenerator.POSTGRES_IDENTIFIER_MAX_BYTES);

    assertEquals(name, TemporaryTableNameGenerator.next(() -> name));
  }

  @Test
  void rejectsNullUnsafeAndOverlongSupplierResults() {
    assertThrows(NullPointerException.class, () -> TemporaryTableNameGenerator.next(null));
    assertThrows(IllegalStateException.class, () -> TemporaryTableNameGenerator.next(() -> null));
    assertThrows(IllegalStateException.class, () -> TemporaryTableNameGenerator.next(() -> "A"));
    assertThrows(
        IllegalStateException.class, () -> TemporaryTableNameGenerator.next(() -> "bad-name"));
    assertThrows(
        IllegalStateException.class, () -> TemporaryTableNameGenerator.next(() -> "a".repeat(64)));
  }
}
