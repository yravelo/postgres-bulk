package io.ybr.postgresbulk.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.BulkException;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Formula;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class HibernateEntityMetadataResolverIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  private static SessionFactory sessionFactory;
  private static HibernateEntityMetadataResolver resolver;

  @BeforeAll
  static void startHibernate() throws Exception {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA IF NOT EXISTS bulk_schema");
    }
    sessionFactory = buildSessionFactory(null, mappedClasses());
    resolver = new HibernateEntityMetadataResolver(sessionFactory);
  }

  @AfterAll
  static void stopHibernate() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @Test
  void resolvesPhysicalTableSchemaQuotesAndBasicInsertSelection() {
    EntityMetadata<BasicEntity> metadata = resolver.resolve(BasicEntity.class);

    assertEquals("bulk_schema", metadata.table().schema().orElseThrow());
    assertEquals("basic_rows", metadata.table().table());
    assertEquals(List.of("id", "immutable_value", "display_name", "version"), names(metadata));
    BasicEntity entity = new BasicEntity(7L, "Ada", "ignored", "fixed");
    assertEquals(List.of(7L, "fixed", "Ada", 0), values(metadata, entity));
  }

  @Test
  void resolvesImplicitAndQuotedIdentifiersWithoutQuoteCharacters() {
    EntityMetadata<ImplicitEntity> implicit = resolver.resolve(ImplicitEntity.class);
    EntityMetadata<QuotedEntity> quoted = resolver.resolve(QuotedEntity.class);

    assertEquals("ImplicitEntity", implicit.table().table());
    assertEquals("Quoted Table", quoted.table().table());
    assertEquals(List.of("id", "Quoted Column"), names(quoted));
  }

  @Test
  void supportsFieldPropertyAndMappedSuperclassAccess() {
    PropertyEntity property = new PropertyEntity();
    property.setId(11L);
    property.setValue("getter-value");
    MappedChild inherited = new MappedChild();
    inherited.id = 12L;
    inherited.value = "inherited";

    assertEquals(
        List.of(11L, "getter-value"), values(resolver.resolve(PropertyEntity.class), property));
    assertEquals(List.of(12L, "inherited"), values(resolver.resolve(MappedChild.class), inherited));
  }

  @Test
  void flattensEmbeddedValueAndEmbeddedIdentifierInRuntimeOrder() {
    EmbeddedEntity embedded = new EmbeddedEntity();
    embedded.id = 1L;
    embedded.address = new Address("Havana", "10100");
    EmbeddedKeyEntity keyed = new EmbeddedKeyEntity();
    keyed.id = new CompositeId("tenant-a", 42L);
    keyed.value = "value";

    EntityMetadata<EmbeddedEntity> embeddedMetadata = resolver.resolve(EmbeddedEntity.class);
    EntityMetadata<EmbeddedKeyEntity> keyMetadata = resolver.resolve(EmbeddedKeyEntity.class);
    assertEquals(
        Map.of("city_name", "Havana", "postal_code", "10100", "id", 1L),
        valueMap(embeddedMetadata, embedded));
    assertEquals(
        Map.of("tenant_key", "tenant-a", "local_key", 42L, "value", "value"),
        valueMap(keyMetadata, keyed));
  }

  @Test
  void includesAssignedIdentifiersAndOmitsIdentityAndSequenceGeneratedIdentifiers() {
    assertTrue(names(resolver.resolve(BasicEntity.class)).contains("id"));
    assertEquals(List.of("value"), names(resolver.resolve(IdentityEntity.class)));
    assertEquals(List.of("value"), names(resolver.resolve(SequenceEntity.class)));
  }

  @Test
  void convertsEnumsAndJpaConvertersToRelationalValuesIncludingNull() {
    ConvertedEntity entity = new ConvertedEntity();
    entity.id = 3L;
    entity.text = "mixedCase";
    entity.stringEnum = Status.ACTIVE;
    entity.ordinalEnum = Status.DISABLED;
    entity.customEnum = Status.ACTIVE;

    EntityMetadata<ConvertedEntity> metadata = resolver.resolve(ConvertedEntity.class);
    Map<String, Object> values = valueMap(metadata, entity);
    assertEquals("MIXEDCASE", values.get("converted_text"));
    assertEquals("ACTIVE", values.get("string_enum"));
    assertEquals(1, values.get("ordinal_enum"));
    assertEquals("A", values.get("custom_enum"));

    entity.text = null;
    entity.stringEnum = null;
    entity.ordinalEnum = null;
    entity.customEnum = null;
    assertNull(valueMap(metadata, entity).get("converted_text"));
    assertNull(valueMap(metadata, entity).get("string_enum"));
    assertNull(valueMap(metadata, entity).get("ordinal_enum"));
    assertNull(valueMap(metadata, entity).get("custom_enum"));

    entity.text = "boom";
    PersistenceException failure =
        assertThrows(
            PersistenceException.class, () -> valueMap(metadata, entity).get("converted_text"));
    assertTrue(failure.getMessage().contains("AttributeConverter"));
    assertEquals("converter failure", failure.getCause().getMessage());
  }

  @Test
  void projectsSimpleAndNullableAssociationForeignKeysWithoutRequiringSession() {
    TargetEntity target = new TargetEntity();
    target.id = 99L;
    target.name = "target";
    AssociationEntity source = new AssociationEntity();
    source.id = 5L;
    source.target = target;
    EntityMetadata<AssociationEntity> metadata = resolver.resolve(AssociationEntity.class);

    assertEquals(99L, valueMap(metadata, source).get("target_id"));
    source.target = null;
    assertNull(valueMap(metadata, source).get("target_id"));
  }

  @Test
  void readsIdentifierFromDetachedUninitializedHibernateProxy() {
    Session session = sessionFactory.openSession();
    session.beginTransaction();
    TargetEntity target = new TargetEntity();
    target.id = 700L;
    target.name = "proxy target";
    session.persist(target);
    session.getTransaction().commit();
    session.clear();
    TargetEntity proxy = session.getReference(TargetEntity.class, 700L);
    assertFalse(org.hibernate.Hibernate.isInitialized(proxy));
    session.close();

    AssociationEntity source = new AssociationEntity();
    source.id = 701L;
    source.target = proxy;
    assertEquals(
        700L, valueMap(resolver.resolve(AssociationEntity.class), source).get("target_id"));
    assertFalse(org.hibernate.Hibernate.isInitialized(proxy));
  }

  @Test
  void excludesFormulaAndIncludesColumnDefaultUnlessMappingMarksItNonInsertable() {
    EntityMetadata<DefaultEntity> metadata = resolver.resolve(DefaultEntity.class);

    assertEquals(List.of("id", "database_default"), names(metadata));
    DefaultEntity entity = new DefaultEntity();
    entity.id = 1L;
    assertNull(valueMap(metadata, entity).get("database_default"));
  }

  @Test
  void rejectsNonEntitiesSecondaryTablesAndInheritanceWithActionableMessages() {
    assertTrue(
        assertThrows(BulkException.class, () -> resolver.resolve(String.class))
            .getMessage()
            .contains("Not a mapped"));
    assertTrue(
        assertThrows(BulkException.class, () -> resolver.resolve(SecondaryEntity.class))
            .getMessage()
            .contains("tables"));
    assertTrue(
        assertThrows(BulkException.class, () -> resolver.resolve(InheritanceRoot.class))
            .getMessage()
            .contains("inheritance"));
  }

  @Test
  void cachesOncePerResolverAndIsSafeUnderConcurrentResolution() throws Exception {
    EntityMetadata<BasicEntity> expected = resolver.resolve(BasicEntity.class);
    var executor = Executors.newFixedThreadPool(8);
    try {
      List<Callable<EntityMetadata<BasicEntity>>> calls = new ArrayList<>();
      for (int index = 0; index < 100; index++) {
        calls.add(() -> resolver.resolve(BasicEntity.class));
      }
      for (var future : executor.invokeAll(calls)) {
        assertSame(expected, future.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void isolatesCachesAndPhysicalNamingAcrossEntityManagerFactories() {
    try (SessionFactory prefixedFactory =
        buildSessionFactory(new PrefixNamingStrategy(), ImplicitEntity.class)) {
      HibernateEntityMetadataResolver prefixed =
          new HibernateEntityMetadataResolver(prefixedFactory);
      assertEquals("p_implicitentity", prefixed.resolve(ImplicitEntity.class).table().table());
      assertEquals("ImplicitEntity", resolver.resolve(ImplicitEntity.class).table().table());
    }
  }

  @Test
  void insertsValuesProducedByMetadataIntoRealPostgresql() throws Exception {
    EntityMetadata<ConvertedEntity> metadata = resolver.resolve(ConvertedEntity.class);
    ConvertedEntity entity = new ConvertedEntity();
    entity.id = 800L;
    entity.text = "postgres";
    entity.stringEnum = Status.ACTIVE;
    entity.ordinalEnum = Status.DISABLED;
    entity.customEnum = Status.ACTIVE;

    try (Connection connection = connection()) {
      String columns =
          metadata.insertColumns().stream()
              .map(column -> quote(column.columnName()))
              .collect(Collectors.joining(", "));
      String placeholders =
          metadata.insertColumns().stream().map(ignored -> "?").collect(Collectors.joining(", "));
      String sql = "INSERT INTO converted_rows (" + columns + ") VALUES (" + placeholders + ")";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        for (int index = 0; index < metadata.insertColumns().size(); index++) {
          statement.setObject(index + 1, metadata.insertColumns().get(index).read(entity));
        }
        assertEquals(1, statement.executeUpdate());
      }
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT converted_text, string_enum, ordinal_enum, custom_enum FROM converted_rows WHERE id = 800")) {
        assertTrue(result.next());
        assertEquals("POSTGRES", result.getString(1));
        assertEquals("ACTIVE", result.getString(2));
        assertEquals(1, result.getInt(3));
        assertEquals("A", result.getString(4));
      }
    }
  }

  private static SessionFactory buildSessionFactory(
      PhysicalNamingStrategy namingStrategy, Class<?>... classes) {
    Configuration configuration = new Configuration();
    configuration.setProperty(AvailableSettings.JAKARTA_JDBC_URL, POSTGRES.getJdbcUrl());
    configuration.setProperty(AvailableSettings.JAKARTA_JDBC_USER, POSTGRES.getUsername());
    configuration.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, POSTGRES.getPassword());
    configuration.setProperty(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.postgresql.Driver");
    configuration.setProperty(AvailableSettings.JAKARTA_HBM2DDL_DATABASE_ACTION, "drop-and-create");
    configuration.setProperty(AvailableSettings.SHOW_SQL, "false");
    if (namingStrategy != null) {
      configuration.setPhysicalNamingStrategy(namingStrategy);
    }
    for (Class<?> type : classes) {
      configuration.addAnnotatedClass(type);
    }
    return configuration.buildSessionFactory();
  }

  private static Class<?>[] mappedClasses() {
    return new Class<?>[] {
      BasicEntity.class,
      ImplicitEntity.class,
      QuotedEntity.class,
      PropertyEntity.class,
      MappedChild.class,
      EmbeddedEntity.class,
      EmbeddedKeyEntity.class,
      IdentityEntity.class,
      SequenceEntity.class,
      ConvertedEntity.class,
      TargetEntity.class,
      AssociationEntity.class,
      DefaultEntity.class,
      SecondaryEntity.class,
      InheritanceRoot.class,
      InheritanceChild.class
    };
  }

  private static Connection connection() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static List<String> names(EntityMetadata<?> metadata) {
    return metadata.insertColumns().stream().map(ColumnMetadata::columnName).toList();
  }

  private static <T> List<Object> values(EntityMetadata<T> metadata, T entity) {
    return metadata.insertColumns().stream().map(column -> column.read(entity)).toList();
  }

  private static <T> Map<String, Object> valueMap(EntityMetadata<T> metadata, T entity) {
    Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (ColumnMetadata<T> column : metadata.insertColumns()) {
      values.put(column.columnName(), column.read(entity));
    }
    return values;
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  @Entity
  @Table(name = "basic_rows", schema = "bulk_schema")
  static class BasicEntity {
    @Id Long id;

    @Column(name = "display_name")
    String name;

    @Column(name = "ignored_value", insertable = false)
    String ignored;

    @Column(name = "immutable_value", updatable = false)
    String immutable;

    @Version Integer version;

    BasicEntity() {}

    BasicEntity(Long id, String name, String ignored, String immutable) {
      this.id = id;
      this.name = name;
      this.ignored = ignored;
      this.immutable = immutable;
      this.version = 0;
    }
  }

  @Entity(name = "ImplicitEntity")
  static class ImplicitEntity {
    @Id Long id;
  }

  @Entity
  @Table(name = "\"Quoted Table\"")
  static class QuotedEntity {
    @Id Long id;

    @Column(name = "\"Quoted Column\"")
    String value;
  }

  @Entity
  @Access(AccessType.PROPERTY)
  @Table(name = "property_rows")
  static class PropertyEntity {
    private Long id;
    private String value;

    @Id
    Long getId() {
      return id;
    }

    void setId(Long id) {
      this.id = id;
    }

    String getValue() {
      return value;
    }

    void setValue(String value) {
      this.value = value;
    }
  }

  @MappedSuperclass
  static class MappedBase {
    @Id Long id;
  }

  @Entity
  @Table(name = "mapped_children")
  static class MappedChild extends MappedBase {
    String value;
  }

  @Embeddable
  static class Address {
    @Column(name = "city_name")
    String city;

    @Column(name = "postal_code")
    String postalCode;

    Address() {}

    Address(String city, String postalCode) {
      this.city = city;
      this.postalCode = postalCode;
    }
  }

  @Entity
  @Table(name = "embedded_rows")
  static class EmbeddedEntity {
    @Id Long id;
    @Embedded Address address;
  }

  @Embeddable
  static class CompositeId {
    @Column(name = "tenant_key")
    String tenant;

    @Column(name = "local_key")
    Long local;

    CompositeId() {}

    CompositeId(String tenant, Long local) {
      this.tenant = tenant;
      this.local = local;
    }
  }

  @Entity
  @Table(name = "embedded_key_rows")
  static class EmbeddedKeyEntity {
    @EmbeddedId CompositeId id;
    String value;
  }

  @Entity
  @Table(name = "identity_rows")
  static class IdentityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String value;
  }

  @Entity
  @Table(name = "sequence_rows")
  @SequenceGenerator(name = "row_sequence", sequenceName = "row_sequence", allocationSize = 1)
  static class SequenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "row_sequence")
    Long id;

    String value;
  }

  enum Status {
    ACTIVE,
    DISABLED
  }

  @Converter
  static class UppercaseConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String attribute) {
      if ("boom".equals(attribute)) {
        throw new IllegalArgumentException("converter failure");
      }
      return attribute == null ? null : attribute.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
      return dbData;
    }
  }

  @Converter
  static class StatusCodeConverter implements AttributeConverter<Status, String> {
    @Override
    public String convertToDatabaseColumn(Status attribute) {
      return attribute == null ? null : attribute.name().substring(0, 1);
    }

    @Override
    public Status convertToEntityAttribute(String dbData) {
      return dbData == null ? null : Status.ACTIVE;
    }
  }

  @Entity
  @Table(name = "converted_rows")
  static class ConvertedEntity {
    @Id Long id;

    @Convert(converter = UppercaseConverter.class)
    @Column(name = "converted_text")
    String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "string_enum")
    Status stringEnum;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "ordinal_enum")
    Status ordinalEnum;

    @Convert(converter = StatusCodeConverter.class)
    @Column(name = "custom_enum")
    Status customEnum;
  }

  @Entity
  @Table(name = "target_rows")
  static class TargetEntity {
    @Id Long id;
    String name;
  }

  @Entity
  @Table(name = "association_rows")
  static class AssociationEntity {
    @Id Long id;

    @ManyToOne
    @jakarta.persistence.JoinColumn(name = "target_id")
    TargetEntity target;
  }

  @Entity
  @Table(name = "default_rows")
  static class DefaultEntity {
    @Id Long id;

    @ColumnDefault("'database-value'")
    @Column(name = "database_default")
    String defaulted;

    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "generated_value")
    String generated;

    @Formula("upper(database_default)")
    String calculated;
  }

  @Entity
  @Table(name = "secondary_rows")
  @SecondaryTable(name = "secondary_details")
  static class SecondaryEntity {
    @Id Long id;

    @Column(table = "secondary_details")
    String detail;
  }

  @Entity
  @Inheritance
  @Table(name = "inheritance_rows")
  static class InheritanceRoot {
    @Id Long id;
  }

  @Entity
  static class InheritanceChild extends InheritanceRoot {
    String detail;
  }

  static class PrefixNamingStrategy implements PhysicalNamingStrategy {
    @Override
    public Identifier toPhysicalCatalogName(
        Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
      return logicalName;
    }

    @Override
    public Identifier toPhysicalSchemaName(
        Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
      return logicalName;
    }

    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
      return Identifier.toIdentifier(
          "p_" + logicalName.getText().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Identifier toPhysicalSequenceName(
        Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
      return logicalName;
    }

    @Override
    public Identifier toPhysicalColumnName(
        Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
      return logicalName;
    }
  }
}
