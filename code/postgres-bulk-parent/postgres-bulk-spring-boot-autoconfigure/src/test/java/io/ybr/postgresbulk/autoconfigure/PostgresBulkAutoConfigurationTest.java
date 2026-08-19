package io.ybr.postgresbulk.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.datasource.AbstractDataSource;

class PostgresBulkAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PostgresBulkAutoConfiguration.class));

  @Test
  void autoConfiguresByDefaultForJpaHibernateAndPgjdbc() {
    contextRunner
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JpaEntityMetadataResolver.class);
              assertThat(context).hasSingleBean(PostgresBulkProperties.class);
              assertThat(context).hasSingleBean(MeterFilter.class);
              assertThat(context.getBean(PostgresBulkProperties.class).enabled()).isTrue();
              assertThat(context.getBean(PostgresBulkProperties.class).observability().enabled())
                  .isTrue();
              assertThat(outcomes(context).isFullMatch()).isTrue();
            });
  }

  @Test
  void observabilityCanBeDisabledIndependently() {
    contextRunner
        .withUserConfiguration(
            SingleEntityManagerFactoryConfiguration.class, MeterRegistryConfiguration.class)
        .withPropertyValues("postgres-bulk.observability.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(JpaEntityMetadataResolver.class);
              assertThat(context.getBean(PostgresBulkProperties.class).enabled()).isTrue();
              assertThat(context.getBean(PostgresBulkProperties.class).observability().enabled())
                  .isFalse();
              assertThat(context).doesNotHaveBean(MeterFilter.class);
            });
  }

  @Test
  void boundsAutomaticErrorTagForBulkOperationTimer() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    meters
        .config()
        .meterFilter(new PostgresBulkAutoConfiguration().postgresBulkObservationErrorTagFilter());
    ObservationRegistry observations = ObservationRegistry.create();
    observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));

    assertThatThrownBy(
            () ->
                Observation.createNotStarted("postgres.bulk.operation", observations)
                    .lowCardinalityKeyValue("operation", "insert")
                    .lowCardinalityKeyValue("outcome", "error")
                    .observe(
                        () -> {
                          throw new ConsumerSpecificFailure();
                        }))
        .isInstanceOf(ConsumerSpecificFailure.class);

    Timer timer =
        meters
            .get("postgres.bulk.operation")
            .tags("operation", "insert", "outcome", "error")
            .timer();
    assertThat(timer.getId().getTag("error")).isEqualTo("error");
    assertThat(timer.getId().getTags())
        .extracting(io.micrometer.core.instrument.Tag::getKey)
        .containsExactlyInAnyOrderElementsOf(Set.of("error", "operation", "outcome"));
  }

  @Test
  void canBeDisabledExplicitly() {
    contextRunner
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .withPropertyValues("postgres-bulk.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JpaEntityMetadataResolver.class);
              assertThat(outcomes(context).isFullMatch()).isFalse();
            });
  }

  @Test
  void backsOffWithoutEntityManagerFactory() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(JpaEntityMetadataResolver.class);
          assertThat(outcomes(context).isFullMatch()).isFalse();
        });
  }

  @Test
  void supportsMultipleEntityManagerFactoriesWithoutChoosingOneAtStartup() {
    contextRunner
        .withUserConfiguration(MultipleEntityManagerFactoryConfiguration.class)
        .run(context -> assertThat(context).hasSingleBean(JpaEntityMetadataResolver.class));
  }

  @Test
  void backsOffForUserResolver() {
    contextRunner
        .withUserConfiguration(
            SingleEntityManagerFactoryConfiguration.class, UserResolverConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JpaEntityMetadataResolver.class);
              assertThat(context.getBean(JpaEntityMetadataResolver.class))
                  .isSameAs(context.getBean("customBulkMetadataResolver"));
            });
  }

  @Test
  void backsOffWithoutHibernate() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(Session.class))
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(JpaEntityMetadataResolver.class));
  }

  @Test
  void backsOffWithoutSpringDataJpa() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(JpaRepository.class))
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(JpaEntityMetadataResolver.class));
  }

  @Test
  void backsOffWithoutPgjdbc() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(PGConnection.class))
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(JpaEntityMetadataResolver.class));
  }

  @Test
  void doesNotOpenDataSourceConnectionDuringStartup() {
    AtomicInteger connectionRequests = new AtomicInteger();
    contextRunner
        .withBean(DataSource.class, () -> new FailingDataSource(connectionRequests))
        .withUserConfiguration(SingleEntityManagerFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JpaEntityMetadataResolver.class);
              assertThat(connectionRequests).hasValue(0);
            });
  }

  private static ConditionEvaluationReport.ConditionAndOutcomes outcomes(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
    return ConditionEvaluationReport.get(context.getBeanFactory())
        .getConditionAndOutcomesBySource()
        .get(PostgresBulkAutoConfiguration.class.getName());
  }

  private static EntityManagerFactory entityManagerFactory() {
    return (EntityManagerFactory)
        Proxy.newProxyInstance(
            PostgresBulkAutoConfigurationTest.class.getClassLoader(),
            new Class<?>[] {EntityManagerFactory.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("equals")) {
                return proxy == arguments[0];
              }
              if (method.getName().equals("isOpen")) {
                return true;
              }
              if (method.getName().equals("toString")) {
                return "testEntityManagerFactory";
              }
              return null;
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class SingleEntityManagerFactoryConfiguration {

    @Bean
    EntityManagerFactory entityManagerFactory() {
      return PostgresBulkAutoConfigurationTest.entityManagerFactory();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class MultipleEntityManagerFactoryConfiguration {

    @Bean
    EntityManagerFactory firstEntityManagerFactory() {
      return PostgresBulkAutoConfigurationTest.entityManagerFactory();
    }

    @Bean
    EntityManagerFactory secondEntityManagerFactory() {
      return PostgresBulkAutoConfigurationTest.entityManagerFactory();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class UserResolverConfiguration {

    @Bean
    JpaEntityMetadataResolver customBulkMetadataResolver() {
      return new JpaEntityMetadataResolver() {
        @Override
        public <T> EntityMetadata<T> resolve(
            EntityManagerFactory entityManagerFactory, Class<T> entityType) {
          return null;
        }
      };
    }
  }

  private static final class FailingDataSource extends AbstractDataSource {

    private final AtomicInteger connectionRequests;

    private FailingDataSource(AtomicInteger connectionRequests) {
      this.connectionRequests = connectionRequests;
    }

    @Override
    public Connection getConnection() throws SQLException {
      connectionRequests.incrementAndGet();
      throw new SQLException("startup must not request a connection");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class MeterRegistryConfiguration {

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static final class ConsumerSpecificFailure extends RuntimeException {}
}
