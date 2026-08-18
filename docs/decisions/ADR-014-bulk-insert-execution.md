# ADR-014: Coordinación interna de bulk insert sobre Connection prestada

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 6 debe componer metadata, encoding y el executor COPY de Phase 5 con la política de
batching de core. La API `BulkOperations<T>` ya expresa el caso de uso, pero no recibe una
`Connection`; decidir ahora cómo adquirirla condicionaría plain JDBC, pools y la futura
participación en transacciones Spring.

También deben cerrarse consumo single-pass, memoria, conteos, error en batches posteriores
y la relación entre límites de batch y de transacción. PostgreSQL documenta que un COPY
exitoso devuelve `COPY count`, donde `count` es el número de filas copiadas; pgJDBC expone
ese conteo como `long` al finalizar `CopyIn`.

Fuentes primarias:

- [PostgreSQL 15: COPY outputs and failure behavior](https://www.postgresql.org/docs/15/sql-copy.html)
- [pgJDBC: `CopyIn`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyIn.html)
- [pgJDBC: `CopyOperation`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyOperation.html)

## Decisión

### Engine y boundary de conexión

Crear `PostgresBulkInserter<T>` como componente preparado package-private en
`postgres-bulk-pgjdbc`. Su operación de menor nivel recibe:

```text
Connection + Iterable<? extends T> + BulkInsertOptions -> BulkWriteResult
```

La instancia queda ligada a una `EntityMetadata<T>` y no adquiere ni libera conexiones.
La conexión es caller-owned y todos los COPY de una invocación reciben exactamente esa
misma instancia. El engine no llama `close`, `commit`, `rollback`, `setAutoCommit`,
`setReadOnly` ni cambia isolation.

Se evaluaron:

1. `DataSource` directo: simple para JDBC plano, pero el engine pasaría a decidir
   adquisición/liberación y podría saltarse una conexión vinculada a Spring.
2. Provider `acquire()`: no expresa quién libera ni permite adaptar con seguridad
   lifecycles distintos.
3. Callback de connection access: es la forma más prometedora para adapters plain JDBC y
   Spring, porque cada adapter puede poseer el scope completo; publicarla ahora exigiría
   al menos callback, política de excepciones, factories y ownership aún no probados.

Se adopta la opción de alcance válida descrita por Phase 6: sólo engine caller-owned. No se
implementa todavía `BulkOperations<T>` ni se publica una connection-access SPI. Phase 9
podrá envolver este motor con acceso transaction-aware una vez exista evidencia real de
Spring; antes de cruzar el límite de módulo se decidirá la mínima superficie pública. La
API core de ADR-009 permanece válida y sin cambios.

### Preparación y concurrencia

Al construir el inserter se validan metadata y executor, se genera una vez el COPY SQL y
se prepara una vez `PreparedCopyCsvRowEncoder<T>`. Un tipo no soportado falla antes de
consumir cualquier iterable. Metadata, SQL, encoder y executor son campos finales; todo
estado de operación —iterator, posición y contadores— es local. Una instancia puede
compartirse concurrentemente si los accessors de metadata cumplen ADR-011 y cada llamada
usa una conexión/iterable apropiados.

### Batching single-pass

Cada operación llama `items.iterator()` exactamente una vez. Usa el mismo iterator hasta
agotarlo y nunca construye una lista, ni global ni por batch. Antes de cada COPY obtiene
un único primer elemento como lookahead; así no inicia un COPY vacío y detecta un null en
la primera posición de un batch antes de abrir protocolo. El productor escribe ese primer
elemento y consume directamente hasta `batchSize - 1` adicionales.

Cada batch no vacío corresponde a exactamente un COPY completado. Complejidad:

```text
tiempo O(N)
memoria adicional O(1) respecto al dataset
```

No hay retry, paralelismo, progreso ni flush por fila.

### Validación y errores de elementos

`Connection`, iterable y options null producen `NullPointerException` descriptivo.
`iterator()` null también se rechaza explícitamente. Un elemento null produce
`IllegalArgumentException` con su posición one-based y nunca se envía al encoder.

Para preservar este contrato durante producción incremental, ADR-013 se refina: después
de cancelar un COPY activo, el executor vuelve a lanzar sin envolver las
`RuntimeException` y `Error` del productor. Los fallos checked de I/O/JDBC siguen
exponiéndose mediante `CopyExecutionException` con causa. Así los errores de argumento y
accessor conservan identidad, mientras SQLState/vendor details continúan en la cause
chain.

Si falla cualquier batch no se devuelve resultado parcial. Un fallo de protocolo se
presenta como `BulkException` con tipo lógico y número de batch, conservando el fallo
interno y la `SQLException` como causas. No se incluyen entidades, filas ni CSV.

### Conteos e invariantes

El productor cuenta filas emitidas por batch y el executor devuelve el `long` del
servidor. Con el dialecto v1 —sin `WHERE`, `ON_ERROR` ni opciones que omitan filas— ambos
deben coincidir. Una discrepancia es una violación de invariante y lanza `BulkException`;
no se reemplaza el dato del servidor por el esperado. Features o triggers que supriman
filas no forman parte del contrato soportado.

`affectedRows` suma sólo server counts de COPY completados mediante `Math.addExact`.
`batches` cuenta sólo COPY completados y usa `Math.incrementExact`. Overflow produce
`BulkException` en vez de un resultado negativo. En input vacío se devuelve
`BulkWriteResult.empty()` y no se llama al executor.

### Transacción y persistencia parcial

Un batch no es una frontera transaccional impuesta por la librería:

- con `autoCommit=false`, el caller puede confirmar o revertir todos los batches como una
  unidad porque todos usan la misma conexión;
- con `autoCommit=true`, cada COPY completado puede quedar confirmado antes de que un
  batch posterior falle;
- el engine nunca intenta ocultar esa diferencia cambiando estado JDBC.

La ausencia de `BulkWriteResult` en fallo describe el resultado de la llamada, no promete
que no haya persistencia parcial fuera de una transacción externa.

## Alternativas descartadas

- Implementar ya una clase pública `BulkOperations<T>` respaldada por `DataSource`: fija
  ownership antes de probar integración Spring y puede abrir otra conexión física.
- Publicar `ConnectionProvider`: adquisición sin release/scope es ambigua.
- Publicar callback/factory JDBC completo: dirección plausible, pero todavía especulativa
  y ampliaría API sólo para cruzar módulos que aún no tienen implementación.
- Materializar el dataset o una lista por batch: innecesario; el encoder ya consume una
  fila cada vez.
- Un único COPY con cortes lógicos: no daría semántica real a `batchSize` ni a
  `BulkWriteResult.batches`.
- Confiar sólo en el número de items producido: perdería el conteo real que PostgreSQL ya
  entrega.

## Validación

Unit tests cubren boundaries `0`, `1`, `B-1`, `B`, `B+1`, `2B`, `2B+1`, defaults,
single-pass, lazy/O(1), orden, nulls, preparación previa, mismatch, política de overflow y
fallo en batch posterior. Tests Testcontainers cubren empty/single/exact/múltiples,
20.000 filas multi-batch, valores de columna null, one-shot, ownership, commit, rollback,
fallo de constraint en batch posterior con autocommit true/false y conexión reutilizable.

## Consecuencias

Phase 6 entrega un motor insert completo y probado, pero aún interno. La superficie
pública permanece en ocho tipos core. Phase 7 reutilizará el executor COPY, no el
coordinador de entidades; Phase 8 entregará metadata real y Phase 9 cerrará connection
access y `BulkOperations<T>` con evidencia Spring. Hasta entonces el engine es una
primitive de adapter, no una API JDBC pública provisional.

## Resolución posterior

Phase 9 cerró esa frontera en ADR-017: `PostgresBulkJdbcOperations<T>` expone el motor con
conexión caller-owned y el fragmento Spring Data implementa `BulkOperations<T>` usando la
conexión del `Session` transaccional.

Phase 11 confirma ADR-019: con batch size 2, dos COPY exitosos y fallo del tercero, una
transacción manual revierte las cinco filas y la conexión se reutiliza tras rollback; con
autocommit, las cuatro filas de los dos COPY completados permanecen. También quedan probados
`Integer.MAX_VALUE`, overflow checked sin datasets gigantes e identidad de fallos de iterator y
accessor.
