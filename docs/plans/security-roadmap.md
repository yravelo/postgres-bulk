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

## SEC1 — Secrets and GitHub Actions Hardening

| Campo | Definición |
| --- | --- |
| Objective | cerrar secret leaks y ejecución mutable/injectable en workflows |
| Scope | Gitleaks CLI; SHA pinning; checkout credentials; env inputs; permissions, timeout, concurrency y action policy |
| Non-goals | SCA, SAST, SBOM, keys/secrets reales, release/publication |
| Tools/cost | Gitleaks CLI y checks shell/YAML; €0, fast |
| Affected | cuatro workflows, scripts security, Gitleaks config/ignore, CONTRIBUTING/security docs |
| Gates | PR commit-range scan; main/release full-history scan; todas Actions full SHA con tag comment |
| Tests | known fake-positive/negative fixtures sin secret utilizable; malicious benchmark input; workflow syntax and remote CI |
| False positives | fingerprint exacto + reason/owner/expiry; revocation precede suppression |
| Documentation | local usage, incident steps, pin update procedure y matrix por workflow |
| Acceptance | cero secret findings untriaged; no mutable Actions; no direct untrusted shell context; least privilege preserved |
| Risks | history scan noise; stale SHAs; accidental display de finding |
| Dependencies | SEC0 |
| Deferred | Dependabot/OSV a SEC2; release inventory a SEC5 |

## SEC2 — Dependency Vulnerability and Update Management

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
| Acceptance | PR updates bounded/no auto-merge; scheduled and release scan reproducible; current production findings resolved/accepted |
| Risks | deps.dev/NVD availability, BOM update coupling, digest multi-arch drift |
| Dependencies | SEC1 hardening before new Actions/bot config |
| Deferred | OWASP DC/Snyk only if evidence shows coverage gap; SAST to SEC3 |

## SEC3 — Java Static Analysis

| Campo | Definición |
| --- | --- |
| Objective | detectar defects/security patterns útiles en bytecode productivo |
| Scope | SpotBugs + FindSecBugs on production modules; baseline triage; focused filters |
| Non-goals | CodeQL/Sonar/Semgrep stack, style duplication, mass source cleanup |
| Tools/cost | SpotBugs Maven + FindSecBugs, local OSS; €0, moderate |
| Affected | parent POM/pluginManagement, focused filters, CI/build docs; product source sólo para confirmed finding |
| Gates | new medium/high confidence production finding falla; SECURITY findings reciben explicit triage |
| Tests | analyzer runs on Java 17/21 bytecode; detector fixture; zero starter/benchmark false gate; Maven lifecycle timing |
| False positives | bug pattern + class/method + rationale/owner/expiry; package wildcards forbidden |
| Documentation | supported modules, detector versions, local command and suppression review |
| Acceptance | clean/triaged baseline, stable runtime budget and normal cause/test semantics preserved |
| Risks | framework-generated patterns, version/JDK sensitivity, hidden categories |
| Dependencies | SEC2 dependency baseline |
| Deferred | CodeQL future public; Semgrep only for demonstrated missing rule |

## SEC4 — SBOM and Dependency/License Integrity

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
| Acceptance | no generated file in Git; candidate artifact contains complete validated evidence at €0 |
| Risks | aggregate includes non-published modules, serial/timestamp nondeterminism, POM metadata errors |
| Dependencies | SEC2 inventory and SEC3 stable build |
| Deferred | signing/provenance to SEC5 |

## SEC5 — Release Signing, Inventory and Provenance Hardening

| Campo | Definición |
| --- | --- |
| Objective | repair release inventory drift and bind source, candidate, signatures and checksums |
| Scope | all nine publishable modules+parent; JPA/JDBC consumers; tag-signature policy; OpenPGP fingerprint/rotation/revocation; provenance feasibility recheck |
| Non-goals | generate real key/token/secret/tag, dispatch Release, upload/publish Central |
| Tools/cost | existing Maven/GPG/checksum/repro scripts; optional dry-run test key outside tracked state; €0, slow release-only |
| Affected | release scripts/workflow/profile/docs and artifact inventory |
| Gates | exact signed tag/SHA; expected fingerprint; signed POM/JAR/SBOM as policy defines; complete staging; double-build match; no unexpected artifacts |
| Tests | 9-module file staging, two clean builds, signature verify with disposable fixture, isolated JPA+JDBC consumers, no key in artifacts/logs |
| False positives | reproducibility exception identifies file/bytes/cause/expiry; signatures/fingerprint cannot be suppressed |
| Documentation | owner key ceremony, offline backup/revocation, token rotation, activation checklist, provenance decision |
| Acceptance | historical six-module assumptions removed; release remains frozen and secret-free until separate authorization |
| Risks | mishandled key, plugin code executing with secrets, provenance leaking private repo name |
| Dependencies | SEC4 artifact/SBOM inventory |
| Deferred | GitHub attestations/Scorecard until plan/public visibility supports them |

## SEC6 — Vulnerability Response and Repository Governance

| Campo | Definición |
| --- | --- |
| Objective | create a real confidential reporting and coordinated response process |
| Scope | supported versions, approved private channel, triage targets, GHSA/CVE/disclosure, maintainer access/MFA review, governance triggers |
| Non-goals | invent/publicar un email, promise commercial SLA, change visibility/plan, enable PVR if unavailable |
| Tools/cost | approved email alias/encryption or PVR when available; €0 target |
| Affected | `SECURITY.md`, CONTRIBUTING, release checklist and response templates |
| Gates | supported release forbidden without tested private channel and owner; critical incidents stop release and rotate affected credentials |
| Tests | end-to-end benign report drill, access/recovery test, advisory/patch tabletop without publication |
| False positives | reporter evidence kept private; duplicate/invalid reports recorded without public disclosure |
| Documentation | channel, scope, expectations, severity, credit/disclosure and EOL |
| Acceptance | honest tested instructions, no secret/contact invented, owner can execute triage/patch/release process |
| Risks | spam/privacy, lost mailbox/key, misleading response promise, single-maintainer availability |
| Dependencies | SEC5 release model; owner approval for channel |
| Deferred | CODEOWNERS/reviews/rulesets until collaborators or entitlement change |

## SEC7 — Security CI and Compatibility Closure

| Campo | Definición |
| --- | --- |
| Objective | compose controls into reliable PR/scheduled/release lanes across supported boundaries |
| Scope | job placement, cache/outage behavior, Java 17/21 and Maven compatibility, PostgreSQL digests, artifact retention, required evidence |
| Non-goals | new scanners, performance thresholds, release/publication |
| Tools/cost | SEC1–SEC6 tools; €0, bounded CI minutes |
| Affected | Build/Compatibility/security/Release workflows, scripts and compatibility evidence |
| Gates | fast PR lane; fresh scheduled SCA; complete release lane; mandatory job absence/failure is visible and not silently ignored |
| Tests | local commands plus remote Build/Compatibility/security jobs; outage/retry, cache-cold and no-secrets PR scenarios |
| False positives | one shared risk register feeds tool configs; expired exception fixture fails |
| Documentation | CI matrix, timing budget, ownership, troubleshooting and evidence run IDs |
| Acceptance | green representative matrix, no duplicated expensive scans, PR overhead within documented budget |
| Risks | rate limits, flaky databases, cache poisoning assumptions, CI fatigue |
| Dependencies | SEC1–SEC6 |
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

SEC1 es la única fase siguiente recomendada. Completar SEC0 no autoriza iniciar SEC1, crear
credentials ni descongelar `0.1.0`.
