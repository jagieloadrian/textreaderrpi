# Phase 13: UI/UX Refresh - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-18 (updated 2026-06-20)
**Phase:** 13-ui-ux-refresh
**Areas discussed (session 1, 2026-06-18):** Schedule table rendering, Send Text zone routing, BaseLayout headExtra hook, Status page skeleton, Schedule create form zone, History filter backend, Zones add-by-IP approach, Zones delete approach  
**Areas discussed (session 2, 2026-06-20):** Settings link fate, ErrorPage layout, History filter + pagination, Zones auto-discovered styling, Desktop MD3 coverage

---

## Session 1 (2026-06-18)

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

## Session 2 (2026-06-20)

## Settings Link Fate

| Option | Description | Selected |
|--------|-------------|----------|
| Drop it | Remove /settings/display from the side nav. D-03 locks the 5 links. | ✓ |
| Keep it (6th link) | Add Settings as a 6th item in the side nav after Status. | |
| You decide | Agent decides based on whether the route actually works. | |

**User's choice:** Drop it  
**Follow-up — route status:** User clarified "do usuniecia" (to be deleted). SettingsPage.kt and GET /settings/display route should be removed entirely.  
**Decision captured:** D-25

---

## ErrorPage Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Use BaseLayout (gets side nav automatically) | ErrorPage calls BaseLayout.render() — inherits new side nav. No changes needed. | ✓ |
| Standalone minimal layout | Error page has its own minimal HTML (no side nav). | |
| You decide | Agent decides based on how ErrorPage is structured. | |

**User's choice:** Use BaseLayout  
**Follow-up — active path on errors:** Empty string (no active link highlighted). Current `activePath = ""` stays.  
**Note:** ErrorPage.kt already uses BaseLayout.render() — confirmed by codebase check. No code changes needed (D-26).

---

## History Filter + Pagination

| Option | Description | Selected |
|--------|-------------|----------|
| Reset to page 1 on filter change | Filter form always returns page 1. Pagination links include filter params. | ✓ |
| Preserve page number | Keep current page when filter changes. | |

**User's choice:** Reset to page 1  

| Option | Description | Selected |
|--------|-------------|----------|
| SSR — HistoryPage builds pagination links with filter params | HistoryPage.kt constructs ?zone=X&effect=Y&page=N links. No JS needed. | ✓ |
| JS — app.js appends filter values to page link clicks | More flexible but adds complexity. | |

**User's choice:** SSR  
**Decisions captured:** D-27

---

## Zones Auto-Discovered Styling

| Option | Description | Selected |
|--------|-------------|----------|
| Badge only | Small "Auto" chip/badge on the zone card. | |
| Card accent color — different background tint | Auto-discovered zones use a secondary surface color. | ✓ |
| You decide | Agent picks what looks best. | |

**User's choice:** Card accent color  

| Option | Description | Selected |
|--------|-------------|----------|
| surface-variant tint | Use --md-sys-color-surface-variant — already defined in D-07. | |
| secondary-container tint | A lighter accent. New token needed in custom.css. | ✓ |
| You decide | Agent picks most harmonious MD3 color. | |

**User's choice:** secondary-container tint  
**Decision captured:** D-28

---

## Desktop MD3 Coverage

Context: User free-text note — "miało to być odświeżenie całego ui w stylu material3, zrób to również dla stron dla desktop" (full MD3 refresh, desktop pages too).

| Option | Description | Selected |
|--------|-------------|----------|
| Card/grid layout on all pages | All pages use MD3 card-style layout on desktop. | |
| Wider side nav + content area on large screens | Desktop layout with max-width centered content. | |
| Both — cards on all pages AND proper desktop layout | MD3 cards + wider side nav + max-width container. | ✓ |

**User's choice:** Both  

| Option | Description | Selected |
|--------|-------------|----------|
| 960px breakpoint, 1200px max-width | Standard MD3 compact→expanded. | |
| 768px breakpoint, no max-width | Earlier switch, fills screen. | |
| You decide | Agent picks. | ✓ |

**User's choice:** You decide  

| Option | Description | Selected |
|--------|-------------|----------|
| Each section is its own card | Send Text = "Send" + "Preview" cards. Status = one card per metric group. | ✓ |
| One wide card per page | Entire page content in one <article>. | |
| You decide | Agent picks per-page. | |

**User's choice:** Each section is its own card  
**Decisions captured:** D-29, D-30

---

## Agent's Discretion (session 1)

- **Zone routing (D-18):** chose `?zone=X` query param over JSON body
- **Schedule create zone selector (D-21):** added for UX consistency

## Agent's Discretion (session 2)

- Desktop breakpoint and max-width exact values (D-29)
- secondary-container color token exact value for dark palette (D-28)

## Deferred Ideas

None — discussion stayed within phase scope.
