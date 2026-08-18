# Lookup masivo mediante tabla temporal

## Alcance

Phase 7 materializa en `postgres-bulk-pgjdbc` un motor relacional interno para buscar
filas por muchas claves sin depender de Hibernate, Spring o JPA. La API pública continúa
diferida: esta fase prueba el mecanismo y sus contratos antes de decidir cómo se conectará
con el adapter ORM.

```text
Iterable<K>
    ↓ single-pass
BulkKeyMetadata<K>
    ↓ columnas físicas ordenadas
CREATE TEMP TABLE ... AS SELECT ... WITH NO DATA
    ↓
COPY key tuples FROM STDIN
    ↓
JOIN target con SELECT DISTINCT keys
    ↓
callback JDBC acotado consume el resultado
    ↓
DROP TABLE explícito + ON COMMIT DROP
```

## Componentes

- `TemporaryTableBulkLookup<K>` prepara metadata, SQL y encoders y coordina todo el
  lifecycle sobre una `Connection` caller-owned.
- `BulkLookupSql` construye CREATE, COPY, SELECT JOIN y DROP.
- `TemporaryTableNameGenerator` crea nombres session-local de 44 bytes con prefijo
  `pgbulk_keys_` y UUID hexadecimal compacto.
- `PreparedCopyCsvRowEncoder` y `PostgresCopyExecutor` son el pipeline COPY existente;
  para keys se activa rechazo explícito de componentes null.
- `LookupQuery<R>` es un callback package-private. Consume completamente los resultados
  mientras la temporal existe y retorna un valor desligado de JDBC.

Ningún `ResultSet`, `Statement`, `PGConnection` o nombre de tabla temporal escapa de la
operación.

## Input y carga

`BulkKeyMetadata<K>` contiene uno o varios `ColumnMetadata<K>` en el orden físico exacto.
Las entradas son valores de clave, no entidades parciales. El motor obtiene exactamente
un iterator, hace lookahead de la primera key y transmite todo en un único COPY sin crear
listas ni batches intermedios. El coste del lado Java es `O(N)` tiempo y `O(1)` memoria
respecto a las keys; 20.000 keys one-shot están cubiertas contra PostgreSQL real.

Un iterable vacío devuelve el resultado vacío suministrado sin leer autocommit, ejecutar
DDL/COPY/SELECT o invocar el callback. El conteo producido debe coincidir con el `long`
reportado por PostgreSQL.

## Tabla temporal y tipos

La forma elegida es:

```sql
CREATE TEMP TABLE "pgbulk_keys_<uuid>" ON COMMIT DROP AS
SELECT "key_one", "key_two"
FROM "schema"."target"
WITH NO DATA
```

PostgreSQL deriva nombres y tipos de las columnas seleccionadas. No existe inferencia
Java → SQL. La evidencia PostgreSQL 15.18 confirma que una referencia directa conserva
domain, typmod de `numeric(12,3)`/`varchar(20)` y collation, pero no copia NOT NULL,
default, identity ni expresión generated. Sólo existen las columnas de key seleccionadas.

`LIKE` se descartó porque incluye todas las columnas y siempre copia NOT NULL. Consultar
catálogos y reconstruir DDL aumentaría complejidad y privilegios. La base de datos valida
que las columnas declaradas realmente pertenecen a la tabla.

## SQL y seguridad

Schema, tabla, columnas y temporal pasan por `PostgresIdentifierQuoter`. Los aliases son
constantes internas y los valores de keys nunca se interpolan: viajan exclusivamente por
COPY. El UUID compacto produce nombres ASCII de 44 bytes, por debajo del límite estándar
de 63 bytes de PostgreSQL. Una colisión falla en CREATE; no se usa `IF NOT EXISTS` para
ocultarla. Los tests cubren quoting, schema custom, unicidad, scopes anidados y dos
conexiones concurrentes.

## Conexión, transacción y lifecycle

Para input no vacío se requiere `autoCommit=false`; con autocommit cada statement forma
una transacción y `ON COMMIT DROP` eliminaría la tabla al terminar CREATE, antes del COPY.
El motor falla antes del DDL cuando autocommit está activo. Tampoco puede operar en una
transacción read-only de PostgreSQL porque `CREATE TABLE AS` falla con SQLState `25006`.
El caller decide si esa configuración es válida y conserva la responsabilidad de rollback.

CREATE, COPY, callback y DROP reciben exactamente la misma `Connection`. El motor no
llama `close`, `commit`, `rollback`, `setAutoCommit`, `setReadOnly`, no cambia isolation y
no crea savepoints.

En éxito o fallo no abortivo ejecuta `DROP TABLE IF EXISTS` antes de retornar. `ON COMMIT
DROP` añade defensa para commit, rollback o fin de sesión. Cuando COPY o SELECT abortan la
transacción, DROP también puede fallar con `25P02`; ese fallo se adjunta como suppressed y
el caller debe hacer rollback. Rollback deshace CREATE y evita contaminar una conexión
reutilizada.

## Semántica relacional

- **Duplicados de input:** se conservan durante COPY para mantener streaming, pero el JOIN
  usa `SELECT DISTINCT` sobre las columnas de key; no multiplican matches.
- **Duplicados de target:** se devuelven todas las filas que compartan la combinación de
  key. `BulkKeyMetadata` no implica UNIQUE.
- **Missing keys:** no producen filas.
- **Null key object:** se rechaza con `IllegalArgumentException` y posición one-based.
- **Null component:** se rechaza con `IllegalArgumentException`, posición y columna; no se
  adopta igualdad null-safe. Si aparece después de iniciar COPY, cancelar COPY aborta la
  transacción y el caller debe hacer rollback.
- **Orden:** no se garantiza. El SELECT no contiene `ORDER BY` ni conserva ordinal de
  entrada.

No se crea índice temporal ni se ejecuta `ANALYZE`. Su beneficio depende de cardinalidad y
planes; Phase 14 podrá compararlos sin convertir tuning en requisito de correctness.

## Resultados y fallos

El callback recibe la misma conexión, SQL de JOIN completamente quoted y conteo de keys
copiadas. Debe abrir y cerrar su statement/result set y materializar o mapear su resultado
antes de retornar. Así el motor puede limpiar la temporal sin devolver un recurso vivo.

Fallos JDBC/COPY se exponen mediante `BulkException` conservando causa, SQLState y detalles
del driver. Fallos runtime de accessor/callback preservan identidad. El cleanup secundario
nunca reemplaza el fallo principal y no se devuelve un resultado parcial.

## Frontera Hibernate/Spring

Phase 9 elevó el callback mínimo mediante `PostgresBulkJdbcOperations.findAllByBulkKey`.
El fragmento Spring Data obtiene la conexión del `Session` actual y ejecuta una native
query JPA dentro del callback, antes del cleanup de la temporal. La API recibe
`BulkKeyMetadata<K>` explícita y devuelve entidades materializadas; no interpreta nombres
de métodos ni crea entidades parciales. La librería no hace `flush()` ni `clear()` y usa
flush mode `COMMIT`. ADR-017 contiene la decisión transaccional completa.

## Fuentes PostgreSQL

- [CREATE TABLE AS](https://www.postgresql.org/docs/15/sql-createtableas.html)
- [CREATE TABLE y tablas temporales](https://www.postgresql.org/docs/15/sql-createtable.html)
- [COPY](https://www.postgresql.org/docs/15/sql-copy.html)
- [SELECT DISTINCT](https://www.postgresql.org/docs/15/queries-select-lists.html#QUERIES-DISTINCT)
- [Identificadores](https://www.postgresql.org/docs/15/sql-syntax-lexical.html)
- [Transacciones read-only](https://www.postgresql.org/docs/15/runtime-config-client.html)
