package io.ybr.postgresbulk.springdata.coexistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import io.ybr.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.repository.PostgresBulkRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class JpaJdbcRepositoryCoexistenceIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk_jpa_jdbc")
          .withUsername("postgres_bulk_jpa_jdbc")
          .withPassword("postgres_bulk_jpa_jdbc");

  private static AnnotationConfigApplicationContext context;
  private static JpaRowRepository jpaRows;
  private static JdbcRowRepository jdbcRows;
  private static JdbcTemplate jdbc;
  private static JpaTransactionManager jpaTransactionManager;
  private static JdbcTransactionManager jdbcTransactionManager;

  @BeforeAll
  static void createContext() {
    context = new AnnotationConfigApplicationContext(CoexistenceConfiguration.class);
    jpaRows = context.getBean(JpaRowRepository.class);
    jdbcRows = context.getBean(JdbcRowRepository.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    jpaTransactionManager = context.getBean("jpaTransactionManager", JpaTransactionManager.class);
    jdbcTransactionManager =
        context.getBean("jdbcTransactionManager", JdbcTransactionManager.class);
    jdbc.execute(
        "ALTER TABLE coexist_jpa_rows ADD COLUMN IF NOT EXISTS backend_pid integer NOT NULL DEFAULT pg_backend_pid()");
    jdbc.execute(
        "CREATE TABLE coexist_jdbc_rows ("
            + "id bigint PRIMARY KEY, code text NOT NULL UNIQUE, "
            + "backend_pid integer NOT NULL DEFAULT pg_backend_pid())");
  }

  @BeforeEach
  void cleanTables() {
    jdbc.execute("TRUNCATE coexist_jpa_rows, coexist_jdbc_rows");
  }

  @AfterAll
  static void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void repositoriesUseTheirConfiguredStoreInfrastructureInOneContext() {
    assertEquals(
        new BulkWriteResult(1, 1), jpaRows.bulkInsert(List.of(new JpaRow(1L, "jpa-fragment"))));
    assertEquals(
        new BulkWriteResult(1, 1), jdbcRows.bulkInsert(List.of(new JdbcRow(2L, "jdbc-fragment"))));

    assertEquals("jpa-fragment", jpaRows.findById(1L).orElseThrow().code);
    assertEquals("jdbc-fragment", jdbcRows.findById(2L).orElseThrow().code());
    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM coexist_jpa_rows", Long.class));
    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM coexist_jdbc_rows", Long.class));
  }

  @Test
  void outerJpaTransactionDoesNotMakeTheJdbcRepositoryCallAtomic() {
    TransactionTemplate outer = new TransactionTemplate(jpaTransactionManager);
    outer.executeWithoutResult(
        status -> {
          jpaRows.saveAndFlush(new JpaRow(10L, "jpa-outer"));
          jdbcRows.bulkInsert(List.of(new JdbcRow(11L, "jdbc-inner")));

          Integer jpaPid =
              jdbc.queryForObject(
                  "SELECT backend_pid FROM coexist_jpa_rows WHERE id = 10", Integer.class);
          Integer jdbcPid =
              jdbc.queryForObject(
                  "SELECT backend_pid FROM coexist_jdbc_rows WHERE id = 11", Integer.class);
          assertEquals(jpaPid, jdbcPid);
          status.setRollbackOnly();
        });

    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM coexist_jpa_rows", Long.class));
    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM coexist_jdbc_rows", Long.class));
  }

  @Test
  void outerJdbcTransactionAndJpaRepositoryDoNotPromiseCrossManagerAtomicity() {
    TransactionTemplate outer = new TransactionTemplate(jdbcTransactionManager);
    assertThrows(
        CannotCreateTransactionException.class,
        () ->
            outer.executeWithoutResult(
                status -> {
                  jdbcRows.bulkInsert(List.of(new JdbcRow(20L, "jdbc-outer")));
                  jpaRows.saveAndFlush(new JpaRow(21L, "jpa-inner"));
                }));

    long jdbcCount =
        jdbc.queryForObject("SELECT count(*) FROM coexist_jdbc_rows WHERE id = 20", Long.class);
    long jpaCount =
        jdbc.queryForObject("SELECT count(*) FROM coexist_jpa_rows WHERE id = 21", Long.class);
    assertEquals(0L, jdbcCount + jpaCount);
  }

  interface JpaRowRepository
      extends JpaRepository<JpaRow, Long>, PostgresBulkRepository<JpaRow, Long> {}

  interface JdbcRowRepository
      extends CrudRepository<JdbcRow, Long>, PostgresBulkJdbcRepository<JdbcRow> {}

  @Entity
  @Table(name = "coexist_jpa_rows")
  static class JpaRow {
    @Id Long id;

    @Column(nullable = false, unique = true)
    String code;

    JpaRow() {}

    JpaRow(Long id, String code) {
      this.id = id;
      this.code = code;
    }
  }

  @org.springframework.data.relational.core.mapping.Table("coexist_jdbc_rows")
  record JdbcRow(@org.springframework.data.annotation.Id Long id, String code) {}

  @Configuration(proxyBeanMethods = false)
  @EnableJpaRepositories(
      basePackageClasses = JpaJdbcRepositoryCoexistenceIT.class,
      considerNestedRepositories = true,
      entityManagerFactoryRef = "entityManagerFactory",
      transactionManagerRef = "jpaTransactionManager")
  @EnableJdbcRepositories(
      basePackageClasses = JpaJdbcRepositoryCoexistenceIT.class,
      considerNestedRepositories = true,
      jdbcOperationsRef = "namedParameterJdbcOperations",
      transactionManagerRef = "jdbcTransactionManager")
  static class CoexistenceConfiguration extends AbstractJdbcConfiguration {

    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Bean
    NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    JdbcOperations jdbcOperations(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcTransactionManager jdbcTransactionManager(DataSource dataSource) {
      return new JdbcTransactionManager(dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan(JpaJdbcRepositoryCoexistenceIT.class.getPackageName());
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      Properties properties = new Properties();
      properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
      properties.setProperty("hibernate.show_sql", "false");
      factory.setJpaProperties(properties);
      return factory;
    }

    @Bean
    JpaTransactionManager jpaTransactionManager(
        EntityManagerFactory entityManagerFactory, DataSource dataSource) {
      JpaTransactionManager manager = new JpaTransactionManager(entityManagerFactory);
      manager.setDataSource(dataSource);
      return manager;
    }

    @Bean
    JpaEntityMetadataResolver bulkMetadataResolver() {
      return JpaEntityMetadataResolver.caching(HibernateEntityMetadataResolver::new);
    }

    @Bean
    SpringDataJdbcEntityMetadataResolver postgresBulkJdbcMetadataResolver(
        JdbcConverter converter, JdbcCustomConversions conversions) {
      return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
    }
  }
}
