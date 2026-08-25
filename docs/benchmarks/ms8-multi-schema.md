# MS8 — Multi-schema benchmark baseline and technical closure

## Result

MS8 measured the operation-scoped target path without changing production code or public API. On
this host, explicit targeting did not produce a consistent end-to-end penalty across pgJDBC,
Spring Data JPA and Spring Data JDBC. The clearest repeated point-estimate cost was JPA INSERT at
10–1.000 rows; it fell from roughly 0.3–0.6 ms absolute at small sizes to no consistent penalty at
10K/100K. Most other pairs changed sign between runs or had broad JMH intervals.

This is local evidence, not an SLO or a claim that a runtime target is free. The result supports
the existing design: build target-specific SQL locally per invocation and do not retain a
target-keyed cache. **NO TARGET-KEYED CACHE**.

## Benchmark architecture and equivalence

Each pair operates on the same physical `public.benchmark_row` table:

- default: the existing unqualified mapping, resolved by PostgreSQL to `public`;
- runtime: `TableName.of("public", "benchmark_row")` passed to the target-aware overload;
- contenders: prepared pgJDBC engine, public Spring Data JPA fragment and public Spring Data JDBC
  fragment;
- INSERT: identical rows, columns, batch 1.000, pool and committed transaction;
- lookup: identical 100K-row target, key subsets, temporary-table workflow and materializer.

Dataset construction, target-table reset and correctness checks remain outside timing. Metadata,
column accessors and encoders are warmed and reused. Only target validation plus qualified SQL
construction differs inside each default/runtime pair.

Before JMH starts, one prepared low-level engine executes and verifies
default/A/default/B/default/C plus a quoted `Benchmark Quoted` schema. Counts and target-aware
lookups prove isolation, then every target is truncated. This is a correctness gate, not a
performance sample.

## Methodology and environment

- JMH 1.37, `AverageTime`, one thread and one fork;
- 2 warmup iterations x 1 s and 3 measured iterations x 1 s;
- `-Xms1g -Xmx3g`, `-prof gc`, JMH error at 99.9%;
- INSERT sizes: 10, 100, 1K, 10K and 100K;
- lookup sizes: 10, 100, 1K and 10K against 100K stored rows;
- two complete baselines plus one non-forked 100 ms smoke;
- CPU: Intel Core i7-12700H, 14 cores/20 threads; RAM visible: 30 GiB;
- Ubuntu kernel `7.0.0-28-generic`, Docker Engine 29.7.0;
- OpenJDK 25.0.3 Ubuntu, project bytecode Java 17;
- PostgreSQL 15.18 Alpine; Boot 3.5.16; Hibernate 6.6.53.Final;
- Spring Data JDBC/Relational 3.5.13; pgJDBC 42.7.11;
- Git base `9b893f2324c4f7470e12f6d2216890113ed4bde9` plus the MS8 worktree.

The host was interactive, without CPU pinning, fixed frequency or reserved resources. Phase 14 and
J8 used Temurin 21, so their absolute values are not combined with this OpenJDK 25 run.

## INSERT target-overhead results

Delta is `runtime - default`; positive is slower. Full ms/op, error, rows/s and bytes/op are in the
versioned CSV files.

| API | Rows | Run 1 delta | Run 1 | Run 2 delta | Run 2 |
| --- | ---: | ---: | ---: | ---: | ---: |
| pgJDBC | 10 | +0.029 ms | +4.0% | -0.056 ms | -10.4% |
| pgJDBC | 100 | -0.202 ms | -11.5% | -0.046 ms | -4.3% |
| pgJDBC | 1K | -1.081 ms | -15.4% | +0.373 ms | +7.1% |
| pgJDBC | 10K | +6.010 ms | +10.7% | +7.768 ms | +15.0% |
| pgJDBC | 100K | +26.024 ms | +4.6% | -46.287 ms | -8.1% |
| Spring Data JDBC | 10 | +0.447 ms | +57.4% | -0.089 ms | -10.7% |
| Spring Data JDBC | 100 | +0.274 ms | +18.6% | -0.521 ms | -26.7% |
| Spring Data JDBC | 1K | +1.073 ms | +19.8% | -0.642 ms | -10.9% |
| Spring Data JDBC | 10K | -6.611 ms | -10.8% | -3.088 ms | -4.9% |
| Spring Data JDBC | 100K | +2.117 ms | +0.4% | -31.625 ms | -5.4% |
| Spring Data JPA | 10 | +0.482 ms | +58.6% | +0.321 ms | +36.4% |
| Spring Data JPA | 100 | +0.370 ms | +18.4% | +0.535 ms | +34.0% |
| Spring Data JPA | 1K | +0.638 ms | +9.0% | +0.372 ms | +5.2% |
| Spring Data JPA | 10K | -0.093 ms | -0.2% | -1.383 ms | -2.4% |
| Spring Data JPA | 100K | -53.845 ms | -9.4% | -1.218 ms | -0.2% |

Small percentages can hide sub-millisecond absolute deltas; large percentages at 10/100 rows also
have wide intervals and high run variance. There is no basis here for changing batch 1.000 or
adding adaptive target behavior. Existing J8 batch-size evidence already covers 100/1K/10K/all-in
for COPY; repeating it would not isolate target overhead.

## Lookup target-overhead results

| API | Keys | Run 1 delta | Run 1 | Run 2 delta | Run 2 |
| --- | ---: | ---: | ---: | ---: | ---: |
| pgJDBC | 10 | -0.521 ms | -20.1% | -0.774 ms | -30.3% |
| pgJDBC | 100 | -0.444 ms | -12.8% | -1.539 ms | -44.5% |
| pgJDBC | 1K | -0.690 ms | -5.9% | +0.477 ms | +4.5% |
| pgJDBC | 10K | +5.299 ms | +8.8% | +2.733 ms | +5.0% |
| Spring Data JDBC | 10 | -0.297 ms | -12.5% | +0.009 ms | +0.6% |
| Spring Data JDBC | 100 | +0.713 ms | +30.2% | -1.327 ms | -37.0% |
| Spring Data JDBC | 1K | -0.094 ms | -0.9% | +1.228 ms | +12.5% |
| Spring Data JDBC | 10K | +0.802 ms | +1.3% | -1.525 ms | -2.6% |
| Spring Data JPA | 10 | -0.135 ms | -4.4% | -0.254 ms | -8.3% |
| Spring Data JPA | 100 | -1.155 ms | -30.0% | +0.508 ms | +14.0% |
| Spring Data JPA | 1K | +1.622 ms | +16.8% | -0.904 ms | -9.2% |
| Spring Data JPA | 10K | +1.159 ms | +2.4% | +0.808 ms | +1.6% |

The signs and magnitudes are mostly unstable. Even where both runs share a sign, intervals and host
noise prevent a universal claim. MS8 therefore does not introduce a lookup threshold or strategy
selector.

## Allocations and SQL construction

`gc.alloc.rate.norm` is published for every pair. Small-call runtime deltas are generally in the
low kilobytes or change sign; some large-row deltas are much larger and inconsistent, showing that
three one-second samples on this interactive host are insufficient for a retention claim from GC
rate alone. No monotonic allocation growth follows target cardinality.

SQL construction was not exposed as a separate product API or reached through reflection solely to
microbenchmark package-private builders. The end-to-end pairs include the real target resolution,
identifier quoting and COPY/CTAS/JOIN SQL construction. The pure core experiment below isolates
resolution itself. This separation is more representative than a synthetic public facade.

## Schema-cardinality experiment

Targets are built in `@Setup`; one invocation resolves the requested count and returns the same
supplied object. Times are microseconds per whole invocation, not per target. Allocations show
same/many bytes/op for each run.

| Targets | Run 1 same | Run 1 many | Delta | Run 2 same | Run 2 many | Delta | Run 1 B/op | Run 2 B/op |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 0.126 | 0.121 | -4.5% | 0.124 | 0.119 | -4.1% | 0.0009/0.0008 | 0.0009/0.0008 |
| 1K | 1.184 | 1.191 | +0.6% | 1.160 | 1.154 | -0.5% | 0.0082/0.0083 | 0.0080/0.0080 |
| 10K | 12.426 | 18.704 | +50.5% | 12.201 | 18.027 | +47.7% | 0.0863/0.1297 | 0.0847/0.1250 |

At 10K, traversing many distinct prebuilt objects costs about 5.8–6.3 microseconds more in these
runs, while allocations remain profiler noise. That is consistent with per-call validation and
object-access locality, not cache insertion. The benchmark state retains its input array by design;
the product resolver returns each target by identity and has no collection in which to retain it.

## Cache and retained-state audit

- `TableName` has only immutable `schema` and `table` fields; resolution returns the caller target.
- prepared pgJDBC state contains mapping, encoders and default SQL, never a map keyed by target;
- Hibernate metadata remains keyed by persistence-unit/type identity;
- Spring Data JDBC metadata remains keyed by converter/context/type and ID shape;
- each runtime COPY SQL and lookup `InvocationSql` is a local variable;
- the same singleton repositories/prepared engine serve default/A/B/C/quoted;
- no target appears in observability tags, Boot properties or connection state.

There is no heap dump or JFR retained-set capture in this run. The limitation is explicit: absence
of retained state is supported by object-identity checks, allocation profiles, structural source
audit and the existing MS2–MS7 concurrency/pool tests, not by a long-lived production heap study.

## Correctness, connection and concurrency

The MS8 preflight validates sequential alternation and quoted targets on PostgreSQL. Existing MS2–
MS7 integration/compatibility suites remain the evidence for concurrent A/B calls, pool size one,
unchanged `getSchema`/`search_path`, rollback, `REQUIRES_NEW`, conditional JDBC NESTED and JPA NESTED
rejection. MS8 found no product defect requiring reproduction outside JMH.

## Reproduction and evidence

```shell
./scripts/run-benchmarks.sh multi-schema-smoke ms8-smoke
./scripts/run-benchmarks.sh multi-schema-baseline ms8-baseline-run-1
./scripts/run-benchmarks.sh multi-schema-baseline ms8-baseline-run-2
./scripts/summarize-multi-schema-benchmarks.sh \
  docs/benchmarks/raw/ms8-baseline-run-1.json \
  docs/benchmarks/ms8-baseline-run-1.csv
```

- [Smoke raw JSON](raw/ms8-smoke.json)
- [Baseline run 1 raw JSON](raw/ms8-baseline-run-1.json) and [CSV](ms8-baseline-run-1.csv)
- [Baseline run 2 raw JSON](raw/ms8-baseline-run-2.json) and [CSV](ms8-baseline-run-2.csv)
- [Methodology](methodology.md)

The smoke numbers are not performance evidence. The raw baseline files contain every score,
99.9% error, JVM parameter and GC secondary metric. No older Phase 14 or J8 artifact was overwritten.

## Technical verdict and boundaries

The runtime `TableName` cost is small in absolute terms and normally amortized by I/O at bulk
sizes, but it is not universally zero. Library state does not grow with schema cardinality in the
implemented design. Multi-schema is technically closed on the supported generation without a new
cache, API, tenant resolver, security baseline or publication action.

```text
new functional feature implemented: no
runtime tenant resolution: no
schema-only convenience API implemented: no
target-keyed cache introduced: no
security baseline implemented: no
publication activated: no
release workflow executed: no
```
