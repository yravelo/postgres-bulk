# Multi-schema en Hibernate y Spring Data JPA

## Estado y alcance

**MS4: DONE (2026-08-24).** El fragmento Spring Data JPA acepta un `TableName` físico explícito
por invocación para insert y lookup. Reutiliza la metadata estructural de Hibernate y los overloads
pgJDBC aceptados en MS2/MS3. No configura multitenancy Hibernate, no resuelve tenants y no cambia
Boot ni Spring Data JDBC.

La aplicación sigue siendo responsable de traducir una identidad autorizada al target. Aceptar y
citar un identifier no concede permiso para usarlo.

## API pública

El camino default permanece intacto:

```java
repository.bulkInsert(rows);
repository.bulkInsert(rows, options);
repository.findAllByBulkKey(keys, keyMetadata);
```

El camino explícito JPA es:

```java
TableName target = TableName.of("tenant_a", "product");

repository.bulkInsert(target, rows);
repository.bulkInsert(rows, options, target);
repository.findAllByBulkKey(keys, keyMetadata, target);
```

El overload corto es target-first deliberadamente. Publicar
`bulkInsert(Iterable, TableName)` junto a `bulkInsert(Iterable, BulkInsertOptions)` haría ambigua
una llamada source existente como `bulkInsert(rows, null)`. La forma elegida conserva esa llamada,
method references tipadas y la ergonomía replicable en Spring Data JDBC durante MS5.

El target debe incluir schema, conservar el nombre de tabla mapeado y coincidir también con el
schema cuando `@Table(schema=...)` lo fija. No existe ausencia mediante `null`: se usa el método
legacy cuando no hay override.

## Metadata estructural y caches

`HibernateEntityMetadataResolver` no cambia. Continúa produciendo una sola
`EntityMetadata<T>` inmutable por clase y resolver/`EntityManagerFactory`; tabla mapeada, columnas,
accessors, converters, asociación FK e ID policy forman el shape estructural.

`JpaEntityMetadataResolver.caching` conserva un resolver por identidad de persistence unit.
`DefaultPostgresBulkOperations` conserva una operación pgJDBC por identidad de
`EntityMetadata<?>`. Ninguna key incluye `TableName`, schema ni tenant y no existe facade o SQL
cacheado por target. El único target retenido durante una operación es su argumento/local Java.

## Pipeline de propagación

```text
repository proxy + runtime TableName
  -> RepositoryMethodContext: domain type
  -> JpaContext: EntityManager de la persistence unit
  -> JpaEntityMetadataResolver: EntityMetadata estructural
  -> Session#doReturningWork: Connection transaction-bound
  -> PostgresBulkJdbcOperations(..., runtimeTarget)
  -> TableName.resolveRuntimeTarget(...)
  -> COPY o CTAS/JOIN schema-qualified
```

Para input no vacío el adapter entrega el target sin reinterpretarlo y pgJDBC sigue siendo la
fuente de resolución. Para input vacío no debe obtenerse una conexión; el adapter resuelve una vez
contra `metadata.table()` usando el mismo método central de core y retorna el resultado vacío. Así
se conserva simultáneamente la validación target-aware de MS2/MS3 y el no-op JDBC JPA.

No se llama `setSchema`, no se modifica `search_path`, no se recrea `Session` y no se abre una
segunda conexión.

## Insert

El target efectivo sólo sustituye la tabla root en COPY. Metadata, orden de columnas, converters,
encoder e ID semantics son los mismos del mapping default. IDs asignados se copian; IDs generados
se omiten y el objeto de entrada no se actualiza. COPY no ejecuta callbacks, `@PrePersist`, cascade,
auditing ni dirty checking y no hace managed a los objetos.

Un batch sigue siendo un límite de ejecución COPY, no una transacción. El iterable se obtiene una
vez y se consume single-pass con memoria adicional `O(1)`.

## Lookup y materialización

pgJDBC construye CTAS y JOIN desde el mismo target efectivo. El callback JPA recibe ese SELECT ya
qualified y ejecuta `EntityManager.createNativeQuery(selectSql, domainType)` con flush mode
`COMMIT` antes del DROP de la temporal. No crea una query basada nuevamente en la tabla default;
por ello las rows materializadas proceden realmente del target runtime.

La materialización supone que la tabla runtime tiene shape compatible con el mapping. Hibernate
puede registrar las entidades resultantes en el persistence context bajo su identidad normal; el
caller debe evitar mezclar simultáneamente la misma identidad lógica desde A y B en un único
contexto. La librería no hace `flush()` ni `clear()` automático. Cambios managed pendientes pueden
quedar fuera del lookup y el contexto puede contener estado stale tras COPY.

## Transacciones y conexión

Los métodos conservan `REQUIRED`, read-write. El proxy crea una transacción si no existe o participa
en la exterior. `Session#doReturningWork` entrega al motor la misma conexión física; la native query
de materialización usa esa misma transacción y backend. PostgreSQL 15.18 lo verifica con
`pg_backend_pid()` y con una columna default poblada durante COPY.

Una transacción puede insertar/consultar A y B y confirmar o revertir ambas. `REQUIRES_NEW` conserva
su conexión y resultado independiente. JPA `NESTED` continúa **UNSUPPORTED**, incluso con el flag
del manager, porque `HibernateJpaDialect` no aporta savepoints. Read-only y uso directo del
delegate sin frontera repository válida se rechazan igual que antes; no hay fallback autocommit.

## Root-only, mappings y conversiones

El target afecta exclusivamente a la tabla root. Un `ManyToOne` continúa proyectando sólo su FK y
la resolución posterior de asociaciones obedece al mapping Hibernate normal; no se redirigen
tablas asociadas. Secondary/multi-table, herencia/discriminador y cascades siguen fuera de soporte.

La evidencia target-aware reutiliza converter JPA, enum STRING, `@Embedded` y FK `ManyToOne` sin
ampliar la matriz del adapter Hibernate. Generated IDs conservan omisión/no propagación. El quoting
por componentes permite schemas con espacios/case; no se acepta SQL libre.

## Concurrencia y observabilidad

Un mismo proxy repository atiende A/B secuencial y concurrentemente. Target, SQL e iterator son
locales por invocación; no existen fields `currentTarget`, `schema` o `tenant`. Cada thread depende
de su transacción/conexión Spring propia.

Los nuevos métodos atraviesan exactamente las observaciones `insert`/`lookup` existentes. No se
añaden tags, logs o métricas con target/schema/tenant y la cardinalidad observable permanece
acotada.

## Errores e identifiers

La policy de conflicto de MS1 se aplica sin duplicarla. Un target null, unqualified o incompatible
falla antes de COPY/CTAS; el proxy Spring puede traducir el `IllegalArgumentException` a
`InvalidDataAccessApiUsageException` conservando la causa. Errores PostgreSQL mantienen
`BulkException`, `SQLException` y SQLState accesibles: `3F000` para schema ausente y `42P01` para
tabla ausente están cubiertos. PostgreSQL sigue decidiendo `42501`; el IT JPA no crea roles, mientras
que la evidencia low-level MS2/MS3 cubre permisos.

Los mensajes propios no añaden identidad de tenant. Identifiers físicos sólo pueden aparecer por
contracts aprobados o mensajes del servidor. Cleanup secundario, transacción abortada y ownership
siguen ADR-019.

## Limitaciones y non-goals

- no `CurrentTenantIdentifierResolver`, `MultiTenantConnectionProvider`, `@TenantId`, hints,
  `ThreadLocal` ni resolver ambiental;
- no sincronización automática del persistence context, callbacks, cascade o IDs generados;
- no redirección de asociaciones/secondary tables ni validación de shape por catálogo;
- no provisionamiento, autorización, datasource routing, retry o cache por target;
- no properties/autoconfiguración Boot y no propagación Spring Data JDBC.

## Handoff MS5

MS5 debe exponer la misma intención en `PostgresBulkJdbcRepository`: target explícito local,
overload corto no ambiguo, overload con options y lookup con `TableName`. Debe reutilizar sus caches
estructurales e ID policy root-only sin importar JPA/Hibernate ni cambiar Boot.
