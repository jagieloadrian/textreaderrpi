---
plan: 05-10
status: complete
completed: 2026-05-28
key-files:
  created:
    - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
    - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
    - src/test/kotlin/com/anjo/effect/EffectRendererTest.kt
    - src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt
---

# Plan 05-10 Summary: Timing-Accurate Behavior Tests

## What Was Built

Four test files implementing virtual-time behavioral tests using `kotlinx-coroutines-test`. No `Thread.sleep()` anywhere in test sources. JaCoCo line coverage gate (≥70%) passes with the full suite.

## Changes Made

### `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt`
5 virtual-time tests using `StandardTestDispatcher(testScheduler)` injected into `SchedulerService` scope:
- `"one-shot schedule fires display after target delay"` — fires at 60,001ms virtual time
- `"one-shot does not fire before target time"` — no fire at 30,000ms
- `"recurring schedule fires multiple times at interval"` — fires exactly 3× at 5m intervals
- `"recurring schedule stops at maxRuns"` — atMost=2 fires across 10 minutes
- `"cancel stops a recurring schedule before first fire"` — cancel at 30s, advance past 2m, 0 fires

### `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt`
Tests for priority ordering and conflict semantics:
- `"ad-hoc displayImmediate cancels running scheduled job"` — verifies `currentDisplayJob` mechanism
- `"priority ordering: higher priority schedule fires before lower"` — sort verification via start()
- `"same priority: earlier createdAt fires first"` — tie-break verification

### `src/test/kotlin/com/anjo/effect/EffectRendererTest.kt`
6 tests for each effect calling correct DisplayDriver methods:
- `"ScrollEffect calls scrollText with original text"` — verify(scrollText("hello"))
- `"ReverseEffect calls scrollText with reversed text"` — verify(scrollText("olleh"))
- `"BlinkEffect calls setBrightness(0) and setBrightness(15) alternately"` — coVerify(atLeast=2)
- `"BlinkEffect restores brightness to 15 after completion"` — final setBrightness(15)
- `"FadeEffect ramps brightness from 0 to 15"` — capture list verification
- `"FadeEffect calls scrollText after fade-in"` — verify(scrollText("fade"))

### `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt`
4 tests for `retryWithBackoff`:
- `"retryWithBackoff succeeds on first attempt if block does not throw"` — no retry
- `"retryWithBackoff retries up to maxAttempts on failure"` — succeeds on 3rd try
- `"retryWithBackoff throws after maxAttempts exceeded"` — exhausted retries
- `"retryWithBackoff delays increase exponentially"` — delay escalation

## Deviations

None. All tests use `runTest { }` with `testScheduler.advanceTimeBy()` for virtual time. `Thread.sleep` is absent in all test sources (`grep -r "Thread.sleep" src/test/` → empty).

## Test Results

```
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification → BUILD SUCCESSFUL
grep -r Thread.sleep src/test/ → (no matches)
```

All 19 test classes (72+ tests) pass, no Thread.sleep, JaCoCo ≥70% line coverage gate passes.

## Self-Check: PASSED
- ✅ SchedulerServiceTest.kt exists with `"one-shot schedule fires display after target delay"`
- ✅ SchedulerServiceTest.kt contains `"recurring schedule fires multiple times at interval"`
- ✅ SchedulerServiceTest.kt contains `"recurring schedule stops at maxRuns"`
- ✅ SchedulerServiceTest.kt contains `testScheduler.advanceTimeBy(` (virtual time)
- ✅ No `Thread.sleep(` in any test file
- ✅ ConflictPolicyTest.kt exists with priority and preemption tests
- ✅ EffectRendererTest.kt exists with all 6 effect tests
- ✅ ScreenDriverRecoveryTest.kt exists with retryWithBackoff tests
- ✅ `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` → BUILD SUCCESSFUL

