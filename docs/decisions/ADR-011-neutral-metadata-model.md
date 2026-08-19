# ADR-011: Modelo neutral y ejecutable de metadata bulk

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Bulk insert necesita convertir cada objeto `T` en una secuencia ordenada de valores dirigida a una tabla física. Bulk lookup necesitará describir una secuencia ordenada de componentes de clave, simples o compuestos, sin recibir entidades parciales. Hibernate será el primer productor automático de esa información, pero core no puede depender de JPA, Hibernate, JDBC, PostgreSQL ni del mecanismo COPY.

La metadata mínima debe conservar nombres físicos ya resueltos, separar schema y tabla, fijar el orden de columnas, extraer valores sin repetir reflection y conservar el tipo Java declarado incluso cuando el valor sea null. No debe convertirse en un metamodelo ORM general.

## Alternativas

### Acceso a valores

1. **Reflection posterior:** metadata conserva miembros o nombres y cada consumidor resuelve/lee después. Reduce trabajo inicial del producer, pero replica reflection en encoding y lookup, reabre problemas de access type, herencia, asociaciones y embeddables, y eleva el coste por fila.
2. **Nombre de propiedad:** es neutral y serializable, pero no garantiza que una propiedad produzca una sola columna y obliga a otro componente a interpretar el mapping.
3. **Accessor propio:** puede expresar el contrato, pero duplica `java.util.function.Function` sin aportar lifecycle ni errores diferentes.
4. **`Function` prerresuelta:** Java puro, testeable y capaz de proyectar asociaciones, converters o componentes embedded a valores escalares antes de que el motor procese filas.

### Tipado de columnas heterogéneas

1. **`ColumnMetadata<T, V>` en listas:** conserva `V` por columna, pero una entidad contiene columnas con tipos distintos y obliga a wildcards/casts en todos los consumidores.
2. **Accessor borrado más `Class<?>`:** la factory genérica comprueba el emparejamiento común al construir, mientras el objeto almacenado devuelve `Object` y conserva el tipo declarado para seleccionar encoding cuando el valor sea null.
3. **`Type` o sistema lógico propio:** representa genéricos, pero no existe todavía un consumidor que necesite esa complejidad y duplicaría un sistema de tipos.

### Forma y visibilidad

1. **Una clase gigante de entidad:** compacta el número de tipos, pero mezcla insert con una API lookup aún diferida.
2. **Descriptors separados y pequeños:** `TableName`, `ColumnMetadata<T>`, `EntityMetadata<T>` y `BulkKeyMetadata<K>` componen insert y claves sin publicar una operación lookup.
3. **Metadata interna:** evita API pública, pero impediría al adapter Hibernate —otro módulo— producirla y cerraría innecesariamente metadata manual futura.
4. **Resolver por `Class<T>`:** simplifica wiring, pero una clase puede tener mappings distintos entre persistence units y todavía no existe consumidor que justifique política de resolución o cache.

## Decisión

- Crear el package cohesivo `io.ybr.postgresbulk.core.metadata` con cuatro tipos **public SPI**:
  - `TableName`: schema opcional y nombre de tabla como componentes separados;
  - `ColumnMetadata<T>`: nombre físico exacto, `Class<?>` Java normalizada y accessor prerresuelto;
  - `EntityMetadata<T>`: tipo lógico, tabla y lista ordenada final de columnas insertables;
  - `BulkKeyMetadata<K>`: tipo de clave y lista ordenada de componentes dirigida a columnas físicas.
- Usar factories públicas y clases finales inmutables. Los tipos que contienen functions no tienen equality estructural porque la igualdad de lambdas no representa igualdad de mapping. `TableName` sí tiene value semantics.
- `ColumnMetadata.of` recibe una `Function<? super T, ? extends V>`. El descriptor expone `Object read(T)` para que listas heterogéneas se consuman sin casts públicos. El producer garantiza que valores no-null son compatibles con el tipo declarado.
- Normalizar clases primitivas a wrappers; rechazar `void`. `Class<?>` basta para la selección de encoder prevista y permite tratar null sin `value.getClass()`. `Type` y un type system propio quedan diferidos hasta que exista un caso parametrizado real.
- Conservar identificadores exactamente como los entrega el producer. Core valida sólo non-null/non-blank y no hace trim, case folding, snake_case, quoting, parsing, límites de longitud ni validación PostgreSQL.
- Representar ausencia de schema internamente sin string vacío y exponerla como `Optional<String>`. No incluir catalog.
- El orden es el orden de la lista copiada al construir. `EntityMetadata` y `BulkKeyMetadata` rechazan listas vacías, elementos null y nombres físicos duplicados exactos, y hacen defensive copies no modificables.
- `EntityMetadata` contiene únicamente columnas insertables ya resueltas; no flags `insertable`, generated, ID, nullable ni motivos de exclusión.
- `BulkKeyMetadata<K>` queda separado de `EntityMetadata<T>`. Puede describir una key simple o compuesta y un key object distinto de la entidad, pero no publica métodos lookup, política de duplicados/nulls/orden de resultados ni estrategia de transporte. No tiene nombre ni semántica de constraint UNIQUE.
- No crear `MetadataResolver`, cache ni `BulkMetadataException`. Los producers construyen descriptors explícitos; wiring/caching se decidirán cuando exista una implementación Hibernate y errores de construcción siguen ADR-009.

## Invariantes y ownership

Los descriptors poseen copias inmutables de las colecciones recibidas. Son seguros para compartir entre operaciones concurrentes siempre que los accessors suministrados sean stateless y thread-safe, condición del SPI. Un accessor puede devolver null y puede proyectar varias columnas diferentes desde el mismo objeto lógico; por tanto, el modelo no asume `field == column` ni utiliza reflection durante el consumo.

## Consecuencias

Phase 4 podrá seleccionar encoding por `javaType` y leer un valor por columna en orden sin conocer Hibernate. Phase 7 podrá combinar la tabla de `EntityMetadata<T>` con una `BulkKeyMetadata<K>` simple o compuesta sin aceptar entidades parciales. El adapter Hibernate de Phase 8 será responsable de convertir FIELD/PROPERTY, associations, embeddables, converters y selectables al descriptor final.

La API aumenta en cuatro tipos públicos SPI, para un total de ocho tipos públicos en core. ADR-004 permanece PROPOSED: este ADR acepta el descriptor receptor, pero no demuestra todavía cómo Hibernate obtiene correctamente la metadata ni su compatibilidad entre versiones.

## Decisiones diferidas

- Resolución y cache por persistence unit/session factory.
- API pública de lookup, key selection, duplicates, nulls y orden del resultado.
- Validación entre columnas de key y tabla destino.
- Tipos Java parametrizados y custom encoder bindings.
- Excepciones runtime específicas de metadata/extracción.
- Nullability, IDs, generated/updatable flags, catalogs, quoting y tipos físicos de base de datos.
