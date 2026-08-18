# Inventario de API publica

## Alcance

Este documento enumera toda la superficie publica del proyecto al cerrar Phase 8. Existen
exactamente **nueve tipos publicos**: ocho en `postgres-bulk-core` y uno en
`postgres-bulk-hibernate`, en los packages
provisionales `io.github.postgresbulk.core` y `io.github.postgresbulk.core.metadata`.
Phases 4–7 no añaden tipos públicos en `postgres-bulk-pgjdbc`. La forma conceptual de la API
core esta ACCEPTED por ADR-009/011, pero las coordenadas y el namespace siguen sujetos a
ADR-008 (PROPOSED) mientras el proyecto permanezca en `0.1.0-SNAPSHOT`.

Los cuatro tipos de operacion son API, los cuatro descriptores de metadata son public SPI
para productores/consumidores y el resolver Hibernate es API de adapter. No existe un SPI
de ejecucion. Los archivos `package-info.java` documentan packages y no constituyen tipos
publicos.

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

## `TableName`

- **Purpose:** identidad fisica neutral con schema opcional y tabla como componentes separados.
- **Visibility:** public SPI value.
- **Stability:** ACCEPTED por ADR-011; el namespace sigue provisional.
- **Important invariants:** componentes non-null/non-blank; ausencia de schema no usa string vacio; conserva texto exacto; no parsea, normaliza ni aplica quoting/reglas PostgreSQL; inmutable, thread-safe y con value semantics.

API exacta:

```java
public final class TableName {
    public static TableName of(String table);

    public static TableName of(String schema, String table);

    public Optional<String> schema();

    public String table();
}
```

## `ColumnMetadata<T>`

- **Purpose:** columna fisica y accessor prerresuelto que proyecta un valor logico desde `T`.
- **Visibility:** public SPI.
- **Stability:** ACCEPTED por ADR-011.
- **Important invariants:** nombre non-null/non-blank y exacto; tipo Java non-null, no `void` y primitivos normalizados a wrappers; accessor non-null, stateless/thread-safe y capaz de devolver null; source de lectura non-null. Tiene identity semantics porque contiene una function.

API exacta:

```java
public final class ColumnMetadata<T> {
    public static <T, V> ColumnMetadata<T> of(
        String columnName,
        Class<V> javaType,
        Function<? super T, ? extends V> accessor
    );

    public String columnName();

    public Class<?> javaType();

    public Object read(T source);
}
```

## `EntityMetadata<T>`

- **Purpose:** mapping final de un tipo logico a una tabla y sus columnas bulk-insertables ordenadas.
- **Visibility:** public SPI.
- **Stability:** ACCEPTED por ADR-011.
- **Important invariants:** tipo/tabla non-null; lista non-null, no vacia, sin nulls ni nombres fisicos duplicados exactos; defensive copy no modificable; el encounter order es el orden de fila. Puede proyectar varias columnas desde una propiedad/asociacion/valor logico y tiene identity semantics.

API exacta:

```java
public final class EntityMetadata<T> {
    public static <T> EntityMetadata<T> of(
        Class<T> javaType,
        TableName table,
        List<? extends ColumnMetadata<T>> insertColumns
    );

    public Class<T> javaType();

    public TableName table();

    public List<ColumnMetadata<T>> insertColumns();
}
```

## `BulkKeyMetadata<K>`

- **Purpose:** componentes ordenados que proyectan una key simple/compuesta a columnas fisicas de lookup futuro.
- **Visibility:** public SPI; no constituye API de operacion lookup.
- **Stability:** descriptor ACCEPTED por ADR-011; politicas y firma lookup siguen diferidas por ADR-010.
- **Important invariants:** tipo non-null; componentes non-null, no vacios, sin nulls ni nombres fisicos duplicados exactos; defensive copy no modificable y orden explicito. No implica constraint UNIQUE ni define duplicates/null/result ordering.

API exacta:

```java
public final class BulkKeyMetadata<K> {
    public static <K> BulkKeyMetadata<K> of(
        Class<K> javaType,
        List<? extends ColumnMetadata<K>> components
    );

    public Class<K> javaType();

    public List<ColumnMetadata<K>> components();
}
```

## Fuera de la API publica

Phase 8 no crea una operacion publica de lookup, encoding, CSV, COPY, execution, JDBC,
observabilidad o serialization. El resolver/cache Hibernate descrito abajo es la única
adición pública. Tampoco crea command
objects, builders, repositories, metadata de ID/lifecycle/nullability ni una jerarquia
generica de resultados.

El package `io.github.postgresbulk.pgjdbc.copy` contiene exclusivamente detalles
package-private: registro de encoders, representación NULL/texto, framing CSV, encoder de
fila/key, quoting, builders SQL, callbacks, executor pgJDBC y coordinadores bulk insert y
temporary-table lookup.
`PostgresBulkInserter<T>` recibe una conexión caller-owned; no implementa aún la fachada
porque la adquisición transaction-aware se probará en Phase 9.
El lookup recibe `BulkKeyMetadata<K>`, una conexión prestada y un callback interno; no
expone recursos JDBC y permanece interno porque el boundary de consumo/adquisición se
probará en Phase 9. `BulkEncodingException` y
`CopyExecutionException` son subtipos internos de la raíz pública `BulkException`; no se
compromete una API de transporte antes de tener una operación pública que la necesite.

## `HibernateEntityMetadataResolver`

- **Purpose:** traducir una clase entidad del metamodelo runtime Hibernate 6.6 al descriptor neutral `EntityMetadata<T>`.
- **Visibility:** public adapter API en `postgres-bulk-hibernate`.
- **Stability:** ACCEPTED por ADR-004/016 para Hibernate 6.6; namespace provisional por ADR-008.
- **Important invariants:** constructor ligado a un `EntityManagerFactory`; cache concurrente por instancia; no abre sesión; no filtra internals Hibernate; mappings unsupported fallan con `BulkException`.

API exacta:

```java
public final class HibernateEntityMetadataResolver {
    public HibernateEntityMetadataResolver(EntityManagerFactory entityManagerFactory);

    public <T> EntityMetadata<T> resolve(Class<T> entityType);
}
```

No se publica `BulkMetadataException`, configuración/override, key resolver ni tipos
Hibernate. El detalle completo está en `hibernate-metadata.md`.
