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
| `GPIO_SPI_CE` | `8` | SPI chip-enable GPIO pin |
| `GPIO_SPI_MOSI` | `10` | SPI MOSI GPIO pin |
| `GPIO_SPI_MISO` | `9` | SPI MISO GPIO pin |
| `GPIO_SPI_SCK` | `11` | SPI clock GPIO pin |
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
| `WEBHOOK_DEFAULT_URL` | _(empty)_ | Default webhook URL for events |
| `RETRY_MAX_ATTEMPTS` | `5` | Hardware retry attempts |
| `RETRY_INITIAL_DELAY_MS` | `1000` | Initial retry delay (ms) |
| `RETRY_MAX_DELAY_MS` | `30000` | Maximum retry delay (ms) |
| `RETRY_FACTOR` | `2.0` | Retry backoff multiplier |

→ Full variable reference in [`docs/deployment/production-guide.md`](docs/deployment/production-guide.md).

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

Key chart defaults (`values.yaml`): `service.type: ClusterIP`, `persistence.data.size: 1Gi`, `replicas: 1` (fixed — not horizontally scalable due to hardware access).

### Systemd (host install)

```bash
./gradlew buildFatJar
sudo ./.devops/host/install-systemd.sh
```

→ Full deployment guide: [`docs/deployment/production-guide.md`](docs/deployment/production-guide.md)
→ Monitoring & alerting: [`docs/operations/monitoring-alerting.md`](docs/operations/monitoring-alerting.md)

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
