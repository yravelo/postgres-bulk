package io.ybr.postgresbulk.autoconfigure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.datasource.AbstractDataSource;

class PostgresBulkJdbcAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PostgresBulkJdbcAutoConfiguration.class));

  @Test
  void autoConfiguresForOneCompleteJdbcInfrastructure() {
    withInfrastructure()
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class);
              assertThat(outcomes(context).isFullMatch()).isTrue();
            });
  }

  @Test
  void canBeDisabledWithSharedProperty() {
    withInfrastructure()
        .withPropertyValues("postgres-bulk.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class);
              assertThat(outcomes(context).isFullMatch()).isFalse();
            });
  }

  @Test
  void backsOffWhenAnyRequiredInfrastructureBeanIsMissing() {
    for (Class<?> missing :
        List.of(
            DataSource.class,
            JdbcOperations.class,
            JdbcConverter.class,
            JdbcCustomConversions.class,
            RelationalMappingContext.class)) {
      withInfrastructureExcept(missing)
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
    }
  }

  @Test
  void backsOffWhenRequiredLibraryTypesAreMissing() {
    for (Class<?> missing :
        List.of(PGConnection.class, JdbcConverter.class, PostgresBulkJdbcRepository.class)) {
      withInfrastructure()
          .withClassLoader(new FilteredClassLoader(missing))
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
    }
  }

  @Test
  void backsOffForUserResolver() {
    SpringDataJdbcEntityMetadataResolver custom =
        new SpringDataJdbcEntityMetadataResolver(
            interfaceStub(JdbcConverter.class), new JdbcCustomConversions());

    withInfrastructure()
        .withBean(
            "customBulkJdbcMetadataResolver",
            SpringDataJdbcEntityMetadataResolver.class,
            () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class);
              assertThat(context.getBean(SpringDataJdbcEntityMetadataResolver.class))
                  .isSameAs(custom);
            });
  }

  @Test
  void backsOffForAmbiguousDataSources() {
    withInfrastructureExcept(DataSource.class)
        .withBean("firstDataSource", DataSource.class, FailingDataSource::new)
        .withBean("secondDataSource", DataSource.class, FailingDataSource::new)
        .run(
            context ->
                assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void acceptsPrimaryDataSourceAmongMultipleCandidates() {
    withInfrastructureExcept(DataSource.class)
        .withBean(
            "primaryDataSource",
            DataSource.class,
            FailingDataSource::new,
            definition -> definition.setPrimary(true))
        .withBean("secondaryDataSource", DataSource.class, FailingDataSource::new)
        .run(
            context ->
                assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void backsOffForAmbiguousJdbcOperations() {
    withInfrastructureExcept(JdbcOperations.class)
        .withBean(
            "firstJdbcOperations", JdbcOperations.class, () -> interfaceStub(JdbcOperations.class))
        .withBean(
            "secondJdbcOperations", JdbcOperations.class, () -> interfaceStub(JdbcOperations.class))
        .run(
            context ->
                assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void acceptsPrimaryJdbcOperationsAmongMultipleCandidates() {
    withInfrastructureExcept(JdbcOperations.class)
        .withBean(
            "primaryJdbcOperations",
            JdbcOperations.class,
            () -> interfaceStub(JdbcOperations.class),
            definition -> definition.setPrimary(true))
        .withBean(
            "secondaryJdbcOperations",
            JdbcOperations.class,
            () -> interfaceStub(JdbcOperations.class))
        .run(
            context ->
                assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void backsOffForAmbiguousConvertersAndMappingContexts() {
    withInfrastructureExcept(JdbcConverter.class)
        .withBean("firstConverter", JdbcConverter.class, () -> interfaceStub(JdbcConverter.class))
        .withBean("secondConverter", JdbcConverter.class, () -> interfaceStub(JdbcConverter.class))
        .run(
            context ->
                assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));

    withInfrastructureExcept(RelationalMappingContext.class)
        .withBean(
            "firstMappingContext", RelationalMappingContext.class, RelationalMappingContext::new)
        .withBean(
            "secondMappingContext", RelationalMappingContext.class, RelationalMappingContext::new)
        .run(
            context ->
                assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void doesNotActivateForJpaOnlyOrOtherwiseEmptyContext() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(SpringDataJdbcEntityMetadataResolver.class));
  }

  @Test
  void doesNotOpenAConnectionDuringStartup() {
    AtomicInteger connectionRequests = new AtomicInteger();
    withInfrastructureExcept(DataSource.class)
        .withBean(DataSource.class, () -> new FailingDataSource(connectionRequests))
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpringDataJdbcEntityMetadataResolver.class);
              assertThat(connectionRequests).hasValue(0);
            });
  }

  private ApplicationContextRunner withInfrastructure() {
    return withInfrastructureExcept(Void.class);
  }

  private ApplicationContextRunner withInfrastructureExcept(Class<?> missing) {
    ApplicationContextRunner runner = contextRunner;
    if (missing != DataSource.class) {
      runner = runner.withBean(DataSource.class, FailingDataSource::new);
    }
    if (missing != JdbcOperations.class) {
      runner = runner.withBean(JdbcOperations.class, () -> interfaceStub(JdbcOperations.class));
    }
    if (missing != JdbcConverter.class) {
      runner = runner.withBean(JdbcConverter.class, () -> interfaceStub(JdbcConverter.class));
    }
    if (missing != JdbcCustomConversions.class) {
      runner = runner.withBean(JdbcCustomConversions.class, JdbcCustomConversions::new);
    }
    if (missing != RelationalMappingContext.class) {
      runner = runner.withBean(RelationalMappingContext.class, RelationalMappingContext::new);
    }
    return runner;
  }

  private static ConditionEvaluationReport.ConditionAndOutcomes outcomes(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
    return ConditionEvaluationReport.get(context.getBeanFactory())
        .getConditionAndOutcomesBySource()
        .get(PostgresBulkJdbcAutoConfiguration.class.getName());
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

  private static final class FailingDataSource extends AbstractDataSource {

    private final AtomicInteger connectionRequests;

    private FailingDataSource() {
      this(new AtomicInteger());
    }

    private FailingDataSource(AtomicInteger connectionRequests) {
      this.connectionRequests = connectionRequests;
    }

    @Override
    public Connection getConnection() throws SQLException {
      connectionRequests.incrementAndGet();
      throw new SQLException("Startup must not open a connection");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }
  }
}
