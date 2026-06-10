---
phase: 4
slug: observability-cleanup
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-10
---

# Phase 4 — Validation Strategy

> Per-phase validation contract — retroactively reconstructed from SUMMARY.md files (04-01 through 04-05) and VERIFICATION.md.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest FunSpec + MockK |
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
| 4-01-routing | 04-01 | 1 | D4-13/22 | — | All existing routes return correct status after consolidation | integration | `./gradlew test` | ✅ | ✅ green |
| 4-01-text-ratelimit | 04-01 | 1 | D4-14 | — | POST /api/v1/text returns 429 after limit exceeded | integration | `./gradlew test --tests "*RateLimitRoutes*"` | ✅ | ✅ green |
| 4-02-health-khealth | 04-02 | 1 | D4-07/09 | — | GET /health returns 200 with appAlive; GET /health/ready returns 200/503 | integration | `./gradlew test --tests "*HealthRoutes*"` | ✅ | ✅ green |
| 4-03-recovery-refactor | 04-03 | 1 | D4-15/25 | — | Recovery retries up to maxAttempts with backoff (readability refactor — behavior unchanged) | unit | `./gradlew test --tests "*RetryPolicy*"` | ✅ | ✅ green |
| 4-04-resourcetracker | 04-04 | 2 | D4-16/27/28 | — | ScreenDriver in-flight counter at zero after ops; failure counter increments; timer records | unit | `./gradlew test --tests "*ScreenDriverResource*"` | ✅ | ✅ green |
| 4-05-metrics-route | 04-05 | 2 | D4-03/18 | — | GET /metrics returns 200 with timestamp and runtime/api groups | integration | `./gradlew test --tests "*MetricsRoutes*"` | ✅ | ✅ green |
| 4-05-metrics-ratelimit | 04-05 | 2 | D4-05/19 | — | GET /metrics respects dedicated rate-limit policy | integration | `./gradlew test --tests "*MetricsRoutes*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all implemented phase requirements.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GET /health/detail returns 7-field extended health payload | D4-08 | **NOT IMPLEMENTED** — returns 404. `HealthRoutesTest` documents the 404. See v1.0-MILESTONE-AUDIT.md GAP-1. | N/A until implemented |
| /metrics includes hardware group (resource slots, recovery retries, display failures) | D4-04 | **NOT IMPLEMENTED** — MetricsCollector only returns runtime+api groups. `MetricsRoutesTest` asserts 2 groups. See v1.0-MILESTONE-AUDIT.md. | N/A until implemented |
| DevOps artifacts in .devops/ (containers/ and host/) | D4-10/21 | Filesystem layout — not unit-testable | Run: `ls .devops/containers/ .devops/host/` and verify files present |
| Swagger UI at GET /openapi lists all API routes | D4-03 | Requires manual browser check | Open http://localhost:8080/openapi, verify all routes listed |

---

## Validation Audit 2026-06-10

| Metric | Count |
|--------|-------|
| Tasks mapped | 7 |
| COVERED | 5 |
| MANUAL-ONLY | 4 (2 implementation gaps, 2 system-level) |
| Gaps found | 0 testable gaps |
| Resolved | 0 |
| Escalated | 0 |

**Note:** 2 manual-only items are implementation gaps (health/detail, hardware metrics) discovered by /gsd-audit-milestone. These are tracked in v1.0-MILESTONE-AUDIT.md and should be addressed in v2.0.

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only designation
- [x] Sampling continuity: all implemented behaviors have automated commands
- [x] Implementation gaps (GET /health/detail, hardware metrics) documented as manual-only
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` — all IMPLEMENTED behaviors have automated coverage

**Approval:** 2026-06-10 (retroactive — phase completed 2026-05-27)
