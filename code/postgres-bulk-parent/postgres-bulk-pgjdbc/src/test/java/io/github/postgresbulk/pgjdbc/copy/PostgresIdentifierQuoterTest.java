package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgresIdentifierQuoterTest {

  @Test
  void quotesEveryIdentifierAndEscapesEmbeddedQuotes() {
    assertEquals("\"simple\"", PostgresIdentifierQuoter.quote("simple"));
    assertEquals("\"Order Details\"", PostgresIdentifierQuoter.quote("Order Details"));
    assertEquals("\"a\"\"b\"", PostgresIdentifierQuoter.quote("a\"b"));
    assertEquals("\"schema.table\"", PostgresIdentifierQuoter.quote("schema.table"));
  }

  @Test
  void rejectsInvalidIdentifierComponents() {
    assertThrows(NullPointerException.class, () -> PostgresIdentifierQuoter.quote(null));
    assertThrows(IllegalArgumentException.class, () -> PostgresIdentifierQuoter.quote(" \t"));
    assertThrows(IllegalArgumentException.class, () -> PostgresIdentifierQuoter.quote("bad\0name"));
  }
}
