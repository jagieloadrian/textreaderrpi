---
phase: 07-scheduler-schema-stabilisation
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 19
files_reviewed_list:
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/db/DatabaseFactory.kt
  - src/main/kotlin/com/anjo/db/ScheduleRepository.kt
  - src/main/kotlin/com/anjo/db/SchedulesTable.kt
  - src/main/kotlin/com/anjo/model/ConflictPolicy.kt
  - src/main/kotlin/com/anjo/model/Schedule.kt
  - src/main/kotlin/com/anjo/model/TextRequest.kt
  - src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt
  - src/main/kotlin/com/anjo/routing/TextRoutes.kt
  - src/main/kotlin/com/anjo/service/SchedulerService.kt
  - src/main/kotlin/com/anjo/service/ScreenDriver.kt
  - src/main/kotlin/com/anjo/validation/ScheduleValidators.kt
  - src/main/resources/db/migration/V1__initial_schema.sql
  - src/main/resources/db/migration/V2__add_scheduler_columns.sql
  - src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt
  - src/test/kotlin/com/anjo/routing/ScheduleRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt
  - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
  - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
findings:
  critical: 4
  warning: 5
  info: 2
  total: 11
status: issues_found
---

# Phase 07: Code Review Report

**Reviewed:** 2026-06-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 19
**Status:** issues_found

## Summary

This phase adds the four scheduler columns (`conflict_policy`, `fired_at`, `webhook_url`, `zone_id`) via a Flyway V2 migration, wires `ConflictPolicy` through the full stack from `TextRequest`/`Schedule` models through the routing layer and into `SchedulerService` and `ScreenDriverService`, and introduces `CRON` expression validation at the POST handler. Test coverage is solid for the happy path. However, four blockers were found: three serialisation-time enums are missing `@Serializable`, unguarded `Instant.parse` in the recurring scheduler coroutine crashes the coroutine silently on a malformed `expiresAt` value, the PATCH handler bypasses CRON validation entirely (allowing an invalid-CRON update to spin up a broken coroutine), and `displayScheduled` always returns `true` even when the display has already completed — masking SKIP_NEW policy effects for scheduled jobs. Five additional warnings cover logic gaps and a meaningful test reliability issue.

---

## Critical Issues

### CR-01: `Effect`, `ScheduleStatus`, and `TriggerType` enums are missing `@Serializable`

**File:** `src/main/kotlin/com/anjo/model/Schedule.kt:5-9`

**Issue:** `Schedule` is annotated `@Serializable` and contains fields of type `Effect`, `ScheduleStatus`, and `TriggerType`. None of these three enums carry `@Serializable`. The kotlinx.serialization compiler plugin requires that every type appearing as a property of a `@Serializable` class also be serializable. Without this, the plugin either emits a compile-time error (when all three enum types are used in a non-`@Contextual` position and no custom serializer is registered) or falls back to a runtime `SerializationException`. In practice this means every `GET /api/v1/schedule` and every `POST /api/v1/schedule` response will fail at runtime. The existing integration tests pass only if the test module registers a lenient serialiser or the compiler happens to synthesise enum support — neither is guaranteed across toolchain updates.

**Fix:**
```kotlin
// Schedule.kt — add @Serializable to each enum
import kotlinx.serialization.Serializable

@Serializable
enum class Effect { SCROLL, BLINK, REVERSE, FADE }

@Serializable
enum class ScheduleStatus { ACTIVE, PAUSED, EXPIRED, DONE, ERROR }

@Serializable
enum class TriggerType { ONESHOT, RECURRING, CRON }
```

---

### CR-02: Unguarded `Instant.parse(expiresAt)` crashes the recurring coroutine silently

**File:** `src/main/kotlin/com/anjo/service/SchedulerService.kt:122`

**Issue:** `Instant.parse(schedule.expiresAt)` is called inside the `launchRecurring` coroutine loop without a `try/catch`. If `expiresAt` is a non-null string that is not a valid ISO-8601 instant (e.g. a relative value or a date-only string like `"2026-12-31"`), `Instant.parse` throws `DateTimeParseException`. Because this is inside a coroutine launched on the `SupervisorJob` scope, the exception is swallowed by the supervisor — the coroutine dies with no log line at ERROR level, the schedule never transitions to DONE or ERROR in the database, and no recovery is attempted. `expiresAt` has no validation in `ScheduleValidators` and no validation in the route handler.

**Fix:**
```kotlin
// Option A: validate expiresAt in ScheduleValidators.validateSchedule()
val expiresAt = schedule.expiresAt
if (expiresAt != null) {
    try { Instant.parse(expiresAt) }
    catch (_: Exception) {
        return ValidationResult.Invalid("expiresAt must be ISO-8601 instant (e.g. 2026-12-31T23:59:00Z)")
    }
}

// Option B (belt-and-suspenders): wrap the call in SchedulerService.launchRecurring
val expiresAt = schedule.expiresAt
if (expiresAt != null) {
    val expiresInstant = try { Instant.parse(expiresAt) }
    catch (e: Exception) {
        log.error("Invalid expiresAt for schedule ${schedule.id}: $expiresAt", e)
        repository.updateStatus(schedule.id, "ERROR")
        break
    }
    if (Instant.now().isAfter(expiresInstant)) break
}
```

Both options should be applied: validate on the way in and handle gracefully on the way out.

---

### CR-03: PATCH handler performs no CRON validation — invalid expression schedules an immediately-broken coroutine

**File:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt:82-94`

**Issue:** The POST handler (lines 37-48) validates CRON expressions and returns 422 on failure. The PATCH handler calls `repository.update(id, body)` directly, then calls `schedulerService.schedule(updated)` without any CRON validation. A client that PATCHes `triggerValue` to an invalid cron string will receive 200 OK. The scheduler then calls `launchCron`, which catches the parse exception, logs it, and returns `null` — silently dropping the job from `activeJobs`. The schedule is now ACTIVE in the database but has no running coroutine and is never re-loaded by `tickLoop` (because it is already ACTIVE and `activeJobs` does not contain it — actually it is correctly filtered out because `tickLoop` checks `!activeJobs.containsKey(it.id)`, but since null was returned and `activeJobs` was never updated for the new id, the tickLoop _will_ attempt to relaunch every 60 s and fail again, spamming the log).

**Fix:**
```kotlin
patch {
    val id = call.parameters["id"]
        ?: return@patch call.respond(HttpStatusCode.BadRequest, "missing id")
    val body = call.receive<Schedule>()
    // Mirror the POST CRON validation for updates
    if (body.triggerType == TriggerType.CRON) {
        try {
            cronParser.parse(body.triggerValue)
        } catch (e: Exception) {
            return@patch call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
            )
        }
    }
    val updated = repository.update(id, body)
        ?: return@patch call.respond(HttpStatusCode.NotFound)
    if (updated.status.name == "ACTIVE") {
        schedulerService.cancel(id)
        schedulerService.schedule(updated)
    }
    log.info("Schedule updated: id=$id status=${updated.status}")
    call.respond(updated)
}
```

---

### CR-04: `displayScheduled` always returns `true` — SKIP_NEW policy is non-functional for the scheduler's `maxRuns` accounting

**File:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt:99`

**Issue:** `displayScheduled` returns `true` unconditionally at line 99, even on the SKIP_NEW fast-exit path (line 77-80 returns `false`) — but on the normal completion path the function exits via the `finally` block and falls through to `return true` at line 99. However, because the SKIP_NEW guard returns `false` at line 79, that path is correct. The real bug is the opposite: `displayScheduled` returns `true` even when the underlying `executeWithRecovery` throws (the exception is caught and logged at line 91, and then execution continues to `return true` at line 99). A display failure is reported as a success, so `runs++` in `launchRecurring` is always incremented regardless of whether the hardware actually rendered anything. `maxRuns` is therefore consumed on failures as well as successes, causing a schedule to exhaust its run count faster than intended.

Additionally, there is a SKIP_NEW check at line 77 that only reads `displayMutex.isLocked` — see WR-01 for the race-window concern, but the return value contract being broken is the blocker here.

**Fix:**
```kotlin
// In displayScheduled, propagate failure:
var displaySucceeded = false
try {
    displayMutex.withLock {
        executeWithRecovery(text, renderer)
        displaySucceeded = true
    }
} catch (_: CancellationException) {
    // intentional
} catch (e: Exception) {
    log.error("Scheduled display failed for schedule $scheduleId: ${e.message}", e)
} finally {
    if (currentScheduledId == scheduleId) {
        currentScheduledId = null
        currentDisplayJob = null
    }
    checkAndPerformPendingSwitch()
}
return displaySucceeded
```

---

## Warnings

### WR-01: TOCTOU race in SKIP_NEW check — `Mutex.isLocked` is not atomic with `withLock`

**File:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt:45,77`

**Issue:** Both `displayImmediate` and `displayScheduled` check `displayMutex.isLocked` and then, if false, proceed to `displayMutex.withLock { ... }`. Between the check and the `withLock` acquisition, another coroutine can lock the mutex. The result is that two callers, both observing `isLocked == false`, both enter `withLock` — the second one blocks rather than returning early with the SKIP_NEW fast path. This is a classic TOCTOU race. In the single-dispatcher test environment it is invisible, but on a multi-threaded dispatcher (e.g. `Dispatchers.Default`) with concurrent requests it will fire.

**Fix:** Use `Mutex.tryLock()` as an atomic test-and-acquire:
```kotlin
// SKIP_NEW path — try to lock without blocking; if already locked, drop
if (conflictPolicy == ConflictPolicy.SKIP_NEW) {
    if (!displayMutex.tryLock()) {
        log.info("SKIP_NEW: display busy, dropping request")
        return false
    }
    try {
        executeWithRecovery(text, renderer)
    } finally {
        displayMutex.unlock()
        checkAndPerformPendingSwitch()
    }
    return true
}
// INTERRUPT path — normal withLock
displayMutex.withLock { executeWithRecovery(text, renderer) }
```

---

### WR-02: `cancel()` writes DONE to DB unconditionally — spurious status update on unknown ids

**File:** `src/main/kotlin/com/anjo/service/SchedulerService.kt:68-75`

**Issue:** `cancel(id)` always fires a `scope.launch { repository.updateStatus(id, "DONE") }` regardless of whether `id` existed in `activeJobs`. The DELETE handler calls `schedulerService.cancel(id)` before checking whether the row exists (`if (!repository.delete(id))`). For an unknown id, the flow is: `cancel` is called → `activeJobs.remove` is a no-op → a fire-and-forget coroutine calls `updateStatus("nonexistent-id", "DONE")` → the UPDATE matches zero rows (silent no-op in SQL) but wastes a DB round trip. The `/cancel` endpoint has this same pattern and the test explicitly calls it with `"nonexistent-id"` and expects 204. While the current SQL behaviour makes this safe for correctness, `updateStatus` swallows exceptions via `catch (_: Exception) {}`, meaning a genuine DB error (connection failure) is silently discarded on every unknown-id cancel call.

**Fix:**
```kotlin
fun cancel(id: String) {
    val removed = activeJobs.remove(id)?.cancel()
    if (removed != null) {   // only write DB if there was an active job
        scope.launch {
            try {
                repository.updateStatus(id, "DONE")
            } catch (e: Exception) {
                log.warn("Failed to persist DONE for cancelled schedule $id: ${e.message}", e)
            }
        }
    }
}
```

---

### WR-03: `tickLoop` continues after DB failure on startup — loaded schedules may be partially registered

**File:** `src/main/kotlin/com/anjo/service/SchedulerService.kt:36-47`

**Issue:** In `start()`, when `repository.findAllActive()` or `schedule(it)` throws, the `catch (e: Exception)` block logs the error and execution falls through to `tickLoop()`. If the DB is unavailable on startup the initial schedule load fails silently and `tickLoop()` starts anyway. On the next tick (60 s later) `tickLoop` calls `findAllActive` again — which may also fail. Since `tickLoop` swallows `Exception` at line 88, this can spin forever without any operator-visible escalation beyond log lines at ERROR level. More critically, if only _some_ of the `schedule(it)` calls in the `forEach` succeed before an exception propagates (e.g. one schedule has a malformed triggerValue that was previously stored), the remaining schedules are never loaded on that startup.

**Fix:** The `forEach { schedule(it) }` loop itself does not throw — the `schedule()` function returns a `Job?` and logs internally. The risk is limited to `repository.findAllActive()` failing. The real fix is to ensure the tickLoop escalates after N consecutive failures rather than logging and retrying indefinitely:
```kotlin
private suspend fun tickLoop() {
    var consecutiveErrors = 0
    while (scope.isActive) {
        try {
            delay(60_000L)
            val active = repository.findAllActive()
                .sortedWith(compareByDescending<Schedule> { it.priority }.thenBy { it.createdAt ?: "" })
                .filter { !activeJobs.containsKey(it.id) }
            active.forEach { schedule(it) }
            consecutiveErrors = 0
        } catch (_: CancellationException) {
            break
        } catch (e: Exception) {
            consecutiveErrors++
            log.error("SchedulerService tick error ($consecutiveErrors consecutive): ${e.message}", e)
            // Optional: alert or stop after threshold
        }
    }
}
```

---

### WR-04: Flyway `baselineOnMigrate(true)` silently baselines a **fresh** schema to V1, skipping the V1 migration

**File:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt:26-31`

**Issue:** `baselineOnMigrate(true)` combined with `baselineVersion("1")` means: "if there is no `flyway_schema_history` table, create it and mark V1 as already applied." This is correct for existing Pi installs that already have the `schedules` table from a pre-Flyway era. However, on a **fresh** install where the database is completely empty, Flyway will also see no history, set the baseline to V1 (skipping the V1 migration), and then run V2. The V2 migration issues `ALTER TABLE schedules ADD COLUMN ...` but the `schedules` table does not exist yet because V1 was skipped. This causes the V2 migration to fail with a table-not-found error on first deploy to a clean DB. The subsequent `SchemaUtils.create(SchedulesTable)` will never execute.

**Fix:** Flyway's `baselineOnMigrate` is intended only for migration of databases that predate Flyway adoption. For clean environments it must not apply. The typical fix is to detect which case applies:
```kotlin
// Detect whether the schedules table already exists (pre-Flyway install)
// If it does, baseline before migrating; if it doesn't, run all migrations from V1.
val hasExistingTable = dataSource.connection.use { conn ->
    conn.metaData.getTables(null, null, "schedules", null).next()
}
Flyway.configure()
    .dataSource(dataSource)
    .baselineOnMigrate(hasExistingTable)
    .baselineVersion("1")
    .load()
    .migrate()
```
Alternatively, remove `baselineOnMigrate` and use a `R__repair` script, or rely on `IF NOT EXISTS` in V1 plus a conditional baseline step run only on Pi hardware.

---

### WR-05: `SchedulerServiceTest` mixes 3-arg and 4-arg `displayScheduled` verify calls — some assertions are vacuously true

**File:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt:52-54, 74, 93, 152, 174, 274`

**Issue:** `ScreenDriverService.displayScheduled` has the signature `(text, scheduleId, renderer, conflictPolicy)` — four parameters. The `beforeEach` mock setup at line 36 stubs it with `coEvery { mockScreen.displayScheduled(any(), any(), any(), any()) } returns true` (four matchers — correct). However, many `coVerify` calls throughout the test use only three matchers, e.g.:
```kotlin
coVerify(exactly = 1) { mockScreen.displayScheduled("hello", "s1", any()) }   // line 54
coVerify(exactly = 3) { mockScreen.displayScheduled("recurring", "s3", any()) } // line 93
```
MockK treats a 3-argument verify against a 4-parameter function as matching calls that were made with exactly three arguments, which is never the case. These verify calls will either fail to match (and the test passes by absence of invocation, not presence) or MockK silently ignores the arity mismatch. Either way, the assertions are not testing what the comment says. The tests for ONESHOT, RECURRING, CRON, cancel, and stop-before-fire are all affected. Only the newer tests in the ERROR/SKIP_NEW area use 4-argument matchers consistently.

**Fix:** Add the fourth `any()` matcher to every `coVerify` that checks `displayScheduled`:
```kotlin
coVerify(exactly = 1) { mockScreen.displayScheduled("hello", "s1", any(), any()) }
coVerify(exactly = 3) { mockScreen.displayScheduled("recurring", "s3", any(), any()) }
// etc.
```

---

## Info

### IN-01: `webhookUrl` is validated and persisted but never consumed — dead field in current release scope

**File:** `src/main/kotlin/com/anjo/model/Schedule.kt:25`, `src/main/kotlin/com/anjo/db/SchedulesTable.kt:18`, `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt:17-19`

**Issue:** `webhookUrl` is stored in the model, persisted to the database, and validated for URL format — but is never read by `SchedulerService.fire()` or any other service-layer code. This is acknowledged as a placeholder for a future phase (per V2 migration comment). The validator wastes CPU on regex matching for a feature that is entirely siloed. This is acceptable as forward-scaffolding but should be tracked.

**Fix:** No code change required now. Add a `// TODO(Phase-10): wire webhookUrl into fire()` comment in `SchedulerService.fire()` at line 155 so the dead field is visibly tracked at the call site.

---

### IN-02: `ScheduleValidators` comment on CRON says validation "moved to ScheduleRoutes" — architectural drift risk

**File:** `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt:22`

**Issue:** The comment `// CRON validation moved to ScheduleRoutes.kt` in the validator is the only indication that CRON validation is handled elsewhere. This split responsibility (validator skips CRON, route handler validates CRON, PATCH handler skips it entirely — see CR-03) is fragile. Any developer adding a new CRON-capable endpoint will follow the validator pattern and omit CRON validation.

**Fix:** Move CRON validation back into `ScheduleValidators.validateSchedule()` with the `cronParser` injected or instantiated there. The route handler should rely entirely on the validator plugin, keeping routing code free of business rule logic.

---

_Reviewed: 2026-06-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
