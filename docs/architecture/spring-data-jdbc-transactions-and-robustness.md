# Spring Data JDBC: transacciones, coexistencia y robustez

## Contrato

El fragmento JDBC ejecuta insert y lookup dentro de la transacción Spring elegida por el
consumidor. `JdbcOperations` entrega la conexión física enlazada al thread y postgres-bulk sólo la
usa durante la llamada. Nunca hace `close`, `commit`, `rollback`, `rollback(savepoint)`,
`setAutoCommit`, `setReadOnly`, `setTransactionIsolation`, `setSchema`, `setSavepoint` ni
`releaseSavepoint`.

Input vacío es un no-op anterior a la precondición transaccional. Input no vacío requiere una
transacción activa, read-write y una conexión con `autoCommit=false`; no existe fallback de alto
nivel a autocommit. El primer fallo es primario, el cleanup posterior es suppressed y la librería
no reintenta.

## Matriz transaccional

| Boundary | Insert | Lookup | Conexión/resultado |
| --- | --- | --- | --- |
| llamada directa al repository | `REQUIRED` crea y confirma | `REQUIRED` crea y confirma | manager configurado por Spring Data |
| outer `REQUIRED` confirma | participa y confirma con outer | participa y elimina temporal | mismo PID y mismo resultado transaccional |
| outer `REQUIRED` revierte | filas revertidas | sin estado persistente | caller/manager hace rollback |
| fallo participante capturado | marca rollback-only | marca rollback-only | completion exterior lanza `UnexpectedRollbackException` |
| SQL capturado en `REQUIRED` | PostgreSQL queda abortado | PostgreSQL queda abortado | siguiente SQL da `25P02`; sólo rollback recupera |
| inner `REQUIRES_NEW` confirma, outer revierte | inner permanece | resultado inner terminó antes del outer | PID/transacción física independiente |
| inner `REQUIRES_NEW` falla, outer confirma | inner se revierte | temporal inner desaparece | outer vuelve a ser usable y confirma |
| inner `NESTED` confirma | participa | participa | savepoint propiedad del manager |
| inner `NESTED` falla | rollback al savepoint | rollback elimina efectos/temporal inner | outer continúa si captura el fallo |
| read-only | rechazo temprano | rechazo temprano | no metadata, conexión, COPY o DDL bulk |
| sin transacción, delegate directo | rechazo temprano | rechazo temprano | no autocommit accidental |

`NESTED` queda **SUPPORTED con condiciones** para `JdbcTransactionManager` y
`DataSourceTransactionManager`: el manager debe controlar el mismo `DataSource` que el
`JdbcOperations` efectivo y tener soporte nested habilitado. Los tests prueban ambos managers con
insert/lookup, éxito y rollback a savepoint. No es una promesa para `JpaTransactionManager`, JTA,
managers arbitrarios ni data sources distintos.

## Matriz de fallos

| Etapa | Fallo visible | Estado PostgreSQL | Ownership/cleanup | Reutilización |
| --- | --- | --- | --- | --- |
| iterator `hasNext`/`next` | runtime original o traducción estándar del proxy | sin SQL o progreso no confirmado | rollback del manager | sí tras rollback |
| accessor/converter | causa original preservada, sin valores en mensaje | COPY puede estar activo | executor cancela; manager revierte | sí tras rollback |
| CREATE TEMP | `BulkException` con `SQLException`/SQLState | puede quedar abortado | no existe temporal que dropear | sí tras rollback |
| COPY/start/write/end | fallo primario; cancel secundario suppressed | normalmente abortado | executor posee `CopyIn`; manager posee tx | sí tras rollback |
| SELECT | fallo primario con SQLState | abortado (`25P02` después) | se intenta DROP; error secundario suppressed | sí tras rollback |
| mapper/callback | runtime original | SQL puede seguir válido | se intenta DROP en la misma conexión | sí si cleanup/rollback termina |
| DROP | fallo de DROP primario si lo anterior tuvo éxito | según SQLState | `ON COMMIT DROP`/rollback limita fuga | sí tras rollback |
| backend terminado | causa/SQLState del driver visibles | conexión muerta | manager intenta rollback; Hikari descarta socket | siguiente borrower sano |

No se publican filas, entidades, keys, valores CSV ni credenciales en mensajes. Los nombres SQL
derivados de metadata sí pueden aparecer como contexto técnico. No se devuelve resultado parcial:
`BulkWriteResult` sólo existe tras completar la llamada.

## Selección de infraestructura

La librería no elige por orden ni adivina entre candidates:

- varios `JdbcOperations` sin primary/qualifier producen `NoUniqueBeanDefinitionException`;
- varios `DataSource` requieren que el consumidor construya y seleccione el `JdbcOperations`
  correcto;
- varios transaction managers requieren `@Primary`, qualifier o `transactionManagerRef` explícito;
- cada `JdbcOperations`, transaction manager y repository debe apuntar al mismo `DataSource`;
- la auto-selección y el back-off de Boot pertenecen a J6.

Los tests levantan dos data sources reales. Sin selección, el contexto falla; con selección
explícita, cada repository escribe sólo en su base, demostrado mediante `application_name`. Dos
transaction managers JDBC también fallan de forma explícita si una frontera `@Transactional` no
está cualificada, y funcionan cuando el consumidor selecciona uno.

## Coexistencia JPA + JDBC

Los artifacts y repositories JPA/JDBC pueden convivir. Cada fragmento separado usa su propio
mapping (`EntityManager`/Hibernate o `JdbcConverter`/`JdbcOperations`) y un repository que hereda
ambos fragments se rechaza explícitamente.

Dos transaction managers locales no forman una transacción coordinada aunque compartan el mismo
`DataSource`. La caracterización real demuestra:

- llamadas independientes de ambos repositories funcionan en el mismo contexto cuando cada
  repository declara su infraestructura;
- outer JPA seguido de repository JDBC puede usar el mismo PID y aun así el manager JDBC puede
  confirmar antes del rollback exterior; no hay atomicidad cross-manager;
- outer JDBC seguido de JPA falla claramente al intentar enlazar un segundo recurso para el mismo
  `DataSource`; el outer JDBC se revierte;
- con data sources separados no existe atomicidad ni coordinación: cada repository sólo debe usar
  su infraestructura y una operación distribuida requiere una solución del consumidor.

Por tanto, no se debe anidar un repository gobernado por un manager local distinto esperando una
unidad atómica. Usar un único owner transaccional compatible para el recurso, separar los scopes o
adoptar coordinación externa es responsabilidad de la aplicación. postgres-bulk no implementa
JTA ni encadena managers.

## Robustez y pool

Una prueba Hikari de tamaño uno ejecuta 100 operaciones secuenciales, fallos y lookups, y compara
antes/después `autoCommit`, read-only, isolation, schema y `search_path`. No queda ninguna tabla
temporal `pgbulk_*`, COPY activo ni conexión prestada. Ocho threads comparten las mismas instancias
inmutables y obtienen exactamente sus filas sin estado temporal cruzado.

Tras `pg_terminate_backend`, la excepción llega al caller; Hikari invalida la conexión y la
siguiente adquisición usa otro PID y completa insert/lookup. postgres-bulk no inspecciona el pool,
no reemplaza conexiones y no intenta continuar una transacción abortada.

## Destinos multi-schema MS5

Los overloads JDBC target-aware conservan íntegramente esta matriz. A y B pueden participar en el
mismo `REQUIRED` y backend porque COPY/CTAS/JOIN usan identifiers qualified; rollback revierte
ambos. `REQUIRES_NEW` y NESTED condicionado siguen perteneciendo al manager. Read-only, no-tx y
`25P02` no tienen bypass target-aware. Hikari reutiliza A→B sin restaurar schema/search path y el
lookup no deja temporales después de éxito, fallo o rollback a savepoint.

## Composición Boot MS6

El starter Boot repite default+A/B/C, commit/rollback, `REQUIRES_NEW`, read-only, quoting,
conflictos, SQLState y concurrencia sin crear manager ni alterar esta matriz. Con varios managers o
data sources, la aplicación mantiene selección explícita; Boot hace back-off ante candidatos JDBC
ambiguos. La coexistencia con JPA no añade atomicidad cross-manager.

## Retry

No hay retry automático para fallos de productor, conversión, SQL, conexión o commit incierto.
El input puede ser one-shot y un batch podría haberse confirmado fuera del contrato Spring. Si una
aplicación conoce su idempotencia, debe abrir una transacción nueva, obtener una conexión nueva y
recrear el input.

## Fuentes oficiales

- [Spring Framework: conexiones JDBC](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)
- [Spring Framework: propagación](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- [Spring Data JDBC: transacciones](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/transactions.html)
- [PostgreSQL: códigos de error](https://www.postgresql.org/docs/current/errcodes-appendix.html)
- [PostgreSQL: rollback a savepoint](https://www.postgresql.org/docs/current/sql-rollback-to.html)
- [PostgreSQL: tablas temporales](https://www.postgresql.org/docs/current/sql-createtable.html)
