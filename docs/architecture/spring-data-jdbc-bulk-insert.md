# Bulk insert root-only con Spring Data JDBC

## Alcance J2 y exposición J4

J2 añade ejecución interna de bulk insert para la fila de una aggregate root. No es
`CrudRepository.save` ni persiste un aggregate graph. J4 expone ese mismo coordinador, sin copiar
lógica, mediante `PostgresBulkJdbcRepository<T>`.

```text
Spring Data JDBC repository proxy (desde J4)
    -> external repository fragment
    -> root entity Iterable
    -> DefaultSpringDataJdbcBulkOperations (package-private)
    -> SpringDataJdbcEntityMetadataResolver.resolveFor(row)
    -> JdbcOperations.execute(ConnectionCallback)
    -> PostgresBulkJdbcOperations
    -> PostgresBulkInserter / PostgresCopyExecutor
    -> PostgreSQL
```

Core y pgJDBC no cambian. El adapter depende productivamente de core, pgJDBC, Spring Data JDBC y
Spring JDBC. JPA, Hibernate y Boot siguen prohibidos.

## Transacción, conexión y ownership

Una operación no vacía exige transacción Spring activa y write. Antes de COPY se rechazan el
estado lógico read-only, una conexión física read-only y `autoCommit=true`. El callback de
`JdbcOperations` recibe el recurso asociado por Spring mediante `DataSourceUtils`; exactamente ese
objeto se entrega al engine.

El adapter y el engine no cierran, confirman, revierten ni reconfiguran la conexión. REQUIRED se
une a la transacción exterior y un rollback exterior revierte todos los COPY. REQUIRES_NEW usa el
scope físico independiente que suministra el transaction manager. J2 observa que
`JdbcTransactionManager` puede resolver NESTED con savepoint, pero no lo declara soportado ni crea
savepoints: su contrato se decidirá en J5.

La prueba de identidad obtiene `pg_backend_pid()` antes y después del bulk insert y compara ambos
con una columna default calculada por PostgreSQL durante COPY. Los tres valores coinciden.

## Algoritmo single-pass

1. Valida argumentos y obtiene `iterator()` exactamente una vez.
2. Si `hasNext()` es false, retorna `BulkWriteResult.empty()` sin transacción, metadata, conexión o
   SQL.
3. Lee la primera fila; null falla con posición 1.
4. Valida transacción, llama `resolveFor(first)` y prepara un único engine.
5. Entrega al engine un iterable one-shot que reproduce la primera fila y continúa con el iterator
   original.
6. Para cada fila siguiente valida null, resuelve metadata y compara su identidad con la inicial.
7. El engine existente aplica `BulkInsertOptions`, COPY batching y conteos.

El coste es O(N) y la memoria adicional O(1). No se pierde ni se duplica la primera fila.
`hasNext`/`next` y accessors/converters se ejecutan durante el consumo normal del engine.

## Política de IDs y metadata homogénea

Un ID Long/UUID assigned forma parte de las columnas COPY. Un ID que `IdValueSource` clasifica
generated selecciona la metadata sin ID; PostgreSQL ejecuta identity/default y la entidad Java no
se modifica.

Todas las filas deben devolver la misma metadata cacheada. Generated+assigned,
assigned+generated, otro runtime type o cualquier cambio de columnas se rechaza con posición
one-based y tipo, sin incluir la entidad ni sus valores. La detección puede ocurrir después de
iniciar COPY porque un pre-scan violaría single-pass/O(1). Por ello la transacción es obligatoria:
el owner debe rollback y ningún progreso queda confirmado.

## Mapping root-only y conversión

La operación usa directamente los accessors preparados por J1. COPY real prueba:

- scalars e IDs assigned Long/UUID;
- `Money -> BigDecimal`;
- enum default -> String y enum custom -> Integer;
- embedded y nested embedded, incluido parent null;
- `AggregateReference<Root, UUID> -> UUID` y FK real;
- schema/table/columns quoted;
- ID identity omitido sin sincronización.

No se reaplica ningún converter en el coordinador. Child entities/collections, sequence, version,
callbacks, auditing, events, defaults de columnas presentes y graph persistence siguen
unsupported según el resolver/contrato J1.

## Batching, resultados y fallos

`BulkInsertOptions` conserva default 1.000. PostgreSQL confirma input 1, batch exacto 1.000,
batch+1 y 2.500/1.000 con resultados 1, 1, 2 y 3 COPY respectivamente. El único resultado es
`BulkWriteResult`; input vacío es `(0,0)`.

Null item conserva `IllegalArgumentException` one-based. Fallos runtime de producer conservan
identidad; fallos de accessor/converter conservan contexto y causa según el resolver. Fallos COPY
conservan `SQLException`/SQLState y cleanup secundario suppressed según el engine existente. Tras
un error SQL, `25P02` permanece visible hasta rollback; el adapter no recupera ni reintenta.

Hikari y Testcontainers prueban success, fallo+rollback y operación posterior. Tras completar el
scope no queda conexión activa prestada y el pool puede reutilizarla.

## Compatibilidad multi-schema futura

J2 no introduce tenant context, ThreadLocal, `search_path`, `setSchema` ni cache tenant-specific.
El resolver conserva la tabla estructural por application context. Un futuro override per-operation
podrá combinar las mismas columnas con otro `TableName`; el coordinador no congela schema adicional
ni añade una key global por entity class.

## Non-goals

- lookup o materialización;
- Spring Boot autoconfiguration/starter;
- observability/Micrometer;
- sequences, callbacks, version o children;
- retries, compensación o generated-ID return;
- publicación, security baseline, multi-tenancy o matriz J7 completa.
