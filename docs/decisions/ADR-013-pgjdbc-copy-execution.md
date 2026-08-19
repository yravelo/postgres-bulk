# ADR-013: Ejecución interna de COPY mediante pgJDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 5 debe consumir el encoding COPY CSV de ADR-012 sobre una `Connection` ya abierta,
producir SQL seguro desde metadata estructurada y controlar el protocolo COPY sin asumir
ownership de conexión ni transacción. El executor debe servir posteriormente tanto a
insert como a la carga de claves de lookup, sin conocer entidades, batching, Spring o
Hibernate.

Fuentes primarias:

- [pgJDBC: PostgreSQL extensions and COPY example](https://jdbc.postgresql.org/documentation/server-prepare/)
- [pgJDBC: `PGConnection`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/PGConnection.html)
- [pgJDBC: `CopyManager`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyManager.html)
- [pgJDBC: `CopyIn`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyIn.html)
- [pgJDBC: `CopyOperation`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyOperation.html)
- [pgJDBC: `PGCopyOutputStream`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/PGCopyOutputStream.html)
- [PostgreSQL: COPY](https://www.postgresql.org/docs/current/sql-copy.html)
- [PostgreSQL: identifiers and quoted identifiers](https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS)

## Decisión

### Responsabilidad y visibilidad

Mantener encoding, SQL y ejecución en un único package cohesivo
`io.ybr.postgresbulk.pgjdbc.copy`. Los tipos de Phase 4 se moverán desde el package
`encoding`; siguen siendo package-private. Phase 5 añade también sólo tipos
package-private:

- un quoter de identificadores;
- un builder de COPY INSERT desde `EntityMetadata<?>`;
- un executor de SQL COPY ya preparado;
- un callback de producción de caracteres;
- una excepción interna de ejecución.

No se publica SPI: los consumidores inmediatos de Phase 6/7 viven en el mismo adapter y
no justifican comprometer pgJDBC, COPY o callbacks de transporte como API externa.

El builder conoce metadata pero el executor no. El contrato de menor nivel es
conceptualmente `Connection + copySql + Writer callback -> long`, lo que permite reutilizar
el protocolo para filas de entidades y claves temporales.

### API pgJDBC y boundary UTF-8

Obtener `PGConnection` mediante `connection.unwrap(PGConnection.class)` para admitir
connections proxied/pooled. No hacer cast directo ni fallback JDBC genérico. Un unwrap
fallido se convierte en un fallo de ejecución que conserva la `SQLException` original.

Iniciar con `pgConnection.getCopyAPI().copyIn(copySql)`, que devuelve `CopyIn`. Envolverlo
en `PGCopyOutputStream` con un buffer interno fijo de 64 KiB y después en
`OutputStreamWriter(..., StandardCharsets.UTF_8)`. Éste es el único boundary
characters-to-bytes; no se usa charset de JVM. No se añade `BufferedWriter`: el encoder de
caracteres y `PGCopyOutputStream` ya amortizan escrituras y no se hace flush por fila.

Se descarta `CopyManager.copyIn(String, Reader)`: es apropiado para un Reader ya existente,
pero el pipeline actual es push-based (`PreparedCopyCsvRowEncoder` escribe a
`Appendable`). Adaptarlo requeriría un Reader productor complejo o pipes y otro hilo, con
lifecycle/cancelación adicionales. También se descarta escribir todo a `String`/`byte[]`.

### Lifecycle y errores

En éxito:

1. el callback escribe incrementalmente al Writer;
2. el executor hace un único `flush` final del boundary de caracteres;
3. llama `CopyIn.endCopy()` a través de `PGCopyOutputStream.endCopy()`;
4. devuelve el conteo `long` reportado por el servidor/driver.

En cualquier `IOException`, `SQLException`, `RuntimeException` o `Error` después de iniciar
COPY, si la operación sigue activa se invoca `cancelCopy()`. Los fallos checked de I/O/JDBC
son la causa principal de una `CopyExecutionException` interna. ADR-014 refina el caso del
productor: una `RuntimeException` o `Error` se relanza sin envolver después del cleanup
para preservar la identidad de errores de argumento/accessor. Si cancelación también
falla, su `SQLException` se añade como suppressed al fallo original. Nunca se intenta
`endCopy()` después de un fallo del productor.

`PGCopyOutputStream.close()` finaliza exitosamente un COPY activo, por lo que no se usa
como cleanup genérico: podría convertir una producción parcial en éxito. La terminación
del protocolo queda deliberadamente expresada por `endCopy` o `cancelCopy`. Los wrappers
de Writer/stream no poseen recursos adicionales una vez que la operación deja de estar
activa.

No existe lógica especial de interrupt: ninguna API elegida expone
`InterruptedException`. Una excepción runtime no limpia ni modifica el interrupt flag y
se trata como cualquier fallo del productor.

### Connection y transacción

La Connection siempre es caller-owned. El executor no llama `close`, `commit`, `rollback`,
`setAutoCommit`, `setReadOnly` ni modifica isolation. COPY participa en el estado
transaccional recibido:

- con autocommit activo, cada COPY sigue la semántica del driver/servidor;
- con autocommit desactivado, el caller decide commit o rollback;
- bytes ya enviados antes de un fallo no implican garantía propia de atomicidad.

Los errores de argumentos (`Connection`, SQL o callback null; SQL blank) usan excepciones
estándar antes de iniciar COPY. Los fallos de protocolo/unwrap/producer usan un subtipo
package-private de `BulkException`, conservando `SQLException`/`IOException` como causa y
sin incluir datos CSV o entidades.

### Identificadores y SQL

Cada schema, tabla y columna se cita siempre por separado con double quotes. Una double
quote embebida se duplica. Se rechaza el carácter NUL, único carácter que PostgreSQL no
permite dentro de un quoted identifier. No se parsea `schema.table`, no se normaliza case,
no se aceptan fragmentos SQL y no se intentan parameters JDBC para identifiers.

El builder conserva exactamente `EntityMetadata.insertColumns()` y produce una forma
canónica equivalente a:

```sql
COPY "schema"."table" ("column_one", "column_two") FROM STDIN WITH (
  FORMAT CSV,
  DELIMITER ',',
  QUOTE '"',
  ESCAPE '"',
  NULL E'\\N',
  ENCODING 'UTF8'
)
```

La implementación puede emitirlo en una línea; el whitespace no forma parte del contrato.
No incluye HEADER, FREEZE, FORCE_NULL/NOT_NULL, ON_ERROR ni opciones avanzadas.

## Alternativas descartadas

- Cast directo a `PGConnection`: falla con proxies y wrappers legítimos.
- `PGConnection.escapeIdentifier`: correcto, pero obligaría al builder puro de SQL a
  depender de una conexión; always-quote con doubling implementa directamente la regla
  oficial y puede probarse sin infraestructura.
- `CopyManager.copyIn(String, Reader)`: pull-based y no permite el callback Writer actual
  sin buffering global o concurrencia auxiliar.
- Try-with-resources ciego sobre `PGCopyOutputStream`: `close()` significa success/endCopy,
  no cancelación por fallo del productor.
- Excepción pública de ejecución: no existe todavía un executor público ni una necesidad
  de captura más específica que `BulkException`.

## Validación

Unit tests fijarán quoting, SQL canónico, orden y errores de argumentos/unwrap. Tests
Failsafe con Testcontainers y PostgreSQL 15 verificarán dialecto exacto, UTF-8,
NULL/empty/marcadores, strings especiales, numéricos, temporales, enum, bytea, schema,
identificadores quoted, row count, autocommit, commit/rollback, ownership de conexión,
error del servidor y fallo mid-stream con cancelación.

La matriz PostgreSQL 15–18, timeouts/cancelación desde otro thread, pools concretos,
network failure y tuning/configuración del buffer quedan para compatibilidad y robustness.

## Consecuencias

pgJDBC pasa a ser dependencia runtime únicamente de `postgres-bulk-pgjdbc`; Testcontainers
permanece test-only en el mismo módulo. Core y el resto de adapters no cambian. Phase 6
podrá coordinar un iterable/batches alrededor de este primitive sin duplicar SQL, encoding
o lifecycle, pero esa coordinación no se implementa en Phase 5.

## Resolución de robustez en Phase 11

ADR-019 confirma con fault doubles que fallos de startup, write y `endCopy` conservan la
`SQLException`; un fallo adicional de `cancelCopy` queda suppressed en el productor primario. La
integración real prueba que una transacción manual queda `25P02` hasta rollback y que una pérdida
de backend conserva causa/SQLState. El executor sigue sin poseer conexión, transacción ni retry.
