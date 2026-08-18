# ADR-009: Forma de la API publica del core

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 2 debe materializar un contrato pequeno para bulk insert sin filtrar conceptos de Spring Data, JPA, Hibernate, JDBC o COPY. La forma elegida condiciona batching, integracion futura, cache de metadata y compatibilidad binaria. Tambien debe distinguirse el contrato estable de los puertos que solo tendran consumidores en fases posteriores.

## Alternativas

### Forma de la operacion

1. **Repositorio:** `repository.bulkInsert(...)` es familiar en Spring Data, pero convertiría una ergonomia de adapter en el modelo de core.
2. **Operacion ligada a un tipo:** `BulkOperations<T>.insert(...)` representa una capacidad independiente, permite asociar una instancia con metadata de `T` y se adapta despues a un repository fragment.
3. **Metodo generico multi-tipo:** una instancia ejecuta `<T> insert(...)`; reduce instancias, pero obliga a resolver el tipo y metadata en cada llamada o a introducirlos como argumentos.
4. **Command object:** facilita agregar parametros, pero para items y una opcion introduce ceremonia y otro tipo publico sin valor inmediato.

### Entrada

1. **`Collection<? extends T>`:** ofrece tamano, pero exige una estructura materializada que batching no necesita.
2. **`Iterable<? extends T>`:** incluye collections y productores de una pasada, permite consumo acotado por batch y no exige acceso aleatorio.
3. **`Stream<? extends T>`:** expresa consumo lazy, pero añade ownership, cierre, consumo unico, paralelismo y propagacion de excepciones a la primera API.

### Options y resultado

1. Un `int` en cada metodo minimiza tipos, pero hace crecer overloads y permite pasar valores invalidos entre capas.
2. Un builder es extensible, pero excesivo para una sola propiedad.
3. Un record para options es conciso, pero fija su constructor canonico como API binaria; una clase final con factories controla mejor su evolucion.
4. `void` oculta el resultado; `long` sirve para filas, pero no expresa batches ni deja evolucionar el valor con claridad.
5. Un resultado jerarquico anticipa operaciones no existentes.

### Excepciones

1. Checked exceptions fuerzan plumbing a traves de adapters y no encajan con las excepciones runtime de Spring ni con rollback declarativo habitual.
2. Una jerarquia completa de configuration/metadata/mapping/execution anticipa fuentes de fallo que Phase 2 aun no implementa.
3. Una raiz unchecked comun permite una frontera estable de captura; subtipos se añaden solo cuando una fase implementa y puede probar el fallo correspondiente.

## Decision

- El core es **operation-centric** y publica `BulkOperations<T>`. La instancia queda ligada a un tipo logico; `insert` acepta `Iterable<? extends T>`.
- Se ofrecen `insert(items)` y `insert(items, options)`. El overload corto usa `BulkInsertOptions.defaults()`; no se introduce command object.
- `Iterable` se consume secuencialmente y no promete reutilizacion, streaming, paralelismo ni tamano conocido. `Stream` queda fuera de la API inicial.
- El contrato rechaza el iterable, options y elementos null con un error descriptivo. Un iterable vacio es un no-op y devuelve `BulkWriteResult.empty()` sin ejecutar batches.
- `batchSize` es una politica conceptual de particionado independiente del transporte y vive en `BulkInsertOptions`. Debe ser mayor que cero. Opciones tecnologicas (buffer, CSV, SQL o temporales) pertenecen a adapters.
- `BulkInsertOptions` es una clase final inmutable con factories `defaults()` y `ofBatchSize(int)`; no necesita builder. El default inicial es 1.000 filas.
- `BulkWriteResult` es un record inmutable con `affectedRows` y `batches`. Ambos proceden de una ejecucion completada; no incluye duracion, IDs generados ni datos de lifecycle. Sus invariantes impiden conteos negativos, batches sin filas, filas sin batches y mas batches que filas.
- Se publica solo `BulkException`, unchecked, con constructores que permiten conservar la causa. Los errores de argumentos/value objects usan `IllegalArgumentException`; los nulls de contrato usan `NullPointerException` descriptivo. Subtipos de metadata, mapping o ejecucion se difieren hasta que exista comportamiento real.
- Los cuatro tipos viven juntos en `io.github.postgresbulk.core`. El namespace sigue siendo provisional segun ADR-008; usarlo no cambia aquel ADR a ACCEPTED.
- Metadata, value encoding y execution no se materializan en Phase 2: metadata comienza en Phase 3; encoding y executors dependen de los mecanismos de fases posteriores.

## Consecuencias

Collections funcionan sin conversion y un iterable de una pasada puede procesarse con memoria acotada, aunque el contrato no ofrece una API de streaming. Las implementaciones deben validar mientras consumen sin iterar previamente todo el input; por ello un elemento null descubierto en un batch posterior puede ocurrir despues de trabajo anterior y la atomicidad dependera de la politica transaccional futura.

El resultado sirve a logica de aplicacion y diagnostico determinista sin mezclar tiempo de pared. La superficie publica de Phase 2 queda limitada a cuatro tipos. Las implementaciones de adapters pueden implementar `BulkOperations<T>` sin que core publique un SPI de ejecucion prematuro.
