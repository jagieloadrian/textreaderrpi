---
phase: 6
slug: max7219-hardware-fix
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-12
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (FunSpec) + JUnit 5 runner |
| **Config file** | none — Kotest auto-detected via `kotest-runner-junit5-jvm` |
| **Quick run command** | `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest"` |
| **Full suite command** | `./gradlew test jacocoTestReport` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.driver.*"`
- **After every plan wave:** Run `./gradlew test jacocoTestReport`
- **Before `/gsd-verify-work`:** Full suite must be green + JaCoCo ≥70%
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 6-01-01 | 01 | 1 | HW-01 | — | N/A | unit | `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest"` | ✅ expand existing | ⬜ pending |
| 6-01-02 | 01 | 1 | HW-01 | — | N/A | unit | `./gradlew test --tests "com.anjo.driver.Max7219MatrixTest"` | ✅ expand existing | ⬜ pending |
| 6-02-01 | 02 | 2 | HW-02 | — | N/A | regression | `./gradlew test` | ✅ existing | ⬜ pending |
| 6-02-02 | 02 | 2 | HW-02 | — | N/A | unit | `./gradlew test --tests "com.anjo.driver.OledDisplayTest"` | ✅ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Two new test cases in `src/test/kotlin/com/anjo/driver/Max7219MatrixTest.kt` — required by D-08: byte-exact test for `buildPacket` with 'A' at offset=0 numDevices=1, and structural test for numDevices=2 column-to-SPI-slot assignment

*`AbstractDisplayDriver.kt` is a new file but logic is covered by existing driver tests — no additional stubs needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scrolling text moves left-to-right on physical 2× MAX7219 chain | HW-01 | Requires Pi hardware | Flash firmware, observe scroll direction on physical device |
| Single LED probe confirms SPI packet direction | HW-01 | Requires Pi hardware + oscilloscope/LED | Wire first module only, send known pattern, verify correct column lights |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
