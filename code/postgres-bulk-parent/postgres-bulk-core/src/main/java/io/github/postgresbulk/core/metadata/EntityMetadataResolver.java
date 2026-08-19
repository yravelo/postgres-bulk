package io.github.postgresbulk.core.metadata;

/**
 * Resolves framework-neutral bulk metadata for a logical entity type.
 *
 * <p>Implementations define their own mapping source and cache scope. Returned descriptors must
 * satisfy the immutability and accessor contracts of {@link EntityMetadata}.
 */
public interface EntityMetadataResolver {

  /**
   * Resolves metadata for the supplied mapped type.
   *
   * @param entityType mapped entity type
   * @param <T> entity type
   * @return immutable bulk metadata
   * @throws NullPointerException if {@code entityType} is {@code null}
   * @throws io.github.postgresbulk.core.BulkException if the type is unmapped or cannot be
   *     represented by the implementation
   */
  <T> EntityMetadata<T> resolve(Class<T> entityType);
}
