# ADR-024: Arquitectura de integración Spring Data JDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-19

## Contexto

El roadmap original entregó un motor neutral, un executor pgJDBC caller-owned y una integración
Spring Data JPA/Hibernate. La siguiente línea funcional debe soportar Spring Data JDBC sin convertir
el core en un metamodelo Spring ni alterar ownership/transacciones del motor. El artifact actual
`postgres-bulk-spring-data` no es común: depende de JPA, Hibernate, `JpaContext` y
`EntityManager`.

Spring Data JDBC trabaja sin persistence context JPA, pero aporta metadatos/conversión relacional,
repository fragments y acceso JDBC transaction-aware. También modela aggregates: un save normal
puede escribir varias tablas, ejecutar callbacks y mantener ID/version, mientras el motor actual
ejecuta COPY sobre una sola tabla.

## Decisión propuesta

- Reutilizar `postgres-bulk-core` y `postgres-bulk-pgjdbc` sin cambios.
- Conservar `postgres-bulk-spring-data` como adapter JPA compatible; no renombrar artifacts o
  packages existentes.
- Añadir `postgres-bulk-spring-data-jdbc`, sin dependencia de JPA/Hibernate.
- Añadir auto-configuración y starter JDBC separados; no ampliar los starters JPA existentes.
- No crear un módulo `spring-data-common` hasta que implementación real demuestre duplicación
  estable que compense otro boundary.
- Exponer un fragment interface JDBC distinto en package JDBC. Reutilizar los métodos de
  `BulkOperations`/lookup, pero no el FQCN ni los Javadocs JPA del fragment actual.
- Registrar el fragmento externo con `META-INF/spring.factories`; la implementación usará
  `RepositoryMetadataAccess` y `RepositoryMethodContext`.
- Ejecutar dentro de `JdbcOperations.execute(ConnectionCallback)` usando la misma conexión para
  tabla temporal, COPY, join, materialización y cleanup.
- Mantener la conexión caller-owned: ningún close, commit, rollback, cambio de auto-commit,
  isolation/read-only ni savepoint creado por postgres-bulk.
- Reutilizar `BulkKeyMetadata` explícita; no inferir keys de repository methods o del ID.
- Materializar inicialmente con el `EntityRowMapper` público de Spring Data JDBC sobre el
  `ResultSet` de la conexión prestada y sólo para aggregate roots sin children.
- Tratar `bulkInsert` como root-table row insert, no como `save` de un aggregate graph. Child
  entities/collections, callbacks, auditing, events, optimistic locking e instance synchronization
  quedan fuera del contrato inicial.
- Mantener REQUIRED/read-write. No declarar NESTED soportado hasta probar savepoints con el
  transaction manager JDBC y PostgreSQL.
- Permitir JPA y JDBC en un mismo classpath mediante fragments distintos. Extender ambos fragments
  en el mismo repository es unsupported. Con varios transaction managers/DataSources, la
  auto-configuración hará back-off o exigirá selección explícita.

## Consecuencias

La arquitectura existente permanece estable y los consumidores JPA no reciben dependencias o
cambios de comportamiento. La variante JDBC necesita tres artifacts nuevos y repite una pequeña
capa interna de observability, pero mantiene cada stack aislado. El término “bulk insert” tendrá
una diferencia explícita respecto a Spring Data JDBC `save`: sólo representa la fila raíz.

El mapper público puede emitir SQL adicional al resolver relaciones. Por ello un characterization
test y el guard root-only son gates: si fallan, no se sustituirá por internals. La selección de
transaction manager en aplicaciones con ambos stacks y la semántica NESTED permanecen preguntas
de implementación, no promesas.

## Evidencia J1 (2026-08-19)

J1 añadió el leaf module JDBC con dependencia productiva exclusiva en core y Spring Data JDBC. El
resolver y sus tests no importan JPA, Hibernate, Boot, Actuator ni pgJDBC productivo. Se demostró
metadata root-only y round-trip PostgreSQL, pero J1 no implementa aún conexión transaccional,
fragment, lookup, coexistencia ni auto-configuración. Por esas evidencias todavía ausentes, este
ADR permanece `PROPOSED`.

## Evidencia J2 (2026-08-20)

El coordinador package-private usa `JdbcOperations.execute(ConnectionCallback)` y entrega esa
misma `Connection` a `PostgresBulkJdbcOperations`. Un default de tabla calculado por
`pg_backend_pid()` coincide con consultas Spring inmediatamente anteriores y posteriores dentro de
la misma transacción. Tests PostgreSQL prueban REQUIRED, rollback exterior, read-only,
REQUIRES_NEW con PID independiente, NESTED sólo como characterization y reutilización Hikari tras
éxito y fallo. La conexión permanece caller-owned y el adapter no invoca close, commit, rollback o
mutadores JDBC.

J2 también confirma que el módulo puede depender productivamente de pgJDBC y Spring JDBC sin
introducir JPA/Hibernate/Boot. No añade fragmento ni lookup. El ADR permanece `PROPOSED` porque
discovery/materialización J3-J4, coexistencia completa y auto-configuración siguen siendo evidencia
requerida.

## Evidencia J3 (2026-08-20)

El lookup package-private usa `JdbcOperations.execute(ConnectionCallback)` y delega CREATE/COPY/
SELECT/DROP al engine existente sobre la misma conexión. `EntityRowMapper` y `JdbcConverter`
públicos materializan el result set completo antes del cleanup; el query-count PostgreSQL es un
SELECT sin consultas laterales para roots. PID, propagaciones, read-only, cleanup, pool y
concurrencia quedan probados. No se añaden fragmento, Boot ni tipos públicos; por ello el ADR sigue
`PROPOSED` hasta discovery J4, coexistencia completa y auto-configuración.

## Evidencia J4 (2026-08-20)

`PostgresBulkJdbcRepository<T>` se descubre desde un JAR externo mediante `spring.factories`; la
implementación package-private usa `RepositoryMetadataAccess`/`RepositoryMethodContext` y delega
sin duplicación al coordinador J2/J3. PostgreSQL confirma llamadas desde repositories reales para
insert/lookup, transacciones, converters, dos domain types y concurrencia. Enforcer conserva el
módulo sin JPA/Hibernate/Boot y el fragmento JPA no cambia. Un repository que declare ambos
fragments falla explícitamente y varios `JdbcOperations` fallan por DI estándar. El ADR permanece
`PROPOSED` hasta la selección multi-manager/coexistencia completa J5 y auto-configuración J6. Como
smoke de both-classpath, el suite JPA carga también el JAR JDBC y confirma que su fragmento sigue
descubriéndose y operando sin selección accidental.

## Evidencia J5 (2026-08-20)

La matriz PostgreSQL cierra REQUIRED, rollback-only/`UnexpectedRollbackException`, `25P02`, ambas
direcciones de REQUIRES_NEW y NESTED con `JdbcTransactionManager` y
`DataSourceTransactionManager`. Dos data sources, dos `JdbcOperations` y dos managers demuestran
fallo por ambiguity y selección explícita sin orden implícito. Un contexto real JPA+JDBC prueba
ambos fragments y demuestra que managers locales distintos no ofrecen atomicidad cross-stack,
incluso con `DataSource` compartido. Ownership, backend loss, pool, repetición y concurrencia pasan.

El ADR permanece `PROPOSED` únicamente por el gate de auto-configuración/back-off J6; las preguntas
transaccionales y de coexistencia quedan resueltas por ADR-029.

## Evidencia J6 (2026-08-20)

Dos artifacts Boot separados completan el grafo sin dependencias JPA/Hibernate. La
autoconfiguración crea sólo el resolver JDBC cuando todas las dependencias tienen candidato único o
`@Primary`, retrocede ante override/ambigüedad y no crea repositories, manager ni conexiones. Un
starter JDBC sin código levanta una aplicación Boot real y prueba insert, lookup, conversiones,
embedded y propagaciones PostgreSQL. La caracterización both-starters carga ambos resolvers sin
colisión; el repository dual continúa rechazado por J5.

Todos los gates de aceptación enumerados abajo tienen evidencia en J1–J6. Este ADR pasa a
`ACCEPTED`; compatibilidad min/current y documentación/example de adopción completos pertenecen a
J7 y no reabren esta decisión arquitectónica.

## Alternativas evaluadas

| Alternativa | Resultado |
| --- | --- |
| Renombrar `postgres-bulk-spring-data` a `-jpa` | Rechazada: rompe coordenadas sin necesidad funcional |
| Convertir el artifact actual en common | Rechazada: mezcla API ya JPA-specific y crea migración |
| Un único fragment FQCN con implementaciones por store | Rechazada: discovery/semántica ambiguas cuando conviven stores |
| Un único starter JPA + JDBC | Rechazada: dependency pollution y transaction ambiguity |
| Adquirir con `DataSource.getConnection()` | Rechazada: puede ignorar la conexión transaccional |
| `DataSourceUtils` explícito | Válida como fallback, menos acotada que `ConnectionCallback` |
| `JdbcAggregateTemplate`/`DataAccessStrategy` para lookup | Rechazada: no expresan el exact-connection temp-table callback |
| Implementación con clases internas Spring Data | Rechazada: incompatibilidad de mantenimiento |

## Evidencia satisfecha para ACCEPTED

- prototype JDBC-only que demuestra identidad física de conexión con `pg_backend_pid()`;
- discovery del fragmento externo en Spring Data JDBC 3.5.0 y 3.5.13;
- lookup root-only materializado por API pública y custom conversions;
- tests JPA-only, JDBC-only y both-classpath sin selección accidental;
- transaction tests REQUIRED, rollback, REQUIRES_NEW, read-only y NESTED condicionado;
- auto-configuración con back-off ante múltiples candidates;
- ninguna modificación ni dependencia nueva en core/pgJDBC.

## Fuentes oficiales

- [Spring Data JDBC configuration](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/getting-started.html)
- [Spring Data JDBC transactions](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/transactions.html)
- [Spring Data repository fragments](https://docs.spring.io/spring-data/commons/reference/repositories/custom-implementations.html)
- [Spring Framework JDBC connections](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)

## Revisión J8 (2026-08-20)

Los benchmarks ejercitaron el fragmento público JDBC y el engine low-level con metadata caliente,
misma tabla y misma transacción. No apareció razón para cambiar boundaries, crear módulo common o
mezclar starters. Core/pgJDBC y API pública no cambiaron. El ADR permanece `ACCEPTED`.

## Evidencia MS5 (2026-08-24)

El fragmento JDBC propaga `TableName` explícito por `RepositoryMethodContext`, coordinador y
`ConnectionCallback` a los motores MS2/MS3. A/B usa el mismo proxy y conexión transaction-bound sin
estado target, multitenancy, cambios core/pgJDBC ni wiring Boot. La arquitectura permanece
`ACCEPTED`.
