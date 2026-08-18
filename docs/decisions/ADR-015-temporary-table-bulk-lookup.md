# ADR-015: Lookup interno mediante tabla temporal, COPY y callback acotado

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 7 debe cargar `Iterable<K>` mediante COPY, relacionarlo con una tabla física y
permitir consumir filas JDBC sin conocer JPA, Hibernate o Spring. El mecanismo debe usar
una sola conexión prestada, no reconstruir tipos PostgreSQL desde Java y no devolver un
`ResultSet` vivo después de eliminar la tabla temporal.

La API pública lookup de ADR-010 continúa diferida. Phase 7 debe validar primero claves
simples/compuestas, tipos físicos, duplicados, nulls, transacciones, cleanup y resultado
relacional.

Fuentes primarias:

- [PostgreSQL 15: CREATE TABLE AS](https://www.postgresql.org/docs/15/sql-createtableas.html)
- [PostgreSQL 15: CREATE TABLE y tablas temporales](https://www.postgresql.org/docs/15/sql-createtable.html)
- [PostgreSQL 15: COPY](https://www.postgresql.org/docs/15/sql-copy.html)
- [PostgreSQL 15: SELECT DISTINCT](https://www.postgresql.org/docs/15/queries-select-lists.html#QUERIES-DISTINCT)
- [PostgreSQL 15: identificadores](https://www.postgresql.org/docs/15/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS)
- [PostgreSQL 15: transacciones](https://www.postgresql.org/docs/15/sql-start-transaction.html)

## Decisión

### Componente y frontera de resultado

Crear `TemporaryTableBulkLookup<K>` como motor preparado package-private en
`postgres-bulk-pgjdbc`. Recibe `TableName`, `BulkKeyMetadata<K>`, una `Connection`
caller-owned, `Iterable<? extends K>`, un resultado vacío y un callback interno. La forma
conceptual es:

```text
Connection + keys + callback
  -> CREATE TEMP
  -> COPY keys
  -> callback(Connection, quoted JOIN SQL, copiedKeyCount)
  -> DROP TEMP
  -> callback result
```

El callback se ejecuta mientras la temporal existe y debe consumir completamente su
consulta/resultado antes de retornar. Así no se devuelve un `ResultSet` ni un `Statement`
vivo y el engine mantiene el lifecycle. Phase 7 usa el callback con JDBC para verificar
filas reales. No se publica aún: Phase 9 decidirá si el consumidor Hibernate/Spring
requiere elevar una versión mínima de este scope a SPI tecnológica.

Un resultado vacío se proporciona explícitamente al motor. Si las keys están vacías se
devuelve sin consultar estado JDBC, crear tabla, abrir COPY ni invocar el callback de
consulta.

### Tabla y tipos físicos

Crear exactamente las columnas ordenadas de `BulkKeyMetadata<K>` mediante:

```sql
CREATE TEMP TABLE "pgbulk_keys_<token>" ON COMMIT DROP AS
SELECT "key_one", "key_two"
FROM "schema"."target"
WITH NO DATA
```

`CREATE TABLE AS` define nombres y tipos desde las columnas de salida del `SELECT`; no hay
mapping `Class<?> -> SQL`. Un spike PostgreSQL 15.18 comprobó que referencias directas
preservan OID de domain, typmod/precision/scale y collation. La temporal no copia NOT NULL,
defaults, identity, generated expressions, índices ni constraints: no son necesarios para
cargar claves y su ausencia evita restricciones accidentales.

`CREATE TEMP TABLE (LIKE target)` se descarta porque copia todas las columnas y siempre
copia NOT NULL; no ofrece una proyección de columnas. Catálogo + DDL explícito se descarta
por complejidad y privilegios. La base de datos valida que cada nombre de key existe en la
tabla destino; `EntityMetadata.insertColumns()` no puede hacerlo correctamente porque una
key física puede no ser insertable.

### Nombre, quoting y SQL

El nombre usa el prefijo ASCII fijo `pgbulk_keys_` y 32 dígitos hexadecimales de un UUID
sin guiones: 44 bytes, por debajo de los 63 bytes del PostgreSQL estándar. No incorpora
tabla, entidad ni claves. UUID ofrece unicidad práctica entre threads, conexiones y scopes
reentrantes; una colisión no se oculta con `IF NOT EXISTS`, sino que falla en CREATE.

Todos los identificadores —schema, tabla, key columns y temporal— pasan por
`PostgresIdentifierQuoter`. Los aliases son constantes internas. Los valores de keys nunca
se concatenan en SQL: sólo viajan por COPY.

### Transacción, ownership y cleanup

Para input no vacío se exige `connection.getAutoCommit() == false`. PostgreSQL ejecuta
cada sentencia como una transacción con autocommit; un spike real confirmó que
`ON COMMIT DROP` elimina la temporal al terminar CREATE, antes de COPY. El motor falla
antes de DDL si autocommit está activo y nunca lo modifica.

La conexión sigue siendo caller-owned: el engine no hace `close`, `commit`, `rollback`,
`setAutoCommit`, `setReadOnly` ni cambia isolation. CREATE, COPY, callback y DROP reciben
exactamente la misma instancia.

En éxito o fallo no abortivo se intenta `DROP TABLE IF EXISTS` explícito antes de retornar.
`ON COMMIT DROP` añade una segunda garantía para commit/rollback/session end. Si una
sentencia SQL/COPY aborta la transacción, DROP puede fallar con transaction-aborted; ese
error se añade como suppressed al fallo principal y el caller debe ejecutar rollback, que
deshace CREATE y elimina la temporal. No se crean savepoints ni transacciones internas.

Una conexión/read-only transaction no se reconfigura. La prueba real muestra que
PostgreSQL rechaza `CREATE TABLE AS` con SQLState `25006`; se conserva la causa y el caller
debe hacer rollback. Phase 9 deberá impedir o documentar lookup en transacciones Spring
read-only.

### Carga, claves y conteos

Se reutilizan `ValueEncoderRegistry`, `CopyCsvFieldWriter`, el encoder preparado y
`PostgresCopyExecutor`. Se añade preparación desde `BulkKeyMetadata<K>` y una variante de
escritura que rechaza componentes null sin leer dos veces el accessor.

El iterable se recorre exactamente una vez. Tras un lookahead de una key se transmite el
resto directamente en un único COPY, sin listas ni batches artificiales. El coste Java es
`O(N)` tiempo y `O(1)` memoria respecto al número de keys. El conteo del productor debe
coincidir con el `long` del servidor; mismatch y overflow fallan.

Una key object null o un componente null produce `IllegalArgumentException` con posición
one-based y, para componentes, nombre de columna. Si el null aparece después de iniciar
COPY, pgJDBC cancela el stream y PostgreSQL deja la transacción abortada; el DROP fallido
queda suppressed y el caller debe hacer rollback. No se adopta igualdad null-safe porque
la metadata no expresa si NULL es una clave válida y `IS NOT DISTINCT FROM` cambiaría
semántica/planificación. Valores de key, aunque no entidades, nunca aparecen en mensajes.

### Duplicados, matches y orden

COPY conserva todas las keys —incluidos duplicados— para mantener streaming `O(1)` en
Java. El JOIN usa una relación derivada:

```sql
JOIN (SELECT DISTINCT "key_one", "key_two" FROM "temp") lookup_key
```

Así una key duplicada no multiplica una fila target. Si la tabla destino contiene varias
filas con la misma combinación, todas se devuelven una vez: `BulkKeyMetadata` no implica
UNIQUE. Keys sin match no producen fila. No se garantiza orden porque no hay `ORDER BY` ni
ordinal de entrada.

No se crea índice ni se ejecuta `ANALYZE` automáticamente. Ambos tienen coste dependiente
de cardinalidad y se decidirán con benchmarks de Phase 14, no como requisito de
correctness.

### Errores

Los fallos JDBC/COPY se presentan como `BulkException` con stage sin incluir keys y
preservan `CopyExecutionException`, `SQLException`, SQLState y vendor details en la cadena.
Errores runtime del accessor o callback conservan identidad. Cualquier fallo evita devolver
un resultado parcial; cleanup secundario nunca reemplaza la causa principal.

## Alternativas descartadas

- Devolver `ResultSet`: traslada ownership de Statement, conexión y temporal fuera del
  motor.
- Materializar filas JDBC neutrales: fija una representación y memoria `O(matches)` sin
  ayudar a Hibernate.
- Devolver sólo SQL sin scope: permite ejecutar SELECT cuando la temporal ya no existe o
  sobre otra conexión.
- Exponer ahora un callback público: aún no existe consumidor cross-module probado.
- Deduplicar keys en Java: requiere memoria `O(N)` y equality/hash adecuados para keys
  compuestas.
- UNIQUE temporal: COPY fallaría ante duplicados en vez de dar semántica idempotente.
- `SELECT DISTINCT target.*`: compara toda la fila, puede ser costoso y altera el problema;
  DISTINCT se aplica sólo a keys antes del JOIN.
- Soportar autocommit con `PRESERVE ROWS`: aumenta riesgo de contaminación en conexiones
  pooled y debilita la frontera transaccional que Phase 9 necesita.

## Validación requerida

Tests unitarios fijan SQL/orden/quoting, nombres, empty, autocommit, single-pass/lazy,
nulls, duplicates, conteos, misma conexión y cleanup/suppression. Testcontainers valida
simple/composite, schema/nombres quoted, CTAS domain/typmod/collation, empty, one-shot,
20.000 keys, missing/duplicates/null, commit/rollback/read-only, cleanup success/failure,
secuencial, reentrante y concurrencia entre conexiones.

## Consecuencias

Phase 7 entrega un motor relacional completo pero interno. ADR-006 podrá aceptarse tras la
evidencia PostgreSQL; ADR-010 permanece ACCEPTED como decisión de diferir la firma pública.
Phase 8 puede producir metadata Hibernate sin conocer pgJDBC. Phase 9 deberá componer ambos,
garantizar la misma conexión física y decidir si usa el callback JDBC, eleva el scope de
temporal a SPI o ejecuta una native query dentro de ese scope.

## Resolución posterior

ADR-017 publica el callback mínimo mediante `PostgresBulkJdbcOperations<T>` y el fragmento
Spring Data ejecuta dentro de él una native query JPA que materializa entidades antes del
cleanup. La coincidencia de sesión física se prueba con `pg_backend_pid()`.
