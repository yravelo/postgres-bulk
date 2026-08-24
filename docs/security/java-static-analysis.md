# Java static analysis

**Estado:** SEC3 baseline implementada el 2026-08-24. La activación remota se registra después de
que Build y Compatibility terminen sobre el commit SEC3. Esta fase no habilita SBOM, firma,
publicación ni scanners adicionales.

## Toolchain y alcance

| Componente | Versión fija | Función |
| --- | --- | --- |
| SpotBugs Maven Plugin | `4.10.4.0` | integración Maven y gate `check` |
| SpotBugs | `4.10.4` | motor de análisis bytecode |
| FindSecBugs | `1.14.0` | detectores `SECURITY`, incluido taint SQL |

Las tres coordenadas están en el parent y en el inventario SCA. FindSecBugs es dependencia del
plugin Maven; el plugin descubre su `findbugs.xml` y cada reporte debe contener
`<Plugin id='com.h3xstream.findsecbugs' enabled='true'>`. Las fuentes primarias de configuración
son la [documentación del plugin Maven](https://spotbugs.github.io/spotbugs-maven-plugin/usage.html),
[SpotBugs](https://github.com/spotbugs/spotbugs) y
[FindSecBugs](https://github.com/find-sec-bugs/find-sec-bugs).

El gate analiza sólo bytecode `src/main` de:

- `postgres-bulk-core`;
- `postgres-bulk-pgjdbc`;
- `postgres-bulk-hibernate`;
- `postgres-bulk-spring-data` (JPA);
- `postgres-bulk-spring-data-jdbc`;
- las autoconfiguraciones JPA y JDBC.

Los dos starters no contienen Java productivo y declaran `spotbugs.skip=true`. Benchmarks también
lo declaran porque miden infraestructura y no forman parte del producto. Los examples tienen parent
Spring Boot independiente, no heredan la ejecución. Tests, código generado y el consumer aislado
no forman parte del gate; siguen cubiertos por compilación y suites funcionales.

## Baseline inicial y triage

El scan inicial se ejecutó sin exclude filter, con `effort=Max`, `threshold=Low`, tests omitidos y
FindSecBugs activo. Analizó siete módulos, terminó en 44,4 s sobre Java 25 y produjo seis findings:

| Rule / categoría / prioridad | Clase y método | Security | Decisión |
| --- | --- | --- | --- |
| `EI_EXPOSE_REP`, MALICIOUS_CODE, P2/rank 18 | `BulkKeyMetadata.components()` | no | no aplicable: el field se crea con `List.copyOf`; exclusión exacta |
| `EI_EXPOSE_REP`, MALICIOUS_CODE, P2/rank 18 | `EntityMetadata.insertColumns()` | no | no aplicable: el field se crea con `List.copyOf`; exclusión exacta |
| `SQL_INJECTION_JDBC`, SECURITY, P2/rank 12 | `TemporaryTableBulkLookup.executeStatement(...)` | sí | falso positivo: SQL estructural de `BulkLookupSql`; exclusión exacta |
| `SQL_INJECTION_JDBC`, SECURITY, P2/rank 12 | `TemporaryTableBulkLookup.cleanup(...)` | sí | falso positivo: DROP usa nombre temporal validado y quoted; exclusión exacta |
| `SQL_INJECTION_JDBC`, SECURITY, P2/rank 12 | `EntityRowMapperMaterializer.materialize(...)` | sí | falso positivo: callback interno recibe sólo SELECT ya construido y quoted; exclusión exacta |
| `CT_CONSTRUCTOR_THROW`, BAD_PRACTICE, P2/rank 16 | `DefaultPostgresBulkOperations.<init>(...)` | no | no aplicable: infraestructura sin finalizer y no extension point; exclusión exacta |

No apareció `NP_*`, `RCN_*`, `DLS_*`, `RV_*`, reflection/accessibility/class-loading,
ignored/lost exception, `EI_EXPOSE_REP2`, `MS_EXPOSE_REP` ni finding de concurrencia. No se encontró
un defecto real, por lo que SEC3 no cambia source productivo, API ni semántica y no añade un test de
regresión artificial. Los tests adversariales existentes de quoting, COPY, lookup, cleanup,
causa/suppressed, caches y concurrencia permanecen como evidencia funcional.

## Evaluación focalizada

`TableName` conserva componentes neutrales, nunca SQL. `CopySqlBuilder` y `BulkLookupSql` pasan cada
schema, tabla y columna por `PostgresIdentifierQuoter`, que duplica comillas y rechaza NUL. Los
nombres de temporales además aceptan sólo ASCII minúsculo y están limitados a 63 bytes. Los valores
de filas y keys viajan por COPY CSV, no por concatenación SQL. El SELECT materializado por JPA/JDBC
es un callback interno del mismo builder; no existe entrada pública de raw SQL. Los tres findings
SQL son por tanto false positives de un sink que no puede distinguir un statement estructural ya
quoted. Si cualquier origen o contrato cambia, la exclusión deja de ser válida y debe eliminarse.

Hibernate usa su runtime mapping SPI, no `setAccessible`, `trySetAccessible`, `Class.forName` ni
class loading dinámico. Spring Data JDBC usa accessors del mapping context. Las excepciones
preservan la causa; el cleanup agrega `SQLException` como suppressed cuando ya existe fallo
primario. No se cambió ese contrato para satisfacer el analyzer.

Los caches productivos son `ConcurrentHashMap` por resolver o maps de identidad sincronizados por
instancia. `computeIfAbsent` se ejecuta bajo la disciplina de cada cache; repositories singleton no
retienen `TableName` runtime ni estado de operación. Las listas expuestas son representaciones
inmutables y sus elementos `ColumnMetadata` tienen un contrato explícito de accessor thread-safe.

## Exclusions y policy

`config/security/spotbugs-exclude.xml` contiene exactamente seis matches `Bug + Class + Method`.
No excluye package, categoría, prioridad ni todos los findings medium. Cada entrada registra owner,
rationale y `review-by: 2027-02-24`. `scripts/check-static-analysis.py` falla si una exclusión
expira, se amplía o se separa del set revisado.

La configuración final usa `effort=Max`, `threshold=Medium`, `includeTests=false`, cero violaciones
permitidas y `failOnError=true`. En consecuencia, todo finding productivo medium/high nuevo,
incluido cualquier finding FindSecBugs `SECURITY`, bloquea `verify` hasta ser corregido o recibir
triage y exclusión exacta. Low se revisó en el baseline inicial; queda fuera del gate continuo para
evitar ruido, no mediante una baseline de bugs. Un scanner error, missing class, reporte ausente,
plugin FindSecBugs inactivo, exclusión expirada o finding restante falla el audit posterior.

El orden de decisión es:

```text
real defect -> minimal fix + regression test
false positive -> Bug + Class + Method + rationale + owner + review-by
non-applicable -> same narrow, expiring exclusion
```

No se añade `Serializable`, logging de datos, annotation suppression, accepted-risk vacío ni
baseline masiva para silenciar el análisis.

## Lifecycle, CI y reports

La ejecución `verify-production-bytecode` está ligada una vez a `verify` en el parent e inherited
por los siete módulos productivos. Build ejecuta `clean verify` sobre Java 17 y después audita los
siete XML. Release candidate conserva exactamente el mismo build y audit antes de staging; el job
de upload no introduce un bypass. Compatibility añade `-Dspotbugs.skip=true` a sus verificaciones
porque Build es el gate canónico y repetir siete análisis en sus once jobs no aporta cobertura. Ese
skip no está presente en Build ni Release.

Los XML son evidencia efímera bajo `*/target/spotbugsXml.xml`: no se versionan ni se suben como
artifact, porque incluyen paths locales y perfiles del runtime. El audit comprueba FindSecBugs,
errores, missing classes, conteo cero, siete módulos esperados y ausencia de reportes para starters,
benchmarks y examples.

Comando local reproducible desde el parent:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
../../scripts/check-static-analysis.py
```

Para investigar un cambio sin reducir el gate final se puede ejecutar temporalmente el scan de
diagnóstico con `-Dspotbugs.threshold=Low -Dspotbugs.failOnError=false`; sus resultados deben
clasificarse antes del commit. SpotBugs 4.10.4 procesa bytecode release 17 y fue ejecutado por el
baseline bajo Java 25; Build valida el runtime Java 17. El plugin 4.10.4.0 requiere Maven 3.8.9,
por lo que Enforcer elevó el mínimo desde 3.6.3; el Wrapper fijado en 3.9.16 sigue siendo la vía
canónica. Java 21/25 continúan en Compatibility con SAST omitido de forma deliberada, pues el
bytecode productivo es el mismo.

## Decisiones y limitaciones

- CodeQL no es baseline bajo el contexto actual de repository privado/plan y no se habilita.
- Semgrep queda opcional/manual hasta que exista un gap concreto no cubierto.
- Sonar no se recomienda: duplicaría el baseline y añadiría servicio/dashboard operativo.
- FindSecBugs no comprende por sí solo la procedencia estructurada de todos los SQL internos; las
  tres exclusiones exactas preservan esa limitación de forma visible y expirable.
- El análisis bytecode no sustituye tests de PostgreSQL, review de autorización ni SCA.

SEC3 entrega cero findings sin triage y cero defectos reales conocidos pendientes. El handoff
exacto, sin iniciarlo aquí, es `SEC4 — SBOM and Dependency/License Integrity`.
