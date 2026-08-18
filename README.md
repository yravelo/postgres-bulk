# PostgreSQL Bulk for Spring

Bulk insert y lookup tipado para Spring Data JPA sobre PostgreSQL `COPY`, sin sustituir
`JpaRepository` ni separar la operación de la transacción JPA activa.

## Getting Started en cinco minutos

Requisitos: Java 17+, Spring Boot 3.5 y PostgreSQL. El proyecto aún usa la versión de desarrollo
`0.1.0-SNAPSHOT`.

Maven:

```xml
<dependency>
  <groupId>io.github.postgresbulk</groupId>
  <artifactId>postgres-bulk-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Gradle:

```groovy
implementation "io.github.postgresbulk:postgres-bulk-spring-boot-starter:0.1.0-SNAPSHOT"
```

Define una entidad normal y opta al fragmento en el repositorio:

```java
@Entity
@Table(name = "product")
class Product {
  @Id Long id;
  @Column(unique = true, nullable = false) String sku;
  @Column(nullable = false) String name;
}

interface ProductRepository
    extends JpaRepository<Product, Long>, PostgresBulkRepository<Product, Long> {}
```

El starter registra automáticamente el bridge de metadata Hibernate. No hace falta una clase
`@Configuration`, una factory de repositorios ni un bean propio:

```java
@Transactional
void load(ProductRepository products, List<Product> input) {
  BulkWriteResult result =
      products.bulkInsert(input, BulkInsertOptions.ofBatchSize(1_000));
}
```

Para lookup masivo, declara explícitamente la clave física:

```java
BulkKeyMetadata<String> skuKey = BulkKeyMetadata.of(
    String.class,
    List.of(ColumnMetadata.of("sku", String.class, value -> value)));

List<Product> found = products.findAllByBulkKey(skus, skuKey);
```

Ambas operaciones usan la conexión de la transacción JPA. El fragmento crea una transacción
`REQUIRED` si no existe, participa en una exterior y rechaza transacciones `readOnly`. COPY no
ejecuta callbacks JPA, no rellena IDs generados y no vuelve managed los objetos insertados.

La autoconfiguración está habilitada por defecto y puede desactivarse así:

```properties
postgres-bulk.enabled=false
```

## Observability

Cuando existe un `ObservationRegistry`, cada insert/lookup publica una única observación
`postgres.bulk.operation`. Con un `MeterRegistry` también publica `postgres.bulk.rows` y
`postgres.bulk.batches`, usando sólo tags bounded y sin entidades, keys ni SQL. Actuator se integra
automáticamente, pero no es obligatorio. Para desactivar únicamente esta instrumentación:

```properties
postgres-bulk.observability.enabled=false
```

Consulta [Observability](docs/architecture/observability.md) para el boundary y la semántica de
meters, errores y rollback.

Consulta [Spring Boot auto-configuration](docs/architecture/spring-boot-autoconfiguration.md)
para condiciones, back-off, varias persistence units y diagnóstico, y
[Spring Data integration](docs/architecture/spring-data-integration.md) para transacciones y
persistence context.

## Estado

Phase 12 completada: observations operation-level, métricas de rows/batches, cardinalidad bounded y
opt-out Boot sin alterar transacciones ni errores. La versión `0.1.0-SNAPSHOT` no ofrece estabilidad
de API ni está lista para release. La siguiente fase recomendada es Phase 13 — Compatibility tests.

## Navegación

- [`docs/architecture/overview.md`](docs/architecture/overview.md): arquitectura y flujos.
- [`docs/architecture/module-boundaries.md`](docs/architecture/module-boundaries.md): dependencias permitidas y prohibidas.
- [`docs/architecture/compatibility.md`](docs/architecture/compatibility.md): matriz inicial de compatibilidad.
- [`docs/architecture/build-and-quality.md`](docs/architecture/build-and-quality.md): Wrapper, tests, formato y quality gates.
- [`docs/architecture/copy-encoding.md`](docs/architecture/copy-encoding.md): contrato tipado y framing COPY CSV.
- [`docs/architecture/pgjdbc-copy-execution.md`](docs/architecture/pgjdbc-copy-execution.md): SQL, UTF-8, lifecycle y ownership JDBC.
- [`docs/architecture/bulk-insert.md`](docs/architecture/bulk-insert.md): batching, conteos, fallos y semántica transaccional.
- [`docs/architecture/bulk-lookup.md`](docs/architecture/bulk-lookup.md): keys, tabla temporal, COPY/JOIN, resultados y cleanup.
- [`docs/architecture/hibernate-metadata.md`](docs/architecture/hibernate-metadata.md): resolver, mappings soportados, conversiones y cache Hibernate.
- [`docs/architecture/spring-data-integration.md`](docs/architecture/spring-data-integration.md): fragmento, transacciones, conexión y persistence context.
- [`docs/architecture/spring-boot-autoconfiguration.md`](docs/architecture/spring-boot-autoconfiguration.md): activación, propiedades y back-off Boot.
- [`docs/architecture/transactions-and-failures.md`](docs/architecture/transactions-and-failures.md): ownership, atomicidad, cleanup, propagaciones y pool reuse.
- [`docs/architecture/observability.md`](docs/architecture/observability.md): observations, meters, tags, privacidad y boundary transaccional.
- [`docs/legacy/current-behavior.md`](docs/legacy/current-behavior.md): caracterización del código existente.
- [`docs/legacy/risk-register.md`](docs/legacy/risk-register.md): problemas y riesgos priorizados.
- [`docs/decisions/`](docs/decisions/): decisiones arquitectónicas.
- [`docs/plans/implementation-plan.md`](docs/plans/implementation-plan.md): migración incremental y criterios de aceptación.

## Estructura

```text
repo/
├── code/postgres-bulk-parent/   # reactor Maven y módulos de la librería
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── legacy/
│   └── plans/
└── examples/                    # reservado para aplicaciones ejecutables futuras
```

El legacy permanece fuera del nuevo repositorio, en [`../legacy`](../legacy), y se trata sólo
como evidencia de comportamiento.

## Validación

Desde la raíz del repositorio:

```shell
cd code/postgres-bulk-parent
./mvnw clean verify
```

El Wrapper oficial ejecuta Maven 3.9.16 con unit tests, integration tests, Enforcer y Spotless.
Los `*IT` requieren Docker y levantan PostgreSQL 15.18 mediante Testcontainers. El bytecode
objetivo es Java 17.

Para aplicar formato Java:

```shell
./mvnw spotless:apply
```
