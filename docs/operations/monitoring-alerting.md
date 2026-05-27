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
Response 200 (ready):
{
  "status": "UP",
  "displayType": "MAX7219",
  "isActive": true,
  "lastError": null
}
Response 503 (not ready):
{
  "status": "DOWN",
  "displayType": "UNKNOWN",
  "isActive": false,
  "lastError": "No display driver available"
}
```
**Alert trigger:** Any 503 response sustained for > 2 minutes indicates hardware failure.
---
## Log-Based Alerting
Application logs are emitted to systemd journal. Key patterns to monitor:
| Pattern | Level | Meaning |
|---|---|---|
| `Display operation failed permanently` | ERROR | Driver failed after all retries — hardware issue |
| `all N attempts exhausted` | ERROR | RecoveryPolicy retry budget exhausted |
| `acquire(readInput) rejected: at capacity` | WARN | ResourceTracker at limit — potential load spike |
| `HealthService started` | INFO | Application started successfully |
### Querying Logs
```bash
# Show all ERROR logs
sudo journalctl -u textreaderrpi -p err
# Monitor for display failures in real time
sudo journalctl -u textreaderrpi -f | grep "permanently"
# Count failed retry events in last hour
sudo journalctl -u textreaderrpi --since "1 hour ago" | grep "all.*attempts exhausted" | wc -l
```
---
## Alerting Recommendations
### Option A: Simple cron health check
```bash
# /etc/cron.d/textreaderrpi-health
*/2 * * * * root curl -sf http://localhost:8080/health/ready || systemctl restart textreaderrpi
```
### Option B: Systemd watchdog (recommended)
The service unit uses `ExecStartPost` to validate readiness at startup. For ongoing watchdog:
Add to the `[Service]` section of the unit file:
```ini
WatchdogSec=120
NotifyAccess=main
```
And update the application to send `sd_notify WATCHDOG=1` periodically (future enhancement).
### Option C: External uptime monitor
Point an external monitor (UptimeRobot, Grafana Alerting, custom script) to:
- `GET /health` — should return 200 always
- `GET /health/ready` — should return 200 when hardware is connected
---
## /metrics — EXPLICITLY DEFERRED
The `/metrics` endpoint and Prometheus/Grafana dashboarding are **deferred to Phase 4+**.
**Decision record (D-39):** `/metrics` endpoint and advanced monitoring dashboards are out of scope for Phase 3. This is a tracked decision, not an oversight.
**Tracked follow-up:** See ROADMAP.md — Phase 4 scope should include:
- Micrometer integration
- `GET /metrics` (Prometheus format)
- Dashboard templates for Grafana
- Histogram metrics for request latency and recovery time
---
## Memory Management
The `/health` liveness endpoint exposes JVM heap stats:
- `memoryUsedMb` — current heap usage
- `memoryMaxMb` — configured max heap
**Alert threshold:** If `memoryUsedMb / memoryMaxMb > 0.90` for > 5 minutes, consider restart.
Heap logs are also emitted at startup:
```
INFO  com.anjo.service.HealthService - HealthService started, heap: 57mb/512mb
```
