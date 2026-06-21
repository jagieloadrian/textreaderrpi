---
phase: 07-scheduler-schema-stabilisation
plan: 03
subsystem: testing
tags: [kotest, mockk, jacoco, h2, ktor-test]

requires:
  - phase: 07-01
    provides: ConflictPolicy enum, ScheduleStatus.ERROR, 4 new Schedule fields, updateFiredAtAndDone, findAllActive ONESHOT filter
  - phase: 07-02
    provides: SKIP_NEW guard in ScreenDriver, fire():Boolean return, CRON ERROR persistence, webhookUrl validator

provides:
  - Automated test coverage for all 4 requirements (SCHED-01..04)
  - ScheduleRepositoryTest: new-column round-trip, ONESHOT firedAt filter, updateFiredAtAndDone atomic, RECURRING-not-excluded
  - SchedulerServiceTest: ERROR-not-loaded, SKIP_NEW maxRuns guard, displayScheduled relaxed-mock fix
  - ConflictPolicyTest: SKIP_NEW drops when busy, INTERRUPT regression passes
  - ScheduleRoutesTest: invalid-CRON ERROR persistence (GET confirms row), webhookUrl 422/201
  - TextApiRouteTest: SKIP_NEW deserialization + accepted response shape
  - Full suite green; JaCoCo LINE >= 70% passes

affects: [future phases touching scheduler, routing, or test infrastructure]

tech-stack:
  added: []
  patterns: [coEvery default-return stubs in beforeEach for suspend Boolean mocks, 4-arg mockk matchers for functions with default params]

key-files:
  created: []
  modified:
    - src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt
    - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
    - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
    - src/test/kotlin/com/anjo/routing/ScheduleRoutesTest.kt
    - src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt

key-decisions:
  - "displayScheduled returns Boolean (after 07-02 fire() refactor) — beforeEach must stub coEvery returns true or maxRuns loop OOMs on virtual time"
  - "3-arg coVerify calls compile to 4-arg via Kotlin default-param fill; tests work but beforeEach stub is still needed for correct run-counting semantics"
  - "CRON ERROR persistence test extends existing 422 test with a GET assertion — avoids duplicating the POST body"

patterns-established:
  - "Pattern 1: For suspend Boolean mocks used in loop conditions, always stub coEvery returns true in beforeEach — relaxed false breaks run-counting logic"
  - "Pattern 2: testApplication { application { module() } } for full integration route tests with live DB (H2)"

requirements-completed: [SCHED-01, SCHED-02, SCHED-03, SCHED-04]

duration: ~45min (including OOM diagnosis and fix)
completed: 2026-06-14
---

# Plan 07-03: Test Coverage Summary

**Automated tests for all four SCHED requirements — suite green and JaCoCo LINE >= 70% passing after diagnosing a pre-existing OOM caused by missing `displayScheduled` mock stub**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-06-14T21:44Z
- **Completed:** 2026-06-14T23:47Z
- **Tasks:** 3
- **Files modified:** 5 test files

## Accomplishments

- Extended five existing test files with 14 new test cases covering SCHED-01 through SCHED-04
- Diagnosed and fixed pre-existing OOM in `SchedulerServiceTest`: relaxed mock returned `false` for `displayScheduled`, preventing `runs` from incrementing and causing the `maxRuns` loop to exhaust 10 virtual minutes of heap
- Full suite green (`./gradlew test --no-daemon` → `BUILD SUCCESSFUL`); JaCoCo LINE coverage gate (≥70%) passes

## Task Commits

1. **Task 1: Repository + scheduler unit tests** — `b0a0b19` (test)
2. **Task 2: SKIP_NEW conflict + CRON ERROR + webhookUrl route/integration tests** — `2f7bcd6` (test)
3. **Task 3: Full-suite green + coverage gate (OOM fix)** — `a0a8572` (test)

## Files Created/Modified

- `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` — new-column round-trip, ONESHOT firedAt filter, updateFiredAtAndDone atomic, RECURRING-not-excluded
- `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — ERROR-not-loaded, SKIP_NEW maxRuns guard; OOM fix via `coEvery displayScheduled returns true` in beforeEach
- `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` — SKIP_NEW drops when mutex locked, INTERRUPT regression
- `src/test/kotlin/com/anjo/routing/ScheduleRoutesTest.kt` — invalid CRON persists ERROR row (GET verify), webhookUrl 422/201
- `src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt` — SKIP_NEW POST deserialization, 202 + accepted field

## Decisions Made

- Added `coEvery { mockScreen.displayScheduled(any(), any(), any(), any()) } returns true` to `beforeEach` rather than patching individual tests — the stub applies globally so any future test with a maxRuns loop is safe by default
- Did not modify the production `displayScheduled` or scheduler source; OOM was a test-infrastructure defect only

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule — Blocking] SchedulerServiceTest OOM from missing displayScheduled stub**

- **Found during:** Task 3 (full suite run)
- **Issue:** `displayScheduled` was changed to return `Boolean` in 07-02 (via `fire():Boolean`). The relaxed mock returned `false` (default Boolean). With `displayed=false`, `runs` never incremented, the `maxRuns` loop ran for all 10 virtual minutes of `advanceTimeBy`, exhausting JVM heap. Gradle reported the test as "passed" (OOM killed the JVM before `coVerify` could report failure) then emitted synthetic `OutOfMemoryError` and `executionError` test cases.
- **Fix:** `coEvery { mockScreen.displayScheduled(any(), any(), any(), any()) } returns true` added to `beforeEach` — one line. With `fire()=true`, `runs` increments correctly and the loop exits after `maxRuns=2` fires.
- **Files modified:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt`
- **Verification:** `./gradlew test --no-daemon` → `BUILD SUCCESSFUL in 52s`, 0 OOM events
- **Committed in:** `a0a8572` (Task 3)

---

**Total deviations:** 1 auto-fixed (1 blocking test-infrastructure defect)
**Impact on plan:** Fix essential for a green full-suite run. No scope creep; no production code modified.

## Issues Encountered

Pre-existing OOM in `SchedulerServiceTest > should stop RECURRING schedule after maxRuns`. The previous executor (plan run) had noted the OOM ("Reordered SchedulerServiceTest to run new tests before OOM-triggering long test") but the underlying cause was the missing `coEvery` stub, not test ordering. Root cause diagnosed and fixed.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All four SCHED requirements have passing automated tests
- Full suite green with JaCoCo LINE ≥ 70%
- Phase 07 execution complete; ready for phase verification

---
*Phase: 07-scheduler-schema-stabilisation*
*Completed: 2026-06-14*
