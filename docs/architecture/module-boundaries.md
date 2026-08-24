# Fronteras de módulos

## Grafo permitido

```text
core <- pgjdbc <- spring-data-jdbc
core <- hibernate
core + pgjdbc + hibernate <- spring-data
spring-data + hibernate <- boot-autoconfigure <- boot-starter
spring-data-jdbc <- boot-autoconfigure-jdbc <- boot-starter-data-jdbc
core + pgjdbc + spring-data + spring-data-jdbc <- benchmarks (no publicable)
```

Las flechas apuntan hacia la dependencia. Es un DAG, no una jerarquía estricta. Ningún módulo productivo depende del starter.

## `postgres-bulk-core`

**Puede conocer:** Java SE; contratos bulk; options; resultados; excepciones; metadata propia; extractores de valores; puertos de resolución/ejecución; política de batching; SPI de encoding si demuestra ser agnóstico.

**No puede conocer:** `jakarta.persistence`, Spring, Hibernate, JDBC, `DataSource`, `Connection`, pgJDBC, Micrometer ni sintaxis COPY/PostgreSQL. Tampoco anotaciones específicas de frameworks.

Paquetes candidatos: `bulk`, `metadata`, `mapping`, `codec`, `exception`, `result`. Se crearán sólo junto con tipos reales en sus fases. `CopySerializer` no pertenece aquí porque COPY es infraestructura PostgreSQL.

## `postgres-bulk-pgjdbc`

**Puede conocer:** core, JDBC estándar, pgJDBC y SQL PostgreSQL. Posee `PGConnection`, `CopyManager`/streams, framing CSV, quoting, generación SQL, scope de conexión y ciclo de tabla temporal.

**No puede conocer:** JPA, Hibernate, Spring Data, Boot o Micrometer. Un adapter de conexión Spring puede vivir en `spring-data`; el contrato JDBC base vive aquí.

Paquetes candidatos: `copy`, `connection`, `temporarytable`, `sql`.

## `postgres-bulk-hibernate`

**Puede conocer:** core, Jakarta Persistence y la generación soportada de Hibernate. Traduce metadata runtime a descriptores core.

**No puede conocer:** pgJDBC, Spring Data, Boot, repositorios ni ejecución COPY. Cualquier uso de API interna Hibernate queda encapsulado y cubierto por compatibility tests.

Paquete candidato: `metadata`. Si soportar dos majors de Hibernate exige internals incompatibles, se preferirán artefactos adapters separados antes que reflection condicional; no se añaden ahora.

## `postgres-bulk-spring-data-jdbc`

**Puede conocer:** core, pgJDBC, Spring JDBC y las APIs públicas de Spring Data
JDBC/Relational/Commons. Traduce el mapping context y las conversiones configuradas a metadata core
y, desde J2/J3, coordina `JdbcOperations.execute(ConnectionCallback)` con
`PostgresBulkJdbcOperations` para insert/lookup de la fila root. Desde J4 publica un fragmento
repository JDBC opt-in y lo registra como extensión externa.

**No puede conocer:** JPA, Hibernate, Spring Data JPA, Spring Boot, Actuator, auto-configuración ni
observability. El driver PostgreSQL entra productivamente sólo a
través de `postgres-bulk-pgjdbc`; Testcontainers y Hikari son test-only. Enforcer prohíbe
JPA/Hibernate/Boot.

La cache pertenece a una instancia de resolver y, por tanto, al converter/mapping context de una
aplicación. Coordinador e implementación externa son package-private; el fragmento público crea o
une una transacción `REQUIRED`, mientras el coordinador exige conexión write activa y nunca la
completa. No existe cache global ni dependencia desde core hacia Spring.

La implementación externa permanece package-private pero no final: Boot usa por defecto proxies
transaccionales class-based y necesita poder crear la subclase de infraestructura. Esto no la
convierte en API pública.

## `postgres-bulk-spring-data`

**Puede conocer:** core, pgjdbc, Spring Framework, Spring JDBC, Spring TX, Spring Data JPA, JPA,
Micrometer Observation/Core y la API pública `Session#doReturningWork` de Hibernate. Ofrece
repository fragment opt-in, acceso a la conexión física y observabilidad operation-level opcional.
Recibe el resolver de metadata como puerto, sin depender del adapter Hibernate concreto ni de sus
internals.

**No puede conocer:** Spring Boot autoconfiguration ni `@ConfigurationProperties`. Micrometer nunca
entra en core/pgjdbc/hibernate y la ausencia de beans registry mantiene un camino NOOP. No reemplaza
globalmente `SimpleJpaRepository`.

Paquetes candidatos: `repository`, `factory` (sólo si finalmente hace falta), `configuration`. La experiencia objetivo es compatible con `JpaRepository<T, ID>, PostgresBulkRepository<T, ID>`; se decidirá si `PostgresBulkRepository` extiende algo o es un fragment puro.

## `postgres-bulk-spring-boot-autoconfigure`

**Puede conocer:** Spring Boot Autoconfigure, Spring Data, Hibernate y pgJDBC. Es el composition
root que registra el bridge cacheado de metadata cuando el classpath, cualquier
`EntityManagerFactory` y `postgres-bulk.enabled` lo permiten; hace back-off por tipo ante el bean
del usuario. Consume Micrometer para configuration metadata/cardinality sin crear registries ni
exporters.

**No puede conocer:** clases de aplicación ni activar repositorios globalmente de forma sorpresiva. No contiene lógica de COPY, metadata o mapping.

## `postgres-bulk-spring-boot-starter`

**Contiene:** únicamente el POM agregador de `spring-boot-starter-data-jpa` y
`postgres-bulk-spring-boot-autoconfigure`. No tiene `src/main`, clases ni recursos productivos.

**No puede contener:** código Java, configuración de negocio, properties ni tests de lógica.

## `postgres-bulk-spring-boot-autoconfigure-jdbc`

**Puede conocer:** Boot Autoconfigure, el adapter Spring Data JDBC, Spring JDBC/Relational y
pgJDBC. Registra únicamente el resolver JDBC después de que Boot declare la infraestructura de
mapping y exige candidato único o `@Primary` para cada dependencia.

**No puede conocer:** JPA, Hibernate, Spring Data JPA, Actuator, repositories de aplicación ni
lógica COPY/mapping. No crea datasource, operations, transaction manager o repositorios y no abre
conexiones al arrancar.

## `postgres-bulk-spring-boot-starter-data-jdbc`

**Contiene:** sólo el POM que agrega `spring-boot-starter-data-jdbc` y
`postgres-bulk-spring-boot-autoconfigure-jdbc`. No tiene clases ni recursos productivos.

**No puede contener:** Hibernate/JPA, Actuator obligatorio, Testcontainers productivo, benchmarks,
configuración de negocio o lógica Java.

## Dependencias y scopes previstos

- Core no tendrá dependencias runtime externas salvo necesidad demostrada.
- El parent importa Spring Boot BOM 3.5.16 para una baseline coherente sin añadir dependencias a
  módulos. El BOM del consumidor puede gobernar los mismos artefactos transitivos.
- pgJDBC debe estar confinado al adapter; que el starter lo agregue no convierte sus tipos en API pública.
- Testcontainers, JUnit, ArchUnit y benchmarks nunca serán dependencias transitivas productivas.

## `postgres-bulk-benchmarks`

**Puede conocer:** todos los módulos productivos necesarios como contenders, JMH, Testcontainers,
Hikari y fixtures Spring JPA/JDBC. Los contextos JPA y JDBC están aislados para impedir que una
repository factory descubra fixtures del otro store.

**No puede contener:** API productiva, cambios de comportamiento, dependencias transitivas para
consumidores ni artefactos publicables. Tiene `maven.deploy.skip=true`; `verify` sólo compila y
empaqueta el arnés. JMH se ejecuta manualmente por script o `workflow_dispatch`.

MS8 amplía sólo este módulo con comparaciones emparejadas default/runtime para pgJDBC, JPA y JDBC,
una caracterización Java pura de cardinalidad y una verificación previa sobre schemas A/B/C/quoted.
El target, SQL y fixtures siguen siendo estado del arnés; no se exportan al grafo productivo. Los
raw JSON/CSV son evidencia documental y el POM conserva `maven.deploy.skip=true`.

## Evidencia multi-schema MS6

MS6 conserva el grafo sin cambios productivos. Los dos starters siguen dependency-only y las
pruebas de coexistencia cargan ambas autoconfiguraciones sin introducir una dependencia JPA en el
grafo productivo JDBC. `TableName` cruza sólo las APIs de operación ya publicadas en MS4/MS5; Boot
no adquiere una responsabilidad target-aware nueva.

## Cierre multi-schema MS8

La revisión final mantiene el grafo productivo intacto. Metadata y engines preparados continúan
cacheados sólo por identidad estructural/tipo; el target efectivo y su SQL son locales a cada
invocación. En particular: **NO TARGET-KEYED CACHE**. El coste medido no justifica ampliar API,
crear un resolver ambiental ni trasladar código del arnés a un artifact publicable.

## Reglas verificables

Maven codifica el DAG y Enforcer prohíbe dependencias framework/infraestructura en core.
Auditorías de imports/JAR confirman core sin frameworks, pgjdbc/hibernate sin cruce, starter sin
lógica y APIs sin tipos pgJDBC/internals Hibernate. Los compatibility tests permanecen separados
de unit tests.
