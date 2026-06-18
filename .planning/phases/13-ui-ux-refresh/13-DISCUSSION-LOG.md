# Phase 13: UI/UX Refresh - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-18
**Phase:** 13-ui-ux-refresh
**Areas discussed:** Side nav on mobile, Material 3 color system, Dynamic data (zones + status), Effect preview on Send form

---

## Side Nav on Mobile

| Option | Description | Selected |
|--------|-------------|----------|
| Hamburger button (JS toggle) | A ☰ button toggles the side panel. Needs ~10 lines of JS in app.js. No extra dependency. | ✓ |
| Icon-only strip on small screens | Side nav collapses to icon-only strip on small screens. Always visible, no toggle needed. | |
| Bottom nav bar on mobile | Side nav becomes a horizontal bar at the bottom on mobile (like mobile apps). | |
| Stacked above content (CSS only) | Side nav just stacks above the content on mobile (simple responsive flex column). | |

**User's choice:** Hamburger button (JS toggle)
**Notes:** Adds ~10 lines to app.js. Side panel hidden on mobile by default, toggled via hamburger ☰ button in top bar.

---

## Material 3 Color System

| Option | Description | Selected |
|--------|-------------|----------|
| custom.css with M3 tokens (override PicoCSS) | Add a custom.css static file with --md-sys-color-* tokens + override PicoCSS vars. No new dependency, CDN stays as-is. | ✓ |
| MWC component library from CDN | Use @material/web (MWC) from CDN — brings real M3 components (FAB, card, etc.) but adds a JS bundle and HTML changes. | |
| PicoCSS colour tweaks only (no new file) | Keep PicoCSS as-is, tweak just the colors to look Material-ish. Minimal work, imperfect M3 fidelity. | |

**User's choice:** custom.css with M3 tokens (override PicoCSS)
**Notes:** New `src/main/resources/static/custom.css` file with `--md-sys-color-*` custom properties. `prefers-color-scheme` controls light/dark. `data-theme="dark"` removed from `<html>`. PicoCSS vars overridden.

---

## Dynamic Data (Zones + Status)

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side rendered (Ktor, snapshot) | Page renders zones/status from Ktor at request time — simple, no JS fetch, but the zone list / health numbers are a snapshot. | |
| JS fetch on page load (+ auto-refresh for /status) | Zone selector and /status data loaded via fetch() in app.js on page load. Status page auto-refreshes every ~10s. | |
| Hybrid — SSR zones, JS-fetch for /status | Zone selector is server-rendered (SSR). /status page data is JS-fetched with auto-refresh (best of both). | ✓ |

**User's choice:** Hybrid — SSR zones, JS-fetch for /status (recommended by agent)
**Notes:** User asked "co polecasz?" (what do you recommend?). Agent recommended hybrid: zone selector is SSR (zones are DB-persisted, rarely change, no JS overhead), /status page is JS-fetched with auto-refresh every 10s (live monitoring data). User confirmed.

---

## Effect Preview on Send Form

| Option | Description | Selected |
|--------|-------------|----------|
| CSS animation preview (live, no extra library) | A small <div> with CSS @keyframes animations shows how SCROLL, BLINK, REVERSE, FADE will look. Updates live as user types. Pure CSS + a few lines of JS. | ✓ |
| Text description only | Below the effect selector, a <small> tag shows a description: e.g. "Text scrolls from right to left across the display." | |
| Skip preview (not worth the complexity) | No preview — just the effect dropdown selector as-is. | |

**User's choice:** CSS animation preview (live, no extra library)
**Notes:** Live preview `<div>` with CSS @keyframes for SCROLL/BLINK/REVERSE/FADE. Updates as user types (same `input` event as char counter). Animations defined in custom.css.

---

## Agent's Discretion

- Exact CSS class naming for side nav toggle and overlay backdrop
- Whether hamburger button uses `<button>` or `<a>` element
- Specific `@keyframes` easing curves for effect preview animations
- Order of items in the side nav (follow UI-03 order: Send Text, Schedules, History, Zones, Status)
- Whether `custom.css` includes card/grid helpers or relies solely on PicoCSS `<article>`/grid

## Deferred Ideas

None — discussion stayed within phase scope.

