# TextReaderRpi

[![CI](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml/badge.svg)](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml)

Ktor service for rendering scrolling text and scheduled messages on a Raspberry Pi display (MAX7219, LCD, OLED) via Pi4J.

---

## API

### Text

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/text` | Send text immediately (`202 Accepted`) |

```bash
curl -X POST http://localhost:8080/api/v1/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello","effect":"SCROLL"}'
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

### Display

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/display/status` | Driver status JSON |
| `POST` | `/api/v1/display/select` | Switch driver (`MAX7219`, `LCD`, `OLED`) |

### System

| Path | Description |
|---|---|
| `GET /health` | Liveness — `200` while process is running |
| `GET /health/ready` | Readiness — `200` when display driver active |
| `GET /metrics` | Runtime metrics JSON (JVM + API counters) |
| `GET /openapi` | Swagger UI |

---

## Configuration

All settings have defaults. Override via environment variables or `.env.local`:

```bash
cp .env.example .env.local
```

Key variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DISPLAY_TYPE` | `MAX7219` | `MAX7219`, `LCD`, `OLED` |
| `DATABASE_URL` | H2 file | Switch to PostgreSQL by changing this + driver |
| `DATABASE_DRIVER` | `org.h2.Driver` | `org.postgresql.Driver` for Postgres |
| `LOG_LEVEL` | `INFO` | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `API_RATE_LIMIT` | `60` | Requests per minute cap |

→ Full variable reference in [`docs/deployment/production-guide.md`](docs/deployment/production-guide.md).

---

## Build and Run

The Ktor Gradle plugin handles **everything** — no Dockerfile needed.

```bash
# Run tests
./gradlew test

# Run locally
./gradlew run

# Build fat JAR
./gradlew buildFatJar
# → build/libs/textreaderrpi.jar

# Build Docker image and load into local daemon
./gradlew publishImageToLocalRegistry

# Build Docker image tarball (CI / registry export)
./gradlew buildImage

# Build and run in a container immediately
./gradlew runDocker

# Push to Docker Hub
DOCKER_HUB_USERNAME=you DOCKER_HUB_PASSWORD=token ./gradlew publishImage
```

Image configuration lives in `build.gradle.kts`:
```kotlin
ktor {
    docker {
        localImageName.set("textreaderrpi")
        imageTag.set("latest")
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

### Docker (standalone)

```bash
./gradlew publishImageToLocalRegistry
docker run --rm -p 8080:8080 --env-file .env.local textreaderrpi:latest
```

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
│   └── model/           # DatabaseConfig, ApiConfig, DisplayConfig, ...
├── db/
│   ├── DatabaseFactory.kt
│   ├── ScheduleRepository.kt
│   └── SchedulesTable.kt
├── di/
│   ├── DependencyInjection.kt
│   ├── ErrorHandling.kt
│   ├── HTTP.kt
│   ├── Monitoring.kt
│   ├── RateLimiting.kt
│   └── Serialization.kt
├── driver/
│   ├── DisplayDriver.kt       # interface
│   ├── Max7219Matrix.kt
│   ├── LcdDisplay.kt
│   ├── OledDisplay.kt
│   └── OfflineDisplayDriver.kt
├── routing/
│   ├── Routing.kt
│   ├── TextRoutes.kt
│   ├── DisplayRoutes.kt
│   ├── ScheduleRoutes.kt
│   └── MetricsRoutes.kt
├── service/
│   ├── ScreenDriverService.kt
│   ├── SchedulerService.kt
│   ├── DisplaySelectionService.kt
│   ├── EffectRendererFactory.kt
│   └── effect/               # ScrollEffect, BlinkEffect, FadeEffect, ReverseEffect
├── model/
├── validation/
└── utils/

.devops/
├── containers/
│   ├── Dockerfile             # Ktor plugin buildFatJar-based
│   ├── docker-compose.yml     # Full env var mapping + device mounts
│   ├── build-image.sh
│   └── .env.example
└── host/
    ├── textreaderrpi.service  # systemd unit
    └── install-systemd.sh

.env.example                   # Root-level env var template
```

---

## Notes

- Built with **Ktor 3.5** + **Kotlin 2.3** + **Exposed 1.3** (PostgreSQL-compatible schema).
- Hardware integration via **Pi4J 4.x** (JitPack).
- Scheduler supports `ONESHOT`, `RECURRING`, and `CRON` triggers — persisted in H2 or PostgreSQL.
- Switching databases: change `DATABASE_URL` + `DATABASE_DRIVER` env vars only — no code change required.
- Docker image is built entirely by the Ktor Gradle plugin (`./gradlew publishImageToLocalRegistry`) — **no Dockerfile** in the repository.
