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
transaction manager. La implementación soporta varias persistence units si un domain type
pertenece a una sola, limitación propia de `JpaContext`.

## Resolución de Phase 11

ADR-019 cierra `NESTED` como **UNSUPPORTED** en la baseline: el default lo rechaza y habilitar
`nestedTransactionAllowed` sigue produciendo `NestedTransactionNotSupportedException` porque
`HibernateJpaDialect` no ofrece savepoints. REQUIRED, REQUIRES_NEW éxito/fallo, read-only,
rollback-only y `UnexpectedRollbackException` quedan probados. La traducción estándar de Spring
puede envolver `IllegalArgumentException`/`IllegalStateException` en
`InvalidDataAccessApiUsageException`, preservando el original como causa; `BulkException` conserva
su tipo y la cadena hasta SQLState.

## Resolución de Phase 12

La observación se abre dentro del fragmento, una vez que el interceptor `REQUIRED` ya ha creado o
unido la transacción, y cubre exactamente una llamada pública `bulkInsert` o
`findAllByBulkKey`. No se añade otro proxy ni se altera la propagación. El cierre ocurre al devolver
o lanzar desde el fragmento, antes de que Spring complete una transacción exterior; por eso
`outcome=success` describe la operación bulk, no el commit final del caller.

## Resolución de Phase 13

El descubrimiento del fragmento externo, insert, lookup y sus límites transaccionales pasan con
Spring Data JPA 3.5.0 y 3.5.13 dentro de los BOM Boot 3.5.0/3.5.16. No se prueba Spring Data 4 en
este artefacto; ADR-021 lo clasifica junto con Boot 4/Hibernate 7 como otra generación.
