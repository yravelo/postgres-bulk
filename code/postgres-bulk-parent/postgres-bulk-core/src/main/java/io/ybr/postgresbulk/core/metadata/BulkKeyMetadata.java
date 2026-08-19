package io.ybr.postgresbulk.core.metadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable ordered metadata for the components of one future bulk lookup key.
 *
 * <p>Each component names a physical target column and reads its value from a key object {@code K}.
 * One component describes a simple key; multiple components describe a composite key. This SPI does
 * not imply a database UNIQUE constraint and does not publish a lookup operation or its policies
 * for duplicate inputs, null components, or result ordering.
 *
 * <p>The descriptor is safe to share between concurrent operations when every component accessor
 * satisfies the thread-safety contract of {@link ColumnMetadata}. It has identity rather than
 * structural equality because its components contain executable functions.
 *
 * @param <K> logical key type from which component values are read
 */
public final class BulkKeyMetadata<K> {

  private final Class<K> javaType;
  private final List<ColumnMetadata<K>> components;

  private BulkKeyMetadata(Class<K> javaType, List<? extends ColumnMetadata<K>> components) {
    this.javaType = Objects.requireNonNull(javaType, "javaType must not be null");
    this.components = copyAndValidateComponents(components);
  }

  /**
   * Creates metadata for a simple or composite key in exact component order.
   *
   * @param javaType logical key class
   * @param components non-empty key components in target-column order
   * @param <K> logical key type
   * @return immutable key metadata
   * @throws NullPointerException if an argument or component is {@code null}
   * @throws IllegalArgumentException if no components are supplied or a physical column name is
   *     duplicated
   */
  public static <K> BulkKeyMetadata<K> of(
      Class<K> javaType, List<? extends ColumnMetadata<K>> components) {
    return new BulkKeyMetadata<>(javaType, components);
  }

  /**
   * Returns the logical key class represented by this descriptor.
   *
   * @return the non-null key class
   */
  public Class<K> javaType() {
    return javaType;
  }

  /**
   * Returns an unmodifiable list of key components in exact target-column order.
   *
   * @return stable, non-empty ordered components
   */
  public List<ColumnMetadata<K>> components() {
    return components;
  }

  private static <K> List<ColumnMetadata<K>> copyAndValidateComponents(
      List<? extends ColumnMetadata<K>> components) {
    Objects.requireNonNull(components, "components must not be null");
    if (components.isEmpty()) {
      throw new IllegalArgumentException("components must not be empty");
    }

    List<ColumnMetadata<K>> copy = new ArrayList<>(components.size());
    Set<String> physicalNames = new HashSet<>();
    for (int index = 0; index < components.size(); index++) {
      ColumnMetadata<K> component =
          Objects.requireNonNull(
              components.get(index), "components[" + index + "] must not be null");
      if (!physicalNames.add(component.columnName())) {
        throw new IllegalArgumentException("duplicate key column: " + component.columnName());
      }
      copy.add(component);
    }
    return List.copyOf(copy);
  }
}
