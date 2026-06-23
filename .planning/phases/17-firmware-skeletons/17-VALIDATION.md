---
phase: 17
slug: firmware-skeletons
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-24
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest (FunSpec) — `should` convention |
| **Config file** | `src/test/kotlin/com/anjo/ProjectConfig.kt` — `coroutineTestScope = false` |
| **Quick run command** | `./gradlew test --tests "com.anjo.model.*" --tests "com.anjo.validation.*" --tests "com.anjo.zone.*"` |
| **Full suite command** | `./gradlew test jacocoTestCoverageVerification` |
| **Estimated runtime** | ~35 seconds |

**Note:** Firmware C/C++ code (firmware/pico/, firmware/esp32/) is not testable via the Kotlin/Gradle suite. Firmware correctness is verified by (a) CMake/PlatformIO compile checks in GitHub Actions CI, and (b) physical device testing (human-verify checkpoint).

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "com.anjo.model.*" --tests "com.anjo.validation.*" --tests "com.anjo.zone.*"`
- **After every plan wave:** `./gradlew test jacocoTestCoverageVerification`
- **Before `/gsd-verify-work`:** Full suite green + firmware CI build check passing
- **Max feedback latency:** ~35 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 17-01-01 | P01 | 0 | FW-01 | — | N/A | unit | `./gradlew test --tests "com.anjo.model.FirmwareMessageTest"` | ❌ W0 | ⬜ pending |
| 17-01-02 | P01 | 0 | FW-01 | T-17-05 | Null timing fields omitted from JSON (no `"speed":null`) | unit | `./gradlew test --tests "com.anjo.model.FirmwareMessageTest"` | ❌ W0 | ⬜ pending |
| 17-02-01 | P02 | 1 | FW-01 | T-17-01 | speed/blinkPeriod/fadeSteps positive-only guard in RequestValidators | unit | `./gradlew test --tests "com.anjo.validation.RequestValidatorsTest"` | ❌ W0 | ⬜ pending |
| 17-02-02 | P02 | 1 | FW-01+02 | — | N/A | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest"` | ✅ extend | ⬜ pending |
| 17-03-01 | P03 | 1 | FW-01+02 | — | N/A | unit | `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest"` | ✅ extend | ⬜ pending |
| 17-04-01 | P04 | 2 | FW-01 | — | N/A | CI build | GitHub Actions: cmake pico_w build | ❌ W0 | ⬜ pending |
| 17-04-02 | P04 | 2 | FW-01 | — | N/A | CI build | GitHub Actions: cmake pico2_w build | ❌ W0 | ⬜ pending |
| 17-05-01 | P05 | 2 | FW-02 | — | N/A | CI build | GitHub Actions: pio run | ❌ W0 | ⬜ pending |
| 17-06-01 | P06 | 3 | FW-01 | — | Physical flash only | manual | — | Human-verify checkpoint | ⬜ pending |
| 17-07-01 | P06 | 3 | FW-02 | — | Physical flash only | manual | — | Human-verify checkpoint | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt` — FirmwareMessage serialization: null timing fields omitted; non-null fields included; FW-01
- [ ] `.github/workflows/firmware-ci.yml` — build-check for pico_w, pico2_w (cmake), and esp32 (pio run); FW-01, FW-02
- [ ] Create `src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt` (new file — does not exist yet) — speed ≤ 0 → 422, blinkPeriod ≤ 0 → 422, fadeSteps ≤ 0 → 422; FW-01+02
- [ ] Extend `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — send() populates speed/blinkPeriod/fadeSteps in serialized JSON; FW-01+02

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flash `firmware/pico/build/textreader_combined.uf2` onto Pico W; verify WebSocket connect + "Hello" scroll on MAX7219 | FW-01 SC-1 | Requires physical Pico W + MAX7219 hardware | Set WIFI_SSID/PASS/SERVER_HOST in config.h; flash; observe serial output and display |
| Flash `firmware/pico/` onto Pico 2W (RP2350); same verification | FW-01 SC-1 | Requires physical Pico 2W hardware | Same as above but with `-DPICO_BOARD=pico2_w` build |
| Reconnect test: drop WiFi/server; verify device reconnects without hang | FW-01+02 SC-4 | Requires hardware | Pull network cable; observe "Connecting..." on display; restore; verify reconnect |
| OTA update: send `{"command":"ota"}` via WS; verify device reboots into OTA mode | FW-01+02 SC-5 | Requires hardware + OTA push setup | Send WS command; observe picowota serial output; push new .uf2 |
| First-boot captive portal: factory reset; verify AP SSID `TextReader-Setup` visible; enter credentials; verify STA-mode reboot | FW-01+02 SC-6 | Requires hardware | Erase flash; power on; scan WiFi on phone; complete form; verify reboot and connect |
| Build and flash `firmware/esp32/` via PlatformIO onto ESP32; verify WS connect + MAX7219 render | FW-02 SC-2 | Requires physical ESP32 + MAX7219 hardware | `pio run -t upload`; observe serial + display |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 35s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
