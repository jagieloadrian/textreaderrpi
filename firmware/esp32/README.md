# TextReader ESP32 Firmware

Firmware for ESP32 that drives a MAX7219 LED matrix display over SPI,
receives text + effect commands from the TextReader server via WebSocket, and
provisions WiFi credentials through a captive portal on first boot.

Built with ESP-IDF (CMake-native). No PlatformIO or Arduino framework required.

## Toolchain Requirements

| Tool | Minimum version | Notes |
|------|----------------|-------|
| ESP-IDF | 5.2.1 | Install via `install.sh` or `install.bat` from the ESP-IDF repository |
| CMake | 3.16+ | Bundled with ESP-IDF |
| Python | 3.8+ | Required by ESP-IDF tooling |

Set `IDF_PATH` to the root of your ESP-IDF installation before building.

## MAX7219 Wiring

| ESP32 GPIO | MAX7219 Pin | Signal |
|------------|-------------|--------|
| GPIO 23 (MOSI) | DIN | Data in |
| GPIO 18 (SCK) | CLK | SPI clock |
| GPIO 5 (CS) | LOAD/CS | Chip select |
| 3.3V | VCC | Power (use level-shifter for 5V-only modules) |
| GND | GND | Ground |

Chain additional MAX7219 modules by connecting DOUT of one to DIN of the next.
Update `NUM_DEVICES` in `main/config.h` to match the chain length.

**Note:** Some MAX7219 modules require 5V for the VCC pin. Connect 5V to VCC
and use a level-shifter for DIN/CLK/CS if the module does not tolerate 3.3V logic.

## Building

```sh
. $IDF_PATH/export.sh          # or export.ps1 on Windows

cmake -S . -B build -DIDF_TARGET=esp32
cmake --build build
```

## Flashing

```sh
cmake --build build --target flash
```

This uses esptool.py (bundled with ESP-IDF) to flash over USB at the auto-detected
port. To specify a port explicitly, pass `-DFLASH_PORT=/dev/ttyUSB0` to the cmake
configure step.

Serial monitor:

```sh
cmake --build build --target monitor
```

## First-Boot WiFi Provisioning

When no WiFi credentials are stored the firmware starts the captive portal:

1. On your phone or laptop, connect to the WiFi network named `TextReader-Setup`
   (open network, no password).
2. A captive portal page opens automatically on mobile. On desktop, navigate to
   `http://192.168.4.1` in a browser.
3. Enter your WiFi network name and password, then click Save.
4. The firmware saves credentials to NVS and reboots into STA mode.
5. Reconnect your device to your regular WiFi network.

To clear stored credentials and re-run provisioning, erase NVS:

```sh
cmake --build build --target erase_flash
cmake --build build --target flash
```

## OTA Updates

Send `{"command":"ota"}` from the TextReader server to trigger an OTA update.
The firmware logs the trigger and reboots into the OTA partition. Full OTA
push implementation is planned for a future phase.

## Configuration

Edit `main/config.h` before building:

| Constant | Default | Description |
|----------|---------|-------------|
| `SERVER_HOST` | `""` | TextReader server hostname or IP |
| `SERVER_PORT` | 8080 | TextReader server WebSocket port |
| `ZONE_ID` | `"esp32"` | Zone identifier sent in WebSocket path |
| `NUM_DEVICES` | 4 | Number of chained MAX7219 modules |
| `RECONNECT_INTERVAL_MS` | 5000 | WebSocket reconnect interval (ms) |
| `SPI_CS_PIN` | 5 | GPIO for MAX7219 CS |
| `SPI_CLK_PIN` | 18 | GPIO for MAX7219 CLK |
| `SPI_MOSI_PIN` | 23 | GPIO for MAX7219 DIN |

## Effects

| Effect name | Description |
|-------------|-------------|
| `SCROLL` | Text scrolls left; `speed` controls animation interval in ms |
| `BLINK` | Display toggles on/off; `blinkPeriod` controls half-period in ms |
| `REVERSE` | Text rendered reversed and scrolled; `speed` controls interval |
| `FADE` | Intensity ramps up then down; `fadeSteps` controls ramp granularity |
