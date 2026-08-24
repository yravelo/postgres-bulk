# Roadmap multi-schema / schema-per-tenant

## Reglas de la línea

Esta línea parte de J0–J8 y del roadmap original cerrados. Cada fase comienza desde `main` verde,
mantiene compatibilidad legacy sin target y no adelanta la fase siguiente. La aplicación resuelve
tenant/autorización/routing; PostgreSQL Bulk recibe un `TableName` físico. Ninguna fase puede
introducir `TenantContext`, tenant ids, ThreadLocal, `search_path`, `Connection.setSchema`, schema
global Boot, cache por tenant, provisioning, publicación o security baseline sin autorización y
ADR separados.

ADR-031 está `ACCEPTED` con evidencia Java pura de MS1 y COPY/lookup/JPA/JDBC reales de MS2–MS5;
ADR-032 está `ACCEPTED` con evidencia pgJDBC/JPA/JDBC MS2–MS5. "Multi-schema" en este roadmap
significa target `schema + table` explícito por operación dentro de una conexión ya elegida; no
significa database routing, row-level tenancy ni Hibernate multi-tenancy.

## MS0 — Multi-Schema Investigation and Architecture — DONE (2026-08-20)

- **Objective:** determinar si metadata estructural y destino físico runtime pueden separarse sin
  contaminar core ni romper los contratos existentes.
- **Scope:** audit de `TableName`, resolvers/caches, SQL preparado, insert/lookup, JPA/JDBC/Boot,
  fuentes oficiales, alternativas, seguridad, transacciones, API y planificación.
- **Non-goals:** código Java, POMs, runtime multi-schema, tests productivos, properties, secrets,
  release/publicación y security baseline.
- **Modules/files:** sólo documentación de arquitectura, roadmap, ADRs e índices.
- **API impact:** cero; propuestas no implementadas.
- **Architecture constraints:** tenant agnostic; target completo por operación; qualified SQL;
  conexión caller-owned; no cache target-keyed.
- **Tasks:** leer código/documentos; localizar acoplamiento target/estructura; comparar A–F;
  decidir schema-only vs full target, cache, conflictos, search path y límites.
- **Tests/validation:** documentación, links, diff-check y gates documentales; ninguna prueba
  pretende demostrar runtime inexistente.
- **Documentation:** `multi-schema-investigation.md`, este roadmap y ADR-031/032.
- **Acceptance criteria:** hipótesis falsable resuelta; impacto por módulo; test matrix; riesgos y
  preguntas abiertas; siguiente fase exacta identificada.
- **Risks:** convertir una propuesta en claim implementado o mezclar tenant identity con target.
- **Dependencies:** arquitectura y líneas Phase 0–16/J0–J8 existentes.
- **Deferred:** toda implementación runtime y aceptación de ADRs.
- **Closure:** la separación es viable. `TableName` ya representa el target completo; metadata,
  accessors, conversiones, ID variants y encoders son reusables. COPY/lookup SQL quedan hoy ligados
  al target y deben hacerse operation-local. No se tocó código productivo.

## MS1 — Operation-Scoped Physical Target Contract — DONE (2026-08-20)

- **Objective:** prototipar y congelar el contrato neutral que entrega un `TableName` completo a
  una invocación sin mutar repository, metadata o conexión.
- **Scope:** API mínima en core, resolución default/runtime, política de conflicto y comparación de
  argumento directo frente a vista inmutable `forTarget`.
- **Non-goals:** repository Spring público, Boot, soporte end-to-end multi-schema, resolver tenant,
  table provisioning o cache SQL por target.
- **Modules/files:** core sólo si el contrato común lo exige; pgjdbc prototype/tests; ADR-031/032 y
  API inventory.
- **API impact:** aditivo; preferencia por reutilizar `TableName`. Ningún tipo `Tenant*`.
- **Architecture constraints:** target completo y schema-qualified en el nuevo path; mapping sin
  schema permite runtime; schema estático distinto rechaza; empty input mantiene no-op.
- **Tasks:** diseñar signatures; probar source/binary compatibility; centralizar validación;
  verificar que shape/target ya son separables; asegurar que no queda field mutable; revisar Javadocs.
- **Tests:** value/validation, matriz de conflicto estático, null/unqualified, metadata reuse,
  concurrent target selection y API baseline.
- **Documentation:** decisión final de ergonomía, examples conceptuales y migration/no-migration.
- **Acceptance criteria:** una única abstracción de target sirve insert/lookup; cero estado tenant;
  no overload explosion; ADR-031 aceptable o fase detenida con evidencia.
- **Risks:** API redundante con `TableName`, default methods incompatibles, facade retenida como
  pseudo-cache.
- **Dependencies:** MS0 y ADR-011/017/024/028.
- **Deferred:** SQL real cross-schema, métodos ejecutables, adapters Spring y compatibility matrix.
- **Closure:** se reutiliza `TableName`; `resolveRuntimeTarget` exige target calificado, tabla igual
  y schema igual cuando el mapping lo fija. Se eligen futuros argumentos explícitos, no vista. La
  metadata y API operativa legacy permanecen intactas; no se añadió SQL ni integración downstream.

## MS2 — pgJDBC Multi-Schema Bulk Insert — DONE (2026-08-24)

- **Objective:** ejecutar COPY insert sobre un target físico explícito reutilizando shape y encoder
  preparados.
- **Scope:** `PostgresBulkJdbcOperations`, `PostgresBulkInserter`, COPY SQL operation-local,
  batching, quoting, conflicto y misma conexión.
- **Non-goals:** lookup, Spring repositories, tenant resolution, schema creation y SQL cache.
- **Modules/files:** pgjdbc; core sólo por corrección del contrato MS1; architecture/docs insert.
- **API impact:** implementación del contrato MS1 en la facade low-level; default path intacto.
- **Architecture constraints:** encoder schema-independent; SQL no se conserva entre targets;
  schema explícito; connection caller-owned y sin mutadores.
- **Tasks:** refactor preparación; construir COPY SQL por llamada; conservar count/error/cancel;
  auditar que metadata.table no se usa accidentalmente cuando existe target.
- **Tests:** schemas A/B con misma tabla, quoted identifiers, tabla runtime distinta rechazada, multibatch,
  rollback/autocommit low-level, A→B en misma conexión, singleton concurrente, failure target A sin
  contaminar B y cero cache growth.
- **Documentation:** insert flow, performance model y security requirements.
- **Acceptance criteria:** filas llegan sólo al target explícito; legacy es idéntico; encoding se
  prepara una vez; ningún `setSchema`/`search_path`.
- **Risks:** COPY SQL stale, error messages con schema, regression de preparación/performance.
- **Dependencies:** MS1 y ADR-013/014/019.
- **Deferred:** lookup y adapters de alto nivel.
- **Closure:** la fachada low-level publica un overload de cuatro argumentos con `TableName` para
  evitar ambigüedad source con el overload histórico de opciones. Resuelve el target una vez,
  conserva metadata/encoder, construye COPY SQL qualified una vez por invocación no vacía y no
  retiene cache por target. PostgreSQL 15.18 valida A/B secuencial y concurrente, pool de un backend,
  commit/rollback, quoted identifiers, privilegios y fallos tardíos; legacy permanece intacto.

## MS3 — pgJDBC Multi-Schema Bulk Lookup — DONE (2026-08-24)

- **Objective:** dirigir CTAS, COPY y JOIN al mismo target explícito sin cambiar temp-table
  lifecycle ni materialización callback.
- **Scope:** `TemporaryTableBulkLookup`, `BulkLookupSql`, key metadata reuse, cleanup y
  transacciones cross-schema secuenciales.
- **Non-goals:** adaptive strategies, index/ANALYZE, derived keys, Spring materializers y result
  ordering.
- **Modules/files:** pgjdbc tests/docs; core sin cambios esperados.
- **API impact:** misma abstracción target de MS1, sin otra variante lookup-only.
- **Architecture constraints:** target qualified en CTAS y JOIN; temporal session-local; keys O(1)
  y target operation-local; primary/suppressed intactos.
- **Tasks:** desacoplar `BulkLookupSql` de prepare global; pasar target a toda sentencia; revisar
  aliases/quoting y fallos.
- **Tests:** simple/composite, duplicates/missing/null, A/B, insert A→lookup A/B, lookup A→B,
  quoted, schema conflict, read-only, `25P02`, cleanup, pool size one y concurrencia.
- **Documentation:** lookup flow, temporary schema distinction y no-order contract.
- **Acceptance criteria:** ningún SELECT/CTAS usa el target anterior/default por error; cero
  temporales filtradas; legacy y callbacks permanecen compatibles.
- **Risks:** una de las sentencias conserve otro target, cleanup en tx abortada y cache accidental.
- **Dependencies:** MS2 y ADR-006/015/027.
- **Deferred:** materialización JPA/JDBC sobre tablas runtime distintas.
- **Closure:** la fachada low-level añade un overload target-aware de seis argumentos. El target se
  resuelve una vez antes de consumir keys y un `InvocationSql` local usa su misma cualificación en
  CTAS/JOIN, sin cache por target ni mutación de metadata/conexión. PostgreSQL 15.18 valida A/B,
  insert→lookup, pool físico A→B, concurrencia, transacciones, simple/composite, 20k one-shot,
  quoted/conflictos/permisos, fallos, `25P02`, cleanup y recuperación.

## MS4 — Hibernate and Spring Data JPA Target Integration — DONE (2026-08-24)

- **Objective:** exponer target explícito en el fragmento JPA y demostrar materialización segura
  sobre una tabla física compatible distinta del mapping default.
- **Scope:** propagation repository→engine, cache JPA estructural, static schema conflict,
  `Session.doReturningWork`, native materialization y observability sin target tags.
- **Non-goals:** Hibernate `CurrentTenantIdentifierResolver`, `MultiTenantConnectionProvider`,
  session tenant switching, persistence-context synchronization y multi-table mappings.
- **Modules/files:** hibernate characterization, spring-data JPA, Boot JPA context tests sólo si
  cambia wiring, docs/API baseline.
- **API impact:** methods/facade aditivos según MS1; llamadas existentes iguales.
- **Architecture constraints:** EMF/type caches no incluyen target; repository nunca se muta;
  schema runtime no se obtiene del EntityManager/ThreadLocal.
- **Tasks:** sustituir engine target-bound cache por shape cache; propagar target; validar schema
  annotation; materializar lookup; revisar observation boundary.
- **Tests:** two schemas/same entity, static schema same/different, assigned/generated IDs,
  converters/embedded/associations, lookup, REQUIRED/REQUIRES_NEW/read-only/rollback, multiple EMF,
  eight threads A/B and same-connection sequence.
- **Documentation:** JPA adoption, persistence context caveats, Hibernate multi-tenancy boundary.
- **Acceptance criteria:** singleton repository atiende A/B sin leakage; metadata se resuelve una
  vez por EMF/type; no Hibernate tenant API en producción.
- **Risks:** native result mapping presupone mapped schema, identity cache misuse, interface binary
  compatibility.
- **Dependencies:** MS3 y ADR-004/005/016/017/020.
- **Deferred:** Spring Data JDBC y Boot user experience.
- **Closure:** el fragmento JPA publica insert/lookup target-aware y propaga el argumento al engine
  pgJDBC sobre la conexión de `Session#doReturningWork`. El overload corto target-first evita
  ambigüedad con options. PostgreSQL 15.18 confirma mismo proxy A/B, aislamiento, concurrencia,
  transacción multi-schema commit/rollback, `REQUIRES_NEW`, read-only, IDs, converters/embedded/FK,
  quoting, conflictos, SQLStates y cache estructural por identidad. Native materialization consume
  el JOIN qualified; no hay API tenant, target cache, metadata mutation ni cambios Boot/JDBC.

## MS5 — Spring Data JDBC Target Integration — DONE (2026-08-24)

- **Objective:** exponer el mismo target físico en el fragmento JDBC conservando root-only,
  conversiones e ID policy.
- **Scope:** repository/coordinator propagation, resolver structural cache, insert/lookup,
  materialization and transaction managers JDBC.
- **Non-goals:** datasource routing, aggregate children, callbacks, version/sequence, resolver
  tenant o API distinta a JPA sin necesidad demostrada.
- **Modules/files:** spring-data-jdbc, tests/docs/API baseline; core/pgjdbc sólo fixes generales.
- **API impact:** forma paralela/coherente con JPA respetando packages distintos.
- **Architecture constraints:** `JdbcOperations.execute(ConnectionCallback)` conserva conexión;
  mapping cache por converter/type; target no participa en ID variants.
- **Tasks:** propagar target en both operations; conservar lookahead/mixed ID; validar static
  schema; materializar A/B; auditar no mutation.
- **Tests:** scalar/converter/embedded/reference/record, assigned/generated/mixed, simple/composite
  lookup, A/B sequential/concurrent, REQUIRED/REQUIRES_NEW/NESTED, multiple DataSources selected
  externally, no cache target keys.
- **Documentation:** JDBC adoption, root-only and multi-datasource boundary.
- **Acceptance criteria:** mismos destinos y conflictos que JPA/pgjdbc; resolver identity reuse;
  no cambios en graph/lifecycle semantics.
- **Risks:** metadata qualified estática inesperada, mixed ID failure mid-COPY y APIs divergentes.
- **Dependencies:** MS4 y ADR-024–030.
- **Deferred:** Boot configuration and full matrix.
- **Closure:** el fragmento JDBC publica las tres formas target-aware simétricas con JPA y propaga
  el target por `JdbcOperations.execute(ConnectionCallback)` al engine pgJDBC existente. PostgreSQL
  15.18 confirma mismo proxy A/B, insert/lookup aislados, concurrencia, conexión física,
  commit/rollback multi-schema, REQUIRED/REQUIRES_NEW/NESTED condicionado, read-only, IDs,
  converters/embedded/reference, quoting, conflictos, SQLStates, `25P02`, pool/cleanup y cache
  estructural por identidad. No hay target cache/state, resolución tenant ni cambios Boot.

## MS6 — Spring Boot Composition and Store Coexistence — DONE (2026-08-24)

- **Objective:** validar que ambos starters componen la capacidad sin introducir configuración
  global de schema/tenant ni seleccionar infraestructura.
- **Scope:** conditions/back-off, JPA-only/JDBC-only/both, same/separate infrastructure,
  transaction characterization, pool/cache/concurrency and zero startup I/O.
- **Non-goals:** `postgres-bulk.schema`, tenant resolver bean, routing datasource, transaction
  manager creation, unified starter o release activation.
- **Modules/files:** ambos autoconfigure modules, starters as dependency-only, context/smoke docs.
- **API impact:** cero; ninguna property nueva.
- **Architecture constraints:** target sigue siendo argumento de operación; Boot no guarda default
  mutable ni lo resuelve.
- **Tasks:** context runners; filtered classpaths; back-off; both-store collision tests; starter JAR
  and dependency audits.
- **Tests:** missing classes/beans, custom resolver, multiple candidates, both starters, no target
  beans, zero connections at startup, PostgreSQL default+A/B/C smoke for each store, transactions,
  concurrency, quoting/errors and external consumers.
- **Documentation:** conditions, explicit application resolver example outside library and
  coexistence.
- **Acceptance criteria:** normal starter adoption funciona con target explícito; no JPA pollution
  in JDBC starter; no Java in starters; no global schema property.
- **Risks:** auto-config temptation to hide target selection, ambiguous beans and store collision.
- **Dependencies:** MS5 and ADR-018/030.
- **Deferred:** exhaustive compatibility, final examples/documentation and benchmarks.
- **Closure:** ambos starters componen los overloads MS4/MS5 sin cambio productivo. JPA-only,
  JDBC-only y ambos resolvers arrancan; datasource ambiguo hace back-off y primary/wiring explícito
  funciona. PostgreSQL 15.18 confirma default+A/B/C, aislamiento, same proxy, transacciones,
  `REQUIRES_NEW`, diferencia NESTED, read-only, concurrencia, quoting, SQLState, pool/cache y cero
  tags/properties target-aware. Starters/dependency boundaries y consumidores externos pasan.

## MS7 — Multi-Schema Compatibility, Examples & Documentation

- **Objective:** convertir la capacidad ya robusta en un contrato de adopción respaldado por la
  matriz de versiones soportada, ejemplos ejecutables y documentación completa.
- **Scope:** Java 17/21, Boot/Data/Hibernate/pgJDBC soportados, PostgreSQL 15–18, ejemplos JPA/JDBC,
  API diff, consumers limpios, guías operativas y evidence index.
- **Non-goals:** publicación, release/tag, Boot 4, resolver tenant, security baseline, benchmarks o
  nuevas semánticas de operación.
- **Modules/files:** compatibility workflow, examples/verification, user guide, architecture,
  release-readiness note and roadmap.
- **API impact:** ninguno esperado; freeze de la API aditiva MS1–MS5.
- **Architecture constraints:** target explícito, SQL qualified, cero cache/state tenant-aware y
  claims públicos limitados a jobs/evidencia.
- **Tasks:** ampliar lanes pairwise, ejecutar ejemplos externos, completar migration/security/error
  guidance, API/docs/JAR/dependency audits y cierre de riesgos.
- **Tests:** matriz soportada, examples/consumers A/B, API baseline, docs links, dependency/JAR audit
  and full PostgreSQL lanes.
- **Documentation:** getting started JPA/JDBC, composition, transactions, migrations, authorization
  boundary, errors, observability and limitations.
- **Acceptance criteria:** jobs obligatorios verdes, ejemplos sólo con API pública, A/B probado en
  la matriz, docs consistentes y decisiones diferidas explícitas.
- **Risks:** coste combinatorio de CI, examples que conviertan input no confiable directamente en
  schema y claims que excedan la evidencia.
- **Dependencies:** MS6 y ADR-021/031/032.
- **Deferred:** benchmarks, publicación, security/supply-chain baseline and Boot 4 generation.

## MS8 — Multi-Schema Benchmarks and Final Closure

- **Objective:** medir el overhead operation-scoped y cerrar la línea técnica sin publicar.
- **Scope:** default frente a warm A/B/C, cardinalidad de schemas, insert/lookup, adapter frente a
  pgJDBC low-level y revisión final ADR/risk.
- **Non-goals:** Central upload/tag/release, Boot/Data 4, security baseline, adaptive target cache,
  universal performance claims or automatic migrations.
- **Modules/files:** benchmark module no publicable, reports, evidence and final roadmap state.
- **API impact:** freeze de la API MS1–MS5; ninguna expansión motivada sólo por benchmark.
- **Architecture constraints:** no cache por target aunque construir SQL tenga coste medible;
  correctness checks fuera del timing y transacciones comparables.
- **Tasks:** warmup/measurement reproducible, dos baselines, perfil grande, cardinality stress,
  publish raw evidence and close/defer risks.
- **Tests:** benchmark correctness, repeatability, no target leakage/cache growth and full build.
- **Documentation:** metodología, resultados con límites y cierre técnico.
- **Acceptance criteria:** evidencia reproducible, claims acotados, cero regresión y ADRs cerrados o
  diferidos explícitamente.
- **Risks:** ruido, coste CI y convertir point estimates en recomendaciones universales.
- **Dependencies:** MS7 y ADR-022/031/032.
- **Deferred:** publication activation, security/supply-chain baseline, Boot 4 and new tenancy model.

## Dependencias

```text
MS0 investigation
  -> MS1 operation-scoped target contract
    -> MS2 pgJDBC insert
      -> MS3 pgJDBC lookup
        -> MS4 Hibernate/JPA
          -> MS5 Spring Data JDBC
            -> MS6 Boot/coexistence
              -> MS7 compatibility/examples/documentation
                -> MS8 benchmarks/final closure
```

La única siguiente fase autorizable después de MS6 es
**MS7 — Multi-Schema Compatibility, Examples & Documentation**.
