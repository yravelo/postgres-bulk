# PostgreSQL Bulk for Spring

Workspace de diseño para una librería de operaciones bulk sobre PostgreSQL, con integración opcional para Hibernate, Spring Data JPA y Spring Boot.

## Estado

Phase 9: integración Spring Data JPA completada. El fragmento opt-in compone metadata Hibernate,
COPY/lookup pgJDBC y la transacción Spring sobre una única conexión física. Incluye insert,
lookup tipado, varias entidades, rollback, readOnly y `REQUIRES_NEW`; Boot auto-configuration
permanece para Phase 10. La versión `0.1.0-SNAPSHOT` no ofrece estabilidad de API.

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
- [`docs/legacy/current-behavior.md`](docs/legacy/current-behavior.md): caracterización del código existente.
- [`docs/legacy/risk-register.md`](docs/legacy/risk-register.md): problemas y riesgos priorizados.
- [`docs/decisions/`](docs/decisions/): decisiones y propuestas arquitectónicas.
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

El legacy permanece fuera del nuevo repositorio, en [`../legacy`](../legacy), y se trata sólo como evidencia de comportamiento.

## Validación disponible

Desde la raíz del repositorio:

```shell
cd code/postgres-bulk-parent
./mvnw clean verify
```

No se requiere Maven global. El Wrapper oficial descarga Maven 3.9.16, verifica su SHA-256 y ejecuta unit tests (`*Test`), integration tests (`*IT`), Enforcer y Spotless. Los `*IT` de pgJDBC requieren un daemon Docker accesible y levantan PostgreSQL 15.18 mediante Testcontainers. Se requiere un JDK 17 o superior; el bytecode objetivo permanece en Java 17.

Para aplicar el formato Java localmente:

```shell
./mvnw spotless:apply
```

Phase 10 — Spring Boot auto-configuration es la siguiente fase prevista.
