# ADR-002: Core independiente de frameworks e infraestructura

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

El legacy entremezcla API, Spring Data, Hibernate, pgJDBC, reflection y SQL. Eso impide probar semánticas puras, publicar una API estable y actualizar adapters de forma independiente.

## Alternativas

1. **Core basado en JPA/JDBC:** menos tipos propios, pero filtra contratos externos y contradice la portabilidad de la lógica bulk.
2. **Abstracción máxima sin tipos Java concretos:** aislamiento fuerte, pero duplica JDBC/ORM y genera puertos vacíos.
3. **Core Java SE con modelos mínimos y puertos justificados:** protege la API y conserva concreción.

## Decisión

Core sólo conoce Java SE y conceptos bulk propios. No importa Spring, JPA, Hibernate, JDBC, pgJDBC ni Micrometer. Los modelos aparecen cuando una fase tiene un consumidor y una implementación/test; no se crean interfaces enumeradas únicamente por el diseño.

## Consecuencias

Hibernate debe traducir su metamodelo a metadata propia y pgjdbc adaptar el executor. Puede existir código de ensamblaje en Spring Data. La regla se verificará automáticamente. Si un concepto sólo tiene sentido en PostgreSQL —por ejemplo CSV COPY o quoting SQL— no se moverá a core para reutilizarlo artificialmente.
