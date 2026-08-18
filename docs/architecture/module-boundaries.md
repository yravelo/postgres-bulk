# Fronteras de módulos

## Grafo permitido

```text
postgres-bulk-core
  ↑                 ↑
pgjdbc          hibernate
  ↑                 ↑
spring-data         │
  ↑                 │
  └── boot-autoconfigure ───┘
                 ↑
             boot-starter
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

## Dependencias y scopes previstos

- Core no tendrá dependencias runtime externas salvo necesidad demostrada.
- El parent importa Spring Boot BOM 3.5.16 para una baseline coherente sin añadir dependencias a
  módulos. El BOM del consumidor puede gobernar los mismos artefactos transitivos.
- pgJDBC debe estar confinado al adapter; que el starter lo agregue no convierte sus tipos en API pública.
- Testcontainers, JUnit, ArchUnit y benchmarks nunca serán dependencias transitivas productivas.

## Reglas verificables

Maven codifica el DAG y Enforcer prohíbe dependencias framework/infraestructura en core.
Auditorías de imports/JAR confirman core sin frameworks, pgjdbc/hibernate sin cruce, starter sin
lógica y APIs sin tipos pgJDBC/internals Hibernate. Los compatibility tests permanecen separados
de unit tests.
