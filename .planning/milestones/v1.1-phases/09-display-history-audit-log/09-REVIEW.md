---
phase: 09-display-history-audit-log
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - src/main/kotlin/com/anjo/model/HistoryRecord.kt
  - src/main/kotlin/com/anjo/db/HistoryTable.kt
  - src/main/kotlin/com/anjo/db/HistoryRepository.kt
  - src/main/kotlin/com/anjo/db/DatabaseFactory.kt
  - src/main/resources/db/migration/V3__add_history_table.sql
  - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/service/SchedulerService.kt
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/test/kotlin/com/anjo/ApplicationTest.kt
  - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
  - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
  - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
  - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
  - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
  - src/main/kotlin/com/anjo/web/templates/BaseLayout.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
findings:
  critical: 5
  warning: 4
  info: 3
  total: 12
status: issues_found
---

# Phase 09: Code Review Report

**Reviewed:** 2026-06-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Phase 09 adds the display-history audit log: a `display_history` table, a repository with cap-based pruning, history recording in `ScreenDriverService`, a JSON API endpoint, and an HTML UI page.

The overall structure is sound. The main concerns are a race condition in the row-cap pruning logic (count-then-delete is not atomic), a DoS vector via unbounded `size=all` queries that can cast `Long` to `Int` with overflow, and several code-style violations against the project rule of no comments in Kotlin files. Additional blockers include missing input-size guards on the `size` query parameter (API route) and the `Int.MAX_VALUE` sentinel being passed directly into the SQL `LIMIT` clause.

---

## Critical Issues

### CR-01: Race condition in row-cap pruning — count and delete are separate statements

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:21-28`
**Issue:** `insert()` counts rows, then conditionally deletes the oldest row in two separate SQL statements inside one `suspendTransaction` block. Because `suspendTransaction` in Exposed v1 does not automatically serialize concurrent callers at the database isolation level (default is `READ COMMITTED` for most JDBC drivers), two concurrent inserts can both read `count = 1000`, both decide to delete, and then both proceed to insert — leaving 1001 rows momentarily. More critically, the same two callers may delete the same "oldest" row, causing a `NoSuchElementException` from `.single()` if the row has already been deleted between the `selectAll` and the `deleteWhere`, crashing the transaction.

```kotlin
// Current — two-statement, non-atomic:
val count = HistoryTable.selectAll().count()   // line 21
if (count >= MAX_ROWS) {
    val oldest = HistoryTable.selectAll()       // line 23
        .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
        .limit(1)
        .single()[HistoryTable.id]              // crashes if deleted between reads
    HistoryTable.deleteWhere { HistoryTable.id eq oldest }
}

// Fix — use a single DELETE … WHERE id = (SELECT id FROM … ORDER BY … LIMIT 1)
// or guard with .singleOrNull() and skip if null:
if (count >= MAX_ROWS) {
    val oldest = HistoryTable.selectAll()
        .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
        .limit(1)
        .singleOrNull()?.get(HistoryTable.id) ?: return@suspendTransaction
    HistoryTable.deleteWhere { HistoryTable.id eq oldest }
}
```

The `.singleOrNull()` guard prevents the crash. A true fix requires a database-level atomic operation or serializable isolation.

---

### CR-02: `Int.MAX_VALUE` passed as SQL LIMIT — integer overflow and runaway query

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:16,23-24`
**Issue:** When `size=all` is supplied, `size` is set to `Int.MAX_VALUE` (2 147 483 647). That value is passed to `findPaginated(1, Int.MAX_VALUE, ...)` on line 26, which feeds it directly into `.limit(size)` in the SQL query. Even if the table has only 1 000 rows, constructing the query with `LIMIT 2147483647` is harmless in execution but signals a design defect. More dangerously, the intermediate fetch at line 23 does a `findPaginated(1, 1, ...)` to get `total`, then calls `t.toInt()` on line 24. `total` is a `Long`; if the table ever grows beyond `Int.MAX_VALUE` rows (pathological, but the cap is only 1 000, so it cannot in practice), this cast silently overflows. The real problem is the code path is architecturally wrong: `Int.MAX_VALUE` should never reach `LIMIT`.

```kotlin
// Current:
val size = if (sizeAll) Int.MAX_VALUE else rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
...
val (_, t) = historyRepository.findPaginated(1, 1, effectFilter, sourceFilter)
historyRepository.findPaginated(1, t.toInt().coerceAtLeast(1), effectFilter, sourceFilter)

// Fix — fetch the real total first, cap at a safe maximum, avoid Int.MAX_VALUE as sentinel:
val (_, t) = historyRepository.findPaginated(1, 1, effectFilter, sourceFilter)
historyRepository.findPaginated(1, t.coerceAtMost(MAX_PAGE_SIZE).toInt().coerceAtLeast(1), effectFilter, sourceFilter)
// where MAX_PAGE_SIZE = 1000L (the repository cap)
```

---

### CR-03: API `size` parameter has no upper bound — unbounded result set

**File:** `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt:12`
**Issue:** The JSON API route clamps `size` to a minimum of 1 with `.coerceAtLeast(1)` but applies no upper bound. A caller sending `?size=2147483647` will cause the repository to execute `SELECT … LIMIT 2147483647`, potentially materialising the entire table into memory as a `List<HistoryRecord>` and serialising it to JSON in a single response. This is a trivial DoS vector with a single HTTP request.

```kotlin
// Current:
val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceAtLeast(1) ?: 20

// Fix:
val size = call.request.queryParameters["size"]?.toIntOrNull()
    ?.coerceIn(1, 200) ?: 20
```

---

### CR-04: `DatabaseFactory.init()` uses `SchedulesTable` before it is referenced via an import

**File:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt:33`
**Issue:** `SchemaUtils.create(SchedulesTable)` on line 33 references `SchedulesTable`, but the file has no visible import for it in the reviewed source. If this compiles only because of a wildcard import elsewhere, it is fragile. More critically, `SchemaUtils.create()` is called after Flyway has already run migrations — meaning the table will exist if the migration ran, and Exposed will silently no-op. However if the Flyway migration fails and leaves the table in a partial state, the `SchemaUtils.create()` call may not catch the inconsistency because it only creates what is missing. This is not a blocker on its own, but combined with the explicit call for `HistoryTable` on line 34 at application startup (after Flyway), it shows that `SchemaUtils.create(HistoryTable)` is redundant with `V3__add_history_table.sql`. The real risk: if the migration is rolled back or missing in a fresh deployment, `SchemaUtils.create` silently creates a slightly different schema (e.g. no `source` quoting), leading to a schema mismatch between what Exposed maps and what the DB has. Flagging as BLOCKER because the redundant `SchemaUtils.create(HistoryTable)` means two code paths own the schema definition, and they can diverge.

```kotlin
// Fix — remove both SchemaUtils.create() calls; let Flyway be the sole schema owner:
transaction {
    // SchemaUtils.create(SchedulesTable)  <-- remove
    // SchemaUtils.create(HistoryTable)    <-- remove
}
// Remove the entire transaction block; schema is managed by Flyway migrations only.
```

---

### CR-05: XSS — user-supplied query parameters reflected unescaped into HTML `href` attributes

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:77-78,115`
**Issue:** The `effect` and `source` values received from the caller are interpolated directly into `href` strings passed to `kotlinx.html`'s `a()` builder:

```kotlin
// line 77
a(href = "?expand=all&effect=$effect&source=$source&size=$rawSize") { +"Expand all" }
// line 78
a(href = "?effect=$effect&source=$source&size=$rawSize") { +"Collapse all" }
// line 115
a(href = "?page=$p&effect=$effect&source=$source&size=$rawSize${if (expandAll) "&expand=all" else ""}") { +"$p" }
```

`kotlinx.html` escapes content nodes (`+` operator) but does **not** URL-encode or HTML-attribute-escape values passed as `href` strings. If an attacker supplies `effect="><script>alert(1)</script>` the raw value is embedded in the generated `href` attribute without escaping, producing a reflected XSS payload in the rendered HTML. The `source` and `rawSize` parameters are subject to the same issue.

```kotlin
// Fix — URL-encode each parameter before interpolation:
import java.net.URLEncoder
fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

a(href = "?expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}") { +"Expand all" }
```

---

## Warnings

### WR-01: History is recorded even when `displaySucceeded = false` in the SKIP_NEW path of `displayScheduled`

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:116-118`
**Issue:** In the `SKIP_NEW` branch of `displayScheduled`, `displaySucceeded` is set to `true` only after `executeWithRecovery` returns (line 117). The history insert (line 118) is inside the same `try` block but fires unconditionally after `executeWithRecovery` — which is correct. However `displaySucceeded` begins as `false` and is only flipped after the render. If `executeWithRecovery` completes without exception, the record is inserted and `displaySucceeded` is set to `true`. This ordering is fine. But note line 118 sits on the same physical line as `displaySucceeded = true`, which means if the history insert throws (it is wrapped in its own try-catch), `displaySucceeded` is still `true`. That is the intended behaviour per the "non-fatal" comment, so no correctness bug here in isolation.

The real warning: the `INTERRUPT` path of `displayScheduled` (lines 137-141) has the history insert **inside** the `displayMutex.withLock` block, while the `SKIP_NEW` path (lines 116-118) calls the history insert **outside** the lock. This asymmetry means history inserts for scheduled events have different lock semantics depending on the conflict policy — a future maintainer editing one path may not realise they need to match the other.

**Fix:** Move the history insert in the `SKIP_NEW` scheduled path to be consistent with the INTERRUPT path, or extract a helper `private suspend fun recordHistory(...)` called from both branches after the lock is released.

---

### WR-02: `HistoryUIRoutes` — `size=all` performs two full database round-trips with no page guard

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:22-24`
**Issue:** The `size=all` flow fetches `total` with `findPaginated(1, 1, ...)` and then immediately fetches all rows with `findPaginated(1, t.toInt(), ...)`. This executes two SQL queries when one would suffice. More importantly, there is no authentication or rate limiting on the UI routes (as seen in `Routing.kt` — `installApiRateLimiting` is only applied inside `route("/api/v1")`), so any unauthenticated user can hit `/history?size=all` repeatedly and generate unbounded table scans.

**Fix:** Apply a cap consistent with `MAX_ROWS` (1 000) when resolving `size=all`, and consider placing UI routes behind the same rate limiter:

```kotlin
val size = if (sizeAll) MAX_UI_SIZE else rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
// MAX_UI_SIZE = 1000 (matches repository cap)
// Use a single findPaginated call; no need to pre-fetch total.
val (items, total) = historyRepository.findPaginated(page, size, effectFilter, sourceFilter)
```

---

### WR-03: `DependencyInjection.kt` instantiates `HistoryRepository` before `DatabaseFactory.init()`

**File:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt:33,49`
**Issue:** `HistoryRepository()` is constructed on line 33 and passed into `ScreenDriverService` on line 41. `DatabaseFactory.init(appConfig.databaseConfig)` is called on line 49 — after the repository is already live and wired into a service that may be called (e.g. via `ApplicationStarted` event on line 51). If `schedulerService.start()` fires a schedule before `DatabaseFactory.init()` completes, the repository will attempt a database call against an uninitialised connection pool and throw a runtime exception.

**Fix:** Move `DatabaseFactory.init(appConfig.databaseConfig)` to the top of `configureDI()`, before any repository is instantiated:

```kotlin
fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    DatabaseFactory.init(appConfig.databaseConfig)   // <-- move here, first thing
    val pi4jContext = Pi4J.newAutoContext()
    ...
}
```

---

### WR-04: Prohibited code comments present in `ScreenDriverService.kt`

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:43,98`
**Issue:** The project coding rule (documented in `feedback_coding_rules.md`) explicitly forbids comments in Kotlin files. Lines 43 and 98 contain KDoc-style `/** ... */` block comments:

- Line 43: `/** Called by ad-hoc POST /api/text — preempts any running scheduled display. */`
- Line 98: `/** Called by the scheduler — registers the job for cancellation via displayImmediate. */`

These violate the hard project rule with no exceptions.

**Fix:** Remove both comment blocks entirely.

---

## Info

### IN-01: `HistoryRecord.id` and `HistoryRecord.displayedAt` have non-intuitive defaults

**File:** `src/main/kotlin/com/anjo/model/HistoryRecord.kt:7,13`
**Issue:** `id` defaults to `""` and `displayedAt` defaults to `""`. These empty-string defaults allow constructing a `HistoryRecord` in an invalid transient state that would fail database constraints if inserted directly. The defaults exist to allow callers to omit them before calling `repository.insert()`, which fills them in. This pattern is fragile — a caller could accidentally call `findPaginated` and receive a record with an empty `id` only if the DB somehow returns one, but more likely a developer will construct a record, forget to call `insert()`, and pass the empty-id record somewhere expecting a valid ID.

**Fix:** Make `id` and `displayedAt` nullable (`String? = null`) to distinguish "not yet persisted" from "empty string", or use a sealed class / factory approach.

---

### IN-02: `HistoryPage.kt` hardcodes the effect option list — will silently omit future effects

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:44-47`
**Issue:** The effect dropdown hardcodes `SCROLL`, `BLINK`, `REVERSE`, `FADE`. If a new `Effect` enum value is added in the future (e.g. during Phase 11 multi-zone work), the filter UI will not show it without a manual update to this template.

**Fix:** Derive the options from `Effect.values()` to keep the UI in sync automatically:

```kotlin
Effect.values().forEach { e ->
    option { value = e.name; if (effect == e.name) selected = true; +e.name }
}
```

---

### IN-03: `ApplicationTest.kt:49` — duplicated assertion on the same value

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:48-49`
**Issue:** Lines 48 and 49 both assert `response.status shouldBe HttpStatusCode.OK` for the same response object. The duplication does not cause a false positive but indicates copy-paste and wastes a test assertion slot.

**Fix:** Remove the duplicate assertion on line 49.

---

_Reviewed: 2026-06-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
