# Mapping support

This table describes the tested Hibernate 6.6 adapter used by bulk insert. “Omitted” means the
column does not participate in COPY; it does not mean ORM behavior is emulated.

| Feature | Status | Notes |
|---|---|---|
| Basic field | SUPPORTED | Requires a supported relational Java type. |
| FIELD access | SUPPORTED | Uses Hibernate's resolved accessor. |
| PROPERTY access | SUPPORTED | Uses Hibernate's resolved getter. |
| Mapped superclass | SUPPORTED | Inherited mappings are flattened. |
| `@Embedded` | SUPPORTED | Components are flattened to ordered columns. |
| `@EmbeddedId` | SUPPORTED | Assigned embedded identifier columns are included. |
| Assigned simple ID | SUPPORTED | Included in COPY. |
| Identity/sequence/generated simple ID | PARTIAL | Generated column is omitted; generated value is not returned or populated. |
| `@ManyToOne` insertable FK | SUPPORTED | Primary-key join columns only; proxy IDs do not require initialization. |
| Nullable `@ManyToOne` | SUPPORTED | Null association produces null FK components. |
| Cascade persist | UNSUPPORTED | Associated entities are never persisted by COPY. |
| Natural-key association / join table | UNSUPPORTED | Resolver rejects associations not aligned with target primary-key columns. |
| `AttributeConverter<X,Y>` | SUPPORTED | Hibernate conversion runs first; `ColumnMetadata.javaType()` exposes relational `Y`. |
| Enum `STRING` | SUPPORTED | Hibernate relational representation is `String`. |
| Enum `ORDINAL` | SUPPORTED | Hibernate relational representation is normalized to `Integer`. |
| Custom enum converter | SUPPORTED | Supported when its relational Java type has a COPY encoder. |
| `insertable=false` | SUPPORTED | Selectable is omitted. |
| `updatable=false`, insertable | SUPPORTED | Column is included for insert. |
| `@Version` | PARTIAL | Insertable value from the object is copied; no ORM callback initializes it. |
| Formula | OMITTED | Formula selectables are not copied. |
| Collection/collection table | UNSUPPORTED | Collection attributes are omitted; collection-table writes do not occur. |
| Generated-on-insert attribute | OMITTED | Database is allowed to generate it; value is not returned. |
| `@ColumnDefault` on insertable column | PARTIAL | Column remains included; null explicitly writes NULL and does not invoke the default. |
| Secondary table | UNSUPPORTED | Multi-table entity mappings fail during metadata resolution. |
| JOINED/TABLE_PER_CLASS/SINGLE_TABLE inheritance | UNSUPPORTED | Inheritance/discriminator mappings are rejected. |
| Hibernate soft delete mapping | UNSUPPORTED | Required insert literal is not represented. |
| Catalog-qualified table | UNSUPPORTED | Core table metadata models optional schema and table only. |
| `@IdClass` | NOT VALIDATED | Not part of the tested support matrix. |
| JSON/JSONB, arrays, arbitrary custom type | UNSUPPORTED | No public custom encoder SPI exists in this release line. |
| Hibernate 7 / Spring Boot 4 | UNSUPPORTED | Requires another adapter/artifact generation. |

## Supported relational Java types

Built-in COPY encoding covers strings/characters, integral and decimal numbers, floating point
including PostgreSQL special values, booleans, UUID, `LocalDate`, `java.sql.Date`, `LocalTime`,
`LocalDateTime`, `OffsetDateTime`, `OffsetTime`, `Instant`, declared enums and `byte[]`.

For converted attributes and enums, Hibernate decides the relational representation. The COPY
encoder consumes that representation; it does not apply `AttributeConverter` itself and does not
fall back to `Object.toString()`.

## Lifecycle limitations

`@PrePersist`, entity listeners and Hibernate events do not run. Generated values are not copied
back to objects. Associations do not cascade. The persistence context is not synchronized. These
are deliberate differences from `persist`/`saveAll`, not mapping bugs.

The implementation-level matrix and evidence are in
[Hibernate metadata](../architecture/hibernate-metadata.md).
