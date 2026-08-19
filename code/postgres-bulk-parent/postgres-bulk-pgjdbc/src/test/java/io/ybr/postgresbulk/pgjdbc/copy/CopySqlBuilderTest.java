package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopySqlBuilderTest {

  @Test
  void buildsCanonicalUnqualifiedCopySql() {
    EntityMetadata<Row> metadata =
        EntityMetadata.of(
            Row.class,
            TableName.of("order"),
            List.of(ColumnMetadata.of("value", String.class, Row::value)));

    assertEquals(
        "COPY \"order\" (\"value\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
        CopySqlBuilder.insert(metadata));
  }

  @Test
  void quotesQualifiedNamesAndPreservesMetadataColumnOrder() {
    EntityMetadata<Row> metadata =
        EntityMetadata.of(
            Row.class,
            TableName.of("Sales Space", "Order\"Archive"),
            List.of(
                ColumnMetadata.of("second column", String.class, Row::value),
                ColumnMetadata.of("first\"column", Integer.class, Row::number)));

    assertEquals(
        "COPY \"Sales Space\".\"Order\"\"Archive\" (\"second column\", \"first\"\"column\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
        CopySqlBuilder.insert(metadata));
  }

  @Test
  void rejectsNullMetadata() {
    assertThrows(NullPointerException.class, () -> CopySqlBuilder.insert(null));
  }

  private record Row(String value, Integer number) {}
}
