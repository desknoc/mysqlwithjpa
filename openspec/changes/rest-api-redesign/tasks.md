# Tasks: REST API Redesign — User Management Microservice

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1800–2600 (≈20 new/modified main files, ≈10 new test files incl. Testcontainers schema.sql, 3 docs, static/ deletion) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Foundation: config + deps + static deletion) → PR 2 (Domain: entity/enums + repository + test schema) → PR 3 (Security plumbing: BCrypt, sanitizer, CORS, rate limit) → PR 4 (Core: service + CRUD controller + errors) → PR 5 (Search & pagination endpoints) → PR 6 (Mongo logging) → PR 7 (Docs) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

> ask-on-risk: before `sdd-apply` starts, ask the user to choose the chain strategy —
> **stacked-to-main** (each PR merges to main in order), **feature-branch-chain** (PR #1 → tracker branch, PR #N → PR #N-1 branch, only tracker merges to main), or **size:exception** (single PR with maintainer approval). Until chosen, Chain strategy stays `pending`.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Foundation: pom deps, env-var `application.properties`, `.env`/`.env.example`/`.gitignore`, delete `static/**` | PR 1 | `./mvnw compile` | `git status` shows no credentials; `./mvnw spring-boot:run` with env vars set (needs Aiven/MySQL reachable) | Revert `pom.xml`, `application.properties`, `.gitignore`; restore `static/**` from git |
| 2 | Domain: User entity rewrite, enums, `UserRepository` → `JpaRepository`, test `schema.sql` with 3 triggers + Testcontainers wiring | PR 2 | `./mvnw test -Dtest=UserRepositoryTest,SchemaTriggersTest` | `@DataJpaTest` + Testcontainers MySQL 8.4 (Docker required) | Revert `entity/`, `repository/`, `src/test/resources/schema.sql` |
| 3 | Security plumbing: `SecurityConfig`, `PasswordEncoder`, `Sanitizer`, `CorsConfig`, `RateLimitFilter` + 429 exception | PR 3 | `./mvnw test -Dtest=PasswordEncoderTest,SanitizerTest,RateLimitFilterTest,CorsConfigTest` | MockMvc CORS preflight; curl loop exceeding limit → 429 + `Retry-After` | Revert `config/`, `util/Sanitizer.java`, `exception/RateLimitExceededException.java` |
| 4 | Core CRUD: `UserService`, DTOs, exceptions, `MainController` → `@RestController /api/users`, `ExceptionController` 400/404/409 | PR 4 | `./mvnw test -Dtest=UserServiceTest,UserControllerTest` | Manual `POST/GET/PUT/DELETE /api/users` against Testcontainers-backed run | Revert `service/`, `dto/`, controller changes |
| 5 | Search & pagination endpoints: AND/OR search, clamp to 7, page metadata | PR 5 | `./mvnw test -Dtest=UserSearchRepositoryTest,UserSearchControllerTest` | `GET /api/users?page=0&size=50` → 7 records; `GET /api/users/search/or?term=…` | Revert search methods/endpoints only |
| 6 | MongoDB logging: `LogService`, `LogEntry`, opt-out property, graceful degradation | PR 6 | `./mvnw test -Dtest=LogServiceTest` | Run with `MONGO_LOGGING_ENABLED=false` and with unreachable Mongo URI → API unaffected | Revert `service/log/` + property keys |
| 7 | Docs: `docs/ARCHITECTURE.md`, `docs/STEP_BY_STEP.md`, `docs/queries/SEARCH_AND_PAGINATION.md` | PR 7 | N/A (docs only; no code under test) | N/A (docs only) | Revert `docs/` |

---

## Phase 1: Foundation — Dependencies, Configuration, Legacy Removal

- [x] 1.1 **RED (config test)**: Add `AppConfigurationPropertiesTest` in `src/test/java/com/sena/mysqlwithjpa/config/` asserting `application.properties` (read via classpath) contains only `${...}` placeholders for `spring.datasource.url/username/password`, has `spring.jpa.hibernate.ddl-auto=validate`, `spring.datasource.url` contains `ssl-mode=${MYSQL_SSL_MODE:REQUIRED}`, and contains no literal credential values — test must FAIL against current hardcoded properties
- [x] 1.2 **GREEN**: Add dependencies to `pom.xml`: `spring-boot-starter-security` (crypto only), `bucket4j-core`, `spring-boot-starter-data-mongodb`; verify `./mvnw compile` succeeds
- [x] 1.3 **GREEN**: Rewrite `src/main/resources/application.properties` per design Decision 10 (env-var placeholders: `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_SSL_MODE:REQUIRED`, `MONGODB_URI`, `MONGO_LOGGING_ENABLED:true`, `RATE_LIMIT_CAPACITY:100`, `RATE_LIMIT_REFILL_PER_MINUTE:100`; `ddl-auto=validate`; `open-in-view=false`) — test from 1.1 now PASSES; remove all hardcoded credentials
- [x] 1.4 **GREEN**: Create `.env.example` at repo root with placeholder values only; add `.env` to `.gitignore`; verify `git status` no longer lists `.env`
- [x] 1.5 **REFACTOR/cleanup**: Delete `src/main/resources/static/index.html`, `src/main/resources/static/js/app.js`, `src/main/resources/static/css/styles.css` (and the `static/` tree)
- [x] 1.6 Verify no production credentials remain in `compose.yaml`; confirm existing local credentials are distinct from production values (documented later in Phase 7) — fix `compose.yaml` only if production values are present

## Phase 2: Domain Mapping — Entity, Enums, Repository, Test Schema

- [ ] 2.1 **RED (test schema)**: Create `src/test/resources/schema.sql` with the full `usuario` DDL (columns per design Decision 7: `id_usuario` PK auto_increment, uniques on `documento`/`correo_electronico`, MySQL ENUMs) plus triggers `rolDefecto` (BEFORE INSERT, `SET NEW.rol = COALESCE(NEW.rol,'USUARIO')`), `actualizarFechaUsuario` (BEFORE UPDATE, `SET NEW.ultima_actualizacion = NOW()`), `validarContrasena` (BEFORE INSERT, `IF CHAR_LENGTH(NEW.contrasena) < 8 THEN SIGNAL SQLSTATE '45000'`)
- [ ] 2.2 **RED (test config)**: Create `src/test/resources/application.properties` pinning `spring.jpa.hibernate.ddl-auto=validate`, `spring.sql.init.mode=always`, `spring.sql.init.schema-locations=classpath:schema.sql`, and `spring.sql.init.separator=^` (write `validarContrasena` body using `^` statement terminators so the compound `IF…END IF` parses); reuse `TestcontainersConfiguration` (`@ServiceConnection` MySQL 8.4)
- [ ] 2.3 **RED**: Add `SchemaTriggersTest` (`@DataJpaTest` + `@Import(TestcontainersConfiguration)`) asserting the `usuario` table exists and `information_schema.TRIGGERS` contains `rolDefecto`, `actualizarFechaUsuario`, `validarContrasena`; and asserting no Hibernate CREATE/ALTER runs with `ddl-auto=validate` — FAILS until entity mapping matches DDL
- [ ] 2.4 **GREEN**: Create enums `src/main/java/com/sena/mysqlwithjpa/entity/TipoDocumento.java` (`CC, TI`), `entity/Rol.java` (`ADMIN, USUARIO`), `entity/TipoApoyo.java` (lowercase `regular, alimentacion, transporte`)
- [ ] 2.5 **GREEN**: Rewrite `src/main/java/com/sena/mysqlwithjpa/entity/User.java` per design Decision 7 exactly: `@Table(name="usuario")`, `@DynamicInsert`, `id_usuario` IDENTITY `Integer`, `documento` `Long`, `EnumType.STRING` enums with matching `columnDefinition`s, `@JsonProperty(WRITE_ONLY)` on `contrasena`, `ultimaActualizacion` with `insertable=false, updatable=false`, `fechaRegistro` with `updatable=false`
- [ ] 2.6 **RED**: Add `UserRepositoryTest` (`@DataJpaTest` + Testcontainers): entity round-trip — persist user, read back; assert `documento` read as `Long`, all fields round-trip — FAILS until repository upgrade
- [ ] 2.7 **GREEN**: Change `src/main/java/com/sena/mysqlwithjpa/repository/UserRepository.java` from `CrudRepository` to `JpaRepository<User, Integer>`; add `existsByDocumento(Long)` and `existsByCorreoElectronico(String)` — test 2.6 PASSES
- [ ] 2.8 **RED (trigger ownership)**: Extend `UserRepositoryTest`: (a) update a non-trigger column, assert DB-set `ultima_actualizacion` changed without the app sending it (`INSERT`/`UPDATE` on entity never carries the column); (b) insert with null `rol` → fresh read returns `USUARIO` (trigger `rolDefecto` via `@DynamicInsert` omitting the column); (c) insert with explicit `rol=ADMIN` → persisted as `ADMIN`; (d) insert with a real 60-char `$2b$10$…` BCrypt hash in `contrasena` succeeds under `validarContrasena` and reads back byte-identical
- [ ] 2.9 **GREEN**: Adjust `User` mapping only if needed to make 2.8 pass (this is where the `fecha_registro` open question is resolved: if the trigger test shows DB-owned `fecha_registro`, switch to `insertable=false, updatable=false`; otherwise keep `updatable=false` and have the service set it)

## Phase 3: Security Plumbing — Crypto, Sanitizer, CORS, Rate Limit

- [ ] 3.1 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/security/PasswordEncoderTest.java`: (a) a known `$2b$10$…` hash from the production dump (documented constant, no plaintext secrets) verifies `true` for its known plaintext and `false` for a wrong plaintext; (b) fresh hash from the encoder verifies its own plaintext and starts with `$2a$` or `$2b$` — FAILS until encoder bean exists
- [ ] 3.2 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/config/SecurityConfig.java`: `@EnableWebSecurity`, `PasswordEncoder` bean = `new BCryptPasswordEncoder(10)`, and an explicit `SecurityFilterChain` with `authorizeHttpRequests(anyRequest().permitAll())`, CSRF disabled, no form login/basic auth — test 3.1 PASSES
- [ ] 3.3 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/util/SanitizerTest.java`: rejects `<script>alert(1)</script>`, event-handler attributes (`onerror=…`), and shell path-traversal (`../`) payloads; accepts `José Lía` preserving Unicode characters exactly — FAILS until sanitizer exists
- [ ] 3.4 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/util/Sanitizer.java` implementing the reject-rules from 3.3 (violation → throws exception mapped to 400) — test PASSES; REFACTOR patterns list if needed
- [ ] 3.5 **RED (CORS, threat matrix)**: Create `src/test/java/com/sena/mysqlwithjpa/config/CorsConfigTest.java` (`@WebMvcTest` + MockMvc): preflight/actual request with `Origin: http://localhost:3000` receives `Access-Control-Allow-Origin: http://localhost:3000`; `Origin: http://evil.example.com` receives NO allow-origin header and NO wildcard, and the response is NOT a 500; request without `Origin` proceeds normally — FAILS until CORS config exists
- [ ] 3.6 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/config/CorsConfig.java` — `CorsConfigurationSource` bean: single allowed origin `http://localhost:3000`, standard methods (GET/POST/PUT/DELETE/OPTIONS) and headers, no wildcard — test 3.5 PASSES
- [ ] 3.7 **RED (rate limit, threat matrix)**: Create `src/test/java/com/sena/mysqlwithjpa/config/RateLimitFilterTest.java`: (a) with capacity N, request N+1 from one IP → 429 `ApiError` envelope with `Retry-After` header; (b) requests distributed across different endpoints share one bucket (global, not per-endpoint); (c) two distinct `X-Forwarded-For` values behind the same remote address get independent buckets; (d) filter internal failure MUST NOT produce a 500 for the underlying request — FAILS until filter exists
- [ ] 3.8 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/exception/RateLimitExceededException.java` and `src/main/java/com/sena/mysqlwithjpa/config/RateLimitFilter.java` (bucket4j `OncePerRequestFilter`: `ConcurrentHashMap<String, Bucket>`, capacity/refill from `app.ratelimit.*` properties, key = first `X-Forwarded-For` value else `getRemoteAddr()`, `tryConsume` → throw `RateLimitExceededException` carrying seconds-until-refill) registered via `FilterRegistrationBean` for `/api/**`; create `src/main/java/com/sena/mysqlwithjpa/config/RateLimitConfig.java` binding the properties — test 3.7 PASSES

## Phase 4: Core CRUD — Service, DTOs, Controller, Exception Mapping

- [ ] 4.1 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/service/UserServiceTest.java` (JUnit 5 + Mockito, mock `UserRepository` + `PasswordEncoder`): (a) create — plaintext `contrasena` of 8+ chars is hashed before `save`, persisted value ≠ plaintext; (b) create with `contrasena` = "abc123" → 400-mapped validation exception, `PasswordEncoder.encode` and `repository.save` NEVER called; (c) create with duplicate `documento`/`correoElectronico` (via `existsBy*`) → duplicate-field exception naming the field; (d) create with `<script>` in `primerNombre` → sanitizer rejection, nothing persisted; (e) update without `contrasena` leaves the stored hash byte-identical; (f) update of non-existent id → not-found exception; (g) trigger-managed fields supplied by client (`fechaRegistro`/`ultimaActualizacion`) are ignored/rejected — FAILS until service exists
- [ ] 4.2 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/service/exception/DuplicateResourceException.java` and `service/exception/NotFoundException.java`; create `src/main/java/com/sena/mysqlwithjpa/service/UserService.java` (hashing, pre-hash plaintext validation ≥ 8, sanitizer, uniqueness pre-checks via `existsBy*`, trigger-aware persistence honoring `@DynamicInsert` null-rol behavior) — test 4.1 PASSES; REFACTOR for thinness
- [ ] 4.3 **GREEN**: Create DTOs `src/main/java/com/sena/mysqlwithjpa/dto/UserRequest.java` (bean validation: required `primerNombre, primerApellido, tipoDocumento, documento, celular, correoElectronico, contrasena`; optional `segundoNombre, segundoApellido, grupoFormacion, rol, tipoApoyo`) and `dto/UserResponse.java` (never contains `contrasena`)
- [ ] 4.4 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/controller/UserControllerTest.java` (`@WebMvcTest` + MockMvc, `UserService` mocked): POST valid → 201 + body with id, body contains no `contrasena`/hash; POST missing `primerNombre` → 400 envelope, nothing persisted; POST duplicate → 409 envelope naming the conflicting field; GET existing id → 200 without password; GET missing id → 404 envelope; PUT valid → 200; PUT missing → 404; DELETE existing → 204 empty; DELETE missing → 404; unhandled service exception → 500 envelope with NO stack trace/SQL/vendor text — FAILS until controller exists
- [ ] 4.5 **GREEN**: Rewrite `src/main/java/com/sena/mysqlwithjpa/controller/MainController.java` as `@RestController` at `/api/users` implementing POST 201 / GET by id 200 / PUT 200 / DELETE 204 per the frozen route table (design Decision 1); delete the `/demo/**` endpoints and `@Controller` annotations; extend `src/main/java/com/sena/mysqlwithjpa/controller/ExceptionController.java` with 404 (`NotFoundException`), 409 (`DuplicateResourceException` + `DataIntegrityViolationException` fallback), 429 (`RateLimitExceededException` setting `Retry-After`), and sanitized 500 handler reusing `ApiError` — test 4.4 PASSES; `controller/ApiError.java` stays unchanged
- [ ] 4.6 **RED (threat matrix — routing)**: Add MockMvc assertions (in `UserControllerTest`): POST `/demo/add` → 404, GET `/demo/all` → 404, POST `/login` → 404 — PASS once 4.5 removes legacy routes; keep as regression coverage

## Phase 5: Search & Pagination

- [ ] 5.1 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/repository/UserSearchRepositoryTest.java` (`@DataJpaTest` + Testcontainers): AND search `findByPrimerNombreAndDocumento` returns the user only when BOTH match, empty page otherwise; OR search `findByPrimerNombreOrPrimerApellidoOrDocumento` matches on `primerApellido` alone; numeric term parameterized as `Long` with no cast failure; metacharacter term `"Ana' OR '1'='1"` treated literally (no error, no match) — FAILS until derived methods exist
- [ ] 5.2 **GREEN**: Add to `UserRepository.java`: `Page<User> findByPrimerNombreAndDocumento(String, Long, Pageable)` and `Page<User> findByPrimerNombreOrPrimerApellidoOrDocumento(String, String, Long, Pageable)` — test 5.1 PASSES
- [ ] 5.3 **RED**: Extend `UserControllerTest` / add `UserSearchControllerTest` (MockMvc): `GET /api/users?page=0` with >7 users → exactly 7 records + correct `totalElements`/`totalPages`; `size=50` clamped to 7 with HTTP 200; page 1 of 10 users → 3 records, totals 10/2; page 99 → 200 with empty content and `totalElements=10`; OR search with 12 matches paginated → max 7 records, `totalElements=12`; empty AND result → 200 (not 404); `term=Ana' OR '1'='1` → 200 empty, no SQL error — FAILS until endpoints exist
- [ ] 5.4 **GREEN**: Add to `MainController.java`: `GET /api/users` (paged, `PageRequest.of(page, Math.min(size, 7))`), `GET /api/users/search/and?primerNombre=&documento=`, `GET /api/users/search/or?term=` (controller parses `term` to `Long` when numeric, else `null` for the `documento` branch per design contract; same clamp to 7) — test 5.3 PASSES

## Phase 6: MongoDB Logging

- [ ] 6.1 **RED**: Create `src/test/java/com/sena/mysqlwithjpa/service/log/LogServiceTest.java` (mocked `MongoTemplate`): `logInfo` writes one document (timestamp, level, message, component) to collection `info` and NOT to `warns`/`error`; `logWarn` → `warns` only; `logError(msg, Throwable)` → `error` including stack trace; logged content NEVER contains a plaintext password, BCrypt hash, or connection string — FAILS until LogService exists
- [ ] 6.2 **GREEN**: Create `src/main/java/com/sena/mysqlwithjpa/service/log/LogEntry.java` (timestamp, level, message, component, stackTrace?) and `service/log/LogService.java` writing via `MongoTemplate` into `info`/`warns`/`error` collections (lazy creation — no explicit collection provisioning); gate all methods on `app.logging.mongo.enabled` (default `true`) — test 6.1 PASSES
- [ ] 6.3 **RED**: Add degradation test to `LogServiceTest`: `MongoTemplate.save` throwing / Mongo unreachable → no exception propagates to caller, failure reported to SLF4J console; with `MONGO_LOGGING_ENABLED=false` methods are no-ops
- [ ] 6.4 **GREEN**: Wrap every `LogService` write in try/catch (swallow + SLF4J) per design Decision 6; ensure application startup never blocks on Mongo reachability (no eager connection) — test 6.3 PASSES
- [ ] 6.5 **GREEN**: Wire `LogService.logInfo/logWarn/logError` calls into `UserService` create/update/delete flows (component name `UserService`), logging only non-secret fields (ids, documento is fine for audit? — NO: log only component + action + affected id; never contrasena/hash/credentials) — covered by the no-secrets assertion in 6.1

## Phase 7: Documentation & Final Verification

- [ ] 7.1 Create `docs/ARCHITECTURE.md`: layered design (Controller → Service → Repository), frozen route table from design Decision 1, trigger ownership model, security components (BCrypt, CORS, rate limit), Mongo logging, env-var contract
- [ ] 7.2 Create `docs/STEP_BY_STEP.md`: manual build/run guide — required env vars, `.env` usage, `compose.yaml` dev-only credentials explicitly documented as distinct from production, `./mvnw test` (Docker required for Testcontainers), boot against Aiven
- [ ] 7.3 Create `docs/queries/SEARCH_AND_PAGINATION.md`: derived AND/OR queries, the 7-record hard max, pagination metadata, parameterized-by-construction guarantee with the injection example
- [ ] 7.4 Final gate: `./mvnw test` green end-to-end; verify success criteria from the proposal (route table live, `/demo/**` + `/login` 404, triggers own their columns in integration tests, 429 + CORS behavior, Mongo collections per level, no credentials in repo, `git status` clean of `.env`)
- [ ] 7.5 Resolve design open question: confirm prod DDL `DEFAULT CURRENT_TIMESTAMP` on `fecha_registro` against the real Aiven instance and record the outcome in `docs/ARCHITECTURE.md` (no code change expected if Phase 2.9 already settled it)

---

## Risk Notes

- **No Jacoco configured** — coverage cannot be measured; strict TDD discipline (RED before every GREEN task above) is the only quality gate. Not added to pom.xml (would be out of scope).
- **Docker required** for Phases 2, 5 (Testcontainers) — tests fail without a running daemon; unit slices (Phases 3, 4 service tests, 6) run without Docker.
- **Estimated lines (~1800–2600) well exceed the 400-line review budget** — chained PRs recommended; chain strategy decision required before apply.
