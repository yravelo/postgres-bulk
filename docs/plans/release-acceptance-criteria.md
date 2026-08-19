# Criterios de aceptación de la primera release pública

Estado auditado para el candidato `0.1.0` en Phase 16. Cada criterio usa exclusivamente `PASS`,
`BLOCKED`, `EXTERNAL PREREQUISITE` o `DEFERRED (non-blocking)`.

## Funcionalidad

- **PASS** — Insert acepta input vacío, 1, múltiples y al menos 10k filas con batching correcto.
- **PASS** — Lookup por clave simple/compuesta usa temp table + COPY + JOIN sin entidades parciales.
- **PASS** — Duplicados, null, orden y partial failure están documentados y probados.
- **PASS** — CSV round-trip cubre NULL, empty, delimitadores, quotes, saltos, UTF-8 y tipos soportados.

## Arquitectura y API

- **PASS** — Core no referencia frameworks ni infraestructura.
- **PASS** — pgJDBC e Hibernate son adapters hermanos y no existen ciclos.
- **PASS** — La API pública no expone COPY/CSV/temp tables/`PGConnection`/internals Hibernate.
- **PASS** — El starter no contiene lógica Java, hace back-off y no obliga Actuator.
- **PASS** — No existe dependencia corporativa ni clase placeholder.
- **PASS** — Existe baseline binaria reproducible de la API pública `0.1.0`.
- **DEFERRED (non-blocking)** — Revapi/japicmp se evaluará al existir una segunda baseline.

## Metadata y SQL

- **PASS** — Mappings e identificadores físicos conservan el contrato validado en Phases 8–13.
- **PASS** — Tipos de temporales provienen de PostgreSQL o metadata física verificable.
- **PASS** — POMs staged no contienen paths locales, repos privados, SNAPSHOTs ni módulos no publicables.
- **PASS** — Name, description, license, project URL, SCM previsto y developer `yravelo` usan la
  identidad final aprobada; no se publica email.
- **EXTERNAL PREREQUISITE** — Crear el repository privado, verificar el SCM remoto y confirmar si
  GitHub Issues estará habilitado antes de añadir issue management.

## Transacciones, robustez y operación segura

- **PASS** — DDL, COPY y JOIN usan la misma conexión física y respetan ownership Spring/caller.
- **PASS** — Commit, rollback, read-only, autocommit, REQUIRED, REQUIRES_NEW y cleanup están probados.
- **PASS** — `NESTED` queda explícitamente unsupported y no existe retry automático.
- **PASS** — SQLState, causa primaria/suppressed, pool reuse, concurrencia y backend loss conservan evidencia.
- **PASS** — Logs, métricas y tags no incluyen filas, keys ni cardinalidad abierta.
- **PASS** — Observabilidad es fail-open, desactivable y no requiere Actuator/exporter/registry propio.

## Calidad y compatibilidad

- **PASS** — Unit, integration, architecture y compatibility suites corresponden a la matriz publicada.
- **PASS** — PostgreSQL real/Testcontainers valida comportamiento, no mocks sustitutivos.
- **PASS** — Benchmarks son reproducibles, explícitos y quedan fuera del build normal/publicación.
- **PASS** — Javadocs estrictos y snippets/example compilan con cero warnings.
- **PASS** — Wrapper, timestamp fijo y comparación doble validan reproducibilidad del release candidate.

## Artifacts y staging

- **PASS** — Los seis módulos Java producen binary, sources, Javadocs y POM `0.1.0`.
- **PASS** — Parent POM se despliega como soporte; benchmark y example no se despliegan.
- **PASS** — Manifest expone `Implementation-Version`; no se añade JPMS artificial.
- **PASS** — El perfil `release` ejecuta tests y bloquea dependencies/parent SNAPSHOT.
- **PASS** — El build normal no requiere firma, token ni secret.
- **PASS** — Staging Maven local contiene únicamente el parent y los seis artifacts publicables.
- **PASS** — Consumer independiente usa parent Boot propio, `0.1.0` y repositorio Maven local aislado.
- **PASS** — Consumer cubre startup, fragment, insert, lookup, rollback, read-only y observabilidad.
- **PASS** — Dependency tree no contiene SNAPSHOT, benchmark/example, Testcontainers productivo ni Actuator.

## License, supply chain y gobernanza

- **PASS** — LICENSE, POMs y documentación son consistentes con Apache-2.0.
- **PASS** — Auditoría reproducible de licencias productivas no presenta metadata desconocida.
- **PASS** — No se requiere NOTICE vacío según el contenido actualmente auditado.
- **PASS** — SHA-256 de artifacts staged se genera e inspecciona.
- **PASS** — Firma OpenPGP está aislada en `central-publish`; estrategia y secrets están documentados.
- **DEFERRED (non-blocking)** — SBOM se evaluará tras definir formato/lifecycle estable.
- **DEFERRED (non-blocking)** — Provenance/attestations requiere un remote/visibilidad GitHub verificables.
- **PASS** — Auditoría de patrones sensibles no encuentra tokens, passwords ni private keys hardcoded.
- **PASS** — `CHANGELOG.md`, release notes y política SemVer `0.x` existen.
- **EXTERNAL PREREQUISITE** — Crear el repository y habilitar GitHub Private Vulnerability
  Reporting para activar el canal documentado en `SECURITY.md`.

## Coordinates y publicación

- **PASS** — El Maven groupId final es `io.github.yravelo` y el namespace Java final es
  `io.ybr.postgresbulk`; no queda uso activo de `io.github.postgresbulk`.
- **PASS** — ADR-008 está `ACCEPTED` y registra deliberadamente que Maven groupId y Java package
  root son distintos; ADR-023 mantiene pendiente sólo la activación externa.
- **PASS** — `${revision}` cambia SNAPSHOT/release sin editar múltiples POMs.
- **PASS** — Maven Central Publisher Portal y su plugin oficial son el target, sin endpoints legacy.
- **EXTERNAL PREREQUISITE** — Verificar namespace y crear cuenta/token del Central Portal.
- **EXTERNAL PREREQUISITE** — Crear/proteger clave OpenPGP y configurar secrets del environment.
- **EXTERNAL PREREQUISITE** — Configurar remote, URLs reales, canal privado y tag `v0.1.0`.
- **EXTERNAL PREREQUISITE** — Ejecutar/validar el workflow en el remote y autorizar el upload.
- **PASS** — Workflow manual exige versión, tag, confirmación, environment y deja `autoPublish=false`.
- **PASS** — No ocurrió push, tag, release ni publicación remota durante Phase 16.

## Veredicto

La ingeniería local del candidato está cerrada y no quedan blockers técnicos. La release pública
sigue **NOT READY** mientras no se completen los external prerequisites de repository/remoto,
Central, security reporting, environment, secrets, OpenPGP, tag, workflow remoto y autorización de
publicación. Phase 16 permanece `PARTIALLY DONE`; no se realizó ninguna acción remota.
