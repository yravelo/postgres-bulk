# Roadmap de seguridad y supply chain

La línea SEC0–SEC8 parte de los roadmaps 0–16, J0–J8 y MS0–MS8 ya cerrados. No los reabre y no
autoriza publicación. La baseline objetivo cuesta €0 recurrente y se ajusta al repositorio privado,
single-maintainer y Java/Maven actual.

Reglas transversales:

- una fase activa sólo su propio scope y termina con diff, tests, docs, commit/push y CI verde;
- ningún bot hace auto-merge; un tool outage no se convierte en suppress permanente;
- findings se triagean por scope, applicability, reachability y explotación, no sólo CVSS;
- toda excepción registra owner, evidence y expiry; no hay mass baselines;
- source productivo sólo cambia si un finding demuestra un defecto y ese fix se autoriza dentro de
  la fase; instalar un scanner no autoriza refactors;
- release, credentials, secrets, keys, tags, upload y publicación siguen requiriendo autorización
  separada.

## SEC0 — Investigation, Threat Model & Roadmap — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | diseñar threat model, baseline mínima, gates y secuencia sostenible |
| Scope | source/deps/build/CI/release, tools, costes, triage y suppressions |
| Non-goals | activar scanners, editar POM/workflows/product code o publicar |
| Tools/cost | documentación y fuentes primarias; €0 |
| Affected | `docs/security`, `docs/plans`, índice docs |
| Gates/tests | `git diff --check`, documentation audit |
| False positives | policy diseñada, ninguna suppression creada |
| Documentation | investigación SEC0 y este roadmap |
| Acceptance | Definition of Done SEC0 completa y no tooling impact |
| Risks | conclusions obsoletas si cambia plan/visibilidad; revisar en cada SEC |
| Dependencies | roadmaps anteriores completos |
| Deferred | toda activación a SEC1+ |

## SEC1 — Secrets and GitHub Actions Hardening — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | cerrar secret leaks y ejecución mutable/injectable en workflows |
| Scope | Gitleaks CLI; SHA pinning; checkout credentials; env inputs; permissions, timeout, concurrency y action policy |
| Non-goals | SCA, SAST, SBOM, keys/secrets reales, release/publication |
| Tools/cost | Gitleaks CLI y checks shell/YAML; €0, fast |
| Affected | cuatro workflows, scripts security, Gitleaks wrapper/ignore, CONTRIBUTING/security docs |
| Gates | Build current-tree scan; Release/full local history scan; deterministic YAML policy; todas las Actions por full SHA con version comment |
| Tests | mutable tag, `pull_request_target`, candidate secret, release push y `contents: write` fallan; full SHA pasa; remote CI verde |
| False positives | ninguna excepción necesaria; si aparece: path/rule exactos + evidence/owner/expiry; revocation precede suppression |
| Documentation | `docs/security/secrets-and-actions-hardening.md`, comandos locales, incident steps, pin update y workflow inventory |
| Acceptance | Gitleaks 8.30.1 current/history sin findings; SHA allow-list; no shell context directo; secrets sólo upload; least privilege preservado |
| Risks | history scan noise; stale SHAs; accidental display de finding |
| Dependencies | SEC0 |
| Deferred | Dependabot/OSV a SEC2; release inventory a SEC5 |

## SEC2 — Dependency Vulnerability and Update Management — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | detectar y remediar CVEs/staleness sin un gate ruidoso |
| Scope | dependency graph/Dependabot alerts+security/version updates; OSV production scan; Maven/Actions/Docker/Wrapper update lanes; image digests |
| Non-goals | Snyk connection, auto-merge, paid Dependency Review, source refactors |
| Tools/cost | Dependabot + OSV-Scanner; €0, moderate network/triage |
| Affected | `.github/dependabot.yml`, security workflow/scripts/config, POM/image references, docs |
| Gates | no untriaged applicable critical/high production or build-chain finding; no dynamic versions/repositories; alerts enabled |
| Tests | resolved trees for nine publishable modules; direct/transitive/test distinction; known synthetic advisory fixture; digest/matrix smoke |
| False positives | alias-aware accepted-risk record with scope, reachability, owner and expiry; no raw CVSS-only suppress |
| Documentation | update cadence, outage/cache policy, triage playbook and current dependency baseline |
| Acceptance | 129/129 coordinates scanned; pgJDBC HIGH fixed; 0 BLOCK/5 expiring WARN; Build+11 Compatibility jobs green; bounded PRs/no auto-merge |
| Risks | deps.dev/NVD availability, BOM update coupling, digest multi-arch drift |
| Dependencies | SEC1 hardening before new Actions/bot config |
| Deferred | OWASP DC/Snyk only if evidence shows coverage gap; SAST to SEC3 |

## SEC3 — Java Static Analysis — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | detectar defects/security patterns útiles en bytecode productivo |
| Scope | SpotBugs + FindSecBugs on production modules; baseline triage; focused filters |
| Non-goals | CodeQL/Sonar/Semgrep stack, style duplication, mass source cleanup |
| Tools/cost | SpotBugs Maven + FindSecBugs, local OSS; €0, moderate |
| Affected | parent POM/pluginManagement, focused filters, CI/build docs; product source sólo para confirmed finding |
| Gates | new medium/high confidence production finding falla; SECURITY findings reciben explicit triage |
| Tests | unfiltered Java 25 scan with real FindSecBugs SQL findings; clean Java 17 gate; zero starter/benchmark/example reports; lifecycle timing |
| False positives | bug pattern + class/method + rationale/owner/expiry; package wildcards forbidden |
| Documentation | supported modules, detector versions, local command and suppression review |
| Acceptance | 7 modules, initial 6/6 findings triaged, final 0 untriaged; FindSecBugs active; Build/Release fail closed; Compatibility skips duplicate scans |
| Risks | framework-generated patterns, version/JDK sensitivity, hidden categories |
| Dependencies | SEC2 dependency baseline |
| Deferred | CodeQL future public; Semgrep only for demonstrated missing rule |

## SEC4 — SBOM and Dependency/License Integrity — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | bind exact release components/scopes/licenses to machine-readable evidence |
| Scope | CycloneDX JSON per published module; aggregate candidate evidence; license/scope policy; inventory comparison |
| Non-goals | provenance/signing activation, committed generated SBOM, vendor dashboard |
| Tools/cost | CycloneDX Maven + existing license/dependency audits; €0, fast/moderate |
| Affected | release profile/POM, release scripts, staging audit, docs |
| Gates | SBOM matches nine artifacts and resolved trees; no test/benchmark leak; unknown/incompatible production license blocks |
| Tests | schema validation, reproducibility/normalization, isolated JPA and JDBC consumers, attachment/classifier inspection |
| False positives | license exception includes exact coordinate/text/legal rationale/owner/expiry; no global license allow |
| Documentation | SBOM lifecycle, format/schema, publication/retention and consumer verification |
| Acceptance | 9 per-artifact + aggregate CycloneDX 1.6 JSON; 55 external production components; 0 unknown/0 BLOCK; no generated file in Git; self-hosted Build PASS and Compatibility 11/11 PASS |
| Risks | aggregate includes non-published modules, serial/timestamp nondeterminism, POM metadata errors |
| Dependencies | SEC2 inventory and SEC3 stable build |
| Deferred | signing/provenance to SEC5 |

## SEC5 — Release Signing, Inventory and Provenance Hardening — DONE (2026-08-24)

| Campo | Definición |
| --- | --- |
| Objective | repair release inventory drift and bind source, candidate, signatures and checksums |
| Scope | all nine publishable modules+parent; JPA/JDBC consumers; tag-signature policy; OpenPGP fingerprint/rotation/revocation; provenance feasibility recheck |
| Non-goals | Portal token, Actions signing secrets, tag, dispatch Release, upload/publish Central |
| Tools/cost | GnuPG 2.4.8, Maven GPG 3.2.8, checksum/inventory scripts and ephemeral fixture keys; €0, slow release-only |
| Affected | release scripts/workflow/profile/docs and artifact inventory |
| Gates | clean synchronized source SHA; expected fingerprint; 46 signed Central files + 3 signed evidence files; complete staging; unchanged payload hashes; no unexpected artifacts |
| Tests | 9-module file staging, unsigned/signed comparison, valid/missing/wrong/tampered/checksum/SNAPSHOT/benchmark fixtures, isolated JPA+JDBC consumers, no private key in artifacts/logs |
| False positives | reproducibility exception identifies file/bytes/cause/expiry; signatures/fingerprint cannot be suppressed |
| Documentation | owner key ceremony, offline backup/revocation, token rotation, activation checklist, provenance decision |
| Acceptance | real protected identity prepared, public fingerprint distributed, signed dry-run/gate PASS; release remains frozen and CI secret-free |
| Risks | mishandled key, plugin code executing with secrets, provenance leaking private repo name |
| Dependencies | SEC4 artifact/SBOM inventory |
| Deferred | GitHub attestations/Scorecard until plan/public visibility supports them |

## SEC6 — Vulnerability Response and Repository Governance — PARTIALLY DONE (2026-08-25)

| Campo | Definición |
| --- | --- |
| Objective | create a real confidential reporting and coordinated response process |
| Scope | supported versions, approved private channel, triage targets, GHSA/CVE/disclosure, maintainer access/MFA review, governance triggers |
| Non-goals | invent/publicar un email, promise commercial SLA, change visibility/plan, enable PVR if unavailable |
| Tools/cost | documentation/CODEOWNERS now; approved email alias or PVR still pending; €0 target |
| Affected | `SECURITY.md`, CONTRIBUTING, CODEOWNERS/issue guidance, release checklist, runbook and response templates |
| Gates | supported release forbidden without tested private channel and owner; critical incidents stop release and rotate affected credentials |
| Tests | end-to-end benign report drill, access/recovery test, advisory/patch tabletop without publication |
| False positives | reporter evidence kept private; duplicate/invalid reports recorded without public disclosure |
| Documentation | channel, scope, expectations, severity, credit/disclosure and EOL |
| Acceptance | PARTIAL: policy/runbook/governance implemented; no contact invented; tested external private channel still missing and blocks release |
| Risks | spam/privacy, lost mailbox/key, misleading response promise, single-maintainer availability |
| Dependencies | SEC5 release model; owner approval for channel |
| Deferred | enforced CODEOWNER reviews/rulesets until collaborators or entitlement change; PVR until repository is public |

SEC6 implementa supported versions, intake/triage/severity, GHSA/CVE/disclosure, respuesta para
dependencies/build chain/secrets/OpenPGP/runner/workstation/repository/artifacts, governance común
de excepciones, CODEOWNERS documental, checklist sensible, issue guidance y handoff REL1. La API
read-only confirma que PVR/advisories devuelven 404 mientras el repo siga privado y que
rulesets/protection requieren cambio de plan o visibilidad. Como no existe un alias privado aprobado
y probado, el estado honesto es `PARTIALLY DONE — PENDING OWNER ACTION`. Esa acción bloquea REL1,
pero no bloquea SEC7, SEC8 ni la evaluación técnica REL0.

## SEC7 — Continuous Security Validation & Operational Resilience — IMPLEMENTED (2026-08-25)

| Campo | Definición |
| --- | --- |
| Objective | compose existing controls into continuous, drift-aware and operationally resilient validation |
| Scope | canonical inventory, fast/full/release orchestration, scheduled workflow, expiry/tool/module/workflow drift, runner health and separate technical/REL1 preflights |
| Non-goals | new scanners, performance thresholds, release/publication |
| Tools/cost | SEC1–SEC6 tools; €0, bounded CI minutes |
| Affected | Build/Compatibility/security/Release workflows, scripts and compatibility evidence |
| Gates | fast PR lane; fresh scheduled SCA; complete release lane; mandatory job absence/failure is visible and not silently ignored |
| Tests | local fast/full commands, expiry fixtures, Docker/Testcontainers cleanup, remote Build/Compatibility/Security jobs and explicit REL1 blocked fixture |
| False positives | exact accepted risks/exclusions/license reviews retain owner and expiry; stale/expired entries fail |
| Documentation | [`continuous-security-validation.md`](../security/continuous-security-validation.md), timing budget, ownership, outage classification and preflight boundaries |
| Acceptance | implementation complete; final `DONE` requires local full plus remote Build, Compatibility 11/11 and manually dispatched Security PASS on the implementation SHA |
| Risks | rate limits, flaky databases, cache poisoning assumptions, CI fatigue |
| Dependencies | SEC1–SEC5 controls plus implemented SEC6 policy; SEC6 channel closure is non-blocking here and blocking only at REL1 |
| Deferred | paid/private-only GitHub features and future Java/Boot generation |

## SEC8 — Security Baseline Technical Closure

| Campo | Definición |
| --- | --- |
| Objective | audit the integrated baseline against SEC0 threats and decide production-evaluable readiness |
| Scope | full control/evidence audit, cost/noise review, incident/release dry run, docs consistency and future backlog |
| Non-goals | activate credentials/publication, add tools to improve a score, begin SEC9 automatically |
| Tools/cost | existing baseline only; €0 recurring target |
| Affected | security investigation/roadmap, release readiness/acceptance, SECURITY/README/CHANGELOG as evidence requires |
| Gates | no expired exception, secret, applicable release blocker, inventory gap or unsupported claim; all controls owned and reproducible |
| Tests | clean full reactor, docs/API, SCA/SAST/secrets/SBOM/license, double release build, isolated consumers and remote CI |
| False positives | audit every suppression for evidence/expiry; delete stale entries, never roll forward blindly |
| Documentation | final matrix, residual risks, operational cadence, exact validation and cost |
| Acceptance | threat-to-control traceability complete; baseline low-noise/maintainable; worktree clean and remote synchronized |
| Risks | checkbox closure/security theater, evidence becoming stale, publication mistaken as authorized |
| Dependencies | SEC7 green closure |
| Deferred | public-repo Scorecard/CodeQL/attestations, Boot 4, paid features and new threat-driven work |

## Dependencias del roadmap

```text
SEC0 investigation
  -> SEC1 secrets/workflows
    -> SEC2 dependencies/updates
      -> SEC3 SAST
        -> SEC4 SBOM/license integrity
          -> SEC5 release signing/inventory/provenance
            -> SEC6 response/governance
              -> SEC7 CI/compatibility closure
                -> SEC8 technical closure
```

SEC5 está cerrado con identidad OpenPGP local protegida, fingerprint público fijado, inventario
source-bound de 46 archivos Central y tres evidencias, comparación unsigned/signed y regresiones
fail-closed. El workflow remoto es candidate-only y no contiene clave, passphrase, token ni upload.
No se creó tag, no se ejecutó Release y no hubo upload/publicación. SEC6 está implementado salvo el
canal privado externo, que bloquea REL1. SEC7 puede cerrar técnicamente con ese estado pendiente;
tras su evidencia verde puede recomendarse **SEC8 — Security Baseline Technical Closure** sin
iniciarlo automáticamente.

## Mantenimiento PRE-SEC6 — DONE (2026-08-25)

Las seis PR iniciales de Dependabot se revisaron de forma controlada y secuencial: cuatro se
fusionaron después de validación local completa y Build + Compatibility 11/11 en `main`; dos
actualizaciones duplicadas de Spotless se cerraron como superseded. El cierre conserva 0 alertas
Dependabot, 0 bloqueos OSV y los 5 riesgos aceptados no relacionados, sin auto-merge, cambios de
guards, tags, Release, publicación ni inicio de SEC6. La evidencia completa está en
[`docs/security/dependabot-review-2026-08.md`](../security/dependabot-review-2026-08.md).
