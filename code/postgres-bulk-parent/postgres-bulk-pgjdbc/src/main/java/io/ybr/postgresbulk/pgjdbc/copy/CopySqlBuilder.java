package io.ybr.postgresbulk.pgjdbc.copy;

import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.Objects;
import java.util.stream.Collectors;

/** Builds the canonical COPY FROM STDIN command from structured metadata. */
final class CopySqlBuilder {

  private static final String COPY_OPTIONS =
      " FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')";

  private CopySqlBuilder() {}

  static String insert(EntityMetadata<?> metadata) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    return insert(metadata, metadata.table());
  }

  static String insert(EntityMetadata<?> metadata, TableName target) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    Objects.requireNonNull(target, "target must not be null");
    String columns =
        metadata.insertColumns().stream()
            .map(ColumnMetadata::columnName)
            .map(PostgresIdentifierQuoter::quote)
            .collect(Collectors.joining(", "));
    return "COPY " + qualifiedName(target) + " (" + columns + ')' + COPY_OPTIONS;
  }

  static String insertTemporary(String tableName, BulkKeyMetadata<?> metadata) {
    Objects.requireNonNull(tableName, "tableName must not be null");
    Objects.requireNonNull(metadata, "metadata must not be null");
    String columns =
        metadata.components().stream()
            .map(ColumnMetadata::columnName)
            .map(PostgresIdentifierQuoter::quote)
            .collect(Collectors.joining(", "));
    return "COPY "
        + PostgresIdentifierQuoter.quote(tableName)
        + " ("
        + columns
        + ')'
        + COPY_OPTIONS;
  }

  static String qualifiedName(TableName table) {
    Objects.requireNonNull(table, "table must not be null");
    String quotedTable = PostgresIdentifierQuoter.quote(table.table());
    return table
        .schema()
        .map(schema -> PostgresIdentifierQuoter.quote(schema) + '.' + quotedTable)
        .orElse(quotedTable);
  }
}
