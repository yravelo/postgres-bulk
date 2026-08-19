package io.ybr.postgresbulk.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TableNameTest {

  @Test
  void representsTableWithoutSchema() {
    TableName table = TableName.of("product");

    assertFalse(table.schema().isPresent());
    assertEquals("product", table.table());
    assertEquals(TableName.of("product"), table);
    assertEquals(TableName.of("product").hashCode(), table.hashCode());
  }

  @Test
  void representsSchemaAndTableWithoutParsingOrNormalization() {
    TableName table = TableName.of("Sales.Schema", "Order Items");

    assertEquals("Sales.Schema", table.schema().orElseThrow());
    assertEquals("Order Items", table.table());
    assertEquals("TableName[schema=Sales.Schema, table=Order Items]", table.toString());
  }

  @Test
  void rejectsNullComponents() {
    assertThrows(NullPointerException.class, () -> TableName.of((String) null));
    assertThrows(NullPointerException.class, () -> TableName.of(null, "product"));
    assertThrows(NullPointerException.class, () -> TableName.of("sales", null));
  }

  @Test
  void rejectsBlankComponents() {
    assertThrows(IllegalArgumentException.class, () -> TableName.of(" \t"));
    assertThrows(IllegalArgumentException.class, () -> TableName.of("", "product"));
    assertThrows(IllegalArgumentException.class, () -> TableName.of("sales", "\n"));
  }
}
