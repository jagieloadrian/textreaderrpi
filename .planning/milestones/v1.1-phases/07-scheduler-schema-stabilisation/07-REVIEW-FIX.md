---
phase: 07-scheduler-schema-stabilisation
fixed_at: 2026-06-15T00:00:00Z
review_path: .planning/phases/07-scheduler-schema-stabilisation/07-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 9
skipped: 0
status: all_fixed
---

# Phase 07: Code Review Fix Report

**Fixed at:** 2026-06-15T00:00:00Z
**Source review:** .planning/phases/07-scheduler-schema-stabilisation/07-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (4 Critical, 5 Warning; Info excluded by fix_scope)
- Fixed: 9
- Skipped: 0

## Fixed Issues

### CR-01: `Effect`, `ScheduleStatus`, and `TriggerType` enums missing `@Serializable`

**Files modified:** `src/main/kotlin/com/anjo/model/Schedule.kt`
**Commit:** ffe87c4
**Applied fix:** Added `@Serializable` annotation to `Effect`, `ScheduleStatus`, and `TriggerType` enum classes. All three are used as properties of the `@Serializable Schedule` data class; without this, kotlinx.serialization fails at runtime on every schedule API response.

---

### CR-02: Unguarded `Instant.parse(expiresAt)` crashes the recurring coroutine silently

**Files modified:** `src/main/kotlin/com/anjo/service/SchedulerService.kt`, `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt`
**Commit:** 70419a3
**Applied fix:** Applied both recommended options. Option A: added `expiresAt` ISO-8601 validation in `ScheduleValidators.validateSchedule()` — an invalid value returns `ValidationResult.Invalid` before the schedule reaches the service layer. Option B: wrapped `Instant.parse(expiresAt)` in `launchRecurring` with a try/catch that logs at ERROR level, calls `repository.updateStatus(id, "ERROR")`, and breaks the loop — preventing the coroutine from silently dying with no status update.

---

### CR-03: PATCH handler performs no CRON validation

**Files modified:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt`
**Commit:** 6ecad14
**Applied fix:** Added CRON validation block to the `patch` handler, before the `repository.update()` call. Mirrors the POST handler pattern: parses `body.triggerValue` with `cronParser`; on failure, responds with `422 UnprocessableEntity` and the `VAL_CRON` error code. This prevents an invalid CRON update from spinning up a broken coroutine that spams logs on every tickLoop tick.

---

### CR-04: `displayScheduled` always returns `true` — failure consumes `maxRuns`

**Files modified:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt`
**Commit:** 5604d19
**Applied fix:** Introduced a `displaySucceeded` boolean flag initialized to `false`. Set to `true` only after `executeWithRecovery` completes successfully inside the mutex lock. Returned instead of the hardcoded `true`. Display failures (caught by the `catch (e: Exception)` branch) now correctly return `false`, so `launchRecurring` does not increment `runs++` on hardware failures.
**Note:** This finding involves logic correctness — requires human verification to confirm the `displaySucceeded` flag placement inside the `withLock` block is correct for both the INTERRUPT and (post WR-01) SKIP_NEW code paths.

---

### WR-01: TOCTOU race in SKIP_NEW check — `Mutex.isLocked` is not atomic with `withLock`

**Files modified:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt`
**Commit:** e00fb85
**Applied fix:** Replaced the `isLocked` check + `withLock` pattern with `Mutex.tryLock()` for the SKIP_NEW path in both `displayImmediate` and `displayScheduled`. `tryLock()` atomically test-and-acquires: if it returns `false` the mutex was held and we return `false` immediately. If it returns `true` we own the lock and proceed, releasing via explicit `displayMutex.unlock()` in the `finally` block. The INTERRUPT path retains `withLock` unchanged.

---

### WR-02: `cancel()` writes DONE to DB unconditionally — spurious update on unknown ids

**Files modified:** `src/main/kotlin/com/anjo/service/SchedulerService.kt`
**Commit:** 296832c
**Applied fix:** Captured the result of `activeJobs.remove(id)` into `removed` (using `.also { it.cancel() }` to cancel the job in a single expression). The `scope.launch { updateStatus(id, "DONE") }` block is now conditional on `removed != null`. The silent `catch (_: Exception) {}` is replaced with `log.warn(...)` so genuine DB errors during cancel are visible in logs.

---

### WR-03: `tickLoop` continues after DB failure with no escalation

**Files modified:** `src/main/kotlin/com/anjo/service/SchedulerService.kt`
**Commit:** 629df30
**Applied fix:** Added a `consecutiveErrors` counter to `tickLoop`. It increments on each `Exception` catch and resets to 0 on a successful tick. The error log message now includes the count (e.g., "SchedulerService tick error (3 consecutive)"), giving operators a clear signal when the DB is persistently unavailable rather than just repeated identical log lines.

---

### WR-04: `baselineOnMigrate(true)` silently baselines fresh schema to V1, skipping V1 migration

**Files modified:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt`
**Commit:** 092a39d
**Applied fix:** Before configuring Flyway, check whether the `schedules` table already exists using JDBC metadata (`conn.metaData.getTables(...).next()`). Pass the boolean result as the `baselineOnMigrate` value. On a fresh database the table does not exist so `baselineOnMigrate(false)` is used and all migrations run from V1. On a legacy Pi install with the existing table, `baselineOnMigrate(true)` baselines V1 and runs V2 as before.

---

### WR-05: `SchedulerServiceTest` uses 3-arg `coVerify` for a 4-parameter function

**Files modified:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt`
**Commit:** 068127b
**Applied fix:** Added the missing fourth `any()` matcher to all 9 affected `coVerify` calls that checked `displayScheduled`. The function signature is `(text, scheduleId, renderer, conflictPolicy)` — 4 parameters. MockK 3-arg verifications against a 4-param function never matched actual invocations, making assertions vacuously pass. All verify calls now use 4 matchers, consistent with the `beforeEach` stub which already used `coEvery { mockScreen.displayScheduled(any(), any(), any(), any()) }`.

---

_Fixed: 2026-06-15T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
