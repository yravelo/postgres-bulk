package io.ybr.postgresbulk.autoconfigure.jdbc;

import io.ybr.postgresbulk.springdata.jdbc.SpringDataJdbcEntityMetadataResolver;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.JdbcOperations;

/** Auto-configures the Spring Data JDBC metadata bridge used by the JDBC repository fragment. */
@AutoConfiguration(after = JdbcRepositoriesAutoConfiguration.class)
@ConditionalOnClass({
  PGConnection.class,
  JdbcOperations.class,
  JdbcConverter.class,
  RelationalMappingContext.class,
  PostgresBulkJdbcRepository.class
})
@ConditionalOnBean({
  DataSource.class,
  JdbcOperations.class,
  JdbcConverter.class,
  JdbcCustomConversions.class,
  RelationalMappingContext.class
})
@ConditionalOnProperty(
    prefix = "postgres-bulk",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnSingleCandidate(DataSource.class)
@Import(SingleJdbcOperationsConfiguration.class)
public final class PostgresBulkJdbcAutoConfiguration {

  /** Creates the auto-configuration instance managed by Spring Boot. */
  PostgresBulkJdbcAutoConfiguration() {}
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnSingleCandidate(JdbcOperations.class)
@Import(SingleJdbcConverterConfiguration.class)
class SingleJdbcOperationsConfiguration {}

@Configuration(proxyBeanMethods = false)
@ConditionalOnSingleCandidate(JdbcConverter.class)
@Import(SingleRelationalMappingContextConfiguration.class)
class SingleJdbcConverterConfiguration {}

@Configuration(proxyBeanMethods = false)
@ConditionalOnSingleCandidate(RelationalMappingContext.class)
@Import(SingleJdbcCustomConversionsConfiguration.class)
class SingleRelationalMappingContextConfiguration {}

@Configuration(proxyBeanMethods = false)
@ConditionalOnSingleCandidate(JdbcCustomConversions.class)
class SingleJdbcCustomConversionsConfiguration {

  @Bean
  @ConditionalOnMissingBean(SpringDataJdbcEntityMetadataResolver.class)
  SpringDataJdbcEntityMetadataResolver postgresBulkJdbcEntityMetadataResolver(
      JdbcConverter converter, JdbcCustomConversions conversions) {
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }
}
