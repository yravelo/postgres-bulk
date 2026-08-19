package io.ybr.postgresbulk.springdata.jdbc;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadataResolver;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.sql.SQLType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.mapping.AccessOptions;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.PersistentPropertyPathAccessor;
import org.springframework.data.relational.core.conversion.IdValueSource;
import org.springframework.data.relational.core.mapping.AggregatePath;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.util.TypeInformation;

/**
 * Resolves Spring Data JDBC mappings into immutable, framework-neutral bulk metadata.
 *
 * <p>Instances are bound to the mapping context owned by one {@link JdbcConverter}. Structural
 * mapping and both identifier variants are cached per resolver instance, making resolved metadata
 * safe for concurrent reads without sharing state between application contexts.
 *
 * <p>The supported mapping subset is one aggregate root table with scalar, converted, embedded, and
 * {@code AggregateReference} values. Child entities, collections, maps, sequences, version
 * properties, and mappings that require more than a schema-qualified table fail during resolution.
 * Database-generated identifiers are selected per instance by {@link #resolveFor(Object)} and are
 * omitted from its insert columns; assigned identifiers are retained.
 */
public final class SpringDataJdbcEntityMetadataResolver implements EntityMetadataResolver {

  private final JdbcConverter converter;
  private final CustomConversions conversions;
  private final RelationalMappingContext mappingContext;
  private final ConcurrentMap<Class<?>, ResolvedMapping<?>> cache = new ConcurrentHashMap<>();

  /**
   * Creates a resolver using the application's configured Spring Data JDBC converter.
   *
   * @param converter converter containing the effective mapping context
   * @param conversions the exact custom conversions configured in {@code converter}
   * @throws NullPointerException if an argument is {@code null}
   */
  public SpringDataJdbcEntityMetadataResolver(
      JdbcConverter converter, CustomConversions conversions) {
    this.converter = Objects.requireNonNull(converter, "converter must not be null");
    this.conversions = Objects.requireNonNull(conversions, "conversions must not be null");
    this.mappingContext = converter.getMappingContext();
  }

  /**
   * Resolves the stable mapping for an entity type, retaining its identifier column.
   *
   * <p>This class-only SPI cannot determine whether a nullable identifier is generated for a
   * particular instance. Call {@link #resolveFor(Object)} at the operation boundary when generated
   * identifiers are supported.
   *
   * @param entityType mapped aggregate-root class
   * @param <T> entity type
   * @return cached metadata including an identifier column, when one exists
   * @throws NullPointerException if {@code entityType} is {@code null}
   * @throws BulkException if the class is not mapped or its mapping is unsupported
   */
  @Override
  public <T> EntityMetadata<T> resolve(Class<T> entityType) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    return mapping(entityType).assignedIdMetadata();
  }

  /**
   * Resolves metadata for one entity instance using Spring Data JDBC identifier semantics.
   *
   * <p>A provided identifier selects the same metadata as {@link #resolve(Class)}. A generated
   * identifier selects a cached variant with the identifier column omitted. This method does not
   * mutate the instance or propagate a generated value. Callers processing many rows can invoke it
   * once per row in a single pass and reject a change of returned metadata as a mixed identifier
   * policy.
   *
   * @param entity mapped aggregate-root instance
   * @param <T> entity type
   * @return cached metadata matching the instance's identifier source
   * @throws NullPointerException if {@code entity} is {@code null}
   * @throws BulkException if the mapping is unsupported or omitting the identifier leaves no column
   */
  public <T> EntityMetadata<T> resolveFor(T entity) {
    Objects.requireNonNull(entity, "entity must not be null");
    ResolvedMapping<T> resolved = mapping(runtimeType(entity));
    IdValueSource source = IdValueSource.forInstance(entity, resolved.persistentEntity());
    return source == IdValueSource.GENERATED
        ? resolved.forGeneratedId()
        : resolved.assignedIdMetadata();
  }

  private <T> ResolvedMapping<T> mapping(Class<T> entityType) {
    try {
      return cast(cache.computeIfAbsent(entityType, this::resolveUncached));
    } catch (BulkException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BulkException(
          "Could not resolve Spring Data JDBC metadata for " + entityType.getName(), exception);
    }
  }

  private ResolvedMapping<?> resolveUncached(Class<?> entityType) {
    RelationalPersistentEntity<?> persistentEntity = mappingContext.getPersistentEntity(entityType);
    if (persistentEntity == null) {
      throw unsupported(entityType, "type is not a mapped persistent entity");
    }
    if (persistentEntity.hasVersionProperty()) {
      throw unsupported(
          entityType,
          "version property '" + persistentEntity.getRequiredVersionProperty().getName() + "'");
    }

    TableName table = tableName(persistentEntity, entityType);
    List<ResolvedColumn> columns = new ArrayList<>();
    for (RelationalPersistentProperty property : persistentEntity) {
      addProperty(persistentEntity, property.getName(), property, entityType, columns);
    }
    if (columns.isEmpty()) {
      throw unsupported(entityType, "mapping has no writable scalar columns");
    }

    return resolvedMapping(entityType, persistentEntity, table, columns);
  }

  private void addProperty(
      RelationalPersistentEntity<?> root,
      String path,
      RelationalPersistentProperty property,
      Class<?> entityType,
      List<ResolvedColumn> columns) {
    if (property.hasSequence()) {
      throw unsupported(entityType, "sequence-backed property '" + path + "'");
    }

    PersistentPropertyPath<RelationalPersistentProperty> persistentPath =
        mappingContext.getPersistentPropertyPath(path, root.getTypeInformation());
    AggregatePath aggregatePath = mappingContext.getAggregatePath(persistentPath);
    if (!aggregatePath.isWritable()) {
      return;
    }
    if (property.isEmbedded()) {
      RelationalPersistentEntity<?> embedded = mappingContext.getPersistentEntity(property);
      if (embedded == null) {
        throw unsupported(entityType, "embedded property '" + path + "' has no mapping");
      }
      for (RelationalPersistentProperty nested : embedded) {
        addProperty(root, path + "." + nested.getName(), nested, entityType, columns);
      }
      return;
    }
    boolean binary = property.getType() == byte[].class;
    if ((!binary
            && (aggregatePath.isMultiValued()
                || aggregatePath.isCollectionLike()
                || aggregatePath.isMap()))
        || aggregatePath.isEntity()) {
      throw unsupported(entityType, "aggregate child property '" + path + "'");
    }

    Class<?> domainType = binary ? byte[].class : property.getActualType();
    boolean directValue = isStandardJdbcScalar(domainType);
    Class<?> defaultColumnType =
        directValue ? boxed(domainType) : converter.getColumnType(property);
    Class<?> customTarget = conversions.getCustomWriteTarget(domainType).orElse(null);
    Class<?> relationalType =
        boxed(
            customTarget != null
                    && !isStandardJdbcScalar(domainType)
                    && conversions.hasCustomWriteTarget(domainType, customTarget)
                ? customTarget
                : defaultColumnType);
    if (relationalType == JdbcValue.class) {
      throw unsupported(
          entityType,
          "property '"
              + path
              + "' converts directly to JdbcValue without a statically known Java value type");
    }
    SQLType sqlType = converter.getTargetSqlType(property);
    String columnName = aggregatePath.getColumnInfo().name().getReference();
    columns.add(
        new ResolvedColumn(
            columnName,
            relationalType,
            persistentPath,
            sqlType,
            root.isIdProperty(property),
            directValue));
  }

  private Object read(
      Object source, RelationalPersistentEntity<?> persistentEntity, ResolvedColumn column) {
    try {
      PersistentPropertyPathAccessor<Object> accessor =
          persistentEntity.getPropertyPathAccessor(source);
      Object value =
          accessor.getProperty(
              column.path(),
              AccessOptions.defaultGetOptions()
                  .withNullValues(AccessOptions.GetOptions.GetNulls.EARLY_RETURN));
      Object relationalValue;
      if (column.directValue()) {
        relationalValue = value;
      } else {
        JdbcValue jdbcValue =
            converter.writeJdbcValue(
                value, TypeInformation.of(column.relationalType()), column.sqlType());
        relationalValue = jdbcValue.getValue();
      }
      if (relationalValue != null && !column.relationalType().isInstance(relationalValue)) {
        throw new BulkException(
            "Spring Data JDBC converter returned "
                + relationalValue.getClass().getName()
                + " for property '"
                + column.path().toDotPath()
                + "' and column '"
                + column.name()
                + "', expected "
                + column.relationalType().getName());
      }
      return relationalValue;
    } catch (BulkException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BulkException(
          "Could not read Spring Data JDBC property '"
              + column.path().toDotPath()
              + "' for column '"
              + column.name()
              + "' from "
              + persistentEntity.getType().getName(),
          exception);
    }
  }

  private static TableName tableName(
      RelationalPersistentEntity<?> persistentEntity, Class<?> entityType) {
    List<String> parts =
        persistentEntity.getQualifiedTableName().stream().map(SqlIdentifier::getReference).toList();
    return switch (parts.size()) {
      case 1 -> TableName.of(parts.get(0));
      case 2 -> TableName.of(parts.get(0), parts.get(1));
      default ->
          throw unsupported(
              entityType,
              "qualified table name has "
                  + parts.size()
                  + " components; only schema.table is supported");
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ResolvedMapping<?> resolvedMapping(
      Class<?> entityType,
      RelationalPersistentEntity<?> persistentEntity,
      TableName table,
      List<ResolvedColumn> columns) {
    List<ColumnMetadata<Object>> assigned = new ArrayList<>(columns.size());
    List<ColumnMetadata<Object>> generated = new ArrayList<>(columns.size());
    boolean hasId = false;
    for (ResolvedColumn column : columns) {
      ColumnMetadata<Object> metadata = columnMetadata(column, persistentEntity);
      assigned.add(metadata);
      if (column.id()) {
        hasId = true;
      } else {
        generated.add(metadata);
      }
    }
    EntityMetadata<Object> assignedMetadata =
        EntityMetadata.of((Class) entityType, table, assigned);
    EntityMetadata<Object> generatedMetadata =
        !hasId
            ? assignedMetadata
            : generated.isEmpty() ? null : EntityMetadata.of((Class) entityType, table, generated);
    return new ResolvedMapping(
        persistentEntity, assignedMetadata, generatedMetadata, hasId && generatedMetadata == null);
  }

  private static BulkException unsupported(Class<?> entityType, String detail) {
    return new BulkException(
        "Unsupported Spring Data JDBC mapping for " + entityType.getName() + ": " + detail);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ColumnMetadata<Object> columnMetadata(
      ResolvedColumn column, RelationalPersistentEntity<?> persistentEntity) {
    return ColumnMetadata.of(
        column.name(),
        (Class) column.relationalType(),
        source -> read(source, persistentEntity, column));
  }

  private static Class<?> boxed(Class<?> type) {
    Objects.requireNonNull(type, "Spring Data JDBC column type must not be null");
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    return type;
  }

  private static boolean isStandardJdbcScalar(Class<?> type) {
    Class<?> boxedType = boxed(type);
    return boxedType == String.class
        || boxedType == Character.class
        || boxedType == Byte.class
        || boxedType == Short.class
        || boxedType == Integer.class
        || boxedType == Long.class
        || boxedType == java.math.BigInteger.class
        || boxedType == java.math.BigDecimal.class
        || boxedType == Float.class
        || boxedType == Double.class
        || boxedType == Boolean.class
        || boxedType == java.util.UUID.class
        || boxedType == java.time.LocalDate.class
        || boxedType == java.time.LocalTime.class
        || boxedType == java.time.LocalDateTime.class
        || boxedType == java.time.OffsetDateTime.class
        || boxedType == java.time.OffsetTime.class
        || boxedType == java.time.Instant.class
        || boxedType == java.sql.Date.class
        || boxedType == java.sql.Time.class
        || boxedType == java.sql.Timestamp.class
        || boxedType == byte[].class;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> runtimeType(T entity) {
    return (Class<T>) entity.getClass();
  }

  @SuppressWarnings("unchecked")
  private static <T> ResolvedMapping<T> cast(ResolvedMapping<?> mapping) {
    return (ResolvedMapping<T>) mapping;
  }

  private record ResolvedColumn(
      String name,
      Class<?> relationalType,
      PersistentPropertyPath<RelationalPersistentProperty> path,
      SQLType sqlType,
      boolean id,
      boolean directValue) {}

  private record ResolvedMapping<T>(
      RelationalPersistentEntity<T> persistentEntity,
      EntityMetadata<T> assignedIdMetadata,
      EntityMetadata<T> generatedIdMetadata,
      boolean generatedIdLeavesNoColumns) {

    private EntityMetadata<T> forGeneratedId() {
      if (generatedIdLeavesNoColumns) {
        throw unsupported(
            persistentEntity.getType(),
            "omitting the generated identifier leaves no insert column");
      }
      return generatedIdMetadata;
    }
  }
}
