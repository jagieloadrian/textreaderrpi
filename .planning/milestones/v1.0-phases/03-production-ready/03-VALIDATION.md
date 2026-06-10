---
phase: 3
slug: production-ready
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-10
---

# Phase 3 — Validation Strategy

> Per-phase validation contract — retroactively reconstructed from SUMMARY.md files (03-01 through 03-05) and VERIFICATION.md.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest FunSpec + MockK + kotlinx-coroutines-test |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "com.anjo.*"` |
| **Full suite command** | `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.*"`
- **After every plan wave:** Run `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification`
- **Before `/gsd-verify-work`:** Full suite must be green, JaCoCo gate must pass
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 3-01-health-liveness | 03-01 | 1 | R3-HEALTH-ENDPOINTS | — | GET /health returns 200 with appAlive check | integration | `./gradlew test --tests "*HealthRoutes*"` | ✅ | ✅ green |
| 3-01-health-readiness | 03-01 | 1 | R3-HEALTH-ENDPOINTS | — | GET /health/ready returns 200/503 based on displayReady check | integration | `./gradlew test --tests "*HealthRoutes*"` | ✅ | ✅ green |
| 3-02-retry-policy | 03-02 | 1 | R3-ERROR-RECOVERY | — | retryWithBackoff succeeds on first attempt; retries on transient failure; throws after maxAttempts | unit | `./gradlew test --tests "*RetryPolicy*"` | ✅ | ✅ green |
| 3-02-screen-recovery | 03-02 | 1 | R3-ERROR-RECOVERY | — | ScreenDriverService retries on SPI timeout; remains usable after permanent failure | unit | `./gradlew test --tests "*ScreenDriverRecovery*"` | ✅ | ✅ green |
| 3-02-mutex-release | 03-02 | 1 | R3-GRACEFUL-SHUTDOWN | — | displayMutex released after permanent driver failure; second readInput() call succeeds without deadlock | unit | `./gradlew test --tests "*ScreenDriverRecovery*"` | ✅ | ✅ green |
| 3-03-resource-metrics | 03-03 | 2 | R3-RESOURCE-MANAGEMENT | — | ScreenDriver inFlight counter at zero after ops; failure counter increments; execution timer records | unit | `./gradlew test --tests "*ScreenDriverResource*"` | ✅ | ✅ green |
| 3-04-rate-limit-api | 03-04 | 2 | R3-RATE-LIMITING | — | /api/v1/* routes return 429 with Retry-After after limit exceeded | integration | `./gradlew test --tests "*RateLimitRoutes*"` | ✅ | ✅ green |
| 3-04-rate-limit-passthrough | 03-04 | 2 | R3-RATE-LIMITING | — | Routes outside /api/v1 (e.g. /status) are not rate-limited | integration | `./gradlew test --tests "*RateLimitRoutes*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all implemented phase requirements. All tests pre-existed at phase execution time.

**Note on Phase 3 refactoring:** `HealthService.kt` and `ResourceTracker.kt` that existed during Phase 3 execution were subsequently replaced/removed in Phase 4 (KHealth re-added in Phase 4, ResourceTracker merged into ScreenDriverService metrics). The behaviors those classes provided are still tested via `HealthRoutesTest`, `ScreenDriverRecoveryTest`, and `ScreenDriverResourceTest`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| JVM heap stays under 256MB during 24h operation | R3-MEMORY-OPTIMIZATION | JVM config (-Xmx256m) — system-level, not unit-testable | Run on Pi 24h, monitor `GET /health` memory fields |
| systemd service restarts on failure | R3-GRACEFUL-SHUTDOWN | Requires systemd runtime | `sudo systemctl stop textreaderrpi; verify auto-restart` |
| Deployment guide steps are complete and accurate | R3-DEPLOYMENT-GUIDE | Documentation audit | Follow `docs/deployment/production-guide.md` on Pi from scratch |
| systemd unit file security hardening | R3-SYSTEMD-SERVICE-FILE | System-level (NoNewPrivileges, PrivateTmp, etc.) | `systemd-analyze security textreaderrpi.service` |
| Monitoring/alerting patterns work as documented | R3-MONITORING-ALERTING | External monitoring stack required | Follow `docs/operations/monitoring-alerting.md` |

---

## Validation Audit 2026-06-10

| Metric | Count |
|--------|-------|
| Tasks mapped | 8 |
| COVERED (green) | 8 |
| MANUAL-ONLY | 5 (all system-level or documentation) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only designation
- [x] Sampling continuity: all implemented behaviors have automated commands
- [x] No testable gaps found
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` — all implemented behaviors have automated coverage

**Approval:** 2026-06-10 (retroactive — phase completed 2026-05-27)
