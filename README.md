# PostgreSQL Bulk for Spring

Workspace de diseño para una librería de operaciones bulk sobre PostgreSQL, con integración opcional para Hibernate, Spring Data JPA y Spring Boot.

## Estado

Phase 1: foundation completada. Existe un reactor Maven reproducible con quality gates, pero todavía no hay implementación productiva ni clases placeholder. La versión `0.1.0-SNAPSHOT` no ofrece estabilidad de API.

## Navegación

- [`docs/architecture/overview.md`](docs/architecture/overview.md): arquitectura y flujos.
- [`docs/architecture/module-boundaries.md`](docs/architecture/module-boundaries.md): dependencias permitidas y prohibidas.
- [`docs/architecture/compatibility.md`](docs/architecture/compatibility.md): matriz inicial de compatibilidad.
- [`docs/architecture/build-and-quality.md`](docs/architecture/build-and-quality.md): Wrapper, tests, formato y quality gates.
- [`docs/legacy/current-behavior.md`](docs/legacy/current-behavior.md): caracterización del código existente.
- [`docs/legacy/risk-register.md`](docs/legacy/risk-register.md): problemas y riesgos priorizados.
- [`docs/decisions/`](docs/decisions/): decisiones y propuestas arquitectónicas.
- [`docs/plans/implementation-plan.md`](docs/plans/implementation-plan.md): migración incremental y criterios de aceptación.

## Estructura

```text
repo/
├── code/postgres-bulk-parent/   # reactor Maven, todavía sin código Java
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

No se requiere Maven global. El Wrapper oficial descarga Maven 3.9.16, verifica su SHA-256 y ejecuta unit tests (`*Test`), integration tests (`*IT`), Enforcer y Spotless. Se requiere un JDK 17 o superior; el bytecode objetivo permanece en Java 17.

Para aplicar el formato Java localmente:

```shell
./mvnw spotless:apply
```

Phase 2 — Core domain/API es la siguiente fase prevista; no ha comenzado.
