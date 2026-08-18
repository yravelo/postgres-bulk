package io.github.postgresbulk.autoconfigure;

import io.github.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.github.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Auto-configures the Hibernate metadata bridge used by the Spring Data bulk fragment. */
@AutoConfiguration(
    after = HibernateJpaAutoConfiguration.class,
    before = JpaRepositoriesAutoConfiguration.class)
@ConditionalOnClass(
    name = {
      "jakarta.persistence.EntityManagerFactory",
      "org.hibernate.Session",
      "org.postgresql.PGConnection",
      "org.springframework.data.jpa.repository.JpaRepository",
      "io.github.postgresbulk.springdata.repository.PostgresBulkRepository"
    })
@ConditionalOnBean(type = "jakarta.persistence.EntityManagerFactory")
@ConditionalOnProperty(
    prefix = "postgres-bulk",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(PostgresBulkProperties.class)
public final class PostgresBulkAutoConfiguration {

  /** Creates the auto-configuration instance managed by Spring Boot. */
  PostgresBulkAutoConfiguration() {}

  /** Creates the default persistence-unit-aware Hibernate metadata resolver. */
  @Bean
  @ConditionalOnMissingBean(JpaEntityMetadataResolver.class)
  JpaEntityMetadataResolver postgresBulkJpaEntityMetadataResolver() {
    return JpaEntityMetadataResolver.caching(HibernateEntityMetadataResolver::new);
  }
}
