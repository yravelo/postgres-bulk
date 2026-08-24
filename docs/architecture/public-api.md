# Inventario de API publica

## Alcance

Este documento enumera la superficie pública al cerrar MS1. Core añade el puerto
`EntityMetadataResolver`; pgJDBC expone una fachada caller-owned y su callback anidado; Spring
Data expone fragmento, resolver por persistence unit e implementación de infraestructura externa.
ADR-008 fija las coordenadas Maven `io.github.yravelo` y el namespace Java
`io.ybr.postgresbulk`. Phases 10–12 no añaden operaciones para invocación directa; Boot requiere
dos tipos públicos de infraestructura.

Los cuatro tipos de operacion son API, los cuatro descriptores de metadata son public SPI
para productores/consumidores y el resolver Hibernate es API de adapter. No existe un SPI
de ejecucion. Los archivos `package-info.java` documentan packages y no constituyen tipos
publicos.

J1 de la evolución Spring Data JDBC añade un único tipo público experimental al release line
todavía no publicado: `SpringDataJdbcEntityMetadataResolver`. J2/J3 añaden ejecución root-only
package-private y cero tipos públicos. J4 añade la primera API de operaciones JDBC:
`PostgresBulkJdbcRepository<T>`. MS5 añade tres overloads target-aware a ese fragmento sin tipos
nuevos. J6 añade sólo `PostgresBulkJdbcAutoConfiguration` como
infraestructura pública de framework; el starter JDBC no tiene clases y no cambia las firmas de
operaciones.

## `BulkOperations<T>`

- **Purpose:** fachada operation-centric ligada a un tipo logico; expresa bulk insert sin describir el mecanismo.
- **Visibility:** public API.
- **Stability:** ACCEPTED para Phase 2; namespace finalizado por ADR-008.
- **Important invariants:** acepta `Iterable<? extends T>` de una pasada; input vacio devuelve `BulkWriteResult.empty()` sin batches; iterable/options null y elementos null son invalidos; no promete IDs generados, callbacks ORM, estado managed, streaming ni paralelismo.

API exacta:

```java
public interface BulkOperations<T> {
    default BulkWriteResult bulkInsert(Iterable<? extends T> items);

    BulkWriteResult bulkInsert(
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
- **Stability:** ACCEPTED por ADR-011; la resolución runtime se cierra en ADR-031.
- **Important invariants:** componentes non-null/non-blank; ausencia de schema no usa string vacio; conserva texto exacto; no parsea, normaliza ni aplica quoting/reglas PostgreSQL; inmutable, thread-safe y con value semantics. Un target runtime debe estar calificado, conservar la tabla y respetar cualquier schema mapeado.

API exacta:

```java
public final class TableName {
    public static TableName of(String table);

    public static TableName of(String schema, String table);

    public Optional<String> schema();

    public String table();

    public TableName resolveRuntimeTarget(TableName runtimeTarget);
}
```

MS1 sólo publica la resolución neutral. No añade operaciones insert/lookup con target ni ejecuta
SQL dinámico. El camino sin target continúa usando `EntityMetadata.table()` directamente.

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

- **Purpose:** componentes ordenados que proyectan una key simple/compuesta a columnas físicas de lookup.
- **Visibility:** public SPI consumida por la fachada pgJDBC y el fragmento Spring Data.
- **Stability:** descriptor ACCEPTED por ADR-011; política y firma lookup cerradas por ADR-017.
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

## API añadida en Phase 9

- `EntityMetadataResolver`: puerto core para resolver `EntityMetadata<T>` por clase.
- `PostgresBulkJdbcOperations<T>`: fachada pgJDBC preparada sobre `Connection` caller-owned;
  publica `bulkInsert` y `findAllByBulkKey`, más `LookupResultMapper<R>` anidado para consumir el
  JOIN dentro del scope temporal.
- `PostgresBulkRepository<T, ID>`: fragmento opt-in con los dos overloads legacy `bulkInsert` y
  lookup `<K> List<T> findAllByBulkKey(Iterable<? extends K>, BulkKeyMetadata<K>)`; MS4 añade las
  variantes target-aware descritas debajo.
- `JpaEntityMetadataResolver`: puerto Spring/JPA que resuelve por `EntityManagerFactory`; su
  factory `caching` adapta resolvers core ligados a una persistence unit.
- `DefaultPostgresBulkOperations<T, ID>`: implementación pública por requisito de carga externa
  de Spring Data; es infraestructura proxyable y no se instancia directamente.

## Overloads low-level añadidos en MS2/MS3

`PostgresBulkJdbcOperations<T>` añade un único método target-aware para COPY insert:

```java
public BulkWriteResult bulkInsert(
    Connection connection,
    Iterable<? extends T> items,
    BulkInsertOptions options,
    TableName runtimeTarget
);
```

El target es completo, schema-qualified y operation-scoped. Las dos firmas anteriores permanecen
intactas. No se añade el overload corto con `TableName`, porque colisionaría por ambigüedad source
con llamadas que pasen `null` al overload existente de `BulkInsertOptions`.

MS3 añade el target equivalente a lookup low-level:

```java
public <K, R> R findAllByBulkKey(
    Connection connection,
    Iterable<? extends K> keys,
    BulkKeyMetadata<K> keyMetadata,
    R emptyResult,
    LookupResultMapper<R> query,
    TableName runtimeTarget
);
```

El overload histórico de cinco argumentos permanece intacto. El nuevo target se resuelve una vez,
es local a la llamada y alimenta CTAS/JOIN; no forma parte del resultado ni del callback.
`BulkOperations` no cambia. La propagación a Spring Data JPA se añade en MS4.

## Overloads Spring Data JPA añadidos en MS4

`PostgresBulkRepository<T, ID>` añade:

```java
public default BulkWriteResult bulkInsert(
    TableName runtimeTarget,
    Iterable<? extends T> items
);

public BulkWriteResult bulkInsert(
    Iterable<? extends T> items,
    BulkInsertOptions options,
    TableName runtimeTarget
);

public <K> List<T> findAllByBulkKey(
    Iterable<? extends K> keys,
    BulkKeyMetadata<K> keyMetadata,
    TableName runtimeTarget
);
```

El overload corto usa orden target-first para conservar inequívoca la llamada histórica
`bulkInsert(items, null)` y method references tipadas hacia el overload de options. Los overloads
existentes permanecen intactos; la release todavía no está publicada y su implementación externa
oficial incorpora simultáneamente los métodos abstractos nuevos.

Los tres métodos exigen un target completo por invocación, root-only y sin resolución tenant. La
implementación pública `DefaultPostgresBulkOperations` expone además los overloads abstractos de
insert completo y lookup por necesidad del mecanismo Spring Data. `TableName` no se añade a
`BulkOperations<T>`, caches, properties ni tipos nuevos.

## Infraestructura pública añadida en Phase 10

- `PostgresBulkAutoConfiguration`: clase de configuración descubierta por Spring Boot mediante
  `AutoConfiguration.imports`; no es una API de negocio ni debe importarse manualmente.
- `PostgresBulkProperties`: record de binding para `postgres-bulk.enabled` y el grupo
  `observability.enabled`; su record anidado `Observability` es infraestructura de configuration
  properties, no API de operaciones.

El número de nuevos tipos de API de operaciones es cero. La infraestructura pública crece en dos
tipos porque Boot necesita cargar la clase y exponer el objeto de propiedades como bean. No se
publican conditions propias, failure analyzers, customizers, ejecutores o tuning.

## Resolver público añadido en Spring Data JDBC J1

- `SpringDataJdbcEntityMetadataResolver`: adapter final y thread-safe por instancia. Se construye
  con el `JdbcConverter` y las mismas `CustomConversions` efectivas de la aplicación.
- `resolve(Class<T>)` implementa el puerto core y devuelve la variante estable que conserva el ID.
- `resolveFor(T)` usa `IdValueSource` por instancia; conserva IDs asignados u omite IDs marcados
  como generated por Spring Data. No propaga valores generados.
- Las dos variantes están cacheadas por clase dentro del resolver. El método per-instance permite
  a J2 validar una política homogénea en una sola pasada.

API exacta:

```java
public final class SpringDataJdbcEntityMetadataResolver
        implements EntityMetadataResolver {
    public SpringDataJdbcEntityMetadataResolver(
        JdbcConverter converter,
        CustomConversions conversions
    );

    public <T> EntityMetadata<T> resolve(Class<T> entityType);

    public <T> EntityMetadata<T> resolveFor(T entity);
}
```

## Fragmento público añadido en Spring Data JDBC J4 y ampliado en MS5

- `PostgresBulkJdbcRepository<T>`: fragmento opt-in específico de Spring Data JDBC. Extiende
  `BulkOperations<T>` y añade lookup tipado con la misma `BulkKeyMetadata<K>` core.
- Usa sólo `<T>` porque las operaciones no consumen el tipo de identifier. El `ID` permanece en el
  `CrudRepository<T, ID>` del consumidor.
- Mantiene un FQCN distinto del fragmento JPA para imports claros y coexistencia de artifacts.
- Su implementación externa permanece package-private y no forma parte de la API binaria.

API exacta:

```java
public interface PostgresBulkJdbcRepository<T> extends BulkOperations<T> {
    default BulkWriteResult bulkInsert(Iterable<? extends T> items);

    BulkWriteResult bulkInsert(
        Iterable<? extends T> items,
        BulkInsertOptions options
    );

    default BulkWriteResult bulkInsert(
        TableName runtimeTarget,
        Iterable<? extends T> items
    );

    BulkWriteResult bulkInsert(
        Iterable<? extends T> items,
        BulkInsertOptions options,
        TableName runtimeTarget
    );

    <K> List<T> findAllByBulkKey(
        Iterable<? extends K> keys,
        BulkKeyMetadata<K> keyMetadata
    );

    <K> List<T> findAllByBulkKey(
        Iterable<? extends K> keys,
        BulkKeyMetadata<K> keyMetadata,
        TableName runtimeTarget
    );
}
```

MS5 replica deliberadamente el shape JPA/MS4. La forma corta target-first evita ambigüedad con
`bulkInsert(items, null)`; los overloads completos de insert y lookup conservan items/keys primero.
El cambio es aditivo dentro de una línea todavía no publicada y no amplía `BulkOperations<T>`.

## Fuera de la API publica

No se exponen `PGConnection`, `CopyIn`, CSV, nombres de temporales, internals Hibernate ni
serialization. `PostgresBulkObservability` permanece package-private y no existe API pública de
custom tags/conventions. Tampoco existen command objects, metadata de
ID/lifecycle/nullability ni una jerarquía genérica de resultados.

Salvo la fachada `PostgresBulkJdbcOperations<T>`, el package
`io.ybr.postgresbulk.pgjdbc.copy` contiene detalles package-private: registro de encoders,
representación NULL/texto, framing CSV, encoder de fila/key, quoting, builders SQL, callbacks,
executor pgJDBC y coordinadores bulk insert y temporary-table lookup.
`PostgresBulkInserter<T>` y `TemporaryTableBulkLookup<K>` permanecen internos detrás de
`PostgresBulkJdbcOperations<T>`. `BulkEncodingException` y
`CopyExecutionException` son subtipos internos de la raíz pública `BulkException`; no se
compromete una API de transporte antes de tener una operación pública que la necesite.

En el adapter Spring Data JDBC, `DefaultSpringDataJdbcBulkOperations<T>`,
`DefaultPostgresBulkJdbcOperations<T>` y sus seams de test son package-private. Spring Data carga
la segunda por `spring.factories`, pero sólo `PostgresBulkJdbcRepository<T>` es API de aplicación.

## Infraestructura pública Boot JDBC añadida en J6

`PostgresBulkJdbcAutoConfiguration` es pública porque Boot debe cargarla desde
`AutoConfiguration.imports`; su constructor y bean factory method no son API invocable. No publica
properties types, executors, managers ni selectors. El artifact
`postgres-bulk-spring-boot-starter-data-jdbc` es dependency-only y aporta cero tipos binarios.

## `HibernateEntityMetadataResolver`

- **Purpose:** traducir una clase entidad del metamodelo runtime Hibernate 6.6 al descriptor neutral `EntityMetadata<T>`.
- **Visibility:** public adapter API en `postgres-bulk-hibernate`.
- **Stability:** ACCEPTED por ADR-004/016 para Hibernate 6.6; namespace finalizado por ADR-008.
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

## Auditoría de documentación en Phase 15

Phase 15 no añade ni cambia firmas públicas. Completa los contratos Javadoc de la fachada pgJDBC,
el fragmento Spring Data, la implementación de infraestructura y los resolvers. El build ejecuta
doclint con warnings como error y deja cero warnings. `ColumnMetadata.javaType()` queda descrito
explícitamente como tipo Java persistence-facing/relacional cuando lo produce el adapter Hibernate.

El example externo compila únicamente contra `postgres-bulk-spring-boot-starter`; no importa
internals, clases package-private ni módulos sibling directamente. El inventario reproducible se
imprime con `scripts/check-documentation.sh`.

## Auditoría Spring Data JDBC J7

J7 no añade tipos, métodos ni firmas públicas a los artifacts de la librería. El nuevo ejemplo
compila sólo contra `postgres-bulk-spring-boot-starter-data-jdbc` y las APIs públicas ya aceptadas:
`PostgresBulkJdbcRepository<T>`, `SpringDataJdbcEntityMetadataResolver`,
`PostgresBulkJdbcAutoConfiguration`, core metadata/options/results y Spring Data JDBC. Sus clases
de dominio y servicio pertenecen al artifact de ejemplo, no a la API de postgres-bulk. El baseline
binario de `0.1.0-SNAPSHOT` permanece sin cambios.
