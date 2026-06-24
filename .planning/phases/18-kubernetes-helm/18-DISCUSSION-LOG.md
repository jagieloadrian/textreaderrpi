# Phase 18: Kubernetes + Helm - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-24
**Phase:** 18-kubernetes-helm
**Areas discussed:** Image registry, Hardware access mechanism, Persistence — H2 data volume, Env var exposure strategy

---

## Image Registry

| Option | Description | Selected |
|--------|-------------|----------|
| Bare name — 'textreaderrpi' | image.repository: textreaderrpi, tag: latest. Works with local registry or any custom registry — user overrides on helm install. | ✓ |
| GHCR placeholder | image.repository: ghcr.io/diether18/textreaderrpi. Future-proof if publishing. | |

**User's choice:** Bare name  
**Notes:** imagePullPolicy: Never (assumes image pre-loaded via `./gradlew publishImageToLocalRegistry`)

---

## Hardware Access Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| privileged: true + hostPath volumes | Mirrors docker-compose. Simplest for single Pi node. | ✓ |
| SecurityContext + volumeDevices | Non-privileged, device plugin. More complex for Pi K3s. | |

**User's choice:** privileged + hostPath  
**Notes:**
- nodeSelector: configurable in values.yaml, defaults to {} (no selector)
- hardwareAccess.enabled: false → inject DISPLAY_TYPE=OFFLINE env var override

---

## Persistence — H2 Data Volume

| Option | Description | Selected |
|--------|-------------|----------|
| PersistentVolumeClaim — configurable | storageClass + size in values.yaml. Survives restarts. K3s local-path compatible. | ✓ |
| emptyDir | Simpler, data lost on restart. Dev-only. | |
| hostPath | Single-node only, loses portability. | |

**User's choice:** PVC for /data  

| Option | Description | Selected |
|--------|-------------|----------|
| emptyDir for logs | Logs ephemeral — emptyDir is correct. | |
| PVC for logs too | Explicit log retention across restarts. | ✓ |

**User's choice:** PVC for /app/logs as well

---

## Env Var Exposure Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Flat values.yaml env block | Mirrors docker-compose. Transparent, diffable. | |
| ConfigMap + Secret | ConfigMap for non-secrets, Secret for credentials. Proper K8s split. | ✓ |
| extraEnv pass-through | No defaults, fully manual. | |

**User's choice:** ConfigMap for non-secrets + Secret for credentials  

| Option | Description | Selected |
|--------|-------------|----------|
| Secret: DATABASE_PASSWORD only | One secret, DATABASE_USER in ConfigMap. | |
| Secret: DATABASE_USER + DATABASE_PASSWORD | Both credentials in Secret. | ✓ |

**User's choice:** Both DATABASE_USER and DATABASE_PASSWORD in Secret

---

## Claude's Discretion

None — all gray areas had explicit user choices.

## Deferred Ideas

- Ingress + TLS (already in REQUIREMENTS.md future list)
- HPA / autoscaling
- RBAC / ServiceAccount
- Multi-replica + PostgreSQL backend
- external-secrets-operator integration
