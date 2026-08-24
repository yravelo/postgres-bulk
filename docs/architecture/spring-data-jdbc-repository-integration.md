# Repository fragment público para Spring Data JDBC

## Contrato J4 y overloads MS5

J4 publica `PostgresBulkJdbcRepository<T>` en
`io.ybr.postgresbulk.springdata.jdbc.repository`. El nombre mantiene `PostgresBulk` como capacidad
y sitúa `Jdbc` junto a `Repository`, evita colisión con el fragmento JPA
`PostgresBulkRepository<T, ID>` y deja inequívoco el import cuando ambos artifacts están presentes.
El fragmento usa sólo `<T>`: sus tres operaciones no reciben, producen ni resuelven el tipo de ID.

```java
interface ProductRepository
    extends CrudRepository<Product, UUID>, PostgresBulkJdbcRepository<Product> {}
```

La superficie pública es deliberadamente pequeña:

```java
BulkWriteResult bulkInsert(Iterable<? extends T> items);

BulkWriteResult bulkInsert(
    Iterable<? extends T> items,
    BulkInsertOptions options
);

<K> List<T> findAllByBulkKey(
    Iterable<? extends K> keys,
    BulkKeyMetadata<K> keyMetadata
);

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

MS5 añade sólo las tres últimas formas. La variante corta target-first conserva inequívocas las
llamadas históricas con `null` al overload de options y mantiene simetría con JPA/MS4. El target es
schema-qualified, root-only, local a la invocación y no introduce resolución tenant.

No se publica la implementación, un factory, una DSL de keys ni configuración Boot. El resolver
de metadata introducido en J1 continúa siendo el único tipo público de infraestructura JDBC.

## Descubrimiento y delegación

El JAR registra el par interface/implementation mediante `META-INF/spring.factories`, el mecanismo
oficial para fragments externos. La implementación package-private implementa
`RepositoryMetadataAccess`; Spring Data habilita entonces `RepositoryMethodContext` solamente en
los repositories que optan por el fragmento. De su `RepositoryMetadata` obtiene el domain type
oficial, sin fields, análisis de genéricos propio ni reflection de entidades.

```text
ProductRepository proxy
    -> DefaultPostgresBulkJdbcOperations (external fragment, package-private)
    -> DefaultSpringDataJdbcBulkOperations (J2/J3)
    -> PostgresBulkJdbcOperations
    -> PostgreSQL COPY / temporary-table JOIN
```

La implementación recibe por constructor un único `JdbcOperations` y un
`SpringDataJdbcEntityMetadataResolver`. Spring resuelve ambos con inyección normal. Dos candidatos
`JdbcOperations` sin `@Primary`/qualifier producen `NoUniqueBeanDefinitionException`; la librería
no elige por nombre, orden ni contexto global. La selección configurable multi-DataSource queda
para J5/J6.

## Configuración explícita, sin Boot

J4 no añade autoconfiguración. Una aplicación Spring Data JDBC declara su infraestructura normal y
el resolver que conecta el `JdbcConverter` efectivo con postgres-bulk:

```java
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableJdbcRepositories
class JdbcConfiguration extends AbstractJdbcConfiguration {

    @Bean
    DataSource dataSource() {
        return applicationDataSource();
    }

    @Bean
    NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    JdbcOperations jdbcOperations(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    SpringDataJdbcEntityMetadataResolver postgresBulkJdbcMetadataResolver(
            JdbcConverter converter,
            JdbcCustomConversions conversions) {
        return new SpringDataJdbcEntityMetadataResolver(converter, conversions);
    }
}
```

El ejemplo está compilado y ejercitado, con equivalentes concretos, por
`PostgresBulkJdbcRepositoryIT`; no presupone Spring Boot, starter o beans globales ocultos.

## Transacciones, input y errores

Los métodos de la interface y de la implementación declaran `@Transactional` con propagación
Spring default `REQUIRED`, read-write. Una llamada directa al proxy crea la transacción; una llamada
desde un service se une al scope existente. `REQUIRES_NEW` funciona por suspensión normal del
`JdbcTransactionManager`. Una transacción read-only, una conexión autocommit o física read-only se
rechazan con `InvalidDataAccessApiUsageException`. PostgreSQL Bulk no inicia, confirma, revierte,
cierra ni reconfigura la conexión.

Input vacío hace un único lookahead y retorna `(0,0)`/lista vacía sin metadata persistente,
conexión ni SQL. Input no vacío conserva single-pass y O(1) adicional para insert/keys. El proxy
puede crear una transacción lógica lazy antes de que el fragmento detecte el vacío.

No existe traducción propia de excepciones:

- argumentos/elementos inválidos conservan `NullPointerException` o `IllegalArgumentException`
  salvo traducción estándar del proxy Spring;
- precondiciones Spring/mapping usan `InvalidDataAccessApiUsageException`;
- fallos del engine conservan `BulkException` y su cause JDBC/SQLState;
- fallos de acceso Spring siguen la jerarquía `DataAccessException`.

## Semántica frente a Spring Data JDBC

`bulkInsert` escribe sólo la fila de la aggregate root. No equivale a `CrudRepository.save`:
children, collections, callbacks, auditing, events, versioning y graph persistence no se ejecutan.
IDs assigned se copian; IDs generados se omiten y no se sincronizan en el objeto. Mezclar ambas
políticas en una llamada se rechaza.

Lookup recibe `BulkKeyMetadata<K>` explícita, materializa roots con el `EntityRowMapper` y
`JdbcConverter` efectivos dentro del scope de la temporal y retorna una lista desconectada. No hay
orden garantizado, streaming, derivación de keys ni carga de children.

## Coexistencia de stores

Los artifacts JDBC y JPA tienen packages, fragments e implementaciones separados. El módulo JDBC
no depende de JPA/Hibernate/Boot y el artifact JPA no cambia, por lo que un classpath puede contener
ambas bibliotecas sin activar una sobre repositories del otro store. Un mismo repository no debe
extender ambos fragments: las firmas colisionan y el orden de composición de Spring decidiría qué
fragment ejecuta. J4 detecta el fragmento JPA por la interface declarada y falla explícitamente con
`InvalidDataAccessApiUsageException` antes de delegar.

La selección completa con varios transaction managers/DataSources, `NESTED`, auto-configuración y
tests dual-store de un único application context pertenecen a J5/J6. J4 no adelanta esos wiring
contracts.

## Evidencia

Unit tests prueban default options, delegación y fallo ante varios `JdbcOperations`. Un contexto
Spring Data JDBC real y PostgreSQL prueban discovery externo, llamada sin implementation local,
insert default/explicit, generated/assigned/mixed IDs, lookup simple/compuesto, converters, empty
sin tabla física, rollback, read-only, `REQUIRES_NEW`, SQLState, dos domain types y concurrencia.
El suite JPA se ejecuta además con el JAR JDBC como dependencia test-only y prueba que discovery,
insert y lectura JPA siguen operativos. El POM publicado de cada adapter conserva sólo su stack;
Enforcer valida el classpath JDBC-only y el grafo productivo valida el consumo JPA-only.
Los suites J2/J3 continúan cubriendo embedded, nested embedded, references, connection identity,
cleanup, query count y fallos durante el pipeline delegado.

MS5 añade evidencia pública con el mismo proxy para A/B: insert/lookup aislados, concurrencia,
transacción multi-schema, REQUIRES_NEW/NESTED, read-only, IDs, mappings, quoting, conflictos,
SQLStates, pool/cleanup y reuse por identidad de metadata. La implementación continúa sin fields o
caches de target.

Fuentes oficiales consultadas:

- [Spring Data Commons 3.5 — custom repository implementations](https://www.springframework.org/spring-data/data-commons/reference/3.5/repositories/custom-implementations.html)
- [Spring Data JDBC 3.5 — configuration](https://docs.spring.io/spring-data/relational/reference/3.5/jdbc/getting-started.html)
- [Spring — external repository fragment announcement](https://spring.io/blog/2024/12/03/extending-spring-data-repositories-just-got-easier/)
