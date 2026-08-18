package io.github.postgresbulk.springdata.repository;

import io.github.postgresbulk.core.BulkInsertOptions;
import io.github.postgresbulk.core.BulkWriteResult;
import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.Session;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.repository.core.RepositoryMethodContext;
import org.springframework.data.repository.core.support.RepositoryMetadataAccess;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Spring Data infrastructure implementation loaded through external fragment registration. */
public class DefaultPostgresBulkOperations<T, ID>
    implements PostgresBulkRepository<T, ID>, RepositoryMetadataAccess {

  private final JpaContext jpaContext;
  private final JpaEntityMetadataResolver metadataResolver;
  private final Map<EntityMetadata<?>, PostgresBulkJdbcOperations<?>> operations =
      Collections.synchronizedMap(new IdentityHashMap<>());

  public DefaultPostgresBulkOperations(
      JpaContext jpaContext, JpaEntityMetadataResolver metadataResolver) {
    this.jpaContext = Objects.requireNonNull(jpaContext, "jpaContext must not be null");
    this.metadataResolver =
        Objects.requireNonNull(metadataResolver, "metadataResolver must not be null");
  }

  @Override
  @Transactional
  public BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(options, "options must not be null");
    PreparedIterable<T> preparedItems = PreparedIterable.from(items, "items");
    if (preparedItems.isEmpty()) {
      return BulkWriteResult.empty();
    }
    Invocation<T> invocation = invocation();
    requireWritableTransaction(invocation.entityManager());
    return withConnection(
        invocation.entityManager(),
        connection -> invocation.operations().bulkInsert(connection, preparedItems, options));
  }

  @Override
  @Transactional
  public <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    PreparedIterable<K> preparedKeys = PreparedIterable.from(keys, "keys");
    if (preparedKeys.isEmpty()) {
      return List.of();
    }
    Invocation<T> invocation = invocation();
    requireWritableTransaction(invocation.entityManager());
    return withConnection(
        invocation.entityManager(),
        connection ->
            invocation
                .operations()
                .findAllByBulkKey(
                    connection,
                    preparedKeys,
                    keyMetadata,
                    List.of(),
                    (sameConnection, selectSql, copiedKeys) ->
                        materialize(
                            invocation.entityManager(), invocation.domainType(), selectSql)));
  }

  private Invocation<T> invocation() {
    Class<T> domainType = domainType();
    EntityManager entityManager = jpaContext.getEntityManagerByManagedType(domainType);
    EntityMetadata<T> metadata =
        metadataResolver.resolve(entityManager.getEntityManagerFactory(), domainType);
    return new Invocation<>(domainType, entityManager, operations(metadata));
  }

  @SuppressWarnings("unchecked")
  private PostgresBulkJdbcOperations<T> operations(EntityMetadata<T> metadata) {
    synchronized (operations) {
      return (PostgresBulkJdbcOperations<T>)
          operations.computeIfAbsent(
              metadata, ignored -> PostgresBulkJdbcOperations.prepare(metadata));
    }
  }

  @SuppressWarnings("unchecked")
  private Class<T> domainType() {
    return (Class<T>) RepositoryMethodContext.getContext().getMetadata().getDomainType();
  }

  private static void requireWritableTransaction(EntityManager entityManager) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !entityManager.isJoinedToTransaction()) {
      throw new InvalidDataAccessApiUsageException(
          "PostgreSQL bulk repository operations require an active JPA transaction");
    }
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new InvalidDataAccessApiUsageException(
          "PostgreSQL bulk repository operations cannot run in a read-only transaction");
    }
  }

  private static <R> R withConnection(EntityManager entityManager, SqlConnectionWork<R> work) {
    Session session = entityManager.unwrap(Session.class);
    return session.doReturningWork(connection -> work.execute(connection));
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> materialize(
      EntityManager entityManager, Class<T> domainType, String selectSql) {
    Query query = entityManager.createNativeQuery(selectSql, domainType);
    query.setFlushMode(FlushModeType.COMMIT);
    return (List<T>) query.getResultList();
  }

  private record Invocation<T>(
      Class<T> domainType, EntityManager entityManager, PostgresBulkJdbcOperations<T> operations) {}

  private static final class PreparedIterable<E> implements Iterable<E> {

    private final Iterator<? extends E> source;
    private final E first;
    private final boolean empty;
    private boolean supplied;

    private PreparedIterable(Iterator<? extends E> source, E first, boolean empty) {
      this.source = source;
      this.first = first;
      this.empty = empty;
    }

    private static <E> PreparedIterable<E> from(
        Iterable<? extends E> values, String parameterName) {
      Iterator<? extends E> iterator =
          Objects.requireNonNull(
              values.iterator(), parameterName + ".iterator() must not return null");
      return iterator.hasNext()
          ? new PreparedIterable<>(iterator, iterator.next(), false)
          : new PreparedIterable<>(iterator, null, true);
    }

    private boolean isEmpty() {
      return empty;
    }

    @Override
    public Iterator<E> iterator() {
      if (supplied) {
        throw new IllegalStateException("prepared iterable may only be consumed once");
      }
      supplied = true;
      return new Iterator<>() {
        private boolean firstPending = !empty;

        @Override
        public boolean hasNext() {
          return firstPending || source.hasNext();
        }

        @Override
        public E next() {
          if (firstPending) {
            firstPending = false;
            return first;
          }
          return source.next();
        }
      };
    }
  }

  @FunctionalInterface
  private interface SqlConnectionWork<R> {
    R execute(Connection connection) throws SQLException;
  }
}
