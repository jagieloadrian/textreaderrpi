# Phase 13: UI/UX Refresh — Research

**Phase:** 13 — ui-ux-refresh
**Date:** 2026-06-20
**Confidence:** MEDIUM (HIGH for architecture and pitfalls; LOW for light palette color values)

---

## Summary

No new dependencies are needed. The entire phase uses technology already in the project: the kotlinx.html DSL, PicoCSS v2 (CDN), and vanilla JS in `app.js`. The biggest risk is the **dual-render divergence** pattern: `SchedulePage.kt` (SSR) and `renderScheduleList()` in `app.js` must be kept in sync.

---

## Architecture

### Stack (unchanged)
- **Server-side templates:** `kotlinx.html` DSL (Kotlin lambda-based HTML DSL) — all templates use `FlowContent.()` lambdas
- **CSS base:** PicoCSS v2 (CDN) — keeps semantic HTML resets; new `custom.css` adds Material 3 tokens on top
- **Client-side:** Vanilla JS in `src/main/resources/static/app.js`; no framework
- **Backend framework:** Ktor

### Layout cascade
`BaseLayout.kt` is the **single propagation point** — all pages call `BaseLayout.render()`. Replacing `<header>` with `<aside>` + `<main>` in `BaseLayout.kt` propagates to all 5+ pages simultaneously. `ErrorPage.kt` inherits the change with zero edits (confirmed by CONTEXT.md D-26).

### New file: `custom.css`
Written to `src/main/resources/static/custom.css` and loaded globally from `BaseLayout.kt` `<head>` after the PicoCSS CDN link. Contains:
- `--md-sys-color-*` CSS custom properties (dark palette default, light palette under `@media (prefers-color-scheme: light)`)
- Pico override mappings (`--pico-background-color`, etc.)
- Side nav layout CSS (aside + main, desktop fixed panel, mobile toggle)
- `@keyframes` for effect preview animations
- `.zone-card--discovered` tint class

### kotlinx.html `headExtra` hook
`BaseLayout.render()` gains `headExtra: (HEAD.() -> Unit)? = null`. The HEAD type is the `kotlinx.html` receiver of the `head {}` block. All existing callers compile unchanged with the default `null`.

---

## File-by-File Changes

| File | Change |
|------|--------|
| `BaseLayout.kt` | Replace `<header>` nav with `<aside>` + `<main>`, add custom.css `<link>`, add headExtra hook, remove data-theme attr, remove /settings/display link |
| `IndexPage.kt` | Add zone `<select>` (SSR from ZoneRegistry), add effect preview `<div>` |
| `SchedulePage.kt` | Add zone + webhookUrl columns to SSR table, add zone `<select>` to create form; constructor receives zone list |
| `HistoryPage.kt` | Add zone + effect filter `<select>` elements; build pagination links with filter params |
| `ZonesPage.kt` | Add online/offline badge per zone, `.zone-card--discovered` class on auto-discovered cards, keep JS-driven add-by-IP and delete buttons |
| `StatusPage.kt` | Render skeleton with `<span id="status-...">Loading…</span>` placeholders, no constructor params |
| `ErrorPage.kt` | **No changes** — inherits new layout automatically via BaseLayout |
| `SettingsPage.kt` | **DELETE** (D-25) |
| `WebRoutes.kt` | Remove `GET /settings/display` route; pass `ZoneRegistry.listAll()` to IndexPage + SchedulePage constructors; simplify StatusPage route |
| `app.js` | Add hamburger toggle (~10 lines), effect preview listener, `/status` fetch + setInterval(10s), update `renderScheduleList()` + `createSchedule()` for zone/webhookUrl, append `?zone=X` in `submitForm()` |
| `custom.css` | **NEW** — MD3 color tokens, PicoCSS overrides, side nav layout, effect preview keyframes, zone card tint |
| `HistoryService.kt` | Extend `getHistory()` with optional `zone: String? = null` + `effect: String? = null` params |
| `HistoryRepository.kt` | Extend paginated query with `andWhere { HistoryTable.zoneId eq zone }` and `andWhere { HistoryTable.effect eq effect }` (same pattern already used for `source` filter) |

---

## Critical Pitfalls

### 1. Dual-Render Divergence (HIGH RISK)
Both `SchedulePage.kt` (SSR initial render) and `renderScheduleList()` in `app.js` (JS re-render after every create/stop/delete) must be updated with zone + webhookUrl columns **in the same wave**. If one is updated without the other, the JS re-render silently drops the new columns on every user action. This is the top planning risk for this phase.

### 2. Settings Page Test Breakage
`WebAndDisplayRoutesTest` has a test case for `GET /settings/display`. When `SettingsPage.kt` and the route are deleted (D-25), this test must also be deleted. The `applyDriver()` function in `app.js` should also be removed.

### 3. History Filter: Zone is NOT Yet Wired
`HistoryRepository.findPaginated()` and `HistoryService.findPaginated()` already accept `effect` and `source` filter params but NOT yet `zone`. Adding `zone: String? = null` follows the identical `andWhere { HistoryTable.zoneId eq zone }` pattern already used for `source` — but this must be implemented in this phase (D-22).

### 4. WaveOrdering: BaseLayout Must Go First
`BaseLayout.kt` must be modified before any page template that references the new layout structure. Plan as Wave 1 to avoid merge conflicts.

### 5. ZoneRegistry DI Injection
`IndexPage` and `SchedulePage` route handlers in `WebRoutes.kt` must have `ZoneRegistry` injected. Verify the existing Ktor DI pattern (likely `application.attributes[ZoneRegistry]` or constructor injection via Koin) before planning the route handler changes.

---

## Validation Architecture

### Unit Tests
- `HistoryRepositoryTest` — add test for zone filter returning only matching zone records
- `HistoryServiceTest` — add test that zone + effect params are forwarded to repository

### Integration Tests
- `WebAndDisplayRoutesTest` — DELETE the `GET /settings/display` test case; ADD test for `GET /history?zone=X` returning filtered results
- `WebRoutesTest` — verify `GET /` returns HTML containing a `<select>` with zone options
- `WebRoutesTest` — verify `GET /schedule` returns HTML containing zone column headers

### Manual / Visual
- Side nav desktop: 240px sidebar visible, active link highlighted
- Side nav mobile: hamburger toggles overlay nav, backdrop closes it
- Dark mode: no `data-theme` attr needed — OS-level dark mode switches palette automatically
- Effect preview: typing in send form updates preview div with correct animation class
- Status page: Loading… spans replaced within 1 second on page load; values refresh every 10s

---

## Open Questions

1. **`headExtra` receiver type** — `HEAD` in `import kotlinx.html.*` is the standard receiver for the `head {}` block. Recommend grepping `BaseLayout.kt` for the exact `head {` usage to confirm the receiver before writing the signature.
2. **History Zone filter: SSR dropdown vs free-text** — CONTEXT.md D-15 specifies `<select>`, so populate SSR from `ZoneRegistry.listAll()` (same pattern as IndexPage). Confirmed: no free-text input.
3. **Auto-discovered zone detection field** — CONTEXT.md D-28 references `ZoneStatus.discoveredVia`. Verify whether values are `"UDP"`, `"MDNS"`, or `"MANUAL"` to correctly apply `.zone-card--discovered`. Read `ZoneStatus` data class before implementing ZonesPage.

---

## RESEARCH COMPLETE

**Summary:** Phase 13 is a pure frontend/CSS/template change with one small backend extension (History filter). No new packages. Key risk is dual-render divergence on Schedules. Plan in 4 waves: (1) BaseLayout + CSS foundation, (2) page templates + backend history filter, (3) app.js extensions + zone routing, (4) tests.
