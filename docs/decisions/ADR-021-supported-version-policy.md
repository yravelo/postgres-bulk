# ADR-021: Política de versiones soportadas

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-19

## Contexto

La librería cruza seis ejes que evolucionan a ritmos distintos: Java, Spring Boot/Spring Data,
Hibernate, pgJDBC y PostgreSQL. Probar su producto cartesiano sería costoso y aportaría poca
información adicional, pero declarar una línea completa a partir de una sola combinación tampoco
sería evidencia suficiente. El adapter Hibernate usa SPI runtime y un tipo `internal`, mientras que
el adapter pgJDBC depende directamente de la API COPY; son los dos límites más sensibles.

## Decisión

- Java 17 es el mínimo de build y runtime y continúa siendo el target de bytecode. Java 17 y 21
  son `SUPPORTED` y se ejecutan con la suite completa. JDK 25 se conserva como validación adicional,
  sin convertirlo en compromiso contractual.
- La línea Spring soportada por el artefacto actual es Boot 3.5.0–3.5.16. Sus stacks gestionados
  mínimo y actual se validan completos; Spring Framework, Spring Data JPA, Spring Data
  JDBC/Relational, Hibernate, Micrometer y pgJDBC se alinean mediante el BOM, no mediante
  combinaciones inventadas.
- Hibernate se soporta en 6.6.15.Final–6.6.55.Final. Se prueban ambos límites directamente sobre el
  adapter, incluido `ToOneAttributeMapping`; la integración completa se prueba con los dos stacks
  Boot gestionados y con el límite nuevo.
- pgJDBC se soporta en 42.7.5–42.7.13 y ambos límites ejecutan el contrato COPY real.
- PostgreSQL 15–18 se soporta mientras cada major siga soportada upstream. CI usa patches exactas,
  actualmente 15.18, 16.14, 17.10 y 18.4; no usa tags flotantes por major.
- La matriz usa fronteras y pairwise: baseline, stack Boot mínimo, stack más nuevo, cada servidor,
  límites del adapter Hibernate y límites del driver. Enforcer sigue activo en todas las
  combinaciones.
- Una nueva versión no entra en `SUPPORTED` hasta tener un job verde. Una versión PostgreSQL EOL se
  elimina en el siguiente minor de la librería, con aviso de release. Actualizar un patch fijado es
  un cambio deliberado acompañado por evidencia nueva.

Spring Boot 4/Hibernate 7 pertenecen a Spring Framework 7/Spring Data 4 y quedan `PLANNED` pero
`UNSUPPORTED` por este artefacto. Si se soportan, será mediante una generación o adapter decidido en
otro ADR; no se usarán reflection probes ni un multi-release JAR.

## Consecuencias

La matriz no demuestra cada patch intermedio ni cada combinación posible. El claim soportado se
basa en límites verdes y en stacks Boot coherentes; las versiones exactas ejecutadas quedan en
`compatibility-evidence.md`. Una combinación manual puede fallar correctamente por convergence si
mezcla versiones que ningún BOM soportado produce.

No cambia la API pública, no se añaden dependencias productivas y no existe detección runtime de
versión. El build local sigue probando una única baseline PostgreSQL; CI amplía los ejes.

## Progresión Spring Data JDBC J7

J7 aplica la misma política boundary/pairwise a la integración JDBC. Boot 3.5.0 y 3.5.16 validan
respectivamente Data JDBC/Relational 3.5.0 y 3.5.13; Java 17/21, pgJDBC 42.7.5/42.7.13 y
PostgreSQL 15.18/16.14/17.10/18.4 mantienen sus límites existentes. Los full reactors incluyen
resolver, fragment discovery, `EntityRowMapper`, transacciones, autoconfiguration, starter,
JDBC-only, coexistencia JPA y el ejemplo ejecutable. No se infieren Boot/Data 4 ni patches no
ejecutados.

## Fuentes upstream

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 3.5 managed coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
- [Spring Data Relational 3.5](https://docs.spring.io/spring-data/relational/reference/3.5/)
- [Hibernate ORM 6.6](https://hibernate.org/orm/releases/6.6/)
- [Hibernate integrations](https://hibernate.org/community/integrations/)
- [pgJDBC downloads](https://jdbc.postgresql.org/download/)
- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/)
