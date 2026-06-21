---
phase: 13-ui-ux-refresh
plan: "01"
subsystem: ui-layer
tags: [css, layout, material3, side-nav, dark-mode]
dependency_graph:
  requires: []
  provides: [custom.css, BaseLayout-side-nav, headExtra-hook]
  affects: [all page templates, IndexPage, SchedulePage, HistoryPage, ZonesPage, StatusPage, ErrorPage]
tech_stack:
  added: [custom.css (MD3 token system)]
  patterns: [prefers-color-scheme media query, aside side nav, CSS custom properties, PicoCSS variable override]
key_files:
  created:
    - src/main/resources/static/custom.css
  modified:
    - src/main/kotlin/com/anjo/web/templates/BaseLayout.kt
decisions:
  - "headExtra defaults to null so all 6 existing callers (IndexPage, SchedulePage, HistoryPage, ZonesPage, StatusPage, ErrorPage) compile unchanged"
  - "Dark palette declared in :root (default), light overrides only under @media (prefers-color-scheme: light) — matches D-04"
  - "data-theme attribute removed from html element; theme fully controlled by CSS custom properties"
  - "navToggle button uses aside h1 TextReaderRpi label in side nav, top-bar has plain text + hamburger"
metrics:
  duration: "2 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  files_created: 1
  files_modified: 1
---

# Phase 13 Plan 01: Foundation CSS + BaseLayout Side Nav Summary

**One-liner:** Material 3 dark/light CSS token system with side nav layout + rewritten BaseLayout replacing header top-nav with aside panel, mobile hamburger, and headExtra hook.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create custom.css with MD3 tokens, PicoCSS overrides, side-nav layout, animations | fb8e65a | src/main/resources/static/custom.css |
| 2 | Rewrite BaseLayout.kt — aside side nav + mobile top bar + custom.css link + headExtra hook | 7e8d9ea | src/main/kotlin/com/anjo/web/templates/BaseLayout.kt |

---

## What Was Built

### custom.css (new file)

- 11 `--md-sys-color-*` CSS custom properties in `:root` (dark Material 3 baseline palette)
- PicoCSS variable overrides mapping `--pico-background-color`, `--pico-color`, `--pico-primary`, `--pico-primary-foreground`, `--pico-secondary`, `--pico-border-color`, `--pico-card-background-color` to MD3 tokens
- `@media (prefers-color-scheme: light)` block redefining all `--md-sys-color-*` tokens with light palette values
- Side nav `aside` layout: 240px fixed, full viewport height, `surface-variant` background
- Active nav link: `aria-current="page"` triggers `background-color: var(--md-sys-color-primary)`, `color: var(--md-sys-color-on-primary)`, `border-radius: 4px`
- Desktop `@media (min-width: 960px)`: `main { margin-left: 240px; max-width: 1200px; margin-right: auto; }`, `.top-bar { display: none; }`
- Mobile `@media (max-width: 959px)`: `aside { transform: translateX(-100%); transition: transform .2s ease; }`, `body.nav-open aside { transform: translateX(0); }`, `body.nav-open .nav-backdrop { display: block; }`
- `.top-bar` flex container with 44px min-height hamburger button (accessibility minimum)
- `.nav-backdrop`: fixed overlay at z-index 1, `rgba(0,0,0,0.4)`, hidden by default
- `.effect-preview` and four animation classes (`.effect-scroll`, `.effect-blink`, `.effect-reverse`, `.effect-fade`)
- `@keyframes scroll-preview`, `blink-preview`, `reverse-preview`, `fade-preview` as specified in UI-SPEC
- `.zone-card--discovered { background-color: var(--md-sys-color-secondary-container); color: var(--md-sys-color-on-secondary-container); }`

### BaseLayout.kt (modified)

- `render()` signature: `fun render(pageTitle: String, activePath: String, headExtra: (HEAD.() -> Unit)? = null, content: FlowContent.() -> Unit): String`
- Removed `attributes["data-theme"] = "dark"` from `<html>` element
- Added `link(rel = "stylesheet", href = "/static/custom.css")` after PicoCSS CDN link
- Added `headExtra?.invoke(this)` as last statement in `head { }` block
- Mobile top bar: `div { attributes["class"] = "top-bar" }` with app name text + `button { id = "navToggle"; +"☰" }`
- Side nav `aside { }` with `h1 { +"TextReaderRpi" }` + `nav { ul { ... } }` with exactly 5 links
- Nav links: Send Text (`/`), Schedules (`/schedule`), History (`/history`), Zones (`/zones`), Status (`/status`) — each with `aria-current` active-link pattern
- `div { id = "navBackdrop"; attributes["class"] = "nav-backdrop" }` for mobile backdrop
- Removed `/settings/display` link entirely (D-25)
- Removed `import kotlinx.html.header` (no longer used)
- Added imports: `aside`, `button`, `HEAD`, `div`, `id`

---

## Deviations from Plan

None - plan executed exactly as written.

---

## Threat Flags

No new threat surface introduced beyond what the plan's threat model covers. `custom.css` is a static file with no secrets. `activePath` in `BaseLayout.kt` remains hardcoded per call site, never user-controlled.

---

## Known Stubs

None. This plan delivers structural CSS and layout foundation only — no data wiring or placeholder content.

---

## Self-Check: PASSED

- `src/main/resources/static/custom.css` exists: FOUND
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` modified: FOUND
- Commit fb8e65a exists: FOUND
- Commit 7e8d9ea exists: FOUND
- `./gradlew compileKotlin` exits 0: PASSED
- CSS verification grep gates: PASSED
- BaseLayout verification grep gates: PASSED
