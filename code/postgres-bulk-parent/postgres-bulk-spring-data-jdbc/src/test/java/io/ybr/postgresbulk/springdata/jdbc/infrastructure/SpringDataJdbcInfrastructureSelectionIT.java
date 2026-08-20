package io.ybr.postgresbulk.springdata.jdbc.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SpringDataJdbcInfrastructureSelectionIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_jdbc_infrastructure")
          .withUsername("postgres_bulk_jdbc_infrastructure")
          .withPassword("postgres_bulk_jdbc_infrastructure");

  private static JdbcTemplate control;

  @BeforeAll
  static void createTable() {
    control =
        new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    control.execute(
        "CREATE TABLE infrastructure_rows ("
            + "id bigint PRIMARY KEY, source text NOT NULL DEFAULT current_setting('application_name'))");
  }

  @BeforeEach
  void cleanTable() {
    control.execute("TRUNCATE infrastructure_rows");
  }

  @Test
  void twoJdbcOperationsWithoutExplicitPrimaryFailRepositoryCreation() {
    AnnotationConfigApplicationContext context = repositoryContext(null);
    try {
      RuntimeException failure = assertThrows(RuntimeException.class, context::refresh);
      assertInstanceOf(NoUniqueBeanDefinitionException.class, mostSpecificCause(failure));
    } finally {
      context.close();
    }
  }

  @Test
  void explicitInfrastructureSelectionUsesOnlyTheChosenDatasource() {
    try (AnnotationConfigApplicationContext contextA = repositoryContext("source-a")) {
      contextA.refresh();
      contextA
          .getBean(InfrastructureRepository.class)
          .bulkInsert(List.of(new InfrastructureRow(1L)));
    }
    try (AnnotationConfigApplicationContext contextB = repositoryContext("source-b")) {
      contextB.refresh();
      contextB
          .getBean(InfrastructureRepository.class)
          .bulkInsert(List.of(new InfrastructureRow(2L)));
    }

    assertEquals(
        List.of("source-a", "source-b"),
        control.queryForList("SELECT source FROM infrastructure_rows ORDER BY id", String.class));
  }

  @Test
  void multipleTransactionManagersRequirePrimaryOrMethodQualifier() {
    try (AnnotationConfigApplicationContext ambiguous = transactionContext(false)) {
      ambiguous.refresh();
      RuntimeException failure =
          assertThrows(
              RuntimeException.class, () -> ambiguous.getBean(TransactionProbe.class).automatic());
      assertInstanceOf(NoUniqueBeanDefinitionException.class, mostSpecificCause(failure));
      assertEquals("source-b", ambiguous.getBean(TransactionProbe.class).explicitB());
    }

    try (AnnotationConfigApplicationContext primary = transactionContext(true)) {
      primary.refresh();
      TransactionProbe probe = primary.getBean(TransactionProbe.class);
      assertEquals("source-a", probe.automatic());
      assertEquals("source-b", probe.explicitB());
    }
  }

  private static AnnotationConfigApplicationContext repositoryContext(String selected) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    HikariDataSource sourceA = pool("source-a");
    HikariDataSource sourceB = pool("source-b");
    context.registerBean(
        "dataSourceA",
        HikariDataSource.class,
        () -> sourceA,
        bean -> bean.setDestroyMethodName("close"));
    context.registerBean(
        "dataSourceB",
        HikariDataSource.class,
        () -> sourceB,
        bean -> bean.setDestroyMethodName("close"));
    context.registerBean("jdbcOperationsA", JdbcOperations.class, () -> new JdbcTemplate(sourceA));
    context.registerBean("jdbcOperationsB", JdbcOperations.class, () -> new JdbcTemplate(sourceB));

    DataSource selectedDataSource = "source-b".equals(selected) ? sourceB : sourceA;
    context.registerBean(
        "dataSource", DataSource.class, () -> selectedDataSource, bean -> bean.setPrimary(true));
    context.registerBean(
        "namedParameterJdbcOperations",
        NamedParameterJdbcOperations.class,
        () -> new NamedParameterJdbcTemplate(selectedDataSource));
    context.registerBean(
        "transactionManager",
        PlatformTransactionManager.class,
        () -> new JdbcTransactionManager(selectedDataSource));
    if (selected != null) {
      context.registerBean(
          "jdbcOperations",
          JdbcOperations.class,
          () -> new JdbcTemplate(selectedDataSource),
          bean -> bean.setPrimary(true));
    }
    context.register(RepositoryConfiguration.class);
    return context;
  }

  private static AnnotationConfigApplicationContext transactionContext(boolean primaryA) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    HikariDataSource sourceA = pool("source-a");
    HikariDataSource sourceB = pool("source-b");
    context.registerBean(
        "dataSourceA",
        HikariDataSource.class,
        () -> sourceA,
        bean -> bean.setDestroyMethodName("close"));
    context.registerBean(
        "dataSourceB",
        HikariDataSource.class,
        () -> sourceB,
        bean -> bean.setDestroyMethodName("close"));
    context.registerBean(
        "txA",
        PlatformTransactionManager.class,
        () -> new JdbcTransactionManager(sourceA),
        bean -> bean.setPrimary(primaryA));
    context.registerBean(
        "txB", PlatformTransactionManager.class, () -> new JdbcTransactionManager(sourceB));
    context.register(TransactionProbeConfiguration.class);
    return context;
  }

  private static HikariDataSource pool(String applicationName) {
    HikariConfig pool = new HikariConfig();
    String jdbcUrl = POSTGRES.getJdbcUrl();
    pool.setJdbcUrl(
        jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "ApplicationName=" + applicationName);
    pool.setUsername(POSTGRES.getUsername());
    pool.setPassword(POSTGRES.getPassword());
    pool.setMaximumPoolSize(2);
    pool.setMinimumIdle(0);
    return new HikariDataSource(pool);
  }

  private static Throwable mostSpecificCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  interface InfrastructureRepository
      extends CrudRepository<InfrastructureRow, Long>,
          PostgresBulkJdbcRepository<InfrastructureRow> {}

  @Table("infrastructure_rows")
  record InfrastructureRow(@Id Long id) {}

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EnableJdbcRepositories(
      basePackageClasses = SpringDataJdbcInfrastructureSelectionIT.class,
      considerNestedRepositories = true,
      jdbcOperationsRef = "namedParameterJdbcOperations",
      transactionManagerRef = "transactionManager")
  static class RepositoryConfiguration extends AbstractJdbcConfiguration {

    @Bean
    SpringDataJdbcEntityMetadataResolver postgresBulkJdbcMetadataResolver(
        JdbcConverter converter, JdbcCustomConversions conversions) {
      return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class TransactionProbeConfiguration {

    @Bean
    TransactionProbe transactionProbe(
        @Qualifier("dataSourceA") DataSource dataSourceA,
        @Qualifier("dataSourceB") DataSource dataSourceB) {
      return new TransactionProbe(dataSourceA, dataSourceB);
    }
  }

  static class TransactionProbe {

    private final DataSource sourceA;
    private final DataSource sourceB;

    TransactionProbe(DataSource sourceA, DataSource sourceB) {
      this.sourceA = sourceA;
      this.sourceB = sourceB;
    }

    @Transactional
    public String automatic() {
      assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
      return selectedResource();
    }

    @Transactional(transactionManager = "txB")
    public String explicitB() {
      assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
      return selectedResource();
    }

    private String selectedResource() {
      if (TransactionSynchronizationManager.hasResource(sourceA)) {
        return "source-a";
      }
      if (TransactionSynchronizationManager.hasResource(sourceB)) {
        return "source-b";
      }
      throw new AssertionError("No JDBC transaction resource is bound");
    }
  }
}
