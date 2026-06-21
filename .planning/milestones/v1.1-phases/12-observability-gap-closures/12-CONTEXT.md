# Phase 12: Observability Gap Closures - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Three v1.0 audit gaps are closed:
1. `GET /health/detail` — new standalone endpoint returning a flat JSON payload with uptime, memory, display status, total failure count, and per-zone error strings. Also adds a `healthCheck("displayAvailable")` named check inside the KHealth plugin so `/health` reflects display state.
2. `GET /metrics` hardware group — a new `hardwareGroup()` in `MetricsCollector` backed by a new `HardwareMetrics` class; counters wired inside `retryWithBackoff`.
3. HTML 404/500 error pages — `ErrorHandling.kt` already handles this via `status(NotFound)` + `prefersHtml()` + `ErrorPage`. OBS-03 requires verification that SwaggerUI's catch-all doesn't intercept first; fix if needed by reordering `swaggerUI()` to the end of the routing block.

No new features, no schema changes, no new UI pages.

</domain>

<decisions>
## Implementation Decisions

### GET /health/detail (OBS-01)
- **D-01:** `GET /health/detail` is a standalone `get("/health/detail")` route registered in `Routing.kt` at root level (not inside `/api/v1`). It is rate-limited at the same rate as `/metrics` (`metricsRateLimitPerMinute` = 120 req/min) via `installMetricsRateLimiting`.
- **D-02:** Response model is a new flat data class `HealthDetailResponse` with fields: `uptime: Long` (ms), `memoryUsed: Long` (bytes), `memoryMax: Long` (bytes), `displayStatus: String` ("ONLINE"/"OFFLINE"), `totalFailures: Long` (from `ScreenDriverMetrics.failedMeter.count`), `zoneErrors: Map<String, String?>` (zoneId → `DisplayStatus.error` per zone, null if no error).
- **D-03:** Additionally, a `healthCheck("displayAvailable") { zoneRegistry.listAll().any { it.status == "ONLINE" } }` check is added inside the KHealth `healthChecks {}` block in `Monitoring.kt`, so `/health` also reflects display state.
- **D-04:** `uptime` and memory values are read from `ManagementFactory.getRuntimeMXBean().uptime` and `Runtime.getRuntime()` — same sources already used in `MetricsCollector.runtimeGroup()`. No new JVM instrumentation.
- **D-05:** `displayStatus` is "ONLINE" if `zoneRegistry.listAll().any { it.status == "ONLINE" }`, otherwise "OFFLINE" — consistent with `ScreenDriverService.status()`.

### GET /metrics hardware group (OBS-02)
- **D-06:** Create a new `HardwareMetrics` class in `src/main/kotlin/com/anjo/model/HardwareMetrics.kt`. It holds four Dropwizard counters: `displayFailureCounter`, `recoveryRetryCounter`, `inFlightCounter`, `skippedCounter` (SKIP_NEW drops). Follows the same `from(registry, config)` factory pattern as `ScreenDriverMetrics`.
- **D-07:** `retryWithBackoff` gains an optional `hardwareMetrics: HardwareMetrics? = null` parameter. On each catch block (before delay): `hardwareMetrics?.recoveryRetryCounter?.inc()`. On final rethrow: `hardwareMetrics?.displayFailureCounter?.inc()`. All existing callers compile unchanged (default null).
- **D-08:** `inFlightCounter` is incremented at the start of each `renderImmediate` / `runScheduledRender` call and decremented on completion (in the existing finally block). `skippedCounter` is incremented when `ConflictPolicy.SKIP_NEW` drops a request (already a distinct code path in `displayImmediate`).
- **D-09:** `MetricsCollector` receives `HardwareMetrics` as a constructor parameter. A new `hardwareGroup()` private fun reads `.displayFailureCounter.count`, `.recoveryRetryCounter.count`, `.inFlightCounter.count`, `.skippedCounter.count` and returns them as a `MetricGroup(name = "hardware", ...)`. `collect()` adds it to the groups list.
- **D-10:** `HardwareMetrics` is registered in `DependencyInjection.kt` and injected into both `MetricsCollector` and `ScreenDriverService`. `retryWithBackoff` callers inside `ScreenDriverService` pass `hardwareMetrics`.

### HTML error pages (OBS-03)
- **D-11:** `ErrorHandling.kt` already has `status(HttpStatusCode.NotFound)` with `prefersHtml()` → `ErrorPage(404, "Page not found")`, and `exception<Throwable>` with `prefersHtml()` → `ErrorPage(500, ...)`. No new handler code is needed.
- **D-12:** Verification task: confirm that `GET /does-not-exist` in a browser returns the HTML 404 page. If SwaggerUI catch-all intercepts first (returns JSON), the fix is to move `swaggerUI(path = "openapi")` to the last position in the `routing {}` block in `Routing.kt`.
- **D-13:** `prefersHtml()` logic (`!path.startsWith("/api") && accept.contains("text/html")`) is unchanged — API paths keep JSON errors, browser paths get HTML.

### Claude's Discretion
- Naming of metric keys in the hardware `MetricGroup` (follow `textreaderrpi.hardware.*` prefix to match existing `textreaderrpi.screenDriver.*` keys)
- Whether `HealthDetailResponse` is placed in `com.anjo.model` or `com.anjo.routing` — model is the right home
- Whether to add a `healthRoutes()` fun for the detail route or inline it in `configureRouting()` — a separate `healthRoutes()` fun is cleaner and consistent with `metricsRoutes()`

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing observability infrastructure
- `src/main/kotlin/com/anjo/service/MetricsCollector.kt` — existing groups/collect pattern; add `hardwareGroup()` here
- `src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt` — pattern for HardwareMetrics factory (`from(registry, config)`)
- `src/main/kotlin/com/anjo/model/MetricsModels.kt` — `MetricsResponse`, `MetricGroup`, `MetricEntry` models
- `src/main/kotlin/com/anjo/di/Monitoring.kt` — KHealth install block; add `healthCheck("displayAvailable")` here
- `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` — `metricsRoutes()` pattern to follow for `healthRoutes()`

### Error handling (OBS-03)
- `src/main/kotlin/com/anjo/di/ErrorHandling.kt` — already has HTML 404/500 via `prefersHtml()`; verify SwaggerUI ordering
- `src/main/kotlin/com/anjo/web/templates/ErrorPage.kt` — existing HTML error page template
- `src/main/kotlin/com/anjo/routing/Routing.kt` — `swaggerUI()` registration; may need reordering

### Hardware retry path
- `src/main/kotlin/com/anjo/service/RetryPolicy.kt` — `retryWithBackoff()` function; add `HardwareMetrics?` param here
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — caller of `retryWithBackoff`; increment `inFlightCounter` / `skippedCounter`

### DI wiring
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — register `HardwareMetrics`; inject into `MetricsCollector` and `ScreenDriverService`

### Rate limiting
- `src/main/kotlin/com/anjo/di/` — `installMetricsRateLimiting()` extension; apply same function to `/health/detail` route

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ManagementFactory.getRuntimeMXBean().uptime` + `Runtime.getRuntime()` — already in `MetricsCollector.runtimeGroup()`; copy pattern for `/health/detail`
- `MetricRegistry.counter(name)` — already used in `ScreenDriverMetrics.from()`; same call for `HardwareMetrics.from()`
- `ErrorPage(code, message).render()` — already exists and uses `BaseLayout`; OBS-03 requires no new template work
- `prefersHtml()` — already implemented in `ErrorHandling.kt`; no changes needed
- `installMetricsRateLimiting()` — already used in `metricsRoutes()`; reuse for `/health/detail`

### Established Patterns
- `MetricGroup(name, metrics)` grouping — add `"hardware"` group alongside `"runtime"` and `"api"`
- `ScreenDriverMetrics.DISABLED` sentinel — `HardwareMetrics` should follow the same `DISABLED` companion pattern for when `config.enabled = false`
- `retryWithBackoff` optional params — adding `HardwareMetrics? = null` matches the existing `RetryConfig = RetryConfig()` default pattern

### Integration Points
- `DependencyInjection.kt` must register `HardwareMetrics` and update `MetricsCollector` and `ScreenDriverService` constructor calls
- `Routing.kt` registers the new `healthRoutes()` call and may need `swaggerUI()` reordered
- `Monitoring.kt` `install(KHealth)` block gets the new `healthChecks { check("displayAvailable") { ... } }`

</code_context>

<specifics>
## Specific Ideas

- OBS-03 may already be fully implemented — execution plan should verify browser behavior first before writing any code for it
- `zoneErrors` map in `HealthDetailResponse` should use `null` for zones with no error (not empty string), consistent with `DisplayStatus.error: String?`

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 12-Observability Gap Closures*
*Context gathered: 2026-06-16*
