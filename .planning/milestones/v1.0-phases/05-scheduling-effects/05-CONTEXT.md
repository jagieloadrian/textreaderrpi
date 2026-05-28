# Phase 5: Scheduling + Effects - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 delivers two things in sequence:

1. **Refactoring completion (Wave 1)** — architectural cleanup items from Phase 4 that weren't fully implemented to vision: health endpoint consolidation, retry library adoption, metrics annotation-config, concurrency guard replacement, route/test package unification, comment removal.

2. **Scheduling + Effects (Waves 2–3)** — new product capabilities: timed/recurring text display with SQL persistence, display effect options (blink, reverse, fade, scroll), schedule management API + UI page.

This phase clarifies HOW to implement both the cleanup and the new capabilities. Multi-display/voice control/remote access remain out of scope.

</domain>

<decisions>
## Implementation Decisions

### Wave 1: Refactoring completion

- **D5-01:** Remove `GET /health/detail` endpoint entirely. Merge its logic (uptime, memoryUsedMb, memoryMaxMb, displayType, isActive, lastError) into the `/health` response body via KHealth extension mechanism.
- **D5-02:** Replace custom `RecoveryPolicy` with a Kotlin-native retry library (lightweight coroutine-based; research phase selects best fit — Resilience4j's `retryWhen` or a coroutine-retry helper). API surface: `withRetry("policyName") { block }` higher-order function wrapper.
- **D5-03:** Retry policy configuration moved to `application.yaml` under `retry.*` namespace (e.g., `retry.screenDriver.maxAttempts: 3`, `retry.screenDriver.initialDelayMs: 100`). Policy looked up by name at startup.
- **D5-04:** Replace `ResourceTracker` with `kotlinx.coroutines.sync.Mutex` for display race-condition protection in `ScreenDriverService`. ResourceTracker is removed (confirmed in-session as permitted).
- **D5-05:** MetricRegistry instrumentation made config-driven from `application.yaml` (e.g., `metrics.enabled: true`, `metrics.prefix: "textreaderrpi"`). Agent decides the annotation/wrapper approach that avoids hardcoded metric registration in service bodies.
- **D5-06:** Remove **all comments** from service and driver code (noise vs. intent distinction is removed entirely — code speaks for itself).
- **D5-07:** Move all routes from `web.routes.*` into `com.anjo.routing.*` — single routing package, no split between `web/routes/` and `routing/`.
- **D5-08:** Unify all test classes under `com.anjo.*` sub-packages matching production code (currently split between `com.anjo` and bare `routing/` in test source root). Test package mirrors production package exactly.

### Wave 2: Scheduling

- **D5-09:** Schedule model supports **both** one-shot (fire at specific datetime) and recurring (interval strings like `"5m"`, `"2h"` AND cron expressions like `"0 9 * * *"`).
- **D5-10:** Scheduled item fields: `id`, `text`, `trigger` (oneOf: `{at: ISO8601}` / `{every: "5m"}` / `{cron: "0 9 * * *"}`), `effect` (optional, default: "scroll"), `priority` (Int, default 0), `maxRuns` (optional Int), `expiresAt` (optional ISO8601), `createdAt`, `status` (active/paused/expired/done).
- **D5-11:** Schedule HTTP API — full CRUD: `POST /api/schedule`, `GET /api/schedule`, `GET /api/schedule/{id}`, `DELETE /api/schedule/{id}`, `PATCH /api/schedule/{id}`.
- **D5-12:** Scheduler engine: agent decides the lightest in-process approach compatible with existing coroutine model (kotlinx-coroutines delay loop or a minimal scheduler abstraction). No Quartz dependency.
- **D5-13:** Persistence: **Jetbrains Exposed** ORM, **H2** in-process by default. Configurable to PostgreSQL via `application.yaml` (`db.url`, `db.driver`, `db.user`, `db.password`).
- **D5-14:** Schedule management UI page: `GET /schedule` — built with existing Ktor HTML DSL templates in `com.anjo.web.templates`, consistent with IndexPage/StatusPage/SettingsPage pattern.
- **D5-15:** Conflict policy: **live ad-hoc submissions preempt scheduled** — a `POST /api/text` interrupts any ongoing scheduled display. Scheduled items that were interrupted are re-queued.
- **D5-16:** Pending schedule queue: **unbounded** — accumulates all fired but waiting items. No drop-on-full behavior.
- **D5-17:** Same-time tie-break: **priority descending, then creation order ascending** (higher priority wins; equal priority → earlier-created schedule wins).
- **D5-18:** Recurring schedule lifetime: runs indefinitely unless an **optional `expiresAt`** date is set (or explicitly deleted via DELETE).

### Wave 3: Display Effects

- **D5-19:** Effects in scope: **scroll** (default, existing), **blink** (alternate frames at configurable rate), **reverse** (reversed character order before scroll), **fade** (gradual brightness ramp in/out).
- **D5-20:** Effect placement: `effect` field on both `POST /api/text` and `POST /api/schedule` request body (optional, enum: `scroll | blink | reverse | fade`). Default: `scroll`.
- **D5-21:** Effect architecture: agent decides the cleanest integration with `DisplayDriver` — likely a strategy/renderer pattern or decorator on the existing `scrollText()` coroutine path.
- **D5-22:** Timing-accurate behavior tests are MUST — test that scheduled items fire at approximately the correct time, that conflict policy works correctly, that effects render without corrupting display state.

### Agent's Discretion

- Retry library selection (D5-02): agent picks the best Kotlin-native lightweight retry helper — Resilience4j's coroutine extension, or a minimal in-house `retryWithBackoff` coroutine function configured from YAML.
- Metrics annotation/wrapper approach (D5-05): agent designs the wrapper that avoids hardcoded metric registration, keeping it configurable from application.yaml.
- Effect architecture (D5-21): agent picks strategy/renderer/decorator pattern based on what integrates cleanly with `DisplayDriver`.
- Scheduler engine (D5-12): agent picks lightest in-process coroutine scheduler — no Quartz.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project scope and prior context
- `.planning/ROADMAP.md` — Phase 5 requirements and scope
- `.planning/REQUIREMENTS.md` — Full feature requirements and non-functional constraints
- `.planning/STATE.md` — Current project state and completed phase status
- `.planning/phases/04-observability-cleanup/04-CONTEXT.md` — Phase 4 decisions (carried-forward constraints)
- `.planning/phases/04-observability-cleanup/04-VERIFICATION.md` — What Phase 4 actually delivered

### Architecture and code layout
- `.planning/codebase/ARCHITECTURE.md` — Layered arch, package responsibilities, DI flow
- `.planning/codebase/STACK.md` — Tech stack and dependency versions
- `.planning/codebase/INTEGRATIONS.md` — How services wire together
- `.planning/codebase/CONVENTIONS.md` — Coding conventions to follow

### Key source files for Wave 1 refactoring
- `src/main/kotlin/com/anjo/di/Monitoring.kt` — KHealth setup to extend for D5-01
- `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` — to be removed (D5-01)
- `src/main/kotlin/com/anjo/model/HealthModels.kt` — to be removed or merged into health response
- `src/main/kotlin/com/anjo/service/RecoveryPolicy.kt` — to be replaced (D5-02, D5-03)
- `src/main/kotlin/com/anjo/service/ResourceTracker.kt` — to be removed (D5-04)
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt` — gains Mutex concurrency guard (D5-04)
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — DI wiring changes for new services
- `src/main/kotlin/com/anjo/routing/Routing.kt` — route assembly after web.routes cleanup (D5-07)
- `src/main/kotlin/com/anjo/web/routes/ApiRoutes.kt` — to migrate to routing package (D5-07)
- `src/main/kotlin/com/anjo/web/routes/WebRoutes.kt` — to migrate to routing package (D5-07)

### Key source files for Wave 2–3
- `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` — interface that effects must integrate with (D5-21)
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt` — scheduling hooks into here
- `src/main/kotlin/com/anjo/routing/Routing.kt` — schedule routes registered here
- `src/main/kotlin/com/anjo/web/templates/` — template package for schedule UI page (D5-14)
- `src/main/resources/application.yaml` — db.* and retry.* and metrics.* config sections added here

### Test structure (for D5-08)
- `src/test/kotlin/routing/` — currently mispackaged, move to `com.anjo.routing`
- `src/test/kotlin/com/anjo/service/` — correctly packaged, mirror this pattern

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Ktor HTML DSL templates** (`web.templates.*`): BaseLayout, IndexPage, StatusPage, SettingsPage, ErrorPage — schedule UI page follows this exact pattern
- **`installApiRateLimiting` / `installMetricsRateLimiting`** in `RateLimiting.kt` — schedule API endpoints should use same rate limit pattern
- **KHealth** in `Monitoring.kt` — extend with health fields per D5-01 (don't create another route)
- **`DisplayDriver.scrollText()`** — current rendering entry point, effects decorate or replace this call
- **`application.yaml` typed config** via `ConfigLoader` + `ApplicationConfig` model — extend with `db.*`, `retry.*`, `metrics.*` sections

### Established Patterns
- **Feature-first routing** (`routing/TextRoutes.kt`, `routing/DisplayRoutes.kt`) — schedule routes follow same pattern
- **Ktor DI plugin** (`dependencies { provide { ... } }`) — new services (ScheduleService, ScheduleRepository) wired here
- **Coroutines + `Dispatchers.IO`** for blocking I/O — database calls use same dispatcher pattern
- **`@Serializable data class`** in `model/` package — schedule request/response DTOs follow same pattern
- **Kotest FunSpec** — test style for all new tests

### Integration Points
- `ScreenDriverService.readInput()` — scheduling service calls this to trigger display
- Conflict policy: scheduling service must check `ScreenDriverService.busy` state before preempting
- `metricsCollector` hardware group — update for Mutex-based concurrency guard (D5-04)
- KHealth extension — `/health` body must include the fields previously in `/health/detail`

</code_context>

<specifics>
## Specific Ideas

- Schedule UI should live at `GET /schedule` — list active schedules, allow create/cancel inline
- H2 connection: in-process mode (`jdbc:h2:./data/schedules`) — no separate server, file on disk survives restarts
- PostgreSQL: configurable via `db.url: jdbc:postgresql://...`, agent chooses correct HikariCP pool config
- Retry config naming: `retry.screenDriver.maxAttempts`, `retry.screenDriver.initialDelayMs`, `retry.screenDriver.maxDelayMs` — matches existing RecoveryPolicy field names
- All existing comments removed from services and drivers — clean code, no noise

</specifics>

<deferred>
## Deferred Ideas

- **Multi-zone displays** — different text on different displays simultaneously (future phase)
- **Remote/cloud access** — secure external web access (explicitly out of scope per PROJECT.md)
- **Voice control integration** — future phase
- **Message templates** — pre-defined templates for common messages (future phase)
- **Weather/calendar feed integration** — out of scope per PROJECT.md
- **Micrometer/Prometheus migration** — Phase 4 designed metrics model to be low-friction; actual migration deferred (D4-06 intent)

</deferred>

---

*Phase: 5-Scheduling + Effects*
*Context gathered: 2026-05-27*

