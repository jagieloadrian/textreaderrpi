---
phase: 14-history-enhancements
fixed_at: 2026-06-22T00:00:00Z
review_path: .planning/phases/14-history-enhancements/14-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 8
skipped: 1
status: partial
---

# Phase 14: Code Review Fix Report

**Fixed at:** 2026-06-22T00:00:00Z
**Source review:** .planning/phases/14-history-enhancements/14-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (4 Critical + 5 Warning)
- Fixed: 8
- Skipped: 1

## Fixed Issues

### CR-01: Silent insert skip when pruning finds no oldest row

**Files modified:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt`
**Commit:** a350675
**Applied fix:** Replaced the `?: return@suspendTransaction` early exit on the `singleOrNull()` result with an explicit null check. When `oldest == null` the delete is skipped and the insert proceeds regardless, preventing silent data loss.

---

### CR-02: Variable-precision fractional seconds break lexicographic timestamp sort

**Files modified:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt`
**Commit:** 410db10
**Applied fix:** Replaced `Instant.now().toString()` with a fixed-format `DateTimeFormatter` using pattern `uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'` (zero-padded 9-digit nanoseconds, UTC). This produces a fixed-length 30-character string that sorts correctly lexicographically, eliminating the `.9` vs `.10` ordering bug.

---

### CR-03: CSV Content-Type is text/plain instead of text/csv

**Files modified:** `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt`
**Commit:** 85af10a
**Applied fix:** Changed `ContentType.Text.Plain` to `ContentType("text", "csv")` in the export endpoint response.

---

### CR-04: MAX_UI_SIZE Long-to-Int silent truncation and no upper bound on size parameter

**Files modified:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt`
**Commit:** 9461522
**Applied fix:** Changed `MAX_UI_SIZE` from `1000L` (Long) to `1000` (Int), removing the `.toInt()` cast. Changed `rawSize.toIntOrNull()?.coerceAtLeast(1)` to `rawSize.toIntOrNull()?.coerceIn(1, MAX_UI_SIZE)` to enforce the upper bound when `sizeAll` is false. Also applied the WR-02 fix in the same commit (see below).

---

### WR-01: Pruning race condition — count and delete are not atomic

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:22-29`
**Reason:** skipped — see Skipped Issues section

---

### WR-02: exportHref passes rawSearch (unsanitized) to the export link

**Files modified:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt`
**Commit:** 9461522
**Applied fix:** Added `val sanitizedSearch = HistoryValidators.sanitizeSearchTerm(rawSearch)` and used `sanitizedSearch.urlEncode()` in the `exportHref` construction instead of `rawSearch.urlEncode()`. This ensures the export link mirrors exactly what the filter displays rather than round-tripping raw wildcard characters.

---

### WR-03: HistoryPage pagination omits search parameter from expand/collapse button URLs

**Files modified:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`
**Commit:** 8010554
**Applied fix:** Appended `&search=${search.urlEncode()}` to the `data-expand-url` attributes of both the "Expand all" and "Collapse all" buttons.

---

### WR-04: Duplicate urlEncode private extension function

**Files modified:** `src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt`, `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`, `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt`
**Commit:** 3b81a13
**Applied fix:** Moved `urlEncode` to `HtmlUtils.kt` as `internal fun String.urlEncode()`, removed the private declaration from `HistoryPage.kt` (same package, uses it automatically), and updated `HistoryUIRoutes.kt` to import `com.anjo.web.templates.urlEncode` and removed its private local copy.

---

### WR-05: CSV column header ID is lowercase while all others are title case

**Files modified:** `src/main/kotlin/com/anjo/service/HistoryService.kt`
**Commit:** 33739c8
**Applied fix:** Changed `"id"` to `"ID"` in the CSV header list to normalise casing across all columns.

---

## Skipped Issues

### WR-01: Pruning race condition — count and delete are not atomic

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:22-29`
**Reason:** skipped — design-level concern requiring transaction isolation change. The fix requires switching the insert transaction to `SERIALIZABLE` isolation or rewriting the prune+insert as a single compound SQL statement. Both approaches carry risk of deadlock or driver-compatibility issues in the Exposed/H2/PostgreSQL stack and require integration-level testing beyond the scope of an automated fix pass. The table soft-cap (at most `MAX_ROWS + N_concurrent_inserts` rows) is acceptable; the reviewer explicitly notes this is a data-consistency warning rather than a crash. Recommend documenting the soft-cap behaviour in a code comment and addressing in a dedicated follow-up.
**Original issue:** Under READ COMMITTED isolation, concurrent inserts between the COUNT and INSERT steps can allow the table to exceed MAX_ROWS without triggering pruning.

---

_Fixed: 2026-06-22T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
