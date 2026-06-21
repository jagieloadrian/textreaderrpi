# Phase 13 — UI Review

**Audited:** 2026-06-21
**Baseline:** 13-UI-SPEC.md (approved design contract)
**Screenshots:** Not captured (no dev server detected on ports 3000, 5173, 8080)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | "Send" button label diverges from spec "Send Text"; zone offline toast copy differs; general error string wrong |
| 2. Visuals | 2/4 | navToggle has no aria-label; reverse-preview @keyframes missing breaks the Reverse effect animation |
| 3. Color | 1/4 | Entire --md-sys-color-* token system absent; only 2 pico overrides implemented; badge error color wrong (#c0392b vs #cf6679) |
| 4. Typography | 2/4 | aside-title is 1rem (16px) not 24px as specified; .history-meta is 0.8rem — outside declared type scale |
| 5. Spacing | 2/4 | 2px, 6px, 10px, 12px values used — not in the 4/8/16/24/32/48px declared scale |
| 6. Experience Design | 3/4 | "Zone removed" toast missing after delete; error copy for generic failures deviates from spec |

**Overall: 12/24**

---

## Top 3 Priority Fixes

1. **Missing MD3 color token system** — Users see PicoCSS default colors instead of MD3 dark/light palette; OFFLINE badges render with a wrong red (#c0392b Material-2 vs #cf6679 MD3 error); the 60/30/10 surface distribution is not applied — Implement all 11 `--md-sys-color-*` tokens in `custom.css :root` as specified in UI-SPEC.md §Color, map them to `--pico-*` overrides, and add the `@media (prefers-color-scheme: light)` block

2. **"Send" button label must be "Send Text"** — Primary CTA on the index page does not match the copy contract; "Send" is ambiguous — Change `+"Send"` at `IndexPage.kt:73` to `+"Send Text"` to match UI-SPEC.md Copywriting Contract

3. **Missing `@keyframes reverse-preview`** — Selecting the Reverse effect on the Send Text page produces a static mirror with no animation; the preview is broken for that effect — Add `@keyframes reverse-preview { 0%, 49% { transform: scaleX(1); } 50%, 100% { transform: scaleX(-1); } }` with `animation: reverse-preview 2s step-start infinite` to `.effect-reverse` in custom.css, replacing the current static `transform: scaleX(-1)`

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

**WARNING** — Multiple spec copy strings not implemented as written.

**BLOCKER: Primary CTA label wrong**
- Spec: "Send Text" (Copywriting Contract row 1)
- Actual: `+"Send"` (`IndexPage.kt:73`)
- Fix: Change to `+"Send Text"`

**WARNING: Zone offline error toast differs from spec**
- Spec: `"Zone [name] is offline. Text not sent."` (interpolated zone name)
- Actual: `showToast("Zone offline. Text not sent.", "error")` (`app.js:83`) — no zone name interpolated
- Fix: Pass zone name into the toast. Read `zoneSelect.options[zoneSelect.selectedIndex]?.text` for the display name.

**WARNING: General error toast deviates**
- Spec: `"Request failed. Check connection and try again."` (error toast for non-503/404 HTTP errors)
- Actual: `showToast("Failed to send: " + (err || response.status), "error")` (`app.js:88`)
- Fix: Replace with the spec string

**WARNING: Empty state heading missing period**
- Spec: `"No zones registered."` (with period)
- Actual: `+"No zones registered"` (`ZonesPage.kt:35`) — missing trailing period

**WARNING: Zone removed toast absent**
- Spec: Toast shows "Zone removed" after successful DELETE
- Actual: `resultDiv.textContent = "Removed. Reloading..."` then page reload (`app.js:352`) — no `showToast("Zone removed", ...)` call

**PASS: Items confirmed correct**
- "Create Schedule" (`SchedulePage.kt:79`): matches spec
- "All zones" first option (`IndexPage.kt:34`, `HistoryPage.kt:73`): matches spec
- "No specific zone" (`SchedulePage.kt:69`): matches spec
- "Loading..." initial span text (`StatusPage.kt:18-30`): matches spec
- "Unavailable" on fetch error (`app.js:145, 165, 171`): matches spec
- "Scan for Displays" button (`ZonesPage.kt:89`): matches spec
- "Add Zone" button (`ZonesPage.kt:106`): matches spec
- "Zone not found." toast (`app.js:85`): matches spec

---

### Pillar 2: Visuals (2/4)

**WARNING** — Icon buttons lack accessibility labels; reverse animation is broken; collapse feature not in spec.

**WARNING: navToggle button has no aria-label**
- `BaseLayout.kt:57`: `button { id = "navToggle"; unsafe { raw(svgIconHtml(ICON_HAMBURGER)) } }`
- No `aria-label` or visible text. Screen readers announce this as an unlabelled button.
- Fix: Add `attributes["aria-label"] = "Open navigation"` to the navToggle button

**WARNING: navCollapseBtn uses title only, not aria-label**
- `BaseLayout.kt:69`: `attributes["title"] = "Collapse menu"` — `title` is not reliably announced by screen readers
- Fix: Add `attributes["aria-label"] = "Collapse menu"` alongside the title

**WARNING: Reverse effect preview broken — no animation**
- `.effect-reverse` in `custom.css:249-251` only applies `transform: scaleX(-1)` statically; there is no `@keyframes reverse-preview` block in the file
- The spec calls for a cycling animation (0%-49% normal, 50%-100% mirrored)
- Visual impact: Effect Preview shows static mirrored text for Reverse, inconsistent with the other three animated effects

**INFO: Collapsed sidebar not in spec**
- The implementation adds a desktop collapse button (`navCollapseBtn`) and `body.nav-collapsed` state that collapses the sidebar to 52px icon-only view. This is not in the UI-SPEC.md Interaction Contract.
- The feature is additive and does not break spec flows, but it introduces untested interaction surface. Mark as `needs_human_review: true`.

**PASS: Active nav link** correctly uses `aria-current="page"` triggering the primary background pill (`BaseLayout.kt:78`)

**PASS: Visual hierarchy** — h2 on each article card, distinct primary CTA buttons, badge differentiation all present

---

### Pillar 3: Color (1/4)

**BLOCKER** — The MD3 token system specified in UI-SPEC.md §Color is not implemented. The custom.css only redefines 2 PicoCSS variables; the 11 `--md-sys-color-*` custom properties do not exist.

**BLOCKER: --md-sys-color-* tokens absent**
- Spec declares: `--md-sys-color-surface`, `--md-sys-color-on-surface`, `--md-sys-color-primary`, `--md-sys-color-on-primary`, `--md-sys-color-secondary`, `--md-sys-color-surface-variant`, `--md-sys-color-outline`, `--md-sys-color-secondary-container`, `--md-sys-color-on-secondary-container`, `--md-sys-color-error`, `--md-sys-color-on-error`
- Actual `custom.css`: Zero `--md-sys-color-*` declarations exist (confirmed by grep — no matches)
- The PicoCSS overrides in the spec (`--pico-background-color: var(--md-sys-color-surface)` etc.) are also absent
- Impact: Entire dark/light palette is PicoCSS default, not MD3

**BLOCKER: Status badge error color wrong**
- Spec: OFFLINE badge uses `background: var(--md-sys-color-error)` which maps to `#cf6679` in dark mode
- Actual: `ZonesPage.kt:53`: `"background: var(--pico-color-red-500, #c0392b); color: white"` — the fallback `#c0392b` is Material Design 2 red, not MD3
- Fix: Define `--md-sys-color-error: #cf6679` token and use it; or at minimum use `var(--pico-del-color, #cf6679)` as fallback

**WARNING: DEGRADED badge uses hardcoded orange (#e67e22)**
- Spec: `DEGRADED` uses `background: #7d5c00; color: #ffe0b2`
- Actual `ZonesPage.kt:54`: `"background: var(--pico-color-orange-500, #e67e22); color: white"` — both color and text color differ from spec

**WARNING: accent color applied to zone-card--discovered border**
- `custom.css:279`: `.zone-card--discovered { border-color: var(--pico-primary); }`
- Spec: Accent reserved for "active nav link, primary CTA buttons, focus ring" — accent on card borders is an out-of-contract use

**PASS: Effect preview hardcoded colors acceptable**
- `custom.css:220-221`: `.effect-preview { background: #0d0d0d; color: #d0bcff; }` — these exact values are in the UI-SPEC.md Component Inventory for the effect preview div

---

### Pillar 4: Typography (2/4)

**WARNING** — Aside title font size does not match spec; an undeclared small size is in use.

**WARNING: aside-title is 1rem (16px), spec says 24px**
- `custom.css:49`: `.aside-title { font-size: 1rem; }`
- Spec §Typography: "Display (page title in side nav): 24px / semibold"
- The app title "TextReaderRpi" in the sidebar renders at body size, not the larger display size specified
- Fix: Change `.aside-title { font-size: 1.5rem; }` (24px equivalent)

**WARNING: .history-meta is 0.8rem — outside declared scale**
- `custom.css:316`: `.history-meta { font-size: 0.8rem; }` — 0.8rem = ~12.8px
- Spec declares only 4 sizes: 14px, 16px, 20px, 24px; 12.8px is not in the contract
- Fix: Change to `font-size: 0.875rem` (14px) which is the declared "Label" size

**WARNING: hamburger button uses font-size: 20px for text icon in .top-bar button**
- `custom.css:178`: `.top-bar button { font-size: 20px; }`
- The implementation replaced the `☰` text char with an inline SVG icon (`ICON_HAMBURGER` in BaseLayout.kt), so this 20px rule now applies to an SVG container rather than text. Harmless in practice but the CSS rule is stale.

**PASS: Font sizes otherwise align**
- `.effect-preview`: `font-size: 1rem` (16px) — matches spec
- `.aside-title` weight `600` — matches spec semibold requirement for display role
- Only 2 weights observed in use: default (400) and 600 — matches spec "only 2 weights" constraint

---

### Pillar 5: Spacing (2/4)

**WARNING** — Several spacing values fall outside the declared 4/8/16/24/32/48px scale.

**WARNING: Values outside the declared scale**
- `custom.css:93`: `aside nav ul li { margin-bottom: 2px; }` — 2px not in scale (nearest: 4px)
- `custom.css:99`: `aside nav a { gap: 12px; }` — 12px not in scale (nearest: 8px or 16px)
- `custom.css:100`: `aside nav a { padding: 10px 12px; }` — both 10px and 12px not in scale
- `custom.css:45`: `.aside-title { gap: 10px; }` — 10px not in scale
- `custom.css:134`: `.aside-header (collapsed) { gap: 6px; }` — 6px not in scale
- `custom.css:292,294`: `.history-grid { gap: 12px; }` and `.history-grid details { padding: 12px 16px; }` — 12px not in scale

**PASS: Core layout spacing correct**
- Side nav `padding: 8px` (sm = 8px) — in scale
- Top bar `padding: 8px 16px` — both sm and md, in scale
- Effect preview `padding: 0 16px` (md = 16px) — in scale
- Main breakpoint: 960px, content max-width 1200px — matches spec exception values
- Side nav width: 240px — matches spec exception value
- Effect preview height: 48px — matches spec exception value
- Hamburger touch target: 44px min-height — matches accessibility minimum
- `gap: 16px` zones grid and `gap: 8px` history expand buttons — in scale

**INFO: 52px collapsed nav width** is a new value not in spec (expected — it's an undeclared feature)

---

### Pillar 6: Experience Design (3/4)

**WARNING** — Core flows covered well; two specific spec-mandated UX behaviors are missing.

**WARNING: "Zone removed" toast not shown after delete**
- Spec: "After successful DELETE, zone card is removed from DOM and toast shows 'Zone removed'"
- Actual (`app.js:352-353`): Sets `resultDiv.textContent = "Removed. Reloading..."` then calls `window.location.href = "/zones"` after 800ms
- The page reload does remove the card (via full SSR re-render) but `showToast("Zone removed", "success")` is never called
- Fix: Add `showToast("Zone removed", "success")` before the reload timeout in `deleteZone()`

**WARNING: No confirmation for zone deletion**
- The spec explicitly states "No modal confirmation. Inline: button label 'Remove'"
- This is correctly implemented (no confirmation dialog, button labeled "Remove") — this is a PASS
- But the Remove button has no disabled-during-request state (no `btn.disabled = true` pattern during DELETE fetch), unlike `scanForDisplays()` which disables the scan button
- Impact: Double-click can trigger two DELETE requests

**PASS: Loading states**
- StatusPage: All 7 spans initialized with "Loading..." and populated by `fetchStatusData()` on DOMContentLoaded
- scanForDisplays: Disables button, sets text to "Scanning..." during request
- setInterval for status at 10000ms — matches spec

**PASS: Error states**
- All async functions have try/catch blocks (`app.js:90, 168, 238, 264, 278, 292, 317, 342, 359`)
- Network errors surface via `showToast("Network error", "error")`
- Status spans set to "Unavailable" on failed fetch

**PASS: Empty states**
- Schedules empty: `app.js:189` "No schedules yet." — matches spec heading
- Zones empty: `ZonesPage.kt:35-36` — present (body copy substantially matches spec)
- History empty: `HistoryPage.kt:104` — present (wording differs slightly from spec but communicates the same intent)

**PASS: Disabled states**
- submitTextBtn: No explicit disabled state during form submission — minor gap but not spec-mandated
- createScheduleBtn: same pattern, consistent

---

## Files Audited

- `src/main/resources/static/custom.css`
- `src/main/resources/static/app.js`
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt`
- `src/main/kotlin/com/anjo/web/templates/IndexPage.kt`
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt`
- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt`
- `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt`
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`
- `.planning/phases/13-ui-ux-refresh/13-UI-SPEC.md`
- `.planning/phases/13-ui-ux-refresh/13-CONTEXT.md`
- `.planning/phases/13-ui-ux-refresh/13-01-SUMMARY.md` through `13-04-SUMMARY.md`

Registry audit: No shadcn detected (`components.json` absent). No third-party registries in use. Audit skipped.
