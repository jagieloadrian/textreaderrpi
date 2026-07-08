# Feature Landscape — TextReaderRpi v1.2

**Domain:** Embedded IoT text-display controller (Kotlin/Ktor, Raspberry Pi, home network)
**Researched:** 2026-06-21
**Confidence:** HIGH (codebase read in full; web search used for protocol/firmware patterns)
**Downstream consumer:** Requirements scoping for v1.2 roadmap — needs categories clear, complexity noted, dependencies on existing multi-zone/history system identified

---

## Context: What Already Exists (Do Not Re-Research)

All v1.0 and v1.1 features are shipped and stable. The relevant foundations for v1.2 are:

- `ZoneRegistry` — `ConcurrentHashMap<String, ZoneEntry>`, `addNetworkZone()`, `removeZone()`, `broadcast()`, `route()`
- `NetworkZoneDriver` — WebSocket client with reconnect loop (5 s backoff), heartbeat, `startConnect()`
- `ZoneRepository` — DB-persisted network zones (`NetworkZonesTable`); `upsert()` / `delete()` / `findAll()`
- `HistoryTable` — `id`, `text`, `effect`, `source`, `schedule_id`, `zone_id`, `displayed_at`, `webhook_status`; 1000-row cap
- `HistoryRepository.findPaginated()` — supports `effect` / `source` / `zone` filters via `andWhere`; no text search
- `POST /zones` — manual zone add via `AddZoneRequest(ip: String)`; persists + registers live; no restart needed
- `DELETE /zones/{id}` — removes from registry and DB; blocks local zones
- Ktor 3.5.0 — ships `ktor-server-sse` (SSE plugin) and `ktor-server-websockets`
- H2 default / PostgreSQL via env var; Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`); Flyway 9.22.3 migrations
- Material 3 SSR UI (Ktor HTML DSL, no JS framework, no HTMX)

Everything below is **additive** — new surface on the v1.1 foundation.

---

## Feature 1: WebSocket Live Feed (Real-Time Display Events in Browser)

### What This Is

The browser opens a persistent connection to the server and receives an event each time text is sent to any zone. The page shows what is currently being displayed — zone, text, effect, timestamp — without polling.

### Protocol Decision: SSE Over WebSocket for Browser Feed

For a **server-to-browser** display feed, SSE (Server-Sent Events) is the correct protocol:

- One-way push only — the browser never sends data back to the Ktor server on this channel
- Ktor 3.x ships `ktor-server-sse` (`io.ktor.server.sse`) as a first-class plugin; no extra dependencies
- SSE reconnects automatically on network drop (browser-native behavior); no client-side JS reconnect code needed
- HTTP/1.1 compatible; works through standard home-network proxies without CONNECT tunneling
- WebSocket is the right choice for the firmware channel (Pico/ESP32 → server) because the device must receive commands; the browser live feed is receive-only

The existing `NetworkZoneDriver` already uses WebSocket for device communication. The two usages are separate and complement each other.

### Table Stakes

Features users expect from any live feed. Missing = the feature does not exist.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Push event on every display dispatch | Core value; if the push is missing the feed is useless | Low | Emit to `MutableSharedFlow<LiveEvent>` at dispatch callsite in `ScreenDriverService` or `TextRoutes` |
| Event payload: text, effect, zone, timestamp | Minimum context to understand what is showing | Low | New `LiveEvent` data class; JSON-serialized |
| Browser auto-reconnects on server restart | Without this, users must manually refresh the tab | None | Native SSE browser behavior; no JS code needed |
| Current-state snapshot on connect | New tab should immediately show the last event, not wait for next dispatch | Low-Med | Replay last N=1 from `SharedFlow` replay buffer, or expose `GET /api/v1/live/current` |
| SSE endpoint: `GET /api/v1/live` | Standard REST-friendly URL for the feed | Low | `sse { }` block in Ktor routing |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Live feed widget on existing Status page | Users see real-time activity without navigating to a dedicated page | Low | Small `<div id="live-feed">` updated by 30-line vanilla JS `EventSource` snippet |
| Show last 5 events in feed (rolling log) | More context than just the current item | Low | `SharedFlow(replay = 5)` — built into kotlinx.coroutines |
| Zone badge coloring in feed | Visual scan of which zone fired | Low | CSS class per zone name; predefined palette |
| Heartbeat event every 30 s | Confirms connection is alive; browsers kill idle SSE after ~45 s on some networks | Low | Ktor SSE `heartbeat(period = 30.seconds)` |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| WebSocket for the browser feed | Overkill for one-way push; SSE is simpler and natively reconnects | SSE via `ktor-server-sse` |
| Polling from the browser (`setInterval fetch`) | Works but wastes bandwidth; misses events between polls | SSE |
| Per-zone live feeds on separate endpoints | Complicates client JS; one global feed with zone field is enough | Single feed, filter client-side |
| Store feed events in DB | History table already handles persistence; the live feed is ephemeral | SharedFlow in-memory only |
| Authentication on the SSE endpoint | Home network, trusted — consistent with all other endpoints | No auth |

### Complexity Assessment

**Overall: Low.** The `SharedFlow` broadcast pattern is standard Kotlin. The Ktor SSE plugin reduces server-side code to ~15 lines. The only non-trivial decision is where to emit: the cleanest hook is immediately after a successful `HistoryService.record()` call, which already fires on every display event.

### Dependencies on Existing System

- Emits from the same callsite that writes to `HistoryTable` — `HistoryService` or the display dispatch path
- Adds one injectable `MutableSharedFlow<LiveEvent>` to DI (`configureDI()`)
- SSE endpoint added to existing `Routing.kt` setup
- Status page HTML (`StatusPage.kt`) gets a `<div>` and ~30 lines of vanilla JS `EventSource` code

---

## Feature 2: Dynamic Zone Creation via API (Without Restart)

### What This Is

A user creates a new zone — specifying name, type (`MAX7219`/`LCD`/`OLED`), and address (IP for network zones) — via `POST /api/v1/zones` (or the existing route). The zone becomes live immediately in `ZoneRegistry` and is persisted to DB via `ZoneRepository` so it survives restart.

### Current State Analysis

The route `POST /zones` already exists and already does exactly this for network zones — it takes `{ "ip": "..." }`, calls `zoneRepository.upsert()` and `zoneRegistry.addNetworkZone()`. The feature as described in v1.2 scope extends this to:

1. Accept `name` (human-readable) and `type` fields, not just `ip`
2. Support explicit zone `id` (currently the id is set to the IP string, which is a limitation)
3. Possibly support deleting and re-adding local (SPI) zones — this is the hard part

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `POST /api/v1/zones` accepts `{ name, type, ip }` | Minimum viable zone creation request | Low | Extend `AddZoneRequest` with `name: String` and `type: String`; `id` derives from name or ip |
| Newly created zone immediately usable for `POST /api/v1/text?zone=X` | Without this, creation is meaningless | None | `ZoneRegistry.addNetworkZone()` already does this |
| Persisted to DB for restart survival | Without this, zones are lost on restart | None | `ZoneRepository.upsert()` already does this |
| `GET /api/v1/zones` returns newly created zone in list | Caller must be able to confirm creation | None | `ZoneRegistry.listAll()` already returns all registered zones |
| Conflict detection (duplicate id or IP) | Prevents silent overwrite of an active zone | Low | `zoneRegistry.contains(id)` and `containsIp(ip)` already exist |
| `DELETE /api/v1/zones/{id}` for cleanup | Symmetry — create without delete is incomplete | None | `DELETE /zones/{id}` already shipped in v1.1 |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| UI form on Zones page for zone creation | No curl required; home-lab UX | Low | Add `<form>` to `ZonesPage.kt` with name/type/ip fields |
| Zone type validation against `DisplayType` enum | Prevents registering an unknown type | Low | Server-side check; `DisplayType.entries.map { it.name }` |
| `name` field distinct from id | Currently id = ip; a human name like "kitchen" improves discoverability | Low | `AddZoneRequest` gains `name: String`; `id` can be slugified name or remain ip |
| Return 201 with full zone JSON on creation | Client immediately knows the assigned `id` | None | Already returns `zone` object in current route |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Dynamic creation of local SPI/I2C zones | Requires Pi4J hardware init at runtime; pin assignment conflicts risk crashes | Keep local zones in env-var config; restart to add hardware |
| Zone editing (PATCH /zones/{id}) | Rename without restart is unclear semantics; removes + re-adds with state loss | DELETE + POST with new data |
| Zone groups or zone aliases | Adds routing complexity; broadcast() already handles all-zones | Use broadcast or multiple POST calls |
| Config file hot-reload for zones | Two sources of truth (DB + file) for zones is a consistency trap | DB is the authoritative zone store; file only for local hardware zones on startup |

### Complexity Assessment

**Overall: Low.** The core infrastructure (`ZoneRegistry`, `ZoneRepository`, existing route) is already in place. The v1.2 work is predominantly a request model extension (`name` field) and a Zones page UI form. The only new server logic is type validation. Estimate: 1–2 days of work.

### Dependencies on Existing System

- `AddZoneRequest` model extended (`name`, `type` fields added)
- Flyway migration needed if `name` column is added to `NetworkZonesTable` (currently has `name` already — confirmed in `ZoneRepository.upsert()`)
- `NetworkZone.name` already exists in the model; the current route sets `name = req.ip` — this is the primary limitation to fix
- `DisplayType` enum used for validation

---

## Feature 3: Full-Text Search in Display History

### What This Is

`GET /api/v1/history?q=hello` returns history records where the `text` column contains "hello" (case-insensitive). The history HTML page gains a search box.

### Database Compatibility Constraint

The system supports both H2 (default) and PostgreSQL. Any search implementation must work on both.

**LIKE is the correct choice for this use case.** Rationale:

- H2's built-in FTS (`org.h2.fulltext.FullText`) is a separate API invoked via `CALL FTS_SEARCH(...)` stored procedures — not expressible in Exposed DSL; requires raw SQL and separate table initialization
- PostgreSQL FTS (`tsvector`/`tsquery`) is powerful but requires a migration, a GIN index, and Exposed `CustomFunction` wrappers; overkill for a 1000-row capped table
- LIKE with `LOWER()` is portable, Exposed-expressible (`LowerCase(HistoryTable.text) like "%${term.lowercase()}%"`), and performs acceptably on ≤1000 rows (no index needed at this scale)
- H2 performs case-insensitive LIKE natively when using `LIKE LOWER(?)` — confirmed by H2 docs

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `GET /api/v1/history?q=<term>` case-insensitive substring match on `text` column | Core of the feature; without this the feature doesn't exist | Low | `LOWER(text) LIKE LOWER('%term%')` in Exposed `andWhere` |
| Composable with existing filters (`effect`, `zone`, `source`) | Users will search + filter simultaneously | Low | `andWhere` chain — same pattern as existing filters |
| Search box on `/history` HTML page | Without UI, only API users benefit | Low | `<input name="q">` in history filter form; persists via GET param |
| Empty/blank `q` treated as no filter | Avoids breaking no-search callers | Low | `q.takeIf { it.isNotBlank() }` before adding `andWhere` |
| Results show matched term highlighted | Helps users scan results | Low-Med | Server-side HTML: wrap match in `<mark>` tag; Kotlin `replace()` with careful escaping |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Match count in page response (`matchCount`) | Shows user how many results their search returned before paginating | None | Already returned as `total` in `HistoryPageResponse` |
| Search applies before pagination | Correct behavior — search within filtered set, then paginate | Low | `WHERE` clause applied before `LIMIT`/`OFFSET` — already how Exposed works |
| Leading/trailing whitespace trim on `q` | Prevents invisible no-match due to accidental spaces | None | `q.trim()` |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| H2 FTS (`CALL FTS_SEARCH(...)`) | Not expressible in Exposed DSL; not portable to PostgreSQL | LIKE |
| PostgreSQL `tsvector` + GIN index | Requires DB-specific migration and Exposed raw SQL workarounds; overkill at 1000 rows | LIKE |
| Regex search | H2 supports `REGEXP_LIKE` but PostgreSQL syntax differs; portability breaks | LIKE |
| Client-side JS filtering of already-loaded rows | Loads all 1000 rows on every page; wastes bandwidth | Server-side `WHERE` clause |
| Saved search queries / search history | Zero demand at home-lab scale | Not needed |

### Complexity Assessment

**Overall: Low.** The `HistoryRepository.findPaginated()` already chains `andWhere` conditionals. Adding a `q: String?` parameter and one more `andWhere { LowerCase(HistoryTable.text) like "%${q.lowercase()}%" }` is ~5 lines of code. The HTML search box is ~3 lines. The only care needed is SQL injection safety — use Exposed's parameterized query API, never string interpolation in the `like` value.

### Dependencies on Existing System

- `HistoryRepository.findPaginated()` signature extended with `q: String? = null`
- `HistoryService.findPaginated()` passes through `q`
- `HistoryRoutes.kt` and `HistoryUIRoutes.kt` read `?q=` query param
- `historyPage()` template gains a search input and optional `<mark>` highlighting
- No migration needed — no schema change

---

## Feature 4: Export History to CSV

### What This Is

`GET /api/v1/history/export.csv` returns all history records (or filtered by existing params) as a downloadable CSV file. The browser triggers a file download.

### Design Decisions

**Fields to export:** All columns from `HistoryRecord`: `id`, `text`, `effect`, `source`, `schedule_id`, `zone_id`, `displayed_at`, `webhook_status`. This is the complete picture; omitting fields makes the export less useful.

**Streaming vs buffered:** At 1000-row cap the entire result set fits comfortably in memory (~100 KB worst case). Buffered response is sufficient and simpler — `respondText(csv, ContentType("text", "csv"))`. Streaming (`respondOutputStream`) is not needed until the cap is removed.

**Filename convention:** `display-history-<ISO date>.csv` e.g. `display-history-2026-06-21.csv`. RFC 4180 MIME type is `text/csv`. Content-Disposition header: `attachment; filename="display-history-2026-06-21.csv"`.

**RFC 4180 compliance:** First row is column headers. Fields containing commas, double-quotes, or newlines are quoted. Double-quotes inside fields are escaped as `""`. Line endings are `\r\n`.

**Filters:** Accept same query params as `GET /api/v1/history` (`effect`, `zone`, `source`, `q` if search is implemented). This allows exporting a filtered subset.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `GET /api/v1/history/export.csv` triggers browser download | Core behavior; without the `Content-Disposition: attachment` header, browser renders it inline | Low | Set `Content-Disposition` header in route |
| All `HistoryRecord` fields as columns | Incomplete columns make the export less useful for import into spreadsheets | Low | Header row + one CSV row per record |
| RFC 4180 quoting (escape commas and quotes in text) | Display text may contain commas; unescaped = broken CSV | Low | `text.contains(',')` → wrap in quotes; `"` → `""` |
| Filters composable with existing `effect`/`zone`/`source` params | Export should mirror what the user sees in the filtered history view | None | Reuse `HistoryService.findPaginated()` with large size |
| Date-stamped filename | Users download multiple exports; they need to distinguish files | Low | `LocalDate.now()` in filename |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| "Export CSV" button on `/history` HTML page | Discoverability; users do not have to know the API URL | Low | `<a href="/api/v1/history/export.csv?...">` with current filter params passed through |
| `q` search filter also applied to export | Consistent with filtered view | None | Same `HistoryService` call |
| Export limited to current filter (not always all 1000 rows) | Prevents accidental full dumps when user wanted a filtered view | Low | Pass filter params from button link |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Streaming chunked transfer | Unnecessary at ≤1000 rows; adds complexity | Buffered `respondText` |
| JSON export endpoint | Swagger UI already gives this for free via `GET /api/v1/history` | Not needed |
| Excel (`.xlsx`) export | Requires Apache POI or similar library; heavy dependency | CSV opens in Excel natively |
| ZIP archive of multiple exports | No user need for batch export | Single file download |
| Scheduled export / email delivery | No email stack; no remote access | Manual download only |

### Complexity Assessment

**Overall: Low.** A 30-line Kotlin function that maps `List<HistoryRecord>` to a CSV string, plus a route that sets two headers and calls `respondText`. The tricky part is RFC 4180 quoting — get it right once, add a unit test covering comma/quote/newline in text content.

### Dependencies on Existing System

- Reuses `HistoryService.findPaginated()` with `size = MAX_ROWS` and filters
- New route added to `HistoryRoutes.kt` (API) alongside existing history routes
- "Export" link added to `historyPage()` template (optional, but useful)
- No schema changes, no new dependencies

---

## Feature 5: Firmware Submodule — RPi Pico (PicoW/Pico2W) and ESP32 as Network Zones

### What This Is

A firmware program that runs on a microcontroller, connects to the home WiFi network, establishes a WebSocket connection to the TextReaderRpi server, receives `{ "text": "...", "effect": "SCROLL" }` messages, and renders the text on a locally attached display (MAX7219, OLED, LCD).

The microcontroller acts as a **network zone** — identical in the server's view to any other `NetworkZoneDriver`. The firmware is the other side of the existing `NetworkZoneDriver` WebSocket protocol.

### Kotlin Native Feasibility: NO for Pico/ESP32

Kotlin Native does not support bare-metal microcontroller targets (RP2040, ESP32). The Kotlin team explicitly classifies embedded bare-metal as very low priority. The only path for Kotlin Native on embedded exists for Linux-capable boards (Raspberry Pi OS, armv6/armv7/arm64) and selected RTOS environments (Zephyr, experimental). Pico and ESP32 are bare-metal targets — no Linux, no JVM, no Kotlin Native runtime.

**The v1.2 scope as written ("Kotlin Native WebSocket receiver") is not achievable for Pico/ESP32.** The language must be C/C++ (Arduino SDK or Pico SDK) or MicroPython.

### Recommended Language per Platform

| Platform | Recommended Language | WebSocket Library | Notes |
|----------|---------------------|-------------------|-------|
| RPi PicoW / Pico2W | C (Pico SDK) | lwIP built-in TCP + manual WS handshake, or `samjkent/picow-websocket` | MicroPython also viable but slower rendering |
| ESP32 series | C++ (Arduino) | `gilmaimon/ArduinoWebsockets` (RFC 6455, actively maintained) or ESPAsyncWebServer | Best library coverage; mature ecosystem |

### Table Stakes (Firmware UX — what a network zone firmware must do)

| Behavior | Why Required | Complexity | Notes |
|----------|--------------|------------|-------|
| Auto-connect to WiFi on boot | Without this, every restart requires manual intervention | Low | WiFi credentials in `config.h` or NVS flash |
| Connect to server WebSocket endpoint (`ws://<host>/ws`) on boot | The server's `NetworkZoneDriver` is waiting; the device must initiate | Low | Server URL in `config.h` or hard-coded |
| Reconnect automatically on WiFi drop or server restart | Home networks are unreliable; displays must recover | Low-Med | `while(!connected) { delay(5000); reconnect(); }` loop |
| Parse incoming JSON `{ "text": "...", "effect": "..." }` | Must understand the server's existing protocol | Low | `ArduinoJson` library on ESP32; manual parser on Pico |
| Render text on attached display (scroll/blink at minimum) | Without rendering, the firmware has no value | Med | MAX7219 via SPI using `MD_MAX72XX` (Arduino) or `max7219.c` (Pico SDK) |
| Render loop independent from network receive | Display must keep scrolling while waiting for next message | Med | Two FreeRTOS tasks (ESP32) or two Pico SDK cores |
| Heartbeat / ping response | Server's `NetworkZoneDriver` sends WebSocket pings; firmware must respond with pong | Low | `ArduinoWebsockets` handles this automatically |
| Status LED on WiFi/WS connect and disconnect | Visual indicator that the device is connected | Low | Built-in LED on PicoW (GP25) and ESP32 |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Effect queue — display finishes current effect before starting next | Prevents mid-scroll interruption | Med | Ring buffer of 1–2 pending messages |
| Configurable WiFi credentials + server URL via serial/flash at first boot | No firmware reflash needed to change network | Med | NVS on ESP32; LittleFS or flash on Pico |
| `mDNS` responder so server auto-discovers the device | Works with existing `NetworkDiscoveryService.scanUdp()` | Med | ESP32: `ESPmDNS`; Pico: `mdns` in lwIP |
| OLED fallback status screen (IP, connection state) when no text pending | Useful for debugging | Low | Only if OLED is attached |
| OTA firmware update via the web | Useful for fleet of devices | High | Significant complexity; defer to v1.3 |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Kotlin Native on Pico/ESP32 | Not feasible — no bare-metal support | C/C++ or MicroPython |
| The firmware acting as a WebSocket server (reverse of current protocol) | Server already acts as client (`NetworkZoneDriver`); changing the connection direction means server-side refactor | Keep current direction: device connects to server |
| Full effect pipeline (FADE) on Pico | FADE requires precise timing and PWM which conflicts with WiFi interrupt handlers on RP2040 | Implement SCROLL and BLINK first; add FADE only if timing tests pass |
| Cloud OTA or remote management | Out of scope for home-network-first project | Not needed |

### Complexity Assessment

**Overall: Medium-High for the firmware itself.** The networking and display rendering are both achievable in C/C++ with well-supported libraries, but the dual-core / dual-task model (network receive + display render simultaneously) requires care. The server side needs no changes — the existing `NetworkZoneDriver` already speaks the right protocol. This is the highest-effort v1.2 feature.

**Submodule structure recommendation:** Keep firmware in a separate directory (e.g., `firmware/pico/` and `firmware/esp32/`) within the main repo rather than a git submodule — simpler for a solo developer, no submodule sync issues.

### Dependencies on Existing System

- Server-side: no changes needed. `NetworkZoneDriver` is the server side of this connection. The device connects to `ws://<server>/ws` — but this path does not exist on the server yet. Currently the server only acts as a WebSocket **client** (`NetworkZoneDriver` connects to remote devices). A new server endpoint `GET /ws` is required so the device can connect to the server instead.
- This is a **direction reversal** for the firmware scenario: instead of server→device (current), the device connects to server. This requires a new `webSocket("/ws")` Ktor route that accepts incoming device connections, receives status/heartbeats, and sends text commands. This is new server work.
- Or: keep current direction (server connects to device), which means the device must also run a tiny WebSocket server. ESP32 with `ESPAsyncWebServer` can do this. Pico is harder. The existing architecture is server-as-client, device-as-server.

**Critical architecture clarification needed:** Decide whether device connects to server (simpler firmware, new server route needed) or server connects to device (current `NetworkZoneDriver` model, device must run a WebSocket server). The current codebase assumes the second model.

### Server-Side Work Required (if keeping current direction)

The firmware must run a WebSocket server on port 80 at path `/ws`. The Ktor server (`NetworkZoneDriver`) connects to it and sends `{ "text": "...", "effect": "..." }` frames. The device listens and renders. This is actually simpler for the device since `ESPAsyncWebServer` handles the WebSocket server role well.

---

## Feature 6: README Update

### What This Is

A rewrite of `README.md` to reflect the v1.2 state of the project: firmware submodule, new API endpoints, hardware requirements for Pico/ESP32, deployment options (Docker, K8s).

### Standard Sections for a Project Like This

Based on open-source hardware project conventions, a README for a Ktor + Raspberry Pi + microcontroller system should cover:

| Section | Content | Required |
|---------|---------|----------|
| Project badge row | Build status, coverage, license, Docker Hub | Yes |
| One-line description | What it does and why | Yes |
| Screenshot / demo GIF | Reduces time-to-understand | Strongly recommended |
| Hardware requirements | Pi model, GPIO wiring, display type, Pico/ESP32 model | Yes (hardware project) |
| Quick start | Clone → build → configure env vars → `docker run` | Yes |
| Configuration reference | All env vars with defaults | Yes |
| API reference | Link to Swagger UI + key endpoints | Yes |
| Architecture overview | How zones, drivers, WebSocket, firmware fit together | Yes |
| Firmware section | How to flash Pico/ESP32, where to set WiFi/server URL | Yes (v1.2 addition) |
| Kubernetes / Helm | How to deploy to k8s | Yes (v1.2 addition) |
| Development setup | `./gradlew build`, test, offline mode | Yes |
| Contributing | Coding rules (no comments, validators only in ScheduleValidators) | Yes |
| License | MIT or Apache | Yes |

### Table Stakes for v1.2 README

| Content | Why Required | Complexity | Notes |
|---------|--------------|------------|-------|
| Firmware flash instructions (Pico + ESP32) | Without this, users cannot use the firmware submodule | Low | Step-by-step; link to PlatformIO / Arduino IDE |
| Updated API endpoint list (includes live feed SSE, CSV export) | New endpoints are invisible without docs | Low | Markdown table with method, path, description |
| Zone creation workflow (POST /zones → display in UI) | Dynamic zone creation is a new v1.2 UX pattern | Low | Short paragraph with example curl command |
| Kubernetes / Helm section (link to `.devops/`) | K8s manifests are useless without README entry point | Low | Reference section with prerequisites |
| History search and CSV export docs | New features users must discover | Low | One paragraph each |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Auto-generated API docs in README | Swagger UI already serves this live | Link to `/swagger-ui` |
| Full code walkthrough / tutorial | Doc rot risk; wrong audience | Architecture diagram + link to source |
| Changelog in README | Hard to maintain | Move to GitHub Releases |

### Complexity Assessment

**Overall: Low.** Pure writing. The only non-trivial part is the firmware section, which must be accurate to the actual flash workflow (depends on Feature 5 being implemented first).

### Dependencies on Existing System

- Should be written last in v1.2, after all features are implemented
- Firmware section depends on Feature 5 being finalized
- K8s section depends on `.devops/` manifests existing

---

## Feature Dependency Graph

```
Feature 1 (WebSocket Live Feed)
    depends on: HistoryService (emits from same callsite) ✓ exists

Feature 2 (Dynamic Zone Creation)
    depends on: ZoneRegistry, ZoneRepository, existing POST /zones ✓ exists
    extends: AddZoneRequest model (low-risk change)

Feature 3 (Full-Text Search in History)
    depends on: HistoryRepository.findPaginated() ✓ exists
    composes with: Feature 2 (zone filter) ✓ already supported

Feature 4 (CSV Export)
    depends on: HistoryService.findPaginated() ✓ exists
    composes with: Feature 3 (search filter passed to export) — implement after F3

Feature 5 (Firmware)
    depends on: NetworkZoneDriver protocol ✓ exists (device must speak the server side)
    server-side dependency: no new server routes if device-as-server model is kept
    independent of: Features 1–4

Feature 6 (README)
    depends on: Features 1–5 implemented and stable
    write last
```

**Recommended implementation order for solo developer:**

1. Feature 3 — Full-text search (lowest complexity, high daily-use value, pure backend)
2. Feature 4 — CSV export (lowest complexity, builds on same `HistoryService`)
3. Feature 2 — Dynamic zone creation UI/model extension (existing route, mostly UI work)
4. Feature 1 — WebSocket live feed / SSE (small new DI component + SSE route + Status page widget)
5. Feature 5 — Firmware (highest complexity, independent of 1–4; can develop in parallel on a separate branch)
6. Feature 6 — README (after all features stable)

---

## MVP Recommendation for v1.2

**Ship first** (highest value-to-effort ratio):
1. Feature 3 — Full-text search (~1 day; 5 lines of repository code + search box in UI)
2. Feature 4 — CSV export (~1 day; 30-line serializer + 1 route + 1 UI link)
3. Feature 2 — Dynamic zone creation UX polish (~2 days; extend request model + add form to Zones page)

**Ship next** (moderate effort, high visibility):
4. Feature 1 — Live feed (~2–3 days; SharedFlow + SSE route + Status page JS widget)

**Longest lead time** (plan separately):
5. Feature 5 — Firmware (1–2 weeks; C/C++ on unfamiliar platforms; defer Kotlin Native claim)

**After all features stable:**
6. Feature 6 — README (1 day of writing)

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Feature 1 (Live Feed / SSE) | HIGH | Ktor 3.x SSE plugin confirmed; SharedFlow pattern standard; codebase integration point clear |
| Feature 2 (Dynamic Zone Creation) | HIGH | Infrastructure exists; work is model + UI extension; `name` field gap confirmed from code |
| Feature 3 (Full-Text Search) | HIGH | LIKE approach confirmed portable across H2 and PostgreSQL at 1000-row scale; Exposed DSL supports it |
| Feature 4 (CSV Export) | HIGH | Standard HTTP pattern; RFC 4180 well-specified; no library dependency needed |
| Feature 5 (Firmware) | MEDIUM | C/C++ ecosystem confirmed; Kotlin Native infeasibility confirmed; exact render loop timing is hardware-dependent and untested |
| Feature 6 (README) | HIGH | Standard sections are well-established; content depends on Feature 5 outcome |

---

## Sources

- Ktor 3.x SSE documentation: [Server-Sent Events in Ktor Server](https://ktor.io/docs/server-server-sent-events.html)
- SSE vs WebSocket comparison: [WebSocket vs SSE: Which One Should You Use?](https://websocket.org/comparisons/sse/)
- H2 case-insensitive LIKE: [Case insensitive LIKE query — H2 Google Group](https://groups.google.com/g/h2-database/c/0UnJGy9i9X0)
- H2 FTS API: [Class FullText — H2 Javadoc](https://www.h2database.com/javadoc/org/h2/fulltext/FullText.html)
- RFC 4180 CSV format: [RFC 4180](https://www.ietf.org/rfc/rfc4180.txt)
- Ktor CSV serialization: [Serialize and Deserialize CSV with Ktor](https://mojoauth.com/serialize-and-deserialize/serialize-and-deserialize-csv-with-ktor)
- Kotlin Native embedded status: [Kotlin Discussions — embedded status](https://discuss.kotlinlang.org/t/what-is-the-current-status-of-kotlin-native-on-embedded-devices/13991)
- Kotlin Native bare-metal: [Kotlin Slack — embedded arm targets](https://slack-chats.kotlinlang.org/t/5114093/does-kotlin-native-support-embedded-arm-targets-i-m-guessing)
- Pico WebSocket client: [samjkent/picow-websocket](https://github.com/samjkent/picow-websocket)
- ESP32 WebSocket library: [gilmaimon/ArduinoWebsockets](https://github.com/gilmaimon/ArduinoWebsockets)
- ESP32 WebSocket guide: [ESP32 and ESP8266 WebSocket Guide](https://tests.ws/guides/esp32-websocket)
- Kotlin SharedFlow: [SharedFlow — kotlinx.coroutines](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/)
- README best practices: [jehna/readme-best-practices](https://github.com/jehna/readme-best-practices)
- Codebase: `/home/diether18/IdeaProjects/TextReaderRpi/src/main/kotlin/com/anjo/` — read in full for v1.2 research
