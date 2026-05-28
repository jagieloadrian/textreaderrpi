---
plan: 05-02
status: complete
commit: e26d0c5
date: 2026-05-28
---

# Summary: Plan 05-02 — Mutex Guard + Config-Driven Metrics + Comment Removal

## What Was Built

### D5-04: Replace AtomicBoolean + ResourceTracker with Mutex
- `ScreenDriverService.busy: AtomicBoolean` → `displayMutex: Mutex`
- `readInput()` wraps display execution in `displayMutex.withLock { ... }`
- `queueDisplaySwitch()` checks `displayMutex.isLocked` instead of `busy.get()`
- `ResourceTracker.kt` deleted (slot-counting concurrency guard, replaced by Mutex)
- `ResourceTrackerTest.kt` deleted (class no longer exists)
- `MetricsCollector` constructor `resourceTracker: ResourceTracker` param removed
- `hardwareGroup()` removed from `MetricsCollector.collect()` (depended on ResourceTracker)
- `DependencyInjection.kt` no longer constructs or provides `ResourceTracker`
- `MetricsRoutesTest` updated: groups count 3→2; expected names `["runtime", "api"]`

### D5-05: MetricRegistry instrumentation config-driven
- New `MetricsConfig(enabled: Boolean, prefix: String)` data class
- Added to `ApplicationConfig` field list; loaded by `ConfigLoader` from `metrics.*`
- `application.yaml` extended with `metrics: { enabled: true, prefix: "textreaderrpi" }`
- `ScreenDriverService` gains `metricsConfig: MetricsConfig` constructor param
- Metric registrations: `acceptedMeter?`, `failedMeter?`, `inFlightCounter?`, `executionTimer?`
  — all nullable, only registered when `metricsConfig.enabled = true`
- Metric names: `"${metricsConfig.prefix}.screenDriver.readInput.{accepted|failed|inFlight|execution}"`
- `ScreenDriverResourceTest` updated to use prefixed metric names

### D5-06: Remove all comments from service and driver files
- `Max7219Matrix.kt`: class KDoc (9 lines), 3 method KDocs, 5 inline `// comments` removed
- `LcdDisplay.kt`: class KDoc (12 lines), 8 method KDocs, 6 inline `// comments` removed
- `OledDisplay.kt`, `ScreenDriver.kt`, `MetricsCollector.kt`, `RetryPolicy.kt`: already clean

## Key Decisions
- `hardwareGroup()` dropped rather than replaced — ResourceTracker metrics were low-value and `/api/metrics` still returns runtime + api groups with all registered Dropwizard meters/timers/counters
- Metric nullability chosen over a no-op registry pattern — simpler, avoids wrapper indirection

## Self-Check: PASSED
- `./gradlew test` → BUILD SUCCESSFUL
- `ResourceTracker.kt` does NOT exist
- `ScreenDriver.kt` contains `Mutex()` and `withLock`
- `application.yaml` contains `metrics:` block with `enabled:` and `prefix:`
- No `//` or `/** */` in `Max7219Matrix.kt` or `LcdDisplay.kt`

