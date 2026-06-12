---
phase: 06-max7219-hardware-fix
plan: 02
subsystem: driver
tags: [kotlin, abstract-class, dry-refactor, display-driver, kotest]

# Dependency graph
requires:
  - 06-01 (buildPacket extracted, Max7219Matrix tests passing)
provides:
  - AbstractDisplayDriver abstract base class with shared job/lastMessage/lastError state
  - Canonical stop()/status() implementations lifted from all three hardware drivers
  - isHardwareAvailable() abstract hook wired into status().hardwareAvailable
  - DRY driver hierarchy: Max7219Matrix, LcdDisplay, OledDisplay all extend AbstractDisplayDriver()
affects:
  - Phase 7+ (driver contracts stable; no more field duplication risk when adding new drivers)
  - Phase 11 multi-zone (new zone drivers can extend AbstractDisplayDriver directly)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Abstract base class with protected mutable state for shared driver lifecycle fields"
    - "Template Method pattern via isHardwareAvailable() hook in AbstractDisplayDriver.status()"
    - "DRY extraction: identical field declarations and method bodies lifted to base class"

key-files:
  created:
    - src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt
  modified:
    - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
    - src/main/kotlin/com/anjo/driver/LcdDisplay.kt
    - src/main/kotlin/com/anjo/driver/OledDisplay.kt

key-decisions:
  - "isHardwareAvailable() placed as protected abstract hook in AbstractDisplayDriver so status() can call it — concrete drivers supply driver-specific availability logic"
  - "Max7219Matrix.isHardwareAvailable() returns lastError == null (SPI has no nullable connection handle, unlike I2C)"
  - "LcdDisplay and OledDisplay.isHardwareAvailable() return i2c != null && lastError == null (I2C handle is null when init throws)"
  - "OfflineDisplayDriver intentionally NOT extended — it is a stateless object null-object; no lifecycle fields needed (D-06)"

requirements-completed: [HW-02]

# Metrics
duration: ~3min
completed: 2026-06-12
---

# Phase 06 Plan 02: AbstractDisplayDriver Refactor Summary

**AbstractDisplayDriver base class introduced; Max7219Matrix, LcdDisplay, and OledDisplay refactored to extend it — removing 15 duplicated field declarations and method bodies; all driver tests pass with JaCoCo >= 70%**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-06-12T20:40:31Z
- **Completed:** 2026-06-12T20:42:50Z
- **Tasks:** 2
- **Files created:** 1
- **Files modified:** 3

## Accomplishments

- Created `AbstractDisplayDriver.kt` — abstract class extending `DisplayDriver` with `protected var job/lastMessage/lastError`, `protected abstract fun isHardwareAvailable()`, and canonical `stop()`/`status()` implementations
- Refactored `Max7219Matrix` to extend `AbstractDisplayDriver()`: removed 3 private field declarations, removed `stop()` and `status()` override bodies, added `isHardwareAvailable() = lastError == null`, removed unused `import kotlinx.coroutines.Job`
- Refactored `LcdDisplay` to extend `AbstractDisplayDriver()`: same 5 changes; `isHardwareAvailable() = i2c != null && lastError == null`
- Refactored `OledDisplay` to extend `AbstractDisplayDriver()`: same 5 changes; same `isHardwareAvailable()` body as LcdDisplay
- `OfflineDisplayDriver.kt` left entirely unchanged per D-06 (stateless object : DisplayDriver)
- Full test suite BUILD SUCCESSFUL; JaCoCo >= 70% gate passed; all 6 Max7219MatrixTest, 3 OledDisplayTest, 4 LcdDisplayTest tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AbstractDisplayDriver.kt** - `92fd8d2` (feat)
2. **Task 2: Refactor 3 drivers to extend AbstractDisplayDriver** - `a3a00b5` (refactor)

## Files Created/Modified

- `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt` — NEW: abstract class with shared state and stop()/status()
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — Extends AbstractDisplayDriver(); removed job/lastMessage/lastError/stop()/status(); added isHardwareAvailable()
- `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` — Extends AbstractDisplayDriver(); same removals; added isHardwareAvailable() with i2c null check
- `src/main/kotlin/com/anjo/driver/OledDisplay.kt` — Extends AbstractDisplayDriver(); same removals; added isHardwareAvailable() with i2c null check

## Decisions Made

- `isHardwareAvailable()` is a `protected abstract fun` in `AbstractDisplayDriver` so `status()` can call it polymorphically — drivers differ only in their hardware-availability predicate
- Max7219Matrix uses `lastError == null` (SPI always creates a handle at `ctx.create()`; failure is caught in `init` and stored as `lastError`)
- LCD and OLED use `i2c != null && lastError == null` (I2C handle assignment is conditional on `ctx.create()` not throwing — `null` signals failed init)

## Deviations from Plan

None - plan executed exactly as written.

## Phase Success Criteria Check

- SC #1: `AbstractDisplayDriver.kt` exists, compiles; declares `abstract class AbstractDisplayDriver : DisplayDriver` with protected fields, abstract hook, stop(), status() — PASSED
- SC #2: All three drivers contain `: AbstractDisplayDriver()` — PASSED (grep confirmed)
- SC #3: No `private var job/lastMessage/lastError` in the three driver files — PASSED (grep confirmed none)
- SC #4: Each driver has exactly one `isHardwareAvailable()` override — PASSED
- SC #5: `OfflineDisplayDriver.kt` unchanged — PASSED (`object OfflineDisplayDriver : DisplayDriver` intact)
- SC #6: All 6 Max7219MatrixTest, 3 OledDisplayTest, 4 LcdDisplayTest tests pass — PASSED
- SC #7: Full test suite BUILD SUCCESSFUL, JaCoCo >= 70% — PASSED

## Known Stubs

None — no placeholder or hardcoded empty values introduced.

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The AbstractDisplayDriver shared state fields (job/lastMessage/lastError) are protected and accessible only to subclasses in the same module — no new external trust boundary introduced. All threats accepted per plan threat register (T-06-03 through T-06-SC).

## Self-Check: PASSED

- `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt` exists and contains `abstract class AbstractDisplayDriver : DisplayDriver`
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` contains `: AbstractDisplayDriver()` and `isHardwareAvailable()`
- `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` contains `: AbstractDisplayDriver()` and `isHardwareAvailable()`
- `src/main/kotlin/com/anjo/driver/OledDisplay.kt` contains `: AbstractDisplayDriver()` and `isHardwareAvailable()`
- `src/main/kotlin/com/anjo/driver/OfflineDisplayDriver.kt` contains `object OfflineDisplayDriver : DisplayDriver` (unchanged)
- Commit `92fd8d2` exists (Task 1)
- Commit `a3a00b5` exists (Task 2)
- Full test suite BUILD SUCCESSFUL with JaCoCo >= 70% coverage gate passed

---
*Phase: 06-max7219-hardware-fix*
*Completed: 2026-06-12*
