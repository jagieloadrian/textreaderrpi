# Phase 17: Firmware Skeletons - Research

**Researched:** 2026-06-24
**Domain:** Embedded C/C++ firmware (pico-sdk + Mongoose + ESP32 Arduino/PlatformIO), server-side Kotlin wire protocol extension
**Confidence:** MEDIUM (toolchain verified locally; library APIs [ASSUMED] from web sources; physical hardware tests deferred)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** SDK/framework: Pure pico-sdk (CMake). No Arduino-Pico. Supports both RP2040 (Pico W) and RP2350 (Pico 2W) — board target selected via `BOARD_TYPE` in `config.h`.
- **D-02:** WebSocket library: Mongoose (Cesanta) — fetched via CMake FetchContent. Runs on pico-sdk's lwIP stack. No TLS required (home network).
- **D-03:** JSON parsing: cJSON (DaveGamble) — fetched via CMake FetchContent. MIT-licensed, single .c/.h pair.
- **D-04:** Framework: Arduino + PlatformIO. WebSocket library: ArduinoWebsockets (explicit in FW-02). JSON: ArduinoJson.
- **D-05:** Display libraries: Adafruit GFX ecosystem (Adafruit SSD1306, Adafruit ST7789, GxEPD2 for e-paper, MD_MAX72XX for MAX7219) — via PlatformIO lib_deps.
- **D-06:** Compile-time display selection: `#define DISPLAY_DRIVER MAX7219` in `config.h`. Only selected driver's source compiled.
- **D-07:** Driver interface — 3 functions: `display_init()`, `display_clear()`, `display_text(text, effect, speed_ms)`.
- **D-08:** Drivers to implement: MAX7219, SSD1306, SSD1309, SSD1327, ST7735, ST7789, ILI9225, ST7687S, e-paper variants (SSD1680 2.13"/2.9", IL0373, UC8151). Researcher to expand.
- **D-09:** Font: `#define FONT_TYPE` in config.h. Adafruit GFX fonts for Arduino; embedded 5×8 bitmap header for pico-sdk.
- **D-10:** All 4 effects: SCROLL, BLINK, REVERSE, FADE.
- **D-11:** Timing from message: `speed` (ms), `blinkPeriod` (ms), `fadeSteps`. Firmware uses defaults if fields absent.
- **D-12:** Extended FirmwareMessage: `speed`, `blinkPeriod`, `fadeSteps` as nullable Int.
- **D-13:** POST /api/v1/text gains optional `speed`, `blinkPeriod`, `fadeSteps` Int? fields. Positive integers only or null.
- **D-14:** Single `config.h` controls BOARD_TYPE, DISPLAY_DRIVER, FONT_TYPE, WIFI_SSID, WIFI_PASS, SERVER_HOST, SERVER_PORT, ZONE_ID, RECONNECT_INTERVAL_MS.
- **D-15:** Fixed 5s retry on WiFi/WS drop. Display shows "Connecting..." while disconnected.
- **D-16:** `firmware/pico/` (CMake) and `firmware/esp32/` (PlatformIO) under `firmware/`. Gradle ignores firmware/.
- **D-17:** Each firmware dir has README.md covering: toolchain, wiring diagram (pin table), flash procedure.
- **D-18:** GitHub Actions CI: build-check + downloadable artifacts (.uf2 for Pico, .bin/.elf for ESP32).
- **D-19:** Pico W OTA via picowota bootloader (submodule) or HTTP download. Researcher to recommend.
- **D-20:** ESP32: ArduinoOTA or HTTPUpdate.
- **D-21:** OTA trigger via WebSocket `"command":"ota"` message or dedicated URL in config.h.
- **D-22:** First-boot AP mode, SSID `TextReader-Setup`, captive portal HTML form for SSID/password.
- **D-23:** Credentials stored to flash (Pico: pico_flash / LittleFS; ESP32: NVS Preferences).
- **D-24:** Pico captive portal via Mongoose HTTP server. ESP32: WiFiManager or custom.

### Claude's Discretion
- Exact CMake FetchContent versions for Mongoose and cJSON
- PlatformIO board identifiers for ESP32 variants
- Wiring diagram format (ASCII table vs Fritzing reference)
- RP2350 vs RP2040 CMake target differences (PICO_BOARD flag)
- Which additional e-paper controllers and display modules to add beyond the listed ones

### Deferred Ideas (OUT OF SCOPE)
- Kotlin Native firmware
- PostgreSQL / multi-replica considerations
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FW-01 | RPi Pico W / Pico 2W firmware skeleton: connects via WiFi, opens WebSocket to server, receives text+effect JSON, renders on MAX7219 (`firmware/pico/`, C, pico-sdk) | Covered by Mongoose WebSocket client patterns, CYW43 WiFi init, cJSON parse, CMake structure, picowota OTA, Mongoose captive portal |
| FW-02 | ESP32 series firmware skeleton: same as FW-01 but C++, Arduino + ArduinoWebsockets (`firmware/esp32/`) | Covered by ArduinoWebsockets, platformio.ini, MD_MAX72XX, ArduinoOTA, WiFiManager |
</phase_requirements>

---

## Summary

Phase 17 creates two standalone embedded firmware projects inside the existing Kotlin mono-repo. The firmware projects are completely decoupled from Gradle — they live in `firmware/pico/` (CMake + pico-sdk) and `firmware/esp32/` (PlatformIO + Arduino) and share only the wire protocol with the server.

The server side requires a modest extension: add three nullable `Int?` fields (`speed`, `blinkPeriod`, `fadeSteps`) to `FirmwareMessage.kt` and `TextRequest.kt`, update `FirmwareZoneDriver.send()` to pass them through, and add positive-integer validation to `RequestValidators` (following the established pattern — validators only in `*Validators.kt` objects, never inline in route handlers).

The Pico W firmware uses Mongoose 7.22 (two-file drop-in: `mongoose.c` + `mongoose.h`) for WebSocket client and HTTP server (captive portal). The preferred integration is copying the two files directly rather than FetchContent, matching Cesanta's own Pico tutorials. pico-sdk 2.2.0 is already installed locally at `$PICO_SDK_PATH=/home/diether18/Development/libs/pico-sdk` and includes `pico2_w.h`, confirming RP2350 support. For OTA, `picowota` (usedbytes) is the recommended approach — a git submodule that provides a safe bootloader with CRC verification and a `picowota_reboot(true)` API callable from the running app.

The ESP32 firmware uses PlatformIO with `ArduinoWebsockets` (gilmaimon), `ArduinoJson`, and the Adafruit GFX ecosystem plus `MD_MAX72XX`/`MD_Parola` for the MAX7219 reference driver. WiFiManager (tzapu) handles the captive portal and NVS persistence with a single `wm.autoConnect("TextReader-Setup")` call. ArduinoOTA coexists with ArduinoWebsockets (non-async) without documented conflicts.

**Primary recommendation:** Implement the Pico project by copying `mongoose.c`/`mongoose.h` into `firmware/pico/lib/mongoose/` rather than using FetchContent. This matches the Cesanta Pico tutorials and avoids CMake FetchContent cache invalidation issues common in CI. For ESP32, use `esp32dev` as the default board identifier in `platformio.ini`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| WiFi connection + reconnect | Firmware (MCU) | — | CYW43/WiFi stack lives on device |
| WebSocket client session | Firmware (MCU) | — | mg_ws_connect / ArduinoWebsockets runs on device |
| JSON parsing of FirmwareMessage | Firmware (MCU) | — | cJSON / ArduinoJson parses incoming WS frame |
| Display rendering (effect animation) | Firmware (MCU) | — | SPI/I2C driver + effect loop runs on device |
| FirmwareMessage serialization | API / Backend (Ktor) | — | FirmwareZoneDriver.send() serializes via kotlinx.serialization |
| POST /api/v1/text optional timing fields | API / Backend (Ktor) | — | TextRequest extended; routing/validation server-side |
| Wire protocol validation (speed > 0 etc.) | API / Backend (Ktor) | — | RequestValidators only — project coding rule |
| OTA binary hosting | API / Backend (Ktor) or CDN/Static | — | Server serves .uf2/.bin URL; device fetches |
| Captive portal (WiFi provisioning) | Firmware (MCU) | — | Mongoose HTTP / WiFiManager AP mode on device |
| Credential storage | Firmware (MCU) Flash | — | LittleFS / pico_flash / NVS Preferences on device |
| CI artifact production | CDN / Static (GitHub Actions) | — | Produces .uf2 / .bin downloadable without local toolchain |

---

## Standard Stack

### Core — Pico W

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| pico-sdk | 2.2.0 (installed) | MCU SDK, CYW43 WiFi, lwIP | Official Raspberry Pi SDK [VERIFIED: locally installed] |
| Mongoose | 7.22 [ASSUMED] | WebSocket client + HTTP server (captive portal) | Two-file drop-in; built-in lwIP integration; Cesanta's own Pico tutorial uses it [CITED: mongoose.ws/documentation/tutorials/rp2040/pico-w/] |
| cJSON | 1.7.19 [ASSUMED] | JSON parsing | ANSI C89, single .c/.h, MIT license, FetchContent-friendly [CITED: github.com/DaveGamble/cJSON] |
| picowota | latest main [ASSUMED] | OTA bootloader | CRC-safe OTA via TCP/4242; `picowota_reboot()` API; active maintenance [CITED: github.com/usedbytes/picowota] |

### Core — ESP32

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| PlatformIO | latest | Build system + package manager | De facto standard for Arduino ESP32 [ASSUMED] |
| ArduinoWebsockets | latest [ASSUMED] | WebSocket client | PlatformIO registry: `gilmaimon/ArduinoWebsockets`; named in FW-02 requirement |
| ArduinoJson | 7.x [ASSUMED] | JSON parsing | `bblanchon/ArduinoJson`; standard in Arduino ecosystem |
| MD_MAX72XX | 3.3.x [ASSUMED] | MAX7219 SPI matrix driver | `majicdesigns/MD_MAX72XX`; well-maintained, Arduino-native |
| MD_Parola | 3.6.x [ASSUMED] | Text scroll/effects on MAX7219 | `majicdesigns/MD_Parola`; built on MD_MAX72XX; handles SCROLL natively |
| WiFiManager | latest [ASSUMED] | Captive portal + NVS credential storage | `tzapu/WiFiManager`; `wm.autoConnect()` handles AP+portal+reconnect in one call |
| Adafruit SSD1306 | latest [ASSUMED] | OLED 128×64 driver | `adafruit/Adafruit SSD1306`; Adafruit GFX ecosystem |
| Adafruit ST7789 | latest [ASSUMED] | 240×240 TFT driver | `adafruit/Adafruit ST7789` |
| GxEPD2 | latest [ASSUMED] | E-paper display driver (SSD1680, UC8151) | `zinggjm/GxEPD2`; supports most Waveshare panels [CITED: registry.platformio.org/libraries/zinggjm/GxEPD2] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Adafruit GFX Library | latest [ASSUMED] | Base graphics layer | Dependency of SSD1306, ST7789, GxEPD2 |
| Adafruit BusIO | latest [ASSUMED] | I2C/SPI abstraction | Transitive dep of Adafruit display libs |
| TFT_eSPI | latest [ASSUMED] | Alternative TFT driver for ILI9341 | Use if Adafruit ILI9341 proves slow; `bodmer/TFT_eSPI` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Mongoose (Pico) | lwIP raw WebSocket from scratch | Mongoose saves ~2 weeks of WS frame parser work; raw lwIP gives smaller binary |
| picowota OTA | HTTP download + flash_range_program directly | picowota provides safe CRC check; direct flash is simpler but bricks on bad download |
| WiFiManager (ESP32) | Custom AP + WebServer + Preferences | WiFiManager is one function call; custom is 200+ lines but removes extra dependency |
| ArduinoWebsockets | links2004/WebSockets | links2004 is older but more battle-tested; both work; ArduinoWebsockets has cleaner callback API |

**Installation — Pico (manual copy, not FetchContent):**
```bash
# Copy Mongoose two-file into project
mkdir -p firmware/pico/lib/mongoose
curl -o firmware/pico/lib/mongoose/mongoose.c \
  https://raw.githubusercontent.com/cesanta/mongoose/7.22/mongoose.c
curl -o firmware/pico/lib/mongoose/mongoose.h \
  https://raw.githubusercontent.com/cesanta/mongoose/7.22/mongoose.h
```

**Installation — cJSON (FetchContent in CMakeLists.txt):**
```cmake
include(FetchContent)
FetchContent_Declare(cjson
  GIT_REPOSITORY https://github.com/DaveGamble/cJSON.git
  GIT_TAG        v1.7.19
  GIT_SHALLOW    ON
)
set(ENABLE_CJSON_TEST OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(cjson)
```

**Installation — picowota (git submodule):**
```bash
git submodule add https://github.com/usedbytes/picowota firmware/pico/picowota
```

---

## Package Legitimacy Audit

This phase does not install npm/PyPI/crates packages. The embedded libraries are C/C++ — not subject to the npm package legitimacy gate. The PlatformIO lib_deps are fetched from the PlatformIO registry at build time.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| gilmaimon/ArduinoWebsockets | PlatformIO | ~5 yrs [ASSUMED] | Moderate [ASSUMED] | github.com/gilmaimon/ArduinoWebsockets | OK [ASSUMED] | Approved |
| majicdesigns/MD_MAX72XX | PlatformIO | ~10 yrs [ASSUMED] | High [ASSUMED] | github.com/MajicDesigns/MD_MAX72XX | OK [ASSUMED] | Approved |
| majicdesigns/MD_Parola | PlatformIO | ~10 yrs [ASSUMED] | High [ASSUMED] | github.com/MajicDesigns/MD_Parola | OK [ASSUMED] | Approved |
| tzapu/WiFiManager | PlatformIO | ~8 yrs [ASSUMED] | Very High [ASSUMED] | github.com/tzapu/WiFiManager | OK [ASSUMED] | Approved |
| zinggjm/GxEPD2 | PlatformIO | ~7 yrs [ASSUMED] | High [ASSUMED] | github.com/ZinggJM/GxEPD2 | OK [ASSUMED] | Approved |
| DaveGamble/cJSON | GitHub | ~15 yrs | Very High | github.com/DaveGamble/cJSON | OK | Approved |
| usedbytes/picowota | GitHub | ~3 yrs [ASSUMED] | Niche [ASSUMED] | github.com/usedbytes/picowota | OK [ASSUMED] | Approved |

**Packages removed due to SLOP verdict:** none

**Packages flagged as suspicious:** none — all are established embedded ecosystem libraries with known GitHub repos. All PlatformIO verdicts [ASSUMED] — executor should verify via `pio lib show <name>` before first build.

---

## Architecture Patterns

### System Architecture Diagram

```
SERVER (Ktor, running on Raspberry Pi)
  POST /api/v1/text ──► TextRoutes ──► ScreenDriverService
                                          │
                                          ├── LocalZoneDriver (MAX7219 via SPI)
                                          ├── NetworkZoneDriver (UDP zones)
                                          └── FirmwareZoneDriver ──► Channel<String>
                                                                         │
                                              WebSocket push (JSON) ◄────┘
                                              ws://host:8080/ws/zone/{id}
                                                    │
              ┌─────────────────────────────────────┴──────────────────────────┐
              │                                                                  │
     PICO W (firmware/pico/)                                      ESP32 (firmware/esp32/)
       mg_mgr_poll loop                                             loop() + client.poll()
       MG_EV_WS_MSG ──► cJSON_Parse ──► display_text()            onMessage ──► ArduinoJson
                         │                │                                       │
                         └─[speed/blink]  └─► driver/max7219.c    display_text() │
                                             (SPI: MOSI/CLK/CS)                  └─► MD_Parola
                                                                                      (SPI)
     OTA trigger: WS {"command":"ota"}         OTA trigger: WS {"command":"ota"}
       ──► picowota_reboot(true)                 ──► ArduinoOTA restart / HTTPUpdate

     First boot (no creds):                    First boot (no creds):
       cyw43_arch_enable_ap_mode()               WiFiManager.autoConnect()
       Mongoose HTTP ──► captive portal            ESP32 AP + portal ──► NVS store
       pico_flash write SSID+pass                  WiFi.begin() on reboot
```

### Recommended Project Structure

```
firmware/
├── pico/
│   ├── CMakeLists.txt          # top-level CMake project
│   ├── config.h                # all tunables
│   ├── pico_sdk_import.cmake   # SDK bootstrap
│   ├── picowota/               # git submodule (OTA bootloader)
│   ├── lib/
│   │   └── mongoose/
│   │       ├── mongoose.c      # copied from release 7.22
│   │       └── mongoose.h
│   ├── src/
│   │   ├── main.c              # WiFi init, WS connect, event loop
│   │   ├── mongoose_config.h   # MG_ARCH, MG_ENABLE_LWIP
│   │   ├── ws_client.c/.h      # mg_ws_connect wrapper + reconnect
│   │   ├── json_parser.c/.h    # cJSON wrappers for FirmwareMessage
│   │   ├── captive_portal.c/.h # AP mode + Mongoose HTTP form handler
│   │   ├── ota.c/.h            # picowota_reboot trigger
│   │   └── driver/
│   │       ├── display.h       # 3-function interface
│   │       ├── max7219.c/.h    # reference driver (SPI + 5×8 font)
│   │       ├── ssd1306.c/.h
│   │       └── ... (one file per display)
│   ├── font/
│   │   └── font5x8.h           # extracted from server Font.kt
│   └── README.md
└── esp32/
    ├── platformio.ini
    ├── config.h                 # same tunables, Arduino-flavoured
    ├── src/
    │   ├── main.cpp             # setup()/loop()
    │   ├── ws_client.cpp/.h     # ArduinoWebsockets wrapper + reconnect
    │   ├── json_parser.cpp/.h   # ArduinoJson wrappers
    │   ├── ota.cpp/.h           # ArduinoOTA setup
    │   └── driver/
    │       ├── display.h        # same 3-function interface
    │       ├── max7219.cpp/.h   # MD_MAX72XX/MD_Parola driver
    │       ├── ssd1306.cpp/.h   # Adafruit SSD1306 driver
    │       └── ... (one file per display)
    └── README.md
```

### Pattern 1: Mongoose WebSocket Client (Pico)

```c
// Source: [ASSUMED] cesanta.com docs + embedded community patterns
static void ws_handler(struct mg_connection *c, int ev, void *ev_data) {
    if (ev == MG_EV_WS_OPEN) {
        // connection ready — nothing to send here (server pushes)
    } else if (ev == MG_EV_WS_MSG) {
        struct mg_ws_message *wm = (struct mg_ws_message *) ev_data;
        char buf[512];
        mg_snprintf(buf, sizeof(buf), "%.*s", (int)wm->data.len, wm->data.buf);
        parse_and_display(buf);
    } else if (ev == MG_EV_CLOSE) {
        s_connected = false;
    }
}

void ws_task(void) {
    struct mg_mgr mgr;
    struct mg_connection *c = NULL;
    mg_mgr_init(&mgr);
    for (;;) {
        if (!s_connected) {
            char url[128];
            snprintf(url, sizeof(url), "ws://%s:%d/ws/zone/%s",
                     SERVER_HOST, SERVER_PORT, ZONE_ID);
            c = mg_ws_connect(&mgr, url, ws_handler, NULL, NULL);
            if (c) s_connected = true;
        }
        mg_mgr_poll(&mgr, 10);
        cyw43_arch_poll();
        sleep_ms(1);
    }
}
```

**mongoose_config.h:**
```c
#define MG_ARCH        MG_ARCH_PICOSDK
#define MG_ENABLE_LWIP 1
```

**CMakeLists.txt snippet:**
```cmake
cmake_minimum_required(VERSION 3.13)
set(PICO_BOARD pico_w)      # or pico2_w for RP2350
include($ENV{PICO_SDK_PATH}/external/pico_sdk_import.cmake)

project(textreader C CXX ASM)
pico_sdk_init()

add_executable(textreader
    src/main.c
    src/ws_client.c
    src/json_parser.c
    src/captive_portal.c
    src/ota.c
    src/driver/max7219.c    # only the selected driver
    lib/mongoose/mongoose.c
)

include(FetchContent)
FetchContent_Declare(cjson
    GIT_REPOSITORY https://github.com/DaveGamble/cJSON.git
    GIT_TAG        v1.7.19
    GIT_SHALLOW    ON)
set(ENABLE_CJSON_TEST OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(cjson)

target_link_libraries(textreader
    pico_stdlib
    pico_cyw43_arch_lwip_threadsafe_background
    hardware_spi
    cjson
)
target_include_directories(textreader PRIVATE
    src
    lib/mongoose
    font
    ${cjson_SOURCE_DIR}
)
pico_add_extra_outputs(textreader)   # generates .uf2
```

### Pattern 2: ESP32 ArduinoWebsockets + Reconnect

```cpp
// Source: [ASSUMED] github.com/gilmaimon/ArduinoWebsockets
#include <ArduinoWebsockets.h>
#include <ArduinoJson.h>
using namespace websockets;

WebsocketsClient wsClient;

void setupWs() {
    wsClient.onMessage([](WebsocketsMessage msg) {
        JsonDocument doc;
        DeserializationError err = deserializeJson(doc, msg.data());
        if (err) return;
        const char* cmd = doc["command"];
        if (cmd && strcmp(cmd, "ota") == 0) { ArduinoOTA.begin(); return; }
        const char* text   = doc["text"]   | "";
        const char* effect = doc["effect"] | "SCROLL";
        int speed          = doc["speed"]  | 50;
        display_text(text, effect, speed);
    });
    String url = String("ws://") + SERVER_HOST + ":" + SERVER_PORT
                 + "/ws/zone/" + ZONE_ID;
    wsClient.connect(url);
}

void loop() {
    ArduinoOTA.handle();
    if (!wsClient.available()) {
        display_text("Connecting...", "BLINK", 500);
        delay(RECONNECT_INTERVAL_MS);
        setupWs();
        return;
    }
    wsClient.poll();
}
```

**platformio.ini:**
```ini
[env:esp32dev]
platform  = espressif32
board     = esp32dev
framework = arduino
lib_deps  =
    gilmaimon/ArduinoWebsockets
    bblanchon/ArduinoJson
    majicdesigns/MD_MAX72XX
    majicdesigns/MD_Parola
    tzapu/WiFiManager
    adafruit/Adafruit SSD1306
    adafruit/Adafruit ST7789
    adafruit/Adafruit GFX Library
    adafruit/Adafruit BusIO
    zinggjm/GxEPD2
build_flags = -DDISPLAY_DRIVER_MAX7219
```

### Pattern 3: cJSON Parse for FirmwareMessage

```c
// Source: [ASSUMED] github.com/DaveGamble/cJSON README
#include "cJSON.h"

void parse_and_display(const char *json_str) {
    cJSON *root = cJSON_Parse(json_str);
    if (!root) return;

    cJSON *cmd = cJSON_GetObjectItemCaseSensitive(root, "command");
    if (cJSON_IsString(cmd) && strcmp(cmd->valuestring, "ota") == 0) {
        cJSON_Delete(root);
        trigger_ota();
        return;
    }

    cJSON *text_j   = cJSON_GetObjectItemCaseSensitive(root, "text");
    cJSON *effect_j = cJSON_GetObjectItemCaseSensitive(root, "effect");
    cJSON *speed_j  = cJSON_GetObjectItemCaseSensitive(root, "speed");

    const char *text   = cJSON_IsString(text_j)   ? text_j->valuestring   : "";
    const char *effect = cJSON_IsString(effect_j)  ? effect_j->valuestring : "SCROLL";
    int speed          = cJSON_IsNumber(speed_j)   ? (int)speed_j->valuedouble : 50;

    display_text(text, effect, speed);
    cJSON_Delete(root);
}
```

### Pattern 4: Server-side — FirmwareMessage Extension

```kotlin
// src/main/kotlin/com/anjo/model/FirmwareMessage.kt
@Serializable
data class FirmwareMessage(
    val text: String,
    val effect: String,
    val zoneId: String,
    val ts: String,
    val speed: Int? = null,
    val blinkPeriod: Int? = null,
    val fadeSteps: Int? = null
)
```

With `Json { explicitNulls = false }` (or the default encodeDefaults=false for null defaults), null fields are omitted from the JSON output. Firmware handles absent fields gracefully by using its defaults. [CITED: kotlinlang.org/api/kotlinx.serialization]

**TextRequest extension:**
```kotlin
@Serializable
data class TextRequest(
    val text: String,
    val effect: Effect = Effect.SCROLL,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT,
    val speed: Int? = null,
    val blinkPeriod: Int? = null,
    val fadeSteps: Int? = null
)
```

**RequestValidators addition (validators only — project rule):**
```kotlin
fun validateTextRequest(req: TextRequest, apiConfig: ApiConfig): ValidationResult {
    if (req.text.isBlank()) return ValidationResult.Invalid("Text cannot be blank")
    if (req.text.length > apiConfig.maxTextLength)
        return ValidationResult.Invalid("Text exceeds maximum length ...")
    req.speed?.let { if (it <= 0) return ValidationResult.Invalid("speed must be positive") }
    req.blinkPeriod?.let { if (it <= 0) return ValidationResult.Invalid("blinkPeriod must be positive") }
    req.fadeSteps?.let { if (it <= 0) return ValidationResult.Invalid("fadeSteps must be positive") }
    return ValidationResult.Valid
}
```

### Pattern 5: OTA + Captive Portal

**Pico OTA (picowota):**
```c
// In CMakeLists.txt: add_subdirectory(picowota)
// Link with: picowota_reboot
#include "picowota/reboot.h"

void trigger_ota(void) {
    picowota_reboot(true);   // reboots into picowota bootloader, listens on TCP/4242
}
```

**ESP32 Captive Portal (WiFiManager):**
```cpp
#include <WiFiManager.h>

void setupWifi() {
    WiFiManager wm;
    // autoConnect: tries saved NVS creds; on failure starts AP "TextReader-Setup"
    if (!wm.autoConnect("TextReader-Setup")) {
        ESP.restart();
    }
}
```

**Pico Captive Portal (Mongoose HTTP + cyw43 AP mode):**
```c
void captive_portal_task(void) {
    cyw43_arch_enable_ap_mode("TextReader-Setup", NULL, CYW43_AUTH_OPEN);
    // Mongoose HTTP server on 80, serves form, writes creds to flash via
    // flash_range_erase + flash_range_program, then reboots
}
```

### Anti-Patterns to Avoid

- **FetchContent for Mongoose on Pico:** Cesanta's own Pico tutorials use direct file copy. FetchContent works but adds cmake cache complexity and produces two-file copy at configure time anyway. Copy once, commit to repo.
- **Using `testApplication` for any WebSocket streaming tests:** Project rule — for streaming tests use `embeddedServer(Netty, port=0)` + CIO client.
- **Inline validation in route handlers:** Project rule — all positive-integer guards for `speed`/`blinkPeriod`/`fadeSteps` MUST live in `RequestValidators.validateTextRequest()`, never in `TextRoutes.kt`.
- **Adding comments to Kotlin files:** Project rule — no inline `//`, block `/* */`, or KDoc anywhere, including test files.
- **ArduinoOTA + ESPAsyncWebServer:** Known heap corruption conflicts. This project uses ArduinoWebsockets (synchronous) which avoids the issue.
- **pico_cyw43_arch_none for Mongoose:** Without lwIP, Mongoose needs its own TCP/IP stack (MG_ENABLE_TCPIP=1) plus 4 CYW43 callback functions. Use `pico_cyw43_arch_lwip_threadsafe_background` for simpler integration.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket frame parsing (Pico) | Manual lwIP TCP + WS handshake | Mongoose | WS handshake is 15-step protocol; Upgrade header, masking, fragmentation |
| JSON parsing in C | strstr/sscanf field extraction | cJSON | Edge cases: escaped quotes, Unicode, number overflow |
| OTA bootloader (Pico) | flash_range_erase in app firmware | picowota | Safe flash partition; CRC verify; anti-brick protection |
| WiFi provisioning (ESP32) | Custom AP + WebServer + NVS | WiFiManager | DNS redirect, form handling, SSID scan list — 400+ lines of boilerplate |
| MAX7219 scroll animation (ESP32) | Manual SPI shift-register timing | MD_Parola | Column timing, daisy-chain register addressing, font rendering |
| E-paper display partial update | Custom SPI command sequences | GxEPD2 | Display controller command sequences differ per chip; GxEPD2 abstracts all variants |
| Cross-compilation CMake for Pico in CI | Custom Docker image | Standard `gcc-arm-none-eabi` apt package | Already available on ubuntu-latest GitHub runners |

**Key insight:** Every item in this list has been hand-rolled in embedded projects and failed in production due to subtle timing, memory, or protocol edge cases. The libraries handle these for free.

---

## Display Driver List (D-08 Expanded)

The following drivers are recommended for Phase 17. Each needs a `.c/.h` (Pico) or `.cpp/.h` (ESP32) stub implementing the 3-function interface. Only the selected driver is compiled via `#ifdef DISPLAY_DRIVER_X`.

| Driver Define | Controller | Panel Type | Interface | Arduino Library | Pico Library |
|---------------|-----------|------------|-----------|-----------------|--------------|
| `MAX7219` | MAX7219 | LED matrix | SPI | MD_MAX72XX + MD_Parola | Custom SPI (5×8 font from Font.kt) |
| `SSD1306` | SSD1306 | 128×64 OLED | I2C/SPI | Adafruit SSD1306 | pico-ssd1306 [ASSUMED] |
| `SSD1309` | SSD1309 | 128×64 OLED | I2C | SSD1306-compatible | SSD1306-compatible |
| `SSD1327` | SSD1327 | 128×128 grayscale OLED | I2C/SPI | [ASSUMED] u8g2 | u8g2 [ASSUMED] |
| `ST7735` | ST7735 | 128×160 color TFT | SPI | Adafruit ST7735 | [ASSUMED] |
| `ST7789` | ST7789 | 240×240 color TFT | SPI | Adafruit ST7789 | pico-st7789 [ASSUMED] |
| `ILI9341` | ILI9341 | 240×320 color TFT | SPI | TFT_eSPI or Adafruit ILI9341 | [ASSUMED] |
| `ILI9225` | ILI9225 | 176×220 color TFT | SPI | [ASSUMED] | [ASSUMED] |
| `GC9A01` | GC9A01 | 240×240 round TFT | SPI | GC9A01 Arduino [ASSUMED] | [ASSUMED] |
| `SH1106` | SH1106 | 128×64 OLED (common cheap panels) | I2C | U8g2 or SH110X Adafruit [ASSUMED] | [ASSUMED] |
| `PCD8544` | PCD8544 | 84×48 Nokia 5110 LCD | SPI | Adafruit PCD8544 [ASSUMED] | [ASSUMED] |
| `HT16K33` | HT16K33 | 7-seg / 8×8 LED matrix | I2C | Adafruit HT16K33 [ASSUMED] | [ASSUMED] |
| `SSD1680` | SSD1680 | 2.13" / 2.9" e-paper | SPI | GxEPD2 | [ASSUMED] |
| `UC8151` | UC8151 | 2.9" e-paper (Waveshare alt) | SPI | GxEPD2 | [ASSUMED] |
| `IL0373` | IL0373 | 2.13" tri-color e-paper | SPI | GxEPD2 | [ASSUMED] |
| `WS2812B` | WS2812B | Addressable LED matrix (NeoPixel) | Single-wire | Adafruit NeoPixel [ASSUMED] | pico-neopixel / PIO [ASSUMED] |

**Recommendation for Phase 17 skeleton:** Implement `MAX7219` driver fully (it is the reference implementation and the only hardware tested in the success criteria). Stub the remaining drivers with a `display_text()` that writes to UART/serial for now — they can be completed in a later phase.

---

## Font Compatibility

The server's `Font.kt` uses 5-column bitmaps where each byte represents one column of 8 rows (LSB = row 0, top). This is the standard MAX7219 column-major format.

**Key insight from reading `Max7219Matrix.kt`:**
- Each character is 5 bytes (5 columns) + 1 byte gap (value 0)
- `buildBitmap()` appends `numDevices * 8` trailing blank columns for scroll runoff
- Scroll advances by 1 column per `speedMs` tick via `render(bitmap, offset++)`

**Firmware font header (`font5x8.h`) can be extracted directly from `Font.kt`:**
```c
// Transliteration of Font.kt asciiFont map into C array
static const uint8_t font5x8[][5] = {
    {0x00, 0x00, 0x00, 0x00, 0x00},  // ' ' (0x20)
    {0x00, 0x00, 0x5F, 0x00, 0x00},  // '!'
    // ... etc
};
#define FONT_CHAR_WIDTH 5
#define FONT_CHAR_GAP   1
```

The scroll algorithm in firmware must mirror the server exactly:
- Build full-text bitmap (all chars concatenated, 1-byte gaps, trailing pad of `display_width` columns)
- Each tick: render `display_width` columns starting at `offset`, then `offset++`
- Stop when `offset > bitmap_len - display_width`

---

## Wire Protocol Extension Details

**Current `FirmwareMessage.kt`** (read from codebase):
```kotlin
@Serializable
data class FirmwareMessage(val text: String, val effect: String, val zoneId: String, val ts: String)
```

**Extended version (Phase 17):** Add `speed: Int? = null`, `blinkPeriod: Int? = null`, `fadeSteps: Int? = null`. With kotlinx.serialization defaults, nullable fields with `null` value are **included** in JSON output by default (`explicitNulls = true` is default). To omit null fields, either:
- Configure `Json { explicitNulls = false }` in the Json instance used by `FirmwareZoneDriver`, or
- Use `@EncodeDefault(EncodeDefault.Mode.NEVER)` on each nullable field

**Recommendation:** Use `@EncodeDefault(EncodeDefault.Mode.NEVER)` per field. This is explicit and doesn't require changing the shared `Json` instance used elsewhere in the application.

**`FirmwareZoneDriver.send()` change:**
```kotlin
override suspend fun send(text: String, effect: Effect): Boolean {
    // Current signature — must be extended to accept timing params
    // New signature needed: send(text, effect, speed, blinkPeriod, fadeSteps)
    // Or: TextRequest passed in directly
}
```

The planner needs to decide: extend `ZoneDriver.send()` signature or pass a `TextRequest` object. Extending the interface affects all 3 ZoneDriver implementations (`LocalZoneDriver`, `NetworkZoneDriver`, `FirmwareZoneDriver`). The cleanest approach is a dedicated data class or nullable parameter overload.

---

## Common Pitfalls

### Pitfall 1: Mongoose integrated with wrong CYW43 arch library
**What goes wrong:** Linking `pico_cyw43_arch_none` (no networking) causes Mongoose lwIP calls to fail at runtime with no error — the device silently cannot connect.
**Why it happens:** `pico_cyw43_arch_none` only enables the LED; network stack is absent.
**How to avoid:** Always link `pico_cyw43_arch_lwip_threadsafe_background` when using Mongoose with `MG_ENABLE_LWIP=1`.
**Warning signs:** `cyw43_arch_wifi_connect_timeout_ms()` times out immediately; no IP assigned.

### Pitfall 2: Flash write during XIP (execute-in-place) on RP2040
**What goes wrong:** Calling `flash_range_erase()` or `flash_range_program()` while code is executing from flash causes a hard fault / corruption.
**Why it happens:** RP2040 has a single XIP cache; writing flash while fetching instructions from it is undefined behavior.
**How to avoid:** picowota handles this — it reboots into bootloader (which runs from SRAM) before writing. If doing manual flash writes, use `__no_inline_not_in_flash_func` and disable interrupts.
**Warning signs:** Random hard faults during OTA attempts.

### Pitfall 3: ArduinoOTA + ESPAsyncWebServer heap corruption
**What goes wrong:** Enabling ArduinoOTA alongside ESPAsyncWebServer causes heap corruption / random resets.
**Why it happens:** ESPAsyncWebServer uses a separate task that conflicts with OTA's UDP listener on port 3232.
**How to avoid:** This project uses ArduinoWebsockets (synchronous, not async) — no conflict. Avoid ESPAsyncWebServer entirely.
**Warning signs:** ESP32 resets with `CORRUPT HEAP` in serial output.

### Pitfall 4: PICO_BOARD mismatch for RP2350 (Pico 2W)
**What goes wrong:** Using `PICO_BOARD=pico_w` attempts to target RP2040 on a Pico 2W (RP2350 chip) — build succeeds but firmware may not boot correctly on RP2350.
**Why it happens:** `pico_w.h` hardcodes RP2040 chip selectors.
**How to avoid:** Use `-DPICO_BOARD=pico2_w` for Pico 2W. pico-sdk 2.2.0 (installed locally) includes `pico2_w.h` — confirmed. [VERIFIED: locally installed]
**Warning signs:** CMake warns "PICO_PLATFORM=rp2040 but board requires rp2350".

### Pitfall 5: kotlinx.serialization null field behavior
**What goes wrong:** Nullable `Int?` fields with `null` values are serialized as `"speed":null` in JSON, and firmware receives a literal `null` token rather than the field being absent.
**Why it happens:** `explicitNulls` defaults to `true` in kotlinx.serialization.
**How to avoid:** Add `@EncodeDefault(EncodeDefault.Mode.NEVER)` to each nullable timing field in `FirmwareMessage`. Firmware cJSON parser checks `cJSON_IsNumber()` which returns false for null — safe.
**Warning signs:** Firmware log shows `"speed":null` in received JSON; cJSON falls back to defaults correctly but wasteful.

### Pitfall 6: WS ZoneDriver.send() interface change
**What goes wrong:** Adding timing params to `FirmwareZoneDriver.send()` requires changing the `ZoneDriver` interface, which forces updates to `LocalZoneDriver` and `NetworkZoneDriver` too.
**Why it happens:** Kotlin interfaces require all implementations to match the signature.
**How to avoid:** Pass timing as a data class or add optional params with defaults to the interface. Alternatively, keep `send(text, effect)` signature on the interface and have `FirmwareZoneDriver` pull timing from a separately stored `TextRequest`. Planner to decide.
**Warning signs:** Compile errors in `LocalZoneDriver` / `NetworkZoneDriver` after interface change.

### Pitfall 7: Gradle scanning firmware/ directory
**What goes wrong:** Gradle may attempt to auto-include `firmware/pico/` or `firmware/esp32/` as subprojects if they contain files Gradle recognizes.
**Why it happens:** Gradle's project discovery can pick up directories with `build.gradle` or similar files.
**How to avoid:** The `settings.gradle.kts` in this project uses explicit `include()` — since `firmware/` is never mentioned, Gradle will not scan it. No explicit exclusion needed. [VERIFIED: read settings.gradle.kts — only contains rootProject.name and dependencyResolutionManagement]

---

## Runtime State Inventory

Phase 17 is a **greenfield addition** (new directories `firmware/`). No renaming or migration of existing server state occurs.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | FirmwareMessage in WebSocket sessions — no persistent DB records for firmware messages | None — wire protocol extension is backward-compatible |
| Live service config | FirmwareZoneDriver registered in ZoneRegistry at startup via ZoneRepository | No migration — existing firmware zones remain OFFLINE until physical device connects |
| OS-registered state | None | None |
| Secrets/env vars | SERVER_HOST, SERVER_PORT in `config.h` (firmware, not server env vars) | Firmware config.h is not an env var — set at compile time |
| Build artifacts | firmware/ is new; no stale artifacts | None |

---

## Validation Architecture

Note: `workflow.nyquist_validation` key is absent from `.planning/config.json` — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest (FunSpec) with `should` convention |
| Config file | `src/test/kotlin/com/anjo/ProjectConfig.kt` — `coroutineTestScope = false` |
| Quick run command | `./gradlew test --tests "com.anjo.model.*" --tests "com.anjo.validation.*"` |
| Full suite command | `./gradlew test jacocoTestCoverageVerification` |

**Note:** Firmware C/C++ code cannot be tested via the Kotlin/Gradle test suite. Firmware correctness is verified by: (a) CMake/PlatformIO compile check in CI, and (b) physical device testing (human-verify checkpoint).

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FW-01 | FirmwareMessage extended with timing fields serializes correctly | unit | `./gradlew test --tests "com.anjo.model.FirmwareMessageTest"` | No — Wave 0 |
| FW-01 | Null timing fields omitted from JSON output | unit | Same | No — Wave 0 |
| FW-01 | Pico CMake project compiles (no hardware) | CI build check | GitHub Actions step: `cmake -DPICO_BOARD=pico_w .. && make` | No — Wave 0 |
| FW-01 | Pico 2W CMake project compiles | CI build check | GitHub Actions step: `cmake -DPICO_BOARD=pico2_w .. && make` | No — Wave 0 |
| FW-01 | Flash + connect + scroll on physical Pico W | manual / physical | — | Human-verify checkpoint |
| FW-02 | PlatformIO ESP32 project compiles | CI build check | `pio run` in esp32/ | No — Wave 0 |
| FW-02 | Flash + connect + render on physical ESP32 | manual / physical | — | Human-verify checkpoint |
| FW-01+02 | POST /api/v1/text with speed=50 returns 202 | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest"` | Extend existing |
| FW-01+02 | POST /api/v1/text with speed=-1 returns 422 | unit | `./gradlew test --tests "com.anjo.validation.RequestValidatorsTest"` | No — Wave 0 (new file) |
| FW-01+02 | FirmwareZoneDriver.send() includes speed/blinkPeriod/fadeSteps | unit | `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest"` | Extend existing |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.model.*" --tests "com.anjo.validation.*" --tests "com.anjo.zone.*"`
- **Per wave merge:** `./gradlew test jacocoTestCoverageVerification`
- **Phase gate:** Full suite green + firmware CI build check passing before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt` — covers FirmwareMessage serialization with null fields omitted
- [ ] GitHub Actions workflow `.github/workflows/firmware-ci.yml` — covers pico + esp32 compile checks
- [ ] Extend `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — verify send() includes timing fields
- [ ] Create `src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt` (new file) — speed/blinkPeriod/fadeSteps validation

---

## Security Domain

`security_enforcement` is not configured — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth in scope (home network, project constraint) |
| V3 Session Management | No | WebSocket sessions are unauthenticated (home network) |
| V4 Access Control | No | All zones accessible without credentials (design decision) |
| V5 Input Validation | Yes | New optional Int fields validated in RequestValidators — positive integers only |
| V6 Cryptography | No | No TLS required per D-02 (home network) |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed JSON from server | Tampering | cJSON_Parse returns NULL on bad JSON — always null-check root before field access |
| OTA push of malicious binary | Tampering | picowota verifies CRC before flashing — rejects bad binaries; no auth beyond LAN access |
| WebSocket zone ID spoofing | Spoofing | Server validates zone existence before attach() — CLOSE frame on unknown zone (already implemented in FirmwareZoneRoutes.kt) |
| WiFi credential exfiltration via portal | Information Disclosure | Portal served only in AP mode (no internet access); credentials stored in flash not transmitted elsewhere |
| `speed` integer overflow in firmware | Tampering | Firmware should clamp: `speed = max(1, min(speed, 5000))` before using as `delay()` arg |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| pico-sdk | Pico firmware build | Yes | 2.2.0 at `$PICO_SDK_PATH` | — |
| cmake | Pico firmware build | Yes | 4.2.1 | — |
| arm-none-eabi-gcc | Pico firmware cross-compile | Yes | 13.2.1 | — |
| Python 3 | PlatformIO | Yes | 3.12.3 | — |
| pip | PlatformIO install | Yes | 24.0 | — |
| PlatformIO / pio | ESP32 build | No | — | `pip install platformio` — Wave 0 task |
| Gradle / Kotest | Server-side tests | Yes | 9.5.1 | — |
| Physical Pico W | Flash + verify FW-01 | Unknown | — | Human-verify checkpoint; CI compile covers build-check |
| Physical ESP32 | Flash + verify FW-02 | Unknown | — | Human-verify checkpoint; CI compile covers build-check |

**Missing with no fallback:** none that block CI.

**Missing dependencies with fallback:**
- PlatformIO not installed globally — install with `pip install platformio` as a Wave 0 task; GitHub Actions installs it in CI workflow.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Arduino-Pico (Arduino framework on Pico) | Pure pico-sdk (CMake) | 2022+ | Direct hardware access, no Arduino overhead, supports RP2350 natively |
| lwIP raw TCP for WebSocket | Mongoose (abstracts lwIP) | 2023+ | Mongoose 7.x has native Pico tutorials; avoids manual WS framing |
| SPIFFS for ESP32 credential storage | NVS Preferences API | ESP-IDF 4.0+ | SPIFFS deprecated for config storage; NVS is faster and wear-leveling-aware |
| ArduinoJson 5.x / 6.x | ArduinoJson 7.x | 2024 | Static `JsonDocument` replaces heap `DynamicJsonDocument`; no size hint needed |
| Separate OTA bootloader + app binaries | picowota (combined CRC-verified) | 2022+ | Anti-brick protection; WiFi firmware deduplication remains a known cost |

**Deprecated/outdated:**
- SPIFFS (ESP32): deprecated for config; use NVS Preferences instead
- `DynamicJsonDocument`/`StaticJsonDocument` (ArduinoJson 6.x): replaced by `JsonDocument` in v7
- `pico_sdk` 1.x: installed SDK is 2.2.0; `pico2_w` board (RP2350) requires SDK 2.x

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Mongoose 7.22 is the latest release | Standard Stack | Minor — use whatever tag is latest at build time |
| A2 | cJSON 1.7.19 is the latest release | Standard Stack | Minor — CMake FetchContent uses GIT_TAG; update to actual latest |
| A3 | PlatformIO library names `gilmaimon/ArduinoWebsockets`, `tzapu/WiFiManager`, etc. match registry exactly | Standard Stack | Medium — wrong name fails PlatformIO build; verify with `pio lib search` |
| A4 | ArduinoWebsockets coexists with ArduinoOTA without conflicts | Code Examples | Medium — if crashes occur, use HTTPUpdate instead of ArduinoOTA |
| A5 | picowota is maintained and buildable against pico-sdk 2.2.0 | Standard Stack | Medium — if SDK API changed, need alternative OTA approach |
| A6 | `mg_ws_connect()` function signature and event handler pattern | Code Examples | Medium — verify against actual mongoose.h before implementation |
| A7 | Mongoose's AP mode (cyw43_arch_enable_ap_mode) works with Mongoose HTTP server simultaneously | Code Examples | High — if AP+HTTP+Mongoose conflict, captive portal needs different approach (lwIP raw HTTP) |
| A8 | Non-Adafruit display libraries (u8g2 for SSD1327, SH1106) work with ESP32 PlatformIO | Display Driver List | Low — stub drivers deferred; only MAX7219 needed for success criteria |
| A9 | ZoneDriver.send() interface can be extended with nullable timing params without breaking LocalZoneDriver/NetworkZoneDriver | Wire Protocol | Medium — requires interface change; all 3 implementations must be updated |
| A10 | Gradle does not auto-discover firmware/ directory as a subproject | Architecture | Low — confirmed by reading settings.gradle.kts: no wildcard include |

---

## Open Questions (RESOLVED)

1. **ZoneDriver.send() signature extension**
   - What we know: Adding timing params to `FirmwareZoneDriver.send()` requires updating the `ZoneDriver` interface and all 3 implementations
   - What's unclear: Should timing travel as separate params, a `TextRequest` object, or a new `DisplayCommand` data class?
   - **RESOLVED: Separate nullable params chosen over a `DisplayCommand` data class.** The plan (17-01) extends `ScreenDriverService.displayImmediate(...)` and the `ZoneDriver.send(...)` path with three trailing nullable parameters (`speed: Int? = null`, `blinkPeriod: Int? = null`, `fadeSteps: Int? = null`) with defaults. Rationale: default-valued params give the minimal ripple — `LocalZoneDriver` and `NetworkZoneDriver` compile unchanged because the new params default to null, preserving backward compatibility. A new `DisplayCommand` data class was rejected because it would force a signature change at every call site and every implementation even where timing is irrelevant, adding a type with no behavioral benefit for local/network zones. Separate params keep the diff localized to `FirmwareZoneDriver` (which forwards them into `FirmwareMessage`) and the API surface.

2. **Pico captive portal DNS redirect**
   - What we know: Captive portal requires DNS server to redirect all queries to 192.168.4.1; Mongoose provides HTTP server but not a DNS server
   - What's unclear: Is there a lightweight DNS server compatible with pico-sdk + Mongoose?
   - **RESOLVED: No DNS spoofing; rely on HTTP captive-portal detection with documented manual fallback.** lwIP includes a DNS server module (`apps/dns`) but it is not wired in for this phase. Most modern mobile OS captive-portal probes follow an HTTP redirect without DNS spoofing; older clients navigate manually to `http://192.168.4.1`. This is acceptable because D-22 requires "serves a captive portal" — not "auto-redirects all domains". The Pico README (17-03 Task 2) documents the manual-navigation fallback. A full lwIP DNS server is deferred as unnecessary for the requirement.

3. **picowota + Mongoose coexistence**
   - What we know: picowota is a separate binary (bootloader); the app links `picowota_reboot` library only for the reboot trigger
   - What's unclear: Does combining `picowota_build_combined()` in CMakeLists.txt affect the `.uf2` output name?
   - **RESOLVED: `picowota_build_combined(textreader)` produces `textreader_combined.uf2` for the initial flash; OTA pushes app-only `textreader.uf2`.** CMakeLists.txt (17-03) invokes `picowota_build_combined(textreader)` so the BOOTSEL drag-and-drop artifact is `textreader_combined.uf2` (bootloader + app), while subsequent OTA updates push the smaller app-only `textreader.uf2`. The README documents both filenames and which to flash when. The executor verifies the combined artifact name after the first CMake configure.

---

## Sources

### Primary (MEDIUM confidence)
- [CITED: mongoose.ws/documentation/tutorials/rp2040/pico-w/] — Pico W integration tutorial, mongoose_config.h settings
- [CITED: raspberrypi.com/documentation/pico-sdk/networking.html] — CYW43 init functions, pico_cyw43_arch CMake targets
- [CITED: github.com/DaveGamble/cJSON] — cJSON 1.7.19, MIT license, parse API
- [CITED: github.com/cesanta/mongoose/releases] — Mongoose 7.22 confirmed (Jun 2026)
- [CITED: github.com/usedbytes/picowota] — OTA bootloader, picowota_reboot API
- [CITED: kotlinlang.org/api/kotlinx.serialization] — explicitNulls, EncodeDefault.Mode.NEVER
- [CITED: docs.platformio.org/en/stable/integration/ci/github-actions.html] — PlatformIO CI workflow
- [CITED: registry.platformio.org/libraries/zinggjm/GxEPD2] — GxEPD2 for e-paper
- [VERIFIED: locally installed] — pico-sdk 2.2.0 at `$PICO_SDK_PATH`, pico2_w.h confirmed present
- [VERIFIED: locally installed] — cmake 4.2.1, arm-none-eabi-gcc 13.2.1, Python 3.12.3 on build machine
- [VERIFIED: read from codebase] — `settings.gradle.kts` uses no wildcard include; firmware/ will be ignored by Gradle
- [VERIFIED: read from codebase] — `FirmwareMessage.kt` current structure (4 fields, all non-null)
- [VERIFIED: read from codebase] — `Font.kt` 5-column bitmap format, byteArrayOf values
- [VERIFIED: read from codebase] — `RequestValidators.kt` — validation pattern to follow
- [VERIFIED: read from codebase] — `FirmwareZoneDriver.kt` — send() signature and channel/session pattern
- [VERIFIED: read from codebase] — `FirmwareZoneDriverTest.kt` — existing test patterns

### Secondary (LOW confidence — web search)
- [ASSUMED] — ArduinoWebsockets API, platformio.ini lib_dep names
- [ASSUMED] — WiFiManager autoConnect behavior, NVS Preferences usage
- [ASSUMED] — ArduinoOTA port 3232, coexistence with ArduinoWebsockets
- [ASSUMED] — GitHub Actions pico-sdk workflow (apt packages, cmake flags)
- [ASSUMED] — MD_MAX72XX / MD_Parola PlatformIO lib_deps and versions

---

## Metadata

**Confidence breakdown:**
- Server-side changes (FirmwareMessage, TextRequest, RequestValidators): HIGH — read from actual codebase
- Pico SDK + toolchain (cmake, gcc, PICO_BOARD, pico2_w): HIGH — verified locally installed
- Mongoose integration: MEDIUM — official Pico tutorial cited; exact mg_ws_connect signature [ASSUMED]
- cJSON integration: MEDIUM — official README cited; FetchContent pattern [ASSUMED]
- ESP32 / PlatformIO libraries: LOW — library names, API signatures all [ASSUMED] from web search
- OTA approaches: LOW — picowota approach [ASSUMED] from GitHub README; physical test deferred
- Captive portal: LOW — WiFiManager well-documented; Pico AP+HTTP DNS caveat [ASSUMED]
- Display driver list: LOW — library existence confirmed for major ones; minor displays [ASSUMED]

**Research date:** 2026-06-24
**Valid until:** 2026-07-24 (30 days for embedded ecosystem; Mongoose releases infrequently)
