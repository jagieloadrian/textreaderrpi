---
phase: 7
slug: scheduler-schema-stabilisation
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-14
audited: 2026-06-15
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 / Kotest FunSpec (Gradle) |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "*.ConflictPolicyTest" --tests "*.SchedulerServiceTest" --tests "*.ScheduleRepositoryTest" --tests "*.ScheduleRoutesTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | SCHED-01 | T-07-06 | SKIP_NEW drops ad-hoc request when display busy (accepted=false); skipped fires do not count toward maxRuns | unit | `./gradlew test --tests "*.ConflictPolicyTest" --tests "*.SchedulerServiceTest"` | ✅ | ✅ green |
| 07-01-02 | 01/02 | 1/2 | SCHED-02 | — | ONESHOT excluded from findAllActive when firedAt is set; updateFiredAtAndDone sets both fields atomically | integration | `./gradlew test --tests "*.ScheduleRepositoryTest"` | ✅ | ✅ green |
| 07-01-03 | 02/03 | 2/3 | SCHED-03 | T-07-04 | Invalid CRON expression rejected by RequestValidation with 422; no row persisted | integration | `./gradlew test --tests "*.ScheduleRoutesTest"` | ✅ | ✅ green |
| 07-01-04 | 01/03 | 1/3 | SCHED-04 | T-07-01/05 | webhookUrl and zoneId persist and read back through ScheduleRepository; invalid webhookUrl rejected with 422 | integration | `./gradlew test --tests "*.ScheduleRepositoryTest" --tests "*.ScheduleRoutesTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements (Flyway migrations auto-applied on startup; JUnit 5 / Kotest test suite already present; H2 in-memory DB for integration tests).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flyway migration applies cleanly on Pi hardware restart | SCHED-02 | Requires physical Raspberry Pi restart | Boot Pi, confirm no migration errors in log, confirm ONESHOT does not re-fire |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-15

---

## Validation Audit 2026-06-15

| Metric | Count |
|--------|-------|
| Gaps found | 4 |
| Resolved | 4 |
| Escalated | 0 |

**Findings:**
- SCHED-03 behavior corrected: original plan specified "persist ERROR row on invalid CRON via route handler" — this violated SRP (validation in routes). Fixed: CRON validation restored to `ScheduleValidators`, runs via Ktor RequestValidation plugin; invalid CRON rejected pre-persistence. `07-VALIDATION.md` updated to reflect actual behavior.
- All automated test commands corrected to match actual test class names.
- Status column updated from ⬜ pending → ✅ green for all 4 requirements.
- Comments removed from 4 test files per `skills.md` coding rules.
