# ADR-007: Baseline y quality gates del build

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

El proyecto necesita un build reproducible desde un checkout limpio, compatible con la baseline Java propuesta y sin tooling acumulativo. El entorno inicial no tenía Maven global.

## Alternativas

1. **Maven del sistema:** configuración mínima, pero versión y disponibilidad varían por desarrollador/CI.
2. **Maven Wrapper sobre Maven 4 preview:** futuro cercano, pero Maven 4 aún no es estable.
3. **Wrapper sobre Maven 3 estable:** bootstrap reproducible y compatible con el ecosistema elegido.
4. **Java 21 baseline:** acceso a APIs nuevas; reduce consumidores sin necesidad funcional demostrada.
5. **Varios formatters/analyzers desde el inicio:** cobertura aparente; diagnósticos solapados, coste y cero código que analizar.

## Decisión

- Maven Wrapper 3.3.4 `only-script`, Maven 3.9.16 y checksum de distribución; Maven mínimo 3.8.9 si se evita el Wrapper (elevado por SpotBugs Maven 4.10.4.0 en SEC3).
- Java 17 como `release` y runtime mínimo del build; CI en 17/21.
- JUnit 5 mediante BOM 5.12.2; Surefire/Failsafe 3.5.4 alineados para `*Test`/`*IT`.
- Spotless 3.9.0 con google-java-format 1.28.0 como única autoridad de formato.
- Enforcer para Java/Maven, convergence y dependencias prohibidas de core.
- Diferir Toolchains, ArchUnit ejecutable y analizadores de bytecode hasta que exista una necesidad/código real.
- Licencia Apache-2.0 para permitir uso y contribución con concesión explícita de patentes.

## Consecuencias

`./mvnw clean verify` necesita red sólo en el primer uso y después reutiliza `~/.m2/wrapper`/repositorio local. El build puede ejecutarse sobre JDK superiores pero produce bytecode Java 17. El formatter queda deliberadamente en una versión compatible con runtime 17, no en la última que exige Java 21. Añadir otra herramienta de calidad requiere demostrar qué riesgo nuevo cubre.
