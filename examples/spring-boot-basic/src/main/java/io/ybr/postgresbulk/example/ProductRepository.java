package io.ybr.postgresbulk.example;

import io.ybr.postgresbulk.springdata.repository.PostgresBulkRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository
    extends JpaRepository<Product, UUID>, PostgresBulkRepository<Product, UUID> {}
