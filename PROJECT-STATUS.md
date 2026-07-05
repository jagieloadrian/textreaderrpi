# Project Status

**Updated:** 2026-07-06 · **Milestone:** v1.2 · **Branch:** feat/18-helm-chart

## Completed work

- Phase 18 (Kubernetes + Helm) gap closure executed: plan 18-09 fixed both verification blockers
  - `privileged: true` moved from pod-level to container-level securityContext (`2d29d59`)
  - ServiceAccount name unified via `textreaderrpi.serviceAccountName` helper across serviceaccount.yaml, rolebinding.yaml, deployment.yaml (`66e69fb`)
- Re-verification: 23/23 must-haves verified, status `human_needed` (was `gaps_found` at 21/23)
- Fresh code review committed (18-REVIEW.md): 2 critical, 5 warning, 6 info — advisory, not blocking
- `18-PLAN.md` renamed to `18-PHASE-OVERVIEW.md` so the plan index stops counting the overview doc as an incomplete plan; ROADMAP checklist updated (9/9 plans)

## Important decisions

- New review criticals CR-01 (`rbac.create` vs `serviceAccount.create` toggle mismatch) and CR-02 (missing `strategy: Recreate` → `helm upgrade` wedges on RWO PVC + H2 lock) were judged follow-up work, not phase blockers: neither affects the default `helm install` path that OPS-01 describes. Documented in 18-VERIFICATION.md and 18-REVIEW.md.
- Gradle test suite deliberately skipped for this run: diff was Helm YAML only; `helm lint` + dual-mode `helm template` were the relevant checks (both pass).

## Next steps

1. `/gsd-verify-work 18` — run the one human UAT item (deploy chart to a real cluster, e.g. k3s with `--set hardwareAccess.enabled=false`, confirm `/health` and `/health/ready` return 200). Phase 18 completes when this passes.
2. Optionally `/gsd-code-review 18 --fix` — address CR-01/CR-02 and the 5 warnings before shipping the chart.
3. Then Phase 19 (DRY/YAGNI Refactoring).
