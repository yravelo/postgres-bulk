# Bulk insert multi-schema en pgJDBC

## Estado y alcance

**MS2: DONE (2026-08-24).** La fachada low-level pgJDBC puede dirigir un `bulkInsert` a un
`TableName` schema-qualified explícito por invocación. El lookup dinámico, Hibernate/JPA, Spring
Data JDBC, Boot, resolución de tenants, security baseline y publicación no forman parte de MS2.

La aplicación sigue eligiendo y autorizando el destino. PostgreSQL Bulk sólo valida su
compatibilidad estructural con el mapping y genera SQL qualified; nunca deriva schemas desde una
identidad de negocio.

## API y resolución

El camino nuevo es un único overload aditivo:

```java
BulkWriteResult result = operations.bulkInsert(
    connection,
    rows,
    BulkInsertOptions.ofBatchSize(1_000),
    TableName.of("tenant_a", "product")
);
```

No se añadió el overload corto `(Connection, Iterable, TableName)`: coexistiría con el overload
histórico `(Connection, Iterable, BulkInsertOptions)` y volvería ambigua en source una llamada
existente como `bulkInsert(connection, rows, null)`. El overload de cuatro argumentos conserva las
firmas existentes y obliga a hacer explícita la política de batching en el camino runtime.

Antes de consumir input, el coordinador resuelve exactamente una vez:

```java
TableName effectiveTarget = metadata.table().resolveRuntimeTarget(runtimeTarget);
```

Por ello el target debe incluir schema, conservar la tabla mapeada y, si el mapping ya declara
schema, coincidir también con éste. `null`, target no qualified y conflictos fallan antes de JDBC.

## Preparación y SQL por invocación

La preparación conserva una sola `EntityMetadata<T>`, un solo
`PreparedCopyCsvRowEncoder<T>` y la sentencia COPY histórica del mapping para llamadas sin target.
No clona metadata ni encoders y no introduce `Map<TableName, ...>` ni cache por schema.

Para una llamada target-aware no vacía, `CopySqlBuilder` recibe por separado la metadata —orden de
columnas— y el `TableName` efectivo —destino—. Cita schema, tabla y columnas como componentes
estructurales mediante `PostgresIdentifierQuoter`; no acepta SQL libre ni nombres qualified
preconcatenados. La sentencia se construye una vez en una variable local y la misma instancia se
reutiliza para todos los batches de esa invocación.

```text
prepare(metadata)
  -> metadata + encoder + COPY SQL default

bulkInsert(connection, rows, options, runtimeTarget)
  -> resolveRuntimeTarget
  -> obtener un iterator
  -> input vacío: BulkWriteResult.empty()
  -> CopySqlBuilder.insert(metadata, effectiveTarget) una vez
  -> uno o más COPY con el mismo SQL y encoder
```

El coste nuevo es `O(columnas)` por invocación target-aware no vacía y `O(1)` de estado retenido.
No depende de la cardinalidad de schemas. Las llamadas legacy continúan reutilizando la sentencia
preparada del mapping y mantienen su comportamiento y coste anteriores.

## Input vacío

El overload target-aware valida conexión, input, opciones y semántica del target, obtiene
exactamente un iterator y ejecuta el lookahead. Si está vacío devuelve `BulkWriteResult.empty()`
sin construir COPY SQL dinámico y sin interactuar con JDBC. Esta elección detecta un contrato de
destino inválido aunque no haya filas, pero conserva el no-op de transporte del camino histórico.

## Conexión, pool y transacción

Cada COPY usa la `Connection` caller-owned recibida. El motor no adquiere ni cierra conexiones, no
hace commit/rollback y no llama `setSchema`, `setAutoCommit`, `SET search_path` ni equivalentes.
El SQL qualified permite A→B en la misma conexión y también sobre el mismo backend físico
reutilizado por un pool sin restauración de estado.

Con `autoCommit=false`, A y B pueden confirmarse o revertirse como una sola transacción. Tras un
error PostgreSQL la transacción puede permanecer en `25P02` hasta que el caller haga rollback. Con
`autoCommit=true`, un batch anterior ya confirmado permanece aunque falle otro posterior. No hay
retry, compensación ni fallback al mapping/default.

Los fallos del productor o accessor conservan identidad y cancelan un COPY activo; SQLState y
causas del servidor permanecen accesibles. PostgreSQL decide `USAGE` y privilegios de tabla: el
quoting evita alterar la sintaxis, pero no sustituye autorización.

## Concurrencia y evidencia

Una instancia preparada es segura para destinos A/B concurrentes cuando cada invocación aporta su
propia conexión y los accessors son thread-safe. Todo el estado de target, SQL, iterator y conteos
es local a la llamada.

La suite MS2 prueba sobre PostgreSQL 15.18:

- aislamiento A/B con la misma metadata y fachada pública, secuencial y concurrente;
- multibatch con una única sentencia target-specific por invocación;
- backend físico único mediante `PGConnectionPoolDataSource`, sin cambios de `getSchema` ni
  `search_path`;
- commit y rollback cross-schema en la misma conexión;
- schemas, tabla y columnas quoted;
- mapping estático compatible y conflictos de schema/tabla/unqualified antes de JDBC;
- schema/tabla inexistentes y permiso denegado con SQLState preservado;
- fallo de batch tardío en autocommit y transacción manual;
- fallo de accessor, estado abortado y ownership caller-owned;
- ausencia de cache target-keyed y reutilización por identidad de metadata/columnas/encoder.

La matriz completa del proyecto y el smoke de PostgreSQL 18.4 se validan por los workflows Build y
Compatibility. MS3 extenderá la misma decisión a CTAS/JOIN del bulk lookup.

## Límites explícitos

MS2 no añade un overload target-aware de `findAllByBulkKey`, no propaga el target a repositories
Spring, no crea properties Boot y no cambia tags de observabilidad. Tampoco provisiona schemas,
consulta catálogos, resuelve tenants ni guarda identifiers de targets. La siguiente fase es
**MS3 — pgJDBC Multi-Schema Bulk Lookup**.
