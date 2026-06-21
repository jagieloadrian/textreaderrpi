# Phase 6: MAX7219 Hardware Fix - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 06-max7219-hardware-fix
**Areas discussed:** write() static display, Refactor scope (HW-02), Test coverage strategy, Single-device probe (SC #2)

---

## write() static display

| Option | Description | Selected |
|--------|-------------|----------|
| Always render, clip if needed | Remove guard entirely; any text renders — short text fills leftmost columns | |
| Fix guard to numDevices check | Keep guard but fix: `bitmap.size >= numDevices * 8` | |
| You decide | Planner picks most sensible approach | ✓ |

**User's choice:** You decide (planner picks)

| Option | Description | Selected |
|--------|-------------|----------|
| Same as displayStatic | write() is for static text without animation | ✓ |
| Fill-display mode only | write() only for text that fills the full matrix | |
| Not critical | Only scrollText() and displayStatic() matter | |

**User's choice:** Same as displayStatic — render any text without animation

| Option | Description | Selected |
|--------|-------------|----------|
| Keep both | write() is interface method, displayStatic() is convenience override | |
| Remove write() from interface | Cleaner interface — one method for static display | ✓ (then reverted) |
| Not now — Phase 8 | Leave interface alone in Phase 6 | |

**User's choice:** Initially chose "Remove write() from interface", then reverted to keeping write() in Phase 6. Interface cleanup deferred to Phase 8 Refactor.

---

## Refactor scope (HW-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Max7219Matrix only | LCD/OLED structurally clean; focus on broken hardware | |
| All 4 drivers | Full driver layer pass | ✓ |
| Max7219Matrix + Offline only | LCD/OLED deferred to Phase 8 | |

**User's choice:** All 4 drivers

| Option | Description | Selected |
|--------|-------------|----------|
| Code review pass only | Identify and remove suspicious code; no restructuring | |
| Full simplification — DRY | Extract common patterns across LCD/OLED | ✓ |
| Compiler warnings + dead code only | Minimal touch | |

**User's choice:** Full simplification with DRY applied

| Option | Description | Selected |
|--------|-------------|----------|
| Abstract base class (AbstractDisplayDriver) | LCD/OLED extend a base holding shared state | ✓ |
| Delegation to DriverState helper | Composition: each driver holds a DriverState | |
| You decide | Planner picks | |

**User's choice:** Abstract base class

| Option | Description | Selected |
|--------|-------------|----------|
| Max7219Matrix extends base | All concrete drivers on same hierarchy | ✓ |
| Max7219Matrix stays standalone | SPI-based, different enough | |
| You decide | Planner assesses fit | |

**User's choice:** Yes — Max7219Matrix also extends AbstractDisplayDriver

| Option | Description | Selected |
|--------|-------------|----------|
| Stay as object — stateless | OfflineDisplayDriver is a null-object; singleton is correct | ✓ |
| Convert to class for consistency | All drivers as class instances | |
| You decide | | |

**User's choice:** Stay as object

| Option | Description | Selected |
|--------|-------------|----------|
| Same driver package — flat | AbstractDisplayDriver alongside concrete classes | ✓ |
| New driver.base sub-package | Cleaner if driver count grows | |
| You decide | | |

**User's choice:** Same driver package — keep it flat

---

## Test coverage strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Extract buildPacket() as pure function | No SPI dependency; directly unit-testable | ✓ |
| Fake SPI capture interface | WritableBuffer wrapping spi.write(); inject FakeSpi in tests | |
| You decide | | |

**User's choice:** Extract buildPacket() as a pure function

| Option | Description | Selected |
|--------|-------------|----------|
| Assert actual byte values | Hardcode expected packet for 'A' at row 0 | |
| Assert structural properties only | Verify leftmost module gets lower columns | |
| Both — byte-exact + structural | One regression pin + one semantic test | ✓ |

**User's choice:** Both

| Option | Description | Selected |
|--------|-------------|----------|
| Expand existing Max7219MatrixTest | Add test cases to existing file | ✓ |
| New Max7219RenderTest file | Separate file for render logic | |
| You decide | | |

**User's choice:** Expand existing Max7219MatrixTest

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — abstract test class for base driver | Parametrized Kotest spec for all subclasses | |
| No — existing mockk contract test is enough | AbstractDisplayDriver is an implementation detail | ✓ |

**User's choice:** No abstract test class — mockk contract test is sufficient

---

## Single-device probe (SC #2)

| Option | Description | Selected |
|--------|-------------|----------|
| Unit test for numDevices=1 | buildPacket with numDevices=1 asserts exact SPI bytes | ✓ |
| New debug endpoint GET /debug/led-probe | API endpoint sends test pattern to device 0 | |
| Manual test guide in README | Document: wire one module, POST text, verify | |

**User's choice:** Unit test that verifies packet bytes for numDevices=1

---

## Claude's Discretion

- Exact form of the render() fix: reversing iteration (`numDevices - 1 downTo 0` + adjusted packet index) vs. `physicalD = numDevices - 1 - d` inside the current loop — pick the more readable option.
- Whether `write()` calls `buildPacket()` directly or delegates to `displayStatic()` internally.
- Error handling in `buildPacket()` for out-of-bounds bitmap access.

## Deferred Ideas

- Remove `write()` from `DisplayDriver` interface, unify with `displayStatic()` — Phase 8 Refactor is the right time for this interface cleanup.
