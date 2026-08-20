# ADR-028: API pública de repository Spring Data JDBC

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

J2/J3 entregaron operaciones internas root-only pero el consumidor todavía debía conocer el
coordinador package-private. J4 necesita un fragmento reusable desde un artifact externo, resolver
el domain type con APIs oficiales y coexistir nominalmente con el fragmento JPA ya publicado sin
crear una API común que prometa semántica equivalente entre stores.

## Decisión

- Publicar `PostgresBulkJdbcRepository<T>` en
  `io.ybr.postgresbulk.springdata.jdbc.repository`.
- No añadir `<ID>`: ninguna operación usa ese tipo y `CrudRepository<T, ID>` ya lo expresa.
- Extender `BulkOperations<T>` y exponer la misma pareja de insert más lookup explícito con
  `BulkKeyMetadata<K>`.
- Registrar un fragmento externo mediante `META-INF/spring.factories`.
- Mantener su implementación package-private, con constructor de infraestructura, e implementar
  `RepositoryMetadataAccess` para obtener el domain type desde `RepositoryMethodContext`.
- Inyectar sólo un `JdbcOperations` y `SpringDataJdbcEntityMetadataResolver`; la ambigüedad de
  candidates falla mediante las reglas estándar de Spring.
- Declarar `@Transactional` `REQUIRED` en contrato e implementación. El proxy crea o une la
  transacción; el coordinador conserva los guards read-write/físicos de J2/J3.
- Declarar NESTED condicionado en Javadocs sin añadir métodos: sólo managers JDBC sobre el mismo
  `DataSource`, con savepoints propiedad del manager.
- No traducir excepciones artificialmente.
- Rechazar explícitamente un repository que herede a la vez el fragmento JDBC y el JPA. La
  coexistencia de ambos artifacts con repositories separados permanece válida.

## Consecuencias

Un consumidor declara un repository normal sin implementación local y recibe insert/lookup
root-only. Sólo la interface se incorpora a la API pública; el tipo de implementación no aparece
en el baseline binario. La firma JDBC no es source-compatible con la JPA por sustitución de import,
lo que evita una migración engañosa: JDBC no tiene persistence context ni lifecycle ORM.

La configuración explícita debe aportar infraestructura Spring Data JDBC y el resolver. Boot,
starter, selección multi-DataSource/manager, observability y una posible API common permanecen
fuera de J4.

## Evidencia J5

La API pública permanece binariamente idéntica. Contextos reales prueban selección explícita de
`DataSource`, `JdbcOperations` y transaction manager; ante varios candidates la librería no
adivina. Los Javadocs documentan NESTED condicionado y ausencia de retry. La coexistencia con el
fragmento JPA funciona para repositories separados, pero no promete atomicidad al anidar managers
locales distintos.

## Evidencia J6

Boot descubre el fragmento sin `@EnableJdbcRepositories` propio de postgres-bulk. La implementación
sigue package-private y queda no-final para admitir el proxy class-based transaccional por defecto;
no aparece en el baseline `javap -public`. El único tipo público nuevo de J6 es la clase de
infraestructura `PostgresBulkJdbcAutoConfiguration`; las firmas del fragmento no cambian.

## Alternativas

| Alternativa | Resultado |
| --- | --- |
| `JdbcPostgresBulkRepository<T, ID>` | Rechazada: lectura menos natural y `ID` sin uso |
| `PostgresBulkRepositoryJdbc<T, ID>` | Rechazada: sufijo store poco idiomático y `ID` sin uso |
| Reutilizar `PostgresBulkRepository` JPA | Rechazada: import/FQCN y semántica ambiguos |
| Implementación pública como JPA | Rechazada: la carga externa 3.5 funciona con clase package-private |
| Resolver `T` por reflection propia | Rechazada: `RepositoryMethodContext` ofrece metadata oficial |
| Factory/base repository global | Rechazada: rompe opt-in y altera repositories ajenos |

## Evidencia

Spring Data Commons 3.5 documenta `spring.factories`, `RepositoryMetadataAccess` y
`RepositoryMethodContext` para extensions externas genéricas. El integration test crea tres
repositories reales sin implementations locales y prueba PostgreSQL, transacciones y domain types
con la clase package-private. El test de candidates demuestra fallo determinista ante dos
`JdbcOperations`; Enforcer mantiene JPA/Hibernate/Boot fuera del módulo.

## Revisión J8 (2026-08-20)

J8 midió directamente `PostgresBulkJdbcRepository.bulkInsert`; el adapter warm no mostró coste
temporal o de allocation consistente frente a low-level COPY. No se añadió método, tipo o promesa
de rendimiento. La API baseline permanece estable y el ADR `ACCEPTED`.
