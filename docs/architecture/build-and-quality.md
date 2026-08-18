# Build y estrategia de calidad

## Baseline reproducible

El reactor se construye desde `code/postgres-bulk-parent` con Maven Wrapper `only-script` 3.3.4, fijado a Maven 3.9.16 y protegido por SHA-256. El Wrapper descarga exclusivamente desde Maven Central; no se versiona un JAR bootstrap. Maven 3.6.3 es el mínimo aceptado por Enforcer para quien decida no usar el Wrapper, y Maven 4 queda fuera hasta abandonar su estado preview.

El bytecode objetivo es Java 17 mediante `maven.compiler.release`; el JDK que ejecuta Maven debe ser 17 o superior. CI prueba Java 17 y 21. No se configura Maven Toolchains porque no se necesita un JDK distinto al que ejecuta el build; añadirlo hoy introduciría una precondición local sin beneficio.

`0.1.0-SNAPSHOT` identifica desarrollo preestable: no existe garantía de compatibilidad de API. `project.build.outputTimestamp` fija timestamps de artefactos durante esta etapa y deberá actualizarse de forma controlada en el proceso de release.

## Dependency management

El parent gestiona versiones, pero no añade dependencias runtime globales. Gestiona:

- artefactos internos con la versión del reactor;
- el BOM Spring Boot 3.5.16 para Spring Framework, Spring Data JPA, Hibernate y pgJDBC;
- el BOM JUnit 5.12.2;
- el BOM Testcontainers 2.0.5;
- versiones de plugins de lifecycle y quality gates.

Cada módulo declara únicamente dependencias que consume. `postgres-bulk-pgjdbc` declara
pgJDBC como implementación productiva y `testcontainers-postgresql`,
`testcontainers-junit-jupiter` y JUnit sólo con scope `test`. Testcontainers no es
transitivo en artefactos productivos y ningún otro módulo recibe pgJDBC por el parent.

El módulo autoconfigure ejecuta los processors oficiales de configuration metadata y
auto-configuration metadata. El starter agrega Data JPA y autoconfigure, sin código productivo.

## Tests

- Surefire ejecuta unit tests llamados `*Test.java` en la fase `test`.
- Failsafe ejecuta integration tests llamados `*IT.java` en `integration-test` y verifica resultados en `verify`.
- JUnit Jupiter se declara con scope `test` en cada módulo con pruebas.
- `./mvnw clean verify` ejecuta ambos carriles. Los integration tests pgJDBC requieren
  Docker y levantan `postgres:15.18-alpine`; no están ocultos tras un perfil.
- `ApplicationContextRunner` prueba once escenarios de conditions/back-off/observability sin Docker.
- El starter arranca una aplicación Boot real y prueba insert, lookup, rollback, read-only,
  pérdida de backend y Micrometer/Actuator contra PostgreSQL 15.18 sin wiring manual.

## Formato

Spotless 3.9.0 ejecuta `spotless:check` en `verify`. Java usa google-java-format 1.28.0, la línea compatible con ejecutar el formatter en Java 17. También prohíbe wildcard imports, elimina imports sin uso y normaliza whitespace/final newline. La corrección local será:

```bash
./mvnw spotless:apply
```

No se combina con Checkstyle para formato: dos autoridades producirían diagnósticos duplicados o contradictorios.

## Análisis estático

Phase 1 no incorpora SpotBugs, PMD, Error Prone ni Sonar:

- no existe código que analizar;
- Error Prone añade sensibilidad al compilador/JDK;
- SpotBugs será evaluable cuando exista bytecode significativo;
- PMD/Checkstyle sólo se añadirán si cubren reglas no satisfechas por compiler, tests, ArchUnit y Spotless;
- Sonar pertenece a infraestructura de hosting y no al build reproducible mínimo.

La prioridad actual es compiler explícito, dependency convergence, tests, formato y límites arquitectónicos.

## Límites arquitectónicos

Maven representa el DAG entre módulos y Enforcer verifica convergence. Core añade una regla `bannedDependencies` para Spring, Boot, Spring Data, Hibernate, Jakarta Persistence, pgJDBC y Micrometer.

Cuando existan clases, ArchUnit vivirá como dependencia `test` del módulo que pruebe arquitectura (inicialmente core y adapters; se reconsiderará un test-module común sólo si hay reglas cross-module). Las reglas previstas son:

- core sin imports JDBC/framework/ORM/pgJDBC/Micrometer;
- pgjdbc sin Spring Data/Hibernate;
- hibernate sin pgJDBC/Spring Data;
- starter sin clases productivas;
- API pública sin internals Hibernate o tipos pgJDBC.

No se crean tests ArchUnit vacíos. Hasta entonces, el DAG Maven, Enforcer y comprobaciones de CI son la evidencia ejecutable disponible.

Surefire y Failsafe quedan alineados en 3.5.4: durante la validación Phase 1, la documentación “current” anunciaba 3.6.0 pero el artefacto Failsafe correspondiente no estaba publicado en Central. El build usa la última pareja confirmada como resoluble, no una versión documental adelantada.

## Reproducibilidad y seguridad

No se declaran repositorios Maven: se usa Central por defecto. No hay rutas locales, credenciales, settings corporativos ni variables obligatorias. Las versiones de plugins de lifecycle están fijadas. GitHub Actions usa el mismo `./mvnw clean verify`, permisos read-only y cache nativa de `setup-java` basada en todos los POM.

El build no publica ni firma. Sources/Javadocs/signing/provenance se incorporarán en Phase 16 sin cambiar la separación de módulos.
