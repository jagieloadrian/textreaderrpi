# Phase 6: MAX7219 Hardware Fix - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix scroll direction on the MAX7219 two-module chain so text scrolls left-to-right, and refactor all four display drivers to eliminate workarounds and extract shared logic into an AbstractDisplayDriver base class.

</domain>

<decisions>
## Implementation Decisions

### HW-01: Render Direction Fix (Max7219Matrix)

- **D-01:** Fix `render()` by correcting the device-to-column mapping. SPI sends `d=0` first, which ends up at the physically last (rightmost) module. Correct fix: map `d=0` in SPI order to the rightmost module's columns (the higher-offset half of the bitmap), and `d=numDevices-1` to the leftmost module's columns (the lower-offset half). The STATE.md pitfall note describes this as "iterate `numDevices - 1 downTo 0`" — either approach (reversing iteration + adjusting packet index, or adding a `physicalD = numDevices - 1 - d` inside the current loop) is acceptable.
- **D-02:** Extract `buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int): ByteArray` as a pure function (no SPI dependency). `render()` calls `buildPacket()` then `spi.write()`. This enables unit testing without Pi4J.
- **D-03:** `write()` should behave identically to `displayStatic()` — remove the broken guard `bitmap.size / 8 >= numDevices * 8` and always attempt to render. Short text fills leftmost columns; rest stays blank.

### HW-02: Driver Refactor (All 4 Drivers)

- **D-04:** Introduce `AbstractDisplayDriver` (abstract class) in the `com.anjo.driver` package (flat — no sub-packages). It holds the shared state (`job`, `lastMessage`, `lastError`) and provides default implementations of `stop()` and `status()`.
- **D-05:** `Max7219Matrix`, `LcdDisplay`, and `OledDisplay` all extend `AbstractDisplayDriver`. Each retains its hardware-specific logic; shared boilerplate is removed.
- **D-06:** `OfflineDisplayDriver` stays as an `object` (intentionally stateless null-object — no inheritance needed).
- **D-07:** Refactor depth is full DRY simplification — not just dead code removal. Remove duplication in scroll loop patterns, init patterns, and error tracking across LCD and OLED.

### Test Coverage

- **D-08:** Add tests to existing `Max7219MatrixTest`:
  1. One byte-exact test: `buildPacket` for a known character (e.g., 'A') at `offset=0`, `numDevices=1` — assert the exact 2-byte SPI packet. Satisfies SC #2 (single-device probe) and SC #4 (no skipped byte ordering assertions).
  2. One structural test: for `numDevices=2`, verify that `buildPacket` assigns lower bitmap columns (0–7) to the leftmost module's SPI slot and higher columns (8–15) to the rightmost.
- **D-09:** No abstract test class for `AbstractDisplayDriver` — the existing driver contract test (mockk) is sufficient.

### Claude's Discretion

- Exact implementation form for the `render()` fix (reversing iteration vs. `physicalD` variable) — pick whichever is cleaner and more readable.
- Whether `write()` calls `buildPacket()` directly or delegates to `displayStatic()` internally.
- Error handling in `buildPacket()` for out-of-bounds bitmap access (keep the existing `else false` guard or handle differently).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Hardware — HW-01 and HW-02 requirements (scroll direction + driver refactor)
- `.planning/ROADMAP.md` §"Phase 6: MAX7219 Hardware Fix" — success criteria (all 4 must be TRUE)

### Codebase
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — current broken render() and write() implementations
- `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` — interface definition (do NOT change in Phase 6)
- `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` — candidate for AbstractDisplayDriver refactor
- `src/main/kotlin/com/anjo/driver/OledDisplay.kt` — candidate for AbstractDisplayDriver refactor
- `src/main/kotlin/com/anjo/driver/OfflineDisplayDriver.kt` — stays as object; confirm no changes needed
- `src/main/kotlin/com/anjo/utils/Font.kt` — column-major font (5 bytes/char; each byte = one column; bit 0 = top row)
- `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` — existing test file to expand

### Key Decision Notes (from STATE.md)
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` line 141: `for (d in 0 until numDevices)` — this is the direction bug; `d=0` SPI slot → physically last (rightmost) module

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Font.asciiFont: Map<Char, ByteArray>` — column-major 5-byte-per-char bitmap; already used by `buildBitmap()`. No changes expected.
- `buildBitmap(text: String): ByteArray` — correctly builds the full scroll bitmap; no fix needed here.
- `CoroutineScope` + `Job` pattern in LCD/OLED — will be lifted to `AbstractDisplayDriver`.

### Established Patterns
- Kotest `should` convention (FunSpec) — all new tests follow this.
- JaCoCo ≥70% gate — new render logic must be covered.
- No Pi4J in test classpath — tests that touch `Max7219Matrix` directly cannot instantiate it (Pi4J init fails). Pure functions (`buildPacket`) avoid this entirely.

### Integration Points
- `ScreenDriverService` calls `scrollText()`, `displayStatic()`, `stop()`, `status()` — these must keep their existing signatures.
- DI wiring in `com/anjo/di/DependencyInjection.kt` — `Max7219Matrix` is instantiated there; no DI changes needed in Phase 6.
- `DisplaySelectionService` — selects the active driver; no changes needed.

</code_context>

<specifics>
## Specific Ideas

- SPI packet direction in MAX7219 daisy-chain: first bytes in `spi.write()` shift through to the LAST device. So: `packet[0..1]` → rightmost module, `packet[2..3]` → leftmost module (for `numDevices=2`).
- Font byte interpretation: `'A' = byteArrayOf(126,17,17,17,126)`. 126 = `0b01111110` — bits 1–6 lit on column 0. The `buildBitmap()` spacing adds a `0` byte between characters. This is the reference for the byte-exact unit test.
- `AbstractDisplayDriver` should hold: `protected var job: Job? = null`, `protected var lastMessage: String? = null`, `protected var lastError: String? = null`, plus default `stop()` and `status()` implementations.

</specifics>

<deferred>
## Deferred Ideas

- **Remove `write()` from `DisplayDriver` interface and unify with `displayStatic()`** — decided against in Phase 6 to avoid interface churn; revisit in Phase 8 (Refactor).

</deferred>

---

*Phase: 6-max7219-hardware-fix*
*Context gathered: 2026-06-12*
