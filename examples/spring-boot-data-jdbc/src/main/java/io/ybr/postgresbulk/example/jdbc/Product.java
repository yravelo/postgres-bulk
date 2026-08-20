package io.ybr.postgresbulk.example.jdbc;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("jdbc_product")
public record Product(
    @Id UUID id,
    String sku,
    String category,
    String name,
    BigDecimal price,
    @Embedded.Nullable(prefix = "shipping_") Address shippingAddress) {}
