# ADR-003: Encoding explícito y COPY CSV inicial

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

`Object.toString()` no define un formato estable. COPY CSV exige distinguir NULL de empty string y tratar delimiter, quote, CR, LF y UTF-8. TEXT y BINARY podrían ser valiosos después, pero no hay evidencia para implementarlos en v1.

## Alternativas

1. **`toString()` + escape:** poco código; incorrecto para null/empty, temporal, binario, locale y converters.
2. **Librería CSV genérica:** framing probado; puede no modelar exactamente COPY, añadir allocations y dificultar custom types.
3. **Encoders tipados + record writer PostgreSQL:** contrato verificable y extensible; exige suite exhaustiva.
4. **COPY binary desde el inicio:** rendimiento potencial mayor; protocolo, OIDs y tipos custom elevan mucho el alcance.

## Decisión

Implementar sólo CSV. Separar un registry de encoding de valores del writer de registros COPY CSV. El writer posee NULL, quoting, delimiter y UTF-8; un encoder nunca decide si una celda va entre comillas. Built-ins mínimos: strings, enteros, decimal, boolean, UUID, temporal acordado, enum y bytes. JSON/JSONB/arrays/custom types entran por registro explícito, no por fallback silencioso.

No se expone todavía `CopyFormat` en API pública. Una frontera interna pequeña permitirá reemplazar el writer si TEXT/BINARY se justifica.

## Validación para aceptar

Tests unitarios y contra PostgreSQL real para NULL, empty, coma, quote, CR, LF, CRLF, unicode/emoji, bytea, locale y temporal; además, fallo claro ante tipo sin encoder. Debe verificarse el valor persistido, no sólo la cadena emitida.

## Consecuencias

El registry y política de selección forman parte sensible de compatibilidad. Los `AttributeConverter` deben aplicarse antes de escoger encoder o entregar el tipo relacional correcto desde metadata. No habrá fallback global a `toString()`.

ADR-012 acepta el contrato interno de Phase 4 y ADR-013 su transporte. Phase 5 verificó
con Testcontainers y PostgreSQL 15.18 los valores persistidos de NULL, empty, caracteres
CSV, Unicode/emoji, numéricos, temporales, enum y `bytea`, además del boundary UTF-8 y los
fallos de protocolo/productor. Esa evidencia satisface el criterio de aceptación de este
ADR.
