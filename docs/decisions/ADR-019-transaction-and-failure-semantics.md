# ADR-019: Ownership transaccional y recuperación explícita ante fallos

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Insert y lookup ya comparten una conexión prestada, pero una release estable necesita cerrar qué
ocurre ante fallo en cada etapa, batches parciales, transacciones PostgreSQL abortadas, propagación
Spring, cleanup, reutilización de pool y pérdida física. Reintentar, compensar o recuperar dentro de
la librería podría violar el scope del caller y duplicar efectos.

## Decisión

### Ownership y atomicidad

El caller o framework posee `Connection` y transacción. La librería nunca hace `close`, `commit`,
`rollback`, `setAutoCommit`, `setReadOnly`, cambia isolation ni crea savepoints. El executor posee
`CopyIn`; lookup posee su tabla temporal y cleanup. Con `autoCommit=false`, rollback del caller
revierte todos los batches. Con `autoCommit=true`, cada COPY finalizado puede persistir aunque un
batch posterior falle; no hay compensación ni resultado parcial.

### PostgreSQL abortado

Un fallo SQL puede dejar una conexión abierta pero su transacción en estado `25P02`. La librería
no intenta recuperarla: el caller debe hacer rollback. La misma conexión vuelve a ser usable tras
rollback; una conexión físicamente muerta no se recupera y el pool debe descartarla.

### Fallo primario y cleanup

El primer fallo conserva primacía. Errores posteriores de `cancelCopy` y DROP se añaden como
suppressed. Runtime/Error de iterator, accessor, converter y callback no se envuelven en el engine;
fallos JDBC/I/O llevan contexto sin perder `SQLException`, SQLState o detalles del driver. Ningún
mensaje incorpora filas, entidades, keys o valores CSV.

### Spring

- `REQUIRED` crea transacción sin outer y participa en una existente.
- Un runtime no capturado provoca rollback por reglas default. Si un outer captura un fallo de un
  participante REQUIRED, queda rollback-only; un fallo SQL deja además PostgreSQL abortado y la
  completion produce `UnexpectedRollbackException`.
- `REQUIRES_NEW` usa transacción física independiente: un inner fallido se revierte y el outer
  puede confirmar; un inner exitoso permanece aunque el outer se revierta.
- En la integración JPA, `NESTED` es **UNSUPPORTED**: `HibernateJpaDialect` no expone savepoints.
  En Spring Data JDBC es **SUPPORTED con condiciones** por ADR-029 cuando
  `JdbcTransactionManager` o `DataSourceTransactionManager` posee el mismo `DataSource`; la
  librería nunca crea ni manipula el savepoint.
- read-only se rechaza antes de metadata/conexión/COPY/DDL y nunca se desactiva internamente.
- Una invocación directa del delegate sin proxy/transacción activa se rechaza.

Spring Data aplica su interceptor estándar: `BulkException` permanece sin traducción;
`IllegalArgumentException`/`IllegalStateException` de productor pueden aparecer como
`InvalidDataAccessApiUsageException` con el runtime original como causa. No se añade traducción
manual ni `rollbackFor` redundante.

### Pool, pérdida física y retry

Hikari con pool de tamaño uno debe devolver una sesión limpia tras éxito o rollback: autocommit,
read-only, isolation, schema/search_path y temporales sin contaminación. Ante
`pg_terminate_backend`, el fallo y SQLState siguen visibles; el rollback del owner atraviesa el
proxy, Hikari marca `08006`, descarta el socket y la siguiente adquisición es saludable.

No existe retry automático. Autocommit, batches ya confirmados, input one-shot y estado de commit
incierto hacen inseguro un retry genérico. Idempotencia y retry pertenecen a aplicación y deben usar
un nuevo scope transaccional e input reproducible.

## Evidencia

Tests deterministas y PostgreSQL 15.18 cubren todos los stages principales, constraints NOT
NULL/CHECK/UNIQUE/FK, `25P02`, tres batches, iterator/accessor/converter, `endCopy`/`cancelCopy`,
CREATE/COPY/SELECT/callback/DROP, REQUIRED/REQUIRES_NEW/NESTED/read-only/rollback-only, Hikari
size 1, 100 operaciones, ocho threads y `pg_terminate_backend`. La matriz completa vive en
[`transactions-and-failures.md`](../architecture/transactions-and-failures.md).

Fuentes primarias:

- [Spring: transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [Spring: `JpaTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)
- [PostgreSQL: error codes](https://www.postgresql.org/docs/current/errcodes-appendix.html)
- [pgJDBC: `CopyIn`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyIn.html)

## Consecuencias

No aparecen tipos públicos, dependencias productivas ni lógica de recuperación. Se conservan las
fronteras de ADR-013/014/015/017. El contrato exige que el caller no continúe usando una
transacción SQL fallida y dimensione `REQUIRES_NEW` según su pool. Phase 12 puede añadir
observabilidad alrededor de estas fronteras sin alterar su semántica.

## Resolución de Phase 12

ADR-020 añade observabilidad fail-open alrededor de la llamada pública sin asumir ownership
transaccional. El mismo throwable continúa siendo primario y se relanza por identidad; cualquier
fallo de handlers, scopes o meters se suprime o ignora y nunca sustituye el resultado ni el error
bulk. Los totales de filas y batches sólo se incrementan al completar con éxito, de modo que un
fallo después de progreso parcial no publica progreso engañoso.

## Resolución Spring Data JDBC J5

ADR-029 conserva ownership, primacía/suppressed, privacidad y ausencia de retry. PostgreSQL prueba
rollback-only/`25P02`, ambas direcciones de `REQUIRES_NEW`, NESTED con dos managers JDBC, pérdida de
backend, recuperación Hikari, 100 repeticiones y ocho threads. Dos managers locales JPA/JDBC no se
coordinan aunque compartan `DataSource`; no se promete atomicidad cross-stack.
