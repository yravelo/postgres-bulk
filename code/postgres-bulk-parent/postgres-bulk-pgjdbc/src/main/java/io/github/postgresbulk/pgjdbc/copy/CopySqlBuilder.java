package io.github.postgresbulk.pgjdbc.copy;

import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.core.metadata.TableName;
import java.util.Objects;
import java.util.stream.Collectors;

/** Builds the canonical COPY FROM STDIN command from structured metadata. */
final class CopySqlBuilder {

  private static final String COPY_OPTIONS =
      " FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL E'\\\\N', ENCODING 'UTF8')";

  private CopySqlBuilder() {}

  static String insert(EntityMetadata<?> metadata) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    String columns =
        metadata.insertColumns().stream()
            .map(ColumnMetadata::columnName)
            .map(PostgresIdentifierQuoter::quote)
            .collect(Collectors.joining(", "));
    return "COPY " + qualifiedName(metadata.table()) + " (" + columns + ')' + COPY_OPTIONS;
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
