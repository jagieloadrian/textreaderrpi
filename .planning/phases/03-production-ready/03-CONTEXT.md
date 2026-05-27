# Phase 3: Production Ready - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

## Phase Boundary

Phase 3 delivers production-readiness hardening for health monitoring, failure recovery, runtime performance control, traffic protection, deployment automation, and production documentation.

This phase clarifies HOW to implement operational reliability for the existing multi-display system without adding Phase 4 capabilities.

## Implementation Decisions

### Health and Readiness
- **D-01:** Implement standard health endpoints via KHealth (`/health`, `/health/ready`).
- **D-02:** `/health` performs app liveness plus a basic hardware state check.
- **D-03:** Health model uses two tiers: summary and detail diagnostics.
- **D-04:** Include `displayType`, `isActive`, and `lastError` in health metadata.
- **D-05:** Hardware probe state is established at startup and reused in health responses.
- **D-06:** `/health/ready` must validate driver initialization and configuration validity.
- **D-07:** Use granular status codes: 200 (healthy), 400 (invalid config/precondition), 503 (unavailable/degraded).

### Error Recovery and Resilience
- **D-08:** On hardware failures use exponential backoff retry (3 attempts).
- **D-09:** If retries exhaust, queue operation, return HTTP 202, continue background recovery.
- **D-10:** Add circuit breaker: open after 3 failures, 30s backoff.
- **D-11:** Run active background recovery every 30s.
- **D-12:** On shutdown clear display and catch/log GPIO cleanup errors.
- **D-13:** Keep local structured logging and expose an error queue endpoint.
- **D-14:** `/api/errors` returns newest-first in-memory top 1000 with filtering by error type.

### Performance and Profiling
- **D-15:** Use balanced, profile-driven optimization instead of single-metric tuning.
- **D-16:** Add display buffer cache to skip unchanged text renders.
- **D-17:** Keep default JVM GC behavior in this phase (no custom GC tuning yet).
- **D-18:** Add JMH benchmark suite plus JVM runtime statistics.
- **D-19:** Log memory stats at key lifecycle events.
- **D-20:** Benchmark scenarios must include latency, throughput, memory, and recovery-time.

### Rate Limiting and Traffic Protection
- **D-21:** Apply rate limiting to all `/api/*` endpoints.
- **D-22:** Health endpoints are also rate-limited (no bypass).
- **D-23:** Prefer Ktor RateLimitPlugin; fallback to token-bucket implementation if unavailable.
- **D-24:** Exceeded limit response is HTTP 429 with `Retry-After`.
- **D-25:** Reject immediately when limited (no queue-on-limit behavior).
- **D-26:** No explicit hardware concurrency cap in this phase.

### Deployment and Runtime Operations
- **D-27:** Deliver two deployment paths: Docker + docker-compose, and systemd + install script.
- **D-28:** Kubernetes and Ansible are deferred to future phases.
- **D-29:** Configuration source is env-vars-first with `.env` support.
- **D-30:** Deployment/startup flow must include health gate and fail deployment if unhealthy.
- **D-31:** Use app-level log rotation via Logback.
- **D-32:** Keep single universal JAR artifact for both deployment modes.
- **D-33:** systemd restart policy is `on-failure` with backoff.

### Documentation Standards
- **D-34:** Produce comprehensive docs split by topic (not a single README-only model).
- **D-35:** Use text and ASCII diagrams only.
- **D-36:** Maintain Swagger/OpenAPI plus manual docs.
- **D-37:** Maintain code-level docs through KDoc and extraction workflow.
- **D-38:** Default documentation language is English.

### Deferred Ideas
- **D-39:** `/metrics` endpoint and advanced monitoring dashboarding are deferred to Phase 4+.

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and prior decisions
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/phases/01-mvp/01-CONTEXT.md`
- `.planning/phases/02-enhanced-display-support/02-CONTEXT.md`

### Architecture and integration context
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/STACK.md`
- `.planning/codebase/INTEGRATIONS.md`

### Code anchors for implementation
- `src/main/kotlin/com/anjo/di/Monitoring.kt`
- `src/main/kotlin/com/anjo/routing/Routing.kt`
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`
- `src/main/resources/application.yaml`

---

*Phase: 3-Production Ready*
*Context gathered: 2026-05-27*

