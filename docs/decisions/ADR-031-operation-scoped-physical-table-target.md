# ADR-031: Destino físico completo y explícito por operación

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-20

## Contexto

Los resolvers Hibernate y Spring Data JDBC producen `EntityMetadata<T>` inmutable y cacheada por
persistence unit/mapping context más tipo. Esa metadata contiene tabla, columnas, accessors,
conversiones y variantes de ID. Los motores pgJDBC preparan hoy SQL desde `metadata.table()`:
`PostgresBulkInserter` conserva el COPY SQL y `TemporaryTableBulkLookup` conserva CTAS/JOIN para el
target. Por ello una operación preparada sólo sirve para el schema mapeado.

Schema-per-tenant necesita reutilizar la misma estructura con un destino diferente en cada
invocación. Incorporar tenant ids o schema runtime a las caches multiplicaría entradas, mezclaría
identidad de negocio con infraestructura y permitiría leakage entre threads. Un override sólo de
schema también dejaría reglas implícitas para combinarlo con tabla/mapping.

## Decisión

- La aplicación resuelve tenant/autorización/routing y entrega únicamente un destino físico. La
  librería no conoce tenant ids ni su contexto.
- Reutilizar `TableName` como representación canónica neutral. Ya modela schema opcional + tabla,
  es inmutable, tiene value semantics y no contiene SQL. No crear otro value object equivalente.
- El target runtime es **completo** (`schema + table`), no un schema override parcial. En el nuevo
  camino multi-schema, schema es obligatorio.
- Mantener `EntityMetadata.table()` como destino mapeado/default y señal de conflicto por
  compatibilidad. Metadata, columnas, accessors, conversiones, ID variants y encoders se cachean
  por estructura; el target runtime no entra en esas caches.
- Una invocación con target explícito usa ese target tanto para insert como para lookup. No puede
  haber resolver o política distinta por operación.
- `TableName.resolveRuntimeTarget(TableName)` centraliza el contrato en core. El target debe estar
  calificado y conservar la tabla mapeada. Si el mapping declara schema, también debe conservarlo;
  si no lo declara, el schema runtime puede variar. El camino sin target usa el mapping directamente.
- Una tabla runtime distinta se rechaza. Cambiar la tabla requeriría una política pública adicional
  y evidencia separada; no se infiere compatibilidad de shape ni se consulta catálogo.
- Se eligen argumentos `TableName` locales por invocación para las futuras operaciones. La vista
  `forTarget` se rechaza porque añade otra fachada y retención posible sin aportar semántica.
- MS1 no publica aún esos métodos de operación: hacerlo sin consumo SQL sería una API inutilizable.
  Nunca se implementa `repository.setSchema`, un resolver ambiental ni un field mutable compartido.
- Cualquier tipo nuevo usará naming físico neutral, nunca `Tenant*`.

## Alternativas

| Alternativa | Evaluación |
| --- | --- |
| schema override parcial | rechazada: merge implícito, no autocontenido y bloquea tabla dinámica |
| nuevo `PhysicalTarget` idéntico a `TableName` | rechazada mientras no exista semántica adicional |
| resolver SPI global | rechazado como primary path: esconde selección y favorece ThreadLocal/context coupling |
| target dentro de `BulkInsertOptions` | rechazado: mezcla batching/destino y no resuelve lookup |
| cache/engine por target | rechazada: crecimiento no acotado y retención de identifiers |
| target explícito completo | aceptada: auditable, thread-safe, reusable y compatible |

## Consecuencias

Los engines deberán separar shape preparado de SQL target-specific desde MS2. El fragmento JPA no podrá
cachear una operación que ya contenga COPY SQL de un schema runtime; cacheará sólo el shape. El
adapter JDBC mantiene sus caches actuales por converter/type. Construir SQL por invocación añade
coste acotado por columnas y cero coste por fila.

Las llamadas existentes no cambian. Aplicaciones con `@Table(schema=...)` continúan estáticas; si
quieren target runtime deberán dejar el schema sin fijar o esperar una futura política explícita de
override, que no forma parte de MS1.

## Evidencia de aceptación MS1

- API aditiva source/binaria en `TableName`, sin nuevo value object ni overloads inejecutables;
- matriz ejecutable de mapping sin/con schema, tabla distinta, target unqualified y null;
- misma `EntityMetadata` y columnas preservadas tras resolver targets A/B;
- selección concurrente A/B sin estado retenido;
- cero SQL, JDBC, cache, tenant/context/routing o mutación de conexión en el contrato.

La evidencia A→B con SQL real, encoder compartido, conexión y ausencia de SQL stale fue aportada
para INSERT por MS2 y ADR-032; MS3 hará lo propio para lookup. No era un requisito fingido para
aceptar este contrato neutral en MS1.

## Evidencia posterior MS2

MS2 implementa el argumento `TableName` en `PostgresBulkJdbcOperations.bulkInsert`, llama al
resolver central antes de consumir input y reutiliza metadata/columnas/encoder por identidad. COPY
SQL se construye una vez por invocación runtime no vacía y no se retiene por target. Las pruebas
PostgreSQL 15.18 confirman aislamiento A/B secuencial/concurrente, misma conexión y backend pooled,
transacciones y conflictos antes de JDBC. ADR-032 queda `ACCEPTED`; lookup y adapters continúan
diferidos.

La investigación que fundamenta la propuesta está en
[`multi-schema-investigation.md`](../architecture/multi-schema-investigation.md). La secuencia de
evidencia está en [`multi-schema-roadmap.md`](../plans/multi-schema-roadmap.md).
