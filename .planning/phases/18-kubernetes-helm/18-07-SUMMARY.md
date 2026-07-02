---
phase: 18-kubernetes-helm
plan: "07"
subsystem: helm-chart-docs
tags: [helm, documentation, kubernetes]
status: complete

dependency_graph:
  requires: [18-01, 18-02, 18-03, 18-04, 18-05, 18-06]
  provides: [helm-readme]
  affects: [.devops/helm/textreaderrpi/README.md]

tech_stack:
  added: []
  patterns: [helm-chart-docs]

key_files:
  created: []
  modified:
    - .devops/helm/textreaderrpi/README.md
    - .devops/helm/textreaderrpi/templates/configmap.yaml

decisions:
  - "JAVA_TOOL_OPTIONS templated via {{ .Values.JAVA_TOOL_OPTIONS | quote }} in configmap so --set override works as documented"

metrics:
  duration: "2 minutes"
  completed: "2026-07-02"
  tasks_completed: 3
  files_modified: 1
---

# Phase 18 Plan 07: Helm Chart README Summary

**One-liner:** Comprehensive Helm chart README with install/uninstall examples, hardwareAccess toggle docs, K3s/k3d/Pi cluster scenarios, and troubleshooting guide; plus configmap fix to make JAVA_TOOL_OPTIONS override actually work.

## What Was Built

README.md already existed at `.devops/helm/textreaderrpi/README.md` from commit `1506f48` (created as part of plan 18-01 chart skeleton). Content fully matched the plan specification — all acceptance criteria were pre-satisfied:

- Chart description and prerequisites
- 5 install scenarios (default, hardware, custom registry, ingress, custom storage)
- Configuration table with 10 key parameters
- Uninstall instructions with PVC cleanup note
- 3 deployment scenarios (K3s, k3d, multi-node Pi cluster)
- 4 troubleshooting sections (Pending state, ImagePullBackOff, hardware access, DB lock)
- Helm chart details summary (replicas, storage, RBAC, TLS)

## Verification Results

```
grep -E "^##" README.md   → 8 top-level sections + 13 sub-sections
grep "helm install" README.md → 11 occurrences (plan requires ≥3)
grep -c "hardwareAccess" README.md → 8 references with warnings
Troubleshooting sections: 4 sub-sections (plan requires ≥1)
helm lint: 0 charts failed, 0 errors
```

All acceptance criteria: PASSED.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] configmap.yaml hardcoded JAVA_TOOL_OPTIONS ignoring values.yaml**
- **Found during:** Task 3 (clarity review)
- **Issue:** `configmap.yaml` had `JAVA_TOOL_OPTIONS: "-Xmx220m"` hardcoded. README documents `--set JAVA_TOOL_OPTIONS="-Xmx512m"` as a valid override, but the hardcoded configmap value made this no-op.
- **Fix:** Replaced hardcoded string with `{{ .Values.JAVA_TOOL_OPTIONS | quote }}` — values.yaml default of `"-Xmx220m"` still renders correctly; `--set` now actually overrides.
- **Files modified:** `.devops/helm/textreaderrpi/templates/configmap.yaml`
- **Commit:** e0cb09b
- **Verified:** `helm template --set 'JAVA_TOOL_OPTIONS=-Xmx512m'` renders `-Xmx512m` correctly.

## Known Stubs

None.

## Threat Flags

None — documentation-only plan, no new network endpoints or auth paths introduced.

## Self-Check: PASSED

- [x] `.devops/helm/textreaderrpi/README.md` exists (pre-existing from 1506f48)
- [x] `.devops/helm/textreaderrpi/templates/configmap.yaml` modified in e0cb09b
- [x] helm lint: 0 charts failed
- [x] All acceptance criteria verified
