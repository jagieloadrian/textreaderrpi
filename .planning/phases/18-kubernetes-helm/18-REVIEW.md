---
phase: 18-kubernetes-helm
reviewed: 2026-07-06T00:00:00Z
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
  warning: 5
  info: 6
  total: 13
status: issues_found
---

# Phase 18: Code Review Report (Re-Review After Gap Closure)

**Reviewed:** 2026-07-06
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Re-review after gap closure. Verified status of prior findings:

**Fixed (verified in current code):**
- **JAVA_TOOL_OPTIONS wiring** — `templates/configmap.yaml:9` now renders `JAVA_TOOL_OPTIONS: {{ .Values.JAVA_TOOL_OPTIONS | quote }}` from `values.yaml:12`. Fixed.
- **Privileged securityContext at pod level** — `templates/deployment.yaml:25-28` now places `securityContext.privileged: true` at the container level, gated on `hardwareAccess.enabled`. Fixed.
- **ServiceAccount name mismatch** — `deployment.yaml:19`, `serviceaccount.yaml:5`, and `rolebinding.yaml:14` all use the `textreaderrpi.serviceAccountName` helper. Fixed.

**Still open from prior review (carried over below):** default database password in values.yaml (WR-02), dead `persistence.*.enabled` flags (WR-04), unpinned CI actions/Helm version (WR-05), dead `replicas` value (IN-01), placeholder repo URLs (IN-02), unguarded `podAnnotations` rendering (IN-03), `image.tag: latest` default (IN-06).

**New findings this pass:** Two blockers, both confirmed by rendering with `helm template`:
1. The ServiceAccount template is gated on `rbac.create` while the naming helper branches on `serviceAccount.create` — both non-default toggle permutations produce a broken install, including the `rbac.create=false` permutation that the CI workflow itself templates.
2. The Deployment uses the default `RollingUpdate` strategy, which deterministically wedges every `helm upgrade`: the surge pod cannot acquire the H2 file lock on the shared RWO data PVC.

Per phase context, `replicas: 1` hardcoding and privileged hardware access via `hardwareAccess.enabled` are deliberate design decisions and are not reported as defects.

---

## Critical Issues

### CR-01: ServiceAccount template gated on `rbac.create`, but naming helper branches on `serviceAccount.create`

**File:** `.devops/helm/textreaderrpi/templates/serviceaccount.yaml:1` (interacts with `templates/_helpers.tpl:54-60`, `templates/deployment.yaml:19`)
**Issue:** The template condition (`{{- if .Values.rbac.create }}`) and the `textreaderrpi.serviceAccountName` helper (`if .Values.serviceAccount.create`) use two different toggles. Both non-default permutations are broken — verified by rendering:

- `--set rbac.create=false` (default `serviceAccount.create=true`): no ServiceAccount is rendered, but the Deployment still sets `serviceAccountName: <fullname>` (rendered output: `serviceAccountName: t-textreaderrpi`). The ReplicaSet fails with `error looking up service account ... not found` and **zero pods are ever created**. The CI workflow (`helm-lint.yml:37-39`) templates exactly this permutation — it passes templating but is undeployable.
- `--set serviceAccount.create=false` (default `rbac.create=true`): the template still renders a ServiceAccount **named `default`** (helper falls through to `default "default"`), so Helm attempts to create and take ownership of the namespace's built-in `default` ServiceAccount; `helm install` fails with a resource-ownership conflict.

**Fix:** Gate the ServiceAccount template on the same value the helper uses:
```yaml
{{- if .Values.serviceAccount.create }}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "textreaderrpi.serviceAccountName" . }}
  labels:
    {{- include "textreaderrpi.labels" . | nindent 4 }}
{{- end }}
```
Keep `rbac.create` gating only Role and RoleBinding. Add a CI template step for `--set serviceAccount.create=false` after fixing.

### CR-02: Default RollingUpdate strategy deadlocks every `helm upgrade` against the H2 file lock

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:7-8` (interacts with `templates/configmap.yaml:49`)
**Issue:** No `strategy` is set, so the Deployment defaults to `RollingUpdate` (for 1 replica: maxSurge → 1, maxUnavailable → 0). On upgrade, Kubernetes starts the new pod **while the old pod is still running**. On the single-node target, both pods mount the same ReadWriteOnce `data` PVC (RWO is node-scoped, so co-located pods both attach), and the new pod's H2 instance (`jdbc:h2:file:/data/schedules;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE`, `configmap.yaml:49`) cannot acquire the database file lock while the old pod holds it. The new pod never passes readiness, the rollout stalls until `progressDeadlineSeconds` expires, and every upgrade requires manual pod deletion. The README's own "Database locked" troubleshooting section (`README.md:242-250`) documents this exact symptom — and recommends force-deleting the data PVC, i.e. destroying all history data, as the workaround for a defect the chart itself causes.
**Fix:**
```yaml
spec:
  replicas: 1
  strategy:
    type: Recreate
```
`Recreate` terminates the old pod before starting the new one, releasing the H2 lock. Also update `README.md` troubleshooting to stop recommending PVC deletion as the first resort.

---

## Warnings

### WR-01: ConfigMap and Secret names hardcoded, bypassing the fullname helper

**File:** `.devops/helm/textreaderrpi/templates/configmap.yaml:4`, `.devops/helm/textreaderrpi/templates/secret.yaml:4` (referenced from `deployment.yaml:36,46,51`)
**Issue:** Every other resource is named via `textreaderrpi.fullname` (honoring `nameOverride`/`fullnameOverride` and release-name prefixing), but the ConfigMap is hardcoded to `textreaderrpi-config` and the Secret to `textreaderrpi-secret`. A second release in the same namespace fails at install with a Helm ownership conflict on these two resources, and `fullnameOverride` silently does not apply to them.
**Fix:** Use the helper in both templates and in the Deployment's `envFrom`/`secretKeyRef` references:
```yaml
name: {{ include "textreaderrpi.fullname" . }}-config
```
```yaml
name: {{ include "textreaderrpi.fullname" . }}-secret
```

### WR-02: Default database credentials (`sa`/`password`) shipped in values.yaml — carryover, still open

**File:** `.devops/helm/textreaderrpi/values.yaml:58-60`, rendered into `templates/secret.yaml:9-10`
**Issue:** Flagged as a blocker in the previous review and not addressed. The chart ships a working default password (`password`) rendered into a Secret on every install; anyone deploying without an override gets known credentials. Exposure is bounded (embedded H2 file DB, `AUTO_SERVER=FALSE`, ClusterIP-only service), but a published default password in VCS remains a credential-hygiene defect with no warning to the operator.
**Fix:** Fail fast when unset:
```yaml
# templates/secret.yaml
DATABASE_PASSWORD: {{ required "database.password must be set (--set database.password=...)" .Values.database.password | quote }}
```
with `password: ""` in values.yaml. At minimum, document that the default must be overridden.

### WR-03: ConfigMap/Secret changes do not restart the pod on upgrade

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:13-15`
**Issue:** All configuration is consumed as environment variables (`envFrom` / `secretKeyRef`), which are only read at container start. A `helm upgrade` that changes `JAVA_TOOL_OPTIONS`, `database.password`, or any ConfigMap key updates the ConfigMap/Secret but leaves the running pod on stale values — silently, with no rollout. The operator believes the new configuration is live when it is not.
**Fix:** Add checksum annotations to the pod template so config changes trigger a rollout:
```yaml
annotations:
  checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
  checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") . | sha256sum }}
  {{- with .Values.podAnnotations }}
  {{- toYaml . | nindent 8 }}
  {{- end }}
```

### WR-04: `persistence.data.enabled` / `persistence.logs.enabled` flags are dead — carryover, still open

**File:** `.devops/helm/textreaderrpi/values.yaml:20,24` vs `templates/pvc-data.yaml`, `templates/pvc-logs.yaml`, `templates/deployment.yaml:92-98`
**Issue:** Flagged in the previous review and not addressed. Both `enabled` flags exist in values.yaml, but neither PVC template nor the Deployment's volumes/volumeMounts check them. Setting `--set persistence.logs.enabled=false` silently does nothing — the knob violates user intent.
**Fix:** Either honor the flags (wrap the PVC templates and the corresponding volume/volumeMount pairs in `{{- if .Values.persistence.<x>.enabled }}` with an `emptyDir` fallback), or delete the `enabled` keys from values.yaml since persistence is mandatory for this app. Deleting is the simpler correct option.

### WR-05: CI uses unpinned actions, non-deterministic Helm version, and no permissions block — carryover, still open

**File:** `.github/workflows/helm-lint.yml:18-23`
**Issue:** Flagged in the previous review and not addressed. `actions/checkout@v3` and `azure/setup-helm@v3` are superseded (v3 runs on a deprecated Node runtime), and `version: 'latest'` makes lint results non-reproducible — a future Helm release can silently flip the gate from pass to fail with no code change. Additionally, the workflow declares no `permissions:` block, so the GITHUB_TOKEN receives the repository default instead of least privilege.
**Fix:**
```yaml
permissions:
  contents: read
...
      - uses: actions/checkout@v4
      - name: Set up Helm
        uses: azure/setup-helm@v4
        with:
          version: 'v3.16.3'
```

---

## Info

### IN-01: `replicas` value in values.yaml is never referenced — carryover, still open

**File:** `.devops/helm/textreaderrpi/values.yaml:14-15`
**Issue:** `replicas: 1` exists in values.yaml but `deployment.yaml:8` hardcodes `replicas: 1`. Hardcoding is the documented design decision; the values entry is dead config implying `--set replicas=3` would work (it silently does nothing).
**Fix:** Delete the `replicas` key from values.yaml; keep the rationale as a comment in the Deployment template or README.

### IN-02: Placeholder repository URLs in Chart.yaml and README — carryover, still open

**File:** `.devops/helm/textreaderrpi/Chart.yaml:7-9`, `.devops/helm/textreaderrpi/README.md:262`
**Issue:** `home` and `sources` point to `https://github.com/yourusername/textreaderrpi` — a template placeholder, not the real repository. Appears in `helm show chart` output.
**Fix:** Replace with the actual repository URL or remove the fields.

### IN-03: `podAnnotations` rendered without a `with` guard — carryover, downgraded from prior WR-03

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:14-15`
**Issue:** `{{- toYaml .Values.podAnnotations | nindent 8 }}` renders `annotations: {}` when the default empty map is used. This is valid YAML and accepted by the Kubernetes API (downgraded from the prior review's Warning — the claimed admission-controller impact does not hold), but it is non-idiomatic template hygiene and combines naturally with the WR-03 checksum fix.
**Fix:** Use the standard `{{- with .Values.podAnnotations }}` guard (see WR-03 fix snippet, which subsumes this).

### IN-04: Ingress template duplicates the entire paths/backend block

**File:** `.devops/helm/textreaderrpi/templates/ingress.yaml:17-38`
**Issue:** The `if .Values.ingress.host` / `else` branches contain identical 9-line `http.paths` blocks differing only by the `host:` line. Future edits must be made twice; drift risk.
**Fix:** Emit the host line conditionally inside a single rule:
```yaml
rules:
  - {{- if .Values.ingress.host }}
    host: {{ .Values.ingress.host | quote }}
    {{- end }}
    http:
      paths:
        - path: {{ .Values.ingress.path | default "/" }}
          ...
```

### IN-05: `service.targetPort` is a configurable trap — container port fixed at 8080 in three places

**File:** `.devops/helm/textreaderrpi/values.yaml:32`, `templates/service.yaml:11`, `templates/deployment.yaml:31`, `templates/configmap.yaml:12`
**Issue:** `service.targetPort` is exposed as a value, but `containerPort: 8080` and `PORT: "8080"` are hardcoded. Setting `targetPort` to anything else breaks traffic routing with no error.
**Fix:** Use the named port so it can never diverge — `targetPort: http` in service.yaml — and drop `service.targetPort` from values.yaml.

### IN-06: `image.tag: latest` default is non-deterministic — carryover, still open

**File:** `.devops/helm/textreaderrpi/values.yaml:4`
**Issue:** Flagged in the previous review and not addressed. Two installs from the same chart version can deploy different images. Risk is reduced by `pullPolicy: Never` (image must be pre-loaded), but the default remains imprecise.
**Fix:** Default the tag to match `Chart.yaml` `appVersion`, e.g. `tag: "1.2"`, or use `{{ .Values.image.tag | default .Chart.AppVersion }}` in the Deployment.

---

_Reviewed: 2026-07-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
