# Spring Boot autoconfiguration for Spring Data JDBC

## Scope and module split

J6 adds two modules without changing the JPA artifacts:

```text
postgres-bulk-spring-data-jdbc
    <- postgres-bulk-spring-boot-autoconfigure-jdbc
    <- postgres-bulk-spring-boot-starter-data-jdbc
```

`postgres-bulk-spring-boot-autoconfigure-jdbc` is the JDBC composition root. The starter is a
dependency-only JAR with no production Java or resources. It aggregates
`spring-boot-starter-data-jdbc` and the JDBC auto-configuration; the latter brings the JDBC adapter,
pgJDBC adapter, core, and PostgreSQL driver. It does not bring Hibernate, Spring Data JPA,
Jakarta Persistence, Actuator, Testcontainers, or benchmarks into the production graph.

## Activation and ordering

Boot discovers `PostgresBulkJdbcAutoConfiguration` through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It runs after
`JdbcRepositoriesAutoConfiguration`, when Boot has declared its JDBC converter and mapping
infrastructure. Activation requires:

- pgJDBC, `JdbcOperations`, `JdbcConverter`, `RelationalMappingContext`, and
  `PostgresBulkJdbcRepository` on the classpath;
- beans of type `DataSource`, `JdbcOperations`, `JdbcConverter`, `JdbcCustomConversions`, and
  `RelationalMappingContext`;
- `postgres-bulk.enabled=true`, or no value for that property;
- one unambiguous candidate for every required infrastructure type.

The classpath condition is structural and startup does not acquire a connection or inspect JDBC
metadata. A JPA-only application therefore creates no JDBC resolver. A JDBC-only application has
no dependency on, or condition involving, JPA.

## Bean and back-off policy

The only bean contributed is `SpringDataJdbcEntityMetadataResolver`, built from Boot's effective
`JdbcConverter` and `JdbcCustomConversions`. A user-provided resolver wins by type. The
auto-configuration does not create `JdbcOperations`, a `DataSource`, a transaction manager,
repositories, converters, mapping contexts, or observability infrastructure.

`@ConditionalOnSingleCandidate` is applied independently to `DataSource`, `JdbcOperations`,
`JdbcConverter`, `RelationalMappingContext`, and `JdbcCustomConversions`. One bean is accepted; one
`@Primary` bean among several is accepted; unresolved ambiguity makes the resolver back off. No
candidate is selected by name, declaration order, or repository proximity. Applications with
multiple stores must expose an explicit primary infrastructure set or provide their own resolver
and repository wiring.

## Repository and transaction behavior

Repository discovery remains the external-fragment registration owned by
`postgres-bulk-spring-data-jdbc` and its `META-INF/spring.factories`. J6 does not add
`@EnableJdbcRepositories`; normal Boot repository discovery is sufficient. The fragment remains
opt-in by extending `PostgresBulkJdbcRepository<T>`.

Boot owns transaction-manager creation and selection. J6 creates no manager and does not qualify
one. The J5 contract remains authoritative: fragment methods use `REQUIRED`, need an active
write-capable physical transaction, participate in outer rollback, support `REQUIRES_NEW`, and
support `NESTED` only when a JDBC manager for the same datasource owns the savepoint. Calls without
a transaction and calls in read-only transactions fail before COPY work.

The fragment implementation is package-private and non-final. This is infrastructure, not public
API; being non-final permits Boot's default class-based transaction proxy without exposing the
implementation to applications.

## JDBC-only, JPA-only, and both stacks

- **JDBC-only:** the JDBC starter starts a normal Boot Data JDBC application, discovers repositories,
  and executes insert/lookup against PostgreSQL without postgres-bulk configuration classes.
- **JPA-only:** absence of JDBC infrastructure prevents the JDBC auto-configuration from matching;
  the existing JPA auto-configuration and starter are unchanged.
- **Both starters:** the JPA and JDBC resolver types, auto-configuration classes, and module names
  are distinct, so both composition roots can coexist. Repositories for each store remain separate.
  A repository that combines both bulk fragments is still rejected, and two local transaction
  managers do not imply cross-store atomicity.

## Properties and metadata

J6 reuses the existing `postgres-bulk.enabled` kill switch and adds no property. The JDBC
auto-configuration JAR publishes configuration metadata for that shared Boolean property, default
`true`. It deliberately adds no global schema, tenant, datasource, converter, transaction-manager,
batch, or temporary-table property.

Schema remains mapping-derived. Future schema-per-operation or tenant resolution can be added at an
operation boundary without overriding entity metadata through a global `postgres-bulk.schema`.

## Installation from the local snapshot

The artifacts are not published to Maven Central. After `./mvnw install` in
`code/postgres-bulk-parent`, a JDBC application can use this single postgres-bulk dependency:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter-data-jdbc</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The application still configures its normal Spring datasource and declares a normal Spring Data
JDBC repository extending the bulk fragment. No manual resolver bean is needed for the single-stack
case.

## Non-goals and evidence

J6 adds no JDBC observability, unified starter, global repository enabling, automatic retry,
multi-schema runtime support, Boot 4 adaptation, publication, benchmark, or new compatibility
matrix. `ApplicationContextRunner` covers activation, missing classes/beans, opt-out, user back-off,
ambiguity and primary selection. A real starter smoke test covers repository discovery, default and
explicit batching, assigned/generated identifiers, simple/composite lookup, custom conversion,
embedded values, rollback, read-only, `REQUIRES_NEW`, and conditioned `NESTED` against PostgreSQL.
The isolated `verification/spring-boot-jdbc-consumer` fixture repeats startup, discovery, insert,
lookup, rollback, and read-only checks against the installed snapshot from outside the reactor.
