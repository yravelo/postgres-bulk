# Dynamic schemas and schema-per-tenant

## What this capability solves

Use the multi-schema API when the same logical root table exists in several PostgreSQL schemas and
the application already knows which physical schema a single bulk operation must use. A typical
case is schema-per-customer with compatible `product` tables in `customer_a`, `customer_b`, and so
on.

> If your application does not use runtime schemas, you do not need to configure or use this capability.

The existing target-free methods remain the default and use the table from JPA or Spring Data JDBC
mapping metadata. There is no multi-schema property to enable.

> postgres-bulk does not resolve tenants. Your application determines the physical destination and passes a TableName explicitly.

## Physical boundary

```text
DataSource / Connection selects database
TableName selects schema + table inside that database
```

`TableName` cannot route to another database, select credentials, provision a schema, or change a
connection's `search_path`. Database-per-tenant applications must perform routing before invoking
postgres-bulk and then pass a target that exists inside the selected database.

This capability is not needed for row-level tenancy using `tenant_id`, PostgreSQL RLS, filters, or
Hibernate `@TenantId`. Those models do not select a physical schema per operation.

## Build a runtime target

Use separate structured schema and table components. Do not pass a pre-concatenated
`"schema.table"` value:

```java
TableName target = TableName.of(schema, "product");
```

The runtime target must be schema-qualified and keep the table name from the mapping. Components
preserve case and spaces; the SQL engine quotes each component independently.

The application should map an already authenticated and authorized identity through a closed
catalog or allow-list:

```java
private static final Map<String, String> CUSTOMER_SCHEMAS = Map.of(
    "customer-a", "customer_a",
    "customer-b", "customer_b"
);

private static TableName targetForCustomer(String customer) {
    String schema = CUSTOMER_SCHEMAS.get(customer);
    if (schema == null) {
        throw new IllegalArgumentException("Unknown or unauthorized customer");
    }
    return TableName.of(schema, "product");
}
```

This resolver is application code, not a postgres-bulk SPI. Do not map an arbitrary header, JWT
claim, URL segment, or other untrusted input directly to a schema.

## Default usage

Calls without a target continue to use the mapped table:

```java
BulkWriteResult result = products.bulkInsert(input);
List<Product> found = products.findAllByBulkKey(skus, SKU_KEY);
```

Default and runtime calls may alternate on the same singleton repository.

## Spring Data JPA

For a repository extending `PostgresBulkRepository<Product, UUID>`:

```java
TableName target = TableName.of("customer_a", "product");

BulkWriteResult defaults = products.bulkInsert(input);
BulkWriteResult targeted = products.bulkInsert(target, customerInput);
BulkWriteResult batched = products.bulkInsert(
    customerInput,
    BulkInsertOptions.ofBatchSize(5_000),
    target
);
List<Product> found = products.findAllByBulkKey(skus, SKU_KEY, target);
```

Only the root table is redirected. Associations, secondary tables, callbacks, cascades, generated
identifier synchronization and persistence-context reconciliation keep their documented JPA
limitations.

## Spring Data JDBC

The JDBC fragment has the same target-first short insert and target-last complete operations:

```java
TableName target = TableName.of("customer_a", "jdbc_product");

BulkWriteResult defaults = products.bulkInsert(input);
BulkWriteResult targeted = products.bulkInsert(target, customerInput);
BulkWriteResult batched = products.bulkInsert(
    customerInput,
    BulkInsertOptions.ofBatchSize(5_000),
    target
);
List<Product> found = products.findAllByBulkKey(skus, SKU_KEY, target);
```

The target changes only the aggregate-root table. Children, collections, callbacks, auditing,
events and generated-ID synchronization remain outside the root-only bulk contract.

## Static schema mappings

Runtime selection is deliberately conservative:

| Mapping | Runtime target | Result |
|---|---|---|
| `product` without schema | `customer_a.product` | allowed |
| `public.product` | `public.product` | allowed |
| `public.product` | `customer_a.product` | rejected before JDBC |
| any mapping | target with a different table | rejected before JDBC |
| any mapping | runtime target without schema | rejected before JDBC |

An application that needs several runtime schemas should normally omit a fixed schema from its
entity/root mapping. The target-free path can still rely on the mapping or connection environment.

## Quoted schemas

Pass the exact component text:

```java
TableName quoted = TableName.of("Customer A", "product");
products.bulkInsert(quoted, input);
```

postgres-bulk emits a qualified identifier equivalent to `"Customer A"."product"`. Quoting
protects SQL syntax; it does not grant permission to use the object.

## Transactions

Multi-schema does not change the transaction model:

| Store | REQUIRED | REQUIRES_NEW | NESTED | Read-only |
|---|---|---|---|---|
| JPA | supported/default | supported by Spring | unsupported with the validated Hibernate JPA dialect | invalid |
| JDBC | supported/default | supported by Spring | conditional on a JDBC manager/savepoints for the same `DataSource` | invalid |

One transaction may touch schemas A and B in the same database because every statement is
qualified. Commit or rollback applies according to the owning manager. Two local JPA/JDBC managers
do not imply distributed atomicity, even when they share a `DataSource`.

Bulk lookup creates and loads a temporary table, so it also requires a write-capable transaction.
After a PostgreSQL error, let the transaction owner roll back before reusing the connection.

## Concurrency and pools

`TableName` is immutable and operation-scoped. Runtime targets are local variables, so singleton
repositories can safely serve A and B concurrently when the surrounding application provides the
normal thread-bound transaction/connection.

postgres-bulk does not call `Connection.setSchema`, issue `SET search_path`, keep an ambient
tenant context, or cache SQL by target. A pooled connection can therefore execute A then B without
schema state to restore.

## Provisioning and migrations

postgres-bulk does not create schemas or tables and does not coordinate tenant lifecycle. Create
and evolve every physical schema with application/platform tooling such as Flyway or Liquibase.
The runtime table must have a shape compatible with the mapping and COPY encoders; drift is exposed
as a normal PostgreSQL failure.

## Security responsibility

`TableName` is structured identifier input, not an authorization token. The application must:

1. authenticate the caller;
2. authorize the requested destination;
3. map it to a known `TableName` rather than concatenate arbitrary input;
4. use a database role with the required schema/table privileges.

PostgreSQL remains authoritative for `USAGE`, `INSERT`, and `SELECT`. Server exceptions may include
physical identifiers because the JDBC cause and SQLState are preserved.

## Observability and privacy

Schema, target, and tenant are not metric tags or library log dimensions. Existing observations
remain bounded to operation/outcome/error semantics, avoiding target-cardinality growth and
identifier disclosure.

## Performance

Runtime targeting builds qualified SQL once per non-empty operation and does not use a target-keyed
cache. MS8 measured default versus the equivalent explicit target in pgJDBC, JPA and JDBC. Most
pairs changed sign between runs or had broad intervals; the repeated small-call JPA INSERT cost was
about 0.3–0.6 ms and was amortized by 10K/100K. Resolving 10.000 prebuilt targets took about 18 µs
total versus about 12 µs when repeating one target, with allocation at profiler noise level. These
local results do not justify a cache or a universal production estimate. See the
[MS8 report](../benchmarks/ms8-multi-schema.md).

## Limitations

- PostgreSQL only, with a pgJDBC-unwrappable connection.
- The target selects one schema/table inside the already selected database.
- Row-level tenancy, `tenant_id`, RLS and Hibernate tenant resolution are outside scope.
- Schema provisioning, migrations and tenant lifecycle are external.
- No global schema property, tenant resolver SPI, ambient context or automatic datasource routing.
- Runtime table shape is trusted rather than inferred from database catalogs.
- JPA and JDBC keep their existing root-only/lifecycle and transaction limitations.

See the executable [JPA example](../../examples/spring-boot-basic/README.md), executable
[JDBC example](../../examples/spring-boot-data-jdbc/README.md), and the maintainer-facing
[Spring Boot composition architecture](../architecture/multi-schema-spring-boot-composition.md).
