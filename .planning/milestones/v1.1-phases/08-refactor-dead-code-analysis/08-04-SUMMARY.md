---
phase: 08-refactor-dead-code-analysis
plan: "04"
subsystem: service,driver,utils
tags: [refactor, rename, dead-code, font, tdd, bounds-safety]
dependency_graph:
  requires: [08-01]
  provides: [ScreenDriverService-renamed, Font-getChar-safe-lookup, FontTest-coverage]
  affects:
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/utils/Font.kt
    - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
    - src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt
    - src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt
    - src/test/kotlin/com/anjo/utils/FontTest.kt
tech_stack:
  added: []
  patterns: [tdd-red-green, safe-map-lookup-fallback, git-mv-rename]
key_files:
  created:
    - src/test/kotlin/com/anjo/utils/FontTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/utils/Font.kt
    - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
    - src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt
    - src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt
decisions:
  - "git mv used for rename so git history preserves file lineage (ScreenDriver.kt -> ScreenDriverService.kt)"
  - "ByteArray(5) fallback width matches all 5-byte glyph entries in asciiFont — not 8 (8 refers to display height)"
  - "Metric key strings textreaderrpi.screenDriver.readInput.* left unchanged — they live in ScreenDriverMetrics and are independent of the method name"
  - "TDD protocol followed: failing FontTest committed as RED before Font.getChar() implementation"
metrics:
  duration: "7 minutes"
  completed: "2026-06-15"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 5
  files_created: 1
requirements_addressed: [REF-01, REF-02, REF-04]
---

# Phase 08 Plan 04: ScreenDriver Rename, readInput Removal, Font Bounds Fix Summary

**One-liner:** ScreenDriver.kt renamed to ScreenDriverService.kt with readInput() deleted and test call sites migrated; Font.getChar() safe bounds fallback added with TDD coverage; Max7219Matrix force-unwrap removed.

## What Was Built

### Task 1: Rename ScreenDriver.kt → ScreenDriverService.kt + remove readInput()

- Used `git mv` to rename the service file, preserving git history; class name `ScreenDriverService` was already correct
- Deleted the `readInput(suspend fun readInput(input: String) { displayImmediate(input) })` one-line wrapper method
- Migrated 5 call sites in `ScreenDriverRecoveryTest.kt` from `readInput(...)` to `displayImmediate(...)`
- Migrated 3 call sites in `ScreenDriverResourceTest.kt` from `readInput(...)` to `displayImmediate(...)`
- Left metric key strings `textreaderrpi.screenDriver.readInput.*` in `ScreenDriverMetrics.kt` untouched (they are independent of method name, as specified)

### Task 2: Font.getChar() + Max7219Matrix wiring + FontTest (TDD)

**RED phase (commit 2384f1c):** Created `FontTest.kt` with 3 failing tests exercising `Font.getChar()` before the function existed. Build failed at compile time with "Unresolved reference 'getChar'".

**GREEN phase (commit 6109d4e):**
- Added `fun getChar(char: Char): ByteArray = asciiFont[char] ?: ByteArray(5) { 0 }` to `Font.kt` before the closing `}` of the object
- Replaced `Font.asciiFont[c] ?: Font.asciiFont[' ']!!` in `Max7219Matrix.buildBitmap()` with `Font.getChar(c)`
- All 3 FontTest tests pass; Max7219MatrixTest continues to pass

## Deviations from Plan

None — plan executed exactly as written.

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| ByteArray(5) fallback width | All existing glyph entries are 5-byte column bitmaps; 8 in CONTEXT.md refers to display height not glyph width |
| Metric strings left unchanged | textreaderrpi.screenDriver.readInput.* live in ScreenDriverMetrics, not derived from method name |
| TDD RED committed at compile-error | Demonstrates intent; GREEN commit makes all 3 tests pass immediately |

## Test Results

```
FontTest > should return mapped glyph for known character PASSED
FontTest > should return blank ByteArray for unmapped character PASSED
FontTest > should return space glyph for space character PASSED
Max7219MatrixTest: all existing tests PASSED
ScreenDriverRecoveryTest: all existing tests PASSED
ScreenDriverResourceTest: all existing tests PASSED

./gradlew clean test — BUILD SUCCESSFUL (full suite + JaCoCo gate)
```

## Known Stubs

None — all implementations are complete. No placeholder data flows.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. The `Font.getChar()` change reduces the threat surface by eliminating the `!!` force-unwrap in Max7219Matrix.

## Self-Check: PASSED

- [x] `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` exists; `ScreenDriver.kt` does not
- [x] `grep -rn "readInput(" src/main/ src/test/` returns only metric key strings (no call sites)
- [x] `Font.kt` contains `fun getChar(char: Char): ByteArray = asciiFont[char] ?: ByteArray(5) { 0 }`
- [x] `Max7219Matrix.kt` contains `Font.getChar(c)` and no `asciiFont[' ']!!`
- [x] `FontTest.kt` exists with 3 tests covering mapped, unmapped, and space character paths
- [x] Commits: `eb3547b` (Task 1), `2384f1c` (RED), `6109d4e` (GREEN)
- [x] `./gradlew clean test` BUILD SUCCESSFUL with JaCoCo gate passing
