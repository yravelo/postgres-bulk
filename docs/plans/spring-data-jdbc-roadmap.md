# Roadmap de integración Spring Data JDBC

## Reglas

Este roadmap comienza después del cierre técnico del roadmap original y no lo reescribe. Cada fase
parte de `main` verde, mantiene el repository PRIVATE y no activa release, tag, Central, secrets o
security baseline. Spring Boot/Data 4, R2DBC, otros motores y aggregate graphs quedan fuera.

```text
Original roadmap 0-16: complete
Publication activation: frozen/deferred
Spring Data JDBC roadmap J0–J8: COMPLETE
Security baseline: deferred until after functional evolution
```

Las fases preservan estas invariantes:

- `postgres-bulk-core` y `postgres-bulk-pgjdbc` se reutilizan sin cambios salvo que una necesidad
  general, no específica de Spring Data JDBC, quede demostrada y aprobada por un ADR nuevo;
- no se usan internals/package-private de Spring Data;
- `ColumnMetadata.javaType()` es siempre tipo Java relacional;
- conexión caller-owned y mismo scope físico para toda la operación;
- ningún soporte pasa de `PLANNED` a `SUPPORTED` sin test PostgreSQL;
- no se crea módulo common por anticipación.

## J1 — Metadata prototype y falsificación de boundaries — DONE (2026-08-19)

- **Goal:** probar que las APIs públicas de Spring Data JDBC 3.5 producen metadata core correcta
  sin modificar core/pgJDBC.
- **Scope entregado:** módulo `postgres-bulk-spring-data-jdbc`, resolver público mínimo,
  scalar/inherited metadata, identifiers y custom write conversions.
- **Out of scope:** fragment público, ejecución COPY, lookup, Boot y publicación.
- **Architecture changes:** añade el adapter leaf en el reactor; mantiene ambos motores intactos.
- **Architecture constraints:** sólo APIs públicas Spring Data; sin imports Spring en core ni
  cambios de quoting/encoding en pgJDBC.
- **Files/modules affected:** parent reactor, nuevo módulo JDBC, ADR-024/025 y docs de arquitectura.
- **API impact real:** un resolver público para poder inyectarlo/probarlo sin publicar todavía
  operaciones o fragmentos. El baseline 0.1.0 se actualiza porque la release no está publicada.
- **Implementation tasks:** inyectar `JdbcConverter`; localizar `RelationalPersistentEntity`;
  enumerar root leaf paths; construir accessors; convertir con `writeJdbcValue`; diseñar cache;
  probar quoted/plain/schema; registrar cualquier API insuficiente.
- **Tests:** scalar/null, inherited, custom `Money -> BigDecimal`, `JdbcValue`, transient,
  insert-only, duplicate columns, unsupported type, cache/concurrency e identifier matrix.
- **Documentation:** actualizar matriz de mapping, evidencia de APIs y resolución de preguntas J0.
- **Acceptance criteria:** descriptor ordenado y estable; valor/type compatibles para null y
  non-null; ningún runtime inference/toString; el mapping objetivo funciona en 3.5.0 y 3.5.13.
- **Risks:** no existir API pública para enumeración/path null-safe; pérdida de quoting/case.
- **Dependencies:** J0, ADR-011/012/025 y Spring Data JDBC 3.5 baseline.
- **Deferred decisions:** nombre del fragmento, generated-ID production y materializador de lookup.
- **Cierre:** core/pgJDBC permanecieron intactos; las APIs públicas 3.5 bastan para mappings
  root-only, embedded, references y conversión. La pérdida de quoted/plain obliga a declarar plain
  mixed-case unsupported. `JdbcValue` directo no expone un tipo Java interno estático y se rechaza.
  Los tests PostgreSQL demuestran schema/quoted, converters, temporales, UUID, bytea, embedded e ID
  generated. ADR-025 mantiene pendientes explícitos y el reactor incluye el nuevo módulo.

## J2 — Metadata production y bulk insert root-only — DONE (2026-08-20)

- **Goal:** entregar metadata soportada y COPY de filas root con transacción JDBC real.
- **Scope:** embedded simple/nested probado, assigned/database-generated IDs, scalar FKs,
  `AggregateReference` characterization y fachada interna hacia `PostgresBulkJdbcOperations`.
- **Out of scope:** sequences, callbacks, version, children, repository fragment y Boot.
- **Architecture changes:** convierte el prototype en resolver productivo interno; define guards de
  aggregate y política de ID por llamada.
- **Architecture constraints:** una tabla/root por operación, metadata relacional y conexión
  caller-owned; no callbacks o graph persistence.
- **Files/modules affected:** adapter JDBC y sus tests; docs/ADR-025. Core/pgJDBC no cambian.
- **API impact:** ninguno todavía; sólo wiring interno del futuro fragmento.
- **Implementation tasks:** flatten embedded; traversal null-safe; reject graphs/version/sequence;
  derivar metadata assigned/generated; ejecutar dentro de `ConnectionCallback`; validar active
  read-write transaction y ownership.
- **Tests:** Testcontainers para scalar/embedded/custom converter/IDs/FKs; one-shot iterable;
  mixed IDs; no-sync de generated ID; rollback/commit; `pg_backend_pid`; conexión reusable.
- **Documentation:** contrato root-row, generated IDs, callbacks/version y support matrix.
- **Acceptance criteria:** root rows insertadas correctamente en una sola conexión; unsupported
  mappings fallan según matriz; no callback/event/auditing; conteos exactos.
- **Risks:** detección tardía de mixed IDs; default inexistente; embedded path ambiguo.
- **Dependencies:** J1 y engines Phase 6.
- **Deferred decisions:** sequences, callbacks, version synchronization y aggregate graphs.
- **Definition of Done:** insert interno end-to-end verde en PostgreSQL 15 y 18; contrato root-only
  documentado; ADR-025 puede pasar a ACCEPTED sólo si toda su evidencia se cumple.
- **Cierre:** `DefaultSpringDataJdbcBulkOperations` package-private usa un único lookahead,
  metadata per-row y `JdbcOperations.execute(ConnectionCallback)` sobre la conexión transaccional.
  PostgreSQL confirma batching 0/1/1.000/1.001/2.500, IDs assigned/generated, converters,
  embedded/reference, schema/quoted, PID físico, REQUIRED/rollback/REQUIRES_NEW, read-only,
  NESTED como characterization, fallos y pool reuse. Mixed ID se rechaza one-based durante la
  pasada; como puede descubrirse con COPY activo, la transacción obligatoria hace reversible todo
  progreso. Core y pgJDBC permanecen sin cambios. ADR-025 sigue `PROPOSED` por sus gates J7 ajenos
  a J2; ADR-026 acepta la política transaccional y de homogeneidad de J2.

## J3 — Bulk lookup y materialización — DONE (2026-08-20)

- **Goal:** demostrar lookup con tabla temporal y materialización Spring Data JDBC usando sólo APIs
  públicas dentro de la conexión exacta.
- **Scope:** `BulkKeyMetadata` simple/compuesta, `EntityRowMapper`, root-only, converters de lectura,
  duplicates/missing/nulls y cleanup.
- **Out of scope:** child relation loading, callbacks, derived-key inference, order guarantees.
- **Architecture changes:** añade el bridge del callback pgJDBC a `PreparedStatement` + row mapper.
- **Architecture constraints:** materializar antes del cleanup y sobre la conexión del callback;
  no segunda adquisición ni internals Spring Data.
- **Files/modules affected:** adapter JDBC, integration tests, ADR-024 y docs lookup.
- **API impact:** ninguno; consume `BulkKeyMetadata` y el callback públicos existentes.
- **Implementation tasks:** preparar mapper por domain type; ejecutar statement en callback;
  preservar column labels; detectar tipos que requieren relation SQL; mapear resultados antes del
  drop; mantener key metadata explícita.
- **Tests:** simple/composite keys, empty/duplicates/missing/null, schema/quoted, embedded,
  constructors/records, read converter, same PID, mapper failure, cleanup y connection reuse.
- **Documentation:** materialización, ausencia de lifecycle callbacks y límites root-only.
- **Acceptance criteria:** resultados correctos y materializados durante temp-table scope; ninguna
  segunda conexión; aggregate con children rechazado; causas preservadas.
- **Risks:** `EntityRowMapper` dispara consultas laterales o depende de aliases no emitidos.
- **Dependencies:** J2 y engine Phase 7.
- **Deferred decisions:** carga de children, custom row mappers y orden de resultados.
- **Definition of Done:** characterization concluyente; si el mapper público no sirve, fase
  detenida con ADR actualizado, sin usar internals.
- **Cierre:** el coordinador package-private reutiliza `TemporaryTableBulkLookup` mediante la
  fachada pgJDBC, conserva keys one-shot y materializa roots con el `EntityRowMapper` público
  dentro del callback y la conexión transaction-bound. PostgreSQL prueba simple/composite,
  duplicates/missing/null, 2.503 keys, converters/embedded/reference/record, schema quoted, PID,
  REQUIRED/REQUIRES_NEW/read-only/NESTED characterization, exactamente un SELECT, fallos
  `42P01`/`25P02`, cleanup, pool reuse, interoperabilidad y concurrencia. Core/pgJDBC y la API
  pública permanecían sin cambios; ADR-027 aceptó la estrategia antes de iniciar J4.

## J4 — Repository fragment y public API JDBC — DONE (2026-08-20)

- **Goal:** ofrecer insert/lookup opt-in desde repositories Spring Data JDBC sin ambigüedad JPA.
- **Scope:** interface JDBC, implementación, `spring.factories`, `RepositoryMethodContext`,
  transacciones y public API docs.
- **Out of scope:** auto-configuración Boot, starter, NESTED soportado y API común nueva.
- **Architecture changes:** congela nombre/package del fragment y añade la primera API pública JDBC.
- **Architecture constraints:** fragment externo opt-in, type distinto al JPA y métodos bulk
  compatibles con core sin compartir semántica falsa.
- **Files/modules affected:** adapter JDBC, public API baseline, user guide y ADR-024.
- **API impact:** nuevo fragment interface JDBC; ningún cambio binario/source en el fragmento JPA.
- **Implementation tasks:** elegir `JdbcPostgresBulkRepository` vs alternativa; extender
  `BulkOperations`; registrar fragment; exponer metadata; resolver domain type; cablear engines y
  observability interna; mensajes/excepciones de uso.
- **Tests:** repository discovery 3.5.0/3.5.13, insert/lookup, interface inheritance, empty input,
  exception translation, observability absent/present y API compatibility.
- **Documentation:** Javadocs, getting started manual, public API inventory y migration/no-migration.
- **Acceptance criteria:** consumer repository compila sin implementation local; JPA API no cambia;
  métodos/javadocs reflejan root-only y generated-ID no-sync.
- **Risks:** metadata ThreadLocal no expuesta; firma duplicada con fragment JPA.
- **Dependencies:** J2 y J3.
- **Deferred decisions:** API común entre stores y lifecycle opt-in permanecen fuera del roadmap.
- **Definition of Done:** public API revisada, Javadocs completos, spring.factories probado y
  ADR-024 aceptable salvo gates Boot/dual-stack explícitamente diferidos.
- **Cierre:** se publica `PostgresBulkJdbcRepository<T>` sin `ID`, con implementation
  package-private registrada por `spring.factories` y domain type obtenido mediante
  `RepositoryMethodContext`. Un contexto Spring real prueba discovery, transacciones y PostgreSQL
  para insert/lookup, converters, IDs, errores, dos repositories y concurrencia. La ambigüedad de
  `JdbcOperations` falla por DI estándar y combinar fragments JPA/JDBC se rechaza explícitamente.
  Core/pgJDBC y el fragmento JPA permanecen intactos; Boot, starter, observability y selección
  multi-manager siguen diferidos.

## J5 — Transacciones, coexistencia y robustez — DONE (2026-08-20)

- **Goal:** cerrar semántica transaccional JDBC y comportamiento con ambos Spring Data stores.
- **Scope:** REQUIRED, outer rollback, REQUIRES_NEW, read-only, rollback-only, NESTED
  characterization, multiple managers/DataSources y JPA+JDBC classpath.
- **Out of scope:** distributed/JTA transactions, automatic retries y cambio del engine ownership.
- **Architecture changes:** fija precondiciones y estrategia de selección del transaction manager.
- **Architecture constraints:** framework owns transaction/connection; postgres-bulk no crea
  boundaries, savepoints ni cambia estado JDBC.
- **Files/modules affected:** adapter tests, transactions docs y ADR-024/019 si sólo se aclara la
  diferencia; un ADR nuevo si se cambia una decisión general.
- **API impact:** ninguno esperado; una qualifier/property sólo se añade si la evidencia la exige.
- **Implementation tasks:** probar manager JDBC; validar same connection; decidir qualification
  documentada; detectar dual fragment; verificar cleanup y suppressed causes; revisar no-close/
  commit/rollback/reconfiguration.
- **Tests:** transaction matrix con PostgreSQL, savepoints NESTED, failure mid-COPY/join/mapper,
  outer catches, `UnexpectedRollbackException`, multiple DataSources y repositories de ambos
  stores en el mismo contexto.
- **Documentation:** matriz REQUIRED/REQUIRES_NEW/NESTED/read-only y dual-stack configuration.
- **Acceptance criteria:** no operación en conexión equivocada; ambiguity falla descriptivamente;
  NESTED se clasifica SUPPORTED o UNSUPPORTED sólo con evidencia; JPA baseline permanece igual.
- **Risks:** selección de manager dependiente de configuración de repository; partial progress con
  autocommit si se evade el proxy.
- **Dependencies:** J4.
- **Deferred decisions:** JTA/distributed transactions, retries y automatic transaction creation.
- **Definition of Done:** matriz transaccional publicada, causas/SQLState preservados y ADR-024
  ACCEPTED si también se cumplen gates de coexistencia.
- **Cierre:** PostgreSQL prueba REQUIRED, rollback-only/`UnexpectedRollbackException`, `25P02`,
  REQUIRES_NEW en ambos sentidos y NESTED condicionado con `JdbcTransactionManager` y
  `DataSourceTransactionManager`. La selección multi-DataSource/`JdbcOperations`/manager es
  explícita; la ambigüedad falla sin elección por orden. Un contexto real con repositories JPA y
  JDBC demuestra operación independiente y ausencia de atomicidad entre managers locales, incluso
  sobre `DataSource` compartido. Ownership, fallos por etapa, backend loss, Hikari size-one, 100
  operaciones y concurrencia pasan sin fugas. Core/pgJDBC y API binaria permanecen intactos;
  ADR-029 acepta el contrato. ADR-024 queda `PROPOSED` sólo hasta el back-off/auto-config J6.

## J6 — Spring Boot auto-configuration y starter JDBC

**Status: DONE (2026-08-20).**

- **Goal:** bootstrap JDBC idiomático, aislado y con back-off correcto.
- **Scope:** artifacts `postgres-bulk-spring-boot-autoconfigure-jdbc` y
  `postgres-bulk-spring-boot-starter-data-jdbc`, conditions, user overrides y configuración
  Boot mínima. No se añadió observability JDBC.
- **Out of scope:** unificación de starters, cambio de artifacts JPA y Boot 4.
- **Architecture changes:** añade dos leaf modules; ninguna dependencia inversa hacia Boot.
- **Architecture constraints:** conditions por stack, back-off ante ambigüedad y cero dependencia
  JPA/Hibernate transitiva en el starter JDBC.
- **Files/modules affected:** parent, dos módulos nuevos, auto-config imports, docs y API baseline.
- **API impact:** nuevos artifacts y beans/configuration properties JDBC; artifacts JPA intactos.
- **Implementation tasks:** conditions on class/beans/single candidates; resolver bean; back-off;
  starter dependencies; metadata processor if properties exist; orden con Boot JDBC configs.
- **Tests:** `ApplicationContextRunner`, `FilteredClassLoader`, JDBC-only, JPA-only, both,
  missing pgJDBC/converter/operations, user bean, one/multiple candidates, Micrometer optional y
  smoke application.
- **Documentation:** dependency snippets, conditions report, overrides y coexistencia de starters.
- **Acceptance criteria:** zero-config con starter JDBC normal; no beans JDBC en app JPA-only;
  no JPA/Hibernate dependency transitiva; override del usuario gana.
- **Risks:** orden de auto-config; candidate selection; dependency pollution.
- **Dependencies:** J5.
- **Deferred decisions:** starter unificado, renombre del starter JPA y soporte Boot 4.
- **Definition of Done:** context tests y smoke PostgreSQL verdes; module graph auditado y starter
  documentado sin alterar los existentes.
- **Cierre:** los dos artifacts están en el reactor; el starter no contiene código productivo. Las
  conditions, override, candidatos únicos/`@Primary`, JDBC-only, JPA-only y both-starters están
  probados. PostgreSQL valida discovery, insert/lookup, IDs, converters, embedded y transacciones.
  El grafo runtime excluye JPA/Hibernate/Actuator/Testcontainers/benchmarks, ADR-024 queda ACCEPTED
  y ADR-030 fija el back-off. J7 conserva matrix completa, ejemplo y documentación de adopción.

## J7 — Compatibilidad, documentación y ejemplo

**Status: DONE (2026-08-20).**

- **Goal:** convertir la baseline JDBC en una capacidad adoptable y respaldada por matrix remota.
- **Scope:** min/current BOM, Java/PostgreSQL/pgJDBC lanes, ejemplo separado, guía completa,
  compatibility evidence y API diff.
- **Out of scope:** performance claims, publication y security baseline.
- **Architecture changes:** ninguna salvo correcciones respaldadas por compatibilidad.
- **Architecture constraints:** misma línea Boot/Data 3.5 y mismos contracts en min/current; no
  conditional code por patch version sin evidencia.
- **Files/modules affected:** CI compatibility, example JDBC, docs/user guide/releases planning.
- **API impact:** sólo estabilización y baseline de la API JDBC introducida en J4.
- **Implementation tasks:** parametrizar BOM JDBC; crear consumer/example; documentar mappings,
  transactions, generated IDs, coexistence, errors y observability; actualizar public API baseline.
- **Tests:** Boot 3.5.0/3.5.16, Spring Data JDBC 3.5.0/3.5.13, Framework managed endpoints,
  Java 17/21, pgJDBC 42.7.5/42.7.13 y PostgreSQL 15–18 según policy.
- **Documentation:** example executable, mapping/transaction/error guides y compatibility evidence.
- **Acceptance criteria:** todas las combinaciones obligatorias verdes; quickstart reproducible;
  supported/unsupported matrix coincide con código/tests; enlaces y snippets validados.
- **Risks:** incompatibilidad de APIs públicas dentro de 3.5; CI excesivamente costoso.
- **Dependencies:** J6 y ADR-021.
- **Deferred decisions:** Boot/Data 4, Java posterior y nuevos PostgreSQL quedan para otro roadmap.
- **Definition of Done:** evidencia remota archivada, docs completas y ningún claim sin test.
- **Cierre:** la policy boundary/pairwise cubre Java 17/21, Boot y Data JDBC/Relational
  3.5.0/3.5.13, Framework 6.2.7/6.2.19, pgJDBC 42.7.5/42.7.13 y PostgreSQL
  15.18/16.14/17.10/18.4. La suite conserva regresión JPA, coexistencia y aislamiento del starter.
  `examples/spring-boot-data-jdbc` demuestra adopción con una sola dependencia directa, Docker
  Compose y Testcontainers. README, user guide, mapping/transacciones, ADR-021/030 y evidencia
  reproducible quedan alineados. Build `32351155913` y Compatibility `32351155919` pasaron sobre
  el primer HEAD remoto de cierre. J8 conserva benchmarks comparativos y cierre técnico.

## J8 — Benchmarks y cierre de línea JDBC — DONE (2026-08-20)

- **Goal:** medir el beneficio y cerrar readiness técnico de Spring Data JDBC sin publicar.
- **Scope:** benchmark insert/lookup root-only, regression gates razonables, revisión ADR/risk/API y
  acceptance criteria de la nueva línea.
- **Out of scope:** comparar aggregate graphs no equivalentes, tag/release/Central y optimización
  que rompa correctness.
- **Architecture changes:** ninguna; cualquier cambio de boundary vuelve a la fase correspondiente.
- **Architecture constraints:** correctness y equivalencia semántica antes de throughput; no
  modificar APIs/motores para mejorar una cifra.
- **Files/modules affected:** benchmarks, reports, roadmap/ADRs/release-readiness futuro.
- **API impact:** ninguno; benchmarks no pueden ampliar contrato.
- **Implementation tasks:** añadir fixtures JDBC; ejecutar metodología existente; comparar
  `saveAll`, JDBC batch y COPY; medir lookup/materialization; documentar hardware/config/raw data.
- **Tests:** correctness antes de cada run; repeatability, warmups/forks, 1k–1M rows, custom
  conversion/embedded, simple/composite lookup y observability on/off.
- **Documentation:** metodología, raw data, entorno, interpretación y límites de comparabilidad.
- **Acceptance criteria:** datos reproducibles y sin marketing engañoso; no regression crítica;
  todos los ADRs resueltos; risks/open questions cerrados o diferidos explícitamente.
- **Risks:** benchmark semánticamente desigual; ruido del host; optimización prematura.
- **Dependencies:** J7 y ADR-022.
- **Deferred decisions:** optimizaciones, nuevos mappings y activación de publicación requieren
  autorización/roadmap separado.
- **Definition of Done:** baseline/public reports versionados, nueva línea técnicamente cerrada y
  publicación todavía congelada hasta autorización separada.
- **Cierre:** se reutilizó JMH/Phase 14 y se midieron `CrudRepository.saveAll`, batch JDBC, la API
  pública JDBC y COPY low-level entre 10 y 100K, más 1M razonable, batch sizes, allocations y
  lookup simple/compuesto. Dos baselines y un large profile conservaron raw JSON/CSV separado. En
  esta máquina COPY público redujo el point estimate frente a `saveAll`, sin overhead consistente
  frente al engine; SQL `IN` ganó hasta 10K. Se mantiene default 1.000, no se introduce estrategia
  adaptativa y no apareció bug productivo. ADR-024..030 quedan ACCEPTED, API/core/pgJDBC intactos,
  benchmark aislado y J0–J8 técnicamente cerrado sin publicación.

## Estado posterior al cierre

```text
Spring Data JDBC roadmap J0–J8: COMPLETE
```

Quedan registrados, sin implementar: runtime multi-schema/schema-per-tenant; Security & Supply
Chain Baseline; publication activation; Boot 4 / Spring Data 4 future generation; additional
performance experiments. Cualquier continuación requiere un roadmap y autorización separados.

## Dependencias del roadmap

```text
J0 investigation
    -> J1 metadata prototype
        -> J2 root-only insert
            -> J3 lookup/materialization
                -> J4 repository API
                    -> J5 transactions/coexistence
                        -> J6 Boot/starter
                            -> J7 compatibility/docs/example
                                -> J8 benchmarks/closure
```

J3 puede investigar `EntityRowMapper` en paralelo conceptual con J2, pero no se acepta hasta usar
la metadata real. J6 no empieza antes de cerrar dual-stack/transaction selection en J5. J8 nunca
se usa para redefinir correctness.
