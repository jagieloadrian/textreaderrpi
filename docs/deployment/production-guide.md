# TextReaderRpi - Production Deployment Guide

## Prerequisites
- Raspberry Pi OS (Bullseye or later) or Linux host with systemd
- Java 25 runtime for host deployment
- Docker (for container deployment)
- `curl` for health checks

## 1) Build Artifact

```bash
./gradlew shadowJar
```

Output: `build/libs/TextReaderRpi-all.jar`

## 2) Host Deployment (script + systemd)

Use the deployment script (it installs JAR + unit and restarts service):

```bash
chmod +x .devops/host/install-systemd.sh
sudo ./.devops/host/install-systemd.sh
```

The script installs:
- JAR to `/opt/textreaderrpi/TextReaderRpi-all.jar`
- unit file from `.devops/host/textreaderrpi.service`
- service user `textreaderrpi` (if missing)

## 3) Manual systemd steps (optional)

```bash
sudo cp .devops/host/textreaderrpi.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable textreaderrpi
sudo systemctl restart textreaderrpi
```

## 4) Validate Host Deployment

```bash
sudo systemctl status textreaderrpi
curl -i http://localhost:8080/health
curl -i http://localhost:8080/health/ready
```

Expected behavior:
- `GET /health` -> HTTP `200`
- `GET /health/ready` -> HTTP `200` when display is ready, `503` otherwise

## 5) Docker Image Build + Run

Build via script:

```bash
chmod +x .devops/containers/build-image.sh
./.devops/containers/build-image.sh textreaderrpi:latest
```

Or directly:

```bash
docker build -f .devops/containers/Dockerfile -t textreaderrpi:latest .
```

Run container:

```bash
docker run --rm -p 8080:8080 --name textreaderrpi textreaderrpi:latest
```

## 6) Docker Compose (Raspberry Pi)

The compose file mounts SPI and I2C devices for hardware access:

```bash
cd .devops/containers
docker compose up -d
docker compose logs -f
docker compose down
```

Configuration overrides go in a `.env.local` file in the project root:

```env
KTOR_ENV=production
```

## 6) Logs and Operations

```bash
sudo journalctl -u textreaderrpi -f
sudo journalctl -u textreaderrpi -n 100
sudo systemctl restart textreaderrpi
```

## 7) Remove Service

```bash
sudo systemctl stop textreaderrpi
sudo systemctl disable textreaderrpi
sudo rm /etc/systemd/system/textreaderrpi.service
sudo systemctl daemon-reload
sudo rm -rf /opt/textreaderrpi
```

## Troubleshooting
| Symptom | Check |
|---|---|
| Service fails to start | `journalctl -u textreaderrpi -n 50` |
| `GET /health/ready` returns 503 | Hardware unavailable or misconfigured display driver |
| API gets throttled too aggressively | Tune `api.rateLimitPerMinute` in `application.yaml` or env override |
| Container unhealthy | Check `docker logs textreaderrpi` and `/health/ready` output |
