package io.github.postgresbulk.starter.it;

import io.github.postgresbulk.springdata.repository.PostgresBulkRepository;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository
    extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}
