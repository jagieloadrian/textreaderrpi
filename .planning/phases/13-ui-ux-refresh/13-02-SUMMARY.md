---
phase: 13-ui-ux-refresh
plan: "02"
subsystem: ui-layer
tags: [templates, zone-selector, history-filter, status-skeleton, ktor-html-dsl]
dependency_graph:
  requires: [13-01]
  provides: [IndexPage-zone-selector, StatusPage-skeleton, ZonesPage-discovered-tint, SchedulePage-zone-webhookUrl-columns, HistoryPage-zone-filter, HistoryRepository-zone-filter, HistoryService-zone-filter]
  affects: [IndexPage, StatusPage, ZonesPage, SchedulePage, HistoryPage, HistoryService, HistoryRepository]
tech_stack:
  added: []
  patterns: [kotlinx.html DSL article cards (D-30), SSR zone selector with ZoneStatus list, Exposed andWhere zone filter chain]
key_files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/web/templates/IndexPage.kt
    - src/main/kotlin/com/anjo/web/templates/StatusPage.kt
    - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
    - src/main/kotlin/com/anjo/web/templates/SchedulePage.kt
    - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
    - src/main/kotlin/com/anjo/service/HistoryService.kt
    - src/main/kotlin/com/anjo/db/HistoryRepository.kt
decisions:
  - "IndexPage constructor changed from no-arg class to class IndexPage(private val zones: List<ZoneStatus>) — route wiring (passing ZoneRegistry.listAll()) deferred to Plan 03"
  - "StatusPage converted from 5-param class to object with no-arg render() — route call site update deferred to Plan 03"
  - "HistoryPage zone select first option uses value=ALL consistent with effect/source filter convention"
  - "SchedulePage scheduleListContainer wrapped in article card per D-30"
  - "HistoryRepository zone andWhere uses same guard pattern as source filter: checks (effect != null || source != null) to decide andWhere vs where"
metrics:
  duration: "3 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  files_created: 0
  files_modified: 7
---

# Phase 13 Plan 02: Template Refresh + History Zone Filter Summary

**One-liner:** SSR zone selectors on IndexPage and SchedulePage, seven-span Loading... skeleton on StatusPage, zone-card--discovered tint on ZonesPage, working zone filter with filter-aware pagination on HistoryPage, and Exposed andWhere zone param wired end-to-end through HistoryService and HistoryRepository.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | IndexPage zone selector + effect preview; StatusPage skeleton shell; ZonesPage discovered tint | 3fbbd40 | IndexPage.kt, StatusPage.kt, ZonesPage.kt |
| 2 | SchedulePage zone/webhookUrl columns + create-form zone selector; HistoryPage zone filter + filter-aware pagination; HistoryRepository/HistoryService zone filter param | e4dc3b2 | SchedulePage.kt, HistoryPage.kt, HistoryService.kt, HistoryRepository.kt |

---

## What Was Built

### IndexPage.kt (modified)

- Constructor signature changed to `class IndexPage(private val zones: List<ZoneStatus>)`
- SSR zone `<select id="zoneSelect" name="zone">` with first option `value="" / "All zones"` and OFFLINE suffix rule
- `<div id="effectPreview" class="effect-preview">` in a separate "Preview" `<article>` card
- "Send Text" controls wrapped in a "Send" `<article>` card per D-30
- Existing ids `submitTextBtn`, `textInput`, `charCounter`, `effectSelect` unchanged

### StatusPage.kt (modified)

- Converted from 5-param `class StatusPage(...)` to `object StatusPage` with no-arg `render()`
- Three `<article>` cards: System (uptime, memory-used, memory-max), Display (status, failures), Hardware Metrics (hw-failures, hw-retries)
- All seven span ids: `status-uptime`, `status-memory-used`, `status-memory-max`, `status-display`, `status-failures`, `status-hw-failures`, `status-hw-retries` — each with initial text `"Loading..."`

### ZonesPage.kt (modified)

- `article` element for each zone card gets `attributes["class"] = "zone-card--discovered"` when `zone.discoveryMethod != null`
- All existing JS-targeted attributes unchanged: `delete-zone-btn`, `data-zone-id`, `addZoneForm`, `ipInput`, `scanBtn`

### SchedulePage.kt (modified)

- Function signature extended to `fun FlowContent.schedulePage(schedules: List<Schedule>, zones: List<ZoneStatus>)`
- SSR table header: `th { +"Zone" }` and `th { +"Webhook URL" }` added after Status, before Actions
- SSR table rows: `td { +(s.zoneId?.take(8) ?: "—") }` and `td { +(s.webhookUrl?.take(40) ?: "—") }` — em-dash fallback per UI-SPEC
- Create form: `<select id="scheduleZoneSelect" name="zoneId">` with first option `value="" / "No specific zone"`, then one option per zone
- Create form and schedule list wrapped in separate `<article>` cards per D-30
- Existing ids `createScheduleForm`, `createScheduleBtn`, `scheduleListContainer` unchanged

### HistoryPage.kt (modified)

- Function signature extended with `zone: String` and `zones: List<ZoneStatus>` params (after `source`)
- Disabled Phase-11 placeholder zone select replaced with real `<select id="zone" name="zone">` populated from zones list; first option `value="ALL" / "All zones"`; `attributes["disabled"]` removed
- Expand-all and Collapse-all links now include `&zone=${zone.urlEncode()}`
- Pagination links now include `&zone=${zone.urlEncode()}` so active zone filter persists across pages

### HistoryRepository.kt (modified)

- `findPaginated` signature extended with `zone: String? = null` as last param
- Zone filter block appended after source block: `andWhere { HistoryTable.zoneId eq zone }` when prior filters exist, else `where { HistoryTable.zoneId eq zone }` — Exposed parameterized query (T-13-03 mitigated)

### HistoryService.kt (modified)

- `findPaginated` signature extended with `zone: String? = null` as last param
- Forwards `zone` to `repository.findPaginated(page, size, effect, source, zone)`

---

## Deviations from Plan

None — plan executed exactly as written.

---

## Threat Flags

No new threat surface beyond what the plan's threat model covers.

- T-13-03 (Tampering/Injection): HistoryRepository zone filter uses Exposed parameterized `andWhere { HistoryTable.zoneId eq zone }` — no raw SQL string concatenation. Mitigated.
- T-13-04 (XSS): All zone IDs, webhookUrl, and schedule text rendered via kotlinx.html DSL text nodes (`+` operator) — auto-escaped. No `unsafe {}` blocks added. Mitigated.

---

## Known Stubs

None. All zone selectors are wired to receive `List<ZoneStatus>` from the constructor/param. The actual route-level wiring (passing `ZoneRegistry.listAll()` into IndexPage and SchedulePage constructors, reading the `zone` query param in HistoryPage route, calling `StatusPage.render()` with no params) is intentionally deferred to Plan 03 as documented in the plan objective.

---

## Self-Check: PASSED

- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` exists: FOUND
- `src/main/kotlin/com/anjo/service/HistoryService.kt` exists: FOUND
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` exists: FOUND
- Commit 3fbbd40 exists: FOUND
- Commit e4dc3b2 exists: FOUND
