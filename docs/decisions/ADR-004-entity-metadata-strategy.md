# ADR-004: Hibernate como proveedor de metadata física

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

JPA estándar no siempre expone nombre físico final, selectables múltiples, naming strategy o tipo relacional tras converter. La reflexión del legacy falla con herencia, property access, embeddables y asociaciones.

## Alternativas

1. **Sólo anotaciones/reflection JPA:** API estable, pero incompleta frente al mapping runtime.
2. **Internals Hibernate directamente en operaciones:** metadata precisa, pero contamina todo y rompe upgrades.
3. **Adapter Hibernate → descriptor core:** confina volatilidad y permite caching/test; requiere mantener un adapter por generación si cambian internals.
4. **Metadata programática obligatoria:** independiente del ORM, pero mala DX y duplica configuración.

## Propuesta

Usar el metamodelo runtime Hibernate en `postgres-bulk-hibernate` y producir descriptores core inmutables. JPA metadata es la primera señal; APIs Hibernate específicas completan nombres físicos, orden/selectables, accessors y tipos relacionales. Configuración programática puede sobrescribir metadata; anotaciones custom sólo expresarán información que JPA/Hibernate no pueda inferir, probablemente la definición de “bulk key”.

## Validación para aceptar

Spikes y tests para `@Table`/schema/quoted names, FIELD/PROPERTY, inherited `@Id`, `@EmbeddedId`, mapped superclass, embeddable, `ManyToOne`/`JoinColumn`, converter, enum, UUID y múltiples selectables. Compatibility suite en mínimo/último Hibernate 6.6.

## Consecuencias

El adapter será la pieza más sensible a upgrades. Los consumers sin Hibernate podrán construir metadata programáticamente, pero esa API no se diseñará hasta validar el modelo. No se infiere tipo PostgreSQL desde el nombre Java.

## Evidencia de aceptación en Phase 8

`HibernateEntityMetadataResolver` prueba el adapter contra Hibernate 6.6.55.Final y
PostgreSQL 15.18. Los tests cubren nombres físicos/naming strategies/quotes, FIELD y
PROPERTY, mapped superclasses, embeddables, `@EmbeddedId`, IDs asignados y generados,
selectables insertables, asociaciones y proxies, converters/enums, fórmulas, defaults,
tablas secundarias, herencia y cache concurrente aislado por persistence unit.

ADR-016 fija el subset exacto, la política de conversiones y las dependencias SPI que
concretan esta decisión. La definición de bulk key y cualquier override programático
continúan diferidos hasta que Phase 9 tenga un consumidor real.
