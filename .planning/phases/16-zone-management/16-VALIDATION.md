---
phase: 16
slug: zone-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-23
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin test (JUnit 5 via Ktor testApplication) |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "*.ZoneRoutesTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*.ZoneRoutesTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 1 | ZONE-09 | — | N/A | unit | `./gradlew test --tests "*.ZoneRoutesTest"` | ✅ | ⬜ pending |
| 16-01-02 | 01 | 1 | ZONE-09 | — | 422 for local hardware types | unit | `./gradlew test --tests "*.ZoneRoutesTest"` | ✅ | ⬜ pending |
| 16-02-01 | 02 | 2 | ZONE-10 | — | N/A | integration | `./gradlew test --tests "*.FirmwareZoneDriverTest"` | ❌ W0 | ⬜ pending |
| 16-02-02 | 02 | 2 | ZONE-10 | — | Graceful error on disconnect | integration | `./gradlew test --tests "*.FirmwareZoneDriverTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/textrpi/FirmwareZoneDriverTest.kt` — stubs for ZONE-10 WebSocket registration and disconnect behavior
- [ ] Update `ZoneRoutesTest` — add name/type fields to existing test bodies

*Existing Ktor testApplication infrastructure covers routing and migration tests.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pico/ESP32 text appears in serial monitor | ZONE-10 | Requires physical hardware | Connect device, open `GET /ws/zone/{id}`, send text via zone API, verify in serial monitor |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
