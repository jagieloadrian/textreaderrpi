# Architecture Patterns — TextReaderRpi v1.1 Integration

**Project:** TextReaderRpi  
**Researched:** 2026-06-11  
**Mode:** Integration analysis for 6 v1.1 features into existing layered Kotlin/Ktor architecture

---

## Existing Architecture (v1.0 baseline)

```
HTTP Request
    │
    ▼
com.anjo.routing.*          ← route handlers (TextRoutes, ScheduleRoutes, WebRoutes, etc.)
    │
    ▼
com.anjo.service.*          ← orchestration (ScreenDriverService, SchedulerService, EffectRendererFactory)
    │              │
    │              ▼
    │     com.anjo.service.effect.*   ← EffectRenderer strategy (ScrollEffect, BlinkEffect, etc.)
    │
    ▼
com.anjo.driver.*           ← DisplayDriver interface + implementations (Max7219Matrix, LcdDisplay, OledDisplay, OfflineDisplayDriver)
    │
    ▼
Pi4J / SPI / I2C hardware

com.anjo.db.*               ← Exposed tables + repositories (SchedulesTable, ScheduleRepository)
com.anjo.di.*               ← Ktor DI wiring, plugin configuration
com.anjo.config.*           ← Typed YAML config (ConfigLoader, config models)
com.anjo.model.*            ← Domain types (Schedule, Effect, TriggerType, etc.)
com.anjo.web.templates.*    ← Ktor HTML DSL page templates
com.anjo.validation.*       ← Ktor RequestValidation config + validators
```

### Current Data Flows

```
POST /api/v1/text
  → TextRoutes
  → ScreenDriverService.displayImmediate(text, effect)
    → displayMutex.withLock { executeWithRecovery(text, renderer) }
    → EffectRenderer.render(text, driver)
    → DisplayDriver (Max7219Matrix / LCD / OLED / Offline)

POST /api/v1/schedule
  → ScheduleRoutes
  → ScheduleRepository.insert(schedule)
  → SchedulerService.schedule(schedule)
    → launchOneShot/launchRecurring/launchCron → coroutine Job → activeJobs[id]

SchedulerService.fire(schedule)
  → EffectRendererFactory.create(effect)
  → ScreenDriverService.displayScheduled(text, scheduleId, renderer)
    → displayMutex.withLock { executeWithRecovery(text, renderer) }

ApplicationStarted event → SchedulerService.start() → loads ACTIVE schedules from DB
ApplicationStopping event → SchedulerService.stop() → cancels all coroutines
```

### Key Structural Observations

- `SchedulerService` already uses coroutines (ONESHOT/RECURRING/CRON via `scope.launch`). The comment "rewrite scheduler" in v1.1 refers to removing `flaxoos` rate-limiting dependency (already done), and cleaning up CRON expiry logic. The core engine is coroutine-based.
- `ScreenDriverService` holds a mutable `var driver: DisplayDriver` — the single-zone assumption is baked into its constructor and `queueDisplaySwitch()` logic.
- `DisplaySelectionService` wraps Pi4J context creation and driver caching. It is the Pi4J lifecycle owner.
- `DependencyInjection.kt` creates all singletons manually and registers them with Ktor DI. Adding new services follows the same pattern.
- `SchedulesTable` is the only DB table. History and multi-zone each need new tables.

---

## Component Map

### New Components Needed

| Component | Package | Type | Purpose |
|-----------|---------|------|---------|
| `DisplayHistoryTable` | `com.anjo.db` | Exposed Table object | Persist display history rows |
| `DisplayHistoryRepository` | `com.anjo.db` | Repository class | CRUD for history (insert + paginated query) |
| `DisplayHistoryService` | `com.anjo.service` | Service class | Business logic: insert on every display, query for UI |
| `HistoryPage` | `com.anjo.web.templates` | HTML DSL template | Render `/history` page |
| `HistoryRoutes` | `com.anjo.routing.ui` | Route extension | `GET /history` |
| `ZoneRegistry` | `com.anjo.service` | Singleton registry | Maps zone IDs → `ScreenDriverService` instances |
| `ZoneConfig` | `com.anjo.config.model` | Config data class | Per-zone type + hardware settings in `application.yaml` |
| `ZoneRoutes` | `com.anjo.routing` | Route extension | `/api/v1/zones/{zoneId}/text`, `/api/v1/zones/{zoneId}/schedule` |
| `WebhookConfig` | `com.anjo.config.model` | Config data class | Webhook URL list, retry settings |
| `WebhookService` | `com.anjo.service` | Service class | HTTP POST to configured URLs after `fire()` |
| `WebhookTable` | `com.anjo.db` | Exposed Table object | Persist webhook delivery log (optional, for retry) |

### Modified Components

| Component | Current State | Change Required | Risk |
|-----------|--------------|-----------------|------|
| `SchedulerService` | Solid coroutine engine; has tickLoop, cancel, ONESHOT/RECURRING/CRON | (1) Add `WebhookService` call inside `fire()`. (2) Add `SKIP_NEW` conflict policy check in `schedule()`. (3) Enforce CRON expiry (`expiresAt`) — currently missing in `launchCron`. | Low — additive |
| `ScreenDriverService` | Single-zone: `var driver: DisplayDriver` mutated on switch; `displayImmediate` / `displayScheduled` operate on that single driver | Refactor constructor to accept `driver` from `ZoneRegistry` instead of `DisplaySelectionService`. For multi-zone: extract single-zone logic to per-zone instances; `ScreenDriverService` becomes one instance per zone. | Medium — constructor change + DI rewire |
| `DisplaySelectionService` | Creates Pi4J context, caches drivers by type, handles live switch | With multi-zone: each zone gets its own `DisplaySelectionService` instance (or the registry holds per-zone Pi4J configs). No code rewrite; instantiate N times via `ZoneRegistry`. | Low — reuse pattern |
| `DependencyInjection.kt` | Creates one of each service, wires manually | Register `ZoneRegistry`, `DisplayHistoryService`, `WebhookService`. Provide them to routes. | Low — additive |
| `Routing.kt` | Registers route groups | Add `historyRoutes(...)`, `zoneRoutes(...)` | Low — additive |
| `ScheduleRoutes.kt` | Zone-unaware; operates on single `SchedulerService` | Add optional `?zone=` query param or zone-prefixed route delegating to `ZoneRegistry.schedulerFor(zoneId)` | Low |
| `SchedulesTable` | No zone column | Add `zoneId varchar(64) default("default")` column; add Exposed migration | Medium — schema migration |
| `DatabaseFactory.kt` | Calls `SchemaUtils.create(SchedulesTable)` | Add `DisplayHistoryTable`, `ZoneRegistry`-related tables to schema create | Low |
| `BaseLayout.kt` / nav | Current nav: Home, Schedule, Settings, Status | Add "History" nav link | Low |
| `WebRoutes.kt` / `web.templates.*` | 5 HTML pages | Restyle all via shared CSS changes in `BaseLayout.kt` | Low — isolated in templates |
| `MetricsCollector` | Runtime + API metrics groups | Add hardware group: display failures, retry counts, per-zone utilization | Low |

### Untouched Components (stable, no change)

- `DisplayDriver` interface — multi-zone uses the same interface; no change needed.
- `EffectRenderer` / `EffectRendererFactory` — effect strategy is zone-agnostic.
- `RetryPolicy` / `retryWithBackoff` — no change.
- `RequestValidation` / validators — extend for new request shapes, not restructured.
- `ErrorHandling.kt` — no change.
- `Font.kt` — no change.
- `model/Schedule.kt` — add `zoneId: String = "default"` field only.

---

## Integration Points

### Feature 1: Display History

**Hook point:** Inside `ScreenDriverService.displayImmediate()` and `displayScheduled()` — after the mutex lock succeeds and before `executeWithRecovery()`. Call `historyService.record(text, effect, source)` where `source` is `"IMMEDIATE"` or `"SCHEDULED"`. This is non-blocking (fire-and-forget coroutine or suspend call).

**Data model:**
```
DisplayHistoryTable:
  id        varchar(36)  PK
  text      text
  effect    varchar(16)
  zoneId    varchar(64)  default "default"
  source    varchar(16)  (IMMEDIATE | SCHEDULED)
  firedAt   varchar(32)  ISO-8601
```

**UI route:** `GET /history` → `HistoryRoutes` → `DisplayHistoryService.findRecent(limit=50)` → `HistoryPage.render()`.

**DI wire:** `DisplayHistoryRepository` and `DisplayHistoryService` registered in `DependencyInjection.kt`. `ScreenDriverService` constructor gains `historyService: DisplayHistoryService?` parameter (nullable avoids breaking existing tests).

**Test strategy:** Unit-test `DisplayHistoryService` with in-memory H2. Test `HistoryRoutes` with `testApplication {}`. Check `ScreenDriverService` calls `historyService.record()` via MockK.

---

### Feature 2: Multi-Zone

**Hook point:** `ZoneRegistry` is the DI entry point. It owns a `Map<String, ScreenDriverService>` keyed by zone ID. At startup it reads zone configs from `application.yaml` under a new `zones:` block and creates one `DisplaySelectionService` + `SchedulerService` + `ScreenDriverService` per zone.

**Config shape (application.yaml):**
```yaml
zones:
  - id: "default"
    type: MAX7219
    ...
  - id: "secondary"
    type: LCD
    ...
```

**API shape:** `/api/v1/zones/{zoneId}/text` delegates to `ZoneRegistry.screenDriverFor(zoneId).displayImmediate(...)`. The existing `/api/v1/text` route continues to operate on the `"default"` zone — no breaking change.

**Schema migration:** Add `zoneId` column to `SchedulesTable` with `default("default")`. Existing rows adopt the default, no data loss.

**Key constraint (Pi single-node):** All zone drivers share the same Pi4J `Context`. `DisplaySelectionService` already accepts a `Context` at construction — pass the single shared context to each zone's `DisplaySelectionService`. Driver cache is per-`DisplaySelectionService` instance, which is correct (each zone has its own hardware address).

**DI wire:** `ZoneRegistry` registered as a singleton. `ZoneRoutes` receives `ZoneRegistry`. Existing single-zone routes continue unchanged by routing to `ZoneRegistry.default`.

---

### Feature 3: Webhook Notifications

**Hook point:** Inside `SchedulerService.fire()`, after `screenService.displayScheduled(...)` succeeds. Call `webhookService.notify(schedule)` as a non-blocking `scope.launch {}` — webhook failures must not block or fail the display path.

**WebhookService contract:**
```kotlin
class WebhookService(private val client: HttpClient, private val config: WebhookConfig) {
    fun notify(schedule: Schedule) {
        scope.launch {
            config.urls.forEach { url ->
                retryWithBackoff { client.post(url) { ... } }
            }
        }
    }
}
```

**Dependencies:** Ktor `HttpClient` (CIO engine, already on classpath). No new Gradle dependencies needed — `ktor-client-core` + `ktor-client-cio` to add.

**Config:**
```yaml
webhooks:
  urls: []          # empty = disabled
  timeoutMs: 5000
  retries: 3
```

**DI wire:** `WebhookService` created in `DependencyInjection.kt`. `SchedulerService` constructor gains `webhookService: WebhookService?` (nullable so existing tests without webhook config continue to pass).

**Pi memory constraint:** Use CIO engine, not OkHttp. CIO is pure Kotlin coroutines with minimal overhead, appropriate for <256MB heap target.

---

### Feature 4: Scheduler Rewrite / Cleanup

**What the "rewrite" actually is:**  
The existing `SchedulerService` is already coroutine-based (no Flaxoos JDBC). The v1.1 work is:  
1. Fix `launchCron` — it currently loops forever without checking `expiresAt` or `maxRuns`.  
2. Implement `SKIP_NEW` conflict policy: in `schedule()`, if `activeJobs.containsKey(schedule.id)` and policy is `SKIP_NEW`, return immediately instead of cancelling the running job.  
3. Add `ConflictPolicy` enum (`CANCEL_ONGOING`, `SKIP_NEW`) to `model/` and thread it through `Schedule` data class and `SchedulerService.schedule()`.  
4. Add `zoneId` field to `Schedule` so `SchedulerService` can route to correct zone driver.

**No Flaxoos removal needed** — the `flaxoos.ktor.server.rateLimiting` dependency is for the HTTP rate-limiting plugin, not the scheduler. It stays. The `cron-utils` dependency (cron expression parsing) also stays.

**Concrete changes:**
- `Schedule.kt`: add `conflictPolicy: ConflictPolicy = ConflictPolicy.CANCEL_ONGOING`, `zoneId: String = "default"`.
- `SchedulesTable`: add `conflictPolicy`, `zoneId` columns with defaults.
- `SchedulerService.schedule()`: read `schedule.conflictPolicy`, branch accordingly.
- `SchedulerService.launchCron()`: add `expiresAt` and `maxRuns` checks matching the `launchRecurring` pattern.

---

### Feature 5: Refactor (Driver Layer + ScreenDriverService Simplification)

**Target areas:**

`ScreenDriverService` currently holds `var driver` as a mutable var and has `queueDisplaySwitch()` / `checkAndPerformPendingSwitch()` as workaround for display switching while locked. With multi-zone, each zone's `ScreenDriverService` is fixed to one driver — the switch logic becomes unnecessary per-zone. Simplification path:
- Remove `pendingDisplayType` / `queueDisplaySwitch()` / `checkAndPerformPendingSwitch()` from `ScreenDriverService`.
- Move display-type switching into `ZoneRegistry` as a top-level operation (switch zone → recreate or swap the zone's `ScreenDriverService`).
- `ScreenDriverService` becomes immutable after construction: `val driver: DisplayDriver`.

`DisplaySelectionService` has `logCurrentData()` that dumps all Pi4J internals at INFO level — noisy in production. Remove or move to DEBUG.

`MAX7219Matrix` chain order bug: fix the byte-reversal in the SPI packet assembly. This is a pure driver change with no architectural impact.

**Refactor is a prerequisite for multi-zone** because `ScreenDriverService` must stop mutating `var driver` before it can safely be instantiated N times.

---

### Feature 6: UI Refresh

**Hook point:** `BaseLayout.kt` — the shared HTML shell that all pages extend. CSS changes in one file cascade to all pages. New `HistoryPage.kt` follows the existing template pattern.

**Scope:**
- Update `<style>` block in `BaseLayout.kt` or link a new `/static/style.css` via `staticResources`.
- Update `IndexPage`, `SchedulePage`, `StatusPage`, `SettingsPage`, `ErrorPage` template files.
- Add `HistoryPage` as a new template.
- Add nav link "History" to `BaseLayout.kt` nav section.

**No architectural change** — UI is fully server-side Ktor HTML DSL. No JS build pipeline needed.

---

## Recommended Build Order (Phase Dependency Graph)

```
Phase A: Refactor (driver + ScreenDriverService simplification)
    │
    ├─► Phase B: Gap closures (health/detail, SKIP_NEW, metrics hardware group, HTML error pages)
    │       [independent of A in principle; sequenced here to keep DB schema migrations together]
    │
    ├─► Phase C: Scheduler cleanup (CRON expiry, ConflictPolicy, zoneId field on Schedule)
    │       [requires: A — ScreenDriverService stable before adding zone routing]
    │
    ├─► Phase D: Display History (new table, service, /history page)
    │       [requires: C — zoneId on Schedule ensures history rows carry zone context]
    │
    ├─► Phase E: Webhook Notifications (WebhookService, HttpClient, fire() hook)
    │       [requires: C — fire() is clean and stable after scheduler cleanup]
    │
    └─► Phase F: Multi-Zone (ZoneRegistry, zone config, zone API routes)
            [requires: A, C, D — ScreenDriverService immutable, Schedule has zoneId, history records zone]

Phase G: UI Refresh
    [requires: D — HistoryPage exists; otherwise independent; can run in parallel with E/F]
```

### Rationale

**A before everything:** `ScreenDriverService.var driver` mutability is the biggest structural knot. Multi-zone requires per-zone immutable instances. Webhook and history can be added to the current mutable version, but doing refactor first means all later features are built on the clean API.

**C before D, E, F:** Adding `zoneId` and `conflictPolicy` to `Schedule` and `SchedulesTable` is a schema migration. Done once in Phase C, all subsequent features (history with zone context, webhooks with schedule payload, multi-zone routing) benefit from the extended model.

**D before F:** `DisplayHistoryService` is injected into `ScreenDriverService`. When F creates N `ScreenDriverService` instances, each gets the same `DisplayHistoryService` singleton. History must exist before zone instances are created.

**E before F (or parallel):** `WebhookService` is injected into `SchedulerService`. When F creates N `SchedulerService` instances, each gets the same `WebhookService` singleton. E and F can technically be developed in parallel but E should be merged first to avoid DI conflicts.

**G anytime after D:** `HistoryPage` is the only new UI page. UI CSS changes are safe to do at any point — isolated in `BaseLayout.kt` and template files with no service dependencies.

---

## Component Boundaries After v1.1

```
com.anjo.routing
  ├── TextRoutes         (unchanged)
  ├── ScheduleRoutes     (add optional zoneId param)
  ├── ZoneRoutes         (NEW — zone-scoped text + schedule endpoints)
  ├── DisplayRoutes      (unchanged)
  ├── MetricsRoutes      (add hardware metrics group)
  └── ui/
       ├── WebRoutes     (unchanged)
       ├── ScheduleUIRoutes (unchanged)
       └── HistoryRoutes  (NEW)

com.anjo.service
  ├── ScreenDriverService   (refactored: val driver, no switch logic)
  ├── SchedulerService      (extended: webhookService, conflictPolicy, CRON expiry fix)
  ├── ZoneRegistry          (NEW — owns N ScreenDriverService + SchedulerService instances)
  ├── WebhookService        (NEW — fire-and-forget HTTP POST)
  ├── DisplayHistoryService (NEW — record + query display history)
  ├── DisplaySelectionService (unchanged — instantiated per zone by ZoneRegistry)
  ├── EffectRendererFactory (unchanged)
  └── effect/               (unchanged)

com.anjo.db
  ├── SchedulesTable     (add zoneId, conflictPolicy columns)
  ├── ScheduleRepository (add zoneId filter method)
  ├── DisplayHistoryTable (NEW)
  └── DisplayHistoryRepository (NEW)

com.anjo.driver            (unchanged interface; MAX7219 chain order fix)

com.anjo.model
  ├── Schedule           (add zoneId, conflictPolicy fields)
  ├── ConflictPolicy     (NEW enum: CANCEL_ONGOING, SKIP_NEW)
  └── (all others unchanged)

com.anjo.config.model
  ├── ZoneConfig         (NEW — per-zone id, type, hardware settings)
  └── WebhookConfig      (NEW — urls list, timeoutMs, retries)

com.anjo.web.templates
  ├── BaseLayout         (nav update + CSS refresh)
  ├── HistoryPage        (NEW)
  └── (all others: CSS/layout updates only)

com.anjo.di
  └── DependencyInjection.kt (register ZoneRegistry, WebhookService, DisplayHistoryService)
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Shared mutable `var driver` across zones
**What:** Keeping `ScreenDriverService.var driver` mutable and sharing one `ScreenDriverService` for all zones via a zone ID parameter.
**Why bad:** Concurrent zone writes race on the same mutex. Zone A's display fires while Zone B swaps the driver pointer.
**Instead:** One immutable `ScreenDriverService` per zone, each with its own `Mutex` and fixed `val driver`.

### Anti-Pattern 2: History as an afterthought appended in routes
**What:** Calling `historyRepository.insert()` directly inside `TextRoutes` or `ScheduleRoutes`.
**Why bad:** History is a cross-cutting concern. It must fire for both immediate and scheduled displays. Route-level calls miss all scheduled fires.
**Instead:** Insert history inside `ScreenDriverService.displayImmediate()` and `displayScheduled()` — the single convergence point for all display operations.

### Anti-Pattern 3: Blocking webhook call inside `fire()`
**What:** `client.post(webhookUrl)` called synchronously inside the `fire()` suspend function.
**Why bad:** On Pi hardware with flaky home-network connectivity, a 5-second webhook timeout blocks the display coroutine. Next scheduled fire is delayed.
**Instead:** Fire-and-forget via `scope.launch {}` inside `WebhookService.notify()`. Webhook failures log but do not propagate to the display path.

### Anti-Pattern 4: Pi4J Context per zone
**What:** Creating one `Pi4J.newAutoContext()` per zone in `ZoneRegistry`.
**Why bad:** Pi4J registers hardware providers globally. Multiple contexts on one Pi fail with duplicate provider errors.
**Instead:** Single `Pi4J.newAutoContext()` in `DependencyInjection.kt` (current pattern), passed to each zone's `DisplaySelectionService` constructor.

### Anti-Pattern 5: DB schema migration via `SchemaUtils.createMissingTablesAndColumns`
**What:** Using Exposed's `createMissingTablesAndColumns` to add new columns to `SchedulesTable` in place.
**Why bad:** Exposed's missing-column detection does not handle column defaults reliably on H2 in all versions. New columns added mid-table can produce unexpected NULL constraint violations on existing rows.
**Instead:** Add new columns with explicit `default(...)` in the Table definition and call `SchemaUtils.create(table, inBatch = false)` on startup. For existing deployed databases, document a one-time SQL migration script (`ALTER TABLE schedules ADD COLUMN zone_id VARCHAR(64) DEFAULT 'default'`).

---

## Scalability on Raspberry Pi 4 (single node)

| Concern | Current (v1.0) | After v1.1 |
|---------|---------------|-----------|
| JVM heap | <256MB target | Same — no new heap-heavy libs. Ktor `HttpClient` (CIO) adds ~2MB. History table adds negligible H2 overhead. |
| Coroutine count | 1 per active schedule | N zones × active schedules. For home use (<5 zones, <20 schedules), no issue. Pi4J thread pool unchanged. |
| DB connections | HikariCP pool, H2 file | Same pool. Two new tables. H2 file size grows with history (paginate + TTL prune recommended). |
| Pi4J GPIO | 1 SPI, 1 or 2 I2C buses | Multi-zone maps to distinct hardware addresses. Bus contention only if >1 zone uses same bus — prevented by config validation at startup. |
| Network I/O | None (inbound only) | Webhook adds outbound HTTP. CIO client uses coroutine dispatcher, no extra threads. Timeout bounded by `WebhookConfig.timeoutMs`. |

---

## Sources

- Codebase read: `src/main/kotlin/com/anjo/` (all production Kotlin files)
- `.planning/STATE.md` — v1.0 architecture summary and deferred items
- `.planning/PROJECT.md` — v1.1 requirements and constraints
- `.planning/codebase/ARCHITECTURE.md`, `CONCERNS.md` — historical analysis
- `build.gradle.kts` — dependency inventory (confirmed: no Flaxoos scheduler, only Flaxoos rate-limiting; `cron-utils` present)
- Confidence: HIGH — analysis based on direct codebase reading, no external research required for this integration-focused question
