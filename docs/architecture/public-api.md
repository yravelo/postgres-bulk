# Inventario de API publica de core

## Alcance

Este documento enumera toda la superficie publica creada en `postgres-bulk-core` al cerrar Phase 2. Existen exactamente **cuatro tipos publicos**, todos en el package provisional `io.github.postgresbulk.core`. Su forma conceptual esta ACCEPTED por ADR-009, pero las coordenadas y el namespace siguen sujetos a ADR-008 (PROPOSED) mientras el proyecto permanezca en `0.1.0-SNAPSHOT`.

No existe un SPI interno de ejecucion en esta fase. `package-info.java` documenta el package y no constituye un tipo publico.

## `BulkOperations<T>`

- **Purpose:** fachada operation-centric ligada a un tipo logico; expresa bulk insert sin describir el mecanismo.
- **Visibility:** public API.
- **Stability:** ACCEPTED para Phase 2; el namespace sigue provisional.
- **Important invariants:** acepta `Iterable<? extends T>` de una pasada; input vacio devuelve `BulkWriteResult.empty()` sin batches; iterable/options null y elementos null son invalidos; no promete IDs generados, callbacks ORM, estado managed, streaming ni paralelismo.

API exacta:

```java
public interface BulkOperations<T> {
    default BulkWriteResult insert(Iterable<? extends T> items);

    BulkWriteResult insert(
        Iterable<? extends T> items,
        BulkInsertOptions options
    );
}
```

## `BulkInsertOptions`

- **Purpose:** politica inmutable y neutral de particionado para bulk insert.
- **Visibility:** public API.
- **Stability:** ACCEPTED; clase final con factories para evitar fijar un constructor extensible.
- **Important invariants:** `batchSize > 0`; default de 1.000; value semantics; thread-safe por inmutabilidad; no contiene opciones de CSV, driver, SQL ni tablas temporales.

API exacta:

```java
public final class BulkInsertOptions {
    public static BulkInsertOptions defaults();

    public static BulkInsertOptions ofBatchSize(int batchSize);

    public int batchSize();
}
```

## `BulkWriteResult`

- **Purpose:** conteos deterministas de una escritura completada.
- **Visibility:** public API.
- **Stability:** ACCEPTED como value record; no existe jerarquia de resultados.
- **Important invariants:** conteos no negativos; cero filas implica cero batches y viceversa; un batch completado contiene al menos una fila; `batches <= affectedRows`; thread-safe por inmutabilidad; no contiene duracion ni IDs.

API exacta:

```java
public record BulkWriteResult(long affectedRows, int batches) {
    public static BulkWriteResult empty();
}
```

## `BulkException`

- **Purpose:** frontera unchecked comun para fallos propios de una operacion bulk y preservacion de causa.
- **Visibility:** public API.
- **Stability:** raiz ACCEPTED; subtipos se añaden solo junto con fallos concretos en fases posteriores.
- **Important invariants:** los errores de argumentos no se convierten en esta excepcion; adapters deben conservar la causa de infraestructura; no existe una jerarquia especulativa de metadata/mapping/execution.

API exacta:

```java
public class BulkException extends RuntimeException {
    public BulkException(String message);

    public BulkException(String message, Throwable cause);
}
```

## Fuera de la API publica

Phase 2 no crea tipos publicos de lookup, metadata, keys, encoding, CSV, COPY, execution, JDBC, ORM, observabilidad o serialization. Tampoco crea command objects, builders, repositories ni una jerarquia generica de resultados.
