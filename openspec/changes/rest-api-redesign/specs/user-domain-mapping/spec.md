# user-domain-mapping Specification

## Purpose

Defines how the `User` JPA entity maps onto the real, DBA-owned Aiven MySQL `usuario` table — including its database triggers — with `ddl-auto=validate` so Hibernate NEVER alters schema. The application writes only application-owned columns; the database owns trigger-managed behavior. Integration tests run against Testcontainers MySQL initialized from `src/test/resources/schema.sql`, which reproduces the real table DDL INCLUDING its triggers.

## Requirements

### Requirement: Entity mapped to the real usuario table

The `User` entity MUST map: `id_usuario` (primary key, `GenerationType.IDENTITY`, Java `Integer`), `primer_nombre`, `segundo_nombre` (NULL), `primer_apellido`, `segundo_apellido` (NULL), `tipo_documento` (Java enum with values `CC`, `TI`), `documento` (BIGINT UNIQUE, Java `Long`), `celular`, `grupo_formacion` (NULL), `correo_electronico` (UNIQUE), `contrasena`, `rol` (Java enum with values `ADMIN`, `USUARIO`), `tipo_apoyo` (Java enum with values `regular`, `alimentacion`, `transporte`; NULL), `fecha_registro`, and `ultima_actualizacion`. Column names, nullability, and types MUST satisfy `ddl-auto=validate` against both the production Aiven schema and the test schema (same DDL).

#### Scenario: Application boots with ddl-auto=validate against the real schema

- GIVEN the application is configured against a database that contains the production `usuario` table
- WHEN the application starts with `spring.jpa.hibernate.ddl-auto=validate`
- THEN the application context starts successfully with no schema-validation errors
- AND Hibernate issues no DDL statements

#### Scenario: Entity round-trip against the real schema

- GIVEN the test container is initialized from `schema.sql` (real DDL with triggers)
- WHEN a user is persisted and read back via the repository
- THEN every mapped field round-trips with its correct Java type
- AND `documento` is read back as `Long`

### Requirement: Trigger-managed columns are database-owned

Columns managed by database triggers MUST be mapped so the application NEVER writes them: `ultima_actualizacion` MUST be `insertable=false, updatable=false` (trigger `actualizarFechaUsuario` sets it on UPDATE), and `fecha_registro` MUST NOT be updatable by the application (the database owns registration timestamps). `rol` MUST be left null on insert when the client does not supply it, so trigger `rolDefecto` applies the `USUARIO` default.

#### Scenario: ultima_actualizacion updated by database trigger on UPDATE

- GIVEN a persisted user whose `ultima_actualizacion` is a known timestamp
- WHEN the application updates any non-trigger-owned column of that user
- THEN the database (via trigger `actualizarFechaUsuario`) sets `ultima_actualizacion` to the update time
- AND the application did not send any value for `ultima_actualizacion` in the UPDATE statement

#### Scenario: rol defaults to USUARIO when omitted

- GIVEN a creation request that omits `rol`
- WHEN the user is inserted
- THEN the persisted record has `rol` = `USUARIO` as applied by trigger `rolDefecto`
- AND the value observed after a fresh read equals `USUARIO`

#### Scenario: explicit rol is respected

- GIVEN a creation request that supplies `rol` = `ADMIN`
- WHEN the user is inserted
- THEN the persisted record has `rol` = `ADMIN`

### Requirement: Password-length trigger compatibility

The `validarContrasena` BEFORE INSERT trigger enforces a minimum length on the stored `contrasena` value. The application MUST always persist BCrypt hashes (approximately 60 characters, never the plaintext), so the trigger constraint against the stored value ALWAYS passes. A scenario MUST verify insertion of a real `$2b$10$`-format hash succeeds under the trigger-present test schema.

#### Scenario: BCrypt hash passes the contrasena length trigger

- GIVEN the test container applies `schema.sql` including trigger `validarContrasena`
- WHEN a user is inserted with a BCrypt hash (~60 characters, `$2b$10$…` format) in `contrasena`
- THEN the insert succeeds
- AND the persisted hash is byte-identical to the value supplied

#### Scenario: No plaintext value ever reaches insert

- GIVEN any creation or password-change request
- WHEN the service persists the user
- THEN the value bound to `contrasena` in the INSERT/UPDATE is a BCrypt hash, never the plaintext submitted by the client

### Requirement: Test database strategy

Repository and integration tests MUST use Testcontainers MySQL initialized with `src/test/resources/schema.sql` that reproduces the production table DDL INCLUDING triggers `actualizarFechaUsuario`, `rolDefecto`, and `validarContrasena`. Tests MUST NOT rely on Hibernate-generated schema. Under `strict_tdd`, the scenarios above that involve trigger behavior MUST each be backed by an executable test.

#### Scenario: Test schema loads with triggers

- GIVEN the Testcontainers MySQL container is started for the test suite
- WHEN `schema.sql` is applied
- THEN the `usuario` table exists with the production column set
- AND all three triggers are present in the information schema

#### Scenario: Hibernate auto-schema is never used in tests

- GIVEN any repository or integration test of the user module
- WHEN the test context initializes the datasource
- THEN `ddl-auto` is `validate` (or schema is fully supplied by `schema.sql`)
- AND no Hibernate-generated CREATE/ALTER statements run
