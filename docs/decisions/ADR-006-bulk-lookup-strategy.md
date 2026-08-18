# ADR-006: Lookup inicial con tabla temporal + COPY + JOIN

- **Estado:** PROPOSED
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

## Propuesta

Adoptar una estrategia `TemporaryTableBulkLookup` en v1 y conservar una frontera para otras estrategias. Hacer un spike comparativo CTAS vs LIKE en PostgreSQL 15–18 para domains, collations, custom types, generated/identity, tablas particionadas y permisos. Elegir la menor DDL que preserve tipos sin heurística; no fijar todavía CTAS o LIKE.

La API recibirá valores de clave, no entidades parciales. La clave compuesta será un tipo explícito con componentes ordenados por metadata. Por defecto se propone rechazar componentes null, deduplicar input conservando primer orden y no prometer orden de salida salvo reconstrucción explícita; estos tres puntos requieren tests de UX antes de aceptar.

## Validación para aceptar

Testcontainers PostgreSQL 15–18; 1/10k keys; clave simple/compuesta; duplicates/null; custom schema/quoted identifiers/domains; autocommit; commit/rollback/readOnly/REQUIRES_NEW; fallo COPY y cleanup en conexión reutilizada. Todas las sentencias deben usar la misma conexión.

## Consecuencias

La estrategia tiene coste fijo y no será óptima para listas diminutas; no se introduce selección automática sin benchmark. El resultado puede requerir reorder en memoria si se promete orden. `ON COMMIT DROP` no sustituye cleanup ni una política transaccional clara.
