# Phase 12: Observability Gap Closures - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-16
**Phase:** 12-observability-gap-closures
**Areas discussed:** /health/detail payload, Hardware metrics scope, OBS-03 already done?

---

## /health/detail payload

| Option | Description | Selected |
|--------|-------------|----------|
| API failure meter only | `ScreenDriverMetrics.failedMeter.count` — one number | |
| Per-zone error map | Map of zoneId → error string per zone | |
| Both: total + per-zone | Total failures + per-zone error strings | ✓ |

**User's choice:** Both: total + per-zone

---

| Option | Description | Selected |
|--------|-------------|----------|
| Standalone route only | `get("/health/detail")` in Routing.kt | |
| Inside KHealth | KHealth DSL (custom check blocks) | ✓ |

**Notes:** KHealth healthChecks{} only returns booleans; cannot produce the required custom JSON payload. Clarified and resolved: standalone `get("/health/detail")` route for the full payload PLUS a named `healthCheck("displayAvailable")` in KHealth so `/health` reflects display state.

---

| Option | Description | Selected |
|--------|-------------|----------|
| No rate limit | Same as /health — open for monitoring tools | |
| Same as /metrics | 120 req/min (metricsRateLimitPerMinute) | ✓ |

**User's choice:** Same rate limit as /metrics (120 req/min)

---

| Option | Description | Selected |
|--------|-------------|----------|
| New flat data class | `HealthDetailResponse(uptime, memoryUsed, memoryMax, displayStatus, totalFailures, zoneErrors)` | ✓ |
| Reuse MetricsResponse groups | Same group/metrics nesting as /metrics | |

**User's choice:** New flat data class (recommended)

---

## Hardware metrics scope

| Option | Description | Selected |
|--------|-------------|----------|
| Extend ScreenDriverMetrics | Add counters to existing class | |
| New HardwareMetrics class | Separate class with own DI entry | ✓ |

**User's choice:** New HardwareMetrics class

---

| Option | Description | Selected |
|--------|-------------|----------|
| Active display jobs count | inFlight per zone | |
| Mutex contention count | SKIP_NEW drop count | |
| Both: inFlight + skipped | inFlightCounter + skippedCounter | ✓ |

**User's choice:** Both: inFlight + skipped count

---

| Option | Description | Selected |
|--------|-------------|----------|
| Inside retryWithBackoff | Optional HardwareMetrics? param; increment on each catch and final rethrow | ✓ |
| Inside ScreenDriverService.renderImmediate | Catch after retryWithBackoff exhausts | |

**User's choice:** Inside retryWithBackoff (recommended)

---

## OBS-03 already done?

| Option | Description | Selected |
|--------|-------------|----------|
| Just verify it works | Test browser behavior; fix route ordering if SwaggerUI intercepts | ✓ |
| Also add HTML 500 page | Add explicit status(500) handler | |
| Add explicit GET catch-all | Register catch-all get("{...}") before SwaggerUI | |

**User's choice:** Just verify it works (recommended)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Reorder swaggerUI() after all routes | Minimal fix for SwaggerUI catch-all | ✓ |
| Add explicit GET catch-all before swaggerUI() | Explicit route before SwaggerUI | |

**User's choice:** Reorder swaggerUI() after all other routes if needed

---

## Claude's Discretion

- Metric key names in hardware group: follow `textreaderrpi.hardware.*` prefix to match existing `textreaderrpi.screenDriver.*` keys
- Model placement: `HealthDetailResponse` in `com.anjo.model`
- Route organization: separate `healthRoutes()` fun consistent with `metricsRoutes()`
- `HardwareMetrics.DISABLED` companion sentinel (follow `ScreenDriverMetrics.DISABLED` pattern)

## Deferred Ideas

None — discussion stayed within phase scope.
