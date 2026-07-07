# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi
**Created:** 2025-01-25
**Status:** v1.2 — In Planning

## What This Is

A production-ready Raspberry Pi text display system with Material 3 UI. Text submitted through a responsive multi-zone web interface is routed to named hardware or network displays, processed via an effect pipeline (SCROLL/BLINK/REVERSE/FADE), and rendered on pluggable hardware drivers (MAX7219, LCD, OLED). The system is fully observable (health/detail, metrics hardware group, HTML error pages), schedules with webhook callbacks, records full display history, and supports multi-zone discovery and routing. Configurable via environment variables, Docker-ready.

## Vision

Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

## Core Value

Simple, reliable one-way display control from any browser on the home network.

## Target Users & Stakeholders

- **Primary:** Home lab enthusiasts, DIY electronics hobbyists
- **Use Case:** Home automation projects, status displays, notification systems

## Key Success Metrics

1. **Functionality:** Text input on website successfully displays on LED screen ✅
2. **Flexibility:** Support multiple screen types and configurations ✅
3. **Reliability:** 99.5% uptime, no crashes ✅

## Requirements

### Validated (v1.0)

- ✓ HTTP API for text submission (`POST /api/v1/text`) — v1.0
- ✓ MAX7219 LED rendering with scrolling — v1.0
- ✓ Request validation (length, charset) — v1.0
- ✓ Centralized error handling (400/422/500) — v1.0
- ✓ DisplayDriver abstraction (MAX7219, LCD, OLED, Offline) — v1.0
- ✓ Configuration-driven display selection — v1.0
- ✓ Responsive HTML UI (Ktor HTML DSL) — v1.0
- ✓ Health endpoints `/health` + `/health/ready` — v1.0
- ✓ Rate limiting 60 req/min — v1.0
- ✓ Hardware error recovery (RecoveryPolicy + retry) — v1.0
- ✓ `/metrics` JSON endpoint (runtime/API/hardware) — v1.0
- ✓ Scheduling engine (ONESHOT/RECURRING/CRON) — v1.0
- ✓ Effect pipeline (SCROLL/BLINK/REVERSE/FADE) — v1.0
- ✓ Cancel endpoint for running schedules — v1.0
- ✓ Full env var config (25 settings) — v1.0
- ✓ Docker image build via Gradle — v1.0

### Validated (v1.1)

- ✓ MAX7219 chain order fix (SPI packet direction corrected) — Phase 6
- ✓ AbstractDisplayDriver base class (DRY driver hierarchy) — Phase 6
- ✓ SKIP_NEW ConflictPolicy enum — Phase 7
- ✓ ONESHOT firedAt atomic update (crash-safe restart) — Phase 7
- ✓ CRON ERROR persistence (invalid expression → stored, not looped) — Phase 7
- ✓ Flyway 9.22.3 migration layer (baselineOnMigrate) — Phase 7
- ✓ DI smoke test (all 11 configureDI() bindings verified) — Phase 8
- ✓ Dead code removed (HardwareConfig/TimingConfig/LoggingConfig, readInput) — Phase 8
- ✓ Display history persisted (1000-row cap, all paths instrumented) — Phase 9
- ✓ GET /api/v1/history — paginated + filterable API — Phase 9
- ✓ GET /history — filterable HTML page (zone + effect) — Phase 9
- ✓ Webhooks — fire-and-forget HTTP POST on schedule fire (5s timeout) — Phase 10
- ✓ Webhook fallback via WEBHOOK_DEFAULT_URL env var — Phase 10
- ✓ webhookStatus in history records (sent/skipped) — Phase 10
- ✓ Multi-zone local displays (ZoneRegistry, SPI named zones) — Phase 11
- ✓ Network zone autodiscovery (UDP broadcast + mDNS/JmDNS) — Phase 11
- ✓ NetworkZoneDriver (WebSocket client + reconnect + heartbeat) — Phase 11
- ✓ POST /api/v1/text?zone=X routing — Phase 11
- ✓ DELETE /api/v1/zones/{id} — network zone removal — Phase 11
- ✓ DisplayType enum enforcement + HistoryService layer — Phase 11.2
- ✓ GET /health/detail — uptime, memory, display status, error counts — Phase 12
- ✓ /metrics hardware group (4 Dropwizard counters) — Phase 12
- ✓ HTML 404/500 pages (SwaggerUI ordering fixed) — Phase 12
- ✓ Material 3 CSS + side navigation + dark mode (prefers-color-scheme) — Phase 13
- ✓ Zone selector + effect preview on send-text form — Phase 13
- ✓ Schedule page with zone + webhookUrl columns — Phase 13
- ✓ History page with zone/effect filter + pagination — Phase 13
- ✓ Status page polling /health/detail + /metrics — Phase 13

### Validated (v1.2)

- ✓ SSE live feed (`GET /api/v1/live`) — real-time event stream of displayed text; status page widget via EventSource — Phase 15
- ✓ Dynamic zone creation via API (without restart) — `POST /api/v1/zones` + Zones UI page with Add Zone form, type toggle, toast feedback — Phase 16

### Active (v1.2)
- [ ] Full-text search in display history
- [ ] Export history to CSV
- [ ] Firmware submodule: RPi Pico (PicoW/Pico2/Pico2W) — Kotlin Native WebSocket receiver + local display rendering
- [ ] Firmware submodule: ESP32 series — Kotlin Native WebSocket receiver + local display rendering
- [ ] DRY/YAGNI refactoring — main code and tests (simplification, deduplication)
- [ ] Kubernetes manifests + Helm chart in .devops/
- [ ] Compress .planning/ files — remove noise, retain decisions/summaries/conventions
- [ ] Delete docs/ folder
- [ ] Update README.md

### Out of Scope

- Cloud sync or remote access beyond home network (home-network-first approach)
- User authentication/authorization (trusted home network)
- Mobile app (responsive web UI sufficient)
- Real-time video streaming
- Custom font support / multilingual text rendering
- JS framework (React, Vue, HTMX) — Ktor HTML DSL sufficient

## Current State (v1.1 — Shipped 2026-06-21)

**Framework:** Ktor 3.5.0 + Kotlin 2.3.21 (JDK 25 toolchain)
**ORM:** Exposed 1.3.0 (H2 default / PostgreSQL via env vars)
**Hardware:** Pi4J 4.0.0 (MAX7219 SPI, LCD/OLED I2C)
**Migrations:** Flyway 9.22.3 (V1–V5 + future via baselineOnMigrate)
**Network:** JmDNS + ktor-client-cio/websockets for zone discovery
**Test suite:** 4,325 LOC main + 3,379 LOC test | JaCoCo LINE ≥ 70% (actual: 80.7%)
**Deferred:** 4 on-device hardware verification checkpoints (human_needed)
**v1.2 progress:** Phase 18 complete (2026-07-07) — Helm chart for Kubernetes deployment with hardware-access and resource controls (`.devops/helm/textreaderrpi/`). Validated in Phase 18: OPS-01.

**New in v1.1:**
- `GET /health/detail` — HealthDetailResponse (uptime, memory, display status, error counts)
- `GET /metrics` — hardware group with 4 Dropwizard counters
- HTML 404/500 pages (SwaggerUI catch-all ordering fix)
- `ConflictPolicy` enum (SKIP_NEW/INTERRUPT) + Flyway schema migrations (V1–V5)
- `GET|POST /api/v1/history` — paginated display event log with zone/effect filters
- `WebhookService` — fire-and-forget HTTP POST, 5s timeout, fallback env var
- `ZoneRegistry` + `NetworkDiscoveryService` — local SPI zones + UDP/mDNS network zones
- `NetworkZoneDriver` — WebSocket client with reconnect + heartbeat
- `HistoryService` layer — decouples routes from `HistoryRepository`
- Material 3 UI — side nav, dark mode, zone selector, effect preview, all pages rebuilt

## Key Decisions

| Decision | Outcome | Phase |
|----------|---------|-------|
| Ktor HTML DSL for UI (no JS framework) | ✓ Good — clean SSR, no build tooling | 2 |
| Pi4J 4.0.0 + SPI for MAX7219 | ✓ Good — stable hardware interface | 2 |
| KHealth plugin for health endpoints | ✓ Good — aligns with Ktor plugin model | 3 |
| Token bucket rate limiting | ✓ Good — simple, effective for home use | 3 |
| H2 default / PostgreSQL via env var | ✓ Good — zero-config dev, production-ready toggle | 5 |
| Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`) | ✓ Good — latest stable with API alignment | 5 |
| Flaxoos JDBC task scheduler → replaced | ✓ Resolved — custom coroutine SchedulerService replaces cluster-safe Flaxoos | 5 |
| `${VAR:default}` env var config pattern | ✓ Good — all settings overridable without code changes | 5 |
| Flyway 9.22.3 (baselineOnMigrate=true) | ✓ Good — handles existing Pi installs; community license | 7 |
| buildPacket in companion object | ✓ Good — pure-JVM testable without Pi4J construction | 6 |
| updateFiredAtAndDone single suspendTransaction | ✓ Good — crash-safe atomic ONESHOT firedAt+DONE update | 7 |
| displayMutex.isLocked for SKIP_NEW | ✓ Good — snapshot read avoids deadlock; false negatives acceptable | 7 |
| DI smoke test first (before refactor) | ✓ Good — prevented silent DI failures during all subsequent refactoring | 8 |
| Single shared Pi4J context + unique string IDs per zone | ✓ Good — avoids SPI registration collision on startup | 11 |
| ZoneStatus in-memory only (not persisted) | ✓ Good — avoids stale offline status across restarts | 11 |
| parseDiscoveryReply ignores JSON "ip" → uses kernel senderIp | ✓ Good — prevents SSRF via rogue device advertisement | 11 |
| HistoryService layer between routes and repository | ✓ Good — prevents direct repository calls from route handlers | 11.2 |
| Material 3 CSS custom properties (no data-theme) | ✓ Good — OS dark/light mode via prefers-color-scheme, no JS toggle | 13 |
| SSR zone selectors (not client-fetched) | ✓ Good — zones available on page load without extra round-trip | 13 |
| `id: String` in DisplayEvent (not Long) | ✓ Good — matches HistoryRecord.id UUID String exactly; avoids lossy conversion | 15 |
| `displayEventBus: DisplayEventBus? = null` as last ScreenDriverService param | ✓ Good — all existing tests compile unchanged; nullable default avoids forced migration | 15 |
| heartbeat declared before `collect` in `sse {}` block | ✓ Good — collect suspends forever; heartbeat after collect would never execute (Pitfall 4) | 15 |
| liveRoutes in SEPARATE `route("/api/v1")` outside `installApiRateLimiting` | ✓ Good — long-lived SSE connections + 30s heartbeats don't count against 60 req/min limit | 15 |
| `textContent` for live-feed.js DOM updates (never innerHTML) | ✓ Good — SSE-delivered data treated as untrusted at DOM boundary; XSS-safe | 15 |
| live-feed.js loaded via `headExtra` in StatusPage only | ✓ Good — limits always-open SSE connection to /status page; doesn't affect other routes | 15 |
| Zone name validated with `[a-zA-Z0-9._-]{1,64}` regex in ZoneValidators | ✓ Good — prevents XSS via kotlinx.html `attributes[key]=value` (unescaped) + enforces DB varchar(64) bound | 16 |
| req.ip null guard via early 400 return (no !! operator) | ✓ Good — eliminates NPE risk if validator ever bypassed; explicit error message | 16 |
| FirmwareZoneDriver drain job tracked via AtomicReference<Job?>, cancelled on re-attach | ✓ Good — prevents double-drain race on rapid firmware reconnect | 16 |
| UDP discovery reply parsed with kotlinx.json; FIRMWARE type from UDP rejected | ✓ Good — prevents type confusion from rogue LAN devices; consistent with parseDiscoveryReply SSRF guard | 16 |
| addZone() form.reset() on 201 + showToast() for all error paths | ✓ Good — UX gap closure: blank fields after success + styled error toasts for 422/409 | 16 |
| V6 migration: ip column nullable + display_subtype added to network_zones | ✓ Good — FIRMWARE zones have no IP; display_subtype holds hardware type for non-Network zones | 16 |

## Constraints

- **Hardware:** Raspberry Pi 4 (4GB), GPIO/I2C/SPI enabled, single node
- **Language:** Kotlin / JVM (JDK 25 toolchain, targeting RPi OS)
- **Network:** Home network only (no auth, no TLS required)
- **Memory:** <256MB JVM heap target

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

## Current Milestone: v1.2 Firmware + Features + Refactor + Ops

**Goal:** Rozszerzyć ekosystem o firmware na mikrokontrolery (Pico/ESP32), dodać nowe funkcje serwera (live feed, dynamiczne strefy, historia), przeprowadzić refactoring DRY/YAGNI i zaktualizować dokumentację/ops.

**Target features:**
- WebSocket live feed — real-time podgląd wyświetlanego tekstu w przeglądarce
- Dynamic zone creation — nowe strefy przez API bez restartu
- Full-text search w historii wyświetleń
- Export historii do CSV
- Firmware submodule: RPi Pico (PicoW/Pico2/Pico2W) + ESP32 — Kotlin Native, WebSocket client, lokalne renderowanie efektów
- DRY/YAGNI refactoring (kod główny + testy)
- Kubernetes + Helm w .devops/
- Kompresja .planning/, usunięcie docs/, update README

---
*Last updated: 2026-07-07 — After Phase 18 (Kubernetes Helm).*
