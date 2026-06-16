# Phase 12: Observability Gap Closures - Pattern Map

**Mapped:** 2026-06-16
**Files analyzed:** 11 (3 new, 8 modified)
**Analogs found:** 11 / 11

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/kotlin/com/anjo/model/HealthDetailResponse.kt` | model | request-response | `src/main/kotlin/com/anjo/model/MetricsModels.kt` | exact |
| `src/main/kotlin/com/anjo/model/HardwareMetrics.kt` | model | event-driven | `src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt` | exact |
| `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` | route | request-response | `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` | exact |
| `src/main/kotlin/com/anjo/model/ZoneStatus.kt` | model | CRUD | `src/main/kotlin/com/anjo/model/MetricsModels.kt` | role-match |
| `src/main/kotlin/com/anjo/service/MetricsCollector.kt` | service | CRUD | `src/main/kotlin/com/anjo/service/MetricsCollector.kt` | self |
| `src/main/kotlin/com/anjo/service/RetryPolicy.kt` | utility | event-driven | `src/main/kotlin/com/anjo/service/RetryPolicy.kt` | self |
| `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` | service | event-driven | `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` | self |
| `src/main/kotlin/com/anjo/di/DependencyInjection.kt` | config | CRUD | `src/main/kotlin/com/anjo/di/DependencyInjection.kt` | self |
| `src/main/kotlin/com/anjo/di/Monitoring.kt` | config | request-response | `src/main/kotlin/com/anjo/di/Monitoring.kt` | self |
| `src/main/kotlin/com/anjo/routing/Routing.kt` | route | request-response | `src/main/kotlin/com/anjo/routing/Routing.kt` | self |
| `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` | test | request-response | `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` | self |
| `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` | test | request-response | `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` | self |
| `src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt` | test | event-driven | `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` | role-match |

---

## Pattern Assignments

### `src/main/kotlin/com/anjo/model/HealthDetailResponse.kt` (model, request-response)

**Analog:** `src/main/kotlin/com/anjo/model/MetricsModels.kt`

**Imports pattern** (lines 1-4):
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable
```

**Core pattern** (lines 5-26, full file contents of MetricsModels.kt):
```kotlin
@Serializable
data class MetricsResponse(
    val timestamp: String,
    val groups: List<MetricGroup>,
)
```

Apply the same `@Serializable data class` structure. `HealthDetailResponse` is a flat data class with no nested lists (except `Map<String, String?>`):
```kotlin
@Serializable
data class HealthDetailResponse(
    val uptime: Long,
    val memoryUsed: Long,
    val memoryMax: Long,
    val displayStatus: String,
    val totalFailures: Long,
    val zoneErrors: Map<String, String?>,
)
```

**No companion object, no factory** — this is a pure DTO assembled in the route handler.

---

### `src/main/kotlin/com/anjo/model/HardwareMetrics.kt` (model, event-driven)

**Analog:** `src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt` (lines 1-29, full file)

**Imports pattern** (lines 1-8):
```kotlin
package com.anjo.model

import com.anjo.config.model.MetricsConfig
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
```

Note: `HardwareMetrics` uses `Counter` only — drop `Meter` and `Timer` imports from `ScreenDriverMetrics`.

**Core pattern — data class + DISABLED sentinel + from() factory** (lines 9-29):
```kotlin
data class ScreenDriverMetrics(
    val acceptedMeter: Meter? = null,
    val failedMeter: Meter? = null,
    val inFlightCounter: Counter? = null,
    val executionTimer: Timer? = null,
) {
    companion object {
        val DISABLED = ScreenDriverMetrics()

        fun from(registry: MetricRegistry, config: MetricsConfig): ScreenDriverMetrics {
            if (!config.enabled) return DISABLED
            val p = config.prefix
            return ScreenDriverMetrics(
                acceptedMeter = registry.meter("$p.screenDriver.readInput.accepted"),
                failedMeter = registry.meter("$p.screenDriver.readInput.failed"),
                inFlightCounter = registry.counter("$p.screenDriver.readInput.inFlight"),
                executionTimer = registry.timer("$p.screenDriver.readInput.execution"),
            )
        }
    }
}
```

`HardwareMetrics` replaces all fields with `Counter?` fields and prefix `$p.hardware.*`:
- `displayFailureCounter = registry.counter("$p.hardware.display.failures")`
- `recoveryRetryCounter = registry.counter("$p.hardware.recovery.retries")`
- `inFlightCounter = registry.counter("$p.hardware.display.inFlight")`
- `skippedCounter = registry.counter("$p.hardware.display.skipped")`

---

### `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` (route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` (lines 1-17, full file)

**Imports pattern** (lines 1-8):
```kotlin
package com.anjo.routing

import com.anjo.di.installMetricsRateLimiting
import com.anjo.service.MetricsCollector
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
```

Replace `MetricsCollector` import with `ZoneRegistry`, `ScreenDriverMetrics`, and `HealthDetailResponse`. Add `ManagementFactory` and `Runtime` imports for JVM data.

**Core pattern** (lines 10-17):
```kotlin
fun Route.metricsRoutes(metricsCollector: MetricsCollector, metricsRateLimitPerMinute: Int) {
    route("/metrics") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            call.respond(metricsCollector.collect())
        }
    }
}
```

`healthRoutes()` follows the exact same shape with `/health/detail` and inline response assembly:
```kotlin
fun Route.healthRoutes(
    zoneRegistry: ZoneRegistry,
    screenDriverMetrics: ScreenDriverMetrics,
    metricsRateLimitPerMinute: Int,
) {
    route("/health/detail") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            val runtime = Runtime.getRuntime()
            val zones = zoneRegistry.listAll()
            call.respond(
                HealthDetailResponse(
                    uptime = ManagementFactory.getRuntimeMXBean().uptime,
                    memoryUsed = runtime.totalMemory() - runtime.freeMemory(),
                    memoryMax = runtime.maxMemory(),
                    displayStatus = if (zones.any { it.status == "ONLINE" }) "ONLINE" else "OFFLINE",
                    totalFailures = screenDriverMetrics.failedMeter?.count ?: 0L,
                    zoneErrors = zones.associate { it.id to it.error },
                )
            )
        }
    }
}
```

---

### `src/main/kotlin/com/anjo/model/ZoneStatus.kt` (model, CRUD — additive change)

**Analog:** `src/main/kotlin/com/anjo/model/ZoneStatus.kt` (lines 1-12, full file)

**Current file:**
```kotlin
@Serializable
data class ZoneStatus(
    val id: String,
    val type: String,
    val status: String,
    val ip: String? = null,
    val lastSeenAt: String? = null
)
```

**Change:** Add `val error: String? = null` as a nullable optional field (same pattern as `ip` and `lastSeenAt`). Zero breaking changes — existing code that constructs `ZoneStatus` without `error` stays valid via default null.

---

### `src/main/kotlin/com/anjo/service/MetricsCollector.kt` (service, CRUD — additive change)

**Analog:** `src/main/kotlin/com/anjo/service/MetricsCollector.kt` (lines 1-43, full file)

**Constructor change** — add `HardwareMetrics` parameter after `metricRegistry`:
```kotlin
class MetricsCollector(
    private val metricRegistry: MetricRegistry,
    private val hardwareMetrics: HardwareMetrics,  // new
)
```

**collect() change** (line 13-16) — add `hardwareGroup()` to the groups list:
```kotlin
fun collect(): MetricsResponse = MetricsResponse(
    timestamp = Instant.now().toString(),
    groups = listOf(runtimeGroup(), apiGroup(), hardwareGroup())
)
```

**New private fun** — copy `runtimeGroup()` structure (lines 18-28) but reading counter `.count` instead of gauge values:
```kotlin
private fun hardwareGroup(): MetricGroup = MetricGroup(
    name = "hardware",
    metrics = listOf(
        MetricEntry(key = "display.failures",  type = "counter", count = hardwareMetrics.displayFailureCounter?.count ?: 0L),
        MetricEntry(key = "recovery.retries",  type = "counter", count = hardwareMetrics.recoveryRetryCounter?.count  ?: 0L),
        MetricEntry(key = "display.inFlight",  type = "counter", count = hardwareMetrics.inFlightCounter?.count       ?: 0L),
        MetricEntry(key = "display.skipped",   type = "counter", count = hardwareMetrics.skippedCounter?.count        ?: 0L),
    )
)
```

---

### `src/main/kotlin/com/anjo/service/RetryPolicy.kt` (utility, event-driven — additive change)

**Analog:** `src/main/kotlin/com/anjo/service/RetryPolicy.kt` (lines 1-28, full file)

**Current signature** (line 11):
```kotlin
suspend fun <T> retryWithBackoff(config: RetryConfig = RetryConfig(), block: suspend () -> T): T {
```

**Change:** Add optional `hardwareMetrics: HardwareMetrics? = null` between `config` and `block`. All existing callers compile unchanged:
```kotlin
suspend fun <T> retryWithBackoff(
    config: RetryConfig = RetryConfig(),
    hardwareMetrics: HardwareMetrics? = null,
    block: suspend () -> T,
): T {
```

**Counter placement inside the existing catch block** (lines 17-26):
```kotlin
} catch (e: Exception) {
    attempt++
    if (attempt >= config.maxAttempts) {
        log.error("All ${config.maxAttempts} retry attempts exhausted: ${e.message}")
        hardwareMetrics?.displayFailureCounter?.inc()   // final failure only
        throw e
    }
    log.warn("Attempt $attempt/${config.maxAttempts} failed, retrying in ${delayMs}ms: ${e.message}")
    hardwareMetrics?.recoveryRetryCounter?.inc()        // each retry
    delay(delayMs.milliseconds)
    delayMs = min((delayMs * config.factor).toLong(), config.maxDelayMs)
}
```

---

### `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` (service, event-driven — additive changes)

**Analog:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` (lines 36-144)

**Constructor change** — add `hardwareMetrics: HardwareMetrics` after existing `metrics`:
```kotlin
class ScreenDriverService(
    private val zoneRegistry: ZoneRegistry,
    private val ioDispatcher: CoroutineDispatcher,
    private val retryConfig: RetryConfig,
    private val metrics: ScreenDriverMetrics,
    private val hardwareMetrics: HardwareMetrics = HardwareMetrics.DISABLED,  // new
    private val effectFactory: EffectRendererFactory = EffectRendererFactory(),
    private val historyRepository: HistoryRepository? = null,
)
```

**inFlightCounter pattern** — mirror the existing `metrics.inFlightCounter` pattern (lines 128-143) already used for `ScreenDriverMetrics`:
- Line 128: `metrics.inFlightCounter?.inc()` — add `hardwareMetrics.inFlightCounter?.inc()` alongside it
- Line 140: `metrics.inFlightCounter?.dec()` — add `hardwareMetrics.inFlightCounter?.dec()` alongside it

**skippedCounter** — increment in the SKIP_NEW drop path in `displayImmediate` (lines 63-66):
```kotlin
val zoneMutex = acquireMutex(zoneId, conflictPolicy) ?: run {
    log.info("SKIP_NEW: display busy for zone=$zoneId, dropping request for text '${text.take(30)}'")
    hardwareMetrics.skippedCounter?.inc()   // new
    return DisplayResult.Accepted(false)
}
```

**retryWithBackoff callers** — wherever `retryWithBackoff(config, block)` is called inside `ScreenDriverService`, add `hardwareMetrics = hardwareMetrics` as named argument.

---

### `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (config, CRUD — additive change)

**Analog:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (lines 28-91, full file)

**Registration pattern** (lines 33-34):
```kotlin
val metricRegistry = MetricRegistry()
val screenDriverMetrics = ScreenDriverMetrics.from(metricRegistry, appConfig.metrics)
```

Add `hardwareMetrics` immediately after `screenDriverMetrics` (same factory pattern):
```kotlin
val hardwareMetrics = HardwareMetrics.from(metricRegistry, appConfig.metrics)
```

**Constructor injection changes:**

`screenDriverService` (lines 45-51) — add `hardwareMetrics = hardwareMetrics`:
```kotlin
val screenDriverService = ScreenDriverService(
    zoneRegistry = zoneRegistry,
    ioDispatcher = Dispatchers.IO,
    retryConfig = appConfig.retryConfig,
    metrics = screenDriverMetrics,
    hardwareMetrics = hardwareMetrics,   // new
    historyRepository = historyRepository,
)
```

`metricsCollector` (line 53) — add `hardwareMetrics`:
```kotlin
val metricsCollector = MetricsCollector(metricRegistry, hardwareMetrics)
```

**provide block** (lines 73-90) — add `provide { hardwareMetrics }` after `provide { metricRegistry }`.

---

### `src/main/kotlin/com/anjo/di/Monitoring.kt` (config, request-response — additive change)

**Analog:** `src/main/kotlin/com/anjo/di/Monitoring.kt` (lines 1-53, full file)

**Current healthChecks block** (lines 32-34):
```kotlin
healthChecks {
    check("appAlive") { true }
}
```

**Change:** Add `zoneRegistry: ZoneRegistry by dependencies` alongside `screenDriverService` at the top of `configureMonitoring()`, then add the new check:
```kotlin
healthChecks {
    check("appAlive") { true }
    check("displayAvailable") { zoneRegistry.listAll().any { it.status == "ONLINE" } }
}
```

---

### `src/main/kotlin/com/anjo/routing/Routing.kt` (route, request-response — additive change)

**Analog:** `src/main/kotlin/com/anjo/routing/Routing.kt` (lines 30-66, full file)

**Current routing block** (lines 42-65):
```kotlin
routing {
    staticResources("/static", "static")
    webRoutes(screenDriverService)
    scheduleUIRoutes(scheduleRepository)
    historyUIRoutes(historyService)
    zonesUIRoutes(zoneRegistry, zoneRepository)
    metricsRoutes(metricsCollector, apiConfig.metricsRateLimitPerMinute)

    route("/api/v1") { ... }

    swaggerUI(path = "openapi") { ... }   // already last — OBS-03 verified
}
```

**Changes:**
1. Add `val screenDriverMetrics: ScreenDriverMetrics by dependencies` alongside other `by dependencies` declarations.
2. Add `healthRoutes(zoneRegistry, screenDriverMetrics, apiConfig.metricsRateLimitPerMinute)` immediately after `metricsRoutes(...)` (before the `route("/api/v1")` block).
3. `swaggerUI` is already last — no reorder needed unless OBS-03 verification proves otherwise.

---

### `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` (test, request-response — update assertions)

**Analog:** `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` (lines 1-58, full file)

**Tests to update:**

Line 31 — `shouldHaveSize 2` → `shouldHaveSize 3`:
```kotlin
json["groups"]!!.jsonArray shouldHaveSize 3
```

Lines 39-42 — add "hardware" to group names assertion:
```kotlin
groupNames shouldBe listOf("runtime", "api", "hardware")
```

**No other changes to test structure or imports.** The `testApplication { application { module() } }` pattern stays identical.

---

### `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` (test, request-response — update + new cases)

**Analog:** `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` (lines 1-39, full file)

**Test to update** (lines 32-38) — flip 404 → 200 and add field assertions:
```kotlin
test("should return 200 with HealthDetailResponse fields for GET /health/detail") {
    testApplication {
        application { module() }
        val response = client.get("/health/detail")
        response.status shouldBe HttpStatusCode.OK
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["uptime"] shouldNotBe null
        json["memoryUsed"] shouldNotBe null
        json["displayStatus"]!!.jsonPrimitive.content shouldBeIn listOf("ONLINE", "OFFLINE")
    }
}
```

**New test** — add `displayAvailable` check appears in `/health` response:
```kotlin
test("should return displayAvailable check for GET /health") {
    testApplication {
        application { module() }
        val response = client.get("/health")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "displayAvailable"
    }
}
```

---

### `src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt` (test, event-driven — new file)

**Analog:** `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` (test structure/imports)

**New file — test structure to follow:**
```kotlin
package com.anjo.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
```

Use `FunSpec` with `test("...") { }` blocks (same convention as all other tests in the project). Test cases to cover:
- `HardwareMetrics.DISABLED` has all null fields
- `HardwareMetrics.from(registry, config)` with `config.enabled = false` returns `DISABLED`
- `HardwareMetrics.from(registry, config)` with `config.enabled = true` returns non-null counters
- Counter names registered match the `textreaderrpi.hardware.*` prefix

---

## Shared Patterns

### Rate Limiting
**Source:** `src/main/kotlin/com/anjo/di/RateLimiting.kt` lines 33-49
**Apply to:** `HealthRoutes.kt`
```kotlin
fun Route.installMetricsRateLimiting(requestsPerMinute: Int) {
    install(RateLimiting) {
        rateLimiter {
            type = TokenBucket::class
            rate = 1.minutes
            capacity = requestsPerMinute.coerceAtLeast(1)
        }
        rateLimitExceededHandler = {
            this.response.header(HttpHeaders.RetryAfter, METRICS_RETRY_AFTER_SECONDS)
            this.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("error" to "Metrics rate limit exceeded. Retry-After: ${METRICS_RETRY_AFTER_SECONDS}s")
            )
        }
    }
}
```
`healthRoutes()` calls `installMetricsRateLimiting(metricsRateLimitPerMinute)` — same function, same rate.

### Metrics Factory Pattern
**Source:** `src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt` lines 15-28
**Apply to:** `HardwareMetrics.kt`
```kotlin
companion object {
    val DISABLED = ScreenDriverMetrics()
    fun from(registry: MetricRegistry, config: MetricsConfig): ScreenDriverMetrics {
        if (!config.enabled) return DISABLED
        val p = config.prefix
        return ScreenDriverMetrics(...)
    }
}
```

### JVM Instrumentation Sources
**Source:** `src/main/kotlin/com/anjo/service/MetricsCollector.kt` lines 18-28
**Apply to:** `HealthRoutes.kt` response assembly
```kotlin
val runtime = Runtime.getRuntime()
ManagementFactory.getRuntimeMXBean().uptime          // → HealthDetailResponse.uptime
runtime.totalMemory() - runtime.freeMemory()         // → HealthDetailResponse.memoryUsed
runtime.maxMemory()                                  // → HealthDetailResponse.memoryMax
```

### Serializable Model Convention
**Source:** `src/main/kotlin/com/anjo/model/MetricsModels.kt` lines 1-26
**Apply to:** `HealthDetailResponse.kt`, `ZoneStatus.kt`
All response models use `@Serializable` annotation. Import is `import kotlinx.serialization.Serializable`.

### Null-safe Counter Increment
**Source:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` line 128
**Apply to:** All `HardwareMetrics` counter increments in `ScreenDriverService` and `RetryPolicy`
```kotlin
metrics.inFlightCounter?.inc()   // existing pattern
hardwareMetrics.inFlightCounter?.inc()   // new — identical null-safe call
```

### DI Registration Order
**Source:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt` lines 33-53
**Apply to:** `HardwareMetrics` registration
Register `hardwareMetrics` immediately after `screenDriverMetrics` (line 34), before `screenDriverService` (line 45). This matches the project pattern where dependencies are declared before the services that consume them.

---

## No Analog Found

All files in scope have close analogs in the codebase.

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`
**Files scanned:** 13
**Pattern extraction date:** 2026-06-16
