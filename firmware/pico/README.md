# TextReader Pico W Firmware

Firmware for Raspberry Pi Pico W and Pico 2W that drives a display over SPI or
I2C, receives text + effect commands from the TextReader server via WebSocket,
and provisions WiFi credentials through a captive portal on first boot.

## Toolchain Requirements

| Tool | Minimum version | Notes |
|------|----------------|-------|
| CMake | 3.13 | Build system |
| arm-none-eabi-gcc | 10.3 | Embedded cross-compiler |
| pico-sdk | 2.1.1 | Set `PICO_SDK_PATH` env var |
| picotool | latest | Required for `cmake --build --target flash` |
| picowota | git submodule | OTA bootloader — init with `git submodule update --init --recursive` |
| cJSON | 1.7.19 | Fetched automatically via FetchContent |
| Mongoose | 7.21 | Bundled in `lib/mongoose/` |

Shared headers (font, display interface, etc.) live in `firmware/common/` and are
included automatically — no extra setup needed.

## Building

```sh
export PICO_SDK_PATH=/path/to/pico-sdk

cmake -S firmware/pico -B firmware/pico/build -DPICO_BOARD=pico_w
cmake --build firmware/pico/build -j$(nproc)
```

Use `-DPICO_BOARD=pico2_w` for Pico 2W.

Outputs in `firmware/pico/build/`:
- `textreader.uf2` — application image (OTA updates)
- `textreader_combined.uf2` — bootloader + application (first flash)

## Flashing

```sh
cmake --build firmware/pico/build --target flash
```

Requires picotool on `PATH` and the Pico in BOOTSEL mode (hold button while
plugging USB). Flashes `textreader_combined.uf2` automatically.

### Manual first flash (drag-and-drop)

1. Hold BOOTSEL while connecting USB — the board mounts as a USB drive.
2. Copy `textreader_combined.uf2` to the drive.
3. The board reboots automatically.

## MAX7219 Wiring (default)

| Pico W GPIO | MAX7219 Pin | Signal |
|-------------|-------------|--------|
| GP18 (SPI0 SCK) | CLK | SPI clock |
| GP19 (SPI0 TX) | DIN | Data in |
| GP17 (SPI0 CSn) | LOAD/CS | Chip select |
| 3V3 (pin 36) | VCC | Power |
| GND (pin 38) | GND | Ground |

Chain additional modules via DOUT → DIN. Update `NUM_DEVICES` in `config.h`.

## I2C Display Wiring (SSD1306, SSD1309, SSD1327, HT16K33)

| Pico W GPIO | Display Pin |
|-------------|-------------|
| GP4 (I2C0 SDA) | SDA |
| GP5 (I2C0 SCL) | SCL |
| 3V3 | VCC |
| GND | GND |

## SPI Display Wiring (SH1106, ST7735, ST7789, ILI9225, PCD8544, SSD1680)

| Pico W GPIO | Display Pin |
|-------------|-------------|
| GP19 (SPI0 TX) | SDA / MOSI |
| GP18 (SPI0 SCK) | SCL / CLK |
| GP17 | CS |
| GP20 | DC / RS |
| GP21 | RST |
| GP22 | BUSY *(SSD1680 only)* |
| 3V3 | VCC |
| GND | GND |

## First-Boot WiFi Provisioning

When no credentials are stored in flash the firmware starts AP mode:

1. Connect to `TextReader-Setup` (open WiFi, no password).
2. Navigate to `http://192.168.4.1` (opens automatically as captive portal on mobile).
3. Enter SSID and password, tap **Connect to WiFi**.
4. Credentials are saved to flash and the board reboots in STA mode.

## OTA Updates

The server sends `{"command":"ota"}` over WebSocket; the firmware calls
`picowota_reboot(true)` and enters the bootloader. Push `textreader.uf2`
via picowota's upload tool over the network.

## Configuration

Edit `config.h` before building:

| Constant | Default | Description |
|----------|---------|-------------|
| `WIFI_SSID` | `""` | Fallback SSID if stored creds fail |
| `WIFI_PASS` | `""` | Fallback password |
| `SERVER_HOST` | `""` | TextReader server hostname or IP |
| `SERVER_PORT` | 8080 | WebSocket port |
| `ZONE_ID` | `"pico"` | Zone identifier in WebSocket path |
| `NUM_DEVICES` | 4 | Number of chained MAX7219 modules |
| `RECONNECT_INTERVAL_MS` | 5000 | WebSocket reconnect interval (ms) |
| `FLASH_CRED_OFFSET` | 256 KB | Flash offset for stored credentials |

## Display Drivers

Select at configure time with `-DDISPLAY_DRIVER=<NAME>`:

| Name | Interface | Notes |
|------|-----------|-------|
| `MAX7219` | SPI | Default |
| `SSD1306` | I2C | 128×64 OLED |
| `SSD1309` | I2C | 128×64 OLED |
| `SSD1327` | I2C | 128×128 grayscale OLED |
| `SH1106` | SPI | 132×64 OLED |
| `HT16K33` | I2C | 8×8 LED matrix |
| `ST7735` | SPI | 160×128 TFT |
| `ST7789` | SPI | 240×240 TFT |
| `ILI9225` | SPI | 220×176 TFT |
| `PCD8544` | SPI | Nokia 5110 LCD |
| `SSD1680` | SPI | E-ink |

## Effects

| Effect | Description |
|--------|-------------|
| `SCROLL` | Columns shift left; `speed` = ms per column |
| `BLINK` | Toggles on/off; `blinkPeriod` = half-period ms |
| `REVERSE` | Scrolls right-to-left; `speed` = ms per column |
| `FADE` | Intensity ramps up then down; `fadeSteps` = ramp steps |
