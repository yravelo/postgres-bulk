# Evidencia de compatibilidad

**Corte de evidencia:** 2026-08-19. Todos los comandos se ejecutaron desde
`code/postgres-bulk-parent` en Linux, con Maven Wrapper 3.9.16, Docker 29.7.0 y sin omitir
Enforcer ni tests de integración.

## Fuente de verdad actual

| Componente | Versión/rango declarado | Versión resuelta por defecto | Versión validada | Fuente |
|---|---|---|---|---|
| Java | mínimo 17; `release=17` | JDK del build | Temurin 17.0.20, 21.0.12; OpenJDK 25.0.3 | parent POM, Enforcer y ejecuciones J01–J03 |
| Spring Boot | 3.5.0–3.5.16 | 3.5.16 | 3.5.0, 3.5.16 | `spring-boot.version`, BOM y B01/J01 |
| Spring Framework | gobernado por Boot | 6.2.19 | 6.2.7, 6.2.19 | dependency tree de B01/J01 |
| Spring Data JPA | gobernado por Boot | 3.5.13 | 3.5.0, 3.5.13 | dependency tree y starter IT |
| Hibernate ORM | 6.6.15–6.6.55 | 6.6.53.Final | 6.6.15, 6.6.53, 6.6.55 | BOM y H01/H02/full stack |
| Micrometer | gobernado por Boot | 1.15.12 | 1.15.0, 1.15.12 | dependency tree y observability tests |
| pgJDBC | 42.7.5–42.7.13 | 42.7.11 | 42.7.5, 42.7.11, 42.7.13 | BOM y D01/D02/full stack |
| PostgreSQL | majors 15–18 | `15.18-alpine` | 15.18, 16.14, 17.10, 18.4 | parent `postgres.version` y Testcontainers |

La baseline real del POM es Boot 3.5.16, Spring Framework 6.2.19, Spring Data JPA 3.5.13,
Hibernate 6.6.53.Final, Micrometer 1.15.12, pgJDBC 42.7.11 y PostgreSQL 15.18. Esto corrige
referencias históricas de fases anteriores que usaron manualmente Hibernate 6.6.55/pgJDBC 42.7.13
antes de integrar el BOM Boot.

## Auditoría de los BOM límite

Comando, repetido con `3.5.0` y `3.5.16`:

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Dspring-boot.version=3.5.0 -DskipTests \
  -pl postgres-bulk-spring-boot-starter -am dependency:tree \
  -Dincludes=org.springframework:spring-core,org.springframework.data:spring-data-jpa,org.hibernate.orm:hibernate-core,io.micrometer:micrometer-core,org.postgresql:postgresql
```

| Boot | Spring Framework | Spring Data JPA | Hibernate ORM | Micrometer | pgJDBC | Enforcer/resolution |
|---|---|---|---|---|---|---|
| 3.5.0 | 6.2.7 | 3.5.0 | 6.6.15.Final | 1.15.0 | 42.7.5 | PASS |
| 3.5.16 | 6.2.19 | 3.5.13 | 6.6.53.Final | 1.15.12 | 42.7.11 | PASS |

## Matriz ejecutada

`216` significa 140 unit/context tests y 76 integration tests. Los jobs focalizados Hibernate
ejecutan 35 tests core + 13 IT del adapter; los focalizados pgJDBC ejecutan 35 core + 83 unit del
adapter + 40 IT.

| ID | Java | Boot | Spring Data | Hibernate | Micrometer | pgJDBC | PostgreSQL | Tests | Resultado |
|---|---|---|---|---|---|---|---|---:|---|
| J01 baseline | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.11 | 15.18 | 216 | PASS |
| J02 LTS | 21.0.12 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.11 | 15.18 | 216 | PASS |
| J03 newer | 25.0.3 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.11 | 15.18 | 216 | PASS |
| B01 Boot min | 17.0.20 | 3.5.0 | 3.5.0 | 6.6.15 | 1.15.0 | 42.7.5 | 15.18 | 216 | PASS |
| P16 server | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.11 | 16.14 | 216 | PASS |
| P17 server | 17.0.20 | 3.5.16 | 3.5.13 | 6.6.53 | 1.15.12 | 42.7.11 | 17.10 | 216 | PASS |
| N01 newest | 21.0.12 | 3.5.16 | 3.5.13 | 6.6.55 | 1.15.12 | 42.7.13 | 18.4 | 216 | PASS |
| H01 adapter min | 25.0.3 | n/a | n/a | 6.6.15 | n/a | 42.7.11 test | 15.18 | 48 | PASS |
| H02 adapter max | 25.0.3 | n/a | n/a | 6.6.55 | n/a | 42.7.11 test | 15.18 | 48 | PASS |
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

## PostgreSQL y semántica crítica

| Servidor exacto | Driver | Integration tests | Resultado |
|---|---:|---:|---|
| 15.18 | 42.7.11 | 76 | PASS |
| 16.14 | 42.7.11 | 76 | PASS |
| 17.10 | 42.7.11 | 76 | PASS |
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
| Boot auto-config/starter | conditions, metadata, back-off y aplicación consumidora | Boot auto-config | 3.5.0, 3.5.16 | PASS |
| Micrometer | ObservationRegistry, MeterRegistry y MeterFilter | Micrometer Observation/Core | 1.15.0, 1.15.12 | PASS |
| pgJDBC COPY | unwrap PGConnection, CopyManager/CopyIn, cancel/end | pgJDBC COPY API | 42.7.5, 42.7.11, 42.7.13 | PASS |
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

`build.yml` mantiene una baseline única. `compatibility.yml` declara 10 ejecuciones adicionales:
Java 21/25, Boot 3.5.0, PostgreSQL 16.14/17.10, newest, Hibernate 6.6.15/6.6.55 y pgJDBC
42.7.5/42.7.13. PostgreSQL 15.18 y Boot 3.5.16 quedan cubiertos por baseline; PostgreSQL 18.4 por
newest. Ambos workflows disparan en pull request y push a `main`. El YAML y sus comandos se
validaron localmente; no se afirma una ejecución remota de GitHub Actions.
