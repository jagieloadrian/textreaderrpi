---
phase: 09-display-history-audit-log
plan: 02
subsystem: service
tags: [kotlin, ktor, kotest, history, di, integration-test, tdd]

requires:
  - phase: 09-display-history-audit-log
    plan: 01
    provides: HistoryRepository, HistoryRecord, HistoryTable, V3 migration

provides:
  - history recording in displayImmediate (INTERRUPT + SKIP_NEW paths, source=IMMEDIATE)
  - history recording in displayScheduled (INTERRUPT + SKIP_NEW paths, source=SCHEDULED)
  - effect: Effect parameter added to displayScheduled signature
  - HistoryRepository injected into ScreenDriverService via DI constructor arg
  - HistoryRepository registered in DI container (provide { historyRepository })
  - DI smoke test extended with HistoryRepository resolution assertion
  - HistoryRecordingTest: 4 integration tests via real wired testApplication

affects:
  - 09-03 (history API routes and UI page — HistoryRepository now accessible from DI)

tech-stack:
  added: []
  patterns:
    - "historyRepository?.insert() wrapped in try/catch — non-fatal DB errors swallowed at WARN"
    - "effect: Effect before conflictPolicy in displayScheduled to allow default-arg callers"
    - "integration tests resolve screenService and historyRepo from application.dependencies via getBlocking"
    - "V3 Flyway migration uses quoted 'source' to fix H2 PostgreSQL mode case-sensitivity"

key-files:
  created:
    - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/service/SchedulerService.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
    - src/test/kotlin/com/anjo/ApplicationTest.kt
    - src/main/resources/db/migration/V3__add_history_table.sql
    - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
    - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt

key-decisions:
  - "effect: Effect placed before conflictPolicy in displayScheduled so existing callers with default conflictPolicy still compile"
  - "historyRepository instantiated before screenDriverService in DI to satisfy Kotlin forward-reference constraint"
  - "V3 migration source column quoted as 'source' (not unquoted source) to ensure H2 PostgreSQL mode stores it as lowercase; Exposed generates quoted lowercase in INSERT/SELECT"

patterns-established:
  - "all insert paths: try { historyRepository?.insert(...) } catch (e: Exception) { log.warn(...) } — non-fatal pattern"
  - "displayScheduled effect threading: new effect param passed from SchedulerService.fire() via schedule.effect"

requirements-completed: [HIST-01]

duration: 11min
completed: 2026-06-15
---

# Phase 09 Plan 02: History Recording Wiring + DI + Integration Tests Summary

**history recording injected into both display paths (IMMEDIATE + SCHEDULED), DI-wired, non-fatal on insert failure, proven by 4 integration tests against real H2**

## Performance

- **Duration:** 11 min
- **Started:** 2026-06-15T17:04:00Z
- **Completed:** 2026-06-15T17:15:00Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added `historyRepository?.insert(HistoryRecord(...))` in 4 locations in ScreenDriverService: displayImmediate INTERRUPT path, displayImmediate SKIP_NEW path, displayScheduled INTERRUPT path, displayScheduled SKIP_NEW path
- All 4 insert calls wrapped in `try/catch` — a DB hiccup never breaks the display return value (D-03, T-09-05)
- Dropped SKIP_NEW requests (early `return false`) insert nothing (D-04)
- `displayScheduled()` now takes `effect: Effect` parameter before `conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT`
- `SchedulerService.fire()` updated to pass `schedule.effect` to displayScheduled
- `DependencyInjection.configureDI()` instantiates `HistoryRepository`, injects it into `ScreenDriverService`, and registers it with `provide { historyRepository }`
- `ApplicationTest` smoke test extended with `HistoryRepository` resolution assertion
- `HistoryRecordingTest` (4 integration tests via `testApplication { application { module() } }`): IMMEDIATE record written, SCHEDULED record written with scheduleId, no extra record on SKIP_NEW, insert exception non-fatal
- All 6 existing `SchedulerServiceTest` + `ConflictPolicyTest` call sites updated to 5-arg displayScheduled signature
- Full test suite passes including JaCoCo 70% gate

## Task Commits

TDD tasks have multiple commits (test RED then feat GREEN):

1. **Task 1 - TDD RED: failing unit tests for history recording** - `e70a4db` (test)
2. **Task 1 - TDD GREEN: history recording + displayScheduled effect param + SchedulerService call site** - `808292b` (feat)
3. **Task 2 - TDD RED: integration tests via testApplication (fail because DI not wired)** - `3d1bf2b` (test)
4. **Task 2 - TDD GREEN: DI wiring + ApplicationTest assertion + V3 migration fix** - `33c55e7` (feat)

## Files Created/Modified

- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — 4 insert calls added (2 immediate paths + 2 scheduled paths); `effect: Effect` param added to displayScheduled
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — fire() passes `schedule.effect` to displayScheduled
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — HistoryRepository instantiated, injected into ScreenDriverService, provided in DI container
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — HistoryRepository DI resolution assertion added
- `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` — 4 integration tests via real wired application
- `src/main/resources/db/migration/V3__add_history_table.sql` — quoted `"source"` column to fix H2 PostgreSQL mode case-sensitivity
- `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` — 4 call sites updated to 5-arg displayScheduled
- `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — 12 mock matcher/stubbing call sites updated to 5-arg displayScheduled

## Decisions Made

- **`effect: Effect` before `conflictPolicy` in displayScheduled**: Places new required param before the existing optional param; callers that use positional args still compile; callers that rely on the default `conflictPolicy` can omit it.
- **`historyRepository` instantiated before `screenDriverService` in DI**: Kotlin requires forward-referenced vals to be declared earlier. The plan said "follow the same position as scheduleRepository" but that position was after screenDriverService — moved it before to satisfy the compiler.
- **V3 migration `"source"` quoting**: H2 in `MODE=PostgreSQL` stores unquoted identifiers as UPPERCASE internally. Exposed generates `"source"` (lowercase quoted) in INSERT/SELECT statements. Without quoting in the DDL, H2 stores the column as `SOURCE`, causing a mismatch when Exposed queries with `"source"`. Quoting the DDL column name as `"source"` forces H2 to store it as lowercase, matching Exposed's query generation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ConflictPolicyTest and SchedulerServiceTest call sites broken by new displayScheduled signature**
- **Found during:** Task 1 GREEN (compile step)
- **Issue:** Adding `effect: Effect` to `displayScheduled()` broke 16 call sites in ConflictPolicyTest (2 direct calls + 2 mock matchers) and SchedulerServiceTest (12 mock matchers/stubs)
- **Fix:** Updated all 16 call sites to pass `Effect.SCROLL` or add `any()` matcher as appropriate
- **Files modified:** ConflictPolicyTest.kt, SchedulerServiceTest.kt
- **Verification:** `./gradlew compileKotlin compileTestKotlin` exits 0
- **Committed in:** 808292b

**2. [Rule 1 - Bug] H2 PostgreSQL mode case-sensitivity breaks Exposed column resolution**
- **Found during:** Task 2 GREEN (integration test run)
- **Issue:** H2 with `MODE=PostgreSQL` stores unquoted `source` column as `SOURCE` internally. Exposed generates `"source"` (lowercase quoted) in INSERT/SELECT. In H2 PostgreSQL mode, quoted identifiers are case-sensitive, so `"source"` != `SOURCE` → `Column "source" not found`
- **Fix:** Updated V3 Flyway migration to use `"source"` (quoted) instead of `source` (unquoted), forcing H2 to store it as lowercase — matching what Exposed generates
- **Files modified:** src/main/resources/db/migration/V3__add_history_table.sql
- **Verification:** All 4 integration tests in HistoryRecordingTest pass
- **Committed in:** 33c55e7

**3. [Rule 2 - Missing critical functionality] historyRepository must be instantiated before screenDriverService in DI**
- **Found during:** Task 2 GREEN (compile step)
- **Issue:** Plan said to add `historyRepository` at the same position as `scheduleRepository` (after screenDriverService), but `historyRepository` is used as a constructor argument to `screenDriverService` — Kotlin requires it to be declared before use
- **Fix:** Moved `val historyRepository = HistoryRepository()` to before the `screenDriverService` constructor block
- **Files modified:** src/main/kotlin/com/anjo/di/DependencyInjection.kt
- **Committed in:** 33c55e7

---

**Total deviations:** 3 auto-fixed (2 Rule 1 bugs, 1 Rule 2 forward-reference)  
**Impact on plan:** All fixes necessary for compilation and correct behavior. No scope creep.

## Known Stubs

None — all display paths are fully wired with real HistoryRepository inserts.

## Threat Flags

No new security surface introduced beyond what was in the plan's threat model (T-09-05 mitigated, T-09-06 accepted). The `effect: Effect` enum parameter eliminates free-text injection risk — residual risk LOW as documented in the plan threat model.

## Self-Check

Files created/modified:
- [x] src/main/kotlin/com/anjo/service/ScreenDriverService.kt — FOUND
- [x] src/main/kotlin/com/anjo/service/SchedulerService.kt — FOUND
- [x] src/main/kotlin/com/anjo/di/DependencyInjection.kt — FOUND
- [x] src/test/kotlin/com/anjo/ApplicationTest.kt — FOUND
- [x] src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt — FOUND

Commits verified:
- [x] e70a4db — test(09-02): TDD RED unit tests
- [x] 808292b — feat(09-02): history recording + effect param
- [x] 3d1bf2b — test(09-02): TDD RED integration tests
- [x] 33c55e7 — feat(09-02): DI wiring + V3 fix

## Self-Check: PASSED

---
*Phase: 09-display-history-audit-log*
*Completed: 2026-06-15*
