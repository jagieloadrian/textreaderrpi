# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi  
**Created:** 2025-01-25  
**Status:** v1.0 — Milestone ARCHIVED ✅ | Preparing for v2.0

## Vision

Wyświetlać text na ekranie podpietym do rasbperry pi (za pomoca pi4j), który można aktualizować na stronie webowej.

**English:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

## What This Is

A production-ready Raspberry Pi text display system. Text submitted through a responsive web UI is routed through a scheduling engine, processed via an effect pipeline (SCROLL/BLINK/REVERSE/FADE), and rendered on pluggable hardware drivers (MAX7219, LCD, OLED). The system is fully observable, rate-limited, Docker-ready, and configurable via environment variables.

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

### Active (v2.0 candidates)

- [ ] Multiple concurrent displays (multi-zone)
- [ ] Remote access beyond home network (VPN/tunnel)
- [ ] Display text history/audit log
- [ ] Webhook / push notification on schedule fire
- [ ] Evaluate replacing Flaxoos JDBC scheduler with simpler coroutine approach

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

## Constraints

- **Hardware:** Raspberry Pi 4 (4GB), GPIO/I2C/SPI enabled, single node
- **Language:** Kotlin / JVM (JDK 25 toolchain, targeting RPi OS)
- **Network:** Home network only (no auth, no TLS required)
- **Memory:** <256MB JVM heap target

## Next Steps

- `/gsd-new-milestone` — define v2.0 scope (questioning → research → requirements → roadmap)

---
*Last updated: 2026-05-28 after v1.0 milestone archive*
