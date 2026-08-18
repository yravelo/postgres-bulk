package io.github.postgresbulk.hibernate;

import io.github.postgresbulk.core.BulkException;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadataResolver;
import io.github.postgresbulk.core.metadata.TableName;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedNameParser;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.generator.Generator;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.metamodel.mapping.ValuedModelPart;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Resolves Hibernate 6.6 runtime mappings into immutable, framework-neutral bulk metadata.
 *
 * <p>Instances are bound to one {@link EntityManagerFactory}. Resolution is cached by mapped Java
 * class in that instance, so distinct persistence units never share metadata. Returned accessors
 * are immutable and safe for concurrent reads; they do not require an open entity manager or
 * Hibernate session.
 *
 * <p>The supported mapping subset is deliberately single-table and insert-oriented. Generated
 * identifiers, generated-on-insert attributes, formulas, collections, and non-insertable
 * selectables are omitted. Inheritance, secondary tables, non-primary-key to-one joins, and other
 * mappings that cannot be represented by one ordered row fail during resolution.
 */
public final class HibernateEntityMetadataResolver implements EntityMetadataResolver {

  private final SessionFactoryImplementor sessionFactory;
  private final WrapperOptions wrapperOptions;
  private final ConcurrentMap<Class<?>, EntityMetadata<?>> cache = new ConcurrentHashMap<>();

  /**
   * Creates a resolver for one persistence unit.
   *
   * @param entityManagerFactory open Hibernate-backed entity manager factory
   * @throws NullPointerException if {@code entityManagerFactory} is {@code null}
   * @throws BulkException if the factory cannot expose Hibernate's runtime session factory SPI
   */
  public HibernateEntityMetadataResolver(EntityManagerFactory entityManagerFactory) {
    Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
    try {
      this.sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
      this.wrapperOptions = sessionFactory.getWrapperOptions();
    } catch (RuntimeException exception) {
      throw new BulkException(
          "EntityManagerFactory is not backed by a supported Hibernate 6.6 SessionFactory",
          exception);
    }
  }

  /**
   * Resolves and caches bulk-insert metadata for a mapped entity class.
   *
   * @param entityType mapped entity class
   * @param <T> entity type
   * @return immutable metadata for the entity's single physical insert table
   * @throws NullPointerException if {@code entityType} is {@code null}
   * @throws BulkException if the class is not an entity or its mapping is unsupported
   */
  @Override
  public <T> EntityMetadata<T> resolve(Class<T> entityType) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    try {
      return cast(cache.computeIfAbsent(entityType, this::resolveUncached));
    } catch (BulkException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BulkException(
          "Could not resolve Hibernate metadata for " + entityType.getName(), exception);
    }
  }

  private EntityMetadata<?> resolveUncached(Class<?> entityType) {
    EntityPersister persister =
        sessionFactory.getMappingMetamodel().findEntityDescriptor(entityType);
    if (persister == null) {
      throw new BulkException("Not a mapped Hibernate entity: " + entityType.getName());
    }
    validateSingleTableMapping(persister, entityType);

    String tableExpression = persister.getMappedTableDetails().getTableName();
    TableName table = parseTableName(tableExpression, entityType);
    List<ColumnMetadata<Object>> columns = new ArrayList<>();

    EntityIdentifierMapping identifierMapping = persister.getIdentifierMapping();
    Generator identifierGenerator = persister.getGenerator();
    if (identifierMapping.getNature() != EntityIdentifierMapping.Nature.SIMPLE
        || identifierGenerator == null
        || !identifierGenerator.generatesOnInsert()
        || identifierGenerator.allowAssignedIdentifiers()) {
      addPart(
          identifierMapping,
          source -> identifierMapping.getIdentifier(source),
          tableExpression,
          entityType,
          columns,
          false);
    }

    persister.forEachAttributeMapping(
        attribute ->
            addAttribute(
                attribute, Function.identity(), tableExpression, entityType, columns, true));
    if (columns.isEmpty()) {
      throw unsupported(entityType, "mapping has no insertable physical columns");
    }
    return entityMetadata(entityType, table, columns);
  }

  private void addAttribute(
      AttributeMapping attribute,
      Function<Object, Object> parentAccessor,
      String tableExpression,
      Class<?> entityType,
      List<ColumnMetadata<Object>> columns,
      boolean inspectGenerator) {
    if (attribute.isPluralAttributeMapping() || !attribute.getAttributeMetadata().isInsertable()) {
      return;
    }
    Generator generator = inspectGenerator ? attribute.getGenerator() : null;
    if (generator != null && generator.generatesOnInsert()) {
      return;
    }
    Function<Object, Object> accessor =
        source -> {
          Object parent = parentAccessor.apply(source);
          return parent == null ? null : attribute.getValue(parent);
        };

    if (attribute instanceof ToOneAttributeMapping toOne) {
      addToOne(toOne, accessor, tableExpression, entityType, columns);
      return;
    }
    addPart(attribute, accessor, tableExpression, entityType, columns, false);
  }

  private void addPart(
      ValuedModelPart part,
      Function<Object, Object> accessor,
      String tableExpression,
      Class<?> entityType,
      List<ColumnMetadata<Object>> columns,
      boolean inspectGenerator) {
    if (part instanceof BasicValuedMapping basic && part instanceof SelectableMapping selectable) {
      addBasic(selectable, basic.getJdbcMapping(), accessor, tableExpression, entityType, columns);
      return;
    }
    if (part instanceof EmbeddableValuedModelPart embedded) {
      embedded
          .getEmbeddableTypeDescriptor()
          .forEachAttributeMapping(
              attribute ->
                  addAttribute(
                      attribute, accessor, tableExpression, entityType, columns, inspectGenerator));
      return;
    }
    throw unsupported(entityType, "unsupported value mapping '" + part.getPartName() + "'");
  }

  private void addToOne(
      ToOneAttributeMapping toOne,
      Function<Object, Object> associationAccessor,
      String tableExpression,
      Class<?> entityType,
      List<ColumnMetadata<Object>> columns) {
    if (!toOne.isReferenceToPrimaryKey() || toOne.hasJoinTable()) {
      throw unsupported(
          entityType,
          "association '" + toOne.getAttributeName() + "' must use primary-key join columns");
    }
    EntityIdentifierMapping targetIdentifier =
        toOne.getAssociatedEntityMappingType().getIdentifierMapping();
    List<LeafAccessor> identifierLeaves = new ArrayList<>();
    collectLeaves(targetIdentifier, Function.identity(), entityType, identifierLeaves);
    if (identifierLeaves.size() != toOne.getJdbcTypeCount()) {
      throw unsupported(
          entityType,
          "association '" + toOne.getAttributeName() + "' has an incompatible composite key");
    }

    for (int index = 0; index < identifierLeaves.size(); index++) {
      SelectableMapping selectable = toOne.getSelectable(index);
      LeafAccessor leaf = identifierLeaves.get(index);
      Function<Object, Object> accessor =
          source -> {
            Object association = associationAccessor.apply(source);
            if (association == null) {
              return null;
            }
            Object identifier = associationIdentifier(targetIdentifier, association);
            return leaf.accessor().apply(identifier);
          };
      addBasic(
          selectable, selectable.getJdbcMapping(), accessor, tableExpression, entityType, columns);
    }
  }

  private void collectLeaves(
      ValuedModelPart part,
      Function<Object, Object> accessor,
      Class<?> entityType,
      List<LeafAccessor> leaves) {
    if (part instanceof BasicValuedMapping) {
      leaves.add(new LeafAccessor(accessor));
      return;
    }
    if (part instanceof EmbeddableValuedModelPart embedded) {
      embedded
          .getEmbeddableTypeDescriptor()
          .forEachAttributeMapping(
              attribute -> {
                if (attribute.isPluralAttributeMapping()) {
                  throw unsupported(entityType, "composite identifier contains a collection");
                }
                Function<Object, Object> nested =
                    source -> {
                      Object parent = accessor.apply(source);
                      return parent == null ? null : attribute.getValue(parent);
                    };
                collectLeaves(attribute, nested, entityType, leaves);
              });
      return;
    }
    throw unsupported(entityType, "unsupported composite identifier mapping");
  }

  private void addBasic(
      SelectableMapping selectable,
      JdbcMapping jdbcMapping,
      Function<Object, Object> accessor,
      String tableExpression,
      Class<?> entityType,
      List<ColumnMetadata<Object>> columns) {
    if (selectable.isFormula() || !selectable.isInsertable()) {
      return;
    }
    if (!tableExpression.equals(selectable.getContainingTableExpression())) {
      throw unsupported(
          entityType,
          "insertable column '"
              + selectable.getSelectionExpression()
              + "' belongs to another table");
    }
    String columnName = unquote(selectable.getSelectionExpression());
    Class<?> preferredType = jdbcMapping.getJdbcType().getPreferredJavaTypeClass(wrapperOptions);
    Class<?> relationalType =
        preferredType == null ? jdbcMapping.getJdbcJavaType().getJavaTypeClass() : preferredType;
    if (jdbcMapping.getValueConverter() == null
        && jdbcMapping.getMappedJavaType().getJavaTypeClass().isEnum()
        && jdbcMapping.getJdbcType().isInteger()) {
      relationalType = Integer.class;
    }
    columns.add(
        column(
            columnName,
            relationalType,
            source -> relationalValue(jdbcMapping, accessor.apply(source))));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object relationalValue(JdbcMapping jdbcMapping, Object domainValue) {
    BasicValueConverter converter = jdbcMapping.getValueConverter();
    if (converter == null
        && domainValue instanceof Enum<?> enumValue
        && jdbcMapping.getJdbcType().isInteger()) {
      return enumValue.ordinal();
    }
    Object relationalValue =
        converter == null ? domainValue : converter.toRelationalValue(domainValue);
    if (relationalValue == null) {
      return null;
    }
    Class<?> preferredType = jdbcMapping.getJdbcType().getPreferredJavaTypeClass(wrapperOptions);
    Class<?> jdbcJavaClass =
        preferredType == null ? jdbcMapping.getJdbcJavaType().getJavaTypeClass() : preferredType;
    if (jdbcJavaClass.isInstance(relationalValue)) {
      return relationalValue;
    }
    JavaType sourceJavaType =
        converter == null ? jdbcMapping.getMappedJavaType() : converter.getRelationalJavaType();
    return sourceJavaType.unwrap(relationalValue, jdbcJavaClass, wrapperOptions);
  }

  private static Object associationIdentifier(
      EntityIdentifierMapping identifierMapping, Object association) {
    LazyInitializer lazyInitializer = HibernateProxy.extractLazyInitializer(association);
    return lazyInitializer == null
        ? identifierMapping.getIdentifier(association)
        : lazyInitializer.getInternalIdentifier();
  }

  private static void validateSingleTableMapping(EntityPersister persister, Class<?> entityType) {
    int[] tableCount = {0};
    persister.forEachMutableTable(ignored -> tableCount[0]++);
    if (tableCount[0] != 1) {
      throw unsupported(entityType, "mapping spans " + tableCount[0] + " mutable tables");
    }
    if (persister.isInherited()
        || persister.hasSubclasses()
        || persister.getDiscriminatorMapping() != null) {
      throw unsupported(entityType, "entity inheritance/discriminator mappings are not supported");
    }
    if (persister.getSoftDeleteMapping() != null) {
      throw unsupported(entityType, "soft-delete mappings require an insert literal");
    }
  }

  private static TableName parseTableName(String expression, Class<?> entityType) {
    QualifiedNameParser.NameParts parts;
    try {
      parts = QualifiedNameParser.INSTANCE.parse(expression);
    } catch (RuntimeException exception) {
      throw new BulkException(
          "Could not parse physical table name for " + entityType.getName() + ": " + expression,
          exception);
    }
    if (parts.getCatalogName() != null) {
      throw unsupported(
          entityType, "catalog-qualified tables are not representable by core metadata");
    }
    String table = parts.getObjectName().getText();
    return parts.getSchemaName() == null
        ? TableName.of(table)
        : TableName.of(parts.getSchemaName().getText(), table);
  }

  private static String unquote(String expression) {
    Identifier identifier = Identifier.toIdentifier(expression);
    return identifier.getText();
  }

  private static BulkException unsupported(Class<?> entityType, String reason) {
    return new BulkException(
        "Unsupported Hibernate mapping for " + entityType.getName() + ": " + reason);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static ColumnMetadata<Object> column(
      String name, Class<?> javaType, Function<Object, Object> accessor) {
    return ColumnMetadata.of(name, (Class) javaType, accessor);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static EntityMetadata<?> entityMetadata(
      Class<?> javaType, TableName table, List<ColumnMetadata<Object>> columns) {
    return EntityMetadata.of((Class) javaType, table, (List) columns);
  }

  @SuppressWarnings("unchecked")
  private static <T> EntityMetadata<T> cast(EntityMetadata<?> metadata) {
    return (EntityMetadata<T>) metadata;
  }

  private record LeafAccessor(Function<Object, Object> accessor) {}
}
