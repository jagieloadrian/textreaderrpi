---
phase: 15-sse-live-feed
reviewed: 2026-06-23T12:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/main/kotlin/com/anjo/di/HTTP.kt
  - src/main/kotlin/com/anjo/model/DisplayEvent.kt
  - src/main/kotlin/com/anjo/routing/LiveRoutes.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/service/DisplayEventBus.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/web/templates/StatusPage.kt
  - src/main/resources/static/live-feed.js
  - src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/WebRoutesTest.kt
  - src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-06-23T12:00:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Phase 15 introduces an SSE live feed: `DisplayEventBus` backed by a `MutableSharedFlow(replay=5, extraBufferCapacity=0)`, `ScreenDriverService` emitting after history insert via `tryEmit`, a `GET /api/v1/live` Ktor SSE endpoint with a 30-second heartbeat, a minimal `live-feed.js` EventSource widget, and a `StatusPage.kt` Live Feed article.

No critical bugs were found. The previously identified mutex-deadlock risk does not exist in the submitted code because `ScreenDriverService` already uses `tryEmit()`, not `emit()`. The `live-feed.js` `JSON.parse` call is correctly wrapped in `try/catch`, and `onerror` correctly reloads only on `EventSource.CLOSED`.

Four warnings and three info findings are present. The most impactful warning is that `tryEmit()` with `extraBufferCapacity=0` silently drops events when any collector is mid-receive and the return value is never checked. A secondary structural concern is that the `/api/v1/live` endpoint is registered in a separate `route("/api/v1")` block that lacks the rate limiter installed on the first block, making the SSE endpoint unlimited.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: `tryEmit()` silently drops events when SSE collector is present — return value never checked

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:189`

**Issue:** `DisplayEventBus` is configured as `MutableSharedFlow(replay = 5, extraBufferCapacity = 0)`. Per `kotlinx.coroutines` semantics, `tryEmit()` returns `false` — without suspending — when there is at least one active subscriber whose internal receive slot is not immediately available. Crucially, a `false` return means the value is **not** placed in the replay cache either; it is silently discarded. The call site ignores the `Boolean` return value entirely:

```kotlin
displayEventBus?.tryEmit(DisplayEvent(...))
```

Consequence: while any SSE client is connected, display events can be silently lost from both the live stream and the replay buffer. A client that connects immediately after a burst of rapid displays may see a gap in the replay cache that has no observable error signal anywhere in the system.

**Fix:** Either add `extraBufferCapacity` so that `tryEmit` has buffer space to succeed (removing the silent-drop window), or log a warning when `tryEmit` returns `false`:

```kotlin
val emitted = displayEventBus?.tryEmit(
    DisplayEvent(id = record.id, text = record.text,
                 effect = record.effect, zoneId = record.zoneId,
                 displayedAt = record.displayedAt)
)
if (emitted == false) {
    log.warn("DisplayEvent dropped — no buffer space (bus full)")
}
```

A more robust fix adds `extraBufferCapacity = 64` to `DisplayEventBus` so transient collector latency does not cause drops:

```kotlin
private val _events = MutableSharedFlow<DisplayEvent>(replay = 5, extraBufferCapacity = 64)
```

### WR-02: `/api/v1/live` registered outside the rate-limited route block — SSE endpoint is unbounded

**File:** `src/main/kotlin/com/anjo/routing/Routing.kt:64`

**Issue:** The routing configuration installs the API rate limiter on the first `route("/api/v1")` block (line 55–62) and then registers `liveRoutes` in a separate second `route("/api/v1")` block (lines 64–66). In Ktor, `installApiRateLimiting` installs the Flaxoos `RateLimiting` plugin as a scoped plugin on the first route subtree only. The second `route("/api/v1")` block creates a distinct route node that does not inherit that plugin.

As a result, `GET /api/v1/live` has no rate limit. Any client (including unauthenticated ones since there is no auth in this application) can open an unlimited number of concurrent SSE connections. On the target Raspberry Pi hardware this can exhaust file descriptors or memory.

**Fix:** Move `liveRoutes` inside the existing rate-limited block, or add a separate rate limiter in the second block:

```kotlin
route("/api/v1") {
    installApiRateLimiting(apiConfig.rateLimitPerMinute)
    textRoutes(screenDriverService)
    displayRoutes(screenDriverService)
    scheduleRoutes(scheduleRepository, schedulerService)
    historyRoutes(historyService)
    zoneRoutes(zoneRegistry, networkDiscoveryService, zoneRepository)
    liveRoutes(displayEventBus)
}
```

Note: SSE connections are long-lived, so a per-minute request rate limit does not bound concurrent connection count. A dedicated connection-count limit (or a separate rate limit tuned for SSE) may be needed in addition.

### WR-03: `DisplayEventBus.suspend fun emit` is dead production API — tests validate an untested code path

**File:** `src/main/kotlin/com/anjo/service/DisplayEventBus.kt:10`

**Issue:** `suspend fun emit(event: DisplayEvent)` is a public method on `DisplayEventBus` that is never called by any production code. The sole production caller, `ScreenDriverService.tryInsertHistory`, uses `tryEmit`. The only callers of `emit` are the two tests in `DisplayEventBusTest`. This means:

1. The public API surface of `DisplayEventBus` exposes a dangerous path (`emit` can suspend indefinitely) that no code currently uses but could be called by accident in a future change.
2. The `DisplayEventBusTest` tests exclusively exercise `emit` — they never cover `tryEmit`. The production path (`tryEmit` + `extraBufferCapacity = 0` + silent drop on full buffer) has zero test coverage.

**Fix:** Remove `suspend fun emit` from `DisplayEventBus`. Update `DisplayEventBusTest` to use `tryEmit` to match the production path:

```kotlin
class DisplayEventBus {
    private val _events = MutableSharedFlow<DisplayEvent>(replay = 5, extraBufferCapacity = 0)
    val events: SharedFlow<DisplayEvent> = _events
    fun tryEmit(event: DisplayEvent) = _events.tryEmit(event)
}
```

### WR-04: Two inline comments violate the project no-comments-in-code rule

**File:** `src/main/kotlin/com/anjo/di/HTTP.kt:18,21`

**Issue:** The project coding rule (recorded in project memory `feedback_coding_rules.md`) prohibits all comments in Kotlin source files — inline `//`, block `/* */`, and KDoc. `HTTP.kt` contains two inline comments:

- Line 18: `anyHost() // @TODO: Don't do this in production if possible. Try to limit it.`
- Line 21: `header("X-Engine", "Ktor") // will send this header with each response`

Both were likely pre-existing, but they remain in the files submitted for this phase and the rule applies to all reviewed files.

**Fix:** Remove both comments. If the CORS `anyHost()` restriction is actionable, track it as a planning item rather than an inline comment.

```kotlin
fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(SSE)
}
```

---

## Info

### IN-01: Unused import `kotlinx.coroutines.flow.collect` in `LiveRoutes.kt`

**File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt:9`

**Issue:** In Kotlin 2.x, `Flow.collect {}` is a member function and does not require an explicit import. `import kotlinx.coroutines.flow.collect` on line 9 is redundant and will produce an "Unused import" compiler warning.

**Fix:** Remove the import line.

### IN-02: Unused import `com.anjo.model.DisplayEvent` in `LiveRoutes.kt`

**File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt:3`

**Issue:** `DisplayEvent` does not appear as an explicit identifier in the body of `LiveRoutes.kt`. The `event` variable is typed by inference from `SharedFlow<DisplayEvent>`, and `Json.encodeToString(event)` resolves the reified type from the inferred argument type without requiring `DisplayEvent` to be in lexical scope. The Kotlin compiler will flag this as an unused import.

**Fix:** Remove the import line. Verify with `./gradlew compileKotlin` that no compile error results.

### IN-03: `LiveRoutesTest` makes no assertion about SSE event data or replay cache behaviour

**File:** `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt:13`

**Issue:** The sole test in `LiveRoutesTest` only checks that `GET /api/v1/live` returns HTTP 200 with `Content-Type: text/event-stream`. It does not:
- Emit a `DisplayEvent` through the bus and verify that the event arrives on the stream with the correct `event: display` field and JSON payload.
- Verify that a fresh client receives replayed events from the cache (`replay=5`).
- Verify heartbeat comment frames arrive within the configured period.

This leaves the core SSE data path — the only observable behaviour the feature adds — without end-to-end test coverage.

**Fix:** Add a test that wires up a `DisplayEventBus`, emits one event, and reads the raw SSE body to assert the `event: display` line and JSON payload. Ktor's `testApplication` with `prepareGet(...).execute { }` and `response.bodyChannel()` supports reading streaming responses in tests.

---

_Reviewed: 2026-06-23T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
