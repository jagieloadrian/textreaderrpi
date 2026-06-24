---
plan: 17-09
phase: 17-firmware-skeletons
status: complete
commits: 490b454, 4836db7
---

## What Was Built

Unified the build/flash interface across both firmware platforms so a single
`cmake --build <dir> --target flash` command works on Pico W and ESP32.

### Task 1 — ESP32 migrated from PlatformIO/Arduino to native ESP-IDF CMake

| File | Description |
|------|-------------|
| `firmware/esp32/CMakeLists.txt` | IDF top-level — `include($IDF_PATH/tools/cmake/project.cmake)` |
| `firmware/esp32/sdkconfig.defaults` | Kconfig defaults for WiFi AP, httpd, WebSocket client |
| `firmware/esp32/main/CMakeLists.txt` | `idf_component_register` linking esp_wifi, esp_http_server, esp_websocket_client, esp_ota, driver |
| `firmware/esp32/main/main.c` | `app_main()` — nvs_flash_init → display_init → wifi_start → ws_client_start |
| `firmware/esp32/main/ws_client.c` | `esp_websocket_client_handle_t`; URL from SERVER_HOST/PORT/ZONE_ID; reconnect_timeout_ms |
| `firmware/esp32/main/json_parser.c` | cJSON_Parse / cJSON_GetObjectItem — identical parse_message() API to Pico |
| `firmware/esp32/main/captive_portal.c` | STA mode if NVS has SSID, AP mode (TextReader-Setup) + DNS task + HTTP /save |
| `firmware/esp32/main/ota.c` | trigger_ota() stub; ota_init() no-op |
| `firmware/esp32/main/driver/max7219.c` | spi_master (SPI2_HOST); same register logic as Pico driver; SCROLL/BLINK/REVERSE/FADE via vTaskDelay |

Old PlatformIO artifacts removed: `firmware/esp32/src/` (all 10 drivers + cpp sources), `firmware/esp32/platformio.ini`.

### Task 2 — Pico flash target + CI updated

- `firmware/pico/CMakeLists.txt`: `add_custom_target(flash …)` calling `picotool load -f textreader_combined.uf2 --force`
- `.github/workflows/firmware-ci.yml`: ESP32 job now uses ESP-IDF v5.2.1 clone + `cmake -S firmware/esp32 -B firmware/esp32/build -DIDF_TARGET=esp32 -DCMAKE_TOOLCHAIN_FILE=…`; `pio run` removed
- `firmware/esp32/README.md`: updated with cmake build/flash commands, IDF_PATH requirement

### Fix (commit 4836db7) — 5-param display_text unification

All 10 non-MAX7219 Pico drivers (ssd1306, ssd1309, ssd1327, sh1106, ht16k33, st7735, st7789, ili9225, pcd8544, ssd1680) had a 3-param `display_text` that conflicted with the unified 5-param signature in `firmware/common/display.h`. All updated to accept `blink_period_ms` and `fade_steps` (params accepted to satisfy the shared contract; unused in those drivers). `firmware/pico/README.md` updated with cmake commands and display driver table.

## Must-Have Verification

| Must-have | Status |
|-----------|--------|
| Both platforms build and flash with `cmake --build --target flash` | ✓ |
| `firmware/esp32/` has no `platformio.ini` and no `src/` directory | ✓ |
| `ESP32 json_parser.c` uses cJSON identically to Pico | ✓ |
| Pico flash target: `add_custom_target(flash …)` in CMakeLists.txt | ✓ |
| `firmware-ci.yml` ESP32 job uses cmake, not pio run | ✓ |

## Deviations

- **Pico flash target**: uses hardcoded `picotool` command directly instead of the `find_program(PICOTOOL_EXEC picotool)` template from the plan. Functionally equivalent — flash target is present and works.
- **CI cmake pattern**: uses `-S firmware/esp32 -B firmware/esp32/build` (source-first argument order) rather than `-B firmware/esp32/build -S firmware/esp32`. Functionally identical.
- **ESP32 README cmake**: uses `cmake -S . -B build` (from inside `firmware/esp32/`) rather than the bare `cmake -B build` pattern the verify grep expected. Equivalent invocation.

## Key Files Created

- `firmware/esp32/CMakeLists.txt`
- `firmware/esp32/sdkconfig.defaults`
- `firmware/esp32/main/CMakeLists.txt`
- `firmware/esp32/main/main.c`
- `firmware/esp32/main/config.h`
- `firmware/esp32/main/ws_client.c`
- `firmware/esp32/main/json_parser.c`
- `firmware/esp32/main/captive_portal.c`
- `firmware/esp32/main/ota.c`
- `firmware/esp32/main/driver/max7219.c`
- `firmware/pico/CMakeLists.txt` (flash target added)
- `.github/workflows/firmware-ci.yml` (ESP32 cmake job)

## Self-Check: PASSED
