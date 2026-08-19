# Getting started

## Install the development snapshot

Version `0.1.0-SNAPSHOT` is not published to Maven Central. From a checkout:

```bash
cd code/postgres-bulk-parent
./mvnw clean install
```

Add only the library starter to a normal Spring Boot 3.5 application:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The starter brings Data JPA, Hibernate, pgJDBC and the bulk adapters. Actuator remains optional.

## Opt in a repository

```java
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                PostgresBulkRepository<Product, UUID> {
}
```

Only repositories that extend `PostgresBulkRepository` receive the extra methods. The starter does
not replace `SimpleJpaRepository` globally and opens no database connection at startup.

## Put operations in a service transaction

```java
@Transactional
public BulkWriteResult importProducts(List<Product> products) {
    return repository.bulkInsert(products);
}
```

Use assigned identifiers for the simplest adoption path. COPY does not populate generated IDs,
invoke JPA callbacks or make input objects managed. The repository method creates a `REQUIRED`
transaction when it is called through the Spring proxy without an outer transaction.

For lookup, define exact physical key columns and use a write-capable transaction. Do not mark the
method `readOnly=true`; the implementation needs a temporary table.

The [executable example](../../examples/spring-boot-basic/README.md) contains entity, repository,
default and explicit batches, simple/composite lookup, rollback, Testcontainers and metrics. Its
POM uses the Spring Boot parent rather than the library parent, so it also acts as a real external
consumer test.
