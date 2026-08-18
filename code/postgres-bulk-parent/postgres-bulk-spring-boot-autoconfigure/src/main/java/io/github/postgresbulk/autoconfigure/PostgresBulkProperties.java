package io.github.postgresbulk.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PostgreSQL bulk auto-configuration settings.
 *
 * @param enabled whether PostgreSQL bulk infrastructure is auto-configured
 * @param observability operation-level observability settings
 */
@ConfigurationProperties("postgres-bulk")
public record PostgresBulkProperties(
    @DefaultValue("true") boolean enabled, @DefaultValue Observability observability) {

  /**
   * Operation-level observability settings.
   *
   * @param enabled whether bulk observations are emitted when infrastructure is available
   */
  public record Observability(@DefaultValue("true") boolean enabled) {}
}
