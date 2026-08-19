# ADR-023: Release and publishing policy

- **Estado:** ACCEPTED — EXTERNAL ACTIVATION PENDING
- **Fecha:** 2026-08-19

## Contexto

Phase 16 debe producir un candidato `0.1.0` auditable sin publicar remotamente. Phase 16B fija el
owner `yravelo`, el repository privado previsto, el Maven `groupId` `io.github.yravelo` y los
packages Java `io.ybr.postgresbulk`. El repository aún no existe y no tiene `git remote`, por lo
que SCM, Issues, security reporting y Central continúan pendientes de activación externa.

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

## Consecuencias

La release candidate es inspeccionable sin cambiar POMs a mano ni tocar servicios remotos. ADR-008
y esta política quedan aceptados, pero la publicación sigue bloqueada aunque los gates técnicos
sean verdes. Deben crearse y verificarse repository/remote, Issues, security reporting, namespace
Central, signing, environment, secrets, tag y workflow remoto. SBOM, attestations y JPMS quedan
diferidos sin bloquear la ingeniería local de `0.1.0`; Spring Data JDBC pertenece únicamente al
roadmap posterior.
