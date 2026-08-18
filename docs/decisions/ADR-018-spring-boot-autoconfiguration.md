# ADR-018: Autoconfiguración Boot mínima, condicional y sin I/O de arranque

**Status:** ACCEPTED
**Date:** 2026-08-18

## Contexto

Phase 9 exige que una aplicación sin Boot declare un `JpaEntityMetadataResolver`. Phase 10 debe
eliminar ese wiring al usar el starter sin activar repositories de forma global, fijar detalles
internos como properties prematuras ni abrir una conexión durante el arranque.

## Alternativas

1. Component scanning y configuración explícita en la aplicación.
2. Auto-configuración legacy registrada mediante `spring.factories`.
3. Auto-configuración moderna, con condiciones estructurales, back-off por tipo y una sola
   propiedad kill switch.
4. Detectar PostgreSQL consultando `DatabaseMetaData` al arrancar.

## Decisión

Se elige la tercera alternativa. `PostgresBulkAutoConfiguration` se registra mediante
`AutoConfiguration.imports`, después de Hibernate JPA y antes de Spring Data repositories. Se
condiciona a las clases JPA/Hibernate/pgJDBC/Spring Data/library, a la presencia de cualquier
`EntityManagerFactory` y a `postgres-bulk.enabled=true` (default). Crea sólo el bridge cacheado de
metadata y hace back-off ante cualquier `JpaEntityMetadataResolver` del usuario.

El fragmento continúa siendo opt-in por herencia de `PostgresBulkRepository`; la
auto-configuración no reemplaza `SimpleJpaRepository`, no escanea componentes y no crea conexiones.
Varias factories son válidas porque la selección ocurre por tipo gestionado en cada llamada.

Se rechaza la detección JDBC en startup: la presencia del driver es el gate barato y una base no
PostgreSQL falla lazily al hacer unwrap en la primera operación bulk. Sólo se publica
`postgres-bulk.enabled`; batch sigue siendo una opción de invocación y buffer/prefijo temporal
siguen internos.

El starter permanece sin código y agrega Data JPA más el artefacto de autoconfiguración. Boot BOM
3.5.16 gobierna Spring Data 3.5.13, Spring Framework 6.2.19, Hibernate 6.6.53.Final y pgJDBC
42.7.11 en la baseline integrada.

## Consecuencias

- Añadir starter y extender el fragmento basta para usar la librería con defaults.
- Un bean propio sustituye totalmente el resolver default sin nombres mágicos.
- El arranque sigue siendo lazy respecto al datasource y no puede certificar el producto de base
  de datos; el diagnóstico aparece al primer uso incompatible.
- Dos persistence units funcionan si cada dominio pertenece inequívocamente a una.
- `PostgresBulkAutoConfiguration` y `PostgresBulkProperties` son infraestructura framework pública,
  no nueva API de operaciones para invocación directa.
- Tuning global, observabilidad, failure analyzers y endurecimiento transaccional quedan fuera de
  Phase 10.
