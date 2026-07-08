---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Firmware + Features + Refactor + Ops
current_phase: null
status: milestone_complete
last_updated: "2026-07-08T14:30:00.000Z"
last_activity: 2026-07-08
last_activity_desc: Milestone v1.2 completed and archived
progress:
  total_phases: 7
  completed_phases: 7
  total_plans: 35
  completed_plans: 35
  percent: 100
stopped_at: v1.2 milestone closed
current_phase_name: null
---

# Project State & Memory

**Last Updated:** 2026-07-08  
**Status:** v1.2 milestone complete

## Current Position

Phase: Milestone v1.2 complete
Plan: —
Status: Awaiting next milestone
Last activity: 2026-07-08 — Milestone v1.2 completed and archived

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-07-08, full v1.2 evolution review)

**Core value:** Simple, reliable one-way display control from any browser on the home network
**Current focus:** Planning next milestone — run `/gsd-new-milestone`

## Project Context

- **Name:** TextReaderRpi
- **Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface
- **Core value:** Simple, reliable one-way display control from any browser on the home network
- **Users:** Home lab enthusiasts, DIY electronics hobbyists
- **Timeline:** No hard deadline; iterative development

---

## Codebase Status

- **Language:** Kotlin 2.3.21, JDK 25 toolchain
- **Framework:** Ktor 3.5.0 (DI plugin + RequestValidation + StatusPages)
- **ORM:** Exposed 1.3.0 (`org.jetbrains.exposed.v1.*` packages)
- **Hardware:** Pi4J 4.0.0 + MAX7219 via SPI, LCD/OLED via I2C
- **Database:** H2 (embedded default) or PostgreSQL (via env vars)
- **Current package root:** `src/main/kotlin/com/anjo/...`
- **Test suite:** Kotest `should` convention, JaCoCo ≥70% gate held through v1.2 (4,775 LOC main + 4,168 LOC test)

---

## Existing Features (v1.2 complete)

- ✅ Typed YAML config with env var overrides (`${VAR:default}` for all 25 settings)
- ✅ Request validation via Ktor `RequestValidation`
- ✅ Centralized error mapping via Ktor `StatusPages`
- ✅ Text endpoint `POST /api/v1/text` with effects (SCROLL/BLINK/REVERSE/FADE)
- ✅ Display driver abstraction: MAX7219, LCD, OLED + OfflineDisplayDriver
- ✅ Health endpoints: `GET /health` (liveness) + `GET /health/ready` (readiness) + `GET /health/detail`
- ✅ `GET /metrics` — runtime/API/hardware metrics JSON
- ✅ Rate limiting (60 req/min API, 120 req/min metrics)
- ✅ Schedule CRUD API: `POST/GET/PATCH/DELETE /api/v1/schedule`
- ✅ Schedule types: ONESHOT, RECURRING, CRON; ConflictPolicy: INTERRUPT/SKIP_NEW
- ✅ Effect renderer strategy pattern (ScrollEffect/BlinkEffect/ReverseEffect/FadeEffect)
- ✅ Flyway 9.22.3 migrations (V1–V6, baselineOnMigrate=true)
- ✅ Display history: `GET /api/v1/history` (paginated, zone/effect filter, full-text search) + CSV export + HTML page
- ✅ Webhooks: fire-and-forget HTTP POST on schedule fire (5s timeout, fallback env var)
- ✅ Multi-zone: ZoneRegistry + local SPI zones + UDP/mDNS network zone autodiscovery + dynamic creation via `POST /api/v1/zones`
- ✅ NetworkZoneDriver: WebSocket client with reconnect + heartbeat
- ✅ Firmware zones: RPi Pico (pico-sdk) + ESP32 (ESP-IDF) WebSocket display clients via `GET /ws/zone/{id}`
- ✅ SSE live feed: `GET /api/v1/live` real-time event stream, status page EventSource widget
- ✅ Material 3 UI: side nav, dark mode, zone selector, effect preview, status/history/schedule/zones pages
- ✅ Kubernetes Helm chart: `.devops/helm/textreaderrpi/` with hardware-access and resource controls

---

## Accumulated Context

v1.2's pitfall list and full per-phase decision log were resolved/shipped and are archived — see `.planning/PROJECT.md` Key Decisions table (curated, cross-milestone) and `.planning/RETROSPECTIVE.md` (v1.2 section: what worked, what was inefficient, patterns established, key lessons).

---

## Architecture Summary

**Layers:**

1. HTTP routes (`com/anjo/routing/*`)
2. Service layer (`com/anjo/service/*`, `com/anjo/service/effect/*`)
3. Driver abstraction + implementations (`com/anjo/driver/*`)
4. Zone layer (`com/anjo/zone/*`)
5. Database layer (`com/anjo/db/*`)
6. DI/plugin setup (`com/anjo/di/*`) + config (`com/anjo/config/*`)

**Data Flows:**

- `POST /api/v1/text` → `TextRoutes` → `ScreenDriverService.displayImmediate(text, effect)` → `EffectRenderer.render()` → `DisplayDriver` + `HistoryService.record()` + `LiveFeedService.emit()` (v1.2)
- `POST /api/v1/schedule` → `ScheduleRoutes` → `ScheduleRepository.insert()` + `SchedulerService.schedule()`
- `SchedulerService.fire()` → `EffectRendererFactory.create(effect)` → `ScreenDriverService.displayScheduled()` → `WebhookService.send()`
- `GET /api/v1/live` → SSE `MutableSharedFlow<LiveEvent>(replay=5)` (v1.2)
- `GET /ws/zone/{id}` → `FirmwareZoneDriver` registered in `ZoneRegistry` (v1.2)

---

## DevOps

- **Docker:** `./gradlew publishImageToLocalRegistry` (no Dockerfile in repo)
- **Compose:** `.devops/containers/docker-compose.yml` — full env var mapping, no build section
- **Host:** `.devops/host/` — systemd unit + install script
- **Env template:** `.env.example` at project root + `.devops/containers/.env.example`
- **Helm:** `.devops/helm/textreaderrpi/` (v1.2, Phase 18)

---

## Deferred Items

Items acknowledged and deferred at v1.2 milestone close on 2026-07-08 (supersedes the v1.1-era list — phases 08/09/11/12/15/16 human-verify items were resolved during v1.1/v1.2 execution):

| Category | Item | Status |
|----------|------|--------|
| verification | Phase 17 (Firmware Skeletons) — 7 physical-hardware UAT checks (Pico/ESP32 flash, scroll, reconnect, OTA, captive portal) | human_needed |

*Note: FW-01/FW-02 are satisfied at the code+CI level (build-check + flashable-artifact CI job passes). Physical-device verification requires hardware and cannot run in CI. See `.planning/milestones/v1.2-MILESTONE-AUDIT.md` for the full audit.*

---

## Milestone Archive

- **v1.0 archived:** 2026-05-28
- **v1.1 archived:** 2026-06-21
- **v1.2 archived:** 2026-07-08
- Roadmap archive v1.0: `.planning/milestones/v1.0-ROADMAP.md`
- Roadmap archive v1.1: `.planning/milestones/v1.1-ROADMAP.md`
- Roadmap archive v1.2: `.planning/milestones/v1.2-ROADMAP.md`
- Requirements archive v1.2: `.planning/milestones/v1.2-REQUIREMENTS.md`
- Audit archive v1.2: `.planning/milestones/v1.2-MILESTONE-AUDIT.md`
- Git tags: `v1.0`, `v1.1`, `v1.2`

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
