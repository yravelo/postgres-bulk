# Metadata Spring Data JDBC

## Alcance J1

`postgres-bulk-spring-data-jdbc` traduce el mapping efectivo de una aggregate root a
`EntityMetadata<T>`. J1 termina en metadata y characterization: no contiene bulk insert público,
lookup, repository fragment, acceso transaccional, auto-configuración Boot, starter ni lifecycle
de aggregates.

La frontera productiva exacta es:

```text
postgres-bulk-spring-data-jdbc
  -> postgres-bulk-core
  -> spring-data-jdbc
       -> spring-data-relational / spring-data-commons / Spring Framework
```

No hay dependencia productiva en pgJDBC, JPA, Hibernate, Spring Data JPA, Spring Boot, Actuator o
Testcontainers, ni dependencia Micrometer declarada por el módulo. `spring-context` conserva su
dependencia transitiva normal en Micrometer Observation. PostgreSQL, JUnit y Testcontainers son
test-only. Maven Enforcer impide incorporar JPA/Hibernate/Boot; el dependency-tree audit demuestra
el scope test-only de pgJDBC.

## Resolver y APIs públicas usadas

`SpringDataJdbcEntityMetadataResolver` recibe el `JdbcConverter` y las mismas
`CustomConversions` configuradas por la aplicación. No crea un mapping context paralelo ni
recalcula `NamingStrategy`.

El resolver usa sólo API pública 3.5:

- `JdbcConverter`: mapping context, tipo/SQL type y `writeJdbcValue`;
- `CustomConversions`: target estático de converters registrados;
- `RelationalMappingContext`, `RelationalPersistentEntity` y
  `RelationalPersistentProperty`: entidades, propiedades y paths;
- `AggregatePath`: columna física, writability y clasificación root/child;
- `PersistentPropertyPathAccessor` con `GetNulls.EARLY_RETURN`: lectura prerresuelta y null-safe;
- `SqlIdentifier.getReference()`: componentes físicos sin delimitadores;
- `IdValueSource.forInstance`: política assigned/generated por instancia.

Fuentes oficiales: [mapping Spring Data JDBC
3.5](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/mapping.html),
[`JdbcConverter`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/jdbc/core/convert/JdbcConverter.html),
[`RelationalPersistentProperty`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/RelationalPersistentProperty.html),
[`AggregatePath`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/AggregatePath.html),
[`SqlIdentifier`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/sql/SqlIdentifier.html)
y [sequences](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/sequences.html).

## Flujo de resolución

1. Localiza el `RelationalPersistentEntity` en el mapping context efectivo.
2. Separa schema/table desde `getQualifiedTableName()` y conserva el texto de cada referencia.
3. Recorre propiedades writable de la root en orden del metamodelo.
4. Aplana `@Embedded` recursivamente mediante persistent paths; no usa reflection manual.
5. Rechaza children, colecciones, maps, version y sequences.
6. Obtiene el nombre de leaf con `AggregatePath.getColumnInfo().name().getReference()`.
7. Fija un tipo Java relacional estático y crea un accessor null-safe.
8. Construye una variante con ID y, si existe ID, otra sin ID.

Los accessors no conservan estado por operación. Cada lectura obtiene el accessor oficial de la
entidad para un path ya resuelto; un parent embedded null produce null en todas sus leaf columns.
Los fallos incluyen entity/property/column cuando es seguro, nunca el valor, y preservan la causa.

## Conversión y tipo relacional

`ColumnMetadata.javaType()` describe siempre el valor que recibe el encoder, incluso cuando el
valor runtime es null. El adapter no usa `value.getClass()`, `toString()` ni inferencia desde la
primera fila.

Para String/Character, numéricos, boolean, UUID, Java Time, `java.sql` temporals y `byte[]`, que ya
son tipos nativos del engine, el accessor conserva el valor y tipo domain. Esta decisión evita una
peculiaridad demostrada de Spring Data 3.5: `getColumnType` y converters generales pueden llevar
varios Java Time a `Timestamp`; para `LocalTime` esa conversión incorpora la fecha actual y no es
una representación estable de COPY.

Para enum y value objects no nativos, `CustomConversions` fija el write target y
`JdbcConverter.writeJdbcValue` produce el valor. Los tests demuestran `Money -> BigDecimal`, enum
custom -> Integer, enum default -> String y `AggregateReference<Root, UUID> -> UUID`. Null usa el
mismo tipo declarado sin ejecutar inference.

Un converter cuyo target es directamente `JdbcValue` queda `UNSUPPORTED`: el wrapper permite
decidir SQL type en runtime, pero la API pública no revela el tipo Java del valor interno para un
domain null. Falla en resolución en lugar de declarar `JdbcValue.class` o adivinar.

Un tipo relacional no soportado por `ValueEncoderRegistry` no se duplica como gate en este módulo:
la metadata puede representarlo y la preparación del encoder de J2 fallará determinísticamente.
El test con `Timestamp` fija ese reparto de responsabilidad.

## IDs

`resolve(Class<T>)` es el puerto estable por tipo y conserva el ID. `resolveFor(T)` aplica
`IdValueSource.forInstance`:

| Estado | Metadata | Sincronización |
| --- | --- | --- |
| ID Long/UUID proporcionado | incluye ID | ninguna necesaria |
| ID null o primitive zero clasificado generated | omite ID | no devuelve ni asigna ID |
| Sin propiedad ID | metadata base | ninguna |
| `@Sequence` | rechazo | fuera de scope |
| `@Version` | rechazo | fuera de scope |

Si omitir el ID deja cero columnas, se rechaza porque core requiere una fila no vacía. J2 consume
esta capability: por cada elemento, en la misma pasada del iterable, llama `resolveFor`; si la
identidad de metadata cambia respecto de la primera fila, rechaza mixed assigned/generated. No
materializa el dataset. Un ID generado por callback también queda fuera porque el bulk path no
ejecuta callbacks.

## Cache, concurrencia y schema futuro

La cache es `ConcurrentHashMap<Class<?>, ResolvedMapping<?>>` dentro de cada resolver. Su key es la
clase y su lifecycle es el del resolver/converter/mapping context; distintos application contexts,
naming strategies o conversion sets no comparten entradas. Cada entrada guarda:

- tabla estructural resuelta;
- columnas/path accessors inmutables;
- variante assigned-ID;
- variante generated-ID.

No existe cache global ni mutable per-operation. Tests concurrentes prueban identity reuse dentro
de una instancia y aislamiento entre instancias.

El schema anotado es parte de la tabla estructural actual, pero no se mezcla con un tenant runtime
ni se usa como key global. Una futura capa schema-per-tenant podrá combinar las mismas columnas
estructurales con un `TableName` derivado por operación, sin invalidar el cache ni incorporar el
tenant al metamodelo J1. J1 no implementa ese override.

## Identifiers y PostgreSQL

`SqlIdentifier.getReference()` devuelve contenido sin delimitadores. Core conserva ese texto y el
motor pgJDBC cita siempre cada componente. PostgreSQL Testcontainers demuestra:

- schema/table/columns quoted, mixed-case, con espacios y palabra reservada: `SUPPORTED`;
- identifiers exactos lowercase: `SUPPORTED` aunque se citen siempre;
- table sin schema: `SUPPORTED`, usa el `search_path` de la conexión;
- schema explícito y schema quoted: `SUPPORTED`;
- un nombre plain mixed-case que PostgreSQL pliega a lowercase: `UNSUPPORTED`.

El último caso es observable: `CREATE TABLE PlainMixed` crea `plainmixed`, mientras metadata
conserva `PlainMixed` y el boundary genera `"PlainMixed"`, que PostgreSQL rechaza con `42P01`.
Spring Data 3.5/core no conservan suficiente semántica quoted/plain para corregirlo sin cambiar un
boundary general. J1 no parchea el quoter.

## Matriz de mapping J1

| Mapping | Estado | Evidencia/comportamiento |
| --- | --- | --- |
| implicit/explicit table y column | SUPPORTED | unit tests |
| custom `NamingStrategy` | SUPPORTED si el nombre físico exacto tolera always-quote | unit + limitación PostgreSQL |
| schema/no schema/quoted schema | SUPPORTED | PostgreSQL |
| scalar String/numeric/boolean/UUID/byte[] | SUPPORTED | type/value tests y PostgreSQL representativo |
| LocalDate/LocalTime/LocalDateTime/offset/Instant | SUPPORTED como tipo nativo del engine | unit + LocalDate/Instant PostgreSQL |
| enum default/custom | SUPPORTED | String/Integer y PostgreSQL custom |
| single-column value object converter | SUPPORTED | `Money -> BigDecimal` |
| converter a `JdbcValue` | UNSUPPORTED | tipo interno no estático para null |
| null scalar/converter/embedded/reference | SUPPORTED | null-safe tests |
| embedded simple/prefixed/nested | SUPPORTED | unit; nested nullable |
| record/immutable/property access | SUPPORTED para lectura | accessor oficial |
| inherited scalar properties | SUPPORTED; no implica ORM inheritance | unit test |
| scalar FK | SUPPORTED | tratado como scalar |
| `AggregateReference<T, ID>` | SUPPORTED para ID scalar | conversion oficial a UUID probada |
| child entity/collection/set/map | UNSUPPORTED | rechazo root-only |
| assigned Long/UUID ID | SUPPORTED | incluido |
| generated numeric ID | PARTIAL, capability J1 | omitido; PostgreSQL default; no sync |
| mixed ID batch | UNSUPPORTED | J2 rechaza ambas direcciones one-based durante la pasada |
| sequence/callback ID | UNSUPPORTED | rechazo/no callbacks |
| `@Version` | UNSUPPORTED | rechazo explícito |
| tipo no encodable | gate del encoder | preparación pgJDBC falla antes de consumir input |
| plain mixed-case dependiente de folding | UNSUPPORTED | PostgreSQL `42P01` |

## PostgreSQL characterization

`SpringDataJdbcEntityMetadataResolverIT` no llama internals pgJDBC. Construye un INSERT JDBC
test-only desde la metadata para demostrar que nombres, orden y valores relacionales corresponden
a columnas reales. Cubre quoted schema/reserved table, converter value object, enum custom,
LocalDate, Instant, UUID, bytea, embedded y selección assigned/generated. El container no se marca
`disabledWithoutDocker`: si Docker está disponible corre; si el entorno obligatorio no puede
arrancarlo, `verify` falla visiblemente.

## Riesgos diferidos

- J3 decidirá materialización de lookup, sin usar internals.
- J4 congelará repository fragment/API de operaciones.
- J5/J6 cerrarán transacciones, coexistencia y Boot.
- El lane mínimo Spring Data JDBC 3.5.0 y la matrix completa pertenecen a fases de compatibilidad.
- Schema runtime por tenant requiere un boundary explícito por operación, no otra cache global.

## Evidencia de consumo J2

`DefaultSpringDataJdbcBulkOperations` usa estas variantes sin duplicar conversions ni accessors.
La primera fila fija metadata y prepara `PostgresBulkJdbcOperations`; cada fila posterior se
resuelve en el mismo iterator. PostgreSQL real confirma converters, enum default/custom,
embedded/nested null, `AggregateReference`, IDs assigned/generated, schema y quoted names. Mixed
ID se detecta durante la pasada y se revierte mediante la transacción JDBC obligatoria definida en
ADR-026.
