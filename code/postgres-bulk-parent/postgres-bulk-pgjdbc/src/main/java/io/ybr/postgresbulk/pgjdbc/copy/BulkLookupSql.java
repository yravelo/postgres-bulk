package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.Objects;
import java.util.stream.Collectors;

/** Prepared target-independent SQL structure for one ordered bulk key definition. */
final class BulkLookupSql {

  private static final String TARGET_ALIAS = "target_row";
  private static final String KEY_ALIAS = "lookup_key";

  private final String selectedColumns;
  private final String joinCondition;
  private final BulkKeyMetadata<?> keyMetadata;

  private BulkLookupSql(BulkKeyMetadata<?> keyMetadata) {
    this.keyMetadata = Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    this.selectedColumns =
        keyMetadata.components().stream()
            .map(ColumnMetadata::columnName)
            .map(PostgresIdentifierQuoter::quote)
            .collect(Collectors.joining(", "));
    this.joinCondition =
        keyMetadata.components().stream()
            .map(ColumnMetadata::columnName)
            .map(PostgresIdentifierQuoter::quote)
            .map(column -> TARGET_ALIAS + '.' + column + " = " + KEY_ALIAS + '.' + column)
            .collect(Collectors.joining(" AND "));
  }

  static BulkLookupSql prepare(BulkKeyMetadata<?> keyMetadata) {
    return new BulkLookupSql(keyMetadata);
  }

  InvocationSql forInvocation(TableName targetTable, String temporaryTable) {
    Objects.requireNonNull(targetTable, "targetTable must not be null");
    String qualifiedTarget = CopySqlBuilder.qualifiedName(targetTable);
    String quotedTemporaryTable = quotedTemporaryTable(temporaryTable);
    return new InvocationSql(
        "CREATE TEMP TABLE "
            + quotedTemporaryTable
            + " ON COMMIT DROP AS SELECT "
            + selectedColumns
            + " FROM "
            + qualifiedTarget
            + " WITH NO DATA",
        CopySqlBuilder.insertTemporary(temporaryTable, keyMetadata),
        "SELECT "
            + TARGET_ALIAS
            + ".* FROM "
            + qualifiedTarget
            + ' '
            + TARGET_ALIAS
            + " JOIN (SELECT DISTINCT "
            + selectedColumns
            + " FROM "
            + quotedTemporaryTable
            + ") "
            + KEY_ALIAS
            + " ON "
            + joinCondition,
        "DROP TABLE IF EXISTS " + quotedTemporaryTable);
  }

  private static String quotedTemporaryTable(String temporaryTable) {
    return PostgresIdentifierQuoter.quote(temporaryTable);
  }

  /** Complete SQL lifecycle built once for one invocation and one effective target. */
  record InvocationSql(
      String createTemporaryTable,
      String copyKeys,
      String selectMatches,
      String dropTemporaryTable) {}
}
