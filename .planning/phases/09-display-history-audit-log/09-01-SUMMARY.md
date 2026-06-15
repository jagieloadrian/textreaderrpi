---
phase: 09-display-history-audit-log
plan: 01
subsystem: database
tags: [kotlin, exposed, flyway, h2, kotest, history, pagination]

requires:
  - phase: 08-refactor-dead-code
    provides: Clean DatabaseFactory.init() and SchedulesTable pattern for SchemaUtils wiring

provides:
  - HistoryRecord and HistoryPageResponse @Serializable data classes (com.anjo.model)
  - HistoryTable Exposed object with 7 columns for display_history table (com.anjo.db)
  - HistoryRepository with insert (1000-row cap) and findPaginated (effect/source filters) (com.anjo.db)
  - V3__add_history_table.sql Flyway migration for display_history schema
  - DatabaseFactory wired to create display_history on startup via SchemaUtils
  - HistoryRepositoryTest with 5 H2 in-memory tests covering all D-24 cases

affects:
  - 09-02 (history recording integration into ScreenDriverService)
  - 09-03 (history API routes and UI page)

tech-stack:
  added: []
  patterns:
    - "HistoryTable column name 'displaySource' used for Kotlin property (maps to DB column 'source') to avoid ColumnSet.source supertype conflict"
    - "Exposed 1.3.0 limit(count).offset(start) two-call API (not single limit(n, offset) call)"
    - "andWhere extension from org.jetbrains.exposed.v1.jdbc for optional second filter without double-where error"
    - "MAX_ROWS=1000L as Long companion constant; count() returns Long so comparison works without cast"

key-files:
  created:
    - src/main/kotlin/com/anjo/model/HistoryRecord.kt
    - src/main/kotlin/com/anjo/db/HistoryTable.kt
    - src/main/kotlin/com/anjo/db/HistoryRepository.kt
    - src/main/resources/db/migration/V3__add_history_table.sql
    - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
  modified:
    - src/main/kotlin/com/anjo/db/DatabaseFactory.kt

key-decisions:
  - "HistoryTable.displaySource (not .source) avoids hiding ColumnSet.source member in Exposed Table supertype"
  - "effect stored as String in HistoryRecord (not Effect enum) to avoid valueOf() call on round-trip per D-06"
  - "MAX_ROWS declared as Long (1000L) matching selectAll().count() return type; avoids silent Int/Long comparison widening"
  - "Exposed 1.3.0 uses separate .limit(n).offset(start: Long) calls, not .limit(size, offset=...) as documented in plan"

patterns-established:
  - "HistoryTable property naming: use descriptive alias when column name collides with Exposed ColumnSet members"
  - "findPaginated filter chaining: first filter uses .where{}, subsequent filters use .andWhere{} from org.jetbrains.exposed.v1.jdbc"

requirements-completed: [HIST-01, HIST-02]

duration: 5min
completed: 2026-06-15
---

# Phase 09 Plan 01: HistoryRecord model + HistoryTable + HistoryRepository + H2 tests Summary

**display_history persistence layer with Exposed HistoryTable, Flyway V3 migration, 1000-row pruning insert, and paginated/filtered query — all covered by 5 H2 Kotest tests**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-15T14:07:45Z
- **Completed:** 2026-06-15T14:13:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- HistoryRecord @Serializable data class with effect as String (not enum) for clean JSON round-trip
- HistoryTable Exposed object with 7 columns and V3 Flyway migration; DatabaseFactory wired at startup
- HistoryRepository.insert() with atomic 1000-row cap (count+delete+insert in single suspendTransaction)
- HistoryRepository.findPaginated() with newest-first order, optional effect/source filters, Long offset (T-09-04)
- 5 H2 in-memory Kotest tests passing: round-trip, pruning at 1001, page slicing, effect filter, source filter

## Task Commits

Each task was committed atomically:

1. **Task 1 - HistoryRecord model + HistoryTable + V3 migration + DatabaseFactory wiring (RED structure)** - `0b93862` (feat)
2. **Task 2 - TDD RED: HistoryRepositoryTest (failing)** - `7ca7568` (test)
3. **Task 2 - TDD GREEN: HistoryRepository implementation** - `43c006a` (feat)

_Note: TDD tasks have multiple commits (test RED → feat GREEN)_

## Files Created/Modified

- `src/main/kotlin/com/anjo/model/HistoryRecord.kt` - @Serializable HistoryRecord + HistoryPageResponse data classes
- `src/main/kotlin/com/anjo/db/HistoryTable.kt` - Exposed Table object for display_history (7 columns)
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` - insert with 1000-row cap, findPaginated with filters
- `src/main/resources/db/migration/V3__add_history_table.sql` - Flyway V3 CREATE TABLE IF NOT EXISTS display_history
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` - Added SchemaUtils.create(HistoryTable) after SchedulesTable
- `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` - 5 H2 in-memory Kotest FunSpec tests

## Decisions Made

- **displaySource not source** in HistoryTable: Exposed's `Table` inherits from `ColumnSet` which has a `source` property; using the same name triggers a Kotlin "hides member of supertype" compile error requiring `override`. Renaming the Kotlin property to `displaySource` while keeping DB column as `source` is the cleanest fix.
- **Exposed 1.3.0 limit/offset API**: The plan described `.limit(size, offset = ...)` (Exposed 1.x style) but the actual 1.3.0 API uses separate `.limit(count: Int)` and `.offset(start: Long)` calls (Rule 1 auto-fix on API deviation).
- **MAX_ROWS as Long**: `selectAll().count()` returns Long in Exposed 1.3.0; declaring `MAX_ROWS = 1000L` avoids implicit widening comparison warning.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] HistoryTable.source property renamed to displaySource**
- **Found during:** Task 1 (HistoryTable creation)
- **Issue:** `val source = varchar("source", 16)` hides `ColumnSet.source` supertype member; compiler error
- **Fix:** Renamed Kotlin property to `displaySource`; DB column name remains `"source"`; all repository references updated accordingly
- **Files modified:** src/main/kotlin/com/anjo/db/HistoryTable.kt, src/main/kotlin/com/anjo/db/HistoryRepository.kt
- **Verification:** `./gradlew compileKotlin` exits 0
- **Committed in:** 0b93862 (Task 1 commit)

**2. [Rule 1 - Bug] Exposed 1.3.0 uses separate limit().offset() API**
- **Found during:** Task 2 (HistoryRepository findPaginated)
- **Issue:** Plan specified `.limit(size, offset = ...)` which does not exist in Exposed 1.3.0; the API is `.limit(count: Int)` + `.offset(start: Long)` as separate chained calls
- **Fix:** Used `.limit(size).offset(((page-1).toLong() * size))` instead
- **Files modified:** src/main/kotlin/com/anjo/db/HistoryRepository.kt
- **Verification:** Tests pass with correct pagination slices
- **Committed in:** 43c006a (Task 2 GREEN commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs — API/naming mismatches)
**Impact on plan:** Both fixes necessary for compilation and correct behavior. No scope creep.

## Issues Encountered

- `org.jetbrains.exposed.v1.core.*` wildcard import needed to bring `eq` operator into scope for WHERE predicates (not imported via jdbc package alone)
- JaCoCo coverage gate fires when running tests targeting a single class (only 4% line coverage); excluded via `-x jacocoTestCoverageVerification` per existing test pattern

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 09-01 artifacts (HistoryTable, HistoryRepository, HistoryRecord) are ready for consumption by:
  - 09-02: ScreenDriverService integration (call `historyRepository.insert()` after display)
  - 09-03: REST API route `GET /api/v1/history` and HTML UI page
- No blockers; display_history schema defined in both Flyway (V3) and SchemaUtils (DatabaseFactory)

---
*Phase: 09-display-history-audit-log*
*Completed: 2026-06-15*
