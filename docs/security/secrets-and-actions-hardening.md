# Secrets and GitHub Actions hardening

**Estado:** SEC1 `DONE` (2026-08-24). La publicación continúa congelada. Esta fase no creó
secrets, tags, releases ni uploads y no activó Dependabot, SCA, SAST o SBOM.

## Resultado y límites

Los cuatro workflows usan permisos `contents: read`, una allow-list versionada de tres Actions y
full commit SHAs verificados contra los tags oficiales. Checkout nunca persiste credenciales; los
jobs no publicadores impiden que `setup-java` genere settings Maven. Los valores dinámicos de shell
entran por `env` y se citan; Benchmarks conserva además un `choice` y un `case` con la misma
allow-list. Gitleaks 8.30.1 pasó sobre el árbol actual y los 66 commits existentes sin findings.

SEC1 no cambió POMs, Java, API pública, imágenes Docker, dependencias o comportamiento de
publicación. **Actualización SEC5:** el inventario quedó cerrado y la estrategia remota descrita
históricamente en esta página fue sustituida por firma local; Release ahora es candidate-only y
referencia cero secrets.

## Inventario de workflows

| Workflow | Triggers e inputs | Jobs/permisos | Actions, caches y artifacts | Secrets y trust boundary |
| --- | --- | --- | --- | --- |
| Build | `push` sólo `main`; `pull_request`; sin inputs | `verify`; self-hosted dedicado; `contents: read`; timeout 120; concurrency por ref cancelable | checkout limpio/setup-java; cache Maven por POM; sin artifact/service | sólo push o PR owner+same-repo; sin secrets/deploy/token write; current-tree Gitleaks y policy gate antes del build |
| Compatibility | `push` sólo `main`; `pull_request`; matrices Java/PostgreSQL/Hibernate/pgJDBC versionadas en YAML | siete jobs/11 lanes; self-hosted dedicado; `contents: read`; timeout 60; concurrency por ref cancelable | checkout limpio/setup-java; cache Maven; sin artifact/service | sólo push o PR owner+same-repo; sin secrets/deploy; matrix shell values pasan por `env` quoted |
| Benchmarks | `workflow_dispatch`; `profile` tipo `choice` con cinco valores | `benchmark`; `contents: read`; timeout 120; concurrency única no cancelable | checkout/setup-java/upload-artifact; cache Maven; JSON JMH 14 días | sin secrets; profile validado en dispatch, workflow y script; artifact no tiene consumidor automático |
| Release | sólo `workflow_dispatch`; version, full SHA y confirmación literal | sólo `candidate` 120; `contents: read`; concurrencia no cancelable | candidate usa cache Maven y sube staging/checksums 7 días | sin secrets, firma, token ni upload; workflow no fue ejecutado |

No existen `pull_request_target`, `workflow_run`, `repository_dispatch`, schedules, triggers de tag,
services ni composite Actions. Build y Compatibility usan exclusivamente el selector
`[self-hosted, linux, x64, postgres-bulk-ci]`; Benchmarks y Release conservan `ubuntu-latest` y
siguen manuales. Compatibility no acepta inputs; sus matrices sólo pueden cambiar mediante commit
revisado.

El guard exacto de cada job self-hosted permite push y, para PR, exige simultáneamente actor
`yravelo` y `head.repo.full_name == github.repository`. El trigger conserva visibilidad, pero un
fork u otro actor queda skipped antes de asignar el host. Docker confiere capacidad aproximadamente
equivalente a root, por lo que este límite es obligatorio y se documenta operativamente en
[Trusted self-hosted CI runner](self-hosted-runner.md).

## Action allow-list y pins

| Action | SHA aprobado | Versión oficial verificada | Uso |
| --- | --- | --- | --- |
| `actions/checkout` | `d23441a48e516b6c34aea4fa41551a30e30af803` | `v6` | todos los jobs |
| `actions/setup-java` | `b6effb05e454b25005698d916606bdc6ffcbf961` | `v5` | todos los jobs |
| `actions/upload-artifact` | `ea165f8d65b6e75b540449e92b4886f43607fa02` | `v4` | Benchmarks y candidate |

La API GitHub de cada repositorio oficial resolvió el major tag exactamente al SHA registrado el
2026-08-24. No hay Actions fuera de `actions/*`, Docker Actions, reusable workflows ni referencias
locales. `scripts/check-workflow-security.py` falla ante una Action no allow-listed, tag mutable,
SHA distinto o comentario de versión incoherente. Una actualización debe verificar owner, release,
tag→SHA, changelog y diff antes de cambiar simultáneamente la allow-list y los workflows.

## Checkout, setup-java y GITHUB_TOKEN

Todos los checkout fijan `persist-credentials: false`; Build y Compatibility fijan además
`clean: true` por usar un host persistente. Build, Compatibility y Benchmarks declaran
`fetch-depth: 1`, mientras Release usa `fetch-depth: 0` para validar history y ancestry. LFS y
submodules permanecen false. Ningún checkout recibe token custom ni existe `git push`.

Los jobs Build/candidate/test fijan `overwrite-settings: false`: `setup-java` instala Temurin y
puede usar cache Maven, pero no genera un `settings.xml` publicador, no recibe server credentials ni
importa GPG. En Build/Compatibility, `settings-path` queda aislado bajo `runner.temp` para que el
host persistente no conserve settings en su home. SEC5 eliminó la excepción `central-upload`;
ningún job recibe inputs de publicación o importa GPG.

El `GITHUB_TOKEN` remoto tiene default read-only y no puede aprobar PR reviews. Los workflows
declaran además `contents: read`; no solicitan OIDC, packages, deployments, issues, pull requests,
tags o releases write. GitHub y Actions pueden usar el token read-only internamente, pero ningún
`run` lo recibe o imprime. Masking reduce exposición accidental: no sustituye scope, aislamiento,
revocación ni eliminación segura.

## Shell, inputs y matrices

El gate prohíbe expresiones `${{ ... }}` dentro de todos los `run`, `eval`, shell tracing y
`git push`. Los valores Release ya entraban por `env` y preservan stable SemVer, SHA hexadecimal de
40 caracteres, confirmación `candidate <version>`, repository/actor/default-branch y
`SHA ∈ origin/main`.

Benchmarks traduce `inputs.profile` y `github.run_id` a variables `env`, cita ambas y rechaza
cualquier profile fuera de la lista incluso si GitHub omitiese el tipo `choice`. El script runner
conserva su propio `case`, de modo que no existen Maven/JMH args libres. Compatibility hace lo
mismo con valores matrix que llegan a comandos Maven; los usos no-shell de matrix en nombres e
inputs declarativos no abren evaluación de comandos.

## Secrets y lifecycle

El inventario remoto de Repository Actions Secrets estaba vacío durante SEC1 y sigue vacío. SEC5
prohíbe almacenar la clave/passphrase de firma en Actions: todos los workflows deben permanecer sin
`secrets.*`. La clave vive sólo en la estación firmante y la passphrase entra por pinentry.

Si se sospecha exposición, se detiene el workflow y se revoca/rota antes de limpiar el repo. No se
imprime el finding, no se confía en masking y no se reescribe history automáticamente. La ausencia
actual de secrets reduce exposición, pero el gate protege el boundary antes de una activación futura.

SEC6 distingue GitHub token, Central token, passphrase, private key y runner registration token en
la [matriz operativa de incidentes](incident-response-runbook.md#secret-exposure-matrix); cada tipo
tiene revocación, evidencia y exit criteria propios.

## Gitleaks reproducible y triage

`scripts/check-secrets.sh` fija Gitleaks 8.30.1 y soporta Linux x86-64/arm64. Descarga desde el
release oficial con HTTPS/TLS, verifica SHA-256 del archive y del binario extraído y vuelve a
verificar versión/hash al reutilizar cache temporal. No usa una Action externa, no escribe
JSON/SARIF y ejecuta `--redact=100`.

```bash
./scripts/check-secrets.sh current
./scripts/check-secrets.sh history
```

Build ejecuta `current` temprano. Release candidate, con checkout completo, ejecuta `history`
antes de cualquier artifact o eventual upload; Compatibility no repite el scan. SEC1 ejecutó ambos
modos localmente: current sin findings y history sobre 66 commits sin findings. No fue necesaria
`.gitleaks.toml`. El grep Release anterior permanece como control project-specific suplementario
para nombres/patrones Central; Gitleaks es el control primario.

Una excepción futura debe ser fingerprint o rule/path-specific, registrar evidencia, owner y
expiry y nunca cubrir paquetes/historial completo. Un secret real se revoca aunque el detector deje
de verlo. Fixtures deben contener valores deliberadamente no utilizables; SEC1 prueba la policy,
no introduce un pseudo-secret que pueda copiarse por error.

## Artifacts y caches

El JSON de Benchmarks contiene resultados JMH y expira en 14 días; no se descarga automáticamente.
El candidate artifact contiene sólo staging, auditoría y checksums explícitos y expira en 7 días.
Settings, GPG home, private keys, secret reports y hidden files quedan fuera del path.

Los caches son exclusivamente los de Maven administrados por setup-java, con keys derivadas de
plataforma y POMs; no hay restore keys custom ni cache upload/download manual. Un PR no recibe
secrets ni permisos write. Candidate puede restaurar dependencies, pero los gates de staging y
reproducibilidad no deben depender de cache. El riesgo de dependency/plugin comprometido se trata
en SEC2, no desactivando cache en SEC1.

## Gate determinista y pruebas

`scripts/check-workflow-security.py` parsea YAML con `PyYAML.BaseLoader` y exige inventario exacto,
triggers, permissions, timeouts, allow-list/pins, checkout/setup-java, ausencia de interpolación
shell, secret scope, retention y guards Release. También fija el selector y guard self-hosted, la
limpieza del checkout y las 11 lanes Compatibility; Build/Compatibility no pueden referenciar
secrets. Falla cerrado y no usa `continue-on-error`.

`scripts/test-workflow-security.py` demuestra que:

- un full SHA aprobado pasa y un tag mutable falla;
- `pull_request_target` falla;
- un secret en candidate se detecta;
- un trigger push en Release falla;
- `contents: write` falla.
- Build en GitHub-hosted o sin label dedicado falla;
- Compatibility sin guard PR confiable falla;
- el selector self-hosted válido pasa y Release sigue en GitHub-hosted.

## Configuración remota auditada

REL1-A-R R4C re-registró oficialmente el runner repository-level con display name y hostname
neutrales `postgres-bulk-ci-01`. El selector dedicado no cambió: GitHub reporta `self-hosted`,
`Linux`, `X64`, `postgres-bulk-ci`, mientras YAML conserva sus equivalentes lowercase. Los 13 jobs
nuevos de Build, Compatibility y Security usaron la identidad neutral, 0 repository secrets y
`persist-credentials: false`; no se añadió ningún acoplamiento al display name.

La auditoría read-only del 2026-08-24 confirmó: repositorio privado, default branch `main`, Actions
habilitadas, `allowed_actions: all`, `sha_pinning_required: false`, default workflow permissions
read, incapacidad del token para aprobar reviews y cero nombres de Repository Actions Secrets. La
API de fork-approval respondió que el ajuste no aplica a private repositories. El environment
`maven-central` no tiene protection rules ni deployment branch policy. Retención default global no
quedó expuesta por los endpoints usados; por eso cada upload declara su retención.

No se cambió configuración remota. Restringir Actions globalmente a `actions/*` es posible, pero
la policy versionada es más estrecha: permite tres repos+SHAs y se valida en Build/candidate. El
setting remoto sigue siendo defensa adicional futura, no sustituto del gate en Git. Rulesets y
required reviews continúan limitados por el plan/visibilidad; single-maintainer, exact SHA/tag,
dispatch manual, least privilege y CI fail-closed son controles compensatorios, no una simulación
de separación de funciones.

## Fuentes oficiales

- [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use)
- [Workflow permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)
- [Using secrets](https://docs.github.com/en/actions/how-tos/security-for-github-actions/security-guides/using-secrets-in-github-actions)
- [Artifact attestations and integrity](https://docs.github.com/en/actions/concepts/security/artifact-attestations)
- [actions/checkout](https://github.com/actions/checkout/blob/main/README.md)
- [actions/setup-java](https://github.com/actions/setup-java/blob/main/README.md)
- [actions/upload-artifact](https://github.com/actions/upload-artifact/blob/main/README.md)
- [Gitleaks releases and documentation](https://github.com/gitleaks/gitleaks)

## Deferred

Dependabot, OSV/OWASP/Snyk, SpotBugs/FindSecBugs, SBOM, provenance, GPG key generation, release tag,
Central upload y publicación permanecen fuera de SEC1. La siguiente fase es **SEC2 — Dependency
Vulnerability and Update Management**.
