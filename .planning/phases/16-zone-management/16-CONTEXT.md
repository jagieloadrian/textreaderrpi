# Phase 16: Zone Management - Context

**Gathered:** 2026-06-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Two capabilities delivered together:

1. **ZONE-09 — Dynamic zone creation via form:** Users can add new network or firmware zones through the `/zones` UI page without restarting the server. New `DisplayType.FIRMWARE` enum value. Zone name becomes the routing ID (`?zone=<name>`). 422 for local hardware types (MAX7219, LCD, OLED) — those require Pi SPI/I2C hardware and must be configured at startup via YAML.

2. **ZONE-10 — Firmware inbound WebSocket endpoint:** A new `GET /ws/zone/{id}` endpoint accepts inbound WebSocket connections from Pico/ESP32 firmware devices. A new `FirmwareZoneDriver` manages each connected device session. When the device connects, the zone goes ONLINE; when it disconnects, OFFLINE. Text sent to that zone ID is pushed to the connected device as JSON over the open WS session.

Requirements: ZONE-09, ZONE-10.

</domain>

<decisions>
## Implementation Decisions

### Zone Creation Form (ZONE-09)

- **D-01:** Zone `name` is the **zone routing ID** — what users pass in `?zone=<name>`. Not a display label. This changes the current IP-as-ID scheme for manually added zones.
- **D-02:** New `DisplayType.FIRMWARE` enum value added. This is the zone category for Pico/ESP32 inbound WS devices. Form shows this plus any remote display subtype.
- **D-03:** Firmware zones have no IP field (device connects inbound). Network zones (outbound WS to another RPi) require an IP. The form adapts based on zone type selection.
- **D-04:** Display subtype for firmware zones is a **free-text field** (e.g. "MAX7219", "SSD1306", "custom"). External microcontrollers can have any display — not limited to the 3 existing enum values. Stored as-is in DB as metadata.
- **D-05:** **422 validation rule:** Reject `POST /zones` when `type` is a local hardware type (MAX7219, LCD, OLED, UNKNOWN) submitted as a new zone. These require Pi4J hardware initialization at startup; they cannot be created at runtime. Validation lives in a new `ZoneValidators` object — never inline in the route handler (project rule).
- **D-06:** Non-atomic check-then-insert race: use `ConcurrentHashMap.compute()` in `ZoneRegistry` to atomically check + insert. Known pitfall from STATE.md.

### FirmwareZoneDriver Lifecycle (ZONE-10)

- **D-07:** **Pre-register via form first.** User creates a FIRMWARE zone via the `/zones` form (assigns the ID, e.g. `pico-salon`). The zone exists as OFFLINE in `ZoneRegistry` before any device connects. Firmware is flashed with that ID in its config.
- **D-08:** When firmware opens `GET /ws/zone/{id}`, the server looks up the zone in `ZoneRegistry` by ID. If found and type=FIRMWARE → accepts WS, creates `FirmwareZoneDriver` session, zone goes ONLINE. If zone not found or not FIRMWARE type → 404.
- **D-09:** On disconnect, zone transitions back to OFFLINE. Subsequent sends to that zone return a graceful error (not a crash). The zone entry remains in `ZoneRegistry` (OFFLINE), ready for firmware to reconnect.
- **D-10:** **Thread safety:** `FirmwareZoneDriver.send()` must NOT call `DefaultWebSocketSession.send()` directly from the display coroutine. Use a `Channel<String>` per session; `send()` calls `channel.trySend()` only; a separate coroutine drains the channel into the WS. (STATE.md pitfall #1.)

### WebSocket Message Format (ZONE-10)

- **D-11:** Server pushes JSON to firmware on each `send()` call:
  ```json
  {"text": "...", "effect": "SCROLL", "zoneId": "pico-salon", "ts": "2026-06-23T12:00:00Z"}
  ```
  This is the locked wire contract. Phase 17 firmware must parse this exact structure. `ts` = ISO-8601 UTC timestamp of the display event.

### Zone Type Architecture

- **D-12:** There are now **three zone categories** in the system:
  1. **Local** — Pi hardware (MAX7219/LCD/OLED via SPI/I2C). Config-only at startup. Not creatable via API.
  2. **Network** — Another TextReaderRpi node on the network. `NetworkZoneDriver` connects outbound WS to their `/ws` endpoint. Discovered via UDP scan or added manually by IP.
  3. **Firmware** — Pico/ESP32 microcontroller. `FirmwareZoneDriver` accepts inbound WS on `/ws/zone/{id}`. Device finds the server (hardcoded IP in firmware). NOT discovered via UDP scan.
- **D-13** [informational]: Autodiscovery (UDP scan / `NetworkDiscoveryService`) works only for Network zones (other TextReaderRpi nodes that respond to UDP broadcasts). Firmware devices do not respond to UDP — they connect directly. No autodiscovery for firmware zones.

### Claude's Discretion

- 422 validation detail: which exact `DisplayType` values are "local-only" — implementation should treat MAX7219, LCD, OLED, UNKNOWN as local-hardware-only (require Pi hardware init). FIRMWARE and any future network-only types pass.
- FirmwareZoneDriver session management: how many concurrent connections per zone ID (likely 1 — last-wins replaces previous session).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Zone Infrastructure
- `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` — `addNetworkZone()`, `removeZone()`, `register()`, `containsIp()`, `contains()`. D-06: replace check-then-insert with `compute()`. New `registerFirmwareZone()` method needed.
- `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` — interface `send()`, `status()`, `stop()`. `FirmwareZoneDriver` implements this.
- `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt` — outbound WS zone driver. `FirmwareZoneDriver` is the inbound counterpart; same interface, opposite WS direction.
- `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` — local zone driver pattern for reference.

### Zone API & UI
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` — existing `POST /zones`, `DELETE /zones/{id}`, `POST /zones/discover`. `POST /zones` needs: name field, type field, firmware vs network branching, 422 for local types.
- `src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt` — renders `/zones` page. May need update to pass form fields.
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` — "Add Display by IP" form (IP only today). Needs: name input, type selector (FIRMWARE / network display types), conditional IP field, display subtype free-text for firmware.
- `src/main/kotlin/com/anjo/model/AddZoneRequest.kt` — currently `data class AddZoneRequest(val ip: String)`. Needs: `name: String`, `type: String`, `ip: String?` (nullable for firmware), `displaySubtype: String?`.

### Models & DB
- `src/main/kotlin/com/anjo/model/NetworkZone.kt` — existing network zone model. Firmware zones use same model; `type = "FIRMWARE"`, `ip = null`.
- `src/main/kotlin/com/anjo/model/DisplayType.kt` — add `FIRMWARE` enum value here.
- `src/main/kotlin/com/anjo/db/ZoneRepository.kt` — `upsert()`, `findAll()`, `findById()`, `delete()`. Firmware zones stored here with `type = "FIRMWARE"`.

### DI & Routing
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — WS endpoint needs `ZoneRegistry` injected. Pattern mirrors `displayRoutes(screenDriverService)`.
- `src/main/kotlin/com/anjo/routing/Routing.kt` — register new `firmwareZoneRoutes(zoneRegistry)` here. Note: WS routes must be outside the rate-limited block (long-lived connections — same reason as SSE in Phase 15).

### Build Config
- `build.gradle.kts` — `io.ktor:ktor-server-websockets` already listed in STATE.md new dependencies for v1.2 (Phase 16). Confirm it's present or add.
- `gradle/ktor-libs.versions.toml` — check for `ktor-server-websockets` alias.

### Pitfalls (from STATE.md)
- `.planning/STATE.md` §Key Pitfalls — pitfall #1 (WS send not thread-safe) and pitfall #2 (non-atomic check-then-insert in ZoneRegistry). Both apply directly to this phase.
- `.planning/PROJECT.md` §Key Decisions — thin route handlers, validation in `*Validators` objects only.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ZoneRegistry.addNetworkZone(zone, client)` — pattern for registering a driver into `zones` map. `registerFirmwareZone(id)` follows same pattern, but stores an OFFLINE `FirmwareZoneDriver` placeholder instead of `NetworkZoneDriver`.
- `NetworkZoneDriver` Channel-based send pattern (if it uses one) — `FirmwareZoneDriver` must use `Channel<String>` per session for thread-safe WS sends from display coroutine.
- `ZonesPage.kt` "Add Display by IP" form — extend with name/type/subtype fields. Keep existing structure.
- `ScheduleValidators` / `HistoryValidators` — naming and structure pattern for new `ZoneValidators`.

### Established Patterns
- **Thin route handlers:** `ZoneRoutes.kt` calls `zoneRegistry.addNetworkZone()` and `zoneRepository.upsert()`. New firmware branch follows same structure — no business logic inline.
- **Validation in `*Validators`:** 422 logic for local types goes in `ZoneValidators`, not in the route handler.
- **WS outside rate limiter:** Phase 15 established that long-lived connections go in a separate `route("/api/v1") { ... }` block outside `installApiRateLimiting`. Same applies to `/ws/zone/{id}`.
- **DI smoke test pattern:** `DependencyInjection.kt` → all new bindings need a smoke test entry.

### Integration Points
- `GET /ws/zone/{id}` → `FirmwareZoneDriver` session → `ZoneRegistry` status update (ONLINE/OFFLINE)
- `POST /zones` with `type=FIRMWARE` → `ZoneRepository.upsert()` + `ZoneRegistry.registerFirmwareZone(id)` (OFFLINE placeholder)
- `ScreenDriverService.displayImmediate(text, effect, zoneId)` → `ZoneRegistry.route(zoneId)` → `FirmwareZoneDriver.send()` → `channel.trySend(json)`
- `ZonesUIRoutes.GET /zones` — must show firmware zones with FIRMWARE type badge + connection status

</code_context>

<specifics>
## Specific Ideas

- `FirmwareZoneDriver` holds a `Channel<String>(capacity = 64)` and an `AtomicReference<DefaultWebSocketSession?>`. When firmware connects, WS session stored in AtomicReference; drain coroutine started. On disconnect, AtomicReference set to null, zone status = OFFLINE.
- Wire contract JSON: `{"text": "hello", "effect": "SCROLL", "zoneId": "pico-salon", "ts": "2026-06-23T12:00:00Z"}` — `kotlinx.serialization` data class, serialized with `Json.encodeToString()`.
- `ZoneValidators.validateAddZone(req: AddZoneRequest): ValidationResult` — checks: name not blank, type not in localHardwareTypes set, IP present if not FIRMWARE type.
- `DisplayType.FIRMWARE` added to enum — used as the zone category; `displaySubtype` (free text) stored separately in `NetworkZone.type` field or a new column.

</specifics>

<deferred>
## Deferred Ideas

- Zone-filtered SSE stream for firmware zones — deferred (same as for network zones, future phase if needed).
- Firmware autodiscovery via UDP — not applicable; firmware devices connect inbound. Noted for clarity.
- Multiple concurrent firmware connections per zone ID — deferred; Phase 16 assumes 1 device per zone ID (last-wins).

</deferred>

---

*Phase: 16-Zone Management*
*Context gathered: 2026-06-23*
