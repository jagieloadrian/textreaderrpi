# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi
**Created:** 2025-01-25
**Status:** v1.2 — Shipped 2026-07-08

## What This Is

A production-ready Raspberry Pi text display system with Material 3 UI. Text submitted through a responsive multi-zone web interface is routed to named hardware or network displays (including RPi Pico and ESP32 firmware clients over WebSocket), processed via an effect pipeline (SCROLL/BLINK/REVERSE/FADE), and rendered on pluggable hardware drivers (MAX7219, LCD, OLED). The system is fully observable (health/detail, metrics hardware group, HTML error pages, real-time SSE live feed), schedules with webhook callbacks, records full-text-searchable display history with CSV export, and supports multi-zone discovery, routing, and dynamic zone creation via API. Configurable via environment variables, deployable via Docker or a Kubernetes Helm chart.

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

### Validated (v1.0 + v1.1)

See `.planning/milestones/v1.0-REQUIREMENTS.md` and `v1.1-REQUIREMENTS.md` for full requirement lists and traceability. Summary: 25 v1.0 requirements + 8 v1.1 requirements delivered across 14 phases with 80.7% test coverage.

### Validated (v1.2)

- ✓ Full-text search in display history — `GET /api/v1/history?search=` with `<mark>`-highlighted matches — Phase 14
- ✓ Export history to CSV — RFC 4180 format, `Content-Disposition: attachment` — Phase 14
- ✓ SSE live feed (`GET /api/v1/live`) — real-time event stream of displayed text; status page widget via EventSource — Phase 15
- ✓ Dynamic zone creation via API (without restart) — `POST /api/v1/zones` + Zones UI page with Add Zone form, type toggle, toast feedback — Phase 16
- ✓ Firmware submodule: RPi Pico (PicoW/Pico2/Pico2W) — pico-sdk/CMake WebSocket receiver + local display rendering; CI-verified, 7 physical-hardware UAT checks deferred (see MILESTONES.md Known Gaps) — Phase 17
- ✓ Firmware submodule: ESP32 series — ESP-IDF/CMake WebSocket receiver + local display rendering; CI-verified, physical-hardware UAT deferred — Phase 17
- ✓ Kubernetes manifests + Helm chart in .devops/ — `.devops/helm/textreaderrpi/` with hardware-access and resource controls — Phase 18
- ✓ DRY/YAGNI refactoring — main code and tests (simplification, deduplication); code-review found and fixed 4 warnings — Phase 19
- ✓ Compress .planning/ files — STATE.md/MILESTONES.md trimmed to decisions only, phase dirs 14-19 deleted (history preserved in git) — Phase 20
- ✓ Delete docs/ folder — 8 files removed, zero salvageable content beyond what README already covers — Phase 20
- ✓ Update README.md — v1.2 reference sections (API/Configuration/Deployment), new Firmware flash-walkthrough section, repo-wide accuracy sweep — Phase 20

### Active (v1.3)
(none yet — run `/gsd-new-milestone` to define)

### Out of Scope

- Cloud sync or remote access beyond home network (home-network-first approach)
- User authentication/authorization (trusted home network)
- Mobile app (responsive web UI sufficient)
- Real-time video streaming
- Custom font support / multilingual text rendering
- JS framework (React, Vue, HTMX) — Ktor HTML DSL sufficient

## Current State (v1.2 — Shipped 2026-07-08)

**Framework:** Ktor 3.5.0 + Kotlin 2.3.21 (JDK 25 toolchain)
**ORM:** Exposed 1.3.0 (H2 default / PostgreSQL via env vars)
**Hardware:** Pi4J 4.0.0 (MAX7219 SPI, LCD/OLED I2C) + RPi Pico (pico-sdk) and ESP32 (ESP-IDF) firmware WebSocket clients
**Migrations:** Flyway 9.22.3 (V1–V6 + future via baselineOnMigrate)
**Network:** JmDNS + ktor-client-cio/websockets for zone discovery; SSE for live feed
**Deployment:** Docker (Gradle build) + Kubernetes Helm chart (`.devops/helm/textreaderrpi/`)
**Test suite:** 4,775 LOC main + 4,168 LOC test (8,943 total)
**Deferred:** 7 physical-hardware firmware UAT checks (Phase 17, human_needed) — see MILESTONES.md Known Gaps

**New in v1.2:**
- `GET /api/v1/live` — SSE real-time event stream of displayed text, status page EventSource widget
- `GET /api/v1/history?search=` — full-text search with `<mark>`-highlighted matches; CSV export (RFC 4180)
- `POST /api/v1/zones` — dynamic zone creation via API without restart; Zones UI page
- RPi Pico and ESP32 firmware skeletons — WebSocket display clients with reconnect/heartbeat, CI-verified
- Kubernetes Helm chart with hardware-access and resource controls
- DRY/YAGNI refactoring pass across main code and tests
- Repository cleanup: `docs/` deleted, `.planning/` compressed, README rewritten and accuracy-swept

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
| Content-Disposition set before respondText for CSV export | ✓ Good — Ktor builder produces unquoted filename=history.csv (valid RFC) | 14 |
| sanitizeSearchTerm called in both list and export handlers | ✓ Good — single validation point, no bypass via export path | 14 |
| @EncodeDefault(Mode.NEVER) per-field, not global explicitNulls=false | ✓ Good — scoped to FirmwareMessage, doesn't affect other serialization paths | 17 |
| embeddedServer(Netty)+CIO client for firmware WS tests, not testApplication | ✓ Good — testApplication's test dispatcher blocks on session.launch drain job; real Netty avoids it | 17 |
| Helm secret.yaml: nested {{- if not $password }}/{{- if $existing }} guards, not `and` | ✓ Good — avoids Go-template short-circuit version dependency | 18 |
| Compress .planning/ files — STATE.md/MILESTONES.md trimmed, phase dirs 14-19 deleted (history preserved) | ✓ Good — but disk-scan tooling (phases.list, init.manager) now false-negatives on deleted phases; milestone close required --force + git-history reconstruction | 20 |

## Constraints

- **Hardware:** Raspberry Pi 4 (4GB), GPIO/I2C/SPI enabled, single node
- **Language:** Kotlin / JVM (JDK 25 toolchain, targeting RPi OS)
- **Network:** Home network only (no auth, no TLS required)
- **Memory:** <256MB JVM heap target

## Document Evolution

This document is updated after each milestone (v1.2 last updated 2026-07-08). Sections are maintained in priority order: Vision/Core Value rarely change; Current State and Key Decisions evolve with each phase; Requirements are archived per milestone to keep this document bounded.

## Milestone History

- **v1.2 Firmware + Features + Refactor + Ops** — Shipped 2026-07-08 (7 phases, 35 plans). See `.planning/MILESTONES.md` for full accomplishments and `.planning/milestones/v1.2-*` for archived roadmap/requirements/audit.
- **v1.1 Refactor + Fixes + UI + New Features** — Shipped 2026-06-21
- **v1.0 MVP** — Shipped 2026-05-28

## Next Milestone

Not yet defined. Run `/gsd-new-milestone` to start questioning → research → requirements → roadmap for v1.3.

---
*Last updated: 2026-07-08 — After v1.2 milestone close (full evolution review).*
