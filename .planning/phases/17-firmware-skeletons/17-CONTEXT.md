# Phase 17: Firmware Skeletons - Context

**Gathered:** 2026-06-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Two standalone embedded firmware projects plus a minor server-side wire protocol extension:

1. **`firmware/pico/`** — C firmware for RPi Pico W (RP2040) and Pico 2W (RP2350) using pico-sdk + Mongoose WebSocket + cJSON. Connects to `ws://host:8080/ws/zone/{id}`, receives `FirmwareMessage` JSON, renders text+effect on a selected display driver.

2. **`firmware/esp32/`** — C++ firmware for ESP32 series using Arduino framework + ArduinoWebsockets + ArduinoJson. Same wire protocol, same display driver abstraction.

3. **Server-side change** — `FirmwareMessage` extended with optional timing fields (`speed`, `blinkPeriod`, `fadeSteps`). `POST /api/v1/text` body accepts these as optional parameters (ignored for local/network zones).

Both firmware projects live in the mono-repo alongside `src/`. Main Gradle build ignores `firmware/`. GitHub Actions provides build-check CI and produces flashable artifacts (`.uf2` for Pico, `.bin` for ESP32).

Requirements: FW-01 (Pico W / Pico 2W), FW-02 (ESP32).

</domain>

<decisions>
## Implementation Decisions

### Pico W / Pico 2W — Build Stack (FW-01)

- **D-01:** **SDK/framework:** Pure pico-sdk (CMake). No Arduino-Pico. Supports both RP2040 (Pico W) and RP2350 (Pico 2W) — board target selected via `BOARD_TYPE` in `config.h`.
- **D-02:** **WebSocket library:** Mongoose (Cesanta) — fetched via CMake `FetchContent`. Runs on pico-sdk's lwIP stack. No TLS required (home network).
- **D-03:** **JSON parsing:** cJSON (DaveGamble) — fetched via CMake `FetchContent`. MIT-licensed, single .c/.h pair.

### ESP32 — Build Stack (FW-02)

- **D-04:** **Framework:** Arduino + PlatformIO. WebSocket library: ArduinoWebsockets (explicit in FW-02). JSON: ArduinoJson.
- **D-05:** Display libraries: Adafruit GFX ecosystem (Adafruit SSD1306, Adafruit ST7789, GxEPD2 for e-paper, MD_MAX72XX for MAX7219) — via PlatformIO `lib_deps`. Selected at compile time; unused drivers are not compiled in.

### Display Driver Architecture (both platforms)

- **D-06:** **Compile-time selection:** `#define DISPLAY_DRIVER MAX7219` (or SSD1306, ST7789, etc.) in `config.h`. CMakeLists.txt / platformio.ini includes only the selected driver's source file.
- **D-07:** **Driver interface (3 functions):**
  ```c
  void display_init(void);
  void display_clear(void);
  void display_text(const char* text, const char* effect, int speed_ms);
  ```
  Effect logic (scroll animation, blink timer, fade ramp) lives inside each driver. Graphical TFTs render text using the configured font.
- **D-08:** **Drivers to implement** (researcher to enumerate full list and find additional ones):
  - MAX7219 (LED matrix via SPI) — reference implementation
  - SSD1306 (128x64 OLED, I2C/SPI)
  - SSD1309 (128x64 OLED, I2C, SSD1306-compatible)
  - SSD1327 (128x128 grayscale OLED)
  - ST7735 (128x160 color TFT, SPI)
  - ST7789 (240x240 color TFT, SPI)
  - ILI9225 (176x220 color TFT, SPI)
  - ST7687S (round TFT, SPI)
  - E-paper variants (SSD1680 2.13"/2.9", IL0373, UC8151 — researcher to verify/expand)
  - Researcher to find and add other common Pico/ESP32 display targets
- **D-09:** **Font selection:** `#define FONT_TYPE` in `config.h`. Use existing ready-made fonts compatible with the selected driver (Adafruit GFX fonts for Arduino builds; embedded 5x8 bitmap header for pico-sdk builds). Font must be compatible with the selected display driver.

### Effect Rendering (both platforms)

- **D-10:** **All 4 effects implemented:** SCROLL (column-shift animation), BLINK (on/off timer), REVERSE (column inversion), FADE (MAX7219 intensity register ramp; for graphical displays: brightness PWM or alpha blend approximation).
- **D-11:** **Timing from message:** `speed` (scroll interval ms, default 50), `blinkPeriod` (on/off cycle ms, default 500), `fadeSteps` (ramp steps, default 8). Firmware uses defaults if fields absent.

### Wire Protocol Extension (server-side)

- **D-12:** **Extended `FirmwareMessage`:**
  ```json
  {
    "text": "...",
    "effect": "SCROLL",
    "zoneId": "pico-salon",
    "ts": "2026-06-23T12:00:00Z",
    "speed": 50,
    "blinkPeriod": 500,
    "fadeSteps": 8
  }
  ```
  All three new fields are `Int?` (nullable). Server sets them from `POST /api/v1/text` body params if provided; omits them otherwise. Firmware uses its hardcoded defaults when fields are absent.
- **D-13:** `POST /api/v1/text` body gains optional `speed: Int?`, `blinkPeriod: Int?`, `fadeSteps: Int?` fields. For local/network zones these are silently ignored. Validation: positive integers only (or null).

### Config File (`config.h`)

- **D-14:** Single `config.h` controls everything:
  ```c
  #define BOARD_TYPE     RP2350        // RP2040, RP2350, ESP32
  #define DISPLAY_DRIVER MAX7219       // SSD1306, ST7789, etc.
  #define FONT_TYPE      FONT_5X8      // driver-compatible font
  #define WIFI_SSID      "..."
  #define WIFI_PASS      "..."
  #define SERVER_HOST    "192.168.1.100"
  #define SERVER_PORT    8080
  #define ZONE_ID        "pico-salon"
  #define RECONNECT_INTERVAL_MS 5000
  ```

### Reconnect Behavior

- **D-15:** Fixed 5s retry on WiFi/WS drop. Display shows "Connecting..." while disconnected. `RECONNECT_INTERVAL_MS` configurable in `config.h`.

### Repo Structure

- **D-16:** `firmware/pico/` (CMake project) and `firmware/esp32/` (PlatformIO project) under top-level `firmware/`. Main Gradle build ignores `firmware/`. No build coupling.
- **D-17:** Each firmware dir has its own `README.md` covering: required toolchain, wiring diagram reference (pin table), and flash procedure. (Required by ROADMAP success criterion 3.)

### CI / Artifacts

- **D-18:** GitHub Actions CI: build-check job for both firmware projects. Produces flashable release artifacts: `.uf2` for Pico W/2W, `.bin`/`.elf` for ESP32 — downloadable from Actions without local toolchain setup.

### Claude's Discretion

- Exact CMake FetchContent versions for Mongoose and cJSON
- PlatformIO board identifiers for ESP32 variants
- Wiring diagram format (ASCII table vs Fritzing reference)
- RP2350 vs RP2040 CMake target differences (PICO_BOARD flag)
- Which additional e-paper controllers and display modules to add beyond the listed ones (researcher to decide based on popularity in Pico/ESP32 ecosystem)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Wire Protocol (locked in Phase 16)
- `.planning/phases/16-zone-management/16-CONTEXT.md` §D-11 — Original FirmwareMessage wire contract. Phase 17 extends it (D-12 above supersedes D-11 for this phase).
- `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` — Server-side data class to extend
- `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt` — Where FirmwareMessage is serialized and pushed
- `src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt` — WebSocket route (`ws://host:8080/ws/zone/{id}`)

### Server-side changes
- `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` — `POST /api/v1/text` handler to extend with optional timing params
- `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` — Validation rules (new optional Int params need positive-value guard)

### Requirements
- `.planning/REQUIREMENTS.md` §FW-01, FW-02 — Firmware requirement specs
- `.planning/ROADMAP.md` §Phase 17 — Success criteria (Pico W flash + scroll, ESP32 flash + render, README, reconnect)

### External docs (researcher to fetch)
- Mongoose WebSocket client docs (Cesanta) — lwIP integration guide
- cJSON library README — usage for embedded C
- pico-sdk WiFi (CYW43) documentation — WiFi init patterns
- PlatformIO ArduinoWebsockets library docs

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` — Extend this (add speed, blinkPeriod, fadeSteps as `Int?`)
- `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt` — Update `send()` to pass new fields
- Server's MAX7219 column bitmap font in `src/main/kotlin/com/anjo/service/` — reference for firmware's 5x8 bitmap font layout

### Established Patterns
- `config.h` pattern: mirrors server's YAML `${VAR:default}` approach — one file controls all tunables
- Column-bitmap scroll: server implements left-shift in `Max7219Matrix` — firmware's SCROLL effect should match the same visual behavior
- Validation for new optional fields: follows project rule — validators only in `*Validators.kt`, never inline in route handler

### Integration Points
- `FirmwareMessage` data class: adding nullable fields is backward-compatible (existing firmware zones unaffected)
- `POST /api/v1/text` body: new optional fields ignored for all non-firmware zone types
- `firmware/` directory: entirely new, no Gradle integration needed

</code_context>

<specifics>
## Specific Ideas

- User explicitly wants RP2350 (Pico 2W) support alongside RP2040 (Pico W) — same firmware, different `BOARD_TYPE` define
- Display driver list should be expanded by researcher: user said "find ones I haven't thought of" — check popular Pico/ESP32 display modules in 2025-2026 ecosystem (ILI9341, SH1106, HT16K33, WS2812B matrix, PCD8544, etc.)
- GitHub Actions should produce downloadable flashable artifacts — key UX requirement so users don't need local toolchain
- Fonts must be compatible with the selected display driver (e.g., Adafruit GFX fonts only for Arduino builds)
- "Connecting..." message on display during reconnect — must be renderable with whatever driver is compiled in

</specifics>

<deferred>
## Deferred Ideas

- OTA (over-the-air) firmware update mechanism — future phase
- Firmware provisioning (captive portal WiFi setup) — future phase
- Kotlin Native firmware — out of scope (KT-44498 unresolved; C/C++ is correct approach per REQUIREMENTS.md)
- PostgreSQL / multi-replica considerations — unrelated to firmware

</deferred>

---

*Phase: 17-firmware-skeletons*
*Context gathered: 2026-06-23*
