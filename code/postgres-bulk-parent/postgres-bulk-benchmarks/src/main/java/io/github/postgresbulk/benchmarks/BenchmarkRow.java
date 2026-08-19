package io.github.postgresbulk.benchmarks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "benchmark_row")
class BenchmarkRow implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 160)
  private String description;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private Boolean active;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(length = 80)
  private String note;

  protected BenchmarkRow() {}

  BenchmarkRow(
      UUID id,
      String code,
      String description,
      BigDecimal amount,
      Boolean active,
      LocalDate businessDate,
      Instant createdAt,
      String note) {
    this.id = id;
    this.code = code;
    this.description = description;
    this.amount = amount;
    this.active = active;
    this.businessDate = businessDate;
    this.createdAt = createdAt;
    this.note = note;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  String code() {
    return code;
  }

  String description() {
    return description;
  }

  BigDecimal amount() {
    return amount;
  }

  Boolean active() {
    return active;
  }

  LocalDate businessDate() {
    return businessDate;
  }

  Instant createdAt() {
    return createdAt;
  }

  String note() {
    return note;
  }
}
