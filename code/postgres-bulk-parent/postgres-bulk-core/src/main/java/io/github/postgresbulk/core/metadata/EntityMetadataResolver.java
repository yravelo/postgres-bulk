package io.github.postgresbulk.core.metadata;

/** Resolves framework-neutral bulk metadata for a logical entity type. */
public interface EntityMetadataResolver {

  /**
   * Resolves metadata for the supplied mapped type.
   *
   * @param entityType mapped entity type
   * @param <T> entity type
   * @return immutable bulk metadata
   */
  <T> EntityMetadata<T> resolve(Class<T> entityType);
}
