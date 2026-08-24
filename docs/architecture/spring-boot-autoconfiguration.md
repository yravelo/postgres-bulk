# Autoconfiguración Spring Boot

## Alcance

`postgres-bulk-spring-boot-autoconfigure` es exclusivamente el composition root de los adapters
existentes. No contiene COPY, SQL, mapping ni lógica de repositorio. El starter es un agregador de
dependencias sin clases productivas.

## Activación

Spring Boot descubre `PostgresBulkAutoConfiguration` mediante el archivo moderno
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. No se usa
`spring.factories` para registrar auto-configuraciones; el `spring.factories` del módulo Spring
Data tiene otra responsabilidad: cargar el repository fragment externo opt-in.

La configuración se activa únicamente cuando:

- existen JPA, Hibernate, pgJDBC, Spring Data JPA y `PostgresBulkRepository` en el classpath;
- existe al menos un bean `EntityManagerFactory`;
- `postgres-bulk.enabled` vale `true` o está ausente.

Se ordena después de `HibernateJpaAutoConfiguration` y antes de
`JpaRepositoriesAutoConfiguration`, de modo que el bridge de metadata ya exista al ensamblar los
repositories. No hace component scanning ni activa repositorios globalmente.

## Bean y back-off

El único default creado es un `JpaEntityMetadataResolver` que adapta
`HibernateEntityMetadataResolver` y mantiene cache por identidad de `EntityManagerFactory`. Si la
aplicación declara cualquier bean de ese tipo, `@ConditionalOnMissingBean` conserva el bean del
usuario sin ambigüedad ni reemplazo.

No se crean ejecutores globales: cada instancia del fragmento prepara y reutiliza sus operaciones
por metadata, igual que en Phase 9. Añadir el starter tampoco convierte todos los repositories en
bulk; cada repository debe extender explícitamente `PostgresBulkRepository<T, ID>`.

## Propiedades

| Propiedad | Default | Efecto |
|---|---:|---|
| `postgres-bulk.enabled` | `true` | Habilita o deshabilita toda la autoconfiguración. |
| `postgres-bulk.observability.enabled` | `true` | Emite observaciones si existe infraestructura; no cambia bulk. |

No se exponen propiedades de batch, buffer o prefijo temporal: el batch ya es una opción por
invocación y no existe evidencia para convertir detalles internos de COPY/temporales en contrato
global. El annotation processor genera `META-INF/spring-configuration-metadata.json` para IDEs.

La autoconfiguración no crea `ObservationRegistry` ni `MeterRegistry`. Aporta un `MeterFilter`
limitado a `postgres.bulk.operation` que normaliza el tag estándar de error a `none|error`, evitando
series por clases de excepción definidas por la aplicación. Con el opt-out, ni el fragmento emite
instrumentación ni se registra ese filtro.

## PostgreSQL y coste de arranque

La presencia de `org.postgresql.PGConnection` es una condición estructural de classpath. La
autoconfiguración no abre conexiones, consulta metadata JDBC ni intenta identificar el producto de
base de datos durante el arranque. Esto evita I/O oculto y permite que pools lazy sigan siendo
lazy.

Por esa razón, un driver pgJDBC presente junto a un datasource apuntando realmente a otra base de
datos puede arrancar. La primera operación bulk fallará de forma explícita al no poder hacer
`Connection.unwrap(PGConnection.class)`. La detección runtime anticipada requeriría conexión y no
se introduce sin una política de failure analysis respaldada por Phase 11.

## Varias persistence units

La condición exige presencia, no un candidato único: la autoconfiguración soporta varios beans
`EntityManagerFactory` sin escoger uno al arrancar. En cada llamada, `JpaContext` selecciona la
persistence unit que gestiona el tipo de dominio y el resolver mantiene un adapter separado por
identidad de factory. Como en Spring Data JPA, una clase gestionada ambiguamente por varias units no
tiene selección automática y debe resolverse en la configuración de repositories de la aplicación.

## Starter y dependencias

`postgres-bulk-spring-boot-starter` agrega `spring-boot-starter-data-jpa` y
`postgres-bulk-spring-boot-autoconfigure`. Este último arrastra los adapters Spring Data, Hibernate
y pgJDBC necesarios. El starter no contiene Java ni recursos propios; las versiones de Spring,
Hibernate y pgJDBC las gobierna el BOM Spring Boot del consumidor.

Micrometer Observation/Core llegan por el adapter Spring Data para el boundary operation-level.
Actuator no es dependencia productiva; aparece sólo en tests y, cuando el consumidor lo incluye,
aporta los registries/handlers que convierten observations en timers o spans.

## AOT y JPMS

La composición no hace classpath scanning propio ni reflection dinámica: usa condiciones y
registro declarativo de Boot. Esto reduce fricción con AOT, pero no constituye soporte probado para
GraalVM/native image; hints y una prueba native quedan diferidos hasta existir un objetivo de
compatibilidad explícito. No se añaden descriptores JPMS en esta fase.

## Evidencia

`ApplicationContextRunner` cubre activación default, opt-out de observabilidad, disabled, falta de
EMF, clases ausentes,
varias factories, back-off ante bean propio y cero aperturas de conexión. Una aplicación
`@SpringBootApplication` real, dependiente sólo del starter dentro del reactor, valida contra
PostgreSQL 15.18 el descubrimiento del fragmento, insert con batching, lookup tipado, rollback
exterior, rechazo read-only y meters reales de insert/lookup/failure sin configuración manual.

## Evidencia multi-schema MS6

La autoconfiguración productiva no cambia. El starter JPA ejecuta default y targets A/B/C con el
mismo proxy, commit/rollback, `REQUIRES_NEW`, read-only, schema quoted, errores PostgreSQL,
concurrencia y metadata por identidad. `TableName` nunca entra en Boot: no aparece property, bean,
cache ni tag nuevo. La composición completa se documenta en
[`multi-schema-spring-boot-composition.md`](multi-schema-spring-boot-composition.md).
