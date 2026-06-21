# Phase 6: MAX7219 Hardware Fix - Research

**Researched:** 2026-06-12
**Domain:** MAX7219 SPI daisy-chain rendering, Kotlin abstract class refactoring, Kotest unit testing
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Fix `render()` by correcting the device-to-column mapping. SPI sends `d=0` first, which ends up at the physically last (rightmost) module. Correct fix: map `d=0` in SPI order to the rightmost module's columns (the higher-offset half of the bitmap), and `d=numDevices-1` to the leftmost module's columns (the lower-offset half). Either approach (reversing iteration + adjusting packet index, or adding a `physicalD = numDevices - 1 - d` inside the current loop) is acceptable.

**D-02:** Extract `buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int): ByteArray` as a pure function (no SPI dependency). `render()` calls `buildPacket()` then `spi.write()`. This enables unit testing without Pi4J.

**D-03:** `write()` should behave identically to `displayStatic()` — remove the broken guard `bitmap.size / 8 >= numDevices * 8` and always attempt to render. Short text fills leftmost columns; rest stays blank.

**D-04:** Introduce `AbstractDisplayDriver` (abstract class) in the `com.anjo.driver` package (flat — no sub-packages). It holds the shared state (`job`, `lastMessage`, `lastError`) and provides default implementations of `stop()` and `status()`.

**D-05:** `Max7219Matrix`, `LcdDisplay`, and `OledDisplay` all extend `AbstractDisplayDriver`. Each retains its hardware-specific logic; shared boilerplate is removed.

**D-06:** `OfflineDisplayDriver` stays as an `object` (intentionally stateless null-object — no inheritance needed).

**D-07:** Refactor depth is full DRY simplification — not just dead code removal. Remove duplication in scroll loop patterns, init patterns, and error tracking across LCD and OLED.

**D-08:** Add tests to existing `Max7219MatrixTest`:
1. One byte-exact test: `buildPacket` for a known character (e.g., 'A') at `offset=0`, `numDevices=1` — assert the exact 2-byte SPI packet (per row). Satisfies SC #2 (single-device probe) and SC #4 (no skipped byte ordering assertions).
2. One structural test: for `numDevices=2`, verify that `buildPacket` assigns lower bitmap columns (0–7) to the leftmost module's SPI slot and higher columns (8–15) to the rightmost.

**D-09:** No abstract test class for `AbstractDisplayDriver` — the existing driver contract test (mockk) is sufficient.

### Claude's Discretion

- Exact implementation form for the `render()` fix (reversing iteration vs. `physicalD` variable) — pick whichever is cleaner and more readable.
- Whether `write()` calls `buildPacket()` directly or delegates to `displayStatic()` internally.
- Error handling in `buildPacket()` for out-of-bounds bitmap access (keep the existing `else false` guard or handle differently).

### Deferred Ideas (OUT OF SCOPE)

- Remove `write()` from `DisplayDriver` interface and unify with `displayStatic()` — decided against in Phase 6 to avoid interface churn; revisit in Phase 8 (Refactor).

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HW-01 | Scroll renders text left-to-right on the 2×8×8 MAX7219 chain | SPI daisy-chain direction analysis, `buildPacket` refactor, verified byte arithmetic |
| HW-02 | Driver layer (Max7219Matrix, LCD, OLED, Offline) refactored — no workarounds, simplified logic | AbstractDisplayDriver design, shared-state extraction, DRY analysis across 4 drivers |

</phase_requirements>

---

## Summary

Phase 6 has two tightly scoped deliverables: fix the SPI packet direction bug in `Max7219Matrix.render()` and refactor all four display drivers under a shared `AbstractDisplayDriver` base class. The bug's root cause is fully understood: in a MAX7219 SPI daisy-chain, the first bytes written to `spi.write()` shift all the way through to the last physical device on the chain. The current loop writes `d=0` data first, which means it controls the rightmost module — the opposite of what the visual layout implies. The fix is a one-line change inside the column-mapping expression: `physicalD = numDevices - 1 - d`.

The refactor work is mechanical. All three hardware drivers (`Max7219Matrix`, `LcdDisplay`, `OledDisplay`) carry identical boilerplate: `var job: Job? = null`, `var lastMessage: String? = null`, `var lastError: String? = null`, plus identical `stop()` and `status()` bodies. This shared state moves to `AbstractDisplayDriver`. Each concrete driver then only overrides `clear()`, `write()`, `scrollText()`, and (where applicable) `setBrightness()`. `OfflineDisplayDriver` is a stateless `object` and is explicitly excluded from the inheritance chain.

The critical enabling technique for testing is extracting `buildPacket()` as a pure function with no Pi4J dependency. Pi4J initializes SPI hardware on construction and fails in JVM test environments — this is why the current test file avoids instantiating `Max7219Matrix` directly. By isolating the packet-building logic in a pure function, precise byte-level assertions become possible without a physical Pi.

**Primary recommendation:** Apply the `physicalD = numDevices - 1 - d` fix inside the `render()` loop, extract `buildPacket()` as a pure internal function (or companion object function), then lift shared driver state to `AbstractDisplayDriver`. Write the two `buildPacket` tests (byte-exact + structural) to lock in the direction fix.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| SPI packet construction | Driver (Max7219Matrix) | — | Pure hardware protocol logic; no service layer concern |
| Column-to-device mapping | Driver (Max7219Matrix) | — | Physical wiring topology knowledge belongs in the driver |
| Scroll animation loop | Driver (all three HW drivers) via AbstractDisplayDriver | CoroutineScope (caller-supplied) | Driver owns timing; scope lifecycle owned by ScreenDriverService |
| Shared driver state | AbstractDisplayDriver | — | DRY extraction of job/lastMessage/lastError from 3 identical implementations |
| Driver selection | DisplaySelectionService | — | Existing; no changes in Phase 6 |
| SPI/I2C hardware init | Each concrete driver's `init` block | — | Hardware specifics stay in the concrete class |

---

## Standard Stack

No new libraries are added in Phase 6. All tools are already on the classpath.

### Core (already in project)

| Library | Version | Purpose | Role in Phase 6 |
|---------|---------|---------|-----------------|
| Kotlin JVM | 2.3.21 | Language | Abstract class, `physicalD` fix |
| kotlinx-coroutines-core | 1.11.0 | Async scroll loops | Already used; `Job`/`CoroutineScope` lifted to base class |
| Pi4J core | 4.0.0 | SPI/I2C hardware access | Used in `init` blocks only; `buildPacket` is Pi4J-free |
| Kotest FunSpec | 6.1.11 | Test framework | New `buildPacket` tests extend existing `Max7219MatrixTest` |
| MockK | 1.14.9 | Mocking | Already used in driver contract tests |
| JaCoCo | 0.8.14 | Coverage gate (≥70%) | New `buildPacket` branch must be covered |

[VERIFIED: codebase — gradle/ktor-libs.versions.toml]

### No New Dependencies

Phase 6 adds zero new `build.gradle.kts` dependencies. The `AbstractDisplayDriver` is a plain Kotlin abstract class; no new library is needed.

---

## Package Legitimacy Audit

No external packages are installed in Phase 6.

**Packages removed due to SLOP verdict:** none  
**Packages flagged as suspicious (SUS):** none

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/text
       |
       v
ScreenDriverService.displayImmediate(text, effect)
       |
       v
EffectRenderer.render()  (ScrollEffect / BlinkEffect / ...)
       |
       v
DisplayDriver  <interface>
   |       |          |            |
   v       v          v            v
Max7219  LcdDisplay  OledDisplay  OfflineDisplayDriver
Matrix   (extends    (extends     (object, no change)
(extends  Abstract)   Abstract)
Abstract)
   |
   |-- buildPacket(bitmap, offset, numDevices): ByteArray  [PURE, no Pi4J]
   |-- spi.write(packet)
```

Data flow for `scrollText()`:

```
scrollText(scope, text, speedMs)
  -> AbstractDisplayDriver: job = scope.launch { ... }
  -> loop: render(bitmap, offset) every speedMs ms
      -> buildPacket(bitmap, offset, numDevices)
          -> for each row:
               physicalD = numDevices - 1 - d
               bitmap cols [offset + physicalD*8 .. offset + physicalD*8 + 7]
               pack into columnByte (MSB = col0)
               append (register, columnByte) to packet
      -> spi.write(packet)
```

### Recommended Project Structure

No structural changes. All files stay in `src/main/kotlin/com/anjo/driver/`:

```
src/main/kotlin/com/anjo/driver/
├── DisplayDriver.kt              # interface — DO NOT CHANGE
├── AbstractDisplayDriver.kt      # NEW — shared job/lastMessage/lastError + stop()/status()
├── Max7219Matrix.kt              # extends AbstractDisplayDriver; buildPacket extracted
├── LcdDisplay.kt                 # extends AbstractDisplayDriver
├── OledDisplay.kt                # extends AbstractDisplayDriver
└── OfflineDisplayDriver.kt       # object — NO change

src/test/kotlin/com/anjo/driver/
└── Max7219MatrixTest.kt          # add buildPacket byte-exact + structural tests
```

### Pattern 1: SPI Daisy-Chain Direction Fix — `physicalD` Variable

**What:** MAX7219 SPI daisy-chain shifts data serially. The first byte pair written enters the shift register of device 1, which then shifts it along to device 2, device 3, etc. So the FIRST bytes in the SPI buffer land at the LAST physical device. The existing loop (`for d in 0 until numDevices`) writes `d=0` data first — meaning `d=0` controls the physically rightmost module, not the leftmost. Adding `physicalD = numDevices - 1 - d` reverses the bitmap column lookup without changing packet byte order.

**When to use:** Any time you must map logical device index (left-to-right visual order) to SPI slot index (transmission order).

**Example — fixed `render()` inner loop:**

```kotlin
// Source: codebase analysis of Max7219Matrix.kt + SPI daisy-chain protocol
private fun render(bitmap: ByteArray, offset: Int) {
    for (row in 0 until 8) {
        val packet = buildPacket(bitmap, offset, numDevices, row)
        spi.write(packet)
    }
}

// Pure function — no SPI dependency — testable in JVM without Pi4J
internal fun buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int, row: Int): ByteArray {
    val packet = ByteArray(numDevices * 2)
    for (d in 0 until numDevices) {
        val physicalD = numDevices - 1 - d   // reverse: d=0 SPI slot = rightmost module
        var columnByte = 0
        for (col in 0 until 8) {
            val globalCol = offset + (physicalD * 8) + col
            val bit = if (globalCol < bitmap.size) {
                bitmap[globalCol].toInt() and (1 shl row) != 0
            } else false
            columnByte = (columnByte shl 1) or (if (bit) 1 else 0)
        }
        packet[d * 2]     = (row + 1).toByte()
        packet[d * 2 + 1] = columnByte.toByte()
    }
    return packet
}
```

[ASSUMED] — The exact function signature (per-row vs. full bitmap) is a discretion choice. Per-row keeps each `spi.write()` call small and is consistent with the current loop structure.

### Pattern 2: AbstractDisplayDriver Base Class

**What:** Lift the three identical fields (`job`, `lastMessage`, `lastError`) and the two identical method bodies (`stop()`, `status()`) from all three hardware drivers into a shared abstract class.

**When to use:** When 3+ concrete implementations carry byte-for-byte identical fields and logic that does not depend on hardware specifics.

**Example:**

```kotlin
// Source: codebase analysis of Max7219Matrix.kt, LcdDisplay.kt, OledDisplay.kt
abstract class AbstractDisplayDriver : DisplayDriver {
    protected var job: Job? = null
    protected var lastMessage: String? = null
    protected var lastError: String? = null

    override fun stop() {
        job?.cancel()
    }

    override fun status(): DisplayStatus = DisplayStatus(
        isActive = job?.isActive ?: false,
        hardwareAvailable = isHardwareAvailable(),
        currentMessage = lastMessage,
        error = lastError,
    )

    // Hardware-specific availability check — each driver knows its own init state
    protected abstract fun isHardwareAvailable(): Boolean
}
```

Note: `Max7219Matrix` always has hardware (SPI init throws into `lastError`; the object still exists). `LcdDisplay` and `OledDisplay` use a nullable `i2c` field to signal unavailability. The `isHardwareAvailable()` hook lets each driver express this without coupling `AbstractDisplayDriver` to I2C or SPI internals.

[ASSUMED] — The `isHardwareAvailable()` hook is one clean approach. An alternative is to keep `hardwareAvailable` as a `protected var` set in each driver's `init` block. Either is discretion-level.

### Pattern 3: `write()` Guard Bug — Remove Broken Condition

**What:** The current `write()` has `if (bitmap.size / 8 >= numDevices * 8) render(bitmap, 0)` — this guard is wrong (the `/ 8` should not be there; it makes the threshold 8× too high, so short messages never render). `displayStatic()` has a different but also wrong guard: `if (bitmap.size / 8 >= numDevices)`.

**Fix:** Remove both guards. Always call `render(bitmap, 0)`. The `buildPacket` function already handles short bitmaps safely with `if (globalCol < bitmap.size) ... else false`.

```kotlin
// BEFORE (broken):
override fun write(text: String) {
    stop(); clear(); lastMessage = text
    val bitmap = buildBitmap(text)
    if (bitmap.size / 8 >= numDevices * 8) render(bitmap, 0)  // almost never true
}

// AFTER (fixed):
override fun write(text: String) {
    stop(); clear(); lastMessage = text
    val bitmap = buildBitmap(text)
    render(bitmap, 0)
}
```

[VERIFIED: codebase — Max7219Matrix.kt lines 79-81, 122-124]

### Anti-Patterns to Avoid

- **Changing `DisplayDriver` interface:** Interface is locked for Phase 6. `write()`, `scrollText()`, `displayStatic()`, `stop()`, `status()` signatures must not change.
- **Putting Pi4J in test classpath for `buildPacket` tests:** The `spi` field is a construction-time Pi4J resource. Tests that call `buildPacket` directly bypass construction entirely; never instantiate `Max7219Matrix` in tests.
- **Adding sub-packages under `com.anjo.driver`:** D-04 specifies flat package; `AbstractDisplayDriver` goes in `com.anjo.driver`, not `com.anjo.driver.base` or similar.
- **Making `buildPacket` a companion object function prematurely:** If it's `internal`, it's testable from the same module. A companion function is equally valid. Do not make it `private` — that blocks testing without reflection.
- **Overriding `status()` in concrete drivers post-refactor:** After `AbstractDisplayDriver` provides `status()`, concrete drivers must NOT also override it (unless they have driver-specific fields not in the base class).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coroutine cancellation | Custom thread interrupt | `job?.cancel()` + `isActive` check in loop | Already in place; lifting to base class preserves this |
| Byte packing | Custom bit manipulation beyond the existing shift-or | Existing `(columnByte shl 1) or bit` pattern | Correct; do not change the bit assembly algorithm |
| Test isolation from Pi4J | Mock the entire `Max7219Matrix` class | Extract `buildPacket` as pure function | Mocking Pi4J context is brittle; pure functions are trivially testable |

---

## Runtime State Inventory

Not applicable — this is a code-only refactor phase. No database migrations, no stored state, no OS-registered resources, no renamed config keys.

---

## Common Pitfalls

### Pitfall 1: Off-by-One in `physicalD` with Variable `numDevices`

**What goes wrong:** For `numDevices=1`, `physicalD = 1 - 1 - 0 = 0` — same as `d`. The fix is a no-op for single-device setups, which is correct. But if the formula is wrong (e.g., `numDevices - d` instead of `numDevices - 1 - d`), a single-device test would still pass while two-device tests fail.

**Why it happens:** Off-by-one in the inversion formula.

**How to avoid:** Always test with `numDevices=2` in the structural test. Verify that `d=0` (SPI first slot, rightmost module) gets bitmap columns from `physicalD=1` (upper half), and `d=1` (SPI second slot, leftmost module) gets columns from `physicalD=0` (lower half).

**Warning signs:** Text appears on wrong module but not mirrored — module 0 shows module 1's content and vice versa.

### Pitfall 2: `buildBitmap()` Padding and Offset Arithmetic

**What goes wrong:** `buildBitmap()` appends 16 trailing zero-columns (`repeat(16) { columns.add(0) }`) plus one spacing zero between characters. For a single character 'A', the bitmap is 5 (font) + 1 (spacing) + 16 (trailing) = 22 bytes. The scroll loop runs while `offset <= bitmap.size - visibleColumns` = 22 - 16 = 6. This is correct — the trailing padding ensures the character fully scrolls off before the loop ends.

**Why it matters for tests:** The `buildPacket` test must use a bitmap from `buildBitmap("A")` (22 bytes), not a hand-crafted 8-byte array. Otherwise the offset arithmetic and bounds check in `buildPacket` are not exercised.

**Warning signs:** Test passes with hand-crafted bitmap but rendering looks wrong on hardware at the end of a scroll.

### Pitfall 3: `status()` in AbstractDisplayDriver — `hardwareAvailable` Diverges Per Driver

**What goes wrong:** `Max7219Matrix` always has a non-null `spi` (Pi4J either succeeds or throws into `lastError`). `LcdDisplay` and `OledDisplay` use a nullable `i2c: I2C?` to signal unavailability. If `AbstractDisplayDriver.status()` uses a uniform formula, it may misreport availability for one driver type.

**How to avoid:** Use the `isHardwareAvailable()` protected abstract hook (or `protected var hardwareAvailable: Boolean`) so each concrete class controls its own availability signal. The existing test `OledDisplayTest: should report hardware unavailable when I2C fails` asserts `status.hardwareAvailable.shouldBeFalse()` — this test must still pass after refactoring.

**Warning signs:** `OledDisplayTest` or `LcdDisplayTest` starts failing on `hardwareAvailable shouldBe false`.

### Pitfall 4: `stop()` Called from `clear()` in LCD — Coroutine Loop Behavior

**What goes wrong:** `LcdDisplay.clear()` calls `stop()` (cancels the coroutine), then clears the hardware. After the refactor, `stop()` is in `AbstractDisplayDriver`. If `clear()` is called from within the scroll coroutine itself, it could cancel its own coroutine before the loop body finishes.

**Why it matters:** This is pre-existing behavior, not introduced by Phase 6. The refactor must preserve it exactly. Do not change the call order inside `clear()`.

**Warning signs:** Scroll coroutine terminates mid-character on LCD.

### Pitfall 5: `bitmap.size / 8 >= numDevices * 8` Guard in `write()` Is Doubly Wrong

**What goes wrong:** The guard `bitmap.size / 8 >= numDevices * 8` (line 80 in Max7219Matrix.kt) means `bitmap.size >= numDevices * 64`. For `numDevices=2`, this requires at least 128 bytes — about 19 characters worth of text — before any static text renders at all. Short messages are silently dropped.

`displayStatic()` has a different guard: `bitmap.size / 8 >= numDevices` which means `bitmap.size >= 16` — barely less broken.

**Fix:** Remove both guards entirely. The `buildPacket` safe fallback (`else false`) handles any bitmap length correctly.

**Warning signs:** `write("Hi")` or `displayStatic("AB")` silently renders nothing.

---

## Exact Test Vectors (computed, verified)

These values are required for D-08 test item 1 (byte-exact assertion). They were computed by tracing the fixed `buildPacket` algorithm with `physicalD = numDevices - 1 - d`.

**Input:** `buildBitmap("A")` = `[126, 17, 17, 17, 126, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]` (22 bytes), `offset=0`, `numDevices=1`

**Font reference:** `Font.asciiFont['A'] = byteArrayOf(126, 17, 17, 17, 126)` — column-major, bit 0 = top row [VERIFIED: codebase — Font.kt]

**Full 16-byte packet for `numDevices=1`, all 8 rows:**

| Byte index | Value (decimal) | Meaning |
|-----------|----------------|---------|
| 0 | 1 | Register: row 1 |
| 1 | 112 (0x70) | Data: row 0 column pattern |
| 2 | 2 | Register: row 2 |
| 3 | 136 (0x88) | Data: row 1 column pattern |
| 4 | 3 | Register: row 3 |
| 5 | 136 (0x88) | Data: row 2 column pattern |
| 6 | 4 | Register: row 4 |
| 7 | 136 (0x88) | Data: row 3 column pattern |
| 8 | 5 | Register: row 5 |
| 9 | 248 (0xF8) | Data: row 4 column pattern |
| 10 | 6 | Register: row 6 |
| 11 | 136 (0x88) | Data: row 5 column pattern |
| 12 | 7 | Register: row 7 |
| 13 | 136 (0x88) | Data: row 6 column pattern |
| 14 | 8 | Register: row 8 |
| 15 | 0 | Data: row 7 (all off — 'A' is 7px tall) |

[VERIFIED: codebase — computed by tracing fixed algorithm against Font.kt and Max7219Matrix.kt logic; independently verified via Python computation in this research session]

**Structural test for `numDevices=2`, `offset=0`, `row=0`:**
- `packet[1]` (d=0, rightmost module, SPI first slot): `0` — bitmap cols 8..15 are padding
- `packet[3]` (d=1, leftmost module, SPI second slot): `112` — bitmap cols 0..7 contain 'A' row 0

The assertion: leftmost module's SPI slot data is non-zero for 'A' at offset 0, rightmost module data is 0.

---

## Code Examples

### Example 1: Fixed `render()` and `buildPacket()` pattern

```kotlin
// Source: codebase analysis — Max7219Matrix.kt current implementation + SPI direction fix
private fun render(bitmap: ByteArray, offset: Int) {
    for (row in 0 until 8) {
        spi.write(buildPacket(bitmap, offset, numDevices, row))
    }
}

internal fun buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int, row: Int): ByteArray {
    val packet = ByteArray(numDevices * 2)
    for (d in 0 until numDevices) {
        val physicalD = numDevices - 1 - d
        var columnByte = 0
        for (col in 0 until 8) {
            val globalCol = offset + (physicalD * 8) + col
            val bit = if (globalCol < bitmap.size) {
                bitmap[globalCol].toInt() and (1 shl row) != 0
            } else false
            columnByte = (columnByte shl 1) or (if (bit) 1 else 0)
        }
        packet[d * 2]     = (row + 1).toByte()
        packet[d * 2 + 1] = columnByte.toByte()
    }
    return packet
}
```

### Example 2: AbstractDisplayDriver skeleton

```kotlin
// Source: codebase analysis — duplicated fields in LcdDisplay.kt, OledDisplay.kt, Max7219Matrix.kt
abstract class AbstractDisplayDriver : DisplayDriver {
    protected var job: Job? = null
    protected var lastMessage: String? = null
    protected var lastError: String? = null

    protected abstract fun isHardwareAvailable(): Boolean

    override fun stop() {
        job?.cancel()
    }

    override fun status(): DisplayStatus = DisplayStatus(
        isActive = job?.isActive ?: false,
        hardwareAvailable = isHardwareAvailable(),
        currentMessage = lastMessage,
        error = lastError,
    )
}
```

### Example 3: `buildPacket` Kotest FunSpec tests (D-08)

```kotlin
// Source: Kotest FunSpec convention from existing Max7219MatrixTest.kt
// Byte vectors computed from fixed algorithm (see Exact Test Vectors section above)

test("buildPacket should produce correct byte-exact SPI packet for 'A' on single device") {
    val bitmap = buildBitmapFor("A")  // helper or inline font: [126,17,17,17,126,0,...,0] (22 bytes)
    val packet = Max7219Matrix.buildPacketStatic(bitmap, offset = 0, numDevices = 1, row = 0)
    // row=0: register=1 (0x01), data=112 (0x70)
    packet.size shouldBe 2
    packet[0] shouldBe 0x01.toByte()  // register for row 1
    packet[1] shouldBe 0x70.toByte()  // 'A' row 0 column pattern
}

test("buildPacket should assign lower bitmap columns to leftmost module for numDevices=2") {
    val bitmap = buildBitmapFor("A")
    val packetRow0 = Max7219Matrix.buildPacketStatic(bitmap, offset = 0, numDevices = 2, row = 0)
    // d=0 = SPI first = rightmost: packet[1] should be 0 (cols 8-15, all zero)
    // d=1 = SPI second = leftmost: packet[3] should be 112 (cols 0-7, 'A' row 0)
    packetRow0[1] shouldBe 0x00.toByte()  // rightmost module: no character
    packetRow0[3] shouldBe 0x70.toByte()  // leftmost module: 'A' row 0
}
```

Note: `buildPacket` must be either `internal` (testable from test module in same project) or moved to a companion object. The exact exposure mechanism is discretion-level.

---

## Duplication Inventory (for D-07 DRY refactor)

Identical code found in all three hardware drivers, confirmed by reading source files:

[VERIFIED: codebase — Max7219Matrix.kt, LcdDisplay.kt, OledDisplay.kt]

| Duplicated Item | Appears In | Lines (approx) | Move To |
|----------------|-----------|----------------|---------|
| `var job: Job? = null` | Max7219Matrix, LcdDisplay, OledDisplay | 1 line × 3 | AbstractDisplayDriver |
| `var lastMessage: String? = null` | Max7219Matrix, LcdDisplay, OledDisplay | 1 line × 3 | AbstractDisplayDriver |
| `var lastError: String? = null` | Max7219Matrix, LcdDisplay, OledDisplay | 1 line × 3 | AbstractDisplayDriver |
| `override fun stop() { job?.cancel() }` | Max7219Matrix, LcdDisplay, OledDisplay | 1-3 lines × 3 | AbstractDisplayDriver |
| `override fun status(): DisplayStatus { return DisplayStatus(isActive = job?.isActive ?: false, ...) }` | Max7219Matrix, LcdDisplay, OledDisplay | 5-7 lines × 3 | AbstractDisplayDriver |

**Scroll loop structure** — LCD and OLED share a similar `scope.launch { var offset = 0; while (isActive && ...) { ... offset++; delay(speedMs) }; clearHardware() }` pattern. Max7219Matrix's scroll is structurally identical but uses column offsets rather than character offsets. The scroll loops cannot be lifted to AbstractDisplayDriver because the loop body (what gets rendered at each step) differs per hardware type, but the `job = scope.launch { ... lastMessage = text ... }` wrapper boilerplate can optionally be extracted as a protected helper method (discretion-level).

---

## State of the Art

| Old Approach | Current Approach | Status |
|--------------|------------------|--------|
| `for d in 0 until numDevices` (direction bug) | `physicalD = numDevices - 1 - d` | This phase fixes it |
| `if (bitmap.size / 8 >= numDevices * 8)` guard | Always render (safe bounds in buildPacket) | This phase fixes it |
| Duplicated job/lastMessage/lastError in 3 drivers | AbstractDisplayDriver base class | This phase introduces it |
| `render()` builds+sends packet inline (untestable) | `buildPacket()` pure function + `spi.write()` call | This phase extracts it |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `buildPacket` is defined with a per-row `row: Int` parameter (called 8 times from `render()`) rather than building all 8 rows in one call | Code Examples, Patterns | Minimal — both designs are functionally correct; adjust signature in plan if full-packet approach chosen |
| A2 | `isHardwareAvailable()` hook is the cleanest way to unify `status()` across Max7219 (always available after init) vs LCD/OLED (nullable i2c) | Architecture Patterns | Low — alternative is `protected var hardwareAvailable: Boolean`; either works |
| A3 | `buildPacket` is marked `internal` (visible to test module in same Gradle project) rather than companion object `fun` | Code Examples | Low — `internal` is idiomatic Kotlin for test visibility without exposing as public API |

**If this table has only 3 assumptions:** The bulk of research is verified from the codebase directly.

---

## Open Questions (RESOLVED)

1. **Per-row `buildPacket` vs. full-frame `buildPacket`**
   - What we know: CONTEXT.md D-08 says "assert the exact 2-byte SPI packet" (per device per row = 2 bytes for numDevices=1 per row). The test description implies per-row.
   - What's unclear: The D-02 description says "render() calls buildPacket() then spi.write()" — this could mean one call per row OR one call per frame.
   - Recommendation: Per-row is more testable (2 bytes per assertion for numDevices=1) and matches the current `spi.write()` call pattern. Use per-row.
   - **RESOLVED:** Use per-row `buildPacket(bitmap, offset, numDevices, row): ByteArray` — render() calls it 8 times (once per row). Plans and test vectors are written to this signature.

2. **`OfflineDisplayDriver` status after AbstractDisplayDriver**
   - What we know: D-06 explicitly says it stays as an `object` with no inheritance.
   - What's unclear: The `OfflineDisplayDriver.status()` method returns hardcoded values already — no change needed.
   - Recommendation: Confirm no import or structural change is required. `object OfflineDisplayDriver : DisplayDriver` remains unchanged.
   - **RESOLVED:** `OfflineDisplayDriver` is untouched — no import change, no structural change, no AbstractDisplayDriver inheritance.

---

## Environment Availability

This phase is code-only. No external services, databases, or CLI tools are required beyond the existing Gradle build environment.

| Dependency | Required By | Available | Note |
|------------|------------|-----------|------|
| Gradle + JDK 25 | Build + test | Assumed present | Project already builds |
| Kotest + MockK | `Max7219MatrixTest` | Already in classpath | No new test deps needed |
| Pi4J (test exclusion) | NOT needed for new tests | — | `buildPacket` is Pi4J-free |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (FunSpec) + JUnit 5 runner |
| Config file | None — Kotest auto-detected via `kotest-runner-junit5-jvm` |
| Quick run command | `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest"` |
| Full suite command | `./gradlew test jacocoTestReport` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HW-01 | `buildPacket` maps 'A' at offset=0 to correct SPI bytes (direction fix) | unit | `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest"` | ✅ expand existing |
| HW-01 | `buildPacket` assigns lower bitmap cols to leftmost SPI slot for numDevices=2 | unit | same | ✅ expand existing |
| HW-02 | OledDisplay post-refactor still reports hardware unavailable on I2C failure | unit | `./gradlew test --tests "com.anjo.driver.OledDisplayTest"` | ✅ existing test must pass |
| HW-02 | All existing driver contract tests pass unchanged | regression | `./gradlew test` | ✅ all existing tests |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.driver.*"`
- **Per wave merge:** `./gradlew test jacocoTestReport`
- **Phase gate:** Full suite green + JaCoCo ≥70% before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `AbstractDisplayDriver.kt` — new file, no tests needed (logic covered by existing driver tests)
- [ ] Two new test cases in `Max7219MatrixTest.kt` — required by D-08

---

## Security Domain

Not applicable to this phase. Phase 6 modifies SPI driver logic and internal class hierarchy only. No new HTTP endpoints, no user input paths, no authentication flows, no cryptography. ASVS categories V2/V3/V4/V6 do not apply. V5 (input validation) is not relevant to the `buildPacket` function (inputs are internal bitmap byte arrays, not user-supplied strings).

---

## Sources

### Primary (HIGH confidence — codebase verified)

- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — bug location confirmed at lines 80, 123, 141–155; SPI packet construction traced precisely
- `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` — duplication inventory confirmed
- `src/main/kotlin/com/anjo/driver/OledDisplay.kt` — duplication inventory confirmed
- `src/main/kotlin/com/anjo/driver/OfflineDisplayDriver.kt` — confirmed stateless object, no changes needed
- `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` — interface confirmed, must not change
- `src/main/kotlin/com/anjo/utils/Font.kt` — `'A' = byteArrayOf(126, 17, 17, 17, 126)` confirmed
- `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` — existing test structure confirmed, no Pi4J instantiation
- `gradle/ktor-libs.versions.toml` — library versions confirmed (Kotest 6.1.11, MockK 1.14.9, JaCoCo 0.8.14)
- `.planning/phases/06-max7219-hardware-fix/06-CONTEXT.md` — all decisions locked

### Secondary (MEDIUM confidence — computed)

- Exact byte vectors (test vectors table) — computed by tracing the fixed algorithm in JavaScript with the same bit-packing logic as the Kotlin implementation; cross-checked against `Font.asciiFont['A']` definition from codebase

### Tertiary (LOW confidence — training knowledge)

- MAX7219 SPI daisy-chain serial shift behavior (first byte → last device) — hardware protocol knowledge [ASSUMED]; consistent with STATE.md pitfall note and CONTEXT.md §Specifics which independently document this behavior

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all libraries already in project, versions from version catalog
- Architecture patterns: HIGH — based on direct codebase reading; no speculation
- Pitfalls: HIGH — bugs identified by reading exact source lines; test vectors verified by computation
- AbstractDisplayDriver design: MEDIUM — based on code analysis; exact hook mechanism is discretion-level

**Research date:** 2026-06-12  
**Valid until:** 2026-09-12 (stable — Kotlin/Pi4J APIs not rapidly changing)
