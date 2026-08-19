# Error handling and retry

## Exceptions

- `NullPointerException` represents a null API argument required to be non-null.
- `IllegalArgumentException` represents invalid options, null input elements or null lookup key
  components. Spring's repository exception translation may expose some misuse as
  `InvalidDataAccessApiUsageException` while retaining the original cause.
- `BulkException` is the unchecked library boundary for mapping, encoding, COPY, lookup and
  infrastructure failures.
- JDBC failures remain reachable in the cause chain, including `SQLException`, SQLState and driver
  details. Cleanup failures from COPY cancellation or temporary-table DROP are suppressed on the
  primary failure rather than replacing it.

Error messages do not include entity contents, CSV values or lookup key values.

## After a database failure

A PostgreSQL statement error can leave the current transaction aborted. Further SQL may fail with
`25P02` until the transaction owner rolls back. Do not catch a bulk failure and continue issuing
SQL in the same transaction as if it were healthy. With Spring, allow the exception to escape or
mark/complete the transaction for rollback.

Loss of the physical backend can make the connection invalid. The pool/framework owns validation,
discard and replacement; the library never closes a borrowed connection to recover it.

## Retry policy

**Do not blindly retry `bulkInsert` after a connection or server failure.** The completion state may
be unknown, low-level autocommit may have persisted earlier COPY batches, and the input may be a
one-shot iterable that cannot be replayed correctly. The library performs no automatic retry or
compensation.

If the application chooses to retry, it must provide:

1. an idempotency design or duplicate-safe business key;
2. a new transaction/connection scope after rollback;
3. reproducible input rather than a partially consumed iterable;
4. classification of the original cause/SQLState as retryable for that application.

A failed call returns no partial `BulkWriteResult`. That absence does not prove that no row was
committed when autocommit or uncertain connection completion was involved.

The exhaustive stage/cleanup matrix is in
[transactions and failures](../architecture/transactions-and-failures.md).
