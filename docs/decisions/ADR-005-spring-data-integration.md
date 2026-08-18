# ADR-005: Integración Spring Data mediante repository fragments

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Se busca `repository.bulkInsert(products)` sin heredar infraestructura corporativa. El legacy reemplaza factory y base class completas, una extensión potente pero acoplada a internals. Spring Data recomienda composición por fragments para funcionalidad reutilizable y reserva custom factory para cambios del mecanismo de creación ([documentación oficial](https://docs.spring.io/spring-data/jpa/reference/repositories/custom-implementations.html)).

## Alternativas

1. **Base repository/factory custom:** experiencia compacta y acceso directo a domain metadata; afecta globalmente y es frágil entre versiones.
2. **Fragment opt-in:** composición oficial, independiente de `JpaRepository` y sólo afecta repositorios elegidos; requiere resolver domain metadata por invocación/registro.
3. **Bean `BulkOperations<T>` separado únicamente:** mínimo acoplamiento; peor descubribilidad e inyección genérica.
4. **Fragment + bean separado:** mejor flexibilidad; riesgo de dos APIs divergentes.

## Decisión

Definir `PostgresBulkRepository<T, ID>` como fragment puro opt-in que extiende la capacidad
core `BulkOperations<T>`. El repositorio del usuario compone explícitamente
`JpaRepository<T, ID>` y el fragment. Su implementación se aporta desde el artefacto mediante
`META-INF/spring.factories`, mecanismo oficial de Spring Data 3.5 para fragments externos.

La implementación usa `RepositoryMethodContext` únicamente para conocer el domain type y
`JpaContext` para seleccionar el `EntityManager` de la persistence unit propietaria. Se descartan
base repository y factory custom: afectan globalmente, exigen configuración propia y no aportan
ninguna capacidad necesaria.

## Validación

El test Spring sin Boot prueba dos entidades/repositorios, registro externo, insert, lookup,
rollback, readOnly y `REQUIRES_NEW` sobre PostgreSQL 15.18. `JpaEntityMetadataResolver` selecciona
y cachea el resolver por identidad de `EntityManagerFactory`, evitando metadata cruzada entre
persistence units. La matriz mínimo/último Spring Data y el contexto Boot con back-off permanecen
en Phase 10/13.

## Consecuencias

La sintaxis final es `extends JpaRepository<Product, Long>, PostgresBulkRepository<Product,
Long>`. Es una interfaz extra que hace visible el opt-in y conserva separación. No se sobrecarga
`save` ni se reemplaza `SimpleJpaRepository`. Una factory custom deja de ser fallback previsto y
sólo se reconsiderará ante una necesidad nueva demostrable.
