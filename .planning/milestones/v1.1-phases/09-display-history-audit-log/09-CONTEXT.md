# Phase 9: Display History + Audit Log - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Every text display event — immediate (`POST /api/v1/text`) and scheduled (`SchedulerService.fire()`) — is persisted to a `display_history` table, queryable via `GET /api/v1/history` (paginated JSON), and browsable at `GET /history` (HTML page with foldable cards, filters, pagination). History is capped at 1000 rows; oldest rows are pruned on insert. No new display routes, no webhook logic, no zone routing — those are Phase 10 and Phase 11.

</domain>

<decisions>
## Implementation Decisions

### Recording Injection

- **D-01:** `HistoryRepository` injected directly into `ScreenDriverService` as a new constructor parameter — same pattern as `RetryConfig` and `EffectRendererFactory`. No new service layer.
- **D-02:** History record written **after** `executeWithRecovery()` succeeds. Only successful renders are persisted — no ghost entries for cancelled or failed displays.
- **D-03:** If `historyRepository.insert()` throws, the exception is caught and logged; `displayImmediate()`/`displayScheduled()` still returns `true`. History is observability — a DB hiccup must not break the display path.
- **D-04:** SKIP_NEW skipped requests are **not** recorded. Consistent with D-02: the display didn't render, so there is no event.
- **D-05:** 1000-row cap enforced synchronously inside `HistoryRepository.insert()` in the same `suspendTransaction{}`: DELETE the single oldest row when count reaches 1000, then INSERT the new row.

### History Table Schema

- **D-06:** `HistoryTable` columns: `id` (VARCHAR 36, UUID PK), `text` (TEXT), `effect` (VARCHAR 16), `source` (VARCHAR 16 — `IMMEDIATE` or `SCHEDULED`), `scheduleId` (VARCHAR 36, nullable), `zoneId` (VARCHAR 64, nullable, default null), `displayedAt` (VARCHAR 32, ISO-8601).
- **D-07:** `displayedAt` stored as `Instant.now().toString()` — matches `createdAt`/`firedAt` pattern in `SchedulesTable`. Consistent, human-readable, sortable.
- **D-08:** `zoneId` nullable with default null — ready for Phase 11 multi-zone without migration. Matches `SchedulesTable.zoneId` naming.
- **D-09:** Flyway migration: `V3__add_history_table.sql` — next in sequence after V1 (initial schema) and V2 (scheduler columns).

### GET /api/v1/history API

- **D-10:** Pagination params: `?page=1&size=20` (1-indexed page). Consistent with HTML page query params.
- **D-11:** Response envelope: `{ items: [...], page: 1, size: 20, total: 87 }`. Client uses `total` to render numbered pagination links.
- **D-12:** Server-side filters: `?effect=SCROLL&source=IMMEDIATE` — narrowed before pagination. Supported values for `effect`: all `Effect` enum values; for `source`: `IMMEDIATE`, `SCHEDULED`.
- **D-13:** Default sort: newest first (`ORDER BY displayedAt DESC`).

### /history HTML Page

- **D-14:** Cards grid layout (not a table). Each card uses `<details>/<summary>` for collapse/expand — pure browser-native, no JavaScript needed.
- **D-15:** Collapsed card shows: text preview (truncated), effect badge, timestamp. Expanded card shows full text, effect, source, scheduleId (linked to `/schedule?id=X`), zoneId (shown if non-null).
- **D-16:** `scheduleId` on expanded card is a clickable `<a href="/schedule?id=X">` link.
- **D-17:** Filter controls above cards: `<select>` dropdowns for effect and source. Zone filter rendered but disabled (placeholder label "Multi-zone — Phase 11") until Phase 11 populates zoneId.
- **D-18:** "Show all expanded" via `?expand=all` query param — Ktor HTML DSL renders all `<details open>` server-side when param is present.
- **D-19:** Page size selector: `<select>` with options 20 / 50 / all, passed as `?size=` query param. Default: 20.
- **D-20:** Pagination: numbered page links footer (1 2 3 … N), computed from total / size.

### Navigation

- **D-21:** "History" link added to `BaseLayout.kt` nav after "Schedule": `Home | Schedule | History | Settings | Status`.

### DI & Test Integration

- **D-22:** `HistoryRepository` registered in `DependencyInjection.kt` (`provide {}` block, same style as `ScheduleRepository`).
- **D-23:** `ApplicationTest.kt` DI smoke test updated to resolve `HistoryRepository` — required since Phase 8 REF-03 mandates all DI bindings are covered.
- **D-24:** `HistoryRepository` and recording integration tested with real H2 in-memory (`testApplication{}` pattern). Tests cover: `insert()`, `findPaginated()`, pruning at row 1001, and recording after `displayImmediate()` succeeds. No mock repositories.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pattern Files (follow exactly)
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` — Exposed table definition pattern to replicate for `HistoryTable`
- `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` — Repository pattern: `suspendTransaction{}`, `ResultRow` mapper, insert/select/delete
- `src/main/resources/db/migration/V2__add_scheduler_columns.sql` — Flyway migration naming and style for V3

### Integration Points
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — Add `HistoryRepository` constructor param; call `insert()` after `executeWithRecovery()` in both `displayImmediate()` and `displayScheduled()`
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` — Add `SchemaUtils.create(HistoryTable)` alongside `SchedulesTable`
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — Register `HistoryRepository` with `provide {}`
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — Update DI smoke test to resolve `HistoryRepository` (D-23)

### UI Templates
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — Add History nav link after Schedule (D-21)
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` — Reference for card/list rendering style
- `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` — Add `GET /history` route here

### Routing
- `src/main/kotlin/com/anjo/routing/Routing.kt` — Wire `historyRoutes()` and `historyUIRoutes()` into `configureRouting()`

### Requirements
- `.planning/REQUIREMENTS.md` §Display History — HIST-01, HIST-02, HIST-03 (3 requirements, all must be satisfied)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SchedulesTable` + `ScheduleRepository` — blueprint for `HistoryTable` + `HistoryRepository`; same Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`) imports and `suspendTransaction{}` pattern
- `testApplication {}` in `ApplicationTest.kt` — real H2 in-memory test pattern for repository and DI tests
- `BaseLayout.kt` nav — add one `<li>` entry; `activePath == "/history"` for aria-current
- Flyway: `V1`, `V2` exist → add `V3__add_history_table.sql` in `src/main/resources/db/migration/`

### Established Patterns
- ISO-8601 VARCHAR(32) for timestamps — `Instant.now().toString()`; used for `createdAt`, `firedAt`, `expiresAt` in `SchedulesTable`
- UUID VARCHAR(36) PKs — `UUID.randomUUID().toString()` on insert, returned in copy
- Kotest `should` convention — all test files use this; new tests follow the same style
- No comments in code files (project rule)
- Validation stays in route handlers only, never in repository or service

### Integration Points
- `ScreenDriverService.displayImmediate()` — after the `displayMutex.withLock { executeWithRecovery(...) }` block, before the `finally` block (or at end of try), call `historyRepository.insert(...)` wrapped in try-catch
- `ScreenDriverService.displayScheduled()` — same: call `historyRepository.insert(...)` after `executeWithRecovery()` sets `displaySucceeded = true`
- `configureRouting()` in `Routing.kt` — add `historyRoutes()` inside `/api/v1` route block; add `historyUIRoutes(historyRepository)` at the top level

</code_context>

<specifics>
## Specific Ideas

- The `/history` page cards use `<details>/<summary>`: collapsed summary row = text preview + effect + timestamp; expanded section = full text + source + scheduleId link + zoneId
- `scheduleId` on expanded card links to `/schedule?id=X` — user explicitly requested this for traceability
- `?expand=all` query param on `/history` causes Ktor HTML DSL to render all `<details open>` server-side (no JS)
- Page size selector (`?size=20|50|all`) rendered as `<select>` in the filter bar above cards
- Numbered page links footer (not just prev/next) — user preference for navigability across up to 50 pages

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 9-Display History + Audit Log*
*Context gathered: 2026-06-15*
