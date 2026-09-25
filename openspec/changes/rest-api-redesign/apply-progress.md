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

## Apply Progress: Phase 3 (Security Plumbing, PR 3 branch)

**Branch**: `rest-api-redesign/pr-3-security` (stacked on `rest-api-redesign/pr-2-domain` @ 3fe772a). **Docker: not required** — all Phase 3 slices are unit or `@WebMvcTest` and were executed for real in this environment.

- [x] 3.1–3.2 RED→GREEN (verified): `PasswordEncoderTest` (unit, no Spring context — the bean contract of `SecurityConfig`) + `config/SecurityConfig.java` (`@EnableWebSecurity`, `PasswordEncoder` = `BCryptPasswordEncoder(10)`, permit-all chain with CSRF/form-login/basic disabled, `cors(withDefaults())`). **Cross-stack compatibility verified for real**: the documented constant is a genuine `$2b$10$` hash of `"password"` generated with bcryptjs (the Express stack's bcrypt library) — Spring's encoder verifies it `true`, wrong plaintext `false`; fresh hashes start with `$2a$10$`.
- [x] 3.3–3.4 RED→GREEN (verified): `SanitizerTest` (11 cases: script tags, uppercase variants, `onerror=`/`onclick =`, `../` + `..\` traversal, `<b>` markup vs. accepted `José Lía`, null/blank, ordinary punctuation) + `util/Sanitizer.java` (`requireClean(field, value)` pure static, blocklist of 3 regex classes) + `util/SanitizationException.java`.
- [x] 3.5–3.6 RED→GREEN (verified): `CorsConfigTest` (`@WebMvcTest(MainController.class)` + MockMvc, `@Import({SecurityConfig, CorsConfig})`, mocked `UserRepository`) — allowed-origin preflight + actual request get the allow-origin grant; foreign origin: 403 preflight rejection, NO allow-origin header, NO wildcard, never a 500; Origin-less requests proceed normally → `config/CorsConfig.java` (single origin `http://localhost:3000`, explicit methods/headers, no wildcard).
- [x] 3.7–3.8 RED→GREEN (verified): `RateLimitFilterTest` (4 threat-matrix cases) + `exception/RateLimitExceededException.java` (carries `retryAfterSeconds`) + `config/RateLimitFilter.java` (bucket4j `OncePerRequestFilter`, `ConcurrentHashMap<String, Bucket>`, key = first `X-Forwarded-For` else remote addr, 429 ApiError-shaped envelope + `Retry-After`, fail-open on internal failure) + `config/RateLimitConfig.java` (`@ConfigurationProperties app.ratelimit.*`, `FilterRegistrationBean` for `/api/**`, highest precedence).

### TDD Cycle Evidence (Phase 3 — all executed; no Docker involved)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1–3.2 | `security/PasswordEncoderTest.java` | Unit | ✅ AppConfigurationPropertiesTest 7/7 baseline | ✅ Compile failure (`SecurityConfig` missing) | ✅ 3/3 pass | ✅ 3 cases (real bcryptjs `$2b$10$` pair, own-plaintext round-trip incl. wrong plaintext, cost-10 format) + fixed an over-claimed constant after a real FAILED run | ✅ Clean |
| 3.3–3.4 | `util/SanitizerTest.java` | Unit | ✅ 7/7 baseline | ✅ Compile failure (`Sanitizer` missing) | ✅ 11/11 pass | ✅ 8 reject payloads + 3 accept paths (unicode, null/blank, punctuation) | ✅ Clean (3 regex classes extracted to a list) |
| 3.5–3.6 | `config/CorsConfigTest.java` | Web slice (`@WebMvcTest` + MockMvc) | ✅ 7/7 baseline | ✅ Compile failure (`CorsConfig` missing) | ✅ 4/4 pass | ✅ 4 scenarios (preflight allow, actual allow, foreign deny not-500, no-Origin pass) | ➖ None needed |
| 3.7–3.8 | `config/RateLimitFilterTest.java` | Unit (MockFilterChain-style harness) | ✅ 7/7 baseline | ✅ Compile failure (filter/exception missing) | ✅ 4/4 pass | ✅ 4 threat-matrix scenarios (429 envelope + Retry-After, global bucket, XFF independence, fail-open) | ➖ None needed |

### Test Summary (Phase 3)
- **Total tests written**: 22 (3 + 11 + 4 + 4)
- **Total tests passing**: 22; combined run with Phase 1 regression: **29/29** (`./mvnw -o test -Dtest=PasswordEncoderTest,SanitizerTest,CorsConfigTest,RateLimitFilterTest,AppConfigurationPropertiesTest` → BUILD SUCCESS)
- **Layers used**: Unit (18), Web slice (4). No Testcontainers, no Docker, no live MySQL/Mongo.
- **Approval tests**: None — no refactoring of existing logic.

### Work Unit Evidence (Unit 3: Security plumbing)

- **Focused test command**: `.\mvnw.cmd -o test "-Dtest=PasswordEncoderTest,SanitizerTest,CorsConfigTest,RateLimitFilterTest,AppConfigurationPropertiesTest"` → **29/29 PASS** (observed).
- **Runtime harness**: CORS behavior executed through a real Spring Security filter chain in the `@WebMvcTest` slice (preflight 200/403, allow-origin headers). Rate limit executed against the real `RateLimitFilter` + bucket4j buckets (429 + `Retry-After` observed). Full boot against Aiven/MySQL not attempted (out of this slice; domain wiring remains Docker-gated from Phase 2). Live curl harness against a running instance remains a Phase 4+/verify activity.
- **Rollback boundary**: `git revert add67e2 da16db3 4363d4f c012856` (or revert `config/SecurityConfig.java`, `config/CorsConfig.java`, `config/RateLimitConfig.java`, `config/RateLimitFilter.java`, `exception/RateLimitExceededException.java`, `util/`; no prior-phase files touched except tasks.md marks).

### Files Changed (Phase 3)

| File | Action | What |
|------|--------|------|
| `src/main/java/com/sena/mysqlwithjpa/config/SecurityConfig.java` | Created | BCrypt encoder (strength 10) + permit-all chain, CSRF/form-login/basic off, cors() enabled |
| `src/test/java/com/sena/mysqlwithjpa/security/PasswordEncoderTest.java` | Created | Cross-stack `$2b$10$` verification (bcryptjs-generated pair), round-trip, cost check |
| `src/main/java/com/sena/mysqlwithjpa/util/Sanitizer.java` | Created | `requireClean` blocklist: HTML tags, `on*=`, `../` traversal |
| `src/main/java/com/sena/mysqlwithjpa/util/SanitizationException.java` | Created | Violation type (→ 400 mapping in Phase 4) |
| `src/test/java/com/sena/mysqlwithjpa/util/SanitizerTest.java` | Created | 11 cases |
| `src/main/java/com/sena/mysqlwithjpa/config/CorsConfig.java` | Created | Single-origin CORS source (no wildcard) |
| `src/test/java/com/sena/mysqlwithjpa/config/CorsConfigTest.java` | Created | 4 threat-matrix web-slice cases |
| `src/main/java/com/sena/mysqlwithjpa/config/RateLimitFilter.java` | Created | bucket4j per-IP filter, 429 envelope + Retry-After, fail-open |
| `src/main/java/com/sena/mysqlwithjpa/config/RateLimitConfig.java` | Created | `app.ratelimit.*` binding + `/api/**` registration |
| `src/main/java/com/sena/mysqlwithjpa/exception/RateLimitExceededException.java` | Created | Carries retry-after seconds |
| `src/test/java/com/sena/mysqlwithjpa/config/RateLimitFilterTest.java` | Created | 4 threat-matrix cases |
| `openspec/changes/rest-api-redesign/tasks.md` | Modified | 3.1–3.8 marked [x] |

### Commits (Phase 3, branch `rest-api-redesign/pr-3-security`)

- `add67e2` feat(security): add BCrypt PasswordEncoder and permit-all security chain
- `da16db3` feat(security): add input sanitizer rejecting XSS and path-traversal payloads
- `4363d4f` feat(security): restrict CORS to the http://localhost:3000 frontend
- `c012856` feat(security): add global per-IP bucket4j rate limiting with 429 envelope

### Deviations from Design (Phase 3)

1. **429 rendered by the filter itself, not by `ExceptionController`** — design Decision 3 routes `RateLimitExceededException` through the advice, but servlet filters run before the DispatcherServlet, so advice can never see filter-thrown exceptions. The filter renders the identical ApiError-shaped JSON + `Retry-After`; the 429 advice handler in task 4.5 still lands as defense-in-depth for MVC-side throws.
2. **`.cors(withDefaults())` lives in `SecurityConfig`** — Spring Security only applies its `CorsFilter` when `cors()` is enabled on the chain; the dedicated `CorsConfig` bean carries the policy (design intent preserved).
3. **Known-hash constant is a freshly generated bcryptjs pair, not a production dump value** — no production dump is present in this repo. The constant is a real `$2b$10$` hash of the documented plaintext `"password"` generated with bcryptjs locally; the earlier well-known jBCrypt sample constant turned out to NOT be a hash of `"password"` (caught by a real failing run of 3.1). A dump-derived pair can replace it without changing the test logic.
4. **`SanitizationException` added** beyond the design file table — the violation type "mapped to 400" needs a concrete class; its `@ExceptionHandler` wiring belongs to Phase 4 (`ExceptionController`).

### Issues / Risks (Phase 3)

- **PR budget**: Phase 3 slice = **689 changed lines** (681+, 8− across 12 `src/` files + tasks.md marks), over the 400 budget as one PR. Honest commit-boundary split: **PR 3a** = `add67e2` + `da16db3` (encoder + sanitizer ≈ 230 lines) → **PR 3b** = `4363d4f` (CORS ≈ 122 lines) → **PR 3c** = `c012856` (rate limit ≈ 337 lines). Every commit is independently green and revertable; do not shrink code to fit.
- **Test `src/test/resources/application.properties`** still shadows the main config (Phase 2 note); the `@WebMvcTest` slice boots fine with it because the web slice never initializes JPA/Mongo.
- Mockito JDK self-attach warning remains cosmetic (Maven-surefire agent config out of scope).

## Status

14/34 tasks verified (Phases 1 + 3 complete and tested); 2.1–2.9 authored but **UNVERIFIED (Docker unavailable)**. Next: Phase 2 Docker verification run, then Phase 4 (Core CRUD) on branch `rest-api-redesign/pr-4-core` stacked on PR 3. Not ready for archive.

## Apply Progress: Phase 4 (Core CRUD, PR 4 branch) — VERIFIED

**Branch**: `rest-api-redesign/pr-4-core` (stacked on `rest-api-redesign/pr-3-security` @ 6ae1b5f). **Docker: not required** — Mockito unit + `@WebMvcTest` slices, all executed for real in this environment.

- [x] 4.1→4.2→4.3 RED→GREEN (verified): `UserServiceTest` (JUnit 5 + Mockito, mock `UserRepository` + `PasswordEncoder`, 14 tests): RED = compile failure (`UserService`/DTOs/exceptions absent, observed) → GREEN **14/14 PASS**. Covers spec cases (a) hash-before-save (stored ≠ plaintext), (b) short `contrasena` → `IllegalArgumentException` BEFORE encoder/repository are touched, (c) duplicate `documento` / `correoElectronico` → `DuplicateResourceException` naming the field, (d) `<script>` in `primerNombre` → `SanitizationException` with zero persistence + triangulation (`José Lía` passes byte-identical, explicit `rol=ADMIN`/`tipoApoyo=alimentacion` kept), (e) update without `contrasena` leaves the stored hash byte-identical (encoder never called) + triangulation (update WITH contrasena re-hashes), (f) update of missing id → `NotFoundException`, (g) trigger-owned columns never carried (`ultimaActualizacion` null on insert, `fechaRegistro` service-set, `rol` null-omitted for `rolDefecto`). Plus findById/delete happy+404 paths. Files: `service/exception/DuplicateResourceException`, `service/exception/NotFoundException` (both carry named context), `service/UserService`, DTOs `dto/UserRequest` + `dto/UserResponse` as **Java records** (see deviation 1).
- [x] 4.4→4.5 RED→GREEN (verified): `UserControllerTest` (`@WebMvcTest(MainController.class)` + `@Import({SecurityConfig, CorsConfig})`, `@MockitoBean UserService`, 16 tests). RED = context load failure (16/16 ERROR) because the old `MainController` still required `UserRepository` — observed. GREEN: `MainController` rewritten as `@RestController /api/users` (POST 201 with `@Validated(OnCreate)`, GET 200, PUT 200 `@Valid`, DELETE 204 empty); legacy `/demo/**` deleted; `ExceptionController` extended with handlers for bean validation (400), `SanitizationException`/`IllegalArgumentException` (400), `NotFoundException` (404), `DuplicateResourceException` + `DataIntegrityViolationException` fallback (409), `RateLimitExceededException` (429 + `Retry-After`), and unrouted paths (`NoHandlerFoundException`/`NoResourceFoundException` → 404 — see deviation 2). `ApiError.java` untouched (verified by diff).
- [x] 4.6 routing threat-matrix regression (verified): POST `/demo/add` → 404, GET `/demo/all` → 404, POST `/login` → 404. Authored inside the 4.4 RED (observed failing: `/demo/*` gave 500 via the interim `UnsupportedOperationException`/legacy mapping, POST `/login` already 404) and passing since the 4.5 rewrite.

### TDD Cycle Evidence (Phase 4 — all executed; no Docker)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 4.1–4.3 | `service/UserServiceTest.java` | Unit (Mockito) | ✅ 29/29 baseline run before edits | ✅ Compile failure (`UserService` missing) | ✅ 14/14 pass | ✅ 12 scenarios incl. unicode accept, re-hash update, duplicate per field, trigger-hygiene | ✅ `applyMutableFields`/`sanitize` helpers extracted; green after |
| 4.4–4.5 | `controller/UserControllerTest.java` | Web slice (`@WebMvcTest`) | ✅ 29/29 baseline | ✅ Context load failure (16/16 ERROR, old controller contract) | ✅ 16/16 pass | ✅ Status matrix 201/200/204/400×3/404×5/409/429/500 + no-password serialization assertions | ✅ `ExceptionController` build/buildBody helpers for the new handlers; existing handlers untouched |
| 4.6 | (3 legacy-route tests inside `UserControllerTest`) | Web slice | ✅ 29/29 baseline | ✅ observed 500/200 while legacy routes lived | ✅ 3/3 pass after rewrite | ➖ Single shape per route (status-only contract) | ➖ None |

Two real failures were caught and fixed DURING `UserControllerTest`'s GREEN pass (honest iteration, not silent): (1) `@Validated(OnCreate.class)` ignored the default group — `OnCreate` now extends `jakarta.validation.groups.Default` so required fields still validate on POST; (2) unrouted paths fell into the 500 catch-all — added a 404 handler for `NoHandlerFoundException`/`NoResourceFoundException`, which is also what makes 4.6's contract hold.

### Test Summary (Phase 4)
- **Total tests written**: 30 (14 service + 16 controller)
- **Total tests passing**: 30; combined run with Phases 1+3 regression: **59/59** (`./mvnw -o test -Dtest=UserServiceTest,UserControllerTest,PasswordEncoderTest,SanitizerTest,CorsConfigTest,RateLimitFilterTest,AppConfigurationPropertiesTest` → BUILD SUCCESS)
- **Layers used**: Unit (14), Web slice (16). No Testcontainers, no Docker, no live MySQL/Mongo.
- **Approval tests**: None — MainController's old behavior was superseded by spec (its only live read path, `/demo/all`, is replaced per contract by 404 + `/api/users` coverage).

### Work Unit Evidence (Unit 4: Core CRUD)

- **Focused test command**: `.\mvnw.cmd -o test "-Dtest=UserServiceTest,UserControllerTest"` → **30/30 PASS** (observed). Full slice regression: **59/59 PASS** (observed).
- **Runtime harness**: full status matrix executed through a real Spring Security filter chain + DispatcherServlet in the `@WebMvcTest` slice (201/200/204/400/404/409/429/500, `Retry-After` header, password-free JSON bodies). Boot against Aiven/MySQL not attempted (runtime DB boundary remains Docker-gated, Phase 2 carry-over); `PUT /api/users/{id}` without `contrasena` exercising the validation-group split ran green through MockMvc.
- **Rollback boundary**: `git revert 4140bd1 2fc8066` (or revert `service/`, `dto/`, `controller/MainController.java`, `controller/ExceptionController.java`, the two new test files, and the `CorsConfigTest` mock/URL update). No prior-phase files otherwise touched; `controller/ApiError.java` verified unchanged by diff.

### Files Changed (Phase 4)

| File | Action | What |
|------|--------|------|
| `src/main/java/com/sena/mysqlwithjpa/service/UserService.java` | Created | Hashing, ≥8 pre-hash validation, sanitizer, uniqueness pre-checks, trigger-aware persistence |
| `src/main/java/com/sena/mysqlwithjpa/service/exception/{DuplicateResourceException,NotFoundException}.java` | Created | 409 (carries `field`) / 404 types |
| `src/main/java/com/sena/mysqlwithjpa/dto/{UserRequest,UserResponse}.java` | Created | Records; `OnCreate` validation group extending `Default`; response has no password field at all |
| `src/main/java/com/sena/mysqlwithjpa/controller/MainController.java` | Rewritten | `@RestController /api/users`; POST/GET/PUT/DELETE per frozen route table; `/demo/**` deleted |
| `src/main/java/com/sena/mysqlwithjpa/controller/ExceptionController.java` | Extended | 400 (bean validation, sanitizer/service input), 404 (not-found + unrouted), 409 (duplicate + constraint fallback), 429 + `Retry-After`; existing handlers kept |
| `src/main/java/com/sena/mysqlwithjpa/controller/ApiError.java` | Unchanged (verified) | Reused envelope |
| `src/test/java/com/sena/mysqlwithjpa/service/UserServiceTest.java` | Created | 14 Mockito cases |
| `src/test/java/com/sena/mysqlwithjpa/controller/UserControllerTest.java` | Created | 16 MockMvc cases incl. legacy-404 regression |
| `src/test/java/com/sena/mysqlwithjpa/config/CorsConfigTest.java` | Modified | Probes moved from deleted `/demo/all` to `/api/users/7`; mock switched to `UserService` (forced by the controller's new constructor) |
| `openspec/changes/rest-api-redesign/tasks.md` | Modified | 4.1–4.6 marked [x] |

### Commits (Phase 4, branch `rest-api-redesign/pr-4-core`)

- `2fc8066` feat(users): add UserService with hashing, validation, sanitization and uniqueness checks (511 src lines)
- `4140bd1` feat(users): expose REST CRUD at /api/users with full ApiError status envelope (≈486 src lines incl. controller test + CorsConfigTest probe move)

### Deviations from Design (Phase 4)

1. **DTOs implemented as Java records** (design shows no DTO shape, only the field/validation contract). Records give immutability + Jackson + bean validation for free and remove ~100 lines of getters; the contract (required/optional fields, `contrasena` create-only via the `OnCreate` group, no password in responses) matches the design exactly.
2. **`NoHandlerFoundException`/`NoResourceFoundException` mapped to 404 explicitly** — design Decision 1's route table plus the spec's legacy-404 requirement silently assumed unmapped paths answer 404, but Spring's default in this stack lets them reach the generic handler as 500. The explicit mapping is the only honest implementation of the spec scenario.
3. **Short-password rejection throws `IllegalArgumentException`** (mapped to 400 alongside `SanitizationException`) — task 4.2 authorizes exactly two new exception types (duplicate/not-found); inventing a third would deviate more.
4. **429 lives in BOTH the filter and the advice** (design correction already recorded in Phase 3): the filter renders its own envelope for filter-chain rejections; the advice handler is the tested MVC-side fallback.

### Issues / Risks (Phase 4)

- **PR budget**: Phase 4 slice = **997 changed lines** (`src/` only, across the two commits), well over the 400-line budget as one PR. Honest commit-boundary split at PR-creation time: **PR 4a** = `2fc8066` (service slice ≈511 lines, independently green: 14/14 + regression) → **PR 4b** = `4140bd1` (controller slice ≈486 lines, 59/59). Both slices are test-heavy by strict-TDD construction; no cohesive further split exists without orphaning tests from their code — if the maintainer hard-enforces 400/PR, these two slices need `size:exception`. Not pushed; no PRs created (per orchestrator boundary).
- Mockito JDK self-attach warning remains cosmetic.

## Status

20/34 tasks verified (Phases 1, 3, 4 complete and tested — 59/59 green in this environment); 2.1–2.9 still **UNVERIFIED (Docker unavailable)**. Next: Phase 2 Docker verification run, then Phase 5 (Search & Pagination) on a PR 5 branch stacked on `rest-api-redesign/pr-4-core`. Not ready for archive.
