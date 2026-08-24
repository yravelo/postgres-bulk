# Bulk lookup multi-schema en pgJDBC

## Estado y alcance

**MS3: DONE (2026-08-24).** La fachada low-level pgJDBC puede dirigir CTAS y JOIN de un
`findAllByBulkKey` a un `TableName` schema-qualified explícito por invocación. El COPY intermedio
continúa cargando una temporal session-local; no representa ni contiene el target de negocio.

Hibernate/JPA, Spring Data JDBC, Boot, resolución de tenants, security baseline, publicación y
benchmarks quedan fuera de MS3. La aplicación elige y autoriza el destino físico.

## API y resolución

MS3 añade un overload público sin modificar la firma histórica:

```java
List<Product> rows = operations.findAllByBulkKey(
    connection,
    keys,
    keyMetadata,
    List.of(),
    (sameConnection, selectSql, copiedKeys) -> materialize(sameConnection, selectSql),
    TableName.of("tenant_a", "product")
);
```

Después de validar argumentos y antes de pedir el iterator, el coordinador ejecuta exactamente:

```java
TableName effectiveTarget = mappedTable.resolveRuntimeTarget(runtimeTarget);
```

Un target `null`, no calificado o incompatible falla antes de consumir keys o ejecutar SQL. El
camino legacy no llama al resolver y usa directamente `metadata.table()`, como antes. Un input
vacío target-aware valida el contrato, obtiene un único iterator y retorna `emptyResult` sin leer
autocommit, crear la temporal, abrir COPY, ejecutar SELECT ni invocar el callback.

## Preparación estructural y SQL local

`TemporaryTableBulkLookup` conserva sólo el mapping default, el `BulkKeyMetadata<K>`, el encoder de
keys preparado y sus dependencias de ejecución. `BulkLookupSql` conserva únicamente estructura
independiente del target: columnas seleccionadas, condición de JOIN y key metadata.

Para una invocación no vacía se genera un nombre temporal independiente y se construye un único
`BulkLookupSql.InvocationSql` local con las cuatro sentencias del lifecycle:

```text
mapped TableName + runtime TableName
  -> resolveRuntimeTarget una vez
  -> iterator/lookahead
  -> nombre pgbulk temporal único
  -> InvocationSql(effective target, temp name)
       CTAS   FROM "tenant_a"."product"
       COPY   "pgbulk_keys_<uuid>"
       JOIN   FROM "tenant_a"."product"
       DROP   "pgbulk_keys_<uuid>"
```

La cualificación del target se calcula una vez al crear ese objeto, de modo que CTAS y JOIN no
pueden divergir. El objeto es local y deja de ser alcanzable al retornar. No existe
`Map<TableName, ...>`, cache por schema/tenant, facade target-bound ni SQL completo retenido entre
invocaciones. El coste adicional es `O(componentes de key)` por lookup no vacío y no crece con la
cardinalidad de schemas.

## Metadata, encoding y semántica relacional

El mismo `EntityMetadata<T>`, lista de columnas, `BulkKeyMetadata<K>` y
`PreparedCopyCsvRowEncoder<K>` sirven para A y B. Ninguno se clona o muta. El runtime target sólo
afecta las dos referencias a la tabla física; no se añade al resultado ni al callback.

Se conservan las reglas del lookup base:

- simple y composite keys respetan el orden físico de `BulkKeyMetadata`;
- input duplicado se copia pero `SELECT DISTINCT` evita multiplicar matches;
- duplicados existentes en el target se materializan todos;
- missing keys no producen filas;
- key o componente null falla con posición/columna y sin exponer valores;
- no hay garantía de orden;
- un iterable one-shot se consulta una sola vez y 20.000 keys siguen en `O(1)` memoria Java.

## Temporal, conexión y transacción

La temporal conserva nombre aleatorio `pgbulk_keys_<uuid>`, `ON COMMIT DROP`, DROP explícito y
scope de sesión. El nombre nunca incorpora schema, tabla, target ni tenant. CREATE, COPY, callback
y DROP usan exactamente la misma `Connection` caller-owned.

La operación no hace close, commit, rollback, savepoint ni cambia autocommit, read-only, isolation,
schema o `search_path`. Sigue exigiendo `autoCommit=false` para input no vacío; PostgreSQL rechaza
CTAS en read-only con `25006`. A y B pueden ejecutarse secuencialmente en una misma conexión y en
una misma transacción, cuyo commit/rollback pertenece al caller.

El callback materializa completamente mientras la temporal existe. Runtime/Error preserva
identidad. Fallos SQL mantienen causa y SQLState; un DROP secundario queda suppressed. Después de
un error SQL PostgreSQL conserva `25P02` hasta rollback, que también elimina cualquier temporal y
permite reutilizar el mismo backend pooled.

## Identifiers, permisos y aislamiento

Schema, tabla, columnas y temporal se citan por componentes con `PostgresIdentifierQuoter`. No se
acepta SQL libre, un nombre `schema.table` preconcatenado, `setSchema` ni `search_path`. PostgreSQL
aplica `USAGE` y `SELECT`: MS3 preserva `42P01` para relaciones ausentes, `42501` para permiso
denegado y la causa del driver sin fallback al mapping default.

La evidencia PostgreSQL 15.18 cubre misma metadata A/B, insert A→lookup A, insert A→lookup B sin
resultados, backend pooled A→B sin cambio de schema/path, A/B concurrentes, commit/rollback
multi-schema, simple/composite, duplicates/missing/null, empty/one-shot/20k, quoted identifiers,
mapping estático idéntico, conflictos pre-SQL, objetos ausentes, permisos, autocommit/read-only,
callback/SELECT failure, `25P02`, cleanup y recuperación del pool.

## Límites

MS3 no propaga el overload a repositories JPA/JDBC, no cambia materializadores Spring, no crea
properties Boot, no introduce observabilidad por target y no ejecuta publicación, release,
security baseline ni benchmarks. La siguiente fase es
**MS4 — Hibernate and Spring Data JPA Target Integration**.
