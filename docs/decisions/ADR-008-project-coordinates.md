# ADR-008: Coordenadas e identidad del proyecto

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18
- **Aceptada:** 2026-08-19

## Contexto

Los artefactos necesitan una identidad estable antes de la primera publicación. El namespace
usado durante el desarrollo inicial era provisional y no demostraba ownership. Phase 16B aporta la
decisión aprobada para el proyecto, aunque el repositorio privado y el namespace de Central todavía
deban activarse externamente.

## Decisión

- Proyecto: `postgres-bulk`.
- Owner GitHub: `yravelo`.
- Repositorio previsto: `https://github.com/yravelo/postgres-bulk`.
- Visibilidad inicial prevista: privada; el repositorio todavía no existe.
- Maven `groupId`: `io.github.yravelo`.
- Raíz de packages Java: `io.ybr.postgresbulk`.
- Primera release candidata: `0.1.0`.

`Maven groupId != Java package root` es una decisión deliberada. Maven usa el namespace asociado al
owner GitHub para permitir su verificación en Central; Java usa el namespace estable elegido para
la API binaria. Ninguno debe inferirse automáticamente del otro.

## Consecuencias

- Los seis módulos publicables usan `io.github.yravelo:postgres-bulk-*`.
- La API, implementación, tests, example y recursos Spring usan `io.ybr.postgresbulk.*`.
- No existe una release pública con el namespace provisional anterior, por lo que la migración no
  rompe consumidores publicados.
- Crear el repositorio privado, configurar el remote y verificar `io.github.yravelo` en Central son
  prerrequisitos externos; no invalidan la decisión local de coordinates.
- El email de la configuración Git no forma parte de la metadata pública. La identidad de
  developer usa únicamente el owner aprobado y su perfil GitHub.

## Alternativas descartadas

- Mantener el namespace Maven provisional: no estaba asociado al owner aprobado.
- Usar el Maven `groupId` como package Java: acoplaría innecesariamente la API binaria al mecanismo
  de verificación del repositorio.
- Usar un dominio propio: no existe un dominio aprobado y verificable para esta release.
