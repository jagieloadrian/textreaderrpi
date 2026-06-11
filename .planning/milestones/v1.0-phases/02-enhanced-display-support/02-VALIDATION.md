---
phase: 2
slug: enhanced-display-support
status: complete
nyquist_compliant: false
wave_0_complete: true
created: 2026-06-10
---

# Phase 2 — Validation Strategy

> Per-phase validation contract — retroactively reconstructed from EXECUTION-SUMMARY-WAVE1/2/3.md artifacts.

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
| 2-01-driver-interface | 02-01 | 1 | D-01/02 | — | DisplayDriver hybrid interface: clear/write/status methods present and correct | unit | `./gradlew test --tests "*Max7219Matrix*"` | ✅ | ✅ green |
| 2-02-lcd-driver | 02-02 | 1 | D-03 | — | LcdDisplay implements all DisplayDriver methods; 16x2 text handling correct | unit | `./gradlew test --tests "*LcdDisplay*"` | ✅ | ✅ green |
| 2-03-config-selection | 02-03 | 1 | D-03/04 | — | DisplaySelectionService selects correct driver from config; runtime switch queued | unit | `./gradlew test --tests "*DisplaySelectionService*"` | ✅ | ✅ green |
| 2-04-html-pages | 02-04 | 2 | D-11/12 | — | GET /, /status, /settings/display return 200 with HTML content | integration | `./gradlew test --tests "*WebAndDisplay*"` | ✅ | ✅ green |
| 2-05-display-api | 02-05 | 2 | D-03/04 | — | GET /api/v1/display/status returns JSON; POST /api/v1/display/select validates type | integration | `./gradlew test --tests "*WebAndDisplay*"` | ✅ | ✅ green |
| 2-06-driver-integration | 02-07 | 3 | D-01/03 | — | Runtime switching across MAX7219, LCD, OLED at service level | integration | `./gradlew test --tests "*DriverIntegration*"` | ✅ | ✅ green |
| 2-07-oled-driver | 02-07 | 3 | D-03 | — | OledDisplay implements DisplayDriver; I2C SSD1306 init and write methods | unit | `./gradlew test --tests "*OledDisplay*"` | ✅ | ✅ green |
| 2-08-json-404 | 02-05 | 2 | D-13 | — | Non-existent API route (/api/v1/nonexistent) returns JSON 404 with ERR_404 | integration | `./gradlew test --tests "*WebAndDisplay*"` | ✅ | ✅ green |
| 2-09-html-404 | 02-05 | 2 | D-13 | — | Non-existent browser route with Accept:text/html returns HTML 404 error page | integration | `./gradlew test --tests "*WebAndDisplay*"` | ✅ xtest | ⚠️ ESCALATED |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · xtest = disabled pending bug fix*

---

## Wave 0 Requirements

Existing infrastructure covers all implemented phase requirements. Tests pre-existed at phase execution time.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| HTML 404/500 error page for unregistered browser routes | D-13 | **IMPLEMENTATION BUG**: Ktor 3.5.0 `swaggerUI(path="openapi")` with `OpenApiDocSource.Routing` intercepts unregistered GET paths and returns 200 instead of propagating to StatusPages 404 handler. `xtest` left in `WebAndDisplayRoutesTest.kt` as regression gate. | Fix: investigate Ktor routing catch-all; re-enable `xtest` after fix |
| Rendering latency < 2s for first visible output | D-07 | Real hardware required | Run on Pi, POST text, time until LED/LCD/OLED shows output |
| Runtime switch waits for current message to finish | D-04 | Timing behavior, not unit-testable | Start display operation, immediately call display switch, verify new text queued correctly |
| MAX7219 / LCD / OLED hardware rendering correctness | D-02/03 | Real hardware required | Run all three display types on Pi, verify text renders correctly |

---

## Validation Audit 2026-06-10

| Metric | Count |
|--------|-------|
| Tasks mapped | 9 |
| COVERED (green) | 8 |
| ESCALATED (implementation bug) | 1 |
| MANUAL-ONLY | 4 |
| Gaps found | 1 (HTML 404 error page) |
| Resolved | 1 (JSON 404 added as new test) |
| Escalated | 1 (HTML 404 — Ktor SwaggerUI routing bug) |

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only designation
- [x] Sampling continuity: all automated tasks have verify commands
- [x] 1 escalated item: HTML error page behavior blocked by Ktor SwaggerUI catch-all bug
- [x] Feedback latency < 60s
- [ ] `nyquist_compliant: false` — 1 escalated item pending implementation bug fix

**Approval:** 2026-06-10 (retroactive — phase completed 2026-05-26)
**Blocking bug:** `swaggerUI` in Ktor 3.5.0 returns 200 for unknown GET paths. Track fix for v2.0.
