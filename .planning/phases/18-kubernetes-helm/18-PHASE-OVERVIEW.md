# Phase 18 — Kubernetes + Helm — PLAN

**Phase**: 18-kubernetes-helm  
**Author**: gsd-plan-phase (auto-generated)  
**Created:** 2026-06-24  
**Status:** Planned

## Summary

Deliver a production-ready Helm chart at `.devops/helm/textreaderrpi/` that packages the existing TextReaderRpi application for Kubernetes. The chart will translate the existing `docker-compose.yml` semantics into Kubernetes resources, respect Pi hardware constraints, and satisfy OPS-01 acceptance criteria in `.planning/REQUIREMENTS.md`.

No application code changes are required. This is packaging/ops only.

## Goals / Success Criteria

1. `helm lint .devops/helm/textreaderrpi/` passes with no errors or warnings.  
2. `helm install textreaderrpi .devops/helm/textreaderrpi/` starts a pod that responds 200 on `/health` and `/health/ready`.  
3. `values.yaml` exposes `hardwareAccess.enabled`. When false, the pod starts without device mounts and receives `DISPLAY_TYPE=OFFLINE` via env override.  
4. JVM heap is capped by injecting `JAVA_TOOL_OPTIONS: "-Xmx220m"`.

## Dependencies

- Phase 13: stable Docker image built and available locally (image name `textreaderrpi:latest` or settable via `values.yaml`).  
- `.devops/containers/docker-compose.yml` — canonical list of env vars and device mounts; use it to seed the ConfigMap.

## Plans / Waves (estimated 2 waves)

Wave 1 (Implementation):
- 18-01: Chart skeleton (`Chart.yaml`, `values.yaml`, directory).  
- 18-02: `templates/deployment.yaml` (hardcoded `replicas: 1`, securityContext hostPath/privileged when `hardwareAccess.enabled`, envFrom ConfigMap, secret refs, `JAVA_TOOL_OPTIONS` injection, liveness/readiness probes).  
- 18-03: `templates/configmap.yaml` (non-sensitive envs from docker-compose) and `templates/secret.yaml` for DB creds.  
- 18-04: PVC templates for `/data` and `/app/logs`.

Wave 2 (Hardening & QA):
- 18-05: `templates/serviceaccount.yaml` + `templates/rbac.yaml` (Role + RoleBinding).  
- 18-06: `templates/ingress.yaml` gated by `ingress.enabled` (no TLS).  
- 18-07: Helpers (`_helpers.tpl`), `README.md` in chart dir with usage & hardwareAccess notes.  
- 18-08: `helm lint` fixes and optional CI `ci/helm-lint.yml`.

## Tasks (detailed estimates)

1. 18-01 — Chart skeleton  
   - Create `.devops/helm/textreaderrpi/Chart.yaml`, `values.yaml`  
   - Estimate: 1.5h  
   - Owner: @todo

2. 18-02 — Deployment template  
   - Implement `replicas: 1`, image fields, `securityContext.privileged` + `hostPath` volumes for `/dev/spidev0.0` and `/dev/i2c-1` when `hardwareAccess.enabled: true`, envFrom ConfigMap, targeted secret refs for DB creds, override `DISPLAY_TYPE=OFFLINE` when `hardwareAccess.enabled: false`, inject `JAVA_TOOL_OPTIONS: -Xmx220m`, probes `GET /health` and `GET /health/ready` (initialDelaySeconds:20, periodSeconds:30, timeoutSeconds:5).  
   - Estimate: 3.0h  
   - Owner: @todo

3. 18-03 — ConfigMap & Secret templates  
   - Create templates for 25 non-sensitive env vars (copy defaults from `.devops/containers/docker-compose.yml`) and Secret for `DATABASE_USER`/`DATABASE_PASSWORD`.  
   - Estimate: 1.5h  
   - Owner: @todo

4. 18-04 — PVC templates  
   - `templates/pvc-data.yaml` (default 1Gi) and `templates/pvc-logs.yaml` (default 500Mi), names `{{ .Release.Name }}-data`, `{{ .Release.Name }}-logs`.  
   - Estimate: 1.0h  
   - Owner: @todo

5. 18-05 — Service, ServiceAccount & RBAC  
   - `templates/service.yaml`, `templates/serviceaccount.yaml`, `templates/rbac.yaml` with `rbac.create: true` default and minimal Role permissions (`get`/`list` pods).  
   - Estimate: 1.0h  
   - Owner: @todo

6. 18-06 — Ingress (gated)  
   - `templates/ingress.yaml` with `ingress.enabled` default false.  
   - Estimate: 0.5h  
   - Owner: @todo

7. 18-07 — Helpers, README & docs  
   - `templates/_helpers.tpl` and chart `README.md` with install/uninstall and hardwareAccess caveats.  
   - Estimate: 1.0h  
   - Owner: @todo

8. 18-08 — Lint, CI & verification  
   - `helm lint`, CI job (optional), smoke tests.  
   - Estimate: 1.0h  
   - Owner: @todo

Total estimated time: ~10.5 hours (1–2 working days depending on reviews).

## File mappings (where to add/change)

- Add chart: `.devops/helm/textreaderrpi/` (new)  
- Source env list: `.devops/containers/docker-compose.yml`  
- Plan document: `.planning/phases/18-kubernetes-helm/18-PLAN.md` (this file)  
- No changes to `src/` required.

## Risks & Mitigations

- **Risk**: ImagePull issues if `imagePullPolicy: Never` and image not present locally.  
  **Mitigation**: Document pushing to registry or loading image onto nodes; provide `image.repository` + `image.tag` knobs in `values.yaml`.

- **Risk**: Privileged pods + hostPath reduce cluster security.  
  **Mitigation**: Default `hardwareAccess.enabled: false` and document enabling only on trusted Pi nodes.

- **Risk**: Single replica + H2 file DB → data loss risk if node dies.  
  **Mitigation**: Document backups and postpone DB migration until a refactor/migration phase.

## Testing & Verification Plan

**Local setup** (k3s / k3d / kind / Pi cluster):

1. Build or make image available (local daemon or registry).  
   Example: `./gradlew jibDockerBuild` or local image load per project README.

2. Lint chart:
   ```bash
   helm lint .devops/helm/textreaderrpi/
   ```

3. Install (hardwareAccess disabled):
   ```bash
   helm install textreaderrpi .devops/helm/textreaderrpi/ --set hardwareAccess.enabled=false
   kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=textreaderrpi --timeout=120s
   kubectl port-forward svc/textreaderrpi 8080:80 &
   curl -sS http://127.0.0.1:8080/health
   curl -sS http://127.0.0.1:8080/health/ready
   ```

4. Install on Pi node (hardwareAccess enabled — careful):
   ```bash
   helm install textreaderrpi-hw .devops/helm/textreaderrpi/ --set hardwareAccess.enabled=true
   kubectl get pods -o wide
   kubectl logs deploy/textreaderrpi -c textreaderrpi
   ```

5. Verify JVM memory cap:
   - Exec into container and inspect `JAVA_TOOL_OPTIONS` or use `jcmd <pid> VM.flags` to confirm `-Xmx220m`.

**Verification checklist** (mark items complete after testing):
- [ ] `helm lint` passes with no warnings or errors.
- [ ] `helm install` (hardwareAccess=false) pod responds 200 on `/health` and `/health/ready`.
- [ ] `helm install` (hardwareAccess=true) starts on Pi node with device mounts (or falls back cleanly to `OFFLINE` mode).
- [ ] `JAVA_TOOL_OPTIONS` includes `-Xmx220m`.
- [ ] Chart `README.md` is present with install/uninstall/hardwareAccess caveats.

## CI / Automation Suggestions (optional)

- Add `.github/workflows/helm-lint.yml` to run `helm lint` across a matrix of Helm versions.
- Optionally add a `ci/helm-install-smoke.yml` job that runs a k3s runner and performs smoke `helm install` + `/health` check.

## Next Steps (actionable)

1. Create a branch:
   ```bash
   git checkout -b feat/18-helm-chart
   ```

2. Implement tasks 1–8 in sequence or waves:
   - Wave 1: create chart skeleton, deployment template, configmap/secret, PVCs
   - Wave 2: add RBAC, ingress, helpers, README

3. After each wave, run `helm lint` and iterate.

4. Once all templates are complete and lint-clean, open a PR:
   ```bash
   git push --set-upstream origin feat/18-helm-chart
   ```
   PR title: `feat(helm): add textreaderrpi Helm chart (.devops/helm/textreaderrpi)`

5. After PR review and approval, merge and tag as Phase 18 complete.

---

**See also:**
- `.planning/phases/18-kubernetes-helm/18-CONTEXT.md` — context & decisions.
- `.planning/REQUIREMENTS.md` — OPS-01 acceptance criteria.
- `.devops/containers/docker-compose.yml` — env vars and device mount reference.


