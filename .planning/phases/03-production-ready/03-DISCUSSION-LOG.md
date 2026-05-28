# Phase 3: Production Ready - Discussion Log
**Date:** 2026-05-27
**Mode:** Interactive discuss-phase
## Areas Discussed
- Health and readiness endpoints
- Error recovery strategy
- Performance optimization and benchmarking
- Rate limiting scope and policy
- Deployment and systemd strategy
- Documentation completeness and format
- Additional follow-up areas (metrics scope, health RL policy, error queue contract, JMH scope)
## Final Decisions Captured
### Health
- KHealth is the health core; custom code only extends its output.
- `/health` uses app + basic hardware state.
- Two-tier health output (summary + detail) is preserved on top of KHealth.
- Include `displayType`, `isActive`, `lastError`.
- Startup-based hardware probe state used in health responses.
- `/health/ready` requires driver init + valid config.
- Status codes: 200 / 400 / 503.
### Recovery
- Retry with exponential backoff (3 attempts).
- On exhausted retries: queue operation + return 202 + background recovery.
- Circuit breaker: 3 failures -> 30s backoff.
- Active recovery loop every 30s.
- Shutdown policy: clear display + catch/log GPIO cleanup failures.
- Keep local logging + expose `/api/errors`.
- `/api/errors`: top 1000 in-memory, newest-first, filter by type.
### Performance
- Optimization is balanced and profile-driven.
- Add display buffer cache for unchanged text.
- Keep default JVM GC behavior in this phase.
- Use JMH + JVM stats.
- Log memory stats on key events.
- Benchmarks: latency, throughput, memory, recovery-time.
### Rate Limiting
- Scope: all `/api/*`.
- Health endpoints remain rate-limited (no bypass).
- Use Flaxoos `ktor-server-rate-limiting` as the primary implementation, with the local token-bucket fallback kept as a compatibility path.
- Rate-limit response: 429 + `Retry-After`.
- No explicit hardware concurrency cap.
- Reject immediately on limit hit.
### Deployment
- Support Docker + docker-compose and systemd + install script.
- Defer Kubernetes/Ansible.
- Env vars primary with `.env` support.
- Startup health gate required.
- App-level log rotation via Logback.
- Single universal JAR artifact.
- systemd restart on-failure with backoff.
### Documentation
- Comprehensive docs, split by topic files.
- Text + ASCII diagrams only.
- Swagger/OpenAPI + manual docs.
- KDoc + extraction for code docs.
- Default language: English.
### Explicitly Deferred
- `/metrics` and advanced monitoring dashboards (Phase 4+).
## Canonical References Mentioned
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/phases/01-mvp/01-CONTEXT.md`
- `.planning/phases/02-enhanced-display-support/02-CONTEXT.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/STACK.md`
- `.planning/codebase/INTEGRATIONS.md`
- `src/main/kotlin/com/anjo/di/Monitoring.kt`
- `src/main/kotlin/com/anjo/routing/Routing.kt`
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`
- `src/main/resources/application.yaml`
## Ready for Next Step
Proceed to phase planning with `/gsd-plan-phase 3`.

## Addendum (2026-05-27 re-discuss)

### Clarifications locked in this session
- Keep health endpoints rate-limited (D-22 stays active).
- API rate-limit key strategy: client token/id first, IP fallback.
- Flaxoos limiter remains mandatory runtime path (no legacy fallback).
- Deployment minimum is explicit: systemd unit + host install script + Dockerfile + docker-compose artifact.
- JVM monitoring must cover memory and CPU visibility, not only endpoint health checks.
- Recovery policy should be aligned with a Ktor-idiomatic resilience standard (research + implementation required).
- UI should provide explicit user feedback for 429 and 400 responses.
- Ktor dependency/plugin versions should be centralized in `gradle/ktor-libs.versions.toml`.

### UAT gate expectations captured
1. Health must expose driver/GPIO operational details, not only ready state.
2. Rate limiting must be verified behaviorally (not only unit tests).
3. Dependencies/plugins versions should be cataloged in `ktor-libs.versions.toml`.
4. Monitoring should include JVM resource metrics (memory, CPU).
5. Recovery implementation should follow researched Ktor resilience best practices.
6. Documentation must be updated with final deployment/ops flow.
7. UI error handling should surface clear messages for 429/400.

