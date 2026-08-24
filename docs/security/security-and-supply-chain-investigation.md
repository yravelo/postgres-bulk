# Investigación de seguridad y supply chain

**Estado:** SEC0 `DONE` (2026-08-24). Este documento diseña controles; no activa scanners,
integraciones, secrets, publicación ni cambios de producto.

## Alcance y criterio

La unidad protegida es una librería Java/Maven multiproyecto que ejecuta COPY y SQL PostgreSQL,
se construye en GitHub Actions y prepara nueve artefactos Java publicables más el parent POM. El
repositorio es privado, tiene un solo maintainer, no tiene contributors externos conocidos y la
publicación `0.1.0` continúa congelada.

SEC0 revisó source, POMs, Wrapper, scripts, cuatro workflows, ejemplos, tests, benchmarks y el
flujo Central real. Las decisiones priorizan controles gratuitos, reproducibles y de bajo ruido.
Una puntuación, badge o cantidad de scanners no sustituye la triage humana.

No son objetivos de SEC0:

- activar tools o servicios;
- modificar POMs, workflows, Java o comportamiento de release;
- generar SBOM, clave OpenPGP, token, secret, tag o artifact público;
- prometer soporte, SLA o un canal privado que todavía no existe.

## Resumen ejecutivo

La postura actual evita varios fallos comunes: dependencias y plugins tienen versiones exactas,
no hay repositorios Maven adicionales, el Wrapper verifica Maven por SHA-256 y el upload Central
es manual, `autoPublish=false`, exact-commit/tag-gated y usa Actions fijadas por SHA. El source no
expone raw SQL público, serialización Java, class loading dinámico ni logging de filas o keys.

Los gaps prioritarios son:

1. Build, Compatibility y Benchmarks usan tags mutables de Actions; sólo Release usa SHA.
2. No hay scanner de secretos, SCA, SAST, SBOM ni política de excepciones activa.
3. Dependabot alerts está deshabilitado. Dependency Review, CodeQL, secret scanning nativo,
   artifact attestations y rulesets no están disponibles gratis en la configuración privada
   actual.
4. Los scripts de staging y reproducibilidad conservan el inventario pre-JDBC de seis módulos.
   No verifican los tres artifacts publicables Spring Data JDBC añadidos posteriormente. Esto no
   expone una release porque la publicación está congelada, pero debe bloquear cualquier release.
5. Las imágenes PostgreSQL usan patches exactos pero tags mutables, no manifest digests.
6. `SECURITY.md` describe honestamente que no existe canal privado, pero esa ausencia debe
   resolverse antes de una release soportada.

Baseline recomendada, a implementar por SEC1–SEC8: Gitleaks CLI, SHA pinning y hardening de
workflows; Dependabot más OSV-Scanner; SpotBugs con FindSecBugs; CycloneDX JSON; gates de
inventario/licencias/reproducibilidad/firma; y una política operativa de vulnerabilidades. Snyk,
CodeQL, Semgrep, Sonar, Renovate, Scorecard y Sigstore no forman la baseline actual.

## Threat model

| Amenaza | Entrada y activo afectado | Probabilidad / impacto | Controles actuales | Tratamiento |
| --- | --- | --- | --- | --- |
| Compromiso de cuenta/source | Cuenta del maintainer o commit malicioso cambia source/build/release | media / crítica | repo privado, único maintainer, SHA/tag gates | MFA/passkey externo; revisar workflows; SEC1/SEC5 |
| Dependency vulnerable o obsoleta | Directa/transitiva productiva, test, example o benchmark | media / alta | BOMs exactos, convergence, matriz | Dependabot + OSV, triage por scope/reachability |
| Update malicioso | PR automático o nueva versión legítima comprometida | baja-media / alta | revisión humana y tests; no auto-merge | PRs pequeños, changelog/diff, no auto-merge |
| Plugin Maven comprometido | Plugin/annotation processor ejecuta código en build o release | baja / crítica | versiones exactas, Central only | inventario, updates revisados, SCA del build chain |
| Action comprometida | Tag mutable o SHA upstream malicioso ejecuta en runner | media / alta | permisos read-only; Release por SHA | fijar toda Action por SHA en SEC1 |
| Exfiltración de secrets | Workflow o Maven malicioso lee Central/GPG secrets | baja ahora / crítica al activar | secrets no creados; sólo upload los referencia | revisar SHA, limitar step/job, rotación y revaluación |
| Commit accidental de secret | Token/key/password en working tree o history | media / alta | `.gitignore` y grep estrecho release-only | Gitleaks diff + history; revocar antes de limpiar |
| Artifact tampering/binary-source mismatch | Staging distinto al SHA aprobado o artifact reemplazado | baja / crítica | exact SHA/tag, OpenPGP previsto, SHA-256, doble build | inventario completo, tag firmado, SBOM/provenance |
| Dependency confusion/spoofing | Coordenada o repo alternativo resuelve artifact atacante | baja / alta | namespace verificado, Central only, internos versionados | prohibir repos extra/dynamic versions; inspeccionar POM |
| Workflow PR malicioso | Inputs/contexto de PR llegan al shell o a job con secret | baja ahora / alta | sin `pull_request_target`/`workflow_run`; normal CI sin secrets | env intermedio, validación, least privilege |
| Release no revisada | Owner dispara upload equivocado o tag mutable | baja / crítica | dispatch manual, actor/ref/SHA/tag/confirmation gates | tag firmado, checklist, Portal manual |
| License/compliance | Copyleft/unknown license productiva | baja / media-alta | Apache-2.0 y audit `failOnMissing` | gate final por scopes y excepción expirable |
| Docker image comprometida | Tag PostgreSQL cambia o registry sirve imagen maliciosa | baja-media / alta en CI | patches exactos | manifest digest en lanes de release/CI |
| Vulnerabilidad SQL | Identificador o valor altera SQL/COPY | baja / alta | metadata estructurada, quoting, CSV encoder | conservar APIs estructuradas y tests adversariales |
| DoS por volumen/callback | Iterable infinito/lento, lookup enorme o callback bloquea conexión | media / media | insert bounded por batch; streaming COPY | límites del caller, timeouts operativos y docs |

No se observó dependencia confusion activa, secret real, deserialización insegura ni API raw SQL.
La ausencia de evidencia de compromiso no demuestra que dependencias o acciones estén libres de
vulnerabilidades; por eso se necesitan controles continuos.

## Trust boundaries

```text
developer workstation
  -> Git repository / GitHub account
    -> GitHub-hosted runner
      -> Maven Central + third-party Action repositories
      -> Docker registry -> PostgreSQL Testcontainers image
      -> candidate artifacts -> Central Publisher Portal
        -> consumer application -> consumer database
```

| Frontera | Código/datos no confiables que puede introducir | Confianza requerida |
| --- | --- | --- |
| Workstation → Git | source, POM, workflow, key o secret accidental | maintainer, OS, Git client, review y MFA |
| GitHub → runner | commit, event metadata, workflow y cache | GitHub control plane y workflow aprobado |
| Action repo → runner | JavaScript/container de cada Action | SHA revisado; un tag no es inmutable |
| Maven repo → build | dependencies, plugins y processors ejecutables | TLS, Central, coordenada+versión exactas |
| Registry → Testcontainers | imagen y entrypoint con ejecución local/CI | tag exacto más digest verificado |
| Runner → Central | bundle, token, private key/passphrase | candidate/tag exactos y secrets aislados |
| Library → consumer DB | identifiers, rows, keys y callback de aplicación | caller autoriza target/datos y posee transacción |

El runner ejecuta código de Maven, Actions, shell e imagen; es una frontera de ejecución, no sólo
un compilador. El consumer y sus callbacks están fuera de la autoridad de la librería.

## Asset inventory

| Activo | Sensibilidad | Propietario / protección esperada |
| --- | --- | --- |
| Source, history y API baseline | alta integridad | GitHub owner; review y CI |
| Cuenta GitHub y repository | crítica | maintainer; MFA/passkey y recovery seguros |
| Workflows, scripts y POMs | crítica | cambios revisados como código productivo |
| Repository secrets | crítica/confidencial | GitHub Repository Secrets; nunca logs/artifacts |
| Central token | crítica/confidencial | token expirable/rotado, sólo upload |
| OpenPGP private key/passphrase | crítica/confidencial | backup y revocation offline; material separado |
| Fingerprint/public key | alta integridad, pública | verificación explícita y keyserver |
| Release tag y candidate SHA | crítica integridad | tag firmado e inmutable por política |
| Maven coordinates/namespace | crítica identidad | `io.github.yravelo`, owner verificado |
| POM/JAR/source/Javadoc/SBOM/provenance | crítica integridad | firma, checksums, inventory y retención |
| Docker image references | alta integridad | patch+digest revisados |

## Auditoría de controles existentes

| Control | Estado | Evidencia y límite |
| --- | --- | --- |
| `.gitignore` para material sensible | ADEQUATE | cubre `.env`, key stores, GPG/PGP y secret dirs; no evita history |
| Audit de patrones sensibles | WEAK | grep sólo en Release, patrones estrechos, sin formatos/reporting/history |
| Permisos Actions | STRONG | los cuatro workflows declaran `contents: read` |
| Release manual y autorización | STRONG | owner/default branch, SemVer, full SHA, ancestor y confirmation |
| Tag/candidate separation | STRONG | upload depende de candidate y verifica `v<version>` al SHA exacto |
| `autoPublish=false` | STRONG | upload y publicación Portal siguen separados |
| Actions Release por SHA | STRONG | checkout/setup-java/upload-artifact fijadas y checkout sin credentials |
| Actions restantes por SHA | WEAK | Build, Compatibility y Benchmarks usan `@v6`, `@v5`, `@v4` |
| Maven versions/repositories | STRONG | plugins/deps exactos; sin `pluginRepositories`; Central default |
| Wrapper | STRONG | only-script 3.3.4, Maven 3.9.16 HTTPS y `distributionSha256Sum` |
| `RequireReleaseDeps` | STRONG | bloquea parent/dependencies SNAPSHOT en profile release |
| Dependency convergence/scopes | STRONG | Enforcer y boundaries por módulo; tests/benchmarks aislados |
| License audit | ADEQUATE | nueve módulos, scopes compile/runtime y missing metadata falla; requiere policy copyleft |
| Reproducible build | WEAK | dos builds idénticos, pero sólo inventario histórico de seis módulos |
| Staging/SHA-256 | WEAK | inspecciona seis módulos; permite tres JDBC no inspeccionados |
| Sources/Javadocs/API baseline | STRONG | strict Javadocs y baseline cubren los artifacts actuales |
| External consumers | ADEQUATE | ejemplos JPA/JDBC existen; release dry-run sólo valida JPA |
| Dependabot alerts/updates | MISSING | API remota confirma alerts deshabilitado; no hay config de updates |
| Secret scanner / SCA / SAST | MISSING | ningún control especializado activo |
| SBOM / provenance | DEFERRED | ninguna evidencia generada o publicada |
| Rulesets/branch protection | DEFERRED | API remota devuelve 403 por plan/visibilidad actuales |
| Private vulnerability reporting | DEFERRED | `SECURITY.md` registra 404 y ausencia de canal real |

## Dependencias, updates y Snyk

### Comparación SCA

| Opción | Cobertura/frescura | CI, ruido y cache | Private/coste | Decisión |
| --- | --- | --- | --- | --- |
| Dependabot alerts/security updates | GitHub Advisory DB, Maven directo/transitivo cuando resoluble | asíncrono, PRs accionables; no es release gate autocontenido | disponible en private, €0; hoy disabled | BASELINE como alerta/update |
| OSV-Scanner v2 | OSV y transitive Maven vía deps.dev/native; test graph Maven aún limitado | CLI simple, JSON; red envía coordinates, cache/offline disponible | Apache-2.0, €0, cualquier repo | BASELINE scanner CI/scheduled/release |
| OWASP Dependency-Check | NVD/CPE y plugins; amplia historia CVE | primera carga 20+ min, API key/cache recomendadas, CPE false positives | Apache-2.0, €0; NVD key operativa | OPTIONAL fallback/cross-check |
| Snyk Open Source | DB propietaria, transitive, fix guidance y priorización | SaaS/CLI, cuenta, policy y cuota; puede subir metadata/source según modo | Free: 5 projects/200 Open Source tests por mes al corte | OPTIONAL, no baseline |

OSV es preferible a Dependency-Check para el primer gate por menor operación y matching por
package/version. No debe gatear sólo por un número: debe generar inventario de production scopes y
pasar por triage. Dependency-Check queda como segundo diagnóstico si OSV/Dependabot discrepan o se
necesita NVD/CPE; no se activa OSS Index/Sonatype Guide ni un API key en SEC0.

Snyk añade una base propietaria, priorización y experiencia de remediación, pero no cubre una
amenaza baseline que Dependabot+OSV+triage no cubran proporcionalmente aquí. Su free tier basta para
experimentos de un solo maintainer, no para un gate estable: tiene cuotas, cinco projects y
dependencia de vendor/cuenta. No se conecta; puede evaluarse on-demand con autorización.

### Automatización de updates

Recomendación: Dependabot, sin auto-merge, con PR limits y grupos pequeños:

- Maven semanal para parent y ejemplos; revisar BOMs como stack, no updates transitivos aislados;
- GitHub Actions semanal, conservando SHA completo y comentario con tag/version humana;
- Docker mensual para Compose/Testcontainers donde el ecosystem lo soporte;
- Maven Wrapper se actualiza deliberadamente con Wrapper oficial y nuevo checksum, no se asume que
  el update de `pom.xml` lo cubra;
- security updates inmediatamente después de habilitar dependency graph/alerts.

Renovate ofrece más grouping y soporte de managers, pero duplica Dependabot y aumenta operación o
dependencia de app. Es `NOT RECOMMENDED` mientras Dependabot satisfaga estos cuatro carriles.

Dependency Review Action no está disponible para este repositorio privado sin GitHub Code
Security/Advanced Security. La alternativa gratuita es un job OSV sobre cambios de POM más review
humana de `dependency:tree`, source/release notes y scopes. No se compra plan en SEC0.

## Secret scanning

| Opción | Fortalezas | Límites/ruido/coste | Decisión |
| --- | --- | --- | --- |
| Gitleaks CLI | working tree, commit range e history; config/fingerprint; MIT | regex/entropy, no verifica vigencia; full history cuesta más | BASELINE |
| GitHub secret scanning | detección nativa y push protection | private requiere Secret Protection no disponible actual | FUTURE/plan change |
| TruffleHog | más detectors y verificación de secrets | mayor coste/ruido; verification hace red con material candidato | OPTIONAL incident/manual |
| grep custom actual | cero dependencia | cobertura muy incompleta, sin history/baseline semántica | defensa adicional, no gate único |

Usar la CLI versionada y verificada evita depender de la licencia del Gitleaks Action. PR escanea el
rango; schedule/release escanea history completo. Un finding nunca se “arregla” sólo borrándolo:
primero se considera comprometido, se revoca/rota y luego se limpia. Test credentials locales se
permiten sólo mediante fingerprint estrecho, razón y expiry; no mediante allowlist global.

## SAST

La librería sí justifica un SAST pequeño porque construye SQL, usa JDBC/COPY, converters y
reflection framework indirecta. No justifica cinco motores.

| Opción | Fit y disponibilidad | Decisión |
| --- | --- | --- |
| SpotBugs + FindSecBugs | bytecode Java/Maven, local, LGPL, un mismo pass; detectors de injection/crypto/hardcoded secret | BASELINE |
| Semgrep CE | rápido y flexible, LGPL engine; rules tienen licencia propia y solapa detectors | OPTIONAL para una regla concreta futura |
| CodeQL | buen dataflow/SARIF | FUTURE PUBLIC REPO; private actual requiere GitHub Code Security |
| SonarQube Cloud/Community | dashboard/quality amplio; free private hasta 50k LOC al corte o self-host operativo | NOT RECOMMENDED baseline por solape/servicio |

SEC3 debe analizar `core`, `pgjdbc`, `hibernate`, ambos adapters Spring Data y ambas
autoconfiguraciones. Los starters code-free no aportan bytecode; examples pueden ejecutarse
report-only; benchmarks se excluyen del gate productivo. Configuración inicial: `effort=Max`,
confianza/threshold `medium`, report de todas las categorías SpotBugs y categoría SECURITY de
FindSecBugs. Tras una corrida de baseline limpia/triaged, fallan findings nuevos medium/high; una
exclusión debe identificar bug pattern, clase/método, razón, owner y expiry. No se acepta un XML
vacío que suprima paquetes completos.

## SBOM, provenance y artifact integrity

CycloneDX JSON es la baseline: tiene modelo de componentes/dependency graph, tooling Maven oficial
y consumo extendido en seguridad. SPDX exportado desde GitHub describe el dependency graph del
repo, pero no el artifact Maven exacto; sirve como vista adicional, no como SBOM de release.

Estrategia SEC4:

- generar JSON por cada uno de los nueve artifacts publicables con `projectType=library`, sin test;
- adjuntarlo con classifier `cyclonedx` sólo en release/install/deploy;
- generar además un aggregate no adjunto para inspección del candidate, excluyendo parent,
  benchmarks y examples según semántica documentada;
- no versionar outputs generados; conservarlos junto al candidate y publicar sólo tras validar que
  coordinates, scopes y serial numbers/timestamps sean reproducibles o explicados;
- verificar el SBOM contra el dependency tree y el inventario staged.

OpenPGP sigue siendo obligatorio en Central para POM/JAR y no es sustituido por Sigstore.
Checksums prueban integridad, no identidad: SHA-256 local debe compararse con el mismo artifact
firmado/staged; Central también genera checksums del bundle.

GitHub artifact attestations no está disponible para private repos en el plan actual (requiere
Enterprise Cloud). `slsa-github-generator` puede operar en private, pero publica el nombre del repo
en Rekor y su Maven builder sigue beta; añadirlo hoy amplía exposición y workflow. Provenance queda
`FUTURE PUBLIC REPO`. Cuando el repo sea público, preferir la attestation nativa de GitHub si encaja
con el bundle Central, o un generator SLSA aislado verificado. Sigstore/cosign es opcional para
artifacts fuera de Central y no duplica la firma OpenPGP sin una historia de verificación usable.

OpenSSF Scorecard ofrece señales útiles sobre pinning, permissions, updates, policy y releases,
pero su Action en private requiere GitHub Advanced Security; la CLI manual funcionaría con token
pero varias señales de review/publicación serían poco representativas para un repo privado de un
solo maintainer. Se difiere hasta publicación y nunca será release gate por score total.

## Auditoría de GitHub Actions

| Workflow | Triggers/secrets | Fortalezas | Findings | Estado |
| --- | --- | --- | --- | --- |
| Build | push main, PR; sin secrets | `contents: read`, baseline/examples/docs | checkout/setup-java por tags, credentials persistentes, apt install mutable, sin timeout/concurrency | ADEQUATE/WEAK |
| Compatibility | push main, PR; sin secrets | matrices fijas, least privilege | Actions por tags, credentials persistentes, cache repetida, sin timeout/concurrency | ADEQUATE/WEAK |
| Benchmarks | dispatch; sin secrets | choice input, read-only, timeout, artifact controlado | tags mutables; input interpolado en shell/name; checkout credentials; sin actor guard/concurrency | WEAK, impacto limitado |
| Release | dispatch; cuatro secrets sólo upload | SHA-pinned, `persist-credentials:false`, stable inputs, exact SHA/tag, candidate dependency, concurrency y manual Portal | `sudo apt` mutable; secrets visibles al Maven deploy/build chain; inventario de release desactualizado | STRONG auth / BLOCKED inventory |

No existen `pull_request_target`, `workflow_run`, self-hosted runners ni permissions write. No se
observó interpolación directa de PR title/body/ref al shell. Los `inputs` de Release entran por
`env` y se validan/quotan. Benchmarks debe hacer lo mismo aunque hoy sólo pueda dañar un runner
efímero read-only. Los caches aceleran dependencias, no se publican como artifacts; aun así un
resultado release debe reproducirse sin confiar en cache.

Política SEC1: toda Action externa, incluso `actions/*`, por full commit SHA con comentario de tag;
`persist-credentials:false` en todos los checkouts; inputs no confiables mediante `env`; permisos
por job; timeouts; concurrency no cancelable para release y cancelable por branch para CI. Revisar
la fuente/owner del SHA y dejar que Dependabot proponga updates sin auto-merge.

## Maven, Wrapper y Docker

El parent fija clean/resources/compiler/surefire/failsafe/jar/javadoc/install/deploy/enforcer,
Spotless, source, GPG, flatten y license. Shade está versionado en benchmarks; Central Publisher
está versionado en el profile; processors Boot/JMH tienen versión exacta. Los plugins Boot de los
examples reciben versión del parent oficial Boot. No hay ranges, `LATEST`, `RELEASE`, HTTP repos,
`pluginRepositories` ni system paths. El profile staged del ejemplo usa sólo el file repository
generado localmente. Resultado: `STRONG`, sujeto a scanning/update del propio build chain.

Wrapper `only-script` evita un JAR bootstrap, descarga Maven 3.9.16 sólo desde HTTPS Central y
verifica `distributionSha256Sum`; es `STRONG`. Cada update debe regenerar scripts con el plugin
oficial, revisar diff y checksum, y pasar doble build.

Testcontainers y Compose usan sólo `postgres:15.18-alpine`; compatibility overridea tags exactos
16.14, 17.10 y 18.4. Son mejores que `15`/`latest`, pero siguen mutables. SEC2 debe registrar
manifest-list digests para CI/release manteniendo el tag legible, con update mensual o ante CVE.
Ejemplos pueden conservar credentials explícitamente locales y desechables, pero deben advertir
que publican `5432` y no son configuración de producción.

## Source, SQL, privacy y disponibilidad

### SQL injection

El riesgo observado es bajo. `TableName` conserva componentes, exige target runtime qualified y
no acepta SQL; `PostgresIdentifierQuoter` rechaza NUL, encierra cada componente entre comillas y
duplica quotes. COPY columns, CTAS, JOIN y DROP se construyen sólo desde metadata estructurada.
Rows/keys viajan por el encoder COPY CSV, no se concatenan en SQL. Los temporary names son internos
y se quotean. No existe API pública raw SQL; el callback lookup recibe SQL interno read-only para
materializar dentro del scope. Conservar tests de identifiers adversariales es un gate.

### Logging y secretos de examples

El código productivo no declara logger ni imprime rows, keys, credentials, schema o SQL. Las
observations usan tags acotados `operation`/`outcome`; el throwable se entrega a Micrometer y su
render final depende del consumer, pero los mensajes propios omiten valores. Algunos mensajes
incluyen tipo Java, posición, batch o nombre físico de columna; son metadata, no row value. Schema
tenant no se etiqueta. Examples imprimen sólo counts.

Los passwords `postgres`/`postgres_bulk` están hardcoded en Compose/application como credenciales
de demo local y coinciden entre sí; no son secrets reales. Deben permanecer marcados local-only y
nunca reutilizarse. Tests obtienen credentials efímeras de Testcontainers. El benchmark pasa su
password efímero como system property al fork; no se registra, pero los process arguments son
visibles al mismo host: aceptable sólo en runner/workstation aislado.

### DoS y código caller-controlled

Insert consume el Iterable una vez, emite como máximo `batchSize` por COPY y usa buffer de 64 KiB;
no acumula todo el input. Un batch enorme aumenta unidad de fallo y tiempo de conexión. Lookup
streaming evita una lista de keys, pero carga todas en una temp table y materializa todos los rows
resultantes: cardinalidad enorme puede consumir disco/memoria, prolongar la transacción y agotar el
pool. Un Iterable infinito/lento o accessor/converter/callback hostil corre como código confiado del
consumer y puede bloquear, lanzar o asignar memoria; la librería preserva su excepción.

No se añaden límites arbitrarios. La aplicación debe validar cardinalidad/tamaño, elegir batch,
aplicar transaction/statement timeouts y dimensionar pool. La documentación/performance es el
control primario; una protección hard futura requiere evidencia y contrato API separado.

### Reflection/deserialization

No hay `ObjectInputStream`, Java serialization, expression/script engine, dynamic class loading,
`setAccessible`, process execution ni unsafe reflection en source productivo. Hibernate y Spring
Data proporcionan metadata/converters del runtime ya confiado; sus callbacks se invocan, no se
deserializan. Los usos de `Class.forName`/dynamic proxies detectados están en tests de aislamiento.

## License y dependency-scope isolation

Apache-2.0, POMs y root license son consistentes; no hay necesidad actual de NOTICE vacío. El
audit productivo cubre nueve módulos y falla metadata desconocida. Debe añadirse policy explícita:
unknown y copyleft incompatible en compile/runtime bloquean; test/benchmark se triagean sin
presentarlos como transitivos. Metadata declarada no sustituye revisión de texto/licencia para una
nueva dependencia relevante.

Los POMs y Enforcer confirman: Testcontainers/JUnit/Hikari son test-only; benchmarks/JMH/Actuator
del harness no se despliegan; Actuator es test-only en el starter JPA y no entra en JDBC; el starter
JDBC prohíbe Hibernate/JPA/Actuator. Esta separación es parte formal de la baseline porque reduce
la superficie de consumers. El gap de staging de los tres artifacts JDBC debe cerrarse antes de
reafirmar el inventario de release.

## Release, signing y repository integrity

Artifact OpenPGP, tag signing y commit signing son controles distintos:

- OpenPGP de cada POM/JAR es obligatorio para Central y `BLOCK` si falta o no verifica;
- un tag anotado firmado une source y release, recomendado como baseline antes de activar;
- firmar cada commit aporta menos valor con un solo maintainer y sin ruleset que lo exija; es
  `OPTIONAL`, no sustituto de review/CI.

Antes de generar una key: UID pública deliberada, algoritmo/tamaño vigente, expiry, fingerprint
registrado por canal independiente, revocation certificate y backup cifrado offline. Separar
private key y passphrase, rotar Portal token, revisar access y detener release ante pérdida. Nunca
subir secret material a artifacts, cache, logs o chat.

La autorización actual es proporcional para un owner privado: manual dispatch, actor/default
branch, full SHA on main, tag exacto, candidate gate, read-only token, upload serializado y Portal
manual. No es suficiente mientras el inventario/reproducibility siga en seis módulos. El release
también debe verificar firma del tag, fingerprint del artifact y los nueve artifacts/SBOMs.

Rulesets, required reviews y CODEOWNERS aportan poco enforcement hoy porque el plan privado devuelve
403 y una review independiente es imposible con un maintainer. `CODEOWNERS` puede documentar
ownership futuro, pero no simular separación de funciones. Revaluar al añadir write collaborators
o cambiar plan/visibilidad.

## Vulnerability response policy propuesta

Antes de una release soportada, SEC6 debe sustituir el texto provisional de `SECURITY.md` con:

1. versiones realmente soportadas y EOL;
2. canal privado confirmado y probado, sin publicar un email no aprobado;
3. información mínima: artifact/version, impacto, reproducción y contacto seguro;
4. triage privado, severidad, owner, accepted risk y fechas de revisión;
5. fix en todas las líneas soportadas, regression test y advisory coordinado;
6. GHSA/CVE cuando una versión publicada esté afectada y corresponda, con disclosure después de
   artifact disponible o mitigación acordada;
7. revocación/rotación inmediata para secretos o signing material comprometido.

Alternativa gratuita mientras el repo siga private: crear y probar un alias/cuenta de email
dedicada controlada por el maintainer, idealmente con clave pública para mensajes cifrados. No usar
el email Git de identidad sin autorización explícita. Al hacer el repo público, habilitar GitHub
Private Vulnerability Reporting y preferir draft Security Advisories. Hasta que exista uno de esos
canales, la política debe seguir diciendo honestamente que no hay canal confirmado y la release
soportada queda bloqueada.

## Severity, triage y excepciones

### Gates

`BLOCK`:

- secret confirmado o no triageado; debe revocarse aunque se elimine del último commit;
- critical/high aplicable en dependency production o vulnerabilidad explotada/alcanzable sin
  mitigación demostrada;
- compromiso plausible de build plugin, Action, image, Wrapper o signing chain;
- finding SAST high-confidence explotable en código productivo;
- Action mutable en Release, tag no firmado/no coincidente, artifact sin firma o fingerprint
  inesperado;
- SNAPSHOT/dynamic version/repository no aprobado, unknown/incompatible production license;
- inventario staged/SBOM/source/Javadoc incompleto, artifact inesperado, mismatch de checksum o
  reproducibilidad no explicada;
- scanner/gate obligatorio no ejecutado o DB manifiestamente stale en release.

`WARN`: medium production sin path demostrado; high en test/example/image no distribuida; stale
dependency; finding low-confidence; digest/update recomendado; Scorecard recommendation.

`INFORMATIONAL`: low CVE no aplicable, dependency sólo benchmark, quality finding sin impacto y
señales de mantenimiento.

La triage registra advisory/CVE/OSV/GHSA aliases, CVSS vector, EPSS/known exploitation cuando haya
datos, dependencia directa/transitiva, scope real, versión resuelta, path/reachability,
configuración/runtime aplicable, fix, compensating control y decisión. CVSS o EPSS solos nunca
deciden el gate. Una critical test-only puede no bloquear; una medium explotada en el release
builder sí puede bloquear.

### Accepted risk / suppression

Cada excepción versionada y revisable contiene:

```text
id; tool/finding; affected coordinate/path/scope; reason and evidence;
owner; decision date; expiry/review date; compensating control; removal condition
```

No se permiten wildcard packages, mass baseline, “false positive” sin evidencia ni expiry
indefinida. Expiry vencida falla el gate. Secret findings no se suprimen antes de confirmar
revocación; fingerprints de demo deben ser exactos. Tool-specific files apuntan al mismo registro
de riesgo para evitar decisiones contradictorias.

## CI placement y performance budget

| Frecuencia | Controles | Presupuesto |
| --- | --- | --- |
| Cada PR/build | Gitleaks rango, policy de Actions/Wrapper, SpotBugs+FindSecBugs; OSV si cambian POMs | fast/moderate, objetivo <5 min adicional |
| Push main / semanal | OSV production completo, full-history Gitleaks, update/digest review, Dependabot async | moderate; DB/red puede fallar como infra, no como suppress |
| Release-only | todos los anteriores frescos, license/scopes, nueve artifacts+SBOM, double build, SHA/OpenPGP/tag fingerprint, candidate consumer | slow permitido, reproducible y sin skip |
| Manual/incident | OWASP DC cross-check, TruffleHog sin verification por defecto, Scorecard CLI, Snyk trial | no gate hasta policy aprobada |

Gitleaks y policy checks son fast; CycloneDX fast; SpotBugs/FindSecBugs y OSV moderate; Dependency-
Check slow en cold DB; CodeQL/Sonar y full reproducibility slow. Un outage de OSV/NVD en PR puede
reintentarse, pero release requiere resultado fresco o una excepción de infraestructura explícita,
nunca `continue-on-error` silencioso.

## Tool overlap

| Threat/control | Dependabot | OSV/DC | Snyk | Gitleaks | SpotBugs/FSB | CodeQL | CycloneDX | Scorecard |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Known vulnerable dependency | primary alert/update | primary gate | duplicate+vendor DB | — | — | limited | inventory only | checks practice |
| Malicious/stale update | PR visibility | detects known CVE | advice | — | may see code symptom | code symptom | records version | update-tool signal |
| Secret | — | — | separate product | primary | hardcoded patterns secondary | queries | — | checks practice |
| Injection/code flaw | — | — | Code product | — | primary baseline | deeper future | — | SAST signal |
| Component inventory | graph | resolved packages | dashboard | — | — | — | primary | metadata signal |
| Workflow/repo hygiene | Actions updates | — | — | leaked secret | — | workflow queries limited | — | primary score |

Dependabot+OSV is complementary (continuous alerts/PRs plus reproducible gate). Snyk is redundant
for baseline. SpotBugs+FindSecBugs share one engine and are intentionally combined; adding
Semgrep+CodeQL+Sonar simultaneously sería ruido. CycloneDX no detecta CVEs y Scorecard no prueba
que el artifact sea seguro.

## Cost and classification matrix

| Control | License/service | Private repo / monetary cost | Operational cost | Clase |
| --- | --- | --- | --- | --- |
| Gitleaks CLI | MIT | sí / €0 | low | BASELINE |
| Dependabot alerts/updates | GitHub service | sí / €0 | low-medium PR triage | BASELINE |
| OSV-Scanner | Apache-2.0 | sí / €0 | medium red/triage | BASELINE |
| SpotBugs + FindSecBugs | LGPL-2.1/LGPL-3.0 | local / €0 | medium suppressions | BASELINE |
| CycloneDX Maven | Apache-2.0 | local / €0 | low | BASELINE |
| OpenPGP/GnuPG | GPL tooling/Central requirement | sí / €0 | high key lifecycle | BASELINE release |
| OWASP Dependency-Check | Apache-2.0 | sí / €0 | high DB/cache/noise | OPTIONAL |
| Snyk Free | proprietary SaaS | sí, quota / €0 today | account/vendor/policy | OPTIONAL |
| Semgrep CE | LGPL engine, separate rules license | local/private / €0 | rule/noise upkeep | OPTIONAL |
| SonarQube | free cloud tier ≤50k LOC or self-host | sí / €0 today | external service/high hosting | NOT RECOMMENDED |
| Renovate | OSS/self-host or app | posible / €0 software | duplicate bot upkeep | NOT RECOMMENDED |
| CodeQL | GitHub Code Security | no gratis en private actual | medium-high | FUTURE PUBLIC |
| Dependency Review | GitHub Code Security | no gratis en private actual | low | FUTURE/plan |
| Native secret scanning | GitHub Secret Protection | no gratis en private actual | low | FUTURE/plan |
| Artifact attestations | GitHub | private requiere Enterprise Cloud | medium | FUTURE PUBLIC |
| OpenSSF Scorecard | Apache-2.0 CLI/Action | Action private requiere GHAS | low-medium | FUTURE PUBLIC |
| SLSA generator / Sigstore | Apache-2.0 ecosystem | €0, Rekor revela repo name | medium-high | FUTURE PUBLIC |

Baseline monetaria: **€0 recurrente**. El coste real es tiempo de triage, updates y custodia de
signing material; debe presupuestarse aunque no aparezca en una factura.

## Fuentes primarias consultadas

- [GitHub secure use](https://docs.github.com/en/actions/reference/security/secure-use)
- [GitHub supply-chain feature availability](https://docs.github.com/en/code-security/concepts/supply-chain-security/supply-chain-security)
- [GitHub security features](https://docs.github.com/en/code-security/getting-started/github-security-features)
- [Dependency Review](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review)
- [Dependabot supported ecosystems](https://docs.github.com/en/code-security/reference/supply-chain-security/supported-ecosystems-and-repositories)
- [OWASP Dependency-Check](https://github.com/dependency-check/DependencyCheck/blob/main/README.md)
- [OSV-Scanner Maven support](https://google.github.io/osv-scanner/supported-languages-and-lockfiles/)
- [Snyk plans](https://snyk.io/plans/)
- [Gitleaks](https://github.com/gitleaks/gitleaks)
- [SpotBugs Maven + FindSecBugs](https://github.com/spotbugs/spotbugs/blob/master/docs/maven.rst)
- [CycloneDX Maven plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [OpenSSF Scorecard Action](https://github.com/ossf/scorecard-action/blob/main/README.md)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)
- [Central Maven Publisher plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [Central PGP requirement](https://central.sonatype.org/publish/requirements/gpg/)
- [SLSA build requirements](https://slsa.dev/spec/v1.2/build-requirements)
- [SLSA GitHub Maven builder](https://github.com/slsa-framework/slsa-github-generator/blob/main/internal/builders/maven/README.md)

## Open questions

1. ¿Qué alias/canal privado aprobará y probará el owner antes de la primera release?
2. ¿Se mantendrá el repo privado al publicar artifacts? Esto cambia PVR, CodeQL, Scorecard y
   attestations disponibles.
3. ¿Debe el SBOM per-module adjuntarse a Central en `0.1.0` o primero conservarse como candidate
   artifact? SEC4 debe verificar aceptación/UX real.
4. ¿Qué fingerprint/expiry/backup model aprobará el owner para OpenPGP?
5. ¿Qué manifest digests funcionan en runners amd64 y workstations arm64 sin degradar la matriz?
6. ¿OSV produce una baseline Maven suficientemente completa para BOM-managed plugins/dependencies,
   o SEC2 necesita un cross-check Dependency-Check focalizado?

## Resultado SEC0

Threats, boundaries, assets, controles, tools, costes, gates, triage, suppressions y placement
quedan diseñados. No se activó ningún control. La siguiente fase recomendada es **SEC1 — Secrets
and GitHub Actions Hardening**, definida en el roadmap de seguridad.
