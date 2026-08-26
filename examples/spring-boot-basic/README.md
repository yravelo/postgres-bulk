# Spring Boot basic example

This standalone application demonstrates the public consumer API only:

- assigned-UUID `Product` mapping;
- `JpaRepository + PostgresBulkRepository` composition;
- default and explicit `BulkInsertOptions` batches;
- simple and composite bulk lookup keys;
- write-capable `@Transactional` service methods;
- rollback after a successful COPY call;
- default plus application-authorized schemas A/B and a quoted schema;
- target-aware insert, lookup and rollback through an application-owned customer allow-list;
- Micrometer counters with an application-owned simple registry, without requiring Actuator;
- Testcontainers integration test.

Its Maven parent is Spring Boot, not the library reactor parent. The only PostgreSQL Bulk
dependency is `postgres-bulk-spring-boot-starter`, so a standalone build catches hidden reactor or
internal-module dependencies.

## Run manually

The published release needs no local install. To exercise current `main` instead, install its
development snapshot once from the repository root and omit the `revision` override below:

```bash
cd code/postgres-bulk-parent
./mvnw clean install
cd ../..
```

Start the pinned local database and application:

```bash
docker compose -f examples/spring-boot-basic/compose.yaml up -d
code/postgres-bulk-parent/mvnw \
  -f examples/spring-boot-basic/pom.xml \
  -Drevision=0.1.0 \
  spring-boot:run
```

The command-line scenario imports two products, looks them up by SKU and prints affected rows,
COPY batches, result count and the observed insert-row counter. This example intentionally has no
web or Actuator dependency. An application may still add Actuator when it wants managed registries
or HTTP metric endpoints.

Stop and remove the local container:

```bash
docker compose -f examples/spring-boot-basic/compose.yaml down
```

The example uses Hibernate `create-drop` only to stay self-contained. Production applications
should manage default and runtime-schema migrations normally. The automated test provisions its
target schemas as a fixture; postgres-bulk never provisions them.

## Run the adoption test

Docker is required. The stable external-consumer path resolves only from Maven Central:

```bash
code/postgres-bulk-parent/mvnw \
  --batch-mode --no-transfer-progress \
  -f examples/spring-boot-basic/pom.xml \
  -Drevision=0.1.0 \
  clean verify
```

The test starts `postgres:15.18-alpine`, exercises default and A/B/quoted targets, target lookup and
rollback, both default insert overloads, simple/composite lookup, read-only rejection and metrics,
and verifies final row counts. It does not access pgJDBC engine internals or the library parent POM.

## Important semantics shown by the example

- Lookup methods require a write-capable `@Transactional` boundary because they create/load a
  temporary table; the test also proves that a read-only boundary is rejected.
- `importThenRollback` proves COPY participates in the surrounding Spring transaction.
- Assigned UUIDs avoid implying that generated identifiers are returned. They are not.
- COPY does not invoke `@PrePersist`, cascade associations or manage/refresh input objects.
- The explicit batch size is an example, not a universal tuning recommendation.
- `ProductImportService.targetForCustomer` is application code backed by a closed allow-list. It is
  not a library resolver, and arbitrary request input must not be converted directly into a schema.
