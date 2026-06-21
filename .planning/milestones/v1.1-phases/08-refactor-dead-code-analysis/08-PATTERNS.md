# Phase 8: Refactor + Dead Code Analysis - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 12 files (modified) + 3 files (deleted)
**Analogs found:** 12 / 12 (all modifications have direct in-file analogs; deletions need no analog)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/test/kotlin/com/anjo/ApplicationTest.kt` | test (integration) | request-response | itself (existing `testApplication` tests) | exact |
| `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` | model (config) | — | itself (data class field removal) | exact |
| `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` | utility (config loader) | transform | itself (parallel construction blocks for other configs) | exact |
| `src/main/kotlin/com/anjo/config/model/ApiConfig.kt` | model (config) | — | itself (field removal) | exact |
| `src/main/kotlin/com/anjo/config/model/HardwareConfig.kt` | model (config) | — | DELETE — no analog needed | n/a |
| `src/main/kotlin/com/anjo/config/model/TimingConfig.kt` | model (config) | — | DELETE — no analog needed | n/a |
| `src/main/kotlin/com/anjo/config/model/LoggingConfig.kt` | model (config) | — | DELETE — no analog needed | n/a |
| `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` | service | event-driven | itself (existing method removal) | exact |
| `src/main/kotlin/com/anjo/service/ScreenDriver.kt` → `ScreenDriverService.kt` | service | request-response | itself (file rename + method removal) | exact |
| `src/main/kotlin/com/anjo/utils/Font.kt` | utility | transform | itself (adding safe lookup function) | exact |
| `src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt` | test | request-response | itself (assertion removal) | exact |
| `src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt` | test | request-response | itself (assertion removal) | exact |
| `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` | test | request-response | itself (call-site rename) | exact |
| `src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt` | test | request-response | itself (call-site rename) | exact |
| `src/test/kotlin/com/anjo/utils/FontTest.kt` | test (new file) | transform | `ScreenDriverRecoveryTest.kt` (FunSpec + mockk pattern) | role-match |

---

## Pattern Assignments

### `src/test/kotlin/com/anjo/ApplicationTest.kt` — DI smoke test addition (REF-03)

**Analog:** itself — lines 1–51 show the existing `testApplication` block pattern.

**Existing test structure to follow** (lines 1–17):
```kotlin
package com.anjo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ApplicationTest : FunSpec({

    test("should start application context and serve health endpoint") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }
```

**New test to ADD** — append after line 50, before closing `})`:
```kotlin
    test("should resolve all configureDI bindings without error") {
        testApplication {
            application { module() }
            val deps = application.dependencies
            deps.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>()) shouldNotBeNull {}
            deps.getBlocking<ApiConfig>(DependencyKey<ApiConfig>()) shouldNotBeNull {}
            deps.getBlocking<DisplayConfig>(DependencyKey<DisplayConfig>()) shouldNotBeNull {}
            deps.getBlocking<CoroutineDispatcher>(DependencyKey<CoroutineDispatcher>()) shouldNotBeNull {}
            deps.getBlocking<MetricRegistry>(DependencyKey<MetricRegistry>()) shouldNotBeNull {}
            deps.getBlocking<DisplaySelectionService>(DependencyKey<DisplaySelectionService>()) shouldNotBeNull {}
            deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>()) shouldNotBeNull {}
            deps.getBlocking<MetricsCollector>(DependencyKey<MetricsCollector>()) shouldNotBeNull {}
            deps.getBlocking<ScheduleRepository>(DependencyKey<ScheduleRepository>()) shouldNotBeNull {}
            deps.getBlocking<EffectRendererFactory>(DependencyKey<EffectRendererFactory>()) shouldNotBeNull {}
            deps.getBlocking<SchedulerService>(DependencyKey<SchedulerService>()) shouldNotBeNull {}
        }
    }
```

**Additional imports to add at top of file:**
```kotlin
import com.anjo.config.model.ApplicationConfig
import com.anjo.config.model.ApiConfig
import com.anjo.config.model.DisplayConfig
import com.anjo.service.DisplaySelectionService
import com.anjo.service.EffectRendererFactory
import com.anjo.service.MetricsCollector
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
import com.anjo.db.ScheduleRepository
import com.codahale.metrics.MetricRegistry
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CoroutineDispatcher
```

**Constraint:** Do NOT call any method on the resolved instances — `shouldNotBeNull {}` only (D-01).

---

### `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` — field removal (REF-01 / D-06)

**Analog:** itself — current file at lines 1–12.

**Current state** (lines 1–12):
```kotlin
data class ApplicationConfig(
    val display: DisplayConfig,
    val hardware: HardwareConfig,
    val api: ApiConfig,
    val timing: TimingConfig,
    val logging: LoggingConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig
)
```

**Target state** (remove `hardware`, `timing`, `logging` fields and their imports):
```kotlin
data class ApplicationConfig(
    val display: DisplayConfig,
    val api: ApiConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig
)
```

---

### `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` — dead config wiring removal (REF-01 / D-06, D-07)

**Analog:** itself — the parallel construction blocks at lines 47–67 show the pattern to delete.

**Lines to DELETE:**
- Lines 5–7: imports for `HardwareConfig`, `LoggingConfig`, `TimingConfig`
- Lines 47–50: `val hardwareConfig = HardwareConfig(...)` block
- Lines 54–55: `queueSize = config.propertyOrNull("api.queueSize")...` named argument inside `ApiConfig(...)` (line 54)
- Lines 59–62: `val timingConfig = TimingConfig(...)` block
- Lines 64–67: `val loggingConfig = LoggingConfig(...)` block
- Lines 90–98: return statement — remove `hardware = hardwareConfig,`, `timing = timingConfig,`, `logging = loggingConfig,` named args

**Target return call** (lines 89–98 after edit):
```kotlin
        return ApplicationConfig(
            display = displayConfig,
            api = apiConfig,
            metrics = metricsConfig,
            retryConfig = retryConfig,
            databaseConfig
        )
```

---

### `src/main/kotlin/com/anjo/config/model/ApiConfig.kt` — `queueSize` field removal (REF-01 / D-07)

**Analog:** itself — current file at lines 1–8.

**Current state** (lines 1–8):
```kotlin
data class ApiConfig(
    val maxTextLength: Int,
    val queueSize: Int,
    val rateLimitPerMinute: Int,
    val metricsRateLimitPerMinute: Int = 120,
)
```

**Target state** (remove `queueSize` field):
```kotlin
data class ApiConfig(
    val maxTextLength: Int,
    val rateLimitPerMinute: Int,
    val metricsRateLimitPerMinute: Int = 120,
)
```

---

### `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` — dead method and field removal (REF-01 / D-08, D-09)

**Analog:** itself — current file at lines 1–144.

**Items to DELETE:**

Line 9: `import java.util.concurrent.ConcurrentLinkedQueue`

Line 21: `private val pendingSwitches = ConcurrentLinkedQueue<String>()`

Lines 43–56: entire `private fun logCurrentData()` method body:
```kotlin
    private fun logCurrentData() {
        ctx.providers().all.forEach { (string, provider) ->
            log.info("Provider : $string with data type=${provider.describe().description()}")
        }
        ctx.platforms().all.forEach { (string, provider) ->
            log.info("Platform : $string with data type=${provider.describe().description()}")
        }
        ctx.properties().all().forEach { (string, provider) ->
            log.info("Property : $string with data type=${provider}")
        }
        ctx.registry().all().forEach { (string, registry) ->
            log.info("Registry : $string with data type=${registry}")
        }
    }
```

Line 36: `logCurrentData()` call in `selectDisplayAtStartup()`

Line 98: `logCurrentData()` call in `selectDisplay()`

Line 96: `pendingSwitches.offer(normalizedType)` call in `selectDisplay()`

Lines 109–113: `getPendingSwitches()` and `clearPendingSwitches()` method bodies:
```kotlin
    fun getPendingSwitches(): List<String> = pendingSwitches.toList()

    fun clearPendingSwitches() {
        pendingSwitches.clear()
    }
```

**After removal**, `selectDisplay()` success branch (lines 92–100) becomes:
```kotlin
            return if (newDriver != null) {
                currentDriver?.stop()
                currentDriver = newDriver
                currentType = normalizedType
                log.info("Driver switched: $normalizedType")
                true
            } else {
```

---

### `src/main/kotlin/com/anjo/service/ScreenDriver.kt` → `ScreenDriverService.kt` — rename + method removal (REF-02 / D-10, D-14)

**Step 1:** `git mv src/main/kotlin/com/anjo/service/ScreenDriver.kt src/main/kotlin/com/anjo/service/ScreenDriverService.kt`

No import updates required — all callers already import `com.anjo.service.ScreenDriverService` (class name unchanged). Package declaration `package com.anjo.service` is unaffected.

**Step 2:** DELETE `readInput()` method (lines 147–149):
```kotlin
    suspend fun readInput(input: String) {
        displayImmediate(input)
    }
```

**After deletion**, `executeWithRecovery` (previously line 151) immediately follows `displayScheduled`. No other changes to `ScreenDriver.kt`.

**Run after rename:** `./gradlew clean test` (not just `test`) to force full recompilation and clear incremental cache.

---

### `src/main/kotlin/com/anjo/utils/Font.kt` — bounds fix (REF-02 / D-15)

**Analog:** itself — current file at lines 1–97.

**Key facts from file inspection:**
- All existing font entries are **5-byte `ByteArray`** (e.g., `' ' to byteArrayOf(0,0,0,0,0)`)
- Font is an `object` with `val asciiFont: Map<Char, ByteArray>`
- `Font.kt` is in `com/anjo/utils/` which is excluded from JaCoCo — no coverage impact

**Pattern to ADD** — append before closing `}` of the `Font` object:

```kotlin
    fun getChar(char: Char): ByteArray = asciiFont[char] ?: ByteArray(5) { 0 }
```

**Critical note:** Use `ByteArray(5) { 0 }` not `ByteArray(8) { 0 }`. CONTEXT.md says 8 but existing entries are 5-byte column bitmaps. Read `Max7219Matrix.kt` to confirm before implementing — if it expects 8 bytes, adjust accordingly.

---

### `src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt` — assertion removal (REF-01 / D-09)

**Analog:** itself — current file at lines 1–77.

**Items to DELETE:**

Line 9: `import io.kotest.matchers.collections.shouldContainExactly` (if no other usage remains after test deletion below)

In test "should load startup driver from display config" (lines 20–29):
- DELETE line 28: `service.getPendingSwitches().isEmpty().shouldBeTrue()`

In test "should stop old driver and update type when switching display" (lines 31–43):
- DELETE line 41: `service.getPendingSwitches() shouldContainExactly listOf("LCD")`

In test "should keep current driver unchanged on failed switch" (lines 45–54):
- DELETE line 53: `service.getPendingSwitches().isEmpty().shouldBeTrue()`

DELETE the entire "should empty queue after clearing pending switches" test (lines 65–76):
```kotlin
    test("should empty queue after clearing pending switches") {
        val maxDriver = mockk<DisplayDriver>(relaxed = true)
        val lcdDriver = mockk<DisplayDriver>(relaxed = true)
        val service = DisplaySelectionService(
            ctx = context, displayConfig = config,
            driverFactory = { type, _, _ -> if (type == "MAX7219") maxDriver else lcdDriver }
        )
        service.selectDisplay("lcd") shouldBe true
        service.getPendingSwitches() shouldContainExactly listOf("LCD")
        service.clearPendingSwitches()
        service.getPendingSwitches().isEmpty().shouldBeTrue()
    }
```

---

### `src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt` — assertion removal (REF-01 / D-09)

**Analog:** itself — current file at lines 1–73.

**Items to DELETE:**

Line 10: `import io.kotest.matchers.collections.shouldContainExactly` (verify no other usage)

Line 44: `selection.getPendingSwitches() shouldContainExactly listOf("LCD", "OLED")`

---

### `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` — call-site rename (REF-01 / D-10)

**Analog:** itself — current file at lines 1–112.

**Pattern for rename** — replace every `readInput("...")` call with `displayImmediate("...")`:

Line 32: `service(driver).readInput("hello")` → `service(driver).displayImmediate("hello")`
Line 44: `service(driver).readInput("test message")` → `service(driver).displayImmediate("test message")`
Line 52: `service(driver).readInput("this will fail hardware")` → `service(driver).displayImmediate("this will fail hardware")`
Line 60 (first): `svc.readInput("first message")` → `svc.displayImmediate("first message")`
Line 61: `svc.readInput("second message")` → `svc.displayImmediate("second message")`

All calls are already inside coroutine scope (FunSpec runs tests as coroutines). `displayImmediate` is also `suspend fun` — no scope changes needed.

---

### `src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt` — call-site rename (REF-01 / D-10)

**Analog:** itself — current file at lines 1–55.

**Pattern for rename** — replace every `readInput("...")` call with `displayImmediate("...")`:

Line 32: `repeat(5) { svc.readInput("text $it") }` → `repeat(5) { svc.displayImmediate("text $it") }`
Line 38 (inside `every { driver.scrollText... }` test): `svc.readInput("will fail")` → `svc.displayImmediate("will fail")`
Line 44 (inside `should record execution time` test): No `readInput` visible on line 44; verify by re-reading if needed.

**Note on metric key strings** (lines 33–35, 44–45, 52–53): The metric names `"textreaderrpi.screenDriver.readInput.*"` are hard-coded strings in `ScreenDriverMetrics.kt`, NOT derived from the method name. Do NOT change these strings.

---

### `src/test/kotlin/com/anjo/utils/FontTest.kt` — new file (REF-04 / Wave 0 gap)

**Analog:** `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` — same FunSpec style, no mocks needed (Font is a pure object).

**Import pattern** (from ScreenDriverRecoveryTest.kt lines 1–16):
```kotlin
package com.anjo.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
```

**Core test pattern:**
```kotlin
class FontTest : FunSpec({

    test("should return mapped glyph for known character") {
        val result = Font.getChar('A')
        result shouldBe byteArrayOf(126,17,17,17,126)
    }

    test("should return blank ByteArray for unmapped character") {
        val result = Font.getChar(' ')
        result shouldBe ByteArray(5) { 0 }
    }

    test("should return blank ByteArray for high unicode character") {
        val result = Font.getChar('中')
        result shouldBe ByteArray(5) { 0 }
    }
})
```

**Note:** Verify blank ByteArray width (5 vs 8) matches the `getChar()` implementation after Max7219Matrix.kt is inspected.

---

## Shared Patterns

### Kotest `FunSpec` test structure
**Source:** `src/test/kotlin/com/anjo/ApplicationTest.kt` lines 1–51 and `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` lines 1–112
**Apply to:** `FontTest.kt` (new), all modified test files
```kotlin
class XxxTest : FunSpec({
    test("should <verb> <expected outcome>") {
        // arrange
        // act
        // assert via Kotest matchers (shouldBe, shouldNotBeNull, etc.)
    }
})
```

### `testApplication` block
**Source:** `src/test/kotlin/com/anjo/ApplicationTest.kt` lines 11–17
**Apply to:** DI smoke test in `ApplicationTest.kt`
```kotlin
testApplication {
    application { module() }
    // use `application.dependencies` or `client.*` here
}
```

### No comments in code rule
**Source:** CONTEXT.md `code_context` section + project rule confirmed by RESEARCH.md
**Apply to:** All modified production files
Do not add `//` or `/* */` explanatory comments when removing or editing code. Remove inline comments on deleted lines without replacing them.

---

## No Analog Found

All files in scope are modifications to existing files or follow well-established existing test patterns. No file requires inventing a new pattern.

| File | Role | Reason |
|------|------|--------|
| `FontTest.kt` (new) | test | No existing FontTest; pattern sourced from ScreenDriverRecoveryTest.kt (same FunSpec style) |

---

## Files to Delete (no edit needed)

| File | Reason |
|------|--------|
| `src/main/kotlin/com/anjo/config/model/HardwareConfig.kt` | Zero callers outside own definition; confirmed dead |
| `src/main/kotlin/com/anjo/config/model/TimingConfig.kt` | Zero callers outside own definition; confirmed dead |
| `src/main/kotlin/com/anjo/config/model/LoggingConfig.kt` | Zero callers outside own definition; confirmed dead |

---

## Execution Order Constraint

Per D-05: DI smoke test MUST be written and passing before any dead code removal begins. Planner should sequence tasks as:
1. Add DI smoke test to `ApplicationTest.kt` → run `./gradlew test`
2. Delete `HardwareConfig.kt`, `TimingConfig.kt`, `LoggingConfig.kt`; edit `ApplicationConfig.kt`, `ConfigLoader.kt`, `ApiConfig.kt` → run `./gradlew test`
3. Edit `DisplaySelectionService.kt`; update `DisplaySelectionServiceTest.kt` and `DriverIntegrationTest.kt` → run `./gradlew test`
4. Rename `ScreenDriver.kt` → `ScreenDriverService.kt`; remove `readInput()`; update `ScreenDriverRecoveryTest.kt` and `ScreenDriverResourceTest.kt` → run `./gradlew clean test`
5. Fix `Font.kt`; write `FontTest.kt` → run `./gradlew test`
6. Run `./gradlew test jacocoTestCoverageVerification` as final gate

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`
**Files read:** 14
**Pattern extraction date:** 2026-06-15
