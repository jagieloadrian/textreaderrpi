---
phase: 12
slug: observability-gap-closures
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-16
updated: 2026-06-17
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest (FunSpec, `should` convention) |
| **Config file** | none — Gradle test config in `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest" --tests "com.anjo.routing.MetricsRoutesTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick test command
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| hardware-metrics-model | 01 | 1 | OBS-02 | — | HardwareMetrics DISABLED sentinel prevents counter allocation when metrics disabled; null-safe increments | unit | `./gradlew test --tests "com.anjo.service.HardwareMetricsTest" --tests "com.anjo.service.RetryPolicyTest"` | ✅ | ✅ green |
| hardware-metrics-wire | 01 | 1 | OBS-02 | T-12-02 | GET /metrics groups array has 3 entries: runtime, api, hardware; rate-limited at 120 req/min | integration | `./gradlew test --tests "com.anjo.routing.MetricsRoutesTest" --tests "com.anjo.ApplicationTest"` | ✅ | ✅ green |
| health-detail-dto | 02 | 2 | OBS-01 | — | ZoneStatus.error additive (no breaking change); HealthDetailResponse flat DTO assembled in route handler | integration | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest"` | ✅ | ✅ green |
| health-detail-register | 02 | 2 | OBS-01 | T-12-05 | GET /health/detail returns 200 + JSON with uptime, memoryUsed, memoryMax, displayStatus, totalFailures, zoneErrors; rate-limited 120/min; displayAvailable liveness check in /health | integration | `./gradlew test --tests "com.anjo.routing.HealthRoutesTest" --tests "com.anjo.ApplicationTest"` | ✅ | ✅ green |
| html-404-fix | 03 | 3 | OBS-03 | T-12-06 | Browser Accept:text/html unknown route → 404 HTML page with "Error 404"; API paths /api/... → JSON (no HTML); respondText() passes correct status code | integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

All test files existed pre-phase; they were updated (not created from scratch) during execution. No new test framework install needed.

- [x] Updated `MetricsRoutesTest` — `shouldHaveSize 3`, added `hardware` to groupNames assertion
- [x] Updated `HealthRoutesTest` — asserts `/health/detail` 200 + JSON fields; `/health` body contains "displayAvailable"; accepts 200 or 503 (no hardware zones ONLINE in test env)
- [x] Updated `ApplicationTest` — HTML 404 assertion (`/does-not-exist` + `Accept: text/html`); API-path JSON discrimination assertion

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser renders HTML 404 page (not JSON) for unknown path | OBS-03 | Human visual confirm that the rendered HTML page looks correct (SC-3 checkpoint in plan 03) | Navigate to `http://<host>:<port>/does-not-exist` in browser; confirm HTML page with "Error 404" heading and no JSON body — **done during plan 03 execution** |

---

## Validation Sign-Off

- [x] All tasks have automated verify commands
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all test updates
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

## Validation Audit 2026-06-17
| Metric | Count |
|--------|-------|
| Gaps found | 5 (all TBD task IDs, status draft) |
| Resolved (automated) | 5 |
| Escalated to manual-only | 0 (browser visual already done during plan 03) |

**Approval:** complete
