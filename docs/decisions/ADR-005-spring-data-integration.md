# ADR-005: Integración Spring Data mediante repository fragments

- **Estado:** PROPOSED
- **Fecha:** 2026-08-18

## Contexto

Se busca `repository.bulkInsert(products)` sin heredar infraestructura corporativa. El legacy reemplaza factory y base class completas, una extensión potente pero acoplada a internals. Spring Data recomienda composición por fragments para funcionalidad reutilizable y reserva custom factory para cambios del mecanismo de creación ([documentación oficial](https://docs.spring.io/spring-data/jpa/reference/repositories/custom-implementations.html)).

## Alternativas

1. **Base repository/factory custom:** experiencia compacta y acceso directo a domain metadata; afecta globalmente y es frágil entre versiones.
2. **Fragment opt-in:** composición oficial, independiente de `JpaRepository` y sólo afecta repositorios elegidos; requiere resolver domain metadata por invocación/registro.
3. **Bean `BulkOperations<T>` separado únicamente:** mínimo acoplamiento; peor descubribilidad e inyección genérica.
4. **Fragment + bean separado:** mejor flexibilidad; riesgo de dos APIs divergentes.

## Propuesta

Definir una única abstracción `BulkOperations<T>` y adaptarla como fragment `PostgresBulkRepository<T, ID>` opt-in. El repository del usuario podrá extender `JpaRepository` y el fragment. La fachada programática reutiliza el mismo motor; no duplica semántica. Explorar registro externo de fragments/metadata soportado por Spring Data antes de una factory custom.

## Validación para aceptar

Proof-of-concept con dos entidades, múltiples persistence units, repositorio sólo fragment y repositorio `JpaRepository + fragment`; contexto Boot con back-off; upgrade entre mínimo/último Spring Data 3.5. Debe funcionar sin `@Enable...` propietario en el caso starter común.

## Consecuencias

La sintaxis final podría ser `extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long>` en vez de que el fragment extienda JPA. Es una línea extra que preserva separación. Una factory custom queda como fallback documentado, no como punto de partida.
