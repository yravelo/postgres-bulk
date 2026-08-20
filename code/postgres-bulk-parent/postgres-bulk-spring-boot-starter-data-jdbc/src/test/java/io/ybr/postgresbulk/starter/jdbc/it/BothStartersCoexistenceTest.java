package io.ybr.postgresbulk.starter.jdbc.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.ybr.postgresbulk.autoconfigure.jdbc.PostgresBulkJdbcAutoConfiguration;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.JdbcOperations;

class BothStartersCoexistenceTest {

  @Test
  void bothStarterClasspathProvidesIndependentJpaAndJdbcResolvers() throws Exception {
    Class<?> jpaAutoConfiguration =
        Class.forName("io.ybr.postgresbulk.autoconfigure.PostgresBulkAutoConfiguration");
    Class<?> entityManagerFactory = Class.forName("jakarta.persistence.EntityManagerFactory");
    Class<?> jpaResolver =
        Class.forName("io.ybr.postgresbulk.springdata.repository.JpaEntityMetadataResolver");

    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(PostgresBulkJdbcAutoConfiguration.class, jpaAutoConfiguration))
        .withBean(DataSource.class, () -> interfaceStub(DataSource.class))
        .withBean(JdbcOperations.class, () -> interfaceStub(JdbcOperations.class))
        .withBean(JdbcConverter.class, () -> interfaceStub(JdbcConverter.class))
        .withBean(JdbcCustomConversions.class, JdbcCustomConversions::new)
        .withBean(RelationalMappingContext.class, RelationalMappingContext::new)
        .withBean(
            "entityManagerFactory",
            rawType(entityManagerFactory),
            () -> interfaceStub(entityManagerFactory))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class);
              assertThat(context.getBeansOfType(rawType(jpaResolver))).hasSize(1);
            });
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> rawType(Class<?> type) {
    return (Class<T>) type;
  }

  private static <T> T interfaceStub(Class<T> type) {
    Object proxy =
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (instance, method, arguments) -> {
              if (method.getName().equals("getMappingContext")) {
                return new RelationalMappingContext();
              }
              if (method.getName().equals("toString")) {
                return type.getSimpleName() + "Stub";
              }
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(instance);
              }
              if (method.getName().equals("equals")) {
                return instance == arguments[0];
              }
              Class<?> returnType = method.getReturnType();
              if (!returnType.isPrimitive()) {
                return null;
              }
              if (returnType == boolean.class) {
                return false;
              }
              if (returnType == char.class) {
                return '\0';
              }
              return 0;
            });
    return type.cast(proxy);
  }
}
