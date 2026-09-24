# Exploration: rest-api-redesign

## Current State

Minimal Spring Boot 4.1.0 (Java 21, Maven wrapper) CRUD against MySQL via Spring Data JPA. There is **no Thymeleaf dependency in `pom.xml`** — the "view" layer is only a static frontend under `src/main/resources/static/` (vanilla HTML/CSS/JS calling `/demo/add` and `/demo/all`). No service layer, no security, no rate limiting, no MongoDB, no CORS config.

### Package layout (`com.sena.mysqlwithjpa`)

- `MysqlwithjpaApplication.java` — main class.
- `controller/MainController.java` — `@Controller` + `@RequestMapping("/demo")`, two endpoints using `@ResponseBody`:
  - `POST /demo/add` (`@RequestParam name, email`) — manual `Validator` call, throws `ConstraintViolationException`, saves via repository.
  - `GET /demo/all` — returns `Iterable<User>` (unpaginated).
- `controller/ExceptionController.java` — `@RestControllerAdvice` with handlers for 400 (missing param), 400 (constraint violation), 404 (`NoSuchElementException`), 500 (generic; hides DB messages).
- `controller/ApiError.java` — POJO: timestamp, status, error, message, path.
- `entity/User.java` — current minimal entity (see gap below).
- `repository/UserRepository.java` — `interface UserRepository extends CrudRepository<User, Integer>` (empty).

### User entity vs required SQL schema

Current `User` has only: `id` (Integer, `AUTO`), `name` (`@NotBlank`, `@Size(3..50)`, `@Pattern` letters), `email` (`@NotNull`, `@NotBlank`, `@Email`). No DDL is managed by Hibernate that matches the target; `ddl-auto=update` currently OWNS the table, which conflicts with the "no DB modification / triggers exist in MySQL and must not be overridden" constraint.

Required schema columns (NOT present in current entity): `id_usuario` PK, `primer_nombre`, `segundo_nombre` NULL, `primer_apellido`, `segundo_apellido` NULL, `tipo_documento` ENUM(CC,TI), `documento` BIGINT UNIQUE, `celular`, `grupo_formacion` NULL, `correo_electronico` UNIQUE, `contrasena`, `rol` ENUM(ADMIN,USUARIO), `tipo_apoyo` ENUM(regular,alimentacion,transporte) NULL, `fecha_registro`, `ultima_actualizacion`. The whole entity must be rewritten (column names, enums, uniques, audit timestamps likely produced by DB triggers → `ddl-auto` must become `none`/`validate` and insertable/updatable flags may be needed for trigger-managed columns).

### pom.xml — remove / add

- Remove: nothing Thymeleaf-related exists (already absent). `src/main/resources/static/` must be deleted instead.
- Present: `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `mysql-connector-j`, test: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-mysql`.
- Missing for the target: `spring-boot-starter-security` (+ BCrypt), `bucket4j` core/impl, `spring-boot-starter-data-mongodb` (future logging, collections per level). Validation already present.

### Views / static assets to delete

- `src/main/resources/static/index.html`, `static/js/app.js`, `static/css/styles.css`.
- No `templates/` folder, no view resolver config. `MainController` is `@Controller` but only uses `@ResponseBody` (behaviorally REST already).

### CRUD completeness

Only Create (partial, 2 fields) and Read-all exist. Missing: read by id, update, delete, JPA derived queries (AND/OR searches), pagination (max 7/page), auth/security, per-IP rate limiting, CORS localhost:3000.

### Config (`application.properties`)

Hardcoded: `spring.datasource.url=jdbc:mysql://localhost:3306/mysqljpa`, `username=root`, `password=` (EMPTY, hardcoded). `ddl-auto=update` (dangerous given trigger constraint). `show-sql=true`. Logging level/pattern configured. No env vars used anywhere — contradicts the "credentials in env vars" constraint. `compose.yaml` has dev MySQL with inline credentials (dev-only concern).

### Tests

- `MysqlwithjpaApplicationTests` — `@SpringBootTest` contextLoads with `@Import(TestcontainersConfiguration)`.
- `TestcontainersConfiguration` — `MySQLContainer` (mysql:latest) with `@ServiceConnection` (would auto-create schema via JPA — needs care vs the real-schema/DBA-managed database).
- `TestMysqlwithjpaApplication.java` — dev main with container.
- No unit tests for controller/repository exist; strict TDD means the new tests drive the rewrite.

## Affected Areas

- `src/main/java/com/sena/mysqlwithjpa/entity/User.java` — full rewrite to target schema.
- `src/main/java/com/sena/mysqlwithjpa/repository/UserRepository.java` — derived queries, paged methods.
- `src/main/java/com/sena/mysqlwithjpa/controller/MainController.java` — → `@RestController`, full CRUD + search + pagination.
- `src/main/java/com/sena/mysqlwithjpa/controller/ExceptionController.java` — extend (auth 401/403, 403 CORS, 429 bucket4j).
- `src/main/resources/application.properties` — env-var credentials, `ddl-auto` none/validate.
- `pom.xml` — add security, bucket4j, (+later) data-mongodb.
- `src/main/resources/static/**` — delete.
- `src/test/**` — new TDD suite.

## Approaches

1. **Layered `@RestController` → Service → Repository** — introduce a service layer.
   - Pros: matches hexagonal/clean practice; room for security/roles logic; testable units.
   - Cons: more classes for a small CRUD.
   - Effort: Medium.
2. **Controller → Repository directly** (current convention).
   - Pros: minimal, matches existing layering stated in `openspec/config.yaml`.
   - Cons: business rules (hashing, trigger-managed fields) leak into controller.
   - Effort: Low.

### Recommendation
Approach 1 (thin service layer) — password hashing with BCrypt and trigger-managed audit fields justify it; still keep it thin.

## Risks / Decision gaps for the user

- **DB ownership**: must confirm `ddl-auto=none|validate` so Hibernate never alters the trigger-managed schema. Testcontainers currently auto-manages schema — needs a schema.sql matching the real DDL (including triggers?) or a shared test schema decision.
- `GenerationType.AUTO` vs `IDENTITY` for `id_usuario` PK — depends on real DDL (AUTO_INCREMENT?).
- Who sets `fecha_registro` / `ultima_actualizacion` — DB triggers (likely) or app? Affects entity mapping (`insertable=false, updatable=false`).
- `contrasena` storage: hashing at registration endpoint only? Login endpoint scope?
- MongoDB logging is "future" — include dependency now or defer entirely?
- Exact REST routes/base path and response envelope not specified.

### Ready for Proposal
Yes — with the gaps above listed for user confirmation in the proposal phase.
