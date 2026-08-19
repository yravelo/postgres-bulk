package io.ybr.postgresbulk.springdata.jdbc;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Objects;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal root-row insert coordinator for the future Spring Data JDBC repository fragment. */
final class DefaultSpringDataJdbcBulkOperations<T> {

  private final JdbcOperations jdbcOperations;
  private final SpringDataJdbcEntityMetadataResolver metadataResolver;
  private final BulkOperationFactory operationFactory;

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations, SpringDataJdbcEntityMetadataResolver metadataResolver) {
    this(jdbcOperations, metadataResolver, PgJdbcBulkOperation::new);
  }

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations,
      SpringDataJdbcEntityMetadataResolver metadataResolver,
      BulkOperationFactory operationFactory) {
    this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
    this.metadataResolver =
        Objects.requireNonNull(metadataResolver, "metadataResolver must not be null");
    this.operationFactory =
        Objects.requireNonNull(operationFactory, "operationFactory must not be null");
  }

  BulkWriteResult bulkInsert(Iterable<? extends T> items) {
    return bulkInsert(items, BulkInsertOptions.defaults());
  }

  BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(options, "options must not be null");

    Iterator<? extends T> source =
        Objects.requireNonNull(items.iterator(), "items.iterator() must not return null");
    if (!source.hasNext()) {
      return BulkWriteResult.empty();
    }

    T first = requireItem(source.next(), 1);
    requireWritableTransaction();
    EntityMetadata<T> expectedMetadata = metadataResolver.resolveFor(first);
    PreparedBulkOperation<T> operation = operationFactory.prepare(expectedMetadata);
    Iterable<T> preparedItems =
        new HomogeneousIterable<>(source, first, expectedMetadata, metadataResolver);

    BulkWriteResult result =
        jdbcOperations.execute(
            (ConnectionCallback<BulkWriteResult>)
                connection -> {
                  requirePhysicalTransaction(connection);
                  return operation.bulkInsert(connection, preparedItems, options);
                });
    return Objects.requireNonNull(result, "JdbcOperations returned a null bulk write result");
  }

  private static void requireWritableTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC bulk insert requires an active JDBC transaction");
    }
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC bulk insert cannot run in a read-only transaction");
    }
  }

  private static void requirePhysicalTransaction(Connection connection) throws SQLException {
    if (connection.getAutoCommit()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC bulk insert requires a transaction-bound Connection with autoCommit disabled");
    }
    if (connection.isReadOnly()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC bulk insert cannot use a read-only Connection");
    }
  }

  private static <T> T requireItem(T item, long position) {
    if (item == null) {
      throw new IllegalArgumentException(
          "items must not contain null elements; null found at position " + position);
    }
    return item;
  }

  @FunctionalInterface
  interface BulkOperationFactory {
    <E> PreparedBulkOperation<E> prepare(EntityMetadata<E> metadata);
  }

  @FunctionalInterface
  interface PreparedBulkOperation<E> {
    BulkWriteResult bulkInsert(
        Connection connection, Iterable<? extends E> items, BulkInsertOptions options);
  }

  private static final class PgJdbcBulkOperation<E> implements PreparedBulkOperation<E> {

    private final PostgresBulkJdbcOperations<E> delegate;

    private PgJdbcBulkOperation(EntityMetadata<E> metadata) {
      delegate = PostgresBulkJdbcOperations.prepare(metadata);
    }

    @Override
    public BulkWriteResult bulkInsert(
        Connection connection, Iterable<? extends E> items, BulkInsertOptions options) {
      return delegate.bulkInsert(connection, items, options);
    }
  }

  private static final class HomogeneousIterable<E> implements Iterable<E> {

    private final Iterator<? extends E> source;
    private final E first;
    private final EntityMetadata<E> expectedMetadata;
    private final SpringDataJdbcEntityMetadataResolver metadataResolver;
    private boolean supplied;

    private HomogeneousIterable(
        Iterator<? extends E> source,
        E first,
        EntityMetadata<E> expectedMetadata,
        SpringDataJdbcEntityMetadataResolver metadataResolver) {
      this.source = source;
      this.first = first;
      this.expectedMetadata = expectedMetadata;
      this.metadataResolver = metadataResolver;
    }

    @Override
    public Iterator<E> iterator() {
      if (supplied) {
        throw new IllegalStateException("prepared items may only be consumed once");
      }
      supplied = true;
      return new Iterator<>() {
        private boolean firstPending = true;
        private long position;

        @Override
        public boolean hasNext() {
          return firstPending || source.hasNext();
        }

        @Override
        public E next() {
          position = nextPosition(position);
          if (firstPending) {
            firstPending = false;
            return first;
          }
          E item = requireItem(source.next(), position);
          EntityMetadata<E> actualMetadata = metadataResolver.resolveFor(item);
          if (actualMetadata != expectedMetadata) {
            throw mixedMetadata(item, position);
          }
          return item;
        }
      };
    }
  }

  private static long nextPosition(long position) {
    try {
      return Math.incrementExact(position);
    } catch (ArithmeticException failure) {
      throw new IllegalArgumentException("items position overflow", failure);
    }
  }

  private static InvalidDataAccessApiUsageException mixedMetadata(Object item, long position) {
    return new InvalidDataAccessApiUsageException(
        "Spring Data JDBC bulk insert requires homogeneous metadata; item at position "
            + position
            + " of type "
            + item.getClass().getName()
            + " resolves to different insert columns (assigned/generated identifier policies cannot be mixed)");
  }
}
