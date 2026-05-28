# Phase 3: Production Ready - Research

**Generated:** 2026-05-27
**Status:** Ready for planning

## Scope Reminder

Phase 3 focuses on production readiness for the existing Ktor + Pi4J app: health/readiness, recovery logic, performance controls, rate limiting, deployment automation, and docs.

## Current Baseline (Code-Backed)

- Monitoring plugins are already wired in `src/main/kotlin/com/anjo/di/Monitoring.kt` (`CallId`, `CallLogging`, `KHealth`).
- Route composition is centralized in `src/main/kotlin/com/anjo/routing/Routing.kt`.
- Display runtime behavior is stateful in `src/main/kotlin/com/anjo/service/ScreenDriver.kt` (`busy`, pending switch queue, cached `lastSentMessage`).
- Config already exposes production-relevant knobs in `src/main/resources/application.yaml`:
  - `hardware.spiTimeoutMs`, `hardware.gpioTimeoutMs`
  - `api.rateLimitPerMinute`, `api.queueSize`
  - display type and hardware settings.
- Build already has test and coverage gates (`jacoco`, 70% min) in `build.gradle.kts`.

## Research Findings by Area

### 1. Health/Readiness Design

Recommended implementation for this codebase:
- Keep KHealth plugin installed in `configureMonitoring()` and add explicit route-level health payload endpoints for the project contract (`/health`, `/health/ready`).
- Use split semantics:
  - `/health`: liveness + light runtime status payload.
  - `/health/ready`: strict readiness check (driver initialized + config valid).
- Include metadata from `ScreenDriverService`/driver status: `displayType`, `isActive`, `lastError`.
- Response code policy from context:
  - `200` healthy
  - `400` bad readiness preconditions/config
  - `503` degraded/unavailable.

Potential pitfall:
- Treating readiness like liveness will cause false green deploys.

### 2. Recovery and Fault Containment

Current service already tracks busy/pending states; Phase 3 should extend it with:
- Bounded retry with backoff (3 attempts).
- Circuit breaker window (open after N failures, cool-down 30s).
- Background recovery probe loop (30s) separated from request thread.
- Explicit shutdown cleanup path for display driver and GPIO.

Potential pitfall:
- Running retries inside request handlers without hard timeout budgets can lock request threads.

### 3. Rate Limiting

Implementation path:
- Prefer native Ktor rate limiting support if available in the current Ktor line.
- If unavailable or insufficient, implement token-bucket limiter in plugin/interceptor style.
- Apply to all `/api/*` endpoints per locked decision; return `429` with `Retry-After`.
- Keep behavior deterministic (reject immediately, no RL queueing).

Potential pitfall:
- High-cardinality per-client state without eviction strategy can increase memory usage.

### 4. Performance/Benchmarking

Practical strategy for this repository:
- Add display message cache check before render to skip duplicate writes.
- Keep JVM defaults for now; rely on benchmark evidence before GC tuning.
- Add JMH benchmark module/scenarios for:
  - submit latency
  - throughput
  - memory behavior under load
  - recovery-time after simulated hardware failure.
- Log memory snapshots at key lifecycle points (startup, recovery events, shutdown).

Potential pitfall:
- Mixing benchmark harness with integration tests can produce noisy, non-reproducible results.

### 5. Deployment Artifacts

Planned deployment deliverables should include both:
1. Docker path
- `Dockerfile`
- `docker-compose.yml`
- `.env`-first runtime configuration
- startup health gate script/check.

2. Bare-metal path
- systemd service unit
- install script
- restart-on-failure with backoff
- log rotation via logback configuration.

Potential pitfall:
- Divergent env var names between Docker and systemd paths.

### 6. Error Queue API

For `/api/errors`:
- Keep in-memory ring buffer (top 1000, newest-first).
- Support filter by error type.
- Read-only endpoint for operational diagnostics.
- Ensure this endpoint itself does not leak stack traces by default.

Potential pitfall:
- Unbounded payload responses if pagination/limit is not enforced.

## Suggested File Targets for Planning

### Likely new files
- `src/main/kotlin/com/anjo/web/routes/HealthRoutes.kt`
- `src/main/kotlin/com/anjo/model/HealthResponse.kt` (or similar DTO file)
- `src/main/kotlin/com/anjo/service/RecoveryStateService.kt` (or equivalent helper)
- `src/main/kotlin/com/anjo/service/ErrorQueueService.kt`
- `src/main/resources/systemd/textreaderrpi.service`
- `scripts/install-systemd.sh`
- `Dockerfile`
- `docker-compose.yml`
- `.env.example`
- `src/jmh/kotlin/...` (benchmark sources)

### Likely modified files
- `src/main/kotlin/com/anjo/di/Monitoring.kt`
- `src/main/kotlin/com/anjo/routing/Routing.kt`
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt`
- `src/main/resources/application.yaml`
- `build.gradle.kts`
- `README.md`

## Sequencing Constraints for Planner

1. Health/readiness contract first (foundation for deploy checks).
2. Recovery/circuit-breaker before load/perf validation.
3. Rate limiting before abuse/load tests.
4. Deployment artifacts after health contract is stable.
5. Docs and runbooks after concrete commands/files exist.

## Verification Evidence Planner Should Require

- Route tests for `/health` and `/health/ready` status matrix (200/400/503).
- Service tests for retry/circuit-breaker/background recovery transitions.
- Rate-limit tests verifying `429` and `Retry-After`.
- JMH results committed (or summarized) for 4 benchmark scenarios.
- Smoke scripts for Docker and systemd startup health gate.
- Documentation checks for deployment and troubleshooting sections.

## Risks

- Hardware-dependent paths may be hard to validate in CI without mocks.
- KHealth + custom health payload may overlap unless route responsibilities are explicit.
- Introducing both Docker and systemd in one phase can increase plan breadth; wave dependency discipline is required.

## Conclusion

Phase 3 is plan-ready: context is specific, required artifacts are clear, and codebase patterns support incremental implementation without architecture rewrite.

