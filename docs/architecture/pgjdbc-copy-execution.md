# Ejecución COPY con pgJDBC

## Alcance y componentes

Phase 5 añade al módulo `postgres-bulk-pgjdbc` la primitive interna que consume el encoder
de Phase 4. No implementa batching, fachada bulk insert, adquisición de conexiones,
Spring, Hibernate ni tablas temporales. Todos los tipos viven package-private en
`io.ybr.postgresbulk.pgjdbc.copy`:

```text
EntityMetadata ──> CopySqlBuilder ──> COPY SQL
                                      │
rows ──> PreparedCopyCsvRowEncoder ──> Writer callback
                                      │
caller-owned Connection ──> PostgresCopyExecutor ──> PostgreSQL
```

SQL y protocolo están separados: el executor acepta una sentencia ya construida y no
conoce metadata ni entidades. Phase 6 podrá iterar/batchear sin duplicar quoting,
encoding o lifecycle.

## SQL e identificadores

`CopySqlBuilder` recibe `EntityMetadata<?>`. Cita schema, tabla y cada columna siempre y
por separado mediante double quotes; duplica cualquier quote interna y rechaza NUL. No
parsea nombres qualified, no concatena fragmentos libres y conserva exactamente el orden
de `insertColumns()`.

La forma canónica es:

```sql
COPY "schema"."table" ("column_one", "column_two") FROM STDIN WITH (FORMAT CSV, DELIMITER ',', QUOTE '"', ESCAPE '"', NULL E'\\N', ENCODING 'UTF8')
```

## Protocolo, streaming y UTF-8

El executor recibe una `java.sql.Connection`, la sentencia COPY y un callback que escribe
a `Writer`. Usa `connection.unwrap(PGConnection.class)`, nunca un cast directo. Inicia
`CopyIn` con `PGConnection.getCopyAPI().copyIn(sql)` y lo adapta mediante un
`PGCopyOutputStream` de 64 KiB y un `OutputStreamWriter` con
`StandardCharsets.UTF_8`. Éste es el único boundary character-to-byte.

El callback produce directamente sobre ese pipeline. No existe colección obligatoria,
buffer de dataset, `String` de todas las filas ni flush por fila. En éxito se hace un
flush final, `endCopy()` y se devuelve su `long` real.

## Ownership, transacción y errores

La conexión pertenece siempre al caller. El executor no llama `close`, `commit`,
`rollback`, `setAutoCommit`, `setReadOnly` ni adquiere un `DataSource`. COPY participa en
la transacción recibida: con autocommit activo queda confirmado al finalizar; con
autocommit desactivado el caller conserva control completo sobre commit o rollback.

Si falla producción, escritura o finalización, el executor cancela `CopyIn` mientras siga
activo. Los fallos checked JDBC/I/O quedan como causa de `CopyExecutionException`; los
fallos runtime y `Error` del productor se relanzan sin envolver después de cancelar para
preservar el contrato del llamador. Si la cancelación también falla, ese error queda
suppressed en el fallo original. No se usa `close()` de
`PGCopyOutputStream` como cleanup porque cerrarlo finaliza un COPY activo y podría aceptar
contenido parcial.

## Evidencia ejecutable

Unit tests fijan quoting, SQL canónico, orden, validación y unwrap. Un `*IT` ejecutado por
Failsafe levanta `postgres:15.18-alpine` y valida:

- NULL, empty, `\\N`, `\\.`, comma, quote, LF, CR, CRLF, espacios, Unicode, emoji y backslash;
- integrales, `BigInteger`, `BigDecimal`, float/double y valores especiales;
- `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime` e `Instant`;
- `bytea` vacío, cero y bytes high-bit, y enums;
- schema/nombres quoted y alineación del orden de columnas;
- conteo de servidor y 20.000 filas generadas incrementalmente;
- productor vacío con conteo de servidor cero;
- autocommit, commit, rollback, conexión abierta/reutilizable;
- fallo del servidor y fallo del productor después de enviar datos.

La suite confirma PostgreSQL 15.18 con pgJDBC 42.7.13. PostgreSQL 16–18, pools concretos,
fallos de red, timeout/cancelación externa y tuning del buffer quedan diferidos.
