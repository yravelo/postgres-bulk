# Changelog

All notable changes to this project will be documented in this file. Versions follow Semantic
Versioning; during `0.x`, minor releases may contain breaking API changes when those changes are
called out in release notes and the migration path is documented.

## Unreleased

- No changes yet.

## 0.1.0

- Adopt final Maven coordinates `io.github.yravelo` and Java namespace `io.ybr.postgresbulk`
  before the first public release.
- Add PostgreSQL COPY-based bulk insert with configurable batching.
- Add temporary-table bulk lookup for simple and composite keys.
- Add Hibernate metadata, Spring Data JPA repository and Spring Boot starter integrations.
- Define transaction, failure and read-only semantics against real PostgreSQL.
- Add optional Micrometer observations and bounded-cardinality counters.
- Publish the supported Java, Spring Boot, Hibernate, pgJDBC and PostgreSQL compatibility matrix.
- Add reproducible benchmarks, adoption documentation and a standalone Spring Boot consumer.
- Add Spring Data JDBC integration, a dedicated JDBC starter and operation-scoped multi-schema
  targets for both persistence stacks.
- Add signed release provenance, CycloneDX SBOMs, vulnerability/license/SAST gates and isolated
  external consumer verification.

Released to Maven Central on 2026-08-26 from signed tag `v0.1.0` and source commit
`9d05829ae66e54be82b33728bd6f56f8318f4b7a`.
