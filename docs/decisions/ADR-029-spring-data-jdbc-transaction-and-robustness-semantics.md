# ADR-029: Semántica transaccional y de robustez Spring Data JDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

J2-J4 probaron operaciones root-only y un fragmento público. Faltaba cerrar propagación,
savepoints, selección de infraestructura, convivencia JPA/JDBC, ownership y recuperación después
de fallos reales antes de añadir wiring Boot.

## Decisión

- Mantener `REQUIRED`, read-write y rechazo temprano sin transacción física.
- Preservar rollback-only y transacción PostgreSQL abortada; no limpiar `25P02` internamente.
- Aceptar `REQUIRES_NEW` como scope físico independiente y exigir capacidad de pool suficiente.
- Clasificar NESTED **SUPPORTED con condiciones** para `JdbcTransactionManager` y
  `DataSourceTransactionManager` que controlen el mismo `DataSource` del `JdbcOperations`. Los
  savepoints pertenecen exclusivamente al manager.
- Exigir selección explícita ante varios `DataSource`, `JdbcOperations` o transaction managers.
  No elegir por nombre, orden ni proximidad.
- Permitir artifacts/repositories JPA y JDBC en un contexto, pero prohibir el repository dual y no
  prometer atomicidad entre managers locales distintos, compartan o no `DataSource`.
- Conservar conexión caller-owned, fallo primario/suppressed, mensajes sin datos y cero retries.
- Delegar descarte de conexión muerta y restauración de estado al transaction manager/pool.

## Consecuencias

No se añaden tipos, métodos, dependencias productivas ni auto-configuración. El consumidor debe
alinear repository, manager y `JdbcOperations`; J6 podrá facilitar selección/back-off sin cambiar
este contrato. JTA, chained managers, coordinación distribuida y retries siguen fuera de alcance.

Una aplicación no debe anidar `JpaTransactionManager` y un manager JDBC local esperando una única
unidad atómica. En la caracterización con `DataSource` compartido, una dirección puede confirmar
antes del rollback exterior y la inversa falla al intentar enlazar dos recursos locales.

## Evidencia

PostgreSQL cubre REQUIRED, `UnexpectedRollbackException`, `23505`/`25P02`, REQUIRES_NEW con PID
distinto, NESTED insert/lookup y rollback a savepoint con ambos managers JDBC, read-only, no-tx,
dos data sources/operations/managers, JPA+JDBC real, producer/converter/mapper, COPY/SELECT/DROP,
backend termination, Hikari size-one, 100 repeticiones y ocho threads. El audit de proxy prohíbe
close/commit/rollback/mutadores/savepoints. La matriz completa está en
[`spring-data-jdbc-transactions-and-robustness.md`](../architecture/spring-data-jdbc-transactions-and-robustness.md).

## Evidencia Boot J6

El starter no crea ni selecciona transaction manager. Con el manager JDBC normal de Boot, el smoke
PostgreSQL conserva `REQUIRED`, rollback exterior, rechazo read-only, `REQUIRES_NEW` independiente y
rollback `NESTED` a savepoint. El guard no-transaction permanece cubierto por J5. La
autoconfiguración retrocede ante ambigüedad de infraestructura y no altera ownership, retry,
SQLState ni semántica cross-stack.

## Alternativas rechazadas

| Alternativa | Motivo |
| --- | --- |
| Crear savepoints en postgres-bulk | Viola ownership y compite con Spring |
| Recuperar `25P02` internamente | Requiere rollback que pertenece al owner |
| Elegir el primer candidate | Cross-wiring silencioso y dependiente del orden |
| Prometer atomicidad JPA/JDBC local | La evidencia shared-DS la contradice |
| Retry automático | Input one-shot y commit incierto impiden garantía general |

## Fuentes oficiales

- [Spring Framework: conexiones JDBC](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)
- [Spring Framework: propagación](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- [Spring Data JDBC: transacciones](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/transactions.html)
- [PostgreSQL: códigos de error](https://www.postgresql.org/docs/current/errcodes-appendix.html)
- [PostgreSQL: rollback a savepoint](https://www.postgresql.org/docs/current/sql-rollback-to.html)

## Revisión J8 (2026-08-20)

Cada operación medida incluyó commit y usó una transacción write comparable; fixtures y checks
quedaron fuera del timing. La ejecución repetida no detectó pérdida de conexión, estado contaminado
ni fallo productivo. No se añadió retry ni se alteró ownership. El ADR permanece `ACCEPTED`.
