# Metodología de benchmarks

## Objetivo y herramienta

El arnés compara rutas públicas completas de persistencia contra PostgreSQL real. Usa JMH 1.37 en
un módulo Maven separado, siguiendo la recomendación upstream de aislar benchmarks en un
subproyecto con annotation processor y JAR ejecutable. No usa `System.nanoTime`, assertions de
rendimiento ni resultados como quality gate.

`postgres-bulk-benchmarks` forma parte del reactor para detectar errores de compilación, pero tiene
`maven.deploy.skip=true`. `test`, `verify` y `clean verify` compilan/empaquetan el arnés sin ejecutar
JMH. La ejecución sólo ocurre mediante el script manual o el workflow `Benchmarks`, que tiene
exclusivamente `workflow_dispatch`.

## Casos comparados

Insert mide una transacción confirmada por invocación y IDs UUID asignados:

- `JpaRepository.saveAll` con defaults Hibernate, sin JDBC batching;
- `JpaRepository.saveAll` con `hibernate.jdbc.batch_size=1000` y `order_inserts=true`;
- `PreparedStatement.addBatch/executeBatch`, batch 1.000 y pgJDBC
  `reWriteBatchedInserts=true`;
- `PostgresBulkRepository.bulkInsert`, API pública, batch 1.000 y observabilidad habilitada.

Todas las rutas adquieren conexión desde Hikari, insertan las mismas columnas y miden desde el
inicio de la llamada hasta después de `commit`. El dataset está pregenerado en `@Setup(Level.Trial)`
y no forma parte del tiempo. `TRUNCATE` y el `count(*)` de corrección viven en fixtures
`Level.Invocation`, fuera de la región medida. El primer warmup prepara también metadata y
encoders COPY; la baseline mide metadata caliente.

Lookup compara `findAllByCodeIn` (SQL `IN` generado por Spring Data y materialización JPA) con la
API pública `findAllByBulkKey` (tabla temporal + COPY + JOIN + materialización JPA). La tabla target
tiene 100.000 filas, todos los keys existen, son únicos y están pregenerados. Ambas rutas incluyen
transacción y commit. No se midieron aquí keys duplicadas/missing porque pertenecen a semántica de
correctness ya cubierta por integration tests.

El caso de observabilidad compara COPY con `postgres-bulk.observability.enabled=true/false` para
100 y 1.000 filas. Actuator aporta registries reales; no se sustituye la instrumentación por mocks.

J8 conserva exactamente ese arnés y añade cuatro contenders Spring Data JDBC con una configuración
JDBC aislada del contexto JPA: `CrudRepository.saveAll`, batch JDBC preparado, la API pública
`PostgresBulkJdbcRepository.bulkInsert` y `PostgresBulkJdbcOperations` low-level. Comparten tabla,
filas, UUID asignado, pool, transacción e índices. La comparación pública/low-level precalienta
metadata. El lookup J8 compara SQL `IN` construido fuera del timing con temporary-table COPY/JOIN;
ambos materializan con `EntityRowMapper` y confirman la transacción dentro del timing.

## Dataset y esquema

Semilla fija: `0x5EED14`. Cada fila contiene UUID asignado, `code`, descripción UTF-8,
`BigDecimal amount`, `Boolean active`, `LocalDate businessDate`, `Instant createdAt` y un `note`
nullable cada siete filas. La tabla tiene PK sobre UUID y un índice business UNIQUE sobre `code`.
La entidad implementa `Persistable` para que `saveAll` use `persist` con IDs asignados y no añada
SELECTs de existencia que COPY/JDBC no realizan.

Tamaños baseline insert: 10, 100, 1K, 10K y 100K. El perfil suplementario ejecuta 1M para JDBC y
COPY. JPA 1M se excluye: a 100K ya asignó aproximadamente 410 MB con batching y 530 MB sin batching,
y extrapolar el dataset administrado compromete el heap de 3 GB y la estabilidad del host. El
perfil de batch COPY fija 100K filas y prueba 100, 1K, 10K y all-in-one (100K).

J8 repite 10–100K para los cuatro contenders JDBC. Su perfil 1M ejecuta batch JDBC, API pública y
COPY low-level; omite `saveAll` porque a 100K ya asignó cerca de 1,87 GB con heap de 3 GB. Lookup
cubre 10, 100, 1K y 10K y añade temporary-table composite a 100, 1K y 10K. No incluye `IN` a 100K
por el límite práctico de parámetros del protocolo pgJDBC.

## PostgreSQL y aislamiento

`BenchmarkRunner` levanta un único `postgres:15.18-alpine` con Testcontainers por ejecución y lo
mantiene vivo para todos los forks JMH. Cada fork crea sus contextos Spring fuera del tiempo
medido. Cliente y contenedor comparten host y usan el puerto loopback publicado por Docker. No hay
H2 ni mocks.

No se cambia `fsync`, `synchronous_commit`, `shared_buffers`, autovacuum ni parámetros del
servidor: se usan defaults de la imagen. Cada operación usa una única hebra. No se ejecuta
concurrencia ni red remota; esos escenarios requieren otra baseline.

## Configuración JMH

- Insert, lookup y batch COPY: 2 warmups de 1 s, 3 iteraciones medidas de 1 s, 1 fork, 1 thread.
- Observabilidad: 3 warmups de 1 s, 5 iteraciones medidas de 1 s, 1 fork, 1 thread.
- Modo `AverageTime`, unidad ms/op, timeout de 10 minutos por iteración.
- Forks con Temurin 21, `-Xms1g -Xmx3g`.
- `-prof gc` en baseline y 1M para `gc.alloc.rate.norm` (bytes/op).
- JMH publica media y error al 99,9%; con tres muestras el intervalo puede ser ancho. El informe
  compara también dos ejecuciones completas, pero no oculta ni selecciona muestras.

## Reproducción

Desde la raíz, con Docker disponible y Java 21 en `JAVA_HOME`:

```shell
./scripts/run-benchmarks.sh smoke smoke-local
./scripts/run-benchmarks.sh baseline baseline-local-1
./scripts/run-benchmarks.sh baseline baseline-local-2
./scripts/run-benchmarks.sh large large-local
./scripts/summarize-benchmarks.sh \
  docs/benchmarks/raw/baseline-local-1.json \
  docs/benchmarks/baseline-local-1.csv
```

`POSTGRES_VERSION` permite cambiar el tag explícitamente. El smoke usa `-f 0`, cero warmups y una
iteración de 100 ms: sólo comprueba wiring, ejecución y corrección; sus números no son resultados.
La baseline y `large` conservan forks reales.

## Entorno de la baseline 2026-08-19

- CPU: Intel Core i7-12700H, 14 cores/20 threads, turbo máximo 4,7 GHz.
- RAM visible: 30 GiB; sin reserva exclusiva para el benchmark.
- Host: Ubuntu, kernel `7.0.0-28-generic`, x86_64.
- JVM: Eclipse Temurin 21.0.12+8 LTS; bytecode del proyecto Java 17.
- Docker Engine 29.7.0, Linux amd64; sin límite explícito de CPU/RAM.
- PostgreSQL 15.18 Alpine, digest
  `sha256:3d0f7584ed7d04e27fa050d6683a74746608faf21f202be78460d679cc56461f`.
- Boot 3.5.16, Hibernate 6.6.53.Final, pgJDBC 42.7.11, JMH 1.37.
- Base Git: `14612b6a52bac6987090e1d158750619b46d6aee`; Phase 13 y Phase 14 estaban
  presentes en el working tree durante la medición.

El equipo no se aisló, no se fijó frecuencia de CPU y tenía carga interactiva. Los resultados
sirven como baseline de este host, no como promesa de producción ni comparación entre máquinas.

## Extensión Spring Data JDBC J8, 2026-08-20

J8 se ejecutó en la misma máquina, imagen PostgreSQL/digest, Docker 29.7.0 y JVM Temurin
21.0.12+8. Las dependencias efectivas fueron Boot 3.5.16, Framework 6.2.19, Spring Data
JDBC/Relational 3.5.13, pgJDBC 42.7.11 y JMH 1.37. Conservó heap, threads, forks, warmups,
iterations y profiler anteriores. El host seguía interactivo, sin aislamiento ni frecuencia fija.
El informe y las nuevas evidencias están en
[`j8-spring-data-jdbc.md`](j8-spring-data-jdbc.md); Phase 14 no fue sobrescrita.

## Fuentes

- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/)
- [Repositorio y guía de ejecución JMH](https://github.com/openjdk/jmh)
- [Baseline medida](baseline.md)
- [Baseline Spring Data JDBC J8](j8-spring-data-jdbc.md)
