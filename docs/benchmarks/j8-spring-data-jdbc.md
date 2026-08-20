# Spring Data JDBC J8 benchmark report

## Scope and conclusion

J8 reused the Phase 14 JMH harness and added equivalent Spring Data JDBC workloads. On this one
local host, the public `PostgresBulkJdbcRepository.bulkInsert` path was faster than
`CrudRepository.saveAll` at every measured size and had no consistent time or allocation penalty
relative to the warmed low-level COPY engine. These observations are a reproducible local
baseline, not a universal performance claim.

The production default remains `batchSize=1000`. Although larger COPY batches improved point
estimates at 100K, this run does not justify trading away failure granularity, bounded work units
and multi-environment uncertainty. SQL `IN` won the measured lookup point estimates through 10K;
no adaptive lookup policy or universal crossover follows from these data.

## Architecture and measurement boundary

The existing non-publishable `postgres-bulk-benchmarks` module now contains a JDBC-only Spring
context alongside the existing JPA context. The JDBC benchmark configuration is deliberately
excluded from the JPA application scan so the two repository factories cannot claim the same
fixture. This is benchmark harness isolation, not a product change.

All insert contenders use the same Hikari pool, PostgreSQL container, table, indexes, assigned UUID
policy and pregenerated rows. Each invocation truncates outside timing, performs the operation and
commit inside timing, then checks count and representative values outside timing. Metadata and COPY
encoders are warm in the primary baseline. Lookup uses the same 100K-row target and index; both
paths include transaction, materialization and commit.

## Environment

Measured on 2026-08-20 on one interactive, unisolated machine:

- Intel Core i7-12700H, 14 cores/20 threads, up to 4.7 GHz;
- 30 GiB RAM and 8 GiB swap; Ubuntu x86_64, kernel `7.0.0-28-generic`;
- Eclipse Temurin `21.0.12+8`, project bytecode Java 17;
- Docker client/server 29.7.0, API 1.55;
- PostgreSQL `15.18-alpine`, image digest
  `sha256:3d0f7584ed7d04e27fa050d6683a74746608faf21f202be78460d679cc56461f`;
- Spring Boot 3.5.16, Framework 6.2.19, Spring Data JDBC/Relational 3.5.13,
  pgJDBC 42.7.11 and JMH 1.37;
- `-Xms1g -Xmx3g`, one thread, one fork, two 1 s warmups and three 1 s measurements;
- `-prof gc` reports normalized allocated bytes per operation, not peak memory.

The host retained interactive load and CPU frequency was not pinned. JMH errors below are 99.9%
intervals; three samples frequently produce wide intervals.

## Dataset and contenders

Seed `0x5EED14` produces rows with UUID, `String`, `BigDecimal`, `Boolean`, `LocalDate`, `Instant`
and a nullable value every seventh row. The target has a UUID primary key and unique `code` index.

The four insert paths are:

1. Spring Data JDBC `CrudRepository.saveAll`;
2. prepared JDBC batch with `reWriteBatchedInserts=true` and batch 1,000;
3. public `PostgresBulkJdbcRepository.bulkInsert`, batch 1,000;
4. prepared low-level `PostgresBulkJdbcOperations`, batch 1,000.

## Insert results

The table reports each complete baseline run, their point-estimate mean and derived throughput.

| Rows | Contender | Run 1 ms/op ± error | Run 2 ms/op ± error | Mean ms/op | Rows/s |
| ---: | --- | ---: | ---: | ---: | ---: |
| 10 | saveAll | 1.72 ± 9.76 | 1.65 ± 4.14 | 1.68 | 5,935 |
| 10 | JDBC batch | 1.20 ± 1.66 | 0.98 ± 1.10 | 1.09 | 9,193 |
| 10 | public bulk API | 0.79 ± 1.35 | 0.86 ± 3.42 | 0.82 | 12,158 |
| 10 | low-level COPY | 0.84 ± 3.51 | 0.79 ± 0.13 | 0.82 | 12,266 |
| 100 | saveAll | 5.00 ± 23.20 | 4.76 ± 5.17 | 4.88 | 20,494 |
| 100 | JDBC batch | 2.39 ± 3.56 | 3.68 ± 6.27 | 3.04 | 32,941 |
| 100 | public bulk API | 1.92 ± 1.77 | 1.57 ± 2.00 | 1.74 | 57,416 |
| 100 | low-level COPY | 1.42 ± 4.20 | 1.65 ± 2.85 | 1.53 | 65,254 |
| 1K | saveAll | 23.04 ± 20.57 | 21.80 ± 11.96 | 22.42 | 44,606 |
| 1K | JDBC batch | 7.96 ± 3.86 | 14.05 ± 27.74 | 11.01 | 90,868 |
| 1K | public bulk API | 5.37 ± 13.46 | 6.80 ± 9.75 | 6.09 | 164,333 |
| 1K | low-level COPY | 6.06 ± 6.90 | 6.86 ± 1.64 | 6.46 | 154,805 |
| 10K | saveAll | 169.44 ± 97.43 | 168.56 ± 60.25 | 169.00 | 59,170 |
| 10K | JDBC batch | 68.80 ± 82.08 | 80.13 ± 115.77 | 74.46 | 134,293 |
| 10K | public bulk API | 68.60 ± 59.98 | 54.97 ± 50.49 | 61.79 | 161,845 |
| 10K | low-level COPY | 60.63 ± 18.95 | 56.30 ± 28.79 | 58.47 | 171,038 |
| 100K | saveAll | 1,718.03 ± 1,165.34 | 1,792.63 ± 2,346.65 | 1,755.33 | 56,969 |
| 100K | JDBC batch | 635.14 ± 496.40 | 921.30 ± 3,345.89 | 778.22 | 128,498 |
| 100K | public bulk API | 588.25 ± 830.72 | 604.36 ± 399.84 | 596.30 | 167,700 |
| 100K | low-level COPY | 585.97 ± 212.40 | 614.17 ± 260.97 | 600.07 | 166,646 |

The public bulk point estimate used 51.2%, 64.3%, 72.9%, 63.4% and 66.0% less time than `saveAll`
from 10 through 100K. Against JDBC batch its point estimate was 24.4%, 42.6%, 44.7%, 17.0% and
23.4% lower. Intervals are broad, so these are descriptions of this run rather than categorical
winner claims.

The 1M characterization omitted `saveAll`: at 100K it already allocated about 1.87 GB and the
fork heap is 3 GB. At 1M, JDBC batch measured 6,273.88 ± 3,323.72 ms, public bulk
6,332.16 ± 22,994.66 ms and low-level COPY 5,356.30 ± 1,769.15 ms. This single noisy profile is
useful for scale characterization only.

## Adapter overhead and allocations

Metadata was warmed before comparing the public adapter and low-level engine.

| Rows | Public ms | Low-level ms | Time delta | Public bytes/op | Low-level bytes/op | Allocation delta |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 0.82 | 0.82 | +0.9% | 114,464 | 110,240 | +3.8% |
| 100 | 1.74 | 1.53 | +13.7% | 368,530 | 370,115 | -0.4% |
| 1K | 6.09 | 6.46 | -5.8% | 2,934,621 | 3,242,735 | -9.5% |
| 10K | 61.79 | 58.47 | +5.7% | 32,062,517 | 33,258,035 | -3.6% |
| 100K | 596.30 | 600.07 | -0.6% | 330,450,540 | 309,244,273 | +6.9% |

There is no consistent measurable time or allocation penalty in these two short runs, and the
data do not identify a causal source for the deltas. At 100K, `saveAll` allocated approximately
1.87 GB, JDBC batch 193 MB, public bulk 330 MB and low-level COPY 309 MB.

Custom converter, embedded/nested and `AggregateReference` were not given separate JMH contenders:
doing so would stop the main paths from sharing one row shape. Their correctness remains covered by
the J7 PostgreSQL integration matrix. This is an explicit secondary-characterization omission.

## COPY batch-size results

Public bulk inserted 100K rows:

| Batch | Run 1 ms/op ± error | Run 2 ms/op ± error | Mean ms/op | Rows/s | Run variation |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 1,461.96 ± 5,745.19 | 1,669.58 ± 4,205.46 | 1,565.77 | 63,866 | 13.3% |
| 1,000 | 589.05 ± 668.13 | 580.35 ± 983.26 | 584.70 | 171,029 | 1.5% |
| 10,000 | 371.43 ± 501.46 | 357.61 ± 89.92 | 364.52 | 274,333 | 3.8% |
| 100,000 | 334.19 ± 34.12 | 334.87 ± 70.07 | 334.53 | 298,926 | 0.2% |

All-in-one was 42.8% faster than 1,000 by point estimate and 10,000 was 37.7% faster. The default
stays 1,000 because this is one local environment and larger batches increase the failure unit,
autocommit exposure for low-level callers and resource uncertainty.

## Lookup results

SQL `IN` is constructed outside timing and executed with `JdbcTemplate` plus the same
`EntityRowMapper` used by the public temporary-table path. Both return equivalent materialized
roots inside a comparable write transaction.

| Keys | SQL IN run 1 / run 2 ms | SQL IN mean | Temp COPY/JOIN run 1 / run 2 ms | Temp mean |
| ---: | ---: | ---: | ---: | ---: |
| 10 | 0.30 ± 1.11 / 0.23 ± 0.70 | 0.26 | 2.74 ± 6.34 / 2.44 ± 4.15 | 2.59 |
| 100 | 1.78 ± 3.34 / 1.69 ± 2.41 | 1.74 | 4.46 ± 5.68 / 2.23 ± 5.63 | 3.34 |
| 1K | 6.92 ± 16.89 / 7.22 ± 6.96 | 7.07 | 11.76 ± 28.42 / 11.04 ± 32.99 | 11.40 |
| 10K | 44.16 ± 6.07 / 47.14 ± 22.45 | 45.65 | 58.78 ± 25.60 / 65.00 ± 158.07 | 61.89 |

SQL `IN` won every J8 point estimate through 10K. A 100K `IN` case was omitted because it exceeds
the practical pgJDBC protocol parameter limit; the temporary-table API remains applicable to
massive and composite key semantics. No chunking or adaptive threshold was introduced.

Composite temporary-table lookup is viable and measured 14.53 ms at 100 keys, 22.68 ms at 1K and
53.60 ms at 10K (two-run means). It is characterization only because there is no equivalent single
portable `IN` contender. Temp indexes, `ANALYZE` and `EXPLAIN (ANALYZE, BUFFERS)` were deferred;
there is no productive optimization based on this baseline.

## Repetition, correctness and limitations

The smoke executed all 17 selected cases successfully but is not performance evidence. Both
baseline runs completed all 71 results and the large profile completed all five selected results.
Point-estimate run variation was generally small for composite and batch cases but reached 55.3%
for JDBC insert and 66.9% for one temp lookup case; statistical errors are retained in the tables.

Every invocation verifies row count and representative first/last values after insert, including
nullable data, and verifies lookup size/content. Transaction commit is in the timed region;
fixtures, table reset and assertions are not. No functional product bug was discovered. The only
issue found was cross-scanning of JDBC fixtures by the JPA benchmark application; the benchmark
context was isolated and the smoke run is its executable regression check.

The baseline does not model remote networks, concurrency, constrained containers, production
schemas/indexes, contention, cold metadata, peak memory, converter-specific performance or planner
tuning. It must not be used as a rigid threshold or performance gate.

## Evidence and reproduction

- [Baseline run 1 CSV](j8-baseline-run-1.csv) and [raw JSON](raw/j8-baseline-run-1.json)
- [Baseline run 2 CSV](j8-baseline-run-2.csv) and [raw JSON](raw/j8-baseline-run-2.json)
- [1M CSV](j8-large-1m.csv) and [raw JSON](raw/j8-large-1m.json)
- [Smoke JSON](raw/j8-smoke.json)

Run the existing commands documented in [methodology](methodology.md). The manual Benchmarks
workflow and scripts do not overwrite historical Phase 14 evidence and do not create release
artifacts.
