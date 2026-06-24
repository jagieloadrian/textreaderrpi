---
plan: 17-08
phase: 17-firmware-skeletons
status: complete
commit: 0c00086
---

## What Was Built

10 additional display drivers for ESP32 under `firmware/esp32/src/driver/`:

| Driver | Library used | Notes |
|--------|-------------|-------|
| SSD1306 | Adafruit_SSD1306 | Hardware scroll cmd; contrast cmd for FADE; invertDisplay for BLINK |
| SSD1309 | Adafruit_SSD1306 (compatible) | Identical to SSD1306 driver |
| SSD1327 | Adafruit_SSD1327 | setTextColor ramp for FADE |
| SH1106 | U8g2 (U8G2_SH1106_128X64_NONAME_F_4W_HW_SPI) | u8g2_font_5x8_tf; setContrast for BLINK/FADE |
| HT16K33 | Adafruit_LEDBackpack (Adafruit_8x8matrix) | Hardware blinkRate for BLINK; setBrightness ramp for FADE |
| ST7735 | Adafruit_ST7735 | 565 color fade; setCursor with negative x for SCROLL |
| ST7789 | Adafruit_ST7789 | Same 565 pattern, 240px width |
| ILI9225 | TFT_22_ILI9225 | drawText API; 565 fade |
| PCD8544 | Adafruit_PCD8544 | setContrast ramp for FADE |
| SSD1680 | GxEPD2_BW (GxEPD2_213_B74) | FreeMonoBold9pt7b font; full page refresh; SCROLL rate-limited by speed_ms |

All drivers implement SCROLL, BLINK, REVERSE, FADE via display_init/display_clear/display_text.
Each .cpp is entirely wrapped in `#ifdef DISPLAY_DRIVER_XXX` — only one compiles per build.

**display.cpp updated**: #ifdef chain — non-MAX7219 drivers define their own symbols; MAX7219 is the default `else` fallback delegating to max7219_*.

**platformio.ini updated**:
- `[env:base]` with shared lib_deps (all display libraries + U8g2 + TFT_22_ILI9225 + LED Backpack + PCD8544)
- `[env:esp32dev]`, `[env:esp32-s3]`, `[env:esp32-s2]`, `[env:esp32-c3]`, `[env:esp32-c2]`, `[env:esp32-h2]`
- All default to `build_flags = -DDISPLAY_DRIVER_MAX7219`
- ESP32-H2 comment: 802.15.4 only (no WiFi) — use C3/S2/S3 for WiFi
- ESP32-C2 comment: no hardware FPU

## Artifacts

- `firmware/esp32/src/driver/{ssd1306,ssd1309,ssd1327,sh1106,ht16k33,st7735,st7789,ili9225,pcd8544,ssd1680}.{h,cpp}`
- `firmware/esp32/src/driver/display.cpp` (updated with #ifdef dispatcher)
- `firmware/esp32/platformio.ini` (updated with multi-board envs)
