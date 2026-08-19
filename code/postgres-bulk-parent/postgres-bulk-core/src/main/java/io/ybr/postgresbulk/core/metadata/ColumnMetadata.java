package io.ybr.postgresbulk.core.metadata;

import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable description of one ordered physical column and how to read its logical Java value.
 *
 * <p>The source type need not correspond to one field or property. An adapter may project an
 * association identifier, converted value, or embedded component through the accessor. The declared
 * Java type is retained independently from runtime values so a later encoder can select a
 * representation when {@link #read(Object)} returns {@code null}.
 *
 * <p>This type is a public SPI. It is safe to share between operations provided the supplied
 * accessor is stateless and thread-safe. It deliberately has identity rather than structural value
 * semantics because function equality does not describe mapping equality.
 *
 * @param <T> source type from which this column reads a value
 */
public final class ColumnMetadata<T> {

  private final String columnName;
  private final Class<?> javaType;
  private final Function<? super T, ?> accessor;

  private <V> ColumnMetadata(
      String columnName, Class<V> javaType, Function<? super T, ? extends V> accessor) {
    this.columnName = requireColumnName(columnName);
    this.javaType = normalizeJavaType(javaType);
    this.accessor = Objects.requireNonNull(accessor, "accessor must not be null");
  }

  /**
   * Creates metadata with a type-compatible, pre-resolved value accessor.
   *
   * <p>The accessor may return {@code null}. Any exception it throws is propagated unchanged; the
   * accessor should not retain or mutate per-operation state.
   *
   * @param columnName exact physical column name
   * @param javaType declared logical Java type produced by the accessor
   * @param accessor function that reads this column from a non-null source
   * @param <T> source type
   * @param <V> declared column value type
   * @return immutable column metadata
   * @throws NullPointerException if any argument is {@code null}
   * @throws IllegalArgumentException if the column name is blank or {@code javaType} is {@code
   *     void.class}
   */
  public static <T, V> ColumnMetadata<T> of(
      String columnName, Class<V> javaType, Function<? super T, ? extends V> accessor) {
    return new ColumnMetadata<>(columnName, javaType, accessor);
  }

  /**
   * Returns the exact physical column name without normalization or quoting.
   *
   * @return the non-blank physical column name
   */
  public String columnName() {
    return columnName;
  }

  /**
   * Returns the declared persistence-facing Java value type, with primitive types normalized to
   * wrappers.
   *
   * <p>Metadata producers must expose the type consumed by the persistence boundary. In particular,
   * the Hibernate adapter returns the relational Java type after applying mapping conversion, which
   * may differ from the entity attribute type.
   *
   * @return a non-null, non-primitive Java class
   */
  public Class<?> javaType() {
    return javaType;
  }

  /**
   * Reads this column's value from a source object.
   *
   * <p>The returned value may be {@code null}. A non-null value must be compatible with {@link
   * #javaType()}; the producer of this SPI is responsible for that contract.
   *
   * @param source source object from which to read
   * @return the logical column value, possibly {@code null}
   * @throws NullPointerException if {@code source} is {@code null}
   * @throws RuntimeException if the supplied accessor cannot read the value
   */
  public Object read(T source) {
    Objects.requireNonNull(source, "source must not be null");
    return accessor.apply(source);
  }

  private static String requireColumnName(String columnName) {
    Objects.requireNonNull(columnName, "columnName must not be null");
    if (columnName.isBlank()) {
      throw new IllegalArgumentException("columnName must not be blank");
    }
    return columnName;
  }

  private static Class<?> normalizeJavaType(Class<?> javaType) {
    Objects.requireNonNull(javaType, "javaType must not be null");
    if (javaType == void.class) {
      throw new IllegalArgumentException("javaType must describe a value, not void");
    }
    if (javaType == boolean.class) {
      return Boolean.class;
    }
    if (javaType == byte.class) {
      return Byte.class;
    }
    if (javaType == short.class) {
      return Short.class;
    }
    if (javaType == int.class) {
      return Integer.class;
    }
    if (javaType == long.class) {
      return Long.class;
    }
    if (javaType == float.class) {
      return Float.class;
    }
    if (javaType == double.class) {
      return Double.class;
    }
    if (javaType == char.class) {
      return Character.class;
    }
    return javaType;
  }
}
