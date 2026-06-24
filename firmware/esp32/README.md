# TextReader ESP32 Firmware

Firmware for ESP32 (esp32dev) that drives a MAX7219 LED matrix display over SPI,
receives text + effect commands from the TextReader server via WebSocket, and
provisions WiFi credentials through a captive portal on first boot using WiFiManager.

## Toolchain Requirements

| Tool | Minimum version | Notes |
|------|----------------|-------|
| PlatformIO | latest | `pip install platformio` or IDE plugin |
| espressif32 platform | 6.x | Installed automatically by PlatformIO |
| Python | 3.8+ | Required by PlatformIO |

**lib_deps verification:** Before building, verify the lib_deps names in
`platformio.ini` match the current PlatformIO registry. The registry occasionally
renames packages. Use `pio pkg search <name>` to confirm. The lib_deps in this
project were pinned to known registry authors (gilmaimon, bblanchon, majicdesigns,
tzapu, adafruit, zinggjm) at time of writing.

## MAX7219 Wiring

| ESP32 GPIO | MAX7219 Pin | Signal |
|------------|-------------|--------|
| GPIO 23 (MOSI) | DIN | Data in |
| GPIO 18 (SCK) | CLK | SPI clock |
| GPIO 5 (CS) | LOAD/CS | Chip select |
| 3.3V | VCC | Power (use level-shifter for 5V-only modules) |
| GND | GND | Ground |

Chain additional MAX7219 modules by connecting DOUT of one to DIN of the next.
Update `NUM_DEVICES` in `config.h` to match the chain length.

**Note:** Some MAX7219 modules require 5V for the VCC pin. Connect 5V to VCC
and use a level-shifter for DIN/CLK/CS if the module does not tolerate 3.3V logic.

## Building

```
pio run -e esp32dev
```

To build and upload in one step:

```
pio run -e esp32dev -t upload
```

Serial monitor:

```
pio device monitor -b 115200
```

## Flash Procedure

1. Connect the ESP32 to your computer via USB.
2. Run `pio run -e esp32dev -t upload` — PlatformIO detects the port automatically.
3. If the port is not found, list ports with `pio device list` and specify with
   `--upload-port /dev/ttyUSB0` (Linux) or `--upload-port COM3` (Windows).

## First-Boot WiFi Provisioning

When no WiFi credentials are stored the firmware starts the WiFiManager captive portal:

1. On your phone or laptop, connect to the WiFi network named `TextReader-Setup`
   (open network, no password).
2. A captive portal page opens automatically on mobile. On desktop, navigate to
   `http://192.168.4.1` in a browser.
3. Select your WiFi network and enter the password, then tap Save.
4. WiFiManager saves the credentials to NVS and the firmware reboots into STA mode.
5. Reconnect your device to your regular WiFi network.

To clear stored credentials and re-run provisioning, erase NVS by flashing a blank
partition: `pio run -t erase` then re-flash normally.

## OTA Updates

ArduinoOTA is enabled. Once the ESP32 is on your network:

```
pio run -e esp32dev -t upload --upload-port <device-ip>
```

PlatformIO uses the ArduinoOTA protocol over TCP port 3232. The device hostname
is set to the value of `ZONE_ID` in `config.h`.

The WebSocket server can also trigger OTA by sending the JSON message
`{"command":"ota"}` — the firmware calls `ArduinoOTA.begin()` and awaits
a push from any OTA-compatible client.

**IMPORTANT:** Do not add `ESPAsyncWebServer` as a dependency. It shares the TCP
stack resources with ArduinoWebsockets in a way that causes heap corruption during
OTA. The project uses synchronous ArduinoWebsockets only.

## Configuration

Edit `config.h` before building:

| Constant | Default | Description |
|----------|---------|-------------|
| `WIFI_SSID` | `""` | Fallback SSID (WiFiManager used on first boot) |
| `WIFI_PASS` | `""` | Fallback password |
| `SERVER_HOST` | `""` | TextReader server hostname or IP |
| `SERVER_PORT` | 8080 | TextReader server WebSocket port |
| `ZONE_ID` | `"esp32"` | Zone identifier sent in messages + OTA hostname |
| `NUM_DEVICES` | 4 | Number of chained MAX7219 modules |
| `RECONNECT_INTERVAL_MS` | 5000 | WebSocket reconnect interval (ms) |
| `MD_CS_PIN` | 5 | GPIO for MAX7219 CS |
| `MD_CLK_PIN` | 18 | GPIO for MAX7219 CLK |
| `MD_MOSI_PIN` | 23 | GPIO for MAX7219 DIN |

## Effects

| Effect name | Description |
|-------------|-------------|
| `SCROLL` | Text scrolls left; `speed` controls animation interval in ms |
| `BLINK` | Display toggles on/off; `blinkPeriod` controls half-period in ms |
| `REVERSE` | Text rendered reversed and scrolled; `speed` controls interval |
| `FADE` | Intensity ramps up then down; `fadeSteps` controls ramp granularity |
