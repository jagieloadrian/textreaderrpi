---
phase: 19-dry-yagni-refactoring
plan: 01
subsystem: refactor
tags: [ktor, kotlin, dry, dedup, history, zones]

# Dependency graph
requires:
  - phase: 09-display-history
    provides: HistoryFilter model, HistoryRepository, HistoryService, HistoryRoutes/HistoryUIRoutes
  - phase: 16-zone-management
    provides: ZoneRoutes NetworkZone POST handler (FIRMWARE + network branches)
provides:
  - "HistoryValidators.parseFilter(Parameters): HistoryFilter — single source for effect/source/zone/search query-param extraction"
  - "ZoneRoutes buildZone(req, ip) private helper — single source for NetworkZone construction"
  - "Confirmed compiler-warning baseline (2 pre-existing Max7219Matrix warnings) and JaCoCo >=70% coverage gate unchanged"
affects: [20-cleanup-docs]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Validator objects (HistoryValidators) are the framework-aware layer — they may import io.ktor.http types directly, consistent with ZoneValidators importing ValidationResult; the model package stays framework-free"
    - "Bounded D-07 dedup audit: fix only unambiguous >5-line copy-paste wins; list borderline cases (where extraction needs as many params as call sites) in the SUMMARY instead of editing"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/validation/HistoryValidators.kt
    - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
    - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
    - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt

key-decisions:
  - "parseFilter uppercases effect/source (matching HistoryRoutes' prior behavior) even though HistoryUIRoutes previously didn't uppercase — intentional behavior change on GET /history for hand-typed URLs: ?effect=scroll now matches SCROLL records (previously matched nothing) and lowercase ?effect=all / ?source=all now disable the filter (previously filtered on literal 'all'); no-op for the UI dropdowns which already emit uppercase values; pinned by HistoryUIRoutesTest case-normalization tests (code review WR-01)"
  - "HistoryPage.kt's 3x-repeated query-string construction line was listed, not fixed — extraction would require ~7 params for a 3-call-site helper, over the borderline threshold set by D-07"

requirements-completed: [REF-05]

coverage:
  - id: D1
    description: "HistoryValidators.parseFilter consolidates the effect/source/zone/search query-param extraction previously duplicated 3x across HistoryRoutes.kt (x2) and HistoryUIRoutes.kt (x1)"
    requirement: "REF-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt"
        status: pass
      - kind: other
        ref: "grep -c HistoryValidators.parseFilter (2 in HistoryRoutes.kt, 1 in HistoryUIRoutes.kt) + grep -c 'fun parseFilter' (1 in HistoryValidators.kt)"
        status: pass
    human_judgment: false
  - id: D2
    description: "ZoneRoutes buildZone() helper removes the 8-line duplicated NetworkZone(...) construction between the FIRMWARE and network-zone POST branches"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt"
        status: pass
    human_judgment: false
  - id: D3
    description: "Bounded main-code dedup/dead-code audit across routing/, routing/ui/, validation/, web/templates/ with warning-baseline and coverage gate confirmation"
    verification:
      - kind: other
        ref: "./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all (2 pre-existing w: lines only)"
        status: pass
      - kind: other
        ref: "./gradlew jacocoTestCoverageVerification"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-07-07
status: complete
---

# Phase 19 Plan 01: History Query-Param Dedup + Bounded DRY/YAGNI Audit Summary

**Added HistoryValidators.parseFilter to eliminate 3x-duplicated history filter parsing, extracted a ZoneRoutes buildZone helper, and confirmed the warning/coverage gates stayed green through a bounded main-code audit.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-07-07T14:05:00Z (approx)
- **Completed:** 2026-07-07T14:20:39Z
- **Tasks:** 2 completed
- **Files modified:** 4

## Accomplishments
- `HistoryValidators.parseFilter(Parameters): HistoryFilter` is now the single parsing source for the effect/source/zone/search query params, called 2x from `HistoryRoutes.kt` and 1x from `HistoryUIRoutes.kt`; the security-relevant sanitize-before-blank-check ordering (LIKE-wildcard stripping) is preserved verbatim
- `HistoryUIRoutes.kt` keeps its raw redisplay reads (`effect`, `source`, `zone`, `rawSearch`) and `exportHref` untouched — only the filter-construction lines were consolidated
- Bounded D-07/D-08 audit found and fixed one additional clear >5-line duplication: `ZoneRoutes.kt`'s `NetworkZone(...)` construction, repeated identically except the `ip` field, extracted to a private `buildZone(req, ip)` helper
- Confirmed D-09 (warning baseline unchanged: exactly 2 pre-existing `Max7219Matrix.kt` SpiChipSelect deprecation warnings, none from any file touched this plan) and D-10 (JaCoCo line coverage gate stays green, `build.gradle.kts` minimum untouched at `0.70`)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add HistoryValidators.parseFilter and route all three call sites through it** - `8d88a5f` (refactor)
2. **Task 2: Bounded main-code dedup/dead-code audit + warning-baseline and coverage gate** - `02cc165` (refactor)

## Files Created/Modified
- `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` - added `parseFilter(Parameters): HistoryFilter`
- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` - both handlers call `parseFilter`; unused `HistoryFilter` import removed
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` - filter construction routed through `parseFilter`; raw redisplay reads and `exportHref` kept
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` - extracted `buildZone(req, ip)` private helper, removing duplicated `NetworkZone(...)` construction

## Decisions Made
- `parseFilter` uppercases effect/source (matching `HistoryRoutes`' prior behavior); `HistoryUIRoutes` previously didn't uppercase these before filtering, but this is a no-op for the real dropdown values (already uppercase enum names) — `HistoryUIRoutesTest` passing unchanged confirms no regression
- `HistoryPage.kt`'s query-string-building line (`?page=...&effect=...&source=...&size=...&zone=...&search=...`), repeated 3x with minor variations, was **listed, not fixed**: extracting a shared helper would need ~7 parameters for only 3 call sites — over the D-07 borderline threshold, and the project rule is to avoid churning a working v1.2 codebase for marginal wins
- No dead code found to delete during the D-08 pass — the pre-existing 2-warning compiler baseline (no "unused" warnings) is consistent with the compiler already flagging nothing to remove in the audited packages

## Deviations from Plan

None - plan executed exactly as written. Task 2's bounded audit surfaced one additional clear >5-line duplication (`ZoneRoutes.kt`'s `NetworkZone` construction) beyond the SC1 history-filter consolidation named in the plan objective; fixing it falls squarely within Task 2's own scope ("audit main code for duplication... fix ONLY unambiguous >5-line wins") so it is documented here as part of planned Task 2 work, not a deviation.

## Issues Encountered

- One pre-existing, unrelated test flake surfaced during the full-suite run: `FirmwareZoneRoutesTest — GET /ws/zone for a pre-registered FIRMWARE zone zone returns OFFLINE after disconnect` failed once (`expected:<OFFLINE> but was:<ONLINE>`) on a timing-sensitive WebSocket disconnect-detection assertion. This test exercises `FirmwareZoneRoutes`/`FirmwareZoneDriver`, files not touched by this plan. Re-ran in isolation immediately after — passed cleanly (3/3, 0 failures). Confirmed as a pre-existing timing flake, out of scope per the executor's scope-boundary rule (not caused by this plan's changes to `HistoryValidators`/`HistoryRoutes`/`HistoryUIRoutes`/`ZoneRoutes`). No action taken; logged here for visibility.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- SC1 (history filter dedup), the model-package framework-free constraint, SC3 (coverage gate), and SC4 (no new warnings) are all satisfied
- 19-02 (test-helper DRY plan) can proceed independently — no shared files between 19-01 and 19-02's planned artifacts (`TestSupport.kt`, `appTest`, `dep<T>()`, `historyRecord()`)
- No blockers for Phase 20 (Cleanup + Docs)

---
*Phase: 19-dry-yagni-refactoring*
*Completed: 2026-07-07*

## Self-Check: PASSED

All 4 modified/created source files and the SUMMARY.md itself found on disk; all 3 commits (8d88a5f, 02cc165, 91d743a) found in git log.
