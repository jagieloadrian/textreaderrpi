---
phase: 11-multi-zone-displays
plan: 05
subsystem: ui
tags: [kotlin, ktor, kotlinx-html, picocss, javascript, zones, ui-routes, kotest]

requires:
  - phase: 11-04
    provides: ZoneRegistry.listAll(), ZoneRepository.findAll(), GET/POST /api/v1/zones/* endpoints
  - phase: 11-01
    provides: NetworkZone model, ZoneRepository, ZoneStatus model

provides:
  - ZonesPage.kt: zonesPage(List<ZoneInfo>) PicoCSS template with zone cards, status badges, scan button, add-by-IP form
  - ZoneInfo view model data class (in com.anjo.web.templates)
  - ZonesUIRoutes.kt: GET /zones server-rendered HTML route
  - BaseLayout.kt: Zones nav link between History and Settings
  - app.js: scanForDisplays() and addZoneByIp() fetch handlers with busy state and spec-exact copy
  - ZonesUIRoutesTest.kt: 5 integration tests

affects: [Phase 12, Phase 13 UI refresh]

tech-stack:
  added: []
  patterns:
    - "zonesPage combines zoneRegistry.listAll() (live status) with zoneRepository.findAll() (persistence metadata) via associateBy{id} merge"
    - "ZoneInfo view model isolates template from both ZoneStatus and NetworkZone models"
    - "app.js guards with getElementById existence checks; zones page handlers add to DOMContentLoaded boot block pattern"
    - "Status badge rendered as <mark style=...> using PicoCSS CSS variables (no hardcoded hex except fallback)"

key-files:
  created:
    - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
    - src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt
    - src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt
  modified:
    - src/main/kotlin/com/anjo/web/templates/BaseLayout.kt
    - src/main/kotlin/com/anjo/routing/Routing.kt
    - src/main/resources/static/app.js

key-decisions:
  - "ZoneInfo view model defined in com.anjo.web.templates package alongside ZonesPage to keep template self-contained"
  - "ZonesUIRoutes merges live ZoneStatus from ZoneRegistry with persisted NetworkZone from ZoneRepository; local zones (not in DB) have isLocal=true"
  - "app.js scanForDisplays re-enables button in finally block to guarantee button is re-enabled even on error"

patterns-established:
  - "UI route data merge pattern: combine in-memory registry with DB repository via associateBy{id} for complete view model"

requirements-completed: [ZONE-06, ZONE-08]

duration: ~6min
completed: 2026-06-16
---

# Phase 11 Plan 05: Zones Management UI Page Summary

**Server-rendered /zones page with PicoCSS zone cards, ONLINE/OFFLINE/DEGRADED status badges, UDP scan button, and add-by-IP form wired via app.js fetch handlers**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-06-16T11:50:29Z
- **Completed:** 2026-06-16T11:56:XX Z
- **Tasks:** 2 auto complete; 1 checkpoint:human-verify pending
- **Files modified:** 6

## Accomplishments

- `ZonesPage.kt` renders zone cards with ONLINE/OFFLINE/DEGRADED `<mark>` status badges using PicoCSS CSS variables, empty state "No zones registered", scan button `#scanBtn`, and add-by-IP form `#addZoneForm` with `#ipInput`
- `ZonesUIRoutes.kt` merges live `ZoneRegistry.listAll()` with `ZoneRepository.findAll()` into `ZoneInfo` view model list and renders via `BaseLayout.render(activePath="/zones")`
- `BaseLayout.kt` Zones nav link added between History and Settings with correct `aria-current` logic
- `app.js` `scanForDisplays()` with "Scanning..." busy label, spec-exact inline result copy, and "Scan failed." error; `addZoneByIp()` with 409/400 spec copy
- 5 integration tests in `ZonesUIRoutesTest.kt` all green; full 171-test suite green

## Task Commits

Each task was committed atomically:

1. **Task 1: ZonesPage + ZonesUIRoutes + BaseLayout + Routing** - `232e54c` (feat)
2. **Task 2: app.js scan + add-by-IP handlers** - `34a0ec8` (feat)
3. **Task 3: checkpoint:human-verify** — awaiting human visual verification

## Files Created/Modified

- `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` - zonesPage template; ZoneInfo view model; zone cards with badges; scan/add-by-IP sections
- `src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt` - GET /zones handler; registry+repository merge; BaseLayout.render
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` - Zones nav link between History and Settings
- `src/main/kotlin/com/anjo/routing/Routing.kt` - zonesUIRoutes registration + zonesUIRoutes import
- `src/main/resources/static/app.js` - scanForDisplays() + addZoneByIp() with busy state, spec copy, boot block guards
- `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt` - 5 tests: 200 status, html content-type, headings, buttons, seeded zone badge, empty state

## Decisions Made

- `ZoneInfo` defined in `com.anjo.web.templates` package — keeps template dependency-free from routing-layer types
- `ZonesUIRoutes` uses `runBlocking { zoneRepository.findAll() }` consistent with how ZoneRegistry loads persisted zones on startup
- `scanForDisplays()` always restores button label in `finally` block — guarantees re-enable even on network error

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None — all zone data is live from ZoneRegistry + ZoneRepository.

## Threat Flags

No new threat surface beyond plan's threat model. ZonesPage uses kotlinx.html DSL which HTML-escapes all text nodes by default (T-11-11 XSS mitigated). Status badge `style` attribute contains only server-controlled CSS variable names, not user-supplied strings.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `/zones` page available for browser verification
- "Zones" nav link visible on all pages
- Scan and add-by-IP UI wired to Plan 04's API endpoints
- Human checkpoint pending: verify visual rendering and interaction flow in browser

---
*Phase: 11-multi-zone-displays*
*Completed: 2026-06-16*
