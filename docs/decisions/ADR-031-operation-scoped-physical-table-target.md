# ADR-031: Destino físico completo y explícito por operación

- **Estado:** PROPOSED
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

## Decisión propuesta

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
- Mapping sin schema + target qualified es válido. Mapping con schema estático + target del mismo
  schema es válido. Un schema runtime distinto de un schema estático se rechaza antes de JDBC. El
  camino sin target conserva exactamente el comportamiento actual.
- Una tabla runtime distinta es válida si el caller garantiza el mismo shape. PostgreSQL valida
  columnas, tipos, privileges y constraints; la librería no consulta catálogo ni migra schemas.
- El target es argumento/local de invocación o se encapsula en una vista inmutable nueva. Nunca se
  implementa `repository.setSchema` ni un field mutable compartido.
- MS1 comparará la mínima forma pública: argumentos `TableName` frente a una vista
  `forTarget(TableName)`. No se acepta un resolver ambiental como mecanismo primario ni una
  multiplicación cartesiana de overloads.
- Cualquier tipo nuevo usará naming físico neutral, nunca `Tenant*`.

## Alternativas

| Alternativa | Evaluación |
| --- | --- |
| schema override parcial | rechazada: merge implícito, no autocontenido y bloquea tabla dinámica |
| nuevo `PhysicalTarget` idéntico a `TableName` | rechazada mientras no exista semántica adicional |
| resolver SPI global | rechazado como primary path: esconde selección y favorece ThreadLocal/context coupling |
| target dentro de `BulkInsertOptions` | rechazado: mezcla batching/destino y no resuelve lookup |
| cache/engine por target | rechazada: crecimiento no acotado y retención de identifiers |
| target explícito completo | propuesta: auditable, thread-safe, reusable y compatible |

## Consecuencias

Los engines deben separar shape preparado de SQL target-specific. El fragmento JPA no podrá
cachear una operación que ya contenga COPY SQL de un schema runtime; cacheará sólo el shape. El
adapter JDBC mantiene sus caches actuales por converter/type. Construir SQL por invocación añade
coste acotado por columnas y cero coste por fila.

Las llamadas existentes no cambian. Aplicaciones con `@Table(schema=...)` continúan estáticas; si
quieren target runtime deberán dejar el schema sin fijar o esperar una futura política explícita de
override, que no forma parte de MS1.

## Evidencia requerida para ACCEPTED

- prototype API source/binario y revisión de overloads;
- misma metadata/encoder reutilizados en targets A/B;
- A→B y B→A sobre la misma conexión sin SQL stale;
- singleton concurrente con targets distintos;
- cache size independiente del número de targets;
- insert y lookup comparten validación/target;
- matriz de schema estático y empty input;
- cero imports/tipos tenant/context/routing en producción.

La investigación que fundamenta la propuesta está en
[`multi-schema-investigation.md`](../architecture/multi-schema-investigation.md). La secuencia de
evidencia está en [`multi-schema-roadmap.md`](../plans/multi-schema-roadmap.md).
