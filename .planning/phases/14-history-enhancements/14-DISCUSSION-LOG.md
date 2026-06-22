# Phase 14: History Enhancements - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-22
**Phase:** 14-History Enhancements
**Areas discussed:** Mark highlight safety, CSV export link mechanics, Search bar UI placement, Search scope in API

---

## Mark Highlight Safety

| Option | Description | Selected |
|--------|-------------|----------|
| Split + escape + DSL | Split text at match boundaries, escape fragments, emit via Ktor DSL mark {} element. No unsafe{}. | ✓ |
| Escape whole text → unsafe raw | Escape full text, string-replace to inject `<mark>`, emit via unsafe { +rawHtml(...) }. | |

**User's choice:** Split + escape + DSL

---

| Option | Description | Selected |
|--------|-------------|----------|
| Reusable utility function | highlightText(text, term?) in HtmlUtils.kt in com.anjo.web.templates | ✓ |
| Inline in HistoryPage.kt | Keep logic local to HistoryPage | |

**User's choice:** Reusable utility function

---

| Option | Description | Selected |
|--------|-------------|----------|
| Same code path, term=null → no-op | highlightText always called; null term emits plain escaped text | ✓ |
| Conditional call site | HistoryPage checks term != null before calling highlight | |

**User's choice:** Same code path, term=null → no-op

---

| Option | Description | Selected |
|--------|-------------|----------|
| Case-insensitive, exact substring | String.indexOf(term, ignoreCase=true); consistent with SQL LIKE | ✓ |
| Word-boundary aware | Regex \bterm\b; fewer false highlights but inconsistent with LIKE | |

**User's choice:** Case-insensitive, exact substring

---

## CSV Export Link Mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| Server-rendered href with current params | Ktor SSR bakes filter params into export link URL. No JS. | ✓ |
| JS rewrites href on filter change | JS watches filter form and updates export link. Requires JS. | |

**User's choice:** Server-rendered href

---

| Option | Description | Selected |
|--------|-------------|----------|
| /api/v1/history/export | In HistoryRoutes.kt alongside JSON API. HIST-05 specifies this path. | ✓ |
| /history/export as UI route | In HistoryUIRoutes.kt co-located with HTML page. | |

**User's choice:** /api/v1/history/export

---

| Option | Description | Selected |
|--------|-------------|----------|
| Respect existing 1000-row DB cap | MAX_ROWS=1000 already enforced; no additional limit | ✓ |
| Configurable export cap | New EXPORT_MAX_ROWS config or query param | |

**User's choice:** Respect existing 1000-row DB cap

---

| Option | Description | Selected |
|--------|-------------|----------|
| All history fields, filename=history.csv | id, text, effect, source, zoneId, scheduleId, displayedAt, webhookStatus | ✓ |
| Core fields only, timestamped filename | text, effect, source, displayedAt only | |

**User's choice:** All history fields, filename=history.csv

---

| Option | Description | Selected |
|--------|-------------|----------|
| Human-readable column names | id, Text, Effect, Source, Zone ID, Schedule ID, Displayed At, Webhook Status | ✓ |
| Kotlin property names (camelCase) | id, text, effect, source, zoneId, scheduleId, displayedAt, webhookStatus | |

**User's choice:** Human-readable column names

---

| Option | Description | Selected |
|--------|-------------|----------|
| HistoryService.exportCsv() method | Routes thin; consistent with findPaginated() pattern | ✓ |
| In the route handler | Simpler but breaks thin-routes pattern | |

**User's choice:** HistoryService.exportCsv() method

---

## Search Bar UI Placement

| Option | Description | Selected |
|--------|-------------|----------|
| Full-width search bar above dropdowns | Search input spans full width at top of filter form | ✓ |
| Inline as fourth field alongside dropdowns | Compact single row but may crowd narrow viewports | |
| Separate search widget above filter bar | Two distinct form blocks; requires handling two forms | |

**User's choice:** Full-width search bar above dropdowns

---

| Option | Description | Selected |
|--------|-------------|----------|
| Next to the filter submit button | Natural placement alongside "Filter" button at end of form | ✓ |
| Above history list near pagination | Near results but separated from filter form | |
| Fixed at page top near heading | Always visible but far from filter controls | |

**User's choice:** Next to the filter submit button

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — reflect search term in input | Route passes search param to historyPage(); input value={search} | ✓ |
| No — clear after submit | Always empty input; user must retype | |

**User's choice:** Yes — reflect search term back in input

---

| Option | Description | Selected |
|--------|-------------|----------|
| "Search displayed text…" | Specific placeholder matching the searched column | ✓ |
| "Search…" | Generic placeholder | |
| No placeholder | Empty input with label only | |

**User's choice:** "Search displayed text…"

---

## Search Scope in API

| Option | Description | Selected |
|--------|-------------|----------|
| Both JSON API and HTML page | GET /api/v1/history and GET /history both gain ?search= | ✓ |
| JSON API only | Only API endpoint; would not satisfy HIST-06 | |

**User's choice:** Both JSON API and HTML page

---

| Option | Description | Selected |
|--------|-------------|----------|
| New HistoryValidators object | com.anjo.validation.HistoryValidators.sanitizeSearchTerm() | ✓ |
| Inside HistoryRepository.findPaginated() | Strip wildcards in DB layer; odd responsibility | |

**User's choice:** New HistoryValidators object

---

| Option | Description | Selected |
|--------|-------------|----------|
| HistoryFilter data class | data class HistoryFilter(effect, source, zone, search); shared by findPaginated and exportCsv | ✓ |
| Add search as 6th param to findPaginated() | Simpler but 6-param signature hard to read; exportCsv repeats params | |

**User's choice:** HistoryFilter data class

---

| Option | Description | Selected |
|--------|-------------|----------|
| Sanitize to empty string → treat as no-search | Blank result after stripping → null filter, return all results | ✓ |
| Return 400 if sanitized term is blank | HTTP 400 for wildcard-only inputs | |

**User's choice:** Sanitize to empty string → treat as no-search (no error)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Exposed DSL: lowerCase() like "%term%" | Pure Exposed DSL, no raw SQL; consistent with existing query style | ✓ |
| Raw SQL via CustomStringFunction | More SQL control but bypasses Exposed escaping | |

**User's choice:** Exposed DSL: HistoryTable.text.lowerCase() like "%${term.lowercase()}%"

---

## Claude's Discretion

- Exposed DSL filter chain refactoring — may refactor the verbose if/else `where`/`andWhere` chain in `findPaginated` to a cleaner accumulator pattern when introducing `HistoryFilter`, as long as behavior is unchanged.

## Deferred Ideas

None — discussion stayed within phase scope.
