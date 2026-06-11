---
phase: 5
slug: scheduling-effects
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-27
audited: 2026-06-10
---

# Phase 5 — Validation Strategy

> Per-phase validation contract — audited retroactively 2026-06-10. All test files verified as existing and passing.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest FunSpec + MockK + kotlinx-coroutines-test |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "com.anjo.*"` |
| **Full suite command** | `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` |
| **Estimated runtime** | ~45–60 seconds |

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
| 5-W1-health | W1 | 1 | D5-01 | — | `/health/detail` returns 404 post-merge | integration | `./gradlew test --tests "*HealthRoutes*"` | ✅ | ✅ green |
| 5-W1-retry | W1 | 1 | D5-02/03 | — | Retry fires ≤ maxAttempts, backoff delays respected | unit (virtual time) | `./gradlew test --tests "*ScreenDriverRecovery*"` | ✅ | ✅ green |
| 5-W1-mutex | W1 | 1 | D5-04 | — | Mutex released after permanent driver failure; second call succeeds without deadlock | unit | `./gradlew test --tests "*ScreenDriverRecovery*"` | ✅ | ✅ green |
| 5-W1-metrics-cfg | W1 | 1 | D5-05 | — | Service operates normally when `ScreenDriverMetrics.DISABLED` | unit | `./gradlew test --tests "*ScreenDriverRecovery*"` | ✅ | ✅ green |
| 5-W1-routes-pkg | W1 | 1 | D5-07/08 | — | All existing routes return correct status codes after package move | integration | `./gradlew test` | ✅ | ✅ green |
| 5-W2-sched-oneshot | W2 | 2 | REQ-SCHED-01 | — | One-shot fires at target time ± 500ms (virtual) | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ✅ | ✅ green |
| 5-W2-sched-recurring | W2 | 2 | REQ-SCHED-01 | — | Recurring fires at every interval, stops at maxRuns | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ✅ | ✅ green |
| 5-W2-sched-cron | W2 | 2 | REQ-SCHED-01 | — | Cron fires at next computed cron time (virtual time, ≤60s) | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ✅ | ✅ green |
| 5-W2-sched-crud | W2 | 2 | REQ-SCHED-01 | V5 | POST /api/schedule returns 422 for invalid cron; 422 for oversized text; cancel returns 204 | integration | `./gradlew test --tests "*ScheduleRoutes*"` | ✅ | ✅ green |
| 5-W2-conflict | W2 | 2 | REQ-CONFLICT-01 | — | Ad-hoc `displayImmediate` cancels active scheduled job | behavioral (virtual time) | `./gradlew test --tests "*ConflictPolicy*"` | ✅ | ✅ green |
| 5-W2-priority | W2 | 2 | REQ-CONFLICT-01 | — | Higher-priority schedule fires before lower on same time | unit | `./gradlew test --tests "*ConflictPolicy*"` | ✅ | ✅ green |
| 5-W3-scroll | W3 | 3 | REQ-EFFECT-01 | — | ScrollEffect calls `driver.scrollText()` with original text | unit | `./gradlew test --tests "*EffectRenderer*"` | ✅ | ✅ green |
| 5-W3-blink | W3 | 3 | REQ-EFFECT-01 | — | BlinkEffect alternates `setBrightness(0)` and `setBrightness(15)`, restores to 15 | unit (MockK) | `./gradlew test --tests "*EffectRenderer*"` | ✅ | ✅ green |
| 5-W3-reverse | W3 | 3 | REQ-EFFECT-01 | — | ReverseEffect passes `text.reversed()` to `scrollText()` | unit | `./gradlew test --tests "*EffectRenderer*"` | ✅ | ✅ green |
| 5-W3-fade | W3 | 3 | REQ-EFFECT-01 | — | FadeEffect ramps brightness 0→15 in order, then calls scrollText | unit (MockK) | `./gradlew test --tests "*EffectRenderer*"` | ✅ | ✅ green |
| 5-W3-effect-api | W3 | 3 | REQ-EFFECT-01 | V5 | POST /api/text with invalid effect returns 4xx | integration | `./gradlew test --tests "*TextApiRoute*"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

All test files existed at phase execution time or were created during execution. No stub generation was needed retroactively.

| File | Status |
|------|--------|
| `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` | ✅ exists (9 tests: ONESHOT ×2, RECURRING ×4, cancel ×2, CRON ×1) |
| `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` | ✅ exists (4 tests: preemption, priority, tie-break) |
| `src/test/kotlin/com/anjo/service/effect/EffectRendererTest.kt` | ✅ exists (6 tests: all 4 effects) |
| `src/test/kotlin/com/anjo/routing/ScheduleRoutesTest.kt` | ✅ exists (9 tests: CRUD, cancel, 422 validation) |
| `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` | ✅ exists (5 tests: insert/find/update/delete, in-memory H2) |
| `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` | ✅ exists (retry, mutex, metrics-disabled) |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| H2 schedule data survives JVM process restart | REQ-SCHED-01 | `ScheduleRepositoryTest` uses in-memory H2; file-mode persistence across JVM restarts requires running process | POST schedule, kill process, restart, GET /api/schedule — entry must persist |
| Cron schedule fires at wall-clock time on Pi | REQ-SCHED-01 | Real-time scheduling on device; virtual-time test only covers delay logic | Schedule cron `"*/1 * * * *"` on Pi, wait 2 cycles, observe display |
| MAX7219 `setBrightness()` blink causes visible on/off | REQ-EFFECT-01 | Real SPI device required | Run on Pi, POST /api/text with `effect=blink`, observe LED alternation |
| MAX7219 `setBrightness()` fade produces visible brightness ramp | REQ-EFFECT-01 | Real SPI device required | Run on Pi, POST /api/text with `effect=fade`, observe LED brightness ramp |

---

## Validation Audit 2026-06-10

| Metric | Count |
|--------|-------|
| Tasks mapped | 16 |
| COVERED (green) | 16 |
| MANUAL-ONLY | 4 (2 hardware, 2 system-level) |
| Gaps found | 1 (`5-W2-sched-cron` — no CRON test) |
| Resolved | 1 (CRON test added to `SchedulerServiceTest.kt`) |
| Escalated | 0 |

**Gap resolution:** Added `"should fire CRON schedule at next computed cron time"` to `SchedulerServiceTest.kt`. Uses `TriggerType.CRON` with expression `"*/1 * * * *"`, advances virtual time 60,001ms (past the next minute boundary), verifies `displayScheduled` called at least once. `delay(delayMs)` inside `launchCron()` is coroutine-virtual-time-controlled; `ZonedDateTime.now()` supplies a real wall-clock delay ≤ 60,000ms for a per-minute cron.

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only designation
- [x] Sampling continuity: all implemented behaviors have automated commands
- [x] No Watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` — all implemented behaviors have automated coverage

**Approval:** 2026-06-10 (retroactive — phase completed 2026-05-28)
