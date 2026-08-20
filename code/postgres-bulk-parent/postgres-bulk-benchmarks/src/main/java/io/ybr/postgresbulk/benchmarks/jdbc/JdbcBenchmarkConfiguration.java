package io.ybr.postgresbulk.benchmarks.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableJdbcRepositories(basePackageClasses = JdbcBenchmarkRepository.class)
class JdbcBenchmarkConfiguration extends AbstractJdbcConfiguration {

  @Bean(destroyMethod = "close")
  DataSource dataSource() {
    HikariConfig pool = new HikariConfig();
    pool.setJdbcUrl(requiredProperty("benchmark.jdbc.url"));
    pool.setUsername(requiredProperty("benchmark.jdbc.username"));
    pool.setPassword(requiredProperty("benchmark.jdbc.password"));
    pool.setMaximumPoolSize(4);
    pool.setMinimumIdle(1);
    return new HikariDataSource(pool);
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
  PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new JdbcTransactionManager(dataSource);
  }

  @Bean
  SpringDataJdbcEntityMetadataResolver postgresBulkJdbcMetadataResolver(
      JdbcConverter converter, JdbcCustomConversions conversions) {
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be supplied by BenchmarkRunner");
    }
    return value;
  }
}
