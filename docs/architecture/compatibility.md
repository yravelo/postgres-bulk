# Compatibilidad soportada

**Estado:** política validada el 2026-08-20. Las versiones exactas, comandos y resultados viven en
[`compatibility-evidence.md`](compatibility-evidence.md); ADR-021 define cómo evoluciona el soporte.

## Significado de los estados

- **SUPPORTED:** forma parte del contrato de mantenimiento de la línea actual.
- **VALIDATED:** existe una ejecución verde identificable; no implica por sí sola soporte futuro.
- **EXPERIMENTAL:** existe evidencia útil, pero no compromiso de mantenimiento contractual.
- **NOT TESTED:** no existe ejecución directa y no se infiere compatibilidad.
- **PLANNED:** pertenece al roadmap, no al artefacto actual.
- **UNSUPPORTED:** está fuera del contrato actual, aunque alguna combinación pueda compilar.

## Declaración de soporte

| Componente | Estado y rango | Versiones exactas validadas | Nota |
|---|---|---|---|
| Java | SUPPORTED 17 y 21 | 17.0.20, 21.0.12 | mínimo build/runtime 17; bytecode `release=17` |
| Java 25 | EXPERIMENTAL | 25.0.3 | suite completa validada; no es baseline contractual |
| Spring Boot | SUPPORTED 3.5.0–3.5.16 | 3.5.0, 3.5.16 | se prueban BOM mínimo y actual |
| Spring Framework | SUPPORTED vía Boot | 6.2.7, 6.2.19 | no se overridea independientemente |
| Spring Data JPA | SUPPORTED 3.5.0–3.5.13 vía Boot | 3.5.0, 3.5.13 | fragment externo, insert y lookup reales |
| Spring Data JDBC | SUPPORTED 3.5.0–3.5.13 vía Boot | 3.5.0, 3.5.13 | resolver, fragment, materialización, transacciones y starter reales |
| Spring Data Relational | SUPPORTED 3.5.0–3.5.13 vía Boot | 3.5.0, 3.5.13 | mapping context, paths, identifiers y row mapper reales |
| Hibernate ORM | SUPPORTED 6.6.15–6.6.55 | 6.6.15, 6.6.53, 6.6.55 | 6.6 es la única línea soportada |
| Micrometer | SUPPORTED vía Boot | 1.15.0, 1.15.12 | Observation, meters y filtro probados |
| pgJDBC | SUPPORTED 42.7.5–42.7.13 | 42.7.5, 42.7.11, 42.7.13 | contrato COPY real en ambos límites |
| PostgreSQL | SUPPORTED majors 15–18 | 15.18, 16.14, 17.10, 18.4 | imágenes con patch exacta |

Los patches intermedios de los rangos soportados no se ejecutan uno a uno. El claim usa una
estrategia de límites: ambos extremos verdes, stacks Boot coherentes y un smoke combinado de los
extremos nuevos. Una futura versión posterior a los máximos de la tabla no se considera soportada
hasta actualizar la matriz.

## Baseline por defecto

El parent fija Java 17 bytecode, Boot 3.5.16, JUnit 5.12.2, Testcontainers 2.0.5 y
`postgres.version=15.18-alpine`. El BOM resuelve:

| Spring Framework | Spring Data JPA | Spring Data JDBC/Relational | Hibernate | Micrometer | pgJDBC |
|---:|---:|---:|---:|---:|---:|
| 6.2.19 | 3.5.13 | 3.5.13 | 6.6.53.Final | 1.15.12 | 42.7.11 |

`./mvnw clean verify` usa sólo esa baseline PostgreSQL para conservar un loop local razonable.

## Stack mínimo y extremo nuevo

El límite mínimo coherente usa JDK 17 + Boot 3.5.0, que gestiona Spring Framework 6.2.7,
Spring Data JPA/JDBC/Relational 3.5.0, Hibernate 6.6.15.Final, Micrometer 1.15.0 y pgJDBC 42.7.5,
sobre PostgreSQL 15.18.

El smoke de extremos nuevos usa JDK 21 + Boot 3.5.16/Spring Data JPA/JDBC/Relational
3.5.13/Micrometer 1.15.12,
Hibernate 6.6.55.Final, pgJDBC 42.7.13 y PostgreSQL 18.4. Los overrides son patches dentro de las
líneas que los adapters declaran independientemente; Enforcer permanece activo.

## PostgreSQL

La suite completa pasa en 15.18, 16.14, 17.10 y 18.4. Incluye COPY CSV
(NULL, empty, literales `\N` y `\.`, UTF-8), bytea/numeric/temporales, identificadores quoted,
CTAS + `ON COMMIT DROP`, insert/lookup, duplicados, read-only, cancelación, transacción abortada,
rollback y reutilización de conexión. No hay branches de producción por versión ni extensiones.

Se fijan patches en vez de tags `15`/`16`/etc. para que un commit conserve el mismo servidor. La
actualización se hace deliberadamente según la
[política oficial de versiones PostgreSQL](https://www.postgresql.org/support/versioning/).

## Hibernate y Boot 4

`ToOneAttributeMapping` y el resto del SPI usado por el adapter permanecen compatibles entre
6.6.15 y 6.6.55. Hibernate 7 no lo usa Boot 3.5: la
[matriz oficial de integraciones](https://hibernate.org/community/integrations/) alinea Boot 4.0
con Hibernate 7.2 y Boot 4.1 con 7.4. Boot 4 también pertenece a Spring Framework 7/Spring Data 4.
Por tanto Boot 4/Hibernate 7 son **PLANNED y UNSUPPORTED** en esta línea; no se introducen probes
reflection ni multi-release JAR.

## Fuera del contrato

- Java <17, Spring Boot <3.5, Hibernate <6.6.15 o major 7+, pgJDBC <42.7.5 y PostgreSQL <15 son
  UNSUPPORTED.
- Boot >3.5.16, Spring Data >3.5.13, Hibernate >6.6.55, pgJDBC >42.7.13 y PostgreSQL
  19+ son NOT TESTED hasta una nueva actualización de evidencia.
- JDK 22–24/26, patches intermedios exactos y combinaciones manuales no producidas por los BOM son
  NOT TESTED, aunque el rango al que pertenezcan pueda estar soportado.
- El probe Hibernate 6.6.15 + Spring Data 3.5.13 es una combinación upstream incoherente y falla
  correctamente dependency convergence; Boot 3.5.0 y el adapter aislado sí pasan.

## Ejecución local

```bash
./mvnw clean verify
./mvnw clean verify -Dspring-boot.version=3.5.0
./mvnw clean verify -Dpostgres.version=18.4-alpine
./mvnw clean verify -pl postgres-bulk-hibernate -am -Dhibernate.version=6.6.55.Final
./mvnw clean verify -pl postgres-bulk-pgjdbc -am -Dpostgresql.version=42.7.13
./mvnw -DskipTests -pl postgres-bulk-spring-boot-starter-data-jdbc -am dependency:tree
./mvnw install
./mvnw -f ../../examples/spring-boot-data-jdbc/pom.xml clean verify
```

Spring Framework, Spring Data y Micrometer se cambian únicamente mediante
`spring-boot.version`. La matriz completa está separada del build normal en
`.github/workflows/compatibility.yml` y usa Linux, suficiente para la integración de servidor.

## Política de mantenimiento

Sólo entra una versión en `SUPPORTED` tras un job verde. Se retira una major PostgreSQL después de
su EOL upstream en el siguiente minor de la librería y se anuncia en release notes. El último patch
probado se actualiza mediante commit con evidencia reproducible. Véanse
[Hibernate 6.6](https://hibernate.org/orm/releases/6.6/),
[dependencias gestionadas Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html),
[Spring Data Relational 3.5](https://docs.spring.io/spring-data/relational/reference/3.5/) y
[pgJDBC](https://jdbc.postgresql.org/download/).
