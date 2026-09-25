# Architecture: User Management REST Microservice

Technical architecture of the Spring Boot 4.1 / Java 21 user-management service produced by the `rest-api-redesign` change. It replaces the legacy server-side `@Controller /demo/**` demo with a layered REST API consumed by the React frontend at `http://localhost:3000`.

> Verification status at a glance: all unit and web-slice tests are green (82/82 in the apply environment). Repository-level integration tests (trigger ownership, derived finders against real MySQL) are authored but pending a Docker-enabled run of `./mvnw test -Dtest=SchemaTriggersTest,UserRepositoryTest,UserSearchRepositoryTest`. See [Verification status](#verification-status).

## Quick path

1. Layers: `MainController` (`@RestController /api/users`) → `UserService` (business rules) → `UserRepository` (`JpaRepository`) → Aiven MySQL `usuario` table.
2. Every error flows through `ExceptionController` into one `ApiError` envelope; success responses are raw payloads (`UserResponse`, `PagedModel<UserResponse>`).
3. The database schema is DBA-owned: Hibernate runs with `ddl-auto=validate` and never alters anything; three MySQL triggers own specific columns (see below).
4. Cross-cutting: BCrypt hashing, input sanitization, single-origin CORS, per-IP bucket4j rate limiting, per-level MongoDB logging.

## Layered design

```
React (http://localhost:3000)
    │  CORS policy enforced (CorsConfig — single origin, no wildcard)
    ▼
RateLimitFilter (bucket4j, per-IP, global across /api/**)
    ▼
SecurityConfig (permit-all chain; CSRF/form-login/basic disabled)
    ▼
MainController (@RestController /api/users, bean validation)
    ▼     ┌ Sanitizer: rejects XSS / event-handler / path-traversal payloads → 400
UserService ┤ Plaintext password rules (≥ 8 chars) BEFORE hashing → 400
    │     ┤ BCryptPasswordEncoder(10) — never persist plaintext
    │     ┤ Uniqueness pre-checks via existsByDocumento / existsByCorreoElectronico → 409
    │     └ Trigger-aware persistence (never writes trigger-owned columns)
    ▼
UserRepository (JpaRepository<User, Integer> + derived finders + Pageable)
    ▼
Aiven MySQL `usuario` (triggers own rol default, ultima_actualizacion, contrasena length)

All layers ──> LogService ──(try/catch, gated by flag)──> MongoDB collections info | warns | error
```

| Layer | Key classes | Responsibility |
|-------|-------------|----------------|
| Controller | `MainController`, `ExceptionController`, `ApiError` | HTTP mapping, bean validation, error envelope |
| Service | `UserService`, `DuplicateResourceException`, `NotFoundException` | Hashing, validation, sanitization, uniqueness, trigger-aware persistence |
| Repository | `UserRepository` | `JpaRepository` + derived AND/OR finders + paging |
| Entity | `User`, `TipoDocumento`, `Rol`, `TipoApoyo` | 1:1 mapping onto the real `usuario` table |
| Config | `SecurityConfig`, `CorsConfig`, `RateLimitConfig`, `RateLimitFilter` | Crypto bean, CORS policy, rate limit |
| Logging | `LogService`, `LogEntry` | Per-level MongoDB persistence with graceful degradation |
| Cross-cutting | `util/Sanitizer`, `exception/RateLimitExceededException` | Input rejection, 429 signal |

## Frozen route table (design Decision 1)

| Method | Path | Description | Success | Errors |
|--------|------|-------------|---------|--------|
| POST | `/api/users` | Create user | 201 + user | 400, 409, 429 |
| GET | `/api/users/{id}` | Read by id | 200 + user | 404, 429 |
| PUT | `/api/users/{id}` | Full update (password optional) | 200 + user | 400, 404, 409, 429 |
| DELETE | `/api/users/{id}` | Delete | 204 (empty) | 404, 429 |
| GET | `/api/users` | Paginated list (`page`, `size`) | 200 + page | 429 |
| GET | `/api/users/search/and?primerNombre=&documento=` | AND search (paginated) | 200 + page | 400, 429 |
| GET | `/api/users/search/or?term=` | OR search (paginated) | 200 + page | 400, 429 |

- The legacy `/demo/**` routes and `/login` are gone and answer **404**; regression coverage lives in `UserControllerTest`.
- Password handling: `contrasena` is write-only inbound (`UserRequest`) and `UserResponse` has no password field at all — responses can never leak a hash.
- Page size is clamped to a hard maximum of 7 records on every listing/search endpoint (never rejected).

## Trigger ownership model

The schema is DBA-owned; Hibernate only validates it (`ddl-auto=validate`). Three MySQL triggers on `usuario` own specific columns, and the entity mapping defers to them:

| Column | Owner | Mapping |
|--------|-------|---------|
| `ultima_actualizacion` | Trigger `actualizarFechaUsuario` (BEFORE UPDATE → `NOW()`) + DB default on insert | `insertable=false, updatable=false` — the application never sends this column |
| `rol` default | Trigger `rolDefecto` (BEFORE INSERT → `COALESCE(NEW.rol,'USUARIO')`) | `@DynamicInsert` omits the column from the INSERT when the client did not supply `rol`; explicit `ADMIN` is respected |
| `contrasena` minimum length | Trigger `validarContrasena` (BEFORE INSERT, `CHAR_LENGTH < 8` → `SIGNAL 45000`) | The service enforces ≥ 8 on the *plaintext* before hashing; the trigger re-checks the stored 60-char BCrypt hash |
| `fecha_registro` | Application sets `LocalDateTime.now()` on insert | `updatable=false` (never modified after insert) |

The test mirror of this schema (full DDL + all three triggers) lives in `src/test/resources/schema.sql` and is exercised by `SchemaTriggersTest` / `UserRepositoryTest` under Testcontainers MySQL 8.4. **Open question**: whether the production DDL also declares `DEFAULT CURRENT_TIMESTAMP` on `fecha_registro` — see [Verification status](#verification-status); if confirmed, the mapping can switch to `insertable=false, updatable=false` with no behavioral change.

## Security components

- **BCrypt hashing** — `SecurityConfig` exposes `BCryptPasswordEncoder(10)`. Spring's encoder verifies `$2a$`/`$2b$`/`$2y$` hashes identically, so existing Express/Node `$2b$10$` hashes in the production database round-trip. Compliance hash handling is threat-modeled in the `api-security` spec.
- **Input sanitization** — `Sanitizer.requireClean` rejects HTML tags (`<script>`, any markup), event-handler attributes (`onerror=`, `onclick =`), and path traversal (`../`, `..\`). Unicode names (`José Lía`) pass byte-identical. Violations map to 400.
- **CORS** — `CorsConfig` pins a single allowed origin, `http://localhost:3000`, with explicit methods/headers and no wildcard. Foreign origins get a 403 preflight with no allow-origin header and never a 500.
- **Rate limiting** — `RateLimitFilter` (bucket4j, `OncePerRequestFilter` registered for `/api/**`): one in-memory bucket per client key (first `X-Forwarded-For` value, else remote address), capacity/refill from `app.ratelimit.*` (default 100 requests/minute). Exhaustion → 429 with an `ApiError`-shaped body and a `Retry-After` header. The filter renders this envelope itself (servlet filters run before the DispatcherServlet); internal limiter failures fail open and are logged — never a 500.

## MongoDB logging

`LogService` writes `LogEntry` documents (timestamp, level, message, component, stackTrace?) via `MongoTemplate` into per-level collections `info`, `warns`, `error`. Collections are created lazily by MongoDB on first insert — no provisioning. Every write is wrapped in try/catch: failures are reported to the SLF4J console and swallowed, so a logging outage can never break the API. All methods no-op when `app.logging.mongo.enabled=false` (env `MONGO_LOGGING_ENABLED`, default `true`). `MongoTemplate` connects lazily on first write, so application startup never blocks on Mongo reachability. Logged content is restricted to component + action + affected id — never passwords, hashes, or connection strings.

## Environment variable contract

All secrets arrive via environment variables; the committed `application.properties` contains only `${...}` placeholders.

| Variable | Purpose | Default |
|----------|---------|---------|
| `MYSQL_HOST` | Aiven MySQL host | — (required) |
| `MYSQL_PORT` | Aiven MySQL port | — (required) |
| `MYSQL_DATABASE` | Database name (`defaultdb` for Aiven) | — (required) |
| `MYSQL_USER` | Datasource username | — (required) |
| `MYSQL_PASSWORD` | Datasource password | — (required) |
| `MYSQL_SSL_MODE` | JDBC `ssl-mode` query param | `REQUIRED` |
| `MONGODB_URI` | MongoDB connection URI | — (required) |
| `MONGO_LOGGING_ENABLED` | Master switch for Mongo logging | `true` |
| `RATE_LIMIT_CAPACITY` | Bucket capacity per client key | `100` |
| `RATE_LIMIT_REFILL_PER_MINUTE` | Tokens refilled per minute | `100` |

`.env` at the repo root holds local values and is gitignored; `.env.example` is committed with placeholders only. `compose.yaml` carries Spring Initializr dev-only credentials (`myuser`/`secret`) that are unrelated to production.

## Verification status

| Area | Status |
|------|--------|
| Unit + web-slice suite (config, crypto, sanitizer, CORS, rate limit, service, controller, search, logging) | Green — 82/82 (`./mvnw test -Dtest=...` focused suites) |
| Repository integration (triggers, round-trip, derived finders vs real MySQL) | Authored; pending Docker-enabled run of `./mvnw test -Dtest=SchemaTriggersTest,UserRepositoryTest,UserSearchRepositoryTest` |
| Production `fecha_registro` DDL (`DEFAULT CURRENT_TIMESTAMP`?) | Open — query `SHOW CREATE TABLE usuario` on the Aiven instance to close |
| End-to-end boot against Aiven | Manual; see `docs/STEP_BY_STEP.md` |
