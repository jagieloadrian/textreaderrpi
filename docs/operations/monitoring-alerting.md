# TextReaderRpi — Monitoring and Alerting

## Current Monitoring Approach

TextReaderRpi uses **health-check + metrics-based monitoring** for operational visibility.

---

## Health Endpoints

### GET /health  (Liveness)

Returns service liveness. Always `200` while the process is running.

```
GET http://localhost:8080/health
Response 200:
{ "status": "UP", "uptime": 3600, "memoryUsedMb": 57, "memoryMaxMb": 512 }
```

**Alert trigger:** Non-200 → process unreachable. Systemd auto-restarts via `Restart=on-failure`.

### GET /health/ready  (Readiness)

Display driver readiness. `200` = hardware active, `503` = driver unavailable.

```
GET http://localhost:8080/health/ready

200 OK:
{ "status": "UP", "displayType": "MAX7219", "isActive": true, "lastError": null }

503 Service Unavailable:
{ "status": "DOWN", "displayType": "UNKNOWN", "isActive": false, "lastError": "No display driver available" }
```

**Alert trigger:** Sustained `503` for > 2 minutes → hardware failure.

---

## GET /metrics — Runtime Metrics

```
GET http://localhost:8080/metrics
200 OK
{
  "timestamp": "2026-05-28T10:00:00Z",
  "groups": [
    { "name": "runtime", "metrics": [ { "key": "uptime", "value": 3600 }, ... ] },
    { "name": "api",     "metrics": [ ... ] }
  ]
}
```

Rate-limited separately: configure `API_METRICS_RATE_LIMIT` (default 120/min).

---

## Schedule API Endpoints

### GET /api/v1/schedule

Returns all schedules with their status (`ACTIVE`, `DONE`).

### POST /api/v1/schedule

Creates a new timed display schedule.

```json
{
  "text": "Hello World",
  "triggerType": "RECURRING",
  "triggerValue": "5m",
  "effect": "SCROLL",
  "priority": 0
}
```

`triggerType` values:
- `ONESHOT` — fires once at ISO-8601 timestamp (e.g. `"2026-06-01T12:00:00Z"`)
- `RECURRING` — fires every interval: `5m`, `1h`, `30s`, `2d`
- `CRON` — Unix cron expression (e.g. `"0 * * * *"` = every hour)

### POST /api/v1/schedule/{id}/cancel

Stops a running RECURRING or CRON schedule without deleting it. Status becomes `DONE`.

```bash
curl -X POST http://localhost:8080/api/v1/schedule/{id}/cancel
# 204 No Content
```

### DELETE /api/v1/schedule/{id}

Stops and removes a schedule from the database entirely.

---

## API Quick Reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/text` | Send text to display immediately |
| `GET` | `/api/v1/display/status` | Current driver status |
| `POST` | `/api/v1/display/select` | Switch display driver |
| `GET` | `/api/v1/schedule` | List all schedules |
| `POST` | `/api/v1/schedule` | Create schedule |
| `GET` | `/api/v1/schedule/{id}` | Get single schedule |
| `DELETE` | `/api/v1/schedule/{id}` | Delete schedule |
| `POST` | `/api/v1/schedule/{id}/cancel` | Stop active recurring/cron schedule |
| `GET` | `/health` | Liveness check |
| `GET` | `/health/ready` | Readiness check |
| `GET` | `/metrics` | Runtime metrics JSON |
| `GET` | `/openapi` | Swagger UI |

---

## Log-Based Alerting

Logs go to the systemd journal (`journalctl -u textreaderrpi`) or Docker stdout.

| Pattern | Level | Meaning |
|---|---|---|
| `Display operation failed after retries` | ERROR | Driver hardware issue |
| `All N retry attempts exhausted` | ERROR | Retry budget exhausted |
| `Firing schedule id=...` | INFO | Schedule successfully triggered |
| `Failed to fire schedule ...` | ERROR | Schedule trigger failed |
| `SchedulerService tick error` | ERROR | Internal scheduler error |

### Querying logs

```bash
# All ERROR-level entries (systemd):
sudo journalctl -u textreaderrpi -p err

# Stream display failures:
sudo journalctl -u textreaderrpi -f | grep "failed after retries"

# Count retry exhaustion events in last hour:
sudo journalctl -u textreaderrpi --since "1 hour ago" | grep "retry attempts exhausted" | wc -l
```

---

## Alerting Options

### Option A: Simple cron health check

```bash
# /etc/cron.d/textreaderrpi-health
*/2 * * * * root curl -sf http://localhost:8080/health/ready || systemctl restart textreaderrpi
```

### Option B: Systemd watchdog (recommended)

Add to the `[Service]` section of the unit file:

```ini
WatchdogSec=120
NotifyAccess=main
```

### Option C: External uptime monitor

Point any monitor (UptimeRobot, Grafana, custom script) to:
- `GET /health` — liveness, always `200`
- `GET /health/ready` — `200` when hardware connected

---

## Memory

The `/health` endpoint exposes JVM heap stats:
- `memoryUsedMb` — current heap usage
- `memoryMaxMb` — configured max heap

**Alert threshold:** `memoryUsedMb / memoryMaxMb > 0.90` for > 5 minutes → consider restart.
