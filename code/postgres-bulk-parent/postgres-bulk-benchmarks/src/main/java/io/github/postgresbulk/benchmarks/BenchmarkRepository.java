package io.github.postgresbulk.benchmarks;

import io.github.postgresbulk.springdata.repository.PostgresBulkRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BenchmarkRepository
    extends JpaRepository<BenchmarkRow, UUID>, PostgresBulkRepository<BenchmarkRow, UUID> {

  List<BenchmarkRow> findAllByCodeIn(Collection<String> codes);
}
