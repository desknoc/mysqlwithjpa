# Design: REST API Redesign — User Management Microservice

## Technical Approach

Rewrite the minimal `@Controller` demo into a layered REST microservice (`@RestController` → thin `UserService` → `JpaRepository`), following the existing package layout under `com.sena.mysqlwithjpa`. The `User` entity is mapped 1:1 onto the DBA-owned Aiven MySQL `usuario` table with `ddl-auto=validate`; trigger-owned columns are mapped read-only. Security is crypto-only (BCrypt bean, **no** `SecurityFilterChain`), plus a bucket4j `OncePerRequestFilter` and a CORS bean. MongoDB logging is a small app-level component with try/catch graceful degradation. Tests are TDD: Mockito/MockMvc unit slices and Testcontainers MySQL repository/integration slices initialized from `src/test/resources/schema.sql` (real DDL + the 3 triggers). This maps directly onto the proposal's Approach steps 1–8 and satisfies all six capability specs.

## Architecture Decisions

### Decision 1: REST base path → `/api/users`

**Choice**: Base path `/api/users`.
**Alternatives considered**: `/users`; keep `/demo/**` shape.
**Rationale**: `/api/users` namespaces the REST contract away from any future server-side routes and from the deleted `/demo/**` legacy, and is the sibling Express backend convention this microservice coexists with. Frozen route table:

| Method | Path | Description | Success | Errors |
|--------|------|-------------|---------|--------|
| POST | `/api/users` | Create user | 201 + user | 400, 409, 429 |
| GET | `/api/users/{id}` | Read by id | 200 + user | 404, 429 |
| PUT | `/api/users/{id}` | Full update (password optional) | 200 + user | 400, 404, 409, 429 |
| DELETE | `/api/users/{id}` | Delete | 204 | 404, 429 |
| GET | `/api/users` | Paginated list (`page`, `size`, `sort`) | 200 + page | 400?, 429 |
| GET | `/api/users/search/and?primerNombre=&documento=` | AND search (paginated) | 200 + page | 400, 429 |
| GET | `/api/users/search/or?term=` | OR search (paginated) | 200 + page | 400, 429 |

All search endpoints accept `page`/`size` — satisfies "searches and pagination interoperate" and bounds the OR search by construction.

### Decision 2: Response envelope → raw payloads; reuse `ApiError` for errors

**Choice**: Success responses return raw resource payloads (`User` DTO, `Page<User>`). Errors reuse the existing `ApiError` POJO (timestamp/status/error/message/path) unchanged.
**Alternatives considered**: Uniform wrapped envelope `{data, meta, error}` for everything.
**Rationale**: Specs mandate the `ApiError` envelope only for errors; wrapping successes adds a breaking contract the React app must unwrap for zero spec'd benefit. `Page<T>` from Spring already carries the required pagination metadata (content, page, size, totalElements, totalPages). Password is excluded via `@JsonProperty(access = WRITE_ONLY)` on the DTO/entity binding — never serialized.

### Decision 3: bucket4j rate limiting

**Choice**:
- **Bucket**: capacity **100**, refill **100 tokens per 1 minute** (greedy per-minute window), per IP, global across the API surface.
- **Placement**: a `OncePerRequestFilter` (`RateLimitFilter`) registered via `FilterRegistrationBean` for `/api/**`, ordered before the dispatcher servlet work. Client key = first value of `X-Forwarded-For` if present, else `request.getRemoteAddr()`. In-memory `ConcurrentHashMap<String, Bucket>` with bucket4j core.
- **429 response**: thrown as `RateLimitExceededException` → handled by `ExceptionController` → `ApiError` envelope (status 429, message "Too many requests"), **with** `Retry-After: <seconds>` response header (seconds until one token refills).

**Alternatives considered**: Per-endpoint buckets; bucket4j-spring filter starter; fixed-window counters.
**Rationale**: Spec mandates global-per-IP via bucket4j; the starter pulls extra config machinery we don't need — 20 lines of `OncePerRequestFilter` satisfies it. `Retry-After` is free and standard for 429.

### Decision 4: Delete semantics → 204 No Content

**Choice**: `DELETE /api/users/{id}` returns **204 No Content** with an empty body.
**Alternatives considered**: 200 with a confirmation body.
**Rationale**: REST convention for deletes; the subsequent-read-404 scenario in the spec proves deletion without needing a body. No product requirement needs a payload back.

### Decision 5: Oversize page request → silent clamp to 7

**Choice**: Clamp. `size > 7` → effective size 7, HTTP 200.
**Alternatives considered**: Reject with 400.
**Rationale**: The spec's scenario ("Oversized page request clamped to 7") already describes clamping language, and a clamp is friendlier to the React UI (no error path for a benign client mistake). Implemented in one place: controller normalizes `size` before building `Pageable` (`PageRequest.of(page, Math.min(size, 7))`). Same normalization applies to search endpoints.

### Decision 6: MongoDB logging

**Choice**:
- **Component**: `LogService` (`service/log/LogService`) with `logInfo(String component, String message)`, `logWarn(...)`, `logError(String component, String message, Throwable t)`. Writes a `LogEntry` document (timestamp, level, message, component, stackTrace?) via `MongoTemplate` into collections `info`, `warns`, `error`. Lazy collection creation on first insert (spec-mandated and sufficient).
- **Graceful degradation**: every write wrapped in try/catch; failures are logged to the SLF4J console and swallowed — never propagate. Non-blocking behavior is a hard spec requirement.
- **Opt-out property**: `app.logging.mongo.enabled=true|false`, default `true`. All `LogService` methods no-op when `false`. React/`dev` developers without Mongo set it to `false`; startup never blocks on Mongo reachability (no eager connection; `MongoTemplate` connects lazily on first write). Note: the `spring-boot-starter-data-mongodb` dependency is always present; only writes are gated.

**Alternatives considered**: Logback MongoDB appender (third-party, extra config, writes hard to gate); pre-created capped collections (violates lazy-creation spec).
**Rationale**: An app-level component keeps secret-filtering (no password logging) enforceable in one code path and satisfies "no migration tooling".

### Decision 7: User entity mapping detail

**Choice**:

```java
@Entity
@Table(name = "usuario")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @Column(name = "primer_nombre", nullable = false)      private String primerNombre;
    @Column(name = "segundo_nombre")                        private String segundoNombre;
    @Column(name = "primer_apellido", nullable = false)     private String primerApellido;
    @Column(name = "segundo_apellido")                      private String segundoApellido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, columnDefinition = "enum('CC','TI')")
    private TipoDocumento tipoDocumento;   // enum { CC, TI }

    @Column(name = "documento", nullable = false, unique = true) private Long documento;
    @Column(name = "celular")                                   private String celular;
    @Column(name = "grupo_formacion")                            private String grupoFormacion;
    @Column(name = "correo_electronico", nullable = false, unique = true) private String correoElectronico;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contrasena", nullable = false)               private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, columnDefinition = "enum('ADMIN','USUARIO')")
    private Rol rol;                        // enum { ADMIN, USUARIO }

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_apoyo", columnDefinition = "enum('regular','alimentacion','transporte')")
    private TipoApoyo tipoApoyo;            // enum { regular, alimentacion, transporte }

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "ultima_actualizacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime ultimaActualizacion;
}
```

- `ultima_actualizacion`: `insertable=false, updatable=false` — owned by `actualizarFechaUsuario` (and DB default on insert).
- `fecha_registro`: `updatable=false` only. If the real DDL gives it `DEFAULT CURRENT_TIMESTAMP` (typical when `validarContrasena`/`rolDefecto` style triggers exist but no insert-date trigger), we could also make it `insertable=false`; the **safer portable mapping** is `updatable=false` with the service setting `LocalDateTime.now()` on insert IF and ONLY IF the test schema.sql (which mirrors prod DDL) lacks a DB default. **Verification task**: integration test asserts `fecha_registro` is populated on insert without app interference; if the prod DDL defaults it, switch to `insertable=false`. Flagged as an open question pending confirmation of the prod DDL.
- `rol` + `rolDefecto` trigger: entity column is `nullable = false` for validate-time, and the **service sets no value when the client omits `rol`**; to let the DB trigger apply the default, the insert must omit the column when null → achieved by marking the relationship at the service level: when `rol == null`, use `@DynamicInsert` on the entity so Hibernate omits null columns from the INSERT, letting `rolDefecto` fire. `@DynamicInsert` is added for this purpose.
- Enums use `EnumType.STRING` with explicit `columnDefinition` matching the MySQL ENUM so `ddl-auto=validate` accepts the mapping. `tipoApoyo` uses Java-side lowercase enum constants (`regular, alimentacion, transporte`) matching the DB values exactly (no name-mangling needed).

### Decision 8: Spring Security scope → crypto-only

**Choice**: Add `spring-boot-starter-security` to the classpath ONLY as the source of `BCryptPasswordEncoder`, and **expose a `PasswordEncoder` bean** in a `SecurityConfig` class together with a `SecurityFilterChain` that is explicitly `permitAll` with CSRF disabled and default security auto-config filters otherwise inert.

**Rationale**: With Spring Security on the classpath, Boot auto-configures a default filter chain that protects everything (401 on all endpoints, form login, CSRF blocking POSTs) — "crypto-only with zero filter chain" is not achievable just by adding the starter. The minimal honest configuration is therefore: `@EnableWebSecurity` + one `SecurityFilterChain` bean with `.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())`, `.csrf(AbstractHttpConfigurer::disable)`, no form login/basic. This satisfies "bcrypt" (Decision: `BCryptPasswordEncoder` with strength 10 → `$2a$10$` hashes; Spring's decoder verifies `$2a$`, `$2b$`, `$2y$` identically — round-trip with existing Express `$2b$10$` hashes is verified by a dedicated unit test) and "does not break the API" (permit-all, CSRF off for the stateless JSON API, CORS handled by the dedicated CORS configuration).

### Decision 9: Test strategy detail

- **`src/test/resources/schema.sql`**: full `usuario` DDL (columns per Decision 7, PK auto_increment, uniques on `documento`/`correo_electronico`) plus the three triggers, MySQL 8.4 syntax (no `DEFINER`, use `CREATE TRIGGER` with `DELIMITER` handled via `;` + `--` script split is not supported → instead each statement separated by `;` and triggers written without delimiter needs using the `spring.sql.init` execution which supports `$$`… — concrete approach: set `spring.sql.init.schema-locations=classpath:schema.sql` in test properties and write triggers using `CREATE TRIGGER` statements separated with `;` and avoid body-internal `;` by using single-statement trigger bodies, which all three triggers are:
  ```sql
  CREATE TRIGGER rolDefecto BEFORE INSERT ON usuario
    FOR EACH ROW
    SET NEW.rol = COALESCE(NEW.rol, 'USUARIO');

  CREATE TRIGGER actualizarFechaUsuario BEFORE UPDATE ON usuario
    FOR EACH ROW
    SET NEW.ultima_actualizacion = NOW();

  CREATE TRIGGER validarContrasena BEFORE INSERT ON usuario
    FOR EACH ROW
    IF CHAR_LENGTH(NEW.contrasena) < 8 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'contrasena must be at least 8 characters';
    END IF;
  ```
  `validarContrasena`'s `IF…END IF;` body requires multi-statement parsing → use `spring.sql.init.separator=^` (or `//`) in test properties so script splitting handles compound bodies. **Verification task**: "Test schema loads with triggers" asserts the 3 triggers exist in `information_schema.TRIGGERS`.
- **Testcontainers config**: existing `TestcontainersConfiguration` (`@ServiceConnection MySQLContainer mysql:8.4`) is reused; test-scoped `application.properties` (under `src/test/resources`) pins `spring.jpa.hibernate.ddl-auto=validate` and `spring.sql.init.mode=always` so schema.sql loads into the container before the EntityManager validates. Repository slices use `@DataJpaTest` + `@Import(TestcontainersConfiguration)`; trigger-behavior integration tests run there.
- **BCrypt round-trip unit test**: `PasswordEncoderTest` — known pair: hash `$2b$10$` taken from the existing production dump (documented in the test as a constant, no plaintext secrets) verifies `true`; same encoder's freshly generated hash verifies and starts with `$2a$`/`$2b$`; wrong plaintext verifies `false`.
- **TDD slices**: `UserServiceTest` (Mockito: hashing, pre-hashing validation ≥8, uniqueness pre-checks, sanitization rejections), `UserControllerTest` (`@WebMvcTest` + MockMvc: 201/200/204/400/404/409/429 mappings, password never serialized), repository slice tests for derived AND/OR queries and pagination clamp, `RateLimitFilterTest`, CORS MockMvc test, Mongo logging unit test (mocked `MongoTemplate`) + degradation test (unreachable Mongo → no exception).

### Decision 10: Configuration / environment variables

**Choice** — env vars (no defaults for secrets; defaults only for non-secret host/port/db to aid local dev… corrected: **all**, since spec says all datasource values via `${...}`; non-secret defaults allowed where they are not credentials):

```properties
# application.properties (committed)
spring.application.name=mysqlwithjpa
spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?ssl-mode=${MYSQL_SSL_MODE:REQUIRED}
spring.datasource.username=${MYSQL_USER}
spring.datasource.password=${MYSQL_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.data.mongodb.uri=${MONGODB_URI}
app.logging.mongo.enabled=${MONGO_LOGGING_ENABLED:true}
# rate limit
app.ratelimit.capacity=${RATE_LIMIT_CAPACITY:100}
app.ratelimit.refill-per-minute=${RATE_LIMIT_REFILL_PER_MINUTE:100}
```

- Env var names frozen: `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE` (`defaultdb` for Aiven), `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_SSL_MODE` (default `REQUIRED`; committed default never `DISABLED`), `MONGODB_URI`, `MONGO_LOGGING_ENABLED`, `RATE_LIMIT_CAPACITY`, `RATE_LIMIT_REFILL_PER_MINUTE`.
- `.env` at repo root holds local values and is added to `.gitignore`; `.env.example` committed with placeholder values only. `compose.yaml` dev credentials remain distinct and are documented as dev-only in `STEP_BY_STEP.md`.

## Data Flow

    React (localhost:3000)
        │  CORS-checked by CorsConfigurationSource (only http://localhost:3000)
        ▼
    RateLimitFilter (bucket4j per-IP, 429 via ApiError)
        ▼
    UserController (@RestController /api/users, bean validation)
        ▼   ┌─ sanitization & plaintext password rules (≥8) — 400 on failure
    UserService ─┤ BCryptPasswordEncoder.encode (never persist plaintext)
        │      └─ uniqueness pre-checks (documento, correo_electronico) — 409
        ▼
    UserRepository (JpaRepository + derived finders + Pageable)
        ▼
    Aiven MySQL `usuario`  (DB triggers own rol default, ultima_actualizacion, contrasena length)

    All layers ──> LogService ──(try/catch, optional via flag)──> MongoDB (info|warns|error)

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `entity/User.java` | Modify | Full rewrite per Decision 7 (enums, trigger-owned columns, `@DynamicInsert`) |
| `entity/TipoDocumento.java`, `entity/Rol.java`, `entity/TipoApoyo.java` | Create | Java enums (CC/TI; ADMIN/USUARIO; regular/alimentacion/transporte) |
| `repository/UserRepository.java` | Modify | `CrudRepository` → `JpaRepository<User, Integer>`; `findByPrimerNombreAndDocumento`, `findByPrimerNombreOrPrimerApellidoOrDocumento`, `existsByDocumento`, `existsByCorreoElectronico` |
| `controller/MainController.java` | Modify | `@RestController /api/users`; CRUD + search + pagination (delete `/demo/**`); request DTOs |
| `controller/ExceptionController.java` | Modify | Add 409 (DataIntegrityViolation + Duplicate field exception) and 429 handlers |
| `controller/ApiError.java` | Unchanged | Reused as the error envelope |
| `dto/UserRequest.java`, `dto/UserResponse.java` | Create | Request binding (validation annotations) / response DTO without password |
| `service/UserService.java` | Create | Hashing, pre-hash validation, sanitization, uniqueness checks, trigger-aware persistence |
| `service/exception/DuplicateResourceException.java`, `NotFoundException.java` | Create | Mapped to 409/404 |
| `service/log/LogService.java`, `service/log/LogEntry.java` | Create | Per-level Mongo writes, graceful degradation |
| `config/SecurityConfig.java` | Create | `PasswordEncoder` bean + permit-all `SecurityFilterChain` (CSRF off) |
| `config/CorsConfig.java` | Create | CorsConfigurationSource: only `http://localhost:3000`, standard methods/headers |
| `config/RateLimitConfig.java` (+ `RateLimitFilter`) | Create | bucket4j per-IP filter for `/api/**`, X-Forwarded-For key, `Retry-After` on 429 |
| `util/Sanitizer.java` | Create | Reject XSS/traversal patterns on string inputs (400 on violation) |
| `application.properties` | Modify | Env-var placeholders per Decision 10; `ddl-auto=validate`; remove hardcoded credentials |
| `.env.example` | Create | Placeholder values only |
| `.gitignore` | Modify | Add `.env` |
| `pom.xml` | Modify | Add `spring-boot-starter-security`, `bucket4j-core`, `spring-boot-starter-data-mongodb` |
| `src/main/resources/static/**` | Delete | index.html, js/app.js, css/styles.css |
| `src/test/resources/schema.sql` | Create | `usuario` DDL + 3 triggers (single/compound bodies, `spring.sql.init.separator`) |
| `src/test/resources/application.properties` | Create/Modify | Testcontainers wiring, `ddl-auto=validate`, `spring.sql.init.*` |
| `src/test/**` (service/controller/repository/filter/cors/security tests) | Create | TDD suite per Decision 9 |
| `docs/ARCHITECTURE.md`, `docs/STEP_BY_STEP.md`, `docs/queries/SEARCH_AND_PAGINATION.md` | Create | Deliverables; route table documented here |
| `exception/RateLimitExceededException.java` | Create | Thrown by rate-limit filter → 429 handler |

## Interfaces / Contracts

```java
public interface UserRepository extends JpaRepository<User, Integer> {
    Page<User> findByPrimerNombreAndDocumento(String primerNombre, Long documento, Pageable pageable);
    Page<User> findByPrimerNombreOrPrimerApellidoOrDocumento(String primerNombre, String primerApellido, Long documento, Pageable pageable);
    boolean existsByDocumento(Long documento);
    boolean existsByCorreoElectronico(String correoElectronico);
}
```

OR search: the controller parses the single `term`; if it parses as a `long` it is passed as `documento`, otherwise `null` (derived query with `null` parameter on an OR branch simply never matches `documento` — parameterization preserved, satisfies the numeric-type-safety scenario).

`UserRequest` (bean validation): required `primerNombre, primerApellido, tipoDocumento, documento, celular, correoElectronico, contrasena`; optional `segundoNombre, segundoApellido, grupoFormacion, rol, tipoApoyo`. `UserResponse` never contains `contrasena`.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Hashing & `$2b$10$` round-trip; plaintext validation ≥8; sanitization rejections; uniqueness pre-checks; pagination clamp logic; rate-limit bucket math; LogService degradation | JUnit 5 + Mockito (`UserServiceTest`, `PasswordEncoderTest`, `SanitizerTest`, `RateLimitFilterTest`) |
| Web slice | 201/200/204/400/404/409/429 mappings; password absent from responses; CORS allowed/denied origins; legacy `/demo/**` → 404; clamp behavior | `@WebMvcTest` + MockMvc |
| Repository slice | Derived AND/OR finders; paged `findAll`; entity round-trip types (Long documento) | `@DataJpaTest` + Testcontainers MySQL 8.4 + schema.sql |
| Integration (container) | Trigger ownership (`ultima_actualizacion`, `rol` default), `validarContrasena` passes with real `$2b$10$` hash; `ddl-auto=validate` boots clean; triggers present in information_schema | `@DataJpaTest`/`@SpringBootTest` + Testcontainers |

## Threat Matrix

| Row | Verdict | Safe / failure behavior & RED test |
|-----|---------|-----------------------------------|
| Shell command execution | N/A | No shell/subprocess boundary in this change. |
| Subprocess spawning | N/A | None. |
| VCS/PR automation | N/A | None. |
| Executable-file classification | N/A | None. |
| Routing (HTTP route table) | **Applicable** | Frozen table (Decision 1); legacy `/demo/**` and `/login` MUST 404. RED tests: MockMvc asserts 404 on `/demo/add`, `/demo/all`, `/login`; full route table covered by `@WebMvcTest`. |
| Process integration (filters) | **Applicable** | Rate-limit filter MUST not 500 on its own failure; CORS filter MUST NOT emit wildcard or foreign allow-origin. RED tests: 429 after threshold with `Retry-After`; foreign-origin preflight lacks `Access-Control-Allow-Origin`; CORS rejection must not produce 500. |

## Migration / Rollout

No database migration: `ddl-auto=validate`, no DDL/DML beyond User CRUD; triggers remain DB-owned. Frontend cutover: React app must target `/api/users/**` (route table documented in `docs/ARCHITECTURE.md`); legacy `/demo/**` returns 404. Rollback: revert the change branch (git) — schema untouched, dependencies additive.

## Open Questions

- [ ] **Prod DDL for `fecha_registro`**: does the Aiven table define `DEFAULT CURRENT_TIMESTAMP` on `fecha_registro`? If yes, switch to `insertable=false, updatable=false` (fully DB-owned). Current design: `updatable=false` + `@DynamicInsert` so the column is omitted when null — safe under both DDLs. Confirm before archive; the integration test ("fecha_registro populated without app interference") resolves it.
- [ ] Exact default rate-limit threshold (100 req/min/IP) is an engineering default; product may tune via `RATE_LIMIT_*` env vars without code change.
