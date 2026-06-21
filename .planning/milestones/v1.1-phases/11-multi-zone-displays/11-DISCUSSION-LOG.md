# Phase 11: Multi-Zone Displays - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-16
**Phase:** 11-Multi-Zone Displays
**Areas discussed:** Local zone configuration, Network zones scope, ZoneRegistry architecture, Network discovery protocol

---

## Local Zone Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| YAML zones list | `display.zones: [...]` list, new ZonesConfig data class | ✓ |
| Env vars per zone | `ZONE_main_TYPE=MAX7219 ZONE_main_SPI_CE=24…` dynamic parsing | |
| Single-zone + extra zones | Keep `display:` block, add optional `display.extraZones:` | |

**User's choice:** YAML zones list

---

| Option | Description | Selected |
|--------|-------------|----------|
| Broadcast to all zones | No-zone request reaches every zone simultaneously | ✓ |
| First zone in list | Top entry is the default zone | |
| Zone named "default" | Config must include id="default" | |

**User's choice:** Broadcast to all zones (local + network simultaneously)
**Notes:** User specified "I would prefer support for several simultaneously, not just one active" — clarified to mean all zones active at startup, no single-active model.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Per-zone gpioPins block | Each zone has its own gpioPins map | |
| Just chip select per zone | Shared MOSI/MISO/SCK, only spi_ce differs | |
| Follow Pi4J API | Researcher checks Pi4J 4.0.0 SPI API and adapts | ✓ |

**User's choice:** "sprawdź jak pi4j konfiguruje wszystkie spi i dostosuj do tego kod" — researcher to determine per-zone SPI config based on Pi4J 4.0.0 documentation.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Start with zone offline | Failed zones registered as OFFLINE, app continues | ✓ |
| Fail startup | Any zone hardware failure blocks application start | |
| Skip failed zones silently | Failed zones not registered, 404 on routing | |

**User's choice:** Start with that zone offline

---

| Option | Description | Selected |
|--------|-------------|----------|
| Always ONLINE for local zones | Local = ONLINE or OFFLINE only, no heartbeat | |
| Dynamic based on last render | ONLINE/DEGRADED based on last render attempt | ✓ |
| You decide | Planner picks | |

**User's choice:** Dynamic status based on last render success

---

| Option | Description | Selected |
|--------|-------------|----------|
| 503 Service Unavailable | Zone exists but hardware unavailable | ✓ |
| 404 Not Found | Treat offline zone as non-existent | |
| Try anyway + return error | Attempt render, catch exception, 500 | |

**User's choice:** Return 503 Service Unavailable

---

| Option | Description | Selected |
|--------|-------------|----------|
| Mixed types supported | MAX7219 + OLED in same zones list | ✓ |
| Same type for all local zones | All local zones must be same driver type | |
| You decide | Planner picks | |

**User's choice:** Yes, mixed types supported

---

## Network Zones Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, all 8 in Phase 11 | Full multi-zone vision in one phase | ✓ |
| Split: local now, network later | ZONE-01/07/08 now, ZONE-02–06 deferred | |
| Minimum viable: local + manual IP | Skip auto-discovery | |

**User's choice:** Yes, all 8 in Phase 11

---

| Option | Description | Selected |
|--------|-------------|----------|
| Pi WebSocket server side only | External firmware out of scope (v1.2+) | ✓ |
| Simple HTTP protocol instead | External displays expose POST endpoint | |
| Both HTTP and WebSocket | Support either per zone config | |

**User's choice:** Pi sends over WebSocket, firmware is out of scope

---

| Option | Description | Selected |
|--------|-------------|----------|
| WebSocket disconnect = OFFLINE | Connection drop immediately marks OFFLINE | |
| Periodic ping over WebSocket | Ping every N seconds, no pong → OFFLINE | ✓ |
| Separate HTTP health check | Pi polls device HTTP /health every N seconds | |

**User's choice:** Periodic ping over WebSocket

---

| Option | Description | Selected |
|--------|-------------|----------|
| Return 503, log, no retry | Same as local offline zones | ✓ |
| Queue and deliver on reconnect | Buffer messages per zone | |
| Silently drop, return 202 | Fire-and-forget, silent failure | |

**User's choice:** Return 503, log it, no retry

---

| Option | Description | Selected |
|--------|-------------|----------|
| Persisted in database | `network_zones` table, survive restarts | ✓ |
| In-memory only | Lost on restart, user re-adds after reboot | |
| In application.yaml as static config | Edit YAML, restart required | |

**User's choice:** Yes, persisted in database (manually-added zones)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-discovered zones also persist | All zones in same `network_zones` table | ✓ |
| Only manual zones persist | Discovery re-runs on each boot | |
| You decide | Planner picks | |

**User's choice:** Auto-discovered zones also persist

---

| Option | Description | Selected |
|--------|-------------|----------|
| All zones: local + network | No-zone broadcast to every registered zone | ✓ |
| Local zones only | Network zones require explicit ?zone=X | |

**User's choice:** All zones: local + network

---

## ZoneRegistry Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| New ZoneRegistry service | Dedicated service holds Map<String, ZoneDriver> | ✓ |
| Extend DisplaySelectionService | Rename/expand existing service | |
| ScreenDriverService zone-aware | Service directly holds zone map | |

**User's choice:** New ZoneRegistry service

---

| Option | Description | Selected |
|--------|-------------|----------|
| New ZoneDriver interface | LocalZoneDriver wraps DisplayDriver; NetworkZoneDriver wraps WebSocket | ✓ |
| Two separate maps | localZones: Map<String, DisplayDriver> + networkZones: Map<String, WebSocketSession> | |
| You decide | Planner picks simplest abstraction | |

**User's choice:** New ZoneDriver interface wrapping DisplayDriver and WebSocket client

---

| Option | Description | Selected |
|--------|-------------|----------|
| Full ScreenDriverService pipeline per zone | Mutex, history, metrics, conflict policy per zone | ✓ |
| ZoneRegistry dispatches directly for network zones | Network zones bypass ScreenDriverService | |
| You decide | Planner picks | |

**User's choice:** Yes, each zone call goes through ScreenDriverService

---

| Option | Description | Selected |
|--------|-------------|----------|
| Best-effort: continue to all, collect results | Parallel coroutines, aggregate {successful, failed} | ✓ |
| All-or-nothing | Any zone failure → whole request fails | |
| Fire and forget, always 202 | Silent failures | |

**User's choice:** Best-effort: continue to all zones, collect results

---

## Network Discovery Protocol

| Option | Description | Selected |
|--------|-------------|----------|
| UDP broadcast | Fixed port scan, no daemon required | |
| mDNS (Bonjour/Avahi) | Standard, requires jmdns + Avahi | |
| Both: UDP scan + mDNS passive | UDP for on-demand, mDNS for passive | ✓ |

**User's choice:** Both: UDP for discovery scan, mDNS for passive listening

---

| Option | Description | Selected |
|--------|-------------|----------|
| POST /api/v1/zones/discover | Dedicated endpoint for button trigger | ✓ |
| GET /api/v1/zones runs fresh scan | Every zones list call triggers scan | |
| Discovery on startup only | No on-demand API | |

**User's choice:** POST /api/v1/zones/discover (Phase 13 UI button will call this)
**Notes:** User confirmed discovery should be triggered by a UI button (button added in Phase 13). Phase 11 delivers the API endpoint.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed port 54321, JSON payload | Simple, no dependency | ✓ |
| You decide | Researcher/planner picks port and payload | |
| mDNS service type only | Skip custom UDP protocol | |

**User's choice:** Fixed port 54321, JSON payload — Pi broadcasts `{"type":"TEXTREADERRPI_DISCOVER"}`, device replies with `{"name":"...", "ip":"...", "type":"MAX7219"}`

---

| Option | Description | Selected |
|--------|-------------|----------|
| 3 seconds | Enough for home LAN, matches ZONE-03 requirement | ✓ |
| 5 seconds | More conservative for slower devices | |
| Configurable via env var | DISCOVERY_TIMEOUT_MS | |

**User's choice:** 3 seconds (hardcoded)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Continuous background listener | mDNS coroutine runs for app lifetime | ✓ |
| mDNS query only during discovery window | Only during POST /api/v1/zones/discover call | |
| mDNS not implemented, UDP only | Skip mDNS entirely, Phase 11 = UDP only | |

**User's choice:** Continuous background listener (mDNS coroutine runs always)

---

## Claude's Discretion

- WebSocket ping interval — planner picks (suggested: 15s ping, 30s no-pong timeout → OFFLINE)
- `network_zones` table schema — follow Exposed patterns from `SchedulesTable`/`HistoryTable`
- `jmdns` Ktor lifecycle wiring — follow `ApplicationStarted`/`ApplicationStopping` pattern from `SchedulerService`

## Deferred Ideas

- External device firmware (ESP32/Arduino) — v1.2+ reference implementation (already in REQUIREMENTS.md Future section)
- UI button "Scan for displays" on `/zones` page — Phase 13 (UI/UX Refresh)
- Dynamic zone creation via API without restart — v1.2+ (REQUIREMENTS.md Future section)
