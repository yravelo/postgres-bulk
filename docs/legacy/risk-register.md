# Registro de riesgos del legacy

Escala: impacto y probabilidad `alta`, `media` o `baja`. Ningún workaround legacy se trasladará sin un test de caracterización.

| ID | Hallazgo | Impacto | Prob. | Evidencia / consecuencia | Tratamiento previsto |
|---|---|---:|---:|---|---|
| L-01 | `NULL` y string vacío se serializan igual | alta | alta | Ambos producen campo CSV vacío; pérdida silenciosa de datos | Contrato CSV y tests de NULL/empty antes del executor |
| L-02 | Gestión de conexión inconsistente en lookup | alta | alta | DDL/COPY usan JDBC; el SELECT usa JPA y podría usar otra conexión fuera de transacción | Una única conexión física por operación; tests con/sin transacción |
| L-03 | `ON COMMIT DROP` con autocommit | alta | alta | CREATE puede confirmar y eliminar la temporal antes del COPY | Precondición/transacción local controlada sin interferir con Spring |
| L-04 | Tipos SQL reconstruidos por heurística | alta | alta | snake_case/camelCase + `friendlyName`, fallback `TEXT` | Clonar definición desde PostgreSQL; spike para columnas seleccionadas/domains |
| L-05 | Metadata incompleta | alta | alta | Sólo campos declarados: falla herencia, property access, embedded IDs y converters | Adapter Hibernate probado contra metamodelo runtime |
| L-06 | Identificadores no modelados | alta | alta | Se ignora schema y se rechazan nombres quoted; posible tabla equivocada por `search_path` | Identificador estructurado schema/name y quoting centralizado |
| L-07 | Serialización con `toString()` | alta | alta | Locale, temporal, enum, binario y custom types no tienen contrato | Encoders explícitos y registry extensible |
| L-08 | Atomicidad de batches implícita | alta | media | Con autocommit pueden persistir batches previos tras un fallo | Semántica pública documentada y pruebas rollback/partial failure |
| L-09 | Cambio de `readOnly` no restaurado | media | media | Lookup fuerza `false` sobre conexión administrada | No mutar estado prestado; validar/read-only policy |
| L-10 | Acoplamiento a internals Hibernate/Spring Data | alta | alta | Upgrades pueden romper binariamente | Adaptadores aislados y compatibility tests |
| L-11 | Dependencia corporativa | alta | alta | Impide publicación independiente | API pública sin `AmigaJpaRepository` |
| L-12 | Lookup acepta entidades completas | media | alta | API poco tipada y obliga a construir objetos parciales | Clave simple tipada + key record/composite extractor |
| L-13 | Duplicados y nulos de clave sin semántica | media | media | JOIN duplica resultados y `NULL = NULL` no coincide | Decidir orden, deduplicación y política de null en ADR/API |
| L-14 | Orden de columnas ligado al nombre del field | alta | media | Cambios de nombre alteran COPY; converters/asociaciones multicolumna fallan | Orden definido por metadata inmutable |
| L-15 | Nombre temporal puede truncarse | media | baja | Límite PostgreSQL de 63 bytes puede provocar colisión | Prefijo corto + token aleatorio acotado + quoting |
| L-16 | Recursos temporales ante fallo | media | media | Sin transacción clara la tabla puede sobrevivir en conexión pooled | Cleanup explícito como respaldo y tests de reutilización del pool |
| L-17 | Errores no verificables | alta | alta | Faltan excepciones importadas, build y tests | Harness de caracterización independiente, no “arreglar” legacy |
| L-18 | `CopyField.column()` es código muerto | media | alta | La configuración declarada no cambia SQL | No migrar; eliminar o reemplazar con metadata consistente |

## Riesgos de la migración

- Diseñar la API antes de cerrar semánticas de lookup puede congelar tipos inadecuados. Mitigación: ADRs `PROPOSED` y pruebas de uso compilables.
- Pretender soportar varias generaciones de Hibernate en un solo artefacto puede filtrar internals incompatibles. Mitigación: baseline única inicial y valorar adapters/versiones separados sólo con evidencia.
- Añadir abstracciones para TEXT/BINARY prematuramente puede diluir el MVP. Mitigación: contrato interno de formato pequeño, CSV como única implementación inicial.
- Mezclar observabilidad o Spring con el core rompería la portabilidad. Mitigación: reglas de dependencia ejecutables con ArchUnit/jdeps en fases posteriores.

## Cierre verificado en Phase 11

- `L-02`: cerrado; DDL, COPY, SELECT JPA y DROP comparten backend, con prueba de
  `pg_backend_pid()` y transacciones reales.
- `L-03`: cerrado; lookup exige `autoCommit=false`, Spring aporta REQUIRED y la librería nunca
  reconfigura el recurso.
- `L-08`: cerrado; rollback manual revierte tres batches y autocommit conserva sólo los COPY
  completados antes del fallo.
- `L-09`: cerrado; read-only se rechaza temprano y Hikari size 1 confirma que read-only,
  autocommit e isolation no quedan contaminados.
- `L-16`: cerrado; DROP explícito más rollback/`ON COMMIT DROP` deja cero temporales en la misma
  sesión pooled después de éxito y fallo.

El registro conserva los hallazgos legacy como trazabilidad; "cerrado" describe la nueva
implementación, no una modificación del artefacto legacy.
