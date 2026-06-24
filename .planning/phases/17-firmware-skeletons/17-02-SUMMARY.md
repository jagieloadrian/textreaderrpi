---
phase: 17-firmware-skeletons
plan: 02
subsystem: api
tags: [validation, text-api, request-dto, firmware-timing]

requires:
  - phase: 17-01
    provides: displayImmediate extended with timing params (speed/blinkPeriod/fadeSteps)
provides:
  - TextRequest DTO with nullable speed/blinkPeriod/fadeSteps fields
  - RequestValidators.validateTextRequest guards: rejects speed/blinkPeriod/fadeSteps <= 0 with 422
  - TextRoutes.textRoutes forwards request timing to screenDriverService.displayImmediate
  - RequestValidatorsTest (5 tests), TextApiRouteTest extended with 2 timing integration tests
affects: [17-03, 17-04, firmware-phases, api-consumers]

tech-stack:
  added: []
  patterns:
    - "Timing validation exclusively in RequestValidators.validateTextRequest — never in route handlers (project coding rule enforced)"
    - "req.speed?.let { if (it <= 0) return Invalid(...) } form for nullable positive-integer guards"

key-files:
  created:
    - src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt
  modified:
    - src/main/kotlin/com/anjo/model/TextRequest.kt
    - src/main/kotlin/com/anjo/validation/RequestValidators.kt
    - src/main/kotlin/com/anjo/routing/TextRoutes.kt
    - src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt

key-decisions:
  - "Nullable timing fields (Int? = null) on TextRequest — absent from request JSON means null, null passes validation, non-positive rejects with 422"
  - "Validation uses ?.let { if (it <= 0) return Invalid } — null fields skip validation entirely, zero treated as non-positive"

patterns-established:
  - "Positive-integer guard pattern: req.field?.let { if (it <= 0) return ValidationResult.Invalid('field must be a positive integer') }"

requirements-completed: [FW-01, FW-02]

duration: 389min
completed: 2026-06-24
status: complete
---

# Phase 17 Plan 02: Text API Timing Extension Summary

**Extended POST /api/v1/text to accept optional speed/blinkPeriod/fadeSteps, validate positive integers in RequestValidators (not route handler), and forward to displayImmediate for firmware zones**

## Performance

- **Duration:** 389 min (includes previous context session)
- **Started:** 2026-06-24T05:44:39Z
- **Completed:** 2026-06-24T05:54:26Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- TextRequest DTO gains three nullable Int? fields (speed, blinkPeriod, fadeSteps), backward-compatible with existing consumers
- RequestValidators.validateTextRequest rejects speed/blinkPeriod/fadeSteps <= 0 with "must be a positive integer" — null fields pass (timing is optional)
- TextRoutes.textRoutes forwards timing to `displayImmediate(... speed = request.speed, blinkPeriod = request.blinkPeriod, fadeSteps = request.fadeSteps)` — no validation in the route (project coding rule)
- 5 new validation unit tests + 2 new API integration tests (positive timing → 202, negative speed → 422)

## Task Commits

1. **test(17-02)** `7afbe59` — failing validation tests for timing field positive-integer rule (RED)
2. **feat(17-02)** `bfc2027` — extend TextRequest with timing fields and add positive-integer validators (GREEN)
3. **feat(17-02)** `f938264` — forward timing from TextRoutes to displayImmediate and add API route tests

## Files Created/Modified
- `src/main/kotlin/com/anjo/model/TextRequest.kt` — adds speed/blinkPeriod/fadeSteps nullable fields with default null
- `src/main/kotlin/com/anjo/validation/RequestValidators.kt` — positive-integer guards added after existing blank/length checks
- `src/main/kotlin/com/anjo/routing/TextRoutes.kt` — displayImmediate call extended with named timing args
- `src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt` — 5 FunSpec tests covering reject-negative, reject-zero, allow-null, allow-positive
- `src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt` — 2 new tests: 202 with positive timing, 422 with negative speed

## Decisions Made
- Use nullable `Int? = null` on TextRequest rather than a wrapper class — avoids breaking existing JSON clients who omit these fields
- Zero is treated as non-positive (guard is `<= 0`) — intentional, speed/blinkPeriod/fadeSteps of 0 is meaningless for firmware animation

## Deviations from Plan

None — plan executed exactly as written. The `FirmwareZoneRoutesTest` "returns OFFLINE after disconnect" failure observed during the coverage gate check is a pre-existing race condition (Phase 16 commit `30989ac` attempted to fix it but it remains intermittent). Not caused by this plan's changes; scope boundary prevents auto-fix.

## Issues Encountered
- Pre-existing flaky test `FirmwareZoneRoutesTest.GET /ws/zone for a pre-registered FIRMWARE zone zone returns OFFLINE after disconnect` occasionally fails due to a race between WS close and session null-out. Not introduced by this plan. Full test suite passed on re-run.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- API wire protocol complete end-to-end: POST body → validation → displayImmediate → ZoneRegistry → FirmwareZoneDriver → FirmwareMessage JSON → WS frame
- Plans 17-03 (Pico firmware) and 17-04 (ESP32 firmware) can now parse speed/blinkPeriod/fadeSteps from the wire frame they receive

## Self-Check: PASSED
- `grep -c 'must be a positive integer' src/main/kotlin/com/anjo/validation/RequestValidators.kt` → 3
- `grep -c 'positive' src/main/kotlin/com/anjo/routing/TextRoutes.kt` → 0
- `./gradlew test --tests "com.anjo.routing.TextApiRouteTest" --tests "com.anjo.validation.RequestValidatorsTest" -x jacocoTestCoverageVerification` → BUILD SUCCESSFUL
- `./gradlew jacocoTestCoverageVerification` → BUILD SUCCESSFUL (coverage ≥ 70%)

---
*Phase: 17-firmware-skeletons*
*Completed: 2026-06-24*
