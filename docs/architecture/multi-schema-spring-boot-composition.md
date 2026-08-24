# Composición Spring Boot multi-schema y coexistencia de stores

## Estado y alcance

**MS6: DONE (2026-08-24).** Spring Boot compone las capacidades target-aware de JPA y Spring Data
JDBC sin resolver tenants, guardar un schema actual ni modificar las APIs de operación. La
aplicación sigue entregando un `TableName` schema-qualified en cada llamada; Boot sólo conecta los
resolvers, repositories, conexiones y transaction managers que ya pertenecen a cada store.

MS6 no añade código productivo, properties, beans target-aware, routing, seguridad, publicación,
benchmarks ni una promesa de transacción distribuida. La evidencia combina tests Boot de los
starters con las matrices PostgreSQL MS4/MS5 que permanecen en el reactor.

## Arquitectura de composición

```text
application-owned target decision
              |
              +-- JPA repository -- JpaTransactionManager -- EntityManager/Connection
              |                         |
              |                         +-- JPA metadata resolver -- pgJDBC engine
              |
              +-- JDBC repository - JDBC transaction manager -- JdbcOperations/Connection
                                        |
                                        +-- JDBC metadata resolver -- pgJDBC engine
```

El target viaja como argumento local del repository al engine. Ninguna auto-configuración lo ve,
resuelve o retiene. Los dos resolvers tienen tipos diferentes y caches estructurales independientes.

## Matriz de robustez y composición

| Stack | Store config | Transaction manager | Target pattern | Expected result |
| --- | --- | --- | --- | --- |
| JPA only | one `DataSource`/EMF | Boot `JpaTransactionManager` | default, A/B/C | starter activa JPA; insert/lookup aislados |
| JDBC only | one complete JDBC infrastructure | Boot JDBC manager | default, A/B/C | starter activa JDBC; insert/lookup aislados |
| JPA + JDBC | same `DataSource`, separate repositories | manager explícito por boundary | JPA A, JDBC B | ambos resolvers conviven; sin promesa cross-manager |
| JPA + JDBC | separate `DataSource`/database per store | one manager per store | targets dentro de cada DB | cada repository permanece en su store |
| JDBC | multiple `DataSource`, no primary | none selected | any | auto-config JDBC backs off; Boot no adivina |
| JDBC | multiple `DataSource`, one primary | manager/infrastructure aligned to primary | default/A/B/C | composición automática sobre el candidate efectivo |
| JDBC | multiple infrastructures, explicit resolver | explicitly wired manager | target inside selected DB | wiring del consumidor gana por type |
| JPA | multiple EMF | `JpaContext` plus repository configuration | target inside selected unit | resolver cachea por identidad de EMF |
| either | multiple transaction managers, no primary/qualifier | ambiguous | any | Spring reports ambiguity; postgres-bulk does not choose |
| either | primary or explicitly qualified manager | selected owner | default/A/B/C | normal REQUIRED/REQUIRES_NEW semantics |
| JDBC | manager owning same `DataSource` with savepoints | JDBC manager | A/B | NESTED remains conditionally supported |
| JPA | `JpaTransactionManager`/Hibernate dialect | JPA manager | A/B | NESTED remains unsupported |
| either | one database, one transaction | same manager/connection | A then B | commit/rollback covers both schemas |
| cross-store | local managers, same or separate databases | two local managers | JPA A + JDBC B | no distributed atomicity claim |

## JPA-only y JDBC-only

El starter JPA conserva sus conditions: classpath JPA/Hibernate/pgJDBC, cualquier EMF y kill switch
general. Su smoke Boot ejecuta default+A/B/C, lookup target-aware, schema quoted, conflictos,
schema ausente, commit/rollback, `REQUIRES_NEW`, read-only y concurrencia sobre el mismo proxy.

El starter JDBC exige una infraestructura completa y no ambigua. Su smoke equivalente ejecuta
default+A/B/C, lookup, quoted identifiers, conflictos, schema ausente, commit/rollback,
`REQUIRES_NEW`, read-only y concurrencia. NESTED sigue siendo propiedad del manager JDBC y sus
savepoints, no del starter.

## Ambos starters

Los artifacts pueden estar simultáneamente en el classpath. La auto-configuración JPA aporta un
`JpaEntityMetadataResolver`; la JDBC aporta un `SpringDataJdbcEntityMetadataResolver`. No comparten
nombre, tipo, cache ni repository fragment. El guard existente rechaza un repository que herede
ambos fragmentos.

Con un mismo `DataSource`, los repositories siguen teniendo boundaries de store distintos. Que dos
managers locales usen la misma fuente no los convierte en una transacción coordinada: la
caracterización existente demuestra que una dirección puede confirmar antes del rollback exterior
y la otra puede rechazar el segundo resource binding. La aplicación debe elegir un único owner
compatible, separar scopes o adoptar coordinación externa.

Con data sources o databases separados, `TableName` sólo selecciona schema/tabla dentro de la base
ya elegida por la conexión. No puede redirigir una conexión a otra database.

## Selección de infraestructura

JPA permite varios EMF porque `JpaContext` selecciona la persistence unit por tipo de dominio. Una
clase gestionada ambiguamente exige configuración de repositories de la aplicación.

JDBC aplica candidato único o `@Primary` a `DataSource`, `JdbcOperations`, `JdbcConverter`,
`RelationalMappingContext` y `JdbcCustomConversions`. Sin candidate inequívoco, la auto-config hace
back-off. Un resolver aportado por el usuario permite wiring explícito sin que la librería elija por
nombre, orden o proximidad.

Los transaction managers son responsabilidad de Boot/aplicación. Con varios managers, una frontera
sin qualifier/primary conserva la ambigüedad estándar de Spring. La librería no crea, encadena ni
elige managers.

## Transacciones target-aware

- `REQUIRED` conserva la conexión física del store y puede tocar A+B en la misma base.
- `REQUIRES_NEW` usa el scope físico independiente elegido por Spring y requiere pool suficiente.
- JPA NESTED permanece unsupported con `HibernateJpaDialect`.
- JDBC NESTED permanece condicionado a savepoints del manager que posee el mismo `DataSource`.
- read-only se rechaza; el repository público crea REQUIRED cuando no existe outer.
- el delegate interno continúa rechazando uso no transaccional, sin fallback a autocommit.
- un fallo SQL preserva SQLState/cause y puede dejar `25P02` hasta rollback del owner.

## Concurrencia, pool y estado

Los smokes Boot ejecutan A/B concurrentes sobre un mismo proxy en ambos stacks. MS4/MS5 mantienen
la evidencia profunda de pool/backend, temporales y metadata. El estado observado antes/después
permanece estable: `autoCommit`, read-only, isolation, `getSchema`, `search_path` y backend ownership.
No se llama `setSchema`; no existe restauración best-effort ni leakage A→B.

El target no participa en caches. JPA reutiliza la misma metadata por EMF/tipo y JDBC la misma por
converter/contexto/tipo para A/B/C. SQL target-specific y temporales siguen siendo locales a la
invocación.

## Opcionalidad y configuración

Una aplicación que nunca pasa targets continúa usando su mapping default sin configuración nueva.
Default y runtime pueden alternarse sobre el mismo repository. Las únicas properties siguen siendo:

```properties
postgres-bulk.enabled=true
postgres-bulk.observability.enabled=true
```

No existen `postgres-bulk.schema`, flags multitenant/multischema ni resolver property. La
configuración metadata generada conserva exactamente esos dos contracts existentes.

## Identifiers, errores y seguridad

Schema y tabla se citan por componentes. Schemas quoted funcionan en ambos stacks; conflictos de
schema/tabla se rechazan por la regla central y objetos ausentes conservan `3F000`/`42P01` en su
cause chain. PostgreSQL sigue decidiendo permisos; `42501` permanece cubierto low-level porque
repetir roles en cada starter no cambia la composición.

Quoting evita alterar sintaxis, no autoriza un target. La aplicación debe mapear una identidad ya
autorizada a una allow-list de `TableName`; no debe concatenar headers, claims o ids no confiables.
Esta auditoría de frontera no ejecuta el Security Baseline.

## Observabilidad

Las llamadas target-aware atraviesan los timers/counters existentes de `insert` y `lookup`. Los
tags continúan limitados a operation/outcome/error; schema, target y tenant no se publican. Así la
cardinalidad no crece con A/B/C ni con el número de tenants de la aplicación.

## Starters, dependencias y consumidores externos

Ambos starters siguen siendo JARs dependency-only, sin Java ni resources productivos. Enforcer
mantiene el starter/autoconfigure JDBC libre de JPA, Hibernate, Actuator obligatorio,
Testcontainers y benchmarks productivos. JPA y JDBC conservan sus respectivos grafos.

Los ejemplos standalone JPA y JDBC, más el consumidor JDBC aislado, compilan contra snapshots
instalados y ejecutan insert/lookup A/B mediante API pública. Esto verifica adopción fuera del
parent/reactor sin importar internals.

## Non-goals y cierre MS7

MS6 no añade tenant resolver/context, routing, property global, JTA, chained managers, provisioning,
migrations, row-level tenancy, security baseline, observability target-aware, publicación,
benchmarks ni cierre completo de compatibilidad.

MS7 completó la matriz soportada, los ejemplos standalone JPA/JDBC y la guía de adopción sin
reabrir el modelo de composición. El job focalizado JPA-only/JDBC-only/both y los full reactors
mínimo/newest pasan, igual que Build `32714347790` y Compatibility `32714347857` (11 jobs). No se
añadieron beans, properties o código runtime.

MS8 cerró después la línea con benchmarks default/runtime sin cambiar esta composición ni añadir
beans/properties. **NO TARGET-KEYED CACHE**.
