---
phase: 18-kubernetes-helm
verified: 2026-07-06T01:00:00Z
status: gaps_found
score: 22/23 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: true
re_verification_data:
  previous_status: human_needed
  previous_score: 23/23
  gaps_closed: []
  gaps_remaining: []
  regressions:
    - "Success Criterion 1's literal command (`helm install textreaderrpi .devops/helm/textreaderrpi/`, no --set flags) now fails deterministically because WR-02 (commit 49b3536) made `database.password` a `required` value with no default. Confirmed via `helm install --dry-run` and `helm template` with zero overrides: both exit 1 with 'database.password must be set'. This is a NEW regression introduced by the WR-02 code-review fix, not present in the prior verification (which ran before WR-02)."
gaps:
  - truth: "Running `helm install textreaderrpi .devops/helm/textreaderrpi/` on a K8s cluster starts a pod that passes both /health and /health/ready liveness/readiness probes (ROADMAP Success Criterion 1, literal command)"
    status: failed
    reason: "The exact command from the roadmap's success criterion, with no --set overrides, fails at the client-side template-render stage before a pod is ever scheduled. WR-02 (fix(18): WR-02 require database.password, drop shipped default credential, commit 49b3536) replaced the shipped default `database.password: \"password\"` with an empty string wrapped in Helm's `required` function. This is a correct security fix in isolation (no more hardcoded default credential in VCS) but it silently broke the literal SC1 install command, which the roadmap and README's own 'default install' example do not show as requiring `--set database.password=...`."
    artifacts:
      - path: ".devops/helm/textreaderrpi/templates/secret.yaml"
        issue: "Line 10: `{{ required \"database.password must be set (--set database.password=...)\" .Values.database.password | quote }}` — hard-fails template rendering when `database.password` is unset."
      - path: ".devops/helm/textreaderrpi/values.yaml"
        issue: "Line 58: `password: \"\"` — no default value is shipped, so the `required` guard always fires unless the operator supplies `--set database.password=...` or a values override."
    missing:
      - "Either: (a) restore a zero-config default install path by generating a random password at install time (e.g. `{{ .Values.database.password | default (randAlphaNum 16) }}`, optionally paired with a `lookup`-based upgrade-stable pattern), so the bare `helm install textreaderrpi .devops/helm/textreaderrpi/` command in SC1 succeeds without extra flags, or (b) update ROADMAP.md's Phase 18 Success Criterion 1 (and README's 'Default Installation' example, which already documents the required flag) to include the mandatory `--set database.password=...` flag, making the documented contract match the enforced one, then request an explicit override acceptance for this deviation."
---

# Phase 18: Kubernetes Helm Verification Report

**Phase Goal:** TextReaderRpi can be deployed to a Kubernetes cluster using a Helm chart with hardware-access and resource controls.
**Verified:** 2026-07-06T01:00:00Z
**Status:** gaps_found
**Re-verification:** Yes — after 7 code-review fix commits (CR-01, CR-02, WR-01..WR-05) applied on top of the prior `human_needed` (23/23) verification at `ebf5b7e`.

---

## Re-Verification Summary

The prior VERIFICATION.md (committed at `ebf5b7e`, status `human_needed`, score 23/23) was verified **before** 7 subsequent code-review fix commits touched `templates/serviceaccount.yaml`, `templates/deployment.yaml`, `templates/configmap.yaml`, `templates/secret.yaml`, `templates/rolebinding.yaml`, `values.yaml`, and `.github/workflows/helm-lint.yml`. This re-verification re-renders the chart at current HEAD (`c749b4c`) with `helm v4.2.2` and checks both (a) that the two previously-closed blockers still hold, and (b) whether any of the 7 new fixes introduced a regression.

**Previously-closed gaps — re-confirmed still closed:**

1. **Container-level `privileged: true`, gated on `hardwareAccess.enabled`:** `helm template ... --set hardwareAccess.enabled=true` renders exactly one `securityContext:` block at `deployment.yaml:233-234`, nested inside `containers[0]` immediately after `imagePullPolicy` — not at pod level. `hardwareAccess.enabled=false` (default) renders zero occurrences of `privileged`. **Holds.**
2. **ServiceAccount name consistency across `serviceAccount.create` / `rbac.create` permutations:** Re-tested all four requested permutations directly against rendered output (not narrative):
   - Default (`serviceAccount.create=true`, `rbac.create=true`): ServiceAccount `metadata.name`, Deployment `serviceAccountName`, and RoleBinding `subjects[0].name` all render `textreaderrpi`. Matches.
   - `serviceAccount.create=false`: No `ServiceAccount` kind rendered (fixed — CR-01 previously gated this on `rbac.create`, now correctly on `serviceAccount.create`); Deployment `serviceAccountName: default`; no RoleBinding rendered issue since it also resolves to `default` via the same helper. Kubernetes' built-in `default` ServiceAccount always exists, so this is deployable.
   - `rbac.create=false`: ServiceAccount **is** still rendered (gate is now independent of `rbac.create`); Deployment references `serviceAccountName: textreaderrpi`, matching the still-created SA. Role/RoleBinding correctly absent. **CR-01 fix holds across permutations.**

**New regression found (not present in the prior human_needed verification):**

3. **WR-02 (`require database.password, drop shipped default credential`, commit 49b3536)** made `database.password` mandatory via Helm's `required` function with no fallback default. This is a legitimate security improvement (no more hardcoded `password` string shipped in VCS) but it breaks Success Criterion 1's **literal** command: `helm install textreaderrpi .devops/helm/textreaderrpi/` — with no `--set` flags — now fails at template-render time, before any pod is scheduled:
   ```
   $ helm install textreaderrpi .devops/helm/textreaderrpi/ --dry-run
   Error: INSTALLATION FAILED: execution error at (textreaderrpi/templates/secret.yaml:10:24): database.password must be set (--set database.password=...)
   ```
   `helm template textreaderrpi .devops/helm/textreaderrpi/` (zero overrides) reproduces the identical failure, exit code 1. This is 100% deterministic and does not require a real cluster to observe — it fails client-side. The README.md was updated in the same fix to document the required flag, but ROADMAP.md's Phase 18 Success Criterion 1 text was not, so the literal roadmap contract (as re-verified against, per this workflow's rules) is unmet by the current chart's default values.

   A secondary, related symptom: bare `helm lint .devops/helm/textreaderrpi/` (no `--set`) now prints two `level=WARN msg="missing required values"` lines to stderr (though it still reports `0 chart(s) failed`, exit 0) — a partial regression against Success Criterion 4 ("`helm lint` passes with no errors **or warnings**"). The CI workflow (`helm-lint.yml`) always passes `--set database.password=ci-lint-only`, so this warning is invisible in CI, but the roadmap's literal criterion text does not mention any required flag either.

**This looks like an intentional security trade-off that was made without updating its contract.** To accept this deviation instead of treating it as a gap, add to this file's frontmatter:

```yaml
overrides:
  - must_have: "Running `helm install textreaderrpi .devops/helm/textreaderrpi/` on a K8s cluster starts a pod that passes both /health and /health/ready liveness/readiness probes"
    reason: "WR-02 intentionally removed the shipped default database.password to avoid a hardcoded credential in VCS; operators are expected to supply --set database.password=... (documented in README.md). ROADMAP.md SC1 should be updated to reflect the required flag, or a random-password default (randAlphaNum) should be added if a zero-config install is required."
    accepted_by: "{name}"
    accepted_at: "{ISO timestamp}"
```

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | ------- | ---------- | -------------- |
| 1 | Chart.yaml exists with apiVersion, name, version, appVersion, metadata | ✓ VERIFIED | Unchanged; regression check only |
| 2 | values.yaml exposes `hardwareAccess.enabled` toggle, default false | ✓ VERIFIED | `values.yaml:9` `enabled: false` |
| 3 | **helm install (literal, no overrides) starts a pod passing /health + /health/ready probes (SC1)** | ✗ FAILED (new regression) | `helm install textreaderrpi .devops/helm/textreaderrpi/ --dry-run` → exit 1, `database.password must be set`. Template never renders; no pod is ever created. Probes themselves (`deployment.yaml:60-76`, paths `/health` and `/health/ready`, 20s/30s/5s) are correctly configured but unreachable because the release cannot install with default values. |
| 4 | When `hardwareAccess.enabled=false`: pod starts without GPIO/SPI mounts, falls back to OfflineDisplayDriver, no crash (SC2) | ✓ VERIFIED | `--set hardwareAccess.enabled=false --set database.password=x`: 0 occurrences of `privileged`, `DISPLAY_TYPE: "OFFLINE"` injected, no `spidev`/`i2c` volumes or mounts rendered |
| 5 | When `hardwareAccess.enabled=true`: container-level `privileged: true` + hostPath device mounts for `/dev/spidev0.0`, `/dev/i2c-1` (SC2 flip side) | ✓ VERIFIED | `deployment.yaml:233-234` `securityContext:\n  privileged: true` nested in `containers[0]`; hostPath volumes render for both devices |
| 6 | JVM heap capped at `-Xmx220m` in Deployment spec (SC3) | ✓ VERIFIED | `configmap.yaml:47` `JAVA_TOOL_OPTIONS: "-Xmx220m"`, wired into container via `envFrom.configMapRef` |
| 7 | `helm lint` passes with no errors or warnings on the chart directory (SC4, literal — no `--set`) | ⚠️ PARTIAL (new regression, non-blocking) | Bare `helm lint .devops/helm/textreaderrpi/` still reports `1 chart(s) linted, 0 chart(s) failed` (exit 0), but now also emits 2 `level=WARN msg="missing required values"` lines caused by WR-02's `required` guard on an unset `database.password`. `helm lint --set database.password=x` is fully clean (only the pre-existing `[INFO] Chart.yaml: icon is recommended`). Grouped with gap #3 above (same root cause); not separately listed as a blocking gap since it doesn't hard-fail, but flagged here for completeness. |
| 8 | Liveness probe: GET /health (20/30/5) | ✓ VERIFIED | `deployment.yaml:60-67` |
| 9 | Readiness probe: GET /health/ready (20/30/5) | ✓ VERIFIED | `deployment.yaml:69-76` |
| 10 | ConfigMap named via `fullname` helper (`{{ include "textreaderrpi.fullname" . }}-config`), not hardcoded (WR-01) | ✓ VERIFIED | `configmap.yaml:4`; Deployment `envFrom.configMapRef.name` uses the same helper (`deployment.yaml:42`) |
| 11 | Secret named via `fullname` helper (`-secret` suffix), DATABASE_USER/PASSWORD via `secretKeyRef` (WR-01) | ✓ VERIFIED | `secret.yaml:4`; Deployment `secretKeyRef.name` at lines 52/57 uses the same helper |
| 12 | PVC for /data: default 1Gi, release-scoped name, unconditional (WR-04 removed dead `enabled` flag) | ✓ VERIFIED | `pvc-data.yaml` renders unconditionally; `values.yaml` no longer has `persistence.data.enabled` |
| 13 | PVC for /app/logs: default 500Mi, release-scoped name, unconditional (WR-04) | ✓ VERIFIED | `pvc-logs.yaml` renders unconditionally; `values.yaml` no longer has `persistence.logs.enabled` |
| 14 | Service ClusterIP exposes port 8080 | ✓ VERIFIED | Unchanged |
| 15 | ServiceAccount created, gated on `serviceAccount.create` (not `rbac.create`) (CR-01 fix) | ✓ VERIFIED | `serviceaccount.yaml:1` now `{{- if .Values.serviceAccount.create }}` |
| 16 | Deployment references the correct ServiceAccount name across `serviceAccount.create`/`rbac.create` permutations | ✓ VERIFIED | Re-tested default, `serviceAccount.create=false`, `rbac.create=false` — SA name always consistent between creator and consumer (see Re-Verification Summary above) |
| 17 | Role with get/list on pods, gated by `rbac.create` | ✓ VERIFIED | Unchanged |
| 18 | RoleBinding binds Role to the same ServiceAccount name the Deployment uses, gated by `rbac.create` | ✓ VERIFIED | `rolebinding.yaml:14` uses `textreaderrpi.serviceAccountName` helper — same as Deployment |
| 19 | Ingress gated by `ingress.enabled` (default false) | ✓ VERIFIED | `--set ingress.enabled=true --set database.password=x` renders `kind: Ingress` |
| 20 | Deployment uses `strategy: Recreate` to avoid H2 file-lock deadlock on upgrade (CR-02 fix) | ✓ VERIFIED | `deployment.yaml:9-10` `strategy:\n  type: Recreate` |
| 21 | ConfigMap/Secret content changes trigger pod restart via checksum annotations (WR-03 fix) | ✓ VERIFIED | `deployment.yaml:17-18` `checksum/config`/`checksum/secret` `sha256sum` of the rendered templates; empirically confirmed the secret checksum changes when `database.password` changes (`9ceff3...` vs `d73d21...`) |
| 22 | `database.password` required, no shipped default credential (WR-02) | ✓ VERIFIED (as a security fix) — see gap #3 for its side effect on SC1 | `values.yaml:58` `password: ""`; `secret.yaml:10` wraps it in `required` |
| 23 | CI workflow pins Helm version and Actions, adds least-privilege `permissions:` block, tests `serviceAccount.create=false` permutation (WR-05) | ✓ VERIFIED | `.github/workflows/helm-lint.yml`: `permissions: contents: read`, `actions/checkout@v4`, `azure/setup-helm@v4` pinned to `version: 'v3.16.3'`, dedicated `serviceAccount.create=false` template step present |
| 24 | README.md documents helm install/uninstall, hardwareAccess toggle, and the now-required `database.password` flag | ✓ VERIFIED | README.md:14 states the required flag prominently; all install examples include `--set database.password=<password>` |

**Score:** 22/23 truths verified (1 failed — SC1 literal command; the related SC4 warning-level regression is grouped under the same root cause and not double-counted as a separate truth)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `.devops/helm/textreaderrpi/templates/deployment.yaml` | Deployment spec, container-level privileged gating, Recreate strategy, checksum annotations | ✓ VERIFIED | All present and correctly wired |
| `.devops/helm/textreaderrpi/templates/serviceaccount.yaml` | Gated on `serviceAccount.create` | ✓ VERIFIED | Fixed per CR-01 |
| `.devops/helm/textreaderrpi/templates/rolebinding.yaml` | Subject name matches Deployment's SA | ✓ VERIFIED | Uses same helper |
| `.devops/helm/textreaderrpi/templates/secret.yaml` | DATABASE_PASSWORD required, no default | ⚠️ WIRED BUT BREAKS DEFAULT INSTALL | `required` guard fires on empty `values.yaml` default — see gap |
| `.devops/helm/textreaderrpi/templates/configmap.yaml` | Named via fullname helper | ✓ VERIFIED | Fixed per WR-01 |
| `.devops/helm/textreaderrpi/values.yaml` | hardwareAccess toggle, no dead persistence flags, no shipped DB credential | ✓ VERIFIED | WR-04 dead flags removed; WR-02 credential removed (side effect noted above) |
| `.github/workflows/helm-lint.yml` | Pinned actions/Helm version, least-privilege permissions, covers key permutations | ✓ VERIFIED | WR-05 applied |
| (All other artifacts) | — | ✓ VERIFIED | No regressions found in Chart.yaml, pvc-data.yaml, pvc-logs.yaml, service.yaml, role.yaml, ingress.yaml, _helpers.tpl, README.md |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| deployment.yaml | serviceaccount.yaml | `serviceAccountName: {{ include "textreaderrpi.serviceAccountName" . }}` | ✓ WIRED | Consistent across all tested permutations |
| rolebinding.yaml | serviceaccount.yaml | `subjects[0].name` via same helper | ✓ WIRED | Consistent |
| deployment.yaml (container) | hardwareAccess.enabled | `securityContext.privileged` at container scope | ✓ WIRED | Confirmed by rendering |
| deployment.yaml | configmap.yaml / secret.yaml | envFrom / secretKeyRef, checksum annotations | ✓ WIRED | Checksum reacts to real content changes (empirically tested) |
| **`helm install` (default values)** | **secret.yaml `required` guard** | template render | ✗ **NOT_WIRED — fails before pod creation** | Zero-override install cannot render `secret.yaml`, so no manifest ever reaches the API server |
| service.yaml | deployment.yaml | selector via `textreaderrpi.selectorLabels` | ✓ WIRED | Unchanged |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Bare `helm install` (SC1 literal) | `helm install textreaderrpi .devops/helm/textreaderrpi/ --dry-run` | `Error: INSTALLATION FAILED: execution error ... database.password must be set` | ✗ FAIL |
| Bare `helm template` (SC1 literal, alternate proof) | `helm template textreaderrpi .devops/helm/textreaderrpi/` | Exit 1, same error | ✗ FAIL |
| `helm lint` with password set | `helm lint .devops/helm/textreaderrpi/ --set database.password=x` | `1 chart(s) linted, 0 chart(s) failed`, only icon INFO | ✓ PASS |
| `helm lint` bare (SC4 literal) | `helm lint .devops/helm/textreaderrpi/` | `0 chart(s) failed` (exit 0) but 2 `level=WARN` lines emitted | ⚠️ PARTIAL |
| `hardwareAccess.enabled=true` privileged at container level | `helm template ... --set hardwareAccess.enabled=true --set database.password=x \| grep -n securityContext` | Single match, line 233-234, inside `containers[0]` | ✓ PASS |
| `hardwareAccess.enabled=false` no privileged | `helm template ... --set database.password=x \| grep -c privileged` | `0` | ✓ PASS |
| SA name consistency, default permutation | `helm template ... --set database.password=x \| grep -E "kind: ServiceAccount|serviceAccountName:|subjects:" -A1` | All render `textreaderrpi` | ✓ PASS |
| SA name consistency, `serviceAccount.create=false` | `helm template ... --set database.password=x --set serviceAccount.create=false` | No SA rendered; Deployment + RoleBinding both use `default` | ✓ PASS |
| SA name consistency, `rbac.create=false` | `helm template ... --set database.password=x --set rbac.create=false` | SA still rendered `textreaderrpi`; Deployment references it | ✓ PASS |
| Ingress renders when enabled | `helm template ... --set database.password=x --set ingress.enabled=true \| grep "kind: Ingress"` | Present | ✓ PASS |
| Checksum reacts to secret content | Rendered with `database.password=x` vs `=y` | Different sha256sum values | ✓ PASS |
| -Xmx220m present | `helm template ... --set database.password=x \| grep Xmx220m` | `JAVA_TOOL_OPTIONS: "-Xmx220m"` | ✓ PASS |
| No debt markers (TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) | `grep -rnE "TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER" .devops/helm/textreaderrpi/` | No matches | ✓ PASS |

### Probe Execution

No conventional `scripts/*/tests/probe-*.sh` probes exist for this phase, and none are declared in any PLAN/SUMMARY. Step 7c: SKIPPED (no probe scripts declared or discovered).

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| OPS-01 | 18-01 through 18-09 | TextReaderRpi deployable to Kubernetes via Helm chart with hardwareAccess toggle, -Xmx220m, health probes at /health + /health/ready | ⚠️ AT RISK | `hardwareAccess.enabled` toggle, `-Xmx220m`, and health probe wiring are all correctly implemented and re-verified after the code-review fixes. However, the literal SC1 install command this requirement traces to (per ROADMAP.md) fails by default due to WR-02's `required` password guard. The requirement's *capability* exists but its zero-config default-install path is currently broken; REQUIREMENTS.md marks OPS-01 "Complete" but that status pre-dates this regression. |

No orphaned requirements — REQUIREMENTS.md's Phase 18 mapping (OPS-01) matches the union of `requirements:` fields across all 9 plans exactly.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `templates/secret.yaml` | 10 | `required` guard on `database.password` with no default, empty string in `values.yaml` | 🛑 BLOCKER (new regression) | Breaks the literal SC1 install command; see gap above |
| `values.yaml` | 15 | `replicas: 1` dead config, never read by deployment.yaml (`spec.replicas: 1` is hardcoded literally in deployment.yaml:8, not `.Values.replicas`) | ℹ️ INFO (carryover) | Documented design decision (Pi hardware cannot be shared across pods); misleads users who try `--set replicas=2` |
| `Chart.yaml` | 7-9 | Placeholder URLs `https://github.com/yourusername/textreaderrpi` | ℹ️ INFO (carryover) | Cosmetic; appears in `helm show chart` and README references |

No other blockers found. All previously-flagged WARNING items from the prior verification (CR-01 toggle mismatch, CR-02 missing `strategy: Recreate`, WR-01 hardcoded ConfigMap/Secret names, WR-02 default credential, WR-03 no checksum, WR-04 dead persistence flags, WR-05 unpinned CI) have been fixed by the 7 review-fix commits and are re-confirmed above — except that the WR-02 fix itself introduced the new SC1 regression documented as the sole gap in this report.

### Human Verification Required

None newly identified in this pass beyond the pre-existing item tracked in `18-UAT.md` (real-cluster deploy + health probe check), which is unchanged by this re-verification and is not duplicated here per the task instructions. That UAT item remains `pending` and separately tracked.

---

## Gaps Summary

One regression blocks the phase goal as literally stated in ROADMAP.md: **Success Criterion 1's exact command (`helm install textreaderrpi .devops/helm/textreaderrpi/`, no flags) now fails deterministically** because WR-02 (commit 49b3536) made `database.password` a `required` value with no shipped default. This was a correct, deliberate security fix (removing a hardcoded default credential from VCS) but its side effect — breaking the bare-command install path the roadmap's SC1 describes — was not reconciled against the roadmap text or given an explicit override. A secondary, same-root-cause symptom is that bare `helm lint` (SC4, also written without flags in the roadmap) now emits `level=WARN` messages, though it does not hard-fail.

Everything else re-verified clean: both previously-closed blockers (container-level `privileged`, ServiceAccount name consistency) hold across all four requested permutations (default, `hardwareAccess.enabled=true`, `serviceAccount.create=false`, `rbac.create=false`, `ingress.enabled=true`), and all 5 other WR-fixes (WR-01 fullname-helper naming, WR-03 checksum-triggered rollout — empirically confirmed reactive to content changes, WR-04 dead flag removal, WR-05 CI pinning) are correctly implemented with no new defects.

This is exactly the kind of task-completion-without-goal-achievement gap this verification exists to catch: the WR-02 code-review fix task was completed and is itself good practice, but it silently broke the literal, previously-passing acceptance criterion for the phase. Recommend either (a) a follow-up plan to generate a random default password (`randAlphaNum`) so the bare install command succeeds unassisted, or (b) accept the deviation via the override block above and update ROADMAP.md's SC1 wording to match the now-required flag.

---

_Verified: 2026-07-06T01:00:00Z_
_Verifier: Claude (gsd-verifier)_
