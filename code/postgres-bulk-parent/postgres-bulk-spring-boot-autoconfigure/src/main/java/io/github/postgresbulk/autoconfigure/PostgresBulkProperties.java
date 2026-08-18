package io.github.postgresbulk.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PostgreSQL bulk auto-configuration settings.
 *
 * @param enabled whether PostgreSQL bulk infrastructure is auto-configured
 */
@ConfigurationProperties("postgres-bulk")
public record PostgresBulkProperties(@DefaultValue("true") boolean enabled) {}
