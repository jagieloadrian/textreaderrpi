---
phase: 16-zone-management
fixed_at: 2026-06-23T19:22:00Z
review_path: .planning/phases/16-zone-management/16-REVIEW.md
iteration: 1
findings_in_scope: 13
fixed: 13
skipped: 0
status: all_fixed
---

# Phase 16: Code Review Fix Report

**Fixed at:** 2026-06-23T19:22:00Z
**Source review:** .planning/phases/16-zone-management/16-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 13
- Fixed: 13
- Skipped: 0

## Fixed Issues

### CR-01: Null-pointer crash when adding a non-FIRMWARE network zone with null IP

**Files modified:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt`
**Commit:** 121abdb
**Applied fix:** Replaced `req.ip!!` at line 73 with an explicit null guard using an early return that responds 400 Bad Request with a descriptive message, eliminating the NPE risk.

---

### CR-02: Zone name used as zone ID — no length or character sanitisation before DB/registry insert

**Files modified:** `src/main/kotlin/com/anjo/validation/ZoneValidators.kt`, `src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt`
**Commit:** 0264c1d
**Applied fix:** Added `ZONE_NAME_REGEX = Regex("^[a-zA-Z0-9._-]{1,64}$")` to `ZoneValidators` and a check before the local hardware type guard. Added four tests: special chars rejected, name > 64 chars rejected, valid chars (dots/hyphens/underscores) accepted, exact 64-char boundary accepted.

---

### CR-03: `anyHost()` CORS configuration permits all origins

**Files modified:** `src/main/kotlin/com/anjo/di/HTTP.kt`
**Commit:** 87cbd5b
**Applied fix:** Replaced `anyHost()` with `allowHost("localhost")` and `allowHost("raspberrypi.local")`, restricting CORS to the two expected local-only origins.

---

### CR-04: UDP discovery reply parsed with hand-rolled regex — name injection from network

**Files modified:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt`
**Commit:** ea2ef8c
**Applied fix:** Replaced the two `Regex.find()` calls in `parseDiscoveryReply` with `Json.decodeFromString<JsonObject>()` from `kotlinx.serialization`. Added `DisplayType.fromString(rawType)` validation that maps FIRMWARE and UNKNOWN to MAX7219, preventing type-confusion from untrusted UDP senders. Added the required kotlinx.serialization JSON imports.

---

### CR-05: `live-feed.js` navigation trap — `window.location.reload()` in SSE `onerror` intercepts page navigation

**Files modified:** `src/main/resources/static/live-feed.js`
**Commit:** 154cc23
**Applied fix:** Replaced `window.location.reload()` with a UI indicator that sets the `live-text` element's content to `'— (reconnecting…)'` when the SSE readyState is not OPEN. The browser's built-in SSE reconnect handles actual reconnection.

---

### WR-01: Duplicate `validateAddZoneRequest` in `RequestValidators` is dead code

**Files modified:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt`
**Commit:** 30b4313
**Applied fix:** Deleted the entire `validateAddZoneRequest` function (lines 25-41) and removed the now-unused `AddZoneRequest` import. The function was never called; `RequestValidationConfig` delegates `AddZoneRequest` validation to `ZoneValidators.validateAddZone`.

---

### WR-02: `FirmwareZoneDriver.attach` races on double-attach — old session coroutine keeps running

**Files modified:** `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt`
**Commits:** 4d64664, 8cabaab
**Applied fix:** Added `private val drainJob = AtomicReference<Job?>()`. In `attach`, calls `drainJob.getAndSet(null)?.cancel()` before starting the new drain coroutine, and stores the new job in `drainJob`. Also added a `finally { sessionRef.compareAndSet(session, null) }` block inside the drain coroutine so the driver self-clears to OFFLINE when the session scope is cancelled — this removes a race between the route handler's `finally { driver.detach() }` and test assertions.

---

### WR-03: `ZoneRegistry` uses `lateinit var wsClient` which throws `UninitializedPropertyAccessException` when no-arg constructor is used

**Files modified:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt`
**Commit:** 8b28a11
**Applied fix:** Changed `private lateinit var wsClient: HttpClient` to `private var wsClient: HttpClient? = null`. Changed the no-arg-path `addNetworkZone(zone)` to `val client = wsClient ?: error("ZoneRegistry: wsClient not initialized")` before delegating to `addNetworkZone(zone, client)`, making the uninitialized case fail with a clear error.

---

### WR-04: `ZoneRepository.upsert` is not atomic — TOCTOU race on concurrent upserts for the same ID

**Files modified:** `src/main/kotlin/com/anjo/db/ZoneRepository.kt`
**Commit:** 80e0b5e
**Applied fix:** Replaced the manual select-then-insert/update pattern with Exposed 1.3.0's native `Table.upsert { }` DSL (backed by `INSERT ... ON CONFLICT DO UPDATE`). The `createdAt` column is excluded from the update clause via `onUpdateExclude = listOf(NetworkZonesTable.createdAt)` to preserve the original registration timestamp. Removed the now-unused `insert` and `update` imports.

---

### WR-05: `NetworkDiscoveryService.receiveUdpReplies` calls `withContext(Dispatchers.IO)` while already on `Dispatchers.IO`

**Files modified:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt`
**Commit:** ea2ef8c (grouped with CR-04)
**Applied fix:** Removed the inner `withContext(Dispatchers.IO) { socket.receive(recvPacket) }` wrapper, leaving a plain `socket.receive(recvPacket)` call since the entire `receiveUdpReplies` call chain is already dispatched on `Dispatchers.IO` by `scanUdp`'s outer `withContext` block.

---

### IN-01: `RequestValidators.validateAddZoneRequest` error message leaks implementation detail

**Files modified:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt`
**Commit:** 30b4313 (resolved by WR-01)
**Applied fix:** Moot — the function was deleted as part of WR-01.

---

### IN-02: `ZonesPage.kt` zone type label for FIRMWARE zones renders redundantly as "Firmware (FIRMWARE)"

**Files modified:** `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt`
**Commit:** 00840f7
**Applied fix:** Changed the `when` branch from `"Firmware (${zone.type})"` to `"Firmware"`, removing the redundant parenthetical echo of the enum constant.

---

### IN-03: `ZoneRoutesTest` duplicate-IP test does not assert the first registration succeeded

**Files modified:** `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt`
**Commit:** aa48c12
**Applied fix:** Captured the first `client.post("/api/v1/zones")` response into `firstResponse` and added `firstResponse.status.value shouldBe 201` assertion before the duplicate request, so a silent first-registration failure produces a clear assertion failure rather than a misleading 201-instead-of-409 error.

---

## Test Suite Result

All 262 tests pass after fixes. One pre-existing timing race in `FirmwareZoneRoutesTest` ("returns OFFLINE after disconnect") was also fixed as part of the WR-02 improvement: `closeReason.await()` now synchronizes the test with the WebSocket close handshake, and the drain job's `finally` block auto-clears `sessionRef` when the session scope is cancelled.

---

_Fixed: 2026-06-23T19:22:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
