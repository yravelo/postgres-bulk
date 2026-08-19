# Contributing

PostgreSQL Bulk currently targets Java 17 bytecode and supports development on Java 17 or 21. Use
the checked-in Maven Wrapper; Docker is required for integration and example tests.

## Build

```bash
cd code/postgres-bulk-parent
./mvnw clean verify
```

The build runs unit tests, PostgreSQL Testcontainers integration tests, the standalone example
consumer, Spotless and warning-free public Javadocs. Apply Java formatting with:

```bash
./mvnw spotless:apply
```

Before submitting a change, also run from the repository root:

```bash
./scripts/check-documentation.sh
git diff --check
```

## Compatibility

The baseline uses PostgreSQL 15.18. Supported server, Boot, Hibernate and pgJDBC boundaries are
documented in [compatibility](docs/architecture/compatibility.md). Representative overrides are:

```bash
./mvnw clean verify -Dpostgres.version=18.4-alpine
./mvnw clean verify -Dspring-boot.version=3.5.0
./mvnw clean verify -pl postgres-bulk-hibernate -am -Dhibernate.version=6.6.55.Final
./mvnw clean verify -pl postgres-bulk-pgjdbc -am -Dpostgresql.version=42.7.13
```

Do not claim a new supported version without a green job and updated evidence.

## Module boundaries

- core remains Java SE and framework-independent;
- pgJDBC and Hibernate are sibling adapters and must not depend on each other;
- Spring Data composes core/pgJDBC through the metadata port;
- Boot auto-configuration is composition only;
- the starter contains no production Java;
- examples and benchmarks are non-published consumers.

Read [module boundaries](docs/architecture/module-boundaries.md) before moving dependencies or
types. Record a meaningful architectural change in an ADR.

## Benchmarks

Benchmarks are explicit and never a normal-build performance gate:

```bash
JAVA_HOME=/path/to/jdk-21 ./scripts/run-benchmarks.sh smoke smoke-local
```

Changes based on performance must include methodology, raw evidence and uncertainty. Do not infer a
universal threshold from one host.

## Pull requests

Keep changes within one phase/problem, add tests for failure paths, preserve root causes and avoid
sensitive values in errors or metrics. Update user documentation when observable behavior changes.
Do not add generated IDs, retries, adaptive lookup or new mapping support merely to simplify an
example; document friction and propose the behavior separately.
