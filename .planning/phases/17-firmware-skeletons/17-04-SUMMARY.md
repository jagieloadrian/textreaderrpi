---
plan: 17-04
phase: 17-firmware-skeletons
status: complete
commit: 94234d7
---

## What Was Built

Complete ESP32 firmware project skeleton at `firmware/esp32/`:

**Build system**
- `platformio.ini`: [env:esp32dev], platform espressif32, framework arduino, lib_deps — gilmaimon/ArduinoWebsockets, bblanchon/ArduinoJson, majicdesigns/MD_MAX72XX, majicdesigns/MD_Parola, tzapu/WiFiManager, Adafruit display libs, zinggjm/GxEPD2; build_flags = -DDISPLAY_DRIVER_MAX7219
- `config.h`: BOARD_TYPE, DISPLAY_DRIVER, WIFI_SSID/PASS, SERVER_HOST/PORT(8080), ZONE_ID("esp32"), RECONNECT_INTERVAL_MS(5000), NUM_DEVICES(4), MD_CS/CLK/MOSI_PIN

**Display**
- `src/driver/display.h`: same 3-function interface as Pico (display_init, display_clear, display_text)
- `src/driver/display.cpp`: thin adapter delegating to max7219_*
- `src/driver/max7219.h` + `max7219.cpp`: MD_Parola over MD_MAX72XX with all 4 effects — SCROLL (PA_SCROLL_LEFT), BLINK (on/off toggle with blinkPeriod), REVERSE (reversed string + PA_SCROLL_LEFT), FADE (intensity ramp via MD_MAX72XX::INTENSITY); speed clamped to [1, 5000] ms

**Networking**
- `src/ws_client.h` + `ws_client.cpp`: WebsocketsClient with onMessage→parse_message, onEvent tracking s_connected; ws_loop() reconnect shows "Connecting..." BLINK, delays RECONNECT_INTERVAL_MS, retries setup_ws
- `src/json_parser.h` + `json_parser.cpp`: ArduinoJson 7 JsonDocument + deserializeJson; checks "command":"ota" branch first (calls trigger_ota), then reads text/effect/speed/blinkPeriod/fadeSteps with defaults via `| default`; routes speed_ms per effect type to display_text

**OTA + provisioning**
- `src/ota.h` + `ota.cpp`: setup_ota() sets hostname=ZONE_ID + ArduinoOTA.begin(); trigger_ota() restarts ArduinoOTA for push accept
- `src/captive_portal.h` + `captive_portal.cpp`: WiFiManager.autoConnect("TextReader-Setup") — stores NVS creds automatically; ESP.restart() on failure
- `src/main.cpp`: Serial.begin → setup_wifi → display_init → setup_ota → setup_ws; loop: ArduinoOTA.handle() + ws_loop(); ESPAsyncWebServer excluded per Pitfall 3

**Documentation**
- `README.md` (120 lines): PlatformIO toolchain table (pip install, esp32dev platform, Python), lib_deps verification note (pio pkg search), MAX7219 wiring pin table (GPIO 23/18/5), build + upload + monitor commands, first-boot provisioning steps, OTA push command, ESPAsyncWebServer warning, config.h reference table, effects table

## Key Decisions

- ArduinoJson 7 JsonDocument used (not deprecated DynamicJsonDocument)
- ESPAsyncWebServer explicitly excluded — heap corruption with ArduinoOTA (Pitfall 3)
- WiFiManager handles NVS credential storage (State of the Art over SPIFFS)
- FADE implemented via MD_MAX72XX::INTENSITY ramp (MD_Parola has no native fade effect)
- display.cpp adapter file added for clean interface separation (not in plan files_modified but required for the build)

## Artifacts

- `firmware/esp32/platformio.ini`
- `firmware/esp32/config.h`
- `firmware/esp32/src/main.cpp`
- `firmware/esp32/src/ws_client.{h,cpp}`
- `firmware/esp32/src/json_parser.{h,cpp}`
- `firmware/esp32/src/ota.{h,cpp}`
- `firmware/esp32/src/captive_portal.{h,cpp}`
- `firmware/esp32/src/driver/display.{h,cpp}`
- `firmware/esp32/src/driver/max7219.{h,cpp}`
- `firmware/esp32/README.md`
