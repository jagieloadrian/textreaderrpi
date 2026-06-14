# Phase 7: Scheduler Schema Stabilisation - Research

**Researched:** 2026-06-14
**Domain:** Kotlin/Ktor scheduler edge-case fixes, Flyway migrations, Exposed ORM, coroutine mutex patterns
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**ConflictPolicy (SCHED-01)**
- D-01: `conflictPolicy: ConflictPolicy = INTERRUPT` added as a field on `TextRequest`
- D-02: `conflictPolicy` column added on `SchedulesTable` (nullable, default `INTERRUPT`)
- D-03: Scope is both ad-hoc POST /api/v1/text and scheduled displays
- D-04: Busy detection uses `displayMutex.isLocked` from `ScreenDriverService`
- D-05: Skipped fires do NOT count against `maxRuns`

**Schema Migration — Flyway**
- D-06: Flyway (`org.flywaydb:flyway-core`) is the migration tool
- D-07: `DatabaseFactory.init()` calls `Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate()` before Exposed schema operations
- D-08: `baselineOnMigrate=true` with `baselineVersion("1")` handles existing Pi installs
- D-09: Migration files in `src/main/resources/db/migration/`: `V1__initial_schema.sql` (baseline) and `V2__add_scheduler_columns.sql` (new columns)

**New Columns (SCHED-04)**
- D-10: `webhookUrl varchar(512) NULL` — URL validated at POST insert time, invalid URL → 422
- D-11: `zoneId varchar(64) NULL` — nullable varchar, no validation in Phase 7
- D-12: `conflictPolicy varchar(16) NULL DEFAULT 'INTERRUPT'` on `SchedulesTable`
- D-13: `firedAt varchar(32) NULL` on `SchedulesTable` — ISO timestamp string

**ONESHOT Restart Guard (SCHED-02)**
- D-14: `firedAt` null-check in `findAllActive()`: add `AND firedAt IS NULL` to WHERE clause
- D-15: `launchOneShot()` sets `firedAt` atomically with `status=DONE` in single `suspendTransaction {}`

**CRON Validation (SCHED-03)**
- D-16: Validate CRON at POST handler using `CronParser`. Invalid: persist with `status=ERROR`, return 422. Scheduler never loads ERROR rows.
- D-17: Add `ERROR` to `ScheduleStatus` enum. `findAllActive()` already filters by `status=ACTIVE` — no query change needed.

### Claude's Discretion

- HTTP response for SKIP_NEW busy path: either 200 with `{accepted:false, message:"Display busy, request skipped"}` or 409 Conflict. Pick based on existing API conventions.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCHED-01 | User can select `SKIP_NEW` as ConflictPolicy; second submission silently ignored while display is busy | ConflictPolicy enum + TextRequest field + ScreenDriverService.displayImmediate/displayScheduled SKIP_NEW path via `displayMutex.isLocked` |
| SCHED-02 | ONESHOT schedule that fired before Pi crash does not fire again after restart | `firedAt` column + `findAllActive()` null-check + atomic `suspendTransaction {}` update in `launchOneShot()` |
| SCHED-03 | Schedule with invalid CRON expression written to DB with status `ERROR`; scheduler loop continues | CRON validation moved from RequestValidation to route handler; `ScheduleStatus.ERROR` enum value; `findAllActive()` already excludes non-ACTIVE |
| SCHED-04 | Schedule row persists `webhookUrl` and `zoneId` fields for downstream phases | V2 migration SQL adds both columns; Exposed table + model + repo updated |
</phase_requirements>

---

## Summary

Phase 7 is a surgical extension of the existing scheduler infrastructure. No rewrites are needed — the work is four targeted changes: add a Flyway migration layer on top of the existing Exposed `SchemaUtils.create()`, fix two scheduler edge-case bugs (`firedAt` for ONESHOT restart guard and `ERROR` status for bad CRON), add the `SKIP_NEW` conflict policy path to display dispatch, and add two nullable stub columns (`webhookUrl`, `zoneId`) for downstream phases.

The biggest implementation subtlety is the CRON validation behaviour change. The current codebase already validates CRON expressions inside `ScheduleValidators.validateCron()` which runs as a Ktor `RequestValidation` handler, rejecting bad expressions with a 422 before the request body ever reaches the route handler. SCHED-03 requires invalid CRON rows to be **persisted** with `status=ERROR`. This means the CRON-specific validation must be removed from `ScheduleValidators` and re-implemented inside the route handler so the row can be written before the 422 is returned.

Flyway 11.8.2 is the current latest stable version. Its `Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate()` API (the `FluentConfiguration` chain) is confirmed present in Flyway 11. H2 support is bundled in `flyway-core` — no separate database module required. The existing H2 2.4.240 in the project is compatible.

**Primary recommendation:** Add Flyway 9.22.3 (last of the 9.x series, LTS API stability) or 11.8.2 (latest) to `ktor-libs.versions.toml`, wire it into `DatabaseFactory.init()`, write the two SQL migration files, then make the four Kotlin changes: new enum value, model fields, repository update, and route handler tweak.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Schema migration (Flyway) | Database / Storage | — | DDL changes belong at the DB init layer (`DatabaseFactory`) |
| ConflictPolicy SKIP_NEW check | Service layer (`ScreenDriverService`) | HTTP routes (response shape) | Busy detection is a service concern; HTTP status code is a route concern |
| ONESHOT firedAt atomic update | Service layer (`SchedulerService`) | Database layer (`ScheduleRepository`) | Scheduler owns the fire event; repo provides the transaction primitive |
| CRON validation + ERROR persist | HTTP routes (`ScheduleRoutes`) | Database layer (`ScheduleRepository`) | Must reach the handler to persist before returning 422 |
| New column definitions | Database / Storage (`SchedulesTable`, `ScheduleRepository`) | Model layer (`Schedule`) | Table is source of truth; model mirrors it |

---

## Standard Stack

### Core (existing — no additions needed except Flyway)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Flyway Core | 9.22.3 [VERIFIED: Maven Central, 259 versions, last of 9.x LTS series] | SQL migration lifecycle | Flyway 9.x: single jar, H2 bundled, stable API, Java 8+ → JDK 25 compatible |
| Exposed (existing) | 1.3.0 [ASSUMED] | ORM / DSL query building | Already in project; `suspendTransaction {}` pattern used throughout |
| H2 (existing) | 2.4.240 [ASSUMED] | Embedded DB | Already used; Flyway 9.x fully supports H2 2.x |
| cron-utils (existing) | 9.2.1 [ASSUMED] | CRON expression parsing | `CronParser` already imported in `SchedulerService` and `ScheduleValidators` |
| Ktor coroutines Mutex (existing) | — | Display busy detection | `displayMutex.isLocked` already available in `ScreenDriverService` |

### Flyway Version Choice

**Recommended: Flyway 9.22.3** [VERIFIED: Maven Central]

Rationale for choosing 9.x over 11.x:
- CONTEXT.md D-07 prescribes the exact `Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate()` chain — identical API in both 9.x and 11.x (`FluentConfiguration.dataSource()`, `baselineOnMigrate()`, `baselineVersion()` all verified present in Flyway 11 source).
- Flyway 10+ introduced a community/enterprise split that affects some features. Flyway 9.22.3 is the last stable 9.x with the classic open-source API and no licensing considerations.
- H2 support is bundled in `flyway-core` for both generations — no extra modules needed.
- JDK 25 toolchain: Flyway 9.x requires Java 8+, so JDK 25 is fine.

If the team prefers latest, Flyway 11.8.2 also works — `FluentConfiguration` API is identical for the operations used in D-07.

### Gradle Dependency Entry

```toml
# Add to gradle/ktor-libs.versions.toml [versions]
flyway = "9.22.3"

# Add to [libraries]
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
```

```kotlin
// Add to build.gradle.kts dependencies
implementation(ktorLibs.flyway.core)
```

### Supporting

No new test libraries needed — Kotest FunSpec + MockK + H2 in-memory already cover all new test cases.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Flyway 9.22.3 | Flyway 11.8.2 | 11.x is latest; 9.x has simpler community licensing. API used here is identical in both. |
| Flyway migration | Manual `ALTER TABLE` in DatabaseFactory | Flyway gives idempotent, versioned, crash-safe migrations. Manual ALTER crashes on re-run. |
| Route-level CRON validation | Keep in RequestValidation | RequestValidation prevents the row from being persisted — breaks SCHED-03 requirement. |

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| org.flywaydb:flyway-core | Maven Central | 15+ years, 259 versions | Widely used (millions of downloads) | github.com/flyway/flyway | OK [VERIFIED: Maven Central] | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious (SUS):** none

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/text
       │
       ▼
  TextRoutes ──► receive<TextRequest>  (+ conflictPolicy field)
       │
       ▼
  ScreenDriverService.displayImmediate(text, effect, conflictPolicy)
       │
       ├── conflictPolicy == SKIP_NEW && displayMutex.isLocked?
       │        YES → return TextResponse(accepted=false, message="Display busy, request skipped") [HTTP 200]
       │        NO  → proceed
       │
       ▼
  displayMutex.withLock { executeWithRecovery(...) }
       │
       ▼
  HTTP 202 TextResponse(accepted=true)

POST /api/v1/schedule
       │
       ▼
  ScheduleRoutes.post handler
       │
       ├── triggerType == CRON?
       │       YES → try cronParser.parse(triggerValue)
       │                 ├── parse OK → continue normal flow
       │                 └── parse throws → repository.insert(body.copy(status=ERROR))
       │                                    → return HTTP 422
       │
       ▼
  ScheduleRepository.insert(schedule)  (persists all new columns)
       │
       ▼
  schedulerService.schedule(created)  [only if status == ACTIVE]

Application startup
       │
       ▼
  DatabaseFactory.init()
       │
       ├── HikariDataSource created
       ├── Flyway.configure().dataSource(ds).baselineOnMigrate(true)
       │       .baselineVersion("1").load().migrate()
       │       ├── fresh install: runs V1 + V2
       │       └── existing Pi install (has schedules table, no flyway_schema_history):
       │               baseline at V1, then applies V2
       │
       └── SchemaUtils.create(SchedulesTable)  [safety net if Flyway missed anything]

SchedulerService.start() / tickLoop()
       │
       ▼
  repository.findAllActive()
       │   WHERE status = 'ACTIVE'
       │     AND firedAt IS NULL        ← SCHED-02: excludes already-fired ONESHOT
       ▼
  schedule(each) → launchOneShot / launchRecurring / launchCron
       │
  launchOneShot fires:
       ├── screenService.displayScheduled(...)
       └── suspendTransaction {
               SchedulesTable.update { firedAt = now; status = DONE }
           }
```

### Recommended Project Structure Changes

```
src/main/
├── kotlin/com/anjo/
│   ├── db/
│   │   ├── SchedulesTable.kt          # add 4 new columns
│   │   └── ScheduleRepository.kt      # updateFiredAt(), findAllActive() firedAt filter
│   ├── model/
│   │   ├── Schedule.kt                # add ERROR enum value + 4 new fields
│   │   ├── TextRequest.kt             # add conflictPolicy field
│   │   └── ConflictPolicy.kt          # NEW: enum INTERRUPT, SKIP_NEW
│   ├── service/
│   │   ├── SchedulerService.kt        # launchOneShot firedAt update
│   │   └── ScreenDriver.kt            # SKIP_NEW check in displayImmediate + displayScheduled
│   ├── routing/
│   │   └── ScheduleRoutes.kt          # CRON validation moved here
│   └── validation/
│       └── ScheduleValidators.kt      # remove validateCron, add webhookUrl validator
└── resources/
    └── db/migration/                  # NEW directory
        ├── V1__initial_schema.sql     # baseline documentation
        └── V2__add_scheduler_columns.sql
```

### Pattern 1: Flyway Baseline-on-Migrate for Existing Installs

**What:** `baselineOnMigrate=true` tells Flyway: if the `flyway_schema_history` table does not exist but the target schema does, stamp it at `baselineVersion` rather than attempting to create tables that already exist.

**When to use:** When adding Flyway to a project that has a pre-existing schema.

```kotlin
// Source: Flyway FluentConfiguration (confirmed in flyway-core 11.x source; same API in 9.x)
// In DatabaseFactory.init(), BEFORE SchemaUtils.create():
val flyway = Flyway.configure()
    .dataSource(dataSource)          // HikariDataSource passed directly
    .baselineOnMigrate(true)
    .baselineVersion("1")
    .load()
flyway.migrate()
```

The call to `SchemaUtils.create(SchedulesTable)` stays after Flyway — it is a no-op when the table already exists (Exposed's `CREATE TABLE IF NOT EXISTS` equivalent).

### Pattern 2: Atomic firedAt + status=DONE in suspendTransaction

**What:** Setting two columns in a single Exposed `suspendTransaction {}` block prevents a crash between the `fire()` call and the status update from leaving the schedule in an orphaned state.

**When to use:** Any time two correlated column writes must be crash-safe.

```kotlin
// In SchedulerService.launchOneShot() — replace the two-step update with:
suspend fun updateFiredAtAndDone(id: String, firedAt: String) {
    suspendTransaction {
        SchedulesTable.update({ SchedulesTable.id eq id }) {
            it[SchedulesTable.firedAt] = firedAt
            it[status] = "DONE"
        }
    }
}

// Called from launchOneShot after fire():
fire(schedule)
repository.updateFiredAtAndDone(schedule.id, Instant.now().toString())
activeJobs.remove(schedule.id)
```

### Pattern 3: SKIP_NEW via displayMutex.isLocked

**What:** Check mutex lock state before acquiring it; return early if busy and policy is SKIP_NEW.

**When to use:** Implementing a non-blocking "drop if busy" policy without a queuing layer.

```kotlin
// In ScreenDriverService.displayImmediate():
suspend fun displayImmediate(text: String, effect: Effect = Effect.SCROLL,
                             conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT) {
    if (conflictPolicy == ConflictPolicy.SKIP_NEW && displayMutex.isLocked) {
        log.info("SKIP_NEW: display busy, dropping request for text '${text.take(30)}'")
        return   // caller receives TextResponse(accepted=false) from the route
    }
    currentDisplayJob?.cancel()
    // ... rest of existing implementation unchanged
}
```

The route checks the return path to build the appropriate response.

**CRITICAL:** `displayMutex.isLocked` is a snapshot read — it is not guaranteed to still be locked when `withLock {}` is called milliseconds later. For SKIP_NEW this is acceptable (false negatives are better than deadlocks), but it must not be used as a correctness gate. [ASSUMED — standard Kotlin mutex documentation caveat]

### Pattern 4: CRON Validation Moved to Route Handler

**What:** Remove `validateCron` from `ScheduleValidators` (where it runs pre-handler via RequestValidation) and re-implement it inside the POST route handler so the row can be persisted before the 422 is returned.

**When to use:** When you need side-effects (DB write) even on invalid input.

```kotlin
// In ScheduleRoutes.kt POST handler:
post {
    val body = call.receive<Schedule>()
    if (body.triggerType == TriggerType.CRON) {
        try {
            cronParser.parse(body.triggerValue)
        } catch (e: Exception) {
            val errorRow = body.copy(status = ScheduleStatus.ERROR)
            repository.insert(errorRow)
            log.warn("Invalid CRON for schedule — persisted with ERROR: ${e.message}")
            return@post call.respond(HttpStatusCode.UnprocessableEntity,
                ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}")))
        }
    }
    val created = repository.insert(body)
    schedulerService.schedule(created)
    call.respond(HttpStatusCode.Created, created)
}
```

`cronParser` is already available as a private val in `SchedulerService` — import and instantiate it similarly in `ScheduleRoutes.kt` (or pass it as a dependency, though inline is simpler given existing pattern).

### Pattern 5: SKIP_NEW Skipped Fires Must Not Decrement maxRuns

**What:** D-05 requires that a SKIP_NEW skip does NOT count as a run. The existing `launchRecurring()` increments `runs` only after `fire()` returns. Since SKIP_NEW returns early inside `displayScheduled()` (before rendering), the scheduler's `fire()` call completes without rendering — but `runs` still increments.

**Resolution:** The SKIP_NEW early return must happen in `ScreenDriverService`, and `SchedulerService.fire()` needs to propagate whether the display actually ran. Two options:

1. `displayScheduled()` and `displayImmediate()` return a `Boolean` indicating whether display occurred.
2. Track skip via an exception or sealed return type.

**Recommended:** Make `displayScheduled()` return `Boolean` — `true` = rendered, `false` = skipped. `fire()` propagates this. `launchRecurring()` only increments `runs` when `fire()` returns `true`. This is the minimal invasive change. [ASSUMED — design choice within Claude's discretion scope]

### Anti-Patterns to Avoid

- **Running `SchemaUtils.create()` before Flyway:** Exposed creates the table with all columns defined in `SchedulesTable.kt` (which will include the new columns after Phase 7). If this runs before Flyway on an existing Pi install, the table structure may be partially correct but `flyway_schema_history` will not exist. Call Flyway first.
- **Two-step firedAt + status update outside a transaction:** A coroutine crash between `updateStatus("DONE")` and a separate `firedAt` update leaves the schedule in an inconsistent state. Always use a single `suspendTransaction {}`.
- **Leaving `validateCron` in `ScheduleValidators`:** If CRON validation remains in `RequestValidation`, the request is rejected before the route handler runs, making it impossible to persist the `status=ERROR` row. The existing test `"should return 422 with error for invalid cron expression"` must be updated to assert that the row was persisted with `status=ERROR`.
- **Using `displayMutex.tryLock()` for SKIP_NEW:** `tryLock()` acquires the lock if available; the intent is to skip without acquiring, not to acquire. Use `isLocked` for the check and do not acquire.
- **Adding `@Serializable` to `ConflictPolicy` without a default in `TextRequest`:** The field must have a default (`INTERRUPT`) so existing callers that do not send `conflictPolicy` still deserialize correctly.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL migration versioning | Custom migration runner in `DatabaseFactory` | Flyway | Idempotent, ordered, crash-safe, handles baseline; custom code re-runs ALTERs on restart and crashes |
| CRON expression parsing | Regex or manual parser | `CronParser` from `cron-utils` (already in project) | CRON edge cases (L, W, #, ranges, steps) are numerous; cron-utils 9.2.1 already handles them |
| Display busy detection | Custom `AtomicBoolean` flag | `displayMutex.isLocked` (already in `ScreenDriverService`) | The mutex is the authoritative source of truth for display lock state — a separate flag would be inconsistent |
| Transaction atomicity | Two separate `suspendTransaction` calls | Single `suspendTransaction { update firedAt + status }` | Two calls are not atomic; crash between them = orphaned ONESHOT |

**Key insight:** Every problem in this phase has an existing in-project primitive that solves it. The work is wiring existing assets together, not building new infrastructure.

---

## Common Pitfalls

### Pitfall 1: Flyway Baseline Race with Exposed SchemaUtils

**What goes wrong:** If `SchemaUtils.create(SchedulesTable)` runs before `Flyway.migrate()` on a fresh install, Flyway finds a table it didn't create and may complain about schema drift (depends on Flyway version and `validateOnMigrate` setting).

**Why it happens:** `DatabaseFactory.init()` currently calls `SchemaUtils.create()` first. Flyway must be added before that line.

**How to avoid:** Flyway call first, then `SchemaUtils.create()`. The order in `DatabaseFactory.init()` is:
1. HikariDataSource configured
2. `Database.connect(dataSource)` (Exposed wiring)
3. `Flyway.configure()...migrate()` — runs V1 baseline + V2 ALTER
4. `transaction { SchemaUtils.create(SchedulesTable) }` — safety net

**Warning signs:** `FlywayException: Found non-empty schema(s) ... without schema history table!` — indicates Flyway ran after a table already existed and `baselineOnMigrate` was not set.

### Pitfall 2: V1__initial_schema.sql Content vs. Flyway Baseline Behavior

**What goes wrong:** Assuming Flyway runs `V1__initial_schema.sql` on existing Pi installs. It does not. `baselineOnMigrate=true` stamps the existing schema as V1 without executing `V1__initial_schema.sql`. The file exists for documentation and for fresh installs only.

**Why it happens:** Flyway's baseline mechanism skips all migrations at or below the baseline version.

**How to avoid:** Design `V1__initial_schema.sql` as a CREATE TABLE that matches the **current** `schedules` table (10 columns). On a fresh install, Flyway runs V1 (creates table), then V2 (adds 4 columns). On existing install, Flyway baselines at V1, then runs V2. Both paths end in the same 14-column table. Do not add the new columns to V1.

**Warning signs:** Fresh install missing columns — means V2 migration file path is wrong or was not picked up by Flyway's classpath scanner. Default scan location is `classpath:db/migration` which maps to `src/main/resources/db/migration/`. [ASSUMED — Flyway default scan location]

### Pitfall 3: CRON Validation Test Update Required

**What goes wrong:** Existing `ScheduleRoutesTest` test `"should return 422 with error for invalid cron expression"` expects a 422. After Phase 7 changes, the row IS persisted with `status=ERROR` before the 422 is returned. The test still passes (422 is still returned), but a more complete test should also assert that the row exists in the DB with `status=ERROR`.

**Why it happens:** The existing test only checks HTTP status; it cannot detect if the row was silently not persisted.

**How to avoid:** Add a `ScheduleRepositoryTest` assertion that after an invalid CRON POST, a row exists with `status=ERROR`.

### Pitfall 4: ConflictPolicy Serialization in TextRequest

**What goes wrong:** Adding `conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT` to `TextRequest` requires `ConflictPolicy` to be `@Serializable`. Since it is used in JSON deserialization (Ktor `call.receive<TextRequest>()`), missing the annotation causes a `SerializationException` at request time.

**Why it happens:** Kotlinx serialization requires explicit annotation for non-primitive types.

**How to avoid:** Annotate `ConflictPolicy` enum with `@Serializable`. Since `TextRequest` is `@Serializable` already, Kotlinx will then handle the nested enum correctly.

**Warning signs:** `SerializationException: Serializer for class 'ConflictPolicy' is not found` in test logs.

### Pitfall 5: ONESHOT firedAt Filter Changes Existing Startup Behavior

**What goes wrong:** Adding `AND firedAt IS NULL` to `findAllActive()` also affects any ONESHOT schedules that were written to the DB before Phase 7, which have `firedAt=NULL` but `status=DONE`. After Phase 7, `findAllActive()` filters by `status='ACTIVE'` — a DONE schedule is already excluded by the status filter. The `firedAt IS NULL` filter is redundant for `status=DONE` rows but essential for the crash scenario: a ONESHOT that fired (setting `firedAt`) but crashed before writing `status=DONE`.

**How to avoid:** Verify the filter: `WHERE status = 'ACTIVE' AND (firedAt IS NULL OR triggerType != 'ONESHOT')`. The D-14 approach (`AND firedAt IS NULL`) works for ONESHOT but would incorrectly exclude RECURRING/CRON rows if they ever get `firedAt` set. A safer filter is: exclude rows where `triggerType='ONESHOT' AND firedAt IS NOT NULL`. [ASSUMED — requires discussion-phase clarification or careful implementation]

**Recommended Exposed implementation:**

```kotlin
// In ScheduleRepository.findAllActive():
SchedulesTable.selectAll()
    .where {
        (SchedulesTable.status eq "ACTIVE") and
        not(
            (SchedulesTable.triggerType eq "ONESHOT") and
            (SchedulesTable.firedAt.isNotNull())
        )
    }
    .map { it.toSchedule() }
```

This is semantically equivalent to D-14 for ONESHOT schedules and does not interfere with RECURRING/CRON rows.

### Pitfall 6: `displayMutex.isLocked` is Package-Private

**What goes wrong:** `displayMutex` is `private val` in `ScreenDriverService`. The SKIP_NEW check must live inside `ScreenDriverService` itself — it cannot be checked from outside.

**Why it happens:** Kotlin visibility: `private val displayMutex` is not accessible from route handlers or scheduler service.

**How to avoid:** The SKIP_NEW check belongs inside `displayImmediate()` and `displayScheduled()` — both are already methods on `ScreenDriverService`. D-04 correctly prescribes this location.

---

## Code Examples

### V1__initial_schema.sql (baseline documentation)

```sql
-- Source: current SchedulesTable.kt schema (10 columns)
-- This file is NOT run on existing Pi installs (baselineOnMigrate skips V1).
-- It IS run on fresh installs before V2.
CREATE TABLE IF NOT EXISTS schedules (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    text         TEXT         NOT NULL,
    trigger_type VARCHAR(16)  NOT NULL,
    trigger_value VARCHAR(256) NOT NULL,
    effect       VARCHAR(16)  NOT NULL DEFAULT 'SCROLL',
    priority     INT          NOT NULL DEFAULT 0,
    max_runs     INT          NULL,
    expires_at   VARCHAR(32)  NULL,
    created_at   VARCHAR(32)  NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
);
```

### V2__add_scheduler_columns.sql

```sql
-- Source: CONTEXT.md §Specifics / D-10 through D-13
ALTER TABLE schedules ADD COLUMN conflict_policy VARCHAR(16) DEFAULT 'INTERRUPT';
ALTER TABLE schedules ADD COLUMN fired_at        VARCHAR(32);
ALTER TABLE schedules ADD COLUMN webhook_url     VARCHAR(512);
ALTER TABLE schedules ADD COLUMN zone_id         VARCHAR(64);
```

Note: H2 and PostgreSQL both support `ALTER TABLE ... ADD COLUMN` without `NULL` keyword (columns are nullable by default). [ASSUMED — standard SQL DDL behavior]

### ConflictPolicy enum (new file)

```kotlin
// src/main/kotlin/com/anjo/model/ConflictPolicy.kt
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConflictPolicy {
    INTERRUPT,   // default: cancel any running display and start new one
    SKIP_NEW     // if display is busy, silently drop this request
}
```

### ScheduleStatus enum update

```kotlin
// In Schedule.kt — add ERROR to existing enum
enum class ScheduleStatus { ACTIVE, PAUSED, EXPIRED, DONE, ERROR }
```

### webhookUrl validation in ScheduleValidators

```kotlin
// Add to ScheduleValidators.validateSchedule()
val webhookUrl = schedule.webhookUrl
if (webhookUrl != null && !webhookUrl.matches(Regex("^https?://.*"))) {
    return ValidationResult.Invalid("webhookUrl must be a valid http/https URL")
}
```

This validation stays in `ScheduleValidators` (not moved to route handler) because it is a format rejection, not a "persist-with-error" scenario. [ASSUMED — consistent with D-10]

### SKIP_NEW response decision (Claude's Discretion)

The existing API returns `HTTP 202 Accepted` with `TextResponse(accepted=true, ...)` for successful text display. For SKIP_NEW when busy, the options are:

- `HTTP 200` with `TextResponse(accepted=false, message="Display busy, request skipped")` — semantically: request was received and processed (policy applied), result is "no display"
- `HTTP 409 Conflict` — semantically: server state conflict prevented action

**Recommendation: HTTP 202 with `accepted=false`.** Rationale: The existing `TextRoutes.kt` always returns 202 Accepted. Using 200 would break response code consistency. 409 implies a recoverable conflict that the client should retry — but SKIP_NEW means "don't retry." `TextResponse.accepted` is already a boolean discriminator. So the client can inspect `accepted` to distinguish "queued" from "skipped" while keeping the status code consistent with the happy path. [ASSUMED — design decision within Claude's discretion]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `SchemaUtils.create()` only | Flyway migration + SchemaUtils safety net | Phase 7 | Schema changes are versioned and idempotent; Pi upgrades no longer require manual DDL |
| ONESHOT fires unconditionally on restart | `firedAt` null-check in `findAllActive()` | Phase 7 | Eliminates duplicate-display bug after Pi power loss |
| CRON validation rejects request | CRON validation persists ERROR row + returns 422 | Phase 7 | Allows audit trail of bad configuration; downstream can query `status=ERROR` rows |

**No deprecated approaches introduced in this phase.**

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Flyway default classpath scan location is `classpath:db/migration` mapping to `src/main/resources/db/migration/` | Architecture Patterns, Pitfall 2 | Migration files not found; Flyway runs with no migrations and does not apply V2. Fix: add explicit `.locations("classpath:db/migration")` to Flyway config. |
| A2 | `displayScheduled()` should be modified to return `Boolean` (true = rendered, false = skipped) to prevent maxRuns decrement on SKIP_NEW | Don't Hand-Roll, Pattern 3 | If false, SKIP_NEW silently exhausts `maxRuns` after enough skips, violating D-05. Alternative: use a thrown exception/sealed type. |
| A3 | `AND firedAt IS NULL` filter in `findAllActive()` should be scoped to `triggerType='ONESHOT'` to avoid accidentally excluding RECURRING/CRON rows if `firedAt` is ever set for those | Common Pitfalls (Pitfall 5) | Low risk in Phase 7 since RECURRING/CRON never set `firedAt`, but creates a subtle long-term trap if Phase 9/10 sets `firedAt` for history. |
| A4 | SKIP_NEW response should be HTTP 202 with `accepted=false` (not 409) | Code Examples, Claude's Discretion | Minor: HTTP 409 is also a defensible choice. Consistency with existing 202 response code is the deciding factor. |
| A5 | `webhookUrl` validation using `^https?://.*` regex stays in `ScheduleValidators` (not route handler), making invalid URLs a rejected-before-persist case | Code Examples | Per D-10: "Invalid URL → 422". If the intent is also to persist with ERROR, this needs to move to the route handler too. Current assumption: only CRON gets the persist-with-ERROR treatment. |
| A6 | H2 `ALTER TABLE ... ADD COLUMN` without explicit NULL keyword creates nullable columns by default | Code Examples (V2 SQL) | If H2 treats `ADD COLUMN` as NOT NULL by default, the migration will fail on existing rows. Standard SQL and H2 default to NULL for added columns. |

---

## Open Questions (RESOLVED)

1. **`displayScheduled()` return type for SKIP_NEW / maxRuns**
   - What we know: D-05 says skipped fires must not count against `maxRuns`
   - What's unclear: Whether `displayScheduled()` should return `Boolean` or use another mechanism (e.g., exception, AtomicBoolean on the service)
   - Recommendation: Return `Boolean` from `displayScheduled()` — minimal diff, testable, consistent with existing Kotlin idioms
   - RESOLVED: `displayImmediate()` and `displayScheduled()` both return `Boolean`; callers skip `maxRuns` increment when `false` (07-02 Task 1)

2. **`firedAt` filter scope in `findAllActive()`**
   - What we know: D-14 says `AND firedAt IS NULL` in WHERE clause
   - What's unclear: Whether this applies to ALL trigger types or only ONESHOT
   - Recommendation: Scope the filter to ONESHOT only (see Pitfall 5 code example) to avoid unintended future breakage
   - RESOLVED: Filter scoped to `triggerType = 'ONESHOT' AND firedAt IS NULL` in `findAllActive()` (07-01 Task 3)

3. **CRON validation in `ScheduleValidators` vs. route handler**
   - What we know: D-16 says "validate at POST /api/v1/schedule" and persist ERROR row
   - What's clarified by research: The existing `ScheduleValidators.validateCron()` prevents the request from reaching the route handler; it must be removed from there
   - Recommendation: Remove `validateCron` case from `ScheduleValidators.validateSchedule()`, add the try/catch inside `ScheduleRoutes.kt` post handler directly
   - RESOLVED: `validateCron` case removed from `ScheduleValidators`; `try/catch` on `CronParser.parse()` added inside `ScheduleRoutes.kt` POST handler, persisting ERROR row before returning 422 (07-02 Task 3)

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Gradle 9.4.0 | Build | ✓ | 9.4.0 (wrapper) | — |
| JDK 25 | Kotlin compilation | ✓ | JDK 25 toolchain configured | — |
| H2 2.4.240 | Test DB + Pi default | ✓ (runtimeOnly + testRuntimeOnly) | 2.4.240 | — |
| cron-utils 9.2.1 | CRON parsing | ✓ (already in dependency graph) | 9.2.1 | — |
| Flyway 9.22.3 | Schema migration | ✗ (not yet in build.gradle.kts) | — | Must be added |

**Missing dependencies with no fallback:**
- Flyway: must be added to `ktor-libs.versions.toml` and `build.gradle.kts`

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 with FunSpec style + JUnit 5 runner |
| Config file | none (JUnit Platform auto-discovery) |
| Quick run command | `./gradlew test --tests "com.anjo.db.ScheduleRepositoryTest" --tests "com.anjo.service.SchedulerServiceTest" --tests "com.anjo.service.ConflictPolicyTest" --no-daemon` |
| Full suite command | `./gradlew test --no-daemon` |

**Baseline status: BUILD SUCCESSFUL** — all existing 21 test classes pass before Phase 7 changes.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCHED-01 | SKIP_NEW drops request when mutex is locked | unit | `./gradlew test --tests "com.anjo.service.ConflictPolicyTest" --no-daemon` | ✅ (extend) |
| SCHED-01 | SKIP_NEW returns accepted=false in TextRoutes | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest" --no-daemon` | ✅ (extend) |
| SCHED-01 | SKIP_NEW does not decrement maxRuns | unit | `./gradlew test --tests "com.anjo.service.SchedulerServiceTest" --no-daemon` | ✅ (extend) |
| SCHED-02 | ONESHOT with non-null firedAt excluded from findAllActive() | unit | `./gradlew test --tests "com.anjo.db.ScheduleRepositoryTest" --no-daemon` | ✅ (extend) |
| SCHED-02 | firedAt + status=DONE written atomically after fire | unit | `./gradlew test --tests "com.anjo.service.SchedulerServiceTest" --no-daemon` | ✅ (extend) |
| SCHED-03 | Invalid CRON persists row with status=ERROR | integration | `./gradlew test --tests "com.anjo.routing.ScheduleRoutesTest" --no-daemon` | ✅ (extend) |
| SCHED-03 | ERROR schedule not loaded by SchedulerService on start | unit | `./gradlew test --tests "com.anjo.service.SchedulerServiceTest" --no-daemon` | ✅ (extend) |
| SCHED-04 | webhookUrl and zoneId persisted and read back | unit | `./gradlew test --tests "com.anjo.db.ScheduleRepositoryTest" --no-daemon` | ✅ (extend) |
| SCHED-04 | Invalid webhookUrl returns 422 | integration | `./gradlew test --tests "com.anjo.routing.ScheduleRoutesTest" --no-daemon` | ✅ (extend) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.db.ScheduleRepositoryTest" --tests "com.anjo.service.SchedulerServiceTest" --tests "com.anjo.service.ConflictPolicyTest" --no-daemon`
- **Per wave merge:** `./gradlew test --no-daemon`
- **Phase gate:** Full suite green + JaCoCo ≥70% before `/gsd-verify-work`

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. All new test cases extend existing test files (no new files needed).

---

## Security Domain

> `security_enforcement` not set in config.json — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Not applicable — trusted home network, no auth |
| V3 Session Management | no | No session concept in this phase |
| V4 Access Control | no | No access control in this phase |
| V5 Input Validation | yes | `webhookUrl` format validation in `ScheduleValidators`; CRON validation in route handler |
| V6 Cryptography | no | No crypto in this phase |

### Known Threat Patterns for Ktor + H2

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via triggerValue or webhookUrl | Tampering | Exposed ORM parameterizes all queries — no string concatenation in SQL |
| SSRF via webhookUrl | Elevation | Phase 7 only stores the URL (no HTTP calls yet). Validation rejects non-http/https URLs. Phase 10 adds actual HTTP dispatch with allowlist consideration. |
| Flyway migration file injection | Tampering | Migration files are classpath resources compiled into the jar — not writable at runtime |

---

## Sources

### Primary (HIGH confidence)

- Maven Central search API — Flyway 9.22.3 and 11.8.2 confirmed with version count, publish dates [VERIFIED: Maven Central]
- github.com/flyway/flyway `flyway-core` source — `FluentConfiguration.dataSource()`, `baselineOnMigrate()`, `baselineVersion()` methods confirmed present in Flyway 11 main branch [VERIFIED: GitHub source]

### Secondary (MEDIUM confidence)

- Codebase grep of `SchedulerService.kt`, `ScreenDriver.kt`, `ScheduleValidators.kt`, `RequestValidationConfig.kt`, `DatabaseFactory.kt` — all existing patterns confirmed by direct file read [VERIFIED: codebase]
- Maven Central artifact listing — no `flyway-database-h2` separate module exists; H2 bundled in `flyway-core` [VERIFIED: Maven Central]

### Tertiary (LOW confidence)

- Flyway default classpath scan location `classpath:db/migration` [ASSUMED — from training knowledge; verify by running and checking Flyway startup log]
- H2 `ADD COLUMN` nullability default behavior [ASSUMED — standard SQL; confirm in H2 2.4 docs]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Flyway confirmed on Maven Central; all other libs already in project
- Architecture: HIGH — all integration points confirmed by reading actual source files
- Pitfalls: MEDIUM — implementation hazards derived from reading code; some edge cases (A2, A3) are ASSUMED

**Research date:** 2026-06-14
**Valid until:** 2026-07-14 (Flyway versions stable; Kotlin/Exposed/Ktor patch releases do not affect this phase)
