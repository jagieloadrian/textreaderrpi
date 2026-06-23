---
phase: 16-zone-management
plan: "02"
subsystem: backend
tags: [ktor, websockets, firmware, coroutines, channel, atomicreference, zone-driver]

requires:
  - phase: 16-zone-management
    plan: "01"
    provides: DisplayType.FIRMWARE, FirmwareMessage wire-contract, ktor-server-websockets dependency

provides:
  - FirmwareZoneDriver with Channel<String>(64) + AtomicReference<DefaultWebSocketServerSession?>
  - ZoneRegistry.registerFirmwareZone() atomic compute() insert
  - ZoneRegistry.firmwareDriver(id) accessor returning FirmwareZoneDriver? cast
  - ZoneRegistry.addNetworkZone() migrated from put() to compute() for atomic safety
  - ZoneRegistry startup branches on FIRMWARE type to register OFFLINE placeholders
  - GET /ws/zone/{id} WebSocket endpoint via FirmwareZoneRoutes.firmwareZoneRoutes()
  - install(WebSockets) in HTTP.kt
  - route("/ws") registered outside installApiRateLimiting in Routing.kt

affects: [16-03, 17-firmware-skeletons]

tech-stack:
  added: []
  patterns:
    - "Channel<String>(64) + AtomicReference session pattern for thread-safe WS sends (D-10)"
    - "attach() starts drain coroutine in session scope; send() uses trySend() only — never session.send() from display coroutine"
    - "ConcurrentHashMap.compute() for atomic check-then-insert in ZoneRegistry (D-06)"
    - "webSocket(\"/zone/{id}\") handler with CANNOT_ACCEPT close for unknown zones (D-08)"
    - "route(\"/ws\") block outside rate limiter matching liveRoutes pattern (Pitfall 5)"

key-files:
  created:
    - src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt
    - src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt
    - src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt
    - src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
    - src/main/kotlin/com/anjo/di/HTTP.kt
    - src/main/kotlin/com/anjo/routing/Routing.kt

key-decisions:
  - "FirmwareZoneDriver.send() uses channel.trySend() only — session.send() is only in drain coroutine within attach()"
  - "ZoneRegistry.addNetworkZone() migrated from zones.put() to zones.compute() closing the non-atomic race (D-06)"
  - "ZoneRegistry startup: when zone.type == FIRMWARE, calls registerFirmwareZone() not addNetworkZone() (OFFLINE on startup)"
  - "FirmwareZoneRoutesTest uses application.dependencies.getBlocking to pre-register firmware zones (Plan 03 ZoneValidators not yet active)"

patterns-established:
  - "Inbound WS driver pattern: Channel<String> queue + AtomicReference session + drain coroutine in session scope"
  - "TDD RED commit (test/) then GREEN commit (feat/) gate sequence maintained for both tasks"

requirements-completed: [ZONE-10]

duration: 7min
completed: 2026-06-23
status: complete
---

# Phase 16 Plan 02: FirmwareZoneDriver and WebSocket Endpoint Summary

**Inbound WS zone driver with Channel/AtomicReference session management and GET /ws/zone/{id} endpoint; zone goes ONLINE on connect, OFFLINE on disconnect**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-06-23T10:59:53Z
- **Completed:** 2026-06-23T11:07:00Z
- **Tasks:** 2 (Task 1: FirmwareZoneDriver + ZoneRegistry; Task 2: FirmwareZoneRoutes + HTTP + Routing)
- **Files modified:** 7 (4 new, 3 modified)

## Accomplishments

- `FirmwareZoneDriver` created: `Channel<String>(capacity=64)` + `AtomicReference<DefaultWebSocketServerSession?>` for thread-safe WS sends; `attach()` launches drain coroutine in session scope; `send()` calls `channel.trySend()` only (D-10); `detach()` sets ref to null (OFFLINE)
- `ZoneRegistry.registerFirmwareZone(id)` uses `zones.compute()` for atomic check-then-insert; preserves local zone entries (`isLocal == true`)
- `ZoneRegistry.firmwareDriver(id)` accessor returns `FirmwareZoneDriver?` cast for route lookup (D-08)
- `ZoneRegistry.addNetworkZone()` migrated from `zones.put()` to `zones.compute()` closing the non-atomic race window (D-06 pitfall fixed)
- ZoneRegistry startup constructor now branches on `zone.type == FIRMWARE.name` to call `registerFirmwareZone()` instead of `addNetworkZone()` — firmware zones load as OFFLINE on startup
- `GET /ws/zone/{id}`: rejects unknown zones with `CANNOT_ACCEPT` close reason; calls `attach()`/`detach()` on connect/disconnect
- `install(WebSockets)` added to `HTTP.kt` alongside existing `install(SSE)`
- `route("/ws") { firmwareZoneRoutes(zoneRegistry) }` registered outside the `installApiRateLimiting` block (Pitfall 5)
- 9 new tests across 2 test classes, TDD RED/GREEN gate maintained; full suite green, JaCoCo ≥70% gate passes

## Task Commits

Each task was committed atomically with RED before GREEN:

1. **Task 1 RED: Failing tests for FirmwareZoneDriver and ZoneRegistry** — `63252c3` (test)
2. **Task 1 GREEN: FirmwareZoneDriver + ZoneRegistry firmware registration** — `c3bb0c6` (feat)
3. **Task 2 RED: Failing tests for FirmwareZoneRoutes** — `036d7ae` (test)
4. **Task 2 GREEN: FirmwareZoneRoutes + HTTP + Routing** — `1996b7c` (feat)

## Files Created/Modified

- `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt` — new inbound WS driver (Channel/AtomicReference)
- `src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt` — new GET /ws/zone/{id} webSocket handler
- `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` — registerFirmwareZone(), firmwareDriver(), addNetworkZone() compute(), startup FIRMWARE branch
- `src/main/kotlin/com/anjo/di/HTTP.kt` — install(WebSockets) added
- `src/main/kotlin/com/anjo/routing/Routing.kt` — route("/ws") block added outside rate limiter
- `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — 6 unit tests (driver + registry)
- `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt` — 3 integration tests (connect, reject, offline after disconnect)

## Decisions Made

- `FirmwareZoneDriver.send()` never calls `session.send()` directly — the `session.send(Frame.Text(msg))` call lives only inside the drain coroutine launched in `attach()` within the session's scope. This ensures display coroutine never crosses into WS session context.
- `ZoneRegistry.addNetworkZone()` migrated from `zones.put()` to `zones.compute()` — the existing log warning for local zone conflicts is preserved inside the compute lambda; the replaced driver is stopped atomically.
- Startup loading branches on FIRMWARE type: `registerFirmwareZone(zone.id)` is called for firmware zones, so they appear as OFFLINE placeholders on server restart without connecting to any network.
- `FirmwareZoneRoutesTest` uses `application.dependencies.getBlocking<ZoneRegistry>` to pre-register firmware zones directly since ZoneValidators (Plan 03) is not yet active; this is the correct approach for Wave-2 integration testing before Plan 03.

## Deviations from Plan

None — plan executed exactly as written.

All acceptance criteria met:
- `FirmwareZoneDriver.kt` exists and contains `class FirmwareZoneDriver`, `channel.trySend`, and `AtomicReference`
- `FirmwareZoneDriver.send()` body only uses `channel.trySend()` — `session.send()` is in drain coroutine only
- `ZoneRegistry.kt` contains `fun registerFirmwareZone(` and `zones.compute(`
- `ZoneRegistry.addNetworkZone()` uses `compute` (no bare `zones.put(` for network insert)
- `FirmwareZoneDriver().send(...)` with no session returns false; `status()` returns OFFLINE
- `FirmwareZoneRoutes.kt` exists and contains `fun Route.firmwareZoneRoutes` and `webSocket("/zone/{id}")`
- `HTTP.kt` contains `install(WebSockets)`
- `Routing.kt` contains `firmwareZoneRoutes(zoneRegistry)` in a `route("/ws")` block NOT inside `installApiRateLimiting`
- All tests pass; full suite green

## Known Stubs

None — all driver methods are fully implemented.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: inbound-ws-auth-absent | src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt | GET /ws/zone/{id} accepts any inbound WS for a known zone ID with no device authentication (T-16-03 accepted — home network scope per RESEARCH Security Domain) |

T-16-04 (channel overflow), T-16-05 (WS coroutine context), T-16-06 (rate limiter) — all mitigated as planned:
- T-16-04: `trySend()` drops on full channel, returns false (no unbounded growth)
- T-16-05: drain coroutine in session scope; `send()` uses `trySend()` only
- T-16-06: `route("/ws")` is outside `installApiRateLimiting`

## Self-Check: PASSED

Files verified:
- `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt` — exists
- `src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt` — exists
- `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — exists
- `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt` — exists
- Commits 63252c3, c3bb0c6, 036d7ae, 1996b7c — all in git log

---
*Phase: 16-zone-management*
*Completed: 2026-06-23*
