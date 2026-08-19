package io.ybr.postgresbulk.springdata.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

class SpringDataJdbcEntityMetadataResolverTest {

  @Test
  void resolvesImplicitExplicitSchemaColumnAndNamingStrategy() {
    SpringDataJdbcEntityMetadataResolver defaultResolver = resolver();
    EntityMetadata<ImplicitEntity> implicit = defaultResolver.resolve(ImplicitEntity.class);
    EntityMetadata<ExplicitEntity> explicit = defaultResolver.resolve(ExplicitEntity.class);

    assertEquals("implicit_entity", implicit.table().table());
    assertEquals(List.of("id", "display_name"), names(implicit));
    assertNull(columns(implicit).get("display_name").read(new ImplicitEntity()));
    assertEquals("bulk_schema", explicit.table().schema().orElseThrow());
    assertEquals("explicit_rows", explicit.table().table());
    assertEquals(List.of("entity_id", "given_name"), names(explicit));

    SpringDataJdbcEntityMetadataResolver named = resolver(new PrefixNamingStrategy());
    EntityMetadata<ImplicitEntity> prefixed = named.resolve(ImplicitEntity.class);
    assertEquals("x_implicitentity", prefixed.table().table());
    assertEquals(List.of("x_id", "x_displayname"), names(prefixed));
  }

  @Test
  void exposesResolvedQuotedReferencesWithoutDelimiterCharacters() {
    JdbcMappingContext context = context(DefaultNamingStrategy.INSTANCE, true, List.of());
    SpringDataJdbcEntityMetadataResolver resolver = resolver(context, List.of());

    EntityMetadata<QuotedEntity> metadata = resolver.resolve(QuotedEntity.class);

    assertEquals("Quoted Schema", metadata.table().schema().orElseThrow());
    assertEquals("Order", metadata.table().table());
    assertEquals(List.of("Id", "Select"), names(metadata));
  }

  @Test
  void reportsPersistenceFacingTypesAndValuesForSupportedScalars() {
    ScalarEntity entity = ScalarEntity.sample();
    EntityMetadata<ScalarEntity> metadata = resolver().resolve(ScalarEntity.class);
    Map<String, ColumnMetadata<ScalarEntity>> columns = columns(metadata);

    assertColumn(columns, entity, "text", String.class, "text");
    assertColumn(columns, entity, "character", Character.class, 'x');
    assertColumn(columns, entity, "byte_value", Byte.class, (byte) 1);
    assertColumn(columns, entity, "short_value", Short.class, (short) 2);
    assertColumn(columns, entity, "int_value", Integer.class, 3);
    assertColumn(columns, entity, "long_value", Long.class, 4L);
    assertColumn(columns, entity, "big_integer", BigInteger.class, BigInteger.TEN);
    assertColumn(columns, entity, "decimal_value", BigDecimal.class, new BigDecimal("12.30"));
    assertColumn(columns, entity, "float_value", Float.class, 1.25F);
    assertColumn(columns, entity, "double_value", Double.class, 2.5D);
    assertColumn(columns, entity, "flag", Boolean.class, true);
    assertColumn(columns, entity, "uuid_value", UUID.class, entity.uuidValue);
    assertColumn(columns, entity, "local_date", LocalDate.class, entity.localDate);
    assertColumn(columns, entity, "local_time", LocalTime.class, entity.localTime);
    assertColumn(columns, entity, "local_date_time", LocalDateTime.class, entity.localDateTime);
    assertColumn(columns, entity, "offset_date_time", OffsetDateTime.class, entity.offsetDateTime);
    assertColumn(columns, entity, "offset_time", OffsetTime.class, entity.offsetTime);
    assertColumn(columns, entity, "instant_value", Instant.class, entity.instantValue);
    assertColumn(columns, entity, "status", String.class, "ACTIVE");
    assertEquals(byte[].class, columns.get("payload").javaType());
    assertArrayEquals(entity.payload, (byte[]) columns.get("payload").read(entity));
  }

  @Test
  void appliesWritingConvertersIncludingEnumAndNulls() {
    SpringDataJdbcEntityMetadataResolver resolver =
        resolver(List.of(MoneyWriteConverter.INSTANCE, StatusWriteConverter.INSTANCE));
    ConvertedEntity entity =
        new ConvertedEntity(1L, new Money(new BigDecimal("19.95")), Status.DISABLED);
    EntityMetadata<ConvertedEntity> metadata = resolver.resolve(ConvertedEntity.class);
    Map<String, ColumnMetadata<ConvertedEntity>> columns = columns(metadata);

    assertColumn(columns, entity, "amount", BigDecimal.class, new BigDecimal("19.95"));
    assertColumn(columns, entity, "status", Integer.class, 7);

    ConvertedEntity nulls = new ConvertedEntity(2L, null, null);
    assertNull(columns.get("amount").read(nulls));
    assertNull(columns.get("status").read(nulls));
  }

  @Test
  void includesAssignedIdsAndSelectsGeneratedIdVariantPerInstance() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    AssignedLong assignedLong = new AssignedLong(41L, "long");
    AssignedUuid assignedUuid =
        new AssignedUuid(UUID.fromString("a9b24df1-29c5-4f3a-9e23-c8fd684a0bba"), "uuid");
    GeneratedLong generated = new GeneratedLong(null, "generated");
    GeneratedLong provided = new GeneratedLong(42L, "provided");

    assertEquals(List.of("id", "value"), names(resolver.resolveFor(assignedLong)));
    assertEquals(List.of("id", "value"), names(resolver.resolveFor(assignedUuid)));
    assertEquals(List.of("value"), names(resolver.resolveFor(generated)));
    assertEquals(List.of("id", "value"), names(resolver.resolveFor(provided)));
    assertSame(resolver.resolve(GeneratedLong.class), resolver.resolveFor(provided));
    assertSame(resolver.resolveFor(generated), resolver.resolveFor(new GeneratedLong(null, "x")));
    assertEquals(List.of("value"), names(resolver.resolveFor(new GeneratedPrimitive(0, "zero"))));
    assertEquals(
        List.of("id", "value"), names(resolver.resolveFor(new GeneratedPrimitive(7, "provided"))));
  }

  @Test
  void rejectsGeneratedOnlyIdSequenceAndVersionMappingsExplicitly() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    BulkException idOnly =
        assertThrows(BulkException.class, () -> resolver.resolveFor(new IdOnly()));
    BulkException sequence =
        assertThrows(BulkException.class, () -> resolver.resolve(SequenceEntity.class));
    BulkException version =
        assertThrows(BulkException.class, () -> resolver.resolve(VersionedEntity.class));

    assertTrue(idOnly.getMessage().contains("leaves no insert column"));
    assertTrue(sequence.getMessage().contains("sequence-backed property 'id'"));
    assertTrue(version.getMessage().contains("version property 'version'"));
  }

  @Test
  void flattensSimpleNullableAndNestedEmbeddedValuesWithPrefixes() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    EmbeddedEntity populated =
        new EmbeddedEntity(1L, new Address("Madrid", new GeoPoint(40.4, -3.7)));
    EmbeddedEntity empty = new EmbeddedEntity(2L, null);
    EntityMetadata<EmbeddedEntity> metadata = resolver.resolve(EmbeddedEntity.class);

    assertEquals(
        List.of("id", "addr_city", "addr_geo_latitude", "addr_geo_longitude"), names(metadata));
    assertEquals(List.of(1L, "Madrid", 40.4, -3.7), values(metadata, populated));
    assertEquals(Arrays.asList(2L, null, null, null), values(metadata, empty));
  }

  @Test
  void convertsAggregateReferenceToScalarIdAndSupportsScalarForeignKey() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    UUID customerId = UUID.fromString("cf253c95-8132-4127-be5a-406bf4663ccb");
    ReferenceEntity entity = new ReferenceEntity(1L, AggregateReference.to(customerId), customerId);
    EntityMetadata<ReferenceEntity> metadata = resolver.resolve(ReferenceEntity.class);
    Map<String, ColumnMetadata<ReferenceEntity>> columns = columns(metadata);

    assertColumn(columns, entity, "customer", UUID.class, customerId);
    assertColumn(columns, entity, "scalar_customer_id", UUID.class, customerId);
    ReferenceEntity nulls = new ReferenceEntity(2L, null, null);
    assertNull(columns.get("customer").read(nulls));
  }

  @Test
  void rejectsAggregateChildrenAndExcludesTransientProperty() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    BulkException child =
        assertThrows(BulkException.class, () -> resolver.resolve(ChildCollectionEntity.class));
    EntityMetadata<TransientEntity> transientMetadata = resolver.resolve(TransientEntity.class);

    assertTrue(child.getMessage().contains("aggregate child property 'children'"));
    assertEquals(List.of("id", "kept", "insert_only"), names(transientMetadata));
  }

  @Test
  void readsRecordsPropertyAccessAndInheritedProperties() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver();
    ImmutableRecord record = new ImmutableRecord(1L, "record");
    GetterEntity getter = new GetterEntity(2L, "getter");
    InheritedEntity inherited = new InheritedEntity(3L, "base", "child");

    assertEquals(List.of(1L, "record"), values(resolver.resolve(ImmutableRecord.class), record));
    assertEquals(List.of(2L, "getter"), values(resolver.resolve(GetterEntity.class), getter));
    assertEquals(
        Map.of("id", 3L, "base_value", "base", "child_value", "child"),
        valueMap(resolver.resolve(InheritedEntity.class), inherited));
  }

  @Test
  void leavesUnsupportedRelationalTypeForEncoderPreparationGate() {
    EntityMetadata<UnsupportedTypeEntity> metadata =
        resolver().resolve(UnsupportedTypeEntity.class);

    assertEquals(Timestamp.class, columns(metadata).get("legacy_timestamp").javaType());
    assertInstanceOf(
        Timestamp.class,
        columns(metadata)
            .get("legacy_timestamp")
            .read(new UnsupportedTypeEntity(1L, Timestamp.from(Instant.EPOCH))));
  }

  @Test
  void wrapsConverterFailureWithSafeContextAndPreservesCause() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver(List.of(FailingConverter.INSTANCE));
    EntityMetadata<FailingEntity> metadata = resolver.resolve(FailingEntity.class);
    BulkException failure =
        assertThrows(
            BulkException.class,
            () -> columns(metadata).get("failure").read(new FailingEntity(1L, new FailureValue())));

    assertTrue(failure.getMessage().contains("property 'failure'"));
    assertTrue(failure.getMessage().contains("column 'failure'"));
    assertEquals("intentional converter failure", rootCause(failure).getMessage());
  }

  @Test
  void rejectsDirectJdbcValueConverterBecauseItsInnerJavaTypeIsNotStatic() {
    SpringDataJdbcEntityMetadataResolver resolver = resolver(List.of(JdbcValueConverter.INSTANCE));

    BulkException failure =
        assertThrows(BulkException.class, () -> resolver.resolve(JdbcValueEntity.class));

    assertTrue(failure.getMessage().contains("converts directly to JdbcValue"));
  }

  @Test
  void cachesPerResolverAndIsSafeForConcurrentResolution() throws Exception {
    SpringDataJdbcEntityMetadataResolver first = resolver();
    SpringDataJdbcEntityMetadataResolver second = resolver();
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<EntityMetadata<ImplicitEntity>>> calls = new ArrayList<>();
      for (int index = 0; index < 64; index++) {
        calls.add(() -> first.resolve(ImplicitEntity.class));
      }
      List<EntityMetadata<ImplicitEntity>> results =
          pool.invokeAll(calls).stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception exception) {
                      throw new AssertionError(exception);
                    }
                  })
              .toList();
      assertTrue(results.stream().allMatch(metadata -> metadata == results.get(0)));
      assertFalse(first.resolve(ImplicitEntity.class) == second.resolve(ImplicitEntity.class));
    } finally {
      pool.shutdownNow();
    }
  }

  private static SpringDataJdbcEntityMetadataResolver resolver(Object... converters) {
    return resolver(List.of(converters));
  }

  private static SpringDataJdbcEntityMetadataResolver resolver(List<?> converters) {
    JdbcMappingContext context = context(DefaultNamingStrategy.INSTANCE, false, converters);
    return resolver(context, converters);
  }

  private static SpringDataJdbcEntityMetadataResolver resolver(NamingStrategy namingStrategy) {
    JdbcMappingContext context = context(namingStrategy, false, List.of());
    return resolver(context, List.of());
  }

  private static JdbcMappingContext context(
      NamingStrategy namingStrategy, boolean forceQuote, List<?> converters) {
    JdbcCustomConversions conversions = new JdbcCustomConversions(converters);
    JdbcMappingContext context = new JdbcMappingContext(namingStrategy);
    context.setForceQuote(forceQuote);
    context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    context.afterPropertiesSet();
    return context;
  }

  private static SpringDataJdbcEntityMetadataResolver resolver(
      JdbcMappingContext context, List<?> converterObjects) {
    JdbcCustomConversions conversions = new JdbcCustomConversions(converterObjects);
    MappingJdbcConverter converter =
        new MappingJdbcConverter(
            context, (identifier, path) -> List.of(), conversions, JdbcTypeFactory.unsupported());
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  private static <T> List<String> names(EntityMetadata<T> metadata) {
    return metadata.insertColumns().stream().map(ColumnMetadata::columnName).toList();
  }

  private static <T> List<Object> values(EntityMetadata<T> metadata, T source) {
    return metadata.insertColumns().stream().map(column -> column.read(source)).toList();
  }

  private static <T> Map<String, Object> valueMap(EntityMetadata<T> metadata, T source) {
    return metadata.insertColumns().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                ColumnMetadata::columnName, column -> column.read(source)));
  }

  private static <T> Map<String, ColumnMetadata<T>> columns(EntityMetadata<T> metadata) {
    return metadata.insertColumns().stream()
        .collect(java.util.stream.Collectors.toMap(ColumnMetadata::columnName, column -> column));
  }

  private static <T> void assertColumn(
      Map<String, ColumnMetadata<T>> columns,
      T source,
      String name,
      Class<?> expectedType,
      Object expectedValue) {
    assertEquals(expectedType, columns.get(name).javaType());
    assertEquals(expectedValue, columns.get(name).read(source));
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  static class ImplicitEntity {
    @Id Long id;
    String displayName;
  }

  @Table(name = "explicit_rows", schema = "bulk_schema")
  static class ExplicitEntity {
    @Id
    @Column("entity_id")
    Long id;

    @Column("given_name")
    String name;
  }

  @Table(name = "Order", schema = "Quoted Schema")
  static class QuotedEntity {
    @Id
    @Column("Id")
    Long id;

    @Column("Select")
    String value;
  }

  static class ScalarEntity {
    @Id Long id;
    String text;
    Character character;
    byte byteValue;
    Short shortValue;
    int intValue;
    Long longValue;
    BigInteger bigInteger;
    BigDecimal decimalValue;
    Float floatValue;
    double doubleValue;
    boolean flag;
    UUID uuidValue;
    LocalDate localDate;
    LocalTime localTime;
    LocalDateTime localDateTime;
    OffsetDateTime offsetDateTime;
    OffsetTime offsetTime;
    Instant instantValue;
    Status status;
    byte[] payload;

    static ScalarEntity sample() {
      ScalarEntity entity = new ScalarEntity();
      entity.id = 1L;
      entity.text = "text";
      entity.character = 'x';
      entity.byteValue = 1;
      entity.shortValue = 2;
      entity.intValue = 3;
      entity.longValue = 4L;
      entity.bigInteger = BigInteger.TEN;
      entity.decimalValue = new BigDecimal("12.30");
      entity.floatValue = 1.25F;
      entity.doubleValue = 2.5D;
      entity.flag = true;
      entity.uuidValue = UUID.fromString("30636c0a-4a2e-4400-89d1-22dc73a0aedd");
      entity.localDate = LocalDate.of(2026, 8, 19);
      entity.localTime = LocalTime.of(12, 34, 56);
      entity.localDateTime = LocalDateTime.of(2026, 8, 19, 12, 34, 56);
      entity.offsetDateTime = OffsetDateTime.of(entity.localDateTime, ZoneOffset.ofHours(2));
      entity.offsetTime = OffsetTime.of(entity.localTime, ZoneOffset.ofHours(2));
      entity.instantValue = Instant.parse("2026-08-19T10:34:56Z");
      entity.status = Status.ACTIVE;
      entity.payload = new byte[] {0, 1, -1};
      return entity;
    }
  }

  record Money(BigDecimal value) {}

  enum Status {
    ACTIVE,
    DISABLED
  }

  static class ConvertedEntity {
    @Id Long id;
    Money amount;
    Status status;

    ConvertedEntity(Long id, Money amount, Status status) {
      this.id = id;
      this.amount = amount;
      this.status = status;
    }
  }

  @WritingConverter
  enum MoneyWriteConverter implements Converter<Money, BigDecimal> {
    INSTANCE;

    @Override
    public BigDecimal convert(Money source) {
      return source.value();
    }
  }

  @WritingConverter
  enum StatusWriteConverter implements Converter<Status, Integer> {
    INSTANCE;

    @Override
    public Integer convert(Status source) {
      return source == Status.ACTIVE ? 1 : 7;
    }
  }

  record AssignedLong(@Id Long id, String value) {}

  record AssignedUuid(@Id UUID id, String value) {}

  record GeneratedLong(@Id Long id, String value) {}

  record GeneratedPrimitive(@Id long id, String value) {}

  static class IdOnly {
    @Id Long id;
  }

  static class SequenceEntity {
    @Id
    @Sequence(sequence = "entity_sequence")
    Long id;

    String value;
  }

  static class VersionedEntity {
    @Id Long id;
    @Version Long version;
    String value;
  }

  static class EmbeddedEntity {
    @Id Long id;

    @Embedded.Nullable(prefix = "addr_") Address address;

    EmbeddedEntity(Long id, Address address) {
      this.id = id;
      this.address = address;
    }
  }

  static class Address {
    String city;

    @Embedded.Nullable(prefix = "geo_") GeoPoint point;

    Address(String city, GeoPoint point) {
      this.city = city;
      this.point = point;
    }
  }

  record GeoPoint(Double latitude, Double longitude) {}

  static class Customer {
    @Id UUID id;
  }

  static class ReferenceEntity {
    @Id Long id;
    AggregateReference<Customer, UUID> customer;

    @Column("scalar_customer_id")
    UUID scalarCustomerId;

    ReferenceEntity(Long id, AggregateReference<Customer, UUID> customer, UUID scalarCustomerId) {
      this.id = id;
      this.customer = customer;
      this.scalarCustomerId = scalarCustomerId;
    }
  }

  static class ChildCollectionEntity {
    @Id Long id;

    @MappedCollection(idColumn = "parent_id")
    List<Child> children;
  }

  static class Child {
    String value;
  }

  static class TransientEntity {
    @Id Long id;
    String kept;
    @InsertOnlyProperty String insertOnly;
    @Transient String ignored;
  }

  @Table("immutable_records")
  record ImmutableRecord(@Id Long id, String value) {}

  @AccessType(AccessType.Type.PROPERTY)
  static class GetterEntity {
    private final Long id;
    private final String value;

    GetterEntity(Long id, String value) {
      this.id = id;
      this.value = value;
    }

    @Id
    public Long getId() {
      return id;
    }

    public String getValue() {
      return value;
    }
  }

  abstract static class BaseEntity {
    @Id Long id;
    String baseValue;
  }

  static class InheritedEntity extends BaseEntity {
    String childValue;

    InheritedEntity(Long id, String baseValue, String childValue) {
      this.id = id;
      this.baseValue = baseValue;
      this.childValue = childValue;
    }
  }

  static class UnsupportedTypeEntity {
    @Id Long id;
    Timestamp legacyTimestamp;

    UnsupportedTypeEntity(Long id, Timestamp legacyTimestamp) {
      this.id = id;
      this.legacyTimestamp = legacyTimestamp;
    }
  }

  record FailureValue() {}

  static class FailingEntity {
    @Id Long id;
    FailureValue failure;

    FailingEntity(Long id, FailureValue failure) {
      this.id = id;
      this.failure = failure;
    }
  }

  @WritingConverter
  enum FailingConverter implements Converter<FailureValue, String> {
    INSTANCE;

    @Override
    public String convert(FailureValue source) {
      throw new IllegalStateException("intentional converter failure");
    }
  }

  record JdbcValueDomain(String value) {}

  static class JdbcValueEntity {
    @Id Long id;
    JdbcValueDomain value;
  }

  @WritingConverter
  enum JdbcValueConverter implements Converter<JdbcValueDomain, JdbcValue> {
    INSTANCE;

    @Override
    public JdbcValue convert(JdbcValueDomain source) {
      return JdbcValue.of(source.value(), JDBCType.VARCHAR);
    }
  }

  static final class PrefixNamingStrategy implements NamingStrategy {
    @Override
    public String getTableName(Class<?> type) {
      return "x_" + type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String getColumnName(
        org.springframework.data.relational.core.mapping.RelationalPersistentProperty property) {
      return "x_" + property.getName().toLowerCase(java.util.Locale.ROOT);
    }
  }
}
