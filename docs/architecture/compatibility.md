# Matriz inicial de compatibilidad

**Estado:** propuesta para discusión, fechada 2026-08-18. No es todavía una promesa de soporte.

## Baseline recomendada para la primera línea

| Componente | Baseline de compilación | Matriz de prueba propuesta | Motivo |
|---|---|---|---|
| Java | 17 bytecode | 17, 21, 25 | Máxima adopción compatible con Boot 3.5; 21 recomendado al desarrollar |
| Spring Boot | 3.5.x | último patch 3.5.x | Línea estable basada en Spring 6.2/Hibernate 6.6 |
| Spring Framework | 6.2.x, gobernado por Boot | versión gestionada por cada patch Boot | Evitar overrides de BOM |
| Spring Data JPA | 3.5.x, gobernado por Boot | versión gestionada por cada patch Boot | Fragments soportados y menor exposición a internals |
| Hibernate ORM | 6.6.x, gobernado por Boot | mínimo y último 6.6 soportado por Boot | Adapter dedicado; Hibernate 6.6 declara compatibilidad Boot 3.4–3.5 |
| PostgreSQL | 15 | 15, 16, 17, 18 | Versiones con soporte suficiente; PostgreSQL 14 termina soporte en 2026-11 |
| pgJDBC | 42.7.x | versión gestionada por Boot y último 42.7.x | COPY API estable; el consumidor mantiene BOM |
| Maven | 3.6.3 mínimo | Wrapper 3.9.16; CI con JDK 17/21 | Release Maven 3 estable y mínimo documentado por Boot 3.5 |

Spring Boot 3.5.16 requiere Java 17 y Spring Framework 6.2.19 o superior ([requisitos oficiales](https://docs.spring.io/spring-boot/3.5/system-requirements.html)); su BOM gestiona Spring Data JPA 3.5.13 ([coordenadas gestionadas](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)). Hibernate 6.6 declara Java 11/17/21/25 y compatibilidad con Boot 3.4–3.5, aunque ya está en soporte limitado ([matriz Hibernate 6.6](https://hibernate.org/orm/releases/6.6/)). pgJDBC publica actualmente 42.7.13 para Java 8+ ([descargas oficiales](https://jdbc.postgresql.org/download/)). PostgreSQL mantiene hoy 14–18, pero 14 llega a EOL el 2026-11-12 ([política oficial](https://www.postgresql.org/support/versioning/)).

## Alternativa Boot 4

Boot 4.1.0 es la línea estable más nueva y soporta Java 17–26 con Spring Framework 7.0.8+ ([requisitos oficiales](https://docs.spring.io/spring-boot/system-requirements.html)). Adoptarla desde el primer release reduciría deuda futura, pero introduce Spring Data 4/Hibernate 7 y aleja a consumidores Boot 3. La recomendación es:

1. lanzar una primera línea pequeña sobre Boot 3.5/Hibernate 6.6;
2. diseñar core y pgjdbc sin esa generación;
3. ejecutar en Phase 13 un spike Boot 4.1/Hibernate 7;
4. decidir entre compatibilidad binaria real, classifier/adapters separados o major `2.x`.

No se afirmará soporte dual hasta que el mismo suite de integración pase. Dado que Hibernate 6.6 está en soporte limitado, el spike Boot 4 es requisito de release, no trabajo indefinido.

## Política de versiones

- La librería compila contra una versión mínima fijada; los compatibility tests prueban mínimo y último patch.
- Spring/Boot/Hibernate/pgJDBC no se versionan en APIs públicas.
- El starter respeta dependency management del consumidor y documenta la matriz probada.
- Se prueban sólo minors PostgreSQL soportados por la comunidad; se elimina una major después de su EOL en el siguiente minor de la librería, avisado en CHANGELOG.
- “Compatible” significa build, unit tests y suite Testcontainers completa; no sólo resolución Maven.

## Baseline de build fijada en Phase 1

Java 17 y Maven Wrapper 3.9.16 quedan fijados por ADR-007. El build puede ejecutarse con JDK superior, pero `maven.compiler.release=17` evita adoptar accidentalmente APIs de ese JDK. Maven 4 no se usa mientras sea preview. Esto fija infraestructura de build, no convierte todavía la matriz Spring/Hibernate/PostgreSQL en promesa de soporte.

## Evidencia pgJDBC de Phase 5

El executor compila y se prueba con pgJDBC 42.7.13. La suite Failsafe ejecuta el contrato
completo sobre `postgres:15.18-alpine`: dialecto COPY CSV, UTF-8, tipos soportados,
identificadores quoted, conteo, autocommit, commit/rollback y recuperación tras fallos.
Esto confirma la baseline PostgreSQL 15, no la matriz propuesta 16–18; esa matriz sigue
reservada para Phase 13.

## Decisiones aún abiertas

- Coordenadas definitivas (`groupId` actual es provisional).
- Política exacta Boot 4/Hibernate 7.
- Java 17 frente a 21 como baseline después de conocer consumidores objetivo.
- Soporte explícito de PostgreSQL 14 durante una ventana inicial corta.
