# Spring Boot Data JDBC example

This standalone application adopts PostgreSQL Bulk through one direct library dependency:
`postgres-bulk-spring-boot-starter-data-jdbc`. It uses normal Spring Boot datasource and Spring
Data JDBC repository discovery; no resolver, fragment implementation, transaction manager, JPA,
or Hibernate configuration is declared by the application.

The example demonstrates assigned UUID identifiers, an embedded value, default and explicit COPY
batching, `BulkWriteResult`, simple and composite lookup, outer rollback, rejection from a
read-only transaction, and application-authorized A/B/quoted runtime schemas. Only aggregate-root
rows are written.

## Run manually

The published release needs no local install. To exercise current `main` instead, install its
development snapshot and omit the `revision` override in the commands below:

```bash
cd code/postgres-bulk-parent
./mvnw install
```

Start the exact PostgreSQL image and run the application from the same directory:

```bash
docker compose -f ../../examples/spring-boot-data-jdbc/compose.yaml up -d
./mvnw -f ../../examples/spring-boot-data-jdbc/pom.xml -Drevision=0.1.0 spring-boot:run
docker compose -f ../../examples/spring-boot-data-jdbc/compose.yaml down
```

The schema is initialized from `schema.sql`. Reruns use unique demo SKUs.

The command-line path uses the default table. The automated adoption test additionally provisions
runtime schemas as fixtures and invokes the application service's closed customer-to-`TableName`
mapping. Production applications must provision/migrate schemas separately and must not map
arbitrary request input directly to an identifier.

## Verify automatically

Docker must be available. Testcontainers starts the PostgreSQL patch selected by
`postgres.version`; no H2 substitute or Docker-absence skip is used.

```bash
cd code/postgres-bulk-parent
./mvnw -f ../../examples/spring-boot-data-jdbc/pom.xml -Drevision=0.1.0 clean verify
```

The test validates Boot startup, repository/fragment discovery, default and explicit inserts,
A/B/quoted targets, target-aware lookup and rollback, simple/composite lookup, and read-only
rejection through public APIs.
