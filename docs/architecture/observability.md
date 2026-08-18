# Observabilidad de operaciones bulk

## Boundary observado

Una operación observable es exactamente una invocación pública completa a `bulkInsert` o
`findAllByBulkKey`. La instrumentación vive en `postgres-bulk-spring-data`, alrededor del trabajo
ya implementado por `DefaultPostgresBulkOperations`; core, pgJDBC e Hibernate permanecen ajenos a
Micrometer.

El interceptor transaccional Spring entra antes que el método del fragmento. La observación empieza
dentro de ese método, antes de validar argumentos, read-only o empty input, y termina cuando retorna
o lanza la llamada bulk, antes del commit/rollback del interceptor. Mide el trabajo bulk propio, no
el coste de crear/completar la transacción. En consecuencia:

```text
bulk operation success != outer transaction commit
```

Un insert exitoso seguido de rollback del outer conserva `outcome=success`; no se modifica
retrospectivamente. Un fallo bulk termina su observación inmediatamente, aunque la completion
exterior produzca después `UnexpectedRollbackException`.

Una llamada de 20.000 filas y 26 COPY produce una observación, una actualización final de rows y
una actualización final de batches. No existen observaciones por batch, COPY chunk, sentencia,
key o fila.

## Mecanismo

`ObservationRegistry` es el boundary único para lifecycle, duration, error y posible tracing. El
fragmento consume un registry ya existente; nunca crea uno. Sin bean `ObservationRegistry`, o con
`postgres-bulk.observability.enabled=false`, utiliza el camino NOOP y la operación conserva la misma
semántica.

Cuando existe un `MeterRegistry`, se usa sólo para los dos totales que no puede producir el timer:
rows y batches. No se crea un segundo timer. Spring Boot Actuator puede aportar ambos registries y
handlers, pero no es dependencia productiva del starter.

## Observations y meters

| Nombre | Tipo | Unidad | Tags | Actualización | Fallo |
|---|---|---|---|---|---|
| `postgres.bulk.operation` | Observation; `Timer` cuando existe handler Micrometer | tiempo nativo del registry | `operation=insert|lookup`, `outcome=success|error`; el handler puede añadir su tag estándar `error` | una vez por llamada, incluso empty/read-only | duration y error; sin resultado parcial |
| `postgres.bulk.rows` | `Counter` | `rows` | `operation=insert|lookup` | una vez tras success; insert usa `BulkWriteResult.affectedRows()`, lookup usa `List.size()` | no se actualiza |
| `postgres.bulk.batches` | `Counter` | `batches` | `operation=insert` | una vez tras insert success, desde `BulkWriteResult.batches()` | no se actualiza |

Los counters responden cuántas filas/batches se procesaron en total. El count del timer permite
derivar promedios por llamada sin publicar además distributions. Empty insert/lookup crea la
observación success y registra los meters con valor cero, sin consultar metadata, conexión o DB.

En un fallo con persistencia parcial por autocommit, la observación es error y rows/batches no se
actualizan: no existe un resultado final autoritativo. Nunca se vuelve a contar un `Iterable`, se
deriva batches mediante aritmética ni se recorre un resultado sólo para métricas.

## Tags, cardinalidad y privacidad

La librería define exclusivamente dos low-cardinality keys:

- `operation`, con dos valores: `insert` y `lookup`;
- `outcome`, con dos valores: `success` y `error`.

El meter de rows comparte `operation`; batches sólo admite `operation=insert`. La integración Boot
normaliza, sólo para el timer `postgres.bulk.operation`, el tag de error que Micrometer deriva del
tipo de excepción a `none|error`; así iterator/callbacks definidos por aplicaciones no crean series
nuevas. No se publican tags ni high-cardinality context para entidad, repository, schema, tabla,
columna, temporal, SQLState o excepción concreta.

Entities, keys, CSV, valores de atributos, SQL generado y mensajes de excepción nunca se copian a
observations, meters o events. Common tags añadidos globalmente por la aplicación quedan fuera del
contrato de esta librería.

## Éxito, error y transacciones

- `BulkWriteResult` y `List.size()` son las únicas fuentes de rows/batches.
- Runtime/Error originales se registran en la Observation y se relanzan sin wrapping; cause y
  suppressed permanecen intactos.
- Un rechazo read-only es una llamada observada con `outcome=error`, sin trabajo JDBC adicional.
- REQUIRED/REQUIRES_NEW, ownership, cleanup y estado PostgreSQL abortado no cambian.
- NESTED continúa UNSUPPORTED y no tiene meter especial.
- No existen retries, eventos de progreso ni resultados parciales observables.

## Boot, opt-out y Actuator

La autoconfiguración mantiene `postgres-bulk.enabled` como kill switch funcional y añade la opción
independiente `postgres-bulk.observability.enabled`, default `true`. El opt-out desactiva sólo la
instrumentación.

Con Actuator presente, su `ObservationRegistry`, `MeterRegistry` y handlers convierten la misma
observación en timer/traces según la configuración del consumidor. Sin Actuator, la librería sigue
funcionando; un consumidor no Boot también puede aportar esos beans. La librería no crea
`SimpleMeterRegistry`, exporter, endpoint HTTP, histogram, percentile ni sampling.

Fuentes primarias:

- [Spring Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Micrometer Observation](https://docs.micrometer.io/micrometer/reference/observation/introduction.html)
- [Micrometer meter naming](https://docs.micrometer.io/micrometer/reference/concepts/naming.html)
- [Micrometer counters](https://docs.micrometer.io/micrometer/reference/concepts/counters.html)
