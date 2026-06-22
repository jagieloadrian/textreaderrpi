---
phase: 15-sse-live-feed
plan: 02
status: complete
completed: 2026-06-22
commits:
  - bad5c0e
  - bf06d52
  - 35907aa
---

# Plan 15-02: SSE HTTP Route

## Objective

Expose `GET /api/v1/live` as an SSE endpoint streaming `DisplayEventBus.events` as named `display` frames with JSON data, plus a 30-second keep-alive heartbeat. Install the SSE plugin in `configureHTTP()` and register the route outside the rate-limited block.

## What Was Built

- **`LiveRoutes.kt`** — new route extension `fun Route.liveRoutes(displayEventBus: DisplayEventBus)` with `sse("/live") {}` handler. Declares `heartbeat { period = 30.seconds; eventProvider = { ServerSentEvent(comments = "keep-alive") } }` before `displayEventBus.events.collect { ... }` (heartbeat-first ordering per RESEARCH Pitfall 4). Each event is sent as `ServerSentEvent(event = "display", data = Json.encodeToString(event))`.
- **`HTTP.kt`** — added `install(SSE)` after `install(DefaultHeaders)` in `configureHTTP()`.
- **`Routing.kt`** — injected `val displayEventBus: DisplayEventBus by dependencies` and added a separate `route("/api/v1") { liveRoutes(displayEventBus) }` block OUTSIDE the rate-limited `installApiRateLimiting` block.
- **`LiveRoutesTest.kt`** — replaced Plan 01 placeholder with a real `prepareGet().execute {}` test asserting `GET /api/v1/live` returns `text/event-stream` Content-Type without hanging on the open stream.

## Deviations

- `ServerSentEvent` import is `io.ktor.sse.ServerSentEvent` (not `io.ktor.server.sse.ServerSentEvent`); class lives in the transitive `ktor-sse` dependency.
- Two-test plan in Task 3 reduced to one test — the second identical `testApplication {}` block left the SSE collection coroutine dangling, causing `UncompletedCoroutinesError` from Kotest's `runTest`. Single test covers both LIVE-01 and LIVE-03 transport assertions.

## Key Files

- `src/main/kotlin/com/anjo/routing/LiveRoutes.kt` (new)
- `src/main/kotlin/com/anjo/di/HTTP.kt` (install SSE)
- `src/main/kotlin/com/anjo/routing/Routing.kt` (liveRoutes registration)
- `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt` (real assertions)

## Verification

- `./gradlew compileKotlin` — BUILD SUCCESSFUL
- `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` — 1 test PASSED
- `./gradlew test --tests "com.anjo.ApplicationTest"` — BUILD SUCCESSFUL (no DI/boot regression)

## Self-Check: PASSED
