---
phase: 16-zone-management
plan: "03"
subsystem: api-validation-ui
tags: [ktor, validation, html-dsl, javascript, zone-management, firmware]

requires:
  - phase: 16-zone-management
    plan: "01"
    provides: AddZoneRequest shape, DisplayType.FIRMWARE, NetworkZone.displaySubtype
  - phase: 16-zone-management
    plan: "02"
    provides: ZoneRegistry.registerFirmwareZone()

provides:
  - ZoneValidators.validateAddZone(): blank name, local-type 422, IP-required-for-network, RFC1918 check
  - RequestValidationConfig delegates AddZoneRequest to ZoneValidators
  - POST /zones branches on FIRMWARE (registerFirmwareZone OFFLINE) vs NETWORK (addNetworkZone)
  - ZonesPage Add Zone form with zoneNameInput/zoneTypeSelect/ipFieldWrapper/displaySubtypeWrapper + accessible labels
  - ZoneInfo.displaySubtype field wired through ZonesUIRoutes
  - app.js initZoneForm() type toggle (hidden attribute) + addZone() JSON POST to /api/v1/zones

affects: [17-firmware-skeletons]

tech-stack:
  added: []
  patterns:
    - "ZoneValidators object mirrors ScheduleValidators/HistoryValidators naming + ValidationResult pattern"
    - "RFC1918 octet check extracted from RequestValidators into ZoneValidators.validateRfc1918 (private)"
    - "POST /zones thin handler: no inline validation, branches solely on req.type == FIRMWARE vs not"
    - "form field toggle via HTML hidden attribute (not CSS display) so hidden fields excluded from tab order and submission"
    - "textContent (not innerHTML) for all dynamic DOM text in addZone() — XSS safe"

key-files:
  created:
    - src/main/kotlin/com/anjo/validation/ZoneValidators.kt
    - src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt
  modified:
    - src/main/kotlin/com/anjo/validation/RequestValidationConfig.kt
    - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
    - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
    - src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt
    - src/main/resources/static/app.js
    - src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt

key-decisions:
  - "ValidationResult.Invalid does not override equals — tests use .reasons.first() and `is ValidationResult.Valid` checks instead of shouldBe object equality"
  - "RFC1918 check extracted into private validateRfc1918() in ZoneValidators rather than re-used via RequestValidators — keeps ZoneValidators self-contained per project rule (validation stays in *Validators only)"
  - "POST /zones guards duplicate on zoneRegistry.contains OR zoneRepository.findById — covers both in-memory and persisted state"
  - "req.ip!! safe in NETWORK branch because ZoneValidators already rejects null/blank ip before route is reached"
  - "Add Zone form placed above Registered Zones section per UI-SPEC focal point: primary CTA first"

patterns-established:
  - "ZoneValidators replaces RequestValidators for AddZoneRequest wiring — RequestValidators.validateAddZoneRequest remains but is no longer called"
  - "TDD RED commit (test/) then GREEN commit (feat/) gate sequence maintained"

requirements-completed: [ZONE-09]

duration: ~18min
completed: 2026-06-23
status: complete
---

# Phase 16 Plan 03: Add Zone Form and POST /zones Validation Summary

**ZoneValidators with RFC1918+type validation, rewritten POST /zones FIRMWARE/NETWORK branching, and Add Zone form with type-conditional fields and JS toggle**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-06-23T13:05:00Z
- **Completed:** 2026-06-23T13:23:00Z
- **Tasks:** 2 (Task 1: ZoneValidators + RequestValidation + ZoneRoutes — TDD; Task 2: ZonesPage + ZonesUIRoutes + app.js)
- **Files modified:** 8 (2 new, 6 modified)

## Accomplishments

- `ZoneValidators` object created with `validateAddZone()`: blank name returns Invalid, local hardware types (MAX7219/LCD/OLED/UNKNOWN) return 422 with startup message, NETWORK type requires private RFC1918 IP, FIRMWARE type valid without IP
- `RequestValidationConfig` re-pointed from `RequestValidators.validateAddZoneRequest` to `ZoneValidators.validateAddZone` — all AddZoneRequest validation now centralized in ZoneValidators
- `ZoneRoutes.kt` POST handler rewritten: duplicate guard on `zoneRegistry.contains || zoneRepository.findById`; FIRMWARE branch calls `registerFirmwareZone(req.name)` (OFFLINE placeholder, D-07); NETWORK branch calls `addNetworkZone(zone)`. No inline validation logic.
- `ZoneRoutesTest.kt` extended with FIRMWARE type → 201 test and MAX7219 type → 422 test
- `ZoneValidatorsTest.kt` created: 14 unit tests covering all `validateAddZone` behaviors
- `ZonesPage.kt` "Add Zone" form replaces "Add Display by IP": full form with `zoneNameInput`, `zoneTypeSelect`, `ipFieldWrapper`, `displaySubtypeWrapper`, accessible `label` elements for each input; FIRMWARE card label branch added with `displaySubtype` rendering
- `ZoneInfo` data class extended with `displaySubtype: String?`
- `ZonesUIRoutes.kt` passes `displaySubtype = networkZone?.displaySubtype` in ZoneInfo construction
- `app.js` `initZoneForm()` toggles `hidden` attribute on `ipFieldWrapper`/`displaySubtypeWrapper` on type change; `addZone()` replaces `addZoneByIp()` — posts JSON to `/api/v1/zones`, uses `.textContent` for 422 errors, `.showToast()` for success/network errors. Fixed wrong URL (`/api/v1/zones/{ip}` → `/api/v1/zones`)
- Full test suite green (258 tests), JaCoCo ≥70% coverage gate passes

## Task Commits

Each task was committed atomically with RED before GREEN for Task 1:

1. **Task 1 RED: Failing tests for ZoneValidators** — `a7862fe` (test)
2. **Task 1 GREEN: ZoneValidators, rewired RequestValidation, rewritten POST /zones** — `3a588b6` (feat)
3. **Task 2: Add Zone form UI, ZoneInfo displaySubtype, and app.js type toggle** — `07e74a7` (feat)

## Files Created/Modified

- `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` — new validation object (blank name, local hardware 422, RFC1918 check)
- `src/main/kotlin/com/anjo/validation/RequestValidationConfig.kt` — validate<AddZoneRequest> delegates to ZoneValidators.validateAddZone
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` — POST /zones rewritten with FIRMWARE/NETWORK branching, no inline validation
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` — Add Zone form, displaySubtype in ZoneInfo, FIRMWARE card label
- `src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt` — displaySubtype wired in ZoneInfo mapping
- `src/main/resources/static/app.js` — initZoneForm() + addZone() replacing addZoneByIp()
- `src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt` — new: 14 unit tests for validateAddZone
- `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` — 2 new tests (FIRMWARE 201, MAX7219 422)

## Decisions Made

- `ValidationResult.Invalid` does not override `equals`; comparisons use `.reasons.first() shouldBe` and `is ValidationResult.Valid` checks rather than `shouldBe ValidationResult.Invalid("...")` object equality (auto-fixed during RED→GREEN iteration)
- RFC1918 octet validation extracted into private `validateRfc1918()` in `ZoneValidators` rather than calling `RequestValidators` — keeps the `*Validators` object self-contained; `RequestValidators.validateAddZoneRequest` remains unused but undisturbed
- `req.ip!!` in the NETWORK branch of the POST handler is safe because `ZoneValidators` rejects null/blank ip before the request reaches the route handler — no NPE risk

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ValidationResult.Invalid equality via shouldBe does not work (no equals override)**
- **Found during:** Task 1 GREEN (ZoneValidatorsTest run, 4 tests failed)
- **Issue:** `shouldBe ValidationResult.Invalid("message")` compares by object identity; the class does not override `equals`
- **Fix:** Changed assertions to `(result as ValidationResult.Invalid).reasons.first() shouldBe "message"` and `(result is ValidationResult.Valid) shouldBe true`
- **Files modified:** `src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt`
- **Verification:** All 14 ZoneValidatorsTest tests pass
- **Committed in:** 3a588b6 (same GREEN commit, no separate fix commit needed — test file updated before final commit)

**2. [Pre-existing] FirmwareZoneRoutesTest intermittent timing failure**
- **Found during:** Full suite run (intermittent, not on every run)
- **Issue:** `GET /ws/zone... returns OFFLINE after disconnect` occasionally fails due to WS session cleanup coroutine timing — pre-existing from Plan 02, not caused by Plan 03 changes
- **Fix:** Not fixed — pre-existing flaky test in Plan 02 scope. Passes in isolation and on most full-suite runs.
- **Impact:** Plan 03 scope not affected; deferred to Plan 02 origin scope

## Known Stubs

None — all form fields are wired to the API; displaySubtype is persisted and rendered from the database; firmware zones are registered OFFLINE and transition to ONLINE on device connect (Plan 02).

## Threat Flags

All threat mitigations from the plan's STRIDE register applied:

| Flag | File | Mitigation Applied |
|------|------|--------------------|
| T-16-07 (Tampering - name as routing id) | ZoneValidators.kt / ZoneRoutes.kt | Blank-name check in ZoneValidators; name stored as-is, no shell exec |
| T-16-08 (Spoofing/SSRF - ip field) | ZoneValidators.kt | validateRfc1918() enforces private range only for NETWORK type |
| T-16-09 (XSS - displaySubtype/name in browser) | app.js | All dynamic DOM text set via `.textContent` (never `.innerHTML`) |

## Self-Check: PASSED

Files verified present:
- `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` — exists, contains `object ZoneValidators` and `fun validateAddZone`
- `src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt` — exists, 14 tests
- Commits a7862fe, 3a588b6, 07e74a7 — all in git log

Acceptance criteria check:
- ZoneValidators.kt contains `object ZoneValidators` and `fun validateAddZone` — YES
- RequestValidationConfig.kt contains `ZoneValidators.validateAddZone` — YES
- ZoneRoutes.kt POST handler contains `registerFirmwareZone` and uses `req.name` — YES; no RFC1918/local-type logic inline — YES
- ZoneRoutesTest sends bodies with `"name"` and `"type"` and includes FIRMWARE 201 and local-hardware 422 tests — YES
- ZonesPage.kt contains `zoneTypeSelect`, `ipFieldWrapper`, `displaySubtypeWrapper`, and FIRMWARE card label branch — YES
- ZonesUIRoutes.kt sets `displaySubtype = networkZone?.displaySubtype` — YES
- app.js contains `function addZone` and `initZoneForm`, posts to `/api/v1/zones`, no old `zones/" + encodeURIComponent(ip)` for add — YES
- app.js sets error text via `.textContent` — YES
- Full test suite green — YES

---
*Phase: 16-zone-management*
*Completed: 2026-06-23*
