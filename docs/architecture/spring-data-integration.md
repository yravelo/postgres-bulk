# Integración Spring Data JPA

## Uso

El fragmento es opt-in y no reemplaza la base global de Spring Data:

```java
interface ProductRepository
    extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}
```

Con `postgres-bulk-spring-boot-starter`, Boot aporta automáticamente el bridge de metadata. Sin
Boot, la aplicación lo declara explícitamente:

```java
@Bean
JpaEntityMetadataResolver bulkMetadataResolver() {
    return JpaEntityMetadataResolver.caching(HibernateEntityMetadataResolver::new);
}
```

Después puede ejecutar `bulkInsert(items)`, `bulkInsert(items, options)` y
`findAllByBulkKey(keys, keyMetadata)`. El fragmento se registra desde el JAR con
`spring.factories`; no requiere una implementación en el package de la aplicación ni una factory
custom.

La autoconfiguración no cambia el mecanismo de registro: `spring.factories` continúa cargando el
fragmento externo de Spring Data y `AutoConfiguration.imports` registra solamente el bean de
composición Boot. Un resolver del usuario gana por back-off. Con varias persistence units, el
bridge cachea por identidad de factory y `JpaContext` decide por tipo gestionado en cada llamada.

## Transacciones y conexión

Cada método declara `REQUIRED` read-write. Sin transacción exterior, el proxy repository crea una;
con una exterior participa en ella. Una exterior `readOnly` falla antes de obtener conexión. El
adapter obtiene la conexión subyacente del `Session` Hibernate mediante `doReturningWork`; DDL,
COPY, JOIN y native query quedan dentro de ese mismo scope físico. La conexión permanece prestada.

`REQUIRES_NEW` usa la conexión de la transacción interna. `NESTED` depende del transaction manager
y no forma parte del contrato actual. Un iterable vacío se inspecciona una sola vez y no abre
conexión, aunque el interceptor pueda haber creado una transacción lógica lazy.

## Persistence context

COPY evita el lifecycle ORM: no ejecuta callbacks, no genera IDs en objetos, no hace dirty
checking y no convierte los objetos insertados en managed. La integración nunca llama `flush()` ni
`clear()`. El lookup usa native query con flush mode `COMMIT`, por lo que el caller debe hacer flush
explícito si necesita que cambios JPA pendientes participen en la búsqueda. Los resultados del
lookup sí se materializan como entidades mediante JPA/Hibernate.

## Lookup

La llamada recibe `BulkKeyMetadata<K>` explícita. Una key simple puede ser `String`, `UUID`, etc.;
una compuesta debe ser un record/value object con componentes ordenados. No se analizan nombres de
métodos derivados. Duplicados de input no duplican resultados, missing keys se omiten, nulls se
rechazan y no se garantiza orden.
