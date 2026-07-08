# TextReader ESP32 Firmware

Firmware for ESP32 family microcontrollers that drives a display over SPI or I2C,
receives text + effect commands from the TextReader server via WebSocket, and
provisions WiFi credentials through a captive portal on first boot.

Built with ESP-IDF (CMake-native). Supports ESP32, S2, S3, C2, C3, C6, and H2 chips.

## Toolchain Requirements

| Tool | Minimum version | Notes |
|------|----------------|-------|
| ESP-IDF | 5.2.1 | Install via `install.sh` / `install.bat` from the ESP-IDF repository |
| CMake | 3.16+ | Bundled with ESP-IDF |
| Python | 3.8+ | Required by ESP-IDF tooling |

Set `IDF_PATH` to the root of your ESP-IDF installation before building.

## Supported Chips

Select the target chip with `idf.py set-target` before configuring. Supported targets:

| Target | `idf.py set-target` argument |
|--------|------------------------------|
| ESP32 classic | `esp32` |
| ESP32-S2 | `esp32s2` |
| ESP32-S3 | `esp32s3` |
| ESP32-C2 | `esp32c2` |
| ESP32-C3 | `esp32c3` |
| ESP32-C6 | `esp32c6` |
| ESP32-H2 | `esp32h2` |

**Note:** ESP32-H2 has no WiFi (Zigbee/BLE only). WiFi provisioning will not work on H2.

## Display Drivers

Select at configure time with `-DDISPLAY_DRIVER=<NAME>` (default: `MAX7219`):

| Name | Interface | Notes |
|------|-----------|-------|
| `MAX7219` | SPI | LED matrix, default |
| `SSD1306` | I2C | 128×64 OLED |
| `SSD1309` | I2C | 128×64 OLED |
| `SSD1327` | I2C | 128×128 grayscale OLED |
| `SH1106` | SPI | 132×64 OLED |
| `HT16K33` | I2C | 8×8 LED matrix |
| `ST7735` | SPI | 160×128 TFT |
| `ST7789` | SPI | 240×240 TFT |
| `ILI9225` | SPI | 220×176 TFT |
| `PCD8544` | SPI | Nokia 5110 LCD |
| `SSD1680` | SPI | E-ink 250×122 |

## Building

```sh
. $IDF_PATH/export.sh          # or export.ps1 on Windows

# Set target chip (run once per build directory)
idf.py set-target esp32        # or esp32s3, esp32c3, etc.

# Build with default driver (MAX7219)
idf.py build

# Build with a specific driver
cmake -S . -B build -DDISPLAY_DRIVER=SSD1306
cmake --build build
```

## Flashing

```sh
idf.py flash
```

Or with an explicit port:

```sh
idf.py -p /dev/ttyUSB0 flash
```

Serial monitor:

```sh
idf.py monitor
```

## Configuration

Edit `main/config.h` before building:

| Constant | Default | Description |
|----------|---------|-------------|
| `SERVER_HOST` | `""` | TextReader server hostname or IP |
| `SERVER_PORT` | 8080 | TextReader server WebSocket port |
| `ZONE_ID` | `"esp32"` | Zone identifier sent in WebSocket path |
| `NUM_DEVICES` | 4 | Number of chained MAX7219 modules |
| `RECONNECT_INTERVAL_MS` | 5000 | WebSocket reconnect interval (ms) |

Pin defaults are set automatically per chip family and can be overridden in `config.h`:

| Constant | Description |
|----------|-------------|
| `SPI_MOSI_PIN` | SPI data out |
| `SPI_CLK_PIN` | SPI clock |
| `SPI_CS_PIN` | SPI chip select |
| `SPI_DC_PIN` | SPI data/command (display drivers with DC pin) |
| `SPI_RST_PIN` | SPI reset (display drivers with RST pin) |
| `SPI_BUSY_PIN` | Busy input (SSD1680 only) |
| `I2C_SDA_PIN` | I2C data (I2C display drivers) |
| `I2C_SCL_PIN` | I2C clock (I2C display drivers) |

Default pin assignments per chip family:

| Pin | ESP32 | S2 / S3 | C2 / C3 / C6 / H2 |
|-----|-------|---------|-------------------|
| `SPI_MOSI_PIN` | 23 | 35 | 7 |
| `SPI_CLK_PIN` | 18 | 36 | 6 |
| `SPI_CS_PIN` | 5 | 34 | 10 |
| `SPI_DC_PIN` | 2 | 37 | 3 |
| `SPI_RST_PIN` | 4 | 38 | 4 |
| `SPI_BUSY_PIN` | 15 | 33 | 5 |
| `I2C_SDA_PIN` | 21 | 8 | 8 |
| `I2C_SCL_PIN` | 22 | 9 | 9 |

## MAX7219 Wiring (default)

| ESP32 GPIO | MAX7219 Pin | Signal |
|------------|-------------|--------|
| `SPI_MOSI_PIN` | DIN | Data in |
| `SPI_CLK_PIN` | CLK | SPI clock |
| `SPI_CS_PIN` | LOAD/CS | Chip select |
| 3.3 V | VCC | Power |
| GND | GND | Ground |

Chain additional MAX7219 modules via DOUT → DIN. Update `NUM_DEVICES` in `config.h`.

**Note:** Some MAX7219 modules require 5 V on VCC. Use a level-shifter for
DIN/CLK/CS if the module does not tolerate 3.3 V logic.

## I2C Display Wiring (SSD1306, SSD1309, SSD1327, HT16K33)

| ESP32 GPIO | Display Pin |
|------------|-------------|
| `I2C_SDA_PIN` | SDA |
| `I2C_SCL_PIN` | SCL |
| 3.3 V | VCC |
| GND | GND |

## SPI Display Wiring (SH1106, ST7735, ST7789, ILI9225, PCD8544, SSD1680)

| ESP32 GPIO | Display Pin |
|------------|-------------|
| `SPI_MOSI_PIN` | SDA / MOSI |
| `SPI_CLK_PIN` | SCL / CLK |
| `SPI_CS_PIN` | CS |
| `SPI_DC_PIN` | DC / RS |
| `SPI_RST_PIN` | RST |
| `SPI_BUSY_PIN` | BUSY *(SSD1680 only)* |
| 3.3 V | VCC |
| GND | GND |

## First-Boot WiFi Provisioning

When no WiFi credentials are stored in NVS, the firmware starts the captive portal:

1. Connect to `TextReader-Setup` (open network, no password).
2. Navigate to `http://192.168.4.1` (opens automatically as captive portal on mobile).
3. Enter your SSID and password, then click **Save**.
4. Credentials are saved to NVS and the board reboots in STA mode.

To clear stored credentials and re-run provisioning:

```sh
idf.py erase-flash
idf.py flash
```

## OTA Updates

Send `{"command":"ota"}` from the TextReader server to trigger an OTA update.
The firmware logs the trigger and reboots into the OTA partition. Full OTA
push implementation is planned for a future phase.

## Effects

| Effect | Description |
|--------|-------------|
| `SCROLL` | Columns shift left; `speed` = ms per column |
| `BLINK` | Toggles on/off; `blinkPeriod` = half-period ms |
| `REVERSE` | Scrolls right-to-left; `speed` = ms per column |
| `FADE` | Intensity ramps up then down; `fadeSteps` = ramp steps |
