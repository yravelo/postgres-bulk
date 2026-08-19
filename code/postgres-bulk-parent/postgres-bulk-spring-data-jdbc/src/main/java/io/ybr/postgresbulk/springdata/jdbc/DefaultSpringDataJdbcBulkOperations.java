package io.ybr.postgresbulk.springdata.jdbc;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal root-row bulk coordinator for the future Spring Data JDBC repository fragment. */
final class DefaultSpringDataJdbcBulkOperations<T> {

  private final JdbcOperations jdbcOperations;
  private final SpringDataJdbcEntityMetadataResolver metadataResolver;
  private final BulkOperationFactory operationFactory;
  private final LookupOperationFactory lookupOperationFactory;
  private final ResultMaterializerFactory resultMaterializerFactory;

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations, SpringDataJdbcEntityMetadataResolver metadataResolver) {
    this(
        jdbcOperations,
        metadataResolver,
        PgJdbcBulkOperation::new,
        PgJdbcLookupOperation::new,
        EntityRowMapperMaterializer::new);
  }

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations,
      SpringDataJdbcEntityMetadataResolver metadataResolver,
      BulkOperationFactory operationFactory) {
    this(
        jdbcOperations,
        metadataResolver,
        operationFactory,
        PgJdbcLookupOperation::new,
        EntityRowMapperMaterializer::new);
  }

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations,
      SpringDataJdbcEntityMetadataResolver metadataResolver,
      ResultMaterializerFactory resultMaterializerFactory) {
    this(
        jdbcOperations,
        metadataResolver,
        PgJdbcBulkOperation::new,
        PgJdbcLookupOperation::new,
        resultMaterializerFactory);
  }

  DefaultSpringDataJdbcBulkOperations(
      JdbcOperations jdbcOperations,
      SpringDataJdbcEntityMetadataResolver metadataResolver,
      BulkOperationFactory operationFactory,
      LookupOperationFactory lookupOperationFactory,
      ResultMaterializerFactory resultMaterializerFactory) {
    this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
    this.metadataResolver =
        Objects.requireNonNull(metadataResolver, "metadataResolver must not be null");
    this.operationFactory =
        Objects.requireNonNull(operationFactory, "operationFactory must not be null");
    this.lookupOperationFactory =
        Objects.requireNonNull(lookupOperationFactory, "lookupOperationFactory must not be null");
    this.resultMaterializerFactory =
        Objects.requireNonNull(
            resultMaterializerFactory, "resultMaterializerFactory must not be null");
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
    requireWritableTransaction("bulk insert");
    EntityMetadata<T> expectedMetadata = metadataResolver.resolveFor(first);
    PreparedBulkOperation<T> operation = operationFactory.prepare(expectedMetadata);
    Iterable<T> preparedItems =
        new HomogeneousIterable<>(source, first, expectedMetadata, metadataResolver);

    BulkWriteResult result =
        jdbcOperations.execute(
            (ConnectionCallback<BulkWriteResult>)
                connection -> {
                  requirePhysicalTransaction(connection, "bulk insert");
                  return operation.bulkInsert(connection, preparedItems, options);
                });
    return Objects.requireNonNull(result, "JdbcOperations returned a null bulk write result");
  }

  <K> List<T> findAllByBulkKey(
      Class<T> entityType, Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");

    Iterator<? extends K> source =
        Objects.requireNonNull(keys.iterator(), "keys.iterator() must not return null");
    if (!source.hasNext()) {
      return List.of();
    }

    K first = requireKey(source.next(), 1);
    requireWritableTransaction("bulk lookup");
    EntityMetadata<T> metadata = metadataResolver.resolve(entityType);
    RelationalPersistentEntity<T> persistentEntity = metadataResolver.persistentEntity(entityType);
    PreparedLookupOperation<T> operation = lookupOperationFactory.prepare(metadata);
    LookupResultMaterializer<T> materializer =
        resultMaterializerFactory.prepare(persistentEntity, metadataResolver.jdbcConverter());
    Iterable<K> preparedKeys = new PreparedKeyIterable<>(source, first);

    List<T> result =
        jdbcOperations.execute(
            (ConnectionCallback<List<T>>)
                connection -> {
                  requirePhysicalTransaction(connection, "bulk lookup");
                  return operation.findAllByBulkKey(
                      connection, preparedKeys, keyMetadata, materializer);
                });
    return Objects.requireNonNull(result, "JdbcOperations returned a null bulk lookup result");
  }

  private static void requireWritableTransaction(String operation) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC " + operation + " requires an active JDBC transaction");
    }
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC " + operation + " cannot run in a read-only transaction");
    }
  }

  private static void requirePhysicalTransaction(Connection connection, String operation)
      throws SQLException {
    if (connection.getAutoCommit()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC "
              + operation
              + " requires a transaction-bound Connection with autoCommit disabled");
    }
    if (connection.isReadOnly()) {
      throw new InvalidDataAccessApiUsageException(
          "Spring Data JDBC " + operation + " cannot use a read-only Connection");
    }
  }

  private static <T> T requireItem(T item, long position) {
    if (item == null) {
      throw new IllegalArgumentException(
          "items must not contain null elements; null found at position " + position);
    }
    return item;
  }

  private static <K> K requireKey(K key, long position) {
    if (key == null) {
      throw new IllegalArgumentException(
          "keys must not contain null elements; null found at position " + position);
    }
    return key;
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

  @FunctionalInterface
  interface LookupOperationFactory {
    <E> PreparedLookupOperation<E> prepare(EntityMetadata<E> metadata);
  }

  @FunctionalInterface
  interface PreparedLookupOperation<E> {
    <K> List<E> findAllByBulkKey(
        Connection connection,
        Iterable<? extends K> keys,
        BulkKeyMetadata<K> keyMetadata,
        LookupResultMaterializer<E> materializer);
  }

  @FunctionalInterface
  interface ResultMaterializerFactory {
    <E> LookupResultMaterializer<E> prepare(
        RelationalPersistentEntity<E> persistentEntity, JdbcConverter converter);
  }

  @FunctionalInterface
  interface LookupResultMaterializer<E> {
    List<E> materialize(Connection connection, String selectSql, long copiedKeys)
        throws SQLException;
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

  private static final class PgJdbcLookupOperation<E> implements PreparedLookupOperation<E> {

    private final PostgresBulkJdbcOperations<E> delegate;

    private PgJdbcLookupOperation(EntityMetadata<E> metadata) {
      delegate = PostgresBulkJdbcOperations.prepare(metadata);
    }

    @Override
    public <K> List<E> findAllByBulkKey(
        Connection connection,
        Iterable<? extends K> keys,
        BulkKeyMetadata<K> keyMetadata,
        LookupResultMaterializer<E> materializer) {
      return delegate.findAllByBulkKey(
          connection, keys, keyMetadata, List.of(), materializer::materialize);
    }
  }

  private static final class EntityRowMapperMaterializer<E> implements LookupResultMaterializer<E> {

    private final EntityRowMapper<E> rowMapper;

    private EntityRowMapperMaterializer(
        RelationalPersistentEntity<E> persistentEntity, JdbcConverter converter) {
      rowMapper = new EntityRowMapper<>(persistentEntity, converter);
    }

    @Override
    public List<E> materialize(Connection connection, String selectSql, long copiedKeys)
        throws SQLException {
      List<E> result = new ArrayList<>();
      try (PreparedStatement statement = connection.prepareStatement(selectSql);
          ResultSet rows = statement.executeQuery()) {
        int rowNumber = 0;
        while (rows.next()) {
          result.add(rowMapper.mapRow(rows, rowNumber++));
        }
      }
      return List.copyOf(result);
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

  private static final class PreparedKeyIterable<K> implements Iterable<K> {

    private final Iterator<? extends K> source;
    private final K first;
    private boolean supplied;

    private PreparedKeyIterable(Iterator<? extends K> source, K first) {
      this.source = source;
      this.first = first;
    }

    @Override
    public Iterator<K> iterator() {
      if (supplied) {
        throw new IllegalStateException("prepared keys may only be consumed once");
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
        public K next() {
          position = nextPosition(position);
          if (firstPending) {
            firstPending = false;
            return first;
          }
          return requireKey(source.next(), position);
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
