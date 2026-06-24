# Phase 18: Kubernetes + Helm - Context

**Gathered:** 2026-06-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a Helm chart in `.devops/helm/textreaderrpi/` that deploys TextReaderRpi to a Kubernetes cluster. The chart must:
- Pass `helm lint` with no errors or warnings
- Start a pod that passes `/health` (liveness) and `/health/ready` (readiness) probes
- Expose a `hardwareAccess.enabled` toggle that mounts `/dev/spidev0.0` + `/dev/i2c-1` when true, and sets `DISPLAY_TYPE=OFFLINE` when false
- Cap JVM heap at `-Xmx220m` via `JAVA_TOOL_OPTIONS`
- Use a PVC for H2 data (`/data`) and a PVC for logs (`/app/logs`)

No new application features. No ingress, TLS, HPA, RBAC, or multi-replica. This is a packaging phase only.

Requirement: OPS-01

</domain>

<decisions>
## Implementation Decisions

### Image

- **D-01:** `image.repository: textreaderrpi`, `image.tag: latest`. Bare name — user overrides on `helm install` if using a registry.
- **D-02:** `imagePullPolicy: Never` — assumes image is pre-loaded on the node via `./gradlew publishImageToLocalRegistry`. No registry pull attempted.

### Hardware Access

- **D-03:** `hardwareAccess.enabled: true` → `securityContext.privileged: true` + two `hostPath` volumes for `/dev/spidev0.0` and `/dev/i2c-1`. Mirrors the proven `docker-compose.yml` approach. No device plugin required.
- **D-04:** `hardwareAccess.enabled: false` → no device mounts, no privileged flag. The Deployment injects `DISPLAY_TYPE=OFFLINE` as an env var override (takes precedence over ConfigMap value). OfflineDisplayDriver is already implemented — no code change needed.
- **D-05:** `nodeSelector: {}` in `values.yaml` (empty by default). User sets `kubernetes.io/arch: arm64` (or a custom label) to pin the pod to a Pi node in multi-node clusters.

### Persistence

- **D-06:** `/data` (H2 database) → PersistentVolumeClaim. Configurable `persistence.data.storageClass` and `persistence.data.size` (default `1Gi`). Works with K3s `local-path` provisioner out of the box.
- **D-07:** `/app/logs` → PersistentVolumeClaim. Configurable `persistence.logs.storageClass` and `persistence.logs.size` (default `500Mi`). User chose explicit log persistence over `emptyDir`.

### Env Var Exposure

- **D-08:** Non-sensitive vars → ConfigMap (`textreaderrpi-config`). All 23 non-credential env vars from `docker-compose.yml` are listed as named keys with the same defaults.
- **D-09:** Sensitive vars → Secret (`textreaderrpi-secret`). Contains `DATABASE_USER` and `DATABASE_PASSWORD`. Deployment references these via `envFrom` (ConfigMap) + targeted `env[].valueFrom.secretKeyRef` entries for the two credential vars.

### JVM Heap

- **D-10:** `JAVA_TOOL_OPTIONS: "-Xmx220m"` injected via ConfigMap. Keeps heap within the 256 MB Pi constraint documented in STATE.md.

### Health Probes

- **D-11:** Liveness probe → `GET /health` (Ktor liveness, already implemented).
- **D-12:** Readiness probe → `GET /health/ready` (Ktor readiness, already implemented).
- Initial delay: 20s (matches docker-compose `start_period`), interval: 30s, timeout: 5s, failure threshold: 3.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Deployment Config
- `.devops/containers/docker-compose.yml` — canonical list of all 25 env vars with defaults; device mounts (`/dev/spidev0.0`, `/dev/i2c-1`); volume structure (`data`, `logs`); health check definition. The Helm chart is a K8s translation of this file.

### Requirements
- `.planning/REQUIREMENTS.md` §Kubernetes + Helm — OPS-01 acceptance criteria (4 success criteria listed). Also lists deferred K8s items (ingress, TLS, HPA, multi-replica) — do NOT implement these.
- `.planning/ROADMAP.md` §Phase 18 — success criteria, dependency note (requires Phase 13 stable Docker image).

### Image Build
- `build.gradle.kts` §jib block — image name `textreaderrpi`, tags `latest` + `${project.version}`, platforms arm64+amd64. No registry configured — local daemon only.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `OfflineDisplayDriver` — already handles the `DISPLAY_TYPE=OFFLINE` path; no code changes needed for the hardware-off case.
- `.devops/containers/docker-compose.yml` — the 25 env vars with defaults are the authoritative source; copy directly into ConfigMap template.

### Established Patterns
- All config is env-var-driven — the chart does not need to write any config files. Env vars land in the Deployment via `envFrom` (ConfigMap) and `env[]` (Secret refs).
- `privileged: true` is already required in docker-compose — same flag applies in K8s securityContext.

### Integration Points
- Chart templates: `templates/deployment.yaml`, `templates/configmap.yaml`, `templates/secret.yaml`, `templates/pvc-data.yaml`, `templates/pvc-logs.yaml`, `templates/service.yaml`, `Chart.yaml`, `values.yaml`.
- No new application code. No changes to `src/`.

</code_context>

<specifics>
## Specific Ideas

- `hardwareAccess.enabled: false` must inject `DISPLAY_TYPE=OFFLINE` as an explicit env override in the Deployment (not just omit device mounts) — the ConfigMap default may have a different value and must be overridden cleanly.
- nodeSelector defaults to empty `{}` in values.yaml — chart works on any node by default, Pi node targeting is opt-in.
- PVC names should follow chart release name convention: `{{ .Release.Name }}-data` and `{{ .Release.Name }}-logs`.

</specifics>

<deferred>
## Deferred Ideas

- Ingress + TLS — listed in REQUIREMENTS.md "Future Requirements (v1.3+)". Do NOT add.
- HPA (autoscaling) — deferred, single-replica only for v1.2.
- RBAC / ServiceAccount — not required by OPS-01.
- Multi-replica with PostgreSQL backend — deferred to v1.3+.
- Secret management via external-secrets-operator — out of scope; plain K8s Secret is sufficient for home lab.

</deferred>

---

*Phase: 18-kubernetes-helm*
*Context gathered: 2026-06-24*
