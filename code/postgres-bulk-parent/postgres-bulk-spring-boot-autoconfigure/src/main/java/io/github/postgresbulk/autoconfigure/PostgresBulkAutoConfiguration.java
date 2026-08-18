package io.github.postgresbulk.autoconfigure;

import io.github.postgresbulk.hibernate.HibernateEntityMetadataResolver;
import io.github.postgresbulk.springdata.repository.JpaEntityMetadataResolver;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;
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

  /** Bounds the Micrometer-provided error tag for PostgreSQL bulk operation timers. */
  @Bean
  @ConditionalOnProperty(
      prefix = "postgres-bulk.observability",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  MeterFilter postgresBulkObservationErrorTagFilter() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        if (!"postgres.bulk.operation".equals(id.getName()) || id.getTag("error") == null) {
          return id;
        }
        List<Tag> tags =
            id.getTags().stream()
                .map(
                    tag ->
                        "error".equals(tag.getKey()) && !"none".equals(tag.getValue())
                            ? Tag.of("error", "error")
                            : tag)
                .toList();
        return id.replaceTags(tags);
      }
    };
  }
}
