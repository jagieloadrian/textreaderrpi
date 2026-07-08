---
phase: 20-cleanup-docs
plan: 03
subsystem: docs
tags: [readme, docs-cleanup, accuracy-sweep]

requires:
  - phase: 20-cleanup-docs (Plans 01-02)
    provides: Rewritten README.md (TOC, API tables, Configuration, Helm, Firmware sections)
provides:
  - docs/ directory permanently removed (content preserved in git history)
  - README.md verified accurate against source code (routes, application.yaml, values.yaml)
  - Zero dead docs/ references outside .planning/ archive
  - Confirmed no deferred v1.3 feature is presented as shipped
affects: [phase-20-final-cleanup]

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - README.md

key-decisions:
  - "No net salvage from the 3 unread docs/ files (architecture/overview.md, configuration/overview.md, testing/overview.md) — README already covers their content at appropriate depth from Plans 01-02"
  - "docs/guides/development.md dropped entirely per D-03 — internal build/convention notes, not end-user docs"

requirements-completed: [CLEAN-01, DOCS-01]

coverage:
  - id: D1
    description: "docs/ directory deleted from working tree via git rm -r (content preserved in git history per D-01)"
    requirement: CLEAN-01
    verification:
      - kind: unit
        ref: "test ! -d docs && echo DOCS-DELETED"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-04 repo-wide grep confirms zero actionable dead docs/ references outside .planning/ archive (2 non-issue matches: PROJECT-STATUS.md prose mention, .devops/helm README's external helm.sh/docs/ URL — both pre-identified as non-issues in 20-RESEARCH.md)"
    requirement: DOCS-01
    verification:
      - kind: unit
        ref: "grep -rn docs/ --include=*.md --include=*.yml --include=*.yaml --include=*.kt --include=*.kts . excluding docs/, .git/, .planning/"
        status: pass
    human_judgment: false
  - id: D3
    description: "README endpoint/UI-route/query-param claims cross-checked against routing/*.kt, application.yaml, values.yaml — 2 drift issues found and fixed (stale /settings/display row removed, /schedules corrected to /schedule, missing zone query param added to history endpoint docs)"
    requirement: DOCS-01
    verification:
      - kind: unit
        ref: "manual cross-check against src/main/kotlin/com/anjo/routing/**/*.kt, src/main/resources/application.yaml, .devops/helm/textreaderrpi/values.yaml"
        status: pass
    human_judgment: false
  - id: D4
    description: "No deferred v1.3 feature (OTA, GIN full-text search, multi-replica Postgres, ready-to-use ingress, WebSocket-live-feed conflation) is presented as a shipped v1.2 feature in README"
    requirement: DOCS-01
    verification:
      - kind: unit
        ref: "grep -i platformio README.md (zero); grep -in 'gin index|full-text|multi-replica|websocket live feed' README.md (zero)"
        status: pass
    human_judgment: true
    rationale: "OTA hedging wording and SSE-vs-firmware-WebSocket distinction require nuanced reading, not just grep — negative greps confirm the mechanical checks but the semantic framing (e.g. OTA scaffolding vs shipped feature) was read manually against 20-RESEARCH.md's Deferred v1.3 Items checklist"

duration: 20min
completed: 2026-07-08
status: complete
---

# Phase 20 Plan 03: docs/ Deletion + README Accuracy Sweep Summary

**Deleted docs/ (8 files, 1668 lines, content preserved in git history) and fixed 3 README drift issues found by cross-checking every endpoint/UI-route/query-param claim against live routing code, application.yaml, and the Helm values.yaml.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-08T13:15:00+02:00 (approx)
- **Completed:** 2026-07-08T13:33:28+02:00
- **Tasks:** 2 completed
- **Files modified:** 9 (8 deleted under docs/, 1 modified: README.md)

## Accomplishments
- Skimmed the 3 docs/ files not fully read during research (architecture/overview.md, configuration/overview.md, testing/overview.md); confirmed near-zero net salvage prediction from 20-RESEARCH.md — README already covers equivalent content from Plans 01-02
- Deleted the entire docs/ directory (`git rm -r docs/`) — git history remains the archive (D-01)
- Ran the D-13 systematic README accuracy sweep against `src/main/kotlin/com/anjo/routing/**/*.kt`, `application.yaml`, and `.devops/helm/textreaderrpi/values.yaml` — found and fixed 2 real drift issues (stale `/settings/display` row with no corresponding route; `/schedules` corrected to the actual mount path `/schedule`) and 1 completeness gap (missing `zone` query param on the history endpoint)
- Ran the D-04 repo-wide `docs/` reference grep — zero actionable dead references remain outside `.planning/`; the 2 remaining matches are pre-identified non-issues (PROJECT-STATUS.md prose describing this phase, and an external `helm.sh/docs/` URL coincidental substring match)
- Confirmed no deferred v1.3 feature (OTA, GIN full-text search, multi-replica Postgres, ready-to-use ingress, WebSocket-live-feed conflation) is presented as a shipped v1.2 feature in README

## Task Commits

1. **Task 1: Skim remaining docs/ files, salvage any unique fact, delete docs/ (D-01, D-03)** - `a894b46` (chore)
2. **Task 2: D-13 accuracy sweep + D-04 repo-wide grep + deferred-v1.3 check (D-04, D-13)** - `dea89b0` (fix)

**Plan metadata:** (this commit)

## Files Created/Modified
- `docs/api/reference.md` - deleted (content salvaged into README in Plans 01-02)
- `docs/architecture/overview.md` - deleted (no unique salvage; README's Code Layout section covers equivalent depth)
- `docs/configuration/overview.md` - deleted (no unique salvage; README's Configuration table already includes all 4 previously-gapped vars)
- `docs/deployment/production-guide.md` - deleted (content salvaged into README in Plans 01-02)
- `docs/guides/development.md` - deleted, intentionally dropped without salvage (internal build/convention notes per D-03)
- `docs/guides/getting-started.md` - deleted (content salvaged into README in Plans 01-02)
- `docs/operations/monitoring-alerting.md` - deleted (content salvaged into README's Monitoring subsection in Plan 01)
- `docs/testing/overview.md` - deleted (out of D-01's scope: endpoints/deployment/quick-start; no testing section requested)
- `README.md` - fixed 2 dead/wrong UI-route table rows and added 1 missing query param to the history endpoint docs

## Decisions Made
- No net salvage from the 3 skimmed docs/ files — README (as built in Plans 01-02) already covers architecture, configuration, and the D-01 gap list at the appropriate depth; testing content explicitly out of D-01's scope
- `docs/guides/development.md` dropped entirely, not migrated — its content (build-command duplication + internal "no comments"/"validation in validators only" conventions) is a CLAUDE.md/AGENTS.md concern, not end-user documentation, per D-03

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] README's Web UI table referenced a non-existent `/settings/display` route and the wrong path for the schedule page**
- **Found during:** Task 2 (D-13 accuracy sweep)
- **Issue:** README's Web UI table listed `/settings/display` (no such route exists anywhere in `src/main/kotlin/com/anjo/routing/ui/`) and `/schedules` (actual mount path in `ScheduleUIRoutes.kt` is `/schedule`, singular — confirmed against `BaseLayout.kt`'s `NAV_ITEMS` list, the single source of truth for real nav links)
- **Fix:** Removed the `/settings/display` row entirely; corrected `/schedules` to `/schedule`
- **Files modified:** README.md
- **Verification:** Cross-checked against `src/main/kotlin/com/anjo/routing/ui/*.kt` and `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` NAV_ITEMS (the 5 real pages: `/`, `/schedule`, `/history`, `/zones`, `/status`)
- **Committed in:** `dea89b0` (part of task commit)

**2. [Rule 1 - Bug] README's history endpoint query parameter list omitted the `zone` filter**
- **Found during:** Task 2 (D-13 accuracy sweep)
- **Issue:** `HistoryValidators.parseFilter()` supports `effect`, `source`, `zone`, and `search` as filter query params, but README's History section only documented `effect`, `source`, `search`
- **Fix:** Added `zone` to the documented query parameter list
- **Files modified:** README.md
- **Verification:** Cross-checked against `src/main/kotlin/com/anjo/validation/HistoryValidators.kt`
- **Committed in:** `dea89b0` (part of task commit)

---

**Total deviations:** 2 auto-fixed (2x Rule 1 — bugs in documentation accuracy, both directly within Task 2's explicit accuracy-sweep scope)
**Impact on plan:** Both fixes are exactly what D-13's accuracy sweep was designed to catch. No scope creep.

## Issues Encountered
The plan's Task 2 verify script assumes `grep -rn ... .` prefixes matched paths with `./` (used `grep -v '^\./docs/'` etc. as exclusion patterns). In this shell environment, `grep -r` on `.` does not prefix output paths with `./` — the exclusion patterns silently matched nothing, initially showing all `.planning/` historical references as false-positive D-04 violations. Worked around by re-running the grep with exclusion patterns matching the actual (unprefixed) path format; confirmed the same 2 non-issue matches predicted by 20-RESEARCH.md and zero real violations. No code change required — this is a shell/grep environment quirk in the plan's verify script, not a project issue.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
Phase 20's own directory (`.planning/phases/20-cleanup-docs/`) is intentionally NOT deleted by this plan — per Pitfall 4 in 20-RESEARCH.md (D-07 sequencing), that deletion must happen in a separate, explicitly-labeled final commit after the phase verifier (`/gsd-verify-work` / 20-VERIFICATION.md) has run. This is the next step for Phase 20 completion, not this plan's scope.

---
*Phase: 20-cleanup-docs*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: README.md
- FOUND: docs/ absent (deletion confirmed)
- FOUND: a894b46 (Task 1 commit)
- FOUND: dea89b0 (Task 2 commit)
