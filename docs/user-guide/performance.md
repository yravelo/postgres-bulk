# Performance

J8 adds a controlled Spring Data JDBC comparison to the historical Phase 14 JPA evidence. On the
measured local host, the public JDBC bulk API used 51–73% less time than Spring Data JDBC
`CrudRepository.saveAll` by point estimate from 10 through 100K rows. It also used 17–45% less time
than JDBC batch by point estimate. JMH intervals were often broad, so these figures describe this
environment and are not a guarantee that COPY always wins.

The benchmark suite measures complete public persistence calls against PostgreSQL 15.18 on one
local machine. It includes transaction commit and excludes dataset construction, table reset and
correctness checks from the timed region. Results are evidence for that environment, not service
level objectives or universal claims.

MS8 separately compares default mapping with the equivalent explicit runtime target for pgJDBC,
Spring Data JPA and Spring Data JDBC. Across two OpenJDK 25 runs, most deltas changed sign or had
broad intervals. JPA INSERT showed a repeated 0.3–0.6 ms point-estimate cost at 10–1.000 rows, then
no consistent penalty at 10K/100K. A 10.000-target pure resolution loop completed in roughly
18 µs versus 12 µs for one repeated target, without material allocation. This supports local SQL
construction and **NO TARGET-KEYED CACHE**, not a zero-overhead claim.

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

J8 therefore explicitly keeps the production default at 1,000. On its 100K JDBC workload,
all-in-one and 10K were faster point estimates than 1K, but the evidence is limited to one host and
does not outweigh failure granularity, resource usage and low-level autocommit exposure.

## Lookup

Phase 14 JPA results had no monotonic crossover. In the separate J8 JDBC baseline, SQL `IN` won
the point estimates at 10, 100, 1K and 10K keys. Neither baseline supports a universal heuristic;
query planning, indexes, target size, key distribution, parameter limits and network all matter.
Temporary COPY/JOIN remains useful for very large and composite key sets, but the library does not
choose strategies adaptively.

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

Read [methodology](../benchmarks/methodology.md), the historical [Phase 14 baseline](../benchmarks/baseline.md),
the [J8 Spring Data JDBC report](../benchmarks/j8-spring-data-jdbc.md) and raw JSON before making a
tuning decision. For operation-scoped targets, also read the
[MS8 multi-schema report](../benchmarks/ms8-multi-schema.md). The suite has no performance
thresholds and never runs as part of normal `verify`.
