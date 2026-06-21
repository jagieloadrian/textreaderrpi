---
phase: 08-refactor-dead-code-analysis
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - src/test/kotlin/com/anjo/ApplicationTest.kt
  - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
  - src/main/kotlin/com/anjo/config/model/ApiConfig.kt
  - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
  - src/main/resources/application.yaml
  - src/test/resources/application.yaml
  - src/main/kotlin/com/anjo/service/DisplaySelectionService.kt
  - src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt
  - src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt
  - src/test/kotlin/com/anjo/utils/FontTest.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/utils/Font.kt
  - src/main/kotlin/com/anjo/driver/Max7219Matrix.kt
  - src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt
  - src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt
findings:
  critical: 3
  warning: 5
  info: 4
  total: 12
status: issues_found
---

# Phase 08: Code Review Report

**Reviewed:** 2026-06-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

The reviewed files cover the config loading layer, display driver selection, the MAX7219 SPI driver, the screen driver service, and associated test suites. The core logic is well-structured, but there are three critical issues: a TOCTOU race condition in the display-switch path, a hardcoded SPI device ID that will crash at runtime when more than one MAX7219 zone is registered, and `String.toBoolean()` being used instead of the safe variant which silently produces `false` for any non-"true" string. Five warnings cover `System.err.println` bleeding into the structured log stream, yaml sections that are parsed but never loaded into the config model (dead config), a `queueDisplaySwitch` return value that lies to the caller, and two test reliability defects. Four info items cover comment violations and a duplicate assertion.

## Critical Issues

### CR-01: TOCTOU race condition in `queueDisplaySwitch` — mutex check and use are not atomic

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:168-179`

**Issue:** `displayMutex.isLocked` is read (line 168) and then, in the `else` branch, `selectionService.selectDisplay()` is called without acquiring the mutex (lines 173-179). Between those two instructions another coroutine can acquire the mutex, making `isLocked` stale. The display switch then runs concurrently with an active render operation — the driver reference `var driver` is mutated at line 176 while the render path (inside the mutex) holds a reference to the old driver. This is a data race on a non-atomic mutable var; in a concurrent context it means the wrong driver could be used for part of a render.

**Fix:** Move the "immediate switch" path inside a `withLock` block or use `tryLock`. The `pendingDisplayType` mechanism already exists for the locked case — the unlocked fast-path should simply call `selectDisplay` under the mutex:

```kotlin
fun queueDisplaySwitch(displayType: String): Boolean {
    val normalizedType = displayType.uppercase()
    val selectionService = displaySelectionService ?: return false

    if (displayMutex.tryLock()) {
        try {
            val switched = selectionService.selectDisplay(normalizedType)
            if (switched) selectionService.currentDriver()?.let { driver = it }
            return switched
        } finally {
            displayMutex.unlock()
        }
    }
    pendingDisplayType.set(normalizedType)
    return true
}
```

---

### CR-02: Hardcoded SPI device ID `"max7219"` prevents multi-zone registration

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:53`

**Issue:** The Pi4J `SpiConfig` is built with `.id("max7219")` — a static string. Pi4J requires unique string IDs per SPI device in the same `Context`. The project memory records that multi-zone support requires IDs such as `"max7219-zone-0"`, `"max7219-zone-1"`. When a second `Max7219Matrix` instance is constructed with the same context, Pi4J will throw a duplicate-registration exception. This is confirmed by the project's own design note: "Pi4J requires unique string IDs per SPI device." The single-zone case works today only by accident; the constructor swallows the exception and sets `spi = null`, meaning the second zone silently produces a dead display with no runtime error surfaced to the caller.

**Fix:** Accept a zone index (or explicit ID string) in the constructor and incorporate it into the SPI config:

```kotlin
class Max7219Matrix(
    private val ctx: Context,
    private val numDevices: Int = 2,
    private val zoneId: Int = 0,
) : AbstractDisplayDriver() {
    // ...
    val config = Spi.newConfigBuilder(ctx)
        .id("max7219-zone-$zoneId")
        .name("MAX7219 SPI zone $zoneId")
        // ...
```

The `defaultDriverFactory` in `DisplaySelectionService` must pass the zone index when constructing `Max7219Matrix`.

---

### CR-03: `String.toBoolean()` silently converts any non-"true" string to `false`

**File:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt:22`
**File:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt:51`

**Issue:** Kotlin's `String.toBoolean()` is not null-safe and, critically, it returns `false` for any string that is not exactly `"true"` (case-insensitive). If the YAML value is `"yes"`, `"1"`, `"on"`, or any typo, the result is silently `false` with no error. For `display.max7219.brightness`, a misconfigured value means the display runs at full brightness but the application believes it is managing brightness. There is also an ordering concern at line 22: `.getString()` is called on `propertyOrNull(...)` result but `.toBoolean()` is called on the `String` — if the property is present but blank, `"".toBoolean()` returns `false` rather than using the `?: true` fallback.

**Fix:** Use `toBooleanStrictOrNull()` (available since Kotlin 1.5) combined with the null-coalescing fallback, which throws on unrecognised values:

```kotlin
brightness = config.propertyOrNull("display.max7219.brightness")
    ?.getString()?.toBooleanStrictOrNull() ?: true,

enabled = config.propertyOrNull("metrics.enabled")
    ?.getString()?.toBooleanStrictOrNull() ?: true,
```

Alternatively, if leniency is intended, `equals("true", ignoreCase = true)` is explicit about the intent.

---

## Warnings

### WR-01: `System.err.println` bypasses the structured logging pipeline

**File:** `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt:35,48,111`

**Issue:** Three call sites write directly to `System.err` in addition to (or instead of) the SLF4J logger. In production the application runs with a JSON log formatter (`LOG_FORMAT=json`). Bare `System.err` output bypasses that formatter, appears as unstructured text in log aggregators (e.g. journald, Loki), and cannot be filtered or correlated with structured fields. Line 111 in `defaultDriverFactory` writes to `System.err` only — there is no corresponding `log.error()` call there. The SLF4J calls on lines 36, 49, and the absence of one on line 111 make the behaviour inconsistent across the three error sites.

**Fix:** Remove all three `System.err.println` calls. The SLF4J logger on line 36 and 49 already covers those paths. Add a `log.warn` on line 111 to match:

```kotlin
else -> {
    log.warn("Unknown display type requested: $displayType")
    null
}
```

---

### WR-02: `queueDisplaySwitch` returns `true` for a pending switch that may never succeed

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:169-171`

**Issue:** When `displayMutex.isLocked`, the method enqueues the type in `pendingDisplayType` and immediately returns `true`. The caller is told the switch succeeded, but the switch has not happened yet and may fail when `checkAndPerformPendingSwitch` eventually runs (e.g. if the display type is unknown). The caller — and by extension the route handler — treats `true` as a guarantee of success and will report HTTP 200 to the client for a switch that may silently fail.

**Fix:** Return a distinct sentinel for "queued" vs "succeeded", or change the contract to return `true` only on confirmed success. The simplest safe change is to return `false` in the queued case (fail-safe), document that callers must poll `currentDisplayType()` to confirm, and let the API surface a `202 Accepted` instead of `200 OK` for async switches.

---

### WR-03: `hardware`, `resources`, `timing`, and `logging` YAML sections are parsed by the application but never loaded into `ApplicationConfig`

**File:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` (entire file)
**File:** `src/main/resources/application.yaml:36-79`

**Issue:** The main `application.yaml` declares four top-level sections (`hardware`, `resources`, `timing`, `logging`) with environment-variable-backed values. `ConfigLoader.loadConfig()` reads none of them — they are silently ignored. This creates two problems:

1. `resources.screenDriverMaxSlots` and `timing.scrollSpeed` / `timing.refreshRate` appear to be config knobs but have no effect at runtime; operators who set `SCREEN_DRIVER_MAX_SLOTS` or `SCROLL_SPEED` via environment variables get no feedback that the value is not consumed.
2. `logging.level` and `logging.format` are present in the YAML but handled (if at all) outside the config model, meaning the config model is an incomplete representation of the application's configuration surface.

**Fix:** Either add data classes and load these sections into `ApplicationConfig`, or remove the dead sections from both YAML files to prevent operator confusion.

---

### WR-04: `clear()` in `Max7219Matrix` calls `stop()` which only cancels the coroutine job — it does not wait for the job to finish before sending SPI commands

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:86-94`

**Issue:** `clear()` calls `stop()` (line 86) which invokes `job?.cancel()`. `cancel()` is non-blocking — the coroutine is merely signalled to stop but may still be in the middle of a `spi?.write(...)` call inside `render()`. Immediately after, `clear()` sends its own `sendCommand(row, 0x00)` sequence (line 88). This interleaves two concurrent SPI write sequences on the same `spi` object, which is not thread-safe. The result is corrupted SPI packets and garbled display output.

**Fix:** Join the job before sending SPI clear commands. Because `clear()` is not a `suspend` function, the correct approach is to use `runBlocking { job?.join() }` inside `stop()`, or restructure `clear()` to be `suspend` and call `job?.cancelAndJoin()`.

---

### WR-05: `write()` in `Max7219Matrix` calls `stop()` then `clear()` — `clear()` calls `stop()` again, doubling the cancel

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:97-103`

**Issue:** `write()` calls `stop()` on line 98 then immediately calls `clear()` on line 99. `clear()` itself calls `stop()` on line 86. The double cancel is harmless in the current implementation (cancelling an already-cancelled job is a no-op) but it establishes a fragile pattern: `lastMessage` is set to `null` by `clear()` and then overwritten by `write()` on line 100 — but only because the call order happens to place the `lastMessage = text` assignment after `clear()`. If the order inside `write()` changes, the message tracking breaks silently.

**Fix:** Remove the redundant `stop()` call from `write()` and rely solely on `clear()` to perform the stop, or restructure so that `clear()` does not call `stop()` and callers invoke them separately.

---

## Info

### IN-01: Inline comments in Kotlin source files violate project coding rules

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:86,115,165`
**File:** `src/main/kotlin/com/anjo/utils/Font.kt:22,34,62,90`
**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:39,93`

**Issue:** Project rule (recorded in `feedback_coding_rules.md`): "Do not add comments to Kotlin files. This includes inline `//`, block `/* */`, and KDoc. No exceptions." Eight comment lines exist across three production source files, including KDoc (`/** ... */`) on two methods in `ScreenDriverService.kt` and four category-separator comments in `Font.kt`.

**Fix:** Remove all comment lines from the affected files.

---

### IN-02: Duplicate assertion in `ApplicationTest` — line 47 is an exact copy of line 46

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:46-47`

**Issue:** The test "should serve static assets after context startup" asserts `response.status shouldBe HttpStatusCode.OK` twice in immediate succession. The second assertion can never fail independently of the first. This is dead test code.

**Fix:** Remove line 47.

---

### IN-03: `ApplicationTest` "should include application name in health response" does not assert the application name

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:59-65`

**Issue:** The test is named "should include application name in health response" but asserts only that the status code is `200 OK`. It does not read or assert on the response body content, making the test name misleading and the coverage claim false.

**Fix:** Add a body assertion:

```kotlin
response.bodyAsText() shouldContain "TextReaderRpi"
```

---

### IN-04: `Max7219Matrix` SPI ID is hardcoded as `"max7219"` while the `.name()` is `"MAX7219 SPI"` — inconsistent casing convention

**File:** `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:53-54`

**Issue:** `.id("max7219")` uses lowercase while `.name("MAX7219 SPI")` uses uppercase. Pi4J IDs are used as lookup keys; inconsistent conventions make diagnostics harder when inspecting the Pi4J registry. This is subordinate to CR-02 but should be addressed at the same time when the zone suffix is added.

**Fix:** Normalise to a consistent scheme, e.g. `.id("max7219-zone-0")` and `.name("MAX7219 SPI Zone 0")`.

---

_Reviewed: 2026-06-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
