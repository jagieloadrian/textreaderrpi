# Project Status

**Updated:** 2026-07-07 · **Milestone:** v1.2 · **Branch:** feat/18-helm-chart

## Completed work

- **Phase 18 (Kubernetes Helm) COMPLETE** — gap plan 18-10 executed (`f105de5`, `d8f62b3`): three-level password default chain (explicit `--set` → `lookup` existing Secret → `randAlphaNum 16`) restored zero-config install; re-verification 23/23 must-haves, status **passed** (two live-cluster items explicitly accepted by user, preserved in `18-VERIFICATION.md`). Phase marked complete 10/10 plans (`9586f61`), PROJECT.md evolved (`4f6e334`).
- Phase 18 code review refreshed (`a6d0b07`): 2 Critical / 6 Warning / 8 Info. Criticals: CR-01 default install CrashLoops (liveness/readiness probes encode hardware state via KHealth — OFFLINE mode returns 503 forever; root cause app-side in `Monitoring.kt`), CR-02 `helm uninstall` deletes H2 PVCs (needs `helm.sh/resource-policy: keep`).
- **Phase 19 (DRY/YAGNI Refactoring) PLANNED** — research (`ce5f6b0`), VALIDATION.md (`e94bf2e`), PATTERNS.md, and 2 plans committed (`4437bca`). Plan-checker: VERIFICATION PASSED all dimensions; decision coverage 10/10; gap analysis 11/11.

## Important decisions

- Phase 18 human-verification items (real-cluster deploy + `lookup`-hit upgrade path) accepted rather than blocking — recorded as `human_verification_accepted` in 18-VERIFICATION.md; UAT test #1 remains available if a real-cluster run is wanted later.
- Phase 19 plan structure: 19-01 (wave 1) `HistoryValidators.parseFilter()` consolidation + bounded main-code audit; 19-02 (wave 2, depends on 19-01) new `TestSupport.kt` (`appTest`/`dep<T>`/`historyRecord`) + migrate the 6 `getBlocking`-heavy test files + full gate (suite + JaCoCo ≥70% + warning diff). Test migration deliberately scoped to 6 of 15 files (3-line bootstraps are below the 5-line SC2 threshold); `LiveRoutesTest`/`FirmwareZoneDriverTest` stay on `embeddedServer` (D-06).
- Worktree isolation auto-degraded for this repo (origin/HEAD unresolved, #683) — executors run sequentially on the main tree; set `worktree.baseRef: "head"` to restore parallel worktrees.

## Next steps

1. `/gsd-execute-phase 19` — run the two verified plans (wave 1 → wave 2)
2. `/gsd-code-review 18 --fix` — address the two Critical Helm findings (CrashLoop probes, PVC deletion on uninstall)
3. `/gsd-secure-phase 18` — security enforcement is on and 18-SECURITY.md doesn't exist yet
