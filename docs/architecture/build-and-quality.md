# Build y estrategia de calidad

## Baseline reproducible

El reactor se construye desde `code/postgres-bulk-parent` con Maven Wrapper `only-script` 3.3.4, fijado a Maven 3.9.16 y protegido por SHA-256. El Wrapper descarga exclusivamente desde Maven Central; no se versiona un JAR bootstrap. Maven 3.6.3 es el mínimo aceptado por Enforcer para quien decida no usar el Wrapper, y Maven 4 queda fuera hasta abandonar su estado preview.

El bytecode objetivo es Java 17 mediante `maven.compiler.release`; el JDK que ejecuta Maven debe
ser 17 o superior. El build baseline usa Java 17; compatibility CI prueba Java 21 y 25 sin cambiar
el bytecode. Java 17/21 son soportados y JDK 25 es validación adicional. No se configura Maven
Toolchains porque CI selecciona el JDK y el developer puede hacer lo mismo con `JAVA_HOME`.

`0.1.0-SNAPSHOT` identifica desarrollo preestable: no existe garantía de compatibilidad de API ni
publicación remota. `project.build.outputTimestamp` fija timestamps de artefactos durante esta etapa
y deberá actualizarse de forma controlada en el proceso de release.

## Dependency management

El parent gestiona versiones, pero no añade dependencias runtime globales. Gestiona:

- artefactos internos con la versión del reactor;
- el BOM Spring Boot 3.5.16 para Spring Framework, Spring Data JPA, Hibernate, Micrometer y pgJDBC;
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
- `./mvnw clean verify` ejecuta ambos carriles. Los integration tests requieren Docker y levantan
  por defecto `postgres:15.18-alpine`; `postgres.version` está centralizada en el parent y puede
  cambiarse con `-Dpostgres.version=18.4-alpine`. No están ocultos tras un perfil.
- `ApplicationContextRunner` prueba once escenarios de conditions/back-off/observability sin Docker.
- El starter arranca una aplicación Boot real y prueba insert, lookup, rollback, read-only,
  pérdida de backend y Micrometer/Actuator contra PostgreSQL 15.18 sin wiring manual.
- `examples/spring-boot-basic` participa como módulo consumidor no publicable. Su POM tiene parent
  Spring Boot propio, depende sólo del starter de la librería y prueba insert, options, lookup
  simple/compuesto, rollback y métricas con Testcontainers. El mismo POM se verifica también fuera
  del reactor después de `install` para detectar dependencias ocultas.

## Formato

Spotless 3.9.0 ejecuta `spotless:check` en `verify`. Java usa google-java-format 1.28.0, la línea compatible con ejecutar el formatter en Java 17. También prohíbe wildcard imports, elimina imports sin uso y normaliza whitespace/final newline. La corrección local será:

```bash
./mvnw spotless:apply
```

No se combina con Checkstyle para formato: dos autoridades producirían diagnósticos duplicados o contradictorios.

## Documentación y Javadocs

`maven-javadoc-plugin` 3.12.0 ejecuta doclint con `failOnWarnings=true` durante `verify` para los
módulos de librería. Benchmarks y example se excluyen porque son consumidores no publicables, no
API. Por tanto un método/tipo público sin contrato suficiente rompe el build en vez de dejar un
warning ignorado.

`scripts/check-documentation.sh` valida todos los targets Markdown relativos, comprueba que el
example no dependa de módulos internos e imprime el inventario de tipos públicos productivos. El
workflow baseline ejecuta este audit y reconstruye el example como consumidor externo después de
instalar el snapshot local.

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

No se declaran repositorios Maven: se usa Central por defecto. No hay rutas locales, credenciales,
settings corporativos ni variables obligatorias. Las versiones de plugins de lifecycle están
fijadas. GitHub Actions usa el mismo `./mvnw clean verify`, permisos read-only y cache nativa de
`setup-java`. `build.yml` ejecuta la baseline; `compatibility.yml` separa JDK, BOM Boot, servidor,
Hibernate y pgJDBC mediante límites/pairwise, manteniendo Enforcer activo.

Los overrides reproducibles son `spring-boot.version`, `hibernate.version`,
`postgresql.version` y `postgres.version`. Spring Framework, Spring Data y Micrometer se cambian
únicamente mediante el BOM Boot. La evidencia exacta se registra en
[`compatibility-evidence.md`](compatibility-evidence.md).

El build no publica ni firma. Phase 15 valida la documentación Javadoc pero no adjunta todavía JARs
de sources/Javadocs; signing, provenance y esos artefactos de release pertenecen a Phase 16.

## Benchmarks

`postgres-bulk-benchmarks` es un consumidor JMH separado y tiene `maven.deploy.skip=true`. El
reactor lo compila y empaqueta para detectar drift, pero Surefire/Failsafe no descubren ni ejecutan
sus métodos `@Benchmark`. No existen assertions ni thresholds de rendimiento en Maven.

La ejecución explícita vive en `scripts/run-benchmarks.sh`; un PostgreSQL Testcontainers real se
comparte entre forks y los JSON se guardan como evidencia. `.github/workflows/benchmarks.yml` sólo
acepta `workflow_dispatch`, por lo que una corrida larga no bloquea PRs ni el build baseline.
Metodología, entorno, resultados y límites están en
[`../benchmarks/methodology.md`](../benchmarks/methodology.md) y
[`../benchmarks/baseline.md`](../benchmarks/baseline.md).
