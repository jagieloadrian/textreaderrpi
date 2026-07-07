---
phase: 18-kubernetes-helm
verified: 2026-07-07T12:00:00Z
status: passed
score: 23/23 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: true
re_verification_data:
  previous_status: gaps_found
  previous_score: 22/23
  gaps_closed:
    - "Running `helm install textreaderrpi .devops/helm/textreaderrpi/` on a K8s cluster starts a pod that passes both /health and /health/ready liveness/readiness probes (ROADMAP Success Criterion 1, literal command) — render no longer fails"
    - "helm lint passes with no errors or warnings on the bare chart directory (SC4) — no more level=WARN lines"
  gaps_remaining: []
  regressions: []
human_verification_accepted: # Accepted by user ("accept") on 2026-07-07 — items resolved without live-cluster run
  - test: "Deploy chart to a real Kubernetes cluster (`helm install textreaderrpi .devops/helm/textreaderrpi/`) and verify the pod starts and `/health` + `/health/ready` return 200 OK"
    expected: "Pod reaches Running/Ready state; both probe endpoints respond 200"
    why_human: "Requires a live cluster (k3s/k3d/kind); helm template/lint/dry-run cannot schedule or run a pod. Pre-existing item, tracked in 18-UAT.md test #1 (status: pending), not duplicated as a new gap here."
  - test: "Run `helm upgrade` against an existing release on a real cluster and confirm DATABASE_PASSWORD in the re-rendered Secret is identical to the original release's password (lookup-hit path), and that the H2-backed app stays reachable across the upgrade"
    expected: "The `lookup \"v1\" \"Secret\" .Release.Namespace $secretName` call finds the prior release's Secret and reuses its DATABASE_PASSWORD verbatim (via b64dec) rather than generating a new one; checksum/secret annotation does not force an unnecessary rollout from a changed password; app's H2 file DB remains accessible with the same credential"
    why_human: "`helm template`/`helm lint`/`--dry-run` have no cluster API access, so `lookup` always returns empty/falsy in those contexts — the randAlphaNum fallback path is the only branch exercisable statically. The lookup-hit branch (the actual upgrade-stability guarantee this gap-closure plan exists to deliver) can only be exercised against a real cluster with a pre-existing release. New item introduced by plan 18-10 (SUMMARY.md flags this explicitly as coverage item D6, human_judgment: true)."
---

# Phase 18: Kubernetes Helm Verification Report

**Phase Goal:** TextReaderRpi can be deployed to a Kubernetes cluster using a Helm chart with hardware-access and resource controls.
**Verified:** 2026-07-07T12:00:00Z
**Status:** passed (human verification items accepted by user on 2026-07-07)
**Re-verification:** Yes — after gap-closure plan 18-10 (commits `f105de5`, `d8f62b3`) executed on top of the prior `gaps_found` (22/23) verification.

---

## Re-Verification Summary

The prior VERIFICATION.md (status `gaps_found`, score 22/23) identified one blocking regression: WR-02's `required` guard on `database.password` (commit `49b3536`) made the literal ROADMAP SC1 command (`helm install textreaderrpi .devops/helm/textreaderrpi/`, no flags) fail deterministically at template-render time, with a secondary `level=WARN` symptom on bare `helm lint` (SC4).

Plan 18-10 replaced the `required` guard in `templates/secret.yaml` with a three-step default chain: explicit `.Values.database.password` -> `lookup` of the existing release Secret (upgrade-stable reuse) -> `randAlphaNum 16` fallback. This was independently re-rendered and spot-checked against the actual codebase at current HEAD (not taken from SUMMARY.md claims):

```
$ helm template textreaderrpi .devops/helm/textreaderrpi/
DATABASE_PASSWORD: "JSfSR7r9nf5b5nD0"        # exit 0 — no error

$ helm template textreaderrpi .devops/helm/textreaderrpi/ --set database.password=explicitpw12345
DATABASE_PASSWORD: "explicitpw12345"          # explicit override honored verbatim

$ helm lint .devops/helm/textreaderrpi/
==> Linting .devops/helm/textreaderrpi/
[INFO] Chart.yaml: icon is recommended
1 chart(s) linted, 0 chart(s) failed          # exit 0, no level=WARN lines

$ helm install textreaderrpi .devops/helm/textreaderrpi/ --dry-run
STATUS: pending-install
REVISION: 1
DESCRIPTION: Dry run complete                 # renders and would install cleanly
```

**Both previously-identified gaps are now closed:**

1. **SC1 literal command** — `helm template`/`helm install --dry-run` with zero overrides now exit 0 and render a non-empty, randomly-generated 16-char `DATABASE_PASSWORD` (`randAlphaNum 16`). Confirmed independently, not from SUMMARY.md.
2. **SC4 literal command** — bare `helm lint` no longer emits any `level=WARN` line; only the pre-existing `[INFO] Chart.yaml: icon is recommended` (cosmetic, not a warning-level finding).

**Security intent (WR-02) preserved:** `values.yaml` line 58 still ships `password: ""` — no credential is committed to VCS. The generated/looked-up value only ever exists inside the rendered Secret at render/install time.

**Upgrade-stability path (`lookup`) partially verifiable statically:** The nested-if guard structure (`{{- if not $password }}` / `{{- if $existing }}`) is correctly written to avoid Go-template `and` short-circuit version-dependency, and the empty-lookup fallback (`randAlphaNum 16`) is exercised and confirmed by every `helm template`/`lint`/`--dry-run` command above (none of these have cluster access, so `lookup` always returns empty in this environment — this is expected and correctly falls through). However, the actual **lookup-hit** branch — where a real `helm upgrade` finds and reuses a prior release's password — cannot be exercised without a live cluster with an existing release. This is a genuinely new human-verification item (not a regression, not a gap — the SUMMARY.md itself flags this as `D6, human_judgment: true`), and is added below alongside the pre-existing, still-pending real-cluster deploy check from `18-UAT.md`.

**No regressions found from the 18-10 changes:** `templates/deployment.yaml` is untouched (confirmed via `git log`); checksum annotations react correctly to secret-content changes (`checksum/secret` differs between `--set database.password=x1` and `=y2`); all previously-verified truths (container-level `privileged` gating, ServiceAccount/RoleBinding name consistency across all four permutations, JVM `-Xmx220m`, `strategy: Recreate`, Ingress toggle) were re-confirmed by direct re-render, not assumed from the prior report.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | ------- | ---------- | -------------- |
| 1 | Chart.yaml exists with apiVersion, name, version, appVersion, metadata | ✓ VERIFIED | Unchanged; regression check only |
| 2 | values.yaml exposes `hardwareAccess.enabled` toggle, default false | ✓ VERIFIED | `values.yaml:9` `enabled: false` |
| 3 | `helm install` (literal, no overrides) renders/installs cleanly; pod configured to pass /health + /health/ready probes (SC1) | ✓ VERIFIED (render/config); pod-running check is human item | `helm install --dry-run` exits 0, `STATUS: pending-install`. Probes correctly configured (`deployment.yaml:60-76`, paths `/health` and `/health/ready`, 20s/30s/5s). Actual live-pod probe-pass check requires a real cluster — tracked in `18-UAT.md` test #1 (pre-existing, unchanged by this re-verification) |
| 4 | When `hardwareAccess.enabled=false`: pod starts without GPIO/SPI mounts, falls back to OfflineDisplayDriver, no crash (SC2) | ✓ VERIFIED | `--set hardwareAccess.enabled=false`: 0 occurrences of `privileged: true`; no spidev/i2c mounts |
| 5 | When `hardwareAccess.enabled=true`: container-level `privileged: true` + hostPath device mounts (SC2 flip side) | ✓ VERIFIED | `--set hardwareAccess.enabled=true \| grep -c "privileged: true"` → `1`, nested in container spec |
| 6 | JVM heap capped at `-Xmx220m` in Deployment spec (SC3) | ✓ VERIFIED | `JAVA_TOOL_OPTIONS: "-Xmx220m"` present in rendered ConfigMap, wired via `envFrom` |
| 7 | `helm lint` passes with no errors or warnings on the chart directory (SC4, literal — no `--set`) | ✓ VERIFIED (gap closed) | Bare `helm lint .devops/helm/textreaderrpi/` → `0 chart(s) failed`, exit 0, zero `level=WARN` lines (previously 2 WARN lines — regression fixed) |
| 8 | Liveness probe: GET /health (20/30/5) | ✓ VERIFIED | `deployment.yaml:60-67` |
| 9 | Readiness probe: GET /health/ready (20/30/5) | ✓ VERIFIED | `deployment.yaml:69-76` |
| 10 | ConfigMap named via `fullname` helper, not hardcoded | ✓ VERIFIED | Unchanged regression check |
| 11 | Secret named via `fullname` helper, DATABASE_USER/PASSWORD via `secretKeyRef` | ✓ VERIFIED | `secret.yaml` `$secretName := printf "%s-secret" (include "textreaderrpi.fullname" .)`; Deployment `secretKeyRef.name` uses same helper |
| 12 | PVC for /data: default 1Gi, release-scoped name, unconditional | ✓ VERIFIED | Unchanged |
| 13 | PVC for /app/logs: default 500Mi, release-scoped name, unconditional | ✓ VERIFIED | Unchanged |
| 14 | Service ClusterIP exposes port 8080 | ✓ VERIFIED | Unchanged |
| 15 | ServiceAccount created, gated on `serviceAccount.create` | ✓ VERIFIED | `--set serviceAccount.create=false` → no ServiceAccount resource rendered, Deployment falls back to `serviceAccountName: default` |
| 16 | Deployment references correct ServiceAccount name across permutations | ✓ VERIFIED | Re-tested `serviceAccount.create=false` and `rbac.create=false` directly — both consistent |
| 17 | Role with get/list on pods, gated by `rbac.create` | ✓ VERIFIED | Unchanged |
| 18 | RoleBinding binds Role to the same ServiceAccount name the Deployment uses | ✓ VERIFIED | `--set rbac.create=false` → ServiceAccount still rendered `textreaderrpi`, Deployment references it |
| 19 | Ingress gated by `ingress.enabled` (default false) | ✓ VERIFIED | `--set ingress.enabled=true` renders `kind: Ingress` |
| 20 | Deployment uses `strategy: Recreate` | ✓ VERIFIED | `strategy:\n  type: Recreate` present |
| 21 | ConfigMap/Secret content changes trigger pod restart via checksum annotations | ✓ VERIFIED | `checksum/secret` differs between `--set database.password=x1` vs `=y2` renders |
| 22 | No default database credential shipped in VCS, but zero-config install still succeeds | ✓ VERIFIED (gap closed) | `values.yaml:58` `password: ""`; `secret.yaml` generates `randAlphaNum 16` at render when unset and no prior release exists — no VCS credential, no render failure |
| 23 | CI workflow pins Helm version/Actions, least-privilege `permissions:`, tests `serviceAccount.create=false` | ✓ VERIFIED | `.github/workflows/helm-lint.yml` unchanged by 18-10, previously confirmed |
| 24 | README.md documents helm install/uninstall, hardwareAccess toggle, and the (now-restored) zero-config default password behavior | ✓ VERIFIED | README preamble documents auto-generation + upgrade stability; "Default Installation" example is bare `helm install textreaderrpi .`; config table row reflects `_(auto-generated)_` |

**Score:** 23/23 truths verified (0 failed). No behavior-dependent truths were marked PRESENT_BEHAVIOR_UNVERIFIED as a distinct status — the two remaining live-cluster checks (real pod scheduling + probe response, and the `lookup`-hit upgrade path) were already present as human-verification needs before and after this fix, and are listed under Human Verification below rather than blocking the score.

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `.devops/helm/textreaderrpi/templates/secret.yaml` | Default chain: explicit -> lookup -> randAlphaNum, no hard `required` fail | ✓ VERIFIED | Rewritten per plan 18-10; renders non-empty password with zero overrides, honors explicit override verbatim |
| `.devops/helm/textreaderrpi/values.yaml` | `password: ""` retained (no VCS credential), comment describes auto-generation | ✓ VERIFIED | Line 58 comment now reads "Auto-generated (randAlphaNum 16) at install when unset..." |
| `.devops/helm/textreaderrpi/README.md` | Zero-config default install documented, explicit override retained as production option | ✓ VERIFIED | Preamble + "Default Installation" example + config table row all reconciled |
| `.devops/helm/textreaderrpi/templates/deployment.yaml` | Unchanged by this gap-closure (checksum/secret still wired) | ✓ VERIFIED | `git log` confirms no commit in 18-10 touched this file; checksum reacts to secret content changes |
| (All other artifacts) | — | ✓ VERIFIED | No regressions in Chart.yaml, configmap.yaml, serviceaccount.yaml, rolebinding.yaml, role.yaml, pvc-data.yaml, pvc-logs.yaml, service.yaml, ingress.yaml, _helpers.tpl, helm-lint.yml |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `helm install`/`helm template` (default values) | `secret.yaml` render | template render, zero overrides | ✓ WIRED (regression fixed) | Renders exit 0; no more hard-fail |
| `secret.yaml` `$secretName` | Deployment `secretKeyRef.name` / `checksum/secret` | `textreaderrpi.fullname`-derived name, computed once and reused | ✓ WIRED | Same helper-derived name across both files |
| `secret.yaml` `lookup` | existing release Secret (on real `helm upgrade`) | `lookup "v1" "Secret" .Release.Namespace $secretName` | ⚠️ WIRED, lookup-hit path unexercised statically | Correctly falls through to `randAlphaNum` when `lookup` returns empty (the only branch `helm template`/`lint`/`--dry-run` can exercise); the reuse branch requires a live cluster — see Human Verification |
| deployment.yaml | serviceaccount.yaml / rolebinding.yaml | `serviceAccountName` helper | ✓ WIRED | Consistent across all tested permutations |
| deployment.yaml (container) | hardwareAccess.enabled | `securityContext.privileged` at container scope | ✓ WIRED | Confirmed by direct re-render |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Bare `helm template` (SC1 literal) | `helm template textreaderrpi .devops/helm/textreaderrpi/` | Exit 0, `DATABASE_PASSWORD: "JSfSR7r9nf5b5nD0"` (16-char alphanumeric) | ✓ PASS |
| Bare `helm install --dry-run` (SC1 literal, alternate proof) | `helm install textreaderrpi .devops/helm/textreaderrpi/ --dry-run` | `STATUS: pending-install`, `DESCRIPTION: Dry run complete` | ✓ PASS |
| Explicit override wins | `helm template ... --set database.password=explicitpw12345` | `DATABASE_PASSWORD: "explicitpw12345"` | ✓ PASS |
| Bare `helm lint` (SC4 literal) | `helm lint .devops/helm/textreaderrpi/` | `0 chart(s) failed`, exit 0, no `level=WARN` | ✓ PASS |
| `hardwareAccess.enabled=true` privileged, container-level | `helm template ... --set hardwareAccess.enabled=true \| grep -c "privileged: true"` | `1` | ✓ PASS |
| `hardwareAccess.enabled=false` no privileged | `helm template ... \| grep -c "privileged: true"` | `0` | ✓ PASS |
| SA gating, `serviceAccount.create=false` | `helm template ... --set serviceAccount.create=false` | No ServiceAccount resource; `serviceAccountName: default`; RoleBinding subject also `default` | ✓ PASS |
| SA gating, `rbac.create=false` | `helm template ... --set rbac.create=false` | ServiceAccount still `textreaderrpi`; Role/RoleBinding absent | ✓ PASS |
| Ingress toggle | `helm template ... --set ingress.enabled=true \| grep "kind: Ingress"` | Present | ✓ PASS |
| Checksum reacts to secret content | `--set database.password=x1` vs `=y2` | Different `checksum/secret` sha256 values | ✓ PASS |
| -Xmx220m present | `helm template ... \| grep Xmx220m` | `JAVA_TOOL_OPTIONS: "-Xmx220m"` | ✓ PASS |
| Recreate strategy | `helm template ... \| grep -A1 strategy:` | `type: Recreate` | ✓ PASS |
| deployment.yaml untouched by 18-10 | `git log --oneline -- .../deployment.yaml` | Last touch predates 18-10 (WR-03 commit `95eec81`) | ✓ PASS |
| No debt markers | `grep -rnE "TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER" .devops/helm/textreaderrpi/` | No matches | ✓ PASS |

### Probe Execution

No conventional `scripts/*/tests/probe-*.sh` probes exist for this phase, and none are declared in any PLAN/SUMMARY. Step 7c: SKIPPED (no probe scripts declared or discovered).

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| OPS-01 | 18-01 through 18-10 | TextReaderRpi deployable to Kubernetes via Helm chart with hardwareAccess toggle, -Xmx220m, health probes at /health + /health/ready | ✓ SATISFIED | All literal ROADMAP success criteria (SC1-SC4) now independently confirmed via re-render/re-lint against current HEAD. `hardwareAccess` toggle, `-Xmx220m`, health probes, and zero-config install are all correctly implemented and wired. REQUIREMENTS.md's "Complete" status is now accurate. |

No orphaned requirements — REQUIREMENTS.md's Phase 18 mapping (OPS-01) matches the union of `requirements:` fields across all 10 plans (18-01 through 18-10) exactly.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `values.yaml` | 15 | `replicas: 1` dead config, never read by deployment.yaml (hardcoded literally) | ℹ️ INFO (carryover) | Documented design decision (Pi hardware cannot be shared across pods); misleads users who try `--set replicas=2` |
| `Chart.yaml` | 7-9 | Placeholder URLs `https://github.com/yourusername/textreaderrpi` | ℹ️ INFO (carryover) | Cosmetic; appears in `helm show chart` and README references |

No blockers found. The prior sole blocker (WR-02's `required` guard breaking SC1) is resolved with no new anti-patterns introduced by plan 18-10.

### Human Verification Required (ACCEPTED by user, 2026-07-07)

### 1. Real-cluster deploy and health-probe check (pre-existing, unchanged)

**Test:** Deploy the chart to a real Kubernetes cluster: `helm install textreaderrpi .devops/helm/textreaderrpi/` (e.g. k3s/k3d/kind, `--set hardwareAccess.enabled=false` off-Pi), then `kubectl port-forward` + `curl http://127.0.0.1:8080/health` and `/health/ready`.
**Expected:** Pod reaches Running/Ready; both endpoints return 200 OK.
**Why human:** `helm template`/`lint`/`--dry-run` cannot schedule or run a pod against a real kubelet/container runtime. Tracked in `18-UAT.md` test #1 (status: pending) — not duplicated as a new gap.

### 2. Upgrade-stability: password reuse via `lookup` on a real `helm upgrade`

**Test:** Install the chart once (recording the generated `DATABASE_PASSWORD`), then run `helm upgrade textreaderrpi .devops/helm/textreaderrpi/` on the same release without `--set database.password`, and check that the re-rendered Secret's `DATABASE_PASSWORD` is byte-for-byte identical to the original, and the app's H2-backed functionality still works post-upgrade.
**Expected:** `lookup` finds the existing release's Secret and reuses its password; no unexpected rollout is forced purely by password churn; the H2 file DB remains accessible with the same credential across the upgrade.
**Why human:** `lookup` requires live cluster API access (`.Release.Namespace` + Secret read) which `helm template`/`lint`/`--dry-run` do not provide — every static check in this verification exercises only the empty-lookup (`randAlphaNum`) fallback branch, never the reuse branch. This is a new item surfaced by plan 18-10 itself (SUMMARY.md coverage ID `D6`, `human_judgment: true`).

---

## Gaps Summary

No gaps remain. Both truths that failed in the prior `gaps_found` (22/23) verification are now independently confirmed fixed by direct re-render/re-lint against the current codebase (not from SUMMARY.md claims):

- **SC1** (`helm install textreaderrpi .devops/helm/textreaderrpi/`, literal, zero overrides) now renders and dry-run-installs successfully with an auto-generated, non-empty `DATABASE_PASSWORD`.
- **SC4** (`helm lint` bare) now passes with zero `level=WARN` lines.

Neither WR-02's security intent (no VCS-committed credential) nor the CR-02/WR-03 upgrade-stability requirement (stable H2 credential across releases) was regressed: `values.yaml` still ships `password: ""`, and the `lookup` chain is correctly structured to reuse an existing release's password when one exists.

**Update 2026-07-07:** The user accepted both human-verification items ("accept"), resolving the phase to `passed`. The original rationale for `human_needed` follows for the record: two items require a live Kubernetes cluster to exercise directly: (1) the pre-existing real-pod-deploy + health-probe check (already pending in `18-UAT.md`, unaffected by this fix), and (2) a new item — the `lookup`-hit branch of the upgrade-stability chain, which no static `helm` command can exercise (every static tool in this environment necessarily takes the empty-lookup fallback path). Both are legitimate human-verification items, not gaps: the code implementing both is present, correctly structured, and passes every check that doesn't require a live cluster.

---

_Verified: 2026-07-07T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
