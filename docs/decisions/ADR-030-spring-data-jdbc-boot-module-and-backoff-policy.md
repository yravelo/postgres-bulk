# ADR-030: Módulos Boot JDBC y política de back-off por candidato único

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

El adapter JDBC de J1–J5 necesitaba un resolver configurado manualmente. Incorporarlo al starter
JPA existente contaminaría el classpath, mientras que seleccionar silenciosamente una de varias
infraestructuras JDBC rompería el contrato explícito de J5.

## Decisión

- Añadir `postgres-bulk-spring-boot-autoconfigure-jdbc` y
  `postgres-bulk-spring-boot-starter-data-jdbc`; conservar intactos los dos artifacts Boot JPA.
- Mantener el starter JDBC sin código ni recursos productivos.
- Registrar `PostgresBulkJdbcAutoConfiguration` mediante `AutoConfiguration.imports` y ordenarla
  después de la autoconfiguración de repositories JDBC de Boot.
- Crear únicamente `SpringDataJdbcEntityMetadataResolver`, con back-off ante un resolver del
  usuario.
- Exigir candidato único o `@Primary` para `DataSource`, `JdbcOperations`, `JdbcConverter`,
  `RelationalMappingContext` y `JdbcCustomConversions`. Ante ambigüedad, no elegir.
- Reutilizar `postgres-bulk.enabled`; no añadir properties globales de schema, tenant, datasource,
  manager o batching.
- No habilitar repositories, no crear transaction managers y no abrir conexiones en startup.

## Consecuencias

Una aplicación Boot Data JDBC de infraestructura única adopta el fragmento con una sola dependencia
starter. Las aplicaciones multi-datasource conservan control explícito mediante `@Primary` o wiring
propio. Los stacks JPA y JDBC pueden compartir classpath sin colisiones, pero siguen teniendo
repositories, resolvers y límites transaccionales separados.

La implementación externa del fragmento JDBC permanece package-private, pero deja de ser final para
permitir el proxy transaccional class-based que Boot activa por defecto. Esto no amplía la API
binaria pública.

## Alternativas rechazadas

| Alternativa | Motivo |
| --- | --- |
| Ampliar el starter JPA | Introduce Hibernate/JPA en consumidores JDBC-only |
| Starter combinado | Oculta elección de store y managers; se difiere |
| Elegir por nombre u orden | Produce cross-wiring silencioso |
| Crear infraestructura JDBC faltante | Duplica responsabilidades de Boot |
| Property global de schema/datasource | Bloquea futura resolución por operación o tenant |

## Evidencia

Tests de contexto prueban condiciones, clases/beans ausentes, opt-out, override, cero I/O y
ambigüedad/`@Primary`. Un smoke Boot real con el starter JDBC prueba discovery, COPY, lookup,
conversiones, embedded e invariantes transaccionales sobre PostgreSQL. Una caracterización con
ambos starters carga simultáneamente los resolvers JPA/JDBC. Enforcer y `dependency:tree` demuestran
que el grafo productivo JDBC no contiene JPA, Hibernate, Actuator, Testcontainers ni benchmarks.
Un consumidor aislado fuera del reactor verifica el snapshot instalado usando el starter JDBC como
su única dependencia directa de postgres-bulk.
