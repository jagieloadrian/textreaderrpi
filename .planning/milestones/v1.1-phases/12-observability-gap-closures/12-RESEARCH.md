# Phase 12: Observability Gap Closures - Research

**Researched:** 2026-06-16
**Domain:** Ktor observability — health endpoint, Dropwizard metrics extension, HTML error routing
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**GET /health/detail (OBS-01)**
- D-01: Standalone `get("/health/detail")` at root level in `Routing.kt` (not inside `/api/v1`). Rate-limited via `installMetricsRateLimiting` at 120 req/min.
- D-02: Response model is a new flat `@Serializable data class HealthDetailResponse` with fields: `uptime: Long` (ms), `memoryUsed: Long` (bytes), `memoryMax: Long` (bytes), `displayStatus: String` ("ONLINE"/"OFFLINE"), `totalFailures: Long` (from `ScreenDriverMetrics.failedMeter.count`), `zoneErrors: Map<String, String?>` (zoneId → error string or null).
- D-03: `healthCheck("displayAvailable") { zoneRegistry.listAll().any { it.status == "ONLINE" } }` added to KHealth `healthChecks {}` block in `Monitoring.kt`.
- D-04: `uptime` = `ManagementFactory.getRuntimeMXBean().uptime`, memory from `Runtime.getRuntime()` — same sources as `MetricsCollector.runtimeGroup()`.
- D-05: `displayStatus` = "ONLINE" if any zone is ONLINE, else "OFFLINE" — consistent with `ScreenDriverService.status()`.

**GET /metrics hardware group (OBS-02)**
- D-06: New `HardwareMetrics` class in `com.anjo.model.HardwareMetrics` — four Dropwizard `Counter`s: `displayFailureCounter`, `recoveryRetryCounter`, `inFlightCounter`, `skippedCounter`. Follows `ScreenDriverMetrics.from(registry, config)` factory pattern.
- D-07: `retryWithBackoff` gains `hardwareMetrics: HardwareMetrics? = null` param. On each catch (before delay): `hardwareMetrics?.recoveryRetryCounter?.inc()`. On final rethrow: `hardwareMetrics?.displayFailureCounter?.inc()`.
- D-08: `inFlightCounter` inc at start of `renderImmediate`/`runScheduledRender`, dec in existing finally block. `skippedCounter` inc in `ConflictPolicy.SKIP_NEW` drop path in `displayImmediate`.
- D-09: `MetricsCollector` receives `HardwareMetrics` as constructor param. New `hardwareGroup()` private fun reads `.count` on each counter, returns `MetricGroup(name = "hardware", ...)`. `collect()` adds it alongside "runtime" and "api".
- D-10: `HardwareMetrics` registered in `DependencyInjection.kt`, injected into `MetricsCollector` and `ScreenDriverService`. `retryWithBackoff` callers inside `ScreenDriverService` pass `hardwareMetrics`.

**HTML error pages (OBS-03)**
- D-11: `ErrorHandling.kt` already has `status(HttpStatusCode.NotFound)` + `prefersHtml()` → `ErrorPage(404, "Page not found")`. No new handler code needed.
- D-12: Verification task: confirm `GET /does-not-exist` in browser returns HTML 404. If SwaggerUI catch-all intercepts (returns JSON), fix = move `swaggerUI(path = "openapi")` to the last position in the `routing {}` block in `Routing.kt`.
- D-13: `prefersHtml()` logic (`!path.startsWith("/api") && accept.contains("text/html")`) is unchanged.

### Claude's Discretion
- Naming of metric keys in the hardware `MetricGroup` — follow `textreaderrpi.hardware.*` prefix to match existing `textreaderrpi.screenDriver.*` keys.
- `HealthDetailResponse` placement — `com.anjo.model` is the right home.
- Whether to add a `healthRoutes()` fun or inline in `configureRouting()` — a separate `healthRoutes()` fun is cleaner and consistent with `metricsRoutes()`.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OBS-01 | `GET /health/detail` returns uptime, memory, display status, error counts | D-01 through D-05 fully specify the endpoint; existing `MetricsCollector.runtimeGroup()` and `ZoneRegistry.listAll()` provide all data sources |
| OBS-02 | `GET /metrics` includes hardware group with display failures, recovery retries, resource utilisation | D-06 through D-10 specify `HardwareMetrics` class and wiring; `ScreenDriverMetrics` provides the factory pattern to follow |
| OBS-03 | Browser navigation to unknown path returns HTML 404 (not Swagger JSON) | `ErrorHandling.kt` already implements this; verification must confirm SwaggerUI ordering does not intercept first |
</phase_requirements>

---

## Summary

Phase 12 closes three audit gaps that were left out of scope in v1.0. All three are narrow, surgical changes: one new route with a new response model, one new Dropwizard counter class wired into the retry path, and one routing-order verification (with an optional single-line fix if SwaggerUI intercepts first).

The codebase already contains every building block needed. `HealthDetailResponse` (OBS-01) copies JVM instrumentation patterns directly from `MetricsCollector.runtimeGroup()`, reads zone status from `ZoneRegistry.listAll()`, and mirrors the `metricsRoutes()` routing structure. `HardwareMetrics` (OBS-02) is a line-for-line structural copy of `ScreenDriverMetrics` with different counter names; wiring into `retryWithBackoff` is additive (optional parameter with null default) so zero existing callers break. OBS-03 may already work — the `status(HttpStatusCode.NotFound) + prefersHtml()` handler in `ErrorHandling.kt` is fully implemented; the only risk is that `swaggerUI()` registers a catch-all before StatusPages fires, which is verified in the first execution task and fixed by reordering if needed.

**Primary recommendation:** Execute OBS-03 verification first (zero code change, high-value check). Then build OBS-01 and OBS-02 in parallel plans since they share no intermediate dependencies.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `/health/detail` endpoint | API / Backend | — | Reads JVM instrumentation and zone registry; not browser-facing data |
| `HealthDetailResponse` model | API / Backend | — | Serialised JSON; consumed by future `/status` UI page (Phase 13) |
| `HardwareMetrics` counters | API / Backend | — | Dropwizard registry counters maintained by service layer |
| Retry path instrumentation | API / Backend | — | `retryWithBackoff` lives in service layer, not routing |
| `MetricsCollector.hardwareGroup()` | API / Backend | — | Aggregator of counter state; same tier as existing groups |
| HTML 404 page routing | Frontend Server (SSR) | API / Backend | `prefersHtml()` discriminates browser vs API callers; HTML rendered server-side |
| SwaggerUI ordering | API / Backend | — | Route registration order in `Routing.kt` affects catch-all interception |

---

## Standard Stack

No new library dependencies. All required capabilities are covered by libraries already in `build.gradle.kts`.

### Core (already in project)
| Library | In Use | Purpose | Notes |
|---------|--------|---------|-------|
| `io.dropwizard.metrics:metrics-core` | Yes | Dropwizard `Counter`, `Meter`, `Timer`, `MetricRegistry` | `HardwareMetrics` uses `Counter` only |
| `dev.hayden:KHealth` | Yes | KHealth plugin; `healthChecks {}` block | D-03 adds one `check()` call |
| `kotlinx.serialization` | Yes | `@Serializable` on `HealthDetailResponse` | Follows existing model convention |
| `java.lang.management.ManagementFactory` | Yes | `getRuntimeMXBean().uptime` | Already in `MetricsCollector.runtimeGroup()` |
| `io.github.flaxoos:ktor-server-rate-limiting` | Yes | `installMetricsRateLimiting()` | Reused verbatim for `/health/detail` |

**Installation:** No `npm install` or `./gradlew` dependency changes needed. [VERIFIED: codebase grep]

---

## Package Legitimacy Audit

No new packages are introduced in this phase. All libraries are established project dependencies.

| Package | Registry | Verdict | Disposition |
|---------|----------|---------|-------------|
| metrics-core (Dropwizard) | Maven Central | OK | Already in use |
| KHealth | Maven Central | OK | Already in use |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious SUS:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Browser GET /does-not-exist
       │
       ▼
   Ktor routing {}
       │
       ├─ staticResources, webRoutes, scheduleUIRoutes... (registered first)
       ├─ metricsRoutes + healthRoutes  ← new healthRoutes() added here
       ├─ route("/api/v1") { ... }
       └─ swaggerUI()  ← must be LAST (OBS-03 fix if needed)
       │
       ▼
   StatusPages plugin (NotFound handler)
       │
       ├─ prefersHtml()? ── yes ──► ErrorPage(404).render() → text/html
       └─ no ──────────────────► ErrorResponse JSON

GET /health/detail
       │
       ▼
   healthRoutes()
       ├─ installMetricsRateLimiting(120)
       └─ get("/health/detail")
              │
              ├─ ManagementFactory.getRuntimeMXBean().uptime
              ├─ Runtime.getRuntime() (used/max memory)
              ├─ zoneRegistry.listAll() → any ONLINE? → displayStatus
              ├─ screenDriverMetrics.failedMeter?.count → totalFailures
              └─ zoneRegistry.listAll() → zoneId→error map → zoneErrors
              │
              ▼
         call.respond(HealthDetailResponse(...))

GET /metrics (extended)
       │
       ▼
   MetricsCollector.collect()
       ├─ runtimeGroup()
       ├─ apiGroup()
       └─ hardwareGroup()  ← new
              ├─ hardwareMetrics.displayFailureCounter.count
              ├─ hardwareMetrics.recoveryRetryCounter.count
              ├─ hardwareMetrics.inFlightCounter.count
              └─ hardwareMetrics.skippedCounter.count

retryWithBackoff() (instrumented)
       ├─ catch block → hardwareMetrics?.recoveryRetryCounter?.inc()
       └─ final rethrow → hardwareMetrics?.displayFailureCounter?.inc()

ScreenDriverService.renderImmediate() / runScheduledRender()
       ├─ start: hardwareMetrics?.inFlightCounter?.inc()
       └─ finally: hardwareMetrics?.inFlightCounter?.dec()

ScreenDriverService.displayImmediate() SKIP_NEW path
       └─ hardwareMetrics?.skippedCounter?.inc()
```

### Recommended Project Structure (additions only)

```
src/main/kotlin/com/anjo/
├─ model/
│   ├─ HardwareMetrics.kt       ← new (OBS-02)
│   └─ HealthDetailResponse.kt  ← new (OBS-01)
├─ routing/
│   └─ HealthRoutes.kt          ← new (OBS-01)
├─ service/
│   └─ MetricsCollector.kt      ← modified (OBS-02): add HardwareMetrics param + hardwareGroup()
│   └─ RetryPolicy.kt           ← modified (OBS-02): add HardwareMetrics? param
│   └─ ScreenDriverService.kt   ← modified (OBS-02): pass hardwareMetrics to retryWithBackoff; inc skippedCounter
├─ di/
│   ├─ DependencyInjection.kt   ← modified (OBS-01, OBS-02): register HardwareMetrics; inject into MetricsCollector + ScreenDriverService
│   └─ Monitoring.kt            ← modified (OBS-01): add healthCheck("displayAvailable")
└─ routing/
    └─ Routing.kt               ← modified (OBS-01): call healthRoutes(); (OBS-03): reorder swaggerUI() if needed
```

### Pattern 1: Route Extension Function (metricsRoutes() model)

**What:** Each feature area defines a `fun Route.xyzRoutes(...)` extension, registered in `configureRouting()`.
**When to use:** Any new top-level route group.

```kotlin
// Source: src/main/kotlin/com/anjo/routing/MetricsRoutes.kt [VERIFIED: codebase read]
fun Route.metricsRoutes(metricsCollector: MetricsCollector, metricsRateLimitPerMinute: Int) {
    route("/metrics") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            call.respond(metricsCollector.collect())
        }
    }
}
```

`healthRoutes()` follows the exact same shape:
```kotlin
fun Route.healthRoutes(
    zoneRegistry: ZoneRegistry,
    screenDriverMetrics: ScreenDriverMetrics,
    metricsRateLimitPerMinute: Int
) {
    route("/health/detail") {
        installMetricsRateLimiting(metricsRateLimitPerMinute)
        get {
            call.respond(buildHealthDetailResponse(zoneRegistry, screenDriverMetrics))
        }
    }
}
```

### Pattern 2: Dropwizard Counter Class (ScreenDriverMetrics model)

**What:** Counters/meters are encapsulated in a data class with a `DISABLED` sentinel and a `from(registry, config)` factory.
**When to use:** Any new instrumentation class.

```kotlin
// Source: src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt [VERIFIED: codebase read]
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
                ...
            )
        }
    }
}
```

`HardwareMetrics` follows this structure with `Counter` fields and prefix `textreaderrpi.hardware.*`.

### Pattern 3: MetricGroup in collect()

**What:** Each instrumentation area returns a `MetricGroup`; `collect()` lists them all.
**When to use:** Extending the `/metrics` response.

```kotlin
// Source: src/main/kotlin/com/anjo/service/MetricsCollector.kt [VERIFIED: codebase read]
fun collect(): MetricsResponse = MetricsResponse(
    timestamp = Instant.now().toString(),
    groups = listOf(runtimeGroup(), apiGroup())  // add hardwareGroup() here
)
```

### Anti-Patterns to Avoid

- **Inline route in configureRouting():** Every previous feature area uses a dedicated `xyzRoutes()` fun. Do not inline the health/detail route directly in `configureRouting()`.
- **Putting HardwareMetrics logic in routes:** Counters are incremented by service/retry layer only; routes only read aggregated state via `MetricsCollector`.
- **Non-null counter fields without a DISABLED sentinel:** If `config.enabled = false`, callers must still compile cleanly. Nullable fields + null-safe `?.inc()` calls is the established pattern.
- **Adding validation logic to route handlers:** Per project coding rules — any validation stays in validator layer, never route handlers.
- **Comments in Kotlin files:** Per project coding rules — no `//`, `/* */`, or KDoc in any Kotlin file.

---

## Critical Implementation Detail: zoneErrors Map

**Finding:** `ZoneStatus` (returned by `ZoneDriver.status()`) does NOT currently have an `error: String?` field. [VERIFIED: codebase read — `ZoneStatus.kt` has `id`, `type`, `status`, `ip`, `lastSeenAt` only]

The `LocalZoneDriver.status()` converts `DisplayStatus` (which has `error: String?` from `AbstractDisplayDriver.lastError`) into `ZoneStatus` and drops the error field. `NetworkZoneDriver.status()` also returns a `ZoneStatus` with no error.

**Consequence for D-02:** To populate `zoneErrors: Map<String, String?>`, the implementation has two clean options:

1. **Add `error: String?` to `ZoneStatus`** and propagate it from `LocalZoneDriver.status()` via `driverStatus.error`. This is consistent with the model and makes error information available to any consumer of `ZoneRegistry.listAll()`. Zero breaking changes — existing consumers ignore unknown fields.

2. **Derive errors from status string only** — map each zone where `status == "OFFLINE"` to a synthetic error string like `"OFFLINE"`, null otherwise. Simpler, requires no model change, but loses `lastError` detail from `AbstractDisplayDriver`.

Option 1 is architecturally superior and aligns with the D-02 wording (`DisplayStatus.error` per zone). The planner should default to Option 1 (add `error: String?` to `ZoneStatus`) as part of the OBS-01 plan.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JVM uptime measurement | Custom timer/counter | `ManagementFactory.getRuntimeMXBean().uptime` | Already in `MetricsCollector.runtimeGroup()` — exact same call |
| Memory measurement | Custom calculation | `Runtime.getRuntime().totalMemory() - freeMemory()` | Same pattern, already tested |
| Rate limiting for new route | Custom throttle | `installMetricsRateLimiting()` | Identical to `/metrics`; reuse without modification |
| HTML error page template | New Ktor HTML DSL block | `ErrorPage(code, message).render()` | Already exists with `BaseLayout`; OBS-03 needs no new template |
| Counter increment thread-safety | `AtomicLong` manually | Dropwizard `Counter.inc()` / `dec()` | Dropwizard counters are thread-safe by design |

---

## Common Pitfalls

### Pitfall 1: SwaggerUI catch-all intercepts before StatusPages

**What goes wrong:** `swaggerUI()` in Ktor 3.x registers a wildcard route for static Swagger assets. If registered before the status page handler resolves, a browser GET to `/does-not-exist` returns a Swagger JSON error instead of the HTML 404 page.

**Why it happens:** Ktor route matching order matters for catch-alls. Swagger registers `/{...}` paths that can swallow non-existent routes if placed early in the routing block.

**How to avoid:** In `Routing.kt`, `swaggerUI()` must be the LAST call inside `routing {}`. The current position in the codebase is already last (after `route("/api/v1")`), which means OBS-03 may already work.

**Warning signs:** OBS-03 verification returns Swagger JSON (content-type `application/json`) for `/does-not-exist` with `Accept: text/html`. Test this first before writing any code.

**Current state:** [VERIFIED: codebase read] `swaggerUI(path = "openapi")` is already the last call in `routing {}` in `Routing.kt`. OBS-03 may require zero code changes — only verification.

### Pitfall 2: retryWithBackoff counter placement — retry vs. failure

**What goes wrong:** Incrementing `displayFailureCounter` on every catch block instead of only on final rethrow. This inflates failure counts since transient failures that eventually recover would be counted.

**Why it happens:** The catch block fires on every failed attempt; the rethrow path only fires when `attempt >= config.maxAttempts`.

**How to avoid:** Per D-07 — `recoveryRetryCounter?.inc()` in each catch (before delay), `displayFailureCounter?.inc()` only on final rethrow (after the `throw e` that exits the loop).

### Pitfall 3: DI registration order for HardwareMetrics

**What goes wrong:** If `HardwareMetrics` is instantiated after `ScreenDriverService` in `DependencyInjection.kt`, the constructor call will fail at compile time or produce a forward-reference error.

**How to avoid:** Register `hardwareMetrics` before `screenDriverService` (and `metricsCollector`) in the `configureDI()` body — same pattern as `historyRepository` being registered before `historyService`.

### Pitfall 4: MetricsRoutesTest hardcodes 2 groups

**What goes wrong:** `MetricsRoutesTest` asserts `json["groups"]!!.jsonArray shouldHaveSize 2` and `groupNames shouldBe listOf("runtime", "api")`. Adding a third "hardware" group breaks these assertions.

**How to avoid:** Update `MetricsRoutesTest` as part of the OBS-02 plan to assert `shouldHaveSize 3` and include "hardware" in the group names list. The test for `HealthRoutesTest` also currently asserts that `GET /health/detail` returns 404 — that assertion must flip to 200 once OBS-01 is implemented.

**Warning signs:** CI fails on `MetricsRoutesTest` and `HealthRoutesTest` even after successful implementation.

### Pitfall 5: HealthDetailResponse not serialisable

**What goes wrong:** `Map<String, String?>` is serialisable by `kotlinx.serialization` only when both key and value types are serialisable. Nullable `String?` is fine — `null` serialises as JSON `null`. The risk is forgetting `@Serializable` on the data class or using a non-serialisable container type.

**How to avoid:** Annotate `HealthDetailResponse` with `@Serializable`. Use `Map<String, String?>` (standard library type — serialisable by default). Follow the same structure as existing models in `MetricsModels.kt`.

### Pitfall 6: KHealth healthChecks vs. readyChecks confusion

**What goes wrong:** Adding the `displayAvailable` check to `readyChecks {}` instead of `healthChecks {}`. The readiness check is for Kubernetes-style orchestration and already has a `displayReady` check. The liveness health check (`/health`) should also reflect display state per D-03.

**How to avoid:** D-03 explicitly says `healthChecks {}` block in `Monitoring.kt`. `readyChecks` is the separate block that already has `displayReady`. Place the new check in `healthChecks {}` alongside `check("appAlive")`.

---

## Code Examples

### HealthDetailResponse model

```kotlin
// Pattern: src/main/kotlin/com/anjo/model/MetricsModels.kt [VERIFIED: codebase read]
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

### HardwareMetrics class

```kotlin
// Pattern: src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt [VERIFIED: codebase read]
data class HardwareMetrics(
    val displayFailureCounter: Counter? = null,
    val recoveryRetryCounter: Counter? = null,
    val inFlightCounter: Counter? = null,
    val skippedCounter: Counter? = null,
) {
    companion object {
        val DISABLED = HardwareMetrics()

        fun from(registry: MetricRegistry, config: MetricsConfig): HardwareMetrics {
            if (!config.enabled) return DISABLED
            val p = config.prefix
            return HardwareMetrics(
                displayFailureCounter = registry.counter("$p.hardware.display.failures"),
                recoveryRetryCounter  = registry.counter("$p.hardware.recovery.retries"),
                inFlightCounter       = registry.counter("$p.hardware.display.inFlight"),
                skippedCounter        = registry.counter("$p.hardware.display.skipped"),
            )
        }
    }
}
```

### retryWithBackoff with instrumentation

```kotlin
// Pattern: src/main/kotlin/com/anjo/service/RetryPolicy.kt [VERIFIED: codebase read]
suspend fun <T> retryWithBackoff(
    config: RetryConfig = RetryConfig(),
    hardwareMetrics: HardwareMetrics? = null,
    block: suspend () -> T
): T {
    var attempt = 0
    var delayMs = config.initialDelayMs
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            if (attempt >= config.maxAttempts) {
                log.error("All ${config.maxAttempts} retry attempts exhausted: ${e.message}")
                hardwareMetrics?.displayFailureCounter?.inc()
                throw e
            }
            log.warn("Attempt $attempt/${config.maxAttempts} failed, retrying in ${delayMs}ms: ${e.message}")
            hardwareMetrics?.recoveryRetryCounter?.inc()
            delay(delayMs.milliseconds)
            delayMs = min((delayMs * config.factor).toLong(), config.maxDelayMs)
        }
    }
}
```

### hardwareGroup() in MetricsCollector

```kotlin
// Pattern: src/main/kotlin/com/anjo/service/MetricsCollector.kt [VERIFIED: codebase read]
private fun hardwareGroup(): MetricGroup = MetricGroup(
    name = "hardware",
    metrics = listOf(
        MetricEntry(key = "display.failures",   type = "counter", count = hardwareMetrics.displayFailureCounter?.count ?: 0L),
        MetricEntry(key = "recovery.retries",   type = "counter", count = hardwareMetrics.recoveryRetryCounter?.count  ?: 0L),
        MetricEntry(key = "display.inFlight",   type = "counter", count = hardwareMetrics.inFlightCounter?.count       ?: 0L),
        MetricEntry(key = "display.skipped",    type = "counter", count = hardwareMetrics.skippedCounter?.count        ?: 0L),
    )
)
```

### KHealth healthChecks addition

```kotlin
// Pattern: src/main/kotlin/com/anjo/di/Monitoring.kt [VERIFIED: codebase read]
healthChecks {
    check("appAlive") { true }
    check("displayAvailable") { zoneRegistry.listAll().any { it.status == "ONLINE" } }
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Single `/health` endpoint | `/health` + `/health/ready` + (new) `/health/detail` | Detail endpoint fills audit gap without modifying existing endpoints |
| 2-group metrics response | 3-group response (runtime, api, hardware) | Metrics breakout enables hardware failure alerting |

**Deprecated / not applicable:**
- Ktor `MicrometerMetrics` plugin: project uses `DropwizardMetrics` exclusively; do not introduce Micrometer.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotest 5.x, FunSpec `should` convention |
| Config file | `src/test/kotlin/com/anjo/ProjectConfig.kt` |
| Quick run command | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest" --tests "com.anjo.routing.MetricsRoutesTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OBS-01 | `GET /health/detail` returns 200 with HealthDetailResponse fields | unit (route) | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest"` | Exists — currently asserts 404; test must flip to assert 200 + fields |
| OBS-01 | `GET /health` includes `displayAvailable` check | unit (route) | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest"` | Exists — add new test case |
| OBS-01 | `/health/detail` is rate-limited at 120 req/min | unit (routing) | `./gradlew test --tests "com.anjo.routing.RateLimitRoutesTest"` | May extend existing |
| OBS-02 | `GET /metrics` groups includes "hardware" | unit (route) | `./gradlew test --tests "com.anjo.routing.MetricsRoutesTest"` | Exists — update `shouldHaveSize 2` → 3; add "hardware" to names |
| OBS-02 | `HardwareMetrics.from()` returns DISABLED when metrics disabled | unit (model) | `./gradlew test --tests "com.anjo.service.HardwareMetricsTest"` | Does NOT exist — Wave 0 gap |
| OBS-02 | `retryWithBackoff` increments recoveryRetryCounter per retry, displayFailureCounter on final failure | unit (service) | `./gradlew test --tests "com.anjo.service.RetryPolicyTest"` | Exists — add new test cases |
| OBS-03 | `GET /does-not-exist` with `Accept: text/html` returns HTML 404 | smoke (route) | `./gradlew test --tests "com.anjo.ApplicationTest"` | Exists — one test already asserts 404 status; enhance to check content-type and body |
| DI | `HardwareMetrics` resolves from DI container | integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | Exists — add `HardwareMetrics` to DI smoke assertions |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.anjo.routing.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green (JaCoCo ≥ 70%) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt` — covers OBS-02 `HardwareMetrics.from()` factory (DISABLED sentinel, counter registration)
- [ ] Update `MetricsRoutesTest` — change `shouldHaveSize 2` → 3; add "hardware" to `groupNames` assertions
- [ ] Update `HealthRoutesTest` — flip 404 assertion for `/health/detail` to 200; add field assertions; add `displayAvailable` in `/health` body assertion

---

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | no | No request body; `/health/detail` is GET-only |
| V4 Access Control | no | Trusted home network; no auth scope (per project out-of-scope) |
| V3 Session Management | no | Stateless endpoint |
| V2 Authentication | no | Out of scope (v1.1 explicitly excludes auth) |

The only security-adjacent concern is rate limiting — OBS-01 uses `installMetricsRateLimiting(120)` which prevents metric-scraping DoS, consistent with the existing `/metrics` endpoint.

---

## Environment Availability

This phase is purely code/config changes. No new external dependencies. The existing JVM environment and Dropwizard `MetricRegistry` provide all required capabilities.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Dropwizard MetricRegistry | OBS-02 HardwareMetrics | Yes | Already in project | — |
| ManagementFactory (JVM stdlib) | OBS-01 uptime | Yes | JDK 25 | — |
| KHealth plugin | OBS-01 healthCheck | Yes | Already in use | — |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | SwaggerUI ordering in current `Routing.kt` (swaggerUI last) means OBS-03 already works without code changes | Common Pitfalls, OBS-03 | If SwaggerUI version registers a global catch-all regardless of position, a Routing.kt reorder may not fix it; alternative is to add an explicit NotFound route before swaggerUI |
| A2 | `ZoneStatus` needs `error: String?` added to support `zoneErrors` map in `HealthDetailResponse` (Option 1 from Critical Implementation Detail section) | Critical Implementation Detail | If planner chooses Option 2 (derive from status string), `ZoneStatus` model stays unchanged; the data is less precise but the plan is simpler |

---

## Open Questions (RESOLVED)

1. **ZoneStatus.error field: add or derive?** — RESOLVED
   - What we know: `ZoneStatus` has no `error` field; `DisplayStatus` (in `AbstractDisplayDriver`) does have `lastError: String?`
   - What's unclear: D-02 says "zoneId → `DisplayStatus.error` per zone" — does this mean add the field to `ZoneStatus` and propagate, or derive a synthetic error string from the OFFLINE status?
   - Recommendation: Add `error: String?` to `ZoneStatus` (Option 1). Propagate it in `LocalZoneDriver.status()` from `driverStatus.error`. `NetworkZoneDriver` can set it to `"OFFLINE"` when `!online`. This is additive, zero breaking changes, and aligns with D-02 wording.
   - **Decision:** Option 1 implemented in Plan 12-02 Task 1.

2. **`HealthDetailResponse` data source for `totalFailures`** — RESOLVED
   - What we know: D-02 says `totalFailures` comes from `ScreenDriverMetrics.failedMeter.count`
   - What's unclear: `ScreenDriverMetrics` is injected into `ScreenDriverService`, not into `Routing.kt`. The health route needs access to it. It should be injected from DI (same as `metricsCollector`).
   - Recommendation: Pass `ScreenDriverMetrics` (or `ScreenDriverService`) as a parameter to `healthRoutes()`. Since `screenDriverService` is already in DI, the cleaner approach is to expose a `failedCount(): Long` fun on `ScreenDriverService` that delegates to `metrics.failedMeter?.count ?: 0L`.
   - **Decision:** `screenDriverMetrics` passed directly to `healthRoutes()` via DI in Plan 12-02 Task 2.

---

## Sources

### Primary (HIGH confidence)
- `src/main/kotlin/com/anjo/service/MetricsCollector.kt` — existing groups/collect pattern; `runtimeGroup()` data sources [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/model/ScreenDriverMetrics.kt` — `HardwareMetrics` factory pattern [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/model/MetricsModels.kt` — `MetricGroup`, `MetricEntry` serialisation pattern [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/di/Monitoring.kt` — KHealth `healthChecks {}` block [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/di/ErrorHandling.kt` — `prefersHtml()` + HTML 404 handler [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/routing/Routing.kt` — `swaggerUI()` ordering, route registration [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` — `metricsRoutes()` pattern [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/service/RetryPolicy.kt` — `retryWithBackoff` signature [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — DI wiring pattern [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/model/ZoneStatus.kt` — field inventory [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` — `status()` to `ZoneStatus` conversion [VERIFIED: codebase read]
- `src/main/kotlin/com/anjo/di/RateLimiting.kt` — `installMetricsRateLimiting()` implementation [VERIFIED: codebase read]
- `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` — existing 404 assertion to update [VERIFIED: codebase read]
- `src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt` — group count assertions to update [VERIFIED: codebase read]

### Secondary (MEDIUM confidence)
- CONTEXT.md decisions D-01 through D-13 — user-locked implementation details

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are verified project dependencies
- Architecture: HIGH — all patterns read directly from canonical source files
- Pitfalls: HIGH — derived from direct code analysis (ZoneStatus missing error field, test assertions that will break, DI ordering)

**Research date:** 2026-06-16
**Valid until:** 2026-07-16 (stable Ktor 3.5.0 / KHealth stack)
