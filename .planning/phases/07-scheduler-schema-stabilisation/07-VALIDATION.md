---
phase: 7
slug: scheduler-schema-stabilisation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-14
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 / Kotlin test (Gradle) |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "*.ScheduleRoutes*" --tests "*.SchedulerService*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | SCHED-01 | — | SKIP_NEW silently ignores second submission when display is busy | unit | `./gradlew test --tests "*.ScheduleRoutes*"` | ✅ | ⬜ pending |
| 07-01-02 | 01 | 1 | SCHED-02 | — | ONESHOT does not fire after restart if firedAt is set | unit | `./gradlew test --tests "*.SchedulerService*"` | ✅ | ⬜ pending |
| 07-01-03 | 01 | 1 | SCHED-03 | — | Invalid CRON row persisted with status=ERROR | unit | `./gradlew test --tests "*.ScheduleRoutes*"` | ✅ | ⬜ pending |
| 07-01-04 | 01 | 1 | SCHED-04 | — | webhookUrl and zoneId columns readable/writable | integration | `./gradlew test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements (Flyway migrations auto-applied on startup; JUnit 5 test suite already present).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flyway migration applies cleanly on Pi hardware restart | SCHED-02 | Requires physical Raspberry Pi restart | Boot Pi, confirm no migration errors in log, confirm ONESHOT does not re-fire |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
