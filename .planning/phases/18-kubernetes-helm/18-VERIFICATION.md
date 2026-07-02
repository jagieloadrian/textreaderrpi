---
phase: 18-kubernetes-helm
verified: 2026-07-02T19:30:00Z
status: gaps_found
score: 21/23 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: false
gaps:
  - truth: "When hardwareAccess.enabled=true: container runs with privileged=true and hostPath device mounts"
    status: failed
    reason: "`privileged: true` is placed at `spec.securityContext` (PodSecurityContext, pod level). `privileged` is not a valid field on PodSecurityContext — it belongs at `spec.containers[*].securityContext`. Kubernetes silently drops or rejects it; the container does not receive privileged access, so /dev/spidev0.0 and /dev/i2c-1 are inaccessible in hardware mode."
    artifacts:
      - path: ".devops/helm/textreaderrpi/templates/deployment.yaml"
        issue: "Lines 20-23: securityContext block with privileged: true is under spec.template.spec (pod level), not under spec.template.spec.containers[0] (container level)"
    missing:
      - "Move securityContext: { privileged: true } from pod-level spec to container-level: nest it under `containers[0]:` as `securityContext: { privileged: true }`"
  - truth: "Deployment references the correct ServiceAccount name"
    status: failed
    reason: "ServiceAccount is created as `<fullname>-sa` (e.g., `textreaderrpi-sa`) by serviceaccount.yaml. The deployment's `serviceAccountName` resolves via `textreaderrpi.serviceAccountName` helper to `<fullname>` (e.g., `textreaderrpi`) — no `-sa` suffix. On any real cluster the pod admission controller rejects the pod: ServiceAccount `textreaderrpi` does not exist. This directly blocks OPS-01 criterion 1 (pod starts and passes health probes)."
    artifacts:
      - path: ".devops/helm/textreaderrpi/templates/serviceaccount.yaml"
        issue: "Creates SA with name `{{ include \"textreaderrpi.fullname\" . }}-sa` (adds -sa suffix)"
      - path: ".devops/helm/textreaderrpi/templates/_helpers.tpl"
        issue: "textreaderrpi.serviceAccountName helper returns `{{ include \"textreaderrpi.fullname\" . }}` (no -sa suffix) — diverges from what serviceaccount.yaml creates"
    missing:
      - "Either: change serviceaccount.yaml to use `{{ include \"textreaderrpi.serviceAccountName\" . }}` (remove hardcoded -sa suffix), OR update _helpers.tpl to append -sa in the helper return value. Also update rolebinding.yaml subjects.name to use the same helper so all three are consistent."
human_verification:
  - test: "Deploy chart to a real Kubernetes cluster and verify pod starts and health probes respond"
    expected: "`helm install textreaderrpi .devops/helm/textreaderrpi/` creates a running pod; `curl /health` and `curl /health/ready` return 200 OK"
    why_human: "No K8s cluster available in dev environment; template rendering cannot prove the pod actually starts. Requires fixing the two gaps above first (pod cannot start in current state due to SA mismatch)."
---

# Phase 18: Kubernetes Helm Verification Report

**Phase Goal:** Deliver a production-ready Helm chart at `.devops/helm/textreaderrpi/` that packages the TextReaderRpi application for Kubernetes, satisfying OPS-01 acceptance criteria.
**Verified:** 2026-07-02T19:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | Chart.yaml exists with apiVersion, name, version, appVersion, and metadata | ✓ VERIFIED | `apiVersion: v2`, `name: textreaderrpi`, `version: 1.0.0`, `appVersion: "1.2"` confirmed in file |
| 2  | values.yaml exists with all top-level keys: image, hardwareAccess, JAVA_TOOL_OPTIONS, replicas, persistence, service, ingress, rbac | ✓ VERIFIED | All 8 keys present in values.yaml |
| 3  | hardwareAccess.enabled defaults to false | ✓ VERIFIED | `hardwareAccess.enabled: false` in values.yaml line 9 |
| 4  | Deployment replicas: 1 is hardcoded (not .Values.replicas) | ✓ VERIFIED | `replicas: 1` literal at deployment.yaml line 8; `.Values.replicas` never referenced |
| 5  | When hardwareAccess.enabled=true: container runs with privileged=true and hostPath device mounts | ✗ FAILED | `privileged: true` placed at pod-level securityContext (spec.securityContext), not container-level. Invalid field on PodSecurityContext — silently dropped by Kubernetes. Device mounts render correctly but privileged access is non-functional. |
| 6  | When hardwareAccess.enabled=false: DISPLAY_TYPE=OFFLINE injected as env override | ✓ VERIFIED | `helm template` with defaults confirms `- name: DISPLAY_TYPE value: "OFFLINE"` in pod env |
| 7  | JAVA_TOOL_OPTIONS: -Xmx220m is in ConfigMap | ✓ VERIFIED | `JAVA_TOOL_OPTIONS: "-Xmx220m"` in configmap.yaml line 9 via `.Values.JAVA_TOOL_OPTIONS` |
| 8  | Liveness probe: GET /health (initialDelaySeconds: 20, periodSeconds: 30, timeoutSeconds: 5) | ✓ VERIFIED | Confirmed in deployment.yaml lines 54-60 and rendered template |
| 9  | Readiness probe: GET /health/ready (same timing) | ✓ VERIFIED | Confirmed in deployment.yaml lines 63-70 |
| 10 | ConfigMap textreaderrpi-config exists with non-sensitive env vars from docker-compose.yml | ✓ VERIFIED | ConfigMap renders with PORT, DISPLAY_TYPE, DATABASE_URL, JAVA_TOOL_OPTIONS, LOG_LEVEL, and 15+ additional keys |
| 11 | Secret textreaderrpi-secret with DATABASE_USER and DATABASE_PASSWORD | ✓ VERIFIED | secret.yaml renders both keys via `stringData` |
| 12 | PVC for /data: default 1Gi, release-scoped name | ✓ VERIFIED | `storage: 1Gi`, name `{{ include "textreaderrpi.fullname" . }}-data` |
| 13 | PVC for /app/logs: default 500Mi, release-scoped name | ✓ VERIFIED | `storage: 500Mi`, name `{{ include "textreaderrpi.fullname" . }}-logs` |
| 14 | Service ClusterIP exposes port 8080 | ✓ VERIFIED | service.yaml: `type: ClusterIP`, port from `.Values.service.port` (default 8080) |
| 15 | ServiceAccount is created when rbac.create=true | ✓ VERIFIED | serviceaccount.yaml gated by `{{- if .Values.rbac.create }}`, renders in default template |
| 16 | Deployment references the correct ServiceAccount name | ✗ FAILED | Deployment `serviceAccountName: textreaderrpi` (via helper returning fullname). ServiceAccount created as `textreaderrpi-sa` (+suffix). Mismatch: pod cannot start on any real cluster. |
| 17 | Role with get/list on pods exists, gated by rbac.create | ✓ VERIFIED | role.yaml: `verbs: ["get", "list"]` on `resources: ["pods"]`; absent when `--set rbac.create=false` |
| 18 | RoleBinding binds Role to ServiceAccount, gated by rbac.create | ✓ VERIFIED | rolebinding.yaml renders correctly; references SA and Role consistently within itself |
| 19 | Ingress gated by ingress.enabled (default false); renders when enabled | ✓ VERIFIED | No Ingress in default render; `kind: Ingress` appears with `--set ingress.enabled=true` |
| 20 | README.md documents helm install/uninstall and hardwareAccess toggle | ✓ VERIFIED | README.md present with Installation, Uninstallation, Configuration table, hardwareAccess warning |
| 21 | helm lint passes with 0 chart(s) failed | ✓ VERIFIED | `1 chart(s) linted, 0 chart(s) failed` (INFO icon warning only) |
| 22 | helm template renders all expected resource kinds | ✓ VERIFIED | ConfigMap, Deployment, 2x PersistentVolumeClaim, Role, RoleBinding, Secret, Service, ServiceAccount (9 resources) |
| 23 | CI workflow .github/workflows/helm-lint.yml runs helm lint and template variants | ✓ VERIFIED | File exists; covers lint + 4 template variants (default, hardwareAccess=true, rbac=false, ingress=true) |

**Score:** 21/23 truths verified (0 present, behavior-unverified)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `.devops/helm/textreaderrpi/Chart.yaml` | Helm chart metadata | ✓ VERIFIED | apiVersion v2, version 1.0.0, appVersion 1.2 |
| `.devops/helm/textreaderrpi/values.yaml` | Default configuration with all keys | ✓ VERIFIED | All top-level keys present |
| `.devops/helm/textreaderrpi/templates/deployment.yaml` | Deployment spec with conditional logic | ⚠️ PARTIAL | Replicas, probes, env injection correct; privileged at wrong API level (CR-02) |
| `.devops/helm/textreaderrpi/templates/_helpers.tpl` | Helper functions | ⚠️ PARTIAL | Exists and substantive; serviceAccountName helper diverges from SA created name (CR-03) |
| `.devops/helm/textreaderrpi/templates/configmap.yaml` | Environment variables | ✓ VERIFIED | JAVA_TOOL_OPTIONS and 15+ env vars present |
| `.devops/helm/textreaderrpi/templates/secret.yaml` | Database credentials | ✓ VERIFIED | DATABASE_USER and DATABASE_PASSWORD via stringData |
| `.devops/helm/textreaderrpi/templates/pvc-data.yaml` | H2 database persistence (1Gi) | ✓ VERIFIED | release-scoped name, 1Gi default |
| `.devops/helm/textreaderrpi/templates/pvc-logs.yaml` | Application logs persistence (500Mi) | ✓ VERIFIED | release-scoped name, 500Mi default |
| `.devops/helm/textreaderrpi/templates/service.yaml` | ClusterIP service on 8080 | ✓ VERIFIED | type ClusterIP, port 8080 |
| `.devops/helm/textreaderrpi/templates/serviceaccount.yaml` | Pod identity | ⚠️ PARTIAL | Exists, gated by rbac.create, but name has -sa suffix not matched by deployment |
| `.devops/helm/textreaderrpi/templates/role.yaml` | RBAC pod permissions | ✓ VERIFIED | get/list on pods, rbac-gated |
| `.devops/helm/textreaderrpi/templates/rolebinding.yaml` | RBAC binding | ✓ VERIFIED | Binds role to SA (internal consistency; deployment SA ref is the broken link) |
| `.devops/helm/textreaderrpi/templates/ingress.yaml` | Optional HTTP ingress | ✓ VERIFIED | Gated by ingress.enabled, no TLS |
| `.devops/helm/textreaderrpi/README.md` | Chart usage documentation | ✓ VERIFIED | install/uninstall, hardwareAccess, configuration table, troubleshooting |
| `.github/workflows/helm-lint.yml` | CI lint job | ✓ VERIFIED | Lint + 4 template variants on chart path changes |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| deployment.yaml | configmap.yaml | `envFrom.configMapRef.name: textreaderrpi-config` | ✓ WIRED | Name hardcoded; matches ConfigMap metadata.name |
| deployment.yaml | secret.yaml | `secretKeyRef.name: textreaderrpi-secret` | ✓ WIRED | Name hardcoded; matches Secret metadata.name |
| deployment.yaml | pvc-data.yaml | `claimName: {{ include "textreaderrpi.fullname" . }}-data` | ✓ WIRED | Naming convention consistent |
| deployment.yaml | pvc-logs.yaml | `claimName: {{ include "textreaderrpi.fullname" . }}-logs` | ✓ WIRED | Naming convention consistent |
| deployment.yaml | serviceaccount.yaml | `serviceAccountName: {{ include "textreaderrpi.serviceAccountName" . }}` | ✗ NOT_WIRED | Helper returns fullname (no -sa); SA created as fullname-sa. Referenced SA does not exist. |
| rolebinding.yaml | serviceaccount.yaml | `subjects[0].name: {{ include "textreaderrpi.fullname" . }}-sa` | ✓ WIRED | Consistent with SA creation name |
| rolebinding.yaml | role.yaml | `roleRef.name: {{ include "textreaderrpi.fullname" . }}-role` | ✓ WIRED | Consistent with Role creation name |
| service.yaml | deployment.yaml | selector via `textreaderrpi.selectorLabels` | ✓ WIRED | Same helper used in both; labels will match |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| helm lint passes | `helm lint .devops/helm/textreaderrpi/` | 1 chart(s) linted, 0 chart(s) failed | ✓ PASS |
| All 9 resource kinds render | `helm template ... \| grep "^kind:" \| sort \| uniq -c` | ConfigMap×1, Deployment×1, PVC×2, Role×1, RoleBinding×1, Secret×1, Service×1, ServiceAccount×1 | ✓ PASS |
| DISPLAY_TYPE=OFFLINE in default render | `helm template ... \| grep -A5 "name: DISPLAY_TYPE"` | `value: "OFFLINE"` present | ✓ PASS |
| JAVA_TOOL_OPTIONS=-Xmx220m in ConfigMap | `helm template ... \| grep JAVA_TOOL_OPTIONS` | `"-Xmx220m"` | ✓ PASS |
| privileged=true at container level when hardware=true | `helm template ... --set hardwareAccess.enabled=true \| grep -B5 privileged` | `privileged: true` under `spec.securityContext` (pod level — WRONG location) | ✗ FAIL |
| SA name matches deployment reference | Compare `serviceAccountName:` vs SA `metadata.name:` | Deployment: `textreaderrpi`, SA: `textreaderrpi-sa` — mismatch | ✗ FAIL |
| RBAC resources absent when rbac.create=false | `helm template ... --set rbac.create=false \| grep "kind: Role" \| wc -l` | 0 | ✓ PASS |
| Ingress renders when ingress.enabled=true | `helm template ... --set ingress.enabled=true \| grep "kind: Ingress"` | `kind: Ingress` present | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| OPS-01 | 18-01 through 18-08 | TextReaderRpi deployable to Kubernetes via Helm chart with hardwareAccess toggle, -Xmx220m, health probes at /health + /health/ready | ✗ PARTIAL | Chart exists and lints; template rendering correct for most paths. Two runtime blockers: (1) privileged=true at wrong K8s API level — hardware access non-functional; (2) SA name mismatch — pod cannot start on real cluster. OPS-01 criterion 1 (pod starts + health probes pass) is blocked. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `values.yaml` | 60 | `database.password: "password"` — hardcoded credential in VCS | ⚠️ WARNING | Not a runtime blocker, but a security concern for any production deployment; Secret renders this as the default |
| `templates/deployment.yaml` | 20-23 | `spec.securityContext.privileged: true` — `privileged` is container-level, not pod-level | 🛑 BLOCKER | Hardware access mode silently broken; CR-02 from code review |
| `templates/serviceaccount.yaml` | 5 | SA name `{{ include "textreaderrpi.fullname" . }}-sa` diverges from helper return | 🛑 BLOCKER | Deployment references non-existent SA; pod cannot start; CR-03 from code review |
| `values.yaml` | 15 | `replicas: 1` — dead config never read by deployment template | ℹ️ INFO | Misleads users; `--set replicas=2` has no effect |
| `.github/workflows/helm-lint.yml` | 18 | `actions/checkout@v3` (outdated) and `azure/setup-helm@v3 version: 'latest'` (non-deterministic) | ⚠️ WARNING | CI may break on future Helm releases without code change |
| `Chart.yaml` | 7-9 | Placeholder URLs `https://github.com/yourusername/textreaderrpi` | ℹ️ INFO | Appears in `helm show chart` output |

### Human Verification Required

#### 1. Live Pod Deployment and Health Probe Verification

**Test:** After fixing the two gaps below, run `helm install textreaderrpi .devops/helm/textreaderrpi/ --set hardwareAccess.enabled=false --namespace default` against a real Kubernetes cluster (k3d, k3s, kind, or bare cluster). Wait for pod readiness, then verify `/health` and `/health/ready` return HTTP 200.

**Expected:** Pod reaches Running state; `kubectl port-forward` + `curl http://127.0.0.1:8080/health` returns 200 OK; `curl http://127.0.0.1:8080/health/ready` returns 200 OK.

**Why human:** No K8s cluster available in dev environment. Template rendering cannot prove pod actually starts, PVCs bind, and the JVM initializes the database. Requires on-device or cluster deployment. In current state this would fail due to SA mismatch (Gap 2) — fix gaps first.

---

## Gaps Summary

Two runtime blockers prevent the phase goal from being achieved. Both are correctness defects in Helm templates that `helm lint` and `helm template` do not catch — they are Kubernetes API semantics issues.

**Gap 1 — privileged at wrong securityContext level (CR-02):** The deployment template places `privileged: true` at `spec.template.spec.securityContext` (PodSecurityContext). `privileged` is only valid at `spec.template.spec.containers[*].securityContext` (SecurityContext). Kubernetes silently ignores it at pod level; the container runs unprivileged regardless of the `hardwareAccess.enabled=true` flag. Hardware mode (`/dev/spidev0.0`, `/dev/i2c-1`) is non-functional.

**Fix:** In `deployment.yaml`, move the conditional block to container level:
```yaml
containers:
- name: textreaderrpi
  {{- if .Values.hardwareAccess.enabled }}
  securityContext:
    privileged: true
  {{- end }}
  image: ...
```

**Gap 2 — ServiceAccount name mismatch (CR-03):** `serviceaccount.yaml` creates the SA as `{{ include "textreaderrpi.fullname" . }}-sa` (e.g., `textreaderrpi-sa`). The `textreaderrpi.serviceAccountName` helper in `_helpers.tpl` returns `{{ include "textreaderrpi.fullname" . }}` (no `-sa` suffix, e.g., `textreaderrpi`). The deployment uses the helper; the SA it references does not exist. Pod admission fails on every real cluster: "serviceaccount textreaderrpi not found."

**Fix (option A — preferred):** Change `serviceaccount.yaml` to use the same helper:
```yaml
metadata:
  name: {{ include "textreaderrpi.serviceAccountName" . }}
```
Also update `rolebinding.yaml` subjects to use `{{ include "textreaderrpi.serviceAccountName" . }}` so all three are consistent.

**Fix (option B):** Update the helper to append `-sa`:
```yaml
{{- define "textreaderrpi.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- printf "%s-sa" (include "textreaderrpi.fullname" .) }}
```
Then update `deployment.yaml` and `rolebinding.yaml` consistently.

---

_Verified: 2026-07-02T19:30:00Z_
_Verifier: Claude (gsd-verifier)_
