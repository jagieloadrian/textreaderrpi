# TextReaderRpi — Monitoring and Alerting
## Current Monitoring Approach (Phase 3)
TextReaderRpi uses **health-check-based monitoring** for operational visibility in this phase.
---
## Health Endpoints
### GET /health  (Liveness)
Returns service liveness status. Will always return 200 as long as the process is running.
```
GET http://localhost:8080/health
Response 200:
{
  "status": "UP",
  "uptime": 3600,
  "memoryUsedMb": 57,
  "memoryMaxMb": 512
}
```
**Alert trigger:** Any non-200 response indicates process is unreachable. Restart automatically via systemd (Restart=on-failure).
### GET /health/ready  (Readiness)
Returns display driver readiness. 200 = hardware initialized and active. 503 = driver unavailable.
```
GET http://localhost:8080/health/ready

200 OK (ready):
{
  "status": "UP",
  "displayType": "MAX7219",
  "isActive": true,
  "lastError": null
}

503 Service Unavailable (not ready):
{
  "status": "DOWN",
  "displayType": "UNKNOWN",
  "isActive": false,
  "lastError": "No display driver available"
}
```

**Alert trigger:** Sustained `503` for > 2 minutes → hardware failure.

---

## GET /metrics — Runtime Metrics

Internal metrics endpoint returning JVM and API counters as JSON.

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

Rate-limited separately from the API (configure `api.metricsRateLimitPerMinute` in `application.yaml`).

---

## Log-Based Alerting

Application logs go to the systemd journal (`journalctl -u textreaderrpi`).

| Pattern | Level | Meaning |
|---|---|---|
| `Display operation failed after retries` | ERROR | Driver failed after all retry attempts — hardware issue |
| `All N retry attempts exhausted` | ERROR | Retry budget exhausted — surface to ops |

### Querying Logs

```bash
# All ERROR-level entries
sudo journalctl -u textreaderrpi -p err

# Stream display failures in real time
sudo journalctl -u textreaderrpi -f | grep "failed after retries"

# Count retry exhaustion events in last hour
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

The service unit uses `ExecStartPost` to validate readiness at startup.
For ongoing watchdog, add to the `[Service]` section:

```ini
WatchdogSec=120
NotifyAccess=main
```

And send `sd_notify WATCHDOG=1` periodically from the application.

### Option C: External uptime monitor

Point any external monitor (UptimeRobot, Grafana Alerting, custom script) to:

- `GET /health` — should always return `200`
- `GET /health/ready` — returns `200` when hardware is connected

---

## Memory

The `/health` liveness endpoint exposes JVM heap stats:

- `memoryUsedMb` — current heap usage
- `memoryMaxMb` — configured max heap

**Alert threshold:** `memoryUsedMb / memoryMaxMb > 0.90` for > 5 minutes → consider restart.
