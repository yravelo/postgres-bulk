# ADR-025: Metadata y conversiones de Spring Data JDBC

- **Estado:** PROPOSED
- **Fecha:** 2026-08-19

## Contexto

El motor necesita `EntityMetadata<T>` con nombres físicos, columnas ordenadas, accessors y el tipo
Java relacional de cada valor. Spring Data JDBC separa el tipo del domain model del tipo que acepta
el driver y permite custom conversions. Además, `SqlIdentifier` conserva quoting, embedded values
se aplanan y el tratamiento del ID puede variar por instancia.

Copiar fields por reflection, usar `value.getClass()` o `toString()` rompería nulls, converters y
el contrato de encoding ya aceptado. Tratar todas las propiedades persistentes como columnas de la
root mezclaría children de un aggregate con una operación de una sola tabla.

## Decisión propuesta

- Crear `SpringDataJdbcEntityMetadataResolver` dentro del nuevo adapter, construido con el
  `JdbcConverter` configurado por la aplicación.
- Obtener `RelationalPersistentEntity` y `RelationalPersistentProperty` desde
  `JdbcConverter.getMappingContext()`; no construir otro mapping context ni invocar
  `NamingStrategy` de nuevo.
- Recorrer sólo leaf paths almacenados en la tabla de la aggregate root. Rechazar children,
  collections y maps; aplanar embedded values con prefixes del metamodelo.
- Leer mediante `PersistentPropertyAccessor`/`PersistentPropertyPathAccessor` públicos, con
  traversal null-safe. No existe ni se inventará un `RelationalPersistentPropertyAccessor`.
- Para cada leaf usar `JdbcConverter.getColumnType(property)`,
  `getTargetSqlType(property)` y `writeJdbcValue(value, type, sqlType)`.
- Definir `ColumnMetadata.javaType()` como el tipo Java relacional declarado por
  `getColumnType`, nunca el domain type o el tipo runtime. El accessor devuelve el valor contenido
  en `JdbcValue` y debe ser compatible con ese tipo.
- Consumir custom conversions únicamente a través del `JdbcConverter` efectivo, incluidas las de
  dialecto y usuario.
- Obtener nombres de table/column desde `SqlIdentifier`; conservar componentes qualified. Probar
  equivalencia PostgreSQL antes de soportar plain identifiers que dependan de case folding.
- Incluir assigned IDs. Para database-generated IDs, derivar una variante que omita la columna sólo
  para llamadas homogéneas; no devolver ni sincronizar IDs. Rechazar initially `@Sequence`, IDs de
  callbacks, mezcla assigned/generated y aggregate roots versionadas.
- Cachear únicamente metadata inmutable dependiente de mapping context/converter + domain type;
  mantener fuera del cache la política de ID por llamada.

## Consecuencias

Null y non-null siguen el mismo encoder y los converters de la aplicación se aplican igual que en
Spring Data JDBC. Los tipos relacionales no soportados fallan explícitamente, sin serialización
genérica. El scope inicial soporta raíces simples, embedded/value objects de una columna y FKs
escalares, no graphs.

La diferencia entre quoted y unquoted identifiers no cabe en `TableName`/`ColumnMetadata`; J0 no
la usa como pretexto para cambiar core. La baseline queda limitada a identifiers cuyo componente
físico exacto sea compatible con el always-quote de pgJDBC, hasta que integration tests demuestren
una adaptación segura.

La detección de ID es per-instance mientras COPY usa una column list fija. La implementación debe
resolver esa tensión sin materializar el iterable completo y documentar que una inconsistencia
descubierta después de empezar puede dejar progreso fuera de transacción, igual que cualquier
fallo mid-stream actual.

## Alternativas evaluadas

| Alternativa | Resultado |
| --- | --- |
| Reflection directa de fields/getters | Rechazada: ignora access mode, paths y metadata |
| `ConversionService` genérico | Rechazada: pierde SQL type y conversiones de dialecto |
| `JdbcCustomConversions` construido por el adapter | Rechazada: diverge del converter real |
| Tipo runtime del primer valor | Rechazada: falla para null y batches heterogéneos |
| Fallback `toString()` | Rechazada: representación no contractual |
| Copiar SQL/type internals de Spring Data | Rechazada: SPI inestable |
| Añadir quoted flag a core en J0 | Rechazada: aún no se demostró necesidad general |
| Insertar ID siempre | Rechazada para generated IDs: impide default/identity |
| Omitir todo ID siempre | Rechazada: rompe assigned/UUID IDs |
| Tratar `@Version` como scalar normal | Rechazada: desincroniza optimistic locking |

## Evidencia requerida para ACCEPTED

- tests de tipo domain distinto del tipo relacional, con valor null y non-null;
- custom converter y converter-to-`JdbcValue` round-trip;
- scalar, inherited, embedded nullable/prefixed y nested characterization;
- `@Table` schema, quoted/default/plain identifier matrix en PostgreSQL;
- assigned ID, database-generated ID omitido/no sincronizado y rejection de mixed/sequence/version;
- scalar FK y `AggregateReference` characterization;
- rejection determinista de child collections y tipos relacionales sin encoder;
- cache aislado por mapping context/converter y seguro bajo concurrencia.

## Fuentes oficiales

- [`JdbcConverter` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/jdbc/core/convert/JdbcConverter.html)
- [Spring Data JDBC mapping](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/mapping.html)
- [Spring Data JDBC sequences](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/sequences.html)
- [`RelationalPersistentProperty` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/RelationalPersistentProperty.html)
- [`SqlIdentifier` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/sql/SqlIdentifier.html)

