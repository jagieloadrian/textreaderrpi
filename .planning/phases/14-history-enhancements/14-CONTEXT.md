# Phase 14: History Enhancements - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Full-text search and CSV export for display history. Users can search history by text content (API + HTML page), the HTML page highlights matched text with `<mark>`, and users can download currently-filtered history rows as a CSV file.

Requirements: HIST-04, HIST-05, HIST-06.

</domain>

<decisions>
## Implementation Decisions

### Mark Highlight (`<mark>` tags, HIST-06)
- **D-01:** Approach — Split the record text at match boundaries, escape each fragment, emit via Ktor DSL `mark {}` element. No `unsafe {}` / raw HTML injection.
- **D-02:** Helper location — Reusable function `highlightText(text: String, term: String?): FlowContent.() -> Unit` in a new shared file (e.g., `HtmlUtils.kt`) in `com.anjo.web.templates`. Not inlined in `HistoryPage.kt`.
- **D-03:** Null handling — Same code path always called: when `term == null` the function emits plain escaped text (no-op highlight). Call site in `HistoryPage` does not branch on `term != null`.
- **D-04:** Matching strategy — Case-insensitive exact substring using `String.indexOf(term, ignoreCase = true)`. Consistent with SQL LIKE behavior. Not word-boundary aware.
- **D-05:** Multiple occurrences — Highlight all occurrences of the term in a single `text` field value (loop until indexOf returns -1).

### CSV Export (HIST-05)
- **D-06:** Endpoint — `GET /api/v1/history/export` in `HistoryRoutes.kt`. Not a UI route.
- **D-07:** Filter propagation — `HistoryUIRoutes` renders the Export CSV link href server-side with the current filter params (`effect`, `source`, `zone`, `search`) baked in. No JS.
- **D-08:** Row cap — Respect existing 1000-row `MAX_ROWS` DB cap; no additional export-specific limit. Export fetches all matching rows without pagination.
- **D-09:** Columns — All `HistoryRecord` fields: `id`, `text`, `effect`, `source`, `zoneId`, `scheduleId`, `displayedAt`, `webhookStatus`.
- **D-10:** CSV header row — Human-readable: `id, Text, Effect, Source, Zone ID, Schedule ID, Displayed At, Webhook Status`.
- **D-11:** Response — `Content-Type: text/csv`, `Content-Disposition: attachment; filename="history.csv"`.
- **D-12:** Logic location — `HistoryService.exportCsv(filter: HistoryFilter): String` using `kotlin-csv-jvm` (com.jsoizo 1.10.0 already selected). Route calls it and calls `call.respondText(csv, ContentType.Text.Plain)` with the disposition header.

### Search Bar UI (HIST-04, HIST-06)
- **D-13:** Placement — Full-width search input at the top of the filter form, above the effect/source/zone dropdowns. Single `<form method="get" action="/history">` with all four filter fields.
- **D-14:** Export link placement — "Export CSV" link-styled button alongside the existing "Filter" submit button at the end of the filter form.
- **D-15:** Sticky search — `HistoryUIRoutes` passes `search` param to `historyPage()`; the input renders `value={search}` so the term is preserved after submit.
- **D-16:** Placeholder — `"Search displayed text…"` on the search input.

### Search Scope & API (HIST-04)
- **D-17:** Coverage — `?search=` added to both `GET /api/v1/history` (JSON) and `GET /history` (HTML). Both routes pass the param through `HistoryService.findPaginated(filter, page, size)`.
- **D-18:** Sanitization — New `com.anjo.validation.HistoryValidators` object with `sanitizeSearchTerm(input: String): String` that strips `%` and `_`. Called in the route handler before building `HistoryFilter`. If the sanitized result is blank, treat as `null` (no filter applied, return all results). No 400 error for wildcard-only inputs.
- **D-19:** `HistoryFilter` data class — Introduce `data class HistoryFilter(val effect: String?, val source: String?, val zone: String?, val search: String?)`. Both `findPaginated(filter, page, size)` and `exportCsv(filter)` accept this type. Replaces the current 3-nullable-param signature.
- **D-20:** Exposed LIKE query — `HistoryTable.text.lowerCase() like "%${term.lowercase()}%"` via Exposed DSL `andWhere {}`. No raw SQL fragments.

### Claude's Discretion
- Exposed DSL filter chain refactoring — `findPaginated` currently has a verbose if/else chain for building `where`/`andWhere` conditions. Claude may refactor this to a cleaner accumulator pattern when introducing `HistoryFilter`, as long as behavior is unchanged.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing History Layer
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` — Current `findPaginated()` signature and Exposed query pattern to extend with search
- `src/main/kotlin/com/anjo/service/HistoryService.kt` — Where `exportCsv()` method is added
- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` — JSON API route; add `?search=` param and `/export` endpoint here
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` — HTML page route; add `?search=` param and export link rendering
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` — Add search input, export button, and `highlightText()` call
- `src/main/kotlin/com/anjo/validation/RequestValidators.kt` — Pattern to follow when adding `HistoryValidators`

### Models & Schema
- `src/main/kotlin/com/anjo/model/HistoryRecord.kt` — Record fields used for CSV columns
- `src/main/kotlin/com/anjo/db/HistoryTable.kt` — Column names for Exposed DSL LIKE query

### Project Rules
- `.planning/STATE.md` §Key Pitfalls — LIKE wildcard injection (strip `%` and `_` in validators, not routes); CSV Content-Disposition; IDE KDoc stub removal
- `.planning/PROJECT.md` §Key Decisions — "Validation stays in ScheduleValidators/RequestValidators, NEVER in route handlers" (now also HistoryValidators)
- `.planning/REQUIREMENTS.md` §History Enhancements — HIST-04, HIST-05, HIST-06 acceptance criteria

### Dependency
- `kotlin-csv-jvm` (com.jsoizo 1.10.0) — already declared in STATE.md as the chosen CSV library for Phase 14; must be added to `build.gradle.kts` dependencies

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HistoryRepository.findPaginated()` — extend to accept `HistoryFilter` instead of 3 individual nullable params; add `search` LIKE clause using the same `andWhere {}` pattern
- `HistoryService.findPaginated()` — thin pass-through; add `exportCsv(filter)` alongside it
- `HistoryPage.kt` existing filter form — extend with new search `<input type="text">` and export link; same `form { method = FormMethod.get; action = "/history" }` block
- `RequestValidators.kt` object pattern — copy structure for new `HistoryValidators` object

### Established Patterns
- Exposed `andWhere {}` filter chaining — current `findPaginated` uses `where`/`andWhere` conditionally; search adds a 4th condition in the same style
- Ktor HTML DSL for all server-rendered HTML — no JS for search or export link; `mark {}` element is available in `kotlinx.html`
- Route handlers stay thin — extract any logic (sanitize, build filter, export) into service/validator layers
- `suspendTransaction {}` for all DB operations — `findAll(filter)` for export will also use it

### Integration Points
- `HistoryRoutes.kt` at `/api/v1/history` and new `/api/v1/history/export` — both registered in `Routing.kt` via `historyRoutes(historyService)`
- `HistoryUIRoutes.kt` at `/history` — export href is built server-side from the same query params the UI route receives
- `DependencyInjection.kt` — no new bindings needed; `HistoryService` already injected

</code_context>

<specifics>
## Specific Ideas

- The `highlightText` helper uses `String.indexOf(term, ignoreCase = true)` in a loop to find all occurrences and emit `prefix → mark { +matched } → remainder` segments.
- The export link URL pattern: `/api/v1/history/export?effect=X&zone=Y&search=Z` — same params as the current filter, minus `page`/`size` (export always fetches all matching rows).
- `HistoryFilter` lives in `com.anjo.model` alongside `HistoryRecord`.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 14-History Enhancements*
*Context gathered: 2026-06-22*
