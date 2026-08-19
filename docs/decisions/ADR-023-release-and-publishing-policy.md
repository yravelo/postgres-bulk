# ADR-023: Release and publishing policy

- **Estado:** ACCEPTED — SECURE CREDENTIAL BOUNDARY READY, ACTIVATION PENDING
- **Fecha:** 2026-08-19

## Contexto

Phase 16 debe producir un candidato `0.1.0` auditable sin publicarlo. Phase 16B fija el owner
`yravelo`, el repository privado, el Maven `groupId` `io.github.yravelo` y los packages Java
`io.ybr.postgresbulk`. Phase 16C crea el repository, publica `main` y valida Build/Compatibility;
security reporting, protección del branch y Central continúan pendientes de activación externa.
Phase 16D verifica el flujo actual de Central, crea el environment vacío y endurece el workflow,
pero no puede confirmar la cuenta/namespace ni configurar secrets reales. En Phase 16E el owner
confirma en Maven Central Portal que `io.github.yravelo` está `VERIFIED`. El repository sigue
PRIVATE y su plan no ofrece environment secrets/protections utilizables, por lo que se necesita
una frontera explícita que no dependa de cambiar plan ni visibilidad.

## Decisión

- Usar `io.github.yravelo` como groupId definitivo y `io.ybr.postgresbulk` como raíz Java.
- Usar `${revision}` como fuente única: default `0.1.0-SNAPSHOT`, override `-Drevision=0.1.0`.
- Publicar seis JARs de librería y el parent POM de soporte; excluir benchmarks y examples.
- Usar Maven Central Publisher Portal con el plugin oficial, nunca endpoints legacy.
- Separar `release` (sources, Javadocs y anti-SNAPSHOT) de `central-publish` (GPG y upload).
- Exigir tests completos; `skipTests` no forma parte del proceso de release.
- Mantener el build normal libre de claves, tokens y firma.
- Realizar staging file-repository, consumer aislado, inspección, SHA-256 y comparación de dos
  builds antes de solicitar publicación.
- Requerir autorización manual, tag exacto, credenciales protegidas y revisión manual del Portal.
- No publicar dependencias SNAPSHOT.
- Considerar `SECURITY.md` provisional y mantener el canal privado como mejora diferida
  non-blocking mientras el repository siga privado y sin release soportada.
- Mantener el repository PRIVATE y no debilitar Build/Compatibility para obtener una señal verde.
- Mantener Benchmarks y Release candidate manuales; su mera visibilidad no autoriza ejecutarlos.
- Usar el plugin oficial Central `0.11.0` con `autoPublish=false` y publicación manual posterior.
- Exigir OpenPGP para cada POM/JAR desplegado y distribuir sólo la clave pública mediante un
  keyserver soportado por Central.
- Fijar las Actions del workflow release por commit, no persistir credenciales checkout y
  serializar uploads Central.
- Guardar los cuatro valores de activación como GitHub Actions **Repository Secrets**. Esta
  decisión es explícita para el threat model actual; no se atribuyen a esos secrets propiedades de
  aprobación o aislamiento por job que GitHub no ofrece.
- Ejecutar release sólo mediante `workflow_dispatch` desde el workflow de `main`, por el owner
  `yravelo`, con stable SemVer, SHA completo de 40 caracteres, intención booleana y confirmación
  literal. Los inputs llegan al shell mediante variables de entorno validadas y quoted.
- Exigir que el SHA candidato pertenezca a `origin/main`. Para upload, exigir además que el tag
  exacto `v<version>` resuelva al mismo SHA aprobado por el job `candidate`.
- Mantener `central-upload` dependiente de `candidate`, con `contents: read`, concurrency sin
  cancelación y las únicas cuatro referencias `secrets.*` del workflow.
- Dejar el environment remoto `maven-central` como marcador inerte y no referenciarlo desde el
  workflow mientras no aporte secrets ni protection rules. No es un control de acceso.

## Consecuencias

La release candidate es inspeccionable sin cambiar POMs a mano. Repository, remote, Issues y CI
remoto están verificados, pero la publicación sigue bloqueada aunque los gates técnicos sean
verdes. Private Vulnerability Reporting devolvió 404 y las branch rules del repository privado no
están disponibles con el plan actual; ninguna de esas limitaciones autoriza hacerlo público. El
namespace y la estrategia de secrets están resueltos; quedan la activación de token/signing,
secret values, tag y autorizaciones de upload/publicación. SBOM, attestations, canal privado y JPMS
quedan diferidos sin bloquear el cierre técnico de `0.1.0`; Spring Data JDBC pertenece únicamente
al roadmap posterior.

El environment `maven-central` existe, pero sin reglas ni secrets y no constituye una frontera
protegida utilizable bajo el entitlement actual. Se conserva para no borrar historial/configuración
remota, pero el workflow ya no lo usa. El namespace Central queda `VERIFIED` por confirmación
explícita del owner; no se guardan screenshots ni datos de sesión. La creación de la clave y del
Portal token pertenece al owner; ninguna credencial debe pasar por Git, logs o chat.

## Threat model y límite de confianza

`GPG_PRIVATE_KEY` y el Portal token son secretos de alto impacto: permiten firmar o presentar
artefactos como el proyecto. Hoy sólo existe un maintainer (`yravelo`), no hay contributors
externos conocidos, el repository es privado, release es manual y el CI normal no referencia esos
secret names. El riesgo dominante es comprometer la cuenta del maintainer o introducir en `main`
un workflow/código de build malicioso que exfiltre secretos durante un upload.

Repository Secrets cifra los valores y evita su exposición directa en configuración, pero GitHub
los hace accesibles a workflows del repository; cualquier usuario con write access debe tratarse
como capaz de usarlos. El masking de logs es defensa en profundidad, no una garantía para valores
transformados. Por ello se exige revisar `main` y el tag antes de activar, mantener un solo owner,
no usar `pull_request_target`, no ejecutar código de PR con secrets, pinning SHA y prohibición de
`set -x`, `printenv`, `env`, `echo $SECRET` o debug indiscriminado en upload. La decisión debe
revisarse si aparecen colaboradores, el repository se hace público, cambia el plan o GitHub ofrece
una protección efectiva adicional.

Fuentes oficiales: [secret types](https://docs.github.com/en/code-security/reference/secret-security/secret-types),
[using Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets),
[secrets reference](https://docs.github.com/en/actions/reference/security/secrets),
[secure use](https://docs.github.com/en/actions/reference/security/secure-use) y
[environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).

## Alternativas evaluadas

| Alternativa | Seguridad/exposición | Operación y auditoría | Decisión |
| --- | --- | --- | --- |
| Repository Secrets | Sin approval gate; un workflow malicioso con write access puede usarlos | Repetible, logs de Actions y compatible con repo privado actual | **Elegida**, con gates compensatorios |
| Environment Secrets | Mejor scope por job y potenciales protections | No utilizables en este repo privado/plan actual | Reconsiderar si cambia entitlement |
| Release local controlada | Mantiene la private key fuera de GitHub y reduce exposición cloud | Más estado manual, menor evidencia centralizada y mayor riesgo de drift del host | Fallback no activado; no es el camino normal |
| GitHub Pro/otro plan | Puede habilitar environment secrets para repo privado; reviewers siguen limitados según plan/visibilidad | Coste y cambio de plan no autorizados | Evaluado, no comprar |
| Repository público | Habilita features de environment según plan y mejora apertura | Cambia exposición y gobierno del source | No permitido en esta fase |

Para este único maintainer y releases infrecuentes, local es el mínimo de exposición para la clave,
pero Repository Secrets ofrece mejor reproducibilidad y evidencia sin añadir una segunda máquina
como frontera operativa. El riesgo residual se acepta de forma proporcional mientras sólo
`yravelo` tenga write access. Si ese supuesto cambia, detener activación y reevaluar antes de otro
upload.

## Lifecycle de credenciales

En `central-upload`, `actions/setup-java` recibe la private key directamente desde el secret, la
escribe temporalmente, la importa y elimina el archivo de importación; su post-step elimina la key
importada al terminar el job. Maven settings se genera bajo `RUNNER_TEMP`, contiene referencias a
variables de entorno y se elimina siempre después del deploy. Username, password y passphrase sólo
se inyectan en el step Maven. No se sube ningún artifact desde ese job y la key, keyring o settings
no forman parte del staging/bundle. Véase el
[lifecycle GPG oficial de setup-java](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md#gpg).

El upload crea una deployment en Central con `autoPublish=false`; la publicación pública exige una
acción manual posterior en Portal. Upload de GitHub y publicación son dos autorizaciones distintas.
