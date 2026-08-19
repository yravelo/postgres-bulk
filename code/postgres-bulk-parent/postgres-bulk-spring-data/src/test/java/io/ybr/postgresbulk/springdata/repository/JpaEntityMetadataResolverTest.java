package io.ybr.postgresbulk.springdata.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadataResolver;
import io.ybr.postgresbulk.core.metadata.TableName;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JpaEntityMetadataResolverTest {

  @Test
  void cachesResolverByPersistenceUnitIdentity() {
    AtomicInteger creations = new AtomicInteger();
    JpaEntityMetadataResolver resolver =
        JpaEntityMetadataResolver.caching(
            factory -> {
              creations.incrementAndGet();
              return new StubResolver();
            });
    EntityManagerFactory first = entityManagerFactory();
    EntityManagerFactory second = entityManagerFactory();

    EntityMetadata<Value> firstResult = resolver.resolve(first, Value.class);
    EntityMetadata<Value> cachedResult = resolver.resolve(first, Value.class);
    EntityMetadata<Value> secondResult = resolver.resolve(second, Value.class);

    assertSame(firstResult, cachedResult);
    assertNotSame(firstResult, secondResult);
    assertEquals(2, creations.get());
  }

  private static EntityManagerFactory entityManagerFactory() {
    return (EntityManagerFactory)
        Proxy.newProxyInstance(
            JpaEntityMetadataResolverTest.class.getClassLoader(),
            new Class<?>[] {EntityManagerFactory.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("isOpen")) {
                return true;
              }
              if (method.getName().equals("hashCode")) {
                return 1;
              }
              if (method.getName().equals("equals")) {
                return arguments[0] instanceof EntityManagerFactory;
              }
              return null;
            });
  }

  private static final class StubResolver implements EntityMetadataResolver {

    private EntityMetadata<?> cached;

    @Override
    @SuppressWarnings("unchecked")
    public <T> EntityMetadata<T> resolve(Class<T> entityType) {
      if (cached != null) {
        return (EntityMetadata<T>) cached;
      }
      ColumnMetadata<T> id = (ColumnMetadata<T>) ColumnMetadata.of("id", Long.class, Value::id);
      cached = EntityMetadata.of(entityType, TableName.of("values"), List.of(id));
      return (EntityMetadata<T>) cached;
    }
  }

  private record Value(Long id) {}
}
