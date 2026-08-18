# PostgreSQL Bulk for Spring

Workspace de diseño para una librería de operaciones bulk sobre PostgreSQL, con integración opcional para Hibernate, Spring Data JPA y Spring Boot.

## Estado

Fase 0: caracterización y diseño. No existe todavía una implementación productiva. Los módulos Maven sólo validan nombres, fronteras y dirección de dependencias; no contienen clases placeholder.

## Navegación

- [`docs/architecture/overview.md`](docs/architecture/overview.md): arquitectura y flujos.
- [`docs/architecture/module-boundaries.md`](docs/architecture/module-boundaries.md): dependencias permitidas y prohibidas.
- [`docs/architecture/compatibility.md`](docs/architecture/compatibility.md): matriz inicial de compatibilidad.
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

Desde `code/postgres-bulk-parent`, una vez instalado Maven 3.6.3 o posterior:

```shell
mvn validate
```

El entorno de esta fase no dispone de Maven; los POM se validan como XML y el reactor se comprueba estructuralmente. Maven Wrapper se incorporará en Phase 1 mediante el generador oficial para no mantener scripts copiados a mano.
