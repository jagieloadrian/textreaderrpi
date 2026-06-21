# Phase 7: Scheduler Schema Stabilisation - Pattern Map

**Mapped:** 2026-06-14
**Files analyzed:** 13 (9 modified, 2 new Kotlin, 2 new SQL)
**Analogs found:** 13 / 13

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V1__initial_schema.sql` | migration | batch | `src/main/kotlin/com/anjo/db/SchedulesTable.kt` (schema source) | role-match |
| `src/main/resources/db/migration/V2__add_scheduler_columns.sql` | migration | batch | `V1__initial_schema.sql` (sibling) | exact |
| `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` | config | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/db/SchedulesTable.kt` | model | CRUD | self (modify) | exact |
| `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` | service | CRUD | self (modify) | exact |
| `src/main/kotlin/com/anjo/model/Schedule.kt` | model | CRUD | self (modify) | exact |
| `src/main/kotlin/com/anjo/model/ConflictPolicy.kt` | model | request-response | `src/main/kotlin/com/anjo/model/TextResponse.kt` | role-match |
| `src/main/kotlin/com/anjo/model/TextRequest.kt` | model | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/service/SchedulerService.kt` | service | event-driven | self (modify) | exact |
| `src/main/kotlin/com/anjo/service/ScreenDriver.kt` | service | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` | route | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt` | utility | request-response | self (modify) | exact |
| `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` | test | request-response | self (extend) | exact |
| `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` | test | CRUD | self (extend) | exact |
| `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` | test | event-driven | self (extend) | exact |

---

## Pattern Assignments

### `src/main/resources/db/migration/V1__initial_schema.sql` (migration, batch)

**Analog:** `src/main/kotlin/com/anjo/db/SchedulesTable.kt` (lines 1-18) — source of truth for existing 10-column schema

**Core pattern — document the current schema exactly as it exists on disk:**
```sql
-- NOT run on existing Pi installs (baselineOnMigrate skips V1).
-- IS run on fresh installs before V2.
CREATE TABLE IF NOT EXISTS schedules (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    text          TEXT         NOT NULL,
    trigger_type  VARCHAR(16)  NOT NULL,
    trigger_value VARCHAR(256) NOT NULL,
    effect        VARCHAR(16)  NOT NULL DEFAULT 'SCROLL',
    priority      INT          NOT NULL DEFAULT 0,
    max_runs      INT          NULL,
    expires_at    VARCHAR(32)  NULL,
    created_at    VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
);
```

Column-name mapping from `SchedulesTable.kt`:
- `id` → varchar("id", 36) PK
- `text` → text("text")
- `trigger_type` → varchar("trigger_type", 16)
- `trigger_value` → varchar("trigger_value", 256)
- `effect` → varchar("effect", 16) default "SCROLL"
- `priority` → integer("priority") default 0
- `max_runs` → integer("max_runs").nullable()
- `expires_at` → varchar("expires_at", 32).nullable()
- `created_at` → varchar("created_at", 32)
- `status` → varchar("status", 16) default "ACTIVE"

---

### `src/main/resources/db/migration/V2__add_scheduler_columns.sql` (migration, batch)

**Analog:** V1 sibling above; CONTEXT.md §Specifics exact SQL

**Core pattern:**
```sql
-- Adds 4 columns required by v1.1 scheduler features.
-- H2 and SQLite ADD COLUMN defaults to NULL; no explicit NULL keyword needed.
ALTER TABLE schedules ADD COLUMN conflict_policy VARCHAR(16) DEFAULT 'INTERRUPT';
ALTER TABLE schedules ADD COLUMN fired_at        VARCHAR(32);
ALTER TABLE schedules ADD COLUMN webhook_url     VARCHAR(512);
ALTER TABLE schedules ADD COLUMN zone_id         VARCHAR(64);
```

Timestamp pattern to follow: `fired_at` uses `VARCHAR(32)` consistent with `expires_at` and `created_at` (ISO-8601 strings).

---

### `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` (config, request-response)

**Analog:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` (self — lines 1-26)

**Current imports pattern** (lines 1-9):
```kotlin
package com.anjo.db

import com.anjo.config.model.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
```

**Add Flyway import:**
```kotlin
import org.flywaydb.core.Flyway
```

**Current init body** (lines 11-25) — Flyway call must be inserted AFTER `Database.connect(dataSource)`, BEFORE the `transaction { SchemaUtils.create(...) }` block:
```kotlin
fun init(databaseConfig: DatabaseConfig) {
    val config = HikariConfig().apply {
        jdbcUrl = databaseConfig.url
        driverClassName = databaseConfig.driver
        maximumPoolSize = databaseConfig.poolSize
        username = databaseConfig.user
        password = databaseConfig.password
        isAutoCommit = false
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    // INSERT FLYWAY CALL HERE — before SchemaUtils
    transaction {
        SchemaUtils.create(SchedulesTable)
    }
}
```

**Flyway pattern to insert:**
```kotlin
Flyway.configure()
    .dataSource(dataSource)
    .baselineOnMigrate(true)
    .baselineVersion("1")
    .load()
    .migrate()
```

**Order is critical:** HikariDataSource → Database.connect() → Flyway.migrate() → SchemaUtils.create(). SchemaUtils stays as safety net.

---

### `src/main/kotlin/com/anjo/db/SchedulesTable.kt` (model, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/SchedulesTable.kt` (self — lines 1-18)

**Current column pattern** (lines 6-16) — all four new columns follow identical style:
```kotlin
// Existing pattern to copy:
val maxRuns = integer("max_runs").nullable()
val expiresAt = varchar("expires_at", 32).nullable()
val createdAt = varchar("created_at", 32)
val status = varchar("status", 16).default("ACTIVE")
```

**Four new columns to append before `override val primaryKey`:**
```kotlin
val conflictPolicy = varchar("conflict_policy", 16).nullable().default("INTERRUPT")
val firedAt = varchar("fired_at", 32).nullable()
val webhookUrl = varchar("webhook_url", 512).nullable()
val zoneId = varchar("zone_id", 64).nullable()
```

---

### `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` (service, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` (self — lines 1-99)

**Current imports pattern** (lines 1-15):
```kotlin
package com.anjo.db

import com.anjo.model.Effect
import com.anjo.model.Schedule
import com.anjo.model.ScheduleStatus
import com.anjo.model.TriggerType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID
```

**Add to imports:**
```kotlin
import com.anjo.model.ConflictPolicy
```

**`findAllActive()` pattern to modify** (lines 30-34) — add ONESHOT+firedAt filter:
```kotlin
// Current:
suspend fun findAllActive(): List<Schedule> = suspendTransaction {
    SchedulesTable.selectAll()
        .where { SchedulesTable.status eq "ACTIVE" }
        .map { it.toSchedule() }
}
// Replace with (scopes firedAt filter to ONESHOT only — safer than bare AND firedAt IS NULL):
suspend fun findAllActive(): List<Schedule> = suspendTransaction {
    SchedulesTable.selectAll()
        .where {
            (SchedulesTable.status eq "ACTIVE") and
            not(
                (SchedulesTable.triggerType eq "ONESHOT") and
                (SchedulesTable.firedAt.isNotNull())
            )
        }
        .map { it.toSchedule() }
}
```

**`insert()` body pattern** (lines 39-53) — copy field-by-field style, add four new columns:
```kotlin
it[conflictPolicy] = schedule.conflictPolicy?.name
it[firedAt] = schedule.firedAt
it[webhookUrl] = schedule.webhookUrl
it[zoneId] = schedule.zoneId
```

**`updateStatus()` pattern** (lines 72-78) — copy for new `updateFiredAtAndDone()`:
```kotlin
suspend fun updateFiredAtAndDone(id: String, firedAt: String) {
    suspendTransaction {
        SchedulesTable.update({ SchedulesTable.id eq id }) {
            it[SchedulesTable.firedAt] = firedAt
            it[status] = "DONE"
        }
    }
}
```
Single `suspendTransaction {}` block — both columns updated atomically (D-15).

**`toSchedule()` private function** (lines 87-98) — add four new field reads at end:
```kotlin
// Copy existing pattern:
status = ScheduleStatus.valueOf(this[SchedulesTable.status])
// Add after:
conflictPolicy = this[SchedulesTable.conflictPolicy]?.let { ConflictPolicy.valueOf(it) },
firedAt = this[SchedulesTable.firedAt],
webhookUrl = this[SchedulesTable.webhookUrl],
zoneId = this[SchedulesTable.zoneId]
```

---

### `src/main/kotlin/com/anjo/model/Schedule.kt` (model, CRUD)

**Analog:** `src/main/kotlin/com/anjo/model/Schedule.kt` (self — lines 1-23)

**Current `@Serializable data class` pattern** (lines 11-23):
```kotlin
@Serializable
data class Schedule(
    val id: String = "",
    val text: String,
    val triggerType: TriggerType,
    val triggerValue: String,
    val effect: Effect = Effect.SCROLL,
    val priority: Int = 0,
    val maxRuns: Int? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val status: ScheduleStatus = ScheduleStatus.ACTIVE
)
```

**Changes required:**
1. Add `ERROR` to `ScheduleStatus` enum (line 7):
   ```kotlin
   enum class ScheduleStatus { ACTIVE, PAUSED, EXPIRED, DONE, ERROR }
   ```
2. Append four nullable fields to `Schedule` data class — follow the `val x: Type? = null` pattern for all four:
   ```kotlin
   val conflictPolicy: ConflictPolicy? = null,
   val firedAt: String? = null,
   val webhookUrl: String? = null,
   val zoneId: String? = null
   ```
3. Add import:
   ```kotlin
   import com.anjo.model.ConflictPolicy
   ```

---

### `src/main/kotlin/com/anjo/model/ConflictPolicy.kt` (model, request-response) — NEW FILE

**Analog:** `src/main/kotlin/com/anjo/model/TextResponse.kt` (lines 1-9) — same `@Serializable` enum pattern

**TextResponse pattern to copy:**
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class TextResponse(...)
```

**New file — copy `@Serializable` annotation, use enum instead of data class:**
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConflictPolicy {
    INTERRUPT,   // cancel any running display and start the new one (default)
    SKIP_NEW     // if display is busy, silently drop this request
}
```

`@Serializable` is required because `ConflictPolicy` will be deserialized from JSON via `TextRequest` and `Schedule` (Kotlinx serialization).

---

### `src/main/kotlin/com/anjo/model/TextRequest.kt` (model, request-response)

**Analog:** `src/main/kotlin/com/anjo/model/TextRequest.kt` (self — lines 1-9)

**Current pattern** (lines 1-9):
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class TextRequest(
    val text: String,
    val effect: Effect = Effect.SCROLL
)
```

**Change — add `conflictPolicy` with default so existing callers without the field still deserialize:**
```kotlin
val conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT
```
Default is mandatory per RESEARCH.md Pitfall 4 — missing default causes `SerializationException` for existing callers.

---

### `src/main/kotlin/com/anjo/service/SchedulerService.kt` (service, event-driven)

**Analog:** `src/main/kotlin/com/anjo/service/SchedulerService.kt` (self — lines 1-177)

**`launchOneShot()` current pattern** (lines 93-108):
```kotlin
private fun launchOneShot(schedule: Schedule): Job? {
    val targetMs = try {
        Instant.parse(schedule.triggerValue).toEpochMilli()
    } catch (e: Exception) {
        log.error("Invalid ONESHOT triggerValue for schedule ${schedule.id}: ${schedule.triggerValue}", e)
        return null
    }
    return scope.launch {
        val now = System.currentTimeMillis()
        val delayMs = targetMs - now
        if (delayMs > 0) delay(delayMs)
        fire(schedule)
        repository.updateStatus(schedule.id, "DONE")   // ← replace these two lines
        activeJobs.remove(schedule.id)
    }
}
```

**Replace the two-step update with the atomic call:**
```kotlin
        fire(schedule)
        repository.updateFiredAtAndDone(schedule.id, Instant.now().toString())
        activeJobs.remove(schedule.id)
```

**`launchCron()` CRON validation removal** — the `try { cronParser.parse(...) }` block (lines 132-138) stays in `launchCron()` as a runtime safety net, but the validation that matters for SCHED-03 moves to `ScheduleRoutes.kt`. `launchCron()` itself is unchanged; `ScheduleValidators` no longer rejects CRON before the handler runs.

**`fire()` return type change for SKIP_NEW / D-05:**
```kotlin
// Current (line 154):
private suspend fun fire(schedule: Schedule) {

// Change to:
private suspend fun fire(schedule: Schedule): Boolean {
```
Return `true` from the happy path; `screenService.displayScheduled()` returns `Boolean` (see ScreenDriver section). `launchRecurring()` only increments `runs` when `fire()` returns `true`.

**`launchRecurring()` maxRuns guard update** (lines 123-125):
```kotlin
// Current:
fire(schedule)
runs++

// Replace with:
val displayed = fire(schedule)
if (displayed) runs++
```

---

### `src/main/kotlin/com/anjo/service/ScreenDriver.kt` (service, request-response)

**Analog:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt` (self — lines 1-131)

**Existing `displayMutex.isLocked` pattern** (lines 107-108) — already used in `queueDisplaySwitch()`:
```kotlin
if (displayMutex.isLocked) {
    pendingDisplayType.set(normalizedType)
```
Copy this `isLocked` check pattern for SKIP_NEW.

**`displayImmediate()` current signature** (line 39):
```kotlin
suspend fun displayImmediate(text: String, effect: Effect = Effect.SCROLL) {
```

**Updated signature + SKIP_NEW guard (insert at top of function body):**
```kotlin
suspend fun displayImmediate(
    text: String,
    effect: Effect = Effect.SCROLL,
    conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT
): Boolean {
    if (conflictPolicy == ConflictPolicy.SKIP_NEW && displayMutex.isLocked) {
        log.info("SKIP_NEW: display busy, dropping ad-hoc request for text '${text.take(30)}'")
        return false
    }
    // ... rest of existing body unchanged ...
    return true   // add at end before closing brace
}
```

**`displayScheduled()` current signature** (line 61):
```kotlin
suspend fun displayScheduled(text: String, scheduleId: String, renderer: EffectRenderer) {
```

**Updated signature + SKIP_NEW guard:**
```kotlin
suspend fun displayScheduled(
    text: String,
    scheduleId: String,
    renderer: EffectRenderer,
    conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT
): Boolean {
    if (conflictPolicy == ConflictPolicy.SKIP_NEW && displayMutex.isLocked) {
        log.info("SKIP_NEW: display busy, dropping scheduled request id=$scheduleId")
        return false
    }
    // ... rest of existing body unchanged ...
    return true   // add before closing brace (after the finally block)
}
```

`displayMutex` is `private val` (line 31) — the SKIP_NEW check MUST live inside these methods, not outside. This is confirmed by RESEARCH.md Pitfall 6.

---

### `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` (route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` (self — lines 1-76)

**Current imports** (lines 1-16):
```kotlin
package com.anjo.routing

import com.anjo.db.ScheduleRepository
import com.anjo.model.Schedule
import com.anjo.service.SchedulerService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
```

**Add imports for CRON validation:**
```kotlin
import com.anjo.model.ErrorResponse
import com.anjo.model.ErrorDetails
import com.anjo.model.ScheduleStatus
import com.anjo.model.TriggerType
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
```

**CronParser instantiation — copy pattern from `ScheduleValidators.kt` line 9:**
```kotlin
private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
```

**Current POST handler** (lines 27-33):
```kotlin
post {
    val body = call.receive<Schedule>()
    val created = repository.insert(body)
    schedulerService.schedule(created)
    log.info("Schedule created: id=${created.id} ...")
    call.respond(HttpStatusCode.Created, created)
}
```

**Updated POST handler — add CRON validation before normal insert:**
```kotlin
post {
    val body = call.receive<Schedule>()
    if (body.triggerType == TriggerType.CRON) {
        try {
            cronParser.parse(body.triggerValue)
        } catch (e: Exception) {
            val errorRow = body.copy(status = ScheduleStatus.ERROR)
            val persisted = repository.insert(errorRow)
            log.warn("Invalid CRON for schedule — persisted with ERROR id=${persisted.id}: ${e.message}")
            return@post call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
            )
        }
    }
    val created = repository.insert(body)
    schedulerService.schedule(created)
    log.info("Schedule created: id=${created.id} trigger=${created.triggerType}:${created.triggerValue} effect=${created.effect} priority=${created.priority}")
    call.respond(HttpStatusCode.Created, created)
}
```

**`ErrorResponse` + `ErrorDetails.now()` pattern** — from `src/main/kotlin/com/anjo/model/ErrorResponse.kt` (lines 7-26):
```kotlin
ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
// ErrorDetails.now() signature: fun now(code: String, message: String, details: Map<String,String>? = null)
```

**SKIP_NEW response in `TextRoutes.kt`** — `TextRoutes.kt` currently always responds 202 Accepted (line 20-23). Update to check `displayImmediate()` return value:
```kotlin
// TextRoutes.kt post handler update:
val accepted = screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy)
call.respond(
    HttpStatusCode.Accepted,
    TextResponse(accepted = accepted, message = if (accepted) "Text queued for rendering" else "Display busy, request skipped")
)
```

---

### `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt` (utility, request-response)

**Analog:** `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt` (self — lines 1-50)

**Current `validateCron()` branch** (lines 22-33) — this branch MUST be removed from `validateSchedule()`. The `when` branch for `TriggerType.CRON` must no longer call `validateCron()`:
```kotlin
// Current (remove this case or replace with ValidationResult.Valid for CRON):
TriggerType.CRON -> validateCron(schedule.triggerValue)

// Replace with:
TriggerType.CRON -> ValidationResult.Valid  // CRON validation moved to ScheduleRoutes.kt
```

The private `validateCron()` method itself can be left or deleted — it is no longer called. Deleting is cleaner.

**`webhookUrl` validator to add** — copy `ValidationResult.Invalid(...)` pattern from existing validators (lines 35-43):
```kotlin
// Add after the priority check, before the triggerType when block:
val webhookUrl = schedule.webhookUrl
if (webhookUrl != null && !webhookUrl.matches(Regex("^https?://.*"))) {
    return ValidationResult.Invalid("webhookUrl must be a valid http/https URL")
}
```

`schedule.webhookUrl` requires that `Schedule` model already has the new field (dependency: model change lands first).

---

## Shared Patterns

### `suspendTransaction {}` for all DB writes
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` (lines 39-53, 72-78)
**Apply to:** `updateFiredAtAndDone()` (new method), all new `insert()` column writes
```kotlin
suspendTransaction {
    SchedulesTable.update({ SchedulesTable.id eq id }) {
        it[SchedulesTable.firedAt] = firedAt
        it[status] = "DONE"
    }
}
```
Single block = atomic. Never split `firedAt` and `status` writes into two `suspendTransaction` calls.

### `@Serializable` enum pattern
**Source:** `src/main/kotlin/com/anjo/model/Schedule.kt` (lines 5-9) — `Effect`, `ScheduleStatus`, `TriggerType` are plain enums; `TextResponse` and `TextRequest` are `@Serializable` data classes
**Apply to:** `ConflictPolicy.kt` (new enum) — must have `@Serializable` because it is embedded in `TextRequest` and `Schedule` which are both `@Serializable` data classes used in `call.receive<>()`
```kotlin
import kotlinx.serialization.Serializable

@Serializable
enum class ConflictPolicy { INTERRUPT, SKIP_NEW }
```

### `ErrorResponse` / `ErrorDetails.now()` error shape
**Source:** `src/main/kotlin/com/anjo/model/ErrorResponse.kt` (lines 1-27)
**Apply to:** `ScheduleRoutes.kt` CRON validation failure path
```kotlin
call.respond(
    HttpStatusCode.UnprocessableEntity,
    ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
)
```

### Kotest FunSpec test structure
**Source:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` (lines 1-35) — standard test scaffold
**Apply to:** all new test cases added to existing test files
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerServiceTest : FunSpec({
    val mockRepo = mockk<ScheduleRepository>(relaxed = true)
    // ...
    beforeEach {
        clearMocks(mockRepo, ...)
        coEvery { ... } returns ...
    }
    test("description") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            // arrange, act, verify
            service.stop()
            testScope.coroutineContext[Job]?.cancel()
        }
    }
})
```

### H2 in-memory test DB setup
**Source:** `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` (lines 23-29)
**Apply to:** `ScheduleRepositoryTest.kt` new test cases — same `beforeSpec` and `beforeEach` blocks
```kotlin
beforeSpec {
    Database.connect("jdbc:h2:mem:test_schedules;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction { SchemaUtils.create(SchedulesTable) }
}
beforeEach {
    transaction { SchedulesTable.deleteWhere { SchedulesTable.id.isNotNull() } }
}
```

### `displayMutex.isLocked` busy-check pattern
**Source:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt` (lines 107-108)
**Apply to:** `displayImmediate()` and `displayScheduled()` SKIP_NEW guard
```kotlin
if (displayMutex.isLocked) {
    pendingDisplayType.set(normalizedType)  // ← existing usage
    // For SKIP_NEW: log and return false instead
}
```

---

## No Analog Found

All files have analogs or are self-modifications. No files lack a pattern reference.

---

## Dependency Order for Implementation

The following ordering is required because later changes depend on earlier ones:

1. `ConflictPolicy.kt` — new file, no dependencies
2. `Schedule.kt` — add ERROR + 4 new fields (requires ConflictPolicy import)
3. `TextRequest.kt` — add conflictPolicy field (requires ConflictPolicy)
4. `SchedulesTable.kt` — add 4 new columns
5. `V1__initial_schema.sql` + `V2__add_scheduler_columns.sql` — SQL resources (no Kotlin deps)
6. `build.gradle.kts` / `ktor-libs.versions.toml` — add Flyway dependency
7. `DatabaseFactory.kt` — wire Flyway (requires flyway dep on classpath)
8. `ScheduleRepository.kt` — updateFiredAtAndDone(), findAllActive() filter, insert/toSchedule new columns (requires SchedulesTable + Schedule + ConflictPolicy)
9. `SchedulerService.kt` — launchOneShot firedAt update, fire() Boolean return (requires ScheduleRepository.updateFiredAtAndDone)
10. `ScreenDriver.kt` — displayImmediate/displayScheduled SKIP_NEW + Boolean return (requires ConflictPolicy)
11. `ScheduleValidators.kt` — remove validateCron case, add webhookUrl validator (requires Schedule.webhookUrl)
12. `ScheduleRoutes.kt` — CRON validation at handler (requires ScheduleRepository, ErrorResponse, CronParser)
13. `TextRoutes.kt` — pass conflictPolicy to displayImmediate, handle Boolean return
14. Test files — extend after all source changes

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`
**Files read:** 14 source files
**Pattern extraction date:** 2026-06-14
