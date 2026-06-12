---
phase: 06-max7219-hardware-fix
fixed_at: 2026-06-12T00:00:00Z
review_path: .planning/phases/06-max7219-hardware-fix/06-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 06: Code Review Fix Report

**Fixed at:** 2026-06-12T00:00:00Z
**Source review:** .planning/phases/06-max7219-hardware-fix/06-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (3 Critical, 4 Warning)
- Fixed: 7
- Skipped: 0

## Fixed Issues

### CR-01: `Max7219Matrix.clear()` Does Not Cancel the Running Scroll Job

**Files modified:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt`
**Commit:** bb1ebac
**Applied fix:** Added `stop()` as the first line in `clear()`, cancelling any active scroll coroutine before sending all-zero rows to the display. This matches the existing `LcdDisplay.clear()` and `OledDisplay.clear()` behaviour.

---

### CR-02: `LcdDisplay` Uses Wrong Protocol for HD44780 4-Bit Mode

**Files modified:** `src/main/kotlin/com/anjo/driver/LcdDisplay.kt`
**Commit:** 653b83a
**Applied fix:** Introduced a `writeNibble(nibble: Int, rs: Int)` private helper that sends one 4-bit nibble with correct E-strobe and backlight-bit handling. Rewrote `writeCommand` and `writeData` to call `writeNibble` twice — high nibble then low nibble — matching the HD44780 4-bit protocol on a PCF8574 I2C backpack. Added `private val BACKLIGHT = 0x08`.

---

### CR-03: `Max7219Matrix` Constructor Throws on SPI Failure

**Files modified:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt`
**Commit:** fe05342
**Applied fix:** Changed `spi` from non-nullable `Spi` to nullable `Spi?`. Wrapped the SPI config build and `ctx.create()` call in a try-catch that stores the error in `lastError` and assigns `null` on failure. `initialize()` is only called when `spi != null`. Updated `isHardwareAvailable()` to check `spi != null && lastError == null`. Updated both `spi.write(...)` call sites in `sendCommand` and `render` to safe-call `spi?.write(...)`.

---

### WR-01: `OledDisplay.renderFrame` Sends Raw Char Codes Instead of Glyph Bitmaps

**Files modified:** `src/main/kotlin/com/anjo/driver/OledDisplay.kt`
**Commit:** d7a7fe2
**Applied fix:** Replaced the `renderFrame(frame: String)` function with `renderText(text: String)` that iterates characters, looks up each in `Font.asciiFont` (falling back to space glyph or `ByteArray(5)`), collects 5-byte glyph columns plus a 1-byte inter-character gap, and sends up to `width` columns. Added `import com.anjo.utils.Font`. Updated both call sites in `write()` and the `scrollText` coroutine to use `renderText`.

---

### WR-02: `scrollText` Silently Displays Nothing When Text Bitmap Is Shorter Than `visibleColumns`

**Files modified:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt`
**Commit:** c49ce99
**Applied fix:** Added a `maxOffset < 0` guard at the top of the `scope.launch` block: when the bitmap is shorter than the visible display width, `render(bitmap, 0)` is called to show what fits and the coroutine returns immediately, rather than falling through to `clear()`.

---

### WR-03: Shared Mutable State Not `@Volatile`

**Files modified:** `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt`
**Commit:** c1cee31
**Applied fix:** Added `@Volatile` annotation to all three shared mutable fields — `job`, `lastMessage`, and `lastError` — ensuring cross-thread visibility on multi-core JVM (Raspberry Pi 4) without requiring heavier synchronisation.

---

### WR-04: `buildBitmap` Trailing Padding Is Hardcoded to 16 Columns

**Files modified:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt`
**Commit:** 19cb1a8
**Applied fix:** Replaced the literal `repeat(16)` with `val trailingPad = numDevices * 8` followed by `repeat(trailingPad)`, so the blank tail appended to the scroll bitmap is exactly one full display width regardless of how many MAX7219 devices are chained.

---

_Fixed: 2026-06-12T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
