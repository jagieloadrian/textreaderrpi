# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi  
**Created:** 2025-01-25  
**Status:** v1.1 — Complete (2026-06-21)

## Milestone: v1.1 Refactor + Fixes + UI + New Features (Complete 2026-06-21)

**Goal:** Spłacić dług techniczny z v1.0, przeprowadzić pełny refactor aplikacji, naprawić bugi hardware (MAX7219), odświeżyć UI/UX oraz dodać nowe funkcjonalności (historia, multi-zone, scheduler rewrite, webhooks).

**Target features:**
- MAX7219 chain order fix + pełny refactor warstwy driverów
- GET /health/detail, HTML error pages, /metrics hardware group, SKIP_NEW policy (gap closures)
- Pełny refactor aplikacji (uproszczenie, czytelność, usunięcie workaroundów)
- UI/UX refresh (layout, stylizacja, user experience)
- Historia wyświetlanych tekstów
- Przepisanie schedulera (coroutines, bez Flaxoos JDBC)
- Multi-zone (wiele wyświetlaczy jednocześnie)
- Webhooks / push notifications on schedule fire

## Vision

Wyświetlać text na ekranie podpietym do rasbperry pi (za pomoca pi4j), który można aktualizować na stronie webowej.

**English:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

## What This Is

A production-ready Raspberry Pi text display system with Material 3 UI. Text submitted through a responsive multi-zone web interface is routed to named hardware or network displays, processed via an effect pipeline (SCROLL/BLINK/REVERSE/FADE), and rendered on pluggable hardware drivers (MAX7219, LCD, OLED). The system is fully observable (health/detail, metrics hardware group, HTML error pages), schedules with webhook callbacks, records full display history, and supports multi-zone discovery and routing. Configurable via environment variables, Docker-ready.

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

**Gap closures from v1.0 audit:**
- ✓ `GET /health/detail` — uptime, memory, display status, error counts — Phase 12
- ✓ HTML 404/500 error pages (Ktor 3.5.0 SwaggerUI routing fixed) — Phase 12
- ✓ `/metrics` hardware group — display failures, recovery retries, resource slot utilization — Phase 12
- ✓ `SKIP_NEW` conflict policy — ConflictPolicy enum, conditional logic in `displayImmediate()` — Phase 7

**Hardware fixes:**
- ✓ MAX7219 chain order fix — scroll direction corrected, SPI packet direction fixed — Phase 6
- ✓ Full driver layer refactor (AbstractDisplayDriver base class) — Phase 6

**Refactor:**
- ✓ Application simplified and cleaned (DI smoke test, dead code removed, service nesting reduced) — Phase 8, 11.2

**UI/UX:**
- ✓ Material 3 dark/light theme, side navigation, all v1.1 pages deployed — Phase 13

**New features:**
- ✓ Display history + audit log (persisted, paginated, filterable) — Phase 9
- ✓ Scheduler schema stabilized (ConflictPolicy, firedAt, CRON validation, Flyway) — Phase 7
- ✓ Multi-zone displays (local + network discovery, zone routing) — Phase 11
- ✓ Webhooks — HTTP POST notifications on schedule fire — Phase 10

### Out of Scope

- Cloud sync or remote access beyond home network (home-network-first approach)
- User authentication/authorization (trusted home network)
- Mobile app (responsive web UI sufficient)
- Real-time video streaming
- Custom font support
- Multilingual text rendering

## Current State (v1.0 — Archived 2026-05-28)

**Framework:** Ktor 3.5.0 + Kotlin 2.3.21  
**ORM:** Exposed 1.3.0 (H2 default / PostgreSQL via env vars)  
**Hardware:** Pi4J 4.0.0 (MAX7219 SPI, LCD/OLED I2C)  
**Test suite:** 77 Kotlin source files, 3,895 LOC, 20 test classes (Kotest `should` convention)

**Delivered features:**
- `POST /api/v1/text` with scroll/blink/reverse/fade effects
- Schedule engine: ONESHOT, RECURRING (intervals), CRON — persisted in H2/PostgreSQL
- `POST /api/v1/schedule/{id}/cancel` — stop recurring schedule without deleting
- Display driver abstraction: MAX7219, LCD, OLED, Offline
- Health, metrics, rate limiting, Swagger UI
- Schedule management HTML page with Stop/Delete actions
- Full env var config (`${VAR:default}` for all 25 settings)
- Gradle-based Docker image build (`./gradlew publishImageToLocalRegistry`)

## Key Decisions

| Decision | Outcome | Phase |
|----------|---------|-------|
| Ktor HTML DSL for UI (no JS framework) | ✓ Good — clean server-side templates, no build tooling | 2 |
| Pi4J 4.0.0 + SPI for MAX7219 | ✓ Good — stable hardware interface | 2 |
| KHealth plugin for health endpoints | ✓ Good — aligns with Ktor plugin model | 3 |
| Token bucket rate limiting | ✓ Good — simple, effective for home use | 3 |
| H2 default / PostgreSQL via env var | ✓ Good — zero-config dev, production-ready toggle | 5 |
| Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`) | ✓ Good — latest stable with API alignment | 5 |
| Flaxoos JDBC task scheduler | ⚠️ Revisit — cluster-safe but adds `task_locks` table complexity for single-node Pi | 5 |
| `${VAR:default}` env var config pattern | ✓ Good — all 25 settings overridable without code changes | 5 |
| Flyway 9.22.3 for migrations (baselineOnMigrate) | ✓ Good — handles existing Pi installs; 9.x community license | 7 |
| Single shared Pi4J context + unique string IDs per zone | ✓ Good — avoids SPI registration collision on startup | 11 |
| ZoneStatus in-memory only (not persisted) | ✓ Good — avoids stale offline status across restarts | 11 |
| HistoryService layer between routes and repository | ✓ Good — prevents direct repository access from routes | 11.2 |
| Material 3 CSS custom properties (no data-theme attribute) | ✓ Good — OS dark/light mode via prefers-color-scheme media query | 13 |
| SSR zone selectors (not client-fetched) | ✓ Good — zones available on page load without extra fetch | 13 |

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

## Next Steps

- `/gsd-complete-milestone v1.1` — archive milestone and start planning v1.2

---
*Last updated: 2026-06-21 after Phase 13 (UI/UX Refresh). Milestone v1.1 complete — all 9 phases, 32 plans done. Material 3 UI, multi-zone, history, webhooks, and observability all shipped.*
