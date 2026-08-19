# ADR-024: Arquitectura de integración Spring Data JDBC

- **Estado:** PROPOSED
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

## Evidencia requerida para ACCEPTED

- prototype JDBC-only que demuestra identidad física de conexión con `pg_backend_pid()`;
- discovery del fragmento externo en Spring Data JDBC 3.5.0 y 3.5.13;
- lookup root-only materializado por API pública y custom conversions;
- tests JPA-only, JDBC-only y both-classpath sin selección accidental;
- transaction tests REQUIRED, rollback, REQUIRES_NEW, read-only y characterization NESTED;
- auto-configuración con back-off ante múltiples candidates;
- ninguna modificación ni dependencia nueva en core/pgJDBC.

## Fuentes oficiales

- [Spring Data JDBC configuration](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/getting-started.html)
- [Spring Data JDBC transactions](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/transactions.html)
- [Spring Data repository fragments](https://docs.spring.io/spring-data/commons/reference/repositories/custom-implementations.html)
- [Spring Framework JDBC connections](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)
