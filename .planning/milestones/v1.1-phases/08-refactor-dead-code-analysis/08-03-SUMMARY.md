---
phase: 08-refactor-dead-code-analysis
plan: "03"
subsystem: service
tags: [dead-code-removal, DisplaySelectionService, refactor, testing]
dependency_graph:
  requires: [08-01]
  provides: [DisplaySelectionService-clean]
  affects:
    - src/main/kotlin/com/anjo/service/DisplaySelectionService.kt
    - src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt
    - src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt
tech_stack:
  added: []
  patterns: [dead-code-deletion, test-assertion-removal]
key_files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/service/DisplaySelectionService.kt
    - src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt
    - src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt
decisions:
  - "Removed inline comment in createDriver() per no-comments project rule (pre-existing comment, not introduced by this plan)"
metrics:
  duration: "12 minutes"
  completed: "2026-06-15"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
requirements_addressed: [REF-01, REF-04]
---

# Phase 08 Plan 03: DisplaySelectionService Dead Code Removal Summary

**One-liner:** Deleted logCurrentData() Pi4J debug method, pendingSwitches ConcurrentLinkedQueue field and accessors, and all pending-switch test assertions from two test files, leaving selectDisplay() success branch and all real-behavior tests intact.

## What Was Built

### Task 1: DisplaySelectionService.kt cleanup

Deleted from `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt`:

- `import java.util.concurrent.ConcurrentLinkedQueue` (line 9)
- `private val pendingSwitches = ConcurrentLinkedQueue<String>()` field
- `private fun logCurrentData()` method (iterated ctx.providers/platforms/properties/registry via Pi4J debug calls)
- `logCurrentData()` call site in `selectDisplayAtStartup()`
- `logCurrentData()` call site in `selectDisplay()` success branch
- `pendingSwitches.offer(normalizedType)` call in `selectDisplay()` success branch
- `fun getPendingSwitches(): List<String>` public accessor
- `fun clearPendingSwitches()` public mutator

The `selectDisplay()` success branch after removal:
```kotlin
return if (newDriver != null) {
    currentDriver?.stop()
    currentDriver = newDriver
    currentType = normalizedType
    log.info("Driver switched: $normalizedType")
    true
} else {
```

Retained untouched: `currentDriver()`, `createDriver()`, `driverCache`, `getCurrentDisplayType()`, `normalizeDisplayType()`, `displayLock`, `defaultDriverFactory` companion object.

### Task 2: Test file cleanup

`DisplaySelectionServiceTest.kt` changes:
- Removed `import io.kotest.matchers.collections.shouldContainExactly` (no remaining usages)
- Removed `service.getPendingSwitches().isEmpty().shouldBeTrue()` from "should load startup driver from display config"
- Removed `service.getPendingSwitches() shouldContainExactly listOf("LCD")` from "should stop old driver and update type when switching display"
- Removed `service.getPendingSwitches().isEmpty().shouldBeTrue()` from "should keep current driver unchanged on failed switch"
- Deleted entire "should empty queue after clearing pending switches" test

Retained in the three kept tests: `selectDisplay("lcd") shouldBe true`, `currentDriver()` assertions, `getCurrentDisplayType()` assertions, `verify(exactly = 1) { maxDriver.stop() }`, `shouldBeFalse()` on failed switch.

`DriverIntegrationTest.kt` changes:
- Removed `import io.kotest.matchers.collections.shouldContainExactly` (no remaining usages)
- Deleted `selection.getPendingSwitches() shouldContainExactly listOf("LCD", "OLED")` line from "should switch between MAX7219 LCD and OLED drivers"
- Retained all `getCurrentDisplayType()`, `queueDisplaySwitch()`, and `currentDisplayType()` assertions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Removed pre-existing comment in createDriver()**
- **Found during:** Task 1
- **Issue:** The original `createDriver()` had `// Return cached driver to prevent Pi4J from rejecting duplicate hardware registrations` — the no-comments project rule applies to all code files, not just new code.
- **Fix:** Removed the comment during the Task 1 rewrite.
- **Files modified:** `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt`
- **Commit:** 53d5334

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Removed pre-existing inline comment in createDriver() | Project memory states "no comments in code files" — the rule applies to existing code as well as new additions |
| Kept shouldBeTrue import via booleans package | `shouldBeFalse()` remains in "should keep current driver unchanged on failed switch" so `import io.kotest.matchers.booleans.shouldBeFalse` stays; `shouldBeTrue` import removed since getPendingSwitches().isEmpty().shouldBeTrue() deleted |

## Test Results

```
DisplaySelectionServiceTest > should load startup driver from display config PASSED
DisplaySelectionServiceTest > should stop old driver and update type when switching display PASSED
DisplaySelectionServiceTest > should keep current driver unchanged on failed switch PASSED
DisplaySelectionServiceTest > should report UNKNOWN type when startup driver fails PASSED

DriverIntegrationTest > should switch between MAX7219 LCD and OLED drivers PASSED
DriverIntegrationTest > should reject unknown display type PASSED
```

All 6 tests pass. JaCoCo threshold failure when running only these two test classes in isolation is expected — the coverage gate requires the full suite and is enforced by plan 08-05.

## Known Stubs

None — this plan only deletes dead code and test assertions. No stubs introduced.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. Only code deletion.

## Self-Check: PASSED

- [x] `grep -n "logCurrentData\|pendingSwitches\|getPendingSwitches\|clearPendingSwitches\|ConcurrentLinkedQueue" src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` returns no matches
- [x] `DisplaySelectionService.kt` still contains `fun selectDisplay`, `fun currentDriver`, `fun getCurrentDisplayType`, `private fun createDriver`
- [x] `grep -rn "getPendingSwitches\|clearPendingSwitches" src/test` returns no matches
- [x] "should empty queue after clearing pending switches" test deleted
- [x] Three retained tests in DisplaySelectionServiceTest.kt still present with real-behavior assertions
- [x] Task 1 commit `53d5334` exists: `refactor(08-03): remove logCurrentData(), pendingSwitches, and accessors from DisplaySelectionService`
- [x] Task 2 commit `6728c87` exists: `refactor(08-03): remove pending-switch assertions from DisplaySelectionServiceTest and DriverIntegrationTest`
- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test --tests "com.anjo.service.DisplaySelectionServiceTest" --tests "com.anjo.driver.DriverIntegrationTest" -x jacocoTestCoverageVerification` exits 0
