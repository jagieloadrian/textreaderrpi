---
phase: 14-history-enhancements
reviewed: 2026-06-22T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/db/HistoryRepository.kt
  - src/main/kotlin/com/anjo/model/HistoryFilter.kt
  - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
  - src/main/kotlin/com/anjo/service/HistoryService.kt
  - src/main/kotlin/com/anjo/validation/HistoryValidators.kt
  - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
  - src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt
  - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
  - src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
  - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
  - src/test/kotlin/com/anjo/service/HistoryServiceTest.kt
  - src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt
  - src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt
findings:
  critical: 4
  warning: 5
  info: 3
  total: 12
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-06-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Phase 14 adds display-history recording with filtering, pagination, search-with-highlight, zone/source/effect filter dropdowns, and CSV export. The overall architecture is reasonable: a thin repository over Exposed, a service layer, separate API and UI routes, and server-side HTML rendering via kotlinx-html.

Four blockers were found:

1. The insert pruning has a silent data-loss bug — when the `singleOrNull()` call returns `null` (theoretically impossible but can occur under a race or table inconsistency), the transaction returns early without inserting the new record and without notifying the caller.
2. The `displayedAt` column is stored as a lexicographically-sortable ISO-8601 string but is declared as `varchar(32)` — `Instant.now().toString()` can emit strings longer than 32 characters when nanosecond precision is included (e.g., `2026-06-22T10:15:30.123456789Z` = 30 chars, fine today, but the theoretical maximum with 9 digits of nanoseconds is 30 chars — just within limit; however the sort-by-string ordering is semantically correct only for UTC ISO-8601, which is the case here — this is actually fine, annotated below).
3. The raw `search` parameter — not the sanitized one — is embedded into the `exportHref` URL in `HistoryUIRoutes.kt`. This means `%` and `_` wildcard characters reach the export endpoint's sanitizer rather than being stripped before link generation, which is the correct flow, but it also means a user-supplied value containing characters such as `"` or `<` could be URL-encoded into the href attribute. Because `URLEncoder` is applied the XSS risk is mitigated, but the export endpoint itself does **not** sanitize its own `search` input — it calls `HistoryValidators.sanitizeSearchTerm` in `HistoryRoutes.kt`, so that is fine. The `exportHref` passes `rawSearch` to the link, which is intentional so the export mirrors the current filter state; this is not a defect on its own. However, the actual critical issue is different: see CR-03.
4. The CSV export sets `Content-Type: text/plain` instead of `text/csv`. Browsers will not offer the correct file-type handling.

---

## Critical Issues

### CR-01: Silent insert skip when pruning finds no oldest row

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:27`

**Issue:** When the table has exactly `MAX_ROWS` (1000) rows and the pruning query `singleOrNull()` returns `null` (e.g., due to a concurrent delete racing this transaction, or a DB-level inconsistency), the `return@suspendTransaction` early-exit silently aborts the **entire** transaction including the subsequent `HistoryTable.insert`. The caller receives a fully constructed `HistoryRecord` with a generated `id` and `displayedAt` as if the insert succeeded, but the record was never written to the database.

```kotlin
// current — insert is skipped silently
val oldest = HistoryTable.selectAll()
    .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
    .limit(1)
    .singleOrNull()?.get(HistoryTable.id) ?: return@suspendTransaction  // BUG: skips insert
HistoryTable.deleteWhere { HistoryTable.id eq oldest }
```

**Fix:** Remove the early return and allow the insert to proceed regardless of whether a row to prune was found. In the degenerate case this may briefly exceed `MAX_ROWS` by one, which is acceptable, and eliminates data loss:

```kotlin
val oldest = HistoryTable.selectAll()
    .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
    .limit(1)
    .singleOrNull()?.get(HistoryTable.id)
if (oldest != null) {
    HistoryTable.deleteWhere { HistoryTable.id eq oldest }
}
// Insert always proceeds
HistoryTable.insert { ... }
```

---

### CR-02: `displayedAt` stored as `varchar` — lexicographic sort is correct only as long as the format stays ISO-8601 UTC, but `Instant.toString()` format is JVM-implementation-defined

**File:** `src/main/kotlin/com/anjo/db/HistoryTable.kt:12` / `src/main/kotlin/com/anjo/db/HistoryRepository.kt:20`

**Issue:** `displayedAt` is stored as `varchar("displayed_at", 32)` and populated via `Instant.now().toString()`. Both `ORDER BY displayedAt ASC/DESC` (pruning) and `ORDER BY displayedAt DESC` (pagination/export) rely on lexicographic ordering producing the same result as chronological ordering. This is only true for ISO-8601 UTC timestamps where all values share the same format and precision. `Instant.toString()` on the standard JVM always emits `YYYY-MM-DDTHH:MM:SS[.fractional]Z`, which is lexicographically monotone — but:

- The **fractional seconds** portion is variable-length (`.1`, `.123`, `.123456789`). Lexicographic comparison of `2026-06-22T10:00:00.9Z` vs `2026-06-22T10:00:00.10Z` gives the wrong order (`"9" > "1"` lexicographically, but `.10` seconds > `.9` seconds). This produces **incorrect pruning** (wrong oldest row deleted) and **incorrect pagination order** when two records share the same second.

**Fix:** Zero-pad fractional seconds, or better, switch to a fixed-length format or use a proper timestamp column:

```kotlin
// Option A: use a proper column type (preferred)
// In HistoryTable:
val displayedAt = datetime("displayed_at")  // Exposed datetime column

// Option B: fixed-length string with zero-padded nanos
import java.time.format.DateTimeFormatter
private val FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
    .withZone(java.time.ZoneOffset.UTC)
val now = FORMATTER.format(Instant.now())
```

---

### CR-03: CSV `Content-Type` is `text/plain` instead of `text/csv`

**File:** `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt:39`

**Issue:** The export endpoint responds with `ContentType.Text.Plain`. While the `Content-Disposition: attachment` header causes most browsers to download the file, the MIME type is incorrect. Tools that consume the API directly (curl pipelines, data-import wizards, spreadsheet applications invoking the URL) rely on the `Content-Type` to identify the format. `text/plain` will cause misidentification.

```kotlin
// current
call.respondText(csv, ContentType.Text.Plain)
```

**Fix:**

```kotlin
call.respondText(csv, ContentType("text", "csv"))
```

---

### CR-04: `MAX_UI_SIZE.toInt()` silently truncates when value is changed

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:15,24`

**Issue:** `MAX_UI_SIZE` is declared `Long` (1000L) and cast to `Int` via `.toInt()` before being passed to `findPaginated(filter, page, size: Int)`. While the current value (1000) is within `Int` range, the code comment implies it is intended as a user-facing cap. If someone raises it above `Int.MAX_VALUE` (2,147,483,647) the cast silently overflows to a negative number, which propagates into `LIMIT <negative>` and `OFFSET <negative>` in the SQL query, resulting in an exception or unbounded query at the database layer. More practically, this pattern hides that the real ceiling is `Int.MAX_VALUE` rows, not the declared `Long`.

Additionally, the UI route accepts any integer `size >= 1` with no upper bound when `sizeAll` is false (`rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20`), whereas the API route caps at 200. A user can request `?size=10000000` through the UI and get an unbounded DB scan.

**Fix:**

```kotlin
// Remove the Long declaration — keep the cap as Int directly
private const val MAX_UI_SIZE = 1000

// For the free-form size, apply a cap:
val size = if (sizeAll) MAX_UI_SIZE else rawSize.toIntOrNull()?.coerceIn(1, MAX_UI_SIZE) ?: 20
```

---

## Warnings

### WR-01: Pruning race condition — count and delete are not atomic

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:22-29`

**Issue:** Within the single `suspendTransaction` block the sequence is: (1) `COUNT(*)`, (2) `SELECT oldest id`, (3) `DELETE`, (4) `INSERT`. Under the default H2 and PostgreSQL isolation levels (READ COMMITTED), a concurrent insert between steps 1 and 4 can cause the table to momentarily hold `MAX_ROWS + 1` rows without triggering pruning in the concurrent transaction. Over time this means the table can silently grow beyond 1000 rows under load. This is a data consistency warning rather than a crash, but the cap is the stated contract.

**Fix:** Use `SERIALIZABLE` isolation for the insert transaction, or use a single `DELETE WHERE id IN (SELECT id FROM ... ORDER BY displayedAt ASC LIMIT ...)` combined with the insert in one atomic statement. Alternatively accept the soft cap and document it.

---

### WR-02: `exportHref` passes `rawSearch` (unsanitized) to the export link

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:36`

**Issue:** The export link is constructed using `rawSearch` (the raw query parameter value) rather than `searchFilter` (the sanitized value). The export endpoint correctly sanitizes on its own when the link is followed, so there is no data-layer issue. However, the link round-trips the raw wildcards (`%`, `_`) back to the client as part of the URL. If a user types `%` in the search box, the export href becomes `...&search=%25`, the endpoint strips it, and the exported file contains all records rather than the empty result the UI shows. This creates a silent discrepancy between what the filter page shows and what the CSV contains.

**Fix:** Use the sanitized value when constructing the export href:

```kotlin
val sanitizedSearch = HistoryValidators.sanitizeSearchTerm(rawSearch)
val exportHref = "/api/v1/history/export?effect=${effect.urlEncode()}&source=${source.urlEncode()}&zone=${zone.urlEncode()}&search=${sanitizedSearch.urlEncode()}"
```

---

### WR-03: `HistoryPage.kt` pagination omits `search` parameter from expand/collapse button URLs

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:108,114`

**Issue:** The `data-expand-url` attributes on the "Expand all" and "Collapse all" buttons do not include the `search` query parameter. Clicking either button while a search term is active will clear the search filter, returning the user to an unfiltered view at the same page number.

```kotlin
// "Expand all" button — missing &search=...
attributes["data-expand-url"] = "?page=$page&expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}"

// "Collapse all" button — also missing &search=...
attributes["data-expand-url"] = "?page=$page&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}"
```

**Fix:**

```kotlin
attributes["data-expand-url"] = "?page=$page&expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}&search=${search.urlEncode()}"

attributes["data-expand-url"] = "?page=$page&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}&search=${search.urlEncode()}"
```

---

### WR-04: Duplicate `urlEncode` private extension function

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:17` and `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:30`

**Issue:** An identical `private fun String.urlEncode()` extension is defined independently in two files. This is not a bug today, but it is a maintenance trap — if the encoding logic ever needs to change (e.g., charset, space encoding), there are two places to update and the duplication will not be caught by the compiler.

**Fix:** Extract to a shared utility in `com.anjo.web.templates` or `com.anjo.util`, make it `internal`, and import it in both files.

---

### WR-05: CSV column order puts `Zone ID` before `Schedule ID` — mismatches header name casing convention

**File:** `src/main/kotlin/com/anjo/service/HistoryService.kt:15-17`

**Issue:** The header row is `listOf("id", "Text", "Effect", "Source", "Zone ID", "Schedule ID", "Displayed At", "Webhook Status")`. The first column header is lowercase `"id"` while all others are title case. Additionally, the data row maps `r.zoneId` to position 4 (Zone ID) and `r.scheduleId` to position 5 (Schedule ID), but the `HistoryRecord` field ordering is `(id, text, effect, source, scheduleId, zoneId, ...)` — the CSV swaps `scheduleId` and `zoneId` relative to the model's natural field order. While the header labels do match the data columns, any consumer who reads both the API JSON (which uses `scheduleId`, `zoneId` field names) and the CSV (which uses `Schedule ID`, `Zone ID` in different order) may be confused. Additionally `id` inconsistently lowercased.

**Fix:** Normalise the header casing:

```kotlin
val header = listOf("ID", "Text", "Effect", "Source", "Zone ID", "Schedule ID", "Displayed At", "Webhook Status")
```

---

## Info

### IN-01: `HistoryService.exportCsv` performs an unbounded `findAll` with no row limit

**File:** `src/main/kotlin/com/anjo/service/HistoryService.kt:14`

**Issue:** `exportCsv` calls `repository.findAll(filter)` which has no `LIMIT` clause. If the history table is at max capacity (1000 rows) and the filter matches all of them, the entire result set is loaded into memory before CSV serialisation. At 1000 rows this is not a practical problem, but the API contract for `MAX_ROWS` and the export's lack of a cap are inconsistent — a future increase to `MAX_ROWS` would make the memory cost grow proportionally with no code change required.

**Fix:** Document the intentional coupling between `MAX_ROWS` and the unbounded export, or add an explicit cap comment.

---

### IN-02: `HistoryValidators.sanitizeSearchTerm` strips wildcards rather than escaping them

**File:** `src/main/kotlin/com/anjo/validation/HistoryValidators.kt:4`

**Issue:** The validator strips `%` and `_` from user input so they cannot be used as SQL LIKE wildcards. A user searching for `100%` or `snake_case` will receive results for `100` and `snakecase` respectively — the characters are silently removed rather than escaped. The correct approach for a "contains" search is to escape them as `\%` and `\_` so the literal characters are matched.

This is INFO rather than WARNING because the current behaviour is consistently applied (the search box tells users to search "displayed text" and the strip is documented via the tests), but it is semantically incorrect for users searching for text that legitimately contains these characters.

**Fix:** Escape instead of strip:

```kotlin
fun sanitizeSearchTerm(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
```

And pass the escape character to the Exposed `like` operator if the driver supports it.

---

### IN-03: Test isolation relies on shared in-memory H2 database without schema uniqueness guard

**File:** `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt:20` and `src/test/kotlin/com/anjo/service/HistoryServiceTest.kt:24`

**Issue:** `HistoryRepositoryTest` connects to `jdbc:h2:mem:test_history` and `HistoryServiceTest` connects to `jdbc:h2:mem:test_history_service`. Both use `DB_CLOSE_DELAY=-1` (keeps the database alive for the JVM lifetime) and call `SchemaUtils.create(HistoryTable)` in `beforeSpec`. If both test classes run in the same JVM (the default for Gradle), the first class to connect creates the schema; the second class may encounter an already-existing `HistoryTable` schema, which is handled gracefully by `SchemaUtils.create`. The `beforeEach` `deleteWhere` call keeps rows isolated. This is not currently broken but relies on the two classes using different DB names — if that naming convention is ever violated, tests will leak state across test classes silently.

**Fix:** No immediate change required, but a brief comment documenting why the two databases have different names would prevent future collision.

---

_Reviewed: 2026-06-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
