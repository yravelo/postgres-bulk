package io.ybr.postgresbulk.starter.it;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "phase10_product")
class Product {

  @Id Long id;

  @Column(nullable = false, unique = true)
  String sku;

  @Column(nullable = false)
  String name;

  Product() {}

  Product(Long id, String sku, String name) {
    this.id = id;
    this.sku = sku;
    this.name = name;
  }
}
