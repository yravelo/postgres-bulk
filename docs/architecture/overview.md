# Arquitectura

## Propósito y límites

La librería ofrecerá inserción y lookup masivos de alto rendimiento para entidades mapeadas a PostgreSQL. El caso principal es Spring Data JPA, pero el motor no dependerá de Spring. La primera versión soportará COPY CSV e Hibernate como proveedor de metadata.

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

## Componentes conceptuales (no clases comprometidas)

- Una fachada `BulkOperations<T>` expresa casos de uso y options/resultados estables.
- Descriptores inmutables expresan tabla, columnas insertables y claves ordenadas sin importar tipos Hibernate.
- El adapter Hibernate producira el SPI neutral; resolver, wiring y cache se materializaran solo cuando exista ese adapter.
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

La estrategia inicial será tabla temporal + COPY + JOIN. `VALUES` y `UNNEST` se reservan para comparación futura, no para el MVP. La tabla temporal y el SELECT deben ejecutarse sobre la misma conexión; mezclar un statement JDBC con una consulta JPA sólo será válido si se demuestra esa identidad.

## API core aceptada y lookup diferido

ADR-009 acepta un modelo operation-centric mediante `BulkOperations<T>`. Cada instancia queda ligada a un tipo lógico y publica `insert(Iterable<? extends T>)` más un overload con `BulkInsertOptions`. `Iterable` acepta `Collection` sin exigir acceso aleatorio, permite batching acotado y soporta productores de una pasada; no promete streaming perezoso ni paralelismo. `Stream` se excluye del primer contrato por ownership, cierre y semántica transaccional.

`batchSize` es la única opción de core: describe particionado lógico y se valida al construir `BulkInsertOptions`. Un input vacío es un no-op con resultado `(0, 0)`; input/options null y elementos null se rechazan según el contrato público. `BulkWriteResult` contiene sólo `affectedRows` y `batches`; duración pertenece a observabilidad. El core publica una única raíz unchecked, `BulkException`, y difiere subtipos hasta que existan fallos implementados y probados.

ADR-010 acepta diferir la firma pública de lookup hasta Phase 7. La futura API recibirá valores de clave, no entidades parciales, y deberá preservar type safety para claves simples y compuestas después de validar el modelo neutral de metadata de Phase 3. Orden, duplicados, nulls y forma de resultado permanecen abiertos; no existe todavía ningún tipo público de lookup.

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

`BulkKeyMetadata<K>` usa el mismo modelo de columna para una key object independiente de la entidad: un componente representa una key simple y varios componentes ordenados una compuesta. Es metadata SPI, no una operación lookup; duplicates, nulls, selección de key y orden de resultados siguen diferidos por ADR-010.

El adapter Hibernate debe entregar posteriormente nombres físicos ya resueltos, acceso FIELD/PROPERTY, converters, columnas de asociaciones e IDs embebidos. Reflection, si hace falta, se resuelve al producir el accessor y no se repite por consumidor/fila. Core no crea todavía resolver ni cache, no almacena nombres de propiedad y no modela nullability, IDs, generated flags o catalog.

No se generarán tipos a partir de clases Java. PostgreSQL `CREATE TABLE ... (LIKE source)` copia nombres y tipos físicos ([documentación CREATE TABLE](https://www.postgresql.org/docs/current/sql-createtable.html)); `CREATE TABLE AS ... WITH NO DATA` crea estructura a partir del resultado ([documentación CTAS](https://www.postgresql.org/docs/current/sql-createtableas.html)). ADR-006 mantiene `PROPOSED` cuál usar: un spike debe verificar domains, collations, generated/identity, columnas no-key, particiones y permisos. Todos los identificadores se modelan por partes y se citan; no se “sanitizan” perdiendo nombres válidos. PostgreSQL diferencia quoted case y limita identificadores a 63 bytes por defecto ([sintaxis léxica](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)).

## Transacciones

El core recibe un scope de conexión, no conoce `@Transactional`. Spring Data usa acceso compatible con la transacción actual; Spring documenta que `DataSourceUtils` devuelve la conexión vinculada cuando existe ([resource synchronization](https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html)). Nunca se llama `commit`, `rollback`, `close` físico ni se cambia `readOnly` sobre una conexión prestada.

Antes del lookup se definirá qué ocurre sin transacción: (a) requerir transacción, (b) crear un scope JDBC local, o (c) usar una temporal con `ON COMMIT PRESERVE ROWS` y cleanup. La opción debe asegurar una conexión única y cleanup; no se inferirá de autocommit accidentalmente.

## Observabilidad y seguridad

Un listener/observer de operación en la frontera de fachada permitirá integrar Micrometer en auto-configure sin que core dependa de él. Tags permitidos: operación, resultado y posiblemente tipo lógico acotado; nunca tabla arbitraria, ID, clave ni excepción completa. Logs no contienen filas ni valores. SQL dinámico sólo interpola identificadores producidos por metadata confiable y quoted por un componente central.
