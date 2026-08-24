# Multi-schema con Spring Data JDBC

## Estado y alcance

**MS5: DONE (2026-08-24).** El fragmento público Spring Data JDBC acepta un `TableName`
schema-qualified por operación para bulk insert y bulk lookup. Reutiliza sin cambios los motores
pgJDBC de MS2/MS3 y conserva metadata, conversiones, materialización y transacciones de J1–J5.

Esta capacidad no resuelve tenants, no selecciona datasource y no introduce configuración Boot.
La aplicación autoriza y transforma su contexto de negocio en un `TableName`; postgres-bulk sólo
recibe el destino físico ya decidido.

## API pública

`PostgresBulkJdbcRepository<T>` conserva sus tres operaciones históricas y añade:

```java
BulkWriteResult bulkInsert(
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
    BulkKeyMetadata<K> keyMetadata,
    TableName runtimeTarget
);
```

La forma corta es target-first igual que JPA/MS4. Así una llamada histórica
`bulkInsert(items, null)` continúa seleccionando el overload de `BulkInsertOptions`; las method
references tipadas tampoco son ambiguas. No aparece un segundo tipo de target ni una facade
target-bound.

## Metadata estructural y resolución

`SpringDataJdbcEntityMetadataResolver` sigue cacheando por clase, dentro de la instancia ligada al
`JdbcConverter`/mapping context. Cada entrada contiene tabla mapeada, columnas, paths, conversiones
y variantes assigned/generated ID. Schema runtime, `TableName`, SQL target-specific y tenant no
forman parte de la key ni del valor cacheado.

Para input no vacío el adapter propaga el target sin resolverlo y pgJDBC ejecuta una vez:

```java
metadata.table().resolveRuntimeTarget(runtimeTarget)
```

El no-op vacío no entra en pgJDBC; por eso el adapter aplica ese mismo contrato Java puro una vez,
sin adquirir conexión. De este modo un target inválido no queda oculto por input vacío y un target
válido continúa sin I/O.

La tabla runtime debe conservar la tabla mapeada. Un mapping sin schema permite schemas distintos;
un mapping con schema exige el mismo schema. Un target runtime sin schema se rechaza.

## Pipeline y conexión

```text
Spring Data JDBC repository proxy
    -> DefaultPostgresBulkJdbcOperations
    -> DefaultSpringDataJdbcBulkOperations
    -> JdbcOperations.execute(ConnectionCallback)
    -> transaction-bound Connection
    -> PostgresBulkJdbcOperations
    -> COPY o CTAS/COPY/JOIN/DROP con target qualified
```

El callback entrega exactamente la conexión asociada por Spring. El adapter no abre otra conexión,
no hace close/commit/rollback, no crea savepoints y no llama `setSchema`, `setAutoCommit`,
`setReadOnly` ni `setTransactionIsolation`. A y B pueden ejecutarse en la misma conexión porque
cada SQL nombra explícitamente su tabla.

## Insert, IDs y single-pass

El primer elemento fija la variante estructural assigned/generated y un wrapper one-shot continúa
con el iterator original. Cada fila posterior debe resolver a la misma identidad de metadata. No
hay pre-scan ni materialización: tiempo O(N), memoria adicional O(1) y un solo iterator.

- ID assigned: se incluye en COPY.
- ID generated: se omite y no se copia al objeto.
- mezcla assigned/generated: se rechaza y la transacción del caller revierte.
- sequence/callback ID y `@Version`: continúan unsupported.

El target no participa en ninguna de esas decisiones.

## Lookup y materialización

El target efectivo alimenta tanto CTAS como JOIN dentro de un `InvocationSql` local. La temporal
session-local conserva nombre aleatorio sin schema/tenant. El adapter usa el mismo
`EntityRowMapper<T>` y `JdbcConverter` configurados, ejecuta un único SELECT de materialización y
consume el resultado antes del DROP. No vuelve al nombre mapeado default y no genera N+1 para el
subset root-only.

Keys simples/compuestas, duplicados, missing, nulls, falta de orden y single-pass conservan el
contrato de J3. Insert A seguido de lookup B no ve filas de A.

## Transacciones

Los overloads conservan `REQUIRED`, read-write:

- llamada al proxy crea o participa en la transacción normal;
- A+B puede confirmar o revertir como una unidad;
- `REQUIRES_NEW` suspende el scope exterior mediante el manager;
- `NESTED` conserva el soporte condicionado de ADR-029 para managers JDBC sobre el mismo
  `DataSource`; postgres-bulk no posee el savepoint;
- read-only y delegate directo sin transacción continúan rechazados;
- PostgreSQL permanece en `25P02` tras fallo SQL hasta rollback del owner.

Input vacío sigue siendo no-op real antes de la conexión y de la precondición transaccional.

## Mapping y root-only

Se reutiliza el subset Spring Data JDBC existente: scalars, converter de value object, enum,
embedded simple/nested/null, records y `AggregateReference` escalar. El target sólo redirige la
tabla de la aggregate root. No habilita children, collections, maps, callbacks, auditing, events,
version, sequences ni persistencia de graphs.

## Identifiers, conflictos y errores

Schema, tabla, columnas y temporal siguen siendo componentes estructurados y quoted por separado.
Schemas/tablas con espacios y case explícito funcionan. No se acepta SQL target libre ni un
`schema.table` preconcatenado.

Los conflictos estáticos se resuelven por MS1 antes de COPY/CTAS/JOIN. PostgreSQL conserva
`3F000`, `42P01`, `42501` y otros SQLStates dentro de la causa existente. `BulkException`, fallo
primario, suppressed cleanup y runtime identity del mapper no se envuelven nuevamente. Los
mensajes propios no añaden identidades tenant; el servidor puede mencionar identifiers físicos en
su `SQLException` preservada.

## Concurrencia, pool y cleanup

El target vive sólo como argumento/variable local. El fragmento singleton no tiene
`currentTarget`, `currentSchema`, schema ni tenant mutable. El mismo proxy atiende A/B secuencial y
concurrentemente sin contaminación de cache.

SQL qualified evita estado A→B al reutilizar un backend pooled. La temporal se elimina mediante
DROP explícito y `ON COMMIT DROP`/rollback como defensa. Éxito, fallo, rollback y rollback NESTED
conservan los contratos de cleanup de J3/J5.

## Coexistencia y límites

Los fragments JPA y JDBC ofrecen prácticamente la misma ergonomía target-aware, con sus packages y
semánticas de store separados. Repositories distintos pueden coexistir; uno que hereda ambos
fragments continúa rechazado. No se promete atomicidad entre managers locales JPA/JDBC.

MS5 no modifica core, pgJDBC, Hibernate/JPA, autoconfiguration, starters, properties ni
observabilidad. Tampoco añade resolver ambiental, `TenantContext`, `ThreadLocal`, datasource
routing, security baseline, publicación o benchmarks.

## Handoff MS6

La siguiente fase puede validar composición Boot y coexistencia de starters manteniendo el target
como argumento explícito. No debe crear una property global de schema, un bean target mutable ni
seleccionar infraestructura ambigua.
