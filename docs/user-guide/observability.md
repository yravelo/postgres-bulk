# Observability

Instrumentation is enabled by default when an application supplies an `ObservationRegistry`.
Without one, the path is a no-op. A `MeterRegistry` adds row and batch counters. The starter does
not create registries, exporters, tracing or HTTP endpoints.

| Name | Type | Tags | Meaning |
|---|---|---|---|
| `postgres.bulk.operation` | Observation; timer with a Micrometer handler | `operation=insert\|lookup`, `outcome=success\|error`; Boot bounds `error` to `none\|error` | One complete public call, including validation/empty input |
| `postgres.bulk.rows` | Counter, unit `rows` | `operation=insert\|lookup` | Rows from a successful final result |
| `postgres.bulk.batches` | Counter, unit `batches` | `operation=insert` | COPY batches from a successful insert result |

The library never tags entity, repository, table, SQL, SQLState, exception class, row or key.
Failed operations update the observation but not result counters; no partial progress is reported.

## Optional Actuator

Add Actuator in the application if its registry integration or endpoints are useful:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Expose metrics explicitly for a local example:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

When the application also has a web stack, query, for example,
`/actuator/metrics/postgres.bulk.rows`. Production endpoint exposure and security remain
application responsibilities. The command-line example reads the same counter directly from its
`MeterRegistry` and does not add a web dependency.

Disable only bulk instrumentation with:

```properties
postgres-bulk.observability.enabled=false
```

`postgres-bulk.enabled=false` disables the entire auto-configuration instead.

## Transaction boundary caveat

The observation ends when the repository bulk method returns or throws, before an outer Spring
transaction completes. Therefore `outcome=success` means the bulk work completed; it does not prove
that an outer transaction later committed. A subsequent outer rollback does not rewrite metrics.

Measured overhead is small relative to larger operations but visible and noisy for tiny calls. The
current baseline observed +21.6% at 100 rows and +5.9% at 1,000 rows, with overlapping intervals;
those point estimates are not a universal overhead guarantee. See [performance](performance.md).
