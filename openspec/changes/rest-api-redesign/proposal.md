# Proposal: REST API Redesign — User Management Microservice

## Intent

Transform the minimal `@Controller` CRUD (`/demo/add`, `/demo/all`) into a pure REST microservice that serves as the user-management submodule of the SENA financial-management application. The React frontend at `http://localhost:3000` consumes this API; authentication/login stays in the existing Express backend. The change also aligns the User entity with the real, DBA-owned Aiven MySQL schema (including its triggers), secures the API surface, and establishes the production configuration discipline (env-var credentials, `ddl-auto=validate`) that the current codebase lacks.

## Scope

### In Scope

1. **Layered architecture** — `@RestController` → thin Service → Repository. Service owns password hashing and validation-before-hashing.
2. **Static frontend removal** — delete `src/main/resources/static/**` (`index.html`, `js/app.js`, `css/styles.css`). No Thymeleaf dependency exists; none will be introduced.
3. **Full User CRUD** — create, read by id, update, delete, paginated list. **No login endpoint.**
4. **Derived-query searches** — (a) AND search on `primerNombre` + `documento`; (b) OR search on `primerNombre`, `primerApellido`, `documento`. Implemented as Spring Data derived methods (parameterized by construction).
5. **Pagination** — `JpaRepository` + `Pageable`, hard max page size of 7 records.
6. **Security hardening** — parameterized JPA queries only; input sanitization against common exploit payloads; per-IP global rate limiting via bucket4j returning HTTP 429 on exceed; CORS restricted to `http://localhost:3000` only.
7. **MongoDB logging** — `spring-boot-starter-data-mongodb`; system logs in separate collections per level (`warns`, `info`, `error`). Lazy collection creation on first insert is acceptable.
8. **User entity rewrite** — mapped to the real MySQL schema: `id_usuario` PK (`GenerationType.IDENTITY`), `primer_nombre`, `segundo_nombre` NULL, `primer_apellido`, `segundo_apellido` NULL, `tipo_documento` ENUM('CC','TI'), `documento` BIGINT UNIQUE (Java `Long`), `celular`, `grupo_formacion` NULL, `correo_electronico` UNIQUE, `contrasena`, `rol` ENUM('ADMIN','USUARIO'), `tipo_apoyo` ENUM('regular','alimentacion','transporte') NULL, `fecha_registro`, `ultima_actualizacion`.
9. **Production config** — Aiven MySQL (`ssl-mode REQUIRED`) via environment variables only; `.env` gitignored; `${...}` placeholders in config. No credentials committed.
10. **Trigger-mirroring mapping** — `ddl-auto=validate` (Hibernate never alters schema). Map trigger-managed columns read-only where required: `actualizarFechaUsuario` (BEFORE UPDATE → `ultima_actualizacion=NOW()`), `rolDefecto` (BEFORE INSERT → default `USUARIO`), `validarContrasena` (BEFORE INSERT length ≥ 8 against the stored BCrypt hash). Application validates plaintext rules in the service layer BEFORE hashing.
11. **BCrypt hashing** — Spring Security crypto, compatible with existing `$2b$10$` Node/Express hashes in the DB.
12. **Documentation** — `/docs/ARCHITECTURE.md` (technical), `/docs/STEP_BY_STEP.md` (manual build guide), `/docs/queries/SEARCH_AND_PAGINATION.md`.
13. **TDD test suite** — per `strict_tdd: true` in `openspec/config.yaml`: unit tests (service, controller via MockMvc) and repository slices for derived queries/pagination drive the rewrite.

### Out of Scope

- Any database modification whatsoever (no DDL, no trigger changes, no data migration).
- Login/authentication endpoints (stay in the Express backend). Only password hashing on create/update.
- Thymeleaf or any server-side view layer.
- Other entities from the SQL dump — User only.
- JWT/session/token mechanisms, refresh tokens, or inter-service auth between Express and this API.
- Hot-reload/migration tooling for MongoDB logs (lazy creation accepted).

## Capabilities

> `openspec/specs/` does not exist yet — every capability below is NEW and each becomes `openspec/specs/<name>/spec.md` at archive.

### New Capabilities

- `user-rest-crud`: Layered `@RestController` CRUD endpoints (create, read by id, update, delete) with global exception handling (`ApiError` envelope, 400/404/409/500 mapping).
- `user-domain-mapping`: User entity rewritten to the real Aiven MySQL schema, including trigger-managed column mapping (`fecha_registro`, `ultima_actualizacion`, `rol` default) and `ddl-auto=validate`.
- `user-search-pagination`: JPA derived-query searches (AND on primer_nombre+documento; OR on primer_nombre/primer_apellido/documento) and paginated listing with max page size 7.
- `api-security`: BCrypt password hashing compatible with Express `$2b$` hashes, plaintext validation before hashing, CORS (only `http://localhost:3000`), input sanitization, bucket4j per-IP rate limiting with HTTP 429.
- `mongodb-logging`: System log persistence into per-level MongoDB collections (`info`, `warns`, `error`) via spring-boot-starter-data-mongodb.
- `app-configuration`: Externalized Aiven MySQL configuration via environment variables with `${...}` placeholders, gitignored `.env`, ssl-mode REQUIRED policy.

### Modified Capabilities

- None — no existing specs in `openspec/specs/`.

## Approach

Exploration recommends a **thin service layer** (Controller → Service → Repository) and this proposal adopts it: password hashing, plaintext validation, and trigger-managed field discipline are business rules that must not live in the controller.

1. **Config & dependencies first**: env-var externalization (`spring.datasource.*` from env), `ddl-auto=validate`, remove hardcoded credentials, add `spring-boot-starter-security` (crypto only), `bucket4j`, `spring-boot-starter-data-mongodb`. Delete `static/**`.
2. **Entity rewrite**: map the real schema with Java enums for `tipo_documento`/`rol`/`tipo_apoyo`; trigger-managed columns (`fecha_registro`, `ultima_actualizacion`) mapped with `insertable=false, updatable=false` so the DB owns them; `rol` nullable on insert so `rolDefecto` applies the default; `documento` as `Long`.
3. **Repository**: extend `JpaRepository<User, Integer>`; declare derived AND/OR finders; paged `findAll(Pageable)`.
4. **Service**: hashing (BCryptPasswordEncoder validates the `$2b$` family natively), plaintext password rules (≥ 8 chars, plus sanitization) before hashing, uniqueness pre-checks for `documento`/`correo_electronico`.
5. **Controller layer**: `@RestController` endpoints for CRUD + search + pagination; extend `ExceptionController` with 409 (unique violations) and 429 (rate limit).
6. **Security filters**: bucket4j rate-limit filter keyed by client IP; CORS configuration bean restricted to `http://localhost:3000`.
7. **MongoDB logging**: a logging component writing to `info`/`warns`/`error` collections.
8. **Docs**: the three markdown deliverables.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/com/sena/mysqlwithjpa/entity/User.java` | Modified | Full rewrite to real schema with enums and trigger-managed columns |
| `src/main/java/com/sena/mysqlwithjpa/repository/UserRepository.java` | Modified | `CrudRepository` → `JpaRepository`; derived AND/OR queries; paging |
| `src/main/java/com/sena/mysqlwithjpa/controller/MainController.java` | Modified | `@Controller`/`/demo` → `@RestController`; full CRUD + search + pagination |
| `src/main/java/com/sena/mysqlwithjpa/controller/ExceptionController.java` | Modified | Add 409 and 429 handlers |
| `src/main/java/com/sena/mysqlwithjpa/service/` | New | UserService (hashing, validation, trigger-aware persistence) |
| `src/main/java/com/sena/mysqlwithjpa/config/` | New | CORS config, rate-limit filter, MongoDB logging wiring |
| `src/main/resources/application.properties` | Modified | Env-var placeholders, Aiven host/port/db, ssl-mode REQUIRED, `ddl-auto=validate` |
| `.env` + `.gitignore` | New/Modified | Local credentials file; gitignored |
| `src/main/resources/static/**` | Removed | Vanilla static frontend deleted |
| `pom.xml` | Modified | Add security (crypto), bucket4j, data-mongodb |
| `src/test/**` | Modified/New | TDD suite: unit (Mockito/MockMvc), repository slices, Testcontainers alignment |
| `docs/` | New | `ARCHITECTURE.md`, `STEP_BY_STEP.md`, `queries/SEARCH_AND_PAGINATION.md` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Testcontainers auto-schema conflicts with the real trigger-managed DDL (`@ServiceConnection` + `ddl-auto=validate` will fail against an empty container) | High | Provide a `schema.sql` (with triggers) for test scope, or scope repo slices to `@DataJpaTest` with explicit schema. **Decision for user/orchestrator: preferred test-database strategy.** |
| Trigger-managed column mapping drift (app accidentally writes `ultima_actualizacion`/`rol` default) | Med | `insertable=false, updatable=false` on trigger-owned columns; integration test asserts DB-set values |
| BCrypt cross-compat assumption (`$2b$` Express hashes vs Spring's `$2a$` encoder) breaks future login flows in Express | Med | Verify round-trip: Spring must VERIFY `$2b$` hashes; add a unit test hashing in Spring and verifying a known `$2b$10$` hash from the real DB dump |
| Rate-limit filter misclassification of shared networks (NAT/proxy) throttling legitimate users | Low | Key by `X-Forwarded-For` fallback to remote addr; threshold values left to design-phase decision |
| Old static frontend bookmarks/calls from existing frontend code hit removed `/demo/**` routes | Low | Confirm with user that React app targets new routes; document route table in `ARCHITECTURE.md` |
| MongoDB connection absent in dev environments blocks startup | Med | Logger degrades gracefully (skip on connection failure) or make Mongo logging optional via property. **Decision for design phase.** |
| REST route naming and response envelope not fixed by the intent (`/api/users` vs `/users`, wrapped vs raw payloads) | Low | Deferred to sdd-design with a concrete proposal; not a product blocker |

## Rollback Plan

1. The change lives entirely in git — revert the change branch / `git revert` the merge commit restores the previous `@Controller` CRUD.
2. `src/main/resources/static/**` deletion is recoverable from git history (`git checkout <pre-change-sha> -- src/main/resources/static`).
3. The database is never touched (`ddl-auto=validate`, no migration scripts, no DML beyond User CRUD) — no DB rollback needed, triggers remain intact.
4. New dependencies (security, bucket4j, data-mongodb) are additive to `pom.xml`; reverting `pom.xml` + code removes them cleanly.
5. `.env` and env-var config revert to the previous `application.properties` from git if the deployment target needs the old local setup.

## Dependencies

- Existing Aiven MySQL instance with the real `usuario` table and its triggers (user-confirmed present).
- Existing Express backend continues to own login; shared `$2b$10$` BCrypt hash compatibility.
- Docker daemon for Testcontainers-based tests (per project config).
- React frontend at `http://localhost:3000` (CORS target; not part of this repo).

## Success Criteria

- [ ] All CRUD + search + paginated-list endpoints respond under the new `@RestController`; `/demo/**` and `static/**` removed.
- [ ] Entity maps cleanly to the real Aiven schema with `ddl-auto=validate` and the app boots against the actual instance without altering DDL.
- [ ] DB triggers demonstrably own `ultima_actualizacion` and `rol` default in integration tests.
- [ ] Passwords are BCrypt-hashed before insert; a Spring-generated hash verifies with the Express-compatible `$2b` variant and vice versa.
- [ ] Rate limiter returns 429 when a single IP exceeds the configured threshold; CORS rejects origins other than `http://localhost:3000`.
- [ ] Logs land in `info`/`warns`/`error` MongoDB collections.
- [ ] No credentials in the repo; `.env` ignored; config uses `${...}` placeholders only.
- [ ] Three docs delivered; test suite green via `./mvnw test` under strict TDD.
