# ADR-008: Coordenadas públicas del proyecto

- **Estado:** PROPOSED
- **Fecha:** 2026-08-18

## Contexto

Los artefactos necesitan un namespace no corporativo y publicable. No existe todavía una organización, dominio o cuenta GitHub verificada que demuestre ownership del namespace definitivo.

## Alternativas

1. **`io.github.postgresbulk`:** neutral y descriptivo; requiere que una organización/usuario `postgresbulk` exista y sea controlado antes de publicar.
2. **`io.github.<owner-verificado>`:** ownership sencillo mediante GitHub; liga el proyecto a una cuenta aún no indicada.
3. **`dev.postgresbulk` o dominio propio:** marca limpia; exige registrar/verificar el dominio.
4. **Namespace legacy:** ya existe en código de referencia, pero es corporativo y queda rechazado.

## Propuesta

Mantener provisionalmente:

- group/base package candidato: `io.github.postgresbulk`;
- artifacts: `postgres-bulk-*`;
- versión inicial: `0.1.0-SNAPSHOT`.

No se crearán paquetes Java hasta Phase 2. Antes de la primera publicación debe verificarse control del namespace o aprobar otro mediante actualización de este ADR. Ningún consumidor debe asumir estabilidad de coordenadas durante `0.x-SNAPSHOT`.

## Consecuencias

El reactor es neutral y no contiene `com.pepe`/`com.amiga`, pero las coordenadas siguen siendo una open issue de release. Cambiarlas antes de tener código/API implica poco coste; después de publicar será breaking.
