# Phase 4: Cleanup + Observability - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

## Phase Boundary

Phase 4 delivers architecture/runtime cleanup and observability expansion for the existing system.

This phase clarifies HOW to restructure and instrument current capabilities without adding new product behavior like scheduling/effects (moved to Phase 5).

## Implementation Decisions

### Scope split and sequencing
- **D4-01:** Use two phases: Phase 4 = cleanup + observability, Phase 5 = scheduling + effects.
- **D4-02:** Phase 4 planning and execution must not include scheduling or display effects implementation.

### Metrics contract
- **D4-03:** Add `GET /metrics` endpoint in JSON format (custom implementation for now, no Micrometer dependency in this phase).
- **D4-04:** `/metrics` includes all groups:
  - runtime: uptime, `memoryUsedMb`, `memoryMaxMb`
  - API: request counters and rate-limit reject counters (HTTP 429)
  - hardware: resource slot state/counters, recovery retries, display failure counters
- **D4-05:** `/metrics` is public and protected by rate limiting (same policy family as API endpoints).
- **D4-06:** Design metrics model so migration to Micrometer/Prometheus in a later phase is low-friction.
- **D4-18:** Metrics response uses dynamic grouped schema (`groups[]`) instead of a flat key-value payload.
- **D4-19:** Rate limiting for `/metrics` uses a dedicated, milder policy than `/api/*` while still enforcing protection.

### Health stack alignment
- **D4-07:** Restore/keep `install(KHealth)` as the health core.
- **D4-08:** Extend health payload with Phase 3 fields from `03-01-SUMMARY.md`: `uptime`, `memoryUsedMb`, `memoryMaxMb`, `displayType`, `isActive`, `lastError`.
- **D4-09:** Keep `/health` and `/health/ready` behavior semantics stable while reintroducing KHealth.
- **D4-20:** Implement KHealth integration via adapter/bridge so existing endpoint contracts remain stable.

### Cleanup/refactor scope
- **D4-10:** Reorganize DevOps artifacts under `.devops/` (deployment scripts, Dockerfile, docker-compose).
- **D4-11:** Move `displayApi` structures into `model` package.
- **D4-12:** Relocate `OfflineDisplayDriver` to the appropriate module/package based on current driver architecture.
- **D4-13:** Consolidate route modules (reduce split/sprawl across folders).
- **D4-14:** Ensure text routes are rate-limited (close current policy gap).
- **D4-15:** Refactor `RecoveryPolicy` for readability and maintainability; remove noise comments.
- **D4-16:** Refactor `ResourceTracker` toward monitoring-oriented ownership/module (instead of ad-hoc runtime instrumentation placement).
- **D4-21:** DevOps directory shape is environment-based: `.devops/containers/*` and `.devops/host/*`.
- **D4-22:** Routing structure after cleanup is feature-first (`text`, `health`, `display`, `metrics`).
- **D4-23:** Test suite cleanup must align tests under package paths mirroring production code ownership.

### Deferred ideas (outside Phase 4)
- **D4-17:** Scheduling and effects implementation is deferred to Phase 5.

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and prior decisions
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/phases/03-production-ready/03-CONTEXT.md`
- `.planning/phases/03-production-ready/03-01-SUMMARY.md`
- `.planning/phases/03-production-ready/03-VERIFICATION.md`

### Architecture and integration context
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/STACK.md`
- `.planning/codebase/INTEGRATIONS.md`

### Code anchors for implementation
- `src/main/kotlin/com/anjo/di/Monitoring.kt`
- `src/main/kotlin/com/anjo/di/RateLimiting.kt`
- `src/main/kotlin/com/anjo/routing/Routing.kt`
- `src/main/kotlin/com/anjo/routing/TextRoutes.kt`
- `src/main/kotlin/com/anjo/service/RecoveryPolicy.kt`
- `src/main/kotlin/com/anjo/service/ResourceTracker.kt`
- `src/main/kotlin/com/anjo/service/HealthService.kt`
- `src/main/resources/application.yaml`
- `Dockerfile`
- `deployment/systemd/textreaderrpi.service`
- `deployment/scripts/install-systemd.sh`

---

*Phase: 4-Cleanup + Observability*
*Context gathered: 2026-05-27*

