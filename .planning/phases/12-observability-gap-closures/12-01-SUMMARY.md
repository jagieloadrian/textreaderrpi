---
phase: 12-observability-gap-closures
plan: "01"
subsystem: observability
tags: [metrics, hardware, retry, di, counters]
dependency_graph:
  requires: []
  provides: [HardwareMetrics, hardware-metrics-group, hardware-counter-instrumentation]
  affects: [MetricsCollector, ScreenDriverService, DependencyInjection, RetryPolicy]
tech_stack:
  added: []
  patterns: [dropwizard-counter-factory, DISABLED-sentinel, null-safe-counter-increment]
key_files:
  created:
    - src/main/kotlin/com/anjo/model/HardwareMetrics.kt
    - src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/RetryPolicy.kt
    - src/main/kotlin/com/anjo/service/MetricsCollector.kt
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
    - src/test/kotlin/com/anjo/routing/MetricsRoutesTest.kt
    - src/test/kotlin/com/anjo/ApplicationTest.kt
    - src/test/kotlin/com/anjo/service/RetryPolicyTest.kt
decisions:
  - "HardwareMetrics modeled line-for-line on ScreenDriverMetrics: data class + DISABLED sentinel + from(registry, config) factory"
  - "displayFailureCounter increments ONLY on final rethrow (after attempt >= maxAttempts branch), never per retry attempt"
  - "recoveryRetryCounter increments on the retry path (before delay), one increment per transient failure that recovers"
  - "hardwareMetrics param on retryWithBackoff defaults to null so all existing callers compile unchanged"
  - "hardwareMetrics param on ScreenDriverService defaults to HardwareMetrics.DISABLED for backward-compatible construction"
  - "hardwareMetrics registered in DI before screenDriverService and metricsCollector to satisfy forward-reference constraint"
  - "inFlightCounter instrumented only in renderImmediate (not runScheduledRender) to mirror ScreenDriverMetrics.inFlightCounter symmetry"
metrics:
  duration_minutes: 4
  tasks_completed: 2
  files_created: 2
  files_modified: 7
  completed_date: "2026-06-16"
---

# Phase 12 Plan 01: Hardware Metrics Group Summary

Hardware counter instrumentation wiring GET /metrics hardware group with four Dropwizard counters for display failures, recovery retries, in-flight slots, and skipped requests.

## What Was Built

Added a fourth observability dimension to the `/metrics` endpoint. Previously the endpoint reported only `runtime` and `api` groups. This plan closes OBS-02 by adding a `hardware` group tracking:

- `display.failures` — incremented on final exhaustion of `retryWithBackoff`
- `recovery.retries` — incremented on each intermediate retry attempt
- `display.inFlight` — incremented/decremented around `renderImmediate`
- `display.skipped` — incremented when SKIP_NEW conflict policy drops a request

`HardwareMetrics` follows the identical factory/DISABLED pattern as `ScreenDriverMetrics`. The `from(registry, config)` factory returns `DISABLED` (all nulls) when `config.enabled == false`, preventing any counter allocation in disabled-metrics environments. All counter increments use null-safe `?.inc()` / `?.dec()` calls.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Create HardwareMetrics + instrument retryWithBackoff | b140e0c | HardwareMetrics.kt, RetryPolicy.kt, HardwareMetricsTest.kt, RetryPolicyTest.kt |
| 2 | Wire into MetricsCollector, ScreenDriverService, DI | 283af97 | MetricsCollector.kt, ScreenDriverService.kt, DependencyInjection.kt, MetricsRoutesTest.kt, ApplicationTest.kt |

TDD RED commit: d5efa25 (failing HardwareMetricsTest before HardwareMetrics existed)

## Verification

- `./gradlew test` (full suite) exits 0, JaCoCo coverage gate passes
- `GET /metrics` groups array has 3 entries: `runtime`, `api`, `hardware`
- `hardware` group contains keys: `display.failures`, `recovery.retries`, `display.inFlight`, `display.skipped`
- `HardwareMetrics` resolves from DI (ApplicationTest smoke assertion)
- No Kotlin files modified in this plan contain `//`, `/*`, or `/**` comments

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all four hardware counters are live Dropwizard `Counter` instances registered in `MetricRegistry`. Values start at 0 and increment during actual hardware operations.

## Threat Flags

None — no new network endpoints or auth paths introduced. Hardware group metrics carry no PII. Existing `installMetricsRateLimiting(120)` on `/metrics` covers T-12-02 without change.

## Self-Check: PASSED

- `src/main/kotlin/com/anjo/model/HardwareMetrics.kt` exists
- `src/test/kotlin/com/anjo/service/HardwareMetricsTest.kt` exists
- Commits b140e0c and 283af97 present in git log
- Full test suite BUILD SUCCESSFUL
