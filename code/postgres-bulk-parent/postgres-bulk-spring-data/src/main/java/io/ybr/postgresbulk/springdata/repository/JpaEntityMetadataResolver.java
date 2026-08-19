package io.ybr.postgresbulk.springdata.repository;

import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadataResolver;
import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Resolves bulk metadata in the context of the persistence unit owning an entity. */
@FunctionalInterface
public interface JpaEntityMetadataResolver {

  /**
   * Resolves metadata for an entity managed by the supplied persistence unit.
   *
   * @param entityManagerFactory persistence unit that owns the entity mapping
   * @param entityType mapped entity type
   * @param <T> entity type
   * @return immutable persistence-facing bulk metadata
   * @throws NullPointerException if an argument is {@code null}
   * @throws io.ybr.postgresbulk.core.BulkException if the mapping cannot be resolved
   */
  <T> EntityMetadata<T> resolve(EntityManagerFactory entityManagerFactory, Class<T> entityType);

  /**
   * Adapts persistence-unit-bound resolver instances and caches one resolver per factory identity.
   *
   * <p>The returned adapter is safe for concurrent use. Factories are compared by identity so
   * metadata never crosses persistence units.
   *
   * @param resolverFactory creates one resolver for each encountered persistence unit
   * @return a caching persistence-unit-aware resolver
   * @throws NullPointerException if the factory is {@code null} or returns {@code null}
   */
  static JpaEntityMetadataResolver caching(
      Function<? super EntityManagerFactory, ? extends EntityMetadataResolver> resolverFactory) {
    Objects.requireNonNull(resolverFactory, "resolverFactory must not be null");
    return new JpaEntityMetadataResolver() {
      private final Map<EntityManagerFactory, EntityMetadataResolver> resolvers =
          Collections.synchronizedMap(new IdentityHashMap<>());

      @Override
      public <T> EntityMetadata<T> resolve(
          EntityManagerFactory entityManagerFactory, Class<T> entityType) {
        Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        EntityMetadataResolver resolver;
        synchronized (resolvers) {
          resolver =
              resolvers.computeIfAbsent(
                  entityManagerFactory,
                  factory ->
                      Objects.requireNonNull(
                          resolverFactory.apply(factory), "resolverFactory must not return null"));
        }
        return resolver.resolve(entityType);
      }
    };
  }
}
