# Phase 11: Multi-Zone Displays - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Register and route text to multiple named display zones — both locally-attached (SPI/I2C on the same Pi) and network-discovered (external devices on the home LAN). The Pi side of WebSocket communication is implemented; external device firmware is out of scope. All 8 ZONE requirements land in this phase.

</domain>

<decisions>
## Implementation Decisions

### Local Zone Configuration

- **D-01:** YAML zones list — `display.zones: [{id, type, numDevices, gpioPins, ...}]`. New `ZonesConfig` data class replaces the single `DisplayConfig` block. Existing single-zone config paths are migrated.
- **D-02:** When `POST /api/v1/text` is called without a `?zone=` param, broadcast to ALL zones simultaneously — both local (SPI/I2C) and network. Parallel coroutines, best-effort.
- **D-03:** Per-zone SPI pin configuration follows Pi4J 4.0.0 SPI API constraints — researcher must investigate how multiple SPI zones coexist on the same bus. Config structure adapts to what Pi4J supports.
- **D-04:** A zone that fails hardware init at startup is registered as OFFLINE. The application starts normally; other zones operate. Consistent with the existing `OfflineDisplayDriver` pattern.
- **D-05:** Zone status in `GET /api/v1/zones` is dynamic — based on last render attempt: `ONLINE` (last render succeeded), `DEGRADED`/`OFFLINE` (last render threw exception).
- **D-06:** `POST /api/v1/text?zone=X` where zone X is `OFFLINE` → `503 Service Unavailable`. Zone exists but hardware is unavailable.
- **D-07:** Mixed hardware types supported in one zones list — e.g., zone `main`=MAX7219 and zone `status`=OLED on the same Pi.

### Network Zones

- **D-08:** All 8 ZONE requirements (ZONE-01 through ZONE-08) land in Phase 11 — no split.
- **D-09:** Pi maintains WebSocket connections to external displays (Pi is the WebSocket client connecting to devices). External firmware (ESP32/Arduino) is out of scope — deferred to v1.2+ reference implementation (already in REQUIREMENTS.md Future section).
- **D-10:** Heartbeat via periodic WebSocket ping frames. If no pong within timeout → zone marked `OFFLINE`.
- **D-11:** Text routed to an offline external zone → `503 Service Unavailable`, log it, no retry. Consistent with local OFFLINE zone behavior (D-06).
- **D-12:** Manually-added zones (ZONE-06, user adds external display by IP) are persisted in a new `network_zones` DB table (H2/PostgreSQL via Exposed). Zones survive Pi restarts and auto-reconnect on boot.
- **D-13:** Auto-discovered zones (via UDP/mDNS) are also persisted in the same `network_zones` table — single source of truth for all external zones.
- **D-14:** No-zone broadcast targets ALL zones — local + network.

### ZoneRegistry Architecture

- **D-15:** New `ZoneRegistry` service holds `Map<String, ZoneDriver>`. `ScreenDriverService` receives a `ZoneRegistry` instead of a single `DisplayDriver`. Clean separation: registry owns zone map, service owns rendering pipeline.
- **D-16:** New `ZoneDriver` interface — `send(text: String, effect: Effect): Boolean`. `LocalZoneDriver` wraps `DisplayDriver`. `NetworkZoneDriver` wraps a WebSocket client connection. `ZoneRegistry` routes to any `ZoneDriver` transparently.
- **D-17:** Every zone call goes through the full `ScreenDriverService` pipeline (mutex per zone, history recording, metrics, retry, conflict policy). A `zoneId: String` param is added to `displayImmediate()` and `displayScheduled()`.
- **D-18:** Broadcast (no zone param) fires to all zones in parallel (coroutines). Response aggregates results: `{successful: ["main", "status"], failed: [{"kitchen", "OFFLINE"}]}`. Best-effort — one failed zone does not block others.

### Network Discovery Protocol

- **D-19:** Two-track discovery: UDP broadcast scan (on-demand, triggered by `POST /api/v1/zones/discover`) + mDNS passive background listener (continuous coroutine).
- **D-20:** `POST /api/v1/zones/discover` — Pi sends a UDP broadcast packet on port **54321**, waits **3 seconds** for replies, registers new devices, returns newly found zones. This is the endpoint the Phase 13 UI button will call.
- **D-21:** UDP discovery payload: Pi broadcasts `{"type":"TEXTREADERRPI_DISCOVER"}`. Device replies with `{"name":"kitchen","ip":"192.168.1.50","type":"MAX7219"}`. Fixed port 54321, JSON, no dependency.
- **D-22:** mDNS passive listener runs as a background coroutine for the lifetime of the application. Listens for `_textreaderrpi._tcp.local` service announcements. New device powers on → Pi detects it within seconds without any button press. Requires `jmdns` JVM library.
- **D-23:** Discovery scan timeout: 3 seconds (hardcoded). Matches ZONE-03 "appear in zones list within 30 seconds" with room for WebSocket setup time.

### Claude's Discretion
- WebSocket ping interval for heartbeat (D-10) — planner picks a reasonable default (e.g. 15s ping, 30s timeout).
- `network_zones` table schema — planner designs based on Exposed patterns from `SchedulesTable` and `HistoryTable`.
- How `jmdns` is wired into the Ktor lifecycle — follow Ktor `ApplicationStarted`/`ApplicationStopping` subscription pattern used by `SchedulerService`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Multi-Zone Displays — ZONE-01 through ZONE-08 (8 requirements, all must be satisfied)

### Existing Architecture to Extend
- `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` — Interface that `LocalZoneDriver` will wrap
- `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt` — Base class pattern; `LocalZoneDriver` may extend or delegate
- `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` — Current single-driver selection service; will be superseded by `ZoneRegistry` (or renamed/replaced)
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — Add `zoneId: String` param to `displayImmediate()` and `displayScheduled()`; inject `ZoneRegistry`
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — Register `ZoneRegistry`, `NetworkDiscoveryService`, `ZoneRepository`; wire into `ScreenDriverService`
- `src/main/kotlin/com/anjo/config/model/DisplayConfig.kt` — Replace single display block with `ZonesConfig` list structure
- `src/main/resources/application.yaml` — Replace `display:` block with `display.zones:` list

### Database Patterns (follow exactly)
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` — Exposed Table pattern for `network_zones` table
- `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` — Repository pattern for `ZoneRepository`
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` — Where to add `NetworkZonesTable` to schema init

### Existing Zone-Related Fields (already in place, no migration needed)
- `src/main/kotlin/com/anjo/model/Schedule.kt` — `zoneId: String?` already present
- `src/main/kotlin/com/anjo/db/HistoryTable.kt` — `zoneId` column already present
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — `zoneId: Int` param already used for Pi4J `id("max7219-zone-$zoneId")` uniqueness

### Pi4J SPI (researcher must investigate)
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — Pi4J SPI config builder usage; researcher must verify how multiple SPI zones coexist (different chip selects or buses)

### Pattern Files
- `src/main/kotlin/com/anjo/service/WebhookService.kt` — Pattern for `CoroutineScope(Dispatchers.IO + SupervisorJob())` for background IO (apply to `NetworkDiscoveryService`)
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — Pattern for `ApplicationStarted`/`ApplicationStopping` lifecycle hooks
- `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` — Config data class pattern; follow for new zone config classes

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Max7219Matrix(ctx, numDevices, zoneId = 0)` — already takes `zoneId: Int` for Pi4J ID uniqueness; extend to use per-zone config values
- `HistoryTable.zoneId` column — already exists; history recording needs `zoneId: String` plumbed through from `displayImmediate`/`displayScheduled`
- `SchedulesTable.zoneId` — already present; schedule routing to zones requires no DB migration
- `OfflineDisplayDriver` — can serve as OFFLINE fallback for zones that fail to init; LocalZoneDriver wraps it when hardware unavailable
- `CoroutineScope(Dispatchers.IO + SupervisorJob())` — established pattern for background IO; NetworkDiscoveryService follows this
- Pi4J single `pi4jContext` in `DependencyInjection.kt` — shared context for all local zones (already the plan from STATE.md)

### Established Patterns
- `${VAR:default}` env var pattern — all 25 existing settings; discovery port could be `${DISCOVERY_PORT:54321}`
- Ktor `ApplicationStarted`/`ApplicationStopping` subscription — used by `SchedulerService`; `NetworkDiscoveryService` follows same pattern
- Exposed 1.3.0 `org.jetbrains.exposed.v1.*` — `ZoneRepository` follows same imports as `ScheduleRepository`
- Kotest `should` convention — all new tests follow this style
- No comments in code files (project rule)
- Validation stays in route handlers; never in service or repository

### Integration Points
- `DependencyInjection.kt` `configureDI()` — new `provide<ZoneRegistry>{}`, `provide<NetworkDiscoveryService>{}`, `provide<ZoneRepository>{}` bindings
- `ScreenDriverService.displayImmediate(text, effect, conflictPolicy, zoneId)` — new `zoneId: String = "broadcast"` param
- `TextRoutes.kt` — parse `?zone=X` query param; pass to `ScreenDriverService`
- `DatabaseFactory.init()` — add `NetworkZonesTable` to `SchemaUtils.createMissingTablesAndColumns()`
- New Flyway migration — add `network_zones` table (V4 migration)
- `Routing.kt` — register new zone routes: `GET /api/v1/zones`, `POST /api/v1/zones/discover`, `POST /api/v1/zones/{ip}` (manual add)

</code_context>

<specifics>
## Specific Ideas

- Discovery triggered by UI button in Phase 13 calls `POST /api/v1/zones/discover`. Phase 11 must deliver this endpoint so Phase 13 can wire the button.
- UDP discovery port: **54321** (fixed). Pi broadcasts `{"type":"TEXTREADERRPI_DISCOVER"}`, device replies with `{"name":"...", "ip":"...", "type":"MAX7219"}`.
- mDNS service name: `_textreaderrpi._tcp.local` — both sides (Pi listener + future device firmware) agree on this string.
- Broadcast response JSON shape: `{"successful": ["main", "secondary"], "failed": [{"zoneId": "kitchen", "reason": "OFFLINE"}]}`.
- WebSocket heartbeat: planner picks interval (suggested: 15s ping, 30s no-pong timeout → OFFLINE).
- `network_zones` table columns: `id VARCHAR PK`, `name VARCHAR`, `ip VARCHAR`, `type VARCHAR`, `discoveryMethod (MANUAL|UDP|MDNS)`, `status (ONLINE|OFFLINE)`, `lastSeenAt VARCHAR`, `createdAt VARCHAR`.

</specifics>

<deferred>
## Deferred Ideas

- **External device firmware** (ESP32/Arduino implementation) — already in REQUIREMENTS.md Future (v1.2+). Phase 11 only implements Pi server side.
- **UI button for discovery** — Phase 13 (UI/UX Refresh) will add the "Scan for displays" button on the `/zones` page that calls `POST /api/v1/zones/discover`.
- **Dynamic zone creation via API without restart** — already listed in REQUIREMENTS.md Future (v1.2+). Phase 11 requires restart to pick up new local YAML zones (network zones are dynamic via discovery).

</deferred>

---

*Phase: 11-Multi-Zone Displays*
*Context gathered: 2026-06-16*
