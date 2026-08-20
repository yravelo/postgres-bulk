# Getting started

## Install the development snapshot

Version `0.1.0-SNAPSHOT` is not published to Maven Central. From a checkout:

```bash
cd code/postgres-bulk-parent
./mvnw clean install
```

Choose one library starter for a normal Spring Boot 3.5 application. For Spring Data JPA:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The starter brings Data JPA, Hibernate, pgJDBC and the bulk adapters. Actuator remains optional.

For Spring Data JDBC:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter-data-jdbc</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The JDBC starter brings Spring Data JDBC, pgJDBC and the JDBC bulk adapter without JPA or
Hibernate. Actuator and Testcontainers remain optional/test concerns.

## Opt in a repository

```java
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                PostgresBulkRepository<Product, UUID> {
}
```

Only repositories that extend `PostgresBulkRepository` receive the extra methods. The starter does
not replace `SimpleJpaRepository` globally and opens no database connection at startup.

The corresponding JDBC repository is:

```java
public interface ProductRepository
        extends CrudRepository<Product, UUID>,
                PostgresBulkJdbcRepository<Product> {
}
```

Boot discovers both the normal JDBC repository and external bulk fragment. With one unambiguous
JDBC infrastructure set it also creates `SpringDataJdbcEntityMetadataResolver`; applications with
multiple candidates must select their infrastructure explicitly.

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

The [JPA executable example](../../examples/spring-boot-basic/README.md) contains entity, repository,
default and explicit batches, simple/composite lookup, rollback, Testcontainers and metrics. Its
POM uses the Spring Boot parent rather than the library parent, so it also acts as a real external
consumer test.

The [Spring Data JDBC executable example](../../examples/spring-boot-data-jdbc/README.md) provides
the same adoption path using `CrudRepository`, the JDBC starter, Docker Compose and Testcontainers.
