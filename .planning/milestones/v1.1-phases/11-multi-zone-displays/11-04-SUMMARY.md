---
phase: 11-multi-zone-displays
plan: 04
subsystem: api
tags: [kotlin, ktor, websocket, zones, network-discovery, udp, jmdns, kotest, mockk]

requires:
  - phase: 11-03
    provides: ZoneRegistry class with addNetworkZone stub, ZoneRepository, ScreenDriverService with zoneId param
  - phase: 11-01
    provides: NetworkZone model, ZoneRepository, V5 migration foundation

provides:
  - NetworkZoneDriver class (WebSocket client + reconnect loop + heartbeat via pingIntervalMillis=15s)
  - NetworkDiscoveryService class (UDP scan + JmDNS passive listener + onDeviceDiscovered)
  - ZoneRoutes: GET /api/v1/zones, POST /api/v1/zones/discover, POST /api/v1/zones/{ip}
  - ZoneRegistry.addNetworkZone(NetworkZone) + addNetworkZone(NetworkZone, HttpClient) overloads
  - ZoneRegistry.wsClient stored for route-layer addNetworkZone calls
  - Shared HttpClient(CIO) with WebSockets + pingIntervalMillis=15000 in DI
  - networkDiscoveryService lifecycle (ApplicationStarted/ApplicationStopping)
  - DI provide networkDiscoveryService; NetworkDiscoveryService and ZoneRegistry resolve in smoke test

affects: [11-05, TextRoutes, ScreenDriverService callers]

tech-stack:
  added: []
  patterns:
    - "NetworkZoneDriver: @Volatile session + online fields; while(isActive) reconnect loop with delay(5000) in finally"
    - "send() wraps Frame.Text write in try/catch; returns false on ClosedSendChannelException (Pitfall 7)"
    - "stop() cancels scope via Job?.cancel() — no client.close() (shared client)"
    - "NetworkDiscoveryService.start() wraps JmDNS.create in scope.launch(Dispatchers.IO) (Pitfall 5)"
    - "onDeviceDiscovered: single callback for both UDP + mDNS paths (D-13 single source of truth)"
    - "isValidPrivateIpv4(): inline RFC1918 validation in ZoneRoutes handler (project rule: validation in routes)"
    - "ZoneRegistry stores wsClient for parameter-free addNetworkZone(zone) from route handlers"

key-files:
  created:
    - src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt
    - src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt
    - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
    - src/test/kotlin/com/anjo/zone/NetworkZoneDriverTest.kt
    - src/test/kotlin/com/anjo/service/NetworkDiscoveryServiceTest.kt
    - src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
    - src/main/kotlin/com/anjo/routing/Routing.kt
    - src/main/resources/db/migration/V5__add_network_zones_table.sql
    - src/test/kotlin/com/anjo/ApplicationTest.kt

key-decisions:
  - "ZoneRegistry stores wsClient field from DI constructor; exposes addNetworkZone(zone) no-arg overload so ZoneRoutes route handlers don't need direct HttpClient reference"
  - "NetworkDiscoveryService.testOnDeviceDiscovered() internal seam for unit testing mDNS/UDP callback paths without real network IO"
  - "V5 migration quoting: name and type reserved words in H2 PostgreSQL mode — fixed with double-quoted column names in DDL"
  - "NetworkZoneDriver.send() uses runBlocking to bridge coroutine suspend into ZoneDriver.send() sync interface"
  - "JmDNS.create() in scope.launch(Dispatchers.IO) per Pitfall 5 — ApplicationStarted hook returns immediately"

patterns-established:
  - "RFC1918 private IP validation: octets[0]==10 || octets[0]==172 && octets[1] in 16..31 || octets[0]==192 && octets[1]==168"
  - "Flyway reserved-word quoting: use double-quoted identifiers (name, type) in H2 PostgreSQL mode DDL"

requirements-completed: [ZONE-02, ZONE-03, ZONE-04, ZONE-05, ZONE-06, ZONE-08]

duration: ~10min
completed: 2026-06-16
---

# Phase 11 Plan 04: Network Zone Discovery and WebSocket Driver Summary

**NetworkZoneDriver WebSocket client with reconnect + heartbeat, NetworkDiscoveryService UDP scan + JmDNS listener, ZoneRoutes endpoints, DI/Routing wiring — full test suite green (171 tests)**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-06-16T11:35:15Z
- **Completed:** 2026-06-16T11:46:11Z
- **Tasks:** 3 (Task 1 TDD, Task 2 TDD, Task 3 auto)
- **Files modified:** 11

## Accomplishments

- `NetworkZoneDriver` WebSocket client with `while(isActive)` reconnect loop, `delay(5000)` in finally, `@Volatile` session/online fields, `send()` returns false on any exception, `stop()` cancels scope without touching shared client
- `ZoneRegistry.addNetworkZone(zone: NetworkZone)` completed: constructs `NetworkZoneDriver`, calls `startConnect()`, adds to zones map; stored wsClient enables route-layer calls without client parameter
- `ZoneRegistry` constructor loads persisted network zones from `zoneRepository.findAll()` on boot (D-12 survive restart); DB failure guards local zone startup
- `NetworkDiscoveryService` with `start()`/`stop()` lifecycle: JmDNS passive listener on `_textreaderrpi._tcp.local` (Dispatchers.IO); UDP scan via DatagramSocket with soTimeout=3000 on port 54321; single `onDeviceDiscovered` callback for both UDP + mDNS paths (D-13)
- `ZoneRoutes`: GET /api/v1/zones, POST /api/v1/zones/discover, POST /api/v1/zones/{ip} with RFC1918 validation, duplicate detection, 409/201 responses
- Shared `wsClient = HttpClient(CIO) { install(WebSockets) { pingIntervalMillis = 15_000 } }` in DI for heartbeat (D-10)
- `networkDiscoveryService` wired into ApplicationStarted/ApplicationStopping lifecycle; `wsClient.close()` in ApplicationStopping
- ApplicationTest DI smoke extended to assert `NetworkDiscoveryService` binding

## Task Commits

Each task was committed atomically with TDD RED/GREEN gates:

1. **Task 1 RED: NetworkZoneDriver failing tests** - `c6c3f19` (test)
2. **Task 1 GREEN: NetworkZoneDriver + ZoneRegistry.addNetworkZone** - `01ed88e` (feat)
3. **Task 2 RED: NetworkDiscoveryService + ZoneRoutes failing tests** - `53f76eb` (test)
4. **Task 2+3 GREEN: NetworkDiscoveryService, ZoneRoutes, DI/Routing** - `b3cdb33` (feat)

## Files Created/Modified

- `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt` - WebSocket client driver; startConnect reconnect loop; send() try/catch; stop() scope cancel
- `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt` - UDP scan + JmDNS listener; onDeviceDiscovered upsert + addNetworkZone; testOnDeviceDiscovered seam
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` - GET /zones, POST /zones/discover, POST /zones/{ip}; isValidPrivateIpv4() RFC1918 check
- `src/test/kotlin/com/anjo/zone/NetworkZoneDriverTest.kt` - 3 Kotest tests; null session returns false; status fields; stop cleans up
- `src/test/kotlin/com/anjo/service/NetworkDiscoveryServiceTest.kt` - 3 Kotest tests; onDeviceDiscovered upsert+addNetworkZone; lifecycle
- `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` - 6 Kotest tests; GET 200; POST discover 200; public IP 400; private IP 201; duplicate 409; bad format 400
- `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` - wsClient field; addNetworkZone(zone) no-arg overload; addNetworkZone(zone, client); persisted zone boot-reload
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` - wsClient HttpClient(CIO) with WebSockets; networkDiscoveryService construction; lifecycle hooks; provide block
- `src/main/kotlin/com/anjo/routing/Routing.kt` - networkDiscoveryService + zoneRepository by dependencies; zoneRoutes() in /api/v1 block
- `src/main/resources/db/migration/V5__add_network_zones_table.sql` - Fixed reserved-word quoting for name and type columns
- `src/test/kotlin/com/anjo/ApplicationTest.kt` - NetworkDiscoveryService DI smoke assertion added

## Decisions Made

- `ZoneRegistry` stores `wsClient` field so `addNetworkZone(zone)` (no-arg overload) works from route handlers without passing client through route parameters
- `NetworkDiscoveryService.testOnDeviceDiscovered()` internal test seam exposes the private `onDeviceDiscovered` callback for unit testing without real network IO
- `NetworkZoneDriver.send()` uses `runBlocking` to bridge the suspend `session.send()` into the synchronous `ZoneDriver.send()` interface
- JmDNS.create() wrapped in `scope.launch(Dispatchers.IO)` per Pitfall 5 — ApplicationStarted hook returns immediately, no startup hang on slow Pi hostname resolution

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] V5 Flyway migration reserved words: name and type columns**
- **Found during:** Task 2 GREEN (ZoneRoutesTest integration test failures)
- **Issue:** H2 in PostgreSQL mode treats `name` and `type` as reserved words; `Column "NETWORK_ZONES.name" not found` error when Exposed generated lowercase-quoted `"name"` but H2 stored the DDL-unquoted `name` as uppercase `NAME`
- **Fix:** Quoted `"name"` and `"type"` in `V5__add_network_zones_table.sql` DDL — same fix pattern as V3 migration `"source"` column (Phase 09 P02 decision)
- **Files modified:** `src/main/resources/db/migration/V5__add_network_zones_table.sql`
- **Committed in:** `b3cdb33`

**Total deviations:** 1 auto-fixed (Rule 1 bug — DDL reserved word quoting)

## Known Stubs

None — all planned functionality is wired and tested. Network WebSocket connections from `NetworkZoneDriver` will fail to connect (reporting OFFLINE) in test environments where no display device is running on the target IP, which is expected behavior per D-04/D-11.

## Threat Flags

No new threat surface beyond what was declared in the plan's `<threat_model>`. All four threats handled:
- T-11-06 (SSRF): RFC1918 validation in ZoneRoutes handler (`isValidPrivateIpv4()`) — test asserts 8.8.8.8 → 400
- T-11-07 (UDP spoofing): accepted — spoofed zone falls to OFFLINE on WS connect
- T-11-08 (persistence tampering): Exposed parameterized DSL via ZoneRepository.upsert
- T-11-09 (plaintext ws://): accepted per trusted LAN
- T-11-10 (JmDNS blocking): mitigated — JmDNS.create in Dispatchers.IO launch

## Issues Encountered

- H2 PostgreSQL mode reserves `name` and `type` as SQL keywords; Flyway DDL must quote them to match Exposed's lowercase-quoted column references — same pattern as the `source` column fix in Phase 09 P02

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `NetworkZoneDriver`, `NetworkDiscoveryService`, `ZoneRoutes` all wired and tested
- `GET /api/v1/zones` lists all zones (local + network) with ONLINE/OFFLINE status
- `POST /api/v1/zones/{ip}` registers manual network zones with RFC1918 validation
- `POST /api/v1/zones/discover` triggers UDP scan
- Plan 05 (final wave: end-to-end broadcast, history, metrics consolidation) can build on top

---
*Phase: 11-multi-zone-displays*
*Completed: 2026-06-16*
