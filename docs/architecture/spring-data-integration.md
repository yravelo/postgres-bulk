# Integración Spring Data

## Variantes

El proyecto ofrece fragments opt-in separados: `PostgresBulkRepository<T, ID>` para JPA y
`PostgresBulkJdbcRepository<T>` para JDBC. No comparten FQCN ni implementación y no debe extenderse
ambos desde el mismo repository. La guía JDBC explícita está en
[`spring-data-jdbc-repository-integration.md`](spring-data-jdbc-repository-integration.md).

## JPA

## Uso

El fragmento es opt-in y no reemplaza la base global de Spring Data:

```java
interface ProductRepository
    extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}
```

Con `postgres-bulk-spring-boot-starter`, Boot aporta automáticamente el bridge de metadata. Sin
Boot, la aplicación lo declara explícitamente:

```java
@Bean
JpaEntityMetadataResolver bulkMetadataResolver() {
    return JpaEntityMetadataResolver.caching(HibernateEntityMetadataResolver::new);
}
```

Después puede ejecutar `bulkInsert(items)`, `bulkInsert(items, options)` y
`findAllByBulkKey(keys, keyMetadata)`. Para un destino físico explícito por operación usa
`bulkInsert(target, items)`, `bulkInsert(items, options, target)` y
`findAllByBulkKey(keys, keyMetadata, target)`. La forma target-first del overload corto evita
ambigüedad source con el overload histórico de options. El fragmento se registra desde el JAR con
`spring.factories`; no requiere una implementación en el package de la aplicación ni una factory
custom.

La autoconfiguración no cambia el mecanismo de registro: `spring.factories` continúa cargando el
fragmento externo de Spring Data y `AutoConfiguration.imports` registra solamente el bean de
composición Boot. Un resolver del usuario gana por back-off. Con varias persistence units, el
bridge cachea por identidad de factory y `JpaContext` decide por tipo gestionado en cada llamada.

## Transacciones y conexión

Cada método declara `REQUIRED` read-write. Sin transacción exterior, el proxy repository crea una;
con una exterior participa en ella. Una exterior `readOnly` falla antes de obtener conexión. El
adapter obtiene la conexión subyacente del `Session` Hibernate mediante `doReturningWork`; DDL,
COPY, JOIN y native query quedan dentro de ese mismo scope físico. La conexión permanece prestada.

`REQUIRES_NEW` usa la conexión de la transacción interna. `NESTED` depende del transaction manager
y es **UNSUPPORTED** en la baseline: tanto el default como `nestedTransactionAllowed=true` fallan
porque `HibernateJpaDialect` no expone savepoints. Un iterable vacío se inspecciona una sola vez y no abre
conexión, aunque el interceptor pueda haber creado una transacción lógica lazy.

Si un participante REQUIRED falla y el outer captura la excepción, Spring lo deja rollback-only;
un error SQL mantiene además PostgreSQL en `25P02` y la completion produce
`UnexpectedRollbackException`. El interceptor estándar puede traducir fallos de argumento/estado
a `InvalidDataAccessApiUsageException` manteniendo el runtime original como causa; no existe
traducción manual de `BulkException`.

El target JPA es sólo un argumento local y se propaga por la misma conexión transaction-bound. No
activa APIs multitenancy Hibernate, no cambia schema/search path y no participa en caches. Una
misma transacción puede operar sobre varios schemas qualified; `NESTED` continúa unsupported.

## Persistence context

COPY evita el lifecycle ORM: no ejecuta callbacks, no genera IDs en objetos, no hace dirty
checking y no convierte los objetos insertados en managed. La integración nunca llama `flush()` ni
`clear()`. El lookup usa native query con flush mode `COMMIT`, por lo que el caller debe hacer flush
explícito si necesita que cambios JPA pendientes participen en la búsqueda. Los resultados del
lookup sí se materializan como entidades mediante JPA/Hibernate. En el camino target-aware, el SQL
native ya contiene el target qualified generado por pgJDBC: JPA no vuelve a resolver la tabla
default para leer rows. El target afecta sólo la root table; asociaciones y secondary tables
conservan las limitaciones del mapping. Véase
[`multi-schema-hibernate-jpa.md`](multi-schema-hibernate-jpa.md).

## Observabilidad de la llamada pública

Phase 12 envuelve dentro de `DefaultPostgresBulkOperations` exactamente una llamada completa a
insert/lookup mediante `ObservationRegistry`. El interceptor REQUIRED ya ha abierto la transacción
cuando empieza la observación; ésta termina antes del completion Spring. Por ello mide trabajo bulk
y no cambia el orden de proxies: success bulk puede preceder a un rollback exterior.

El helper es package-private, stateless por llamada y fail-open. Consume registries opcionales,
registra rows/batches únicamente desde el resultado exitoso y no toca conexión, metadata ni
iterables. La especificación está en [`observability.md`](observability.md).

## Lookup

La llamada recibe `BulkKeyMetadata<K>` explícita. Una key simple puede ser `String`, `UUID`, etc.;
una compuesta debe ser un record/value object con componentes ordenados. No se analizan nombres de
métodos derivados. Duplicados de input no duplican resultados, missing keys se omiten, nulls se
rechazan y no se garantiza orden.
