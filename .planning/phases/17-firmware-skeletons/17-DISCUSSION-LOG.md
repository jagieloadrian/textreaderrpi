# Phase 17: Firmware Skeletons — Discussion Log

**Date:** 2026-06-23
**Participants:** User + Claude

---

## Area 1: Pico W SDK/framework

**Question:** Pico W build framework — pico-sdk has no native WebSocket client.
**Options:** Arduino-Pico / Pure pico-sdk + C WS lib / pico-sdk + lwip hand-rolled
**Selected:** Pure pico-sdk + C WebSocket lib

**Follow-up — WebSocket library:**
**Options:** Mongoose / cWebSockets / Custom minimal WS over lwIP
**Selected:** Mongoose (Cesanta)

**Follow-up — JSON parsing:**
**Options:** cJSON / Mongoose built-in JSON / sscanf hand-parsed
**Selected:** cJSON (DaveGamble)

---

## Area 2: Effect Rendering Scope

**Question:** What should firmware do with the `effect` field?
**Options:** SCROLL only / SCROLL+BLINK / All 4 effects
**Selected:** All 4 effects

**Follow-up — Scroll speed:**
**Options:** Configurable in config.h / Speed embedded in message / Fixed hardcoded
**Selected:** Speed embedded in message (optional Int field in wire protocol)

**Note:** User clarified this requires server-side change; confirmed they want it extended in Phase 17.

**Follow-up — Blink/Fade timing:**
**Options:** Fixed defaults in config.h / Server sends period/steps in message
**Selected:** Server sends blinkPeriod + fadeSteps in message

**Follow-up — Wire protocol fields:**
**Options:** speed, blinkPeriod, fadeSteps (all recommended)
**Selected:** All three — speed: Int?, blinkPeriod: Int?, fadeSteps: Int?

---

## Area 3: Display Drivers + JSON Libraries

**Question (user-initiated):** Support multiple display drivers (ST7735, ST7789, SSD1306, ST7687S, ILI9225, SSD1327, SSD1309, e-paper, MAX7219, more) with compile-time selection via config file.

**Architecture question:**
**Options:** Preprocessor flags in config.h / CMake DISPLAY_DRIVER variable / Runtime registry
**Selected:** Preprocessor flags in config.h (`#define DISPLAY_DRIVER MAX7219`)

**Scope question — which drivers implemented in Phase 17:**
**Selected:** All listed + researcher to find additional ones (user said "implement all and research more")

**ESP32 display libraries:**
**User constraint:** Libraries must work for both ESP32 and Pico. This created tension with pure pico-sdk choice.

**Cross-platform resolution:**
**Options:** Switch Pico to Arduino-Pico / C HAL with platform backends / Separate codebases per platform
**User note:** "remember Pico 2 has RP2350, not RP2040. Can combine but make it configurable via file like screen drivers."
**Decision:** Keep pico-sdk for Pico; use `BOARD_TYPE` in config.h to select RP2040/RP2350/ESP32. Pico uses pico-sdk compatible C display libs; ESP32 uses Arduino (Adafruit GFX + specific libs). Driver interface unified via `display_init/clear/display_text`.

**Font selection:**
**User:** "Use ready-made fonts, must be compatible with given driver, configurable via file."
**Decision:** `#define FONT_TYPE` in config.h. Adafruit GFX fonts for Arduino builds, embedded 5x8 bitmap header for pico-sdk builds.

---

## Area 4: Reconnect Behavior

**Question:** Reconnect strategy on WiFi/WS drop.
**Options:** Fixed 5s + blank / Exponential backoff / Fixed retry + show "Connecting..."
**Selected:** Fixed retry + show "Connecting..." on display

---

## Area 5: Wire Protocol Backward Compatibility

**Question:** How does `POST /api/v1/text` expose new timing fields?
**Options:** Optional body fields ignored for non-firmware zones / Separate endpoint / Zone-level config
**Selected:** Optional body fields, ignored for non-firmware zones

---

## Area 6: Display Driver Interface Contract

**Question:** What must every driver implement?
**Options:** init()+clear()+display_text(char*,effect,speed) / init()+clear()+push_columns() / init()+render(FirmwareMessage*)
**Selected:** init() + clear() + display_text(char*, effect, speed_ms)

---

## Area 7: Repo Structure

**Question:** Where does firmware live?
**Options:** firmware/pico + firmware/esp32 in mono-repo / Separate repos / Git submodule
**Selected:** firmware/pico/ + firmware/esp32/ in mono-repo, Gradle ignores them

---

## Area 8: Font/Charset

**Question:** How does firmware convert text to pixels?
**Options:** Embedded 5x8 bitmap font / Server sends pre-rendered bitmaps / Arduino/library font
**User response:** Use ready-made fonts, compatible with driver, configurable via file.
**Decision:** config.h `FONT_TYPE` define; driver-appropriate font per platform.

---

## Area 9: Build CI

**Question:** GitHub Actions or local build only?
**User response:** Add GitHub Actions build-check; automate creating flashable package (uf2 for Pico) so user can flash without building libs locally.
**Decision:** CI build-check for both projects + downloadable release artifacts (.uf2, .bin).

---

## Claude's Discretion Items

- Exact CMake FetchContent versions for Mongoose and cJSON
- PlatformIO board identifiers for ESP32 variants
- Wiring diagram format in READMEs
- RP2350 vs RP2040 CMake PICO_BOARD differences
- Additional display drivers to add (researcher task)

## Deferred Ideas

- OTA firmware update
- WiFi provisioning (captive portal)
- Kotlin Native firmware (out of scope, KT-44498 unresolved)
