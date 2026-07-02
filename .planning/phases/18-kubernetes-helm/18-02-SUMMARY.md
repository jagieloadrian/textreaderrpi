---
phase: 18-kubernetes-helm
plan: "02"
subsystem: devops
tags: [helm, kubernetes, deployment, hardware-access, probes]
status: complete

dependency_graph:
  requires: [helm-chart-skeleton]
  provides: [helm-deployment-template, helm-helpers]
  affects: [.devops/helm/textreaderrpi/templates/]

tech_stack:
  added: []
  patterns: [conditional-helm-templates, hardware-access-toggle, http-probes, hostpath-volumes]

key_files:
  created:
    - .devops/helm/textreaderrpi/templates/deployment.yaml
    - .devops/helm/textreaderrpi/templates/_helpers.tpl
  modified: []

decisions:
  - "securityContext.privileged placed at pod spec level (not container level) — matches K8s privileged semantics"
  - "DISPLAY_TYPE=OFFLINE injected as env override (not envFrom) so it takes precedence over ConfigMap value"
  - "replicas: 1 hardcoded in template (not .Values.replicas) — Pi hardware cannot share SPI/I2C + H2 file DB"
  - "hostPath type: CharDevice for /dev/spidev0.0 and /dev/i2c-1 — explicit type prevents accidental bind of non-device paths"

metrics:
  duration: "3 minutes"
  completed: "2026-07-02"
  tasks_completed: 3
  tasks_total: 3
---

# Phase 18 Plan 02: Deployment Template + Helper Functions Summary

Helm Deployment template with hardcoded replicas:1, conditional privileged+hostPath device mounts for hardware access, OFFLINE env override, HTTP liveness/readiness probes, and standard _helpers.tpl with fullname/labels/serviceAccount helpers.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Create deployment.yaml with conditional hardwareAccess, probes, PVC mounts | 1506f48 |
| 2 | Create _helpers.tpl with fullname, labels, selectorLabels, serviceAccountName | 1506f48 |
| 3 | Verify helm lint + template rendering (both hardwareAccess modes) | 1506f48 |

## Verification Results

- `helm lint .devops/helm/textreaderrpi/` — 1 chart(s) linted, 0 chart(s) failed
- `replicas: 1` hardcoded (not `.Values.replicas`)
- `hardwareAccess.enabled=false` (default): `DISPLAY_TYPE: "OFFLINE"` injected, no device mounts
- `hardwareAccess.enabled=true`: `privileged: true` rendered, spidev + i2c hostPath volumes mounted
- Liveness probe: `GET /health` — initialDelaySeconds:20, periodSeconds:30, timeoutSeconds:5
- Readiness probe: `GET /health/ready` — same timing
- Resources: requests 100m/128Mi, limits 500m/256Mi

## Deviations from Plan

### Notes

**1. Files already implemented**
- Found during: Execution start
- Status: Both tasks already complete in commit `1506f48` (same commit as Plan 01)
- The full template suite was implemented during planning/context phase on branch `feat/18-helm-chart`
- No re-implementation needed; all acceptance criteria verified and passing

## Known Stubs

None — deployment.yaml references ConfigMap `textreaderrpi-config` and Secret `textreaderrpi-secret` which are created in Plan 03 (18-03). This is an intentional forward reference, not a stub.

## Threat Flags

None — Deployment template introduces no new network endpoints or auth paths. The `privileged: true` toggle is gated behind `hardwareAccess.enabled` which defaults to `false`; hostPath device mounts are limited to `/dev/spidev0.0` and `/dev/i2c-1` with `type: CharDevice`.

## Self-Check: PASSED

- [x] `.devops/helm/textreaderrpi/templates/deployment.yaml` exists (commit 1506f48)
- [x] `.devops/helm/textreaderrpi/templates/_helpers.tpl` exists (commit 1506f48)
- [x] `helm lint` passes with 0 failures
- [x] `replicas: 1` hardcoded
- [x] `DISPLAY_TYPE: "OFFLINE"` injected when hardwareAccess=false
- [x] `privileged: true` + device mounts when hardwareAccess=true
- [x] Liveness and readiness probes present with correct paths and timing
