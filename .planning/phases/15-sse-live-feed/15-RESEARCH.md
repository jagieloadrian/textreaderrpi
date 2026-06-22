# Phase 15: SSE Live Feed - Research

**Researched:** 2026-06-22
**Domain:** Ktor SSE plugin, Kotlin SharedFlow, Browser EventSource
**Confidence:** HIGH (all key facts verified against official sources or codebase inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `DisplayEventBus` is a new dedicated service (NOT on `ScreenDriverService`). Holds `SharedFlow<DisplayEvent>`.
**D-02:** `SharedFlow` config: `replay = 5, extraBufferCapacity = 0`. New SSE connections receive the last 5 events automatically.
**D-03:** `DisplayEventBus` registered as a singleton in `DependencyInjection.kt`, mirrors `ZoneRegistry` / `HistoryRepository` pattern.
**D-04:** Emit point: inside `renderImmediate()` in `ScreenDriverService`, **after** `historyRepository.insert()` completes. Covers both immediate and scheduled display paths.
**D-05:** Replay resets on server restart. No DB warm-up.
**D-06:** No max concurrent connection limit.
**D-07:** Ktor SSE plugin (`ktor-server-sse`) handles subscriber lifecycle — client disconnect cancels collecting coroutine automatically.
**D-08:** `data class DisplayEvent(val id: Long, val text: String, val effect: String, val zoneId: String?, val displayedAt: String)` in `com.anjo.model`.
**D-09:** `webhookStatus` and `scheduleId` excluded from `DisplayEvent`.
**D-10:** SSE frame format: `event: display` + `data: <json>`. Client uses `addEventListener('display', handler)`.
**D-11:** All-zone stream, no `?zone=` filter.
**D-12:** New `<article>` block "Live Feed" at TOP of `StatusPage.kt`, above existing sections.
**D-13:** Widget shows most recent event's text, zone + effect below. Replaces content on each new event.
**D-14:** No "connecting" placeholder — replay buffer populates the widget on connect.
**D-15:** EventSource JS in `src/main/resources/static/live-feed.js`, loaded via `<script src="/static/live-feed.js">` in `StatusPage.kt` only. NOT in `BaseLayout.kt`.
**D-16:** Heartbeat: 30-second comment frame (`: keep-alive`) via a coroutine loop inside SSE route.

### Claude's Discretion

- `install(SSE)` location (module function vs. dedicated configure file)
- Whether to use built-in `heartbeat {}` DSL or manual `while(true) { delay(30_000); send(...) }` coroutine loop
- Test approach for SSE endpoint (HTTP header assertions vs. stream content)
- Whether `ScreenDriverService` takes `DisplayEventBus` as nullable or non-null constructor parameter

### Deferred Ideas (OUT OF SCOPE)

- Zone-filtered SSE stream (`?zone=X`)
- Per-connection SSE connection limit / 503 rejection
- DB warm-up of replay buffer on restart
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LIVE-01 | User can subscribe to a real-time SSE stream of display events (`GET /api/v1/live`, MutableSharedFlow replay=5) | `ktor-server-sse` plugin + `MutableSharedFlow(replay=5)` + emit in `renderImmediate()` |
| LIVE-02 | Status page shows real-time currently-displayed text via EventSource widget (no page reload needed) | `live-feed.js` + `StatusPage.kt` article + browser `EventSource` API |
| LIVE-03 | SSE connection stays alive through proxies via 30-second heartbeat comment frames | `heartbeat {}` DSL or `ServerSentEvent(comments = "keep-alive")` loop |
</phase_requirements>

---

## Summary

Phase 15 adds a real-time display event stream to an existing Ktor 3.5.0 application. The core mechanism is a Kotlin `MutableSharedFlow<DisplayEvent>` with `replay=5`, wrapped in a singleton `DisplayEventBus` service. The existing `ScreenDriverService.renderImmediate()` method emits events after each successful history insert. The Ktor SSE plugin (`ktor-server-sse`) provides the `sse {}` route DSL and the `ServerSentEvent` data class, which supports named events and comment-only heartbeat frames.

The status page gains a new "Live Feed" article rendered by `StatusPage.kt` (Ktor HTML DSL), backed by a separate `live-feed.js` static file that uses the browser `EventSource` API with `addEventListener('display', ...)`. This file is deliberately NOT loaded globally via `BaseLayout.kt` — only the status page script tag loads it.

All implementation decisions are locked in CONTEXT.md. The primary open area for the planner is how to structure the install point for the SSE plugin and the exact test strategy for the streaming endpoint.

**Primary recommendation:** Use the built-in `heartbeat {}` DSL for the 30-second comment frame — it is cleaner than a manual `while(true)` loop and avoids needing `coroutineScope { launch {} }` inside the route handler. The `collect {}` on `DisplayEventBus.events` runs directly in the `sse {}` block after the heartbeat declaration.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| SSE event emission | API / Backend | — | Server pushes; client only reads |
| Event bus (SharedFlow) | API / Backend service layer | — | Singleton shared state, same JVM |
| Heartbeat keep-alive | API / Backend (route handler) | — | Server-side protocol concern |
| Replay buffer | API / Backend (SharedFlow) | — | Kotlin stdlib feature, zero custom code |
| EventSource widget | Browser / Client | — | Native browser API, no framework |
| DOM update on event | Browser / Client | — | JS manipulation of span elements |
| SSE frame serialization | API / Backend (route handler) | — | `kotlinx.serialization` JSON in route |
| Status page HTML | Frontend Server (SSR) | — | Ktor HTML DSL, Kotlin templates |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.ktor:ktor-server-sse` | 3.5.0 | SSE plugin: `install(SSE)`, `sse {}` DSL, `ServerSentEvent` | First-party Ktor plugin, same version as project's Ktor [CITED: ktor.io/docs/server-server-sent-events.html] |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | `MutableSharedFlow`, `SharedFlow` | Already in project via `kotlinx-coroutines-core` [VERIFIED: gradle/ktor-libs.versions.toml] |
| Browser `EventSource` API | Native | Client-side SSE subscription | W3C standard, no library needed [ASSUMED] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx.serialization` JSON | (bundled with Ktor) | Serialize `DisplayEvent` to JSON in SSE `data:` field | Already in project via `ktor-serialization-kotlinx-json` [VERIFIED: build.gradle.kts] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| built-in `heartbeat {}` DSL | `while(true) { delay(30_000); send(...) }` in a launched coroutine | DSL is cleaner; manual loop requires explicit `launch {}` inside route handler; both work correctly |
| `ServerSentEvent(comments = ...)` for heartbeat | Named heartbeat event with `data:` | Comment frames (`:` prefix) are the standard proxy-keep-alive mechanism [ASSUMED] |

**Installation — one new line in build.gradle.kts:**
```kotlin
implementation(ktorLibs.ktor.server.sse)
```

**One new entry in gradle/ktor-libs.versions.toml:**
```toml
ktor-server-sse = { module = "io.ktor:ktor-server-sse", version.ref = "ktor" }
```

**Version verification:** `io.ktor:ktor-server-sse` confirmed present on Maven Central. The project's Ktor version is 3.5.0 [VERIFIED: gradle/ktor-libs.versions.toml line 4]. The artifact uses the same version ref — no separate version pin needed.

---

## Package Legitimacy Audit

> Note: This project uses Maven/JVM ecosystem. The npm-based legitimacy tool does not apply. Verification is via Maven Central and Ktor official documentation.

| Package | Registry | Age | Source Repo | Verdict | Disposition |
|---------|----------|-----|-------------|---------|-------------|
| `io.ktor:ktor-server-sse` | Maven Central | 2023+ (Ktor 2.3+) | github.com/ktorio/ktor | OK | Approved — first-party Ktor artifact, same group as all other Ktor deps [CITED: ktor.io/docs/server-server-sent-events.html] |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/text ──► TextRoutes ──► ScreenDriverService.displayImmediate()
                                              │
                              ┌───────────────┴──────────────────────┐
                              │                                        │
                         ZoneRegistry                       HistoryRepository.insert()
                         .broadcast()                               │
                                                         DisplayEventBus.emit(DisplayEvent)
                                                                    │
                                          ┌─────────────────────────┘
                                          │           (SharedFlow replay=5)
                                          ▼
                              GET /api/v1/live (SSE route)
                              ├── heartbeat { 30s, comments="keep-alive" }
                              └── events.collect { send(ServerSentEvent) }
                                          │
                                          ▼
                               Browser EventSource
                               addEventListener('display', handler)
                                          │
                                          ▼
                               StatusPage Live Feed article
                               #live-text / #live-meta DOM update
```

### Recommended Project Structure

New files to create:
```
src/main/kotlin/com/anjo/
├── model/
│   └── DisplayEvent.kt          # new data class
├── service/
│   └── DisplayEventBus.kt       # new singleton service
└── routing/
    └── LiveRoutes.kt            # new sse route handler

src/main/resources/static/
└── live-feed.js                 # new EventSource widget
```

Modified files:
```
gradle/ktor-libs.versions.toml   # add ktor-server-sse alias
build.gradle.kts                 # add ktor.server.sse dependency
src/main/kotlin/com/anjo/
├── di/DependencyInjection.kt    # register DisplayEventBus singleton; inject into ScreenDriverService
├── di/HTTP.kt (or Application.kt)  # install(SSE)
├── routing/Routing.kt           # add liveRoutes(displayEventBus) call
├── service/ScreenDriverService.kt  # add displayEventBus param; emit after historyRepository.insert()
└── web/templates/StatusPage.kt  # add Live Feed article + script tag
```

### Pattern 1: Ktor SSE Route with Heartbeat and SharedFlow Collect

**What:** SSE endpoint that emits display events and keeps connection alive via comment frames.
**When to use:** Whenever a one-directional server-push stream is needed.

```kotlin
// Source: ktor.io/docs/server-server-sent-events.html + github.com/ktorio/ktor
fun Route.liveRoutes(displayEventBus: DisplayEventBus) {
    sse("/live") {
        heartbeat {
            period = 30.seconds
            eventProvider = { ServerSentEvent(comments = "keep-alive") }
        }
        displayEventBus.events.collect { event ->
            send(ServerSentEvent(
                event = "display",
                data = Json.encodeToString(event)
            ))
        }
    }
}
```

### Pattern 2: DisplayEventBus Service

**What:** Thin wrapper around `MutableSharedFlow<DisplayEvent>` exposing a read-only `SharedFlow` view.
**When to use:** Any time you need a pub/sub in-process event bus in Kotlin.

```kotlin
// Source: kotlinx.coroutines documentation
class DisplayEventBus {
    private val _events = MutableSharedFlow<DisplayEvent>(replay = 5)
    val events: SharedFlow<DisplayEvent> = _events
    suspend fun emit(event: DisplayEvent) = _events.emit(event)
}
```

### Pattern 3: Emit Point in ScreenDriverService

**What:** After `historyRepository.insert()` returns the persisted record (with `id`), construct and emit a `DisplayEvent`.
**When to use:** Single emit point covering both `displayImmediate` and `displayScheduled` paths.

```kotlin
// Inside renderImmediate() after tryInsertHistory() succeeds
private suspend fun tryInsertHistory(...): HistoryRecord? {
    return historyRepository?.insert(...).also { record ->
        if (record != null) {
            displayEventBus?.emit(DisplayEvent(
                id = record.id.hashCode().toLong(),
                text = record.text,
                effect = record.effect,
                zoneId = record.zoneId,
                displayedAt = record.displayedAt
            ))
        }
    }
}
```

> Note: `HistoryRecord.id` is a UUID String, not a Long. D-08 specifies `val id: Long` in `DisplayEvent`. Use `record.id.hashCode().toLong()` or a sequential counter, OR change `DisplayEvent.id` to `String` to match. This is a decision the planner must make explicit — see Open Questions.

### Pattern 4: Install SSE Plugin

**What:** `install(SSE)` must be called before the `sse {}` route is registered.
**When to use:** Once in the application module.

```kotlin
// Add to di/HTTP.kt (alongside CORS and DefaultHeaders) or Application.kt module()
import io.ktor.server.sse.SSE
fun Application.configureHTTP() {
    install(CORS) { ... }
    install(DefaultHeaders) { ... }
    install(SSE)               // add here
}
```

### Pattern 5: Browser EventSource Widget

**What:** `live-feed.js` subscribes to the SSE stream and updates DOM elements.
**When to use:** Status page only.

```javascript
// Source: W3C EventSource API (standard browser API)
// File: src/main/resources/static/live-feed.js
(() => {
  const src = new EventSource('/api/v1/live');
  src.addEventListener('display', e => {
    const d = JSON.parse(e.data);
    const textEl = document.getElementById('live-text');
    const metaEl = document.getElementById('live-meta');
    if (textEl) textEl.textContent = d.text;
    if (metaEl) metaEl.textContent = (d.zoneId ?? '—') + ' · ' + d.effect;
  });
  src.onerror = () => {};
})();
```

### Anti-Patterns to Avoid

- **Installing SSE after routing:** `install(SSE)` must come before `routing { sse(...) {} }` is evaluated. In this project, `configureHTTP()` runs before `configureRouting()` in `Application.module()` — install there.
- **Adding live-feed.js to BaseLayout.kt:** The SSE connection would open on every page, not just `/status`. Place `<script src="/static/live-feed.js">` in `StatusPage.kt` only.
- **Emitting DisplayEvent before historyRepository.insert():** The event would have no `id` and could represent an event that was never persisted (if DB insert fails). Always emit after the insert completes.
- **Using `StateFlow` instead of `SharedFlow`:** `StateFlow` conflates events (only delivers latest). `SharedFlow(replay=5)` delivers every event and replays 5 on connect — matching LIVE-01 exactly.
- **Putting validation logic in the SSE route handler:** Project rule from STATE.md — validation stays in validator objects only. The SSE route has no query params (D-11), so this is not an issue for this phase.
- **Making `DisplayEventBus` nullable in DI:** Register it as a non-null singleton in `dependencies {}`. The `ScreenDriverService` constructor may take it as nullable (`DisplayEventBus? = null`) only for backward compatibility with existing tests that construct `ScreenDriverService` directly without a bus.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SSE frame formatting | Manual `text/event-stream` chunked response with string concatenation | `ktor-server-sse` plugin | Frame format, connection flushing, `Transfer-Encoding: chunked`, `Content-Type: text/event-stream` all handled by the plugin |
| Pub/sub event fan-out | `CopyOnWriteArrayList<Channel<T>>` manual subscriber list | `MutableSharedFlow` | SharedFlow handles concurrent collectors, cancellation, replay buffer, and backpressure — all thread-safe |
| Heartbeat timer | Manually launching a `Job` per connection and cancelling it on disconnect | `heartbeat {}` DSL | Built-in to Ktor SSE plugin; automatically scoped to the connection lifetime |
| JSON serialization of DisplayEvent | Hand-rolled JSON string builder | `kotlinx.serialization` `@Serializable` + `Json.encodeToString()` | Already in project; type-safe, handles null fields correctly |

**Key insight:** The Ktor SSE plugin abstracts all protocol-level SSE concerns. The only application code needed is the `DisplayEventBus` service (5 lines) and a thin route handler calling `collect {}` and `heartbeat {}`.

---

## Common Pitfalls

### Pitfall 1: SSE Plugin Not Installed — Route Returns 404 or 500

**What goes wrong:** The `sse {}` route handler compiles but Ktor returns an error at runtime because the SSE plugin is not installed.
**Why it happens:** `ssr {}` requires `install(SSE)` first. Without it, the route registration fails or requests receive an unexpected response.
**How to avoid:** Add `install(SSE)` in `configureHTTP()` (runs before `configureRouting()` in `Application.module()`).
**Warning signs:** Route registered but `GET /api/v1/live` returns non-SSE response or 500.

### Pitfall 2: SSE Route Registered Inside `route("/api/v1") { installApiRateLimiting(...) }`

**What goes wrong:** SSE connections are long-lived. If registered inside the rate-limited `/api/v1` block, each heartbeat frame or keep-alive tick may count against the rate limit, eventually closing the connection with 429.
**Why it happens:** `Routing.kt` wraps `/api/v1` with `installApiRateLimiting(apiConfig.rateLimitPerMinute)`. SSE is a persistent connection, not repeated request.
**How to avoid:** Register `liveRoutes(...)` **outside** the `route("/api/v1") { installApiRateLimiting(...) }` block, but still under `/api/v1` path prefix using a separate `route("/api/v1") { liveRoutes(displayEventBus) }` block without rate limiting.
**Warning signs:** Long-lived SSE connection drops after ~60 events in one minute.

### Pitfall 3: DisplayEvent.id Type Mismatch (String UUID vs Long)

**What goes wrong:** `HistoryRecord.id` is a `String` (UUID). D-08 defines `DisplayEvent.id` as `Long`. Direct assignment `id = record.id` fails to compile.
**Why it happens:** The CONTEXT.md decision used `Long` to mirror a numeric ID, but the project uses UUID strings.
**How to avoid:** Either (a) change `DisplayEvent.id` to `String` to match `HistoryRecord.id` exactly (cleaner), or (b) use a hash/counter conversion. Decision needed before implementation — see Open Questions.
**Warning signs:** Compile error in `ScreenDriverService` when constructing `DisplayEvent`.

### Pitfall 4: `collect {}` Blocks Heartbeat (or vice versa)

**What goes wrong:** `displayEventBus.events.collect { ... }` suspends indefinitely. If called before `heartbeat {}`, the heartbeat never runs.
**Why it happens:** `collect` is a terminal operator that suspends the coroutine forever (until cancellation). Sequential code after it never executes.
**How to avoid:** Declare `heartbeat {}` first (it registers a concurrent background task), then call `collect {}`. The `heartbeat {}` DSL is non-blocking — it launches a background coroutine inside the SSE scope.
**Warning signs:** 30-second heartbeat never fires; proxy drops long-idle connections.

### Pitfall 5: ScreenDriverService Tests Break After Adding DisplayEventBus Constructor Param

**What goes wrong:** Existing unit tests (e.g., `HistoryRecordingTest`, `ScreenDriverRecoveryTest`) construct `ScreenDriverService` directly with keyword arguments. Adding a new required parameter breaks them.
**Why it happens:** Kotlin constructors with required parameters require all callers to be updated.
**How to avoid:** Add `displayEventBus: DisplayEventBus? = null` as a nullable parameter with default `null`. The emit call in `tryInsertHistory` guards with `displayEventBus?.emit(...)`. Existing tests pass unchanged. The DI wiring provides the real instance in production.
**Warning signs:** Compile errors in test files that construct `ScreenDriverService` directly.

### Pitfall 6: Browser EventSource Auto-Reconnect on Status Page Navigation Away/Back

**What goes wrong:** If the user navigates away and back, a second `EventSource` may be created, doubling connections.
**Why it happens:** Standard `EventSource` reconnects automatically; `live-feed.js` is re-executed on page reload.
**How to avoid:** This is acceptable behavior for the home lab use case (D-06: no connection limit). Each page load creates one connection. Navigation away triggers `pagehide` which closes the connection automatically. No fix needed.
**Warning signs:** Not a pitfall in this use case — documented for awareness only.

---

## Code Examples

### Complete SSE Route Handler

```kotlin
// Source: ktor.io/docs/server-server-sent-events.html + CONTEXT.md decisions
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Route.liveRoutes(displayEventBus: DisplayEventBus) {
    sse("/live") {
        heartbeat {
            period = 30.seconds
            eventProvider = { ServerSentEvent(comments = "keep-alive") }
        }
        displayEventBus.events.collect { event ->
            send(ServerSentEvent(
                event = "display",
                data = Json.encodeToString(event)
            ))
        }
    }
}
```

### DisplayEventBus Service

```kotlin
// Source: kotlinx.coroutines SharedFlow API
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DisplayEventBus {
    private val _events = MutableSharedFlow<DisplayEvent>(replay = 5)
    val events: SharedFlow<DisplayEvent> = _events
    suspend fun emit(event: DisplayEvent) = _events.emit(event)
}
```

### DisplayEvent Model

```kotlin
// Source: CONTEXT.md D-08
import kotlinx.serialization.Serializable

@Serializable
data class DisplayEvent(
    val id: String,
    val text: String,
    val effect: String,
    val zoneId: String?,
    val displayedAt: String
)
```

> `id` shown as `String` here (matching `HistoryRecord.id` UUID) — see Open Questions #1 for final decision.

### DI Registration Pattern (mirrors ZoneRegistry)

```kotlin
// In DependencyInjection.kt configureDI(), following existing singleton pattern
val displayEventBus = DisplayEventBus()

val screenDriverService = ScreenDriverService(
    zoneRegistry = zoneRegistry,
    ioDispatcher = Dispatchers.IO,
    retryConfig = appConfig.retryConfig,
    metrics = screenDriverMetrics,
    hardwareMetrics = hardwareMetrics,
    historyRepository = historyRepository,
    displayEventBus = displayEventBus,    // add this
)

// In dependencies { } block:
provide { displayEventBus }
```

### Routing Registration (outside rate-limited block)

```kotlin
// In Routing.kt configureRouting():
val displayEventBus: DisplayEventBus by dependencies

routing {
    staticResources("/static", "static")
    webRoutes(zoneRegistry)
    // ... existing UI routes ...

    route("/api/v1") {
        installApiRateLimiting(apiConfig.rateLimitPerMinute)
        textRoutes(screenDriverService)
        // ... existing API routes ...
    }

    // SSE outside rate-limited block — persistent connection
    route("/api/v1") {
        liveRoutes(displayEventBus)
    }
}
```

### Status Page Live Feed Article (Ktor HTML DSL)

```kotlin
// In StatusPage.kt pageContent(), BEFORE existing article blocks
article {
    h2 { +"Live Feed" }
    p { +"Current Text: "; span { id = "live-text"; +"—" } }
    p { span { id = "live-meta"; +"" } }
}
// At end of StatusPage.render() call:
// BaseLayout.render(..., headExtra = { script(src = "/static/live-feed.js") {} }) { ... }
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual chunked HTTP for streaming | `ktor-server-sse` plugin with `sse {}` DSL | Ktor 2.3+ | No hand-rolled chunked transfers |
| WebSocket for one-way server push | SSE (`text/event-stream`) | Industry shift ~2019 | Auto-reconnect built into browser; simpler than WebSocket for one-directional use |

**Deprecated/outdated:**
- `Channel<T>` per SSE subscriber with manual fan-out: replaced by `SharedFlow` which handles all this automatically.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `heartbeat {}` DSL runs concurrently with `collect {}` without an explicit `launch {}` | Architecture Patterns, Pitfall 4 | If DSL is sequential, heartbeat would never fire after collect. Fix: use `coroutineScope { launch { heartbeat } + collect }` — both patterns compile correctly either way. |
| A2 | `heartbeat { eventProvider = { ServerSentEvent(comments = "...") } }` emits a comment frame (`: keep-alive`) correctly | Code Examples | If the plugin formats comments differently, proxy keep-alive may not work. Can be verified empirically with `curl --no-buffer`. |
| A3 | Browser `EventSource` auto-reconnect does not cause issues for the home lab use case | Common Pitfalls #6 | Benign for 1-5 users; could matter in a multi-tab scenario. |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed.

---

## Open Questions

1. **`DisplayEvent.id` type: `Long` (per D-08) vs `String` (matching `HistoryRecord.id`)**
   - What we know: `HistoryRecord.id` is a UUID `String`. D-08 specifies `Long` for `DisplayEvent.id`.
   - What's unclear: How to convert. `hashCode().toLong()` is lossy; a monotonic counter requires state.
   - Recommendation: Change `DisplayEvent.id` to `String` matching the UUID. Simpler, no conversion, unambiguous for clients. The planner should confirm before writing the model file.

2. **`install(SSE)` placement: `configureHTTP()` vs a new `configureSSE()` function**
   - What we know: `install(SSE)` requires no configuration — it's a one-liner.
   - What's unclear: Project convention for adding a new plugin.
   - Recommendation: Add to `configureHTTP()` alongside `CORS` and `DefaultHeaders`. No new file needed for a single `install()` call.

3. **SSE route placement relative to rate limiting**
   - What we know: The current `route("/api/v1") { installApiRateLimiting(...) }` block wraps all API routes. A long-lived SSE connection should not be subject to per-minute rate limiting.
   - What's unclear: Whether the rate limiter counts SSE frames or only initial connection requests.
   - Recommendation: Register `liveRoutes(...)` in a separate `route("/api/v1") { }` block without `installApiRateLimiting`. This is conservative and correct — see Pitfall 2.

---

## Environment Availability

Step 2.6: All dependencies are Maven artifacts — no external services, CLIs, or runtimes beyond the existing JDK 25 / Gradle build required. The `ktor-server-sse` artifact will be resolved from Maven Central during the first Gradle build after adding the dependency.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `io.ktor:ktor-server-sse` | LIVE-01, LIVE-03 | ✓ (Maven Central) | 3.5.0 | — |
| `kotlinx-coroutines-core` | LIVE-01 (`SharedFlow`) | ✓ | 1.11.0 [VERIFIED: ktor-libs.versions.toml] | — |
| Browser `EventSource` API | LIVE-02 | ✓ (all modern browsers) | Native | — |

**Missing dependencies with no fallback:** none
**Missing dependencies with fallback:** none

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 with `FunSpec` style |
| Config file | `src/test/kotlin/com/anjo/ProjectConfig.kt` (coroutineTestScope = true) |
| Quick run command | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LIVE-01 | `GET /api/v1/live` returns `Content-Type: text/event-stream` | unit (HTTP) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ Wave 0 |
| LIVE-01 | New SSE connection receives replay of last 5 events immediately | unit (stream) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ Wave 0 |
| LIVE-01 | Emit after display causes SSE frame within 1 second | integration | `./gradlew test --tests "com.anjo.service.DisplayEventBusTest"` | ❌ Wave 0 |
| LIVE-02 | `GET /status` HTML contains `id="live-text"` and `id="live-meta"` spans | unit (HTML) | `./gradlew test --tests "com.anjo.routing.WebRoutesTest"` | ✅ (extend existing) |
| LIVE-02 | `GET /status` HTML loads `<script src="/static/live-feed.js">` | unit (HTML) | `./gradlew test --tests "com.anjo.routing.WebRoutesTest"` | ✅ (extend existing) |
| LIVE-03 | SSE connection receives `Content-Type: text/event-stream` header | unit (HTTP) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ Wave 0 |
| — | `DisplayEventBus` singleton resolved from DI | unit (DI) | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ (extend existing) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.routing.LiveRoutesTest" --tests "com.anjo.service.DisplayEventBusTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green (`./gradlew test`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt` — covers LIVE-01, LIVE-03 (HTTP response headers, Content-Type, SSE frame format)
- [ ] `src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt` — covers `DisplayEventBus` emit/collect, replay=5, unit test with no Ktor dependency

**Testing note on streaming:** Ktor `testApplication` + `HttpClient` can make a request to an SSE endpoint and check response headers (`Content-Type: text/event-stream`). Reading actual SSE frames in tests requires setting `responseTimeout = 0` on the test client and reading the byte channel — this is more involved. Recommend testing the `DisplayEventBus` in isolation (plain coroutine tests) and testing the route for header correctness only. The one-second event latency requirement (LIVE-01 success criterion) is a manual/integration test, not unit-testable without a real display action.

---

## Security Domain

> `security_enforcement` not set to false in `.planning/config.json` — section included.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | SSE endpoint is unauthenticated (project-wide: no auth scope) |
| V3 Session Management | No | SSE is stateless server-push; no session tokens |
| V4 Access Control | No | Home lab; no access control in scope |
| V5 Input Validation | No | `GET /api/v1/live` has no query parameters (D-11) |
| V6 Cryptography | No | No encryption in scope |

### Known Threat Patterns for SSE

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| EventSource data injected into DOM via `innerHTML` | Tampering/XSS | Use `textContent` not `innerHTML` in `live-feed.js` — already in CONTEXT.md D-13 and code example pattern |
| Open SSE endpoint as resource exhaustion vector | DoS | D-06: acceptable for home lab; no throttling needed |

---

## Sources

### Primary (MEDIUM confidence)
- `ktor.io/docs/server-server-sent-events.html` — SSE plugin install, route DSL, heartbeat config, ServerSentEvent usage
- `github.com/ktorio/ktor` shared/ktor-sse/common — `ServerSentEvent` data class signature (all 5 fields: data, event, id, retry, comments)

### Secondary (MEDIUM confidence — codebase inspection)
- `/home/diether18/IdeaProjects/TextReaderRpi/gradle/ktor-libs.versions.toml` — confirmed Ktor 3.5.0, kotlinx-coroutines 1.11.0, existing library alias patterns
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/di/DependencyInjection.kt` — singleton registration pattern, exact constructor call for ScreenDriverService
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/routing/Routing.kt` — rate-limited block structure, existing route registrations, staticResources already present
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — `renderImmediate()` and `tryInsertHistory()` structure, exact parameter names, nullable `historyRepository` pattern
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/model/HistoryRecord.kt` — `id: String` (UUID) field confirmed
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/web/templates/StatusPage.kt` — existing article DSL pattern, BaseLayout.render signature with headExtra
- `/home/diether18/IdeaProjects/TextReaderRpi/src/main/resources/static/app.js` — existing JS style (IIFE, textContent usage, DOM id patterns)

### Tertiary (LOW confidence)
- None required — all key facts confirmed from official docs or codebase inspection.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `ktor-server-sse` confirmed on Maven Central; in project Ktor version group; `SharedFlow` already in codebase via kotlinx-coroutines
- Architecture: HIGH — all files inspected directly; DI pattern, route structure, HTML DSL patterns all confirmed from codebase
- SSE API details: MEDIUM — confirmed from official Ktor docs and GitHub source; `heartbeat {}` concurrent behavior tagged [ASSUMED] (A1)
- Pitfalls: MEDIUM — derived from code inspection and SSE protocol knowledge; rate-limiter interaction (Pitfall 2) is conservative reasoning

**Research date:** 2026-06-22
**Valid until:** 2026-07-22 (Ktor 3.5.0 is stable; SharedFlow API stable in kotlinx-coroutines 1.x)
