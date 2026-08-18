# Arquitectura

## Propósito y límites

La librería ofrecerá inserción y lookup masivos de alto rendimiento para entidades mapeadas a PostgreSQL. El caso principal es Spring Data JPA, pero el motor no dependerá de Spring. La primera versión soportará COPY CSV e Hibernate como proveedor de metadata.

No es un ORM, no administra el ciclo de vida de entidades, no sustituye `saveAll`, no genera DDL permanente y no promete portabilidad a otras bases de datos. COPY no sincroniza automáticamente persistence context, callbacks JPA, auditoría ni IDs generados; estas diferencias deberán formar parte del contrato público.

## Arquitectura objetivo evaluada

La cadena lineal propuesta inicialmente se reemplaza por un grafo acíclico con adapters hermanos:

```text
                                  Application
                            ┌──────────┴──────────┐
                            │                     │
                    BulkOperations<T>   PostgresBulkRepository<T, ID>
                            │                     │
                            └──────────┬──────────┘
                                       │
                               Spring Data fragment
                              ┌─────────┴─────────┐
                              ▼                   ▼
                            core               pgjdbc ───→ PostgreSQL
                              ▲                   ▲
                              │ metadata port     │ connection scope
                              │                   │
                          hibernate          Spring transaction
                              ▲
                              │
                           JPA mapping

                 Boot auto-configuration wires the concrete adapters
```

En dependencias Maven: `pgjdbc → core`, `hibernate → core`, `spring-data → core + pgjdbc`, `autoconfigure → spring-data + pgjdbc + hibernate` y `starter → autoconfigure` más dependencias de experiencia de usuario. Hibernate no necesita pgJDBC y pgJDBC no necesita Hibernate; auto-configure compone los adapters concretos. Sin Boot, el consumidor los ensambla explícitamente.

## Componentes conceptuales (no clases comprometidas)

- Una fachada `BulkOperations<T>` expresa casos de uso y options/resultados estables.
- Un descriptor inmutable de entidad expresa tabla, columnas, extractores y claves sin importar tipos Hibernate.
- Un resolver de metadata es un puerto; el adapter Hibernate produce el descriptor.
- Encoders convierten valores Java a una representación escalar definida; el escritor CSV aplica reglas de framing, quoting, NULL y UTF-8.
- Un acceso a conexión entrega una conexión física durante toda la operación; la integración Spring participa en la transacción actual.
- El executor pgJDBC posee COPY, SQL PostgreSQL, identificadores y tablas temporales.
- Una estrategia de lookup separa la decisión temp-table/COPY/JOIN de la fachada.
- Batching envuelve la ejecución y define consumo, conteo y fallos; no pertenece al protocolo COPY.

`BulkInsertService` y `BulkLookupService` sólo se materializarán si las implementaciones divergen lo suficiente para justificar dos servicios. En el core se prefieren pocos puertos cohesionados a interfaces uno-a-uno sin más de una implementación plausible.

## Flujo bulk insert

```text
caller → facade → validate options/input → resolve cached metadata
       → partition iterator → acquire transaction-aware connection once
       → build quoted COPY SQL once → encode + frame CSV rows
       → pgJDBC COPY per batch → accumulate server row counts → result
```

La misma conexión vive durante la operación. Un fallo cancela el COPY activo y conserva la causa. La atomicidad total sólo se promete dentro de una transacción que abarque todos los batches; fuera de ella se documentará y probará la política elegida antes de publicar la API.

## Flujo bulk lookup

```text
typed keys → validate/deduplicate policy → resolve key metadata
           → acquire one transaction-aware connection
           → create temporary relation from physical table definition
           → COPY key tuples → quoted JOIN against qualified real table
           → map rows through JPA/Hibernate boundary → cleanup/result
```

La estrategia inicial será tabla temporal + COPY + JOIN. `VALUES` y `UNNEST` se reservan para comparación futura, no para el MVP. La tabla temporal y el SELECT deben ejecutarse sobre la misma conexión; mezclar un statement JDBC con una consulta JPA sólo será válido si se demuestra esa identidad.

## Decisiones de diseño de API pendientes

La forma preferida de entrada es `Iterable<? extends T>` para insert: acepta `Collection` sin exigir acceso aleatorio y permite batching acotado; no promete streaming perezoso ni paralelismo. `Stream` se excluye del primer contrato por ownership/cierre y semántica transaccional.

Para lookup se separan claves de entidades. Una clave simple puede usar `Iterable<K>`; una compuesta debe ser un tipo de clave del consumidor y un extractor/definición validada, no `Collection<?>` ni varargs de nombres. Quedan por resolver orden, duplicados, nulls y tipado final en ADR-005/006 antes de crear código.

`BulkWriteResult` debería empezar con datos verificables del servidor (`affectedRows`, `batches`). Duración pertenece a observabilidad y no al resultado: incluirla dificultaría determinismo y compatibilidad. Un resultado lookup probablemente sea `List<T>` hasta que exista una necesidad demostrada de metadata adicional.

## Serialización COPY

CSV es la única implementación inicial. Se conservará una frontera interna entre encoding de valor y framing de registro para poder añadir TEXT/BINARY sin exponer un enum de formato prematuramente. En CSV PostgreSQL distingue `NULL` de empty string mediante quoting: con defaults, NULL es un campo vacío no citado y el string vacío es `""`. CR, LF, delimitador, quote y el token NULL requieren quoting/escape correcto ([documentación COPY](https://www.postgresql.org/docs/current/sql-copy.html)).

## Metadata y tablas temporales

El adapter Hibernate debe entregar nombres físicos ya resueltos, acceso FIELD/PROPERTY, converters, columnas de asociaciones e IDs embebidos. El core sólo ve descriptores propios.

No se generarán tipos a partir de clases Java. PostgreSQL `CREATE TABLE ... (LIKE source)` copia nombres y tipos físicos ([documentación CREATE TABLE](https://www.postgresql.org/docs/current/sql-createtable.html)); `CREATE TABLE AS ... WITH NO DATA` crea estructura a partir del resultado ([documentación CTAS](https://www.postgresql.org/docs/current/sql-createtableas.html)). ADR-006 mantiene `PROPOSED` cuál usar: un spike debe verificar domains, collations, generated/identity, columnas no-key, particiones y permisos. Todos los identificadores se modelan por partes y se citan; no se “sanitizan” perdiendo nombres válidos. PostgreSQL diferencia quoted case y limita identificadores a 63 bytes por defecto ([sintaxis léxica](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)).

## Transacciones

El core recibe un scope de conexión, no conoce `@Transactional`. Spring Data usa acceso compatible con la transacción actual; Spring documenta que `DataSourceUtils` devuelve la conexión vinculada cuando existe ([resource synchronization](https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html)). Nunca se llama `commit`, `rollback`, `close` físico ni se cambia `readOnly` sobre una conexión prestada.

Antes del lookup se definirá qué ocurre sin transacción: (a) requerir transacción, (b) crear un scope JDBC local, o (c) usar una temporal con `ON COMMIT PRESERVE ROWS` y cleanup. La opción debe asegurar una conexión única y cleanup; no se inferirá de autocommit accidentalmente.

## Observabilidad y seguridad

Un listener/observer de operación en la frontera de fachada permitirá integrar Micrometer en auto-configure sin que core dependa de él. Tags permitidos: operación, resultado y posiblemente tipo lógico acotado; nunca tabla arbitraria, ID, clave ni excepción completa. Logs no contienen filas ni valores. SQL dinámico sólo interpola identificadores producidos por metadata confiable y quoted por un componente central.
