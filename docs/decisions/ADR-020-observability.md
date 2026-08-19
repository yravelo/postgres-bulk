# ADR-020: Observabilidad por operación bulk con cardinalidad acotada

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Insert y lookup ya tienen una frontera pública estable en el fragmento Spring Data y semántica
transaccional cerrada. La observabilidad debe medir la operación que percibe el caller sin
contaminar core/pgJDBC/Hibernate, duplicar eventos por batch o COPY, exponer datos de negocio ni
convertirse en requisito para aplicaciones que no configuran Micrometer/Actuator.

## Decisión

### Capa y lifecycle

La instrumentación vive en `postgres-bulk-spring-data`, alrededor de cada método público del
fragmento. Se emite exactamente una observación `postgres.bulk.operation` por llamada, incluidos
empty input, validación y rechazo read-only. El interceptor Spring abre o une la transacción antes
de entrar; la observación se cierra al retornar o lanzar desde el fragmento, antes de la completion
de una transacción exterior. Por tanto, éxito bulk no promete commit exterior.

No se instrumentan filas, batches internos, stages JDBC ni COPY. Core, pgJDBC y Hibernate siguen
sin dependencias ni imports Micrometer.

### API Micrometer y métricas

`ObservationRegistry` es la abstracción primaria para lifecycle, timing, error y tracing. Cuando
existe además un `MeterRegistry`, dos counters directos publican progreso autoritativo:

- `postgres.bulk.rows` (`rows`): insert usa `BulkWriteResult.affectedRows()` y lookup el tamaño de
  la lista materializada; sólo se incrementa al éxito.
- `postgres.bulk.batches` (`batches`): sólo insert, usando `BulkWriteResult.batches()` al éxito.

El handler Micrometer derivado de Observation produce el timer `postgres.bulk.operation`. La
librería no registra histogramas/SLOs ni crea registry, exporter, tracing o endpoint.

### Tags y cardinalidad

La observación define exclusivamente tags low-cardinality propios:

- `operation=insert|lookup`
- `outcome=success|error`

Los counters sólo llevan `operation`. No aparecen entidad, clase, tabla, schema, SQL, excepción,
mensaje, fila, key ni valor de negocio. En Boot, un `MeterFilter` limitado al timer propio
normaliza el tag estándar `error` generado por el handler a `none|error`; no afecta otros meters.

### Fallos y opcionalidad

Ante fallo, se registra el throwable original en Observation y se relanza la misma instancia. Los
counters no publican filas/batches parciales. Fallos del subsistema de observabilidad se ignoran y
nunca cambian el resultado, la excepción bulk ni su lista de suppressed.

`postgres-bulk.observability.enabled=false` desactiva toda la instrumentación sin afectar
`postgres-bulk.enabled`. Sin `ObservationRegistry`, la ruta es no-op incluso si existe un
`MeterRegistry`. Actuator es una conveniencia de la aplicación y permanece únicamente test-scope
en el starter.

## Alternativas descartadas

- Instrumentar core o pgJDBC: impondría Micrometer a capas framework-neutral y multiplicaría
  eventos por detalle interno.
- Usar sólo `MeterRegistry`: perdería el lifecycle común para tracing/handlers de Observation.
- Inferir rows/batches desde eventos por elemento: aumenta overhead, cardinalidad y riesgo de
  doble conteo; el resultado del motor ya es autoritativo.
- Etiquetar entidad/tabla/excepción: el espacio de valores no está cerrado y puede filtrar datos.
- Observar la completion transaccional exterior: el fragmento no posee esa transacción y hacerlo
  cambiaría la integración/proxying.

## Evidencia

Tests unitarios cubren éxito/fallo de insert y lookup, 20 000 filas, 26 batches, empty input,
disabled, ausencia de registry, fallo de handler, throwable identity y ocho operaciones
concurrentes. Tests Boot reales cubren Actuator/Micrometer, rollback exterior, read-only, error SQL,
pool de tamaño uno y backend terminado. Auditorías de dependencias/imports/JAR confirman las
fronteras y opcionalidad descritas.

## Consecuencias

La API de operaciones permanece intacta. Spring Data incorpora Micrometer Observation/Core y
autoconfigure incorpora Core para su filtro acotado; los tres módulos inferiores no cambian. La
aplicación controla registries, handlers, exporters y sampling. Phase 13 validó esta integración
con Micrometer 1.15.0/1.15.12 y Boot 3.5.0/3.5.16 sin redefinir nombres, tags ni semántica.
