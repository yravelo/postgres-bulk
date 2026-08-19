# PostgreSQL Bulk

PostgreSQL Bulk uses PostgreSQL `COPY` for high-throughput bulk insert and temporary-table bulk
lookup while preserving a Spring Data JPA repository experience. It integrates with Spring Boot
3.5 and Hibernate 6.6; it is not an ORM replacement.

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
- Transaction-aware access to the Hibernate connection and runtime mapping metadata.
- Operation-level Micrometer observations and bounded metrics.
- Contractual support for PostgreSQL 15–18 and Java 17/21.

## Requirements

| Component | Supported |
|---|---|
| Java | 17 and 21 |
| Spring Boot | 3.5.0–3.5.16 |
| Spring Data JPA | 3.5.0–3.5.13, through the Boot BOM |
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

Then use the starter. It already brings Spring Data JPA, Hibernate and pgJDBC.

Maven:

```xml
<dependency>
  <groupId>io.github.postgresbulk</groupId>
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
    implementation("io.github.postgresbulk:postgres-bulk-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

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

In the documented local environment, COPY outperformed JPA `saveAll` at all measured sizes and
was 3.8–33.7% faster than JDBC batch. That is evidence for one host, dataset and schema—not a
universal production claim. At one million rows, COPY and JDBC batch were close, and COPY allocated
more memory than JDBC.

Lookup performance was non-monotonic: SQL `IN` won at 10, 100 and 10,000 keys, while temporary
COPY/JOIN won at 1,000. Schema, data distribution and query plan matter; there is no automatic or
recommended key-count threshold. See the [performance guide](docs/user-guide/performance.md) and
[raw benchmark baseline](docs/benchmarks/baseline.md).

## Limitations

- PostgreSQL only; the driver connection must unwrap to pgJDBC.
- No generated-ID return/population, JPA callbacks, cascades or automatic persistence-context sync.
- No secondary-table or multi-table entity insert, supported inheritance discriminator insert,
  collection-table insert, or Hibernate soft-delete literal generation.
- No built-in JSON/JSONB, array or arbitrary custom-type encoder.
- No automatic retry, adaptive lookup strategy, index/`ANALYZE` tuning or guaranteed result order.
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
- [Benchmarks](docs/benchmarks/baseline.md)
- [Contributing](CONTRIBUTING.md)

## License

Licensed under the [Apache License 2.0](LICENSE).
