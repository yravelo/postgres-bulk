package io.github.postgresbulk.benchmarks;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BenchmarkDataset {

  static final long SEED = 0x5EED_14L;
  private static final LocalDate BASE_DATE = LocalDate.of(2024, 1, 1);
  private static final Instant BASE_INSTANT = Instant.parse("2024-01-01T00:00:00Z");

  private BenchmarkDataset() {}

  static List<BenchmarkRow> rows(int size) {
    List<BenchmarkRow> rows = new ArrayList<>(size);
    for (int index = 0; index < size; index++) {
      rows.add(row(index));
    }
    return List.copyOf(rows);
  }

  static List<String> codes(int size) {
    List<String> codes = new ArrayList<>(size);
    for (int index = 0; index < size; index++) {
      codes.add(code(index));
    }
    return List.copyOf(codes);
  }

  private static BenchmarkRow row(int index) {
    return new BenchmarkRow(
        new UUID(SEED, index + 1L),
        code(index),
        "deterministic benchmark row " + index + " — PostgreSQL Bulk",
        BigDecimal.valueOf((index * 104_729L) % 100_000_000L, 2),
        index % 2 == 0,
        BASE_DATE.plusDays(index % 3_650L),
        BASE_INSTANT.plusSeconds(index * 17L),
        index % 7 == 0 ? null : "nullable note " + index);
  }

  private static String code(int index) {
    return "BENCH-%08d".formatted(index);
  }
}
