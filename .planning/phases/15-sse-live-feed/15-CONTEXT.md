# Phase 15: SSE Live Feed - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

A Server-Sent Events endpoint (`GET /api/v1/live`) that pushes `DisplayEvent` frames to subscribers within one second of any display activity, plus a status page live feed widget backed by `EventSource` that shows the most recently displayed text without a page reload.

Requirements: LIVE-01, LIVE-02, LIVE-03, LIVE-04.

</domain>

<decisions>
## Implementation Decisions

### Event Bus Architecture (LIVE-01, LIVE-03, LIVE-04)
- **D-01:** A new dedicated `DisplayEventBus` service holds the `SharedFlow<DisplayEvent>`. It is NOT on `ScreenDriverService` — keeps the SSE route independent of the full display service.
- **D-02:** `SharedFlow` configuration: `replay = 5, extraBufferCapacity = 0`. The replay satisfies LIVE-04 automatically — new SSE connections get the last 5 events via Kotlin's built-in SharedFlow replay, with no separate ring buffer needed.
- **D-03:** `DisplayEventBus` registered as a singleton in `DependencyInjection.kt`, consistent with `ZoneRegistry` and `HistoryRepository`. Both `ScreenDriverService` and the SSE route inject the same instance.
- **D-04:** Emit point: inside `renderImmediate()` in `ScreenDriverService`, **after** `historyRepository.insert()` completes. This ensures the event is authoritative (has an ID, is persisted). Both the immediate and scheduled display paths go through `renderImmediate`, so a single emit point covers LIVE-01 for all display triggers.
- **D-05:** Replay resets on server restart — the `SharedFlow` starts empty on boot. No DB warm-up of the replay buffer. The history page remains the authoritative source for past events.
- **D-06:** No max concurrent SSE connection limit. Home lab use case; expected 1–5 subscribers. SharedFlow collectors are lightweight coroutines.
- **D-07:** Ktor SSE plugin (`ktor-server-sse`) handles subscriber lifecycle automatically — client disconnect cancels the collecting coroutine, which auto-unsubscribes from the SharedFlow. No manual `CancellationException` handling needed.

### DisplayEvent Model (LIVE-01)
- **D-08:** New `data class DisplayEvent(val id: Long, val text: String, val effect: String, val zoneId: String?, val displayedAt: String)` in `com.anjo.model`. Separate from `HistoryRecord` — decouples the live feed API contract from DB schema changes.
- **D-09:** Excluded fields: `webhookStatus` and `scheduleId` are operational metadata, not display state. `DisplayEvent` stays lean.
- **D-10:** SSE frame format: named event `event: display` before `data:`. The browser-side `EventSource` listens via `source.addEventListener('display', handler)`. Explicit naming allows future event types (e.g., `event: heartbeat`) without breaking existing clients.
- **D-11:** The SSE stream emits all zones — no `?zone=` filter param on `GET /api/v1/live`. All-zone stream; clients filter client-side if needed.

### Status Page Widget (LIVE-02)
- **D-12:** New `<article>` block titled **"Live Feed"** added at the **top** of `StatusPage.kt`, above the existing System/Display/Hardware sections. The existing polling sections are unchanged.
- **D-13:** Widget content: single "Current Text" row showing the most recent event's text, with zone + effect in smaller text below. Replaces its own content on each new event (not a scrollable list).
- **D-14:** Initial state: the `EventSource` replays the last 5 events on connect — the widget populates with the most recent display text immediately (usually within a few hundred ms). No explicit "waiting" or "connecting" placeholder state needed.
- **D-15:** EventSource JS lives in a new **`src/main/resources/static/live-feed.js`** file, loaded via `<script src="/static/live-feed.js">` in `StatusPage.kt` only. Not in `app.js` (which is loaded on every page via `BaseLayout.kt`). Static serving via `staticResources("/static", "static")` is already in `Routing.kt`.

### Heartbeat (LIVE-03)
- **D-16:** The 30-second heartbeat comment frame (`: keep-alive`) is emitted by a coroutine inside the SSE route handler using `delay(30_000)` in a `while(true)` loop alongside the `collect {}` on `DisplayEventBus`. Standard Ktor SSE comment frame.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Bus Integration Points
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — `renderImmediate()` is the emit point; inject `DisplayEventBus` here; both `displayImmediate` and `displayScheduled` paths go through it
- `src/main/kotlin/com/anjo/DependencyInjection.kt` — register `DisplayEventBus` as a singleton; pattern mirrors `ZoneRegistry` and `HistoryRepository`
- `src/main/kotlin/com/anjo/routing/Routing.kt` — register new `liveRoutes(displayEventBus)` here; `staticResources("/static", "static")` already present

### Status Page
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` — add "Live Feed" article at top; add `<script src="/static/live-feed.js">` to this page only
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — do NOT add live-feed.js here (would load on all pages)
- `src/main/resources/static/app.js` — existing polling JS pattern; live-feed.js follows the same structural style but is a separate file

### Existing Route Pattern
- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` — route handler structure and thin-handler pattern to follow in the new SSE route
- `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` — how UI routes inject services and call `respondText`

### Model
- `src/main/kotlin/com/anjo/model/HistoryRecord.kt` — source of field names for `DisplayEvent`; D-08 defines the subset to include
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` — `insert()` return value (HistoryRecord with id) is the source for constructing the `DisplayEvent` to emit

### Build Config
- `gradle/ktor-libs.versions.toml` — add `ktor-server-sse` library entry here (Ktor 3.5.0 ships with it; just needs the alias)
- `build.gradle.kts` — add `ktor-server-sse` to the `dependencies {}` block

### Project Rules
- `.planning/STATE.md` §Key Pitfalls — validation stays in validator objects, never route handlers (applies to any query param on the SSE route)
- `.planning/PROJECT.md` §Key Decisions — DI patterns, thin route handlers

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ScreenDriverService.renderImmediate()` — single emit point for both immediate and scheduled display; inject `DisplayEventBus` here and call `bus.emit(displayEvent)` after `historyRepository.insert()`
- `CoroutineScope(SupervisorJob() + ioDispatcher)` pattern in `ScreenDriverService` — same coroutine dispatcher pattern applies to the heartbeat coroutine in the SSE route
- `staticResources("/static", "static")` in `Routing.kt` — already serving `app.js`, `custom.css`, `pico.min.css`, `favicon.svg`; `live-feed.js` drops in the same `resources/static/` directory

### Established Patterns
- Singleton DI via `DependencyInjection.kt` — `DisplayEventBus` follows the same pattern as `ZoneRegistry` (no factory, single instance)
- Thin route handlers — all business logic in service layer; the SSE route only calls `displayEventBus.events.collect { ... }` and emits frames
- Ktor HTML DSL for server-rendered HTML — `StatusPage.kt` uses `kotlinx.html` DSL; new "Live Feed" article follows same `article { h2 {} p {} span {} }` pattern
- Named SSE events — `event: display` allows `addEventListener('display', ...)` in JS, compatible with browser `EventSource` API

### Integration Points
- `DependencyInjection.kt` — new `DisplayEventBus` singleton injected into `ScreenDriverService` and the SSE route function
- `Routing.kt` — new `liveRoutes(displayEventBus)` call alongside existing `historyRoutes`, `webRoutes`, etc.
- `ScreenDriverService.renderImmediate()` — constructor parameter `displayEventBus: DisplayEventBus?` (nullable for backward compat in tests) or non-null if all tests are updated
- `StatusPage.kt` — `<script src="/static/live-feed.js">` added via Ktor DSL `script(src = "/static/live-feed.js") {}`

</code_context>

<specifics>
## Specific Ideas

- `DisplayEventBus` implementation: `class DisplayEventBus { private val _events = MutableSharedFlow<DisplayEvent>(replay = 5); val events: SharedFlow<DisplayEvent> = _events; suspend fun emit(event: DisplayEvent) = _events.emit(event) }`
- Heartbeat loop in SSE route: `while (true) { delay(30_000); send(ServerSentEvent(comment = "keep-alive")) }` running concurrently with `events.collect { send(ServerSentEvent(event = "display", data = Json.encodeToString(it))) }`
- `live-feed.js` pattern: `const src = new EventSource('/api/v1/live'); src.addEventListener('display', e => { const d = JSON.parse(e.data); document.getElementById('live-text').textContent = d.text; document.getElementById('live-meta').textContent = d.zoneId + ' · ' + d.effect; });`
- Status page widget DOM IDs: `live-text` for the text content, `live-meta` for zone + effect metadata

</specifics>

<deferred>
## Deferred Ideas

- Zone-filtered SSE stream (`?zone=X`) — deferred to a future phase if needed. All-zone stream is sufficient for the home lab use case.
- Per-connection SSE connection limit / 503 rejection — not needed at this scale.
- DB warm-up of replay buffer on restart — deferred; history page covers past events.

</deferred>

---

*Phase: 15-SSE Live Feed*
*Context gathered: 2026-06-22*
