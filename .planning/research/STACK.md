# Technology Stack — TextReaderRpi v1.1 (New Features Only)

**Project:** TextReaderRpi  
**Researched:** 2026-06-11  
**Scope:** Stack additions for 5 new v1.1 capabilities. Existing v1.0 stack is locked and not re-evaluated here.

---

## Critical Pre-Research Finding

After reading the actual source code, the PROJECT.md description "replacing Flaxoos JDBC scheduler" is misleading. The `SchedulerService` is **already pure coroutines** — it uses `CoroutineScope + delay + ConcurrentHashMap<String, Job>`. No Flaxoos scheduler was ever added. Flaxoos is present **only** for rate limiting (`ktor-server-rate-limiting`). This changes the scope of the scheduler feature significantly.

---

## Existing Stack (Locked — Do Not Change)

| Library | Version | Role |
|---------|---------|------|
| Kotlin | 2.3.21 | Language |
| Ktor server | 3.5.0 | HTTP server + HTML DSL |
| Pi4J | 4.0.0 | Hardware GPIO/SPI/I2C |
| Exposed | 1.3.0 (`org.jetbrains.exposed.v1.*`) | ORM |
| H2 / PostgreSQL | 2.4.240 / 42.7.7 | Database |
| HikariCP | 7.0.2 | Connection pool |
| kotlinx-coroutines | 1.11.0 | Concurrency |
| cron-utils | 9.2.1 | CRON expression parsing |
| Flaxoos rate-limiting | 2.2.1 | Token bucket rate limiter |
| KHealth | 3.0.2 | Health check plugin |
| Kotest | 6.1.11 | Test framework |
| MockK | 1.14.9 | Mocking |
| logback-classic | 1.5.18 | Logging |

---

## New Dependencies Required

### Feature 1: Coroutine-Based Scheduler (Clarified Scope)

**Actual finding:** The scheduler (`SchedulerService.kt`) is already coroutine-based. It uses `CoroutineScope(Dispatchers.Default + SupervisorJob())` with `delay`, a `ConcurrentHashMap<String, Job>`, and a `tickLoop()`. No new library is needed.

**What v1.1 actually needs for the scheduler:**
- Add `webhookUrl: String?` field to the `Schedule` model and `SchedulesTable`
- Add conflict policy enum (`SKIP_NEW`, `PREEMPT`) to `SchedulerService.fire()`
- These are pure code changes — zero new dependencies

**Verdict: No new library. Zero additions.**

---

### Feature 2: Multi-Zone Display Management

**Actual finding:** `DisplaySelectionService` is single-driver (one active driver at a time with a `driverCache`). Multi-zone requires running multiple `ScreenDriverService` instances concurrently, one per named zone.

**What is needed:** A `ZoneRegistry` that holds a `Map<String, ScreenDriverService>` and routes `POST /api/v1/zones/{zone}/text` to the correct service instance. Each zone gets its own Pi4J pin configuration.

**Verdict: No new library.** Multi-zone is a structural refactor of `DisplaySelectionService` and `ScreenDriverService`. The existing `kotlinx-coroutines` Mutex and `ConcurrentHashMap` are sufficient. Pi4J 4.0.0 already supports multiple simultaneous I/O registrations.

---

### Feature 3: Display Text History / Audit Log

**What is needed:** A new `DisplayHistoryTable` in Exposed, recording each `fire()` event with timestamp, text, zone, schedule ID. Exposed 1.3.0 already provides `exposed-java-time` for `java.time.Instant` column mapping.

**New Exposed module required:**

The project already has `exposed-core`, `exposed-jdbc`, `exposed-java-time` in the TOML. No new Exposed artifact is needed — the audit log is a new table defined with `object DisplayHistoryTable : Table("display_history")`.

**Verdict: No new library.** One new `Table` object + one new `Repository` class using existing Exposed.

---

### Feature 4: Webhook / Push Notifications on Schedule Fire

**What is needed:** Outbound HTTP POST from `SchedulerService.fire()` when `schedule.webhookUrl != null`. This requires a Ktor HTTP client.

**Recommended addition: `ktor-client-core` + `ktor-client-cio`**

| Artifact | Version | Purpose |
|----------|---------|---------|
| `io.ktor:ktor-client-core` | 3.5.0 | Ktor client API (same version as server — no transitive conflicts) |
| `io.ktor:ktor-client-cio` | 3.5.0 | CIO engine — pure Kotlin/coroutine, no additional thread pool, minimal memory overhead |
| `io.ktor:ktor-client-content-negotiation` | 3.5.0 | JSON serialization for webhook body (reuses existing kotlinx.serialization setup) |

**Why CIO engine, not OkHttp or Apache:**
- CIO (Coroutine I/O) is the Ktor-native engine. It runs on the existing coroutine dispatcher — no extra thread pool or native library.
- OkHttp adds ~600 KB jar + Java thread pool overhead; unnecessary for home Pi.
- Apache HttpClient adds ~2 MB + blocking thread model; contradicts coroutine-first design.
- CIO jar is ~150 KB and shares the JVM event loop already in use.

**Why same 3.5.0 version:**
- Ktor client and server must share the same version to avoid classpath conflicts on shared modules (`ktor-io`, `ktor-http`). Version 3.5.0 is already locked by the server.

**TOML additions:**
```toml
[versions]
# (no new version entry needed — reuse ktor = "3.5.0")

[libraries]
ktor-client-core              = { module = "io.ktor:ktor-client-core",                version.ref = "ktor" }
ktor-client-cio               = { module = "io.ktor:ktor-client-cio",                 version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
```

**`build.gradle.kts` additions:**
```kotlin
implementation(ktorLibs.ktor.client.core)
implementation(ktorLibs.ktor.client.cio)
implementation(ktorLibs.ktor.client.content.negotiation)
```

**Integration pattern:**
```kotlin
// In SchedulerService — inject as dependency, close on stop()
private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) { requestTimeoutMillis = 5_000 }
}

private suspend fun fireWebhook(url: String, schedule: Schedule) {
    try {
        httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(WebhookPayload(scheduleId = schedule.id, text = schedule.text, firedAt = Instant.now().toString()))
        }
    } catch (e: Exception) {
        log.warn("Webhook delivery failed for schedule ${schedule.id}: ${e.message}")
        // Non-fatal — display still fires
    }
}
```

**Verdict: Add 3 Ktor client artifacts at version 3.5.0.**

---

### Feature 5: UI/UX Refresh

**What is needed:** Improvements to Ktor HTML DSL templates in `src/main/kotlin/com/anjo/web/templates/`. Currently uses PicoCSS 2 via CDN link. The history page needs a new template; the schedule page needs zone selector controls.

**Verdict: No new library.** Changes are in the existing Kotlin HTML DSL templates. PicoCSS 2 (already in `BaseLayout.kt` via CDN) is sufficient for the new pages. No JS framework.

---

## Full Delta: What Changes in `ktor-libs.versions.toml`

```toml
# ADD under [libraries] — these are the only new entries needed for all 5 features:
ktor-client-core                 = { module = "io.ktor:ktor-client-core",                 version.ref = "ktor" }
ktor-client-cio                  = { module = "io.ktor:ktor-client-cio",                  version.ref = "ktor" }
ktor-client-content-negotiation  = { module = "io.ktor:ktor-client-content-negotiation",  version.ref = "ktor" }
```

No new `[versions]` entries. No new plugins. Everything else is code-only changes against the existing stack.

---

## What NOT to Add (Keep Pi Memory Footprint Small)

| What | Why Not |
|------|---------|
| OkHttp | Adds ~600 KB + Java thread pool; redundant with CIO |
| Apache HttpClient 5 | ~2 MB jar, blocking model, contradicts coroutines |
| Quartz Scheduler | 2+ MB, JDBC locking tables, overkill — scheduler already works |
| JobRunr | JVM reflection-heavy, designed for multi-node clusters; Pi is single-node |
| Flaxoos task-scheduler module | Different Flaxoos module from rate-limiting — adds JDBC task_locks table, designed for cluster-safe distributed jobs. Unnecessary for single-Pi |
| Spring Batch / Spring Data | Full Spring context — ~50 MB heap overhead, incompatible with Ktor DI |
| React / Vue / HTMX | Adds build tooling; Ktor HTML DSL + minimal JS is sufficient for home UI |
| Redis / Kafka | Event bus overkill; webhook HTTP POST is sufficient for notifications |
| Flyway / Liquibase | DB migration framework; Exposed's `SchemaUtils.createMissingTablesAndColumns()` is sufficient for this scale |
| kotlinx-datetime | Not needed — `java.time.Instant` + `exposed-java-time` already handle all timestamp needs |
| Koin / Kodein | Ktor 3.x `ktor-server-di` already in use; second DI framework creates conflicts |

---

## Integration Notes

### Scheduler + Webhook Integration
`SchedulerService.fire()` is the single integration point. After calling `screenService.displayScheduled()`, call `fireWebhook()` if `schedule.webhookUrl != null`. The `HttpClient` should be a singleton injected into `SchedulerService` and closed in `stop()` via `httpClient.close()`.

### History + Scheduler Integration
Add `DisplayHistoryRepository.insert()` call inside `SchedulerService.fire()` after successful display. Also call it from `ScreenDriverService.displayImmediate()` for ad-hoc text submissions. The `displayedAt` timestamp uses `java.time.Instant.now()` — already available via `exposed-java-time`.

### Multi-Zone + DI Integration
`DependencyInjection.kt` currently creates one `ScreenDriverService`. For multi-zone, replace with a `ZoneRegistry` class wrapping `Map<String, ScreenDriverService>`. The Ktor DI (`ktor-server-di`) `provide { }` block provides the registry. Route parameter `{zone}` looks up the correct service instance.

### Test additions needed
- `ktor-client-mock` (`io.ktor:ktor-client-mock`, version `3.5.0`) for unit-testing webhook calls without a live HTTP server. Add as `testImplementation`.

```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

---

## Final Dependency Summary

| Artifact | Version | Scope | Purpose |
|----------|---------|-------|---------|
| `ktor-client-core` | 3.5.0 | `implementation` | Ktor HTTP client API |
| `ktor-client-cio` | 3.5.0 | `implementation` | CIO engine (coroutine-native, Pi-friendly) |
| `ktor-client-content-negotiation` | 3.5.0 | `implementation` | JSON body for webhook POST |
| `ktor-client-mock` | 3.5.0 | `testImplementation` | Mock engine for webhook unit tests |

**Total new jars: 4.** All reuse the existing `ktor = "3.5.0"` version reference. No version conflicts possible.

---

## Sources

- Codebase analysis: `src/main/kotlin/com/anjo/service/SchedulerService.kt` (scheduler already coroutine-based)
- Codebase analysis: `src/main/kotlin/com/anjo/di/RateLimiting.kt` (Flaxoos used only for rate limiting)
- Codebase analysis: `gradle/ktor-libs.versions.toml` (locked versions)
- Ktor documentation: https://ktor.io/docs/client-create-new-application.html (CIO engine choice rationale)
- Ktor versioning policy: client and server must share the same version to avoid `ktor-io` classpath conflicts
- Pi4J 4.0.0 documentation: supports concurrent I/O registrations for multi-zone
- Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`): `SchemaUtils.createMissingTablesAndColumns()` handles new table creation without migration framework
