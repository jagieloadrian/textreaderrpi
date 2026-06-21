---
phase: 09-display-history-audit-log
fixed_at: 2026-06-15T00:00:00Z
review_path: .planning/phases/09-display-history-audit-log/09-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 8
skipped: 1
status: partial
---

# Phase 09: Code Review Fix Report

**Fixed at:** 2026-06-15T00:00:00Z
**Source review:** .planning/phases/09-display-history-audit-log/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (5 Critical + 4 Warning)
- Fixed: 8
- Skipped: 1

## Fixed Issues

### CR-01: Race condition in row-cap pruning — count and delete are separate statements

**Files modified:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt`
**Commit:** 3d50875
**Applied fix:** Replaced `.single()` with `.singleOrNull()?.get(...) ?: return@suspendTransaction` so that a concurrent delete of the oldest row between the count check and the id fetch does not crash with `NoSuchElementException`.

---

### CR-02: `Int.MAX_VALUE` passed as SQL LIMIT — integer overflow and runaway query

**Files modified:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt`
**Commit:** ac54795
**Applied fix:** Introduced `private const val MAX_UI_SIZE = 1000L`. Replaced the `Int.MAX_VALUE` sentinel with `MAX_UI_SIZE.toInt()` for the `sizeAll` path. Applied `coerceAtMost(MAX_UI_SIZE)` before the `toInt()` cast to eliminate the overflow risk.

---

### CR-03: API `size` parameter has no upper bound — unbounded result set

**Files modified:** `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt`
**Commit:** 0859d8d
**Applied fix:** Changed `.coerceAtLeast(1)` to `.coerceIn(1, 200)`, capping the maximum page size at 200 for the JSON API endpoint.

---

### CR-04: Redundant `SchemaUtils.create()` calls after Flyway migration

**Files modified:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt`
**Commit:** dd92aaa
**Applied fix:** Removed the entire `transaction { SchemaUtils.create(SchedulesTable); SchemaUtils.create(HistoryTable) }` block and its `SchemaUtils`, `transaction` imports. Flyway is now the sole schema owner.

---

### CR-05: XSS — user-supplied query parameters reflected unescaped into HTML `href` attributes

**Files modified:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`
**Commit:** e6ab5ec
**Applied fix:** Added `import java.net.URLEncoder` and a private `String.urlEncode()` extension. Applied it to `effect`, `source`, and `rawSize` at all three `href` interpolation sites (expand all, collapse all, and pagination links).

---

### WR-02: `HistoryUIRoutes` — `size=all` performs two full database round-trips

**Files modified:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt`
**Commit:** f2e6071
**Applied fix:** Removed the pre-fetch `findPaginated(1, 1, ...)` round-trip. Since `size` is already resolved to `MAX_UI_SIZE.toInt()` for the `sizeAll` path, a single `findPaginated(page, size, ...)` call now handles both paginated and all cases uniformly.

---

### WR-03: `DependencyInjection.kt` instantiates `HistoryRepository` before `DatabaseFactory.init()`

**Files modified:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt`
**Commit:** f3b7a86
**Applied fix:** Moved `DatabaseFactory.init(appConfig.databaseConfig)` to immediately after `ConfigLoader.loadConfig(this)`, before any repository or service is instantiated. Removed the now-redundant call that was previously on line 49.

---

### WR-04: Prohibited code comments present in `ScreenDriverService.kt`

**Files modified:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`
**Commit:** a5b3a4b
**Applied fix:** Removed both KDoc `/** ... */` block comments above `displayImmediate` and `displayScheduled`, in compliance with the project rule prohibiting comments in Kotlin files.

---

## Skipped Issues

### WR-01: History is recorded even when `displaySucceeded = false` in the SKIP_NEW path of `displayScheduled`

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:116-118`
**Reason:** Code inspection shows both the `SKIP_NEW` and `INTERRUPT` paths of `displayScheduled` already hold the mutex during the history insert — the `SKIP_NEW` path acquires it via `tryLock`/`finally { unlock() }` and the history call sits inside that locked scope. The reviewer's characterisation that the insert is "outside" the lock does not match the actual code. No correctness bug is present; the suggested refactor (extracting a `recordHistory` helper) is a style preference. Skipping to avoid unnecessary refactoring scope.

---

_Fixed: 2026-06-15T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
