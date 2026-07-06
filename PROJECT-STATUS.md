# Project Status

**Updated:** 2026-07-06 · **Milestone:** v1.2 · **Branch:** feat/18-helm-chart

## Completed work

- Phase 18 code-review fixes applied via `/gsd-code-review 18 --fix`: CR-01, CR-02, WR-01..WR-05 committed (report in 18-REVIEW-FIX.md)
- Re-verification after fixes: status `gaps_found`, 22/23 must-haves (was `human_needed` 23/23 before the fixes)
  - Both original blockers still hold; WR-01, WR-03, WR-04, WR-05 re-verified clean
  - New regression: WR-02 (`database.password` now `required`, no shipped default — commit `49b3536`) breaks Success Criterion 1's literal command `helm install textreaderrpi .devops/helm/textreaderrpi/`, which fails at template render with no `--set` flags
- `/gsd-execute-phase 18 --gaps` ran: no-op — no gap-closure plans exist yet (`/gsd-plan-phase 18 --gaps` was never run after re-verification)

## Important decisions

- WR-02 itself is a correct security fix (removes hardcoded default credential from VCS); the open question is how to reconcile it with SC1 — decision pending in gap planning:
  - (a) generate a random default at install time (`randAlphaNum`, optionally `lookup`-based for upgrade stability) so the bare install works, or
  - (b) update ROADMAP SC1 + README to require `--set database.password=...` and accept the deviation via override
- Gradle test suite deliberately skipped for Helm-only diffs; `helm lint` + dual-mode `helm template` are the relevant checks

## Next steps

1. `/gsd-plan-phase 18 --gaps` — create the gap-closure plan for the WR-02/SC1 regression (choose (a) or (b) above)
2. `/gsd-execute-phase 18 --gaps-only` — execute it; verifier re-runs automatically
3. `/gsd-verify-work 18` — the one human UAT item still pending (deploy chart to a real cluster, confirm `/health` and `/health/ready` return 200)
4. Then Phase 19 (DRY/YAGNI Refactoring)
