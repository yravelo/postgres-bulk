# Bulk lookup

## When to use it

Bulk lookup accepts many typed keys, loads them into a connection-local temporary table with COPY,
and joins that table to the mapped entity table. It avoids constructing a large parameterized SQL
string, but it has a fixed CREATE/COPY/JOIN/DROP cost and is not always faster than `IN`.

## Simple key

The metadata uses exact physical column names after the configured naming strategy:

```java
private static final BulkKeyMetadata<String> SKU_KEY = BulkKeyMetadata.of(
    String.class,
    List.of(ColumnMetadata.of("sku", String.class, sku -> sku))
);

@Transactional
public List<Product> findBySkus(List<String> skus) {
    return repository.findAllByBulkKey(skus, SKU_KEY);
}
```

## Composite key

Use a value object and keep components in target-column order:

```java
record ProductLookupKey(String sku, String name) {}

private static final BulkKeyMetadata<ProductLookupKey> SKU_AND_NAME_KEY = BulkKeyMetadata.of(
    ProductLookupKey.class,
    List.of(
        ColumnMetadata.of("sku", String.class, ProductLookupKey::sku),
        ColumnMetadata.of("name", String.class, ProductLookupKey::name)
    )
);
```

`BulkKeyMetadata` describes a lookup tuple; it does not assert that the database has a UNIQUE
constraint.

## Exact result semantics

- Duplicate input keys are retained while streaming but deduplicated relationally before the JOIN;
  they do not multiply results.
- If the target table contains multiple rows with the same key tuple, all those rows may appear.
- Missing keys simply produce no row.
- A null key object or null component produces `IllegalArgumentException`. Error messages report
  position/column but never the key value.
- Result order is unspecified. There is no `ORDER BY` or input ordinal in the temporary relation.
- Empty input returns an empty list without metadata, connection or database work.

The method must run in a write-capable transaction. `@Transactional(readOnly = true)` is invalid
because PostgreSQL must create and load a temporary table. The JPA fragment materializes through
Hibernate; the JDBC fragment uses Spring Data's `EntityRowMapper` and effective `JdbcConverter`.
Both finish materialization before cleanup. Pending JPA writes are not flushed automatically.

## Performance guidance

The current local baseline found `IN` faster at 10 and 100 keys, temporary COPY/JOIN faster at
1,000, and `IN` faster again at 10,000. There is no stable crossover and no automatic strategy
selection. Key count, indexes, data distribution, schema, network and PostgreSQL query plan all
matter. Benchmark both approaches for a critical workload; do not adopt a threshold such as “use
bulk lookup above 500 keys” from this evidence.

See [performance](performance.md) and the [benchmark baseline](../benchmarks/baseline.md).
