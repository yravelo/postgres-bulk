# Evidencia de compatibilidad

## Evidencia pública alojada de MIG4

El repositorio público ejecutó la matriz canónica exclusivamente en runners alojados por GitHub.
Build `32925430081` pasó y Compatibility `32925429985` pasó sus 11/11 jobs para el commit
`dd63b0d2a0cdbfe033b94afc07ef1a9d2c648752`. Los jobs conservaron `ubuntu-latest`, token de solo
lectura, cero secretos de repositorio y cero runners self-hosted registrados. La primera ejecución
pública reveló que la imagen alojada no incluía PyYAML ni `rg`; ambos prerrequisitos de auditoría
quedaron fijados por versión y checksum antes de repetir la matriz con resultado PASS.

**Corte de evidencia:** 2026-08-24, SEC2. Todos los comandos se ejecutaron desde
`code/postgres-bulk-parent` en Linux, con Maven Wrapper 3.9.16, Docker 29.7.0 y sin omitir
Enforcer ni tests de integración.

## Fuente de verdad actual

| Componente | Versión/rango declarado | Versión resuelta por defecto | Versión validada | Fuente |
|---|---|---|---|---|
| Java | mínimo 17; `release=17` | JDK del build | Temurin 17.0.20, 21.0.12; OpenJDK 25.0.3 | parent POM, Enforcer y ejecuciones J01–J03 |
| Spring Boot | 3.5.0–3.5.16 | 3.5.16 | 3.5.0, 3.5.16 | `spring-boot.version`, BOM y B01/J01 |
| Spring Framework | gobernado por Boot | 6.2.19 | 6.2.7, 6.2.19 | dependency tree de B01/J01 |
| Spring Data JPA | gobernado por Boot | 3.5.13 | 3.5.0, 3.5.13 | dependency tree y starter IT |
| Spring Data JDBC | gobernado por Boot | 3.5.13 | 3.5.0, 3.5.13 | dependency tree, resolver/fragment/starter/example tests |
| Spring Data Relational | gobernado por Boot | 3.5.13 | 3.5.0, 3.5.13 | dependency tree, mapping/materialization tests |
| Hibernate ORM | 6.6.15–6.6.55 | 6.6.53.Final | 6.6.15, 6.6.53, 6.6.55 | BOM y H01/H02/full stack |
| Micrometer | gobernado por Boot | 1.15.12 | 1.15.0, 1.15.12 | dependency tree y observability tests |
| pgJDBC | 42.7.5–42.7.13 | 42.7.13 | 42.7.5, 42.7.11, 42.7.13 | override de seguridad, D01/D02/full stack |
| PostgreSQL | majors 15–18 | `15.18-alpine` | 15.18, 16.14, 17.10, 18.4 | parent `postgres.version` y Testcontainers |

La baseline real del POM es Boot 3.5.16, Spring Framework 6.2.19, Spring Data
JPA/JDBC/Relational 3.5.13,
Hibernate 6.6.53.Final, Micrometer 1.15.12, pgJDBC 42.7.13 y PostgreSQL 15.18. SEC2 cambió el
default pgJDBC de 42.7.11 a 42.7.13 tras resolver el finding HIGH
`GHSA-j92g-9f8w-j867`; es un patch dentro del rango ya validado, no un cambio de compatibilidad.

## Auditoría de los BOM límite

Comando, repetido con `3.5.0` y `3.5.16`:

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Dspring-boot.version=3.5.0 -DskipTests \
  -pl postgres-bulk-spring-boot-starter,postgres-bulk-spring-boot-starter-data-jdbc \
  -am dependency:tree \
  -Dincludes=org.springframework:spring-core,org.springframework:spring-jdbc,org.springframework.data:spring-data-jpa,org.springframework.data:spring-data-jdbc,org.springframework.data:spring-data-relational,org.hibernate.orm:hibernate-core,io.micrometer:micrometer-core,org.postgresql:postgresql
```

| Boot | Framework | Data JPA | Data JDBC/Relational | Hibernate | Micrometer | pgJDBC | Resultado |
|---|---|---|---|---|---|---|---|
| 3.5.0 | 6.2.7 | 3.5.0 | 3.5.0 | 6.6.15.Final | 1.15.0 | 42.7.13 | PASS |
| 3.5.16 | 6.2.19 | 3.5.13 | 3.5.13 | 6.6.53.Final | 1.15.12 | 42.7.13 | PASS |

## Matriz ejecutada

Los jobs `full reactor` ejecutan unit/context tests y todas las integraciones JPA, JDBC,
autoconfiguration, ambos starters y ambos ejemplos. Los jobs focalizados conservan los contratos
de los adapters Hibernate y pgJDBC sin crear un producto cartesiano.

| ID | Java | Boot | Data JPA/JDBC | Hibernate | Micrometer | pgJDBC | PostgreSQL | Tests | Resultado |
|---|---|---|---|---|---|---|---|---:|---|
| J01 baseline | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.13 | 15.18 | full reactor | PASS |
| J02 LTS | 21.0.12 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.13 | 15.18 | full reactor | PASS |
| J03 experimental | 25.0.3 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.13 | 15.18 | full reactor | PASS |
| B01 minimum | 17.0.20 | 3.5.0 | 3.5.0 | 6.6.15 | 1.15.0 | 42.7.13 | 15.18 | full reactor | PASS |
| P16 server | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.13 | 16.14 | full reactor | PASS |
| P17 server | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.13 | 17.10 | full reactor | PASS |
| N01 newest | 21.0.12 | 3.5.16 | 3.5.13 | 6.6.55 | 1.15.12 | 42.7.13 | 18.4 | full reactor | PASS |
| H01 adapter min | 25.0.3 | n/a | n/a | 6.6.15 | n/a | 42.7.13 test | 15.18 | 48 | PASS |
| H02 adapter max | 25.0.3 | n/a | n/a | 6.6.55 | n/a | 42.7.13 test | 15.18 | 48 | PASS |
| D01 driver min | 25.0.3 | n/a | n/a | n/a | n/a | 42.7.5 | 15.18 | 158 | PASS |
| D02 driver max | 25.0.3 | n/a | n/a | n/a | n/a | 42.7.13 | 15.18 | 158 | PASS |

Los comandos exactos fueron:

```bash
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw --batch-mode --no-transfer-progress clean verify
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk21 ./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress clean verify
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw --batch-mode --no-transfer-progress clean verify -Dspring-boot.version=3.5.0
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw --batch-mode --no-transfer-progress clean verify -Dpostgres.version=16.14-alpine
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw --batch-mode --no-transfer-progress clean verify -Dpostgres.version=17.10-alpine
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk21 ./mvnw --batch-mode --no-transfer-progress clean verify -Dhibernate.version=6.6.55.Final -Dpostgresql.version=42.7.13 -Dpostgres.version=18.4-alpine
./mvnw --batch-mode --no-transfer-progress clean verify -pl postgres-bulk-hibernate -am -Dhibernate.version=6.6.15.Final
./mvnw --batch-mode --no-transfer-progress clean verify -pl postgres-bulk-hibernate -am -Dhibernate.version=6.6.55.Final
./mvnw --batch-mode --no-transfer-progress clean verify -pl postgres-bulk-pgjdbc -am -Dpostgresql.version=42.7.5
./mvnw --batch-mode --no-transfer-progress clean verify -pl postgres-bulk-pgjdbc -am -Dpostgresql.version=42.7.13
```

`JAVA_HOME` identifica aquí los JDK exactos usados; en CI `setup-java` hace esa selección.

## Evidencia multi-schema MS7

MS7 volvió a ejecutar localmente los full reactors J01, J02, B01, P16, P17 y N01 sobre los
overloads target-aware ya entregados por MS2–MS6. J03, H01/H02 y D01/D02 quedaron cubiertos por el
workflow remoto del mismo commit. La estrategia continúa siendo boundary/pairwise: cambia un eje
por lane y añade un smoke coherente con todos los límites nuevos, sin producto cartesiano.

| Área | Evidencia incluida en la matriz | Resultado |
|---|---|---|
| Core/pgJDBC | default + A/B/C, insert, lookup, quoted, schema/table ausente y conflicto pre-JDBC | PASS |
| Spring Data JPA | repository singleton, default/A/B, lookup, rollback, read-only, REQUIRES_NEW y NESTED rechazado | PASS |
| Spring Data JDBC | repository singleton, default/A/B, lookup/materialización, rollback, read-only, REQUIRES_NEW y NESTED condicionado | PASS |
| Boot JPA-only | activación/back-off, starter real y target explícito | PASS |
| Boot JDBC-only | activación/back-off, starter real y target explícito | PASS |
| Ambos starters | resolvers separados, sin cross-wiring y managers caracterizados | PASS |
| Estado de conexión | A→B pooled y concurrencia sin `setSchema`/`search_path` | PASS |
| Aislamiento | grafo JDBC sin JPA/Hibernate/Actuator obligatorio/Testcontainers productivo/benchmarks | PASS |
| Adopción | ejemplos standalone JPA/JDBC y consumidor JDBC aislado | PASS |

Comandos adicionales ejecutados para MS7:

```bash
./mvnw spotless:check
./mvnw test
./mvnw verify
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw clean verify
./mvnw install
JAVA_HOME=/tmp/postgres-bulk-jdks/jdk17 ./mvnw --batch-mode --no-transfer-progress -q clean verify \
  -pl postgres-bulk-spring-boot-starter,postgres-bulk-spring-boot-starter-data-jdbc -am \
  -Dtest=PostgresBulkAutoConfigurationTest,PostgresBulkJdbcAutoConfigurationTest,BothStartersCoexistenceTest \
  -Dit.test=PostgresBulkStarterIT,JdbcStarterSmokeIT \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
./mvnw --batch-mode --no-transfer-progress -q -f ../../examples/spring-boot-basic/pom.xml clean verify
./mvnw --batch-mode --no-transfer-progress -q -f ../../examples/spring-boot-data-jdbc/pom.xml clean verify
./mvnw --batch-mode --no-transfer-progress -q -f ../../verification/spring-boot-jdbc-consumer/pom.xml clean verify
../../scripts/generate-public-api.sh --check
../../scripts/check-documentation.sh
git diff --check
```

`verify` produjo Javadocs sin warnings/errores del plugin. La baseline binaria pública no cambió,
los dos JARs starter contienen sólo metadata Maven/manifest, los enlaces y coordinates pasan el
audit y ningún reporte Surefire/Failsafe generado contiene failures, errors o skips. Los módulos
benchmark se empaquetaron como parte normal del reactor, pero no se ejecutó JMH ni se generó claim
de rendimiento.

## Evidencia Spring Data JDBC J7

Cada ejecución `full reactor` incluye, además de la regresión JPA:

- resolver de metadata con scalars, IDs assigned/generated, enums, converters, embedded,
  `AggregateReference`, UUID, `byte[]`, temporal e identificadores/schema quoted;
- insert y lookup root-only con `EntityRowMapper`, claves simples/compuestas y PostgreSQL real;
- REQUIRED, rollback, read-only, no-transaction, `REQUIRES_NEW` y `NESTED` condicionado;
- activation/back-off/single-candidate/user override/JPA-only/JDBC-only/both de autoconfiguration;
- starter JDBC-only, ambos starters y repositorios JPA/JDBC separados;
- ejemplo ejecutable con discovery, batch default/explícito, lookup, rollback y read-only.

El stack mínimo B01 cubre Boot/Data JDBC/Relational 3.5.0, Framework 6.2.7 y pgJDBC 42.7.5. N01
cubre Boot 3.5.16, Data JDBC/Relational 3.5.13, Framework 6.2.19, pgJDBC 42.7.13 y PostgreSQL
18.4 sobre Java 21. P16/P17 fijan los servidores intermedios; J01 cubre PostgreSQL 15.18. Java 25
se registra como EXPERIMENTAL, no como una ampliación del soporte 17/21.

La dependencia productiva del starter JDBC se audita en los BOM mínimo y actual. Contiene
core/pgjdbc/spring-data-jdbc/autoconfigure y no contiene Hibernate, Spring Data JPA,
`jakarta.persistence`, Actuator obligatorio, Testcontainers ni benchmarks. El ejemplo declara ese
starter como única dependencia directa de postgres-bulk.

Comandos adicionales reproducibles:

```bash
./mvnw --batch-mode --no-transfer-progress -DskipTests \
  -pl postgres-bulk-spring-boot-starter-data-jdbc -am dependency:tree
./mvnw --batch-mode --no-transfer-progress install
./mvnw --batch-mode --no-transfer-progress \
  -f ../../examples/spring-boot-data-jdbc/pom.xml clean verify
./mvnw --batch-mode --no-transfer-progress \
  -f ../../verification/spring-boot-jdbc-consumer/pom.xml clean verify
```

## PostgreSQL y semántica crítica

| Servidor exacto | Driver | Integration tests | Resultado |
|---|---:|---:|---|
| 15.18 | 42.7.13 | 76 | PASS |
| 16.14 | 42.7.13 | 76 | PASS |
| 17.10 | 42.7.13 | 76 | PASS |
| 18.4 | 42.7.13 | 76 | PASS |

En cada major, la suite completa cubre COPY CSV con NULL, empty, `\N`, `\.`, delimitadores,
CR/LF/CRLF y UTF-8; `bytea`, numeric y fechas/tiempos; schema e identificadores quoted; cancelación
por fallo de servidor/productor y finalización de COPY; insert y batching; CTAS + `ON COMMIT DROP`;
lookup simple/compuesto, duplicados, missing/nulls y cleanup; autocommit/read-only, commit/rollback,
transacción abortada y recuperación de conexión. Al ejecutarse completa también valida fragment
Spring Data, starter real y observabilidad. Domain/typmod/collation se verifican en los cuatro
servidores porque el mismo `TemporaryTableBulkLookupIT` forma parte de cada ejecución completa.

## Sensibilidad por capa

| Capa | Riesgo | API usada | Versiones probadas | Resultado |
|---|---|---|---|---|
| Hibernate adapter | SPI runtime y `ToOneAttributeMapping` internal | metamodelo 6.6 | 6.6.15, 6.6.53, 6.6.55 | PASS |
| Spring Data fragment | descubrimiento de fragment externo y transacción JPA | Spring Data JPA | 3.5.0, 3.5.13 | PASS |
| Spring Data JDBC metadata/fragment | mapping público, discovery, `EntityRowMapper` y transacciones | Spring Data JDBC/Relational | 3.5.0, 3.5.13 | PASS |
| Boot auto-config/starter | conditions, metadata, back-off y aplicación consumidora | Boot auto-config | 3.5.0, 3.5.16 | PASS |
| Boot JDBC auto-config/starter | conditions, single candidates, override, JDBC-only y both | Boot/Data JDBC | 3.5.0, 3.5.16 | PASS |
| Micrometer | ObservationRegistry, MeterRegistry y MeterFilter | Micrometer Observation/Core | 1.15.0, 1.15.12 | PASS |
| pgJDBC COPY | unwrap PGConnection, CopyManager/CopyIn, cancel/end | pgJDBC COPY API | 42.7.5, 42.7.13 | PASS |
| PostgreSQL SQL | COPY CSV, CTAS, ON COMMIT DROP, quoting | servidor vanilla | 15.18, 16.14, 17.10, 18.4 | PASS |

## Fallo clasificado

El probe `-Dhibernate.version=6.6.15.Final -pl postgres-bulk-spring-data -am` sobre las demás
dependencias de Boot 3.5.16 falló correctamente en Enforcer: Spring Data 3.5.13 resolvía
`antlr4-runtime` 4.13.2 y Hibernate 6.6.15 requería 4.13.0. Clasificación: **unsupported upstream
combination**. No se desactivó convergence ni se añadió override. Hibernate 6.6.15 sí pasa aislado
y en el stack coherente Boot 3.5.0; por eso el job Hibernate prueba el adapter y los jobs Boot
prueban la composición.

## Estrategia y límites

La matriz no es cartesiana. Java cambia el runtime manteniendo el stack; PostgreSQL cambia el
servidor; Boot cambia el BOM completo; Hibernate y pgJDBC cambian únicamente el adapter que los
consume; N01 hace el smoke combinatorio de límites nuevos. Esto cubre las fronteras donde existe
acoplamiento sin multiplicar 3×2×3×3×4 builds.

Spring Boot 4/Hibernate 7 no se compilaron como claim. La investigación oficial muestra que Boot
4.0 se alinea con Hibernate 7.2 y Boot 4.1 con Hibernate 7.4, además de Spring Framework 7/Spring
Data 4. Es otra generación y queda `PLANNED / UNSUPPORTED`. También quedan no probados patches
intermedios exactos, PostgreSQL 14 o anterior, futuras majors 19+, JDK 22–24/26, Boot 3.4 o
anterior y combinaciones manuales fuera de los BOM registrados.

La política upstream se apoya en [versiones PostgreSQL](https://www.postgresql.org/support/versioning/),
[Hibernate 6.6](https://hibernate.org/orm/releases/6.6/),
[integraciones Hibernate/Spring](https://hibernate.org/community/integrations/),
[dependencias Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
y [descargas pgJDBC](https://jdbc.postgresql.org/download/).

## CI

`build.yml` mantiene una baseline única y verifica ambos ejemplos como consumidores instalados.
`compatibility.yml` declara 11 ejecuciones adicionales:
composición multi-schema, Java 21/25, Boot 3.5.0, PostgreSQL 16.14/17.10, newest, Hibernate 6.6.15/6.6.55 y pgJDBC
42.7.5/42.7.13. PostgreSQL 15.18 y Boot 3.5.16 quedan cubiertos por baseline; PostgreSQL 18.4 por
newest. Todos los full reactors conservan JPA y añaden la evidencia JDBC; el job newest audita
explícitamente aislamiento del starter. Ambos workflows disparan en pull request y push a `main`.

La primera ejecución remota de MS7 sobre `726cbec` terminó verde el 2026-08-24:

- Build `32714347790` (historical run ID `32714347790`): PASS;
- Compatibility `32714347857` (historical run ID `32714347857`): PASS en sus 11 jobs.

La primera ejecución remota de cierre J7 sobre `2717f10` terminó verde el 2026-08-20:

- Build `32351155913` (historical run ID `32351155913`): PASS;
- Compatibility `32351155919` (historical run ID `32351155919`): PASS en sus 10 jobs.
