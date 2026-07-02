---
phase: 18-kubernetes-helm
plan: "05"
subsystem: devops
tags: [helm, kubernetes, service, rbac, serviceaccount, role, rolebinding]
status: complete

dependency_graph:
  requires: [helm-chart-skeleton, helm-deployment-template]
  provides: [helm-service, helm-rbac]
  affects: [.devops/helm/textreaderrpi/templates/]

tech_stack:
  added: []
  patterns: [clusterip-service, namespace-scoped-rbac, conditional-rbac-gating]

key_files:
  created: []
  modified:
    - .devops/helm/textreaderrpi/templates/service.yaml
    - .devops/helm/textreaderrpi/templates/serviceaccount.yaml
    - .devops/helm/textreaderrpi/templates/role.yaml
    - .devops/helm/textreaderrpi/templates/rolebinding.yaml

decisions:
  - "Service type ClusterIP (not NodePort/LoadBalancer) — internal cluster routing only; Ingress handles external access"
  - "ServiceAccount name uses -sa suffix (not fullname alone) — avoids collision with Deployment name"
  - "Role scoped to namespace (not ClusterRole) — minimal privilege; pod needs only pods get/list in own namespace"
  - "All RBAC resources gated by rbac.create — disabling falls back to cluster default ServiceAccount"

metrics:
  duration: "2 minutes"
  completed: "2026-07-02"
  tasks_completed: 7
  tasks_total: 7
---

# Phase 18 Plan 05: Service, ServiceAccount, and RBAC Templates Summary

ClusterIP Service on port 8080 + namespace-scoped RBAC (ServiceAccount, Role with pods get/list, RoleBinding) — all gated by `rbac.create`.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Create service.yaml — ClusterIP type, port 8080, selector via selectorLabels helper | 1506f48 |
| 2 | Create serviceaccount.yaml — gated by rbac.create, name with -sa suffix | 1506f48 |
| 3 | Create role.yaml — namespace Role with pods get/list, gated by rbac.create | 1506f48 |
| 4 | Create rolebinding.yaml — binds Role to ServiceAccount, gated by rbac.create | 1506f48 |
| 5 | Verify values.yaml has service + rbac + serviceAccount keys (already present from Plan 01) | 1506f48 |
| 6 | Verify Deployment references ServiceAccount via serviceAccountName helper | 1506f48 |
| 7 | Run helm template + helm lint — all resources render, RBAC gating works | 1506f48 |

## Verification Results

- `helm lint .devops/helm/textreaderrpi/` — 1 chart(s) linted, 0 chart(s) failed
- `helm template` with rbac.create=true (default): Service, ServiceAccount, Role, RoleBinding all rendered
- `helm template --set rbac.create=false | grep "kind: Role" | wc -l` → `0` (RBAC gated correctly)
- Service port: 8080 → targetPort: 8080, protocol: TCP, type: ClusterIP
- Role rules: apiGroups: [""], resources: ["pods"], verbs: ["get", "list"]
- RoleBinding subjects: ServiceAccount textreaderrpi-sa in namespace default
- Deployment.spec.serviceAccountName: references `textreaderrpi.serviceAccountName` helper

## Deviations from Plan

### Notes

**1. Files already implemented**
- Found during: Execution start
- Status: All 4 template files already complete in commit `1506f48` (same commit as Plans 01-04)
- The complete template suite was implemented during planning/context phase on branch `feat/18-helm-chart`
- All acceptance criteria verified and passing — no re-implementation needed

## Self-Check: PASSED

- [x] `.devops/helm/textreaderrpi/templates/service.yaml` exists (commit 1506f48)
- [x] `.devops/helm/textreaderrpi/templates/serviceaccount.yaml` exists (commit 1506f48)
- [x] `.devops/helm/textreaderrpi/templates/role.yaml` exists (commit 1506f48)
- [x] `.devops/helm/textreaderrpi/templates/rolebinding.yaml` exists (commit 1506f48)
- [x] `helm lint` passes with 0 failures
- [x] Service exposes port 8080 (ClusterIP)
- [x] RBAC resources gated by rbac.create — 0 Role kinds when disabled
- [x] Deployment references serviceAccountName via helper
- [x] Commit `1506f48` confirmed in git log
