package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.Objects;
import java.util.stream.Collectors;

/** Prepared SQL fragments for one target table and ordered bulk key definition. */
final class BulkLookupSql {

  private static final String TARGET_ALIAS = "target_row";
  private static final String KEY_ALIAS = "lookup_key";

  private final String qualifiedTarget;
  private final String selectedColumns;
  private final String joinCondition;
  private final BulkKeyMetadata<?> keyMetadata;

  private BulkLookupSql(TableName targetTable, BulkKeyMetadata<?> keyMetadata) {
    this.keyMetadata = Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    this.qualifiedTarget = CopySqlBuilder.qualifiedName(targetTable);
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

  static BulkLookupSql prepare(TableName targetTable, BulkKeyMetadata<?> keyMetadata) {
    return new BulkLookupSql(
        Objects.requireNonNull(targetTable, "targetTable must not be null"), keyMetadata);
  }

  String createTemporaryTable(String temporaryTable) {
    return "CREATE TEMP TABLE "
        + quotedTemporaryTable(temporaryTable)
        + " ON COMMIT DROP AS SELECT "
        + selectedColumns
        + " FROM "
        + qualifiedTarget
        + " WITH NO DATA";
  }

  String copyKeys(String temporaryTable) {
    return CopySqlBuilder.insertTemporary(temporaryTable, keyMetadata);
  }

  String selectMatches(String temporaryTable) {
    return "SELECT "
        + TARGET_ALIAS
        + ".* FROM "
        + qualifiedTarget
        + ' '
        + TARGET_ALIAS
        + " JOIN (SELECT DISTINCT "
        + selectedColumns
        + " FROM "
        + quotedTemporaryTable(temporaryTable)
        + ") "
        + KEY_ALIAS
        + " ON "
        + joinCondition;
  }

  String dropTemporaryTable(String temporaryTable) {
    return "DROP TABLE IF EXISTS " + quotedTemporaryTable(temporaryTable);
  }

  private static String quotedTemporaryTable(String temporaryTable) {
    return PostgresIdentifierQuoter.quote(temporaryTable);
  }
}
