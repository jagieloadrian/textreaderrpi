---
status: testing
phase: 18-kubernetes-helm
source: [18-VERIFICATION.md]
started: 2026-07-06T00:00:00Z
updated: 2026-07-06T00:00:00Z
---

## Current Test

number: 1
name: Deploy chart to a real Kubernetes cluster and verify pod starts and health probes respond
expected: |
  `helm install textreaderrpi .devops/helm/textreaderrpi/` creates a running pod;
  `curl /health` and `curl /health/ready` return 200 OK
awaiting: user response

## Tests

### 1. Deploy chart to a real Kubernetes cluster and verify pod starts and health probes respond
expected: `helm install textreaderrpi .devops/helm/textreaderrpi/` (e.g. on k3s/k3d/kind, `--set hardwareAccess.enabled=false` off-Pi) creates a running pod; `kubectl port-forward` + `curl http://127.0.0.1:8080/health` and `/health/ready` return 200 OK
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
