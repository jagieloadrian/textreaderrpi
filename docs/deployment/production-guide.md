# TextReaderRpi — Production Deployment Guide

## Prerequisites

- Raspberry Pi OS (Bullseye or later) or any Linux host with systemd
- Java 25 runtime (host deployment)
- Docker + Docker Compose (container deployment)
- `curl` for health checks

---

## Environment Variables

All runtime configuration can be overridden via environment variables.  
Copy `.env.example` to `.env.local` and edit:

```bash
cp .env.example .env.local
```

### Full variable reference

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP server port |
| `DISPLAY_TYPE` | `MAX7219` | Active driver: `MAX7219`, `LCD`, `OLED` |
| `MAX7219_NUM_DEVICES` | `2` | Number of chained MAX7219 modules |
| `GPIO_SPI_CE` | `8` | SPI chip-enable GPIO pin |
| `GPIO_SPI_MOSI` | `10` | SPI MOSI GPIO pin |
| `GPIO_SPI_MISO` | `9` | SPI MISO GPIO pin |
| `GPIO_SPI_SCK` | `11` | SPI clock GPIO pin |
| `I2C_BUS` | `1` | I2C bus number (LCD / OLED) |
| `SPI_TIMEOUT_MS` | `1000` | SPI operation timeout |
| `GPIO_TIMEOUT_MS` | `500` | GPIO operation timeout |
| `DATABASE_URL` | H2 file | JDBC URL |
| `DATABASE_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `DATABASE_USER` | `sa` | Database user |
| `DATABASE_PASSWORD` | _(empty)_ | Database password |
| `DATABASE_POOL_SIZE` | `5` | HikariCP pool size |
| `API_MAX_TEXT_LENGTH` | `128` | Max chars accepted per text request |
| `API_QUEUE_SIZE` | `10` | In-flight request queue depth |
| `API_RATE_LIMIT` | `60` | API requests per minute |
| `API_METRICS_RATE_LIMIT` | `120` | `/metrics` requests per minute |
| `RETRY_MAX_ATTEMPTS` | `5` | Driver retry attempts |
| `RETRY_INITIAL_DELAY_MS` | `1000` | First retry delay (ms) |
| `RETRY_MAX_DELAY_MS` | `30000` | Max retry delay cap (ms) |
| `RETRY_FACTOR` | `2.0` | Exponential backoff factor |
| `METRICS_ENABLED` | `true` | Enable `/metrics` endpoint |
| `METRICS_PREFIX` | `textreaderrpi` | Metric name prefix |
| `SCREEN_DRIVER_MAX_SLOTS` | `10` | Max concurrent screen driver slots |
| `SCROLL_SPEED` | `16` | Scroll step delay (ms) |
| `REFRESH_RATE` | `60` | Display refresh rate (Hz) |
| `LOG_LEVEL` | `INFO` | Log level: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `LOG_FORMAT` | `json` | Log format: `json`, `text` |

### Switching to PostgreSQL

Set these three variables (all others keep their defaults):

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/textreader
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USER=textreader
DATABASE_PASSWORD=secret
DATABASE_POOL_SIZE=10
```

---

## 1) Build Artifact

The Ktor Gradle plugin handles **all** build + Docker tasks — no Dockerfile required.

```bash
# Fat JAR only (host deployment or CI):
./gradlew buildFatJar
# Output: build/libs/textreaderrpi.jar

# Docker image → loaded into local Docker daemon:
./gradlew publishImageToLocalRegistry

# Docker image → tarball (CI export / registry push):
./gradlew buildImage

# Build + run immediately in a container:
./gradlew runDocker

```

Image name and tag are configured in `build.gradle.kts`:
```kotlin
ktor {
    docker {
        localImageName.set("textreaderrpi")
        imageTag.set("latest")
    }
}
```

---

## 2) Host Deployment (systemd)

```bash
chmod +x .devops/host/install-systemd.sh
sudo ./.devops/host/install-systemd.sh
```

The script installs:
- JAR → `/opt/textreaderrpi/textreaderrpi.jar`
- Systemd unit → `/etc/systemd/system/textreaderrpi.service`
- Service user `textreaderrpi` (if missing)
- Loads `/opt/textreaderrpi/.env` for env overrides

### Manual systemd steps

```bash
sudo cp .devops/host/textreaderrpi.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable textreaderrpi
sudo systemctl restart textreaderrpi
```

### Validate host deployment

```bash
sudo systemctl status textreaderrpi
curl -i http://localhost:8080/health
curl -i http://localhost:8080/health/ready
```

---

## 3) Docker Image Build + Run

The Ktor Gradle plugin builds the image internally — **no Dockerfile** in the repository is used.

### Build and load into local Docker daemon

```bash
./gradlew publishImageToLocalRegistry
```

### Or use the helper script

```bash
chmod +x .devops/containers/build-image.sh
./.devops/containers/build-image.sh           # builds textreaderrpi:latest
./.devops/containers/build-image.sh myapp:v2  # builds and retags
```

### Run standalone

```bash
./gradlew publishImageToLocalRegistry
docker run --rm -p 8080:8080 \
  -e LOG_LEVEL=DEBUG \
  textreaderrpi:latest
```

### Push to Docker Hub

```bash
DOCKER_HUB_USERNAME=you DOCKER_HUB_PASSWORD=token ./gradlew publishImage
```

---

## 4) Docker Compose (Raspberry Pi with hardware)

Build the image first, then start compose:

```bash
# 1. Build and load image into local Docker daemon
./gradlew publishImageToLocalRegistry

# 2. Configure environment
cp .env.example .env.local
# edit .env.local as needed

# 3. Start services
cd .devops/containers
docker compose up -d
docker compose logs -f
docker compose down
```

The compose file mounts SPI (`/dev/spidev0.0`) and I2C (`/dev/i2c-1`) devices for hardware access and persists the H2 database in a named volume.

---

## 5) Logs and Operations

```bash
# Host (systemd):
sudo journalctl -u textreaderrpi -f
sudo journalctl -u textreaderrpi -n 100 --no-pager

# Container:
docker logs -f textreaderrpi
cd .devops/containers && docker compose logs -f
```

---

## 6) Remove Service

```bash
sudo systemctl stop textreaderrpi
sudo systemctl disable textreaderrpi
sudo rm /etc/systemd/system/textreaderrpi.service
sudo systemctl daemon-reload
sudo rm -rf /opt/textreaderrpi
```

---

## Troubleshooting

| Symptom | Check |
|---|---|
| Service fails to start | `journalctl -u textreaderrpi -n 50` |
| H2 password error on startup | Delete `data/schedules.mv.db` and restart — DB recreates itself |
| `GET /health/ready` returns 503 | Hardware unavailable or wrong `DISPLAY_TYPE` |
| API gets throttled | Increase `API_RATE_LIMIT` in `.env.local` |
| Container unhealthy | `docker logs textreaderrpi` and check `/health/ready` |
| Schedule not firing | Check `DISPLAY_TYPE` is correct and `/api/v1/schedule` status |
