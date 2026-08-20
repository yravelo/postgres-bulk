package io.ybr.postgresbulk.springdata.jdbc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.jdbc.core.JdbcOperations;

class DefaultPostgresBulkJdbcOperationsTest {

  @Test
  void repositoryCombiningJdbcAndJpaFragmentsFailsExplicitly() {
    RepositoryMetadata metadata =
        (RepositoryMetadata)
            Proxy.newProxyInstance(
                RepositoryMetadata.class.getClassLoader(),
                new Class<?>[] {RepositoryMetadata.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("getRepositoryInterface")) {
                    return MixedRepository.class;
                  }
                  throw new AssertionError("Unexpected metadata call: " + method);
                });

    InvalidDataAccessApiUsageException failure =
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> DefaultPostgresBulkJdbcOperations.rejectMixedStoreRepository(metadata));
    assertTrue(failure.getMessage().contains(MixedRepository.class.getName()));
  }

  @Test
  void multipleJdbcOperationsCandidatesFailInsteadOfSelectingArbitrarily() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean("firstJdbcOperations", JdbcOperations.class, this::jdbcOperations);
      context.registerBean("secondJdbcOperations", JdbcOperations.class, this::jdbcOperations);
      context.registerBean(SpringDataJdbcEntityMetadataResolver.class, this::resolver);
      context.registerBean(DefaultPostgresBulkJdbcOperations.class);

      UnsatisfiedDependencyException failure =
          assertThrows(UnsatisfiedDependencyException.class, context::refresh);
      assertInstanceOf(NoUniqueBeanDefinitionException.class, failure.getMostSpecificCause());
    }
  }

  private JdbcOperations jdbcOperations() {
    return (JdbcOperations)
        Proxy.newProxyInstance(
            JdbcOperations.class.getClassLoader(),
            new Class<?>[] {JdbcOperations.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("equals")) {
                return proxy == arguments[0];
              }
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("toString")) {
                return "test JdbcOperations";
              }
              throw new AssertionError("JDBC must not be called");
            });
  }

  private SpringDataJdbcEntityMetadataResolver resolver() {
    JdbcCustomConversions conversions = new JdbcCustomConversions();
    JdbcMappingContext mappingContext = new JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    mappingContext.setForceQuote(true);
    mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    mappingContext.afterPropertiesSet();
    MappingJdbcConverter converter =
        new MappingJdbcConverter(
            mappingContext,
            (identifier, path) -> List.of(),
            conversions,
            JdbcTypeFactory.unsupported());
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  interface MixedRepository
      extends PostgresBulkJdbcRepository<Object>,
          io.ybr.postgresbulk.springdata.repository.PostgresBulkRepository<Object, Long> {}
}
