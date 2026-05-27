---
phase: 5
slug: scheduling-effects
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-27
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest (FunSpec) + MockK + kotlinx-coroutines-test |
| **Config file** | `build.gradle.kts` (test configuration present) |
| **Quick run command** | `./gradlew test --tests "com.anjo.*"` |
| **Full suite command** | `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` |
| **Estimated runtime** | ~30–60 seconds |

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
| 5-W1-health | W1 | 1 | D5-01 | — | `/health/detail` returns 404 post-merge | integration | `./gradlew test --tests "*HealthRoutes*"` | ✅ modify | ⬜ pending |
| 5-W1-retry | W1 | 1 | D5-02/03 | — | Retry fires ≤ maxAttempts, backoff delays respected | unit (virtual time) | `./gradlew test --tests "*RecoveryPolicy*"` | ❌ W0 | ⬜ pending |
| 5-W1-mutex | W1 | 1 | D5-04 | — | Mutex prevents concurrent display ops | unit | `./gradlew test --tests "*ScreenDriver*"` | ✅ modify | ⬜ pending |
| 5-W1-metrics-cfg | W1 | 1 | D5-05 | — | Metrics disabled when `metrics.enabled: false` | unit | `./gradlew test --tests "*Metrics*"` | ✅ modify | ⬜ pending |
| 5-W1-routes-pkg | W1 | 1 | D5-07/08 | — | All existing routes return correct status codes after move | integration | `./gradlew test` | ✅ modify | ⬜ pending |
| 5-W2-sched-oneshot | W2 | 2 | REQ-SCHED-01 | — | One-shot fires at target time ± 500ms (virtual) | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ❌ W0 | ⬜ pending |
| 5-W2-sched-recurring | W2 | 2 | REQ-SCHED-01 | — | Recurring fires at every interval, stops at maxRuns | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ❌ W0 | ⬜ pending |
| 5-W2-sched-cron | W2 | 2 | REQ-SCHED-01 | — | Cron fires at next computed cron time | unit (virtual time) | `./gradlew test --tests "*SchedulerService*"` | ❌ W0 | ⬜ pending |
| 5-W2-sched-crud | W2 | 2 | REQ-SCHED-01 | V5 | POST /api/schedule returns 422 for invalid trigger format | integration | `./gradlew test --tests "*ScheduleRoute*"` | ❌ W0 | ⬜ pending |
| 5-W2-conflict | W2 | 2 | REQ-CONFLICT-01 | — | Ad-hoc preempts schedule; schedule re-queued | behavioral (virtual time) | `./gradlew test --tests "*ConflictPolicy*"` | ❌ W0 | ⬜ pending |
| 5-W2-priority | W2 | 2 | REQ-CONFLICT-01 | — | Higher-priority schedule fires before lower on same time | unit | `./gradlew test --tests "*ConflictPolicy*"` | ❌ W0 | ⬜ pending |
| 5-W2-persist | W2 | 2 | REQ-SCHED-01 | V5 | Schedule survives service restart (H2 file-mode) | integration | `./gradlew test --tests "*ScheduleRepository*"` | ❌ W0 | ⬜ pending |
| 5-W3-scroll | W3 | 3 | REQ-EFFECT-01 | — | ScrollEffect calls driver.scrollText() | unit | `./gradlew test --tests "*EffectRenderer*"` | ❌ W0 | ⬜ pending |
| 5-W3-blink | W3 | 3 | REQ-EFFECT-01 | — | BlinkEffect calls setBrightness(0) then setBrightness(15) | unit (MockK) | `./gradlew test --tests "*EffectRenderer*"` | ❌ W0 | ⬜ pending |
| 5-W3-reverse | W3 | 3 | REQ-EFFECT-01 | — | ReverseEffect passes text.reversed() to scrollText() | unit | `./gradlew test --tests "*EffectRenderer*"` | ❌ W0 | ⬜ pending |
| 5-W3-fade | W3 | 3 | REQ-EFFECT-01 | — | FadeEffect ramps brightness 0→15 in order | unit (MockK) | `./gradlew test --tests "*EffectRenderer*"` | ❌ W0 | ⬜ pending |
| 5-W3-effect-api | W3 | 3 | REQ-EFFECT-01 | V5 | POST /api/text with invalid effect returns 422 | integration | `./gradlew test --tests "*TextApiRoute*"` | ✅ modify | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — stubs for REQ-SCHED-01 (one-shot, recurring, cron, virtual time)
- [ ] `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt` — stubs for REQ-CONFLICT-01 (preemption, re-queue, priority)
- [ ] `src/test/kotlin/com/anjo/effect/EffectRendererTest.kt` — stubs for REQ-EFFECT-01 (all 4 effects)
- [ ] `src/test/kotlin/com/anjo/routing/ScheduleRouteTest.kt` — stubs for CRUD API + input validation
- [ ] `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` — H2 in-memory fixture for repository tests
- [ ] Test fixture: in-memory H2 DB helper (not file-mode) for test isolation

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| MAX7219 `setBrightness()` produces visible hardware fade | REQ-EFFECT-01 | Real SPI device required | Run app on Pi, POST /api/text with `effect=fade`, observe LED brightness ramp |
| MAX7219 `setBrightness()` blink causes visible on/off | REQ-EFFECT-01 | Real SPI device required | Run app on Pi, POST /api/text with `effect=blink`, observe LED alternation |
| H2 schedule data survives process restart | REQ-SCHED-01 | File system state across JVM restarts | POST schedule, kill process, restart, GET /api/schedule — entry persists |
| Cron schedule fires at wall-clock time on Pi | REQ-SCHED-01 | Real-time scheduling on device | Schedule cron "*/1 * * * *" (every minute), wait 2 cycles, observe display |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING (❌ W0) references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending


