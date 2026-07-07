---
phase: 18-kubernetes-helm
reviewed: 2026-07-07T08:58:48Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - .devops/helm/textreaderrpi/Chart.yaml
  - .devops/helm/textreaderrpi/README.md
  - .devops/helm/textreaderrpi/templates/configmap.yaml
  - .devops/helm/textreaderrpi/templates/deployment.yaml
  - .devops/helm/textreaderrpi/templates/_helpers.tpl
  - .devops/helm/textreaderrpi/templates/ingress.yaml
  - .devops/helm/textreaderrpi/templates/pvc-data.yaml
  - .devops/helm/textreaderrpi/templates/pvc-logs.yaml
  - .devops/helm/textreaderrpi/templates/rolebinding.yaml
  - .devops/helm/textreaderrpi/templates/role.yaml
  - .devops/helm/textreaderrpi/templates/secret.yaml
  - .devops/helm/textreaderrpi/templates/serviceaccount.yaml
  - .devops/helm/textreaderrpi/templates/service.yaml
  - .devops/helm/textreaderrpi/values.yaml
  - .github/workflows/helm-lint.yml
findings:
  critical: 2
  warning: 6
  info: 8
  total: 16
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-07-07T08:58:48Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Fresh full review of the Helm chart after gap-closure commits (f105de5, d8f62b3). The chart lints clean (`helm lint` passes, zero-config `helm template` renders successfully — verified locally), the WR-01..05/CR-01..02 fixes from the previous review pass are in place, and the ConfigMap env contract was cross-checked against `application.yaml` and `docker-compose.yml`.

However, tracing the chart's probe configuration into the application source reveals a critical defect the template-only verification could not catch: **the default (software-only) install cannot pass its own liveness/readiness probes and will CrashLoop indefinitely** (CR-01). A second critical issue: **`helm uninstall` destroys the H2 database PVC**, directly contradicting the README's data-safety claim (CR-02). Both are invisible to `helm lint`/`helm template` and would only surface on a real cluster — the 18-UAT real-cluster check is still pending, so nothing has caught them yet.

## Critical Issues

### CR-01: Default install CrashLoops — probes target endpoints that report 503 by design in OFFLINE mode

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:45-48, 60-76`
**Issue:** With default values (`hardwareAccess.enabled=false`), the chart injects `DISPLAY_TYPE=OFFLINE` (deployment.yaml:45-48) and points liveness at `GET /health` and readiness at `GET /health/ready`. Tracing into the application:

- `OFFLINE` is not a `DisplayType` enum value (`src/main/kotlin/com/anjo/model/DisplayType.kt:4`) → maps to `UNKNOWN` → `ZoneRegistry.initLocalZone` registers `OfflineDisplayDriver` (`ZoneRegistry.kt:69-72`)
- `OfflineDisplayDriver.status()` returns `hardwareAvailable = false` (`OfflineDisplayDriver.kt:12`)
- `ScreenDriverService.status()` returns `hardwareAvailable = anyOnline = false` (`ScreenDriverService.kt:207-216`)
- KHealth is configured with `check("displayAvailable") { ...hardwareAvailable }` on **`/health`** and `check("displayReady") { ...hardwareAvailable }` on **`/health/ready`**, returning `503 ServiceUnavailable` when any check fails (`src/main/kotlin/com/anjo/di/Monitoring.kt:26-42`)

Result on a real cluster with default values: readiness never passes (the Service never gets endpoints; the README's `kubectl wait --for=condition=ready --timeout=60s` and `port-forward` smoke test both fail), and the liveness probe kills the pod after 20s + 3x30s, forever. ROADMAP Success Criterion 1 ("default `helm install` starts a pod that passes both probes") is unachievable. The same trap fires in hardware mode whenever MAX7219 init fails (init failure also falls back to `OfflineDisplayDriver`, `ZoneRegistry.kt:74-77`), so a flaky SPI cable becomes a permanent restart loop instead of a running app in software fallback.

`helm lint`/`helm template` verification cannot detect this; the pending 18-UAT real-cluster item would have.

**Fix:** The liveness probe must never encode hardware availability — a JVM serving HTTP is alive. Chart-side minimal fix:
```yaml
        livenessProbe:
          tcpSocket:
            port: http
          initialDelaySeconds: 20
          periodSeconds: 30
```
Root-cause fix is app-side (outside this chart's diff but must be tracked): remove `displayAvailable` from KHealth `healthChecks` in `Monitoring.kt` — `OfflineDisplayDriver` is a designed fallback, not a dead app — and make `displayReady` pass when the offline fallback is the configured mode. Until the app change lands, readiness must also not point at `/health/ready` for the default install to satisfy SC1 (e.g., probe `/health/detail`, which always returns 200).

### CR-02: `helm uninstall` deletes both PVCs — H2 database destroyed; README documents the opposite

**File:** `.devops/helm/textreaderrpi/templates/pvc-data.yaml:1-17` (and `pvc-logs.yaml`); `README.md:122-125`
**Issue:** README.md:122 states "PersistentVolumeClaims are **not** automatically deleted" on uninstall and instructs users to delete them manually afterwards. This is false for this chart: PVCs defined as plain chart templates are release-owned resources and **are deleted** by `helm uninstall` (only StatefulSet `volumeClaimTemplates` survive, or resources annotated with a keep policy). A routine `helm uninstall` — including an uninstall/reinstall cycle to recover from a bad state — silently destroys the entire schedule-history database. This is a data-loss defect, not just a doc error: the documented mental model tells operators uninstall is safe when it is not.
**Fix:** Annotate the data PVC (and, if log retention matters, the logs PVC) so Helm leaves it behind, which also makes the README's uninstall section correct:
```yaml
metadata:
  name: {{ include "textreaderrpi.fullname" . }}-data
  annotations:
    "helm.sh/resource-policy": keep
```

## Warnings

### WR-01: README recommends `-Xmx512m` override while the 256Mi memory limit is hardcoded and non-configurable

**File:** `README.md:190-195`; `.devops/helm/textreaderrpi/templates/deployment.yaml:78-84`
**Issue:** README says "If you're running on a larger node with more memory, this can be overridden: `--set JAVA_TOOL_OPTIONS="-Xmx512m"`". But `deployment.yaml:78-84` hardcodes `limits.memory: 256Mi` with no values knob for resources. Node size is irrelevant — the cgroup limit stays 256Mi, so following this documented advice guarantees an OOMKill as soon as the heap grows past the limit. Doc-induced failure.
**Fix:** Expose resources in values (standard passthrough: `resources: {{- toYaml .Values.resources | nindent 10 }}` with the current values as defaults) and update the README example to raise both together, or delete the override advice entirely.

### WR-02: ~36Mi non-heap headroom between `-Xmx220m` and the 256Mi limit — OOMKill risk

**File:** `.devops/helm/textreaderrpi/values.yaml:12`; `.devops/helm/textreaderrpi/templates/deployment.yaml:84`
**Issue:** Heap capped at 220m inside a 256Mi container limit leaves ~36Mi for everything non-heap: Metaspace, code cache, thread stacks (Ktor/Netty spawn many), GC structures, and direct byte buffers (Netty allocates these heavily). A JVM of this shape typically needs 60-100Mi+ off-heap. The container is likely to be OOMKilled under load — and a limit kill is a SIGKILL, leaving the H2 file DB with exactly the stale-lock condition the README's own troubleshooting section describes.
**Fix:** Lower the heap (e.g., `-Xmx160m`, or better `-XX:MaxRAMPercentage=60`) or raise the limit to 320Mi. Validate with `/health/detail` memory figures under load on the Pi.

### WR-03: Pinning `database.password` after first install breaks the existing H2 database

**File:** `README.md:14, 24, 112`; `.devops/helm/textreaderrpi/values.yaml:56-58`
**Issue:** H2 file databases fix the credential at creation time; every later connection must present the same password. The README presents `--set database.password=<password>` as a general-purpose option ("set explicitly for production") without stating it only works at **first install**. An operator who installs with the auto-generated password and later "hardens" the release via `helm upgrade --set database.password=...` changes the env var but not the DB file's credential — the app can no longer open `/data/schedules` and the release breaks until the data PVC is wiped. The lookup-reuse logic (secret.yaml:4-10) protects the *unset* path but nothing protects against an explicit late pin.
**Fix:** Document the constraint in README and values.yaml (pin at initial install only, otherwise requires wiping the data PVC). Consider failing loudly: if `.Values.database.password` is set AND `lookup` finds an existing secret with a different value, `fail` with an explanatory message.

### WR-04: RBAC Role grants pod get/list to an app with no Kubernetes client — unused permission plus auto-mounted SA token

**File:** `.devops/helm/textreaderrpi/templates/role.yaml:8-11`; `templates/serviceaccount.yaml`; `templates/deployment.yaml:25`
**Issue:** The Role grants `pods: get, list` "for self-discovery" (decision D-16: "Pi node self-awareness"), but the application has no Kubernetes API client at all — no fabric8/official client in `build.gradle.kts` or `gradle/libs.versions.toml`, and no API usage anywhere in `src/main`. The permission is dead, and the ServiceAccount token is auto-mounted into a pod that (in hardware mode) runs **privileged** — handing a privileged container an API credential it never needs enlarges the blast radius of any container compromise for zero benefit.
**Fix:** Set `automountServiceAccountToken: false` on the pod spec (or ServiceAccount), and default `rbac.create: false` (or remove role.yaml/rolebinding.yaml entirely until code actually calls the API — YAGNI).

### WR-05: CI never exercises the zero-config password path that regressed before; stale comment

**File:** `.github/workflows/helm-lint.yml:28-51`
**Issue:** Every lint/template step passes `--set database.password=ci-lint-only`, so the `lookup`/`randAlphaNum` branch in secret.yaml — the exact zero-config default-install path whose earlier regression failed SC1 (see 18-VERIFICATION.md row 3) — has zero CI coverage. The comment at line 28, "database.password is required by the chart (no default)", has been false since commit f105de5 restored auto-generation. A future edit re-breaking the default path would sail through this workflow green.
**Fix:** Remove `--set database.password=...` from the `helm lint` step and at least one `helm template` step (both succeed without it — verified locally), and delete or correct the line 28 comment.

### WR-06: checksum/secret annotation is unstable — guaranteed spurious pod restart on first upgrade

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:18`; `templates/secret.yaml:1-10`
**Issue:** `checksum/secret` re-executes secret.yaml via `include`. On fresh install, `lookup` returns nil in that second execution too, so `randAlphaNum 16` runs **again** — the annotation hashes a different random password than the one stored in the Secret. On the first subsequent `helm upgrade`, `lookup` now returns the stored password, the hash changes, and the Deployment restarts (Recreate strategy = downtime) even when nothing actually changed. The annotation's entire purpose — restart only on real change — deterministically misfires once per release lifecycle, and any `helm template`-based diff tooling shows permanent secret churn.
**Fix:** Hash only the stable inputs instead of re-rendering the template:
```yaml
        checksum/secret: {{ printf "%s:%s" .Values.database.user .Values.database.password | sha256sum }}
```
(User-pinned password changes still trigger a rollout; the auto-generated path is stable because the stored Secret never changes.)

## Info

### IN-01: `values.yaml` `replicas: 1` is dead — deployment hardcodes the value

**File:** `.devops/helm/textreaderrpi/values.yaml:15`; `templates/deployment.yaml:8`
**Issue:** `deployment.yaml:8` hardcodes `replicas: 1` and never references `.Values.replicas`. Setting the value silently does nothing — a trap despite the comment.
**Fix:** Delete the key; the deployment comment and README already document non-scalability.

### IN-02: `API_QUEUE_SIZE` in ConfigMap is consumed nowhere

**File:** `.devops/helm/textreaderrpi/templates/configmap.yaml:29`
**Issue:** No reference in `application.yaml` or any Kotlin source. Dead configuration copied from docker-compose (which carries the same dead entry).
**Fix:** Remove the entry.

### IN-03: `DISPLAY_TYPE=OFFLINE` relies on the app's unknown-value fallback

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:46-47`
**Issue:** `OFFLINE` is not in the `DisplayType` enum; it works only because `fromString` maps it to `UNKNOWN`, which falls back to `OfflineDisplayDriver` with an "Unknown display type" WARN log at startup. Functionally correct today, fragile if the app ever validates config values. (Directly implicated in CR-01.)
**Fix:** Add `OFFLINE` to the `DisplayType` enum app-side, or document the sentinel contract.

### IN-04: Ingress template duplicates the entire paths block

**File:** `.devops/helm/textreaderrpi/templates/ingress.yaml:16-38`
**Issue:** The host and no-host branches are byte-identical except the `host:` line — 20 lines of duplication.
**Fix:** Single rule with a conditional host line:
```yaml
  rules:
    - {{- if .Values.ingress.host }}
      host: {{ .Values.ingress.host | quote }}
      {{- end }}
      http:
        ...
```

### IN-05: Placeholder URLs in Chart.yaml

**File:** `.devops/helm/textreaderrpi/Chart.yaml:7-9`
**Issue:** `home` and `sources` point at `https://github.com/yourusername/textreaderrpi` — template boilerplate never filled in (README.md:272 repeats it).
**Fix:** Set the real repository URL or drop the fields.

### IN-06: Secret `lookup` is incompatible with `helm template`-based GitOps

**File:** `.devops/helm/textreaderrpi/templates/secret.yaml:2-9`
**Issue:** Under ArgoCD/Flux (`helm template` rendering), `lookup` always returns nil, so a fresh random password is generated on every sync — constant Secret churn, pod restarts, and eventual H2 auth failures against the existing DB file. Fine for the documented Helm-CLI workflow, but the constraint is undocumented.
**Fix:** One line in README: GitOps deployments must pin `database.password` explicitly.

### IN-07: Upgrade render fails with a cryptic error if the existing Secret lacks the `DATABASE_PASSWORD` key

**File:** `.devops/helm/textreaderrpi/templates/secret.yaml:6`
**Issue:** If someone recreates or edits the Secret without that key, `index $existing.data "DATABASE_PASSWORD" | b64dec` pipes nil into `b64dec` and the entire upgrade aborts with a template error. Edge case (requires manual tampering).
**Fix:** `{{- $password = index $existing.data "DATABASE_PASSWORD" | default "" | b64dec }}` and fall through to regeneration when empty.

### IN-08: Workflow self-trigger asymmetry and tag-pinned actions

**File:** `.github/workflows/helm-lint.yml:8-12, 21-26`
**Issue:** (a) The `push` trigger's paths omit `.github/workflows/helm-lint.yml` while the PR trigger includes it, so direct pushes touching only the workflow don't validate it. (b) Commit f82ade1 claims to "pin CI actions", but `actions/checkout@v4` / `azure/setup-helm@v4` are mutable major tags, not SHAs — true supply-chain pinning uses commit SHAs.
**Fix:** Add the workflow path to the push trigger; pin actions by SHA if pinning is the intent.

---

_Reviewed: 2026-07-07T08:58:48Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
