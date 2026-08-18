# Plan de implementación incremental

## Reglas del plan

Cada fase entra por PR separada, parte de main verde, termina con reactor compilable y no adelanta APIs de fases posteriores. Los ADRs `PROPOSED` sólo pasan a `ACCEPTED` con la evidencia indicada. No se corrige el legacy: se conserva como referencia. “Tests” significa automatizados salvo revisión documental explícita.

## Phase 0 — Legacy characterization

**Estado:** completada en documentación; no existe binario legacy ejecutable.

- **Goal:** hacer visible el comportamiento que se preserva y los fallos que no deben migrarse.
- **Scope:** inventario de API, flujo insert/lookup, metadata, transacciones, serialización, dependencias y riesgos.
- **Out of scope:** compilar, completar excepciones faltantes o modificar el legacy.
- **Architecture changes:** establece el legacy fuera del reactor y los primeros ADRs.
- **Files/modules affected:** `docs/legacy`, `docs/architecture`, `docs/decisions`; ningún módulo productivo.
- **Implementation tasks:** mantener hashes del snapshot; revisar comportamiento con owners; convertir expectativas no observables en hipótesis.
- **Tests:** revisión estática; en fases 4/7, reproducir defectos críticos contra PostgreSQL.
- **Acceptance criteria:** API/capacidades/limitaciones/riesgos trazados; funciones a preservar enumeradas; huecos del snapshot declarados.
- **Risks:** confundir intención de Javadoc con comportamiento real.
- **Dependencies:** snapshot legacy proporcionado.
- **Definition of Done:** `current-behavior.md` y `risk-register.md` revisables y enlazados.

## Phase 1 — Project foundation

**Estado:** completada el 2026-08-18.

- **Goal:** disponer de un reactor reproducible y publicable sin lógica de negocio.
- **Scope:** parent/modules, Maven Wrapper generado oficialmente, toolchains/compile, JUnit, Surefire/Failsafe, formato mínimo, CI de build y estructura de publicación.
- **Out of scope:** APIs bulk, Testcontainers, release a Central.
- **Architecture changes:** codifica el DAG Maven aceptado; añade checks de ciclos/imports básicos.
- **Files/modules affected:** parent y seis POMs, `.mvn`, `.github/workflows`, root docs.
- **Implementation tasks:** decidir groupId/licencia; fijar wrapper; dependency/plugin management; reproducible builds; Enforcer para Java/Maven/dependency convergence; separar unit/integration profiles.
- **Tests:** `mvn verify` en Java 17/21; smoke de módulos vacíos; validación de POM.
- **Acceptance criteria:** checkout limpio construye con `./mvnw verify`; CI no requiere Maven global; starter sin código; ninguna dependencia corporativa.
- **Risks:** exceso de plugins y tiempos de CI.
- **Dependencies:** ADR-001/002 y decisión de coordenadas/licencia.
- **Definition of Done:** build verde en Linux, comandos documentados, versiones centralizadas y sólo tooling justificado.

### Registro de cierre de Phase 1

- [x] Parent y seis módulos construyen como un único reactor acíclico.
- [x] Maven Wrapper oficial 3.3.4 `only-script` fija Maven 3.9.16 y valida SHA-256.
- [x] Java 17/UTF-8 son explícitos; Enforcer exige Java 17+ y Maven 3.6.3–3.x.
- [x] Dependency/plugin management, plugins de lifecycle y output timestamp están centralizados.
- [x] JUnit BOM, Surefire `*Test` y Failsafe `*IT` quedan preparados sin tests placeholder.
- [x] Spotless verifica formato Java; Enforcer verifica convergence y la frontera de dependencias de core.
- [x] Git hygiene, Apache-2.0 y CI Java 17/21 con cache Maven están presentes.
- [x] README, compatibilidad, estrategia de calidad y ADRs reflejan las decisiones reales.
- [x] `./mvnw --version`, `./mvnw validate` y `./mvnw clean verify` terminan correctamente.
- [x] No existen fuentes Java ni se ha comenzado Phase 2.

**Aprendizajes:** Toolchains se difiere porque `release=17` satisface el objetivo sin exigir otro JDK local. Testcontainers/ArchUnit se añadirán sólo en el primer módulo que los consuma. Surefire/Failsafe se fijaron en 3.5.4 porque Failsafe 3.6.0 aparecía en documentación adelantada pero no estaba publicado en Maven Central durante la validación. `io.github.postgresbulk` sigue provisional hasta verificar ownership; Apache-2.0 sí queda adoptada.

## Phase 2 — Core domain and API

**Estado:** completada el 2026-08-18.

- **Goal:** definir el contrato mínimo independiente de framework para insert y lookup.
- **Scope:** fachada/operaciones, options validadas, resultados, excepciones pequeñas, política de empty input y batches.
- **Out of scope:** JDBC, CSV, metadata Hibernate, Spring, streaming y observabilidad.
- **Architecture changes:** materializa sólo puertos con consumidor inmediato; documenta qué es API pública.
- **Files/modules affected:** `postgres-bulk-core` y ADR/API docs.
- **Implementation tasks:** prototipos de uso compilables; decidir `Iterable`/`Collection`; `BulkWriteResult`; jerarquía raíz + configuration/metadata/mapping/execution; política de causas.
- **Tests:** invariantes de options, empty input, batch 1/>input/final incompleto, conteos y exception causes; API compatibility baseline.
- **Acceptance criteria:** ejemplos de insert y claves simple/compuesta son type-safe; no hay imports prohibidos; no hay interfaces sin uso.
- **Risks:** congelar lookup antes de entender metadata; resultados con métricas innecesarias.
- **Dependencies:** ADR-002 y feedback de API.
- **Definition of Done:** Javadocs de tipos públicos, tests unitarios y ADRs actualizados; reactor verde.

### Registro de cierre de Phase 2

- [x] `BulkOperations<T>` define insert operation-centric para `Iterable<? extends T>` y liga una instancia al tipo lógico.
- [x] Los overloads con defaults/options, nulls, elementos null y empty input tienen contrato público explícito.
- [x] `BulkInsertOptions` es inmutable, valida `batchSize > 0` al construirse y no contiene opciones tecnológicas.
- [x] `BulkWriteResult` contiene `affectedRows`/`batches`, valida sus conteos y omite duración, IDs y lifecycle ORM.
- [x] `BulkException` establece una raíz unchecked y conserva causas; no se crean subtipos sin fallos concretos.
- [x] La superficie pública queda inventariada en cuatro tipos con Javadocs y sin dependencias runtime.
- [x] ADR-009 acepta la forma de API; ADR-010 acepta diferir lookup; ADR-006 y ADR-008 siguen PROPOSED.
- [x] Tests Java puro cubren defaults, invariantes, value semantics, null del overload, delegación genérica y causas.
- [x] Spotless, unit tests y reactor completo terminan correctamente.
- [x] No se han creado metadata, codecs, executors ni código de Phase 3.

**Decisiones diferidas:** firma y resultado de lookup; representación neutral de metadata/key; subtipos de excepción; semántica transaccional entre batches; streaming; generated IDs; tipos de encoding y puertos de ejecución.

**Aprendizajes:** `Iterable` permite la ergonomía de `Collection` y consumo de una pasada sin adoptar el lifecycle de `Stream`. Una clase final con factories deja evolucionar options mejor que el constructor canónico de un record. Los tests de particionado real (batch 1, mayor que input y batch final incompleto) pertenecen a Phase 6, donde existirá una implementación de batching; Phase 2 fija y prueba únicamente los value objects y el contrato observable disponible.

**Deuda explícita:** una interfaz no puede imponer por sí sola la validación del overload implementado; cada implementación debe cumplir sus Javadocs y Phase 6 añadirá contract tests reutilizables para empty input, null elements, one-shot iterables y conteos por batch.

## Phase 3 — Metadata abstraction

- **Goal:** representar mapping físico suficiente sin tipos JPA/Hibernate.
- **Scope:** entidad/tabla/columnas/keys/extractores, nombres qualified/quoted, orden y validación/cache contract.
- **Out of scope:** resolver Hibernate y consultar catálogo PostgreSQL.
- **Architecture changes:** crea descriptor core y puerto resolver sólo si operaciones Phase 2 lo consumen.
- **Files/modules affected:** core `metadata`/`mapping`, tests fixtures.
- **Implementation tasks:** modelar identifier por partes; columnas insertables; componentes key; tipo Java y relacional; acceso a valor sin reflection obligatoria; errores deterministas.
- **Tests:** metadata válida/inválida, composite ordering, quoted/schema, duplicate columns, extractor failure, concurrency del cache.
- **Acceptance criteria:** fixtures cubren simple/inherited/embedded conceptualmente sin dependencia JPA; el modelo no asume snake_case ni tipo PostgreSQL por nombre Java.
- **Risks:** duplicar el metamodelo ORM; exponer detalles Hibernate accidentalmente.
- **Dependencies:** contratos Phase 2; ADR-004 sigue propuesta.
- **Definition of Done:** modelo justificado por flows/tests y documentación de invariantes.

## Phase 4 — COPY encoding

- **Goal:** producir registros CSV correctos y extensibles para COPY.
- **Scope:** encoder registry, built-ins acordados, writer CSV UTF-8, NULL/empty y límites de buffer.
- **Out of scope:** conexión o ejecución COPY, TEXT/BINARY, JSON/arrays incluidos por defecto.
- **Architecture changes:** encoding escalar agnóstico en core sólo si es realmente reusable; framing COPY en pgjdbc.
- **Files/modules affected:** core `codec` si procede, pgjdbc `copy`, ADR-003.
- **Implementation tasks:** resolver encoder determinísticamente; aplicar tipo relacional/converter contract; escribir a `Writer`/sink sin línea completa obligatoria; error por tipo desconocido.
- **Tests:** matriz NULL, empty, comma, quotes, CR/LF/CRLF, unicode/emoji, temporal/UUID/decimal/boolean/enum/bytes; round-trip PostgreSQL pequeño.
- **Acceptance criteria:** round-trip distingue todos los casos; sin fallback `toString`; mensajes no incluyen valores sensibles.
- **Risks:** formatos temporales y `bytea`; allocations ocultas.
- **Dependencies:** Phase 3 y PostgreSQL Testcontainers inicial.
- **Definition of Done:** ADR-003 aceptable con evidencia y suite rápida/integradora separada.

## Phase 5 — pgJDBC COPY executor

- **Goal:** encapsular por completo el protocolo COPY y su lifecycle.
- **Scope:** connection scope port, unwrap validado, SQL builder/quoting, executor COPY CSV, cancel/close/error row count.
- **Out of scope:** insert de entidades, temp tables, Spring transaction integration.
- **Architecture changes:** pgjdbc implementa el port de ejecución sin subir clases `org.postgresql` a API.
- **Files/modules affected:** pgjdbc `copy`, `connection`, `sql`.
- **Implementation tasks:** componer SQL desde identifiers; acquire/use/release ownership explícito; buffer configurable con límites; preservar `SQLException`; mapear errores.
- **Tests:** fake unitario de builder/ownership y Testcontainers para 1/múltiples filas, quoted/schema, invalid COPY, cancel y conexión reusable.
- **Acceptance criteria:** búsqueda de bytecode/API confirma confinamiento pgJDBC; affected rows viene del servidor; recursos cierran en éxito/fallo.
- **Risks:** cerrar conexión prestada o enmascarar error de COPY durante close.
- **Dependencies:** Phase 4.
- **Definition of Done:** executor usable con metadata fixture, sin Spring/Hibernate.

## Phase 6 — Bulk insert

- **Goal:** entregar insert end-to-end programático sobre core + pgjdbc.
- **Scope:** validación, batching iterativo, un connection scope, progreso interno, resultado agregado y semántica de fallo.
- **Out of scope:** Hibernate/Spring repositories, callbacks JPA, streaming API y generated IDs.
- **Architecture changes:** decidir si una clase service separada aporta cohesión; si no, implementación directa de fachada.
- **Files/modules affected:** core/pgjdbc y integration tests.
- **Implementation tasks:** evitar `subList`; iterator batches acotados; preparar metadata/SQL una vez; documentar persistence-context caveats y atomicidad.
- **Tests:** empty, 1, multi, 10k, tamaños 1/>input/final, iterable one-shot, batch N falla, transaction commit/rollback y datos especiales.
- **Acceptance criteria:** conteos/batches exactos; sin materializar input completo; rollback integral dentro de transacción JDBC suministrada.
- **Risks:** partial commit fuera de tx; entidades con IDs/defaults/callbacks.
- **Dependencies:** Phase 2–5.
- **Definition of Done:** operación pública estable provisional y guía de semántica.

## Phase 7 — Temporary-table bulk lookup

- **Goal:** lookup escalable por clave simple/compuesta sobre una conexión.
- **Scope:** strategy interface justificada, temp relation, COPY keys, JOIN, cleanup, duplicate/null/order policy.
- **Out of scope:** VALUES/UNNEST y selección adaptativa.
- **Architecture changes:** materializa estrategia inicial; decide CTAS vs LIKE con spike.
- **Files/modules affected:** pgjdbc `temporarytable`/`sql`, core lookup contracts, ADR-006.
- **Implementation tasks:** nombres acotados; qualified/quoted SQL; misma conexión para todas las etapas; mapper de filas como port; cleanup en éxito/fallo.
- **Tests:** simple/composite, 1/10k, duplicates/null, domains/custom schema/quoted, PG 15–18, autocommit/transactions, COPY/JOIN failure, pool reuse.
- **Acceptance criteria:** no heurística Java→SQL; ninguna temporal filtrada; política de orden/null documentada; explain plan razonable registrado, no asserted.
- **Risks:** NOT NULL al usar LIKE, locks/permisos, resultado duplicado y lifecycle `ON COMMIT`.
- **Dependencies:** Phase 3–5; no requiere Hibernate con fixtures.
- **Definition of Done:** ADR-006 aceptado o fase detenida con evidencia reproducible.

## Phase 8 — Hibernate metadata adapter

- **Goal:** resolver entidades reales al descriptor core.
- **Scope:** Hibernate 6.6 baseline, metadata física, accessors, IDs/embeddables/associations/converters y caching seguro.
- **Out of scope:** Hibernate 7, repositorios Spring Data y soportar todo custom user type sin extensión.
- **Architecture changes:** única frontera que conoce internals Hibernate.
- **Files/modules affected:** `postgres-bulk-hibernate`, ADR-004, fixtures JPA.
- **Implementation tasks:** resolver naming/schema/quotes/selectables; tipo relacional tras converter; definir override programático; diagnóstico actionable.
- **Tests:** todos los mappings pedidos, dos naming strategies, multiple EMFs, proxy class, invalid metadata; mínimo/último Hibernate 6.6.
- **Acceptance criteria:** cero reflection basada sólo en declared fields; ningún internal Hibernate en API pública; cache aislado por persistence unit.
- **Risks:** APIs internas cambian por patch; associations multi-column.
- **Dependencies:** Phase 3 y matriz de compatibilidad.
- **Definition of Done:** ADR-004 aceptado y insert/lookup funcionan con entidades fixture.

## Phase 9 — Spring Data integration

- **Goal:** ofrecer DX repository y fachada programática participando en transacciones Spring.
- **Scope:** fragment, domain metadata, connection accessor Spring, exceptions translation policy y múltiples persistence units.
- **Out of scope:** Boot auto-config y custom global base repository salvo spike concluyente.
- **Architecture changes:** confirma fragments o registra la alternativa en ADR-005.
- **Files/modules affected:** `postgres-bulk-spring-data`, ADR-005, integration app test.
- **Implementation tasks:** wiring del mismo engine; `JpaRepository + PostgresBulkRepository`; determinar flush/clear policy (default no implícito); conexión vinculada.
- **Tests:** dos repositorios/entidades, multiple EMFs/DataSources, `@Transactional`, rollback, readOnly, REQUIRES_NEW, nested behavior documentado y no-tx.
- **Acceptance criteria:** no dependencia corporativa; opt-in por repository; todas las sentencias de lookup comparten conexión; JPA context semantics documentadas.
- **Risks:** metadata de invocación costosa y fragment registration entre patches.
- **Dependencies:** Phase 6–8.
- **Definition of Done:** snippet objetivo compila y pasa Testcontainers con Spring sin Boot.

## Phase 10 — Spring Boot auto-configuration

- **Goal:** starter usable con defaults y back-off predecible.
- **Scope:** conditions, properties mínimas, configuration metadata, starter POM y context tests.
- **Out of scope:** observabilidad y tuning automático.
- **Architecture changes:** composición sólo; no lógica bulk.
- **Files/modules affected:** autoconfigure, starter, docs.
- **Implementation tasks:** decidir propiedades tras medir necesidad; defaults batch/buffer/temp prefix; `enabled`; bean override; failure analysis si falta PostgreSQL/Hibernate.
- **Tests:** ApplicationContextRunner: happy path, disabled, missing class/bean, custom beans, property validation, starter sin código.
- **Acceptance criteria:** añadir dependencia + fragment basta; no activa con DB/driver incompatible; back-off documentado.
- **Risks:** demasiadas properties y auto-config agresiva.
- **Dependencies:** Phase 9 y Boot 3.5 baseline.
- **Definition of Done:** app de test arranca sin configuración extra y metadata IDE generada.

## Phase 11 — Transactions and robustness

- **Goal:** cerrar explícitamente ownership, atomicidad y recuperación.
- **Scope:** commit/rollback/readOnly/autocommit/REQUIRES_NEW/nested, cancel COPY, partial batches, temp cleanup y connection pool reuse.
- **Out of scope:** distributed transaction guarantees no soportadas.
- **Architecture changes:** sólo ajustes respaldados por fault-injection; no nueva capa genérica.
- **Files/modules affected:** pgjdbc, spring-data, docs/contracts.
- **Implementation tasks:** matriz de estados; política fuera de tx; no mutar conexión prestada; exception suppression; timeouts/cancellation si disponibles.
- **Tests:** fault injection en cada etapa y matriz transaccional real; verificar visibilidad final y estado de conexión devuelta.
- **Acceptance criteria:** ninguna fuga; causas SQL preservadas; semantics por modo publicadas; no commit/rollback ilegal de recurso Spring.
- **Risks:** diferencias entre transaction managers y savepoints.
- **Dependencies:** Phase 7/9.
- **Definition of Done:** risk L-02/L-03/L-08/L-09/L-16 cerrados con tests.

## Phase 12 — Observability

- **Goal:** observabilidad opcional sin contaminar core ni datos.
- **Scope:** frontera de eventos/observer, Micrometer Observation en auto-config, métricas/logs acordados.
- **Out of scope:** exporters/dashboards obligatorios y métricas por fila.
- **Architecture changes:** hook alrededor de operación/batch; implementación Micrometer externa.
- **Files/modules affected:** core hook si necesario, autoconfigure, docs.
- **Implementation tasks:** nombres/tags; timers/counters; errores; desactivar; sanitizar entity/table; medir overhead.
- **Tests:** meter registry, success/error/batches, disabled/no Micrometer, ausencia de tags high-cardinality/datos.
- **Acceptance criteria:** métricas estables y documentadas; no dependencias Micrometer transitivas obligatorias; overhead medido.
- **Risks:** doble conteo/retries y cardinalidad por exception/entity.
- **Dependencies:** fronteras estables Phase 6/7/10.
- **Definition of Done:** tests contractuales y guía de logging/metrics.

## Phase 13 — Compatibility tests

- **Goal:** transformar la matriz propuesta en soporte verificable.
- **Scope:** Java, Boot/Spring Data/Hibernate patches, PostgreSQL 15–18, pgJDBC; spike Boot 4.1/Hibernate 7.
- **Out of scope:** todas las combinaciones cartesianas en cada PR.
- **Architecture changes:** posible adapter separado sólo mediante nuevo ADR.
- **Files/modules affected:** profiles/BOM test modules/CI, compatibility doc.
- **Implementation tasks:** matriz tiered (PR/nightly/release); mínimos/máximos; registrar incompatibilidades; decidir Boot 4 roadmap.
- **Tests:** suite funcional común por eje y smoke combinatorio; no mocks de PostgreSQL.
- **Acceptance criteria:** cada claim de soporte corresponde a job verde; versiones EOL excluidas con rationale.
- **Risks:** duración/flakiness y Docker availability.
- **Dependencies:** funcionalidades completas y release matrix vigente.
- **Definition of Done:** compatibilidad pasa de PROPOSED a política publicada.

## Phase 14 — Benchmarks

- **Goal:** comparar rendimiento de forma reproducible sin assertions frágiles.
- **Scope:** JPA `saveAll`, JDBC batch y COPY para 1K/10K/100K/1M; CPU/memoria/throughput y dataset definido.
- **Out of scope:** gating del build normal o promesas universales.
- **Architecture changes:** ninguna; benchmark fuera de módulos de producción.
- **Files/modules affected:** módulo/perfil benchmark dedicado y docs.
- **Implementation tasks:** JMH o harness justificado; warmup; DB config/hardware; reset dataset; ejecutar manual/CI dedicada.
- **Tests:** smoke del harness; resultados como artefacto, no assertions.
- **Acceptance criteria:** comando único reproduce; versions/config/seeds publicados; resultados incluyen incertidumbre.
- **Risks:** medir setup/red/containers y comparar semánticas no equivalentes.
- **Dependencies:** Phase 6, 11 y compatibilidad estable.
- **Definition of Done:** informe reproducible, benchmarks fuera de `verify` normal.

## Phase 15 — Examples and documentation

- **Goal:** permitir adopción sin leer internals.
- **Scope:** quickstart, app ejemplo, insert/lookup, keys compuestas, transactions, tuning, limitations y troubleshooting.
- **Out of scope:** catálogo de recetas no probadas.
- **Architecture changes:** ejemplos actúan como consumer tests y pueden descubrir fricción, no crear API paralela.
- **Files/modules affected:** `examples`, README/Javadocs/site.
- **Implementation tasks:** Maven consumer; schema migrations; commands; explicar JPA callbacks/IDs/context; property reference.
- **Tests:** ejemplo compila/arranca y ejecuta en Testcontainers en CI.
- **Acceptance criteria:** dependencia + repository fragment + llamada funcionan copiando el quickstart; todos los snippets compilados.
- **Risks:** docs divergentes de releases.
- **Dependencies:** Phase 10–13.
- **Definition of Done:** docs versionadas y enlaces comprobados.

## Phase 16 — Release readiness

- **Goal:** producir artefactos firmables y gobernables para publicación independiente.
- **Scope:** licencia, notices, SCM/developer metadata, sources/Javadocs, signing, Central, semantic versioning, changelog, security/support policy y release automation.
- **Out of scope:** publicar sin aprobación explícita y garantías comerciales.
- **Architecture changes:** congelación/revisión de API `1.0`; remover paquetes accidentales.
- **Files/modules affected:** parent POM, root governance docs, CI release.
- **Implementation tasks:** reproducible build; API diff; SBOM/dependency scan justificados; staging dry-run; provenance; release checklist.
- **Tests:** clean-room consumer, `mvn verify`, package/source/Javadoc validation y staging local/remoto autorizado.
- **Acceptance criteria:** ningún SNAPSHOT/dependencia corporativa; coordenadas/licencia definitivas; docs/matriz/changelog completos; artefactos verificables.
- **Risks:** secretos/signing y API prematuramente estable.
- **Dependencies:** todas las fases y aprobación de publicación.
- **Definition of Done:** release candidate aprobada; publicación sigue siendo una acción separada y explícita.

## Gates transversales

- Todo cambio arquitectónico relevante actualiza o añade ADR.
- Cada fase prueba errores además del happy path y preserva causa raíz.
- Nunca se registran entidades o claves completas.
- Integration/performance tests no vuelven lento el loop unitario; perfiles y CI dejan visible qué se ejecutó.
- Ninguna fase se considera completa sólo porque compila: debe cumplir sus acceptance criteria y Definition of Done.
