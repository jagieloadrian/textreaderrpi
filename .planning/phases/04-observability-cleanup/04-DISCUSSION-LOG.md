# Phase 4: Cleanup + Observability - Discussion Log

**Date:** 2026-05-27
**Mode:** Interactive discuss-phase

## Areas Discussed
- Phase scope definition (Phase 4 not previously formalized)
- Scope split strategy (4a cleanup+observability, 4b scheduling+effects)
- `/metrics` contract and rollout strategy
- Cleanup/refactor backlog promoted into phase scope
- Health implementation direction (KHealth restoration with extended fields)

## Final Decisions Captured

### Scope
- Phase 4 is defined as **cleanup + observability**.
- Scheduling/effects are split into the next phase (Phase 5).

### Metrics
- `/metrics` includes all groups: runtime, API, and hardware metrics.
- Current implementation mode: **custom JSON endpoint** (no Micrometer in this phase).
- Access policy: **Option A** — public endpoint with **rate limiting enabled**.
- Rate-limit nuance: `/metrics` gets a **separate milder policy** than `/api/*`.
- Payload structure: **dynamic grouped schema** (`groups[]`).
- Design should keep migration path to Micrometer/Prometheus straightforward.

### Health
- Keep/restore **KHealth** as primary health plugin.
- Integration mode: adapter/bridge over KHealth to preserve existing endpoint contracts.
- Extend health response with additional fields from Phase 3 outcomes:
  - `uptime`, `memoryUsedMb`, `memoryMaxMb`, `displayType`, `isActive`, `lastError`.

### Cleanup and Refactor
- Reorganize devops artifacts under `.devops/containers/*` and `.devops/host/*`.
- Move `displayApi` package contents into `model`.
- Relocate `OfflineDisplayDriver` to the proper module/package.
- Consolidate routing into **feature-first** layout (`text`, `health`, `display`, `metrics`).
- Ensure text routes are protected by rate limiting.
- Refactor `RecoveryPolicy` for readability and remove excessive comments.
- Refactor `ResourceTracker` toward a monitoring-oriented module.
- Standardize tests into one convention: package structure mirrors production ownership.

## Canonical References Mentioned
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/phases/03-production-ready/03-CONTEXT.md`
- `.planning/phases/03-production-ready/03-01-SUMMARY.md`
- `.planning/phases/03-production-ready/03-VERIFICATION.md`

## Ready for Next Step
Proceed to phase planning with `/gsd-plan-phase 4`.

