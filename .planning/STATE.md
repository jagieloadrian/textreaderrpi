# Project State & Memory

**Last Updated:** 2026-05-28  
**Status:** ✅ v1.0 MILESTONE ARCHIVED — ready for `/gsd-new-milestone`

## Current State

### Project Context
- **Name:** TextReaderRpi
- **Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface
- **Users:** Home lab enthusiasts, DIY electronics hobbyists
- **Timeline:** No hard deadline; iterative development

### Codebase Status
- **Language:** Kotlin 2.3.21, JDK 25 toolchain
- **Framework:** Ktor 3.5.0 (DI plugin + RequestValidation + StatusPages)
- **ORM:** Exposed 1.3.0 (`org.jetbrains.exposed.v1.*` packages)
- **Hardware:** Pi4J 4.0.0 + MAX7219 via SPI, LCD/OLED via I2C
- **Database:** H2 (embedded default) or PostgreSQL (via env vars)
- **Current package root:** `src/main/kotlin/com/anjo/...`

### Existing Features (all complete)
- ✅ Typed YAML config with env var overrides (`${VAR:default}` for all 25 settings)
- ✅ Request validation via Ktor `RequestValidation`
- ✅ Centralized error mapping via Ktor `StatusPages`
- ✅ Text endpoint `POST /api/v1/text` with effects (SCROLL/BLINK/REVERSE/FADE)
- ✅ Display driver abstraction: MAX7219, LCD, OLED + OfflineDisplayDriver
- ✅ Health endpoints: `GET /health` (liveness) + `GET /health/ready` (readiness)
- ✅ `GET /metrics` — runtime/API metrics JSON with separate rate limiting
- ✅ Rate limiting (60 req/min API, 120 req/min metrics)
- ✅ Schedule CRUD API: `POST/GET/PATCH/DELETE /api/v1/schedule`
- ✅ Schedule cancel endpoint: `POST /api/v1/schedule/{id}/cancel`
- ✅ Schedule types: ONESHOT, RECURRING (interval strings), CRON (unix cron)
- ✅ Effect renderer strategy pattern (ScrollEffect/BlinkEffect/ReverseEffect/FadeEffect)
- ✅ SchedulerService: coroutine-based, priority-sorted, maxRuns/expiresAt support
- ✅ Schedule UI page: `GET /schedule` (HTML with Stop/Delete buttons per row)
- ✅ `GET /openapi` Swagger UI
- ✅ H2/PostgreSQL database (switch via DATABASE_URL + DATABASE_DRIVER env vars)
- ✅ Gradle-based Docker image build (Ktor plugin — no Dockerfile)
- ✅ systemd service file + install script

### Test Suite
- **Framework:** Kotest FunSpec + MockK + kotlinx-coroutines-test
- **Convention:** All test names follow `should ...` pattern
- **Packages:** Test packages mirror production packages exactly
- **Count:** 20 test classes, all green
- **Coverage:** JaCoCo ≥70% line coverage gate — PASSING
- **Startup test:** `com.anjo.ApplicationTest` — 5 context startup tests

### DevOps
- **Docker:** `./gradlew publishImageToLocalRegistry` (no Dockerfile in repo)
- **Compose:** `.devops/containers/docker-compose.yml` — full env var mapping, no build section
- **Host:** `.devops/host/` — systemd unit + install script
- **Env template:** `.env.example` at project root + `.devops/containers/.env.example`

---

## Documentation Completed
- ✅ `README.md` — full API tables, env vars, Gradle Docker workflow, code layout
- ✅ `docs/deployment/production-guide.md` — 25 env vars table, PostgreSQL switching, Gradle tasks
- ✅ `docs/operations/monitoring-alerting.md` — all endpoints, schedule API, cancel docs
- ✅ `.planning/codebase/` (7 docs: STACK, INTEGRATIONS, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, CONCERNS)
- ✅ `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`
- ✅ All phase artifacts (01–05) with PLAN.md, SUMMARY.md, VERIFICATION.md

---

## Phase 5 Status — FULLY COMPLETE (2026-05-28)

### Wave 1 — Refactoring ✅
- 05-01: Health, retry, metrics cleanup
- 05-02: Mutex, metrics config, comment removal
- 05-03: Route + test package unification

### Wave 2 — Scheduling Engine ✅
- 05-04: Schedule data model + Exposed/H2 DB
- 05-05: SchedulerService coroutine engine + conflict policy
- 05-06: Schedule HTTP CRUD API + validation
- 05-07: Schedule UI page + DI wiring

### Wave 3 — Effects + Behavioral Tests ✅
- 05-08: Effect renderer architecture + DisplayDriver extensions
- 05-09: Effect field on POST /api/text + POST /api/schedule
- 05-10: Timing-accurate behavior tests (virtual time)

### Wave 4 — Post-Execution Fixes ✅ (2026-05-28)
- 05-11: SchedulerService bug fix, cancel endpoint, UI Stop button, test restructuring, deps update, env vars, Gradle Docker

**Verification:** `.planning/phases/05-scheduling-effects/05-VERIFICATION.md` — status: PASSED (10/10 + 8/8)  
**Plans:** 11 plans, all have SUMMARY.md  
**Build:** `./gradlew test -x jacocoTestCoverageVerification → BUILD SUCCESSFUL`

---

## Previous Phases (all complete)

- **Phase 4** ✅ — Observability cleanup, KHealth, metrics endpoint, DevOps artifacts
- **Phase 3** ✅ — Production ready: health, recovery, rate limiting, systemd, docs
- **Phase 2** ✅ — Multi-display: LCD, OLED, display selection, HTML pages
- **Phase 1** ✅ — MVP: text submission, MAX7219, validation, error handling

---

## Architecture Summary

**Layers:**
1. HTTP routes (`com/anjo/routing/*`)
2. Service layer (`com/anjo/service/*`, `com/anjo/service/effect/*`)
3. Driver abstraction + implementations (`com/anjo/driver/*`)
4. Database layer (`com/anjo/db/*`)
5. DI/plugin setup (`com/anjo/di/*`) + config (`com/anjo/config/*`)

**Data Flows:**
- `POST /api/v1/text` → `TextRoutes` → `ScreenDriverService.displayImmediate(text, effect)` → `EffectRenderer.render()` → `DisplayDriver`
- `POST /api/v1/schedule` → `ScheduleRoutes` → `ScheduleRepository.insert()` + `SchedulerService.schedule()`
- `SchedulerService.fire()` → `EffectRendererFactory.create(effect)` → `ScreenDriverService.displayScheduled()`

**Database:** H2 (default) → file `./data/schedules.mv.db`; switch to PostgreSQL via env vars only

---

## Key Decisions Since Phase 5 Execution

| Decision | Rationale |
|----------|-----------|
| `cancel()` must NOT be called from `schedule()` | Prevents new schedules from being immediately marked DONE in DB |
| Cancel endpoint separate from DELETE | Preserves schedule history; status → DONE without removal |
| Gradle Docker plugin instead of Dockerfile | Single source of truth for image config; Ktor plugin handles Dockerfile generation |
| H2 + PostgreSQL as `runtimeOnly` | Exposed is JDBC-agnostic; drivers loaded at runtime from classpath |
| `should` test naming | Kotest convention; self-documenting test intent |
| Test package mirrors production package | Easier navigation; `EffectRendererTest` in `com.anjo.service.effect` |

---

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-28)

**Core value:** Simple, reliable one-way display control from any browser on the home network.  
**Current focus:** Planning v2.0 milestone — run `/gsd-new-milestone` to begin.

## Milestone Archive

- **v1.0 archived:** 2026-05-28
- Roadmap archive: `.planning/milestones/v1.0-ROADMAP.md`
- Requirements archive: `.planning/milestones/v1.0-REQUIREMENTS.md`
- Phase archive: `.planning/milestones/v1.0-phases/`
- MILESTONES.md: `.planning/MILESTONES.md`
- Git tag: `v1.0`
