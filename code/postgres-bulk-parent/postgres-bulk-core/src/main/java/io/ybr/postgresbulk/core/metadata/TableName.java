package io.ybr.postgresbulk.core.metadata;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable physical table identity with an optional schema component.
 *
 * <p>Names are retained exactly as supplied. This type validates only that each supplied component
 * is non-null and non-blank; it does not trim, parse, quote, normalize case, or enforce rules from
 * a particular database. Instances are thread-safe and have value semantics.
 */
public final class TableName {

  private final String schema;
  private final String table;

  private TableName(String schema, String table) {
    this.schema = schema;
    this.table = requireIdentifier(table, "table");
  }

  /**
   * Creates an unqualified table name whose schema will be selected by the execution environment.
   *
   * @param table exact physical table name
   * @return an immutable unqualified table name
   * @throws NullPointerException if {@code table} is {@code null}
   * @throws IllegalArgumentException if {@code table} is blank
   */
  public static TableName of(String table) {
    return new TableName(null, table);
  }

  /**
   * Creates a table name with explicit schema and table components.
   *
   * @param schema exact physical schema name
   * @param table exact physical table name
   * @return an immutable qualified table name
   * @throws NullPointerException if either component is {@code null}
   * @throws IllegalArgumentException if either component is blank
   */
  public static TableName of(String schema, String table) {
    return new TableName(requireIdentifier(schema, "schema"), table);
  }

  /**
   * Returns the exact schema component when one was explicitly supplied.
   *
   * @return the schema, or an empty optional for an unqualified table
   */
  public Optional<String> schema() {
    return Optional.ofNullable(schema);
  }

  /**
   * Returns the exact physical table component.
   *
   * @return the non-blank table name
   */
  public String table() {
    return table;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof TableName that
        && Objects.equals(schema, that.schema)
        && table.equals(that.table);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schema, table);
  }

  @Override
  public String toString() {
    return schema == null
        ? "TableName[table=" + table + ']'
        : "TableName[schema=" + schema + ", table=" + table + ']';
  }

  private static String requireIdentifier(String value, String component) {
    Objects.requireNonNull(value, component + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(component + " must not be blank");
    }
    return value;
  }
}
