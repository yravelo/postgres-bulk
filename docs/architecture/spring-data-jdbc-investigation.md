# Investigación de integración con Spring Data JDBC

## Estado y alcance

Este documento cierra J0: investigación, arquitectura y planificación. No añade módulos Maven ni
código productivo. Las decisiones que requieren evidencia ejecutable permanecen `PROPOSED` en
[ADR-024](../decisions/ADR-024-spring-data-jdbc-integration-architecture.md) y
[ADR-025](../decisions/ADR-025-spring-data-jdbc-metadata-strategy.md).

La baseline investigada es Spring Boot 3.5 y Spring Data Relational/JDBC 3.5. La publicación de
`0.1.0`, el security baseline diferido y Spring Boot 4 quedan fuera de alcance.

## Arquitectura actual

El pipeline JPA real es:

```text
entity / JpaRepository + PostgresBulkRepository
    -> external Spring Data fragment (spring.factories)
    -> JpaContext + EntityManagerFactory
    -> HibernateEntityMetadataResolver
    -> EntityMetadata<T> / BulkKeyMetadata<K>
    -> PostgresBulkJdbcOperations<T>
    -> Hibernate Session.doReturningWork(Connection)
    -> PostgreSQL COPY / temporary-table lookup
```

La clasificación por responsabilidad es:

| Área actual | Clasificación | Evidencia en el código | Decisión JDBC |
| --- | --- | --- | --- |
| `EntityMetadata`, `ColumnMetadata`, `BulkKeyMetadata` y API de resultados | Reutilizable y neutral | `postgres-bulk-core` no importa Spring, JPA ni pgJDBC | Reutilizar sin cambios |
| COPY, batching, tabla temporal y join | Reutilizable y PostgreSQL/pgJDBC-specific | `PostgresBulkJdbcOperations` recibe una `Connection` prestada | Reutilizar sin cambios |
| Resolver de metadatos | Hibernate-specific | Usa `SessionFactory`, `EntityPersister` y `JdbcMapping` | No reutilizar; crear resolver JDBC |
| Fragmento actual | JPA-specific aunque su artifact se llame `spring-data` | Usa `JpaContext`, `EntityManager`, `FlushModeType` y `Session` | Preservar para compatibilidad, no presentarlo como común |
| Registro externo de fragments | Spring-Data-common | `META-INF/spring.factories` y `RepositoryMetadataAccess` | Repetir el mecanismo con otro tipo de fragmento |
| Auto-configuración y starter actuales | Boot + JPA/Hibernate-specific | Condiciones y dependencias sobre JPA/Hibernate | Preservar; añadir variantes JDBC separadas |
| Observabilidad actual | Contrato reusable, implementación local JPA | Helper package-private dentro de `spring-data` | Repetir contrato; no crear módulo común aún |

La hipótesis `core` y `pgjdbc` reutilizables sin cambios sobrevive a J0. Los motores ya aceptan
exactamente los dos datos que debe producir el adapter: valores relacionales tipados y una
`Connection` caller-owned. Las dificultades encontradas —metamodelo, callbacks, identifiers,
transacción y materialización— pertenecen al adapter, no demuestran una abstracción neutral
ausente.

## Estructura de módulos propuesta

```text
postgres-bulk-core
    ^
    +-- postgres-bulk-pgjdbc
            ^
            +-- postgres-bulk-hibernate
            |       ^
            |       +-- postgres-bulk-spring-data          (JPA existente)
            |               ^
            |               +-- postgres-bulk-spring-boot-autoconfigure
            |                       ^
            |                       +-- postgres-bulk-spring-boot-starter
            |
            +-- postgres-bulk-spring-data-jdbc             (nuevo)
                    ^
                    +-- postgres-bulk-spring-boot-autoconfigure-jdbc (nuevo)
                            ^
                            +-- postgres-bulk-spring-boot-starter-data-jdbc (nuevo)
```

J0 recomienda:

- conservar `postgres-bulk-spring-data` y sus coordenadas: renombrarlo rompería consumidores antes
  de aportar valor funcional;
- documentarlo explícitamente como adapter Spring Data JPA;
- añadir `postgres-bulk-spring-data-jdbc`;
- añadir auto-configuración y starter JDBC separados;
- no crear `spring-data-common`: hoy sólo compartiría contratos pequeños y produciría una
  abstracción especulativa.

Evaluación explícita de naming:

| Opción J0 | Evaluación | Resultado |
| --- | --- | --- |
| A: tratar `postgres-bulk-spring-data` como common y añadir `-jdbc` | El artifact actual contiene JPA/Hibernate y su API documenta persistence context | Rechazada como descripción falsa |
| B: renombrar el actual a `-jpa` y añadir `-jdbc` | Es simétrico, pero cambia coordenadas/API del release candidate sin necesidad técnica | No adoptar; sólo reconsiderar en un breaking release futuro |
| C: `common` + `jpa` + `jdbc` | No existe aún duplicación suficiente y exigiría mover API publicada | Rechazada por especulativa |
| D: preservar el artifact JPA histórico y añadir artifacts JDBC explícitos | Mantiene compatibilidad y boundaries reales, aunque el nombre viejo sea menos preciso | **Propuesta elegida** |

Un único starter introduciría JPA/Hibernate en aplicaciones JDBC o JDBC en aplicaciones JPA y
haría ambiguo el transaction manager. Los starters separados mantienen dependencias, condiciones
y soporte operativos independientes. Una aplicación que realmente usa ambos puede declarar ambos.

## APIs oficiales seleccionadas

| API | Papel | Evaluación J0 |
| --- | --- | --- |
| `JdbcConverter` | Tipo de columna, SQL type y write conversion | Pública, central y preferida |
| `JdbcConverter.getMappingContext()` | Acceso al contexto efectivo configurado | Pública; evita reconstruir configuración |
| `RelationalMappingContext` | Descubrimiento de entidades y paths | Pública y estable en la línea 3.5 |
| `RelationalPersistentEntity` | Tipo, tabla qualified, id, version y accessors | Pública y estable |
| `RelationalPersistentProperty` | Columna, embedded, insert-only, sequence y flags de mapping | Pública y estable |
| `PersistentPropertyAccessor` / `PersistentPropertyPathAccessor` | Lectura de valores sin reflection propia | APIs públicas de Spring Data Commons |
| `SqlIdentifier` | Nombre lógico, composición e intención de quoting | Pública; requiere adaptación PostgreSQL probada |
| `NamingStrategy` | Fuente indirecta de nombres en el mapping context | Pública; no debe reevaluarse en el adapter |
| `JdbcCustomConversions` | Configuración de converters | Pública; se consume indirectamente por `JdbcConverter` |
| `IdValueSource` | Distinguir ID proporcionado de generado por instancia | Pública; útil para una política homogénea por llamada |
| `EntityRowMapper` | Materialización de un `ResultSet` | Pública en 3.5; puede cargar miembros relacionados |
| `JdbcOperations.execute(ConnectionCallback)` | Scope de la conexión transaction-aware | Pública de Spring Framework |
| `DataSourceUtils` | Alternativa explícita de acquire/release | Pública; segunda opción, no la default |
| `TransactionSynchronizationManager` | Validar transacción activa/read-only | Pública; inspección, no adquisición |
| `RepositoryMethodContext` / `RepositoryMetadataAccess` | Domain type del repository fragment | Públicas desde Spring Data Commons 3.4 |

No existe un tipo público llamado `RelationalPersistentPropertyAccessor` en la baseline. La
estrategia usa los accessors genéricos de Spring Data Commons. Tampoco se utilizarán clases
package-private, reflection sobre internals, `DbAction` ni SQL generators internos.

`DataAccessStrategy` y `JdbcAggregateTemplate` son APIs públicas útiles para operaciones de
agregados, pero no son el boundary correcto para un join contra una tabla temporal que debe
ejecutarse en una conexión exacta. En Spring Data 3.5, `JdbcAggregateTemplate` tampoco ofrece el
`getRowMapper(Class)` que aparece desde 4.0. `EntityRowMapper` es por ello la opción inicial, sujeta
a characterization tests.

## Estrategia de metadatos y conversión

El futuro `SpringDataJdbcEntityMetadataResolver` se construirá con el `JdbcConverter` real de la
aplicación. Por domain type:

1. obtiene el `RelationalPersistentEntity` del mapping context efectivo;
2. toma `getQualifiedTableName()` y conserva cada componente schema/table;
3. recorre únicamente paths escalares almacenados en la fila de la aggregate root;
4. excluye transient, colecciones, maps y entidades hijas;
5. aplana embedded paths y compone el prefix resuelto por el metamodelo;
6. crea un accessor prerresuelto, null-safe y sin reflection tardía;
7. obtiene `relationalType = jdbcConverter.getColumnType(leafProperty)`;
8. obtiene `sqlType = jdbcConverter.getTargetSqlType(leafProperty)`;
9. en cada lectura aplica
   `jdbcConverter.writeJdbcValue(domainValue, relationalType, sqlType).getValue()`;
10. publica ese `relationalType` como `ColumnMetadata.javaType()`.

`ColumnMetadata.javaType()` representa siempre el tipo Java relacional esperado por el driver y
por el registry de encoding después de la conversión. No representa el tipo declarado en el
domain model, el tipo runtime de un valor no-null ni el tipo SQL. Un converter `Money -> BigDecimal`
produce `BigDecimal.class`; un null conserva esa decisión por metadata. Si el converter devuelve un
tipo no compatible con el tipo declarado o no soportado por los encoders existentes, la llamada
falla de forma explícita. No habrá `value.getClass()` ni fallback `toString()`.

El resolver no instancia `JdbcCustomConversions`: reutiliza el `JdbcConverter` configurado, que ya
incorpora conversions de store, dialect y usuario. Esto evita divergir de la lectura normal de
Spring Data JDBC y respeta converters que devuelven `JdbcValue` con un `JDBCType` específico.

La metadata estática se cacheará por identidad del mapping context/converter y domain type. Las
variantes dependientes de la llamada —principalmente incluir u omitir ID— no pueden contaminar ese
cache: se derivarán de un descriptor base inmutable.

## Tabla, columnas e identifiers

`NamingStrategy` no se invoca por separado. `RelationalPersistentEntity` y
`RelationalPersistentProperty` ya contienen el resultado de convenciones, `@Table`, `@Column`,
schema y embedded prefix. Para acceso a `ResultSet`, `SqlIdentifier.getReference()` entrega el
nombre sin quoting.

El boundary actual merece una restricción explícita: core conserva componentes exactos y pgJDBC
los cita siempre. Esto coincide con identifiers quoted —la configuración default de Spring Data
JDBC 3.5— y con nombres físicos lowercase. Una estrategia plain que dependa de folding de case
puede no ser equivalente al citar el texto. J1 debe probar default, quoted, schema, explicit y
plain identifiers contra PostgreSQL. Hasta entonces, plain identifiers con mayúsculas o folding
dependiente del dialecto son `UNSUPPORTED`; no justifican añadir quoting flags a core.

## Semántica de bulk insert para aggregates

`bulkInsert` significa insertar, mediante COPY, columnas de la fila de la aggregate root. No es
equivalente a `CrudRepository.save`, `JdbcAggregateTemplate.insert` ni a persistir un aggregate
graph:

- no inserta child entities, `@MappedCollection`, listas, sets ni maps;
- no ejecuta entity callbacks, lifecycle events, auditing ni domain events;
- no aplica optimistic locking;
- no actualiza ID/version en la instancia;
- no realiza cascade, dirty tracking ni identity-map management;
- no usa SQL generado por Spring Data JDBC.

El resolver rechazará el tipo completo si detecta una propiedad que Spring Data interpreta como
child aggregate state. Ignorar silenciosamente esa propiedad daría una entidad aparentemente
insertada pero incompleta.

### IDs y version

- ID asignado, incluido UUID creado por la aplicación: `SUPPORTED`; se incluye la columna.
- ID database-generated (`null` o cero según `IdValueSource`): `PLANNED/PARTIAL`; toda la llamada
  debe ser homogénea, se omite la columna y el valor generado no vuelve a la instancia.
- `@Sequence`: `UNSUPPORTED` inicialmente. Spring Data obtiene el valor con un SELECT y lo incluye;
  omitirlo no garantiza que exista un default en la columna.
- ID generado por `BeforeConvertCallback`: `UNSUPPORTED`; los callbacks no se ejecutan.
- llamada que mezcla IDs proporcionados y generados: `UNSUPPORTED`; COPY usa una lista fija de
  columnas. Debe fallar, nunca escoger por el primer row sin validar los restantes.
- aggregate root con `@Version`: `UNSUPPORTED` inicialmente. Insertar sin sincronizar el valor
  inicial rompería la detección de estado y la futura semántica de optimistic locking.

La implementación generated-ID sólo se aceptará si mantiene consumo single-pass, detecta mezcla
determinísticamente, documenta el riesgo de fallo parcial fuera de transacción y prueba que la
columna omitida tiene generación real en PostgreSQL.

## Embedded, value objects y references

Un embedded se aplana a leaf columns usando paths y prefixes del mapping context. El accessor debe
devolver null para cada leaf si un parent embedded es null; `USE_NULL`/`USE_EMPTY` influye en
materialización, no autoriza inventar valores al escribir. Nested embedded requiere pruebas antes
de pasar de `PLANNED` a `SUPPORTED`.

Un value object de una sola columna es soportable si `JdbcConverter` tiene una write conversion a
un tipo relacional ya encodable y una read conversion compatible. Conversiones de un valor a
múltiples columnas no están soportadas por el mecanismo de custom converters de Spring Data JDBC.

Un FK representado como scalar ID se trata como scalar. `AggregateReference<T, ID>` se acepta sólo
si el converter oficial produce el ID relacional y el mapping no implica cargar/cascadear otro
aggregate. Una referencia a una entidad/colección dentro del aggregate se rechaza.

## Conexión y transacciones

La estrategia preferida es inyectar el `NamedParameterJdbcOperations`/`JdbcOperations` usado por
Spring Data JDBC y ejecutar el motor dentro de:

```text
JdbcOperations.execute(ConnectionCallback)
    -> transaction-bound Connection supplied by JdbcTemplate
    -> PostgresBulkJdbcOperations on that exact Connection
```

`JdbcTemplate` usa `DataSourceUtils` y participa en una transacción existente. Esta forma limita
el préstamo al callback y evita que el adapter implemente acquire/release. Si wiring real impide
obtener `JdbcOperations`, la alternativa oficial es `DataSourceUtils.getConnection(dataSource)`
con `releaseConnection` en `finally`, nunca `DataSource.getConnection()` directo.

El fragmento será `REQUIRED` y read-write. Antes de COPY valida transacción activa y no read-only.
El adapter y pgJDBC no cierran, commit, rollback, cambian auto-commit, isolation, read-only ni
crean savepoints. El mismo objeto `Connection` recibido alimenta DDL temporal, COPY, join,
materialización y cleanup.

Con `DataSourceTransactionManager`/`JdbcTransactionManager`, Spring Framework documenta soporte de
savepoints y `PROPAGATION_NESTED`; esto difiere de la baseline JPA/Hibernate, donde NESTED está
rechazado. La integración JDBC no creará savepoints y no prometerá NESTED en J1. J5 deberá probar
rollback al savepoint, lifecycle de temp tables y estado PostgreSQL antes de declararlo soportado.

Si JPA y JDBC conviven, la presencia de ambos transaction managers puede hacer ambiguo un
`@Transactional` no cualificado. El caso soportable exige un manager JDBC primario/no ambiguo o un
boundary de servicio explícitamente cualificado. La auto-configuración no debe elegir un manager
arbitrariamente.

## Bulk lookup y materialización

`BulkKeyMetadata<K>` se reutiliza sin cambios y continúa siendo explícita: no se infieren claves
desde nombres de repository methods ni desde `@Id`. La tabla temporal, COPY de keys, join y cleanup
permanecen en `PostgresBulkJdbcOperations`.

El callback de lookup recibe la misma conexión y el SQL mientras existe la tabla temporal. Dentro
de ese scope, el adapter crea un `PreparedStatement` directamente sobre esa conexión y aplica un
`EntityRowMapper<T>(persistentEntity, jdbcConverter)` público a cada row. Esto mantiene conversions
de lectura, constructores y embedded semantics. No se llama a `JdbcTemplate.query` desde dentro
del callback porque una segunda adquisición, aunque normalmente transaction-bound, haría menos
explícita la garantía de identidad física.

`EntityRowMapper` puede disparar SQL adicional para miembros del mismo aggregate. Por eso J3 debe
caracterizarlo y la baseline sólo acepta aggregate roots sin child collections/entities. No se
prometen callbacks `AfterConvert`, lifecycle events ni domain events en lookup. Si el mapper no
puede materializar de forma estable con el SQL actual, J3 se detiene: no se reemplaza con internals.

## Repository fragments y API pública

J4 congela un fragmento JDBC distinto:

```text
io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository<T>
```

El nombre coloca JDBC junto al sustantivo repository y elimina `ID` porque las operaciones no lo
usan. Extiende `BulkOperations<T>` y
conserva los mismos nombres y tipos de métodos bulk, pero su Javadoc expresa semántica JDBC, no
estado gestionado JPA. Reutilizar el FQCN actual exigiría mover una API publicada, conservar
Javadocs falsos o introducir un módulo común sin evidencia; por eso se descarta.

El fragmento se registra desde el JAR con `META-INF/spring.factories`. Su implementación implementa
`RepositoryMetadataAccess`, obtiene el domain type desde `RepositoryMethodContext` y usa únicamente
beans JDBC. Este es el mecanismo oficial para fragments externos en Spring Data Commons 3.5.

JPA y JDBC pueden estar juntos en el classpath porque cada repository opta por un fragment type
distinto. Un mismo repository no debe extender ambos fragments: aportan firmas equivalentes con
semántica/lifecycle diferentes y el orden de composición decidiría cuál gana. Ese caso será
rechazado o documentado como `UNSUPPORTED`. También se probará strict repository configuration con
repositories JPA y JDBC separados.

## Auto-configuración y starters

La auto-configuración JDBC futura tendrá condiciones aisladas sobre `JdbcConverter`,
`JdbcOperations`/`NamedParameterJdbcOperations`, `DataSource`, pgJDBC y el adapter JDBC. Hará
back-off ante resolver/beans del usuario y ante múltiples candidates no cualificados. No importará
JPA ni Hibernate.

Se compararon tres opciones:

| Opción | Ventaja | Coste/riesgo | Decisión |
| --- | --- | --- | --- |
| Extender auto-config/starter actuales | Menos artifacts | Contamina ambos stacks y aumenta ambigüedad | Rechazada |
| Auto-config común con configs internas JPA/JDBC | Puede compartir wiring pequeño | El artifact queda dependiente de dos adapters | Rechazada por ahora |
| Auto-config y starter JDBC separados | Dependencias/condiciones claras y coexistencia explícita | Dos artifacts nuevos | Elegida |

`ApplicationContextRunner`, `FilteredClassLoader`, back-off y contextos JPA-only/JDBC-only/both son
gates de J6. El starter JDBC dependerá del starter oficial `spring-boot-starter-data-jdbc`, del
adapter JDBC y de pgJDBC con el mismo criterio de runtime usado por el starter JPA.

## Observabilidad y excepciones

Se reutilizan nombres de observación, low-cardinality tags, conteos y semántica fail-open definidos
en [observability.md](observability.md). El helper existente es package-private y vive en un módulo
JPA-specific; no se mueve en J0. El adapter JDBC tendrá inicialmente una implementación interna
equivalente. Sólo evidencia de duplicación estable justificaría después un módulo común.

Los errores del motor mantienen `BulkException` y la cadena hasta `SQLException`/SQLState. Mapping,
aggregate no soportado, ID mezclado, read-only y wiring ambiguo son errores de uso y se exponen como
`InvalidDataAccessApiUsageException` conservando la causa. No se crea otra jerarquía. Las
excepciones de `JdbcOperations.execute` conservan la traducción estándar de Spring; no se traducen
dos veces ni se incluyen valores de entidades/keys en mensajes.

## Matriz de mapping propuesta

`SUPPORTED` significa objetivo de la primera versión JDBC sólo después de sus tests; J0 no entrega
soporte productivo. `PLANNED/PARTIAL` exige una restricción adicional. `UNSUPPORTED` falla pronto.

| Scenario | Mechanism | Support | Reason | Official evidence | Risk | Required tests |
| --- | --- | --- | --- | --- | --- | --- |
| Simple scalar String/numeric/boolean | leaf property + `JdbcConverter` | SUPPORTED | Encoders existentes reciben tipo relacional | JdbcConverter Javadoc | precision/coercion | round-trip por tipo y null |
| `LocalDate` | `JdbcConverter` -> relational temporal type | SUPPORTED | Encoder temporal existente | JdbcConverter/mapping docs | timezone accidental | round-trip/null |
| `Instant` | `JdbcConverter` -> relational temporal type | SUPPORTED | Converter fija representación JDBC | JdbcConverter/mapping docs | precision/timezone | round-trip UTC/null |
| `BigDecimal` | relational scalar | SUPPORTED | Encoder decimal existente, sin locale | JdbcConverter Javadoc | scale/precision | boundary round-trip |
| `UUID` | relational scalar or custom conversion | SUPPORTED | Encoder UUID existente | JdbcConverter Javadoc | UUID domain wrapper | assigned ID/value round-trip |
| `byte[]` | relational binary scalar | SUPPORTED | Encoder `bytea` existente | JdbcConverter Javadoc/current engine | copies/memory | empty/null/binary round-trip |
| Enum con conversión default | `writeJdbcValue` | SUPPORTED | Converter decide representación, no el enum runtime | JDBC mapping docs | cambio de converter | enum name/custom round-trip |
| Implicit table | `RelationalPersistentEntity.getTableName()` | SUPPORTED | Mapping context aplica la convención | RelationalPersistentEntity/mapping docs | naming drift | convention integration |
| Explicit `@Table` | persistent entity identifier | SUPPORTED | Metamodelo resuelve el override | RelationalPersistentEntity Javadoc | quoting | explicit integration |
| Schema | `getQualifiedTableName()` components | SUPPORTED | API incluye schema desde 3.0 | RelationalPersistentEntity Javadoc | component loss | schema integration |
| Explicit `@Column` | `RelationalPersistentProperty.getColumnName()` | SUPPORTED | Metamodelo resuelve el override | RelationalPersistentProperty Javadoc | duplicate names | explicit integration |
| Custom `NamingStrategy` | effective mapping context identifiers | PLANNED/PARTIAL | Debe consumirse el resultado, no reevaluarse | mapping/configuration docs | case folding | quoted/lower/plain variants |
| NamingStrategy default quoted | mapping context identifiers | SUPPORTED | Coincide con always-quote pgJDBC | mapping docs | case sensitivity | generated and explicit DDL |
| Plain lowercase identifiers | `SqlIdentifier` reference | PLANNED/PARTIAL | PostgreSQL physical name suele coincidir | SqlIdentifier Javadoc | folding mismatch | forceQuote(false) integration |
| Plain mixed/upper-case strategy | identifier rendering | UNSUPPORTED | Core no conserva quoted flag/folding | SqlIdentifier Javadoc | wrong physical object | fail-fast characterization |
| Assigned `@Id` | include ID column | SUPPORTED | Valor pertenece a la instancia | IdValueSource Javadoc | duplicates | assigned numeric/UUID |
| Database-generated `@Id` | omit ID for homogeneous call | PLANNED/PARTIAL | COPY can use DB default; no key return | persistence docs | mixed rows/no default | identity/default/no-sync |
| `@Sequence` ID | Spring pre-insert select | UNSUPPORTED | COPY path no ejecuta callback/select | sequence docs | null/not-null violation | rejection |
| Callback-generated UUID | lifecycle callback | UNSUPPORTED | bulk path intentionally bypasses callbacks | lifecycle docs | silent missing ID | rejection |
| `@Version` root | optimistic-lock lifecycle | UNSUPPORTED | no initial value sync or locking | persistence docs | wrong entity state | rejection |
| `@Transient` | property flag | SUPPORTED | Excluded from persistence row | mapping docs | accidental inclusion | metadata test |
| `@InsertOnlyProperty` scalar | property flag, normal insert column | SUPPORTED | Operation is insert only | property Javadoc | defaults | round-trip |
| Nullable scalar | converted null | SUPPORTED | Metadata supplies type for null | JdbcConverter Javadoc | SQL null/default confusion | null round-trip |
| Single embedded | persistent property path + prefix | SUPPORTED | Same root table | mapping docs | null parent | prefix/USE_NULL/USE_EMPTY |
| Nested embedded | nested paths | PLANNED/PARTIAL | Model permits it; traversal needs proof | mapping docs | duplicate names/nulls | multi-level tests |
| Single-column value object | custom write/read converters | SUPPORTED | Converts property to one relational value | custom conversion docs | asymmetric converters | converter round-trip |
| Multi-column converter | none | UNSUPPORTED | Official custom conversion is property-to-one-value | custom conversion docs | lossy mapping | rejection |
| Simple FK scalar | scalar property | SUPPORTED | No graph semantics | mapping docs | referential failure | FK success/failure |
| `AggregateReference<T, ID>` | JdbcConverter to ID | PLANNED/PARTIAL | Official reference wrapper maps as ID | mapping docs | API evolution/converter type | insert + lookup |
| Child entity / `@MappedCollection` | aggregate graph | UNSUPPORTED | More than root row/table | persistence docs | partial aggregate | metadata rejection |
| List/Set/Map children | additional tables/backrefs | UNSUPPORTED | COPY call targets one table | mapping docs | orphan/incomplete data | metadata rejection |
| Aggregate graph persistence | Spring Data aggregate writer/actions | UNSUPPORTED | Requiere varias tablas y lifecycle | persistence docs | partial aggregate | whole-type rejection |
| Database default on non-ID null | COPY NULL | UNSUPPORTED as default semantics | Column is present, so default is not invoked | PostgreSQL COPY semantics/current engine | unexpected null | document and test NULL |
| Custom unsupported relational type | converter + encoder registry | UNSUPPORTED | No generic string fallback | current encoding contract | corrupt representation | deterministic error |
| Composite embedded ID | flattened ID columns | PLANNED/PARTIAL | Metadata exists, generated policy is harder | mapping docs | new-state detection | assigned-only tests |
| Inherited persistent scalar | mapping context path | SUPPORTED | Persistent metadata includes inherited properties | mapping metadata API | accessor order | superclass round-trip |

## Matriz de escenarios explícitamente no soportados

| Scenario | Detection point | Required behavior | Future condition |
| --- | --- | --- | --- |
| Aggregate con child collection/entity | Metadata resolution | Fail before connection/COPY | Multi-table bulk design and ADR |
| Versioned aggregate | Metadata resolution | Fail with usage error | Define version initialization/sync |
| Sequence/callback-generated ID | Metadata/call preparation | Fail before COPY | Official callback/sequence integration without internals |
| Mixed generated/assigned IDs | Streaming validation | Fail, preserve cause and tx semantics | Separate explicit calls/policy |
| Both fragment interfaces on one repository | Repository bootstrap or documented guard | Reject ambiguity | Explicit composition contract |
| Read-only or no suitable JDBC transaction | Fragment precondition | Reject before COPY | No relaxation planned |
| Multiple DataSources/transaction managers without qualifier | Auto-config/first invocation | Back off or fail descriptively | User selects candidate |
| Plain identifier whose rendered name differs when quoted | Metadata validation | Reject/mark unsupported | Proven canonical adapter mapping |
| Mapper requiring child relation SQL | Lookup characterization | Reject root type | Supported aggregate materializer design |
| Entity callbacks/auditing/domain events expected | Public contract | Do not invoke; document | Separate opt-in lifecycle design |
| Generated IDs/version returned to objects | Result contract | Never claim synchronization | New API and engine capability |
| R2DBC, non-PostgreSQL, Boot 4 / Spring Data 4 | Dependency/runtime gate | Unsupported | Separate roadmap |

## Compatibilidad propuesta

La primera línea JDBC conserva la política de la release candidate y añade Spring Data JDBC:

| Axis | Minimum lane | Current lane | Notes |
| --- | --- | --- | --- |
| Java | 17 | 21; 25 informational | Same project baseline |
| Spring Boot | 3.5.0 | 3.5.16 | Boot-managed dependencies only |
| Spring Data JDBC/Relational | 3.5.0 | 3.5.13 | Compile/test both BOM endpoints |
| Spring Framework JDBC | 6.2.7 | 6.2.19 | Comes from Boot 3.5 BOM endpoints |
| pgJDBC | 42.7.5 | 42.7.13 | Existing tested range |
| PostgreSQL | 15 | 18 | Existing compatibility lanes |

Spring Boot/Data 4 es otra generación y no entra en esta artifact line. La API
`AggregateReference` ya anuncia cambios de implementación hacia 4.0, otra razón para validar sólo
su conversión pública en 3.5 y no acoplarse a su implementation class.

## Estrategia de tests

Los gates J1+ obligatorios son:

- unit tests de metadata para orden, cache, accessors, nulls, inheritance, embedded y rejection;
- contract tests de conversion que prueben que valor y `ColumnMetadata.javaType()` son
  relacionales y compatibles incluso con null;
- Testcontainers PostgreSQL para insert/lookup, schema, quoted names, custom conversions,
  assigned/generated IDs, references y fallos;
- identidad de conexión con `pg_backend_pid()` durante callback, COPY, join y mapper;
- transacciones REQUIRED, outer rollback, REQUIRES_NEW, read-only, rollback-only y conexión reusable;
- NESTED con savepoint como characterization, sin convertirlo en soporte automáticamente;
- repository fragment discovery en Spring Data JDBC 3.5.0/3.5.13;
- classpaths JDBC-only, JPA-only, both stacks y repository que intenta ambos fragments;
- auto-config `ApplicationContextRunner`, missing classes/beans, user back-off, single/multiple
  DataSources y observability absent/present;
- matrix Java/Boot/Spring Data/pgJDBC/PostgreSQL alineada con compatibility policy;
- auditorías de imports/bytecode que prohíban Spring en core e internals Spring Data en el adapter.

Los docs-only de J0 requieren `git diff --check` y los gates documentales existentes, no el reactor
completo.

## Benchmark futuro

Después de correctness y antes de declarar performance, J8 comparará sobre el mismo PostgreSQL:

- `CrudRepository.saveAll` vs JDBC batch insert vs postgres-bulk JDBC COPY;
- tamaños 1k, 10k, 100k y 1M, con warmup y forks existentes;
- scalar root, custom converter y embedded root;
- lookup simple/compuesto con hit ratios y duplicates controlados;
- throughput, elapsed time, rows/s, heap/GC y número de statements/connections;
- overhead de materialización root-only y observability on/off.

No se compararán aggregate graphs porque el bulk adapter no ofrece la misma semántica.

## Compatibilidad hacia atrás

No se renombra ni modifica ningún artifact, package o public type existente. El adapter JPA y sus
starters conservan comportamiento. Los artifacts JDBC son opt-in. Core y pgJDBC no cambian. Una
aplicación existente sólo observará nueva documentación.

Antes de implementación se debe reservar nombres definitivos y extender el baseline de API sólo
con los artifacts nuevos. No se reabre `0.1.0` ni se publica nada durante J0–J8.

## Riesgos y mitigaciones

| Risk | Impact | Mitigation/gate |
| --- | --- | --- |
| Diferencia entre domain type y relational type | Encoding incorrecto, especialmente nulls | `JdbcConverter` + contract tests; sin runtime inference |
| Quoting/case folding perdido en boundary neutral | Tabla/columna equivocada | Matrix PostgreSQL; scope inicial quoted/lowercase |
| Aggregate parcial tratado como completo | Pérdida de children/callback semantics | Reject graph mappings; wording explícito root-row |
| Generated ID heterogéneo | Column list inconsistente/partial write | Policy homogénea y fail-fast cuando sea posible |
| Version no sincronizada | Saves posteriores incorrectos | Versioned roots unsupported |
| EntityRowMapper ejecuta SQL adicional | Conexión/lifecycle inesperados | Characterization y root-only guard |
| Fragmento doble o store ambiguity | Implementación equivocada | Tipos separados y both-classpath tests |
| Transaction manager equivocado | COPY fuera de tx o en conexión distinta | Single candidate/back-off, PID tests, docs de qualifier |
| Duplicación de observability | Drift de tags | Contract tests; extraer sólo con evidencia |
| Dependencia en API removida en 4.x | Upgrade bloqueado | Baseline 3.5 explícita; no internals |

## Preguntas abiertas que requieren evidencia J1+

1. ¿Puede el mapping de `SqlIdentifier` reproducir quoted/plain PostgreSQL sin cambiar core?
2. ¿Qué orden público y estable debe usarse para enumerar todos los root leaf paths?
3. ¿`PersistentPropertyPathAccessor` ofrece el null traversal exacto para nested embedded o hace
   falta componer accessors públicos?
4. ¿Cómo rechazar mixed ID source antes de iniciar COPY sin romper el contrato single-pass?
5. ¿`EntityRowMapper` materializa todos los root-only mappings objetivo sin lifecycle lateral?
6. ¿Cómo seleccionar explícitamente el transaction manager del repository JDBC cuando ambos
   stacks están activos?
7. **Resuelta J4:** `PostgresBulkJdbcRepository<T>` ofrece el import más legible y evita un `ID`
   genérico sin uso; ADR-028 congela la decisión.
8. ¿NESTED conserva temp table/COPY correctamente tras rollback a savepoint en PostgreSQL 15–18?

## Evidencia oficial

- [Spring Data JDBC mapping](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/mapping.html)
- [Spring Data JDBC persistence and optimistic locking](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/entity-persistence.html)
- [Spring Data JDBC sequences](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/sequences.html)
- [Spring Data JDBC transactions](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/transactions.html)
- [`JdbcConverter` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/jdbc/core/convert/JdbcConverter.html)
- [`RelationalPersistentEntity` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/RelationalPersistentEntity.html)
- [`RelationalPersistentProperty` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/mapping/RelationalPersistentProperty.html)
- [`SqlIdentifier` 3.5 API](https://docs.spring.io/spring-data/relational/reference/3.5/api/java/org/springframework/data/relational/core/sql/SqlIdentifier.html)
- [Spring Data repository fragments](https://docs.spring.io/spring-data/commons/reference/repositories/custom-implementations.html)
- [Spring Framework JDBC connections](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html)
- [Spring Boot SQL/JDBC support](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html)
- [Spring Boot 3.5 managed coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
