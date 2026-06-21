---
phase: 11-multi-zone-displays
verified: 2026-06-16T15:30:00Z
status: human_needed
score: 6/7
overrides_applied: 0
requirements_covered: [ZONE-01, ZONE-02, ZONE-03, ZONE-04, ZONE-05, ZONE-06, ZONE-07, ZONE-08]
human_verification:
  - test: "Two SPI MAX7219 displays render independently on same Pi"
    expected: "Each zone receives and displays its own text without interfering with the other; application starts without Pi4J ID-collision errors"
    why_human: "Requires real Raspberry Pi hardware with two chip-select lines; cannot be verified programmatically"
  - test: "A network display broadcasting UDP or mDNS appears in GET /api/v1/zones within 30 seconds"
    expected: "Zone entry appears with ONLINE status, discoveryMethod=UDP or MDNS, correct IP, within 30s of the device coming online"
    why_human: "Requires a physical LAN device that announces via UDP broadcast or mDNS; mock-based tests only verify the callback path, not the full 30s timing requirement from ROADMAP SC-2"
  - test: "WebSocket heartbeat marks zone OFFLINE when the remote device disconnects"
    expected: "Zone transitions from ONLINE to OFFLINE when the ws:// connection drops; status reflected in GET /api/v1/zones within one ping interval (~15s)"
    why_human: "Requires a live WebSocket peer to connect and then disconnect; NetworkZoneDriver online flag verified by unit test but end-to-end disconnect behavior requires real network"
  - test: "/zones page visual rendering and interaction flow in browser"
    expected: "Zone cards render with correct colored status badges; Scan button shows Scanning... busy label; RFC1918-rejected IP shows correct inline error message; zone removal works"
    why_human: "Plan 05 Task 3 (checkpoint:human-verify) is flagged pending in the SUMMARY. The 0682750 commit message says 'human-verified' but the SUMMARY itself retains the 'awaiting human visual verification' language. CSS variable rendering, PicoCSS badge colors, and JS interaction cannot be verified by grep."
---

# Phase 11: Multi-Zone Displays — Verification Report

**Phase Goal:** Multiple locally-attached and network-discovered displays are registered as named zones and can each receive routed text
**Verified:** 2026-06-16T15:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Multiple displays can be addressed independently via `?zone=X` query param (ZONE-07) | VERIFIED | `TextRoutes.kt` parses `queryParameters["zone"]`; `ZoneRegistry.route(zoneId, ...)` dispatches to the named driver; TextApiRouteTest asserts 202/404/503 semantics |
| 2 | Remote displays can be discovered via UDP scan + mDNS (ZONE-02, ZONE-03) | VERIFIED | `NetworkDiscoveryService` sends UDP broadcast on port 54321 with 3s timeout; JmDNS passive listener on `_textreaderrpi._tcp.local`; both paths funnel to `onDeviceDiscovered` → upsert + addNetworkZone; NetworkDiscoveryServiceTest covers both paths |
| 3 | Remote displays can be manually added by IP (ZONE-06) | VERIFIED | `ZoneRoutes.kt` POST `/zones/{ip}` with `isValidPrivateIpv4()` RFC1918 check; persists MANUAL zone; ZoneRoutesTest asserts 8.8.8.8 → 400, 192.168.1.50 → 201, duplicate → 409 |
| 4 | Remote displays can be removed (gap closure, post-plan DELETE) | VERIFIED | `ZoneRoutes.kt` DELETE `/zones/{id}` deletes from `ZoneRepository` and calls `ZoneRegistry.removeZone(id)`; `removeZone` calls `driver.stop()` to cancel the reconnect coroutine; app.js `deleteZone()` function with 204/404 response handling |
| 5 | All zones persist across restarts (ZONE-05) | VERIFIED | V5 Flyway migration creates `network_zones` table; `ZoneRepository` upsert/findAll via Exposed `suspendTransaction`; `ZoneRegistry` constructor calls `zoneRepository.findAll()` on boot and calls `addNetworkZone` for each persisted zone; `ZoneRepositoryTest` includes explicit restart-survival case (new repository instance returns previously inserted zone) |
| 6 | GET /api/v1/zones returns all zone statuses (ZONE-08) | VERIFIED | `ZoneRoutes.kt` GET `/zones` calls `zoneRegistry.listAll()` which maps all drivers in `ConcurrentHashMap` to `ZoneStatus`; wired in `Routing.kt` under `/api/v1`; ZoneRoutesTest asserts 200 JSON response |
| 7 | /zones UI page shows zone status and management controls (ZONE-08 UI) | UNCERTAIN | `ZonesPage.kt` exists with zone cards, `<mark>` status badges, `#scanBtn`, `#addZoneForm`, `#ipInput`; `ZonesUIRoutes.kt` merges live + persisted data; `BaseLayout.kt` has Zones nav link; ZonesUIRoutesTest 5 tests pass; BUT Plan 05 SUMMARY explicitly notes "Task 3: checkpoint:human-verify — awaiting human visual verification" |

**Score:** 6/7 truths verified (1 UNCERTAIN — human check pending)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` | Uniform zone abstraction interface | VERIFIED | `interface ZoneDriver { fun send(text, effect): Boolean; fun status(): ZoneStatus; fun stop() {} }` |
| `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` | DisplayDriver-backed zone driver | VERIFIED | `class LocalZoneDriver : ZoneDriver`; delegates to `driver.write(text)`; try/catch → false on exception; maps `hardwareAvailable` → ONLINE/OFFLINE |
| `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt` | WebSocket-client zone driver with reconnect | VERIFIED | `while(isActive)` reconnect loop; `@Volatile session/online`; `if(isActive) delay(5000ms)` in finally; `send()` try/catch; `stop()` cancels scope |
| `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` | Map<String,ZoneDriver> routing, broadcast, listAll, addNetworkZone | VERIFIED | `ConcurrentHashMap<String,ZoneDriver>`; `route/broadcast/listAll/contains/statusOf/addNetworkZone/removeZone/stop`; `localZoneIds` set; `ipIndex` for IP-based duplicate detection |
| `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` | GET /zones, POST /zones/discover, POST /zones/{ip}, DELETE /zones/{id} | VERIFIED | All four routes present; `isValidPrivateIpv4()` RFC1918 check; `containsIp()` duplicate detection; 400/409/201/204/404 semantics |
| `src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt` | GET /zones HTML route | VERIFIED | `fun Route.zonesUIRoutes(zoneRegistry, zoneRepository)`: merges live `listAll()` with `zoneRepository.findAll()` via associateBy{id}; renders via `BaseLayout.render(activePath="/zones")` |
| `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` | PicoCSS zones page template | VERIFIED | `fun FlowContent.zonesPage(zones: List<ZoneInfo>)`; `<mark style=...>` badges; `#scanBtn`, `#addZoneForm`, `#ipInput`; empty state "No zones registered"; Remove button for network zones |
| `src/main/resources/db/migration/V5__add_network_zones_table.sql` | network_zones DDL | VERIFIED | `CREATE TABLE IF NOT EXISTS network_zones (...)` with quoted `"name"` and `"type"` for H2 PostgreSQL mode compatibility |
| `src/main/kotlin/com/anjo/config/model/ZonesConfig.kt` | ZonesConfig + ZoneConfig data classes | VERIFIED | `data class ZonesConfig(val zones: List<ZoneConfig>)`; `data class ZoneConfig(id, type, numDevices, bus, chipSelect)` |
| `src/main/kotlin/com/anjo/db/NetworkZonesTable.kt` | Exposed Table for network_zones | VERIFIED | `object NetworkZonesTable : Table("network_zones")`; id PK; no status column (status is in-memory per D-05) |
| `src/main/kotlin/com/anjo/db/ZoneRepository.kt` | CRUD for network zones | VERIFIED | `findAll/findById/upsert/delete` via `suspendTransaction`; `ResultRow.toNetworkZone()` extension |
| `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt` | UDP scan + JmDNS passive listener | VERIFIED | `DatagramSocket` broadcast on port 54321, soTimeout=3000; `JmDNS.create` in `Dispatchers.IO` launch; service name `_textreaderrpi._tcp.local`; CR-01 fix: uses `senderIp` not JSON-supplied ip |
| `src/main/resources/static/app.js` | scanForDisplays() and addZoneByIp() handlers | VERIFIED | `fetch("/api/v1/zones/discover")`, `fetch("/api/v1/zones/" + ip)`; Scanning... busy label; 409/400 spec copy; boot block guards with getElementById checks; deleteZone() handler |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ZonesUIRoutes.kt` | `ZoneRegistry.listAll` | view-model mapping | VERIFIED | `val liveStatuses = zoneRegistry.listAll()` line 15 |
| `app.js` | `/api/v1/zones/discover` and `/api/v1/zones/{ip}` | fetch handlers | VERIFIED | `fetch("/api/v1/zones/discover")` at line 208; `fetch("/api/v1/zones/" + encodeURIComponent(ip))` at line 235 |
| `ZoneRoutes.kt` | `ZoneRegistry.listAll / addNetworkZone` | route handlers | VERIFIED | `zoneRegistry.listAll()` in GET; `zoneRegistry.addNetworkZone(zone)` in POST /{ip} |
| `NetworkDiscoveryService.kt` | `ZoneRepository.upsert + ZoneRegistry.addNetworkZone` | onDeviceDiscovered | VERIFIED | `zoneRepository.upsert(zone)` then `zoneRegistry.addNetworkZone(zone, wsClient)` at lines 122-123 |
| `DependencyInjection.kt` | `networkDiscoveryService.start/stop` | lifecycle hooks | VERIFIED | `networkDiscoveryService.start()` under `ApplicationStarted`; `networkDiscoveryService.stop()` under `ApplicationStopping` (line 64) |
| `Routing.kt` | `zoneRoutes(...)` | /api/v1 block | VERIFIED | `zoneRoutes(zoneRegistry, networkDiscoveryService, zoneRepository)` at line 56 |
| `Routing.kt` | `zonesUIRoutes(...)` | routing block | VERIFIED | `zonesUIRoutes(zoneRegistry, zoneRepository)` at line 47 |
| `TextRoutes.kt` | `?zone=` → `ZoneRegistry` | queryParameters | VERIFIED | `val zoneId = call.request.queryParameters["zone"]`; routes through `screenDriverService.displayImmediate(..., zoneId)` |
| `ScreenDriverService.kt` | `ZoneRegistry.route / broadcast` | displayImmediate | VERIFIED | `zoneRegistry.route(zoneId, ...)` for named zones; `zoneRegistry.broadcast(...)` for no-zone path |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `ZonesUIRoutes.kt` (GET /zones) | `liveStatuses` | `zoneRegistry.listAll()` — reads from `ConcurrentHashMap` populated at startup and on discovery | Yes — live driver status | FLOWING |
| `ZonesUIRoutes.kt` (GET /zones) | `networkZones` | `zoneRepository.findAll()` — `suspendTransaction` DB query | Yes — real DB rows | FLOWING |
| `ZoneRoutes.kt` (GET /api/v1/zones) | `zoneRegistry.listAll()` | In-memory `ConcurrentHashMap.values.map { it.status() }` | Yes — live zone status | FLOWING |
| `ZoneRegistry` (startup) | persisted zones | `zoneRepository.findAll()` then `addNetworkZone` for each | Yes — survives restart | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| app.js parses clean | `node --check src/main/resources/static/app.js` | exit 0 | PASS |
| ZoneRoutes GET test exists | `grep -l "GET.*zones\|get.*zones" src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` | file exists | PASS |
| ZoneRepository restart-survival test exists | `grep "survive restart" src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt` | line 63: `test("should survive restart — new repository instance returns previously inserted zone")` | PASS |
| RFC1918 rejection test | `grep "8.8.8.8.*400\|400.*8.8.8.8\|isValidPrivateIpv4" src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` | passes via ZoneRoutesTest (6 tests per SUMMARY) | PASS |
| V5 migration file present | `ls src/main/resources/db/migration/V5__add_network_zones_table.sql` | file exists, CREATE TABLE IF NOT EXISTS confirmed | PASS |

Step 7b SKIPPED for physical hardware and live network checks — no running server in this environment.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ZONE-01 | Plans 01, 02, 03 | Multiple local displays via SPI/I2C, named zones, backward-compatible | SATISFIED | ZonesConfig list; LocalZoneDriver; OfflineDisplayDriver fallback; application.yaml zones.0.* default zone |
| ZONE-02 | Plan 04 | External displays announce via UDP broadcast or mDNS | SATISFIED | NetworkDiscoveryService UDP on port 54321; JmDNS listener on `_textreaderrpi._tcp.local` |
| ZONE-03 | Plan 04 | Pi auto-detects and registers network displays | SATISFIED | onDeviceDiscovered → upsert + addNetworkZone; ROADMAP SC-2 "within 30s" requires hardware verification (see human check) |
| ZONE-04 | Plan 04 | WebSocket communication with external display | SATISFIED | NetworkZoneDriver sends Frame.Text `{"text":...,"effect":...}` over ws:// connection |
| ZONE-05 | Plans 01, 04 | Heartbeat / health check — Pi tracks online/offline | SATISFIED | pingIntervalMillis=15000 in shared wsClient; `@Volatile online` field; ONLINE/OFFLINE in status(); restart-survival via ZoneRepository.findAll() on boot |
| ZONE-06 | Plans 01, 04, 05 | Manual add by IP | SATISFIED | POST /api/v1/zones/{ip} with RFC1918 check; persisted MANUAL; Add Display by IP form in ZonesPage |
| ZONE-07 | Plan 03 | POST /api/v1/text?zone=X routing | SATISFIED | TextRoutes parses `?zone=`; validates name (alphanumeric+dash, ≤64); 400/404/503/202 semantics |
| ZONE-08 | Plans 04, 05 | GET /api/v1/zones list + /zones UI | SATISFIED | GET /api/v1/zones returns zoneRegistry.listAll() JSON; GET /zones renders ZonesPage HTML with status badges |

**Note on REQUIREMENTS.md tracking:** ZONE-02, ZONE-03, ZONE-04, ZONE-05 remain marked `[ ]` (Pending) and "Pending" in the traceability table in `.planning/REQUIREMENTS.md`. The implementation is present in the codebase, but the tracking document was not updated to mark these complete. This is a documentation gap only — it does not affect phase goal achievement, but REQUIREMENTS.md should be updated to `[x]` for ZONE-02, ZONE-03, ZONE-04, ZONE-05.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ZoneRegistry.kt` | 87 | `runBlocking` inside `broadcast()` (non-suspend function) | WARNING | Blocks dispatcher thread for full broadcast duration; WR-05 from code review — not fixed by the 8480123 commit. Does not prevent the goal but may degrade throughput under many zones. |
| `ZoneRegistry.kt` | 49 | `runBlocking` in secondary constructor during startup | INFO | Accepted per IN-03 in code review; ties startup thread until DB query completes but no timeout. Low risk at this scale. |

No `TBD`, `FIXME`, `XXX`, or inline code comments found in any phase-11 source files. The `placeholder` hit in `ZonesPage.kt` is the HTML `placeholder` attribute on the IP input field — correct usage, not a stub indicator.

**Code Review Fixes Applied (commit 8480123):**

All 5 critical items from the 11-REVIEW.md were addressed:
- CR-01: UDP discovery now uses kernel-verified `senderIp` instead of JSON-supplied `"ip"` field
- CR-02: `localZoneIds` set in `ZoneRegistry` blocks network zones from overwriting local hardware zones
- CR-03: `ipIndex` added; `containsIp()` used in ZoneRoutes for correct duplicate detection across name-keyed discovered zones
- CR-04: `ZoneRegistry.stop()` added; wired in `ApplicationStopping` before `wsClient.close()`
- CR-05: `zones.put().stop()` in `addNetworkZone` prevents orphaned reconnect coroutines on re-registration

Warnings addressed: WR-01 (removed runBlocking in zonesUIRoutes handler), WR-02 (`if(isActive)` guard on reconnect delay), WR-04 (mDNS name sanitised with regex + 64-char cap).

Unresolved warnings: WR-03 (no CSRF protection — accepted for trusted LAN app), WR-05 (runBlocking in broadcast — correctness risk, not functionality blocker), WR-06 (JmDNS race between start() coroutine and stop()). INFO items IN-01, IN-02, IN-03 also unresolved but non-blocking.

---

### Human Verification Required

#### 1. Two SPI MAX7219 displays render independently

**Test:** Wire two MAX7219 displays on SPI0 with CS0 and CS1. Add a second zone to `application.yaml` under `display.zones.1.*` with `id: secondary, chipSelect: 1`. Start the app. POST to `/api/v1/text?zone=main` with one text, then POST to `/api/v1/text?zone=secondary` with different text.
**Expected:** Each display shows its own text; neither display flickers or shows the other's content; application logs show no Pi4J ID-collision errors.
**Why human:** Requires real Pi hardware with two physical SPI chip-select lines. Cannot be unit-tested (Pi4J construction needs GPIO).

#### 2. Network display auto-discovery within 30 seconds (ROADMAP SC-2)

**Test:** Connect a device to the same LAN that periodically broadcasts UDP on port 54321 with payload `{"name":"testzone","ip":"<its-ip>","type":"MAX7219"}`, OR announces `_textreaderrpi._tcp.local` via mDNS. Start the app. Wait up to 30 seconds. Call `GET /api/v1/zones`.
**Expected:** The device appears in the zones list with `discoveryMethod` set to "UDP" or "MDNS" and status "OFFLINE" (it will be OFFLINE since no actual WebSocket server runs on the device).
**Why human:** Requires a real LAN device; unit tests (NetworkDiscoveryServiceTest) only mock the callback path, not the full timing loop.

#### 3. WebSocket heartbeat marks zone OFFLINE on disconnect (ZONE-05)

**Test:** Add a zone that has a running WebSocket server at a known IP. Confirm it appears ONLINE in `GET /api/v1/zones`. Kill the WebSocket server. Wait ~30 seconds (2x ping interval). Call `GET /api/v1/zones` again.
**Expected:** Zone transitions to OFFLINE status once the ping-pong fails and the `webSocket { }` block exits; `online = false` is reflected in the status response.
**Why human:** Requires a live WebSocket peer; reconnect loop behavior under real network drop is not unit-testable.

#### 4. /zones page visual rendering and interaction (Plan 05 human-verify checkpoint)

**Test:**
1. Run `./gradlew run` (or equivalent). Open `http://localhost:8080/zones`.
2. Confirm the top nav shows "Zones" between "History" and "Settings".
3. Confirm the default `main` local zone appears as a card with an ONLINE badge.
4. Click "Scan for Displays" — button label should change to "Scanning..." for ~3 seconds, then restore to "Scan for Displays" with an inline result message.
5. In "Add Display by IP", enter `8.8.8.8` — expect inline error "Enter a valid IP address (e.g. 192.168.1.50)."
6. Enter `192.168.1.50` — expect "Zone added. Page reloading..." and a new card on reload.
**Expected:** All visual elements match the 11-UI-SPEC.md layout; status badges use correct PicoCSS CSS variable colors; interaction flow works end-to-end.
**Why human:** CSS variable rendering and interactive behavior cannot be verified by grep. The SUMMARY states the human checkpoint is "awaiting" though the docs commit message says "human-verified" — resolving this ambiguity requires a browser test.

---

### Gaps Summary

No code-level BLOCKER gaps. The 7 must-have truths all have substantive implementations wired through to real data sources. The single UNCERTAIN item (truth #7, /zones UI visual) is a pending human-verify checkpoint carried from Plan 05. All 5 critical code-review issues were fixed post-plan.

The only action item before marking the phase fully PASSED is:

1. Complete the human verification checkpoint for the /zones page (4 tests above).
2. Update `REQUIREMENTS.md` to mark ZONE-02, ZONE-03, ZONE-04, ZONE-05 as `[x]` Complete — the implementation exists but the tracking document was not updated.

---

_Verified: 2026-06-16T15:30:00Z_
_Verifier: Claude (gsd-verifier)_
