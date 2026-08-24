package io.ybr.postgresbulk.springdata.repository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import io.ybr.postgresbulk.pgjdbc.copy.PostgresBulkJdbcOperations;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.repository.core.RepositoryMethodContext;
import org.springframework.data.repository.core.support.RepositoryMetadataAccess;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring Data infrastructure implementation loaded through external fragment registration.
 *
 * <p>Applications should declare {@link PostgresBulkRepository} on their repository interface and
 * let Spring Data construct this implementation.
 *
 * @param <T> repository domain type
 * @param <ID> repository identifier type
 */
public class DefaultPostgresBulkOperations<T, ID>
    implements PostgresBulkRepository<T, ID>, RepositoryMetadataAccess {

  private final JpaContext jpaContext;
  private final JpaEntityMetadataResolver metadataResolver;
  private final Map<EntityMetadata<?>, PostgresBulkJdbcOperations<?>> operations =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private volatile PostgresBulkObservability observability = PostgresBulkObservability.disabled();

  /**
   * Creates the external repository fragment implementation.
   *
   * <p>This constructor is public for Spring Data infrastructure and is not an application
   * extension point.
   *
   * @param jpaContext selects the persistence unit for the current repository domain type
   * @param metadataResolver resolves persistence-facing metadata for that unit
   */
  public DefaultPostgresBulkOperations(
      JpaContext jpaContext, JpaEntityMetadataResolver metadataResolver) {
    this.jpaContext = Objects.requireNonNull(jpaContext, "jpaContext must not be null");
    this.metadataResolver =
        Objects.requireNonNull(metadataResolver, "metadataResolver must not be null");
  }

  @Autowired
  void configureObservability(
      ObjectProvider<ObservationRegistry> observationRegistries,
      ObjectProvider<MeterRegistry> meterRegistries,
      Environment environment) {
    if (!environment.getProperty("postgres-bulk.observability.enabled", Boolean.class, true)) {
      observability = PostgresBulkObservability.disabled();
      return;
    }
    ObservationRegistry observationRegistry = observationRegistries.getIfAvailable();
    observability =
        observationRegistry == null
            ? PostgresBulkObservability.disabled()
            : new PostgresBulkObservability(observationRegistry, meterRegistries.getIfAvailable());
  }

  @Override
  @Transactional
  public BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
    return observability.observeInsert(() -> doBulkInsert(items, options));
  }

  private BulkWriteResult doBulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
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
  public BulkWriteResult bulkInsert(
      Iterable<? extends T> items, BulkInsertOptions options, TableName runtimeTarget) {
    return observability.observeInsert(() -> doBulkInsert(items, options, runtimeTarget));
  }

  private BulkWriteResult doBulkInsert(
      Iterable<? extends T> items, BulkInsertOptions options, TableName runtimeTarget) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(options, "options must not be null");
    Objects.requireNonNull(runtimeTarget, "runtimeTarget must not be null");
    PreparedIterable<T> preparedItems = PreparedIterable.from(items, "items");
    Invocation<T> invocation = invocation();
    if (preparedItems.isEmpty()) {
      invocation.metadata().table().resolveRuntimeTarget(runtimeTarget);
      return BulkWriteResult.empty();
    }
    requireWritableTransaction(invocation.entityManager());
    return withConnection(
        invocation.entityManager(),
        connection ->
            invocation.operations().bulkInsert(connection, preparedItems, options, runtimeTarget));
  }

  @Override
  @Transactional
  public <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
    return observability.observeLookup(() -> doFindAllByBulkKey(keys, keyMetadata));
  }

  private <K> List<T> doFindAllByBulkKey(
      Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
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

  @Override
  @Transactional
  public <K> List<T> findAllByBulkKey(
      Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget) {
    return observability.observeLookup(() -> doFindAllByBulkKey(keys, keyMetadata, runtimeTarget));
  }

  private <K> List<T> doFindAllByBulkKey(
      Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata, TableName runtimeTarget) {
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(keyMetadata, "keyMetadata must not be null");
    Objects.requireNonNull(runtimeTarget, "runtimeTarget must not be null");
    PreparedIterable<K> preparedKeys = PreparedIterable.from(keys, "keys");
    Invocation<T> invocation = invocation();
    if (preparedKeys.isEmpty()) {
      invocation.metadata().table().resolveRuntimeTarget(runtimeTarget);
      return List.of();
    }
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
                        materialize(invocation.entityManager(), invocation.domainType(), selectSql),
                    runtimeTarget));
  }

  private Invocation<T> invocation() {
    Class<T> domainType = domainType();
    EntityManager entityManager = jpaContext.getEntityManagerByManagedType(domainType);
    EntityMetadata<T> metadata =
        metadataResolver.resolve(entityManager.getEntityManagerFactory(), domainType);
    return new Invocation<>(domainType, entityManager, metadata, operations(metadata));
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
      Class<T> domainType,
      EntityManager entityManager,
      EntityMetadata<T> metadata,
      PostgresBulkJdbcOperations<T> operations) {}

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
