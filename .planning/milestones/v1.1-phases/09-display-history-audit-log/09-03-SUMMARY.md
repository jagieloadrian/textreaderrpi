---
phase: 09-display-history-audit-log
plan: 03
subsystem: routing
tags: [kotlin, ktor, kotest, history, json-api, html, tdd, kotlinx-html, pagination]

requires:
  - phase: 09-display-history-audit-log
    plan: 01
    provides: HistoryRepository, HistoryRecord, HistoryPageResponse
  - phase: 09-display-history-audit-log
    plan: 02
    provides: HistoryRepository registered in DI container

provides:
  - GET /api/v1/history paginated filterable JSON with items/page/size/total envelope (HIST-02)
  - GET /history HTML page with details/summary cards, effect/source filters, expand-all, numbered pagination (HIST-03)
  - History nav link in BaseLayout after Schedule (D-21)
  - HistoryRoutes wired in /api/v1 block, HistoryUIRoutes wired at top level in configureRouting

affects:
  - All pages using BaseLayout (History nav link now rendered globally)

tech-stack:
  added: []
  patterns:
    - "historyRoutes uses page/size coerceAtLeast(1) and treats ALL as no-filter sentinel"
    - "HistoryUIRoutes size=all handled by two findPaginated calls (first to get total, then fetch all)"
    - "HistoryPage uses attributes[open] = empty string for details presence attribute (not attributes[open] = open)"
    - "kotlinx.html + operator auto-escapes all string content; no unsafe block (T-09-03 XSS mitigated)"
    - "Numbered pagination footer omitted when rawSize=all or total <= size (D-20)"
    - "Expand-all preserved across filter submissions via hidden input (RESEARCH Pitfall 4)"

key-files:
  created:
    - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
    - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
    - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
    - src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt
    - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
  modified:
    - src/main/kotlin/com/anjo/web/templates/BaseLayout.kt
    - src/main/kotlin/com/anjo/routing/Routing.kt

key-decisions:
  - "historyRoutes placed in com.anjo.routing package (same as Routing.kt) — no cross-package import needed"
  - "HistoryUIRoutes size=all uses two findPaginated calls to avoid passing Int.MAX_VALUE as SQL LIMIT"
  - "details open rendered via attributes[open] = empty string (presence attribute, not value attribute)"
  - "Effect and source filter dropdowns use labeled selects with htmlFor/id matching (accessibility contract)"

patterns-established:
  - "GET UI route: parse rawSize as String, sizeAll flag, then resolve actual size for DB call"
  - "Pagination: rawSize.toIntOrNull().coerceAtLeast(1) avoids division by zero in pageCount calculation"

requirements-completed: [HIST-02, HIST-03]

duration: 5min
completed: 2026-06-15
---

# Phase 09 Plan 03: HistoryRoutes JSON API + HistoryPage HTML + HistoryUIRoutes + Nav Summary

**paginated JSON API and server-rendered HTML history page with details/summary cards, effect/source filters, expand-all, and numbered pagination — all proven by 8 Kotest tests**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-15T17:14:29Z
- **Completed:** 2026-06-15T17:20:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- `GET /api/v1/history` returns `{items, page, size, total}` JSON envelope; page/size default 1/20; coerceAtLeast(1) on both; effect=ALL and source=ALL treated as no filter; unknown values yield empty set (lenient pattern)
- `GET /history` renders details/summary cards with collapsed preview (60-char truncation + ellipsis), effect badge (span role="note"), and ISO timestamp; expanded body shows full text, effect, source, scheduleId link (SCHEDULED only), zoneId (non-null only), displayedAt
- Filter form: labeled selects for effect, source, size, zone (disabled placeholder); hidden expand input preserves state; "Apply Filters" submit button; "Expand all" / "Collapse all" anchors
- `?expand=all` renders all details with open attribute (no JavaScript)
- Numbered pagination footer with aria-current="page" on active page; omitted when size=all or total <= size
- History nav link added to BaseLayout after Schedule; nav order: Home | Schedule | History | Settings | Status
- Both routes wired in `configureRouting()`: `historyRoutes` inside `/api/v1`, `historyUIRoutes` at top level
- Full test suite passes including JaCoCo 70% gate

## Task Commits

TDD tasks have multiple commits (test RED then feat GREEN):

1. **Task 1 - TDD RED: failing tests for HistoryRoutes JSON API** - `f554eaf` (test)
2. **Task 1 - TDD GREEN: HistoryRoutes.kt + Routing.kt wiring** - `5aa384e` (feat)
3. **Task 2 - TDD RED: failing tests for HistoryUIRoutes HTML page** - `2b36400` (test)
4. **Task 2 - TDD GREEN: HistoryPage + HistoryUIRoutes + BaseLayout + Routing** - `1a58b7c` (feat)

## Files Created/Modified

- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` — fun Route.historyRoutes; GET /history (inside /api/v1); page/size/effect/source param parsing
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` — fun Route.historyUIRoutes; GET /history HTML; rawSize/sizeAll logic; BaseLayout.render with History page title
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` — fun FlowContent.historyPage; filter form; details/summary cards; effect badge; scheduleId link; pagination footer
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — History li nav link inserted after Schedule, before Settings
- `src/main/kotlin/com/anjo/routing/Routing.kt` — historyRepository by dependencies; historyRoutes inside /api/v1; historyUIRoutes at top level; historyUIRoutes import added
- `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` — 4 Kotest tests: envelope, pagination slice, effect filter, source filter
- `src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt` — 4 Kotest tests: 200 with heading, filter dropdowns present, details open, nav href

## Decisions Made

- **size=all two-call approach**: The plan warned against passing `Int.MAX_VALUE` as SQL LIMIT. When `rawSize == "all"`, the route first calls `findPaginated(1, 1)` to get `total`, then calls `findPaginated(1, total.toInt().coerceAtLeast(1))`. This keeps the LIMIT bounded to the actual row count.
- **`attributes["open"] = ""`**: The kotlinx.html `details { attributes["open"] = "open" }` generates `open="open"` which is valid HTML5 but not idiomatic. Using `attributes["open"] = ""` generates `open=""` — the presence form preferred by the plan spec.

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria verified, all 8 tests pass, JaCoCo gate cleared.

## Known Stubs

None — all filter selects pass real query params to a live HistoryRepository; no hardcoded or empty data sources.

## Threat Flags

No new security surface beyond the plan's threat model:
- T-09-03 (XSS via stored display text): mitigated — HistoryPage.kt uses `+text` operator throughout; `grep -c "unsafe" HistoryPage.kt` returns 0
- T-09-04 (page/size overflow): mitigated — coerceAtLeast(1) in both routes

## Self-Check

Files created/modified:
- [x] src/main/kotlin/com/anjo/routing/HistoryRoutes.kt — FOUND
- [x] src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt — FOUND
- [x] src/main/kotlin/com/anjo/web/templates/HistoryPage.kt — FOUND
- [x] src/main/kotlin/com/anjo/web/templates/BaseLayout.kt — FOUND
- [x] src/main/kotlin/com/anjo/routing/Routing.kt — FOUND
- [x] src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt — FOUND
- [x] src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt — FOUND

Commits verified:
- [x] f554eaf — test(09-03): TDD RED HistoryRoutesTest
- [x] 5aa384e — feat(09-03): HistoryRoutes + Routing wiring
- [x] 2b36400 — test(09-03): TDD RED HistoryUIRoutesTest
- [x] 1a58b7c — feat(09-03): HistoryPage + HistoryUIRoutes + BaseLayout + Routing

## Self-Check: PASSED

---
*Phase: 09-display-history-audit-log*
*Completed: 2026-06-15*
