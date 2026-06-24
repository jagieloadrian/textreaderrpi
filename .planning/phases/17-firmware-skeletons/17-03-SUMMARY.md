---
plan: 17-03
phase: 17-firmware-skeletons
status: complete
commit: 9c18135
---

## What Was Built

Complete RPi Pico W firmware project skeleton at `firmware/pico/`:

**Build system**
- `CMakeLists.txt`: cmake 3.13+, pico-sdk init, cJSON v1.7.19 via FetchContent, picowota submodule, single `add_executable(textreader ...)`, lwIP threadsafe background, pico_add_extra_outputs + picowota_build_combined
- `pico_sdk_import.cmake`: standard SDK bootstrap from $PICO_SDK_PATH
- `config.h`: all tunables — BOARD_TYPE, WIFI_SSID/PASS, SERVER_HOST/PORT(8080), ZONE_ID("pico"), RECONNECT_INTERVAL_MS(5000), NUM_DEVICES(4), FLASH offsets

**Display**
- `src/driver/display.h`: 3-function interface (display_init, display_clear, display_text)
- `src/driver/max7219.h` + `max7219.c`: full SPI driver with SCROLL (column shift), BLINK (on/off toggle), REVERSE (reversed direction), FADE (intensity ramp); speed clamped to [1, 5000] ms
- `font/font5x8.h`: 96-entry static table, ASCII 0x20–0x7F, 5 columns per char

**Networking**
- `lib/mongoose/mongoose.h` + `mongoose.c`: Mongoose 7.21 two-file drop-in (downloaded from github.com/cesanta/mongoose tag 7.21; tag 7.22 does not exist — used latest available)
- `src/mongoose_config.h`: MG_ARCH=MG_ARCH_PICOSDK, MG_ENABLE_LWIP=1
- `src/ws_client.h` + `ws_client.c`: Mongoose WS event handler (MG_EV_WS_MSG → parse_and_display, MG_EV_CLOSE → s_connected=false); reconnect loop shows "Connecting..." BLINK
- `src/json_parser.h` + `json_parser.c`: cJSON_Parse → null check → OTA branch (trigger_ota) → text/effect/speed/blinkPeriod/fadeSteps extraction → display_text → cJSON_Delete

**OTA + provisioning**
- `src/ota.h` + `ota.c`: trigger_ota() calls picowota_reboot(true)
- `src/captive_portal.h` + `captive_portal.c`: AP mode via cyw43_arch_enable_ap_mode("TextReader-Setup", NULL, CYW43_AUTH_OPEN); Mongoose HTTP on port 80; exact UI-SPEC FORM_HTML and SUCCESS_HTML; POST /save reads ssid/pass → flash_range_erase + flash_range_program with interrupts disabled → SCB reset
- `src/main.c`: stdio_init_all → cyw43_arch_init → load_creds from flash → captive_portal_start() if no creds else STA mode + wifi_connect → display_init → ws_task

**Documentation**
- `README.md` (93 lines): toolchain requirements table, MAX7219 wiring pin table, build commands for both boards, first-flash procedure (combined.uf2 via BOOTSEL), OTA update procedure, first-boot captive portal steps, config.h reference table, effects table

## Key Decisions

- Mongoose 7.21 used (7.22 tag does not exist in cesanta/mongoose; 7.21 is the latest available)
- picowota `add_subdirectory` left active in CMakeLists.txt (not commented out) — picowota_reboot target required by ota.c
- cJSON fetched via FetchContent rather than bundled (keeps tree clean, version pinned)
- SCB reset (`PPB_BASE + 0x0ED0C = 0x05FA0004`) used in captive_portal after flash write instead of watchdog, consistent with pico-sdk bare-metal pattern

## Artifacts

- `firmware/pico/CMakeLists.txt`
- `firmware/pico/pico_sdk_import.cmake`
- `firmware/pico/config.h`
- `firmware/pico/src/main.c`
- `firmware/pico/src/ws_client.{h,c}`
- `firmware/pico/src/json_parser.{h,c}`
- `firmware/pico/src/ota.{h,c}`
- `firmware/pico/src/captive_portal.{h,c}`
- `firmware/pico/src/mongoose_config.h`
- `firmware/pico/src/driver/display.h`
- `firmware/pico/src/driver/max7219.{h,c}`
- `firmware/pico/font/font5x8.h`
- `firmware/pico/lib/mongoose/mongoose.{h,c}`
- `firmware/pico/README.md`
