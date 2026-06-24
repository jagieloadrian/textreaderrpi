# TextReader Pico W Firmware

Firmware for Raspberry Pi Pico W and Pico 2W that drives a MAX7219 LED matrix
display over SPI, receives text + effect commands from the TextReader server via
WebSocket, and provisions WiFi credentials through a captive portal on first boot.

## Toolchain Requirements

| Tool | Minimum version | Notes |
|------|----------------|-------|
| CMake | 3.13 | Build system |
| arm-none-eabi-gcc | 10.3 | Embedded cross-compiler |
| pico-sdk | 2.x | Set `PICO_SDK_PATH` env var |
| picowota | git submodule | OTA bootloader (init with `git submodule update --init`) |
| cJSON | 1.7.19 | Fetched automatically via FetchContent |
| Mongoose | 7.21 | Bundled in `lib/mongoose/` — two-file drop-in |

## Building

```
mkdir build && cd build
cmake .. -DPICO_BOARD=pico_w     # or pico2_w for Pico 2W
cmake --build . -j$(nproc)
```

Outputs produced in `build/`:
- `textreader.uf2` — application image (used for OTA updates after first flash)
- `textreader_combined.uf2` — bootloader + application (required for first flash)

## MAX7219 Wiring

| Pico W GPIO | MAX7219 Pin | Signal |
|-------------|-------------|--------|
| GP18 (SPI0 SCK) | CLK | SPI clock |
| GP19 (SPI0 TX) | DIN | Data in |
| GP17 (SPI0 CSn) | LOAD/CS | Chip select |
| 3V3 (pin 36) | VCC | Power |
| GND (pin 38) | GND | Ground |

Chain additional MAX7219 modules by connecting DOUT of one to DIN of the next.
Update `NUM_DEVICES` in `config.h` to match the chain length.

## Flash Procedure

### First Flash (physical access required)

1. Hold the BOOTSEL button on the Pico W while connecting USB.
2. The board mounts as a USB mass storage device.
3. Copy `textreader_combined.uf2` to the drive — this installs both the OTA
   bootloader and the application.
4. The board reboots automatically after the copy completes.

### OTA Updates (over WiFi, after first flash)

1. The server sends `{"cmd":"ota"}` over the WebSocket connection.
2. The firmware calls `picowota_reboot(true)` to enter the bootloader.
3. Use the picowota upload tool to push `textreader.uf2` over the network.

## First-Boot WiFi Provisioning

When no WiFi credentials are stored in flash the firmware starts in AP mode:

1. On your phone or laptop, connect to the WiFi network named `TextReader-Setup`
   (open network, no password).
2. Open a browser and navigate to `http://192.168.4.1` (the page may appear
   automatically as a captive portal on mobile devices).
3. Enter your WiFi network name (SSID) and password, then tap Connect to WiFi.
4. The board saves credentials to flash and reboots into STA mode.
5. Reconnect your device to your regular WiFi network.

## Configuration

Edit `config.h` before building to override defaults:

| Constant | Default | Description |
|----------|---------|-------------|
| `WIFI_SSID` | `""` | Fallback SSID if flash creds fail |
| `WIFI_PASS` | `""` | Fallback password |
| `SERVER_HOST` | `""` | TextReader server hostname or IP |
| `SERVER_PORT` | 8080 | TextReader server WebSocket port |
| `ZONE_ID` | `"pico"` | Zone identifier sent in messages |
| `NUM_DEVICES` | 4 | Number of chained MAX7219 modules |
| `RECONNECT_INTERVAL_MS` | 5000 | WebSocket reconnect interval (ms) |
| `FLASH_CRED_OFFSET` | 256 KB | Flash offset for stored credentials |

## Effects

| Effect name | Description |
|-------------|-------------|
| `SCROLL` | Columns shift left; `speed` controls shift interval in ms |
| `BLINK` | Display toggles on/off; `blinkPeriod` controls half-period in ms |
| `REVERSE` | Text rendered right-to-left; `speed` controls interval in ms |
| `FADE` | Intensity ramps up then down; `fadeSteps` controls ramp step count |
