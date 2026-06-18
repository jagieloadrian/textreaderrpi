# Phase 13: UI/UX Refresh - Context

**Gathered:** 2026-06-18
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

No backend changes. No new API endpoints. No new DB schema. No JS framework (React/Vue/HTMX remain out of scope per REQUIREMENTS.md).

</domain>

<decisions>
## Implementation Decisions

### Side Navigation Layout
- **D-01:** Replace the current `<header>` horizontal nav in `BaseLayout.kt` with a CSS side panel layout (`<aside>` for nav, `<main>` for content). The side panel is always visible on desktop.
- **D-02:** On mobile/small screens, the side nav is hidden by default. A hamburger `☰` button in the top bar toggles a `nav-open` CSS class on `<body>`. Implementation: ~10 lines added to `app.js` — no new dependency.
- **D-03:** The top bar on mobile shows only the app name + hamburger button. The side nav overlays content when open (CSS `position: fixed` with overlay backdrop).

### Material 3 Color System
- **D-04:** A new static file `src/main/resources/static/custom.css` defines `--md-sys-color-*` CSS custom properties following the Material 3 color token spec. Both light and dark palettes are defined — dark variant applied by default, `prefers-color-scheme: light` overrides to the light palette.
- **D-05:** PicoCSS CSS variables (`--pico-background-color`, `--pico-color`, `--pico-primary`, etc.) are overridden with Material 3 token values inside `custom.css`. PicoCSS stays as the base reset/layout framework — no CDN change.
- **D-06:** `custom.css` is loaded via `<link>` in `BaseLayout.kt` after the PicoCSS CDN link (using the existing `headExtra` hook pattern established in Phase 12). The `data-theme="dark"` attribute is removed from `<html>` — theme is fully controlled by `prefers-color-scheme`.
- **D-07:** Material 3 baseline dark surface palette: `surface=#1c1b1f`, `on-surface=#e6e1e5`, `primary=#d0bcff`, `on-primary=#381e72`, `secondary=#ccc2dc`, `surface-variant=#49454f`, `outline=#938f99`. Light palette overrides defined in the same file under `@media (prefers-color-scheme: light)`.

### Dynamic Data — Zones Selector + Status Page
- **D-08:** **Zone selector on Send Text form (UI-04):** Server-side rendered (SSR) — `IndexPage` constructor receives the list of `ZoneStatus` objects from `ZoneRegistry`. The `<select>` is populated at request time. No JS fetch needed for the zone selector.
- **D-09:** **`/status` page (UI-08):** Data is loaded via `fetch()` calls in `app.js` on `DOMContentLoaded`. The page shell is rendered by Ktor; health/metrics data is populated client-side by calling `GET /health/detail` and `GET /metrics`. Auto-refresh every 10 seconds via `setInterval`.
- **D-10:** `StatusPage.kt` is simplified to a shell template — it no longer receives driver/status constructor params. All live values are fetched and rendered by `app.js` into labelled `<span id="...">` placeholders.

### Effect Preview on Send Form
- **D-11:** A `<div id="effectPreview">` is added below the effect selector in `IndexPage.kt`. It displays the typed text with a CSS animation matching the selected effect.
- **D-12:** Four CSS `@keyframes` animations defined in `custom.css`: `scroll-preview` (translateX), `blink-preview` (opacity step), `reverse-preview` (scaleX(-1) then scaleX(1)), `fade-preview` (opacity ease in-out loop). The active animation class is applied by `app.js` when the user changes the effect select.
- **D-13:** The preview updates live as the user types (same `input` event handler that drives the char counter).

### Page-Level Refresh (Schedules, History, Zones)
- **D-14:** **Schedules (`/schedule`):** Existing `SchedulePage.kt` extended to render a `zone` column and `webhookUrl` column. Expandable rows use the existing `<details>/<summary>` HTML pattern (already used in History page). No JS needed for expansion.
- **D-15:** **History (`/history`):** Existing `HistoryPage.kt` already has pagination and card layout from Phase 9. Phase 13 adds zone and effect filter `<select>` elements to the filter bar. Filter is submitted as a GET form (`?zone=X&effect=Y`) to keep it server-side — no JS required.
- **D-16:** **Zones (`/zones`):** Existing `ZonesPage.kt` extended to show online/offline badge per zone (from `ZoneStatus.status`), highlight auto-discovered zones, and surface the "Add by IP" form that already exists as a route (`POST /api/v1/zones/{ip}`). The add-by-IP form is an HTML `<form>` with `method="post"` pointing at the API route.

### Agent's Discretion
- Exact CSS class naming for the side nav toggle and overlay backdrop
- Whether the hamburger button uses a `<button>` or `<a>` element
- Specific `@keyframes` easing curves for the effect preview animations
- Order of items in the side nav (follow UI-03: Send Text, Schedules, History, Zones, Status)
- Whether `custom.css` also contains card/grid helper classes or relies solely on PicoCSS article/grid

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing UI layer
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — current layout; replace top nav with side panel, add custom.css link
- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt` — Send Text form; add zone selector (SSR) + effect preview div
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` — Schedules table; add zone + webhookUrl columns, expandable rows
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` — History page; add zone/effect filter selects
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` — Zones page; add status badges, auto-discovered highlight, add-by-IP form
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` — Status page shell; strip constructor params, add placeholder spans for JS population
- `src/main/kotlin/com/anjo/web/templates/ErrorPage.kt` — Error page; must work with new side nav layout

### Static assets
- `src/main/resources/static/app.js` — existing JS; add hamburger toggle logic, effect preview handler, /status auto-refresh fetch
- `src/main/resources/static/` — location for new `custom.css`

### Routes that supply data
- `src/main/kotlin/com/anjo/routing/` — UI routes that instantiate page templates; `IndexPage` route must pass `ZoneRegistry.listAll()` to constructor
- `GET /api/v1/zones` — used by app.js? No — zone list is SSR (D-08)
- `GET /health/detail` — polled by app.js for /status page (D-09, D-10)
- `GET /metrics` — polled by app.js for /status page (D-09, D-10)

### Requirements
- `REQUIREMENTS.md` §UI-01 through UI-08 — full requirement text for all 8 UI requirements
- `REQUIREMENTS.md` §Out of Scope — confirms no JS framework, no React/Vue/HTMX

### Phase 12 observability endpoints (feeds /status)
- `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` (or equivalent) — `GET /health/detail` response shape for JS parsing
- `src/main/kotlin/com/anjo/routing/MetricsRoutes.kt` — `GET /metrics` response shape for JS parsing

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BaseLayout.render(pageTitle, activePath, content)` — single entry point for all pages; changing the layout here propagates to all pages automatically
- `headExtra` hook in BaseLayout (added Phase 12) — use to inject `custom.css` link once
- `app.js` static script — already loaded on every page; add to it for hamburger, effect preview, and status fetch
- `<details>/<summary>` expansion pattern — already used in HistoryPage for schedule details; reuse for expandable schedule rows (D-14)
- `ZoneRegistry.listAll(): List<ZoneStatus>` — available via DI; inject into IndexPage route handler for SSR zone selector
- `GET /health/detail` response: flat JSON with `uptime`, `memoryUsed`, `memoryMax`, `displayStatus`, `totalFailures`, `zoneErrors` — parse in app.js
- `GET /metrics` response: `MetricsResponse` with groups `runtime`, `api`, `hardware` — parse `hardware` group in app.js for /status page

### Established Patterns
- Ktor HTML DSL (`kotlinx.html`) — all templates use `FlowContent.()` lambdas; no string concatenation
- `attributes["aria-current"] = if (activePath == x) "page" else ""` — existing active-link pattern in BaseLayout nav; preserve for side nav
- PicoCSS v2 semantic HTML — `<article>` renders as a card, `<nav><ul><li><a>` renders as nav; use these semantic elements for Material 3 card appearance
- `select { option { value = "SCROLL"; selected = true } }` — kotlinx.html select pattern from IndexPage
- `GET /history?page=N&size=M` — existing paginated history route; extend with `&zone=X&effect=Y` query params

### Integration Points
- `IndexPage` route handler in `src/main/kotlin/com/anjo/routing/ui/` — must receive `ZoneRegistry` via DI and pass zone list to `IndexPage` constructor
- `StatusPage` route handler — simplify to render the shell (no more driver status constructor params needed)
- `BaseLayout.kt` — single change propagates new layout to all 5+ pages; be careful not to break `ErrorPage` which also uses BaseLayout

</code_context>

<specifics>
## Specific Ideas

- Material 3 side nav panel on desktop: ~240px wide fixed sidebar, app title at top, nav links with icon (text emoji or Unicode icon) + label, active item has `primary-container` background
- Effect preview div should look like a mini LED display — dark background, monospace font, fixed height ~2 lines, overflow hidden
- The hamburger overlay on mobile should have a semi-transparent backdrop (`rgba(0,0,0,0.4)`) that closes the nav on click
- Zone selector: show zone name + "(OFFLINE)" suffix for zones where `status == "OFFLINE"` so user knows it may not respond

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-UI/UX Refresh*
*Context gathered: 2026-06-18*

