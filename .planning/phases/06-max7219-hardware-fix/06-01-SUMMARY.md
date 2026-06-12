---
phase: 06-max7219-hardware-fix
plan: 01
subsystem: driver
tags: [max7219, spi, kotlin, kotest, tdd, hardware, pi4j]

# Dependency graph
requires: []
provides:
  - buildPacket() pure internal companion object function in Max7219Matrix
  - Corrected SPI render direction via physicalD = numDevices - 1 - d
  - Removed broken size guards in write() and displayStatic()
  - Byte-exact and structural unit tests for buildPacket (6 total tests passing)
affects:
  - 06-02 (driver refactor — extends AbstractDisplayDriver, reuses buildPacket)
  - Phase 7+ (scroll direction now correct for multi-zone and scheduler testing)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "physicalD = numDevices - 1 - d for SPI daisy-chain direction correction"
    - "Extract pure function from hardware method to enable JVM unit testing without Pi4J"
    - "companion object internal fun for testable static-like helpers in Kotlin"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
    - src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt

key-decisions:
  - "buildPacket placed in companion object (not instance method) so tests call Max7219Matrix.buildPacket(...) without Pi4J construction"
  - "Per-row buildPacket(bitmap, offset, numDevices, row): ByteArray signature — called 8 times from render(), enables 2-byte assertions per row"
  - "Size guards removed from write() and displayStatic() — buildPacket handles short bitmaps safely via else false bounds check"

patterns-established:
  - "Pattern: SPI daisy-chain direction fix — physicalD = numDevices - 1 - d maps SPI slot index to physical left-to-right position"
  - "Pattern: Extract Pi4J-free pure function to enable JVM unit tests without hardware init"

requirements-completed: [HW-01]

# Metrics
duration: 3min
completed: 2026-06-12
---

# Phase 06 Plan 01: MAX7219 Render Direction Fix + buildPacket Extraction Summary

**SPI packet direction bug fixed via physicalD = numDevices - 1 - d; buildPacket() extracted as internal companion object pure function with byte-exact Kotest tests confirming packet[0]=0x01 and packet[1]=0x70 for 'A' row=0**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-06-12T20:34:20Z
- **Completed:** 2026-06-12T20:37:24Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Extracted `buildPacket()` as an `internal` companion object function in `Max7219Matrix` — pure, Pi4J-free, JVM-testable
- Fixed SPI render direction: `physicalD = numDevices - 1 - d` ensures d=0 (SPI first slot, physically rightmost module) reads higher bitmap columns
- Removed both broken size guards — `write()` and `displayStatic()` now call `render(bitmap, 0)` unconditionally; short messages now render correctly
- Added 2 new buildPacket tests (byte-exact + structural) — 6 total tests pass, full suite BUILD SUCCESSFUL with JaCoCo ≥70%

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: add failing buildPacket tests** - `af5308c` (test)
2. **Task 1 GREEN + Task 2: fix render(), extract buildPacket(), remove write guards** - `82c01f0` (feat)

_Note: TDD RED phase committed first (failing tests). GREEN phase committed the production implementation. Task 2 tests were already in the RED commit._

## Files Created/Modified

- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — Added `buildPacket()` internal companion fun; simplified `render()` to delegate to it; removed size guards from `write()` and `displayStatic()`; removed commented-out visibleWidth line
- `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` — Added 2 new buildPacket tests (byte-exact numDevices=1 and structural numDevices=2); 4 existing tests unchanged

## Decisions Made

- `buildPacket` placed in companion object (not as instance method) so tests can call `Max7219Matrix.buildPacket(...)` without constructing a `Max7219Matrix` instance (which requires Pi4J hardware init)
- Per-row signature `buildPacket(bitmap, offset, numDevices, row): ByteArray` chosen — called 8 times from `render()`, enables precise 2-byte per-row assertions in tests
- Bitmap inlined as literal in tests (`byteArrayOf(126,17,17,17,126,0,...,0)`) rather than importing Font — per plan guidance to avoid Font dependency in test

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- The plan's verify command `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest" -x jacocoTestReport` triggered `jacocoTestCoverageVerification` which failed with 0.00 coverage (expected since only Max7219MatrixTest was run and coverage measured full suite). Added `-x jacocoTestCoverageVerification` for per-task verification. Full wave gate `./gradlew test jacocoTestReport` passed correctly with BUILD SUCCESSFUL.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `buildPacket()` is ready for use in Plan 02 (AbstractDisplayDriver refactor)
- Plan 02 will add `AbstractDisplayDriver`, extend all three hardware drivers, remove duplicated `job`/`lastMessage`/`lastError` fields
- HW-01 (scroll renders left-to-right) is satisfied; HW-02 (driver refactor) is addressed in Plan 02

## Self-Check: PASSED

- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` exists and contains `physicalD = numDevices - 1 - d`
- `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` exists and contains `buildPacket`
- Commit `af5308c` exists (RED)
- Commit `82c01f0` exists (GREEN)
- Full test suite BUILD SUCCESSFUL with JaCoCo ≥70% coverage gate passed

---
*Phase: 06-max7219-hardware-fix*
*Completed: 2026-06-12*
