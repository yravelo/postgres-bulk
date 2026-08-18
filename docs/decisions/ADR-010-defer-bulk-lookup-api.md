# ADR-010: Diferir la API publica de bulk lookup

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Bulk lookup es una capacidad del MVP, pero su contrato depende de como Phase 3 represente metadata, claves simples/compuestas y extraccion ordenada de componentes. Publicar ahora una firma obligaria a elegir esos elementos sin implementacion ni tests end-to-end. Al aceptar este aplazamiento, ADR-006 mantenía en estado PROPOSED la estrategia tecnica de tabla temporal + COPY + JOIN; Phase 7 la aceptó después con ADR-015.

## Alternativas evaluadas

1. **Key objects:** el consumidor usa tipos como `ProductKey`. Es type-safe y representa bien claves compuestas, pero todavia falta definir como se relacionan sus componentes ordenados con metadata neutral.
2. **Claves derivadas de entidades:** conserva la forma legacy, pero exige entidades parciales, confunde valores de busqueda con estado persistible y hace facil suministrar datos irrelevantes.
3. **Extractor o `BulkKey<T, K>` publico:** separa entidad y clave y puede ser type-safe, pero añade una abstraccion cuya responsabilidad se solapa con la metadata aun no diseñada.
4. **Clave definida por metadata y valores sin tipo:** centraliza el mapping, pero una API de `Object[]`, `Map` o nombres de columna pierde seguridad de tipos y es facil de usar mal.

## Decision

Diferir la firma publica de lookup hasta Phase 7, despues de validar el modelo neutral de Phase 3. Phase 2 no publica metodos, key objects, extractors, definitions ni resultados de lookup.

Los criterios que debe satisfacer la decision futura son:

- aceptar valores de clave, no entidades parciales;
- modelar claves simples y compuestas con tipos explicitos;
- definir orden de componentes sin strings de columnas en la llamada normal;
- resolver y probar nulls, duplicados y orden del resultado;
- no exponer tablas temporales, COPY, JDBC ni metadata Hibernate;
- mantener la instancia de operaciones ligada al tipo de entidad si se extiende `BulkOperations<T>`, o justificar un contrato separado.

El aplazamiento de la firma es una decisión aceptada. Phase 7 ha aceptado la estrategia
interna de ADR-006/015, pero no ha producido evidencia del consumidor Hibernate/Spring
que permita elegir una firma pública sin acoplar lifecycle JDBC, mapping de resultado o
adquisición transaccional. La API pública continúa diferida hasta Phase 9.

## Consecuencias

Phase 7 valida keys simples/compuestas, políticas relacionales y un callback interno
acotado, manteniendo ocho tipos públicos. Phase 8 puede producir metadata sin conocer el
motor. Phase 9 debe probar la misma conexión física y decidir si el callback mínimo se
eleva a SPI o si el adapter consume el resultado dentro de otro scope; sólo entonces se
revisará la firma pública.
