# Examples

- [`spring-boot-basic`](spring-boot-basic/README.md): Spring Data JPA/Hibernate adoption.
- [`spring-boot-data-jdbc`](spring-boot-data-jdbc/README.md): Spring Data JDBC adoption with the
  dedicated JDBC-only starter.

Both are standalone Boot applications whose direct postgres-bulk dependency is the starter for
their persistence stack. Their application services demonstrate default calls and application-owned
customer-to-`TableName` allow-lists; Testcontainers executes default, A/B, quoted-target, lookup and
rollback scenarios against PostgreSQL rather than H2.
