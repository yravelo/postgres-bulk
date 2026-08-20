# Performance

The existing numbers measure the JPA integration, JDBC batch and direct COPY workloads defined by
the current benchmark module. J7 did not run new benchmarks and does not claim a Spring Data JDBC
comparison. A controlled Spring Data JDBC versus `JdbcTemplate`/JDBC batch/COPY comparison is
deferred to J8.

The benchmark suite measures complete public persistence calls against PostgreSQL 15.18 on one
local machine. It includes transaction commit and excludes dataset construction, table reset and
correctness checks from the timed region. Results are evidence for that environment, not service
level objectives or universal claims.

## Insert summary

At 10, 100, 1K, 10K and 100K rows, COPY was faster than both default and batched JPA `saveAll` in
the documented baseline. It was 3.8–33.7% faster than JDBC batch depending on size. At one million
rows COPY and JDBC batch were close (5.65 s and 5.87 s point estimates), with broad uncertainty.

COPY allocated less than JPA but more than JDBC batch at 100K. A time advantage must not be
described as an allocation advantage.

## Batch size

The production default is 1,000. At 100K rows on the baseline host, batches of 10K/all-in-one
reduced round trips and improved throughput. Larger batches also mean:

- a larger COPY unit to cancel or reject;
- coarser progress/failure granularity;
- greater exposure to already committed work when low-level autocommit is used.

Do not change the default or recommend one value globally from this run. Benchmark representative
rows, constraints, network and transaction policy.

## Lookup

The observed winner changed with cardinality: SQL `IN` won at 10, 100 and 10K keys; temporary
COPY/JOIN won at 1K. There is no monotonic crossover or supported heuristic. Query planning,
indexes, target size, key distribution and network all influence the result.

## Observability

Enabled/disabled intervals overlapped. The relative point-estimate overhead was more visible for
100 rows than 1K, while absolute extra allocation was about 5.3 KB at 100 rows and within noise at
1K. Do not claim zero overhead.

## Reproduce

With Docker and Java 21:

```bash
JAVA_HOME=/path/to/jdk-21 ./scripts/run-benchmarks.sh smoke smoke-local
JAVA_HOME=/path/to/jdk-21 ./scripts/run-benchmarks.sh baseline baseline-local
```

Read [methodology](../benchmarks/methodology.md), [baseline](../benchmarks/baseline.md) and raw JSON
before making a tuning decision. The suite has no performance thresholds and never runs as part of
normal `verify`.
