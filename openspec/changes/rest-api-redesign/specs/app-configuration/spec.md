# app-configuration Specification

## Purpose

Defines the production configuration discipline for the microservice: all environment-specific values MUST come from environment variables referenced through `${...}` placeholders, no credentials may be committed to the repository, and the local `.env` file MUST be gitignored. With `ddl-auto=validate` (covered by `user-domain-mapping`), configuration MUST point at the real Aiven MySQL (`defaultdb`) with SSL required; the database is never modified by the application.

## Requirements

### Requirement: Externalized credentials via environment variables

Datasource URL, host, port, database name, username, and password — plus MongoDB connection details — MUST be sourced from environment variables through `${...}` placeholders in `application.properties` (or equivalent config). No literal credential value MUST appear in any committed configuration file, source file, or test fixture.

#### Scenario: No hardcoded credentials in the repository

- GIVEN the change is committed to the repository
- WHEN the repository tree is inspected (config, sources, git history of the change)
- THEN no Aiven or local database password appears as a literal value
- AND `application.properties` uses `${...}` placeholders for all datasource credentials

#### Scenario: Application reads configuration from the environment

- GIVEN the required environment variables are set with a valid Aiven MySQL configuration
- WHEN the application starts
- THEN the datasource is initialized from those environment variables
- AND no fallback to committed default credentials occurs

### Requirement: .env file gitignored

A `.env` file MAY be provided for local development, and it MUST be listed in `.gitignore` so it is never committed. An example template (e.g. `.env.example`) MAY be committed with placeholder values only.

#### Scenario: .env is ignored by git

- GIVEN a `.env` file exists at the repository root with real credentials
- WHEN `git status` (or equivalent) is evaluated
- THEN the `.env` file does not appear as a commitable change

#### Scenario: Example env file contains no real values

- GIVEN a `.env.example` (if provided)
- WHEN its contents are inspected
- THEN every value is a placeholder, not a usable credential

### Requirement: Aiven MySQL connection policy

The datasource configuration MUST target the Aiven MySQL service: database `defaultdb`, with SSL required (`ssl-mode=REQUIRED`). Local/dev overrides MAY relax SSL through their own environment values, but the committed default profile MUST NOT disable SSL for the production target.

#### Scenario: SSL is required for the production datasource

- GIVEN the production configuration resolves through environment variables for Aiven
- WHEN the JDBC URL is assembled
- THEN it includes `ssl-mode=REQUIRED` (or the equivalent driver property)
- AND `ssl-mode` is never `DISABLED` in the committed default configuration

### Requirement: Hibernate schema management is validate-only

`spring.jpa.hibernate.ddl-auto` MUST be `validate`. Hibernate MUST NOT issue DDL against the trigger-owned schema. This is a configuration-level guarantee complementing the entity mapping rules in `user-domain-mapping`.

#### Scenario: ddl-auto is validate in configuration

- GIVEN the committed `application.properties`
- THEN `spring.jpa.hibernate.ddl-auto` equals `validate`
- AND no profile in the committed configuration sets `update`, `create`, or `create-drop` for the real database

#### Scenario: Boot against Aiven performs no DDL

- GIVEN the application is configured against the real Aiven `defaultdb`
- WHEN the application starts successfully per `user-domain-mapping`
- THEN no DDL statements have been issued by Hibernate

### Requirement: No credentials in docker/dev scaffolding

`compose.yaml` or similar local development files MUST NOT contain production credentials. Any local-development credentials committed to the repository MUST be documented as dev-only and distinct from production values.

#### Scenario: Local scaffolding uses distinct dev credentials

- GIVEN `compose.yaml` exists with local MySQL credentials
- WHEN its values are compared against environment variables used in production
- THEN they are different from production credentials
- AND the values are documented as local-only in the docs deliverables
