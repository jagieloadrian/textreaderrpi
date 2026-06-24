---
plan: 17-07
phase: 17-firmware-skeletons
status: complete
commit: bcd36e4
---

## What Was Built

10 additional display drivers for Pico W/2W under `firmware/pico/src/driver/`:

| Driver | Type | Interface | Notes |
|--------|------|-----------|-------|
| SSD1306 | 128x64 OLED | I2C (i2c0, GP4/5, 0x3C) | Contrast ramp for FADE; on/off cmd for BLINK; hardware SCROLL via framebuffer shift |
| SSD1309 | 128x64 OLED | I2C (SSD1306-compatible) | Identical init/effect pattern to SSD1306 |
| SSD1327 | 128x128 grayscale OLED | I2C (0x3C) | 4bpp pixel format; contrast cmd 0x81 for FADE |
| SH1106 | 132x64 OLED | SPI (spi0, CS=17, DC=20, RST=21) | 2-column offset (SH1106 has 132 cols); page-by-page write |
| HT16K33 | 8x8 LED matrix | I2C (0x70) | Hardware blink register for BLINK; brightness register 0xE0..0xEF for FADE |
| ST7735 | 128x160 color TFT | SPI (spi0) | 16bpp 565 color; intensity ramp via 565 grayscale for FADE |
| ST7789 | 240x240 color TFT | SPI (spi0) | Same 565 pattern as ST7735 at 240px width; INVON in init |
| ILI9225 | 176x220 color TFT | SPI (2-byte register writes) | Reg 0x07 display on/off for BLINK; 565 fade |
| PCD8544 | 84x48 monochrome | SPI (Nokia 5110) | Vop ramp (0x21 + 0x80|v) for FADE; 0x09/0x0C all-on/normal for BLINK |
| SSD1680 | 250x122 e-paper | SPI (CS=17, DC=20, RST=21, BUSY=22) | 1bpp framebuffer; partial refresh for SCROLL; 2x full refresh for BLINK |

All drivers implement SCROLL, BLINK, REVERSE, FADE via display_init/display_clear/display_text.
All wrap implementation in `#ifdef DISPLAY_DRIVER_XXX`.

**CMakeLists.txt updated** with if/elseif chain selecting exactly one driver source:
- I2C drivers (SSD1306/1309/1327/HT16K33) link `hardware_i2c`
- SPI drivers link `hardware_spi hardware_gpio`
- `cmake -DDISPLAY_DRIVER=ST7789 ..` selects ST7789 and excludes all others

## Artifacts

- `firmware/pico/src/driver/{ssd1306,ssd1309,ssd1327,sh1106,ht16k33,st7735,st7789,ili9225,pcd8544,ssd1680}.{h,c}`
- `firmware/pico/CMakeLists.txt` (updated with driver selection block)
