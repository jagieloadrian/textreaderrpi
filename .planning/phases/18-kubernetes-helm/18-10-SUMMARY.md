---
phase: 18-kubernetes-helm
plan: 10
subsystem: infra
tags: [helm, kubernetes, secrets, ops]

# Dependency graph
requires:
  - phase: 18-kubernetes-helm (plan 09)
    provides: Completed Helm chart with ServiceAccount/RoleBinding, phase-18-complete tag
provides:
  - Zero-config default install path restored (no required database.password flag needed)
  - Upgrade-stable password generation via helm lookup
affects: [18-kubernetes-helm verification, any future phase touching the Helm chart]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Helm Secret default-value chain: explicit .Values override -> lookup existing release Secret -> randAlphaNum fallback, guarded with nested if (not and) for template-engine portability"

key-files:
  created: []
  modified:
    - .devops/helm/textreaderrpi/templates/secret.yaml
    - .devops/helm/textreaderrpi/values.yaml
    - .devops/helm/textreaderrpi/README.md

key-decisions:
  - "Used nested {{- if not $password }} / {{- if $existing }} guards instead of `and` to avoid Go-template short-circuit version dependency"
  - "metadata.name in secret.yaml reuses the same $secretName variable computed for the lookup, avoiding a duplicate include call"

patterns-established:
  - "Chart Secrets needing upgrade-stable auto-generated values: lookup existing release Secret first, fall back to random generation only when lookup returns empty (covers helm template/--dry-run with no cluster access)"

requirements-completed: [OPS-01]

coverage:
  - id: D1
    description: "helm template with zero overrides renders successfully with a non-empty auto-generated DATABASE_PASSWORD (SC1 restored)"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm template textreaderrpi .devops/helm/textreaderrpi/ | grep -E 'DATABASE_PASSWORD: \"[A-Za-z0-9]{8,}\"'"
        status: pass
    human_judgment: false
  - id: D2
    description: "Explicit --set database.password=<value> is used verbatim, preserving override precedence"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm template ... --set database.password=explicitpw12345 | grep 'DATABASE_PASSWORD: \"explicitpw12345\"'"
        status: pass
    human_judgment: false
  - id: D3
    description: "Bare helm lint emits no level=WARN line (SC4 restored)"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm lint .devops/helm/textreaderrpi/"
        status: pass
    human_judgment: false
  - id: D4
    description: "values.yaml still ships password: \"\" — no credential committed to VCS (WR-02 intact)"
    verification:
      - kind: other
        ref: "grep 'password: \"\"' .devops/helm/textreaderrpi/values.yaml"
        status: pass
    human_judgment: false
  - id: D5
    description: "README documents zero-config default install and retains the explicit-credential production option"
    verification:
      - kind: other
        ref: "grep -qi auto-generat .devops/helm/textreaderrpi/README.md"
        status: pass
    human_judgment: false
  - id: D6
    description: "Upgrade stability: lookup reuses the existing release Secret's password across helm upgrade"
    human_judgment: true
    rationale: "helm template/lint have no cluster access, so the lookup branch cannot be exercised without a live helm upgrade against a real cluster; the nested-if fallback logic was verified by code review and the empty-lookup path (randAlphaNum) was confirmed via helm template, but the lookup-hit path itself requires human verification with a live install/upgrade cycle."

# Metrics
duration: 3min
completed: 2026-07-07
status: complete
---

# Phase 18 Plan 10: Restore Zero-Config Helm Install Summary

**Replaced the hard-fail `required` guard on `database.password` with an upgrade-stable default chain (explicit override -> lookup existing Secret -> randAlphaNum 16), closing the sole Phase 18 verification gap.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-07-07T08:45:00Z (approx)
- **Completed:** 2026-07-07T08:46:00Z (approx)
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- `secret.yaml` no longer hard-fails render when `database.password` is unset — zero-override `helm template`/`helm install` now succeeds (ROADMAP SC1 restored)
- Bare `helm lint` is WARN-free (SC4 restored)
- Explicit `--set database.password=...` / values-file override precedence preserved and verified
- `values.yaml` still ships `password: ""` — no credential committed to VCS (WR-02 intent intact)
- README's Installation preamble, Default Installation example, and configuration table reconciled to describe the zero-config auto-generated default while retaining the explicit-credential production path

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace the required-password guard with an upgrade-stable default chain in secret.yaml (+ reconcile values.yaml comment)** - `f105de5` (fix)
2. **Task 2: Reconcile README.md to document the restored zero-config default install** - `d8f62b3` (docs)

**Plan metadata:** (this commit, following SUMMARY.md write)

## Files Created/Modified
- `.devops/helm/textreaderrpi/templates/secret.yaml` - Replaced `required "..."` hard-fail with `$secretName`/`$existing`/`$password` variables and a nested-if default chain (explicit value -> lookup existing Secret's `DATABASE_PASSWORD` (b64dec) -> `randAlphaNum 16`)
- `.devops/helm/textreaderrpi/values.yaml` - Reconciled the `password: ""` trailing comment to describe auto-generation instead of claiming the value is required
- `.devops/helm/textreaderrpi/README.md` - Reconciled Installation preamble, Default Installation example command, and `database.password` configuration-table row to document the zero-config default and the explicit-credential production option

## Decisions Made
- Used nested `{{- if not $password }}` / `{{- if $existing }}` guards (not `and $x $y`) — avoids Go-template short-circuit evaluation being version-dependent, per plan's explicit rationale.
- `metadata.name` in `secret.yaml` now reuses the `$secretName` variable (computed once for the `lookup` call) instead of a second `include "textreaderrpi.fullname"` call — same rendered value, one fewer template evaluation. Not called out in the plan's literal action steps but consistent with its "declare $secretName at top of file" instruction and verified to produce an identical render.

## Deviations from Plan

None - plan executed exactly as written (the `metadata.name` reuse of `$secretName` above is a same-behavior simplification within Task 1's stated scope, not a deviation from any `must_haves` or acceptance criteria).

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The sole Phase 18 verification gap (18-VERIFICATION.md, gaps_found, 22/23) is closed. All `must_haves.truths` for this plan are verified via `helm template` / `helm lint` except D6 (upgrade-stability lookup-hit path), which requires a live cluster `helm upgrade` cycle to observe directly — recommend a human-verify pass during the next real deployment to confirm the password is reused rather than regenerated.
- No blockers for Phase 19 (DRY/YAGNI Refactoring).

---
*Phase: 18-kubernetes-helm*
*Completed: 2026-07-07*

## Self-Check: PASSED

All created/modified files verified present on disk; both task commits (`f105de5`, `d8f62b3`) verified present in git log.
