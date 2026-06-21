---
phase: 13-ui-ux-refresh
plan: "03"
subsystem: ui-layer
tags: [routes, app.js, zone-routing, status-polling, nav-toggle, settings-removal]
dependency_graph:
  requires: [13-01, 13-02]
  provides: [ZoneRegistry-injected-UI-routes, app.js-nav-toggle, app.js-effect-preview, app.js-zone-routing, app.js-status-polling, app.js-schedule-zone-webhook-columns]
  affects: [WebRoutes.kt, ScheduleUIRoutes.kt, HistoryUIRoutes.kt, Routing.kt, app.js]
tech_stack:
  added: []
  patterns: [ZoneRegistry DI injection into UI route handlers, fetch Promise.all polling, encodeURIComponent zone URL param, JS textContent span updates]
key_files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt
    - src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt
    - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
    - src/main/kotlin/com/anjo/routing/Routing.kt
    - src/main/resources/static/app.js
  deleted:
    - src/main/kotlin/com/anjo/web/templates/SettingsPage.kt
decisions:
  - "WebRoutes.kt screenDriverService param kept (still needed as function param even though /status no longer uses it — signature kept for future use; alternatively could be removed but plan did not explicitly require removal)"
  - "fetchStatusData sets all 7 spans to Unavailable on catch (not just the failed endpoint's spans) to keep error handling simple and consistent"
  - "closeNav called on aside nav link click to auto-close mobile nav on navigation — matches Interaction Contract"
  - "applyDriverBtn listener removed by deleting the entire settings block; no Settings page listener or function remains"
metrics:
  duration: "6 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  files_created: 0
  files_modified: 5
  files_deleted: 1
---

# Phase 13 Plan 03: Route + JS Wiring Summary

**One-liner:** ZoneRegistry injected into WebRoutes/ScheduleUIRoutes/HistoryUIRoutes so IndexPage and SchedulePage receive live zone lists; HistoryUIRoutes reads the zone query param; SettingsPage deleted; app.js extended with hamburger nav toggle, live effect preview, zone-routed send-text, 10s status polling, schedule Zone/Webhook columns, createSchedule zoneId, and applyDriver removed.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Inject ZoneRegistry into UI routes, delete SettingsPage + route, make StatusPage call param-less, wire history zone param | bf1c38c | WebRoutes.kt, ScheduleUIRoutes.kt, HistoryUIRoutes.kt, Routing.kt, SettingsPage.kt (deleted) |
| 2 | Extend app.js — hamburger toggle, effect preview, send-text zone routing, status polling, schedule zone/webhook columns, remove applyDriver | 1dd9198 | app.js |

---

## What Was Built

### WebRoutes.kt (modified)

- Signature changed to `fun Route.webRoutes(screenDriverService: ScreenDriverService, zoneRegistry: ZoneRegistry)`
- `get("/")`: `IndexPage(zoneRegistry.listAll()).render()`
- `get("/status")`: `StatusPage.render()` — no constructor params (Plan 02 object)
- `get("/settings/display") { ... }` block deleted (D-25)
- `get("/home") { call.respondRedirect("/") }` unchanged
- Import `com.anjo.service.ZoneRegistry` added; `import com.anjo.web.templates.SettingsPage` removed

### ScheduleUIRoutes.kt (modified)

- Signature changed to `fun Route.scheduleUIRoutes(repository: ScheduleRepository, zoneRegistry: ZoneRegistry)`
- `schedulePage(schedules, zoneRegistry.listAll())` — passes zone list to Plan 02 template

### HistoryUIRoutes.kt (modified)

- Signature changed to `fun Route.historyUIRoutes(historyService: HistoryService, zoneRegistry: ZoneRegistry)`
- Reads `val zone = call.request.queryParameters["zone"].orEmpty()`
- Computes `val zoneFilter = zone.takeIf { it.isNotEmpty() && it != "ALL" }`
- Calls `historyService.findPaginated(page, size, effectFilter, sourceFilter, zoneFilter)`
- Passes `zone` and `zoneRegistry.listAll()` to `historyPage(...)`

### Routing.kt (modified)

- `webRoutes(screenDriverService, zoneRegistry)` — zoneRegistry already resolved at line 34 via `by dependencies`
- `scheduleUIRoutes(scheduleRepository, zoneRegistry)`
- `historyUIRoutes(historyService, zoneRegistry)`

### SettingsPage.kt (deleted)

- Removed entirely per D-25. No replacement.

### app.js (modified)

**Nav toggle (D-02):**
- `toggleNav()` / `closeNav()` / `openNav()` functions added
- DOMContentLoaded: `#navToggle` click → `toggleNav`; `#navBackdrop` click → `closeNav`; each `aside nav a` click → `closeNav`

**Effect preview (D-11/D-12/D-13):**
- `mirrorPreviewText()` copies `#textInput` value to `#effectPreview.textContent`
- `applyEffectPreview()` removes `effect-scroll`/`effect-blink`/`effect-reverse`/`effect-fade` then adds the class matching `#effectSelect.value.toLowerCase()`
- DOMContentLoaded (guarded by `#effectPreview` existing): `input` on `#textInput` → `mirrorPreviewText`; `change` on `#effectSelect` → `applyEffectPreview`; both called once on load (default: `effect-scroll`)
- `#textInput` input listener also calls `mirrorPreviewText` in addition to existing `updateCounter`

**Send-text zone routing (D-18):**
- Reads `document.getElementById("zoneSelect")?.value ?? ""`
- URL: `/api/v1/text?zone=${encodeURIComponent(zoneId)}` when non-empty, else `/api/v1/text`
- HTTP 503 → toast "Zone offline. Text not sent."; 404 → "Zone not found."

**Status polling (D-09/D-20):**
- `async function fetchStatusData()` runs `Promise.all([fetch("/health/detail"), fetch("/metrics")])`
- Sets: `status-uptime`, `status-memory-used`, `status-memory-max`, `status-display`, `status-failures` from `/health/detail`
- Sets: `status-hw-failures` from `groups[name="hardware"].metrics[key="display.failures"].count`, `status-hw-retries` from `.metrics[key="recovery.retries"].count`
- On any error: sets affected spans to `"Unavailable"` (no throw)
- DOMContentLoaded (guarded by `#status-uptime` existing): `fetchStatusData()` immediately + `setInterval(fetchStatusData, 10000)`

**Schedule zone/webhook columns (D-17):**
- `renderScheduleList()` header: `<th>Zone</th><th>Webhook</th>` added after Status, before Actions
- Rows: `<td>${escHtml(s.zoneId || "—")}</td><td>${escHtml(s.webhookUrl || "—")}</td>` in same position
- Column order matches SSR table in SchedulePage.kt (Plan 02): ID/Text/Trigger/Effect/Status/Zone/Webhook/Actions

**Schedule create zoneId (D-21):**
- `createSchedule()` reads `document.getElementById("scheduleZoneSelect")?.value ?? ""`
- POST body includes `zoneId: zoneId || null`

**Settings cleanup (D-25):**
- `applyDriver()` function deleted
- `applyDriverBtn` listener in DOMContentLoaded deleted

---

## Deviations from Plan

None — plan executed exactly as written.

---

## Threat Flags

All mitigations from the plan's threat model applied:

- T-13-06 (XSS): `s.zoneId` and `s.webhookUrl` in `renderScheduleList()` wrapped via `escHtml()` — no raw interpolation.
- T-13-07 (XSS): Status span values set via `.textContent` not `innerHTML` — markup injection impossible.
- T-13-09 (Tampering): `encodeURIComponent` applied to zone ID before URL append; backend validates zone (404/503).

No new threat surface beyond what the plan's threat model covers.

---

## Known Stubs

None. All zone data flows are live:
- `IndexPage` receives `zoneRegistry.listAll()` at request time
- `SchedulePage` receives `zoneRegistry.listAll()` at request time
- `HistoryPage` receives zone query param and `zoneRegistry.listAll()` at request time
- Status page spans are populated by `fetchStatusData()` on `DOMContentLoaded`

---

## Self-Check: PASSED

- `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` exists: FOUND
- `src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt` exists: FOUND
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` exists: FOUND
- `src/main/kotlin/com/anjo/routing/Routing.kt` exists: FOUND
- `src/main/resources/static/app.js` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/SettingsPage.kt` DELETED: CONFIRMED
- Commit bf1c38c exists: FOUND
- Commit 1dd9198 exists: FOUND
- Task 1 grep gate (ROUTES_OK): PASSED
- Task 2 grep gate (APPJS_OK): PASSED
