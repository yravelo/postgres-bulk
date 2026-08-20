# Contrato de destino físico por operación

## Estado y alcance

**MS1: DONE (2026-08-20).** Esta fase hace representable y valida un destino físico runtime en
core. No añade overloads ejecutables, SQL dinámico, integración Hibernate/JPA o Spring Data JDBC,
auto-configuración Boot ni resolución de tenants. Esas capacidades siguen diferidas al roadmap.

## Contrato elegido

`TableName` continúa siendo la única representación neutral de un destino físico. El mapping
persistente conserva su `TableName` en `EntityMetadata.table()` y una aplicación puede preparar
otro `TableName` completo para una sola operación. La validación vive en un único método Java puro:

```java
TableName effectiveTarget = metadata.table().resolveRuntimeTarget(runtimeTarget);
```

El método devuelve el mismo objeto `runtimeTarget`; no crea metadata derivada, no modifica el
mapping y no retiene estado. La ausencia de target no se representa con `null`: el camino existente
sigue usando directamente `metadata.table()`. Así no se cambia ninguna firma ejecutable antes de
que MS2 implemente el consumo real del target.

## Matriz de resolución

| Mapping | Target runtime | Resultado |
| --- | --- | --- |
| `product` | ausente | `product`, por el camino legacy sin invocar el resolver |
| `product` | `tenant_a.product` | `tenant_a.product` |
| `product` | `tenant_a.product_archive` | rechazo: cambia la tabla mapeada |
| `public.product` | ausente | `public.product`, por el camino legacy |
| `public.product` | `public.product` | `public.product` |
| `public.product` | `tenant_a.product` | rechazo: contradice el schema mapeado |
| `public.product` | `public.product_archive` | rechazo: cambia la tabla mapeada |
| cualquier mapping | `product` sin schema | rechazo: el target runtime no es completo |

El contrato compara ambos componentes. La tabla mapeada siempre es una restricción estructural. Un
schema mapeado también es una restricción; cuando el schema está ausente, los datos disponibles no
permiten distinguir entre una ausencia deliberada y el schema default del framework. La política
se basa únicamente en esa representación: ausencia permite seleccionar schema, presencia exige
igualdad. No se inventan anotaciones, flags ni inferencias del adapter.

Los componentes conservan case, espacios, puntos y quotes tal como se entregan. Esta fase sólo
compara valores; quoting y rechazo de NUL pertenecen a la futura construcción SQL de MS2/MS3.

## Forma de API

Se elige argumento explícito sobre vista inmutable. Las futuras operaciones usarán el mismo
`TableName` como argumento final para insert y lookup, por ejemplo conceptualmente:

```java
bulkInsert(items, options, target);
findAllByBulkKey(keys, keyMetadata, target);
```

Estas firmas son diseño congelado, no API publicada en MS1. La posición final conserva como
prefijo las firmas actuales y mantiene la misma forma conceptual en JPA y Spring Data JDBC:

```java
BulkWriteResult bulkInsert(Iterable<? extends T> items, TableName target);
BulkWriteResult bulkInsert(
    Iterable<? extends T> items,
    BulkInsertOptions options,
    TableName target
);
<K> List<T> findAllByBulkKey(
    Iterable<? extends K> keys,
    BulkKeyMetadata<K> keyMetadata,
    TableName target
);
```

Para `PostgresBulkJdbcOperations`, MS2/MS3 usarán la misma regla y conservarán `Connection` como
primer argumento:

```java
BulkWriteResult bulkInsert(
    Connection connection,
    Iterable<? extends T> items,
    TableName target
);
BulkWriteResult bulkInsert(
    Connection connection,
    Iterable<? extends T> items,
    BulkInsertOptions options,
    TableName target
);
<K, R> R findAllByBulkKey(
    Connection connection,
    Iterable<? extends K> keys,
    BulkKeyMetadata<K> keyMetadata,
    R emptyResult,
    LookupResultMapper<R> query,
    TableName target
);
```

Añadirlas ahora produciría métodos que no podrían cumplir su contrato sin adelantar SQL dinámico.
Una vista `forTarget(TableName)` añade otra fachada, puede retenerse accidentalmente y no reduce el
número de primitivas que los engines necesitan. Tampoco se introduce un resolver ambiental,
`ThreadLocal` ni estado mutable.

`BulkOperations<T>` no crece en MS1. Añadir un método abstracto rompería implementaciones; un
`default` no puede honrar un target que la implementación desconoce. Las fases de integración
decidirán si la capacidad madura merece un subcontrato core implementable, sin alterar esta
primitiva de resolución.

## Ubicación y límites

La resolución pertenece a core porque expresa compatibilidad entre dos valores físicos neutrales y
debe ser idéntica para pgJDBC, JPA y Spring Data JDBC. `TableName` ya contenía todos los datos e
invariantes necesarios; crear `BulkTarget`, `PhysicalTarget` o `TenantTarget` duplicaría el modelo.

El resolver no:

- construye ni cita SQL;
- recibe `Connection` ni consulta catálogo;
- conoce tenants, autenticación, routing o datasource;
- altera `EntityMetadata`, columnas, accessors, encoders o keys de cache;
- modifica `search_path`, schema de conexión, transacciones u observabilidad.

La aplicación sigue siendo responsable de convertir una identidad autorizada en un `TableName`.
Que un identifier sea válido no implica que el caller esté autorizado a usarlo.

## Metadata y caches

`EntityMetadata`, `ColumnMetadata`, accessors, conversiones, variantes de ID y encoders continúan
siendo estructura reusable. Resolver A y después B no crea una copia de metadata ni cambia la
identidad de sus elementos. Las keys Hibernate y Spring Data JDBC siguen siendo contexto/resolver
más clase de entidad; schema, target y tenant no participan. MS1 no introduce ningún
`Map<TableName, ...>` ni otra cache target-keyed, por lo que la cardinalidad de schemas no hace
crecer el heap de la librería.

## Errores

La ausencia se expresa con el overload legacy, nunca pasando `null`. Un target `null` produce
`NullPointerException`; un target sin schema o incompatible produce `IllegalArgumentException`
antes de cualquier futura llamada JDBC. Los mensajes propios describen el componente inválido sin
incluir los identifiers físicos ni una identidad de negocio. Blank/null de componentes conserva la
validación de las factories `TableName.of`.

## Compatibilidad y concurrencia

Las llamadas existentes permanecen intactas porque no cambió `BulkOperations` ni ninguna fachada o
interfaz Spring. El cambio binario es aditivo: un método nuevo en una clase final ya existente.
Factories, igualdad y serialización textual de `TableName` no cambian.

Las pruebas demuestran resolución A/B concurrente sobre el mismo mapping, preservación exacta de
identifiers, matriz de conflictos, null/unqualified y que `EntityMetadata` conserva la misma tabla
y las mismas columnas después de resolver destinos. No existe cache por schema o target.

## Continuación autorizable

MS2 podrá propagar el argumento explícito sólo en pgJDBC y construir COPY SQL local por invocación.
Hasta entonces, `resolveRuntimeTarget` es un contrato neutral probado pero ninguna operación bulk
acepta ni ejecuta un target runtime.
