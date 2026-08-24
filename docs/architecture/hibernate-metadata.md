# Adapter de metadata Hibernate

## Propósito y frontera

`postgres-bulk-hibernate` convierte el mapping runtime de Hibernate 6.6 en
`EntityMetadata<T>`. Su única API pública es:

```java
var resolver = new HibernateEntityMetadataResolver(entityManagerFactory);
EntityMetadata<Order> metadata = resolver.resolve(Order.class);
```

El resolver no recibe `EntityManager`/`Session`, no abre conexiones y no conoce pgJDBC,
COPY, Spring, repositorios, claves lookup ni tablas temporales. La instancia pertenece a
una persistence unit; su cache concurrente resuelve cada clase una vez y nunca cruza
`EntityManagerFactory`.

MS4 no cambia esa frontera: un `TableName` runtime nunca entra al resolver ni a sus cache keys. El
fragmento JPA conserva esta metadata estructural y propaga el target explícito por separado al
engine pgJDBC. No clona metadata por schema ni usa infraestructura multitenancy Hibernate.

## Metamodelo usado

La entrada estándar se desenvuelve a `SessionFactoryImplementor`. Desde
`MappingMetamodel` se localiza `EntityPersister`; `EntityMappingType` aporta tabla,
identificador, atributos, discriminator y tablas mutables. `AttributeMapping` aporta el
`PropertyAccess` pre-resuelto. `SelectableMapping` decide nombre físico, tabla, fórmula e
insertability. `JdbcMapping`, su converter y Java types proyectan el valor relacional.

Hibernate documenta 6.6 como línea compatible con Jakarta Persistence 3.1, Java
11/17/21/25 y Spring Boot 3.4–3.5, actualmente en soporte limitado
([release 6.6](https://hibernate.org/orm/releases/6.6/)). Las interfaces runtime son SPI
([Javadocs 6.6](https://docs.hibernate.org/orm/6.6/javadocs/)); no aparecen en firmas
públicas. `ToOneAttributeMapping` es el único tipo `internal` consumido y queda encerrado
en una clase.

## Tabla y columnas

La tabla procede de `getMappedTableDetails().getTableName()`. `QualifiedNameParser`
separa schema y objeto, y `Identifier.getText()` elimina delimitadores sin normalizar
case. Así se respetan nombres implícitos, physical naming strategies, schemas e
identificadores quoted. Catálogos no caben en `TableName` y se rechazan.

El orden del descriptor es identificador seguido por el encounter order del metamodelo
Hibernate, aplanado por selectables. Es estable dentro de una metadata cacheada.

| Mapping | Resultado bulk insert |
|---|---|
| ID asignado / `@EmbeddedId` | incluido |
| ID identity/sequence/otro generado simple | omitido |
| basic insertable | incluido |
| `insertable=false` | omitido |
| `updatable=false`, insertable | incluido |
| `@Version` | incluido; usa el valor de entidad, sin callback ORM |
| formula / colección | omitida |
| generated-on-insert | omitido |
| `@ColumnDefault` insertable | incluido; null sobrescribe el default |
| discriminator/herencia | error |
| secondary/multi-table/soft delete | error |

## Matriz probada

| # | Caso | Resultado |
|---:|---|---|
| 1 | entidad básica | tabla, columnas y valores |
| 2 | `@Table` explícita | nombre físico |
| 3 | schema custom | componentes separados |
| 4 | implicit naming | nombre runtime |
| 5 | physical naming strategy | nombre transformado |
| 6 | tabla quoted | contenido sin delimitadores |
| 7 | columna quoted | contenido sin delimitadores |
| 8 | PROPERTY access | getter pre-resuelto |
| 9 | FIELD access | field pre-resuelto |
| 10 | mapped superclass | ID heredado |
| 11 | `@Embedded` | flatten y valores |
| 12 | `@EmbeddedId` | columnas y valores de key |
| 13 | ID asignado | incluido |
| 14 | ID identity/sequence | omitido |
| 15 | `insertable=false` | omitido |
| 16 | `updatable=false` insertable | incluido |
| 17 | `ManyToOne` simple | valor FK, no entidad |
| 18 | asociación nullable | null FK |
| 19 | converter String | valor relacional |
| 20 | enum STRING | `String` |
| 21 | enum ORDINAL | `Integer` |
| 22 | converter enum custom | código relacional |
| 23 | fórmula | omitida |
| 24 | generated/default | generated omitido; default insertable incluido |
| 25 | secondary/multi-table | error explícito |
| 26 | cache | misma instancia por clase/resolver |
| 27 | concurrencia | 100 resoluciones sobre ocho threads |
| 28 | consumo PostgreSQL | insert JDBC real con metadata resuelta |

## Lectura y conversión

FIELD, PROPERTY, acceso mixto válido y mapped superclass usan el accessor que Hibernate
ya resolvió durante bootstrap; no se buscan fields/methods por fila. Un embeddable compone
accessors null-safe y produce una columna por selectable. `@EmbeddedId` se trata igual
desde el accessor de identificador.

Un to-one soportado lee la asociación y proyecta su primary key, nunca la entidad. Para
un `HibernateProxy` usa el identificador interno sin inicializarlo; una asociación null
produce null en todas sus columnas. Joins a natural key, join table y formas que no
alinean los componentes FK/PK fallan al resolver.

El tipo de `ColumnMetadata` es el tipo relacional preferido por el `JdbcType`. Un
`BasicValueConverter` se aplica antes de exponer el valor. Enum STRING produce `String`,
enum ORDINAL produce `Integer` y converters JPA producen su tipo relacional. Null atraviesa
la misma política de converter de Hibernate; una excepción se propaga como
`PersistenceException` con causa preservada.

## Errores y compatibilidad

Una clase no entidad, un mapping no representable o un fallo de introspección produce
`BulkException` con clase y motivo. Errores de lectura/converter mantienen la excepción
runtime original de Hibernate/JPA. No se añade todavía `BulkMetadataException`: no existe
una recuperación diferente que justifique otro tipo público.

El suite levanta un `SessionFactory` real y PostgreSQL con Testcontainers. Además de assertar
nombres, tipos y valores, inserta mediante JDBC los valores resueltos para probar que
converters/enums son realmente relacionales. Phase 13 validó 6.6.15.Final, 6.6.53.Final y
6.6.55.Final; `ToOneAttributeMapping` permanece compatible en los límites soportados.

Phase 14 confirmó end-to-end que Hibernate puede preferir `java.sql.Date` para un atributo
`LocalDate`. El encoder COPY acepta esa forma relacional y la serializa como fecha ISO; la API de
entidad y el modelo neutral no cambian.

## Resolución MS4

Un mismo `EntityMetadata<T>` resuelto por Hibernate soporta targets A/B compatibles. PostgreSQL
15.18 valida desde el repository JPA converters, enum, embedded y proyección FK `ManyToOne`, además
de IDs asignados/generados y schema estático compatible/conflictivo. El target sólo redirige la
tabla root: asociaciones y secondary/multi-table mappings no adquieren semántica dinámica.
