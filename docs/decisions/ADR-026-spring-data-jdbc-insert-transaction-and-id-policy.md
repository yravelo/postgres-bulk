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
- REQUIRED y REQUIRES_NEW siguen las fronteras del transaction manager. Desde J5, NESTED está
  soportado sólo con `JdbcTransactionManager`/`DataSourceTransactionManager` sobre el mismo
  `DataSource`; el manager crea, revierte y libera savepoints.

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

## Relación con J3

J3 reutiliza los mismos guards de transacción lógica/física y el mismo
`JdbcOperations.execute(ConnectionCallback)` para lookup. No cambia la decisión de IDs/insert ni
amplía NESTED; la estrategia de materialización y cleanup se acepta separadamente en ADR-027.

## Evidencia J4

El proxy repository crea una transacción `REQUIRED` para llamadas directas y conserva outer
rollback, `REQUIRES_NEW` y rechazo read-only al atravesar el fragmento externo. PostgreSQL prueba
IDs generated/assigned, mixed rejection, batching explícito y SQLState desde esa API. El fragmento
no crea boundaries ni modifica la política single-pass/homogénea aceptada aquí.

## Evidencia J5

Ambos managers JDBC completan insert NESTED y revierten un COPY fallido al savepoint, tras lo cual
el outer sigue usable y confirma. El audit prohíbe todas las operaciones de ownership, incluidas
las tres de savepoint. REQUIRED capturado termina rollback-only y `25P02`; REQUIRES_NEW usa PID
independiente en ambas direcciones.

## Alternativas rechazadas

| Alternativa | Motivo |
| --- | --- |
| Materializar/pre-escanear todas las filas | Rompe one-shot y memoria O(1) |
| Escoger columnas sólo por la primera fila | Puede codificar silenciosamente una política de ID incorrecta |
| Separar automáticamente mixed IDs en dos COPY | Cambia encounter order, errores y semántica de una llamada |
| Permitir autocommit | Puede confirmar batches previos antes de descubrir mismatch/fallo |
| Crear/commit/rollback transacciones en el adapter | Viola ownership y composición Spring |
| Usar `DataSource.getConnection()` | Puede escapar de la conexión transaction-bound |

## Revisión J8 (2026-08-20)

Los cuatro contenders usaron UUID assigned y commit dentro del límite medido; los checks de
correctness quedaron fuera. La API pública conservó la conexión transaccional y la política por
llamada. No hubo bug ni cambio productivo; el ADR permanece `ACCEPTED`.

## Evidencia MS5 (2026-08-24)

Insert target-aware conserva lookahead one-shot, O(1), batches, ID assigned/generated y rechazo
mixed. A+B commit/rollback, `REQUIRES_NEW`, NESTED condicionado, read-only y `25P02` pasan sin que
el target altere ownership o política de IDs. El ADR permanece `ACCEPTED`.
