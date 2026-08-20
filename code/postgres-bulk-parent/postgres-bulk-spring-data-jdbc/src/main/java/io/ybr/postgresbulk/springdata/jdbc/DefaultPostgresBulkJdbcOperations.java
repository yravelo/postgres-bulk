package io.ybr.postgresbulk.springdata.jdbc;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.RepositoryMethodContext;
import org.springframework.data.repository.core.support.RepositoryMetadataAccess;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data infrastructure loaded through external repository-fragment registration. */
final class DefaultPostgresBulkJdbcOperations<T>
    implements PostgresBulkJdbcRepository<T>, RepositoryMetadataAccess {

  private static final String JPA_FRAGMENT =
      "io.ybr.postgresbulk.springdata.repository.PostgresBulkRepository";

  private final DefaultSpringDataJdbcBulkOperations<T> delegate;

  /**
   * Creates the external fragment implementation.
   *
   * @param jdbcOperations transaction-aware JDBC operations
   * @param metadataResolver resolver backed by the application's effective JDBC converter
   */
  public DefaultPostgresBulkJdbcOperations(
      JdbcOperations jdbcOperations, SpringDataJdbcEntityMetadataResolver metadataResolver) {
    delegate = new DefaultSpringDataJdbcBulkOperations<>(jdbcOperations, metadataResolver);
  }

  @Override
  @Transactional
  public BulkWriteResult bulkInsert(Iterable<? extends T> items, BulkInsertOptions options) {
    rejectMixedStoreRepository(repositoryMetadata());
    return delegate.bulkInsert(items, options);
  }

  @Override
  @Transactional
  public <K> List<T> findAllByBulkKey(Iterable<? extends K> keys, BulkKeyMetadata<K> keyMetadata) {
    RepositoryMetadata metadata = repositoryMetadata();
    rejectMixedStoreRepository(metadata);
    return delegate.findAllByBulkKey(domainType(metadata), keys, keyMetadata);
  }

  private static RepositoryMetadata repositoryMetadata() {
    return RepositoryMethodContext.getContext().getMetadata();
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> domainType(RepositoryMetadata metadata) {
    return (Class<T>) metadata.getDomainType();
  }

  static void rejectMixedStoreRepository(RepositoryMetadata metadata) {
    Class<?> repositoryInterface =
        Objects.requireNonNull(metadata.getRepositoryInterface(), "repository interface missing");
    if (inherits(repositoryInterface, JPA_FRAGMENT)) {
      throw new InvalidDataAccessApiUsageException(
          "A repository must not combine the Spring Data JDBC and JPA PostgreSQL bulk fragments: "
              + repositoryInterface.getName());
    }
  }

  private static boolean inherits(Class<?> type, String interfaceName) {
    for (Class<?> candidate : type.getInterfaces()) {
      if (candidate.getName().equals(interfaceName) || inherits(candidate, interfaceName)) {
        return true;
      }
    }
    return false;
  }
}
