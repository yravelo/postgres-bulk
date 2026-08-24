# ADR-027: Materialización de bulk lookup Spring Data JDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

El engine pgJDBC ya ejecuta CREATE TEMP, COPY de keys, SELECT/JOIN y DROP en una conexión
caller-owned. Spring Data JDBC debe convertir el result set en aggregate roots sin adquirir otra
conexión, devolver recursos lazy, usar internals de Spring o provocar N+1 silencioso.

## Decisión

- El coordinador interno recibe `Class<T>`, keys y la `BulkKeyMetadata<K>` existente.
- Empty se resuelve con lookahead antes de transacción, metadata, conexión o SQL.
- Una llamada no vacía requiere transacción Spring activa/write y conexión física no autocommit.
- El adapter usa `JdbcOperations.execute(ConnectionCallback)` y delega el lifecycle completo a
  `PostgresBulkJdbcOperations.findAllByBulkKey`/`TemporaryTableBulkLookup` sin copiarlo.
- Se obtiene `RelationalPersistentEntity<T>` y `JdbcConverter` del resolver configurado y se usa el
  constructor público `EntityRowMapper(RelationalPersistentEntity, JdbcConverter)`.
- El callback ejecuta exactamente un `PreparedStatement` con el SQL entregado por el engine,
  consume y cierra todo el `ResultSet`, y retorna `List.copyOf` antes del DROP.
- Sólo se materializan roots aceptadas por el guard de metadata; no se resuelven children.
- No se ofrece orden, streaming, custom mapper público ni derivación automática de key.
- Runtime del materializador conserva identidad; SQLException conserva cause/SQLState y cleanup
  secundario queda suppressed según ADR-015/019.

## Consecuencias

La implementación usa sólo API pública Spring Data 3.5 y mantiene core/pgJDBC intactos. Los
converters de lectura, embedded, references y records usan el mapping configurado por la
aplicación. Toda la materialización ocupa memoria O(result size), mientras las keys siguen
single-pass/O(1) adicional. No puede retornarse `Stream` porque el resultado debe quedar separado
de la temporal y de la conexión prestada.

## Evidencia

PostgreSQL prueba claves simples/compuestas/convertidas, duplicates/missing/null, input grande,
schema quoted, converters de resultado, embedded, `AggregateReference`, record immutable, PID,
REQUIRED/REQUIRES_NEW/read-only/NESTED, un solo SELECT, fallos `42P01`/`25P02`, cleanup, pool reuse,
interoperabilidad insert/lookup y concurrencia. Unit tests prueban empty, one-shot, delegación,
ownership y validación posicional.

## Evidencia J4

Repositories reales resuelven su domain type mediante `RepositoryMethodContext` y delegan lookup
simple/compuesto al materializador J3. PostgreSQL confirma converters de lectura, duplicate/missing
keys, resultado completo y empty sin tabla física desde la interface pública. No se añade mapper,
streaming, key inference ni carga de children.

## Evidencia J5

Lookup NESTED funciona con ambos transaction managers JDBC. Ante SELECT fallido, el fallo `42P01`
permanece primario, el DROP en la transacción abortada queda suppressed con `25P02`, y el rollback
al savepoint del manager retira el estado temporal y permite continuar al outer. Concurrencia,
100 operaciones y Hikari size-one no dejan tablas `pgbulk_*` ni estado de conexión contaminado.

## Alternativas rechazadas

| Alternativa | Motivo |
| --- | --- |
| `DataSource.getConnection()` | Puede escapar de la conexión transaction-bound |
| `JdbcAggregateTemplate`/repository `findAll` | No acepta el result set del callback temporal ni garantiza la conexión exacta |
| Spring Data internals | Boundary inestable e innecesario |
| Mapper reflection propio | Duplica conversiones, constructors y embedded mapping |
| Resultado lazy/`Stream<T>` | Sobrevive al DROP y retiene recursos caller-owned |
| Cargar relations/children | Cambia root-only y puede introducir N+1 |
| Materializar keys | Rompe el contrato one-shot/O(1) del engine |

## Revisión J8 (2026-08-20)

El benchmark comparó SQL `IN` y temporary-table COPY/JOIN con el mismo target, índice,
`EntityRowMapper`, transacción y shape. SQL `IN` ganó los point estimates hasta 10K, mientras el
lookup compuesto confirmó viabilidad de la estrategia existente. No existe evidencia para un
threshold universal, index/ANALYZE automático o adaptive lookup. El ADR permanece `ACCEPTED`.

## Evidencia MS5 (2026-08-24)

El target runtime alimenta CTAS y JOIN y el mismo `EntityRowMapper` consume ese SELECT en la
conexión prestada. A/B, converters/embedded/reference, un SELECT, cleanup y SQLState permanecen
correctos; no se re-resuelve la tabla default. El ADR permanece `ACCEPTED`.
