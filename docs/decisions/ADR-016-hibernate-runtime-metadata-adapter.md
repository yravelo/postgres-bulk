# ADR-016: Adapter de metadata runtime Hibernate 6.6

- **Estado:** ACCEPTED
- **Fecha:** 2026-08-18

## Contexto

Phase 8 debe traducir una entidad Hibernate al `EntityMetadata<T>` neutral de core sin
abrir una sesión ni duplicar las reglas de mapping con reflection. JPA no expone nombres
físicos finales, selectables, converters ni foreign keys con suficiente precisión.

## Decisión

Publicar un único tipo, `HibernateEntityMetadataResolver`, construido con el
`EntityManagerFactory` estándar. El constructor hace unwrap una vez a
`SessionFactoryImplementor`; `resolve(Class<T>)` obtiene el `EntityPersister` del
`MappingMetamodel` y conserva un `ConcurrentHashMap` por resolver. No hay cache global ni
estado por fila.

Se acepta sólo una tabla mutable y sin herencia/discriminador. Se incluyen IDs asignados,
`@EmbeddedId`, selectables insertables y `@Version`; se omiten IDs generados, atributos
generated-on-insert, `insertable=false`, fórmulas y colecciones. `updatable=false` no
afecta un insert. Un default SQL se usa únicamente si Hibernate omite la columna; una
simple `@ColumnDefault` no cambia insertability y por tanto el valor de entidad, incluso
null, se incluye.

Los accessors son los `PropertyAccess` precompilados por Hibernate. Embeddables se aplanan
recursivamente. Un `ManyToOne` proyecta el identificador, incluidos proxies no inicializados,
y null produce null. Sólo se aceptan joins a primary key y sin join table. Los converters
se ejecutan a través de `JdbcMapping`/`BasicValueConverter`; STRING produce `String`,
ORDINAL se normaliza a `Integer` y un converter custom produce su tipo relacional. Las
excepciones de converter conservan el wrapping de Hibernate/JPA y su causa.

## APIs Hibernate y estabilidad

- `EntityManagerFactory` es API Jakarta Persistence.
- `SessionFactoryImplementor`, `MappingMetamodel`, `EntityPersister`, `EntityMappingType`,
  `AttributeMapping`, `SelectableMapping`, `JdbcMapping`, `EmbeddableMappingType`,
  `PropertyAccess` y `LazyInitializer` son SPI Hibernate: necesarios, no se filtran.
- `BasicValueConverter` es converter SPI.
- `QualifiedNameParser` es API pública de boot model usada para separar schema/table.
- `ToOneAttributeMapping` es implementación en package `internal`; es el único downcast
  interno y permite acceder al foreign-key descriptor/selectables reales.

## Alternativas descartadas

- Reflection/anotaciones: ignora PROPERTY, herencia de acceso, naming strategies,
  converters y selectables.
- Abrir una `Session` por resolución o fila: cambia ownership y no es necesario.
- Usar SQL de insert generado por Hibernate: acopla ejecución y callbacks ORM al adapter.
- Soportar multi-table mediante varios descriptores: `EntityMetadata` representa una tabla
  y no puede hacer atómicamente varias operaciones.

## Consecuencias

El adapter es específico de Hibernate 6.6 y debe probarse en mínimo/último patch antes de
prometer rango completo. Cambios en `ToOneAttributeMapping`, parser de nombres o mapping
SPI pueden requerir un patch de la librería. Hibernate 7 necesita otro spike/adaptador.
Phase 9 compondrá este resultado con pgJDBC y decidirá bulk-key/overrides; Phase 8 no
depende de JDBC, Spring ni COPY.

## Resolución de Phase 13

El adapter y sus 13 integration tests pasan con Hibernate 6.6.15.Final, 6.6.53.Final y
6.6.55.Final; el downcast `ToOneAttributeMapping` permanece compatible en ambos límites. ADR-021
fija 6.6.15–6.6.55 como rango soportado. Hibernate 7 pertenece a Boot 4/Spring Data 4 y queda
PLANNED/UNSUPPORTED en este artefacto, sin reflection probes.

## Resolución de Phase 14

El benchmark end-to-end reveló que el `JdbcType` de fecha puede preferir `java.sql.Date` para un
atributo `LocalDate`. El encoder pgJDBC incorpora esa representación relacional con prueba de
regresión; el resolver conserva su regla de exponer el tipo preferido de Hibernate.

## Resolución MS4

El soporte JPA multi-schema no modifica el resolver: el runtime `TableName` se propaga como
argumento separado y nunca entra en su `ConcurrentHashMap`, key de persistence unit o metadata.
Una única instancia estructural sirve para A/B. La evidencia repository reutiliza converter,
enum, embedded y proyección `ManyToOne`; el target sólo afecta la root table y no habilita mappings
multi-table ni APIs multitenancy Hibernate.
