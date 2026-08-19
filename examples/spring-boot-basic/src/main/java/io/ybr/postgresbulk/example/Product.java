package io.ybr.postgresbulk.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Product() {}

  public Product(UUID id, String sku, String name, BigDecimal price, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.sku = Objects.requireNonNull(sku, "sku must not be null");
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.price = Objects.requireNonNull(price, "price must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public UUID id() {
    return id;
  }

  public String sku() {
    return sku;
  }

  public String name() {
    return name;
  }

  public BigDecimal price() {
    return price;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
