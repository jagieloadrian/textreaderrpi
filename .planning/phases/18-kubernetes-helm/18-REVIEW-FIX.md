---
phase: 18-kubernetes-helm
fixed_at: 2026-07-06T00:00:00Z
review_path: .planning/phases/18-kubernetes-helm/18-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 18: Code Review Fix Report

**Fixed at:** 2026-07-06
**Source review:** .planning/phases/18-kubernetes-helm/18-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (fix_scope: critical_warning — CR-01, CR-02, WR-01..WR-05)
- Fixed: 7
- Skipped: 0

All fixes validated with `helm lint` and `helm template` (helm v4.2.2) across permutations: default, `rbac.create=false`, `serviceAccount.create=false`, `hardwareAccess.enabled=true`, `ingress.enabled=true`. Verified must-haves intact after all fixes: container-level `privileged: true` gated on `hardwareAccess.enabled`, ServiceAccount name resolved via `textreaderrpi.serviceAccountName` helper everywhere, liveness/readiness probes and `JAVA_TOOL_OPTIONS` wiring unchanged.

## Fixed Issues

### CR-01: ServiceAccount template gated on `rbac.create`, but naming helper branches on `serviceAccount.create`

**Files modified:** `.devops/helm/textreaderrpi/templates/serviceaccount.yaml`, `.github/workflows/helm-lint.yml`
**Commit:** 2196807
**Applied fix:** Changed the ServiceAccount template gate from `.Values.rbac.create` to `.Values.serviceAccount.create` (matching the helper). `rbac.create` still gates only Role/RoleBinding. Added a CI template step for `--set serviceAccount.create=false`. Verified: `rbac.create=false` renders the SA the Deployment references; `serviceAccount.create=false` renders no SA and the Deployment uses `default`.

### CR-02: Default RollingUpdate strategy deadlocks every `helm upgrade` against the H2 file lock

**Files modified:** `.devops/helm/textreaderrpi/templates/deployment.yaml`, `.devops/helm/textreaderrpi/README.md`
**Commit:** 3b6592e
**Applied fix:** Added `strategy: {type: Recreate}` to the Deployment spec so the old pod releases the H2 file lock before the new pod starts. Rewrote the README "Database locked" troubleshooting section: pod deletion first, PVC deletion explicitly demoted to last resort with a data-loss warning.

### WR-01: ConfigMap and Secret names hardcoded, bypassing the fullname helper

**Files modified:** `.devops/helm/textreaderrpi/templates/configmap.yaml`, `.devops/helm/textreaderrpi/templates/secret.yaml`, `.devops/helm/textreaderrpi/templates/deployment.yaml`
**Commit:** 55824af
**Applied fix:** ConfigMap now named `{{ include "textreaderrpi.fullname" . }}-config`, Secret `{{ include "textreaderrpi.fullname" . }}-secret`; updated the Deployment's `configMapRef` and both `secretKeyRef` references. Verified with `--set fullnameOverride=myapp` — all five references render `myapp-config`/`myapp-secret`.

### WR-02: Default database credentials (`sa`/`password`) shipped in values.yaml

**Files modified:** `.devops/helm/textreaderrpi/templates/secret.yaml`, `.devops/helm/textreaderrpi/values.yaml`, `.github/workflows/helm-lint.yml`, `.devops/helm/textreaderrpi/README.md`
**Commit:** 49b3536
**Applied fix:** `DATABASE_PASSWORD` now uses `required` (fail-fast at install when unset); `values.yaml` ships `password: ""` with a REQUIRED comment. All CI lint/template steps pass a throwaway `--set database.password=ci-lint-only`. README: prominent required-password note under Installation, updated default install example, added `database.user`/`database.password` rows to the configuration table. Verified: template fails with a clear error when unset, passes when set.

### WR-03: ConfigMap/Secret changes do not restart the pod on upgrade

**Files modified:** `.devops/helm/textreaderrpi/templates/deployment.yaml`
**Commit:** 95eec81
**Applied fix:** Added `checksum/config` and `checksum/secret` sha256sum annotations to the pod template, and wrapped `podAnnotations` in the idiomatic `{{- with }}` guard (also resolves IN-03). Verified: checksum changes when `database.password` changes; custom podAnnotations still render alongside checksums.

### WR-04: `persistence.data.enabled` / `persistence.logs.enabled` flags are dead

**Files modified:** `.devops/helm/textreaderrpi/values.yaml`
**Commit:** baeef01
**Applied fix:** Deleted both dead `enabled` keys (the review's recommended simpler option — persistence is mandatory for the H2 file DB) and documented in a values.yaml comment that the volumes are always created. Verified both PVCs still render.

### WR-05: CI uses unpinned actions, non-deterministic Helm version, and no permissions block

**Files modified:** `.github/workflows/helm-lint.yml`
**Commit:** f82ade1
**Applied fix:** Added `permissions: contents: read`, bumped `actions/checkout@v4` and `azure/setup-helm@v4`, pinned Helm to `v3.16.3`. Workflow YAML parse-checked.

---

_Fixed: 2026-07-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
