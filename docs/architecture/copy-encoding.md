# COPY CSV encoding

## Alcance

Phase 4 convierte las columnas ordenadas de `EntityMetadata<T>` en caracteres para
`COPY FROM ... FORMAT CSV`. No abre conexiones, no construye SQL, no llama pgJDBC y no
convierte caracteres a bytes. Esas responsabilidades pertenecen al executor de Phase 5.

El mecanismo vive íntegramente en `postgres-bulk-pgjdbc`; core no conoce CSV ni
PostgreSQL. Todos sus tipos son package-private para que el formato permanezca invisible
en la API.

## Pipeline

```text
ColumnMetadata.javaType() ──prepare──> encoder fijo por columna
entity ──accessor──> Java value ──encoder──> logical text | NULL
logical text | NULL ──CSV field writer──> framed field
ordered framed fields ──row encoder──> Appendable + LF
```

La preparación recorre la metadata una sola vez. Cada fila reutiliza los encoders ya
resueltos, lee las columnas en encounter order y escribe directamente al destino. No se
materializa el input ni es obligatorio construir una `String` por fila. El encoder no
cierra el `Appendable`; el owner de Phase 5 controlará su lifecycle y buffering.

## Contrato NULL y CSV

El dialecto requiere que Phase 5 emita una sentencia equivalente a:

```sql
COPY ... FROM STDIN WITH (
    FORMAT CSV,
    DELIMITER ',',
    QUOTE '"',
    ESCAPE '"',
    NULL E'\\N',
    ENCODING 'UTF8'
)
```

`E'\\N'` usa la sintaxis explícita de
[escape strings de PostgreSQL](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)
para que el literal SQL produzca backslash + `N` sin depender de la configuración de la
sesión.

El terminador producido es siempre LF (`\n`), independiente de la plataforma. La
representación interna no usa `null` como texto: distingue estado NULL de estado VALUE.

| Valor lógico | Campo emitido | Motivo |
|---|---|---|
| SQL NULL | `\N` | marcador NULL sin quotes |
| string vacío | `""` | VALUE vacío inequívoco |
| string literal `\N` | `"\N"` | quoted para no convertirse en NULL |
| string literal `\.` | `"\."` | evita el marcador end-of-data de PostgreSQL 15–17 |
| `a,b` | `"a,b"` | contiene delimiter |
| `a"b` | `"a""b"` | quote duplicada dentro de campo quoted |
| CR, LF o CRLF embebido | campo quoted | conserva el salto dentro del valor |

Un VALUE se cita si está vacío, contiene `\N`, es exactamente `\.` o contiene comma,
quote, CR o LF. Los espacios iniciales/finales son significativos y se conservan; no
obligan a quoting. La barra inversa no se escapa en CSV fuera de esas protecciones. Unicode
y emoji se conservan como caracteres Java; el executor deberá escribir el stream en UTF-8
explícito. Citar `\.` mantiene compatibilidad con la excepción de end-of-data descrita por
PostgreSQL para versiones anteriores a 18.

Estas reglas siguen [PostgreSQL COPY](https://www.postgresql.org/docs/current/sql-copy.html),
que define el tratamiento del marcador NULL, quoting/escape, caracteres significativos y
newlines de CSV.

## Tipos soportados

| Tipo declarado | Texto lógico determinista |
|---|---|
| `String` | contenido sin cambios |
| `Character` | el carácter |
| `Byte`, `Short`, `Integer`, `Long` | representación decimal de su wrapper |
| `BigInteger` | decimal arbitrario |
| `BigDecimal` | `toPlainString()`, sin pérdida de precisión ni locale |
| `Float`, `Double` | representación Java específica, incluidos `NaN`, `Infinity`, `-Infinity` |
| `Boolean` | `true` o `false` |
| `UUID` | forma canónica con guiones |
| `LocalDate` | ISO local date |
| `LocalTime` | ISO local time, con fracción cuando existe |
| `LocalDateTime` | ISO local date-time con `T` |
| `OffsetDateTime` | ISO date-time con offset explícito |
| `OffsetTime` | ISO time con offset explícito |
| `Instant` | ISO instant en UTC con `Z` |
| cualquier `enum` declarado | `Enum.name()`, nunca `toString()` |
| `byte[]` | input hexadecimal `bytea`: `\x` + dos hex minúsculos por byte |

PostgreSQL acepta los valores floating point especiales documentados en
[Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html). Los
formatos ISO y offsets explícitos evitan depender de la sesión según
[Date/Time Types](https://www.postgresql.org/docs/current/datatype-datetime.html). El
formato hexadecimal de `bytea` es el formato preferido y siempre aceptado según
[Binary Data Types](https://www.postgresql.org/docs/current/datatype-binary.html).

## Resolución y mapping

El registro usa exactamente `ColumnMetadata.javaType()`. No examina `value.getClass()`
para escoger encoder y no busca supertipos compatibles. Esto hace que una columna tenga
un único formato incluso si su primer valor es null o sus valores runtime cambian de
subclase. Un enum sí se reconoce por `declaredType.isEnum()`.

El producer de metadata debe entregar el tipo que llega realmente a PostgreSQL después de
cualquier mapping/converter. Un `AttributeConverter<X, Y>` debe proyectar `Y` y declarar
`Y.class`; encoding no ejecuta converters. Un custom type sin built-in falla durante la
preparación, antes de leer filas. No hay fallback a `Object.toString()`.

## Errores y datos sensibles

`BulkEncodingException` es un subtipo package-private de `BulkException`. Un tipo sin
encoder identifica el nombre de columna y la clase declarada. Un valor incompatible
identifica columna, clase declarada y clase runtime. Ningún mensaje incluye el valor.

Las excepciones del accessor se propagan sin envolver para respetar ADR-011. Una
`IOException` del destino también se propaga sin convertirla ni cerrar el destino. Como
la escritura es incremental, el owner debe descartar/cancelar el COPY activo tras
cualquier fallo; Phase 5 probará ese lifecycle.

## Inmutabilidad, concurrencia y coste

El registro default, sus formatters y cada encoder preparado son inmutables. Pueden
compartirse entre threads si los accessors de metadata son stateless/thread-safe y cada
operación usa su propio destino. El coste por fila es una lectura y una conversión por
columna más el append de caracteres; no hay lookup de encoder ni buffer de fila completo.

Cada VALUE produce actualmente su texto lógico como `String`. Esta allocation acotada es
deliberada para mantener encoders simples y verificables; eliminarla sólo se considerará
con profiling. El framing se transmite carácter a carácter únicamente cuando requiere
quotes.

## Validación y límites pendientes

Los tests unitarios cubren la matriz completa de tipos, locale/timezone distintas,
NULL/empty/marcador literal, comma, quote, CR/LF/CRLF, espacios, Unicode/emoji,
backslash/end-of-data, orden, varias filas, escritura incremental, ownership, errores de accessor,
tipo no soportado, mismatch runtime y fallo de I/O.

No se afirma todavía round-trip real ni contrato de bytes: Phase 5 añadirá Testcontainers,
construirá el SQL con estas opciones, usará UTF-8 explícito y verificará valores
persistidos. JSON/JSONB, arrays, tipos custom, registro público de encoders, otros
marcadores y COPY TEXT/BINARY siguen fuera de alcance.
