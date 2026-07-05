---
phase: 18-kubernetes-helm
plan: 09
subsystem: infra
tags: [helm, kubernetes, securitycontext, serviceaccount, rbac]

# Dependency graph
requires:
  - phase: 18-kubernetes-helm (plans 01-08)
    provides: full Helm chart skeleton (Deployment, ServiceAccount, RoleBinding, Role, PVCs, ConfigMap, Secret, Ingress)
provides:
  - Container-level privileged securityContext (was silently ignored at pod level)
  - Unified ServiceAccount naming across ServiceAccount/Deployment/RoleBinding via textreaderrpi.serviceAccountName helper
affects: [18-kubernetes-helm final verification]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - .devops/helm/textreaderrpi/templates/deployment.yaml
    - .devops/helm/textreaderrpi/templates/serviceaccount.yaml
    - .devops/helm/textreaderrpi/templates/rolebinding.yaml

key-decisions:
  - "privileged: true belongs under containers[0].securityContext, not pod-level spec.securityContext (Kubernetes silently ignores privileged at pod level)"
  - "ServiceAccount and RoleBinding subject converge on textreaderrpi.serviceAccountName helper, matching what Deployment already used"

patterns-established: []

requirements-completed: [OPS-01]

coverage:
  - id: D1
    description: "privileged: true renders under container securityContext (not pod level) when hardwareAccess.enabled=true; no securityContext at pod level"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm template textreaderrpi .devops/helm/textreaderrpi/ --set hardwareAccess.enabled=true (manual grep of rendered YAML)"
        status: pass
    human_judgment: false
  - id: D2
    description: "ServiceAccount metadata.name, Deployment serviceAccountName, and RoleBinding subject name all render identically"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm template textreaderrpi .devops/helm/textreaderrpi/ | grep -E 'serviceAccountName:|name: textreaderrpi'"
        status: pass
    human_judgment: false
  - id: D3
    description: "helm lint passes and helm template renders cleanly for hardwareAccess.enabled=true and =false"
    requirement: "OPS-01"
    verification:
      - kind: other
        ref: "helm lint .devops/helm/textreaderrpi/ (0 chart(s) failed); helm template with both --set values"
        status: pass
    human_judgment: false

# Metrics
duration: 5min
completed: 2026-07-06
status: complete
---

# Phase 18 Plan 09: Gap Closure — securityContext + ServiceAccount naming Summary

**Fixed two runtime-blocking Kubernetes API semantics defects: privileged securityContext moved from ineffective pod-level to correct container-level, and ServiceAccount naming unified via existing serviceAccountName helper.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-07-05T22:01:00Z
- **Completed:** 2026-07-06T00:00:00Z (approx)
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- `privileged: true` now renders under `containers[0].securityContext`, where Kubernetes actually honors it — previously placed at pod-level `spec.securityContext`, where the field is silently dropped and hardware mode never got device access
- `serviceaccount.yaml` and `rolebinding.yaml` now render the ServiceAccount name via the same `textreaderrpi.serviceAccountName` helper the Deployment already used, eliminating a `<fullname>-sa` vs `<fullname>` mismatch that would fail pod admission on any real cluster
- Full-chart validation: `helm lint` (0 failures) and `helm template` for both `hardwareAccess.enabled=true` and `=false` all succeed

## Task Commits

Each task was committed atomically:

1. **Task 1: Move privileged securityContext from pod level to container level** - `2d29d59` (fix)
2. **Task 2: Unify ServiceAccount naming through the serviceAccountName helper** - `66e69fb` (fix)
3. **Task 3: Full-chart validation** - no commit (verification only, no file changes required)

**Plan metadata:** (recorded in final commit, see below)

## Files Created/Modified
- `.devops/helm/textreaderrpi/templates/deployment.yaml` - securityContext block moved from pod spec to container spec
- `.devops/helm/textreaderrpi/templates/serviceaccount.yaml` - name now uses `textreaderrpi.serviceAccountName` helper instead of `<fullname>-sa` literal
- `.devops/helm/textreaderrpi/templates/rolebinding.yaml` - subject name now uses `textreaderrpi.serviceAccountName` helper instead of `<fullname>-sa` literal

## Decisions Made
- `privileged: true` is a container-level securityContext field only; Kubernetes API silently ignores it at pod level — no schema validation catches this, hence a gap-closure plan (CR-02 from 18-VERIFICATION.md) was required instead of relying on `helm lint`
- ServiceAccount/RoleBinding converge on the pre-existing `textreaderrpi.serviceAccountName` helper rather than introducing a new one; `_helpers.tpl` and `deployment.yaml` were untouched per plan scope (CR-03)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Both runtime blockers identified in 18-VERIFICATION.md (CR-02, CR-03) are closed
- Chart is ready for phase-level final verification (18-VERIFICATION re-check or new verification pass)
- No blockers remain from this plan's scope

---
*Phase: 18-kubernetes-helm*
*Completed: 2026-07-06*

## Self-Check: PASSED
