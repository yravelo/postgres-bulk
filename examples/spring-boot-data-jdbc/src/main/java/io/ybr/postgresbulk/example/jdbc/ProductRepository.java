package io.ybr.postgresbulk.example.jdbc;

import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface ProductRepository
    extends CrudRepository<Product, UUID>, PostgresBulkJdbcRepository<Product> {}
