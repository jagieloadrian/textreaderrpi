# Phase 13 — UI Review

**Audited:** 2026-06-21
**Baseline:** 13-UI-SPEC.md (approved design contract)
**Screenshots:** Not captured — no dev server detected on ports 3000, 5173, or 8080

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Primary CTA on Send Text page is "Send" — spec requires "Send Text" |
| 2. Visuals | 2/4 | REVERSE effect has no animation keyframes — static CSS only; badge uses wrong HTML element |
| 3. Color | 1/4 | Full MD3 token system never implemented — only 2 tokens defined; hardcoded hex throughout |
| 4. Typography | 2/4 | App title renders at 16px — spec requires 24px; history-meta uses undeclared 0.8rem size |
| 5. Spacing | 3/4 | Scale largely honored; minor non-scale values in nav internals (10px, 12px, 2px) |
| 6. Experience Design | 3/4 | State coverage solid; REVERSE preview nonfunctional; zone delete toast text deviates |

**Overall: 13/24**

---

## Top 3 Priority Fixes

1. **Missing MD3 token system in custom.css** — All color roles throughout the app resolve through PicoCSS defaults rather than the specified Material 3 palette. The spec's 11 `--md-sys-color-*` properties are absent from the file entirely. Hardcoded badge/button/toast colors (`#c0392b`, `#e67e22`, `#b91c1c`) are scattered across ZonesPage.kt and app.js instead of token references. Additionally, dark mode is not the default: light palette values sit in `:root` and dark overrides sit under `@media (prefers-color-scheme: dark)` — the spec explicitly requires the inverse. Fix: add all `--md-sys-color-*` token declarations to `custom.css` `:root` with light overrides under `@media (prefers-color-scheme: light)`, then replace hardcoded hex values in ZonesPage.kt (lines 53-55, 73) and app.js (line 16) with token references.

2. **Send Text CTA label reads "Send" instead of "Send Text"** — `IndexPage.kt:73` has `+"Send"`. The copywriting contract explicitly specifies "Send Text" for the primary CTA on the Send Text page. Every other primary CTA label matches the spec exactly. Fix: change the string on line 73 to `+"Send Text"`.

3. **REVERSE effect preview is a static mirror, not an animated preview** — `custom.css` has no `@keyframes reverse-preview` rule. The `.effect-reverse` class applies `transform: scaleX(-1)` statically. The spec requires `@keyframes reverse-preview`: `0%–49% { transform: scaleX(1); } 50%–100% { transform: scaleX(-1); }` at 2s step-start infinite. Without this animation, selecting REVERSE shows mirrored static text instead of the flipping behavior users will see on the physical display. Fix: add the `@keyframes reverse-preview` rule to `custom.css` and change `.effect-reverse` to use `animation: reverse-preview 2s step-start infinite` instead of the static transform property.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

**BLOCKER — Primary CTA label mismatch:**
- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt:73` — button reads `+"Send"`. Spec says "Send Text". All other primary CTAs match their contract labels.

**WARNING — Empty state copy deviations:**
- `ZonesPage.kt:35` — heading reads `"No zones registered"` (no period). Spec: `"No zones registered."`.
- `ZonesPage.kt:36` — body reads `"No display zones found. Connect a local display and restart, or scan the network to discover nearby devices."`. Spec omits the "No display zones found." prefix sentence.
- `HistoryPage.kt:103-104` — single `<p>` reads `"No display events recorded yet. Send text via the home page to see history here."`. Spec declares heading `"No display history yet."` and separate body `"Send text to the display to start recording events."` — both structure and wording deviate.
- `app.js:189` — schedule list JS empty state shows `"No schedules yet."` with no body sentence. Spec: heading plus body `"Use the form above to create a recurring or one-shot schedule."` — body sentence missing.

**WARNING — History filter option labels are raw uppercase:**
- `HistoryPage.kt:50,59` — effect and source filter selects render `"ALL"` as the no-filter option. The zone filter (line 73) correctly uses `"All zones"`. The all-caps `"ALL"` is inconsistent with sentence-case style used everywhere else in the UI.

**PASS — Matching labels confirmed:**
- "Create Schedule" (SchedulePage.kt:77), "Add Zone" (ZonesPage.kt:107), "Scan for Displays" (ZonesPage.kt:89 + app.js:321), "All zones" (IndexPage.kt:34 + HistoryPage.kt:73), "No specific zone" (SchedulePage.kt:68), "Loading..." (StatusPage.kt:18-30), "Unavailable" (app.js:145,165,171), toast messages "Text sent"/"Schedule created"/"Zone not found."/"Zone offline." — all match spec.

---

### Pillar 2: Visuals (2/4)

**BLOCKER — REVERSE effect has no animation keyframes:**
- `custom.css` defines `@keyframes scroll-preview` (line 254), `@keyframes blink-preview` (line 259), `@keyframes fade-preview` (line 264) — but no `@keyframes reverse-preview`. The `.effect-reverse` class (line 248-250) applies only `display: inline-block; transform: scaleX(-1)` — a permanent static mirror. The spec requires a step-start 2s animation alternating between `scaleX(1)` and `scaleX(-1)`. Selecting REVERSE gives users no preview of the actual time-based flip behavior.

**WARNING — Status badge uses `<mark>` element instead of spec'd `<span>`:**
- `ZonesPage.kt:52-56` — badge rendered via `<mark style="...">`. Spec (Component Inventory, Status Badges) defines inline CSS on a `<span>` with `padding: 2px 8px; border-radius: 4px`. Using `<mark>` is semantically wrong (mark denotes highlighted search text) and PicoCSS applies its own yellow-background default styles to `<mark>`, requiring inline style overrides that may not survive all PicoCSS versions.

**WARNING — Zone selector shows raw IDs, not zone names:**
- `IndexPage.kt:37-39` — zone option labels display `zone.id`. If `ZoneStatus` has a human-readable `name` field it is not used. Showing UUIDs or technical identifiers in a user-facing select degrades scannability. OFFLINE suffix is correct in format but appended to the raw ID.

**WARNING — Collapsed nav icon-only state lacks accessible names:**
- `BaseLayout.kt:67-71` — `#navCollapseBtn` has `title` attribute but no `aria-label`. When sidebar is collapsed, nav `<a>` elements render icon-only with `.nav-label` hidden. The SVG icons have `aria-hidden="true"`, leaving the anchor elements with no accessible name in collapsed state. (The collapse feature is an agent addition beyond spec scope, but the accessibility gap is real.)

**PASS — Visual structure:**
- Side nav 240px fixed `<aside>` — correct.
- `<article>` card pattern across all pages (D-30) — correct.
- Active nav link `aria-current="page"` pattern — correct.
- Mobile hamburger `min-height: 44px` touch target (custom.css:176) — correct.
- Effect preview styled as dark LED display with monospace font, `#0d0d0d` background — correct.
- Nav backdrop `rgba(0,0,0,0.4)` overlay — correct.

---

### Pillar 3: Color (1/4)

**BLOCKER — MD3 token system not implemented:**
- `custom.css` `:root` block defines only 3 values: `--pico-primary`, `--pico-primary-foreground`, `--app-tint`. None of the 11 `--md-sys-color-*` properties mandated by the spec exist. Querying `grep "md-sys-color" custom.css` returns zero results.
- Missing tokens: `--md-sys-color-surface`, `--md-sys-color-on-surface`, `--md-sys-color-secondary`, `--md-sys-color-surface-variant`, `--md-sys-color-outline`, `--md-sys-color-secondary-container`, `--md-sys-color-on-secondary-container`, `--md-sys-color-error`, `--md-sys-color-on-error`.
- The PicoCSS variable mappings from the spec (`--pico-background-color: var(--md-sys-color-surface)`, `--pico-border-color: var(--md-sys-color-outline)`, etc.) are entirely absent.

**BLOCKER — Dark mode is not the default:**
- `custom.css:1-6` — `:root` sets light palette values (`#6750a4`, `#ffffff`). Dark values are defined under `@media (prefers-color-scheme: dark)` (lines 8-14). Spec (D-04, D-07) explicitly states dark palette is default in `:root`, light palette as `@media (prefers-color-scheme: light)` overrides. On devices with no color scheme preference set, the app renders the light/PicoCSS default palette instead of MD3 dark surface.

**WARNING — Status badge colors hardcoded with wrong values:**
- `ZonesPage.kt:53` — OFFLINE: `background: var(--pico-color-red-500, #c0392b); color: white`. Spec: `background: var(--md-sys-color-error)` (dark: #cf6679 / light: #b3261e) + `color: var(--md-sys-color-on-error)`.
- `ZonesPage.kt:54` — DEGRADED: `background: var(--pico-color-orange-500, #e67e22); color: white`. Spec: `background: #7d5c00; color: #ffe0b2`.
- `ZonesPage.kt:73` — Remove zone button: `background: var(--pico-color-red-500, #c0392b)`. Spec: `--md-sys-color-error`.

**WARNING — Toast error background hardcoded:**
- `app.js:16` — `background: type === "error" ? "#b91c1c" : "#166534"`. Neither value is a spec token. `#b91c1c` (Tailwind red-700) ≠ `--md-sys-color-error` (#cf6679 dark).

**WARNING — `.zone-card--discovered` uses non-spec token:**
- `custom.css:278-280` — `.zone-card--discovered { background: var(--app-tint); border-color: var(--pico-primary); }`. Spec requires `background-color: var(--md-sys-color-secondary-container)`. `--app-tint` is a custom rgba tint not in the spec. Spec also does not define a border-color change for discovered cards.

**PASS — Accent primary color values match:**
- Dark accent `#d0bcff` (custom.css:10) = spec `--md-sys-color-primary` dark value.
- Light accent `#6750a4` (custom.css:3) = spec `--md-sys-color-primary` light value.
- Active nav link uses `background: var(--pico-primary)` which resolves correctly.
- Effect preview `color: #d0bcff` and `background: #0d0d0d` — match spec exactly.

---

### Pillar 4: Typography (2/4)

**WARNING — App title in side nav renders at 16px instead of 24px:**
- `custom.css:49` — `.aside-title { font-size: 1rem; }`. Spec declares the Display role (page title in side nav): `24px, weight 600 (semibold), line-height 1.2`. The weight 600 is correct; the size is wrong (1rem = 16px under default PicoCSS, not 24px).

**WARNING — `history-meta` uses 0.8rem — outside the declared 4-size scale:**
- `custom.css:317` — `.history-meta { font-size: 0.8rem; opacity: 0.65; }`. 0.8rem resolves to approximately 12.8px. The spec declares exactly 4 font sizes: 14px (label), 16px (body), 20px (h2), 24px (display). 12.8px is not on the scale. The secondary metadata role maps to the 14px label size.

**PASS — Core typography:**
- Body 16px — PicoCSS default, not overridden, consistent with spec.
- Effect preview `font-size: 1rem` (16px) — correct per spec (body size).
- Effect preview `font-family: monospace` — correct.
- Only 2 font weights used: 400 (body via PicoCSS) and 600 (`.aside-title`) — matches the 2-weight constraint.
- No bold (700) or light (300) weights introduced.

---

### Pillar 5: Spacing (3/4)

**WARNING — Non-scale values in nav internal layout:**
- `custom.css:99` — `gap: 12px` on `aside nav a`. Scale defines 8px (sm) and 16px (md); 12px is absent from the spec scale.
- `custom.css:45` — `gap: 10px` in `.aside-title`. Not on scale.
- `custom.css:93` — `margin-bottom: 2px` on `aside nav ul li`. xs minimum is 4px.

**WARNING — Collapsed sidebar width 52px not on scale:**
- `custom.css:121` — `body.nav-collapsed aside { width: 52px; }`. Agent-added feature. If spec'd, nearest scale value would be 48px (3xl).

**PASS — Spec scale values confirmed:**
- Side nav `padding: 8px` (sm = 8px) — correct.
- Effect preview `padding: 0 16px` (md = 16px) — correct.
- `.zones-grid gap: 16px` (md = 16px) — correct.
- Desktop `min-width: 960px` breakpoint — matches spec exactly.
- Desktop `padding-left: 240px` content offset — matches spec exactly.
- Max-width 1200px is set via `padding-left` on body (240px) + PicoCSS container — structural intent met.
- `padding: 4px 8px` on aside header — 4px (xs) and 8px (sm) both on scale.

---

### Pillar 6: Experience Design (3/4)

**WARNING — REVERSE effect preview nonfunctional:**
- Selecting REVERSE in the effect dropdown applies a static horizontal mirror. No animation plays. Users cannot preview the actual time-based behavior on the physical LED display (D-11, D-12). This is a functional gap in the Send Text page's primary feature.

**WARNING — Zone delete feedback uses inline text, not the spec'd toast:**
- `app.js:352` — `resultDiv.textContent = "Removed. Reloading..."`. Spec (D-24): "After successful DELETE, zone card is removed from DOM and toast shows 'Zone removed'". The implementation uses an inline result div, not a toast, and the text is "Removed. Reloading..." not "Zone removed".

**WARNING — Zone offline toast missing zone name interpolation:**
- `app.js:83` — `showToast("Zone offline. Text not sent.", "error")`. Spec: `"Zone [name] is offline. Text not sent."` with the zone name embedded. When multiple zones exist, the user cannot identify which zone failed.

**PASS — Loading state coverage:**
- StatusPage: all 7 `Loading...` spans render server-side and are replaced by `fetchStatusData()` on DOMContentLoaded — correct.
- Scan button: disabled during scan, re-enabled in `finally` block — correct.
- Schedule list: `loadSchedules()` fires on DOMContentLoaded — correct.

**PASS — Error state coverage:**
- Toast error for network failure present in all async paths (app.js:91, 265, 279, 293).
- `fetchStatusData` on any error: all 7 spans set to "Unavailable" (app.js:169-173) — matches spec.
- Zone 404 → "Zone not found." (app.js:86) — matches spec.

**PASS — Empty state coverage:**
- Schedules: JS renders "No schedules yet." (app.js:189).
- Zones: SSR renders heading and body when zones list is empty (ZonesPage.kt:34-37).
- History: SSR renders empty paragraph when items list is empty (HistoryPage.kt:103-104).

**PASS — Interaction wiring:**
- Hamburger toggle, backdrop close, nav link close — wired in app.js:374-380.
- Effect preview mirrors text on `input` event — correct.
- Status page auto-refresh at 10s interval plus 1s uptime tick — correct (app.js:404-406).
- History filter zone param persists across pagination links — correct (HistoryPage.kt:145).
- `createSchedule()` sends `zoneId: null` when no zone selected — matches D-21.
- Zone delete: no modal confirmation (matches D-24 inline-only contract).

---

## Files Audited

- `src/main/resources/static/custom.css`
- `src/main/resources/static/app.js`
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt`
- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt`
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt`
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt`
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt`
- `.planning/phases/13-ui-ux-refresh/13-UI-SPEC.md`
- `.planning/phases/13-ui-ux-refresh/13-01-SUMMARY.md` through `13-04-SUMMARY.md`
- `.planning/phases/13-ui-ux-refresh/13-CONTEXT.md`
