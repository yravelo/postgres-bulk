# ADR-001: Arquitectura modular en adapters hermanos

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

COPY, metadata Hibernate, repositorios Spring Data y auto-configuración cambian a ritmos distintos. La cadena lineal propuesta haría que Hibernate dependiera de pgJDBC sin necesitarlo y convertiría detalles de infraestructura en dependencias transitivas.

## Alternativas

1. **Monolito:** simple al inicio; mezcla APIs, aumenta superficie pública y hace imposible usar el motor sin Spring.
2. **Cadena estricta core → pgjdbc → hibernate → Spring:** fácil de dibujar; introduce dependencias artificiales y aumenta riesgo de ciclos.
3. **Core con adapters hermanos y composición superior:** fronteras honestas y tests aislados; requiere POMs y reglas arquitectónicas.

## Decisión

Adoptar seis módulos: core, pgjdbc, hibernate, spring-data, boot-autoconfigure y starter. pgjdbc e hibernate dependen de core pero no entre sí. Spring Data depende de core/pgjdbc para integración transaccional, pero recibe metadata por puerto. Auto-configure es el composition root que conecta los adapters concretos; sin Boot, lo hace configuración explícita del consumidor. No se añade un séptimo módulo hasta que exista contenido cohesionado que no encaje.

## Consecuencias

El reactor inicial tiene POMs sin clases vacías. Habrá algo de configuración repetida, compensada por aislamiento binario. Los package internals no sustituyen fronteras Maven. Cambiar el grafo requiere un ADR que explique qué dependencia nueva es necesaria.
