---
phase: 12-observability-gap-closures
reviewed: 2026-06-17T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/main/kotlin/com/anjo/di/ErrorHandling.kt
  - src/main/kotlin/com/anjo/di/Monitoring.kt
  - src/main/kotlin/com/anjo/model/HardwareMetrics.kt
  - src/main/kotlin/com/anjo/model/HealthDetailResponse.kt
  - src/main/kotlin/com/anjo/model/ZoneStatus.kt
  - src/main/kotlin/com/anjo/routing/HealthRoutes.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/service/MetricsCollector.kt
  - src/main/kotlin/com/anjo/service/RetryPolicy.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt
  - src/test/kotlin/com/anjo/ApplicationTest.kt
  - src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt
  - src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt
  - src/test/kotlin/com/anjo/service/RetryPolicyTest.kt
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
fixed_at: 2026-06-18T00:00:00Z
---

# Phase 12: Code Review Report

**Reviewed:** 2026-06-17T00:00:00Z
**Depth:** standard
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Phase 12 introduces the `HealthDetailResponse` DTO, `ZoneStatus.error` field, `healthRoutes()`, `MetricsCollector`, hardware metrics counters via `HardwareMetrics`, retry telemetry in `RetryPolicy`, and supporting plumbing in DI / Monitoring / Routing. The feature set is broadly correct, but three blockers stand out: the `renderer` parameter passed through the entire scheduled-display call chain is silently discarded inside `executeWithRecovery` (which hardcodes `Effect.SCROLL`), creating a silent correctness regression; `NetworkZoneDriver.send()` blocks a coroutine thread with `runBlocking` inside what is called from an IO-dispatched coroutine scope, creating a potential thread-pool exhaustion path; and the `totalFailures` field in `HealthDetailResponse` is sourced from `ScreenDriverMetrics.failedMeter` (a rate meter counting route-level failures) rather than `HardwareMetrics.displayFailureCounter` (the hardware failure counter), so the field reports a semantically wrong value.

---

## Critical Issues

### CR-01: `renderer` parameter accepted but silently discarded — all display calls use Effect.SCROLL unconditionally

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:196-204`

**Issue:** `executeWithRecovery` accepts an `EffectRenderer` parameter but never uses it. Instead it hard-codes `Effect.SCROLL` when calling `zoneRegistry.route()`. This means every call to `displayImmediate` with a non-SCROLL effect, and every call to `displayScheduled` with a custom renderer, silently ignores the effect and forces scroll. The renderer object is created, passed through three call-chain levels (`renderImmediate` → `executeWithRecovery`; `runScheduledRender` → `executeWithRecovery`), and then thrown away. This is an existing regression that phase 12 does not introduce, but `MetricsCollector` and `HealthRoutes` now surface `totalFailures` data that will mask whether the wrong effect is silently applied. The renderer parameter should either be used or the call chain should be corrected to route the effect through `ZoneRegistry.route`.

**Fix:**
```kotlin
private suspend fun executeWithRecovery(
    input: String,
    effect: Effect,
    zoneId: String?
) {
    withContext(ioDispatcher) {
        retryWithBackoff(retryConfig, hardwareMetrics) {
            if (zoneId != null) {
                zoneRegistry.route(zoneId, input, effect)
            }
        }
    }
}
```
Remove the `renderer: EffectRenderer` parameter from `executeWithRecovery`, `renderImmediate`, and `runScheduledRender`. Pass `effect: Effect` directly. Remove the now-unused `effectFactory` field (or use it correctly at the zone driver level).

---

### CR-02: `runBlocking` inside `NetworkZoneDriver.send()` blocks an IO coroutine thread

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:56-67`

**Issue:** `send()` is a plain (non-suspend) function that wraps `WebSocketSession.send()` — a suspend function — using `runBlocking`. This is called from `ZoneRegistry.broadcast()` which itself runs inside `scope.async { ... }` on `Dispatchers.IO`, and from `ZoneRegistry.route()` called inside `retryWithBackoff` on `ioDispatcher`. `runBlocking` on a coroutine dispatcher thread will block that thread completely for the duration of the WebSocket send. Under load (multiple zones, concurrent requests) this exhausts the `Dispatchers.IO` thread pool, causing coroutine starvation. Because the `ZoneDriver` interface defines `send()` as a blocking function, the correct fix is to make `send()` a suspend function, requiring an interface change.

**Fix:**
```kotlin
interface ZoneDriver {
    suspend fun send(text: String, effect: Effect): Boolean
    fun status(): ZoneStatus
    fun stop() {}
}
```
Then in `NetworkZoneDriver`:
```kotlin
override suspend fun send(text: String, effect: Effect): Boolean {
    val s = session ?: return false
    return try {
        s.send(Frame.Text("""{"text":${Json.encodeToString(text)},"effect":"${effect.name}"}"""))
        true
    } catch (e: Exception) {
        log.warn("NetworkZoneDriver send failed to $ip: ${e.message}")
        false
    }
}
```
`LocalZoneDriver.send()` and all callers in `ZoneRegistry` must also be made suspend accordingly.

---

### CR-03: `totalFailures` in `HealthDetailResponse` reports meter count from wrong metric source

**File:** `src/main/kotlin/com/anjo/routing/HealthRoutes.kt:29`

**Issue:** `totalFailures` is sourced from `screenDriverMetrics.failedMeter?.count`, which is a Dropwizard `Meter` tracking route-level `displayImmediate` / `displayScheduled` failures as marked in `renderImmediate` line 141 (`metrics.failedMeter?.mark()`). This is the service-layer retry-exhaustion event count. `HardwareMetrics.displayFailureCounter` is the canonical hardware failure count incremented in `RetryPolicy.retryWithBackoff()` at line 27 when all attempts are exhausted. The two sources count the same logical event but `ScreenDriverMetrics.failedMeter` is a rate meter (also tracks mean rate, 1m/5m rates), while the DTO only exposes `.count`. More importantly, `HealthDetailResponse` exists to report hardware observability, making `HardwareMetrics.displayFailureCounter` the semantically correct field. If `HardwareMetrics` is disabled (metrics disabled in config), `displayFailureCounter` is null and should fall back to `0L`, which the existing null-safe pattern handles.

**Fix:**
```kotlin
fun Route.healthRoutes(
    zoneRegistry: ZoneRegistry,
    hardwareMetrics: HardwareMetrics,
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
                    totalFailures = hardwareMetrics.displayFailureCounter?.count ?: 0L,
                    zoneErrors = zones.associate { it.id to it.error },
                )
            )
        }
    }
}
```
Update `Routing.kt` to pass `hardwareMetrics` instead of `screenDriverMetrics` to `healthRoutes()`. Update the `HealthRoutesTest` to verify `totalFailures` starts at 0 and increments after a hardware failure rather than after a route-level failure.

---

## Warnings

### WR-01: `NetworkZoneDriver.status()` sets `error` to the string `"OFFLINE"` instead of a meaningful message

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:69-75`

**Issue:** When the zone is offline, `error` is set to the literal string `"OFFLINE"` — the same value as `status`. `ZoneStatus.error` is meant to carry a diagnostic reason (e.g., the last exception message, a connection failure description). `HealthDetailResponse.zoneErrors` exposes this field directly to callers. Returning `"OFFLINE"` for `error` when `status` is already `"OFFLINE"` duplicates data and gives operators nothing actionable. The last disconnection reason from the `catch` block in `startConnect()` is available but never stored.

**Fix:** Store the last exception message in a `@Volatile private var lastError: String? = null` field, set it in the `catch` block of `startConnect()`, and return it from `status()`:
```kotlin
@Volatile private var lastError: String? = null

// in startConnect() catch block:
} catch (e: Exception) {
    lastError = e.message
    log.warn("WS connection lost to $ip: ${e.message}")
}

// in status():
error = if (online) null else (lastError ?: "OFFLINE")
```

---

### WR-02: `displayAvailable` health check in `Monitoring.kt` calls `listAll()` which re-invokes `driver.status()` on every zone on every health poll — including hardware I/O for local zones

**File:** `src/main/kotlin/com/anjo/di/Monitoring.kt:36`

**Issue:** `zoneRegistry.listAll().any { it.status == "ONLINE" }` is executed on every `/health` request. For `LocalZoneDriver`, `driver.status()` calls `AbstractDisplayDriver.status()` which reads live hardware state. If hardware is slow or stuck, this will block the health check coroutine. The KHealth plugin uses health check results to determine HTTP status code — a slow or hanging hardware read will delay health responses, which infrastructure probes (load balancers, Kubernetes liveness) interpret as the service being unavailable, triggering unnecessary restarts. `ScreenDriverService.status()` already provides a cached `anyOnline` check using the same `listAll()` call and is directly injectable.

**Fix:** Use `screenDriverService.status().hardwareAvailable` for the `displayAvailable` check, consistent with how `displayReady` is implemented:
```kotlin
healthChecks {
    check("appAlive") { true }
    check("displayAvailable") { screenDriverService.status().hardwareAvailable }
}
```

---

### WR-03: `generateSequence` cause-chain traversal in `ErrorHandling.kt` terminates too early for cycles and misses the root cause

**File:** `src/main/kotlin/com/anjo/di/ErrorHandling.kt:70-72`

**Issue:** The deserialization-failure detection code builds a sequence starting from `cause.cause` and then calls `.plus(cause)`, which appends the top-level throwable at the end. However `generateSequence(cause.cause) { it.cause }` will loop infinitely if the exception cause chain contains a cycle (Java's `Throwable` does not prevent `cause == this`). While unusual, a misbehaving serialization library or reflection-based proxy could produce such a chain, causing the handler to hang the HTTP request until it OOMs or is cancelled. Additionally, `.plus(cause)` at the end means `cause` is checked last, after its own cause chain — this is redundant since the cause chain starting from `cause.cause` already feeds up to `cause`'s causes, but `cause` itself is not in `generateSequence` unless added at the end. This is functionally correct but fragile.

**Fix:** Bound the traversal depth to prevent potential infinite loops:
```kotlin
val isDeserializationFailure = generateSequence(cause as Throwable?) { it.cause }
    .take(20)
    .any { it is SerializationException || it is IllegalArgumentException }
```
Starting the sequence from `cause` (not `cause.cause`) includes the top-level throwable without needing `.plus()`, and `take(20)` guards against pathological chains.

---

### WR-04: `ScreenDriverService.queueDisplaySwitch()` always returns `false` — callers treat this as a real failure

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:222`

**Issue:** `queueDisplaySwitch(displayType: String): Boolean = false` is a permanent stub. `DisplayRoutes.kt` (line 35-37) calls it and when it returns `false`, logs a warning `"Driver switch rejected — queueDisplaySwitch returned false for type: ..."`. Because the function always returns `false`, this warning is emitted on every display-switch request, polluting logs and making the endpoint functionally broken. The `currentDisplayType()` function (line 217-220) returns `statuses.firstOrNull()?.type ?: "UNKNOWN"` which is also incomplete (returns only the first zone type in an arbitrary iteration order).

**Fix:** If the feature is intentionally deferred, the function should not be part of the public interface in a shipping class. Either remove the route that calls it, return an explicit `HTTP 501 Not Implemented` from `DisplayRoutes`, or remove the stub and the log warning that will fire on every call.

---

## Info

### IN-01: `MetricsCollector.apiGroup()` double-counts hardware counters already emitted in `hardwareGroup()`

**File:** `src/main/kotlin/com/anjo/service/MetricsCollector.kt:40-42`

**Issue:** `metricRegistry.counters.forEach { ... }` in `apiGroup()` iterates all counters registered with the `MetricRegistry`, which includes the four hardware counters registered by `HardwareMetrics.from()` (e.g., `textreaderrpi.hardware.display.failures`). These same counters are also emitted explicitly in `hardwareGroup()`. Callers reading the `/metrics` response will see the hardware counters twice — once under the `"api"` group and once under the `"hardware"` group. This is a data quality issue for any monitoring system that ingests both groups.

**Fix:** Filter the hardware counter prefix from `apiGroup()`:
```kotlin
metricRegistry.counters.forEach { (name, counter) ->
    if (!name.contains(".hardware.")) {
        entries.add(MetricEntry(key = name, type = "counter", count = counter.count))
    }
}
```
Or register hardware counters in a separate registry instead of the shared `metricRegistry`.

---

### IN-02: Tautological status assertions in `HealthRoutesTest` provide no real coverage

**File:** `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt:23, 33, 58`

**Issue:** The pattern `response.status shouldBe response.status.also { assert(it in validStatuses) }` asserts that `response.status` equals itself (always true) while performing a side-effect assertion inside `.also`. The `shouldBe` call here is a no-op for test coverage; only the `assert` inside `.also` does any real checking, and that assertion message on failure will be unhelpful. This pattern appears three times. A test regression (e.g., getting HTTP 400) would still pass the `shouldBe` assertion and only fail on the weaker `assert`.

**Fix:**
```kotlin
response.status shouldBeIn setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
```

---

### IN-03: Duplicate assertion in `ApplicationTest` "should serve static assets" test

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:57-58`

**Issue:** Lines 57 and 58 are identical: `response.status shouldBe HttpStatusCode.OK` is asserted twice in the same test. The second assertion is a copy-paste artifact that adds no coverage.

**Fix:** Remove the duplicate line 58.

---

_Reviewed: 2026-06-17T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
