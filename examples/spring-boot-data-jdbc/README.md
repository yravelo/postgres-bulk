# Spring Boot Data JDBC example

This standalone application adopts PostgreSQL Bulk through one direct library dependency:
`postgres-bulk-spring-boot-starter-data-jdbc`. It uses normal Spring Boot datasource and Spring
Data JDBC repository discovery; no resolver, fragment implementation, transaction manager, JPA,
or Hibernate configuration is declared by the application.

The example demonstrates assigned UUID identifiers, an embedded value, default and explicit COPY
batching, `BulkWriteResult`, simple and composite lookup, outer rollback, and rejection from a
read-only transaction. Only aggregate-root rows are written.

## Run manually

Install the development snapshot first:

```bash
cd code/postgres-bulk-parent
./mvnw install
```

Start the exact PostgreSQL image and run the application from the same directory:

```bash
docker compose -f ../../examples/spring-boot-data-jdbc/compose.yaml up -d
./mvnw -f ../../examples/spring-boot-data-jdbc/pom.xml spring-boot:run
docker compose -f ../../examples/spring-boot-data-jdbc/compose.yaml down
```

The schema is initialized from `schema.sql`. Reruns use unique demo SKUs.

## Verify automatically

Docker must be available. Testcontainers starts the PostgreSQL patch selected by
`postgres.version`; no H2 substitute or Docker-absence skip is used.

```bash
cd code/postgres-bulk-parent
./mvnw install
./mvnw -f ../../examples/spring-boot-data-jdbc/pom.xml clean verify
```

The test validates Boot startup, repository/fragment discovery, default and explicit inserts,
simple/composite lookup, rollback, and read-only rejection through public APIs.
