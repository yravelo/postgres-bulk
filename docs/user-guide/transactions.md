# Transactions

## Spring repository methods

All `PostgresBulkRepository` methods use Spring `REQUIRED`, read-write semantics:

```java
@Transactional
public BulkWriteResult importProducts(List<Product> products) {
    return repository.bulkInsert(products, BulkInsertOptions.ofBatchSize(1_000));
}
```

Without an outer transaction, the repository proxy creates one. With an outer transaction, COPY
participates in that physical transaction. A runtime failure that escapes the boundary triggers
normal Spring rollback.

```java
@Transactional
public void importAndReject(List<Product> products) {
    repository.bulkInsert(products);
    throw new IllegalStateException("the entire transaction rolls back");
}
```

A batch is one COPY execution, not one transaction. Rollback of the surrounding transaction
reverts all completed batches in that scope.

## Lookup requires write capability

Bulk lookup creates a temporary table, loads keys with COPY, runs a JOIN and removes the table.
Consequently this is correct:

```java
@Transactional
public List<Product> findProducts(List<String> skus) {
    return repository.findAllByBulkKey(skus, SKU_KEY);
}
```

This is rejected before JDBC work:

```java
@Transactional(readOnly = true)
public List<Product> findProducts(List<String> skus) {
    return repository.findAllByBulkKey(skus, SKU_KEY);
}
```

Insert is also rejected inside an outer read-only transaction. The library never disables the
read-only flag for the caller.

## Propagation

- `REQUIRED` is supported and is the repository default.
- `REQUIRES_NEW` is supported through Spring: it suspends the outer transaction and uses an
  independent physical transaction.
- `NESTED` is **unsupported** in the validated Hibernate 6.6 + `JpaTransactionManager` baseline.
  Enabling `nestedTransactionAllowed` does not add savepoint support to `HibernateJpaDialect`.

Size the connection pool carefully when using `REQUIRES_NEW`; the suspended outer scope may retain
one connection while the inner scope acquires another.

## Persistence context

COPY writes directly to PostgreSQL. It does not flush or clear the current persistence context and
does not make input objects managed. Pending JPA changes are not automatically flushed before bulk
lookup because its native query uses flush mode `COMMIT`.

Call `flush()` before lookup when pending ORM writes must participate. Call `clear()` or
`refresh()` only when your application needs to discard or update stale managed state; the library
does not make that decision globally.

## Low-level JDBC usage

`PostgresBulkJdbcOperations` receives a caller-owned connection and never commits, rolls back,
closes or reconfigures it. Lookup requires `autoCommit=false`. Insert can run with autocommit, but
each completed COPY batch may already be persisted if a later batch fails. Use an explicit
transaction when atomicity matters.

After a SQL error PostgreSQL may remain in state `25P02` until rollback. The owner must roll back
before reusing the connection. The complete maintainer matrix is in
[transactions and failures](../architecture/transactions-and-failures.md).
