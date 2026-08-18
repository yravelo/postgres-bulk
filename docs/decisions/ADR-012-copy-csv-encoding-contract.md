# ADR-012: Contrato interno de encoding COPY CSV

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 4 debe transformar valores ya resueltos por la metadata neutral en filas aptas para
`COPY FROM ... FORMAT CSV`, sin introducir PostgreSQL, JDBC ni detalles de transporte en
core. La transformación debe ser determinista aunque el valor sea null, la locale o zona
horaria del proceso cambien, y el tipo runtime sea más específico que el tipo declarado.

PostgreSQL separa el texto lógico de un campo de su framing CSV. En CSV, un campo que
coincide con el marcador NULL sólo conserva su carácter no-null si está entrecomillado;
delimiter, quote, CR y LF también obligan a entrecomillar. Con `QUOTE '"'` y `ESCAPE '"'`,
una quote embebida se duplica. La barra inversa no tiene semántica especial en CSV.

Fuentes primarias:

- [PostgreSQL: COPY](https://www.postgresql.org/docs/current/sql-copy.html)
- [PostgreSQL: Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html)
- [PostgreSQL: Date/Time Types](https://www.postgresql.org/docs/current/datatype-datetime.html)
- [PostgreSQL: Binary Data Types](https://www.postgresql.org/docs/current/datatype-binary.html)
- [PostgreSQL: Lexical Structure](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)

## Decisión

### Ubicación y fronteras

Todo el mecanismo reside en `postgres-bulk-pgjdbc`, bajo el package interno cohesivo
`io.github.postgresbulk.pgjdbc.copy`. Core no cambia. Se mantienen tres pasos
independientes:

1. un encoder tipado convierte un valor Java no-null en texto lógico;
2. el writer CSV convierte `NULL` o texto en un campo COPY CSV;
3. un encoder preparado recorre las columnas de metadata en orden y escribe una fila.

Los componentes son package-private. Phase 4 no añade API ni SPI pública: el detalle de
COPY debe permanecer invisible para consumidores. Los fallos internos de configuración o
encoding usan un subtipo package-private de `BulkException`, de modo que la frontera
pública sigue siendo la excepción raíz existente.

### NULL y dialecto CSV

El dialecto queda fijado a:

- `FORMAT CSV`;
- delimiter `,`;
- quote `"`;
- escape `"`;
- marcador NULL `\N`;
- encoding del transporte `UTF8`;
- terminador de fila `\n`.

La representación interna distingue explícitamente NULL de texto. NULL se escribe como
`\N` sin quotes; el string vacío como `""`; y el string literal `\N` como `"\N"`. Un
texto se entrecomilla si está vacío, contiene el marcador NULL o contiene comma, quote,
CR o LF. Las quotes se duplican. También se cita el valor exacto `\.` para evitar que
versiones PostgreSQL anteriores a 18 puedan interpretarlo como end-of-data cuando sea la
única columna de la fila. Espacios iniciales/finales, las demás barras inversas y Unicode
se conservan sin normalización.

El executor de Phase 5 genera una sentencia COPY con exactamente estas opciones y
representa el marcador mediante el escape string SQL `E'\\N'`, que produce los dos
caracteres backslash + `N` sin depender de `standard_conforming_strings`. No puede confiar
en los defaults de sesión o servidor.

### Resolución y tipos

El registro se resuelve exclusivamente con `ColumnMetadata.javaType()` al preparar el
encoder de fila. La resolución ocurre una vez por columna, no una vez por valor. No hay
fallback a `Object.toString()`, inspección de `value.getClass()`, coerción assignable ni
dependencia de locale, timezone o charset por defecto.

Se soportan explícitamente:

- `String` y `Character`;
- `Byte`, `Short`, `Integer`, `Long`, `BigInteger`, `BigDecimal`, `Float`, `Double`;
- `Boolean`;
- `UUID`;
- `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `OffsetTime`, `Instant`;
- cualquier tipo declarado que sea `enum`, usando `Enum.name()`;
- `byte[]`, usando el formato hexadecimal PostgreSQL `\x` seguido de dos dígitos
  hexadecimales minúsculos por byte.

`BigDecimal` usa `toPlainString()`. Floating point usa las representaciones explícitas de
`Float`/`Double`, incluidas `NaN`, `Infinity` y `-Infinity`. Los temporales usan formatters
ISO y `Instant` conserva `Z`; nunca se consulta la zona horaria del sistema.

### Streaming, errores y concurrencia

El encoder preparado escribe una fila directamente a un `Appendable`, no construye una
cadena completa ni cierra el destino. Conserva metadata y encoders en estructuras
inmutables, por lo que es seguro compartirlo si los accessors de metadata cumplen el
contrato thread-safe de ADR-011.

Un tipo declarado sin encoder falla al preparar la fila e identifica columna y clase, sin
incluir el valor. Un valor runtime incompatible también identifica columna y tipos, nunca
el contenido. Las excepciones lanzadas por el accessor se propagan sin envolver, según
ADR-011; los errores de escritura del destino se propagan como `IOException`.

## Alternativas descartadas

- Usar el marcador CSV por defecto (campo vacío sin quotes): PostgreSQL lo soporta, pero
  `\N` visible hace más auditable la distinción NULL/empty y obliga a declarar el contrato
  completo en Phase 5.
- Citar todos los campos: sería correcto, pero aumenta bytes sin simplificar las reglas de
  NULL ni de quote embebida.
- Resolver por el tipo runtime: rompe columnas null y permite que una misma columna cambie
  de formato entre filas.
- Exponer un registro público de custom encoders: no hay aún un caso de integración que
  justifique estabilizar esa SPI.
- Usar una librería CSV general: no elimina la necesidad de modelar el marcador NULL y el
  contrato exacto de PostgreSQL.

## Validación

Phase 4 valida mediante tests unitarios el texto lógico, todas las ramas de framing CSV,
el orden de columnas, NULL/empty/marcador literal, tipos no soportados, errores de accessor
y escritura incremental. Phase 5 añadió la prueba de ida y vuelta con PostgreSQL 15.18,
la sentencia COPY exacta y el charset UTF-8 explícito. Los valores persistidos cubren
todas las familias soportadas y ADR-003 pasa a ACCEPTED.

## Consecuencias

El contrato de bytes de Phase 5 queda definido sin acoplar core ni ampliar la superficie
pública. Añadir JSON/JSONB, arrays, tipos custom, otros marcadores o otros formatos COPY
requerirá una decisión explícita y tests de compatibilidad; no aparecerán por fallback.
