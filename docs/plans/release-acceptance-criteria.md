# Criterios de aceptación de la primera release pública

Estado auditado para el candidato `0.1.0` tras SEC5. Cada criterio usa exclusivamente `PASS`,
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
- **PASS** — El repository `yravelo/postgres-bulk` existe como PRIVATE, el SCM remoto coincide con
  los POMs y GitHub Issues está habilitado.

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

- **PASS** — Los nueve módulos publicables producen binary, sources, Javadocs y POM `0.1.0`.
- **PASS** — Parent POM se despliega como soporte; benchmark y example no se despliegan.
- **PASS** — Manifest expone `Implementation-Version`; no se añade JPMS artificial.
- **PASS** — El perfil `release` ejecuta tests y bloquea dependencies/parent SNAPSHOT.
- **PASS** — El build normal no requiere firma, token ni secret.
- **PASS** — Staging Maven local contiene únicamente el parent y los nueve artifacts publicables:
  core, pgJDBC, Hibernate, Spring Data JPA/JDBC y sus cuatro artifacts Boot.
- **PASS** — El inventario separa 37 artifacts Maven primarios de nueve SBOM JSON adjuntos y un
  aggregate de evidencia.
- **PASS** — El dry-run firmado verifica 46 firmas Central y tres firmas de evidencia; el manifest
  liga coordenadas, clasificadores, SHA-256, SBOM, fingerprint, versión y source commit.
- **PASS** — La comparación unsigned/signed conserva idénticos los 46 payloads; faltantes,
  wrong-signer, tampering, checksum incorrecto, artifact inesperado, SNAPSHOT y benchmark fallan.
- **PASS** — Consumer independiente usa parent Boot propio, `0.1.0` y repositorio Maven local aislado.
- **PASS** — Consumer cubre startup, fragment, insert, lookup, rollback, read-only y observabilidad.
- **PASS** — Dependency tree no contiene SNAPSHOT, benchmark/example, Testcontainers productivo ni Actuator.

## License, supply chain y gobernanza

- **PASS** — LICENSE, POMs y documentación son consistentes con Apache-2.0.
- **PASS** — Auditoría reproducible de licencias productivas no presenta metadata desconocida.
- **PASS** — OSV-Scanner 2.5.1 verifica por checksum e inspecciona 138/138 coordenadas externas;
  no queda ningún BLOCK y los cinco WARN exactos tienen owner, evidencia y expiry revisable.
- **PASS** — El release candidate repite el vulnerability gate antes de cualquier upload futuro;
  scanner error, inventario incompleto, production HIGH/CRITICAL sin triage o accepted risk expirado
  bloquean.
- **PASS** — Dependabot opera en cinco lanes semanales Maven/Actions con PRs limitados, majors
  manuales y sin auto-merge; Compose conserva tags explícitos y revisión manual porque las dos
  lanes Docker reales rechazaron `compose.yaml`.
- **PASS** — SpotBugs 4.10.4 con FindSecBugs 1.14.0 analiza los siete módulos productivos durante
  `verify`; los seis findings iniciales tienen triage/exclusión exacta y quedan cero sin triage.
- **PASS** — Build y Release validan reportes y activación FindSecBugs; Compatibility documenta y
  aplica `spotbugs.skip` sólo para evitar once repeticiones del gate canónico.
- **PASS** — No se requiere NOTICE vacío según el contenido actualmente auditado.
- **PASS** — SHA-256 de artifacts staged se genera e inspecciona.
- **PASS** — OpenPGP sigue siendo obligatorio; `local-signing` usa Maven GPG 3.2.8,
  gpg-agent/pinentry, fingerprint completo y SHA-512 sin compartir clave/passphrase con CI.
- **PASS** — CycloneDX Maven plugin 2.9.3 genera spec 1.6 JSON para los nueve artifacts y aggregate;
  Maven/OSV/purl/edge/version/scope se reconcilian y dos generaciones limpias se comparan
  semánticamente.
- **PASS** — La baseline de 55 dependencias externas productivas contiene ocho IDs de licencia,
  cero unknown, seis reviews múltiples, dos excepciones exactas y cero BLOCK.
- **PASS** — `release-inventory.json`, SHA-256, firma del aggregate y exact source commit forman la
  provenance mínima; no se afirma SLSA.
- **DEFERRED (non-blocking)** — GitHub artifact attestations requiere Enterprise Cloud para este
  repository privado; Sigstore no sustituye OpenPGP y queda diferido.
- **PASS** — Auditoría de patrones sensibles no encuentra tokens, passwords ni private keys hardcoded.
- **PASS** — El inventario SEC7 reconcilia gates, tools, cinco workflows, 14 POMs, módulos
  publicables/no publicables, expiries y fingerprint OpenPGP; drift o caducidad falla cerrado.
- **PASS** — Security tiene schedule semanal UTC y dispatch manual sobre el runner confiable, con
  `contents: read`, cero secrets, full history, timeout y concurrencia explícitos.
- **PASS** — El preflight técnico admite el canal privado `PENDING`; el preflight REL1 lo bloquea
  de forma separada hasta que sea aprobado y probado.
- **PASS** — SEC7 cierra sobre `0533866b04b4d89ea9199473dd5abf2cceb852c0`: Build
  `32828408419`, Compatibility `32828408347` 11/11 y Security manual `32828466698` pasan sin retry
  ni gate debilitado.
- **PASS** — `CHANGELOG.md`, release notes y política SemVer `0.x` existen.
- **BLOCKED** — No existe todavía un canal privado externo aprobado y probado; `SECURITY.md` lo
  declara `PENDING OWNER ACTION` y prohíbe inventar el email Git o usar Issues.
- **DEFERRED (non-blocking como feature)** — GitHub Private Vulnerability Reporting requiere que el
  repository sea público; REL1 reevaluará/activará PVR y probará notifications/intake.
- **DEFERRED (non-blocking)** — Branch protection/rules requiere GitHub Pro o hacer público el
  repository; se preservó la visibilidad PRIVATE y no se aplicaron reglas.

## Coordinates y publicación

- **PASS** — El Maven groupId final es `io.github.yravelo` y el namespace Java final es
  `io.ybr.postgresbulk`; no queda uso activo de `io.github.postgresbulk`.
- **PASS** — ADR-008 está `ACCEPTED` y registra deliberadamente que Maven groupId y Java package
  root son distintos; ADR-023 mantiene pendiente sólo la activación externa.
- **PASS** — `${revision}` cambia SNAPSHOT/release sin editar múltiples POMs.
- **PASS** — Maven Central Publisher Portal y el plugin oficial `0.11.0` son el target, sin
  endpoints legacy y con `autoPublish=false`.
- **PASS** — El owner confirma `io.github.yravelo` como `VERIFIED` en Maven Central Portal; no se
  almacena screenshot, sesión ni token.
- **PASS** — La firma local es la frontera aprobada; el runner persistente y GitHub Actions no
  reciben clave, passphrase ni token.
- **DEFERRED (non-blocking)** — El environment vacío `maven-central` permanece como marcador inerte
  y el workflow no lo referencia porque el plan no aporta secrets/protections utilizables.
- **EXTERNAL PREREQUISITE** — El Portal token sigue MISSING; no se configuró ningún secret remoto.
- **PASS** — Clave OpenPGP real RSA-3072 protegida, fingerprint
  `11545CD242C9575DF408AC08F83D364143C798A3`, revocación privada y export público preparado.
- **PASS** — `origin` usa SSH, `main` está publicado y las URLs de project/SCM coinciden con el
  repository privado real.
- **PASS** — Build remoto `32264391877` y los 10 jobs de Compatibility `32264393355` terminaron
  correctamente para `7b7c0f6394c8220f1149ef2fb21c718e535522bb`.
- **PASS** — El hardening SHA `457681c7be28222fa2cd5b715f613da8523abc5a` pasa Build
  `32274812469` y los 10 jobs de Compatibility `32274812453` (PostgreSQL 16.14 pasó al reintentar
  únicamente el job fallido, sin cambios ni reducción de matriz).
- **PASS** — SEC4 queda cerrado para `fbb1105c83c3a75312604ae6c9bb5f14b74a782c`: Build
  self-hosted `32774191694` pasó todos los gates y Compatibility `32774191674` pasó 11/11. El runner
  es repository-scoped, non-root y usa labels dedicadas; PRs fork/no confiables no se ejecutan
  automáticamente. No se ejecutaron Benchmarks ni Release.
- **EXTERNAL PREREQUISITE** — Configurar/probar el canal privado; sólo después crear `v0.1.0` y
  autorizar por separado tag, upload y publicación.
- **PASS** — Release es candidate-only, `workflow_dispatch` desde `main` por `yravelo`; exige stable
  SemVer, full SHA perteneciente a `origin/main` y confirmación literal.
- **PASS** — El workflow no contiene job/input de upload ni referencias `secrets.*`; todas las
  Actions están pinned, `GITHUB_TOKEN` conserva `contents: read` y `autoPublish=false`.
- **PASS** — Fork PRs y Dependabot no reciben Actions secrets en condiciones documentadas; release
  no usa `pull_request_target`, `workflow_run`, push, PR ni schedule.
- **PASS** — Hubo push de `main`; no se creó tag ni se ejecutó release, upload o publicación.

## Veredicto

SEC5 está cerrado con identidad OpenPGP y signed dry-run verificable. SEC6 implementa policy,
runbook, governance, CODEOWNERS/checklist y handoff PVR, pero queda `PARTIALLY DONE` porque falta un
canal privado externo aprobado y probado. SEC7 queda `DONE` con validación continua, drift,
expiries, salud del runner y preflights separados; su cierre remoto no autoriza REL1. La release pública aún
no está autorizada: además falta verificar el backup offline, generar el Portal token, crear el tag
exacto y autorizar upload/publicación. No se requieren Repository Secrets y SEC8 no se inicia.
