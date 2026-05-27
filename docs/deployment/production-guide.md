# TextReaderRpi — Production Deployment Guide
## Prerequisites
- Raspberry Pi running Raspberry Pi OS (Bullseye or later)
- Java 17+ installed: `java -version`  
- `curl` installed for health checks
- User account for the service: `textreaderrpi`
---
## 1. Build the Fat JAR
```bash
./gradlew shadowJar
# Output: build/libs/TextReaderRpi-all.jar
```
---
## 2. Install on Target Host
```bash
# Create service user (no login shell, no home)
sudo adduser --system --no-create-home --group textreaderrpi
# Create installation directory
sudo mkdir -p /opt/textreaderrpi/logs
sudo chown -R textreaderrpi:textreaderrpi /opt/textreaderrpi
# Copy JAR
sudo cp build/libs/TextReaderRpi-all.jar /opt/textreaderrpi/
```
---
## 3. Configure Environment
```bash
# Create .env from example
sudo cp deployment/.env.example /opt/textreaderrpi/.env
sudo nano /opt/textreaderrpi/.env
sudo chmod 640 /opt/textreaderrpi/.env
sudo chown root:textreaderrpi /opt/textreaderrpi/.env
```
Key environment variables:
| Variable | Default | Description |
|---|---|---|
| `DISPLAY_TYPE` | `MAX7219` | Driver type: MAX7219, LCD, OLED |
| `API_RATE_LIMIT_PER_MINUTE` | `60` | Rate limit for /api/* endpoints |
| `LOG_LEVEL` | `INFO` | Logback log level |
| `SERVER_PORT` | `8080` | Ktor server port |
---
## 4. Install systemd Service
```bash
sudo cp deployment/systemd/textreaderrpi.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable textreaderrpi
sudo systemctl start textreaderrpi
```
---
## 5. Validate Deployment
```bash
# Check service status
sudo systemctl status textreaderrpi
# Validate health (liveness)
curl http://localhost:8080/health
# Expected: {"status":"UP","uptime":...}
# Validate readiness (driver initialized)
curl http://localhost:8080/health/ready
# Expected: {"status":"UP","displayType":"MAX7219","isActive":true,"lastError":null}
# OR 503 if hardware unavailable
```
---
## 6. View Logs
```bash
# Follow live logs
sudo journalctl -u textreaderrpi -f
# Last 100 lines
sudo journalctl -u textreaderrpi -n 100
# Since last boot
sudo journalctl -u textreaderrpi -b
```
---
## 7. Restart Service
```bash
sudo systemctl restart textreaderrpi
# Verify restarted cleanly
sudo systemctl status textreaderrpi
curl http://localhost:8080/health/ready
```
---
## 8. Rollback Procedure
```bash
# Stop service
sudo systemctl stop textreaderrpi
# Swap JAR with previous version
sudo cp /opt/textreaderrpi/TextReaderRpi-all.jar /opt/textreaderrpi/TextReaderRpi-all.jar.bak
sudo cp /opt/textreaderrpi/TextReaderRpi-all-prev.jar /opt/textreaderrpi/TextReaderRpi-all.jar
# Restart and validate
sudo systemctl start textreaderrpi
curl http://localhost:8080/health/ready
```
---
## 9. Log Rotation
Log rotation is handled by Logback at the application level (configured in `src/main/resources/logback.xml`). systemd journal rotation is handled by the OS `journald` configuration.
To adjust: edit `src/main/resources/logback.xml` and rebuild.
---
## 10. Removing the Service
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
| Service fails to start | `journalctl -u textreaderrpi -n 50` — Java runtime errors |
| `/health/ready` returns 503 | Hardware not available: check SPI/I2C wiring and `DISPLAY_TYPE` env var |
| Rate limit 429s in production | Increase `API_RATE_LIMIT_PER_MINUTE` in `.env` |
| Service restarts frequently | Check `journalctl` for uncaught exceptions; verify GPIO permissions |
| High memory usage | Check heap stats from `/health` endpoint — restart if > 90% of max |
