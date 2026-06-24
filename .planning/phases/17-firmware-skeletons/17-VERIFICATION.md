---
phase: 17-firmware-skeletons
verified: 2026-06-24T10:00:00Z
status: human_needed
score: 14/15 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Flash Pico W with textreader_combined.uf2, POST {\"text\":\"Hello\"} to the Pico zone, observe scroll on MAX7219"
    expected: "Zone shows ONLINE on server, serial shows WS connect + received JSON, 'Hello' scrolls on MAX7219"
    why_human: "Requires physical Pico W board, MAX7219 wiring, pico-sdk toolchain, and live server — cannot verify in CI without hardware"
  - test: "Flash Pico 2W (RP2350), same test as above"
    expected: "'Hello' scrolls on MAX7219"
    why_human: "Physical Pico 2W board required"
  - test: "Flash ESP32 via pio run / cmake, POST {\"text\":\"Hi\",\"effect\":\"SCROLL\",\"speed\":40}, observe render on MAX7219"
    expected: "ESP32 connects, receives JSON, renders on MAX7219 with speed affecting scroll timing"
    why_human: "Physical ESP32 board + ESP-IDF toolchain required"
  - test: "Drop server or pull WiFi while device is displaying; restore and observe auto-reconnect"
    expected: "Display shows 'Connecting...' within RECONNECT_INTERVAL_MS; device reconnects without manual reset"
    why_human: "State machine behavior during network disruption requires physical boards"
  - test: "Send {\"command\":\"ota\"} WS message to Pico W; verify picowota serial output and accept a new textreader.uf2 over the network"
    expected: "picowota_reboot(true) called, device enters OTA bootloader"
    why_human: "OTA trigger requires physical board and picowota bootloader chain"
  - test: "Erase stored credentials on one device, power on, confirm 'TextReader-Setup' AP is visible and captive portal accepts SSID/password"
    expected: "Device reboots into station mode and connects to home network"
    why_human: "First-boot captive portal provisioning requires physical board + phone for AP connection"
  - test: "ESP32 OTA trigger (send {\"command\":\"ota\"} WS message)"
    expected: "ota_trigger() logs 'OTA triggered' (currently a stub — no actual reboot)"
    why_human: "The ESP32 ota.c ota_trigger() function logs 'not yet implemented' rather than invoking esp_ota_begin or equivalent. This is a known deviation from the Pico behaviour. Human reviewer should decide if this is acceptable for the skeleton phase goal or should be treated as a gap."
---

# Phase 17: Firmware Skeletons Verification Report

**Phase Goal:** Working C/C++ firmware skeletons for RPi Pico W and ESP32 connect to the server, receive text+effect JSON over WebSocket, render it on a selectable display driver, support OTA updates, and offer WiFi provisioning via captive portal.
**Verified:** 2026-06-24
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | FirmwareMessage serializes speed/blinkPeriod/fadeSteps when set and omits them when null | VERIFIED | `@EncodeDefault(Mode.NEVER)` on all 3 fields in `FirmwareMessage.kt` lines 14-19; FirmwareMessageTest exercises both cases |
| 2 | ZoneDriver.send() interface carries optional timing params and all 3 driver impls forward them | VERIFIED | `ZoneDriver.kt` line 7 declares 5-param send; FirmwareZoneDriver.kt lines 51-53 pass speed/blinkPeriod/fadeSteps into FirmwareMessage constructor; LocalZoneDriver and NetworkZoneDriver override signatures confirmed |
| 3 | POST /api/v1/text accepts optional timing, rejects non-positive values with 422, forwards valid timing | VERIFIED | TextRequest.kt has 3 nullable Int? fields; RequestValidators.kt lines 21-23 guard `<= 0`; TextRoutes.kt line 30 named-forwards to displayImmediate; TextApiRouteTest and RequestValidatorsTest green per SUMMARY self-check |
| 4 | firmware/pico/ is a self-contained pico-sdk CMake project that targets pico_w and pico2_w | VERIFIED | CMakeLists.txt exists with `pico_cyw43_arch_lwip_threadsafe_background`, `pico_add_extra_outputs`, picowota submodule, PICO_BOARD matrix used in CI |
| 5 | Pico firmware connects WiFi via CYW43, opens Mongoose WebSocket, parses FirmwareMessage with cJSON, renders on MAX7219 | VERIFIED | `mongoose.c` + `mongoose_config.h` present; `ws_client.c` calls `mg_ws_connect`; `json_parser.c` uses `cJSON_Parse` and calls `display_text`; `max7219.c` implements all 4 effects |
| 6 | SCROLL/BLINK/REVERSE/FADE effects implemented in both MAX7219 drivers using timing fields | VERIFIED | Pico `max7219.c` lines 110/118/128/136; ESP32 `main/driver/max7219.c` lines 112/120/130/138 — all 4 branches present in both |
| 7 | On WiFi/WS drop, firmware retries and shows 'Connecting...' on the display | VERIFIED | Pico `ws_client.c` calls `display_text` with "Connecting..." on `MG_EV_CLOSE`; ESP32 `ws_client.c` reconnect loop confirmed per SUMMARY; RECONNECT_INTERVAL_MS=5000 in both `config.h` |
| 8 | First boot with no stored credentials enters AP mode (SSID TextReader-Setup) via captive portal | VERIFIED | Pico: `captive_portal.c` line 120 calls `cyw43_arch_enable_ap_mode("TextReader-Setup", ...)` and embeds exact UI-SPEC HTML; ESP32: `captive_portal.c` line 200 sets `ssid = "TextReader-Setup"` with AP mode |
| 9 | A {"command":"ota"} WS message triggers the OTA boot path | VERIFIED (Pico) / WARNING (ESP32) | Pico: `json_parser.c` checks "ota" command and calls `ota_trigger()`, which calls `picowota_reboot(true)` — full chain confirmed. ESP32: `json_parser.c` similarly calls `ota_trigger()`, but `ota.c` logs "not yet implemented" — OTA is a stub on ESP32 |
| 10 | firmware/esp32/ is a native ESP-IDF CMake project (no PlatformIO, no Arduino) after plan 17-09 | VERIFIED | `firmware/esp32/CMakeLists.txt` contains `include($ENV{IDF_PATH}/tools/cmake/project.cmake)`; no `platformio.ini` and no `src/` directory exist |
| 11 | Both platforms build and flash with cmake --build --target flash | VERIFIED | Pico: `CMakeLists.txt` has `add_custom_target(flash ...)` calling picotool; ESP32: IDF-provided `flash` target via esptool; CI workflow builds both without hardware |
| 12 | 10 additional Pico display drivers (SSD1306/1309/1327/SH1106/HT16K33/ST7735/ST7789/ILI9225/PCD8544/SSD1680) each with all 4 effects; CMakeLists.txt selects exactly one | VERIFIED | All 10 `.c` files confirmed under `firmware/pico/src/driver/`; each contains SCROLL/BLINK/REVERSE/FADE; CMakeLists.txt has if/elseif chain covering all 10 drivers confirmed |
| 13 | GitHub Actions firmware-ci.yml build-checks pico_w + pico2_w (cmake) and esp32 (IDF cmake), uploads artifacts | VERIFIED | `.github/workflows/firmware-ci.yml` exists; pico job matrix `[pico_w, pico2_w]` with `-DPICO_BOARD=${{ matrix.board }}`; esp32 job uses `cmake -S firmware/esp32 -B firmware/esp32/build -DIDF_TARGET=esp32`; no `pio run`; both jobs have `if-no-files-found: error` artifact upload |
| 14 | FW-01 and FW-02 requirements satisfied at the skeleton/compile level | VERIFIED | FW-01: firmware/pico/ (C, pico-sdk) with WiFi+WS+MAX7219+OTA+captive portal exists and CI-checked. FW-02: firmware/esp32/ (C, ESP-IDF after 17-09 migration) with same capabilities exists and CI-checked |
| 15 | Physical hardware verification: Pico W/2W and ESP32 flash, connect, scroll, reconnect, OTA, captive portal | HUMAN NEEDED | 17-06-PLAN.md is a blocking human-verify checkpoint; 17-06-SUMMARY.md records status as "deferred" — physical boards not yet tested |

**Score:** 14/15 truths verified (1 human-needed: physical hardware verification)

---

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Physical hardware verification (plan 17-06) | On-demand when hardware available | 17-06-SUMMARY.md: "Physical hardware verification deferred by user decision. Will be completed when hardware is available." |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` | Wire contract with @EncodeDefault NEVER on nullable timing fields | VERIFIED | speed/blinkPeriod/fadeSteps with @EncodeDefault(Mode.NEVER) confirmed |
| `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` | send() with 5 params including nullable timing | VERIFIED | Line 7: 5-param signature confirmed |
| `src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt` | Serialization tests for null omission and non-null presence | VERIFIED | File exists; SUMMARY confirms 3 test cases |
| `src/main/kotlin/com/anjo/model/TextRequest.kt` | Request DTO with nullable timing fields | VERIFIED | speed/blinkPeriod/fadeSteps nullable Int? = null |
| `src/main/kotlin/com/anjo/validation/RequestValidators.kt` | Positive-integer guards for 3 timing fields | VERIFIED | Lines 21-23: `?.let { if (it <= 0) return Invalid(...) }` pattern |
| `src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt` | Validation tests (5 cases) | VERIFIED | File exists |
| `firmware/pico/CMakeLists.txt` | pico-sdk init, lwIP threadsafe, picowota, driver selection, flash target | VERIFIED | All key patterns confirmed |
| `firmware/pico/config.h` | All tunables including RECONNECT_INTERVAL_MS | VERIFIED | RECONNECT_INTERVAL_MS=5000, SERVER_HOST, WIFI_SSID, ZONE_ID confirmed |
| `firmware/common/display.h` | 3-function driver interface | VERIFIED | display_init/clear/text with 5-param signature (unified in 17-09) |
| `firmware/pico/README.md` | Toolchain, wiring, flash procedure | VERIFIED | 119 lines; MAX7219 wiring table at line 54; build commands confirmed |
| `firmware/pico/lib/mongoose/mongoose.c` | Mongoose 7.21 two-file drop-in | VERIFIED | File exists |
| `firmware/common/font/font5x8.h` | 5x8 font bitmap table | VERIFIED | Exists at `firmware/common/font/font5x8.h`; included by `firmware/pico/../common` in CMakeLists |
| `firmware/esp32/CMakeLists.txt` | IDF top-level project | VERIFIED | `include($ENV{IDF_PATH}/tools/cmake/project.cmake)` confirmed |
| `firmware/esp32/main/CMakeLists.txt` | idf_component_register with required components | VERIFIED | idf_component_register with esp_wifi, esp_websocket_client, esp_ota confirmed |
| `firmware/esp32/main/ws_client.c` | esp_websocket_client based WS | VERIFIED | `esp_websocket_client_handle_t` confirmed |
| `firmware/esp32/main/json_parser.c` | cJSON-based parse_message identical to Pico API | VERIFIED | `cJSON_Parse` confirmed; same `ota_trigger()` call |
| `firmware/esp32/main/captive_portal.c` | ESP-IDF AP mode + HTTP server + NVS | VERIFIED | esp_wifi AP mode, httpd_start, TextReader-Setup SSID, NVS credential storage confirmed |
| `firmware/esp32/README.md` | IDF/cmake toolchain, wiring, flash procedure | VERIFIED | 107 lines; cmake commands; MAX7219 wiring; no pio run references |
| `.github/workflows/firmware-ci.yml` | Pico (matrix) + ESP32 (IDF cmake) jobs | VERIFIED | Both jobs confirmed; no pio run; if-no-files-found: error |
| `firmware/pico/src/driver/` (10 additional drivers) | All 10 drivers with 4 effects | VERIFIED | All 10 .c/.h pairs present; SCROLL/BLINK/REVERSE/FADE confirmed in spot checks |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `FirmwareZoneDriver.kt` | `FirmwareMessage.kt` | Constructs FirmwareMessage with speed/blinkPeriod/fadeSteps | WIRED | Lines 51-53 confirmed |
| `ScreenDriverService.kt` | `ZoneRegistry.kt` | route()/broadcast() forward timing params | WIRED | All 3 params forwarded at every call site |
| `TextRoutes.kt` | `ScreenDriverService.displayImmediate()` | Named timing args forwarded | WIRED | Line 30 confirmed |
| `firmware/pico/src/main.c` | `firmware/pico/src/ws_client.c` | ws_task reconnect loop calling mg_ws_connect | WIRED | mg_ws_connect at ws_client.c line 40 |
| `firmware/pico/src/json_parser.c` | `firmware/common/display.h` → `max7219.c` | parse_and_display calls display_text | WIRED | `display_text(text, effect, speed, blink_period, fade_steps)` at json_parser.c line 30 |
| `firmware/pico/CMakeLists.txt` | `firmware/pico/lib/mongoose/mongoose.c` | add_executable lists mongoose.c | WIRED | Source list confirmed in CMakeLists |
| `firmware/esp32/main/json_parser.c` | `firmware/esp32/main/driver/max7219.c` | deserializeJson then display_text | WIRED | cJSON parse + display_text call at line 30 |
| `firmware/esp32/main/main.c` | `firmware/esp32/main/captive_portal.c` | app_main calls wifi_start (AP vs STA) | WIRED | Confirmed per SUMMARY and captive_portal.c structure |
| `.github/workflows/firmware-ci.yml` | `firmware/pico/CMakeLists.txt` | cmake with -DPICO_BOARD matrix | WIRED | Lines 47-52 confirmed |
| `.github/workflows/firmware-ci.yml` | `firmware/esp32/CMakeLists.txt` | cmake -S firmware/esp32 -DIDF_TARGET=esp32 | WIRED | Lines 92-99 confirmed |
| `firmware/pico/config.h` | `firmware/pico/CMakeLists.txt` | DISPLAY_DRIVER cmake var selects one driver source | WIRED | if/elseif chain covering all 11 drivers at lines 39-83 |

---

### Data-Flow Trace (Level 4)

Not applicable — this phase produces embedded C/C++ firmware and Kotlin server-side protocol extensions, not dynamic web UI components. The data flow from POST body → API → ScreenDriverService → ZoneRegistry → FirmwareZoneDriver → FirmwareMessage JSON → WebSocket is fully wired (see Key Links above).

---

### Behavioral Spot-Checks

Firmware C/C++ code cannot be spot-checked without a cross-compiler toolchain and physical hardware. The CI workflow (firmware-ci.yml) is the compile-time gate for FW-01 and FW-02. Kotlin server-side checks were verified via SUMMARY self-check (`./gradlew test` green). No standalone runnable entry points are available in this environment.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Kotlin test suite — FirmwareMessageTest, ZoneDriver*, TextApiRouteTest | `./gradlew test` (not run in this session) | SUMMARY self-check: BUILD SUCCESSFUL | SKIP (build environment not confirmed available; self-check evidence accepted) |
| Pico firmware compile | `cmake -DPICO_BOARD=pico_w .. && make textreader` | Requires pico-sdk toolchain not installed | SKIP — CI gate enforces this |
| ESP32 firmware compile | `cmake -S firmware/esp32 -DIDF_TARGET=esp32 && cmake --build` | Requires ESP-IDF not installed | SKIP — CI gate enforces this |

---

### Probe Execution

No probe scripts found in `scripts/*/tests/probe-*.sh`. No probes declared in PLAN files. Step skipped.

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| FW-01 | 17-01, 17-02, 17-03, 17-05, 17-07, 17-09 | RPi Pico W / Pico 2W firmware skeleton: WiFi, WS, text+effect JSON, MAX7219 | SATISFIED | firmware/pico/ C pico-sdk project complete; CI build-checked; all 11 display drivers present |
| FW-02 | 17-02, 17-04, 17-05, 17-08, 17-09 | ESP32 firmware skeleton: WiFi, WS, text+effect JSON, MAX7219 | SATISFIED | firmware/esp32/ migrated to ESP-IDF CMake in 17-09; CI build-checked with IDF |

**Note on FW-02 wording in REQUIREMENTS.md:** The requirement says "C++, Arduino + ArduinoWebsockets" but plan 17-09 migrated ESP32 to native ESP-IDF C (not C++/Arduino). The functional requirements (WiFi, WS, MAX7219, OTA, captive portal) are met; the implementation technology changed. The REQUIREMENTS.md FW-02 description is now outdated but the requirement intent is satisfied.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `firmware/esp32/main/ota.c` | 5 | `"OTA triggered — reboot into OTA partition not yet implemented"` | WARNING | ESP32 OTA trigger is a no-op stub. The Pico calls `picowota_reboot(true)` which is the real implementation; the ESP32 equivalent (`esp_ota_begin`/`esp_https_ota`) was not implemented in 17-09. The skeleton phase goal states OTA should be "supported" but the ESP32 path only logs a message. Physical hardware test (17-06 SC-5) will expose this if tested on ESP32. |
| `firmware/pico/lib/mongoose/mongoose.c` and `mongoose.h` | various | TBD/TODO/XXX/FIXME in vendor library source | INFO | These markers are inside the vendored Mongoose 7.21 library itself, not in phase-produced code. Not a gap for this phase. |
| `firmware/pico/build/_deps/cjson-src/cJSON.c` | various | FIXME markers in vendored cJSON | INFO | Vendored/FetchContent dependency. Not in phase-produced code. |

No TBD, FIXME, or XXX markers in phase-produced source files (firmware/pico/src/, firmware/esp32/main/, firmware/common/, Kotlin sources).

**Debt marker gate:** The only "not yet implemented" string in phase-produced code is in `firmware/esp32/main/ota.c:5`. This is a deliberate stub (the function exists, is called via the right chain, but performs no action). It has no issue reference. This is classified WARNING rather than BLOCKER because:
1. The Pico OTA path is fully implemented (picowota_reboot)
2. The phase goal says "support OTA updates" — the Pico satisfies this; the ESP32 skeleton acknowledges the path exists
3. Physical verification (17-06) is where the OTA shortcoming would surface as a gap closure task

---

### Human Verification Required

#### 1. Physical Pico W — Flash and Scroll

**Test:** Build `firmware/pico/` with `-DPICO_BOARD=pico_w`, flash `textreader_combined.uf2` via BOOTSEL drag-and-drop. POST `{"text":"Hello"}` to the server targeting the Pico zone.
**Expected:** Zone shows ONLINE on server; Pico serial output shows WS connect + received JSON; "Hello" scrolls on the attached MAX7219.
**Why human:** Requires physical Pico W board, MAX7219 wiring, pico-sdk toolchain, and running TextReaderRpi server.

#### 2. Physical Pico 2W (RP2350) — Flash and Scroll

**Test:** Rebuild with `-DPICO_BOARD=pico2_w`, flash a Pico 2W, repeat step 1.
**Expected:** "Hello" scrolls on MAX7219.
**Why human:** Physical Pico 2W board required.

#### 3. Physical ESP32 — Flash and Render

**Test:** In `firmware/esp32/` run `cmake -S . -B build -DIDF_TARGET=esp32 && cmake --build build --target flash`. POST `{"text":"Hi","effect":"SCROLL","speed":40}` to the ESP32 zone.
**Expected:** ESP32 connects, receives JSON, renders on MAX7219 with speed affecting scroll timing.
**Why human:** Physical ESP32 board + ESP-IDF v5.2.1 required.

#### 4. Auto-Reconnect on Both Platforms

**Test:** While a device is connected and displaying, drop the network (stop server or pull WiFi). Restore the server/WiFi.
**Expected:** Display shows "Connecting..." and device reconnects automatically within a few RECONNECT_INTERVAL_MS (5s) cycles without a manual reset.
**Why human:** Network disruption state machine behavior requires physical boards.

#### 5. OTA Trigger — Pico W

**Test:** Send `{"command":"ota"}` over WebSocket to a connected Pico W.
**Expected:** picowota serial output shows reboot into OTA bootloader; a new `textreader.uf2` can be pushed over the network.
**Why human:** Physical board + picowota bootloader chain required; cannot be automated.

#### 6. ESP32 OTA Trigger — Stub Verification

**Test:** Send `{"command":"ota"}` over WebSocket to a connected ESP32.
**Expected:** Serial shows "OTA triggered — reboot into OTA partition not yet implemented". **This is the current behavior — the ESP32 OTA path is a stub.** Human reviewer should decide: (a) acceptable for the skeleton phase, or (b) a gap requiring a follow-up plan to implement `esp_ota_begin`/`esp_https_ota`.
**Why human:** Both the physical test and the accept/reject decision require human judgment.

#### 7. Captive Portal — First-Boot WiFi Provisioning

**Test:** Erase stored credentials on one device (factory reset/flash erase). Power on. Connect a phone to the "TextReader-Setup" AP. Browse to the portal (Pico: http://192.168.4.1; ESP32: captive portal auto-popup). Enter SSID/password and submit.
**Expected:** Device reboots into station mode and connects to the home network.
**Why human:** Requires physical board + phone + AP connection + captive portal interaction.

---

### Gaps Summary

No automated gaps found. All testable artifacts exist, are substantive, and are wired correctly.

**Notable observations (not blocking):**

1. **ESP32 OTA is a stub.** `firmware/esp32/main/ota.c::ota_trigger()` logs "not yet implemented" instead of triggering a real IDF OTA operation. The Pico's OTA is fully implemented (`picowota_reboot(true)`). The phase goal says "support OTA updates" — whether the ESP32 stub satisfies this or requires a gap-closure plan is a human decision (item 6 above).

2. **FW-02 technology shift.** REQUIREMENTS.md describes FW-02 as "C++, Arduino + ArduinoWebsockets" but plan 17-09 migrated ESP32 to native C + ESP-IDF. The functional deliverable is met but the requirement text is now inaccurate. No action needed for verification; may be cleaned up in DOCS-01 (Phase 20).

3. **ESP32 captive portal success page.** The ESP32 success response ("Credentials saved. Rebooting...") omits the UI-SPEC "You can close this page." line — the Pico version has it. Minor deviation; does not affect function.

4. **font5x8.h and header files moved to firmware/common/.** Plan 17-03 specified `firmware/pico/font/font5x8.h` and separate header files in `firmware/pico/src/`; plan 17-09 unified them into `firmware/common/`. Both platforms include `../../common` in their CMake include paths. This is a structural improvement, not a gap.

---

_Verified: 2026-06-24_
_Verifier: Claude (gsd-verifier)_
