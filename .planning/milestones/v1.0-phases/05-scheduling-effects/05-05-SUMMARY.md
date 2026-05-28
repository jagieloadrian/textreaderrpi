---
phase: 5
plan: "05-05"
subsystem: scheduling
tags: [scheduler, coroutines, conflict-policy, preemption, cron]
requires: [05-04]
provides: [SchedulerService, displayImmediate, displayScheduled, SchedulerService lifecycle hooks]
affects: [service/ScreenDriver.kt, di/DependencyInjection.kt]
tech-stack:
  added: [cron-utils CronParser/ExecutionTime, SupervisorJob scope, ConcurrentHashMap job tracking]
  patterns: [coroutine supervisor job, preemption via Job.cancel(), Ktor ApplicationStarted/Stopping lifecycle]
key-files:
  created:
    - src/main/kotlin/com/anjo/service/SchedulerService.kt
  modified:
    - src/main/kotlin/com/anjo/service/ScreenDriver.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
key-decisions:
  - readInput() now delegates to displayImmediate() - preserves existing API while adding preemption
  - CancellationException in displayScheduled is swallowed (not re-thrown) since scheduler handles re-queue
  - SchedulerService lifecycle tied to ApplicationStarted/Stopping via monitor.subscribe in configureDI
  - Used containsKey() explicitly instead of 'in' operator on ConcurrentHashMap to avoid Kotlin warning KT-18053
requirements-completed: [REQ-SCHED-01, REQ-CONFLICT-01]
duration: "20 min"
completed: "2026-05-28"
---

# Phase 5 Plan 05-05: SchedulerService Coroutine Engine + Conflict Policy Summary

Coroutine-based scheduler with one-shot/recurring/cron support, ad-hoc preemption conflict policy, and Ktor lifecycle integration.

**Duration:** ~20 min | **Tasks:** 4 | **Files:** 3

## What Was Built

- **`SchedulerService.kt`** — coroutine engine with `CoroutineScope(Dispatchers.Default + SupervisorJob())`, `ConcurrentHashMap<String, Job>` job tracking, and three launch strategies:
  - `launchOneShot`: `delay(targetMs - now)` then `fire(schedule)`
  - `launchRecurring`: interval loop with `expiresAt` and `maxRuns` guards
  - `launchCron`: cron-utils `ExecutionTime.forCron(...).nextExecution(...)` for next delay
- **Preemption hook** in `ScreenDriver.kt`:
  - `displayImmediate()` — cancels `currentDisplayJob`, clears `currentScheduledId`, then acquires mutex and displays
  - `displayScheduled()` — registers `currentDisplayJob = currentCoroutineContext().job`, then displays
  - `readInput()` now delegates to `displayImmediate()` (backward compatible)
- **DI wiring** in `DependencyInjection.kt`:
  - `SchedulerService` instantiated and bound in `dependencies { }`
  - `monitor.subscribe(ApplicationStarted) { schedulerService.start() }`
  - `monitor.subscribe(ApplicationStopping) { schedulerService.stop() }`

## Deviations from Plan

**[Rule 1] containsKey() instead of `in` operator for ConcurrentHashMap**
Found during: Task 05-05-03 | Issue: Kotlin compiler warning/error KT-18053 — `in` on ConcurrentHashMap calls containsValue, not containsKey | Fix: Used `.containsKey(it.id)` explicitly | Impact: None.

**Total deviations:** 1 auto-fixed. **Impact:** None — 58/58 tests pass.

## Acceptance Criteria Verification

| Criterion | Result |
|-----------|--------|
| `SchedulerService.kt` exists with `CoroutineScope(Dispatchers.Default + SupervisorJob())` | ✅ PASS |
| Contains `activeJobs = ConcurrentHashMap` | ✅ PASS |
| Contains `fun start()`, `fun stop()`, `fun schedule(schedule: Schedule)` | ✅ PASS |
| Contains `CronParser`, `launchRecurring`, `launchCron` | ✅ PASS |
| `ScreenDriverService` contains `currentScheduledId`, `displayImmediate(`, `currentDisplayJob?.cancel()` | ✅ PASS |
| ApplicationStarted/Stopping subscriptions present | ✅ PASS (in configureDI) |
| `./gradlew compileKotlin` exits 0 | ✅ PASS |
| `./gradlew test` exits 0 | ✅ PASS — 58/58 tests, SchedulerService stopping logged |

## Self-Check: PASSED

