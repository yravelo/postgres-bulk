# ADR-023: Release and publishing policy

- **Estado:** ACCEPTED — RELEASE CONFIG READY, CREDENTIALS/PUBLICATION PENDING
- **Fecha:** 2026-08-19

## Contexto

Phase 16 debe producir un candidato `0.1.0` auditable sin publicarlo. Phase 16B fija el owner
`yravelo`, el repository privado, el Maven `groupId` `io.github.yravelo` y los packages Java
`io.ybr.postgresbulk`. Phase 16C crea el repository, publica `main` y valida Build/Compatibility;
security reporting, protección del branch y Central continúan pendientes de activación externa.
Phase 16D verifica el flujo actual de Central, crea el environment vacío y endurece el workflow,
pero no puede confirmar la cuenta/namespace ni configurar secrets reales.

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
- Considerar `SECURITY.md` provisional y bloquear publicación hasta disponer de canal privado real.
- Mantener el repository PRIVATE y no debilitar Build/Compatibility para obtener una señal verde.
- Mantener Benchmarks y Release candidate manuales; su mera visibilidad no autoriza ejecutarlos.
- Usar el plugin oficial Central `0.11.0` con `autoPublish=false` y publicación manual posterior.
- Exigir OpenPGP para cada POM/JAR desplegado y distribuir sólo la clave pública mediante un
  keyserver soportado por Central.
- Fijar las Actions del workflow release por commit, no persistir credenciales checkout y
  serializar uploads Central.

## Consecuencias

La release candidate es inspeccionable sin cambiar POMs a mano. Repository, remote, Issues y CI
remoto están verificados, pero la publicación sigue bloqueada aunque los gates técnicos sean
verdes. Private Vulnerability Reporting devolvió 404 y las branch rules del repository privado no
están disponibles con el plan actual; ninguna de esas limitaciones autoriza hacerlo público. Aún
deben resolverse Central, signing, environment, secrets, tag y autorización del workflow de
release. SBOM, attestations y JPMS quedan diferidos sin bloquear la ingeniería local de `0.1.0`;
Spring Data JDBC pertenece únicamente al roadmap posterior.

El environment `maven-central` existe, pero sin reglas ni secrets y no constituye todavía una
frontera protegida utilizable en el repository privado bajo el entitlement actual. La cuenta y el
namespace Central quedan UNKNOWN hasta que `yravelo` complete el login manual. Estas acciones y la
creación de la clave real pertenecen al owner; ninguna credencial debe pasar por Git, logs o chat.
