---
plan: 14-03
phase: 14-history-enhancements
status: complete
completed: 2026-06-22
requirements: [HIST-04, HIST-06]
key-files:
  created:
    - src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt
    - src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt
  modified:
    - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
    - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
    - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
---

# Plan 14-03: SSR/UI Layer — Search Input, Mark Highlighting, Export Link

## What Was Built

**`HtmlUtils.kt` — `highlightText` helper (HIST-06)**
Created `fun highlightText(text: String, term: String?): FlowContent.() -> Unit` in `com.anjo.web.templates`. Uses `String.indexOf(term, ignoreCase = true)` loop to find all occurrences, emitting plain `+text` fragments between matches and `mark { +text.substring(idx, idx + term.length) }` for each hit. Uses the kotlinx.html `+` operator throughout — no `unsafe {}`, so all text is auto-escaped. Null/empty term falls through to plain text (same code path, no branch on caller side).

**`HistoryPage.kt` — search input, export link, highlight call, sticky search**
- Extended `historyPage(...)` signature with `search: String = ""` and `exportHref: String = ""` params
- Added `<input type="text" name="search" placeholder="Search displayed text…">` above the effect dropdown (full-width, sticky value via `value = search`)
- Added `<a href="exportHref" role="button" class="secondary outline">Export CSV</a>` alongside the Apply Filters button
- Replaced plain `+" ${item.text}"` with `highlightText(item.text, search.takeIf { it.isNotBlank() })()` — invokes the returned lambda in the `<p>` block
- Added `&search=${search.urlEncode()}` to pagination href so search term is preserved across page turns

**`HistoryUIRoutes.kt` — search wiring + export href construction**
- Added `rawSearch` extraction and `HistoryValidators.sanitizeSearchTerm(rawSearch).takeIf { it.isNotBlank() }` → `searchFilter`
- Updated `HistoryFilter` to include `search = searchFilter`
- Built `exportHref` server-side: `/api/v1/history/export?effect=...&source=...&zone=...&search=...` (all URL-encoded, no page/size — export fetches all)
- Added private `String.urlEncode()` extension (mirroring HistoryPage's private version)
- Passes `search = rawSearch` and `exportHref` to `historyPage()`

## Tests

**HtmlUtilsTest.kt (5 tests — all pass):**
- Wraps single match in `<mark>`
- Highlights all occurrences case-insensitively (`Hello`, `HELLO`, `hello` → 3 `<mark>` elements)
- Null term → plain text, no `<mark>`
- Empty term → plain text, no `<mark>`
- Angle brackets in record text are escaped (`&lt;b&gt;`, not `<b>`)

**HistoryUIRoutesTest.kt (3 new tests — all pass):**
- Page contains `name="search"` input
- Page contains `Export CSV` text and `/api/v1/history/export` href
- `GET /history?search=hello` body contains `<mark>hello</mark>`

## Acceptance Criteria

- `grep -c "fun highlightText" src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt` → 1 ✓
- `grep -c "import kotlinx.html.mark" src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt` → 1 ✓
- No `unsafe {}` in HtmlUtils.kt ✓
- `grep -c "indexOf" src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt` → 1 ✓
- `grep -c "highlightText(item.text" src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` → 1 ✓
- `grep -c "name = \"search\"" src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` → 1 ✓
- `grep -c "Export CSV" src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` → 1 ✓
- `grep -c "search=" src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` → 1+ ✓
- `grep -c "HistoryFilter(" src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` → 1 ✓
- `grep -c "/api/v1/history/export" src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` → 1 ✓
- `grep -c "HistoryValidators" src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` → 1+ ✓
- HistoryUIRoutesTest asserts `<mark>hello</mark>` for `?search=hello` ✓

## Self-Check: PASSED
