---
phase: 14-history-enhancements
plan: "01"
subsystem: history-data-layer
tags: [history, search, csv-export, HistoryFilter, HistoryValidators, kotlin-csv]
dependency_graph:
  requires: []
  provides:
    - com.anjo.model.HistoryFilter
    - com.anjo.validation.HistoryValidators.sanitizeSearchTerm
    - com.anjo.db.HistoryRepository.findPaginated(filter, page, size)
    - com.anjo.db.HistoryRepository.findAll(filter)
    - com.anjo.service.HistoryService.findPaginated(filter, page, size)
    - com.anjo.service.HistoryService.exportCsv(filter)
  affects:
    - com.anjo.routing.HistoryRoutes
    - com.anjo.routing.ui.HistoryUIRoutes
    - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
tech_stack:
  added:
    - com.jsoizo:kotlin-csv-jvm:1.10.0 (RFC 4180 CSV generation)
  patterns:
    - Exposed DSL accumulator pattern for filter conditions (buildList + reduce)
    - HistoryFilter data class as shared parameter type across repository/service layers
    - sanitizer-in-validators pattern (mirrors RequestValidators)
key_files:
  created:
    - src/main/kotlin/com/anjo/model/HistoryFilter.kt
    - src/main/kotlin/com/anjo/validation/HistoryValidators.kt
    - src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt
  modified:
    - gradle/ktor-libs.versions.toml
    - build.gradle.kts
    - src/main/kotlin/com/anjo/db/HistoryRepository.kt
    - src/main/kotlin/com/anjo/service/HistoryService.kt
    - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
    - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
    - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
    - src/test/kotlin/com/anjo/service/HistoryServiceTest.kt
    - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
decisions:
  - "D-19: HistoryFilter data class in com.anjo.model replaces 5-param findPaginated signature"
  - "D-18: HistoryValidators.sanitizeSearchTerm strips % and _ (LIKE wildcard injection mitigation)"
  - "D-20: Exposed lowerCase() like pattern for case-insensitive text search"
  - "D-12: HistoryService.exportCsv uses kotlin-csv-jvm 1.10.0 writeAllAsString"
  - "Accumulator pattern chosen over if/else chain for cleaner filter composition"
metrics:
  duration: "6m 30s"
  completed: "2026-06-22T07:59:38Z"
  tasks_completed: 2
  files_changed: 9
status: complete
---

# Phase 14 Plan 01: History Data Layer Foundation Summary

Keystone data-and-logic plan that introduces `HistoryFilter`, `HistoryValidators.sanitizeSearchTerm`, the `kotlin-csv-jvm` dependency, refactored `findPaginated(filter, page, size)` with LIKE search, new `findAll(filter)` for export, and `HistoryService.exportCsv(filter)` producing RFC 4180 CSV. All existing callers updated to new signature; Plans 02 and 03 wire these into the API and UI.

## What Was Built

### New Files
- **`HistoryFilter.kt`** — `data class HistoryFilter(effect, source, zone, search)` in `com.anjo.model`. No `@Serializable` (internal type, never JSON-serialized). Per D-19.
- **`HistoryValidators.kt`** — `object HistoryValidators { fun sanitizeSearchTerm(input: String): String }` in `com.anjo.validation`. Strips `%` and `_` to prevent LIKE wildcard injection. Per D-18. Mirrors `RequestValidators` structure.
- **`HistoryValidatorsTest.kt`** — 5 Kotest FunSpec unit tests covering all sanitizer behaviors.

### Modified Files
- **`ktor-libs.versions.toml`** — Added `kotlin-csv = "1.10.0"` under `[versions]` and `kotlin-csv-jvm = { module = "com.jsoizo:kotlin-csv-jvm", version.ref = "kotlin-csv" }` under `[libraries]`.
- **`build.gradle.kts`** — Added `implementation(ktorLibs.kotlin.csv.jvm)` in dependencies block.
- **`HistoryRepository.kt`** — Replaced 5-param `findPaginated` with `findPaginated(filter: HistoryFilter, page: Int, size: Int)` using accumulator pattern (`buildList {}` + `reduce { acc, op -> acc and op }`). Added `findAll(filter)` (same accumulator, no LIMIT/OFFSET). Both methods include `lowerCase() like` clause for case-insensitive text search.
- **`HistoryService.kt`** — Updated `findPaginated` to accept `HistoryFilter`. Added `suspend fun exportCsv(filter: HistoryFilter): String` using `csvWriter().writeAllAsString()` with D-10 header row.
- **`HistoryRoutes.kt` + `HistoryUIRoutes.kt`** — Updated to build `HistoryFilter` from extracted params and pass to new `findPaginated(filter, page, size)`.
- **`HistoryRepositoryTest.kt`** — All `findPaginated` call sites migrated to `HistoryFilter`. Added 2 new search tests.
- **`HistoryServiceTest.kt`** — All `findPaginated` call sites migrated. Added 3 exportCsv tests.
- **`HistoryRecordingTest.kt`** — `findPaginated` call sites migrated (Rule 3 auto-fix).

## TDD Gate Compliance

Both tasks followed RED/GREEN/REFACTOR:

| Task | RED commit | GREEN commit |
|------|-----------|-------------|
| Task 1: HistoryValidators | `19b288e` (compile fails — HistoryValidators missing) | `42e3804` (5 tests pass) |
| Task 2: Repository/Service | `2e8ffc8` (compile fails — old signature mismatch) | `b1fcc0d` (14 tests pass) |

No REFACTOR commits needed — implementations were clean.

## Test Results

| Test Suite | Tests | Passed | Failed |
|-----------|-------|--------|--------|
| HistoryValidatorsTest | 5 | 5 | 0 |
| HistoryRepositoryTest | 9 | 9 | 0 |
| HistoryServiceTest | 5 | 5 | 0 |
| HistoryRoutesTest | 4 | 4 | 0 |
| HistoryUIRoutesTest | 4 | 4 | 0 |
| HistoryRecordingTest | 4 | 4 | 0 |
| **Total** | **31** | **31** | **0** |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated HistoryRecordingTest call sites**
- **Found during:** Task 2 GREEN phase compile
- **Issue:** `HistoryRecordingTest.kt` (not listed in plan's files) had `findPaginated(1, 50)` calls using the old 5-param signature
- **Fix:** Added `HistoryFilter` import; replaced 3 call sites with `findPaginated(HistoryFilter(null, null, null, null), 1, 50)`
- **Files modified:** `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt`
- **Commit:** `b1fcc0d`

**2. [Rule 3 - Blocking] Updated HistoryRoutes + HistoryUIRoutes call sites**
- **Found during:** Task 2 GREEN phase compile
- **Issue:** Both routing files used old `findPaginated(page, size, effect, source, zone)` signature
- **Fix:** Introduced `HistoryFilter(effect, source, zone, search=null)` and updated call sites
- **Files modified:** `HistoryRoutes.kt`, `HistoryUIRoutes.kt`
- **Commit:** `b1fcc0d`

## Threat Model Coverage

| Threat | Status |
|--------|--------|
| T-14-01: LIKE wildcard injection | Mitigated — `sanitizeSearchTerm` strips `%` and `_`; tests verify this (Behaviors 1-3 in HistoryValidatorsTest) |
| T-14-02: kotlin-csv-jvm package legitimacy | Accepted — pinned to 1.10.0, Maven Central, RESEARCH.md verified |
| T-14-03: findAll DoS (unbounded query) | Accepted — DB capped at 1000 rows by insert guard |

## Known Stubs

None — all implementations are fully wired. `HistoryFilter.search` flows correctly through `findPaginated` and `findAll` to the LIKE clause.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced in this plan.

## Self-Check: PASSED

Files verified exist:
- `src/main/kotlin/com/anjo/model/HistoryFilter.kt`
- `src/main/kotlin/com/anjo/validation/HistoryValidators.kt`
- `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt`
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` (modified)
- `src/main/kotlin/com/anjo/service/HistoryService.kt` (modified)

Commits verified:
- `19b288e` test(14-01): add failing test for HistoryValidators sanitizeSearchTerm
- `42e3804` feat(14-01): add kotlin-csv dependency, HistoryFilter model, and HistoryValidators sanitizer
- `2e8ffc8` test(14-01): add failing tests for HistoryFilter signature and exportCsv behaviors
- `b1fcc0d` feat(14-01): refactor HistoryRepository+Service to HistoryFilter, add findAll and exportCsv
