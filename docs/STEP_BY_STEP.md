# Step by Step: Build, Test, and Run

Manual guide for building and running the user management microservice locally and against the Aiven MySQL production instance.

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Java 21 (Temurin or equivalent) | `JAVA_HOME` must point to a JDK 21 install, e.g. `C:\Users\<you>\.jdks\temurin-21` |
| Maven wrapper only | Use `./mvnw` (or `.\mvnw.cmd` on Windows) — no standalone Maven needed |
| Docker daemon | **Only required to run the Testcontainers-backed repository tests** (schema + triggers). The app itself does not need Docker to boot against Aiven |
| Aiven MySQL + MongoDB credentials | For real runs; see the env-var table below |

## Quick path

1. Copy `.env.example` to `.env` and fill in your values.
2. Export the variables into your shell (the app reads the process environment; `.env` is a convenience source, not auto-loaded by Spring).
3. `./mvnw spring-boot:run` (Windows PowerShell: `./mvnw.cmd spring-boot:run`).
4. Verify: `curl http://localhost:8080/api/users` → `200` with a paged body (possibly empty).

## Environment variables

The committed `application.properties` contains only `${...}` placeholders. Set these before running:

| Variable | Purpose | Default |
|----------|---------|---------|
| `MYSQL_HOST` | Aiven MySQL host | — (required) |
| `MYSQL_PORT` | Aiven MySQL port | — (required) |
| `MYSQL_DATABASE` | Database name (`defaultdb` on Aiven) | — (required) |
| `MYSQL_USER` | Datasource username | — (required) |
| `MYSQL_PASSWORD` | Datasource password | — (required) |
| `MYSQL_SSL_MODE` | JDBC `ssl-mode` parameter | `REQUIRED` — do not set `DISABLED` |
| `MONGODB_URI` | MongoDB connection URI | — (required) |
| `MONGO_LOGGING_ENABLED` | Mongo logging master switch | `true` (set `false` in dev without Mongo) |
| `RATE_LIMIT_CAPACITY` | Bucket capacity per IP | `100` |
| `RATE_LIMIT_REFILL_PER_MINUTE` | Tokens refilled per minute | `100` |

`.env` is gitignored — **never commit it**. `.env.example` shows the expected keys with placeholder values only.

PowerShell example:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
}
```

## `compose.yaml` credentials are dev-only

The committed `compose.yaml` spins up a throwaway local MySQL with Spring Initializr placeholder credentials (`myuser`/`secret`, database `mydatabase`). These values are **development-only and intentionally unrelated to the production Aiven credentials**; they exist so `docker compose up` gives you a scratch database without touching production. Never paste Aiven credentials into `compose.yaml` — production config arrives exclusively through the environment variables above.

## Run the test suite

```powershell
# Full suite (Docker REQUIRED — repository tests use Testcontainers MySQL 8.4)
./mvnw.cmd test

# Docker-free slice (unit + web-slice tests only — all green without Docker)
./mvnw.cmd -o test "-Dtest=PasswordEncoderTest,SanitizerTest,CorsConfigTest,RateLimitFilterTest,AppConfigurationPropertiesTest,UserServiceTest,UserControllerTest,UserSearchControllerTest,LogServiceTest"

# Repository integration slice (Docker REQUIRED; verifies the usuario DDL, the 3 triggers, and the derived finders)
./mvnw.cmd test "-Dtest=SchemaTriggersTest,UserRepositoryTest,UserSearchRepositoryTest"
```

If Docker is not running, the third command fails on the Testcontainers connection — not on assertions. Start the daemon and retry.

## Boot against Aiven

1. Set `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD` from the Aiven console connection details, leave `MYSQL_SSL_MODE=REQUIRED`.
2. Set `MONGODB_URI` (or `MONGO_LOGGING_ENABLED=false` while developing without Mongo — writes become no-ops and startup never blocks on Mongo either way: the driver connects lazily on first write).
3. `./mvnw.cmd spring-boot:run`
4. Smoke checks:
   - `curl http://localhost:8080/api/users` → 200 with `content` + `page` metadata
   - `curl http://localhost:8080/demo/all` → 404 (legacy routes are gone)
   - `curl -i http://localhost:8080/api/users -H "Origin: http://evil.example.com"` → no `Access-Control-Allow-Origin` header

The app runs with `spring.jpa.hibernate.ddl-auto=validate`: it verifies the `usuario` table mapping at startup and **never alters the schema**. If boot fails with a validation error, the entity mapping and the real DDL have drifted — fix the mapping, never the database (triggers and structure are DBA-owned).
