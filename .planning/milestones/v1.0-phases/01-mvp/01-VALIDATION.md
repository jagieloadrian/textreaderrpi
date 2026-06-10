---
phase: 1
slug: mvp
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-10
---

# Phase 1 — Validation Strategy

> Per-phase validation contract — retroactively reconstructed from EXECUTION-SUMMARY.md and VERIFICATION.md artifacts.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest FunSpec + MockK |
| **Config file** | `build.gradle.kts` (JaCoCo + test configuration) |
| **Quick run command** | `./gradlew test --tests "com.anjo.*"` |
| **Full suite command** | `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` |
| **Estimated runtime** | ~30 seconds |

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
| 1-T1-route | 01 | 1 | MVP-01 | — | POST /api/v1/text returns 202 for valid input | integration | `./gradlew test --tests "*TextApiRoute*"` | ✅ | ✅ green |
| 1-T1-validation | 01 | 1 | MVP-02 | — | Blank text → 422; oversized text → 422; invalid effect → 4xx | integration | `./gradlew test --tests "*TextApiRoute*"` | ✅ | ✅ green |
| 1-T1-error-handling | 01 | 1 | MVP-01 | — | StatusPages returns structured error JSON on validation failure | integration | `./gradlew test --tests "*TextApiRoute*"` | ✅ | ✅ green |
| 1-T1-startup | 01 | 1 | MVP-01 | — | Application starts and loads config without errors | integration | `./gradlew test --tests "*ApplicationTest*"` | ✅ | ✅ green |
| 1-T3-coverage | 01 | 2 | MVP-03 | — | JaCoCo line coverage ≥ 70% | build gate | `./gradlew jacocoTestCoverageVerification` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No Wave 0 stubs needed — all tests existed at phase execution time.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| MAX7219 LED matrix renders scrolling text | MVP-01 | Real SPI hardware required | Run on Pi: POST /api/v1/text, observe LED matrix scrolling |

---

## Validation Audit 2026-06-10

| Metric | Count |
|--------|-------|
| Tasks mapped | 5 |
| COVERED | 4 |
| MANUAL-ONLY | 1 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only designation
- [x] Sampling continuity: all tasks have automated commands
- [x] No Wave 0 requirements (all tests pre-exist)
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** 2026-06-10 (retroactive — phase completed 2026-05-26)
