# Apply Progress: rest-api-redesign — Phases 1–2

**Change**: rest-api-redesign
**Mode**: Strict TDD
**Delivery**: chained PRs, chain strategy `stacked-to-main`
**Branches**: `rest-api-redesign/pr-1-foundation` (Phase 1), `rest-api-redesign/pr-2-domain` (Phase 2, stacked on PR 1 @ 569faa6)
**Date**: 2026-09-24

## Completed Tasks (6/34 verified; Phase 2 authored but UNVERIFIED)

- [x] 1.1 RED: `AppConfigurationPropertiesTest` — 7 assertions against committed `application.properties` — FAILED against previous hardcoded config (7/7 failures observed)
- [x] 1.2 GREEN: pom.xml — added `spring-boot-starter-security` (crypto/spring-context), `spring-boot-starter-data-mongodb`, `com.bucket4j:bucket4j-core` — `./mvnw compile` OK
- [x] 1.3 GREEN: `application.properties` rewritten per design Decision 10 — config test now 7/7 PASS
- [x] 1.4 GREEN: `.env.example` with placeholders only; `.env` gitignored (verified via `git check-ignore` and a scratch `.env` file, then removed)
- [x] 1.5 REFACTOR/cleanup: deleted `src/main/resources/static/**` (3 files)
- [x] 1.6 Verified `compose.yaml` carries only Spring Initializr dev placeholders (`myuser`/`secret`), distinct from production Aiven credentials — no change needed; dev-only nature is documented in Phase 7.2

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `src/test/java/com/sena/mysqlwithjpa/config/AppConfigurationPropertiesTest.java` | Unit | N/A (new test; no prior unit tests) | ✅ 7/7 failed against hardcoded props | ✅ 7/7 passed after 1.3 | ✅ 7 distinct config scenarios (placeholders, URL, ssl-mode REQUIRED, no DISABLED, ddl-auto=validate, open-in-view=false, mongo/ratelimit placeholders, no literal credentials) | ➖ None needed (config files) |
| 1.2 | — (compile gate) | Build | N/A | ➖ Skipped: dependency addition, no logic | ✅ `mvnw compile` OK | ➖ Skipped: structural only | ➖ |
| 1.3 | (same as 1.1) | Unit | — | — | ✅ 7/7 passed | ➖ Covered by 1.1 triangulation | ✅ Kept logging settings; translated to English |
| 1.4 | — (gitignore contract) | Repo | N/A | ➖ Skipped: structural | ✅ `git check-ignore .env` confirmed; scratch `.env` not listed by `git status` | ➖ Single behavior | ➖ |
| 1.5 | — (deletion) | Repo | N/A | ➖ Skipped: structural deletion | ✅ static tree removed, compile still OK | ➖ | ➖ |
| 1.6 | — (inspection) | Repo | N/A | ➖ Skipped: verification-only task | ✅ compose.yaml values are dev placeholders, not production | ➖ | ➖ |

Triangulation skipped for 1.2/1.4/1.5/1.6: structural/cleanup tasks with a single verifiable outcome (per strict-tdd skip criteria).

### Test Summary
- **Total tests written**: 7
- **Total tests passing**: 7 (`mvnw test -Dtest=AppConfigurationPropertiesTest` green)
- **Layers used**: Unit (7)
- **Approval tests**: None — no refactoring of existing logic

## Work Unit Evidence (Unit 1: Foundation)

- **Focused test command**: `.\mvnw.cmd test -Dtest=AppConfigurationPropertiesTest` → 7/7 PASS (RED observed first: 7/7 FAIL)
- **Runtime harness**: `./mvnw compile` PASS; full `./mvnw test` NOT runnable — Docker daemon unavailable (Testcontainers blocks `MysqlwithjpaApplicationTests`). Spring Boot app boot against Aiven not attempted (no runtime boundary change in this slice beyond config shape; Aiven credentials not available in this environment).
- **Rollback boundary**: `git revert 16a9c1b 7b83805` (or revert `pom.xml`, `application.properties`, `.gitignore`; restore `static/**` from `Reto4`). No DB touched.

## Files Changed

| File | Action | What |
|------|--------|------|
| `pom.xml` | Modified | +spring-boot-starter-security, +spring-boot-starter-data-mongodb, +com.bucket4j:bucket4j-core |
| `src/main/resources/application.properties` | Modified | Env-var placeholders per design Decision 10; `ddl-auto=validate`; `open-in-view=false`; credentials removed |
| `.env.example` | Created | Placeholder values only for MYSQL_*, MONGODB_URI, RATE_LIMIT_* |
| `.gitignore` | Modified | `.env` ignored |
| `src/main/resources/static/{index.html,js/app.js,css/styles.css}` | Deleted | Legacy static frontend |
| `src/test/java/com/sena/mysqlwithjpa/config/AppConfigurationPropertiesTest.java` | Created | 7 config-discipline assertions (RED→GREEN) |
| `openspec/changes/rest-api-redesign/tasks.md` | Modified | Tasks 1.1–1.6 marked [x] |

## Commits

- `16a9c1b` build(rest-api): add spring-security, mongodb and bucket4j dependencies
- `7b83805` feat(config): externalize datasource credentials to env vars, add .env template, drop legacy static frontend

## Deviations from Design

None — implementation matches design Decision 10. (bucket4j version chosen: current stable 8.x line under `com.bucket4j`; exact coordinate `bucket4j-core`.)

## Issues / Risks

- **Docker daemon unavailable on this machine** — Testcontainers-backed tests (pre-existing `MysqlwithjpaApplicationTests`, and Phase 2/5 repository slices) cannot run here. Phase 1 relied on the unit layer only, as permitted. Flag for the orchestrator: Phases 2 and 5 will BLOCK without Docker.
- `./mvnw test` (full suite) was NOT run because it triggers the Testcontainers context test and fails without Docker; only the focused unit test was executed per strict-tdd guidance.

## Remaining Tasks

- Phase 2 (2.1–2.9): test schema + entity/enums + repository
- Phases 3–7 per tasks.md
- Chain strategy in tasks.md header still reads `pending`; orchestrator resolved `stacked-to-main` for this run.

## Apply Progress: Phase 2 (Domain Mapping, PR 2 branch)

**Docker daemon: UNAVAILABLE on this machine** — all Testcontainers slices were authored under strict RED→GREEN ordering but could NOT be executed. Every Phase 2 task below is therefore **UNVERIFIED** and remains unchecked (`- [ ]`) in tasks.md per the apply gate.

- [ ] 2.1–2.3 RED (authored, unexecuted): `src/test/resources/schema.sql` (full `usuario` DDL + 3 triggers, `^` separators for the compound `validarContrasena` body), `src/test/resources/application.properties` (sql.init wiring, `ddl-auto=validate`; datasource/mongo/ratelimit placeholder keys duplicated verbatim from main so the Phase 1 config contract test still passes — this file shadows the main one on the test classpath), `TestcontainersConfiguration` pinned to `mysql:8.4`, `SchemaTriggersTest` (column set, 3 triggers in information_schema, ddl-auto=validate by context boot plus Environment assertion)
- [ ] 2.4–2.5 GREEN (authored, compile-verified): enums `TipoDocumento` (CC, TI), `Rol` (ADMIN, USUARIO), `TipoApoyo` (regular, alimentacion, transporte); `User` rewritten per design Decision 7 exactly (`@Table("usuario")`, `@DynamicInsert`, IDENTITY `id_usuario`, `Long documento`, `EnumType.STRING` + columnDefinitions, `@JsonProperty(WRITE_ONLY)` on `contrasena`, `ultima_actualizacion` insertable=false/updatable=false, `fecha_registro` updatable=false)
- [ ] 2.6–2.7 RED+GREEN (authored): `UserRepositoryTest` round-trip (documento `Long` beyond int range, all fields) + `UserRepository` is now `JpaRepository<User, Integer>` with `existsByDocumento(Long)` / `existsByCorreoElectronico(String)`
- [ ] 2.8–2.9 RED+GREEN (authored): trigger-ownership tests — `ultima_actualizacion` set by DB default on insert and rewritten by `actualizarFechaUsuario` on update (application never carries the column), null `rol` → `USUARIO` via `rolDefecto` on fresh read, explicit `ADMIN` respected, real 60-char `$2b$10$…` BCrypt hash passes `validarContrasena` byte-identical. **2.9 resolution: no mapping change needed** — `fecha_registro` keeps `updatable=false` with the application setting it on insert; the schema.sql mirror has NO DB default for it. The production-DDL open question (`DEFAULT CURRENT_TIMESTAMP`?) remains open → task 7.5.

### TDD Cycle Evidence (Phase 2 — authoring order only; NO test execution without Docker)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1–2.3 | `src/test/resources/schema.sql`, `src/test/java/.../repository/SchemaTriggersTest.java` | Integration (Testcontainers) | ✅ AppConfigurationPropertiesTest 7/7 baseline | ✅ Written (would fail: old entity maps table `user`, mySQL container absent) | ⚠️ UNVERIFIED — not executed | ✅ 3 scenarios (column set, triggers, validate-only) | ➖ None |
| 2.4–2.5 | (covered by 2.3's schema-validate boot + 2.6 round-trip) | Integration | 7/7 baseline | ➖ RED implicit via 2.3 | ⚠️ UNVERIFIED — compile OK only | ➖ Structural (mapping per Decision 7) | ➖ None |
| 2.6–2.7 | `src/test/java/.../repository/UserRepositoryTest.java` | Integration (Testcontainers) | 7/7 baseline | ✅ Written (would fail: CrudRepository lacked existsBy/finder + old entity fields) | ⚠️ UNVERIFIED — not executed | ✅ round-trip incl. documento beyond int range | ➖ None |
| 2.8 | (same file — trigger ownership) | Integration (Testcontainers) | 7/7 baseline | ✅ Written (would fail without @DynamicInsert/insertable=false) | ⚠️ UNVERIFIED — not executed | ✅ 4 scenarios (update trigger, null rol default, explicit ADMIN, BCrypt under trigger) | ➖ None |
| 2.9 | — | Mapping decision | — | ➖ | ⚠️ UNVERIFIED — no change required by current design; prod DDL check deferred to 7.5 | ➖ | ➖ |

### Executed verification (this environment)

- **Focused unit**: `.\mvnw.cmd -o test -Dtest=AppConfigurationPropertiesTest` → PASS (7/7). Proves the new test-classpath `application.properties` shadowing preserves the Phase 1 contract.
- **Compile**: `.\mvnw.cmd -o test-compile` → PASS (all new tests + entity + repository compile under Spring Boot 4.1).
- **Runtime harness (repository slices)**: NOT RUNNABLE — no Docker daemon. This is the top risk of this batch.
- **Rollback boundary**: `git revert aea6cda 6a825c8 12a473a e2b31a5 eb7a736` (or revert `entity/`, `repository/`, `controller/MainController.java`, `src/test/**`, keeping Phase 1 commits untouched).

### Files Changed (Phase 2)

| File | Action | What |
|------|--------|------|
| `openspec/.../tasks.md` | Modified | Chain strategy header → `stacked-to-main` (separate tiny commit); 2.1–2.9 stay unchecked (unverified) |
| `src/test/resources/schema.sql` | Created | `usuario` DDL + triggers `rolDefecto`, `actualizarFechaUsuario`, `validarContrasena`; `^` separator; varchar(255) columns |
| `src/test/resources/application.properties` | Created | sql.init wiring + verbatim placeholder keys (shadow-safe for Phase 1 test) |
| `src/test/java/.../TestcontainersConfiguration.java` | Modified (public, `mysql:8.4`) | Reuse for repository slices |
| `src/test/java/.../repository/SchemaTriggersTest.java` | Created | Table/column-set/trigger/validate-only assertions |
| `src/main/java/.../entity/{TipoDocumento,Rol,TipoApoyo}.java` | Created | Enums matching the production ENUM columns |
| `src/main/java/.../entity/User.java` | Rewritten | Full Decision 7 mapping |
| `src/main/java/.../controller/MainController.java` | Modified | Compile-preserving interim: `/demo/add` no longer writes (entity has no name/email); rejects with UnsupportedOperationException until Phase 4 replaces it. `/demo/all` unchanged |
| `src/main/java/.../repository/UserRepository.java` | Modified | `JpaRepository<User, Integer>` + `existsByDocumento` / `existsByCorreoElectronico` |
| `src/test/java/.../repository/UserRepositoryTest.java` | Created | Round-trip + 4 trigger-ownership scenarios |

### Commits (Phase 2, branch `rest-api-redesign/pr-2-domain`)

- `aea6cda` chore(openspec): record resolved chain strategy stacked-to-main
- `6a825c8` docs(openspec): track rest-api-redesign planning artifacts (proposal, design, specs)
- `12a473a` test(domain): add usuario test schema with triggers and container wiring
- `e2b31a5` feat(domain): rewrite User entity onto the usuario table with type enums
- `eb7a736` feat(domain): upgrade UserRepository to JpaRepository with trigger-ownership tests

### Deviations from Design (Phase 2)

1. **`MainController` touched outside Phases 3–4 scope** — minimal, compile-forcing: the User rewrite removed `name`/`email`, so `/demo/add` cannot compile. It now rejects writes via `UnsupportedOperationException` pointing to Phase 4. `/demo/all` and the `@Controller /demo` mapping are unchanged; Phase 4 still owns the real rewrite and the 404 regression tests.
2. **schema.sql column sizes are `varchar(255)`** to match Hibernate's validate-time defaults; production column sizes are DBA-owned and unknown here — verify at 2.3's first real run / task 7.5.
3. **Planning artifacts committed on this branch** (separate `docs(openspec)` commit): proposal/design/exploration/specs/config were untracked; they are the hybrid store's source of truth and should not float uncommitted. Reviewers can exclude that commit from the PR-2 code review.
4. **`fecha_registro` open question NOT resolved** — design's fallback kept (`updatable=false`, application sets it); test schema has no default. Task 7.5 must confirm the production DDL.

### Issues / Risks (Phase 2)

- **Docker daemon unavailable** — every 2.1–2.9 assertion is authored-only. Do not treat this slice as done; scheduling a Docker-enabled run of `./mvnw test -Dtest=SchemaTriggersTest,UserRepositoryTest` is the immediate gate.
- **Hibernate validate vs MySQL ENUM column types** is a plausible first-run failure (`enum('CC','TI')` columnDefinitions vs reported JDBC type/length); catch at the Docker run; fix by aligning column definition/length if validation complains.

## Files Changed (cumulative)

| File | Action | What |
|------|--------|------|
| `pom.xml` | Modified | +spring-boot-starter-security, +spring-boot-starter-data-mongodb, +com.bucket4j:bucket4j-core |
| `src/main/resources/application.properties` | Modified | Env-var placeholders per design Decision 10; `ddl-auto=validate`; `open-in-view=false`; credentials removed |
| `.env.example` | Created | Placeholder values only for MYSQL_*, MONGODB_URI, RATE_LIMIT_* |
| `.gitignore` | Modified | `.env` ignored |
| `src/main/resources/static/{index.html,js/app.js,css/styles.css}` | Deleted | Legacy static frontend |
| `src/test/java/com/sena/mysqlwithjpa/config/AppConfigurationPropertiesTest.java` | Created | 7 config-discipline assertions (RED→GREEN) |
| `openspec/changes/rest-api-redesign/tasks.md` | Modified | 1.1–1.6 [x]; chain strategy header resolved |
| `openspec/changes/rest-api-redesign/{proposal,design,exploration}.md`, `specs/**`, `openspec/config.yaml` | Created (tracked) | Planning artifacts committed |
| `src/test/resources/{schema.sql,application.properties}` | Created | Test schema with 3 triggers; sql.init wiring |
| `src/test/java/com/sena/mysqlwithjpa/repository/{SchemaTriggersTest,UserRepositoryTest}.java` | Created | Schema + round-trip + trigger ownership (unverified) |
| `src/test/java/com/sena/mysqlwithjpa/TestcontainersConfiguration.java` | Modified | `mysql:8.4`, public |
| `src/main/java/com/sena/mysqlwithjpa/entity/{User,TipoDocumento,Rol,TipoApoyo}.java` | Rewritten/Created | Decision 7 mapping |
| `src/main/java/com/sena/mysqlwithjpa/repository/UserRepository.java` | Modified | JpaRepository + existsBy* |
| `src/main/java/com/sena/mysqlwithjpa/controller/MainController.java` | Modified | Legacy `/demo/add` write path removed (compile interim) |

## Deviations from Design

Phase 1: None — implementation matches design Decision 10. (bucket4j version chosen: current stable 8.x line under `com.bucket4j`; exact coordinate `bucket4j-core`.)

Phase 2: see "Deviations from Design (Phase 2)" above.

## Issues / Risks

- **Docker daemon unavailable on this machine** — Testcontainers-backed tests (Phase 2 slices, future Phase 5) cannot run here. Phase 2's entire suite is authored but UNVERIFIED; run `./mvnw test -Dtest=SchemaTriggersTest,UserRepositoryTest` on a Docker-enabled host before marking 2.1–2.9 `[x]`.
- `./mvnw test` (full suite) intentionally NOT run: `MysqlwithjpaApplicationTests` boot requires Testcontainers (and a Mongo URI); only the focused unit test was executed.
- **PR budget**: Phase 2 slice = **539 changed lines** (additions+deletions across `src/`), above the 400 budget. Honest split for review, at PR-creation time: PR 2a = commits `aea6cda`,`12a473a`,`e2b31a5` (schema + entity, ≈369 lines, compiles standalone) → PR 2b = commit `eb7a736` (repository + trigger tests, ≈172 lines). Do not shrink code to fit; split along commit boundaries as listed.

## Remaining Tasks

- Phase 2 verification run (Docker): SchemaTriggersTest + UserRepositoryTest, then mark 2.1–2.9 `[x]`
- Tasks 3.1–7.5 per tasks.md
- Phase 4 must replace `MainController` ( interim rejection of `/demo/add` becomes a hard 404)

## Status

6/34 tasks verified; 2.1–2.9 authored but **UNVERIFIED (Docker unavailable)** — overall Phase 2 status: **partial**. Not ready for archive; ready for Phase 3 (security plumbing — unit/web slice, no Docker needed) or for the Docker verification run, whichever the orchestrator schedules first.
