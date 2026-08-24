# ADR-032: SQL target-qualified sin mutar schema de conexión

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-24

## Contexto

PostgreSQL permite iguales nombres de tabla en schemas distintos. Un nombre no cualificado se
resuelve por `search_path`: gana el primer match, y cualquier schema writable incluido en ese path
amplía la frontera de confianza. pgJDBC ofrece `currentSchema`/`Connection.setSchema` como formas
de influir en ese estado. En una conexión pooled o compartida por JPA/JDBC, cambiarlo exige
restauración correcta en éxito, fallo, transacción abortada y pérdida de backend.

Los contratos aceptados ADR-013/019/026/029 prohíben que postgres-bulk reconfigure una conexión
caller-owned. El quoter actual ya construye identifiers a partir de componentes separados y puede
emitir `"schema"."table"` sin estado de sesión.

Fuentes primarias:

- [PostgreSQL: schemas y search path](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [pgJDBC: `currentSchema`](https://jdbc.postgresql.org/documentation/use/)
- [Spring Framework: recursos JDBC transaction-aware](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)

## Decisión

- Cada target runtime debe ser schema-qualified y cada sentencia target-specific debe usar ese
  nombre: COPY INSERT, CTAS y JOIN.
- Reutilizar `PostgresIdentifierQuoter`: schema, tabla y columnas permanecen componentes
  estructurados, se citan por separado, duplican quotes internas y rechazan NUL.
- No aceptar SQL libre, un `schema.table` preconcatenado, templates ni identifiers derivados por la
  librería desde tenant ids.
- No invocar `Connection.setSchema`, `SET SCHEMA`, `SET search_path`, `SET LOCAL search_path` ni
  configuración pgJDBC `currentSchema`. No implementar restore best-effort.
- Un mapping existente sin schema y sin target explícito conserva su resolución histórica por el
  ambiente de conexión. Se clasifica como compatibilidad/default, no como mecanismo multi-schema.
- Las tablas temporales internas permanecen session-local y no convierten `search_path` en selector
  del target de negocio.
- La misma conexión/transacción puede ejecutar A→B porque cada SQL es autocontenido. La librería no
  limita varias schemas en una transacción ni cambia ownership, propagación o cleanup.
- La aplicación debe autorizar/mapear el target; quoting previene inyección sintáctica pero no
  autoriza acceso. PostgreSQL conserva `USAGE`/privilegios de objetos como control efectivo.
- Schema/target no aparece en tags de observabilidad ni logs propios. La causa `SQLException` se
  preserva aunque el servidor pueda incluir nombres físicos.

## Alternativas

| Alternativa | Evaluación |
| --- | --- |
| `Connection.setSchema` + restore | rechazada: mutación compartida, leakage y restore bajo fallo |
| `SET LOCAL search_path` | rechazada: sigue siendo estado transaccional ambiental y afecta otras sentencias |
| datasource/pool por schema dentro de la librería | rechazada: routing y credentials pertenecen a aplicación |
| SQL no cualificado con path preconfigurado | sólo compatibilidad existente; no garantiza target explícito |
| SQL qualified por componentes | aceptada: determinista, local y coherente con ownership |

## Consecuencias

El coste es repetir el schema citado en SQL. A cambio, no hay estado que restaurar, un repository
singleton es seguro entre targets y database-per-tenant externo sigue funcionando sin interferencia.
La librería no provisiona schemas, no consulta privilegios y no soporta row-level tenancy.

MS1 aceptó la resolución neutral mediante `TableName.resolveRuntimeTarget`. MS2 demuestra la
estrategia con COPY real: target qualified local, misma conexión A→B, backend físico pooled
reutilizado, concurrencia, commit/rollback/fallo y ausencia de mutación de schema/search path. Esta
evidencia acepta la decisión arquitectónica. MS3 conforma CTAS y JOIN a la misma decisión y publica
el lookup target-aware; el lookup sin target continúa siendo compatibilidad basada en el mapping.

## Evidencia de aceptación MS2

- COPY target-specific qualified y structurally quoted en tests unitarios y PostgreSQL real;
- schema/tabla/columnas quoted, quotes internas preservadas y NUL rechazado por el quoter común;
- A→B en la misma conexión con `getSchema`/`search_path` sin cambios;
- pool con un backend físico reutilizado y ownership preservado;
- commit/rollback cross-schema y fallo `25P02` recuperable por rollback del caller;
- concurrencia sobre una fachada preparada y conexiones separadas;
- ausencia de mutadores, SQL de `SET search_path` y cache target-keyed;
- tags existentes sin schema/target;
- fallo de privilegios `42501` y SQLStates de schema/tabla inexistentes preservados, sin fallback.

La evidencia ejecutable de INSERT y su modelo de coste están en
[`multi-schema-bulk-insert.md`](../architecture/multi-schema-bulk-insert.md). MS3 aporta debajo la
conformance equivalente para CTAS/JOIN sin reabrir la decisión.

## Evidencia posterior MS3

- CTAS y JOIN reciben la misma cualificación estructural desde un `InvocationSql` local;
- A→B sobre misma conexión y mismo backend pooled no cambia `getSchema` ni `search_path`;
- misma metadata/key metadata soporta A/B secuencial y concurrente sin resultados cruzados;
- quoted identifiers, mapping estático idéntico y conflictos pre-SQL conservan la política común;
- objetos ausentes y permiso denegado preservan SQLState sin fallback;
- commit/rollback multi-schema, `25P02`, DROP suppressed y cleanup no alteran ownership;
- no existen mutadores de schema/path, observabilidad target-aware ni caches por `TableName`.

La evidencia detallada está en
[`multi-schema-bulk-lookup.md`](../architecture/multi-schema-bulk-lookup.md). ADR-032 permanece
`ACCEPTED` y no nace un ADR nuevo porque MS3 aplica, sin cambiarla, la decisión ya aceptada.

## Evidencia posterior MS4

El repository JPA propaga el mismo target qualified a COPY y lookup. `Session#doReturningWork` y la
native query comparten backend físico; A/B funciona en el mismo proxy, en una transacción y en dos
threads. Un schema quoted se materializa correctamente y no se añade `setSchema`, `search_path`,
hint Hibernate, target tag ni log propio. Spring Data JPA aplica así ADR-032 sin reabrirla; Boot y
Spring Data JDBC permanecen sin propagación.
