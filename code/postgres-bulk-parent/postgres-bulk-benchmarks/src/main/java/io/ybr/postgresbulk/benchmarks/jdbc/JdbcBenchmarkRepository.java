package io.ybr.postgresbulk.benchmarks.jdbc;

import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface JdbcBenchmarkRepository
    extends CrudRepository<JdbcBenchmarkRow, UUID>, PostgresBulkJdbcRepository<JdbcBenchmarkRow> {}
