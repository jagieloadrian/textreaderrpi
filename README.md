<!-- generated-by: gsd-doc-writer -->
# TextReaderRpi

[![CI](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml/badge.svg)](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml)

Ktor service for rendering scrolling text and scheduled messages on Raspberry Pi displays (MAX7219, LCD, OLED) via Pi4J. Supports multi-zone output across local hardware and networked remote displays discovered via mDNS or UDP broadcast.

## Table of Contents

- [API](#api)
- [Firmware](#firmware)
- [Configuration](#configuration)
- [Build and Run](#build-and-run)
- [Deployment](#deployment)
- [Code Layout](#code-layout)
- [Notes](#notes)

---

## API

All endpoints live under `/api/v1` and are rate-limited (default 60 req/min).

### Text

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/text` | Send text immediately (`202 Accepted`) |
| `POST` | `/api/v1/text?zone={id}` | Send text to a specific zone |

```bash
# Broadcast to all zones
curl -X POST http://localhost:8080/api/v1/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello","effect":"SCROLL"}'

# Target a specific zone
curl -X POST "http://localhost:8080/api/v1/text?zone=main" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Zone message","effect":"BLINK"}'
```

Effects: `SCROLL` (default), `BLINK`, `REVERSE`, `FADE`

### Schedules

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/schedule` | List all schedules |
| `POST` | `/api/v1/schedule` | Create schedule |
| `GET` | `/api/v1/schedule/{id}` | Get schedule |
| `PATCH` | `/api/v1/schedule/{id}` | Update schedule |
| `POST` | `/api/v1/schedule/{id}/cancel` | Stop recurring/cron schedule (keeps record) |
| `DELETE` | `/api/v1/schedule/{id}` | Delete schedule |

**Trigger types:**
- `ONESHOT` — fires once at ISO-8601 timestamp: `"2026-06-01T12:00:00Z"`
- `RECURRING` — repeats on interval: `"5m"`, `"1h"`, `"30s"`, `"2d"`
- `CRON` — Unix cron expression: `"0 * * * *"`

```bash
curl -X POST http://localhost:8080/api/v1/schedule \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hourly reminder","triggerType":"CRON","triggerValue":"0 * * * *","effect":"SCROLL","priority":0}'
```

### Zones

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/zones` | List all zones with status |
| `POST` | `/api/v1/zones` | Register a remote zone by IP, or a firmware zone by type |
| `DELETE` | `/api/v1/zones/{id}` | Remove a remote zone |
| `POST` | `/api/v1/zones/discover` | Trigger UDP broadcast scan for remote displays |
| `WS` | `/ws/zone/{id}` | Inbound WebSocket for firmware nodes; `id` must already be registered as a `FIRMWARE` zone, or the server closes with `1003 CANNOT_ACCEPT` |

```bash
# Manually register a remote display by IP
curl -X POST http://localhost:8080/api/v1/zones \
  -H 'Content-Type: application/json' \
  -d '{"ip":"192.168.1.42"}'

# Register a firmware zone (no IP required — firmware connects inbound)
curl -X POST http://localhost:8080/api/v1/zones \
  -H 'Content-Type: application/json' \
  -d '{"name":"pico-01","type":"FIRMWARE"}'
# -> 201 Created

# Scan the local network for displays
curl -X POST http://localhost:8080/api/v1/zones/discover

# Firmware node connects here (not a browser live feed — see Live Feed below)
wscat -c ws://localhost:8080/ws/zone/pico-01
```

Remote zones connect over WebSocket and auto-reconnect on disconnect. mDNS (`_textreaderrpi._tcp.local.`) and UDP broadcast (port 54321) discovery run automatically at startup.

### History

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/history` | Paginated message history |
| `GET` | `/api/v1/history/export` | CSV export of message history (same filters as above) |

Query parameters: `page` (default 1), `size` (1–200, default 20), `effect`, `source`, `search`.

```bash
curl -o history.csv "http://localhost:8080/api/v1/history/export?effect=SCROLL&search=hello"
```

### Live Feed

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/live` | SSE stream of display events (`event: display`, 30s heartbeat, no auth) |

```bash
curl -N http://localhost:8080/api/v1/live
```

This SSE endpoint is a one-way browser live feed — distinct from the `/ws/zone/{id}` firmware-inbound WebSocket documented under Zones above.

### Display

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/display/status` | Driver status JSON |
| `POST` | `/api/v1/display/select` | Reserved — always returns `501 Not Implemented` |

### System

| Path | Description |
|---|---|
| `GET /health` | Liveness — `200` while process is running |
| `GET /health/ready` | Readiness — `200` when display driver active |
| `GET /health/detail` | Detailed health: uptime, memory, zone errors |
| `GET /metrics` | Runtime metrics JSON (JVM + API counters) |
| `GET /openapi` | Swagger UI |

### Web UI

A built-in HTML UI is served at the root. Key pages:

| Path | Description |
|---|---|
| `/` | Dashboard / index |
| `/status` | Live display status |
| `/settings/display` | Driver selection UI |
| `/schedules` | Schedule management UI |
| `/history` | Message history UI |
| `/zones` | Zone management UI |

---

## Firmware

Complete Linux flash path for both supported firmware targets: install the toolchain, wire the display, configure `config.h`, build, and flash — the zone then appears on the server once it connects.

### Pico (RP2040/RP2350)

Built on pico-sdk 2.1.1 + CMake.

**Toolchain:**

| Tool | Version | Install |
|---|---|---|
| CMake | 3.13+ | `apt install cmake` |
| arm-none-eabi-gcc | 10.3+ | `apt install gcc-arm-none-eabi` |
| pico-sdk | 2.1.1 | `git clone --branch 2.1.1 https://github.com/raspberrypi/pico-sdk`, then set `PICO_SDK_PATH` |
| picotool | latest | required for `cmake --build --target flash` |
| picowota | git submodule | OTA bootloader — `git submodule update --init --recursive` |
| ninja-build | any | used alongside cmake |

**Wiring (MAX7219 default, Pico W):**

| Pico W GPIO | MAX7219 Pin |
|---|---|
| GP18 (SPI0 SCK) | CLK |
| GP19 (SPI0 TX) | DIN |
| GP17 (SPI0 CSn) | LOAD/CS |
| 3V3 (pin 36) | VCC |
| GND (pin 38) | GND |

**Configuration** (`firmware/pico/config.h`):

```c
#define WIFI_SSID              "YourSSID"
#define WIFI_PASS              "YourPassword"
#define SERVER_HOST            "192.168.1.100"
#define SERVER_PORT            8080
#define ZONE_ID                "pico-01"
#define RECONNECT_INTERVAL_MS  5000
#define NUM_DEVICES            1
```

Display driver is chosen at configure time via `-DDISPLAY_DRIVER=<NAME>` (default `MAX7219`; also `SSD1306`, `SSD1309`, `SSD1327`, `SH1106`, `HT16K33`, `ST7735`, `ST7789`, `ILI9225`, `PCD8544`, `SSD1680`).

First-boot WiFi provisioning: the board opens the `TextReader-Setup` AP → `http://192.168.4.1` captive portal → credentials saved to flash → reboots into STA mode.

**Build:**

```bash
export PICO_SDK_PATH=/path/to/pico-sdk
cmake -S firmware/pico -B firmware/pico/build -DPICO_BOARD=pico_w   # or pico2_w for Pico 2W
cmake --build firmware/pico/build -j$(nproc)
```

Outputs: `textreader.uf2` (OTA payload) and `textreader_combined.uf2` (bootloader+app, first flash).

**Flash:**

```bash
cmake --build firmware/pico/build --target flash   # requires picotool + BOOTSEL mode
```

Manual alternative: hold BOOTSEL while plugging in USB, then drag `textreader_combined.uf2` onto the mounted drive.

picowota OTA bootloader scaffolding is present in the build, but OTA push is not a shipped v1.2 feature — it's deferred to a future phase.

### ESP32

Built on ESP-IDF 5.2.1 native CMake.

**Toolchain:**

| Tool | Version | Install |
|---|---|---|
| ESP-IDF | 5.2.1 | `git clone --branch v5.2.1 https://github.com/espressif/esp-idf`, then `./install.sh esp32`, then `. ./export.sh` per shell session |
| CMake | 3.16+ | bundled with ESP-IDF |
| Python | 3.8+ | required by ESP-IDF tooling |

Supported chips: ESP32, S2, S3, C2, C3, C6, H2 (H2 has no WiFi — provisioning will not work on H2).

**Wiring (MAX7219 default, classic ESP32):**

| ESP32 GPIO | MAX7219 Pin |
|---|---|
| GPIO23 | DIN |
| GPIO18 | CLK |
| GPIO5 | LOAD/CS |
| 3.3V | VCC |
| GND | GND |

Some MAX7219 modules need 5V VCC and a level shifter if the module does not tolerate 3.3V logic.

**Configuration** (`firmware/esp32/main/config.h`):

```c
#define WIFI_SSID              ""   // fallback only — captive portal is primary provisioning path
#define WIFI_PASS              ""
#define SERVER_HOST            ""
#define SERVER_PORT            8080
#define ZONE_ID                "esp32"
#define NUM_DEVICES            4
#define RECONNECT_INTERVAL_MS  5000
```

SPI/I2C pins vary per chip family; classic ESP32 defaults: `SPI_MOSI_PIN=23`, `SPI_CLK_PIN=18`, `SPI_CS_PIN=5`, `I2C_SDA_PIN=21`, `I2C_SCL_PIN=22`.

Captive-portal provisioning is the primary path (the `config.h` WiFi fields are fallback only): `TextReader-Setup` AP → `http://192.168.4.1` → saved to NVS → reboot into STA mode.

**Build:**

```bash
. $IDF_PATH/export.sh
idf.py set-target esp32        # once per build dir; or esp32s3, esp32c3, etc.
idf.py build                   # default driver MAX7219
# or an explicit driver:
cmake -S firmware/esp32 -B firmware/esp32/build -DDISPLAY_DRIVER=SSD1306
cmake --build firmware/esp32/build
```

**Flash:**

```bash
idf.py flash                   # or: idf.py -p /dev/ttyUSB0 flash
idf.py monitor                 # serial monitor
```

ESP-IDF OTA partition scaffolding exists, but full OTA push is planned for a future phase — not a shipped v1.2 feature.

### Firmware Troubleshooting

| Symptom | Cause / check |
|---|---|
| Board not detected over USB | Pico: check BOOTSEL mode entry; ESP32: check `idf.py -p /dev/ttyUSB0` port permission (`dialout` group on Linux) |
| WiFi provisioning fails / no captive portal | Confirm no stored credentials are blocking AP mode; erase flash to force re-provision (`idf.py erase-flash` for ESP32) |
| Zone doesn't appear on server | Confirm `SERVER_HOST`/`SERVER_PORT` in `config.h` match the running server; confirm the zone was registered as type `FIRMWARE` via `POST /api/v1/zones` before connecting (the server closes with `1003 CANNOT_ACCEPT` otherwise) |
| Display shows nothing after connecting | Confirm `-DDISPLAY_DRIVER=<NAME>` matches the physically wired display; confirm wiring against the platform's wiring table above |

Building on macOS or Windows: follow the official [pico-sdk](https://www.raspberrypi.com/documentation/pico-sdk/getting-started.html) or [ESP-IDF](https://docs.espressif.com/projects/esp-idf/en/stable/esp32/get-started/) installation guides for your platform — the build/flash commands above are otherwise identical.

---

## Configuration

All settings have defaults. Override via environment variables or `.env.local`:

```bash
cp .env.example .env.local
# edit .env.local as needed
```

Key variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DISPLAY_TYPE` | `MAX7219` | `MAX7219`, `LCD`, `OLED` |
| `MAX7219_NUM_DEVICES` | `2` | Number of chained MAX7219 modules |
| `GPIO_SPI_CE` | `24` | SPI chip-enable GPIO pin |
| `GPIO_SPI_MOSI` | `19` | SPI MOSI GPIO pin |
| `GPIO_SPI_MISO` | `9` | SPI MISO GPIO pin |
| `GPIO_SPI_SCK` | `23` | SPI clock GPIO pin |
| `SPI_TIMEOUT_MS` | `1000` | SPI transaction timeout (ms) |
| `GPIO_TIMEOUT_MS` | `500` | GPIO operation timeout (ms) |
| `I2C_BUS` | `1` | I2C bus number (LCD/OLED) |
| `DATABASE_URL` | H2 file | Switch to PostgreSQL by changing this + driver |
| `DATABASE_DRIVER` | `org.h2.Driver` | `org.postgresql.Driver` for Postgres |
| `DATABASE_USER` | `sa` | Database user |
| `DATABASE_PASSWORD` | _(empty)_ | Database password |
| `DATABASE_POOL_SIZE` | `5` | HikariCP pool size |
| `API_RATE_LIMIT` | `60` | API requests per minute |
| `API_MAX_TEXT_LENGTH` | `128` | Maximum text message length |
| `SCREEN_DRIVER_MAX_SLOTS` | `10` | Max queued display slots |
| `SCROLL_SPEED` | `16` | Scroll speed (ms per step) |
| `REFRESH_RATE` | `60` | Display refresh rate (Hz) |
| `LOG_LEVEL` | `INFO` | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `LOG_FORMAT` | `json` | `json` or `text` |
| `METRICS_ENABLED` | `true` | Enable metrics endpoint |
| `METRICS_PREFIX` | `textreaderrpi` | Metric name prefix |
| `API_METRICS_RATE_LIMIT` | `120` | `/metrics` requests per minute |
| `WEBHOOK_DEFAULT_URL` | _(empty)_ | Default webhook URL for events |
| `RETRY_MAX_ATTEMPTS` | `5` | Hardware retry attempts |
| `RETRY_INITIAL_DELAY_MS` | `1000` | Initial retry delay (ms) |
| `RETRY_MAX_DELAY_MS` | `30000` | Maximum retry delay (ms) |
| `RETRY_FACTOR` | `2.0` | Retry backoff multiplier |

---

## Build and Run

The Ktor Gradle plugin handles builds and Docker images — no separate Dockerfile is needed.

```bash
# Run tests
./gradlew test

# Run locally (no hardware required — uses OfflineDisplayDriver)
./gradlew run

# Build fat JAR
./gradlew buildFatJar
# → build/libs/textreaderrpi.jar

# Build Docker image and load into local daemon (arm64 + amd64)
./gradlew publishImageToLocalRegistry

# Build Docker image tarball (CI / registry export)
./gradlew buildImage

# Build and run in a container immediately
./gradlew runDocker

# Push to Docker Hub
DOCKER_HUB_USERNAME=you DOCKER_HUB_PASSWORD=token ./gradlew publishImage
```

Image configuration in `build.gradle.kts`:
```kotlin
ktor {
    docker {
        localImageName.set("textreaderrpi")
        imageTag.set("${project.version}")
        jreVersion.set(JavaVersion.VERSION_25)
    }
    jib {
        from {
            platforms {
                platform { architecture = "arm64"; os = "linux" }
                platform { architecture = "amd64"; os = "linux" }
            }
        }
    }
}
```

---

## Deployment

### Docker Compose (Raspberry Pi with SPI/I2C hardware)

```bash
# Step 1 — build image
./gradlew publishImageToLocalRegistry

# Step 2 — configure
cp .env.example .env.local   # edit as needed

# Step 3 — start
cd .devops/containers
docker compose up -d
docker compose logs -f
```

The Compose file mounts `/dev/spidev0.0` and `/dev/i2c-1` and persists data in a named volume.

### Docker (standalone)

```bash
./gradlew publishImageToLocalRegistry
docker run --rm -p 8080:8080 --env-file .env.local textreaderrpi:latest
```

### Helm (Kubernetes / K3s)

A Helm chart is provided for cluster deployments:

```bash
# Install with default values (ClusterIP, no hardware access, H2 persistence)
helm install textreaderrpi .devops/helm/textreaderrpi

# Override image and enable hardware device access
helm install textreaderrpi .devops/helm/textreaderrpi \
  --set image.repository=myregistry/textreaderrpi \
  --set image.tag=0.0.1 \
  --set hardwareAccess.enabled=true
```

Key chart defaults (`values.yaml`): `service.type: ClusterIP`, `persistence.data.size: 1Gi`, `replicas: 1` (fixed — not horizontally scalable due to hardware access). Liveness probe `GET /health` and readiness probe `GET /health/ready`, both `initialDelaySeconds: 20` / `periodSeconds: 30`. `database.password` is auto-generated as a 16-char random string at install time when left unset.

### Monitoring

No built-in alerting — pick one: a cron job hitting `/health` and paging on non-200, a systemd watchdog restarting the service on failed health checks, or an external monitor (e.g. Uptime Kuma, Prometheus blackbox exporter) polling `/health/ready`.

### Systemd (host install)

```bash
./gradlew buildFatJar
sudo ./.devops/host/install-systemd.sh
```

---

## Code Layout

```text
src/main/kotlin/com/anjo/
├── Application.kt
├── config/
│   ├── loader/          # ConfigLoader (reads YAML + env vars)
│   └── model/           # DatabaseConfig, ApiConfig, DisplayConfig, ZonesConfig, ...
├── db/
│   ├── DatabaseFactory.kt
│   ├── HistoryRepository.kt
│   ├── NetworkZonesTable.kt
│   ├── ScheduleRepository.kt
│   ├── SchedulesTable.kt
│   └── ZoneRepository.kt
├── di/
│   ├── DependencyInjection.kt
│   ├── ErrorHandling.kt
│   ├── HTTP.kt
│   ├── Monitoring.kt
│   ├── RateLimiting.kt
│   └── Serialization.kt
├── driver/
│   ├── DisplayDriver.kt           # interface
│   ├── AbstractDisplayDriver.kt   # shared driver base
│   ├── Max7219Matrix.kt
│   ├── LcdDisplay.kt
│   ├── OledDisplay.kt
│   └── OfflineDisplayDriver.kt
├── routing/
│   ├── Routing.kt
│   ├── TextRoutes.kt
│   ├── DisplayRoutes.kt
│   ├── FirmwareZoneRoutes.kt
│   ├── HealthRoutes.kt
│   ├── HistoryRoutes.kt
│   ├── LiveRoutes.kt
│   ├── MetricsRoutes.kt
│   ├── ScheduleRoutes.kt
│   ├── ZoneRoutes.kt
│   └── ui/                        # HTML UI routes (Web, Schedule, History, Zones)
├── service/
│   ├── DisplayEventBus.kt
│   ├── DisplaySelectionService.kt
│   ├── ScreenDriverService.kt
│   ├── SchedulerService.kt
│   ├── EffectRendererFactory.kt
│   ├── HistoryService.kt
│   ├── MetricsCollector.kt
│   ├── NetworkDiscoveryService.kt
│   ├── RetryPolicy.kt
│   ├── WebhookService.kt
│   ├── ZoneRegistry.kt
│   └── effect/                    # EffectRenderer
├── zone/
│   ├── ZoneDriver.kt              # interface (suspend send, status, stop)
│   ├── LocalZoneDriver.kt         # wraps a DisplayDriver for local hardware zones
│   ├── FirmwareZoneDriver.kt      # zone backed by firmware (e.g. Pi Pico)
│   └── NetworkZoneDriver.kt       # WebSocket client for remote display nodes
├── web/
│   └── templates/                 # kotlinx.html page templates
├── model/
├── validation/
└── utils/

.devops/
├── containers/
│   ├── docker-compose.yml     # Full env var mapping + SPI/I2C device mounts
│   └── build-image.sh
├── helm/
│   └── textreaderrpi/         # Helm chart (Chart.yaml, values.yaml, templates/)
└── host/
    ├── textreaderrpi.service  # systemd unit
    └── install-systemd.sh

.env.example                   # Root-level env var template
```

---

## Notes

- Built with **Ktor 3.5** + **Kotlin 2.3** + **Exposed 1.3** + **JVM 25**.
- Hardware integration via **Pi4J 4.x** (JitPack). Falls back to `OfflineDisplayDriver` when hardware is unavailable.
- Multi-zone output: each zone is a `ZoneDriver` — either a `LocalZoneDriver` (direct hardware), `FirmwareZoneDriver` (Pi Pico / firmware node), or a `NetworkZoneDriver` (WebSocket to a remote node). Zones auto-reconnect.
- Network discovery via mDNS (`_textreaderrpi._tcp.local.`) and UDP broadcast on port 54321. Zones are persisted in the database and reloaded on restart.
- Scheduler supports `ONESHOT`, `RECURRING`, and `CRON` triggers — persisted in H2 or PostgreSQL.
- Switching databases: change `DATABASE_URL` + `DATABASE_DRIVER` env vars only — no code change required.
- Docker image targets `arm64` and `amd64` and is built entirely by the Ktor Gradle plugin.
- Line coverage gate: 70% (enforced in CI via JaCoCo).
