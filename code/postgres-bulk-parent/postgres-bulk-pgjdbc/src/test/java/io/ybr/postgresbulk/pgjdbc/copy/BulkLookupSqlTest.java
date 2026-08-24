package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;
import org.junit.jupiter.api.Test;

class BulkLookupSqlTest {

  @Test
  void buildsSimpleKeyLifecycleSql() {
    BulkLookupSql sql = BulkLookupSql.prepare(simpleKey());
    BulkLookupSql.InvocationSql invocation =
        sql.forInvocation(TableName.of("customers"), "pgbulk_keys_test");

    assertEquals(
        "CREATE TEMP TABLE \"pgbulk_keys_test\" ON COMMIT DROP AS SELECT \"id\" FROM \"customers\" WITH NO DATA",
        invocation.createTemporaryTable());
    assertEquals(
        "COPY \"pgbulk_keys_test\" (\"id\") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')",
        invocation.copyKeys());
    assertEquals(
        "SELECT target_row.* FROM \"customers\" target_row JOIN (SELECT DISTINCT \"id\" FROM \"pgbulk_keys_test\") lookup_key ON target_row.\"id\" = lookup_key.\"id\"",
        invocation.selectMatches());
    assertEquals("DROP TABLE IF EXISTS \"pgbulk_keys_test\"", invocation.dropTemporaryTable());
  }

  @Test
  void quotesQualifiedTargetAndPreservesCompositeKeyOrder() {
    BulkKeyMetadata<Key> metadata =
        BulkKeyMetadata.of(
            Key.class,
            List.of(
                ColumnMetadata.of("tenant Code", String.class, Key::tenant),
                ColumnMetadata.of("order\"id", Integer.class, Key::id)));
    BulkLookupSql sql = BulkLookupSql.prepare(metadata);
    BulkLookupSql.InvocationSql invocation =
        sql.forInvocation(TableName.of("Sales Space", "Order\"Archive"), "pgbulk_keys_test");

    assertEquals(
        "CREATE TEMP TABLE \"pgbulk_keys_test\" ON COMMIT DROP AS SELECT \"tenant Code\", \"order\"\"id\" FROM \"Sales Space\".\"Order\"\"Archive\" WITH NO DATA",
        invocation.createTemporaryTable());
    assertEquals(
        "SELECT target_row.* FROM \"Sales Space\".\"Order\"\"Archive\" target_row JOIN (SELECT DISTINCT \"tenant Code\", \"order\"\"id\" FROM \"pgbulk_keys_test\") lookup_key ON target_row.\"tenant Code\" = lookup_key.\"tenant Code\" AND target_row.\"order\"\"id\" = lookup_key.\"order\"\"id\"",
        invocation.selectMatches());
  }

  @Test
  void rejectsNullPreparationArguments() {
    assertThrows(NullPointerException.class, () -> BulkLookupSql.prepare(null));
    BulkLookupSql sql = BulkLookupSql.prepare(simpleKey());
    assertThrows(NullPointerException.class, () -> sql.forInvocation(null, "pgbulk_keys_test"));
    assertThrows(
        NullPointerException.class, () -> sql.forInvocation(TableName.of("customers"), null));
  }

  private static BulkKeyMetadata<Integer> simpleKey() {
    return BulkKeyMetadata.of(
        Integer.class, List.of(ColumnMetadata.of("id", Integer.class, value -> value)));
  }

  private record Key(String tenant, Integer id) {}
}
