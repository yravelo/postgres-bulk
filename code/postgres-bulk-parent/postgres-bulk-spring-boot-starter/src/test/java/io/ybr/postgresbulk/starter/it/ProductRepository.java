package io.ybr.postgresbulk.starter.it;

import io.ybr.postgresbulk.springdata.repository.PostgresBulkRepository;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository
    extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}
