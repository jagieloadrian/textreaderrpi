---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Refactor + Fixes + UI + New Features
status: executing
last_updated: "2026-06-15T08:49:16.883Z"
last_activity: 2026-06-15
progress:
  total_phases: 8
  completed_phases: 2
  total_plans: 10
  completed_plans: 6
  percent: 25
---

# Project State & Memory

**Last Updated:** 2026-06-11  
**Status:** Executing Phase 08

## Current Position

Phase: 08 (refactor-dead-code-analysis) — EXECUTING
Plan: 2 of 5
**Phase:** 8
**Plan:** 08-01 COMPLETE — DI binding-resolution smoke test added
**Status:** All 11 configureDI() bindings verified green; safe to proceed with dead-code removal
**Last activity:** 2026-06-15

Progress: `[ Phase 6 | Phase 7 | Phase 8 | Phase 9 | Phase 10 | Phase 11 | Phase 12 ]`  
`░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░` 0% (0/7 phases)

---

## Project Context

- **Name:** TextReaderRpi
- **Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface
- **Core value:** Simple, reliable one-way display control from any browser on the home network
- **Users:** Home lab enthusiasts, DIY electronics hobbyists
- **Timeline:** No hard deadline; iterative development

---

## Codebase Status

- **Language:** Kotlin 2.3.21, JDK 25 toolchain
- **Framework:** Ktor 3.5.0 (DI plugin + RequestValidation + StatusPages)
- **ORM:** Exposed 1.3.0 (`org.jetbrains.exposed.v1.*` packages)
- **Hardware:** Pi4J 4.0.0 + MAX7219 via SPI, LCD/OLED via I2C
- **Database:** H2 (embedded default) or PostgreSQL (via env vars)
- **Current package root:** `src/main/kotlin/com/anjo/...`
- **Test suite:** 20 test classes, Kotest `should` convention, JaCoCo ≥70% gate

---

## Existing Features (v1.0 complete)

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

---

## v1.1 Roadmap Summary

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 6 | MAX7219 Hardware Fix | HW-01, HW-02 | COMPLETE |
| 7 | Scheduler Schema Stabilisation | SCHED-01, SCHED-02, SCHED-03, SCHED-04 | Not started |
| 8 | Refactor + Dead Code Analysis | REF-01, REF-02, REF-03, REF-04 | Not started |
| 9 | Display History + Audit Log | HIST-01, HIST-02, HIST-03 | Not started |
| 10 | Webhooks | HOOK-01, HOOK-02, HOOK-03 | Not started |
| 11 | Multi-Zone Displays | ZONE-01 through ZONE-08 | Not started |
| 12 | Observability Gap Closures + UI/UX Refresh | OBS-01, OBS-02, OBS-03, UI-01 through UI-08 | Not started |

---

## Accumulated Context

### Key Decisions (v1.1 planning)

| Decision | Rationale |
|----------|-----------|
| buildPacket placed in companion object (not instance method) | Tests call Max7219Matrix.buildPacket(...) without Pi4J construction — pure JVM testable |
| Per-row buildPacket(bitmap, offset, numDevices, row) signature | Called 8 times from render(); enables 2-byte per-row assertions in Kotest |
| Size guards removed from write() and displayStatic() | buildPacket handles short bitmaps safely via else false bounds check |
| isHardwareAvailable() as protected abstract hook in AbstractDisplayDriver | status() delegates to it polymorphically; drivers supply their own availability predicate |
| Max7219Matrix.isHardwareAvailable() = lastError == null | SPI always creates handle; failure stored as lastError |
| LcdDisplay/OledDisplay.isHardwareAvailable() = i2c != null && lastError == null | I2C handle is null when ctx.create() throws during init |
| Phase 6 first: fix MAX7219 before adding zones | Hardware bug amplifies across all multi-zone testing |
| Phase 7: targeted schema fixes, NOT a scheduler rewrite | SchedulerService is already coroutine-based; Flaxoos stays for HTTP rate limiting |
| Phase 8: DI smoke test is the first task | Silent DI failures block all refactor work safely |
| Phase 9: history cap MAX_ROWS=1000 in HistoryRepository.insert() | Prevents SD card fill on long-running Pi |
| Phase 10: webhooks fire in separate IOScope, withTimeout(5_000) | Does not block Dispatchers.Default (only 4 threads on Pi 4) |
| Phase 11: single shared Pi4J context, unique string IDs per zone | Avoids Pi4J SPI registration collision crash on startup |
| Phase 12: add headExtra to BaseLayout before any page-specific CSS | Prevents CSS cascade breaks in Ktor HTML DSL |
| Stack additions: ktor-client-core/cio/content-negotiation 3.5.0 only | Zero version conflicts; no OkHttp, Quartz, JobRunr, Flyway, JS frameworks |
| Flyway 9.22.3 added as migration layer (07-01) | baselineOnMigrate=true handles existing Pi installs; 9.x chosen for simpler community licensing |
| ONESHOT firedAt filter scoped to triggerType=ONESHOT (07-01) | Avoids accidentally excluding RECURRING/CRON rows if firedAt ever set for those types |
| updateFiredAtAndDone() uses single suspendTransaction{} (07-01) | Crash-safe atomic firedAt+status=DONE update prevents ONESHOT re-fire after Pi restart |
| displayMutex.isLocked (not tryLock) for SKIP_NEW (07-02) | Snapshot read avoids deadlock; false negatives acceptable for drop-if-busy policy |
| fire() returns Boolean propagated from displayScheduled (07-02) | launchRecurring conditionally increments runs only when display occurred (D-05) |
| CRON validation moved to ScheduleRoutes POST handler (07-02) | Allows persist-with-ERROR before 422 response; ScheduleValidators returns Valid for CRON |
| HTTP 202 with accepted=false for SKIP_NEW TextRoutes response (07-02) | Consistent with existing 202 Accepted; client reads accepted boolean to detect skip |
| DI smoke test requires client.get() trigger before getBlocking() (08-01) | testApplication defers module execution until first HTTP interaction; accessing dependencies before startup yields MissingDependencyException |
| getBlocking() import explicit: io.ktor.server.plugins.di.getBlocking (08-01) | Top-level extension function — not auto-imported by package membership |
| CoroutineDispatcher (not CloseableCoroutineDispatcher) for Dispatchers.IO key (08-01) | Ktor DI infers declared type CoroutineDispatcher; runtime type is CloseableCoroutineDispatcher but key must match declared type |

### Critical Pitfalls to Watch

- MAX7219 SPI packet direction: `d=0` controls the physically last module — fix `render()` to iterate `numDevices - 1 downTo 0`
- ONESHOT duplicate fire: add `firedAt` column, set atomically with `status=DONE`, skip if `targetMs < now` on restart
- Bad CRON spin: write `status=ERROR` in catch; exclude ERROR from `findAllActive()`
- Pi4J multi-zone collision: each zone must use `Spi.newConfigBuilder().id("max7219-zone-N")`
- Webhook IOScope: `scope.launch {}` + `withTimeout(5_000)`, never retry inline
- H2 history growth: `MAX_ROWS=1000` cap with daily cleanup coroutine

### Open Decisions for Phase 11

- Pi4J single-context-unique-IDs approach needs hardware validation (two-zone spike recommended first)
- H2 `AUTO_SERVER=TRUE` behavior in Docker on Pi should be tested early in Phase 9

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

**New v1.1 integration points:**

- History recording → inside `ScreenDriverService.displayImmediate()` and `displayScheduled()`
- Webhooks → inside `SchedulerService.fire()` via `scope.launch {}`
- Multi-zone → single shared `Pi4J.newAutoContext()` with unique IDs per zone

---

## DevOps

- **Docker:** `./gradlew publishImageToLocalRegistry` (no Dockerfile in repo)
- **Compose:** `.devops/containers/docker-compose.yml` — full env var mapping, no build section
- **Host:** `.devops/host/` — systemd unit + install script
- **Env template:** `.env.example` at project root + `.devops/containers/.env.example`

---

## Milestone Archive

- **v1.0 archived:** 2026-05-28
- Roadmap archive: `.planning/milestones/v1.0-ROADMAP.md`
- Requirements archive: `.planning/milestones/v1.0-REQUIREMENTS.md`
- Phase archive: `.planning/milestones/v1.0-phases/`
- MILESTONES.md: `.planning/MILESTONES.md`
- Git tag: `v1.0`
