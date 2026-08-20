package io.ybr.postgresbulk.benchmarks.jdbc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("benchmark_row")
record JdbcBenchmarkRow(
    @Id UUID id,
    String code,
    String description,
    BigDecimal amount,
    Boolean active,
    @Column("business_date") LocalDate businessDate,
    @Column("created_at") Instant createdAt,
    String note)
    implements Persistable<UUID> {

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
