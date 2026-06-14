---
phase: 07-scheduler-schema-stabilisation
plan: "01"
subsystem: database/model
tags: [flyway, schema-migration, exposed, model, repository]
dependency_graph:
  requires: []
  provides:
    - ConflictPolicy enum (INTERRUPT, SKIP_NEW) @Serializable
    - ScheduleStatus.ERROR
    - Schedule.conflictPolicy/firedAt/webhookUrl/zoneId fields
    - TextRequest.conflictPolicy field
    - SchedulesTable: conflict_policy, fired_at, webhook_url, zone_id columns
    - ScheduleRepository.updateFiredAtAndDone()
    - ScheduleRepository.findAllActive() ONESHOT firedAt filter
    - Flyway 9.22.3 migration layer
    - V1 + V2 SQL migration files
  affects:
    - src/main/kotlin/com/anjo/db/ (SchedulesTable, ScheduleRepository, DatabaseFactory)
    - src/main/kotlin/com/anjo/model/ (Schedule, TextRequest, ConflictPolicy)
tech_stack:
  added:
    - org.flywaydb:flyway-core 9.22.3 (schema migration lifecycle)
  patterns:
    - Flyway baselineOnMigrate=true pattern for existing-install DB upgrades
    - Single suspendTransaction{} for atomic firedAt+status=DONE update
    - ONESHOT firedAt filter scoped to triggerType=ONESHOT only (Pitfall 5 guidance)
key_files:
  created:
    - src/main/kotlin/com/anjo/model/ConflictPolicy.kt
    - src/main/resources/db/migration/V1__initial_schema.sql
    - src/main/resources/db/migration/V2__add_scheduler_columns.sql
  modified:
    - src/main/kotlin/com/anjo/model/Schedule.kt
    - src/main/kotlin/com/anjo/model/TextRequest.kt
    - src/main/kotlin/com/anjo/db/SchedulesTable.kt
    - src/main/kotlin/com/anjo/db/ScheduleRepository.kt
    - src/main/kotlin/com/anjo/db/DatabaseFactory.kt
    - gradle/ktor-libs.versions.toml
    - build.gradle.kts
decisions:
  - "Flyway 9.22.3 chosen over 11.x for simpler community licensing; API identical for this plan's usage"
  - "baselineOnMigrate(true) + baselineVersion('1') handles existing Pi installs without re-running V1 DDL"
  - "ONESHOT firedAt filter scoped to triggerType=ONESHOT only (not bare AND firedAt IS NULL) to avoid affecting RECURRING/CRON rows"
  - "updateFiredAtAndDone() uses a single suspendTransaction{} for crash-safe atomic firedAt+status update"
  - "V1 SQL uses CREATE TABLE IF NOT EXISTS to be idempotent on fresh installs"
metrics:
  duration_seconds: 348
  completed: "2026-06-14"
  tasks_completed: 3
  tasks_total: 3
  files_created: 3
  files_modified: 7
---

# Phase 7 Plan 01: Schema and Data-Layer Foundation Summary

**One-liner:** Flyway 9.22.3 migration layer + ConflictPolicy enum + 4 new schedule columns + atomic firedAt update + ONESHOT restart guard in findAllActive().

## What Was Built

Added the complete model and database foundation required by Phase 7 scheduler fixes. Three atomic commits deliver:

1. **ConflictPolicy enum** (`@Serializable`, INTERRUPT/SKIP_NEW), `ScheduleStatus.ERROR`, four nullable fields on `Schedule` data class, and `conflictPolicy` with INTERRUPT default on `TextRequest`.

2. **Flyway 9.22.3** wired into `DatabaseFactory.init()` before `SchemaUtils.create()`, with `baselineOnMigrate(true)` and `baselineVersion("1")` for zero-downtime Pi upgrades. Two SQL migration files: V1 documents the baseline 10-column schema; V2 adds the 4 new columns via `ALTER TABLE`.

3. **SchedulesTable** extended with `conflict_policy`, `fired_at`, `webhook_url`, `zone_id` columns. **ScheduleRepository** updated to write/read all four columns, adds atomic `updateFiredAtAndDone(id, firedAt)` in a single `suspendTransaction{}`, and updates `findAllActive()` to exclude ONESHOT rows where `firedAt IS NOT NULL` (crash-restart guard).

## Tasks

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | ConflictPolicy enum, model fields, ScheduleStatus.ERROR | 9edfbb7 | DONE |
| 2 | Flyway dependency, migration SQL, DatabaseFactory wiring | 3dd0ea6 | DONE |
| 3 | SchedulesTable columns + ScheduleRepository (new cols, updateFiredAtAndDone, ONESHOT filter) | b1e78d2 | DONE |

## Verification

- `./gradlew compileKotlin --no-daemon` — BUILD SUCCESSFUL
- `./gradlew compileTestKotlin --no-daemon` — BUILD SUCCESSFUL
- `./gradlew test --no-daemon` — BUILD SUCCESSFUL (full test suite, JaCoCo ≥70%)
- `grep -rl "conflict_policy" src/main/resources/db/migration/V2__add_scheduler_columns.sql` — non-empty

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all new columns are wired through all layers (model → table → repository). No placeholder values introduced.

## Threat Flags

No new trust-boundary surface beyond what the plan's threat model already covers. `webhookUrl` and `zoneId` are persisted via Exposed parameterized writes (T-07-01 mitigation satisfied). Flyway migration files are classpath resources compiled into the jar (T-07-02 accepted). No new network endpoints, auth paths, or schema changes outside plan scope.

## Self-Check: PASSED

- ConflictPolicy.kt exists: FOUND
- V1__initial_schema.sql exists: FOUND
- V2__add_scheduler_columns.sql exists: FOUND
- Commit 9edfbb7 exists: FOUND
- Commit 3dd0ea6 exists: FOUND
- Commit b1e78d2 exists: FOUND
