---
phase: 5
plan: "05-04"
subsystem: database
tags: [schedule, exposed, h2, repository, persistence]
requires: [05-01, 05-02, 05-03]
provides: [Schedule model, SchedulesTable, DatabaseFactory, ScheduleRepository]
affects: [di/DependencyInjection.kt, build.gradle.kts]
tech-stack:
  added: [exposed-core 0.61.0, exposed-jdbc 0.61.0, exposed-java-time 0.61.0, h2 2.3.232, HikariCP 6.3.0, cron-utils 9.2.1, kotlinx-coroutines-test 1.10.2]
  patterns: [Exposed Table DSL, newSuspendedTransaction, HikariCP pool, in-memory H2 for tests]
key-files:
  created:
    - src/main/kotlin/com/anjo/model/Schedule.kt
    - src/main/kotlin/com/anjo/db/SchedulesTable.kt
    - src/main/kotlin/com/anjo/db/DatabaseFactory.kt
    - src/main/kotlin/com/anjo/db/ScheduleRepository.kt
    - src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt
  modified:
    - build.gradle.kts
    - src/main/resources/application.yaml
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
key-decisions:
  - Used propertyOrNull with defaults for database config (same as ConfigLoader pattern) so testApplication works without YAML; testApplication uses MapApplicationConfig which doesn't load application.yaml
  - Fallback DB URL is jdbc:h2:mem:testdb so route tests remain hermetic without file system side effects
  - Used SchemaUtils.create (not deprecated createMissingTablesAndColumns) for Exposed 0.61 compatibility
requirements-completed: [REQ-SCHED-01]
duration: "25 min"
completed: "2026-05-28"
---

# Phase 5 Plan 05-04: Schedule Data Model + Exposed/H2 Database Setup Summary

Schedule persistence layer using Jetbrains Exposed ORM and H2 embedded database with full CRUD repository and integration tests.

**Duration:** ~25 min | **Tasks:** 7 | **Files:** 8

## What Was Built

- **`Schedule.kt`** — `@Serializable` data class with all D5-10 fields: id, text, triggerType (ONESHOT/RECURRING/CRON), triggerValue, effect (SCROLL/BLINK/REVERSE/FADE), priority, maxRuns, expiresAt, createdAt, status (ACTIVE/PAUSED/EXPIRED/DONE) + `isExpired()` helper
- **`SchedulesTable.kt`** — Exposed `Table("schedules")` object with all varchar/integer columns and primary key
- **`DatabaseFactory.kt`** — `object DatabaseFactory { fun init(...) }` using HikariCP pool + `Database.connect()` + `SchemaUtils.create(SchedulesTable)`
- **`ScheduleRepository.kt`** — full CRUD: `findAll`, `findById`, `findAllActive`, `insert`, `update`, `updateStatus`, `delete` — all using `newSuspendedTransaction`
- **`ScheduleRepositoryTest.kt`** — 5 Kotest FunSpec tests using H2 in-memory DB; all pass
- **Gradle**: added Exposed, H2, HikariCP, cron-utils, kotlinx-coroutines-test deps
- **application.yaml**: added `database:` section with H2 file URL, driver, poolSize
- **DependencyInjection.kt**: added `DatabaseFactory.init()` call and `ScheduleRepository` DI binding

## Deviations from Plan

**[Rule 1 - Bug Fix] `propertyOrNull` instead of `.property()` for database config**
Found during: Task 05-04-06 | Issue: Ktor `testApplication` uses `MapApplicationConfig` (in-memory config), NOT the YAML file. Using `.property()` caused `ApplicationConfigurationException` in all 15 route tests that boot the full module. | Fix: Changed to `propertyOrNull` with sensible defaults matching the ConfigLoader pattern throughout the file. | Commit: 9c84f31

**Total deviations:** 1 auto-fixed. **Impact:** None — all 58 existing tests continue to pass; JaCoCo gate passes.

## Acceptance Criteria Verification

| Criterion | Result |
|-----------|--------|
| `Schedule.kt` exists with all D5-10 fields | ✅ PASS |
| `Effect`, `ScheduleStatus`, `TriggerType` enums defined | ✅ PASS |
| `SchedulesTable.kt` with `override val primaryKey` | ✅ PASS |
| `ScheduleRepository` uses `newSuspendedTransaction` exclusively | ✅ PASS |
| `DatabaseFactory.init(` in DependencyInjection.kt | ✅ PASS |
| `./gradlew compileKotlin` exits 0 | ✅ PASS |
| `./gradlew test --tests "com.anjo.db.ScheduleRepositoryTest"` exits 0 | ✅ PASS (coverage gate skipped for subset) |
| `./gradlew test` exits 0 (full suite) | ✅ PASS — 58/58 tests pass, JaCoCo ≥70% |

## Self-Check: PASSED

