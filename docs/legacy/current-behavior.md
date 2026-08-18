# Comportamiento actual del legacy

**Estado:** inventario de referencia, 2026-08-18. El contenido de `legacy/` no forma parte del reactor nuevo.

## Evidencia inspeccionada

Se inspeccionaron 8 fuentes Java (802 líneas) bajo `../../legacy/factory/` y un descriptor IntelliJ. No hay `pom.xml`, tests, configuración Spring, entidades de prueba ni las clases de excepción importadas; por ello el fragmento no es compilable ni ejecutable de forma autónoma. Los nombres de paquete mezclan `com.pepe...` con una dependencia corporativa `AmigaJpaRepository`.

## API existente

`BulkCopyRepository<T, I>` extiende `AmigaJpaRepository<T, I>` y añade:

```java
void saveWithCopy(List<T> entities);
void saveWithCopy(List<T> entities, int batchSize);
List<T> findAllByUniqueKeyUsingCopy(List<T> entities);
```

La integración sustituye la clase base de los repositorios Spring Data mediante `JpaRepositoryFactoryBean`, `JpaRepositoryFactory` y `SimpleJpaRepository`. Inyecta un único `DataSource` y usa el `EntityManager` de la factory.

## Bulk insert observado

1. Una lista nula o vacía es un no-op, incluso si `batchSize <= 0`.
2. Sin tamaño explícito se utiliza el tamaño total como un único batch.
3. Los campos incluidos son exclusivamente campos declarados con `@CopyField`; se ordenan alfabéticamente por nombre Java.
4. Los nombres físicos se resuelven con `@Column`, después con metadata interna de Hibernate y finalmente con conversión camelCase a snake_case. El atributo `CopyField.column()` existe pero no se consulta.
5. Se obtiene la conexión con `DataSourceUtils`, se hace `unwrap(PGConnection.class)` y se crea un `copyIn` por batch.
6. Cada entidad se materializa como una línea CSV UTF-8 y se escribe a `PGCopyOutputStream`.
7. Se libera la conexión mediante `DataSourceUtils.releaseConnection`.
8. No se devuelve el número que reporta COPY ni un resultado por batch.

Los batches son vistas `List.subList`; se procesan secuencialmente. Fuera de una transacción, la atomicidad depende de autocommit y puede haber batches confirmados antes de un fallo posterior. Dentro de una transacción Spring compatible, `DataSourceUtils` puede reutilizar la conexión vinculada.

## Bulk lookup observado

1. Una lista nula o vacía produce una lista inmutable vacía.
2. La entrada son entidades completas; sus campos declarados con `@UniqueKeyField` se usan como claves.
3. Crea una tabla temporal `tmp_<tabla>_uk_<nanoTime>` con `ON COMMIT DROP`.
4. Intenta reconstruir cada tipo buscando un atributo Hibernate mediante snake_case → camelCase; usa el `friendlyName` JDBC y cae a `TEXT`.
5. Carga las claves en la tabla temporal con COPY CSV.
6. Ejecuta un `SELECT a.* ... JOIN ...` mediante una native query de JPA.

La tabla se determina con `@Table.name` o, en su ausencia, con el nombre de entidad del metamodelo. No se incorpora `@Table.schema`. Las claves duplicadas pueden duplicar filas del resultado; los componentes nulos no hacen match debido a `=`.

## Serialización observada

- Todos los valores usan `toString()`.
- Una asociación cuyo tipo concreto está anotado con `@Entity` se sustituye por un campo `@Id` declarado directamente.
- `null` se escribe como campo CSV vacío sin comillas.
- `""` también termina como campo vacío sin comillas, por lo que se interpreta como `NULL` con los defaults de COPY CSV.
- Se duplican comillas y se entrecomillan comas, comillas y LF; CR aislado no activa el entrecomillado.
- No existe contrato explícito para zona horaria, locale, enums, binarios, converters, arrays ni JSON.

## Metadata observada

Hay caches globales por `Class<?>` para campos COPY, claves e ID. La reflexión sólo usa `getDeclaredFields()`: no cubre `@MappedSuperclass`, property access, `@EmbeddedId`, proxies ni miembros heredados. Parte de la resolución usa APIs internas y específicas de Hibernate 6 (`SessionFactoryImplementor` y mapping metamodel).

## Excepciones, logging y observabilidad

Las operaciones capturan cualquier `Exception` y la envuelven en tipos específicos del proyecto original, conservando la causa. Esos tipos no están presentes. Se registra inicio/batches/final y errores; no hay métricas, eventos de progreso ni política documentada de cardinalidad. Los mensajes no imprimen entidades, lo cual sí debe preservarse.

## Capacidades que deben preservarse como comportamiento, no como diseño

- Inserción de grandes volúmenes mediante PostgreSQL COPY.
- Batching configurable y determinista.
- Lookup por clave simple o compuesta mediante tabla temporal + COPY + JOIN.
- Participación correcta en transacciones Spring.
- Resolución de mapping físico desde el proveedor ORM.
- UTF-8 y escape CSV correcto.
- Integración idiomática y opt-in con repositorios Spring Data.

No se preservan como contrato los nombres `saveWithCopy`, `@CopyField`, `@UniqueKeyField`, la entrada de lookup mediante entidades completas, la herencia corporativa ni el reemplazo global de la repository factory.

## Trazabilidad del snapshot

Los SHA-256 se registraron durante la inspección. Ejemplos: `BulkCopyRepositoryImpl.java` = `28a5309b...ab5f13`; `PersistenceUtils.java` = `605c361b...2901d05d`. El snapshot canónico permanece en `../../legacy/`; cualquier cambio futuro debe actualizar fecha, hashes y este documento.
