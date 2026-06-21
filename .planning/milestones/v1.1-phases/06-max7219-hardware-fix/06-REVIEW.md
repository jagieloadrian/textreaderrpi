---
phase: 06-max7219-hardware-fix
reviewed: 2026-06-12T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt
  - src/main/kotlin/com/anjo/driver/LcdDisplay.kt
  - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
  - src/main/kotlin/com/anjo/driver/OledDisplay.kt
  - src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt
findings:
  critical: 3
  warning: 4
  info: 3
  total: 10
status: issues_found
---

# Phase 06: Code Review Report

**Reviewed:** 2026-06-12T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Five driver source files were reviewed: the abstract base, the LCD (HD44780 via I2C), the MAX7219 LED matrix (via SPI), the SSD1306 OLED (via I2C), and the MAX7219 unit test. The test coverage for the MAX7219 is narrow — it tests only `DisplayStatus` data class properties and one mock-based contract check, plus two `buildPacket` cases. No integration or coroutine-level tests are present for the drivers.

Three critical issues were found: a race condition in `Max7219Matrix.clear()` that allows a running scroll job to override a clear operation; an incorrect HD44780 4-bit mode protocol in `LcdDisplay` that will produce garbage on real hardware; and an inconsistent constructor contract where `Max7219Matrix` throws on SPI failure while the other two drivers silently absorb hardware errors. Four warnings cover incorrect OLED character rendering, a silent blank-display path for short text, missing `@Volatile` on shared mutable state, and a hardcoded bitmap padding constant that diverges from `visibleColumns`. Three info-level items cover minor robustness and style gaps.

## Critical Issues

### CR-01: `Max7219Matrix.clear()` Does Not Cancel the Running Scroll Job

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:78`

**Issue:** `clear()` sends all-zero rows to the display and resets `buffer`, but does not call `stop()` first. If a scroll coroutine launched by `scrollText()` is still running when `clear()` is called externally, the coroutine will call `render()` immediately after `clear()` returns, putting bitmap content back on the display. The user perceives `clear()` as a no-op. Both `LcdDisplay.clear()` (line 82) and `OledDisplay.clear()` (line 78) correctly call `stop()` first; this driver is inconsistent and broken.

**Fix:**
```kotlin
override fun clear() {
    stop()                          // cancel scroll job first
    try {
        for (row in 1..8) sendCommand(row, 0x00)
        buffer = Array(numDevices) { ByteArray(8) }
        lastMessage = null
        lastError = null
    } catch (e: Exception) {
        lastError = "Clear failed: ${e.message}"
    }
}
```

---

### CR-02: `LcdDisplay` Uses Wrong Protocol for HD44780 4-Bit Mode

**File:** `src/main/kotlin/com/anjo/driver/LcdDisplay.kt:58`

**Issue:** The init sequence at line 46 sends `0x33`, `0x32`, then `0x28` — the standard sequence to place an HD44780 into 4-bit mode. After `writeCommand(0x28)` the controller expects all subsequent bytes as two consecutive 4-bit nibbles (high nibble first). However, `writeCommand` and `writeData` continue to send full 8-bit values with only an E-strobe toggle:

- `writeCommand(cmd)`: sends `cmd | 0x04` (E high), then `cmd & 0xFB` (E low) — one E pulse with the full byte
- `writeData(data)`: sends `data | 0x05` (E high, RS), then `data | 0x01` (E low, RS) — same, full byte

On a PCF8574 I2C backpack in 4-bit mode, the LCD's D7–D4 pins are driven by I2C bits 7–4. A full-byte E pulse presents the entire byte's upper nibble, then the lower nibble must be sent in a second E pulse. The current code never sends the second (low) nibble. Every character and command sent after `writeCommand(0x28)` will be corrupted. The display will show random garbage or nothing.

**Fix:** Implement a proper nibble write helper:
```kotlin
private val BACKLIGHT = 0x08

private fun writeNibble(nibble: Int, rs: Int) {
    val data = (nibble and 0xF0) or BACKLIGHT or rs
    writeI2C(data or 0x04)   // E high
    writeI2C(data)            // E low
}

private fun writeCommand(cmd: Int) {
    try {
        writeNibble(cmd and 0xF0, 0)           // high nibble, RS=0 (command)
        writeNibble((cmd shl 4) and 0xF0, 0)  // low nibble,  RS=0
    } catch (e: Exception) {
        lastError = "Write command failed: ${e.message}"
    }
}

private fun writeData(data: Int) {
    try {
        writeNibble(data and 0xF0, 1)           // high nibble, RS=1 (data)
        writeNibble((data shl 4) and 0xF0, 1)  // low nibble,  RS=1
    } catch (e: Exception) {
        lastError = "Write data failed: ${e.message}"
    }
}
```

---

### CR-03: `Max7219Matrix` Constructor Throws on SPI Failure; Other Drivers Do Not

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:47`

**Issue:** `spi` is declared as a non-nullable `val` and assigned directly from `ctx.create(config)` at line 61, which is outside any try-catch. If `ctx.create()` throws (e.g., SPI device not present, permission error), the exception propagates out of the constructor. The calling code receives an unhandled exception instead of a usable driver object. By contrast, `LcdDisplay` and `OledDisplay` both declare `i2c` as nullable and assign it inside a try-catch, so construction always succeeds and hardware failures are reflected via `lastError` and `isHardwareAvailable()`. This contract inconsistency means callers cannot uniformly construct all three drivers without conditional exception handling.

**Fix:** Mirror the LCD/OLED pattern — make `spi` nullable and catch the creation failure:
```kotlin
private val spi: Spi?

init {
    spi = try {
        val config = Spi.newConfigBuilder(ctx)
            // ... same config ...
            .build()
        ctx.create(config)
    } catch (e: Exception) {
        lastError = "SPI initialization failed: ${e.message}"
        null
    }

    if (spi != null) {
        try {
            initialize()
        } catch (e: Exception) {
            lastError = "Initialization failed: ${e.message}"
        }
    }
}

override fun isHardwareAvailable() = spi != null && lastError == null
```
All `spi.write(...)` call sites must be updated to use `spi?.write(...)`.

---

## Warnings

### WR-01: `OledDisplay.renderFrame` Sends Raw Char Codes Instead of Glyph Bitmaps

**File:** `src/main/kotlin/com/anjo/driver/OledDisplay.kt:70`

**Issue:** `renderFrame` sends each character's Unicode code point directly as a pixel-column byte via `sendData(it.code and 0xFF)`. The SSD1306 interprets each byte as a column of 8 pixels; the ASCII code for 'A' is `0x41 = 0b01000001`, which renders as a pixel pattern with bits 0 and 6 set — not a recognisable letter. `Max7219Matrix` correctly maps characters through `Font.asciiFont`; `OledDisplay` does not. All text displayed via this driver will appear as random pixel patterns.

**Fix:** Use `Font.asciiFont` and build a page-aligned frame buffer, analogous to `Max7219Matrix.buildBitmap`:
```kotlin
private fun renderText(text: String) {
    val columns = mutableListOf<Int>()
    for (c in text) {
        val glyph = Font.asciiFont[c] ?: Font.asciiFont[' ']!!
        glyph.forEach { columns.add(it.toInt() and 0xFF) }
        columns.add(0) // inter-character gap
    }
    sendCommand(0xB0); sendCommand(0x00); sendCommand(0x10)
    columns.take(width).forEach { sendData(it) }
}
```

---

### WR-02: `scrollText` Silently Displays Nothing When Text Bitmap Is Shorter Than `visibleColumns`

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:103`

**Issue:** `maxOffset = bitmap.size - visibleColumns`. When `bitmap.size < visibleColumns` (e.g., a 3-character string on a 4-device display: bitmap ~34 bytes vs. visibleColumns=32 is marginal, but a 1-character string gives 22 bytes vs. 32 for 4 devices gives `maxOffset = -10`), the while-loop condition `offset <= maxOffset` is immediately false. The coroutine body skips directly to `clear()` at line 112, which also sets `lastMessage = null`. The caller passed a non-empty string to `scrollText()`, but the display is cleared and shows nothing. There is no fallback to `write()` or `displayStatic()`.

**Fix:** Before starting the scroll loop, check whether the text fits statically and fall back:
```kotlin
if (maxOffset < 0) {
    render(bitmap, 0)   // display what fits
    return@launch
}
```

---

### WR-03: Shared Mutable State (`lastMessage`, `lastError`) Not `@Volatile`

**File:** `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt:8`

**Issue:** `lastMessage` and `lastError` are plain `var` fields on the JVM. They are written inside launched coroutines (e.g., `lastError = "Scroll failed: ..."` in the `scrollText` coroutine body) and read from the calling thread via `status()`. Without `@Volatile` or a synchronisation mechanism, the JVM memory model does not guarantee the calling thread will see the most-recently written value. `job` faces the same issue. On multi-core ARM hardware (Raspberry Pi 4) this is a realistic concern, not merely theoretical.

**Fix:**
```kotlin
@Volatile protected var lastMessage: String? = null
@Volatile protected var lastError: String? = null
@Volatile protected var job: Job? = null
```

---

### WR-04: `buildBitmap` Trailing Padding Is Hardcoded to 16 Columns, Not `visibleColumns`

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:153`

**Issue:** `buildBitmap` appends `repeat(16) { columns.add(0) }` regardless of `numDevices`. For `numDevices=1` (8 columns visible) the 16-column pad is twice the display width — excessive but harmless. For `numDevices=3` (24 columns visible) the 16-column pad is less than one full display width: the last scroll frame will show 8 columns of text content alongside 16 blank columns instead of a fully blank terminal frame. The visual tail-off is cut short.

**Fix:**
```kotlin
private fun buildBitmap(text: String): ByteArray {
    val columns = mutableListOf<Byte>()
    for (c in text) {
        val glyph = Font.asciiFont[c] ?: Font.asciiFont[' ']!!
        columns.addAll(glyph.toList())
        columns.add(0)
    }
    val trailingPad = numDevices * 8   // one full display width of blank columns
    repeat(trailingPad) { columns.add(0) }
    return columns.toByteArray()
}
```

---

## Info

### IN-01: `Font.asciiFont[' ']!!` Unnecessary Non-Null Assertion

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:148`

**Issue:** The fallback `?: Font.asciiFont[' ']!!` uses `!!` to force-unwrap. The space character `' '` is always present in `Font.asciiFont`, making the `!!` safe in practice, but the assertion will throw `NullPointerException` if the map is ever refactored to remove the space entry. A safer pattern avoids the assertion.

**Fix:**
```kotlin
val glyph = Font.asciiFont[c] ?: Font.asciiFont[' '] ?: ByteArray(5)
```

---

### IN-02: `OledDisplay.initializeDisplay` SSD1306 Init Sequence Is Too Minimal

**File:** `src/main/kotlin/com/anjo/driver/OledDisplay.kt:45`

**Issue:** The init sequence sends only display-off (`0xAE`), multiplex ratio (`0xA8` + height−1), and display-on (`0xAF`). Critical defaults are missing: display offset (`0xD3`), start line (`0x40`), segment remap, COM output direction, COM pin config, contrast (`0x81`), pre-charge period, VCOMH deselect level, entire display on/off, and the charge-pump enable (`0x8D`/`0x14`). On many SSD1306 modules the internal charge pump is off by default; without `0x8D, 0x14` the OLED panel will be dark even when power is applied.

**Fix:** Use a complete SSD1306 startup sequence (e.g., the Adafruit reference sequence), at minimum adding:
```kotlin
sendCommand(0x8D); sendCommand(0x14)  // charge pump enable
sendCommand(0x20); sendCommand(0x00)  // horizontal addressing mode
sendCommand(0xA1)                      // segment remap
sendCommand(0xC8)                      // COM output scan direction
sendCommand(0x81); sendCommand(0x7F)  // contrast
```

---

### IN-03: `Max7219MatrixTest` Tests `DisplayStatus` and Mocks, Not the Driver Logic

**File:** `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt:12`

**Issue:** Three of the five tests verify `DisplayStatus` data class properties (equality, toString, field values) and one tests a mock of the `DisplayDriver` interface. These tests exercise Kotlin's `data class` auto-generation and MockK, not any logic in `Max7219Matrix`. The two `buildPacket` tests are genuinely useful. There are no tests for: `scrollText` scroll-loop boundary behaviour, `clear()` stopping an active job, `write()` after a failed SPI command, `scrollText` with text shorter than `visibleColumns`, or `isHardwareAvailable()` returning the correct state.

**Fix:** Replace the three `DisplayStatus`/mock tests with targeted `Max7219Matrix` logic tests using the internal `buildPacket` companion method and by extracting bitmap/render logic behind a seam. At minimum, add a test for `maxOffset < 0` short-text path (WR-02).

---

_Reviewed: 2026-06-12T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
