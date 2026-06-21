# Phase 7: Scheduler Schema Stabilisation - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend `SchedulesTable` with all columns needed by v1.1 features in one Flyway migration (`conflictPolicy`, `firedAt`, `webhookUrl`, `zoneId`), and fix three scheduler edge-case bugs: ONESHOT restart re-fire (SCHED-02), CRON spin-loop (SCHED-03), and SKIP_NEW conflict policy (SCHED-01). No scheduler rewrite — SchedulerService is already coroutine-based; Flaxoos stays for HTTP rate limiting.

</domain>

<decisions>
## Implementation Decisions

### ConflictPolicy (SCHED-01)

- **D-01:** `conflictPolicy: ConflictPolicy = INTERRUPT` added as a field on `TextRequest` (ad-hoc POST /api/v1/text) — caller decides per-request, consistent with how `effect` is already per-request.
- **D-02:** `conflictPolicy` also added as a column on `SchedulesTable` (nullable, default `INTERRUPT`) so scheduled displays can declare their own conflict behavior.
- **D-03:** Scope is both ad-hoc POST /api/v1/text and scheduled displays.
- **D-04:** When `SKIP_NEW` fires and display is busy (`displayMutex.isLocked`): busy detection uses the existing mutex from `ScreenDriverService`. Claude decides HTTP response shape (200 with `accepted:false` consistent with `TextResponse`, or 409 — whichever fits existing API conventions better).
- **D-05:** Skipped fires do NOT count against `maxRuns`. A schedule with `maxRuns=3` and `conflictPolicy=SKIP_NEW` must display successfully 3 times, not be silently skipped to exhaustion.

### Schema Migration — Flyway

- **D-06:** Flyway (`org.flywaydb:flyway-core`) is the migration tool. Replaces the prior no-Flyway constraint — user explicitly approved this.
- **D-07:** `DatabaseFactory.init()` calls `Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate()` before Exposed schema operations. `SchemaUtils.create(SchedulesTable)` stays as a safety net for table existence.
- **D-08:** `baselineOnMigrate=true` with `baselineVersion("1")` handles existing Pi installs that have a `schedules` table but no Flyway history. First run baselines at V1, then applies V2+.
- **D-09:** Migration files in `src/main/resources/db/migration/`: `V1__initial_schema.sql` (baseline — documents existing schema), `V2__add_scheduler_columns.sql` (new columns).

### New Columns (SCHED-04)

- **D-10:** `webhookUrl varchar(512) NULL` — URL format validated at `POST /api/v1/schedule` insert time (basic URL pattern check, not DNS resolution). Invalid URL → 422.
- **D-11:** `zoneId varchar(64) NULL` — nullable varchar, no validation in Phase 7. Phase 11 adds behavior.
- **D-12:** `conflictPolicy varchar(16) NULL DEFAULT 'INTERRUPT'` on `SchedulesTable`.
- **D-13:** `firedAt varchar(32) NULL` on `SchedulesTable` — ISO timestamp string, consistent with existing `createdAt`/`expiresAt` pattern.

### ONESHOT Restart Guard (SCHED-02)

- **D-14:** `firedAt` null-check lives in `findAllActive()` query — add `AND firedAt IS NULL` to the WHERE clause. Schedules that already fired before a crash are excluded from the active list at startup. No runtime guard needed in `launchOneShot()`.
- **D-15:** `launchOneShot()` sets `firedAt` atomically with `status=DONE` in a single `suspendTransaction {}` block after firing.

### CRON Validation (SCHED-03)

- **D-16:** Validate CRON expression at `POST /api/v1/schedule` using `CronParser` (already imported in `SchedulerService`). If invalid: persist the row with `status=ERROR` and return HTTP 422 to the caller. Scheduler never loads ERROR rows (`findAllActive()` already filters by `status=ACTIVE`).
- **D-17:** Add `ERROR` to `ScheduleStatus` enum. `findAllActive()` query already uses string comparison `status eq "ACTIVE"` — no query change needed.

### Claude's Discretion

- HTTP response for SKIP_NEW busy path: either 200 with `{accepted:false, message:"Display busy, request skipped"}` (consistent with `TextResponse` shape and current 202 Accepted pattern) or 409 Conflict. Claude picks based on what fits existing API conventions better.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scheduler and Schema
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` — current table definition (10 columns); new migration adds 4 more
- `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` — all DB operations; must be updated for new columns and `firedAt` null-check in `findAllActive()`
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` — where Flyway init call goes; currently uses `SchemaUtils.create(SchedulesTable)` only
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — `launchOneShot()` (firedAt fix), `launchCron()` (remove; validation moves to route)
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt` — `displayImmediate()` and `displayScheduled()` (add SKIP_NEW check via `displayMutex.isLocked`); `displayMutex` and `pendingDisplayType` patterns to follow

### Models and Validation
- `src/main/kotlin/com/anjo/model/Schedule.kt` — `Schedule` data class and `ScheduleStatus` enum (add `ERROR`); add `conflictPolicy`, `firedAt`, `webhookUrl`, `zoneId` fields
- `src/main/kotlin/com/anjo/model/TextRequest.kt` — add `conflictPolicy: ConflictPolicy = INTERRUPT`
- `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` — add CRON validation at POST handler
- `src/main/kotlin/com/anjo/validation/ScheduleValidators.kt` — add URL format validator for `webhookUrl`

### Test Infrastructure
- `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` — existing test class for conflict policy (review before adding new tests)
- `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` — extend for new columns and `findAllActive()` firedAt filter
- `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — extend for ONESHOT firedAt and CRON ERROR behavior

### Requirements
- `.planning/REQUIREMENTS.md` §Scheduler Fixes — SCHED-01 through SCHED-04 (4 requirements, all must be satisfied)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CronParser` (already imported in `SchedulerService`) — move/reuse for validation at route level; import `com.cronutils.parser.CronParser` in `ScheduleRoutes.kt`
- `displayMutex: Mutex` in `ScreenDriverService` — `isLocked` property gives busy detection for SKIP_NEW; already used in `queueDisplaySwitch()`
- `ScheduleStatus` enum in `Schedule.kt` — add `ERROR`; existing `ACTIVE/PAUSED/EXPIRED/DONE` values unchanged
- `TextResponse` data class — shape for SKIP_NEW response (`accepted: Boolean`, `message: String`)

### Established Patterns
- All timestamps stored as `varchar(32)` ISO strings (see `createdAt`, `expiresAt`) — `firedAt` follows same pattern
- `suspendTransaction {}` for all DB ops — `firedAt` + `status=DONE` update must be a single transaction
- `ScheduleRepository.updateStatus()` pattern — extend to `updateFiredAt(id, firedAt, status)` for atomic set
- Exposed `SchemaUtils.create()` stays in `DatabaseFactory` as safety net after Flyway runs

### Integration Points
- `DatabaseFactory.init()` → add Flyway call before `SchemaUtils.create()`
- `ScheduleRepository.findAllActive()` → add `and (SchedulesTable.firedAt eq null)` to WHERE
- `ScheduleRoutes.kt` POST handler → add CRON parse attempt; on failure persist `status=ERROR`, return 422
- `ScreenDriverService.displayImmediate()` and `displayScheduled()` → check `displayMutex.isLocked` for SKIP_NEW

</code_context>

<specifics>
## Specific Ideas

- Flyway `baselineOnMigrate(true)` + `baselineVersion("1")` is the exact config needed for existing Pi installs
- `V1__initial_schema.sql` documents the existing `schedules` table (serves as documentation baseline, not run on fresh installs — Flyway skips it after baselining)
- `V2__add_scheduler_columns.sql`: `ALTER TABLE schedules ADD COLUMN conflict_policy VARCHAR(16) DEFAULT 'INTERRUPT'; ALTER TABLE schedules ADD COLUMN fired_at VARCHAR(32); ALTER TABLE schedules ADD COLUMN webhook_url VARCHAR(512); ALTER TABLE schedules ADD COLUMN zone_id VARCHAR(64);`
- `webhookUrl` validation: basic URI pattern (`^https?://.*`) at the Ktor `RequestValidation` level, consistent with how other validations work in `ScheduleValidators.kt`

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 7-scheduler-schema-stabilisation*
*Context gathered: 2026-06-12*
