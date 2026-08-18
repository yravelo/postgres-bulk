# Transacciones y modelo de fallos

## Alcance

Este documento fija el contrato de Phase 11 para `bulkInsert` y
`findAllByBulkKey`. Describe ownership, atomicidad, recuperación y la excepción que ve el
caller. No añade retries, transacciones distribuidas, timeouts propios ni observabilidad.

## Ownership

| Recurso | Owner | Responsabilidad de la librería |
|---|---|---|
| `Connection` JDBC | caller o framework transaccional | usarla durante la llamada y devolver el control sin cerrarla ni reconfigurarla |
| transacción física/lógica | caller, Spring y su transaction manager | participar en el scope existente; nunca `commit`, `rollback` ni crear savepoints |
| protocolo `CopyIn` | executor pgJDBC | terminarlo con `endCopy` en éxito o intentar `cancelCopy` tras un fallo |
| tabla temporal de lookup | engine lookup | crear, consumir y ejecutar `DROP TABLE IF EXISTS`; `ON COMMIT DROP` es la defensa final |
| iterator, callback y resultado materializado | caller/adaptador invocante | entregar un input válido y no conservar recursos JDBC fuera del callback |

Una conexión prestada nunca recibe `close`, `commit`, `rollback`, `setAutoCommit`,
`setReadOnly` ni cambios de isolation por parte de la librería. El caller conserva la
responsabilidad de completar la transacción, y debe hacer rollback después de un error SQL
que haya dejado PostgreSQL en estado abortado.

## Matriz de fallos por etapa

La columna "estado tx" describe el estado observable esperado después del fallo. `aborted`
significa que una sentencia posterior falla con SQLState `25P02` hasta rollback. La matriz
es el contrato validado por la fault injection de Phase 11; las diferencias introducidas por el
proxy Spring se detallan más abajo.

| Etapa | Fallo primario | Excepción visible | Cleanup | Estado tx | Estado conexión | Fallo secundario | Responsabilidad del caller |
|---|---|---|---|---|---|---|---|
| resolución de metadata | mapping no representable o introspección | `BulkException`; causa de Hibernate/JPA preservada | ninguno; aún no hay JDBC | sin iniciar/sin cambio | no adquirida | ninguno | corregir mapping/configuración |
| preparación de encoding | tipo relacional no soportado o metadata inválida | `BulkException`/excepción de argumento original | ninguno; aún no se consume input ni hay JDBC | sin iniciar/sin cambio | no adquirida | ninguno | corregir metadata/tipo |
| `iterator()` / `hasNext()` / `next()` antes de COPY | runtime/Error del iterable | misma instancia runtime/Error | ninguno | sin cambio | no adquirida o prestada y usable | ninguno | decidir rollback según su scope, aunque no hubo SQL |
| `hasNext()` / `next()` durante COPY | runtime/Error del iterable | misma instancia runtime/Error | `cancelCopy` si sigue activo | puede quedar `aborted` | abierta; requiere rollback antes de reutilizar | error de cancelación queda suppressed | rollback y no reintentar el mismo iterable automáticamente |
| accessor de valor | runtime/Error de lectura | misma instancia runtime/Error | `cancelCopy` si sigue activo | puede quedar `aborted` | abierta; requiere rollback antes de reutilizar | error de cancelación queda suppressed | rollback; corregir objeto/accessor |
| `AttributeConverter` | runtime de Hibernate/JPA/converter | misma excepción runtime expuesta por el accessor preparado | `cancelCopy` si sigue activo | puede quedar `aborted` | abierta; requiere rollback antes de reutilizar | error de cancelación queda suppressed | rollback; corregir converter/dato |
| inicio COPY (`unwrap`, `getCopyAPI`, `copyIn`) | `SQLException`/protocolo | `BulkException` con `SQLException` y SQLState accesibles en causas | cancelar sólo si existe `CopyIn` activo | sin cambio si no llegó al servidor; si llegó, puede quedar `aborted` | abierta salvo fallo físico | cancelación suppressed | rollback si la transacción fue alcanzada; descartar si la conexión es inválida |
| escritura/stream COPY | `IOException`, `SQLException`, constraint o pérdida backend | `BulkException` conservando causa JDBC/I/O y SQLState | `cancelCopy` si sigue activo | normalmente `aborted` con transacción manual/Spring | abierta tras fallo SQL; inválida tras pérdida física | cancelación suppressed | rollback; el pool valida/descarta una conexión rota |
| finalización `endCopy` | rechazo servidor, I/O o `SQLException` | `BulkException` conservando causa y SQLState | `cancelCopy` si sigue activo | normalmente `aborted` | abierta o inválida según causa | cancelación suppressed | rollback o permitir que Spring lo haga |
| cancelación `cancelCopy` | `SQLException` durante cleanup | nunca reemplaza el fallo primario; queda suppressed | no hay segundo intento ni `endCopy` | indeterminado/posible `aborted` | no asumir reutilizable hasta rollback/validación | es el propio fallo secundario | rollback; pool/driver decide validez física |
| transición entre batches | overflow de conteo o siguiente batch falla | `BulkException` para protocolo/conteo; runtime original para productor | COPY activo se cancela; batches previos no se compensan | manual tx: reversible; autocommit: batches previos persistidos | prestada, abierta si no hubo pérdida física | cleanup suppressed | rollback para atomicidad; aceptar persistencia parcial con autocommit |
| lookup `CREATE TEMP` | SQL/permiso/read-only/columna inválida | `BulkException` con causa SQL | intentar `DROP` sólo si CREATE se completó | `aborted` tras fallo SQL | abierta; requiere rollback | DROP suppressed | rollback |
| lookup COPY de keys | productor, constraint/tipo, I/O o SQL | runtime original o `BulkException` con causa | cancelar COPY y después intentar DROP | normalmente `aborted` | abierta o inválida | cancel/Drop suppressed en el primario | rollback |
| lookup SELECT/materialización JPA | SQL, mapping o materialización | `BulkException`/runtime de JPA conservando causa | intentar DROP | SQL: normalmente `aborted`; runtime local: puede seguir activa | abierta salvo pérdida física | DROP suppressed | rollback ante fallo SQL o si Spring marca rollback-only |
| callback lookup | runtime/Error del callback | misma instancia runtime/Error | intentar DROP | sin cambio si fue puramente local | abierta | DROP suppressed | decidir rollback; Spring lo hace por runtime por defecto |
| lookup DROP | `SQLException` | si no había fallo previo, `BulkException`; si lo había, suppressed | `ON COMMIT DROP` o rollback | fallo SQL deja `aborted` | abierta; requiere rollback | se adjunta al fallo previo cuando es cleanup | rollback |
| completion Spring | commit/rollback o synchronization falla | excepción del transaction manager; `UnexpectedRollbackException` cuando corresponde | propiedad de Spring/JPA/pool | completada o desconocida según fallo | Spring libera/descarta según validez | nunca se sustituye dentro del engine | tratar el resultado como no confirmado salvo commit demostrado |
| release al pool | validación/reset físico falla | fuera de la API bulk; excepción del pool/framework cuando la exponga | pool restaura estado o descarta | scope terminado | no vuelve al pool si es inválida | propiedad del pool | dimensionar/monitorizar pool y no retener handles |

## Regla de excepción primaria

El primer fallo de la operación es siempre el primario. Un fallo posterior de
`cancelCopy`, `DROP` u otro cleanup se añade mediante `Throwable.addSuppressed` y nunca
reemplaza tipo, identidad, causa o stack trace del primario. Las excepciones runtime y
`Error` originadas por iterator, accessor, converter o callback conservan identidad. Los
fallos JDBC/I/O pueden envolverse con contexto de etapa/batch, pero la `SQLException`, su
SQLState y sus detalles del driver permanecen alcanzables en la cadena de causas. Los
mensajes no incluyen entidades, keys, campos CSV ni valores de columnas.

## Atomicidad por modo

- `autoCommit=false`: todos los COPY de una invocación comparten la transacción prestada;
  rollback del caller revierte batches completos y parciales.
- `autoCommit=true`: cada COPY terminado puede quedar confirmado. Un batch posterior que
  falle no se compensa y la llamada no devuelve un resultado parcial.
- Spring `REQUIRED`: sin scope exterior crea una transacción; con scope exterior participa
  en la misma transacción física. Un runtime no capturado provoca rollback por defecto.
- Spring `REQUIRES_NEW`: suspende el scope exterior y usa una transacción física
  independiente; su éxito o rollback no se mezcla con el resultado exterior.
- Spring `NESTED`: **UNSUPPORTED** en la baseline Hibernate 6.6 +
  `HibernateJpaDialect` + `JpaTransactionManager`. Con el default falla por configuración;
  incluso con `nestedTransactionAllowed=true` falla antes del trabajo bulk porque el dialecto
  JPA no expone savepoints. La librería no crea savepoints propios ni promete una variante JDBC
  parcial.
- read-only: insert y lookup se rechazan en la frontera Spring antes de metadata/conexión y
  sin efectos en base de datos. La librería nunca desactiva read-only.

## PostgreSQL abortado y reutilización

Después de un fallo SQL dentro de una transacción, PostgreSQL rechaza sentencias con
`25P02` hasta rollback. La librería no intenta esconder ese estado ni iniciar una segunda
transacción. Tras rollback, la misma conexión física debe poder ejecutar otra operación y
no debe contener temporales `pgbulk_keys_%`; después de pérdida de backend, el pool debe
descartar la conexión inválida y una adquisición posterior debe resultar saludable. En Hikari,
el owner intenta rollback a través del proxy; SQLState `08006` hace que el pool marque la conexión
como rota y la reemplace. No se intenta recuperar el socket físico muerto.

No existe retry automático. Repetir un iterable one-shot, un callback con efectos o una
operación cuya visibilidad transaccional sea incierta podría duplicar datos o efectos. La
decisión de retry pertenece al caller y debe ocurrir sobre un scope transaccional nuevo y
un input reproducible.

## Rollback-only y traducción Spring

Un fallo unchecked que cruza el método repository `REQUIRED` aplica las reglas de rollback
default de Spring. Si una transacción exterior captura el fallo, el scope físico queda a la vez
marcado rollback-only y, para fallos SQL, abortado en PostgreSQL. Java puede continuar, pero otra
consulta obtiene `25P02`; cuando el exterior intenta completar, Spring lanza
`UnexpectedRollbackException` y no persiste filas.

El engine pgJDBC conserva identidad para runtime/Error del iterator, accessor y callback. En el
proxy Spring Data, el interceptor de traducción puede envolver `IllegalArgumentException` o
`IllegalStateException` en `InvalidDataAccessApiUsageException`; la instancia original permanece
como causa. `BulkException` de COPY/constraint se conserva como tal y mantiene la `SQLException` y
SQLState en su cause chain. La precondición read-only también usa
`InvalidDataAccessApiUsageException`.

## Evidencia Phase 11

- PostgreSQL real confirma tres batches con fallo en el tercero: rollback manual deja cero filas;
  autocommit conserva cuatro filas de los dos COPY completados.
- SQLState `23502`, `23514`, `23505`, `23503`, `42P01`, `25P02` y `08006` permanecen alcanzables.
- Fault doubles cubren startup, write, `endCopy`, `cancelCopy`, CREATE, COPY, SELECT, callback y
  DROP, incluida la regla primary/suppressed.
- Spring confirma `REQUIRED`, rollback exterior multibatch, rollback-only,
  `UnexpectedRollbackException`, `REQUIRES_NEW` aislado en éxito/fallo, read-only y NESTED
  unsupported incluso con opt-in.
- Hikari `maximumPoolSize=1` confirma misma sesión después de insert/lookup/fallos con rollback,
  estado JDBC restaurado, cero temporales, 100 operaciones secuenciales y reemplazo tras terminar
  el backend.
- Ocho threads ejecutan transacciones y conexiones independientes sobre el mismo repository
  singleton sin compartir estado de operación.

## Fuentes primarias

- [PostgreSQL: códigos de error](https://www.postgresql.org/docs/current/errcodes-appendix.html)
- [PostgreSQL: COPY](https://www.postgresql.org/docs/current/sql-copy.html)
- [Spring Framework: propagación transaccional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [Spring Framework: `JpaTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)
- [pgJDBC: `CopyIn`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyIn.html)
- [pgJDBC: `CopyOperation`](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/copy/CopyOperation.html)
