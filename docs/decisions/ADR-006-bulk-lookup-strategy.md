# ADR-006: Lookup inicial con tabla temporal + COPY + JOIN

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

El lookup masivo es diferenciador, pero su rendimiento depende de cardinalidad, tipos y conexión. Reconstruir tipos desde Java es incorrecto. También deben decidirse clave simple/compuesta, duplicados, null y orden.

## Alternativas de transporte

1. **`VALUES`:** simple y bueno para pocos elementos; muchos parámetros/SQL grande y límites prácticos.
2. **`UNNEST` de arrays:** una ida y vuelta; complejo para tipos compuestos/custom/null y requiere arrays JDBC.
3. **Temporal + COPY + JOIN:** escala y reutiliza encoder; exige transacción, cleanup y una conexión física.

## Alternativas para la tabla

1. **Tipos explícitos generados:** sólo columnas necesarias; riesgo grave con domains, precision y custom types.
2. **`CREATE TEMP TABLE AS SELECT keys ... WITH NO DATA`:** columnas mínimas y sintaxis simple; hay que verificar qué propiedades/tipos se preservan.
3. **`CREATE TEMP TABLE ... (LIKE real)` completa:** PostgreSQL copia nombres/tipos de todas las columnas; puede añadir columnas/NOT NULL innecesarias y COPY sólo cargará keys.
4. **Consulta a `pg_catalog` + DDL:** control máximo; complejidad y privilegios/versiones.

## Decisión

Adoptar `TemporaryTableBulkLookup` como estrategia inicial interna y conservar la libertad
de comparar otras estrategias. La relación usa CTAS con proyección directa de las
columnas ordenadas de `BulkKeyMetadata` y `WITH NO DATA`. PostgreSQL deriva los tipos
físicos sin heurística Java → SQL.

La API futura recibirá valores de clave, no entidades parciales. Las keys compuestas usan
un tipo explícito y componentes ordenados. El input se conserva en COPY; el JOIN deduplica
la relación de keys con `SELECT DISTINCT`, rechaza keys/componentes null y no promete
orden. Keys sin match no producen fila y target duplicates producen todas sus filas.

Se requiere una única conexión con `autoCommit=false`. El lifecycle exacto, naming,
callback de resultado y cleanup quedan fijados por ADR-015.

## Evidencia de aceptación

PostgreSQL 15.18 verificó keys simples/compuestas, 20.000 keys one-shot, duplicates/null,
schema/nombres quoted, domain/typmod/collation, autocommit, commit/rollback/read-only,
fallos COPY/SELECT/callback, cleanup, reutilización, nesting y concurrencia en conexiones
distintas. La matriz PostgreSQL 16–18, tablas particionadas y permisos específicos queda
en Phase 13; no impide aceptar la estrategia v1 sobre la baseline probada.

## Consecuencias

La estrategia tiene coste fijo y no será óptima para listas diminutas; no se introduce
selección automática sin benchmark. Tampoco se crean índice ni estadísticas. Un futuro
contrato que prometa orden deberá reconstruirlo explícitamente. `ON COMMIT DROP` complementa
el DROP explícito y la política transaccional; no los sustituye.
