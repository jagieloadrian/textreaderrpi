# Phase 6: MAX7219 Hardware Fix - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 6 (1 new, 4 modified, 1 test expanded)
**Analogs found:** 5 / 6 (AbstractDisplayDriver has no prior analog — new file)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt` | base class (abstract) | — | `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` (field donor) | partial — fields/methods extracted FROM analogs |
| `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` | hardware driver | streaming (SPI row-by-row) | itself (modify existing) | exact |
| `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` | hardware driver | request-response + streaming | `src/main/kotlin/com/anjo/driver/OledDisplay.kt` | exact role-match |
| `src/main/kotlin/com/anjo/driver/OledDisplay.kt` | hardware driver | request-response + streaming | `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` | exact role-match |
| `src/main/kotlin/com/anjo/driver/OfflineDisplayDriver.kt` | null-object driver | — | itself (no changes) | N/A |
| `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` | unit test (expand) | — | itself (expand existing) | exact |

---

## Pattern Assignments

### `src/main/kotlin/com/anjo/driver/AbstractDisplayDriver.kt` (NEW — abstract base class)

**Analog:** Fields and method bodies extracted from all three hardware drivers (confirmed identical at `Max7219Matrix.kt` lines 31–34, 102–113; `LcdDisplay.kt` lines 18–21, 135–146; `OledDisplay.kt` lines 20–23, 123–133).

**Imports pattern** — copy from `LcdDisplay.kt` lines 6–9 (the coroutines needed here):
```kotlin
import kotlinx.coroutines.Job
```
`CoroutineScope` is NOT needed in the abstract class itself (it is the concrete `scrollText` that receives a scope).

**Core pattern** — lifted from `Max7219Matrix.kt` lines 31–34, 102–113 and `LcdDisplay.kt` lines 18–21, 135–146 (all identical):
```kotlin
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

**`isHardwareAvailable()` per-driver implementation:**
- `Max7219Matrix`: `override fun isHardwareAvailable() = lastError == null` (SPI init either succeeded or set `lastError`; see `Max7219Matrix.kt` lines 47–52)
- `LcdDisplay`: `override fun isHardwareAvailable() = i2c != null && lastError == null` (see `LcdDisplay.kt` line 138)
- `OledDisplay`: `override fun isHardwareAvailable() = i2c != null && lastError == null` (see `OledDisplay.kt` line 126)

---

### `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` (MODIFY — fix render + extract buildPacket + extend AbstractDisplayDriver)

**Analog:** itself — `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt`

**Change 1 — class declaration** (currently line 20):
```kotlin
// BEFORE:
class Max7219Matrix( ... ) : DisplayDriver {

// AFTER:
class Max7219Matrix( ... ) : AbstractDisplayDriver() {
```

**Change 2 — remove duplicated fields** (currently lines 31, 33–34):
```kotlin
// REMOVE these three lines (now in AbstractDisplayDriver):
private var job: Job? = null
private var lastMessage: String? = null
private var lastError: String? = null
```
`buffer` field (line 32) stays — it is Max7219-specific.

**Change 3 — remove duplicated stop() and status()** (currently lines 102–113):
```kotlin
// REMOVE entirely — provided by AbstractDisplayDriver:
override fun status(): DisplayStatus { ... }
override fun stop() { ... }
```

**Change 4 — add isHardwareAvailable()** (companion to removed status()):
```kotlin
override fun isHardwareAvailable() = lastError == null
```

**Change 5 — fix write() guard** (currently lines 75–81):
```kotlin
// BEFORE (broken — guard is almost never true):
override fun write(text: String) {
    stop()
    clear()
    lastMessage = text
    val bitmap = buildBitmap(text)
    if (bitmap.size / 8 >= numDevices * 8) render(bitmap, 0)
}

// AFTER:
override fun write(text: String) {
    stop()
    clear()
    lastMessage = text
    val bitmap = buildBitmap(text)
    render(bitmap, 0)
}
```

**Change 6 — fix displayStatic() guard** (currently lines 119–124):
```kotlin
// BEFORE (guard is also wrong):
override suspend fun displayStatic(text: String) {
    stop()
    lastMessage = text
    val bitmap = buildBitmap(text)
    if (bitmap.size / 8 >= numDevices) render(bitmap, 0)
}

// AFTER:
override suspend fun displayStatic(text: String) {
    stop()
    lastMessage = text
    val bitmap = buildBitmap(text)
    render(bitmap, 0)
}
```

**Change 7 — extract buildPacket() and fix render()** (currently lines 135–158):
```kotlin
// BEFORE (direction bug: d=0 → leftmost, but SPI d=0 → rightmost):
private fun render(bitmap: ByteArray, offset: Int) {
    for (row in 0 until 8) {
        val packet = ByteArray(numDevices * 2)
        for (d in 0 until numDevices) {
            var columnByte = 0
            for (col in 0 until 8) {
                val globalCol = offset + (d * 8) + col   // BUG: d used directly
                val bit = if (globalCol < bitmap.size) {
                    bitmap[globalCol].toInt() and (1 shl row) != 0
                } else false
                columnByte = (columnByte shl 1) or (if (bit) 1 else 0)
            }
            packet[d * 2] = (row + 1).toByte()
            packet[d * 2 + 1] = columnByte.toByte()
        }
        spi.write(packet)
    }
}

// AFTER (buildPacket extracted as internal pure function; physicalD fixes direction):
private fun render(bitmap: ByteArray, offset: Int) {
    for (row in 0 until 8) {
        spi.write(buildPacket(bitmap, offset, numDevices, row))
    }
}

internal fun buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int, row: Int): ByteArray {
    val packet = ByteArray(numDevices * 2)
    for (d in 0 until numDevices) {
        val physicalD = numDevices - 1 - d   // d=0 SPI slot = rightmost module
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

**Imports to remove** (no longer needed after removing duplicated fields):
```kotlin
// REMOVE — Job is now declared in AbstractDisplayDriver:
import kotlinx.coroutines.Job
```
All other imports (`CoroutineScope`, `delay`, `isActive`, `launch`, etc.) remain — still used by `scrollText()`.

---

### `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` (MODIFY — extend AbstractDisplayDriver, remove duplicates)

**Analog:** `src/main/kotlin/com/anjo/driver/OledDisplay.kt` (structurally identical)

**Change 1 — class declaration** (currently line 15):
```kotlin
// BEFORE:
class LcdDisplay( ... ) : DisplayDriver {

// AFTER:
class LcdDisplay( ... ) : AbstractDisplayDriver() {
```

**Change 2 — remove duplicated fields** (currently lines 18–20):
```kotlin
// REMOVE (now in AbstractDisplayDriver):
private var job: Job? = null
private var lastMessage: String? = null
private var lastError: String? = null
```

**Change 3 — remove duplicated stop() and status()** (currently lines 135–146):
```kotlin
// REMOVE entirely:
override fun status(): DisplayStatus { ... }
override fun stop() { ... }
```

**Change 4 — add isHardwareAvailable():**
```kotlin
override fun isHardwareAvailable() = i2c != null && lastError == null
```

**Imports to remove** (no longer needed):
```kotlin
// REMOVE:
import kotlinx.coroutines.Job
```

**Preserved intact:** `init` block (lines 27–47), `initializeLcd()` (lines 49–56), `clear()` (lines 85–94), `write()` (lines 96–111), `scrollText()` (lines 113–133), `setBrightness()` (lines 148–151), `displayStatic()` (lines 153–155), and all private hardware methods.

---

### `src/main/kotlin/com/anjo/driver/OledDisplay.kt` (MODIFY — extend AbstractDisplayDriver, remove duplicates)

**Analog:** `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` (mirror of LCD pattern above)

**Same four changes as LcdDisplay:**

1. Class declaration: `: DisplayDriver` → `: AbstractDisplayDriver()`
2. Remove fields at lines 20–22 (`job`, `lastMessage`, `lastError`)
3. Remove `status()` (lines 123–130) and `stop()` (lines 132–133)
4. Add: `override fun isHardwareAvailable() = i2c != null && lastError == null`
5. Remove `import kotlinx.coroutines.Job`

**Preserved intact:** all hardware-specific logic (`initializeDisplay()`, `sendCommand()`, `sendData()`, `clearHardware()`, `renderFrame()`, `clear()`, `write()`, `scrollText()`, `setBrightness()`, `displayStatic()`).

---

### `src/main/kotlin/com/anjo/driver/OfflineDisplayDriver.kt` (NO CHANGES)

D-06 explicitly locks this file. It is an `object` (stateless null-object pattern) and does not participate in the AbstractDisplayDriver hierarchy. Current content at lines 1–16 is the final state.

---

### `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` (EXPAND — add 2 buildPacket tests)

**Analog:** itself — existing `Max7219MatrixTest.kt` (Kotest FunSpec style, lines 12–52)

**Existing test style to copy** (lines 12–22):
```kotlin
class Max7219MatrixTest : FunSpec({
    test("should carry expected values in DisplayStatus") {
        // arrange inline, assert with shouldBe
    }
})
```

**New test 1 — byte-exact single device** (D-08 item 1):
Vectors from RESEARCH.md §Exact Test Vectors: `offset=0, numDevices=1, row=0` → `packet[0]=0x01, packet[1]=0x70`.
```kotlin
test("buildPacket should produce correct SPI bytes for 'A' row 0 on single device") {
    val bitmap = buildBitmapPublic("A")   // see note below
    val packet = buildPacketPublic(bitmap, offset = 0, numDevices = 1, row = 0)
    packet.size shouldBe 2
    packet[0] shouldBe 0x01.toByte()   // register: row 1
    packet[1] shouldBe 0x70.toByte()   // data: 'A' row 0 column pattern
}
```

**New test 2 — structural two-device direction** (D-08 item 2):
```kotlin
test("buildPacket should assign lower bitmap columns to leftmost SPI slot for numDevices=2") {
    val bitmap = buildBitmapPublic("A")
    val packet = buildPacketPublic(bitmap, offset = 0, numDevices = 2, row = 0)
    packet.size shouldBe 4
    packet[1] shouldBe 0x00.toByte()   // d=0 (rightmost, SPI first): cols 8-15, all zero
    packet[3] shouldBe 0x70.toByte()   // d=1 (leftmost, SPI second): cols 0-7, 'A' row 0
}
```

**Note on test access:** `buildPacket` is `internal` in `Max7219Matrix`. Since the test is in the same Gradle module (`src/test`), `internal` visibility is accessible directly. `buildBitmap` is `private` — the test must either call a helper that exposes it (e.g., make `buildBitmap` `internal` too, or inline the font data directly). Simplest approach: inline the known bitmap bytes directly in the test, based on `Font.asciiFont['A'] = byteArrayOf(126, 17, 17, 17, 126)` plus spacing and trailing zeros (22 bytes total as documented in RESEARCH.md §Pitfall 2).

**Imports to add to test file:**
```kotlin
import com.anjo.utils.Font
```
(Only if using Font directly; not needed if bitmap is inlined.)

---

## Shared Patterns

### Pattern: Coroutine Job Lifecycle
**Source:** `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` lines 113–133 and `OledDisplay.kt` lines 103–121 (identical structure)
**Apply to:** All three hardware drivers (preserved unchanged in concrete classes, job field moves to AbstractDisplayDriver)
```kotlin
override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
    stop()
    lastMessage = text
    job = scope.launch {
        try {
            // ... hardware-specific loop ...
        } catch (e: Exception) {
            lastError = "Scroll failed: ${e.message}"
        }
    }
}
```

### Pattern: Init Block Error Capture
**Source:** `src/main/kotlin/com/anjo/driver/LcdDisplay.kt` lines 27–47, `OledDisplay.kt` lines 27–47
**Apply to:** `Max7219Matrix.kt` `init` block (already uses same pattern at lines 47–52)
```kotlin
init {
    // hardware setup attempt
    try {
        initialize()
    } catch (e: Exception) {
        lastError = "Initialization failed: ${e.message}"
    }
}
```

### Pattern: Kotest FunSpec test structure
**Source:** `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` lines 1–52
**Apply to:** New test cases added in Max7219MatrixTest
```kotlin
class NameTest : FunSpec({
    test("should <behavior description>") {
        // arrange
        // act
        // assert using shouldBe / shouldContain
    }
})
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `AbstractDisplayDriver.kt` | abstract base class | — | No abstract base class exists yet; it is being introduced in this phase. Pattern is synthesized from the three concrete driver files it unifies. |

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/driver/`, `src/test/kotlin/com/anjo/driver/`
**Files scanned:** 6 source files read in full
**Pattern extraction date:** 2026-06-12
