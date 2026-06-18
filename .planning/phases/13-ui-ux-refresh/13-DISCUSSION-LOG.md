# Phase 13: UI/UX Refresh - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-18 (updated session)
**Phase:** 13-UI/UX Refresh
**Areas discussed:** Schedule table rendering, Send Text zone routing, BaseLayout headExtra hook, Status page skeleton, Schedule create form zone, History filter backend, Zones add-by-IP approach, Zones delete approach

---

## Schedule Table: JS Re-render vs SSR

| Option | Description | Selected |
|--------|-------------|----------|
| Keep dual — update both SSR + JS render | Update both SchedulePage.kt (SSR fallback) and renderScheduleList() in app.js. JS render is the live view for Stop/Delete/Create refresh. Least disruption. | ✓ |
| Switch to SSR — fetch actions trigger full page reload | Remove renderScheduleList() from app.js. After Stop/Delete/Create, do a full page.reload(). Simpler long-term but loses live refresh without full reload. | |

**User's choice:** Keep dual — update both SSR + JS render
**Notes:** Both SchedulePage.kt and renderScheduleList() must include zone + webhookUrl columns.

---

## Send Text Zone Routing: ?zone= vs JSON body

| Option | Description | Selected |
|--------|-------------|----------|
| JSON body — add zoneId to { text, effect, zoneId } | TextRequest already has zoneId field. Add zone to JSON body. No URL change. | |
| Query param — append ?zone=X to fetch URL | POST /api/v1/text?zone=X — already supported from Phase 11 ZONE-07. | Agent decided |

**User's choice:** "zadecyduj" (you decide)
**Notes:** Agent chose query param to match existing Phase 11 route pattern. submitForm() appends `?zone=<value>` when a non-default zone is selected.

---

## BaseLayout headExtra Hook

| Option | Description | Selected |
|--------|-------------|----------|
| Optional lambda param on render() | Add headExtra: (HEAD.() -> Unit)? = null to BaseLayout.render(). Per-page opt-in. | ✓ |
| Just add custom.css directly in BaseLayout head | Load custom.css globally. No per-page hook needed. | |

**User's choice:** Optional lambda param on render()
**Notes:** custom.css is loaded globally in BaseLayout anyway. headExtra provides flexibility for future per-page overrides. All existing callers unchanged.

---

## Status Page SSR Shell vs Blank Canvas

| Option | Description | Selected |
|--------|-------------|----------|
| Full skeleton — labelled spans with Loading… default | Named sections with <span id="...">Loading…</span>. JS replaces text after fetch. | ✓ |
| Blank canvas — JS builds everything | Only page title + empty container. JS builds the entire DOM. | |

**User's choice:** Full skeleton — labelled spans with Loading… default
**Notes:** Sections: Uptime, Memory Used, Memory Max, Display Status, Total Failures, Hardware Metrics. All start as "Loading…".

---

## Schedule Create Form: Add Zone Selector?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — add zone selector (SSR) | SchedulePage.kt constructor receives zone list. createSchedule() includes zoneId. | Agent decided |
| No — zone column in list only | UI-05 only requires zone column in list view. | |

**User's choice:** "zadecyduj" (you decide)
**Notes:** Agent added for UX consistency — show and set zone from the same page.

---

## History Filter: Wire Backend Params or HTML Only?

| Option | Description | Selected |
|--------|-------------|----------|
| Wire both HTML + backend in Phase 13 | Add HTML selects AND wire through HistoryService + HistoryRepository. Full feature. | ✓ |
| HTML controls only — backend wiring later | Add HTML selects but backend ignores params until a future phase. | |

**User's choice:** Wire both HTML + backend in Phase 13
**Notes:** Minor backend change — no schema change, query filter extension only.

---

## Zones Add-by-IP: HTML Form POST vs Keep JS Fetch

| Option | Description | Selected |
|--------|-------------|----------|
| Keep JS fetch — addZoneByIp() already works | Handles 201/409/400/error with inline result messages. | ✓ |
| Switch to HTML form POST with server redirect | After submit, server redirects to /zones. No inline result. | |

**User's choice:** Keep JS fetch — addZoneByIp() already works

---

## Zones Delete: HTML Form vs JS Fetch

| Option | Description | Selected |
|--------|-------------|----------|
| Keep JS fetch — deleteZone() already works | DELETE with inline result + page reload. delete-zone-btn pattern stays. | ✓ |
| Switch to HTML form with server-side delete route | Requires new Ktor route. | |

**User's choice:** Keep JS fetch — deleteZone() already works

---

## Agent's Discretion

- **Zone routing (D-18):** chose `?zone=X` query param over JSON body
- **Schedule create zone selector (D-21):** added for UX consistency

## Deferred Ideas

None — discussion stayed within phase scope.
