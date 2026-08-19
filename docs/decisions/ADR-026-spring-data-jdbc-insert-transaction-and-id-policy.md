# ADR-026: Transacción y homogeneidad de bulk insert Spring Data JDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

COPY fija tabla y lista de columnas al comenzar. Spring Data JDBC, en cambio, clasifica el ID por
instancia: un ID assigned se copia y un ID database-generated se omite. Comprobar todo el input
antes de abrir COPY evitaría progreso parcial, pero rompería el contrato one-shot/O(1). El adapter
también debe participar en la conexión física del transaction manager sin apropiársela.

## Decisión

- El bulk insert de alto nivel JDBC requiere una transacción Spring activa, write y una
  `Connection` con `autoCommit=false`; el contrato low-level pgJDBC permanece caller-owned y no se
  restringe.
- El adapter obtiene la conexión exclusivamente mediante
  `JdbcOperations.execute(ConnectionCallback)` y no llama close, commit, rollback, `setAutoCommit`,
  `setReadOnly`, `setTransactionIsolation`, `setSchema` ni crea savepoints.
- Input vacío se detecta con un único iterator y devuelve `BulkWriteResult.empty()` antes de
  transacción, metadata, conexión o SQL.
- Para input no vacío se toma una fila de lookahead, se resuelve y prepara el engine una vez. Un
  wrapper one-shot vuelve a entregar la primera fila y resuelve metadata para cada fila restante.
- La identidad de metadata cacheada debe coincidir con la primera fila. Una diferencia se rechaza
  como `InvalidDataAccessApiUsageException` con posición one-based, tipo y razón, sin valores.
- Generated y assigned IDs no pueden mezclarse. Generated omite ID y no sincroniza la instancia;
  assigned incluye el ID.
- La detección puede ocurrir mientras COPY está activo. No se materializa ni se hace pre-scan: la
  transacción obligatoria y rollback del owner son la garantía de atomicidad.
- REQUIRED y REQUIRES_NEW siguen las fronteras del transaction manager. NESTED no es una promesa
  J2; sólo se caracteriza y postgres-bulk no crea savepoints.

## Consecuencias

El algoritmo conserva tiempo O(N), memoria adicional O(1), un solo iterator y los mecanismos de
batching/cancelación/conteo del engine existente. Un fallo tardío puede haber enviado filas al
backend, pero ninguna queda confirmada si el owner completa rollback. Capturar el fallo dentro de
la transacción obliga al caller a marcar rollback-only; el adapter no intenta recuperar una
transacción PostgreSQL abortada.

No existe modo autocommit en la integración de alto nivel. Aplicaciones que necesitan ownership
manual siguen usando `PostgresBulkJdbcOperations` directamente.

## Evidencia

- Unit tests prueban empty optimization, lookahead, un iterator, posiciones, ambas direcciones de
  mixed ID, resultado/options, fallos `hasNext`/`next` e invariantes de ownership.
- PostgreSQL prueba batches 1/1.000/1.001/2.500, rollback de mixed/null/producer/converter/SQL,
  SQLState `23505`/`25P02`, PID físico, read-only, REQUIRED, REQUIRES_NEW y pool reuse.
- Generated ID se genera en base sin modificar la entidad; assigned Long/UUID se persisten.

## Alternativas rechazadas

| Alternativa | Motivo |
| --- | --- |
| Materializar/pre-escanear todas las filas | Rompe one-shot y memoria O(1) |
| Escoger columnas sólo por la primera fila | Puede codificar silenciosamente una política de ID incorrecta |
| Separar automáticamente mixed IDs en dos COPY | Cambia encounter order, errores y semántica de una llamada |
| Permitir autocommit | Puede confirmar batches previos antes de descubrir mismatch/fallo |
| Crear/commit/rollback transacciones en el adapter | Viola ownership y composición Spring |
| Usar `DataSource.getConnection()` | Puede escapar de la conexión transaction-bound |
