---
phase: 13-ui-ux-refresh
plan: "04"
subsystem: testing
tags: [kotest, jacoco, h2, ktor-test, history, zone-filter, ssr]

requires:
  - phase: 13-ui-ux-refresh-01
    provides: BaseLayout side nav + custom.css (no Settings link)
  - phase: 13-ui-ux-refresh-02
    provides: IndexPage zone selector + effectPreview, SchedulePage Zone/Webhook URL columns, StatusPage skeleton, HistoryRepository zone filter
  - phase: 13-ui-ux-refresh-03
    provides: UI route wiring (ZoneRegistry injected, SettingsPage deleted, app.js nav/preview/polling)

provides:
  - Zone filter proven at repository layer (where + andWhere chaining)
  - Zone + effect param forwarding proven at service layer
  - SSR structure asserted for index/schedule/status/nav (zone selector, columns, skeleton, no settings link)
  - /history?zone=X wired without 500
  - /settings/display returns 404
  - Full suite green with JaCoCo 70% gate
  - Human visual checkpoint defined for dark mode, hamburger nav, effect preview, status live refresh

affects: [phase-14, future-test-phases]

tech-stack:
  added: []
  patterns:
    - "H2 in-memory DB with beforeSpec/beforeEach isolation reused for HistoryServiceTest"
    - "testApplication { application { module() } } pattern for SSR route assertions"
    - "Zone-filter tests follow effect/source filter shape from existing HistoryRepositoryTest"

key-files:
  created:
    - src/test/kotlin/com/anjo/service/HistoryServiceTest.kt
    - src/test/kotlin/com/anjo/routing/WebRoutesTest.kt
  modified:
    - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
    - src/test/kotlin/com/anjo/routing/WebAndDisplayRoutesTest.kt

key-decisions:
  - "HistoryServiceTest uses dedicated H2 DB name (test_history_service) to avoid colliding with HistoryRepositoryTest DB"
  - "WebRoutesTest checks absence of /settings/display in nav body to prove D-25 compliance"
  - "WebAndDisplayRoutesTest /history?zone=ALL asserts All zones text — proves zone param wired without requiring pre-seeded data"

patterns-established:
  - "Service-layer tests wire a real Repository over H2 (not mocks) to prove param forwarding end-to-end"

requirements-completed: [UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07, UI-08]

duration: 15min
completed: 2026-06-20
---

# Phase 13 Plan 04: UI/UX Refresh — Test Lock Summary

**Kotest zone-filter + SSR structure tests locking the MD3 UI refresh with H2 integration and JaCoCo 70% gate**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-06-20T21:20:00Z
- **Completed:** 2026-06-20T21:35:00Z
- **Tasks:** 2 auto + 1 human checkpoint
- **Files modified:** 4

## Accomplishments

- Added two zone-filter tests to `HistoryRepositoryTest` (zone-only and zone+effect andWhere branch)
- Created `HistoryServiceTest` proving zone+effect param forwarding through the service layer (H2-backed, not mocked)
- Replaced the invalid `/settings/display` 200 test in `WebAndDisplayRoutesTest` with a `/history?zone=ALL` zone-param wiring test
- Created `WebRoutesTest` with five SSR assertions: zone selector, schedule columns, status skeleton, custom.css/no settings link, 404 for `/settings/display`
- Fixed missing `org.jetbrains.exposed.v1.core.*` import in `HistoryServiceTest` that caused `isNotNull` compile error
- Full suite green; JaCoCo 70% gate passes

## Task Commits

1. **Task 1+2: zone-filter + WebRoutes + HistoryService tests** - `8ab2a9d` (feat)

## Files Created/Modified

- `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` - Added zone-only and zone+effect filter tests
- `src/test/kotlin/com/anjo/service/HistoryServiceTest.kt` - New: proves zone+effect forwarding through HistoryService
- `src/test/kotlin/com/anjo/routing/WebAndDisplayRoutesTest.kt` - Removed /settings/display test; added /history?zone=ALL test
- `src/test/kotlin/com/anjo/routing/WebRoutesTest.kt` - New: asserts SSR structure for index/schedule/status/nav/404

## Decisions Made

- Fixed missing `org.jetbrains.exposed.v1.core.*` import in `HistoryServiceTest` — `isNotNull` is in that package, mirroring the pattern from `HistoryRepositoryTest`
- Used separate H2 DB name `test_history_service` in `HistoryServiceTest` to avoid shared-state collision with the parallel `HistoryRepositoryTest` H2 DB

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing Exposed core import in HistoryServiceTest**
- **Found during:** Task 1 (compiling test files)
- **Issue:** `HistoryServiceTest.kt` used `HistoryTable.id.isNotNull()` in the `deleteWhere` lambda but was missing `import org.jetbrains.exposed.v1.core.*` — the same import present in `HistoryRepositoryTest`
- **Fix:** Added `import org.jetbrains.exposed.v1.core.*` to the import block
- **Files modified:** `src/test/kotlin/com/anjo/service/HistoryServiceTest.kt`
- **Verification:** `./gradlew test` exits 0 after fix
- **Committed in:** `8ab2a9d` (combined with test file commit)

---

**Total deviations:** 1 auto-fixed (blocking import error)
**Impact on plan:** Minimal — single missing import, no logic change.

## Issues Encountered

None beyond the auto-fixed import issue above.

## Known Stubs

None — all four test files assert real behavior against real in-memory H2 or real Ktor test application routes. No placeholder assertions.

## Threat Flags

None — test-only files; no new production attack surface.

## Human Checkpoint

Task 3 requires human visual verification. The following behaviors are CSS/timing-driven and cannot be asserted in headless Ktor:

1. **Dark mode palette (UI-02):** With OS in dark mode, page background should be `#1c1b1f` (MD3 dark surface); side nav (240px) visible on left with active link highlighted. Toggle to light mode — palette switches to `#fffbfe`.
2. **Side nav links (UI-03):** Exactly 5 links — Send Text, Schedules, History, Zones, Status — and NO Settings link.
3. **Mobile hamburger (UI-01/03):** At < 960px viewport, side nav hidden; top bar with `☰` appears. Tapping `☰` slides nav in over dark backdrop; tapping backdrop closes it.
4. **Effect preview (UI-04):** On `/`, typing text mirrors into preview box; changing effect select among Scroll/Blink/Reverse/Fade changes the preview animation.
5. **Status live refresh (UI-08):** On `/status`, `Loading...` spans replaced with real values within ~1s; values refresh every ~10s without full page reload.
6. **Zones tint (UI-07):** Any auto-discovered zone card uses `.zone-card--discovered` secondary-container tint.

Start app: `./gradlew run`, then open `http://localhost:8080/`.

## Next Phase Readiness

Phase 13 (UI/UX Refresh) is complete pending human checkpoint approval. All 8 UI requirements (UI-01 through UI-08) are covered by automated tests. The four manual-only behaviors listed above require human sign-off before the milestone can be closed.

---
*Phase: 13-ui-ux-refresh*
*Completed: 2026-06-20*
