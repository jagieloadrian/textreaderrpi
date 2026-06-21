---
phase: 08-refactor-dead-code-analysis
plan: "01"
subsystem: test
tags: [di, smoke-test, testing, ktor-di]
dependency_graph:
  requires: []
  provides: [DI-smoke-test-baseline]
  affects: [src/test/kotlin/com/anjo/ApplicationTest.kt]
tech_stack:
  added: []
  patterns: [ktor-di-getblocking-resolution, testApplication-startup-trigger]
key_files:
  created: []
  modified:
    - src/test/kotlin/com/anjo/ApplicationTest.kt
decisions:
  - "Added client.get('/health') before DI resolution to trigger testApplication startup — without it, configureDI() has not yet executed and all getBlocking() calls throw MissingDependencyException"
  - "Used CoroutineDispatcher (not CloseableCoroutineDispatcher) for Dispatchers.IO binding key — confirmed correct per A3"
  - "Imported getBlocking explicitly from io.ktor.server.plugins.di — required as it is a top-level extension function not in scope by default"
metrics:
  duration: "24 minutes"
  completed: "2026-06-15"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 1
requirements_addressed: [REF-03]
---

# Phase 08 Plan 01: DI Binding-Resolution Smoke Test Summary

**One-liner:** DI smoke test resolves all 11 configureDI() bindings via getBlocking<T>(DependencyKey<T>()) inside testApplication, failing fast on any missing provider.

## What Was Built

Added the test `should resolve all configureDI bindings without error` to `src/test/kotlin/com/anjo/ApplicationTest.kt`. The test:

- Runs inside `testApplication { application { module() } }` with real H2 in-memory DB (D-03)
- Calls `client.get("/health")` first to trigger application startup and ensure `configureDI()` has executed
- Resolves all 11 bindings via `application.dependencies.getBlocking<T>(DependencyKey<T>())`
- Asserts each with `shouldNotBeNull {}` only — no method calls on resolved instances (D-01)
- Throws `MissingDependencyException` and fails fast if any provider is missing (D-02)

**Bindings covered (all 11):**
1. `ApplicationConfig`
2. `ApiConfig`
3. `DisplayConfig`
4. `CoroutineDispatcher` (`Dispatchers.IO`)
5. `MetricRegistry`
6. `DisplaySelectionService`
7. `ScreenDriverService`
8. `MetricsCollector`
9. `ScheduleRepository`
10. `EffectRendererFactory`
11. `SchedulerService`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added startup trigger call before accessing application.dependencies**
- **Found during:** Task 1 — first test run
- **Issue:** `testApplication { application { module() } }` only registers the module; `configureDI()` does not execute until the first HTTP interaction triggers application startup. Calling `application.dependencies.getBlocking<...>()` before any HTTP call results in `MissingDependencyException` for every binding.
- **Fix:** Added `client.get("/health")` as a startup trigger before the `val deps = application.dependencies` line. This matches what all existing tests in the file already do (they each make an HTTP call before the application block completes).
- **Files modified:** `src/test/kotlin/com/anjo/ApplicationTest.kt`
- **Commit:** 1ca5e99

**2. [Rule 1 - Bug] Added explicit import for getBlocking extension function**
- **Found during:** Task 1 — first compile
- **Issue:** `getBlocking` is a top-level extension function in `io.ktor.server.plugins.di` and not automatically in scope; importing just `DependencyKey` and `dependencies` was insufficient.
- **Fix:** Added `import io.ktor.server.plugins.di.getBlocking`.
- **Files modified:** `src/test/kotlin/com/anjo/ApplicationTest.kt`
- **Commit:** 1ca5e99

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Added `client.get("/health")` before DI resolution | testApplication defers module execution until first HTTP interaction; without this, configureDI() has not run when getBlocking() is called |
| Used `CoroutineDispatcher` as binding key for `Dispatchers.IO` | Confirmed correct per assumption A3 in RESEARCH.md; Ktor DI infers the declared type `CoroutineDispatcher`, not the runtime `CloseableCoroutineDispatcher` |
| No fallback to `CloseableCoroutineDispatcher` needed | `getBlocking<CoroutineDispatcher>()` resolved successfully on first attempt |

## Test Results

```
ApplicationTest > should start application context and serve health endpoint PASSED
ApplicationTest > should expose API routes after context startup PASSED
ApplicationTest > should serve static assets after context startup PASSED
ApplicationTest > should return 404 for unknown routes PASSED
ApplicationTest > should include application name in health response PASSED
ApplicationTest > should resolve all configureDI bindings without error PASSED

6 tests, 0 failures, 0 errors
```

## Known Stubs

None — this plan adds only a test. No production stubs introduced.

## Threat Flags

None — test-only addition, no new network endpoints or auth paths.

## Self-Check: PASSED

- [x] `src/test/kotlin/com/anjo/ApplicationTest.kt` exists and contains `should resolve all configureDI bindings without error`
- [x] `getBlocking` appears 12 times (11 calls + 1 import), `DependencyKey` appears 12 times (11 calls + 1 import)
- [x] No `.start(`, `.displayImmediate(`, `.currentDriver(` calls inside the new test
- [x] Commit `1ca5e99` exists: `feat(08-01): add DI binding-resolution smoke test to ApplicationTest`
- [x] `./gradlew test --tests "com.anjo.ApplicationTest"` exits 0
