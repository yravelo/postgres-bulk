# Investigación multi-schema y schema-per-tenant

## Estado y alcance

**MS0: DONE (2026-08-20).** Este documento cierra investigación, arquitectura y planificación.
No añade soporte runtime multi-schema, código Java productivo, properties Boot, publicación ni
trabajo del security baseline. Las decisiones siguen `PROPOSED` hasta que MS1 aporte un prototype
ejecutable.

La línea estudia un caso concreto: una misma estructura lógica —tipo, tabla base, columnas y
conversiones— existe en varios schemas PostgreSQL y cada operación bulk debe dirigirse al destino
físico que ya resolvió la aplicación. PostgreSQL permite que schemas distintos contengan objetos
con el mismo nombre y que se acceda a ellos mediante `schema.table`; también advierte que los
nombres no cualificados dependen de `search_path` y de la confianza otorgada a schemas incluidos
en él. Por ello la dirección elegida usa nombres cualificados explícitos y no estado de sesión.

Fuentes oficiales consultadas:

- [PostgreSQL: schemas y schema search path](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [Spring Framework: conexiones JDBC transaction-aware](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)
- [Spring Framework: propagación transaccional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- [Hibernate ORM 6.6: user guide](https://docs.hibernate.org/orm/6.6/userguide/html_single/)
- [Spring Data Relational 3.5: `RelationalPersistentEntity`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/RelationalPersistentEntity.html)
- [Spring Data Relational 3.5: `SqlIdentifier`](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/sql/SqlIdentifier.html)

## Principio rector: la librería no conoce tenants

La aplicación decide **quién** es el tenant y traduce esa decisión a **dónde** debe ejecutarse la
operación. PostgreSQL Bulk recibe únicamente un destino físico estructurado. La librería nunca
debe introducir ni depender de:

- `TenantContext`, `TenantId`, JWT, headers, claims o seguridad web;
- `ThreadLocal`, Reactor Context u otro contexto ambiental para escoger destino;
- `CurrentTenantIdentifierResolver`, `MultiTenantConnectionProvider` o resolución multi-tenant
  Hibernate;
- `AbstractRoutingDataSource`, selección de datasource o lookup de credenciales;
- un resolver global que reciba tenant ids, ni una cache indexada por tenant;
- tags, logs o excepciones que conviertan tenant/schema en dimensión de observabilidad.

Hibernate documenta sus contratos multi-tenant como resolución de tenant y adquisición de
conexiones específicas. Son válidos para una aplicación que los adopte, pero quedan aguas arriba:
PostgreSQL Bulk puede usar la conexión que esa aplicación ya obtuvo y el destino físico que ya
resolvió, sin acoplarse a esos contratos.

## Arquitectura actual auditada

### `TableName`

`TableName` ya es un value object neutral, inmutable y thread-safe con dos componentes: schema
opcional y tabla obligatoria. Conserva texto exacto, no parsea `schema.table`, no normaliza case y
no genera SQL. Su igualdad incluye ambos componentes. Por tanto, ya representa exactamente un
destino físico `schema + table`; crear otro value object con la misma forma no aportaría semántica.

La limitación no está en `TableName`, sino en dónde se almacena: hoy forma parte de
`EntityMetadata<T>` y queda fijado al preparar los motores.

### Metadata y caches

| Componente | Cache/key actual | Contenido físico actual | Evaluación multi-schema |
| --- | --- | --- | --- |
| `HibernateEntityMetadataResolver` | `ConcurrentHashMap<Class<?>, EntityMetadata<?>>` por `EntityManagerFactory` | tabla, schema mapeado, columnas y accessors | la estructura es reusable; un schema runtime no debe entrar en esta cache |
| `JpaEntityMetadataResolver.caching` | resolver por identidad de `EntityManagerFactory` | delega metadata por persistence unit | correcto; no debe añadir tenant/target a la key |
| `SpringDataJdbcEntityMetadataResolver` | `ConcurrentHashMap<Class<?>, ResolvedMapping<?>>` por converter/context | tabla qualified, columnas, paths y variantes de ID | la estructura y variantes de ID son reusables; no debe crear una variante por schema runtime |
| fragmento JPA | `IdentityHashMap<EntityMetadata<?>, PostgresBulkJdbcOperations<?>>` por repository | engine preparado que hoy incluye SQL de tabla | debe dejar de cachear SQL target-specific, no crecer por tenant |
| adapter JDBC | prepara engine por llamada después del lookahead | metadata y SQL del target mapeado | no tiene cache global; puede pasar target explícito sin crearla |

La metadata de columnas, accessors, conversiones, tipo relacional y política assigned/generated ID
es estructural. El schema/tabla concreto de una invocación es operacional. La hipótesis de
separarlos **sobrevive** a la auditoría: ningún encoder o accessor necesita schema, y la selección
de target sólo afecta SQL e invariantes de compatibilidad.

`EntityMetadata.table()` debe conservarse por compatibilidad como destino mapeado/default. MS1 no
debe romper factories ni resolvers existentes. La separación puede ser semántica e interna antes
de plantear una revisión mayor del descriptor: metadata entrega estructura más un default; la
invocación puede aportar un destino explícito que se valida y usa sin mutar metadata.

### Preparación y cache de SQL

El audit encontró dos puntos target-specific:

1. `PostgresBulkInserter` construye `copySql` una vez desde `EntityMetadata` y lo conserva en un
   field final. La misma instancia no puede apuntar a otro schema.
2. `TemporaryTableBulkLookup` prepara `BulkLookupSql` desde `metadata.table()`. CTAS y JOIN
   conservan el target cualificado en ese objeto.

En cambio, `PreparedCopyCsvRowEncoder`, `ValueEncoderRegistry`, columnas ordenadas y
`BulkKeyMetadata` no conocen tabla/schema. Son reutilizables entre todos los destinos compatibles.

No existe hoy una cache global de statements JDBC. El fragmento JPA sí reutiliza
`PostgresBulkJdbcOperations` por identidad de metadata, lo que indirectamente reutiliza el COPY SQL
target-specific. El diseño futuro debe conservar la reutilización estructural y construir el SQL
completo como variable local de operación. No se añade `Map<TableName, ...>`: en schema-per-tenant
sería una cache potencialmente ilimitada y retendría nombres de tenants.

## Separación propuesta

```text
mapping context / persistence unit
        |
        v
estructura cacheable por tipo
  - java type
  - columnas ordenadas
  - accessors/converters
  - variantes de ID
  - encoders preparados
  - tabla mapeada como default/conflict signal
        |
        | + TableName explícito por operación
        v
invocación inmutable
  - valida target y conflicto estático
  - construye SQL quoted local
  - usa Connection caller-owned
  - no conserva target después de retornar
```

La operación no clona accessors ni recalcula el metamodelo. Tampoco crea una entrada de cache por
schema. El coste target-specific queda limitado a validación y construcción de unas pocas cadenas
SQL acotadas por el número de columnas.

## Alternativas evaluadas

| Opción | Pureza core | Usabilidad | Reuso insert/lookup | Cache/concurrencia | Compatibilidad y riesgo | Resultado |
| --- | --- | --- | --- | --- | --- | --- |
| A. argumento `TableName` por método | alta; ya es neutral | explícito, pero multiplica overloads | sí | excelente si es local | aditivo; superficie crece | primitive recomendada para motores, forma pública por validar |
| B. override sólo de schema | mezcla target runtime con tabla heredada | conciso | sí | segura | resolución parcial y conflictos implícitos; bloquea tabla dinámica | rechazada |
| C. nuevo `PhysicalTarget`/`TableTarget` con schema+table | neutral | clara | sí | excelente | duplica hoy la forma y validación de `TableName` | no crear mientras no haya semántica adicional demostrada |
| D. SPI `PhysicalTargetResolver` | puede ser neutral nominalmente | oculta un argumento | sí | riesgo de contexto implícito y timing | facilita ThreadLocal/tenant coupling y wiring ambiguo | no como mecanismo primario; adapter de aplicación eventual fuera del core |
| E. options por operación | evolutiva | evita algunos overloads | difícil compartir: insert ya tiene options, lookup no | segura si inmutable | mezcla batching con destino o exige nueva jerarquía | rechazada para MS1 |
| F. vista inmutable `forTarget(TableName)` | neutral | fluida y evita overloads | sí | thread-safe, sin mutación | añade facade/tipos y puede ser retenida por caller | candidata pública a comparar con A en MS1 |

La recomendación combina A y una prueba de ergonomía F: `TableName` es la representación
canónica; el engine recibe el target explícitamente. MS1 comparará si las APIs Spring deben exponer
argumentos directos o una vista inmutable `forTarget`, pero no puede esconder la selección en un
resolver ambiental. Ninguna forma final llevará `tenant` en su nombre.

## Destino completo frente a schema-only

Se elige un destino completo `schema + table`, no un `schemaOverride` parcial:

- una invocación queda autocontenida y no depende de merge rules invisibles;
- insert y lookup consumen exactamente el mismo concepto;
- habilita tablas físicas dinámicas futuras sin otro cambio de modelo;
- reduce errores al reutilizar metadata cuya tabla default no sea la esperada;
- `TableName` ya ofrece esa forma y quoting por componentes;
- facilita tests y mensajes de conflicto deterministas.

Para el nuevo camino runtime multi-schema, el target explícito debe estar schema-qualified. Los
targets sin schema continúan disponibles sólo mediante el comportamiento existente/default, donde
la aplicación acepta que la conexión resuelva el nombre con su ambiente.

## Política de schema estático y conflictos

La política inicial es conservadora:

| Mapping estructural | Target runtime | Resultado propuesto |
| --- | --- | --- |
| tabla sin schema | ausente | comportamiento actual; tabla no cualificada |
| tabla sin schema | schema + table explícitos | permitido; target runtime completo |
| schema + tabla estáticos | ausente | comportamiento actual; mapping estático |
| schema estático | target con el mismo schema | permitido; la tabla explícita puede variar si conserva columnas compatibles |
| schema estático | target con otro schema | rechazo explícito antes de JDBC |
| cualquier mapping | target runtime sin schema | rechazo en el nuevo API |

No se decide silenciosamente que "runtime gana" sobre un `@Table(schema=...)` o `@Table` Spring
Data qualified. Las aplicaciones dinámicas deben dejar el schema sin fijar en el mapping. Permitir
override deliberado de un schema estático requeriría una política pública adicional y evidencia;
queda fuera de MS1.

Una tabla runtime distinta se permite porque el target completo es una capacidad intencional. El
caller garantiza que comparte la estructura requerida. PostgreSQL seguirá validando columnas,
tipos, privileges y constraints; la librería no consulta catálogos ni provisiona objetos.

## SQL, identifiers y seguridad

La construcción conserva las reglas actuales:

- schema, tabla, columnas y temporales son componentes estructurados;
- cada componente se cita separadamente con el quoter central y se rechaza NUL;
- nunca se acepta `schema.table` como un único string, SQL libre, fragmentos, placeholders de
  identifier o interpolación de tenant ids;
- los valores continúan viajando por COPY/parameters, nunca concatenados;
- el target completo se usa tanto en COPY INSERT como en CTAS y JOIN de lookup.

La aplicación debe traducir su identidad de tenant a una allow-list o mapping autorizado de
`TableName`; pasar directamente un claim/header como schema sería un error de seguridad aunque el
quoting evite inyección SQL. Quoting protege sintaxis, no autorización. PostgreSQL debe aplicar
`USAGE` del schema y privilegios de tabla con el role de la conexión.

Los identifiers pueden contener información sensible —por ejemplo, un schema derivado de una
cuenta—. La librería no debe añadirlos a métricas, tags o logs. Una `SQLException` del servidor
puede incluir nombres de objetos y se preserva por el contrato de causa; esta limitación debe
documentarse, no ocultarse destruyendo la causa.

## Decisión sobre `search_path` y estado de conexión

El nuevo camino usa exclusivamente SQL cualificado. PostgreSQL resuelve un nombre no cualificado
tomando el primer match de `search_path` y advierte del riesgo de schemas modificables por usuarios
no confiables. Además, pgJDBC documenta `currentSchema` como configuración del search path de la
conexión.

Por tanto PostgreSQL Bulk no invocará:

- `Connection.setSchema`;
- `SET SCHEMA`, `SET search_path` ni `SET LOCAL search_path`;
- properties pgJDBC `currentSchema`;
- restauración best-effort de estado de conexión.

`search_path` se clasifica así:

- **legacy/default mapping sin schema:** comportamiento compatible actual, controlado por el
  caller/pool y no promocionado como mecanismo multi-schema;
- **target runtime:** nunca se usa para resolver la tabla; schema explícito obligatorio;
- **temporales internas:** siguen usando nombre session-local generado; no representan un target
  de negocio y el lifecycle actual permanece;
- **funciones, tipos u operators referenciados por PostgreSQL:** pertenecen al schema/DDL de la
  aplicación; la librería no configura su path.

Esto evita leakage A→B en conexiones pooled, restore failures y cambios observables por código
JPA/JDBC que comparte la transacción.

## Insert y lookup

### Insert

El engine preparado debe conservar columnas y `PreparedCopyCsvRowEncoder`, pero recibir un
`TableName` efectivo por llamada. El COPY SQL se construye localmente con target + columnas. El
batching, conteos, single-pass y ownership no cambian.

### Lookup

El mismo target efectivo alimenta CTAS y JOIN. `BulkKeyMetadata` sigue schema-independent. La
temporal continúa por conexión y operación; no se comparte entre schemas. El materializador JPA o
Spring Data JDBC consume el SELECT en la misma conexión antes del DROP, como hoy.

No se permite que insert resuelva schema de una forma y lookup de otra. Ambos caminos comparten la
misma validación central del target efectivo y la misma política de conflicto estático.

## Transacciones y conexión caller-owned

La característica no crea una frontera transaccional nueva:

- una operación usa la conexión que ya posee el caller/framework;
- no hace close, commit, rollback, savepoints ni mutadores JDBC;
- varias operaciones dirigidas a schemas A y B pueden ejecutarse secuencialmente en la misma
  conexión/transacción porque cada SQL nombra su target completo;
- una misma transacción puede modificar múltiples schemas de la misma base de datos; la aplicación
  decide si esa unidad es válida;
- `REQUIRED`, `REQUIRES_NEW`, NESTED condicionado de JDBC, read-only, `25P02` y cleanup conservan
  sus contratos actuales;
- database-per-tenant sigue siendo routing externo: la librería recibe la conexión ya elegida.

No se soporta una operación que intente abarcar bases distintas con una sola conexión. PostgreSQL
expone schemas dentro de una base; la conexión sólo accede a la base seleccionada al abrirse.

## Concurrencia y ausencia de leakage

Todos los objetos runtime deben ser inmutables o locales. Un repository singleton puede atender en
paralelo:

```text
thread 1 -> target schema_a.product -> connection 1
thread 2 -> target schema_b.product -> connection 2
```

También debe ser seguro:

```text
same connection/transaction
  operation A -> "schema_a"."product"
  operation B -> "schema_b"."product"
```

El segundo caso es el falsificador principal de dependencia ambiental: después de A, B no puede
observar schema activo, SQL cached o facade mutable de A. Se prohíbe `repository.setSchema(...)` y
cualquier field mutable de destino. Una vista `forTarget` sólo sería aceptable si es un objeto
nuevo, inmutable y sin modificar el repository delegado.

## Estrategia de cache y rendimiento

Debe cachearse:

- resolución de mapping por persistence unit/context y tipo;
- columnas, accessors y conversiones;
- variantes assigned/generated ID;
- encoders por shape estructural;
- fragmentos SQL que no contengan target, sólo si una medición lo justifica.

No debe cachearse por la librería:

- tenant id, schema runtime o `TableName` runtime;
- SQL completo por target;
- facades por target;
- decisiones de autorización o datasource routing.

Construir SQL local añade coste `O(columnas)` por invocación y allocations pequeñas, no por fila.
COPY, I/O y materialización dominan operaciones bulk. MS8 medirá antes de considerar una cache
acotada; no se aceptará una cache tenant-keyed sólo para recuperar esa construcción.

## Compatibilidad pública

El cambio futuro será aditivo:

- llamadas sin target conservarán exactamente metadata/default y comportamiento actual;
- `TableName` mantiene factories, value semantics y package;
- `EntityMetadata.of` y `table()` permanecen;
- no se modifica el default batch, lookup semantics ni resultados;
- no aparecen métodos abstractos nuevos sin defaults en interfaces ya implementables por usuarios
  hasta verificar compatibilidad source/binaria;
- se evitará una combinación cartesiana de overloads; MS1 comparará argumento explícito frente a
  una vista inmutable target-bound.

Un tipo público nuevo, si la evidencia obliga a crearlo, usará nombres neutrales como
`PhysicalTableTarget` o `TargetedBulkOperations`, nunca `Tenant*`. La recomendación actual es no
duplicar `TableName`.

## Impacto por módulo

| Módulo/área | Debe cambiar en la línea | Puede cambiar | No debe cambiar por esta capacidad |
| --- | --- | --- | --- |
| core | contrato explícito de target sólo si MS1 demuestra que pertenece a API común | Javadocs/helpers neutrales alrededor de `TableName` | tenant context, SQL, JDBC, resolver ambiental |
| pgjdbc | separar preparación estructural de SQL target-specific; insert y lookup reciben target | facade/vista inmutable y tests | conexión ownership, COPY dialect, cache por tenant |
| hibernate | tratar tabla mapeada como default/conflict signal | validación de schema estático | resolver tenant Hibernate, session switching |
| Spring Data JPA | propagar target explícito y evitar engine cache target-specific | API target-bound aditiva | flush/clear, entity lifecycle, mutable repository schema |
| Spring Data JDBC | propagar target explícito conservando metadata/ID/materializer | API target-bound aditiva | callbacks/graphs, datasource routing |
| Boot JPA | como máximo wiring neutral si fuese necesario | conditions/tests de ausencia de bean global | property global schema/tenant, resolver de tenant |
| Boot JDBC | igual que Boot JPA | conditions/tests | elegir datasource/manager o schema global |
| starters | ninguno | dependency metadata sólo si nace un tipo en módulos existentes | Java/configuración productiva |
| benchmarks | contenders y escenarios A/B cuando el API exista | medición de coste de SQL por operación | cambios de engine para ganar una cifra |
| docs/examples | adopción, límites, seguridad, migrations y matrices | ejemplo schema-per-operation | claims de aislamiento/autorización no probados |

## Límites explícitos

- **Database-per-tenant:** routing, pools y credenciales son externos; la capacidad no los
  reemplaza ni interfiere.
- **Row-level tenancy:** discriminator columns, RLS, filtros y `@TenantId` están fuera de alcance.
- **Provisioning/migrations:** crear schemas/tablas, Flyway/Liquibase, upgrades coordinados y
  drift detection pertenecen a la aplicación/plataforma.
- **Authorization:** la librería no decide qué caller puede usar un target.
- **Observability:** no se añaden schema/target/tenant como tags; la cardinalidad permanece cerrada.
- **Publication/security baseline:** no forman parte de MS0 ni de la línea funcional hasta nueva
  autorización.

## Matriz futura de tests

| Eje | Casos obligatorios |
| --- | --- |
| targets | schema A/B, quoted/mixed-case/reserved/espacios, tabla runtime distinta, target ausente |
| conflicto | mapping sin schema, mismo schema, schema distinto rechazado, target runtime no qualified |
| operaciones | insert default/options/multibatch; lookup simple/compuesto/duplicates/missing/null |
| stacks | pgjdbc low-level, JPA/Hibernate, Spring Data JDBC, ambos starters |
| transacción | commit/rollback, REQUIRED, REQUIRES_NEW, NESTED JDBC condicionado, read-only, `25P02` |
| secuencia | A→B y B→A en misma conexión/transacción; insert→lookup y lookup→insert cross-schema |
| concurrencia | singleton compartido con A/B, misma metadata, conexiones separadas, pool size one secuencial |
| cache | identidad de metadata/encoder estable, cero crecimiento por targets, no reuse de SQL target A en B |
| seguridad | quoting/NUL, no raw SQL, schema no presente en metrics, errores sin datos de filas/keys |
| compatibilidad | llamadas legacy sin target idénticas; API diff; Java/Boot/Data/Hibernate/pgJDBC/PostgreSQL soportados |
| infraestructura | multiple EMF/converters/datasources con selección existente; no bean/resolver tenant nuevo |
| rendimiento | coste warm target A/B, schema cardinality stress y 1M sin cache tenant-specific |

## Riesgos y mitigaciones

| Riesgo | Mitigación propuesta |
| --- | --- |
| SQL preparado para A se reutiliza en B | target como variable local y tests A→B/B→A |
| cache crece con tenants | prohibir cache target-keyed; auditar heap/keys |
| target se deriva directamente de input no confiable | aplicación resuelve allow-list; identifiers estructurados + privileges PostgreSQL |
| schema estático contradice runtime | rechazo explícito antes de JDBC |
| restauración de conexión falla | no mutar schema/search_path |
| JPA materializa desde tabla distinta al mapping | characterization específica; native result mapping puede asumir columnas, no schema, pero debe probarse |
| drift de schema rompe encoding/lookup | migrations externas y fallo PostgreSQL visible; no catalog inference |
| mensajes del driver exponen schema | no añadirlo en library telemetry; documentar cause externa preservada |
| API crece por overloads | prototype A/F y API review antes de aceptar ADR-031 |
| target-bound facade retenida por caller | objeto inmutable sin cache interna; documentar scope/reuso seguro |

## Preguntas abiertas para MS1+

1. ¿Argumentos directos o `forTarget(TableName)` producen la menor API coherente en pgjdbc, JPA y
   JDBC sin romper implementadores externos?
2. ¿Puede `PostgresBulkJdbcOperations` compartir un encoder preparado y construir COPY/lookup SQL
   local sin cambios públicos adicionales?
3. ¿Debe la validación de conflicto vivir en core o en cada adapter que conoce el mapping estático?
4. ¿La materialización native JPA funciona con una tabla runtime distinta y mismo row shape en
   todos los patches Hibernate soportados?
5. ¿Cómo preservar el empty-input no-op: validar target antes o después del lookahead? La hipótesis
   compatible es después, igual que metadata/conexión actuales.
6. ¿El nombre final del método debe ser `forTarget`, `forTable` o un argumento `target`? Ninguno
   puede contener `tenant`.
7. ¿Hace falta exponer el target efectivo al callback low-level, o basta con que el engine genere
   todo el SQL antes de invocarlo?
8. ¿Qué API-diff/default methods son necesarios para consumidores que implementen directamente
   interfaces core/repository?

## Conclusión MS0

El diseño es viable sin hacer tenant-aware a la librería. `TableName` ya expresa el destino físico
completo; el cambio necesario es desplazar su selección al scope de invocación y evitar que SQL
target-specific quede atrapado en caches estructurales. Qualified SQL elimina dependencia de
`search_path` y mutación de conexión. Metadata, encoders y key descriptors se reutilizan sin
tenant keys. La línea puede avanzar a **MS1 — Operation-Scoped Physical Target Contract** sin
implementar todavía integración Spring ni comportamiento multi-schema end-to-end.
