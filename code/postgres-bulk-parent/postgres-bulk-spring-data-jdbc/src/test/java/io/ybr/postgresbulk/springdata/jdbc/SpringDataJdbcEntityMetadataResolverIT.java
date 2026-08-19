package io.ybr.postgresbulk.springdata.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SpringDataJdbcEntityMetadataResolverIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
          .withDatabaseName("postgres_bulk")
          .withUsername("postgres_bulk")
          .withPassword("postgres_bulk");

  private static SpringDataJdbcEntityMetadataResolver resolver;

  @BeforeAll
  static void createDatabaseObjects() throws Exception {
    resolver = resolver();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA \"Bulk Schema\"");
      statement.execute(
          "CREATE TABLE \"Bulk Schema\".\"Order\" ("
              + "\"Id\" uuid PRIMARY KEY, \"Amount\" numeric(12,2), \"Status\" integer, "
              + "\"Business Date\" date, \"Created At\" timestamptz, \"Payload\" bytea, "
              + "\"addr_city\" text, \"addr_postal_code\" text)");
      statement.execute(
          "CREATE TABLE \"generated_rows\" (\"id\" bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, \"value\" text)");
      statement.execute("CREATE TABLE plainmixed (id bigint PRIMARY KEY, value text)");
    }
  }

  @Test
  void roundTripsQuotedSchemaConvertersEnumTemporalUuidBinaryAndEmbedded() throws Exception {
    RichEntity entity =
        new RichEntity(
            UUID.fromString("0769eb55-aafb-4143-a5fb-334c787307d1"),
            new Money(new BigDecimal("123.45")),
            Status.ACTIVE,
            LocalDate.of(2026, 8, 19),
            Instant.parse("2026-08-19T10:15:30Z"),
            new byte[] {0, 1, 2, -1},
            new Address("Madrid", "28001"));
    EntityMetadata<RichEntity> metadata = resolver.resolveFor(entity);

    assertEquals("Bulk Schema", metadata.table().schema().orElseThrow());
    assertEquals("Order", metadata.table().table());
    assertEquals(
        List.of(
            "Id",
            "Amount",
            "Status",
            "Business Date",
            "Created At",
            "Payload",
            "addr_city",
            "addr_postal_code"),
        names(metadata));
    insert(metadata, entity);

    try (Connection connection = connection();
        PreparedStatement query =
            connection.prepareStatement(
                "SELECT \"Amount\", \"Status\", \"Business Date\", \"Created At\", "
                    + "\"Payload\", \"addr_city\", \"addr_postal_code\" "
                    + "FROM \"Bulk Schema\".\"Order\" WHERE \"Id\" = ?")) {
      query.setObject(1, entity.id);
      try (ResultSet result = query.executeQuery()) {
        assertTrue(result.next());
        assertEquals(new BigDecimal("123.45"), result.getBigDecimal(1));
        assertEquals(9, result.getInt(2));
        assertEquals(entity.businessDate, result.getObject(3, LocalDate.class));
        assertEquals(entity.createdAt, result.getObject(4, OffsetDateTime.class).toInstant());
        assertArrayEquals(entity.payload, result.getBytes(5));
        assertEquals("Madrid", result.getString(6));
        assertEquals("28001", result.getString(7));
        assertFalse(result.next());
      }
    }
  }

  @Test
  void omitsGeneratedIdButKeepsProvidedIdSelectionDeterministic() throws Exception {
    GeneratedEntity generated = new GeneratedEntity(null, "database-default");
    GeneratedEntity provided = new GeneratedEntity(99L, "assigned");
    EntityMetadata<GeneratedEntity> generatedMetadata = resolver.resolveFor(generated);

    assertEquals(List.of("value"), names(generatedMetadata));
    assertEquals(List.of("id", "value"), names(resolver.resolveFor(provided)));
    insert(generatedMetadata, generated);

    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT \"id\", \"value\" FROM \"generated_rows\"")) {
      assertTrue(result.next());
      assertEquals(1L, result.getLong(1));
      assertEquals("database-default", result.getString(2));
      assertFalse(result.next());
    }
    assertEquals(null, generated.id);
  }

  @Test
  void provesPlainMixedCaseCannotSurviveTheAlwaysQuotedBoundary() throws Exception {
    EntityMetadata<PlainMixedEntity> metadata = resolver.resolve(PlainMixedEntity.class);
    SQLException failure =
        assertThrows(SQLException.class, () -> insert(metadata, new PlainMixedEntity(1L, "x")));

    assertEquals("PlainMixed", metadata.table().table());
    assertEquals("42P01", failure.getSQLState());
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT to_regclass('plainmixed') IS NOT NULL, to_regclass('\"PlainMixed\"') IS NOT NULL")) {
      assertTrue(result.next());
      assertTrue(result.getBoolean(1));
      assertFalse(result.getBoolean(2));
    }
  }

  private static SpringDataJdbcEntityMetadataResolver resolver() {
    List<?> converterObjects = List.of(MoneyConverter.INSTANCE, StatusConverter.INSTANCE);
    JdbcCustomConversions conversions = new JdbcCustomConversions(converterObjects);
    JdbcMappingContext context = new JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    context.setForceQuote(true);
    context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    context.afterPropertiesSet();
    MappingJdbcConverter converter =
        new MappingJdbcConverter(
            context, (identifier, path) -> List.of(), conversions, JdbcTypeFactory.unsupported());
    return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
  }

  private static <T> void insert(EntityMetadata<T> metadata, T source) throws SQLException {
    String table =
        metadata
            .table()
            .schema()
            .map(schema -> quote(schema) + "." + quote(metadata.table().table()))
            .orElseGet(() -> quote(metadata.table().table()));
    String columns =
        metadata.insertColumns().stream()
            .map(ColumnMetadata::columnName)
            .map(SpringDataJdbcEntityMetadataResolverIT::quote)
            .collect(java.util.stream.Collectors.joining(", "));
    String parameters =
        java.util.stream.IntStream.range(0, metadata.insertColumns().size())
            .mapToObj(ignored -> "?")
            .collect(java.util.stream.Collectors.joining(", "));
    try (Connection connection = connection();
        PreparedStatement insert =
            connection.prepareStatement(
                "INSERT INTO " + table + " (" + columns + ") VALUES (" + parameters + ")")) {
      for (int index = 0; index < metadata.insertColumns().size(); index++) {
        Object value = metadata.insertColumns().get(index).read(source);
        if (value instanceof byte[] bytes) {
          insert.setBytes(index + 1, bytes);
        } else if (value instanceof Instant instant) {
          insert.setObject(index + 1, OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
        } else {
          insert.setObject(index + 1, value);
        }
      }
      assertEquals(1, insert.executeUpdate());
    }
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static <T> List<String> names(EntityMetadata<T> metadata) {
    return metadata.insertColumns().stream().map(ColumnMetadata::columnName).toList();
  }

  record Money(BigDecimal value) {}

  enum Status {
    ACTIVE,
    DISABLED
  }

  record Address(String city, String postalCode) {}

  @Table(name = "Order", schema = "Bulk Schema")
  static class RichEntity {
    @Id
    @Column("Id")
    UUID id;

    @Column("Amount")
    Money amount;

    @Column("Status")
    Status status;

    @Column("Business Date")
    LocalDate businessDate;

    @Column("Created At")
    Instant createdAt;

    @Column("Payload")
    byte[] payload;

    @Embedded.Nullable(prefix = "addr_") Address address;

    RichEntity(
        UUID id,
        Money amount,
        Status status,
        LocalDate businessDate,
        Instant createdAt,
        byte[] payload,
        Address address) {
      this.id = id;
      this.amount = amount;
      this.status = status;
      this.businessDate = businessDate;
      this.createdAt = createdAt;
      this.payload = payload;
      this.address = address;
    }
  }

  @Table("generated_rows")
  record GeneratedEntity(@Id Long id, String value) {}

  @Table("PlainMixed")
  record PlainMixedEntity(@Id Long id, String value) {}

  @WritingConverter
  enum MoneyConverter implements Converter<Money, BigDecimal> {
    INSTANCE;

    @Override
    public BigDecimal convert(Money source) {
      return source.value();
    }
  }

  @WritingConverter
  enum StatusConverter implements Converter<Status, Integer> {
    INSTANCE;

    @Override
    public Integer convert(Status source) {
      return source == Status.ACTIVE ? 9 : 4;
    }
  }
}
