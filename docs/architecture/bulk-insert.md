# Motor bulk insert

## Alcance

Phase 6 compone metadata neutral, SQL COPY, encoding CSV preparado y ejecución pgJDBC en
un motor end-to-end interno. No implementa todavía la fachada pública `BulkOperations<T>`:
esa fachada necesita una política de adquisición/liberación que funcione tanto con JDBC
plano como con una conexión ligada a una transacción Spring. Publicar esa SPI sin probar
el adapter Spring fijaría prematuramente ownership y lifecycle.

`PostgresBulkInserter<T>` es package-private y se prepara desde `EntityMetadata<T>`. Su
operación recibe una `Connection` ya abierta, un `Iterable<? extends T>` y
`BulkInsertOptions`, y devuelve `BulkWriteResult`:

```text
EntityMetadata ──prepare once──> COPY SQL + PreparedCopyCsvRowEncoder
                                      │
Connection + Iterable + options ──> PostgresBulkInserter
                                      │ one COPY per non-empty batch
                                      ▼
                              PostgresCopyExecutor
```

## Conexión y transacción

La conexión es caller-owned. El motor no la adquiere, libera, cierra ni reconfigura; no
hace `commit`, `rollback`, `setAutoCommit`, `setReadOnly` ni cambia isolation. Todos los
batches de una llamada usan exactamente la instancia recibida.

Un batch es una frontera de COPY, no una frontera transaccional impuesta por la librería:

- con `autoCommit=false`, el caller puede confirmar todos los batches o revertirlos como
  una unidad;
- con `autoCommit=true`, cada COPY completado puede quedar confirmado aunque falle uno
  posterior;
- un fallo nunca devuelve un `BulkWriteResult` parcial, pero esto no implica que una
  transacción externa haya revertido trabajo ya confirmado.

Para Spring se prevé un wrapper transaction-aware que entregue la conexión asociada al
scope completo de la operación. Se evaluará en Phase 9 si ese wrapper justifica un callback
público mínimo en `postgres-bulk-pgjdbc`. No se usa `DataSource`, Spring ni Hibernate en
Phase 6.

## Preparación y concurrencia

Al construir el motor se validan metadata y executor, se genera una única sentencia COPY
y se resuelven una única vez los encoders de las columnas. Un tipo no soportado falla
antes de consumir el iterable. Esos componentes son inmutables y se reutilizan entre
batches y llamadas.

La instancia no conserva estado mutable por operación: iterator, posiciones y contadores
son variables locales. Puede compartirse entre threads siempre que los accessors de
metadata sean thread-safe y cada invocación proporcione su propio scope JDBC e iterable.

## Algoritmo de batching

La operación obtiene exactamente un iterator. Lee un elemento de lookahead antes de cada
batch; si no existe, devuelve el resultado sin abrir otro COPY. Para un batch no vacío, el
callback escribe el lookahead y consume directamente hasta `batchSize - 1` elementos
adicionales del mismo iterator.

Por tanto, para `N > 0`, el número de COPY completados es `ceil(N / batchSize)`; para input
vacío es cero. No se construye una lista global ni una lista por batch. El coste es tiempo
`O(N)` y memoria adicional `O(1)` respecto del dataset. No hay retry, paralelismo ni flush
por fila.

## Resultados, conteos y errores

Cada callback cuenta las filas producidas y `PostgresCopyExecutor` devuelve el conteo
`long` del servidor. En el dialecto actual —sin opciones que omitan filas— ambos deben
coincidir. Un mismatch lanza `BulkException`; el dato del servidor nunca se reemplaza por
el esperado. `affectedRows` suma mediante `Math.addExact` exclusivamente los conteos de
COPY completados y `batches` cuenta esos COPY mediante `Math.incrementExact`. Cualquier
overflow falla explícitamente.

Input vacío devuelve `BulkWriteResult.empty()` sin abrir COPY. Argumentos null producen
`NullPointerException` descriptivo. Un elemento null produce `IllegalArgumentException`
con posición one-based; un valor de columna null sigue codificándose como el marcador COPY
NULL. Si el null aparece dentro de un COPY activo, el executor cancela el protocolo y
repropaga la misma excepción runtime.

Fallos JDBC/I/O se conservan dentro de `CopyExecutionException`; el coordinador los
presenta como `BulkException` con tipo y número de batch, manteniendo la cadena hasta la
`SQLException`. Fallos runtime del productor o accessor se propagan sin envolver después
de cancelar el COPY activo. Ningún mensaje incluye entidad, valores o CSV.

## Evidencia

Los tests unitarios cubren límites `0`, `1`, `B-1`, `B`, `B+1`, `2B`, `2B+1`, default
1.000, orden, iterable one-shot/lazy, nulls, preparación previa, misma conexión/SQL,
mismatch y fallo posterior. Testcontainers con PostgreSQL 15.18 valida input vacío,
single/exact/múltiples, 2.500 filas en tres COPY, one-shot, null dentro del stream,
commit/rollback externos, fallo posterior con ambos modos de autocommit, conexión abierta
y 20.000 filas en 26 batches sin materialización.

Generated IDs, defaults omitidos, callbacks/listeners JPA, sincronización del persistence
context, retries y lookup quedan fuera del contrato. COPY opera directamente contra la
tabla: no gestiona entidades ORM ni devuelve IDs generados.
