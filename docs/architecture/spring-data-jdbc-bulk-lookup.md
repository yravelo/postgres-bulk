# Bulk lookup root-only con Spring Data JDBC

## Alcance J3

J3 añade lookup interno por `BulkKeyMetadata<K>` al coordinador package-private de Spring Data
JDBC. No publica fragmento de repository ni API adicional.

```text
Iterable<K> + BulkKeyMetadata<K>
    -> DefaultSpringDataJdbcBulkOperations
    -> JdbcOperations.execute(ConnectionCallback)
    -> PostgresBulkJdbcOperations.findAllByBulkKey
    -> TemporaryTableBulkLookup: CREATE TEMP -> COPY -> SELECT/JOIN
    -> EntityRowMapper<T> -> List<T>
    -> DROP TEMP
```

Core y pgJDBC permanecen sin cambios. J3 reutiliza sin duplicar `TemporaryTableBulkLookup`,
`BulkLookupSql`, el pipeline COPY y sus contratos de null, duplicates, cleanup y ownership.

## Transacción y conexión

Una entrada no vacía exige una transacción Spring activa y write. El coordinador rechaza el estado
lógico read-only antes de resolver metadata o adquirir conexión, y dentro del callback rechaza
`autoCommit=true` o una conexión física read-only. `JdbcOperations.execute(ConnectionCallback)`
entrega el recurso transaction-bound; ese mismo objeto atraviesa CREATE, COPY, SELECT,
materialización y DROP.

El adapter no cierra, confirma, revierte ni reconfigura la conexión. REQUIRED participa en el
scope exterior; REQUIRES_NEW usa el recurso independiente del transaction manager. PostgreSQL
confirma el mismo `pg_backend_pid()` para el caller y una fila materializada. NESTED funciona con
el savepoint de `JdbcTransactionManager` en la characterization J3, pero sigue sin ser promesa de
soporte: postgres-bulk no crea ni gestiona savepoints.

## Input y semántica relacional

El método recibe la misma `BulkKeyMetadata<K>` pública del core, tanto para claves simples como
compuestas. No infiere ID, nombres de métodos ni conversiones desde una entidad parcial.

- Se obtiene un solo iterator y se hace lookahead de una key.
- Empty retorna `List.of()` sin transacción, metadata, conexión o SQL.
- Las keys se transmiten one-shot sin `size()`, pre-scan o materialización intermedia.
- Duplicados de input no multiplican resultados por el `SELECT DISTINCT` existente.
- Duplicados de target sí devuelven todas las filas coincidentes.
- Missing keys no producen filas; key o componente null fallan con posición one-based.
- No existe garantía de orden; el resultado debe tratarse como conjunto relacional.

Una prueba con 2.503 entradas cubre el camino representativo grande. Otra usa claves compuestas y
una key de value object cuyo accessor produce el `BigDecimal` relacional explícito.

## Materialización

El adapter prepara el `RelationalPersistentEntity<T>` del mismo mapping context y construye el
`EntityRowMapper<T>` público con el `JdbcConverter` efectivo. Dentro del callback abre un único
`PreparedStatement`, recorre completamente el `ResultSet`, aplica `mapRow` y devuelve una copia
inmutable. Statement y result set se cierran antes de retornar, por lo que ningún recurso lazy
sobrevive al lifecycle de la tabla temporal.

La evidencia PostgreSQL cubre constructor/record immutable, enums default y custom, converters de
lectura `BigDecimal -> Money` e `Integer -> Priority`, embedded/nested embedded nullable y
`AggregateReference<Parent, UUID>`. También cubre schema/table/columns quoted, espacios y case
mixto. El plain mixed-case que J1 declaró no representable continúa unsupported.

El scope es sólo aggregate root. El resolver rechaza child entities, collections/maps, version y
otros mappings fuera del subset antes del engine. No se cargan relaciones, cascades, callbacks,
auditing ni eventos.

## Query count y N+1

El SQL de lookup proyecta `target_row.*`, preservando los labels físicos que usa
`EntityRowMapper`. Una conexión instrumentada cuenta exactamente un `prepareStatement(SELECT)`
durante la materialización de varias filas. El relation resolver de este subset root-only no emite
consultas laterales: el conteo total permanece en uno, no uno por fila.

## Cleanup y fallos

El engine intenta `DROP TABLE IF EXISTS` después de éxito o fallo; `ON COMMIT DROP`/rollback es la
defensa final. Tests comprueban cero tablas temporales tras llamadas normales y concurrentes, y
reutilización Hikari tras rollback.

Una excepción runtime del materializador conserva identidad. Una sentencia SELECT inválida
conserva `42P01`; el DROP posterior falla con `25P02` y queda suppressed sobre el fallo primario.
PostgreSQL sigue abortado hasta rollback y el adapter no intenta recuperación interna. Después del
rollback, una operación sana sobre el pool vuelve a materializar y no encuentra temporales.

## Interoperabilidad y concurrencia

Bulk insert y lookup usan el mismo coordinador y engine preparado por llamada. Tests ejecutan
insert -> lookup y lookup -> insert en una transacción. Dos lookups concurrentes usan scopes
físicos y nombres temporales independientes, obtienen resultados correctos y terminan sin estado
temporal compartido.

## Compatibilidad multi-schema futura

J3 no añade `TenantContext`, ThreadLocal, `search_path`, `setSchema` ni cache global. La tabla se
resuelve por operación desde metadata y las keys permanecen explícitas. Un futuro diseño
multi-schema puede seleccionar metadata/table por invocation sin cambiar el motor ni el
materializador.

## Unsupported y diferido

- fragment/API repository pública, Boot, starter y observability JDBC;
- children/aggregate graph loading y N+1 relation loading;
- custom row mapper público, streaming/lazy results y garantía de orden;
- derivación automática de keys o conversiones domain dentro de `BulkKeyMetadata`;
- multi-tenancy, publicación, security baseline y matriz de compatibilidad J7.
