# ADR-017: Integración transaccional y API lookup de Spring Data

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 9 debe ejecutar DDL temporal, COPY y materialización ORM sobre la misma conexión física,
sin transferir ownership al motor pgJDBC ni depender de que un `DataSource` concreto esté
correctamente expuesto por `JpaTransactionManager`. También debe cerrar la firma pública de
lookup aplazada por ADR-010 y la semántica del persistence context.

## Decisión

- `PostgresBulkRepository<T, ID>` es un fragmento Spring Data opt-in con `bulkInsert` y
  `<K> findAllByBulkKey(keys, BulkKeyMetadata<K>)`. Recibe valores de clave tipados, nunca
  entidades parciales ni nombres de columnas sueltos.
- Los métodos tienen transacción Spring `REQUIRED`, read-write. Una transacción exterior manda;
  si es `readOnly`, la operación falla con `InvalidDataAccessApiUsageException` y nunca cambia el
  flag JDBC. No se introduce `REQUIRES_NEW` arbitrario.
- `JpaContext` elige el `EntityManager` por domain type. `JpaEntityMetadataResolver` recibe su
  `EntityManagerFactory` y cachea un resolver neutral por identidad de persistence unit.
- La conexión se obtiene con `EntityManager.unwrap(Session.class).doReturningWork`. Todo el motor
  caller-owned se ejecuta dentro del callback. Se descartan `DataSource.getConnection`,
  `DataSourceUtils` y unwrap directo a `Connection`: el primero ignora transacciones; el segundo
  depende de wiring `JpaDialect`/DataSource; el tercero no es portable ni garantizado por JPA.
- El callback lookup materializa mediante native query de JPA antes de eliminar la temporal. El
  query usa `FlushModeType.COMMIT`: la librería no hace flush ni clear implícitos y no adjunta al
  contexto las entidades insertadas por COPY. Un test compara `pg_backend_pid()` por JDBC y JPA.
- El motor pgJDBC publica una única fachada preparada, `PostgresBulkJdbcOperations<T>`, y un
  callback anidado para mantener el consumo dentro del scope. Sigue sin adquirir/cerrar conexión,
  commit, rollback ni mutar estado.
- Inputs vacíos se detectan con lookahead single-pass antes de metadata/conexión; Spring puede
  crear una transacción lógica por el interceptor, pero no se adquiere recurso JDBC.
- Los errores propios siguen `BulkException`; las precondiciones de uso Spring usan
  `InvalidDataAccessApiUsageException`. No se traducen causas SQL manualmente.

## Consecuencias

Duplicados de input se deduplican, missing keys se omiten, null keys/componentes se rechazan y el
orden no está definido, preservando ADR-015. `REQUIRES_NEW` funciona por suspensión normal del
transaction manager. `NESTED` no se promete: `JpaTransactionManager` no ofrece savepoints nested
por defecto y Phase 11 cerrará variantes. La implementación soporta varias persistence units
si un domain type pertenece a una sola, limitación propia de `JpaContext`.
