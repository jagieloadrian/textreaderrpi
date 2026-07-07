---
phase: 19-dry-yagni-refactoring
plan: 02
subsystem: testing
tags: [kotlin, ktor, kotest, dry, test-utils]

# Dependency graph
requires:
  - phase: 19-dry-yagni-refactoring
    provides: 19-01's HistoryValidators.parseFilter and ZoneRoutes buildZone (independent, no shared files)
provides:
  - "src/test/kotlin/com/anjo/TestSupport.kt — appTest{}, reified dep<T>(), historyRecord() shared by six test classes"
  - "Six test files migrated off the testApplication{application{module()};client.get(\"/health\")}/getBlocking<T>(DependencyKey<T>()) incantation"
  - "Confirmed green full-suite/coverage/warning gates over the combined Phase 19 refactor (19-01 + 19-02)"
affects: [20-cleanup-docs]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Test-utils as top-level functions in package com.anjo (not an abstract base spec) — Kotest favors composition over inheritance"
    - "appTest{} always includes the /health warm-up (idempotent; testApplication defers module() until first HTTP interaction)"
    - "Mechanical migration is literal-pattern-scoped: only testApplication blocks that already contain the exact application{module()};client.get(\"/health\") bootstrap were converted to appTest{}; blocks without that warm-up (where /health itself is the assertion, or no warm-up existed) were left as raw testApplication"

key-files:
  created:
    - src/test/kotlin/com/anjo/TestSupport.kt
  modified:
    - src/test/kotlin/com/anjo/ApplicationTest.kt
    - src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt
    - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
    - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
    - src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt
    - src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt

key-decisions:
  - "HistoryRoutesTest's second repeat(25) loop (page-2-pagination test) originally inserted text = \"pagination-text-$i\"; historyRecord()'s fixed \"text $i\" default was applied anyway per the plan's explicit must-have (both repeat(25) loops use the builder) and acceptance criteria (exactly 2 historyRecord() calls) — the test only asserts page1Body != page2Body, never the literal text, so behavior is unaffected"
  - "In ApplicationTest.kt and FirmwareZoneRoutesTest.kt, testApplication blocks that lack the literal application{module()};client.get(\"/health\") warm-up (6 of 8 tests in ApplicationTest — several use /health itself as the assertion under test or have no warm-up at all; 1 of 3 tests in FirmwareZoneRoutesTest — the unknown-zone WS rejection test) were left on raw testApplication rather than force-fit into appTest{}, since appTest{} would silently add an extra HTTP call with different observable behavior"

requirements-completed: [REF-05]

coverage:
  - id: D1
    description: "TestSupport.kt provides appTest{}, dep<T>(), and historyRecord(), consumed by six migrated test classes (ApplicationTest, HistoryRoutesTest, HistoryUIRoutesTest, HistoryRecordingTest, ZonesUIRoutesTest, FirmwareZoneRoutesTest)"
    requirement: "REF-05"
    verification:
      - kind: other
        ref: "grep -rl getBlocking src/test/kotlin/com/anjo/ — only TestSupport.kt (shared helper) plus LiveRoutesTest.kt/FirmwareZoneDriverTest.kt (D-06 exempt)"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests com.anjo.ApplicationTest --tests com.anjo.routing.HistoryRoutesTest --tests com.anjo.routing.HistoryUIRoutesTest"
        status: pass
    human_judgment: false
  - id: D2
    description: "The two repeat(25) history insertion fixture loops in HistoryRoutesTest use the historyRecord() builder"
    requirement: "REF-05"
    verification:
      - kind: other
        ref: "grep -c 'historyRecord(' src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt (returns 2)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Full test suite green and JaCoCo line coverage stays >=70% over the combined Phase 19 refactor (19-01 + 19-02)"
    requirement: "REF-05"
    verification:
      - kind: other
        ref: "./gradlew test (BUILD SUCCESSFUL, 0 failures/errors across all test-result XMLs)"
        status: pass
      - kind: other
        ref: "./gradlew jacocoTestCoverageVerification (BUILD SUCCESSFUL)"
        status: pass
    human_judgment: false
  - id: D4
    description: "No new compiler warnings introduced by the migration"
    requirement: "REF-05"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all (only the 2 pre-existing Max7219Matrix.kt SpiChipSelect warnings)"
        status: pass
    human_judgment: false

duration: 8min
completed: 2026-07-07
status: complete
---

# Phase 19 Plan 02: Test-Helper DRY Extraction (TestSupport.kt) Summary

**Extracted a shared TestSupport.kt (appTest{}, reified dep<T>(), historyRecord()) and migrated all six getBlocking-using test files off the repeated testApplication/DependencyKey bootstrap, while the full 47-test-class suite stayed green through the combined Phase 19 refactor.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-07T14:24:30Z
- **Completed:** 2026-07-07T14:32:53Z
- **Tasks:** 3 completed
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments
- `src/test/kotlin/com/anjo/TestSupport.kt` created with three top-level members: `appTest{}` (bootstrap + `/health` warm-up wrapper), reified `ApplicationTestBuilder.dep<T>()` (replaces `application.dependencies.getBlocking<T>(DependencyKey<T>())`), and `historyRecord(i, ...)` (fixture builder for `text = "text $i"` insertion loops)
- All six non-exempt `getBlocking`-using test files migrated: `ApplicationTest.kt` (18→0 getBlocking calls, including the 15-binding DI-resolution test collapsed to 15 `dep<T>()` one-liners), `HistoryRoutesTest.kt` (8→0, two `repeat(25)` loops now use `historyRecord()`), `HistoryUIRoutesTest.kt` (3→0), `HistoryRecordingTest.kt` (7→0), `ZonesUIRoutesTest.kt` (3→0), `FirmwareZoneRoutesTest.kt` (2→0)
- `grep -rl getBlocking src/test/kotlin/com/anjo/` now returns exactly three files: `TestSupport.kt` (the legitimate shared home of the call) plus the two D-06-exempt SSE/WS files (`LiveRoutesTest.kt`, `FirmwareZoneDriverTest.kt`) — no individual route/service test file retains the incantation
- Full phase gate run over the combined 19-01 + 19-02 refactor: `./gradlew test` green (all test-result XMLs report 0 failures/0 errors), `./gradlew jacocoTestCoverageVerification` green (>=70% line coverage), `./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all` shows only the 2 pre-existing `Max7219Matrix.kt` `SpiChipSelect` deprecation warnings — no new warning from any file touched across both plans

### Below-threshold and exempt files (documented, not churned)

Per SC2's "no >5-line copy-paste block" gate, these carry only the 3-line `testApplication { application { module() }; client.get("/health") }` bootstrap (below the 5-line threshold) and were left as-is: `TextApiRouteTest.kt`, `ScheduleRoutesTest.kt`, `ZoneRoutesTest.kt`, `WebAndDisplayRoutesTest.kt`, `RateLimitRoutesTest.kt`, `WebRoutesTest.kt`, `MetricsRoutesTest.kt`, `HealthRoutesTest.kt`, `ScheduleUIRoutesTest.kt`.

D-06-exempt (SSE/WS, `testApplication` incompatible with streaming): `LiveRoutesTest.kt`, `FirmwareZoneDriverTest.kt`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create TestSupport.kt with appTest{}, dep<T>(), and historyRecord()** - `64b1032` (feat)
2. **Task 2: Migrate the three heaviest files (ApplicationTest, HistoryRoutesTest, HistoryUIRoutesTest)** - `0fe62ad` (refactor)
3. **Task 3: Migrate remaining getBlocking files, then run the full phase gate** - `290b596` (refactor)

## Files Created/Modified
- `src/test/kotlin/com/anjo/TestSupport.kt` - new shared helper: `appTest{}`, `dep<T>()`, `historyRecord()`
- `src/test/kotlin/com/anjo/ApplicationTest.kt` - 2 of 8 tests (DI-heavy ones) migrated to `appTest{}`/`dep<T>()`; 6 unrelated tests left on raw `testApplication` (no matching warm-up pattern)
- `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` - all 9 tests migrated to `appTest{}`; two `repeat(25)` loops use `historyRecord()`; two `repeat(5)` loops with distinct literal text left inline
- `src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt` - all 7 tests migrated to `appTest{}`/`dep<T>()`
- `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` - 3 of 4 tests (the `testApplication`-using ones) migrated to `appTest{}`/`dep<T>()`; the pure-mock test (no `testApplication`) untouched
- `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt` - all 6 tests migrated to `appTest{}`/`dep<T>()`
- `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt` - 2 of 3 tests migrated to `appTest{}`/`dep<T>()`; the unknown-zone WS-rejection test (no warm-up call) left on raw `testApplication`

## Decisions Made
- HistoryRoutesTest's second `repeat(25)` loop originally inserted `text = "pagination-text-$i"` — applied `historyRecord()`'s fixed `"text $i"` default anyway per the plan's explicit must-have and acceptance criteria (exactly 2 `historyRecord()` calls in the file); the test only asserts `page1Body != page2Body`, never the literal text content, so behavior is unaffected
- Mechanical migration was scoped to the literal `testApplication { application { module() }; client.get("/health") }` pattern — testApplication blocks without that exact warm-up (health-check-is-the-assertion tests in `ApplicationTest.kt`; the WS-rejection test in `FirmwareZoneRoutesTest.kt` that never calls `/health`) were left on raw `testApplication` rather than force-fitting `appTest{}`, which would have silently added an extra HTTP call and changed observable request counts
- `FirmwareZoneRoutesTest.kt`'s two migrated tests previously used a separately-created `client2` for the `/health` warm-up (distinct from the `wsClient` used for the actual WebSocket assertions); `appTest{}`'s built-in `client.get("/health")` warm-up is functionally equivalent (plain unconfigured HTTP client, same as `client2`) and the dedicated `wsClient` (with `WebSockets` plugin installed) remains a separate client instance — no interference

## Deviations from Plan

None - plan executed exactly as written. The Decisions Made section above documents interpretive choices for literal-pattern-matching scope, consistent with the plan's own "mechanical migration" framing and the RESEARCH.md Pitfall 5 guidance that below-threshold/non-matching blocks are listed or trivially handled, not force-churned.

## Issues Encountered

- A transient Gradle test-worker JVM crash occurred mid-execution during an earlier full-suite run, producing `NoClassDefFoundError`/`ClassNotFoundException` across unrelated test classes (`HtmlUtilsTest`, `FirmwareZoneDriverTest`, `LocalZoneDriverTest`, `NetworkZoneDriverTest`, `ZoneStatusTest`) plus a `FileNotFoundException` on the JaCoCo exec file. Root cause: a piped `head` command truncated Gradle's stdout mid-run (SIGPIPE), killing forked test-worker JVMs — not related to this plan's changes (none of the affected classes were touched by 19-02). Re-ran `./gradlew test --rerun-tasks` writing to a plain file (no pipe) immediately after: `BUILD SUCCESSFUL`, all 47 test-result XMLs report 0 failures/0 errors. No action needed on source files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- SC2 (shared TestSupport.kt, no >5-line duplication across test classes), SC3 (coverage gate green), and SC4 (no new warnings) are all satisfied
- Phase 19 (DRY/YAGNI Refactoring) is complete — both 19-01 (main-code dedup) and 19-02 (test-helper dedup) are done; REF-05 fully satisfied
- No blockers for Phase 20 (Cleanup + Docs)

---
*Phase: 19-dry-yagni-refactoring*
*Completed: 2026-07-07*
