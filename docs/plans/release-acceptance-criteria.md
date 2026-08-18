# Criterios de aceptación de la primera release estable

## Funcionalidad

- Insert acepta input vacío, 1, múltiples y al menos 10k filas con batching correcto.
- Lookup por clave simple/compuesta usa temp table + COPY + JOIN sin aceptar entidades parciales como requisito.
- Resultados y semántica de duplicados/null/orden/partial failure están documentados y probados.
- CSV round-trip preserva NULL, empty, delimitadores, quotes, CR/LF/CRLF, UTF-8 y tipos soportados.

## Arquitectura y API

- Core no referencia Spring/JPA/Hibernate/JDBC/pgJDBC/Micrometer.
- pgJDBC e Hibernate son adapters hermanos; no hay ciclos.
- API pública no expone COPY, CSV, temp tables, `PGConnection` ni internals Hibernate.
- Starter no contiene lógica Java y hace back-off ante configuración del usuario.
- No existe dependencia corporativa ni clase placeholder.

## Metadata y SQL

- Mapping validado para schema/quoted names, FIELD/PROPERTY, inherited/embedded IDs, mapped superclass, embeddable, associations, converters, enums y UUID.
- Identificadores se citan por componente; no hay camel/snake heuristics como fallback silencioso.
- Tipos de la temporal provienen de PostgreSQL o metadata física verificable, incluyendo domains/custom types acordados.

## Transacciones y robustez

- [x] DDL, COPY y JOIN de una operación usan una misma conexión física.
- [x] Commit, rollback, readOnly, autocommit, REQUIRED, REQUIRES_NEW, rollback-only y failure
      cleanup tienen comportamiento explícito; NESTED queda UNSUPPORTED en la baseline con test.
- [x] La librería no confirma, revierte, cierra físicamente ni muta una conexión Spring prestada
      fuera de su contrato.
- [x] No quedan COPY streams ni temporales al reutilizar una conexión tras éxito o fallo + rollback
      en Hikari con `maximumPoolSize=1`.
- [x] El fallo primario prevalece, cleanup queda suppressed y SQLState permanece accesible para
      constraints, transacción abortada y pérdida de conexión.
- [x] No existe retry automático; autocommit parcial e idempotencia pertenecen al caller.
- [x] Reutilización repetida, ocho operaciones concurrentes independientes y terminación de
      backend tienen evidencia PostgreSQL real.

## Calidad y compatibilidad

- Unit, integration, architecture y compatibility suites verdes en la matriz publicada.
- PostgreSQL real/Testcontainers valida datos; mocks no sustituyen integración.
- Benchmarks son reproducibles y no forman parte de assertions/build normal.
- Javadocs y snippets compilan; quickstart funciona desde un proyecto consumidor limpio.
- Build reproducible con Wrapper, CI, sources/Javadocs y release dry-run.

## Operación segura

- Logs/metrics no incluyen filas, claves ni tags de alta cardinalidad.
- Una observación por operación registra duración y error con tags cerrados; filas y batches son
  totales monotónicos de éxito, sin instrumentación por fila/COPY.
- La observabilidad es fail-open, se puede desactivar independientemente y no requiere Actuator,
  exporter ni registry creado por la librería.
- Excepciones conservan causa SQL y distinguen configuración/metadata/mapping/ejecución sin una clase por fallo.
- Defaults funcionan sin configuración y cada property existente resuelve un caso demostrado.

## Estado de release

Phase 12 cierra los criterios de observabilidad, pero la release final **no está ready**. Aún
faltan la matriz de compatibilidad completa, benchmarks/documentación de adopción y gates de
release de las Phases 13–16.
