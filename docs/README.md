# PostgreSQL Bulk documentation

This index separates adoption guidance from implementation details.

## User guide

- [Getting started](user-guide/getting-started.md)
- [Spring Data JDBC](user-guide/spring-data-jdbc.md)
- [Dynamic schemas / schema-per-tenant](user-guide/multi-schema.md)
- [Transactions](user-guide/transactions.md)
- [Mapping support](user-guide/mapping-support.md)
- [Bulk lookup](user-guide/bulk-lookup.md)
- [Observability](user-guide/observability.md)
- [Performance](user-guide/performance.md)
- [Error handling and retry](user-guide/error-handling.md)
- [Executable Spring Boot example](../examples/spring-boot-basic/README.md)
- [Executable Spring Boot Data JDBC example](../examples/spring-boot-data-jdbc/README.md)

## Architecture

- [System overview](architecture/overview.md)
- [Public API inventory](architecture/public-api.md)
- [Module boundaries](architecture/module-boundaries.md)
- [Bulk insert](architecture/bulk-insert.md)
- [Bulk lookup](architecture/bulk-lookup.md)
- [Transactions and failures](architecture/transactions-and-failures.md)
- [Spring Boot auto-configuration](architecture/spring-boot-autoconfiguration.md)
- [Spring Data JDBC J0 investigation](architecture/spring-data-jdbc-investigation.md)
- [Spring Data JDBC J1 metadata](architecture/spring-data-jdbc-metadata.md)
- [Spring Data JDBC J2 root-only bulk insert](architecture/spring-data-jdbc-bulk-insert.md)
- [Spring Data JDBC J3 root-only bulk lookup](architecture/spring-data-jdbc-bulk-lookup.md)
- [Spring Data JDBC J4 repository integration](architecture/spring-data-jdbc-repository-integration.md)
- [Spring Data JDBC J5 transactions and robustness](architecture/spring-data-jdbc-transactions-and-robustness.md)
- [Spring Data JDBC J6 Boot auto-configuration](architecture/spring-data-jdbc-boot-autoconfiguration.md)
- [Multi-schema MS0 investigation](architecture/multi-schema-investigation.md)
- [Operation-scoped physical target contract](architecture/operation-scoped-physical-target.md)
- [pgJDBC multi-schema bulk insert](architecture/multi-schema-bulk-insert.md)
- [pgJDBC multi-schema bulk lookup](architecture/multi-schema-bulk-lookup.md)
- [Hibernate/Spring Data JPA multi-schema](architecture/multi-schema-hibernate-jpa.md)
- [Spring Data JDBC multi-schema](architecture/multi-schema-spring-data-jdbc.md)
- [Spring Boot multi-schema composition](architecture/multi-schema-spring-boot-composition.md)

## Compatibility

- [Supported versions](architecture/compatibility.md)
- [Compatibility evidence](architecture/compatibility-evidence.md)

## Decisions and evidence

- [Architecture decision records](decisions/)
- [Benchmark methodology](benchmarks/methodology.md)
- [Benchmark baseline](benchmarks/baseline.md)
- [Spring Data JDBC J8 benchmark baseline](benchmarks/j8-spring-data-jdbc.md)
- [0.1.0 release notes](releases/0.1.0.md)
- [0.1.0 release readiness](releases/release-readiness.md)
- [0.1.0 public API baseline](releases/0.1.0-public-api.txt)

## Planning

- [Implementation plan](plans/implementation-plan.md)
- [Release acceptance criteria](plans/release-acceptance-criteria.md)
- [Spring Data JDBC roadmap](plans/spring-data-jdbc-roadmap.md)
- [Multi-schema roadmap](plans/multi-schema-roadmap.md)
