# Arquitectura

## Propósito y límites

La librería ofrecerá inserción y lookup masivos de alto rendimiento para entidades mapeadas a PostgreSQL. El caso principal es Spring Data JPA, pero el motor no dependerá de Spring. La primera versión soportará COPY CSV e Hibernate como proveedor de metadata.

La línea Spring Data JDBC J0–J8 está técnicamente completa: añade un adapter hermano root-only,
fragmento público y starter propios sin introducir JPA/Hibernate en su grafo. Ambos adapters
reutilizan core/pgJDBC, pero preservan metadata, materialización y semántica de lifecycle distintas.

No es un ORM, no administra el ciclo de vida de entidades, no sustituye `saveAll`, no genera DDL permanente y no promete portabilidad a otras bases de datos. COPY no sincroniza automáticamente persistence context, callbacks JPA, auditoría ni IDs generados; estas diferencias deberán formar parte del contrato público.

## Arquitectura objetivo evaluada

La cadena lineal propuesta inicialmente se reemplaza por un grafo acíclico con adapters hermanos:

```text
                                  Application
                            ┌──────────┴──────────┐
                            │                     │
                    BulkOperations<T>   PostgresBulkRepository<T, ID>
                            │                     │
                            └──────────┬──────────┘
                                       │
                               Spring Data fragment
                              ┌─────────┴─────────┐
                              ▼                   ▼
                            core               pgjdbc ───→ PostgreSQL
                              ▲                   ▲
                              │ metadata port     │ connection scope
                              │                   │
                          hibernate          Spring transaction
                              ▲
                              │
                           JPA mapping

                 Boot auto-configuration wires the concrete adapters
```

En dependencias Maven: `pgjdbc → core`, `hibernate → core`, `spring-data → core + pgjdbc`, `autoconfigure → spring-data + pgjdbc + hibernate` y `starter → autoconfigure` más dependencias de experiencia de usuario. Hibernate no necesita pgJDBC y pgJDBC no necesita Hibernate; auto-configure compone los adapters concretos. Sin Boot, el consumidor los ensambla explícitamente.

Phase 10 materializa esa composición: Boot descubre `PostgresBulkAutoConfiguration` por
`AutoConfiguration.imports`, espera a Hibernate JPA, crea el resolver default antes de ensamblar
repositories y hace back-off ante uno del usuario. Sólo se activa con las clases necesarias, algún
`EntityManagerFactory` y `postgres-bulk.enabled=true`; no abre una conexión al arrancar. El
repository continúa siendo opt-in. El contrato completo está en
[`spring-boot-autoconfiguration.md`](spring-boot-autoconfiguration.md).

Phase 11 valida las fronteras bajo fallo sin introducir otra capa: caller/framework posee conexión
y transacción; pgJDBC posee `CopyIn`; lookup posee la temporal. PostgreSQL `25P02`, rollback-only,
REQUIRED/REQUIRES_NEW, NESTED unsupported, read-only, Hikari size 1 y pérdida de backend se fijan en
[`transactions-and-failures.md`](transactions-and-failures.md) y ADR-019.

## Componentes conceptuales (no clases comprometidas)

- Una fachada `BulkOperations<T>` expresa casos de uso y options/resultados estables.
- Descriptores inmutables expresan tabla, columnas insertables y claves ordenadas sin importar tipos Hibernate.
- `HibernateEntityMetadataResolver` produce el SPI neutral desde el mapping runtime y mantiene cache por persistence unit.
- Encoders convierten valores Java a una representación escalar definida; el escritor CSV aplica reglas de framing, quoting, NULL y UTF-8.
- Un acceso a conexión entrega una conexión física durante toda la operación; la integración Spring participa en la transacción actual.
- El executor pgJDBC posee COPY, SQL PostgreSQL, identificadores y tablas temporales.
- Una estrategia de lookup separa la decisión temp-table/COPY/JOIN de la fachada.
- Batching envuelve la ejecución y define consumo, conteo y fallos; no pertenece al protocolo COPY.

`BulkInsertService` y `BulkLookupService` sólo se materializarán si las implementaciones divergen lo suficiente para justificar dos servicios. En el core se prefieren pocos puertos cohesionados a interfaces uno-a-uno sin más de una implementación plausible.

## Flujo bulk insert

```text
caller → facade → validate options/input → obtain entity metadata
       → partition iterator → acquire transaction-aware connection once
       → build quoted COPY SQL once → encode + frame CSV rows
       → pgJDBC COPY per batch → accumulate server row counts → result
```

Phase 6 materializa este flujo como `PostgresBulkInserter<T>`, un motor package-private
preparado que recibe una conexión caller-owned. Obtiene un solo iterator, usa lookahead de
una fila y alimenta cada COPY directamente sin listas por batch. SQL y encoders se preparan
una vez por instancia. La misma conexión vive durante todos los batches; el motor no la
cierra, reconfigura, confirma ni revierte. Un fallo cancela el COPY activo y conserva la
causa. Con `autoCommit=false` el caller puede confirmar/revertir toda la operación; con
`autoCommit=true` los COPY previos pueden quedar confirmados. El contrato completo está en
[`bulk-insert.md`](bulk-insert.md).

## Flujo bulk lookup

```text
typed keys → validate/deduplicate policy → resolve key metadata
           → acquire one transaction-aware connection
           → create temporary relation from physical table definition
           → COPY key tuples → quoted JOIN against qualified real table
           → map rows through JPA/Hibernate boundary → cleanup/result
```

Phase 7 materializa este flujo como `TemporaryTableBulkLookup<K>`, un motor package-private.
Recibe valores de key descritos por `BulkKeyMetadata<K>` y un callback de consumo acotado;
no devuelve recursos JDBC vivos. CTAS proyecta sólo las key columns y deja que PostgreSQL
derive domain, typmod y collation; un único COPY consume el iterable directamente. El JOIN
deduplica input mediante `SELECT DISTINCT`, omite missing keys, conserva todas las filas
target duplicadas y no promete orden.

Para input no vacío exige `autoCommit=false`. CREATE, COPY, SELECT/callback y DROP usan la
misma conexión prestada; el motor no modifica estado, commit/rollback ni close. El DROP
explícito se complementa con `ON COMMIT DROP`; tras un fallo que aborte la transacción el
caller debe hacer rollback. `VALUES`, `UNNEST`, índice y `ANALYZE` quedan para comparación
futura. El contrato completo está en [`bulk-lookup.md`](bulk-lookup.md).

## API core aceptada y lookup diferido

ADR-009 acepta un modelo operation-centric mediante `BulkOperations<T>`. Cada instancia queda ligada a un tipo lógico y publica `bulkInsert(Iterable<? extends T>)` más un overload con `BulkInsertOptions`. `Iterable` acepta `Collection` sin exigir acceso aleatorio, permite batching acotado y soporta productores de una pasada; no promete streaming perezoso ni paralelismo. `Stream` se excluye del primer contrato por ownership, cierre y semántica transaccional.

`batchSize` es la única opción de core: describe particionado lógico y se valida al construir `BulkInsertOptions`. Un input vacío es un no-op con resultado `(0, 0)`; input/options null y elementos null se rechazan según el contrato público. `BulkWriteResult` contiene sólo `affectedRows` y `batches`; duración pertenece a observabilidad. El core publica una única raíz unchecked, `BulkException`, y difiere subtipos hasta que existan fallos implementados y probados.

ADR-017 cierra la firma pública de lookup en el fragmento Spring Data: recibe valores de clave y
`BulkKeyMetadata<K>`, no entidades parciales, y devuelve `List<T>` materializada por JPA mientras
la temporal sigue visible. Duplicados, nulls, missing keys y orden conservan ADR-015.

## Serialización COPY

Phase 4 materializa CSV como única implementación inicial dentro de
`postgres-bulk-pgjdbc`. La cadena interna separa encoder tipado, representación explícita
NULL/texto, framing de campo y escritura de fila. Los encoders se resuelven una sola vez
por columna a partir de `ColumnMetadata.javaType()`; nunca por el valor runtime. El writer
escribe incrementalmente a un `Appendable` sin poseerlo ni construir obligatoriamente la
fila completa.

El dialecto seleccionado usa delimiter `,`, quote/escape `"`, NULL `\N`, UTF-8 y `\n`
como terminador. Por tanto NULL se emite `\N`, empty se emite `""` y el texto literal
`\N` se emite `"\N"`. CR, LF, delimiter y quote fuerzan quoting, y una quote interna se
duplica. Las reglas provienen del contrato oficial de
[COPY](https://www.postgresql.org/docs/current/sql-copy.html) y se detallan en
[`copy-encoding.md`](copy-encoding.md). Phase 5 refleja exactamente estas opciones en la
sentencia y convierte los caracteres a bytes UTF-8.

Los built-ins cubren strings/caracteres, numéricos integrales y arbitrarios, floating
point incluidos los valores especiales de PostgreSQL, boolean, UUID, temporales ISO,
enum por `name()` y `byte[]` hexadecimal. No existe fallback a `Object.toString()` ni SPI
pública de custom encoders. TEXT/BINARY, JSON/JSONB, arrays y custom types continúan
diferidos.

## Ejecución COPY pgJDBC

Phase 5 materializa una primitive interna con el contrato
`Connection + COPY SQL + Writer callback -> long`. SQL se construye aparte desde
`EntityMetadata`: schema, tabla y columnas se citan siempre por componente y el orden de
columnas es el mismo que consume el encoder preparado.

El executor obtiene `PGConnection` únicamente mediante `Connection.unwrap`, inicia un
`CopyIn`, transmite caracteres incrementalmente por `OutputStreamWriter(UTF_8)` y un
`PGCopyOutputStream` de 64 KiB, y devuelve el conteo de `endCopy()`. En fallo cancela la
operación activa, conserva la causa original y añade como suppressed cualquier error de
cleanup. No cierra ni reconfigura la conexión y nunca hace commit o rollback. El contrato
completo y su evidencia se documentan en
[`pgjdbc-copy-execution.md`](pgjdbc-copy-execution.md).

Phase 6 refina el error del productor: después de cancelar un COPY activo, las excepciones
runtime y `Error` se relanzan sin envolver para conservar los contratos de argumentos y
accessors. Los fallos checked JDBC/I/O conservan `CopyExecutionException` y su cause chain.

## Metadata y tablas temporales

ADR-011 acepta metadata neutral y ejecutable:

```text
T
↓
EntityMetadata<T> → TableName(schema?, table)
↓
ordered ColumnMetadata<T>
↓
javaType + pre-resolved Function<T, ?>
```

`EntityMetadata<T>` contiene solamente la lista final ordenada de columnas insertables. Cada `ColumnMetadata<T>` conserva el nombre físico exacto, el tipo Java declarado —también cuando el valor es null— y un accessor prerresuelto. Una asociación o componente embedded puede producir varias columnas porque core nunca asume `field == column`. Las colecciones se copian defensivamente, son no modificables y rechazan nombres físicos duplicados exactos.

`BulkKeyMetadata<K>` usa el mismo modelo de columna para una key object independiente de la entidad: un componente representa una key simple y varios componentes ordenados una compuesta. Es metadata SPI y Phase 9 la consume desde la operación pública del fragmento Spring Data. El lookup rechaza nulls, deduplica relacionalmente antes del JOIN, omite missing keys y no promete orden.

Phase 8 materializa `HibernateEntityMetadataResolver`: entrega nombres físicos ya
resueltos, acceso FIELD/PROPERTY, converters, foreign keys de asociaciones e IDs embebidos.
Usa accessors del metamodelo, sin reflection por fila, y cache concurrente por
`EntityManagerFactory`. Sólo el módulo Hibernate conoce sus SPI/internals. Core no almacena
nombres de propiedad ni modela nullability, IDs, generated flags o catalog. El subset y
los fallos están en [`hibernate-metadata.md`](hibernate-metadata.md).

No se generan tipos a partir de clases Java. ADR-006/015 aceptan `CREATE TEMP TABLE AS
SELECT key_columns ... WITH NO DATA`: PostgreSQL 15.18 preservó domain, typmod y collation
en referencias directas y no copió propiedades innecesarias. `LIKE` se descartó porque
incluye todas las columnas y NOT NULL. Todos los identificadores se modelan por partes y
se citan; no se “sanitizan” perdiendo nombres válidos. PostgreSQL diferencia quoted case y
limita identificadores a 63 bytes por defecto
([sintaxis léxica](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)).

## Transacciones

El core recibe un scope de conexión, no conoce `@Transactional`. Spring Data usa acceso compatible con la transacción actual; Spring documenta que `DataSourceUtils` devuelve la conexión vinculada cuando existe ([resource synchronization](https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html)). Nunca se llama `commit`, `rollback`, `close` físico ni se cambia `readOnly` sobre una conexión prestada.

Lookup exige una transacción manual ya iniciada por el caller (`autoCommit=false`) y falla
antes de DDL si no existe ese scope. También falla en transacciones PostgreSQL read-only
porque CTAS no está permitido. No crea una transacción o savepoint interno. El adapter de
Phase 9 declara `REQUIRED`, rechaza explícitamente scopes Spring read-only y obtiene la
conexión prestada del `Session` sin alterar su estado.

## Observabilidad y seguridad

Phase 12 integra Micrometer exclusivamente en Spring Data/autoconfigure. Una observación
`postgres.bulk.operation` rodea cada llamada pública y puede producir duration/tracing; counters
finales publican rows y batches sólo en success. Los únicos tags propios son operation/outcome y el
tag estándar de error se normaliza a un conjunto bounded. No existen tags de entidad, repository,
tabla, SQLState o excepción concreta, y nunca se copian filas, keys, SQL ni valores. El contrato
completo vive en [`observability.md`](observability.md).

## Cierre de rendimiento Spring Data JDBC

J8 no modifica la arquitectura productiva. El módulo JMH no publicable depende de ambos adapters
sólo para comparar `saveAll`, JDBC batch, API pública y low-level COPY contra la misma tabla. En el
host medido, la API pública JDBC redujo el tiempo frente a `saveAll`, no mostró overhead consistente
frente al engine y no aportó evidencia para cambiar el default 1.000 ni añadir lookup adaptativo.
La evidencia, limitaciones y raw data están en
[`j8-spring-data-jdbc.md`](../benchmarks/j8-spring-data-jdbc.md).

Runtime multi-schema/schema-per-tenant, security baseline, publicación y Boot/Data 4 permanecen
deferidos; no forman parte de la arquitectura implementada.
