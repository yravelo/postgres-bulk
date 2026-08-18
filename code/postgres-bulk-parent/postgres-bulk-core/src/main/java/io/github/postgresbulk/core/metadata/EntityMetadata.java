package io.github.postgresbulk.core.metadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable bulk-insert metadata for one logical source type and physical table.
 *
 * <p>The insert-column list is the final mapping resolved by a producer: its encounter order is the
 * row order consumers must use, and omitted columns require no flags or explanation in core. The
 * model permits several columns to read from one logical property or association.
 *
 * <p>This type is a public SPI and is safe to share between concurrent operations when every column
 * accessor satisfies the thread-safety contract of {@link ColumnMetadata}. It deliberately does not
 * define structural equality because its columns contain executable functions.
 *
 * @param <T> logical source type described by this metadata
 */
public final class EntityMetadata<T> {

  private final Class<T> javaType;
  private final TableName table;
  private final List<ColumnMetadata<T>> insertColumns;

  private EntityMetadata(
      Class<T> javaType, TableName table, List<? extends ColumnMetadata<T>> insertColumns) {
    this.javaType = Objects.requireNonNull(javaType, "javaType must not be null");
    this.table = Objects.requireNonNull(table, "table must not be null");
    this.insertColumns = copyAndValidateColumns(insertColumns);
  }

  /**
   * Creates metadata from the final ordered list of physical columns used by bulk insert.
   *
   * @param javaType logical source class
   * @param table physical destination table
   * @param insertColumns non-empty columns in exact row order
   * @param <T> logical source type
   * @return immutable entity metadata
   * @throws NullPointerException if an argument or column is {@code null}
   * @throws IllegalArgumentException if no columns are supplied or a physical column name is
   *     duplicated
   */
  public static <T> EntityMetadata<T> of(
      Class<T> javaType, TableName table, List<? extends ColumnMetadata<T>> insertColumns) {
    return new EntityMetadata<>(javaType, table, insertColumns);
  }

  /**
   * Returns the logical source class represented by this descriptor.
   *
   * @return the non-null source class
   */
  public Class<T> javaType() {
    return javaType;
  }

  /**
   * Returns the physical destination table.
   *
   * @return immutable table identity
   */
  public TableName table() {
    return table;
  }

  /**
   * Returns an unmodifiable list of insertable columns in exact row order.
   *
   * @return stable, non-empty ordered columns
   */
  public List<ColumnMetadata<T>> insertColumns() {
    return insertColumns;
  }

  private static <T> List<ColumnMetadata<T>> copyAndValidateColumns(
      List<? extends ColumnMetadata<T>> columns) {
    Objects.requireNonNull(columns, "insertColumns must not be null");
    if (columns.isEmpty()) {
      throw new IllegalArgumentException("insertColumns must not be empty");
    }

    List<ColumnMetadata<T>> copy = new ArrayList<>(columns.size());
    Set<String> physicalNames = new HashSet<>();
    for (int index = 0; index < columns.size(); index++) {
      ColumnMetadata<T> column =
          Objects.requireNonNull(
              columns.get(index), "insertColumns[" + index + "] must not be null");
      if (!physicalNames.add(column.columnName())) {
        throw new IllegalArgumentException("duplicate insert column: " + column.columnName());
      }
      copy.add(column);
    }
    return List.copyOf(copy);
  }
}
