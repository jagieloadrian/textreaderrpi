---
phase: 11-multi-zone-displays
plan: "02"
subsystem: zone
tags: [kotlin, ktor, kotest, mockk, kotlinx-serialization, zone-driver, interface]

requires:
  - phase: 11-multi-zone-displays-01
    provides: NetworkZonesTable, ZoneRepository, NetworkZone model — DB foundation consumed by ZoneRegistry (Plan 03)

provides:
  - ZoneDriver interface (com.anjo.zone.ZoneDriver) with send(text, effect): Boolean and status(): ZoneStatus
  - LocalZoneDriver class wrapping DisplayDriver with ONLINE/OFFLINE status mapping and try/catch error containment
  - ZoneStatus serializable model (com.anjo.model.ZoneStatus) for GET /api/v1/zones response
  - LocalZoneDriverTest with 3 passing Kotest cases

affects:
  - 11-multi-zone-displays-03 (ZoneRegistry consumes ZoneDriver + LocalZoneDriver)
  - 11-multi-zone-displays-04 (NetworkZoneDriver implements ZoneDriver)
  - 11-multi-zone-displays-05 (ZoneRoutes returns List<ZoneStatus>)

tech-stack:
  added: []
  patterns:
    - "ZoneDriver interface mirrors DisplayDriver shape: two contract members, no defaults"
    - "LocalZoneDriver delegates to DisplayDriver.write() synchronously; scrollText bypassed for direct write path"
    - "status() maps DisplayStatus.hardwareAvailable -> ZoneStatus.status string (ONLINE/OFFLINE)"
    - "try/catch in send() follows WebhookService warn+false-return pattern (T-11-02 STRIDE mitigation)"

key-files:
  created:
    - src/main/kotlin/com/anjo/model/ZoneStatus.kt
    - src/main/kotlin/com/anjo/zone/ZoneDriver.kt
    - src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt
    - src/test/kotlin/com/anjo/zone/ZoneStatusTest.kt
    - src/test/kotlin/com/anjo/zone/LocalZoneDriverTest.kt
  modified: []

key-decisions:
  - "LocalZoneDriver.send() calls driver.write() for all effects (synchronous direct write path); scrollText is fire-and-forget so bypassed here — Plan 03 owns the full effect rendering pipeline"
  - "ZoneStatus.status is String not enum — matches DB convention per plan and PicoCSS badge contract in UI spec"
  - "com.anjo.zone package created as the home for all zone abstraction types (ZoneDriver, LocalZoneDriver, NetworkZoneDriver)"

patterns-established:
  - "ZoneDriver: two-method interface pattern (send + status) — both LocalZoneDriver and NetworkZoneDriver implement fully, no defaults"
  - "LocalZoneDriver: constructor injection of DisplayDriver; status derived from driver.status().hardwareAvailable"

requirements-completed: [ZONE-01]

duration: 15min
completed: "2026-06-16"
---

# Phase 11 Plan 02: ZoneDriver Abstraction + LocalZoneDriver Summary

**ZoneDriver interface and LocalZoneDriver wrapping DisplayDriver with ONLINE/OFFLINE status mapping and exception-contained send() for fault-isolated multi-zone routing**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-06-16T10:46:00Z
- **Completed:** 2026-06-16T11:01:17Z
- **Tasks:** 2 (TDD: 4 commits total — 2 RED + 2 GREEN)
- **Files modified:** 5 created, 0 modified

## Accomplishments
- `ZoneStatus` @Serializable data class in `com.anjo.model` — `id`, `type`, `status` (String), nullable `ip` and `lastSeenAt`
- `ZoneDriver` interface in new `com.anjo.zone` package with `send(text, effect): Boolean` and `status(): ZoneStatus`
- `LocalZoneDriver` wrapping `DisplayDriver` with synchronous `write()` delegation, try/catch on failure, `hardwareAvailable` → `"ONLINE"`/`"OFFLINE"` mapping
- `LocalZoneDriverTest` with 3 passing Kotest FunSpec cases (healthy driver, throwing driver, OfflineDisplayDriver)

## Task Commits

Each task was committed atomically (TDD RED then GREEN):

1. **Task 1 RED: ZoneStatus test** - `3eb2fc6` (test)
2. **Task 1 GREEN: ZoneStatus + ZoneDriver** - `0725ef2` (feat)
3. **Task 2 RED: LocalZoneDriverTest** - `f417184` (test)
4. **Task 2 GREEN: LocalZoneDriver** - `68e5942` (feat)

**Plan metadata:** (docs commit follows)

_Note: TDD plan — 2 RED commits + 2 GREEN commits per gate sequence_

## Files Created/Modified
- `src/main/kotlin/com/anjo/model/ZoneStatus.kt` - Serializable zone status value object (id, type, status, ip?, lastSeenAt?)
- `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` - Uniform zone abstraction interface (send + status contract)
- `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` - DisplayDriver-backed zone driver with error containment
- `src/test/kotlin/com/anjo/zone/ZoneStatusTest.kt` - ZoneStatus JSON serialization tests (Task 1 RED)
- `src/test/kotlin/com/anjo/zone/LocalZoneDriverTest.kt` - LocalZoneDriver behavior tests — 3 cases (Task 2 RED)

## Decisions Made
- `LocalZoneDriver.send()` calls `driver.write(text)` for all effects — `scrollText()` is fire-and-forget (void) so bypassed here; Plan 03's `ScreenDriverService` pipeline owns full effect rendering. This keeps `send()` synchronous returning Boolean cleanly.
- `ZoneStatus.status` stored as String (`"ONLINE"`/`"OFFLINE"`/`"DEGRADED"`) not enum — matches DB column convention and PicoCSS badge contract per plan action spec.
- `com.anjo.zone` package created for all zone abstraction types; `ZoneStatus` lives in `com.anjo.model` (alongside `Effect`, `Schedule`, etc.) since it is a serializable API model.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## TDD Gate Compliance

RED gate: `3eb2fc6` (test, compile-error RED for ZoneStatus) and `f417184` (test, compile-error RED for LocalZoneDriver)
GREEN gate: `0725ef2` (feat, ZoneStatus + ZoneDriver) and `68e5942` (feat, LocalZoneDriver)

Both RED → GREEN gate sequences satisfied.

## Threat Surface Scan

No new network endpoints, auth paths, or external attack surface introduced. `LocalZoneDriver.send()` wraps all driver calls in try/catch per T-11-02 STRIDE mitigation — a faulty zone cannot crash ZoneRegistry or block other zones (D-04, ASVS V5).

## Known Stubs

None — `LocalZoneDriver` directly delegates to `DisplayDriver`; no hardcoded or mock data flows to any UI.

## Next Phase Readiness
- `ZoneDriver` interface is ready for `ZoneRegistry` (Plan 03) to consume — both `LocalZoneDriver` and the upcoming `NetworkZoneDriver` (Plan 04) implement the same contract
- `ZoneStatus` is ready for `GET /api/v1/zones` endpoint in Plan 05
- Plans 03 and 04 can proceed in parallel as both contracts are now defined

---
*Phase: 11-multi-zone-displays*
*Completed: 2026-06-16*
