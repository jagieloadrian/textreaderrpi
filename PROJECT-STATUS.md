# Project Status

**Updated:** 2026-07-07 · **Milestone:** v1.2 · **Branch:** feat/18-helm-chart

## Completed work

- **Phase 19 (DRY/YAGNI Refactoring) COMPLETE** — 2/2 plans, verification **passed** 4/4 must-haves (`19-VERIFICATION.md`), REF-05 satisfied.
  - 19-01 (`8d88a5f`, `02cc165`): history query-param parsing consolidated into `HistoryValidators.parseFilter` (3 call sites); bounded audit also extracted `ZoneRoutes.buildZone`. Warning baseline unchanged (2 pre-existing `Max7219Matrix.kt`), JaCoCo ≥70% green.
  - 19-02 (`64b1032`, `0fe62ad`, `290b596`): new `src/test/kotlin/com/anjo/TestSupport.kt` (`appTest{}`, `dep<T>()`, `historyRecord()`); six test files migrated off the `getBlocking(DependencyKey)` incantation. Full suite green (47 test classes, 0 failures); verifier re-ran all gates fresh with `--rerun-tasks`.
- Phase 19 code review (`19-REVIEW.md`, standard depth, 11 files): 0 Critical / 5 Warning / 3 Info. Notable: WR-01 — UI history route now uppercases `effect`/`source` via `parseFilter` (`?effect=scroll` now matches; behavior improvement but undocumented/untested); WR-02 — `parseFilter` has no direct unit tests despite being the new load-bearing choke point.

## Important decisions

- WR-01 behavior change accepted as-is (improvement in practice); document or test it if it matters for v1.2 release notes.
- `HistoryPage.kt` query-string builder duplication deliberately deferred (rationale recorded in 19-01 SUMMARY) — listed, not fixed.
- Codebase map in `.planning/codebase/` is stale vs. phases 17–18 files (drift gate advisory) — refresh with `/gsd-map-codebase` when convenient.
- Phase 18 carry-overs unchanged: 2 Critical Helm findings open (CrashLoop probes, PVC deletion on uninstall); 18-SECURITY.md still missing.

## Next steps

1. `/gsd-discuss-phase 20` then `/gsd-plan-phase 20` — Cleanup + Docs (.planning/ compression, delete docs/, update README); no CONTEXT.md exists yet
2. `/gsd-code-review 18 --fix` — address the two Critical Helm findings
3. `/gsd-secure-phase 18` — security enforcement is on and 18-SECURITY.md doesn't exist yet
4. Optional: `/gsd-code-review 19 --fix` — apply the 5 warning-level test/doc fixes from 19-REVIEW.md
