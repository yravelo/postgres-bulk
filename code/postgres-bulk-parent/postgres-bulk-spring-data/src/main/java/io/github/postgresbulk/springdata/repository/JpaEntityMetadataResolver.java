package io.github.postgresbulk.springdata.repository;

import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadataResolver;
import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Resolves bulk metadata in the context of the persistence unit owning an entity. */
@FunctionalInterface
public interface JpaEntityMetadataResolver {

  /** Resolves metadata for an entity managed by the supplied persistence unit. */
  <T> EntityMetadata<T> resolve(EntityManagerFactory entityManagerFactory, Class<T> entityType);

  /**
   * Adapts persistence-unit-bound resolver instances and caches one resolver per factory identity.
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
