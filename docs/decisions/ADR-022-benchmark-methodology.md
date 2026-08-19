# ADR-022: Benchmarks JMH aislados y sin gates de rendimiento

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-19

## Contexto

La librería necesita comparar JPA, JDBC batch y COPY sin medir construcción de datos, esconder
commit ni convertir ruido de infraestructura en promesas universales. El build normal debe seguir
siendo determinista y no depender del rendimiento de Docker o del host.

## Decisión

- Se crea `postgres-bulk-benchmarks`, módulo JMH 1.37 no publicable que depende de los artefactos
  productivos como consumidor y genera un JAR ejecutable.
- JMH se compila en el reactor, pero ninguna fase Maven ejecuta benchmarks. Las corridas son
  explícitas mediante script y workflow manual `workflow_dispatch`.
- Un runner padre mantiene un PostgreSQL Testcontainers real durante todos los forks. Setup,
  dataset, `TRUNCATE` y verificaciones quedan fuera del tiempo; cada operación medida incluye su
  transacción y commit.
- Se publican raw JSON, CSV, seed, esquema, versiones, hardware, warmups, iteraciones, forks,
  allocation profiler, limitaciones y repetición completa. No se añaden thresholds.
- Los resultados pueden motivar experimentos, no cambios de defaults ni claims de producción por
  sí solos.

## Consecuencias

`clean verify` gana compilación del arnés y un JAR sombreado, pero no tiempo de benchmark ni Docker
adicional por Phase 14. El módulo no llega a deploy. La baseline detectó como bug real que metadata
Hibernate entrega `LocalDate` como `java.sql.Date`; el encoder incorpora ese tipo JDBC con prueba
de regresión, sin cambiar API.

La evidencia es local y ruidosa. JPA 1M no se ejecuta por presión de heap demostrada a 100K; JDBC
y COPY sí tienen un perfil suplementario 1M. Lookup requiere experimentos posteriores de plan
antes de cualquier estrategia adaptativa.
