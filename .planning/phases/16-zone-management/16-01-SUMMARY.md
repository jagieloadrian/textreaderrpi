---
phase: 16-zone-management
plan: "01"
subsystem: database
tags: [ktor, websockets, flyway, exposed, kotlin-serialization, firmware]

requires:
  - phase: 15-sse-live-feed
    provides: ktor-server-sse pattern for long-lived connections outside rate limiter

provides:
  - DisplayType.FIRMWARE enum value (fromString case-insensitive)
  - V6 Flyway migration making network_zones.ip nullable and adding display_subtype column
  - ktor-server-websockets 3.5.0 added to build (catalog alias + implementation dep)
  - AddZoneRequest expanded with name, type, nullable ip, nullable displaySubtype
  - NetworkZone.ip made nullable; displaySubtype nullable field added
  - FirmwareMessage @Serializable wire-contract data class (D-11 locked format)
  - NetworkZonesTable ip column nullable; displaySubtype column added
  - ZoneRepository upsert/toNetworkZone handle null ip and displaySubtype
  - ZoneRoutes POST handler uses req.name as zone ID (replaces IP-as-ID)

affects: [16-02, 16-03, 17-firmware-skeletons]

tech-stack:
  added:
    - "io.ktor:ktor-server-websockets 3.5.0 (catalog alias ktor-server-websockets, version.ref = ktor)"
  patterns:
    - "AddZoneRequest.name as zone routing ID (replaces IP-as-ID — D-01)"
    - "Nullable ip in NetworkZone/NetworkZonesTable for firmware zones (D-03)"
    - "Free-text displaySubtype column for firmware display metadata (D-04)"
    - "FirmwareMessage JSON wire contract: text/effect/zoneId/ts fields (D-11)"
    - "ZoneRegistry.addNetworkZone skips null-ip zones (FIRMWARE zones handled by Plan 02 registerFirmwareZone)"

key-files:
  created:
    - src/main/kotlin/com/anjo/model/FirmwareMessage.kt
    - src/main/resources/db/migration/V6__make_ip_nullable_add_display_subtype.sql
    - src/test/kotlin/com/anjo/model/DisplayTypeTest.kt
  modified:
    - gradle/ktor-libs.versions.toml
    - build.gradle.kts
    - src/main/kotlin/com/anjo/model/DisplayType.kt
    - src/main/kotlin/com/anjo/model/AddZoneRequest.kt
    - src/main/kotlin/com/anjo/model/NetworkZone.kt
    - src/main/kotlin/com/anjo/db/NetworkZonesTable.kt
    - src/main/kotlin/com/anjo/db/ZoneRepository.kt
    - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
    - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
    - src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt
    - src/main/kotlin/com/anjo/validation/RequestValidators.kt
    - src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt
    - src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt

key-decisions:
  - "ktor-server-websockets reuses existing ktor = 3.5.0 version.ref — no new version pin needed"
  - "V6 migration uses H2-compatible ALTER COLUMN ip VARCHAR(64) NULL syntax (verified: passes H2 2.4.240 test suite)"
  - "ZoneRegistry.addNetworkZone guards val ip = zone.ip ?: return — FIRMWARE zones with null ip are skipped until Plan 02 adds registerFirmwareZone"
  - "ZoneRoutes POST handler uses req.name as zone ID replacing req.ip — breaking change to existing IP-as-ID scheme"
  - "RequestValidators.validateAddZoneRequest returns Invalid for null ip (backwards compatible for NETWORK type; FIRMWARE validation deferred to ZoneValidators in Plan 02/03)"

patterns-established:
  - "Firmware zone foundation: null ip + displaySubtype in model/table/repository layer"
  - "TDD RED commit (test/) then GREEN commit (feat/) gate sequence maintained"

requirements-completed: [ZONE-09, ZONE-10]

duration: 6min
completed: 2026-06-23
status: complete
---

# Phase 16 Plan 01: Zone Management Schema & Model Foundation Summary

**V6 Flyway migration making ip nullable + display_subtype column, ktor-server-websockets dependency, FIRMWARE enum, expanded AddZoneRequest/NetworkZone models, and FirmwareMessage wire-contract data class**

## Performance

- **Duration:** 6 min
- **Started:** 2026-06-23T10:50:32Z
- **Completed:** 2026-06-23T10:56:22Z
- **Tasks:** 2 (Task 1: build/migration; Task 2: models/table/repository — TDD)
- **Files modified:** 13

## Accomplishments

- Added `ktor-server-websockets` 3.5.0 to version catalog and build (first-party JetBrains artifact, reuses existing `ktor` version ref)
- V6 Flyway migration makes `network_zones.ip` nullable and adds `display_subtype VARCHAR(64) NULL` column; H2-compatible syntax passes full test suite
- `DisplayType.FIRMWARE` added; `fromString("FIRMWARE")` returns FIRMWARE not UNKNOWN (case-insensitive per existing behavior)
- `AddZoneRequest` expanded with `name`, `type`, nullable `ip`, nullable `displaySubtype`; `NetworkZone.ip` made nullable with `displaySubtype` added
- `FirmwareMessage` wire-contract data class created: `{"text":...,"effect":"SCROLL","zoneId":"pico-salon","ts":"..."}` (D-11 locked format)
- `NetworkZonesTable` and `ZoneRepository` updated to persist/read nullable ip and displaySubtype

## Task Commits

Each task was committed atomically:

1. **Task 1: Add ktor-server-websockets dependency and V6 Flyway migration** — `c9bd83f` (feat)
2. **Task 2 RED: Failing tests for FIRMWARE enum and nullable zone fields** — `857b7f4` (test)
3. **Task 2 GREEN: Expand models, table, and repository** — `b8f665f` (feat)

## Files Created/Modified

- `gradle/ktor-libs.versions.toml` — added `ktor-server-websockets` catalog alias
- `build.gradle.kts` — added `implementation(ktorLibs.ktor.server.websockets)`
- `src/main/resources/db/migration/V6__make_ip_nullable_add_display_subtype.sql` — V6 Flyway migration (ip nullable + display_subtype)
- `src/main/kotlin/com/anjo/model/DisplayType.kt` — added FIRMWARE enum constant
- `src/main/kotlin/com/anjo/model/AddZoneRequest.kt` — added name, type, nullable ip, nullable displaySubtype
- `src/main/kotlin/com/anjo/model/NetworkZone.kt` — ip made nullable, displaySubtype added
- `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` — new wire-contract data class (D-11)
- `src/main/kotlin/com/anjo/db/NetworkZonesTable.kt` — ip nullable, displaySubtype column added
- `src/main/kotlin/com/anjo/db/ZoneRepository.kt` — upsert/toNetworkZone handle null ip and displaySubtype
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` — POST handler uses req.name as zone ID, nullable ip handling
- `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` — FIRMWARE added to exhaustive when; addNetworkZone guards null ip
- `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt` — null guard before onDeviceDiscovered call
- `src/main/kotlin/com/anjo/validation/RequestValidators.kt` — nullable ip in validateAddZoneRequest
- `src/test/kotlin/com/anjo/model/DisplayTypeTest.kt` — new: 4 tests for FIRMWARE fromString
- `src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt` — 2 new tests for null-ip firmware zone round-trip
- `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` — updated POST bodies to new JSON shape (name+type+ip)

## Decisions Made

- H2-compatible `ALTER COLUMN ip VARCHAR(64) NULL` syntax confirmed working (A2 assumption verified by full test suite pass)
- `ZoneRegistry.addNetworkZone` skips FIRMWARE zones (null ip → early return) instead of crashing; `registerFirmwareZone()` is Plan 02's responsibility
- `ZoneRoutes POST` updated to use `req.name` as zone ID — behavioural break from IP-as-ID is intentional (D-01); old test bodies updated to new shape

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ZoneRegistry when expression not exhaustive after adding FIRMWARE**
- **Found during:** Task 2 GREEN (compileKotlin)
- **Issue:** `initLocalZone()` uses exhaustive `when` on `DisplayType`; adding FIRMWARE caused compile error
- **Fix:** Added `DisplayType.FIRMWARE` to the `UNKNOWN` branch (falls back to OFFLINE driver — local FIRMWARE config not supported at startup)
- **Files modified:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt`
- **Verification:** compileKotlin exits 0
- **Committed in:** b8f665f

**2. [Rule 3 - Blocking] ZoneRegistry.addNetworkZone passes nullable ip to NetworkZoneDriver constructor expecting String**
- **Found during:** Task 2 GREEN (compileKotlin)
- **Issue:** `NetworkZoneDriver(ip = zone.ip, ...)` with `zone.ip: String?` doesn't compile against `ip: String` parameter
- **Fix:** Guard `val ip = zone.ip ?: return` — skip zones with no IP (firmware zones); Plan 02 adds `registerFirmwareZone()`
- **Files modified:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt`
- **Verification:** compileKotlin exits 0; existing ZoneRegistryTest still passes
- **Committed in:** b8f665f

**3. [Rule 3 - Blocking] NetworkDiscoveryService passes nullable zone.ip to onDeviceDiscovered(ip: String)**
- **Found during:** Task 2 GREEN (compileKotlin)
- **Issue:** `receiveUdpReplies` called `onDeviceDiscovered(ip = zone.ip, ...)` with `zone.ip: String?`
- **Fix:** Added null guard `if (zone != null && zone.ip != null)` — UDP discovery ignores null-ip replies (firmware devices don't respond to UDP anyway — D-13)
- **Files modified:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt`
- **Verification:** compileKotlin exits 0
- **Committed in:** b8f665f

**4. [Rule 3 - Blocking] RequestValidators.validateAddZoneRequest calls split on nullable String**
- **Found during:** Task 2 GREEN (compileKotlin)
- **Issue:** `req.ip.split(".")` fails compilation after ip became `String?`
- **Fix:** Extract `val ip = req.ip ?: return ValidationResult.Invalid(...)` — null ip returns same RFC1918 error
- **Files modified:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt`
- **Verification:** compileKotlin exits 0; ZoneRoutesTest with public IP still returns 422
- **Committed in:** b8f665f

**5. [Rule 1 - Bug] ZoneRoutesTest sent old `{"ip":"..."}` JSON body incompatible with new AddZoneRequest**
- **Found during:** Task 2 GREEN (ZoneRoutesTest run)
- **Issue:** `{"ip":"192.168.1.50"}` missing required `name` field → deserialization error → 400 instead of expected 201/422/409
- **Fix:** Updated all POST bodies to `{"name":"kitchen","type":"NETWORK","ip":"192.168.1.50"}` shape (Pitfall 3 from RESEARCH explicitly called this out)
- **Files modified:** `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt`
- **Verification:** All 6 ZoneRoutesTest tests pass
- **Committed in:** b8f665f

---

**Total deviations:** 5 auto-fixed (4 blocking compile fixes + 1 test body update)
**Impact on plan:** All auto-fixes were cascading consequences of making `NetworkZone.ip` nullable — required for correctness. No scope creep. ZoneRoutes update (using `req.name` as ID) was explicitly in the plan action.

## Issues Encountered

- JaCoCo coverage gate fails when running only a subset of tests (`-x jacocoTestCoverageVerification` used for targeted test runs). Full suite runs with gate pass (coverage 80.7% ≥ 70% gate).

## Known Stubs

None — all fields are fully wired from model → table → repository → route.

## Threat Flags

None — changes are additive (new nullable columns, new enum value). No new network endpoints in this plan (Plan 02 adds the WebSocket route).

## Next Phase Readiness

- Plan 02 can now create `FirmwareZoneDriver` and `FirmwareZoneRoutes` using `FirmwareMessage` wire contract and `NetworkZone(type="FIRMWARE", ip=null)` model
- Plan 03 can add `ZoneValidators` using `DisplayType.FIRMWARE` and the expanded `AddZoneRequest` fields
- V6 migration is idempotent (additive nullable columns; `IF NOT EXISTS` guard on display_subtype)
- Full test suite green including JaCoCo ≥70% gate

## Self-Check: PASSED

Files verified:
- `src/main/resources/db/migration/V6__make_ip_nullable_add_display_subtype.sql` — exists
- `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` — exists
- `src/test/kotlin/com/anjo/model/DisplayTypeTest.kt` — exists
- Commits c9bd83f, 857b7f4, b8f665f — all in git log

---
*Phase: 16-zone-management*
*Completed: 2026-06-23*
