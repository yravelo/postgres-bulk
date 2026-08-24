# PostgreSQL Bulk

PostgreSQL Bulk uses PostgreSQL `COPY` for high-throughput bulk insert and temporary-table bulk
lookup while preserving an opt-in Spring Data repository experience. It integrates with Spring
Data JPA/Hibernate and Spring Data JDBC through separate starters and repository fragments; it is
not an ORM replacement.

The project is currently `0.1.0-SNAPSHOT`. Coordinates and API may change before the first release,
and the artifacts are **not published to Maven Central**.

## Why

`JpaRepository.saveAll(...)` applies normal ORM semantics: entity state management, callbacks,
dirty checking and identifier handling. Those semantics are valuable, but can be unnecessary work
for an import whose rows are already prepared. `bulkInsert` writes the mapped relational values
directly with PostgreSQL COPY.

`bulkInsert != saveAll`: choose COPY only when bypassing the ORM lifecycle is acceptable.

## Features

- Bulk insert through PostgreSQL COPY with bounded, single-pass batching.
- Typed bulk lookup through a temporary table, COPY and JOIN.
- Simple and composite lookup keys through `BulkKeyMetadata`.
- Opt-in Spring Data repository fragment and Spring Boot starter.
- Opt-in Spring Data JDBC repository fragment and dedicated Boot starter with root-only semantics.
- Transaction-aware access to the Hibernate connection and runtime mapping metadata.
- Operation-level Micrometer observations and bounded metrics.
- Contractual support for PostgreSQL 15–18 and Java 17/21.

## Requirements

| Component | Supported |
|---|---|
| Java | 17 and 21 |
| Spring Boot | 3.5.0–3.5.16 |
| Spring Data JPA | 3.5.0–3.5.13, through the Boot BOM |
| Spring Data JDBC / Relational | 3.5.0–3.5.13, through the Boot BOM |
| Hibernate ORM | 6.6.15–6.6.55 |
| pgJDBC | 42.7.5–42.7.13 |
| PostgreSQL | 15–18 |

Java 25 has been validated experimentally but is not part of the support contract. Spring Boot 4,
Spring Data 4 and Hibernate 7 are not supported by this artifact generation. See the complete
[compatibility policy](docs/architecture/compatibility.md).

## Installation

There is no remote release yet. Install the current snapshot into your local Maven repository:

```bash
cd code/postgres-bulk-parent
./mvnw clean install
```

Then choose the starter for the application's persistence stack. The JPA starter brings Spring Data
JPA, Hibernate and pgJDBC:

Maven:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Gradle:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.github.yravelo:postgres-bulk-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

For a Spring Data JDBC application, use the JDBC-only starter instead:

```xml
<dependency>
  <groupId>io.github.yravelo</groupId>
  <artifactId>postgres-bulk-spring-boot-starter-data-jdbc</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

It does not bring JPA or Hibernate. Normal Boot datasource and repository configuration is enough;
the metadata resolver is auto-configured when the JDBC infrastructure has one unambiguous
candidate. See the [Spring Data JDBC user guide](docs/user-guide/spring-data-jdbc.md).

## Spring Data JDBC adoption

Use Spring Data annotations and extend both repository interfaces; no postgres-bulk configuration
class is needed for a normal single-datasource Boot application:

```java
@Table("product")
public record Product(@Id UUID id, String sku, String category) {}

public interface ProductRepository
        extends CrudRepository<Product, UUID>,
                PostgresBulkJdbcRepository<Product> {
}
```

Run non-empty operations inside a write-capable transaction. Repository fragment methods use
`REQUIRED`; an outer transaction controls commit or rollback:

```java
@Transactional
public BulkWriteResult importProducts(List<Product> input) {
    return products.bulkInsert(input, BulkInsertOptions.ofBatchSize(5_000));
}
```

Lookup uses explicit physical key columns and may be simple or composite:

```java
BulkKeyMetadata<String> skuKey = BulkKeyMetadata.of(
    String.class,
    List.of(ColumnMetadata.of("sku", String.class, sku -> sku))
);

@Transactional
public List<Product> findBySkus(List<String> skus) {
    return products.findAllByBulkKey(skus, skuKey);
}
```

For an application-authorized physical target, Spring Data JDBC uses the same operation-scoped
shape as JPA:

```java
TableName tenantA = TableName.of("tenant_a", "product");

products.bulkInsert(tenantA, input);
products.bulkInsert(input, BulkInsertOptions.ofBatchSize(5_000), tenantA);
List<Product> found = products.findAllByBulkKey(skus, skuKey, tenantA);
```

The resolver metadata, ID policy and `EntityRowMapper` stay structural; the target is not retained
by the repository or inferred from tenant context.

The JDBC path writes only aggregate-root columns: it does not persist child collections, invoke
callbacks/auditing, or populate generated identifiers. Assigned IDs are copied; generated numeric
IDs are omitted and remain unset in the input object. Read-only and unproxied no-transaction calls
are invalid for non-empty work. Choose the JPA starter for JPA/Hibernate repositories and the JDBC
starter for Spring Data JDBC repositories; both share the same pgJDBC COPY engine but use different
metadata, materialization, lifecycle and transaction integration.

The complete executable application is
[`examples/spring-boot-data-jdbc`](examples/spring-boot-data-jdbc/README.md).

## Quick Start

Use an assigned identifier in the first integration. Generated identifiers have different
semantics, described below.

```java
@Entity
@Table(name = "product")
public class Product {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

Opt in by adding the repository fragment; no custom repository factory or configuration class is
needed with the starter:

```java
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                PostgresBulkRepository<Product, UUID> {
}
```

Put the operation behind a transactional application service:

```java
@Service
public class ProductImportService {
    private static final BulkKeyMetadata<String> SKU_KEY = BulkKeyMetadata.of(
        String.class,
        List.of(ColumnMetadata.of("sku", String.class, sku -> sku))
    );

    private final ProductRepository products;

    ProductImportService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public BulkWriteResult importProducts(List<Product> input) {
        BulkWriteResult result = products.bulkInsert(input);
        System.out.printf("rows=%d batches=%d%n", result.affectedRows(), result.batches());
        return result;
    }

    @Transactional
    public List<Product> findBySkus(List<String> skus) {
        return products.findAllByBulkKey(skus, SKU_KEY);
    }
}
```

For an explicit batch size:

```java
BulkWriteResult result = products.bulkInsert(
    input,
    BulkInsertOptions.ofBatchSize(10_000)
);
```

For a schema-qualified physical target selected and authorized by the application:

```java
TableName tenantA = TableName.of("tenant_a", "product");

products.bulkInsert(tenantA, input);
products.bulkInsert(input, BulkInsertOptions.ofBatchSize(10_000), tenantA);
List<Product> found = products.findAllByBulkKey(skus, SKU_KEY, tenantA);
```

The target is operation-scoped and applies only to the root table. The library does not resolve
tenants, change `search_path`/connection schema, redirect associations, or cache by target. See the
[Hibernate/JPA multi-schema contract](docs/architecture/multi-schema-hibernate-jpa.md).

The default remains 1,000. A larger value improved throughput in the current local benchmark, but
also creates a larger COPY failure unit and increases partial-persistence exposure with autocommit.
Measure with your schema and workload instead of treating 10,000 as a universal recommendation.

The complete, compiled application is in
[`examples/spring-boot-basic`](examples/spring-boot-basic/README.md).

## Transaction semantics

Repository methods use Spring `REQUIRED`: they create a read-write transaction when none exists or
join the current transaction. `REQUIRES_NEW` is supported through normal Spring propagation.
`NESTED` is unsupported with the validated `JpaTransactionManager`/Hibernate baseline.

> **Do not call bulk lookup from `@Transactional(readOnly = true)`.** Lookup creates a temporary
> table and loads it with COPY, so it requires a write-capable transaction. Insert is rejected in a
> read-only transaction as well.

`bulkInsert` participates in the surrounding transaction. A rollback reverses all its COPY batches.
At the low-level JDBC API, autocommit can persist completed batches before a later batch fails. See
the user-facing [transaction guide](docs/user-guide/transactions.md).

## bulkInsert vs saveAll

| Aspect | `saveAll` | `bulkInsert` |
|---|---|---|
| ORM lifecycle | Normal JPA/Hibernate lifecycle | Bypassed |
| Managed entities | Normal JPA state transitions | Input objects do not become managed |
| Generated IDs | Normal provider semantics | Not returned or populated |
| `@PrePersist`, listeners, Hibernate events | Invoked as applicable | Not invoked |
| Persistence context | Kept consistent by ORM operations | May be stale after direct COPY |
| Write path | General ORM SQL | PostgreSQL COPY |
| Transaction | JPA transaction | Participates in the Spring/JPA transaction |

COPY does not call `flush()`, `clear()` or `refresh()`. Avoid mixing managed instances and direct
bulk changes without an explicit consistency plan. Flush pending ORM changes before a lookup when
they must be visible; clear or refresh only when your application semantics require it.

Assigned IDs are included in COPY. Hibernate-generated identity/sequence columns are omitted from
the physical mapping, but `bulkInsert` does not return the generated values or synchronize them
into the input objects.

## Supported mappings

The Hibernate adapter supports basic fields, field/property access, mapped superclasses,
`@Embedded`, `@EmbeddedId`, assigned IDs, insertable `@ManyToOne` foreign keys,
`AttributeConverter`, and enum `STRING`/`ORDINAL` mappings. It uses Hibernate's relational Java
representation after conversion; it does not simply call `Enum.name()` for every entity enum.

Associations are projected to foreign keys only. COPY does not cascade or persist associated
entities. See the tested [mapping support table](docs/user-guide/mapping-support.md), including
unsupported secondary tables, multi-table inheritance, soft delete mappings, collection tables
and custom types without an encoder.

## Bulk lookup semantics

A lookup key names the exact physical target column or columns. Simple keys can use `String`,
`UUID` or another supported relational type; composite keys use a value object with ordered
components.

- Duplicate input keys do not duplicate results.
- Duplicate target rows with the same key are all returned; key metadata does not imply UNIQUE.
- Missing keys produce no result.
- A null key or component is rejected with `IllegalArgumentException` without including its value.
- Result order is not guaranteed.

See [Bulk lookup](docs/user-guide/bulk-lookup.md) for simple/composite examples and performance
guidance.

## Observability

When an `ObservationRegistry` is available, each public insert or lookup emits one
`postgres.bulk.operation` observation. With a `MeterRegistry`, successful operations also update:

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `postgres.bulk.operation` | Observation/timer | `operation`, `outcome`; bounded `error` in Boot | One complete public bulk call |
| `postgres.bulk.rows` | Counter | `operation=insert\|lookup` | Successfully processed rows |
| `postgres.bulk.batches` | Counter | `operation=insert` | Successfully completed COPY batches |

Actuator is optional and is not installed by the starter. Add it in the application when its
registry integration is wanted; HTTP metrics endpoints additionally require a web application.
Disable only library instrumentation with:

```properties
postgres-bulk.observability.enabled=false
```

An observed bulk success can still be followed by rollback of an outer transaction. Details and an
Actuator example are in [Observability](docs/user-guide/observability.md).

## Performance

In the documented J8 local environment, the public Spring Data JDBC bulk API used 51–73% less time
than `CrudRepository.saveAll` and 17–45% less time than JDBC batch by point estimate from 10 through
100K rows. Its warmed adapter overhead versus low-level COPY was not consistent across sizes.
That is evidence for one host, dataset and schema—not a universal production claim.

SQL `IN` won the J8 point estimates from 10 through 10K keys. Historical JPA results were
non-monotonic. Schema, data distribution and query plan matter; there is no automatic or
recommended key-count threshold. See the [performance guide](docs/user-guide/performance.md),
[J8 report](docs/benchmarks/j8-spring-data-jdbc.md) and historical
[Phase 14 baseline](docs/benchmarks/baseline.md).

## Limitations

- PostgreSQL only; the driver connection must unwrap to pgJDBC.
- No generated-ID return/population, JPA callbacks, cascades or automatic persistence-context sync.
- No secondary-table or multi-table entity insert, supported inheritance discriminator insert,
  collection-table insert, or Hibernate soft-delete literal generation.
- No built-in JSON/JSONB, array or arbitrary custom-type encoder.
- No automatic retry, adaptive lookup strategy, index/`ANALYZE` tuning or guaranteed result order.
- Runtime multi-schema insert and lookup accept an explicit qualified `TableName` through the
  low-level pgJDBC facade and both Spring Data JPA and JDBC fragments. Boot composition does not
  resolve or retain a target; existing target-free operations still use mapped metadata.
- Spring Boot 4, Spring Data 4 and Hibernate 7 are unsupported in this artifact generation.

## How it fits

```text
Application
    ↓
PostgresBulkRepository
    ↓
Spring Data adapter
    ├── Hibernate runtime metadata
    └── pgJDBC COPY engine
            ↓
        PostgreSQL
```

## Documentation

- [Documentation index](docs/README.md)
- [User guide](docs/user-guide/README.md)
- [Executable Spring Boot example](examples/spring-boot-basic/README.md)
- [Architecture](docs/architecture/overview.md)
- [Compatibility and evidence](docs/architecture/compatibility.md)
- [Spring Data JDBC benchmarks](docs/benchmarks/j8-spring-data-jdbc.md)
- [Multi-schema investigation and roadmap](docs/architecture/multi-schema-investigation.md)
- [Low-level multi-schema bulk insert](docs/architecture/multi-schema-bulk-insert.md)
- [Low-level multi-schema bulk lookup](docs/architecture/multi-schema-bulk-lookup.md)
- [Hibernate/Spring Data JPA multi-schema](docs/architecture/multi-schema-hibernate-jpa.md)
- [Spring Data JDBC multi-schema](docs/architecture/multi-schema-spring-data-jdbc.md)
- [Contributing](CONTRIBUTING.md)

## License

Licensed under the [Apache License 2.0](LICENSE).
