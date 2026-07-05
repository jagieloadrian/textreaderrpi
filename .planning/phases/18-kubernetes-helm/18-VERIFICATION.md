---
phase: 18-kubernetes-helm
verified: 2026-07-06T00:20:00Z
status: human_needed
score: 23/23 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: true
re_verification_data:
  previous_status: gaps_found
  previous_score: 21/23
  gaps_closed:
    - "When hardwareAccess.enabled=true: container runs with privileged=true and hostPath device mounts (was: privileged at wrong pod-level securityContext)"
    - "Deployment references the correct ServiceAccount name (was: <fullname>-sa created vs <fullname> referenced)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Deploy chart to a real Kubernetes cluster and verify pod starts and health probes respond"
    expected: "`helm install textreaderrpi .devops/helm/textreaderrpi/` creates a running pod; `curl /health` and `curl /health/ready` return 200 OK"
    why_human: "No K8s cluster available in dev environment. Template rendering confirms correctness of manifests (privileged at container level, SA names consistent, probes/resources wired) but cannot prove the pod actually reaches Running state, PVCs bind, and the JVM initializes the H2 database on a real node."
---

# Phase 18: Kubernetes Helm Verification Report

**Phase Goal:** TextReaderRpi can be deployed to a Kubernetes cluster using a Helm chart with hardware-access and resource controls.
**Verified:** 2026-07-06T00:20:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (plan 18-09)

---

## Re-Verification Summary

The previous verification (2026-07-02) found 2 gaps, both classified 🛑 BLOCKER:

1. **CR-02 (old):** `privileged: true` placed at pod-level `spec.securityContext` instead of container-level — silently dropped by Kubernetes, hardware mode non-functional.
2. **CR-03 (old):** ServiceAccount created as `<fullname>-sa` but Deployment referenced `<fullname>` via helper — pod admission would fail on any real cluster ("serviceaccount ... not found").

Gap-closure plan 18-09 (commits `2d29d59`, `66e69fb`) addressed both. Both fixes were independently re-verified below by re-rendering the chart with `helm template` (not by trusting the SUMMARY).

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
| --- | ------- | ---------- | -------------- |
| 1 | Chart.yaml exists with apiVersion, name, version, appVersion, metadata | ✓ VERIFIED | Unchanged since prior verification; regression check only |
| 2 | values.yaml exists with all top-level keys: image, hardwareAccess, JAVA_TOOL_OPTIONS, replicas, persistence, service, ingress, rbac | ✓ VERIFIED | All 8 keys still present |
| 3 | hardwareAccess.enabled defaults to false | ✓ VERIFIED | `values.yaml:9` |
| 4 | Deployment replicas: 1 hardcoded (not .Values.replicas) | ✓ VERIFIED | `deployment.yaml:8` literal `replicas: 1` |
| 5 | **When hardwareAccess.enabled=true: container runs with privileged=true and hostPath device mounts** | ✓ VERIFIED (gap closed) | `helm template --set hardwareAccess.enabled=true` renders exactly one `securityContext:` block, nested inside `containers[0]` (line 230, immediately after `imagePullPolicy`), NOT at `spec.template.spec` pod level. Confirmed by line-numbered grep of rendered output — see raw render below. Device hostPath mounts for `/dev/spidev0.0` and `/dev/i2c-1` still render correctly (unchanged, was already passing). |
| 6 | When hardwareAccess.enabled=false: DISPLAY_TYPE=OFFLINE injected, no privileged/device mounts | ✓ VERIFIED | `--set hardwareAccess.enabled=false` renders 0 occurrences of `privileged`; `DISPLAY_TYPE: "OFFLINE"` present |
| 7 | JAVA_TOOL_OPTIONS: -Xmx220m in ConfigMap | ✓ VERIFIED | `configmap.yaml:9` (unchanged) |
| 8 | Liveness probe: GET /health (20/30/5) | ✓ VERIFIED | `deployment.yaml:54-61` |
| 9 | Readiness probe: GET /health/ready (20/30/5) | ✓ VERIFIED | `deployment.yaml:63-70` |
| 10 | ConfigMap textreaderrpi-config with non-sensitive env vars | ✓ VERIFIED | Unchanged, renders correctly |
| 11 | Secret textreaderrpi-secret with DATABASE_USER/PASSWORD | ✓ VERIFIED | Unchanged, renders via stringData |
| 12 | PVC for /data: default 1Gi, release-scoped name | ✓ VERIFIED | Unchanged |
| 13 | PVC for /app/logs: default 500Mi, release-scoped name | ✓ VERIFIED | Unchanged |
| 14 | Service ClusterIP exposes port 8080 | ✓ VERIFIED | Unchanged |
| 15 | ServiceAccount created when rbac.create=true | ✓ VERIFIED | Renders in default template |
| 16 | **Deployment references the correct ServiceAccount name** | ✓ VERIFIED (gap closed) | Default render: ServiceAccount `metadata.name: textreaderrpi`, RoleBinding `subjects[0].name: textreaderrpi`, Deployment `serviceAccountName: textreaderrpi` — all three now identical (previously SA was `textreaderrpi-sa`, referenced as `textreaderrpi`). Confirmed by direct grep of all three template outputs. |
| 17 | Role with get/list on pods, gated by rbac.create | ✓ VERIFIED | Unchanged |
| 18 | RoleBinding binds Role to ServiceAccount, gated by rbac.create | ✓ VERIFIED | Now references the same (fixed) ServiceAccount name as the Deployment — this closes the gap end-to-end, not just internally consistent as before |
| 19 | Ingress gated by ingress.enabled (default false) | ✓ VERIFIED | `--set ingress.enabled=true` renders `kind: Ingress` |
| 20 | README.md documents helm install/uninstall and hardwareAccess toggle | ✓ VERIFIED | Unchanged |
| 21 | helm lint passes with 0 chart(s) failed | ✓ VERIFIED | `helm lint .devops/helm/textreaderrpi/` → `1 chart(s) linted, 0 chart(s) failed` (only an INFO on missing icon) |
| 22 | helm template renders all expected resource kinds | ✓ VERIFIED | ConfigMap×1, Deployment×1, PVC×2, Role×1, RoleBinding×1, Secret×1, Service×1, ServiceAccount×1 (9 resources, default values) |
| 23 | CI workflow .github/workflows/helm-lint.yml runs helm lint and template variants | ✓ VERIFIED | Unchanged; still covers lint + 4 template variants |

**Score:** 23/23 truths verified (0 present, behavior-unverified)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `.devops/helm/textreaderrpi/templates/deployment.yaml` | Deployment spec with conditional logic | ✓ VERIFIED | Privileged securityContext now correctly nested under `containers[0]` (was pod-level) |
| `.devops/helm/textreaderrpi/templates/serviceaccount.yaml` | Pod identity | ✓ VERIFIED | Name now uses `textreaderrpi.serviceAccountName` helper (was hardcoded `-sa` suffix) |
| `.devops/helm/textreaderrpi/templates/rolebinding.yaml` | RBAC binding | ✓ VERIFIED | Subject name now uses the same helper, consistent with ServiceAccount and Deployment |
| `.devops/helm/textreaderrpi/templates/_helpers.tpl` | Helper functions | ✓ VERIFIED | Untouched per plan scope; `serviceAccountName` helper unchanged and now correctly the single source of truth for all three consumers |
| (All other artifacts from initial verification) | — | ✓ VERIFIED | No regressions found; re-checked existence/render for Chart.yaml, values.yaml, configmap.yaml, secret.yaml, pvc-data.yaml, pvc-logs.yaml, service.yaml, role.yaml, ingress.yaml, README.md, helm-lint.yml |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| deployment.yaml | serviceaccount.yaml | `serviceAccountName: {{ include "textreaderrpi.serviceAccountName" . }}` | ✓ WIRED (was NOT_WIRED) | Helper now returns the same value the ServiceAccount is actually created with (`textreaderrpi`) |
| rolebinding.yaml | serviceaccount.yaml | `subjects[0].name: {{ include "textreaderrpi.serviceAccountName" . }}` | ✓ WIRED (was WIRED to the wrong name) | Now converges on the same helper as ServiceAccount and Deployment — the binding subject matches the actual SA name |
| deployment.yaml (container) | hardwareAccess.enabled | `securityContext.privileged` at container scope | ✓ WIRED (was NOT_WIRED — invalid pod-level field) | Kubernetes honors `privileged` at container level; hostPath device mounts unchanged and correctly gated |
| deployment.yaml | configmap.yaml / secret.yaml / pvc-data.yaml / pvc-logs.yaml | envFrom / secretKeyRef / claimName | ✓ WIRED | Unchanged, re-confirmed |
| service.yaml | deployment.yaml | selector via `textreaderrpi.selectorLabels` | ✓ WIRED | Unchanged |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| helm lint passes | `helm lint .devops/helm/textreaderrpi/` | `1 chart(s) linted, 0 chart(s) failed` | ✓ PASS |
| privileged renders at container level, hw=true | `helm template ... --set hardwareAccess.enabled=true \| grep -n securityContext` | Single match, line 230, inside `containers[0]` block, after `imagePullPolicy` | ✓ PASS |
| no privileged rendered, hw=false | `helm template ... --set hardwareAccess.enabled=false \| grep -c privileged` | `0` | ✓ PASS |
| SA name identical across ServiceAccount/Deployment/RoleBinding | `helm template ... \| grep -E "kind: ServiceAccount|serviceAccountName:|subjects:" -A2` | All three render `textreaderrpi` | ✓ PASS |
| All 9 resource kinds render (default) | `helm template ... \| grep "^kind:" \| sort \| uniq -c` | ConfigMap, Deployment, 2×PVC, Role, RoleBinding, Secret, Service, ServiceAccount | ✓ PASS |
| Ingress renders when enabled | `helm template ... --set ingress.enabled=true \| grep "kind: Ingress"` | Present | ✓ PASS |
| No debt markers (TBD/FIXME/XXX) in chart | `grep -rn -E "TBD\|FIXME\|XXX" .devops/helm/textreaderrpi/` | No matches | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` conventional probes exist for this phase, and no PLAN/SUMMARY references one — this phase's verification mechanism is `helm lint`/`helm template`, executed directly above. Step 7c: SKIPPED (no probe scripts declared or discovered).

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| OPS-01 | 18-01 through 18-09 | TextReaderRpi deployable to Kubernetes via Helm chart with hardwareAccess toggle, -Xmx220m, health probes at /health + /health/ready | ✓ SATISFIED | Both runtime blockers from the initial verification (privileged at wrong API level, SA name mismatch) are closed and independently re-rendered. Chart lints clean, all resource kinds render, hardwareAccess toggle and resource controls (requests/limits) confirmed. No orphaned requirement IDs — REQUIREMENTS.md maps exactly OPS-01 to Phase 18, and all 9 plans declare `requirements: [OPS-01]`. |

No orphaned requirements found — REQUIREMENTS.md's Phase 18 mapping (OPS-01, marked Complete) matches the union of `requirements:` fields across all 18-0X plans exactly.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `values.yaml` | 60 | `database.password: "password"` — hardcoded default credential in VCS | ⚠️ WARNING (carryover, unresolved) | Not a deployment blocker; flagged in both prior and fresh code review (WR-02). Should be fixed via `required` template function in a follow-up plan. |
| `templates/serviceaccount.yaml` | 1 | Gated on `.Values.rbac.create`, while `_helpers.tpl`'s `serviceAccountName` helper branches on `.Values.serviceAccount.create` (18-REVIEW.md CR-01) | ⚠️ WARNING (new, not a phase must-have) | Verified by rendering: `--set rbac.create=false` leaves the Deployment referencing a ServiceAccount that is never created (pod admission would fail); `--set serviceAccount.create=false` renders a ServiceAccount named `default`, risking ownership conflict with the namespace's built-in account. Both are non-default permutations — the *default* install path (which OPS-01's acceptance criteria and every phase must-have exercise) is unaffected. The CI workflow does exercise `rbac.create=false` and "passes" (i.e., templates without error) while producing an undeployable manifest — this is a real correctness gap but was never declared as a must-have truth in any of the 9 plans, and does not block the phase goal as stated. Recommend a follow-up gap-closure plan to gate `serviceaccount.yaml` on `serviceAccount.create` instead. |
| `templates/deployment.yaml` | 7-8 | No `strategy: Recreate`; defaults to `RollingUpdate` (18-REVIEW.md CR-02, new) | ⚠️ WARNING (new, not a phase must-have) | Confirmed: no `strategy` key present. On `helm upgrade`, the surge pod and the outgoing pod would both mount the same RWO `data` PVC and contend for the H2 file lock (`AUTO_SERVER=FALSE` in configmap.yaml:49), stalling the rollout. This affects *upgrade* behavior, not the initial `helm install` the phase goal and OPS-01 text describe ("TextReaderRpi is deployable to Kubernetes via Helm chart" — install, not upgrade). No plan's must_haves mention upgrade semantics. Real defect, recommend follow-up plan to add `strategy: { type: Recreate }`, but does not block this phase's goal. |
| `values.yaml` | 15 | `replicas: 1` dead config, never read by deployment.yaml | ℹ️ INFO (carryover) | Misleads users; `--set replicas=2` silently does nothing. Documented design decision per phase context (replicas hardcoded intentionally). |
| `.github/workflows/helm-lint.yml` | 18,21,23 | `actions/checkout@v3`, `azure/setup-helm@v3`, `version: 'latest'` | ⚠️ WARNING (carryover) | Non-deterministic Helm version in CI; outdated action versions. |
| `Chart.yaml` | 7-9 | Placeholder URLs `https://github.com/yourusername/textreaderrpi` | ℹ️ INFO (carryover) | Cosmetic; appears in `helm show chart` output. |

No 🛑 BLOCKER anti-patterns remain — both blockers from the initial verification (CR-02 old / CR-03 old) are closed. The two new critical findings in 18-REVIEW.md (CR-01, CR-02 new) are judged to be real defects but **out of scope for this phase's established must-haves**: they surface only on non-default toggle combinations (CR-01) or on `helm upgrade` rather than `helm install` (CR-02 new), neither of which any of the 9 plans' must_haves or OPS-01's acceptance text require. They are downgraded to WARNING here and recommended as follow-up work, not treated as phase-blocking gaps.

### Human Verification Required

#### 1. Live Pod Deployment and Health Probe Verification

**Test:** Run `helm install textreaderrpi .devops/helm/textreaderrpi/ --set hardwareAccess.enabled=false --namespace default` against a real Kubernetes cluster (k3d, k3s, kind, or a bare Pi cluster). Wait for pod readiness, then verify `/health` and `/health/ready` return HTTP 200. Additionally, with a device-capable node, run with `--set hardwareAccess.enabled=true` and confirm the container actually gets privileged access to `/dev/spidev0.0` and `/dev/i2c-1` (e.g., `kubectl exec` into the pod and read from the device).

**Expected:** Pod reaches Running state; `kubectl port-forward` + `curl http://127.0.0.1:8080/health` returns 200 OK; `curl http://127.0.0.1:8080/health/ready` returns 200 OK; with hardwareAccess enabled, the SPI/I2C devices are accessible from inside the container.

**Why human:** No K8s cluster is available in this dev environment. `helm template` rendering proves the manifests are now structurally correct (both prior blockers fixed, re-confirmed above), but only a real cluster can prove the pod actually starts, PVCs bind, the JVM initializes the H2 database, and privileged device access is functional at runtime.

---

## Gaps Summary

No gaps remain against the established must-haves. Both previously identified blockers are closed and independently re-verified against the rendered chart output (not the SUMMARY's narrative):

1. `privileged: true` now renders at `containers[0].securityContext` (confirmed via `helm template --set hardwareAccess.enabled=true`, single match at container scope, no pod-level `securityContext` block present).
2. ServiceAccount `metadata.name`, Deployment `serviceAccountName`, and RoleBinding `subjects[0].name` all render as the identical value `textreaderrpi` in the default template.

Status is `human_needed` rather than `passed` because one human-verification item remains from the initial verification pass (real-cluster deployment and health-probe check) — this was never closeable by template rendering alone and no K8s cluster is available in this dev environment.

Two new findings surfaced by the fresh code review (18-REVIEW.md CR-01: rbac.create/serviceAccount.create toggle mismatch; CR-02 new: missing `strategy: Recreate`) were evaluated against this phase's established must-haves and OPS-01's acceptance text. Both are real, confirmed-by-rendering defects, but neither blocks the phase goal as scoped: CR-01 only breaks non-default toggle permutations (default install is unaffected), and CR-02 new only affects `helm upgrade` behavior (not the initial `helm install` deployability that OPS-01 and every plan's must_haves describe). They are recorded as WARNING-level anti-patterns above with a recommendation for a follow-up gap-closure plan, not as blocking gaps for this verification.

---

_Verified: 2026-07-06T00:20:00Z_
_Verifier: Claude (gsd-verifier)_
