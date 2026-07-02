---
phase: 18-kubernetes-helm
reviewed: 2026-07-02T00:00:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - .devops/helm/textreaderrpi/Chart.yaml
  - .devops/helm/textreaderrpi/values.yaml
  - .devops/helm/textreaderrpi/templates/deployment.yaml
  - .devops/helm/textreaderrpi/templates/_helpers.tpl
  - .devops/helm/textreaderrpi/templates/configmap.yaml
  - .devops/helm/textreaderrpi/templates/secret.yaml
  - .devops/helm/textreaderrpi/templates/pvc-data.yaml
  - .devops/helm/textreaderrpi/templates/pvc-logs.yaml
  - .devops/helm/textreaderrpi/templates/service.yaml
  - .devops/helm/textreaderrpi/templates/serviceaccount.yaml
  - .devops/helm/textreaderrpi/templates/role.yaml
  - .devops/helm/textreaderrpi/templates/rolebinding.yaml
  - .devops/helm/textreaderrpi/templates/ingress.yaml
  - .devops/helm/textreaderrpi/README.md
  - .github/workflows/helm-lint.yml
findings:
  critical: 3
  warning: 4
  info: 3
  total: 10
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-07-02T00:00:00Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Reviewed the complete Helm chart for TextReaderRpi. Three blockers were found: a hardcoded default database password committed in plain text, a `privileged: true` field placed at the wrong Kubernetes API level (pod vs. container securityContext) making hardware access non-functional, and a ServiceAccount name mismatch that causes the deployment to reference a ServiceAccount that does not exist. Four warnings cover dead configuration, persistence `enabled` flags that are never checked, unconditional pod-annotation rendering, and a non-pinned CI action. Three info items cover placeholder repo URLs, a dead `replicas` value in values.yaml, and a misleading PVC name in the uninstall docs.

---

## Critical Issues

### CR-01: Hardcoded Default Database Password in Plain Text

**File:** `.devops/helm/textreaderrpi/values.yaml:60`
**Issue:** `database.password: "password"` is committed as the default in `values.yaml`. This file is part of the repository and will be used as-is by anyone who runs `helm install textreaderrpi .` without an explicit override. The credential is also rendered verbatim into the Secret via `stringData`, which base64-encodes but does not encrypt it. A real password should never appear as a default in VCS.
**Fix:** Remove the plaintext default and force the user to supply it at install time, or document that it must always be overridden:

```yaml
# values.yaml
database:
  user: "sa"
  password: ""   # REQUIRED — set via --set database.password=... or -f secrets.yaml
```

Then guard the Secret template:
```yaml
# templates/secret.yaml
{{- if not .Values.database.password }}
{{ fail "database.password must be set — run helm install with --set database.password=<value>" }}
{{- end }}
```

---

### CR-02: `privileged: true` Placed in PodSecurityContext — Wrong API Level, Hardware Access Is Broken

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:21-23`
**Issue:** When `hardwareAccess.enabled=true`, the template renders:

```yaml
spec:
  securityContext:
    privileged: true
```

`privileged` is **not** a valid field of `PodSecurityContext` (`spec.securityContext`). It belongs to `SecurityContext` (`spec.containers[*].securityContext`). Kubernetes either silently drops unknown fields or rejects the pod spec at admission. Either way, the container does not actually get privileged access, so `/dev/spidev0.0` and `/dev/i2c-1` will be inaccessible and hardware mode silently fails.

**Fix:** Move the securityContext block to the container level:

```yaml
# deployment.yaml
containers:
- name: textreaderrpi
  {{- if .Values.hardwareAccess.enabled }}
  securityContext:
    privileged: true
  {{- end }}
  image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
```

Remove the pod-level `securityContext` block entirely when it only contained `privileged`.

---

### CR-03: ServiceAccount Name Mismatch — Deployment References a Non-Existent ServiceAccount

**File:** `.devops/helm/textreaderrpi/templates/serviceaccount.yaml:5` and `.devops/helm/textreaderrpi/templates/_helpers.tpl:54-59`
**Issue:** There is a name divergence between what is created and what the deployment references:

- `serviceaccount.yaml` creates: `{{ include "textreaderrpi.fullname" . }}-sa`  (e.g., `textreaderrpi-textreaderrpi-sa`)
- `_helpers.tpl` `serviceAccountName` helper returns: `{{ include "textreaderrpi.fullname" . }}`  (no `-sa` suffix)
- `deployment.yaml` uses the helper, so it references the name **without** `-sa`

The ServiceAccount referenced by the deployment does not exist. The Kubernetes ServiceAccount admission controller blocks pod creation when the named ServiceAccount is absent. The `rolebinding.yaml` correctly references `<fullname>-sa`, but the pod never receives those permissions because it cannot start.

**Fix:** Either remove the `-sa` suffix from `serviceaccount.yaml` so the name matches the helper:

```yaml
# templates/serviceaccount.yaml
metadata:
  name: {{ include "textreaderrpi.serviceAccountName" . }}
```

And update `rolebinding.yaml` to use the same helper:

```yaml
# templates/rolebinding.yaml
subjects:
  - kind: ServiceAccount
    name: {{ include "textreaderrpi.serviceAccountName" . }}
    namespace: {{ .Release.Namespace }}
```

---

## Warnings

### WR-01: `persistence.data.enabled` / `persistence.logs.enabled` Flags Are Never Checked in PVC Templates

**File:** `.devops/helm/textreaderrpi/templates/pvc-data.yaml:1` and `.devops/helm/textreaderrpi/templates/pvc-logs.yaml:1`
**Issue:** `values.yaml` exposes `persistence.data.enabled: true` and `persistence.logs.enabled: true`. A user who sets either to `false` expects the corresponding PVC not to be created. Both PVC templates are unconditional — they always render regardless of the flag. The deployment's volumeMounts are equally unconditional, so disabling persistence via the flag leaves a broken deployment (volumes defined without PVCs).
**Fix:** Wrap each PVC template in a conditional and add a matching guard in the deployment's volume/volumeMount sections:

```yaml
# templates/pvc-data.yaml
{{- if .Values.persistence.data.enabled }}
apiVersion: v1
kind: PersistentVolumeClaim
...
{{- end }}
```

In `deployment.yaml`, wrap the data volumeMount and volume similarly:
```yaml
{{- if .Values.persistence.data.enabled }}
- name: data
  mountPath: /data
{{- end }}
```

---

### WR-02: `replicas` Value in values.yaml Is Dead Configuration

**File:** `.devops/helm/textreaderrpi/values.yaml:15` and `.devops/helm/textreaderrpi/templates/deployment.yaml:8`
**Issue:** `values.yaml` declares `replicas: 1` with a comment "(hardcoded to 1 — not configurable)", yet `deployment.yaml` hardcodes `replicas: 1` and never references `{{ .Values.replicas }}`. The value in `values.yaml` is never consumed. This misleads users into thinking setting `--set replicas=2` would change behavior.
**Fix:** Either remove `replicas` from `values.yaml` entirely (since single-replica is intentional), or use it in the deployment:

```yaml
# templates/deployment.yaml
replicas: {{ .Values.replicas }}
```

Removing it is the correct YAGNI choice given the chart explicitly documents single-replica as a constraint.

---

### WR-03: `podAnnotations` Rendered Without `with` Guard — Produces Spurious Empty Block

**File:** `.devops/helm/textreaderrpi/templates/deployment.yaml:14-15`
**Issue:** `{{- toYaml .Values.podAnnotations | nindent 8 }}` is rendered unconditionally. When `podAnnotations: {}` (the default), this renders as `annotations:\n        {}\n` — a non-empty annotations object containing an empty mapping. Some admission controllers and tools that parse pod specs treat `annotations: {}` differently from an absent `annotations` key, and `helm lint --strict` may warn about it.
**Fix:** Use the standard `with` guard:

```yaml
{{- with .Values.podAnnotations }}
annotations:
  {{- toYaml . | nindent 8 }}
{{- end }}
```

---

### WR-04: CI Uses Unpinned `actions/checkout@v3` and Non-Deterministic Helm Version

**File:** `.github/workflows/helm-lint.yml:18` and `.github/workflows/helm-lint.yml:22`
**Issue:** `actions/checkout@v3` is an old major version (v4 is current) and references a floating major tag that can receive breaking patches without notice. `azure/setup-helm@v3` with `version: 'latest'` is non-deterministic — a future Helm release with breaking changes will silently alter CI behavior. For a lint gate, non-determinism means a passing job today may fail tomorrow with no code change.
**Fix:**

```yaml
- uses: actions/checkout@v4
- name: Set up Helm
  uses: azure/setup-helm@v4
  with:
    version: 'v3.16.3'   # pin to a known-good release
```

---

## Info

### IN-01: Placeholder Repository URLs in Chart.yaml

**File:** `.devops/helm/textreaderrpi/Chart.yaml:7-9`
**Issue:** Both `home` and `sources` reference `https://github.com/yourusername/textreaderrpi` — a placeholder that was never replaced with the real repository URL. The README has the same placeholder. These will appear in `helm show chart` output and in any chart repository index.
**Fix:** Replace with the actual repository URL or remove the fields if the repo is private.

---

### IN-02: README Uninstall Section Uses Wrong PVC Names

**File:** `.devops/helm/textreaderrpi/README.md:118-119`
**Issue:** The uninstall section shows:
```bash
kubectl delete pvc textreaderrpi-data textreaderrpi-logs
```
But the actual PVC names are `{{ fullname }}-data` and `{{ fullname }}-logs`, which depend on the release name (e.g., `helm install myapp .` creates `myapp-textreaderrpi-data`). Users following these docs will delete the wrong PVC or get a "not found" error.
**Fix:** Update the example to make the release-name dependency explicit:
```bash
# Replace RELEASE_NAME with your actual release name (helm list)
kubectl delete pvc RELEASE_NAME-textreaderrpi-data RELEASE_NAME-textreaderrpi-logs
```

---

### IN-03: `image.tag: latest` Default Is Non-Deterministic

**File:** `.devops/helm/textreaderrpi/values.yaml:4`
**Issue:** Using `latest` as the default tag means two installs from the same chart version can deploy different images. Combined with `imagePullPolicy: Never`, this is less risky in practice (the image must be pre-loaded), but it is still an imprecise default.
**Fix:** Default to a specific version string that matches `Chart.yaml`'s `appVersion`:
```yaml
image:
  tag: "1.2"   # matches appVersion in Chart.yaml
```

---

_Reviewed: 2026-07-02T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
