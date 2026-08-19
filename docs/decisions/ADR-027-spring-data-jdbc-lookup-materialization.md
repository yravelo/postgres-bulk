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
