# Phase 13: UI/UX Refresh - Context

**Gathered:** 2026-06-18 (updated 2026-06-20)
**Status:** Ready for planning

<domain>
## Phase Boundary

Full UI/UX refresh of the TextReaderRpi web interface. Replaces the current top-nav PicoCSS layout with a Material Design 3–style side navigation panel, adds Material 3 color tokens via a custom CSS file, and refreshes all five pages (Send Text, Schedules, History, Zones, Status) with cards, zone selectors, effect preview, and live data where needed.

Delivers UI-01 through UI-08:
- **UI-01** — Material Design 3 layout: side nav panel, cards, grids, responsive, no JS framework
- **UI-02** — Dark mode via `prefers-color-scheme` + Material 3 color tokens
- **UI-03** — Side nav links: Send Text, Schedules, History, Zones, Status
- **UI-04** — Send Text form: zone selector + live effect preview
- **UI-05** — Schedules page: zone column, webhookUrl, expandable rows
- **UI-06** — History page: cards with pagination, filter by zone and effect
- **UI-07** — Zones page: all zones (local + network), online/offline status, auto-discovered, add-by-IP button
- **UI-08** — Status page: data from `GET /health/detail` + hardware metrics from `GET /metrics`

No backend changes (except History filter wiring — D-22). No new API endpoints. No new DB schema. No JS framework (React/Vue/HTMX remain out of scope per REQUIREMENTS.md).

Also in scope:
- Delete `SettingsPage.kt` and `GET /settings/display` route (D-25) — not part of the target nav
- Desktop MD3 card layout and proper desktop breakpoint/max-width layout (D-29)

</domain>

<decisions>
## Implementation Decisions

### Side Navigation Layout
- **D-01:** Replace the current `<header>` horizontal nav in `BaseLayout.kt` with a CSS side panel layout (`<aside>` for nav, `<main>` for content). The side panel is always visible on desktop.
- **D-02:** On mobile/small screens, the side nav is hidden by default. A hamburger `☰` button in the top bar toggles a `nav-open` CSS class on `<body>`. Implementation: ~10 lines added to `app.js` — no new dependency.
- **D-03:** The top bar on mobile shows only the app name + hamburger button. The side nav overlays content when open (CSS `position: fixed` with overlay backdrop). Side nav links: Send Text (`/`), Schedules (`/schedule`), History (`/history`), Zones (`/zones`), Status (`/status`) — exactly 5 links.

### Settings Page Removal
- **D-25:** Delete `SettingsPage.kt` and remove `GET /settings/display` from `WebRoutes.kt`. Remove the `/settings/display` nav link from `BaseLayout.kt`. The Settings page is not part of the v1.1 UI and should not appear in the new side nav.

### Material 3 Color System
- **D-04:** A new static file `src/main/resources/static/custom.css` defines `--md-sys-color-*` CSS custom properties following the Material 3 color token spec. Both light and dark palettes are defined — dark variant applied by default, `prefers-color-scheme: light` overrides to the light palette.
- **D-05:** PicoCSS CSS variables (`--pico-background-color`, `--pico-color`, `--pico-primary`, etc.) are overridden with Material 3 token values inside `custom.css`. PicoCSS stays as the base reset/layout framework — no CDN change.
- **D-06:** `custom.css` is loaded via `<link>` directly in `BaseLayout.kt`'s `<head>` block (after the PicoCSS CDN link). The `data-theme="dark"` attribute is removed from `<html>` — theme is fully controlled by `prefers-color-scheme`. See D-19 for the headExtra hook.
- **D-07:** Material 3 baseline dark surface palette: `surface=#1c1b1f`, `on-surface=#e6e1e5`, `primary=#d0bcff`, `on-primary=#381e72`, `secondary=#ccc2dc`, `surface-variant=#49454f`, `outline=#938f99`. Add `--md-sys-color-secondary-container` token for auto-discovered zone cards (D-28). Light palette overrides defined in the same file under `@media (prefers-color-scheme: light)`.

### BaseLayout headExtra Hook
- **D-19:** `BaseLayout.render()` gains an optional `headExtra: (HEAD.() -> Unit)? = null` parameter. The lambda is invoked inside `<head>` before `</head>`. `custom.css` is loaded globally by `BaseLayout` itself (not through headExtra). The hook is available for future per-page overrides. All existing callers pass `null` (no change needed at call sites).

### Desktop MD3 Layout
- **D-29:** Full desktop MD3 layout: side nav always visible on desktop (≥960px), content area uses `max-width` + centered container — agent picks exact values (~1200px max-width). On large screens the side nav stays 240px wide and content fills the remaining space.
- **D-30:** Each page's content sections are their own `<article>` cards (MD3 card pattern). Examples: Send Text = "Send" card + "Preview" card; Schedules = "Create" card + "Schedule list" card; Status = one card per metric group. Uses PicoCSS `<article>` semantic element which renders as a card with the MD3 palette.

### Error Pages
- **D-26:** `ErrorPage.kt` already uses `BaseLayout.render()` — no changes needed. It inherits the new side nav automatically. `activePath = ""` (empty string) keeps no nav link highlighted on error pages. No modifications to `ErrorPage.kt`.

### Dynamic Data — Zones Selector + Status Page
- **D-08:** **Zone selector on Send Text form (UI-04):** Server-side rendered (SSR) — `IndexPage` constructor receives the list of `ZoneStatus` objects from `ZoneRegistry`. The `<select>` is populated at request time.
- **D-09:** **`/status` page (UI-08):** Data is loaded via `fetch()` calls in `app.js` on `DOMContentLoaded`. The page shell is rendered by Ktor; health/metrics data is populated client-side by calling `GET /health/detail` and `GET /metrics`. Auto-refresh every 10 seconds via `setInterval`.
- **D-10:** `StatusPage.kt` is a shell template — no constructor params. All live values are fetched and rendered by `app.js` into labelled `<span id="...">` placeholders.
- **D-20:** `StatusPage.kt` renders a full skeleton with named sections: Uptime, Memory Used, Memory Max, Display Status, Total Failures, Hardware Metrics. Each field has `<span id="status-...">Loading…</span>`. `app.js` replaces placeholder text after fetch resolves.

### Send Text Zone Routing
- **D-18 (Agent's discretion):** Zone selector on Send Text submits via `?zone=X` query param (matching the existing `/api/v1/text?zone=X` route from Phase 11 ZONE-07). The JSON body stays `{ text, effect }`. `submitForm()` appends `?zone=<selectedZoneId>` to the fetch URL when a non-default zone is selected. A default "All zones" option sends no zone param.

### Effect Preview on Send Form
- **D-11:** A `<div id="effectPreview">` is added below the effect selector in `IndexPage.kt`. It displays the typed text with a CSS animation matching the selected effect.
- **D-12:** Four CSS `@keyframes` animations defined in `custom.css`: `scroll-preview` (translateX), `blink-preview` (opacity step), `reverse-preview` (scaleX(-1) then scaleX(1)), `fade-preview` (opacity ease in-out loop). The active animation class is applied by `app.js` when the user changes the effect select.
- **D-13:** The preview updates live as the user types (same `input` event handler that drives the char counter).

### Page-Level Refresh (Schedules, History, Zones)
- **D-14:** **Schedules (`/schedule`):** Existing `SchedulePage.kt` extended to render a `zone` column and `webhookUrl` column as SSR fallback. Expandable rows use the `<details>/<summary>` HTML pattern. No JS needed for expansion.
- **D-17:** **Schedule list is dual SSR+JS:** `renderScheduleList()` in `app.js` overwrites the SSR table on every page load (for live Stop/Delete/Create refresh). Both `SchedulePage.kt` (SSR fallback) AND `renderScheduleList()` (JS re-render) must be extended with zone + webhookUrl columns. Stop/Delete/Create continue to trigger JS re-render after action.
- **D-21 (Agent's discretion):** Schedule create form also gets a zone `<select>` populated SSR from `ZoneRegistry` (same pattern as IndexPage). `SchedulePage.kt` constructor receives zone list. `createSchedule()` in `app.js` includes `zoneId` in the POST body.
- **D-15:** **History (`/history`):** Existing `HistoryPage.kt` gets zone and effect filter `<select>` elements added to the filter bar. Filter submitted as a GET form (`?zone=X&effect=Y`).
- **D-27:** **History filter resets pagination:** Filter form submission always returns page 1 (no `?page=` param in the filter form action). Pagination links include the active filter params — `HistoryPage.kt` constructs `/history?zone=X&effect=Y&page=N` links SSR. No JS needed for filter/pagination coexistence.
- **D-22:** **History filter backend wired in Phase 13:** `HistoryService.getHistory()` and `HistoryRepository` extended with optional `zone` and `effect` filter params. Route passes `?zone=X&effect=Y` query params through to the service layer. SSR-rendered filtered page returned.
- **D-16:** **Zones (`/zones`):** Existing `ZonesPage.kt` extended to show online/offline badge per zone, highlight auto-discovered zones (D-28), and surface the "Add by IP" form. The form is `<form id="addZoneForm">` (JS intercepts submit via `addZoneByIp()`).
- **D-28:** **Auto-discovered zone card styling:** Auto-discovered zones use `--md-sys-color-secondary-container` as card background tint (added to `custom.css`). This is applied via a CSS class (e.g., `.zone-card--discovered`) on `<article>` elements where `ZoneStatus.discoveredVia` indicates auto-discovery.
- **D-23:** **Add-by-IP stays as JS fetch** — `addZoneByIp()` in `app.js` already handles 201/409/400/error cases with inline result messages. No HTML form POST to API approach.
- **D-24:** **Zone delete stays as JS fetch** — `deleteZone()` in `app.js` already handles DELETE with inline result + page reload. `delete-zone-btn` button pattern in ZonesPage.kt stays.

### Agent's Discretion
- Exact CSS class naming for the side nav toggle and overlay backdrop (D-02, D-03)
- Whether the hamburger button uses a `<button>` or `<a>` element
- Specific `@keyframes` easing curves for the effect preview animations (D-12)
- Order of items in the side nav (follow D-03: Send Text, Schedules, History, Zones, Status)
- Whether `custom.css` also contains card/grid helper classes or relies solely on PicoCSS article/grid
- Zone routing for send text: default "All zones" option label and value (D-18)
- Status page `<span>` IDs for JS targeting (D-20)
- Schedule zone selector: what value/label to use for "no specific zone" option (D-21)
- Exact desktop breakpoint and max-width values for D-29 (~960px breakpoint, ~1200px max-width suggested)
- secondary-container color token value for D-28 (pick harmonious value matching the MD3 dark palette in D-07)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing UI layer
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — current layout; replace top nav with side panel, add custom.css link, add headExtra hook (D-19), remove /settings/display link (D-25), remove data-theme attribute
- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt` — Send Text form; add zone selector (SSR) + effect preview div; update constructor to accept zones
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` — Schedules table; add zone + webhookUrl columns to SSR table, add zone selector to create form; update constructor to accept zones
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` — History page; add zone/effect filter selects; build pagination links with filter params included (D-27)
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` — Zones page; add status badges, auto-discovered card tint (D-28), keep JS-driven add-by-IP form and delete buttons
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` — Status page shell; strip constructor params, render skeleton with Loading… spans (D-20)
- `src/main/kotlin/com/anjo/web/templates/ErrorPage.kt` — No changes needed; already uses BaseLayout, inherits new side nav (D-26)
- `src/main/kotlin/com/anjo/web/templates/SettingsPage.kt` — DELETE this file (D-25)

### Static assets
- `src/main/resources/static/app.js` — existing JS; add hamburger toggle, effect preview, /status auto-refresh fetch, update renderScheduleList() + createSchedule() for zone/webhookUrl, zone param in submitForm()
- `src/main/resources/static/` — location for new `custom.css`

### Routes that supply data
- `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` — remove `GET /settings/display` route (D-25); IndexPage + SchedulePage routes must pass `ZoneRegistry.listAll()` to constructor
- `GET /api/v1/text?zone=X` — zone routing in submitForm() (D-18)
- `GET /health/detail` — polled by app.js for /status page (D-09, D-20)
- `GET /metrics` — polled by app.js for /status page (D-09, D-20)

### Service + repository layer (History filter — D-22)
- `src/main/kotlin/com/anjo/service/HistoryService.kt` — extend getHistory() with optional zone + effect params
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt` — extend paginated query with optional WHERE zone/effect filters

### Requirements
- `REQUIREMENTS.md` §UI-01 through UI-08 — full requirement text for all 8 UI requirements
- `REQUIREMENTS.md` §Out of Scope — confirms no JS framework, no React/Vue/HTMX

### Phase 12 observability endpoints (feeds /status)
- `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` — `GET /health/detail` response shape for JS parsing
- `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` — `GET /metrics` response shape for JS parsing

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BaseLayout.render(pageTitle, activePath, content)` — single entry point for all pages; changing the layout here propagates to all pages automatically
- `app.js` static script — already loaded on every page; extend for hamburger, effect preview, status fetch, schedule zone column, and send-text zone routing
- `<details>/<summary>` expansion pattern — already used in HistoryPage for schedule details; reuse for expandable schedule rows (D-14)
- `ZoneRegistry.listAll(): List<ZoneStatus>` — available via DI; inject into IndexPage + SchedulePage route handlers for SSR zone selector
- `GET /health/detail` response: flat JSON with `uptime`, `memoryUsed`, `memoryMax`, `displayStatus`, `totalFailures`, `zoneErrors` — parse in app.js
- `GET /metrics` response: `MetricsResponse` with groups `runtime`, `api`, `hardware` — parse `hardware` group in app.js for /status page
- `renderScheduleList()` in app.js — currently renders ID/Text/Trigger/Effect/Status/Actions columns; must be extended with zone + webhookUrl columns (D-17)
- PicoCSS `<article>` element renders as a card — use for MD3 card pattern (D-30); no new card component needed

### Established Patterns
- Ktor HTML DSL (`kotlinx.html`) — all templates use `FlowContent.()` lambdas; no string concatenation
- `attributes["aria-current"] = if (activePath == x) "page" else ""` — existing active-link pattern in BaseLayout nav; preserve for side nav
- PicoCSS v2 semantic HTML — `<article>` renders as a card, `<nav><ul><li><a>` renders as nav; use these semantic elements for Material 3 card appearance
- `select { option { value = "SCROLL"; selected = true } }` — kotlinx.html select pattern from IndexPage; reuse for zone selectors
- `GET /history?page=N&size=M` — existing paginated history route; extend with `&zone=X&effect=Y` query params (D-22, D-27)
- `fetch("/api/v1/text", { method: "POST", body: JSON.stringify({ text, effect }) })` — existing submitForm() pattern; append `?zone=X` to URL (D-18)
- ZoneStatus.discoveredVia field — check to determine auto-discovered vs. manually-added zones for card tint (D-28)

### Integration Points
- `IndexPage` route handler in `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` — must receive `ZoneRegistry` via DI and pass zone list to `IndexPage` constructor
- `SchedulePage` route handler — must also receive `ZoneRegistry` via DI and pass zone list for create form selector
- `StatusPage` route handler — simplify to render the shell (no constructor params)
- `BaseLayout.kt` — single change propagates new layout to all 5+ pages; be careful not to break `ErrorPage` which also uses BaseLayout (it requires no changes per D-26)
- `HistoryService.getHistory()` — extend with zone/effect optional params; update route handler to read query params and forward
- `WebRoutes.kt` — remove GET /settings/display route (D-25)

</code_context>

<specifics>
## Specific Ideas

- Material 3 side nav panel on desktop: ~240px wide fixed sidebar, app title at top, nav links with icon (text emoji or Unicode icon) + label, active item has `primary-container` background
- Effect preview div should look like a mini LED display — dark background, monospace font, fixed height ~2 lines, overflow hidden
- The hamburger overlay on mobile should have a semi-transparent backdrop (`rgba(0,0,0,0.4)`) that closes the nav on click
- Zone selector: show zone name + "(OFFLINE)" suffix for zones where `status == "OFFLINE"` so user knows it may not respond
- Status page skeleton sections: Uptime, Memory (used/max), Display Status, Total Failures, Hardware Metrics group (failure count, retry count from `GET /metrics` hardware group)
- Schedule table: zone column shows zone ID (abbreviated) or "—" if no zone; webhookUrl column shows URL (truncated) or "—" if not set
- Auto-discovered zone cards: `--md-sys-color-secondary-container` tint (agent picks exact color harmonious with dark palette)
- Desktop layout: each page section is its own `<article>` card (D-30); e.g., Send Text has "Send" card + "Preview" card; Status has one card per metric group

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-UI/UX Refresh*
*Context gathered: 2026-06-18 (updated 2026-06-20)*
