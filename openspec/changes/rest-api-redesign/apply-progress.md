# Apply Progress: rest-api-redesign — Phase 1 (Foundation, PR 1)

**Change**: rest-api-redesign
**Mode**: Strict TDD
**Delivery**: chained PRs, chain strategy `stacked-to-main`
**Branch**: `rest-api-redesign/pr-1-foundation` (from `Reto4` @ b25a1ef)
**Date**: 2026-09-24

## Completed Tasks (6/34 total)

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

## Status

6/34 tasks complete. Phase 1 finished on `rest-api-redesign/pr-1-foundation`. Ready for next batch (Phase 2) — **blocked on Docker availability** for its Testcontainers tests.
