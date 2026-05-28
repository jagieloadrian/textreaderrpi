# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi  
**Created:** 2025-01-25  
**Status:** v1.0 — All 5 phases complete, ready for milestone archive

## Vision

Wyświetlać text na ekranie podpietym do rasbperry pi (za pomoca pi4j), który można aktualizować na stronie webowej.

**English:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

## Target Users & Stakeholders

- **Primary:** Home lab enthusiasts, DIY electronics hobbyists
- **Use Case:** Home automation projects, status displays, notification systems

## Key Success Metrics

1. **Functionality:** Text input on website successfully displays on LED screen ✅
2. **Flexibility:** Support multiple screen types and configurations ✅
3. **Reliability:** 99.5% uptime, no crashes ✅

## Current State (v1.0 — 2026-05-28)

**Framework:** Ktor 3.5.0 + Kotlin 2.3.21  
**ORM:** Exposed 1.3.0 (H2 default / PostgreSQL via env vars)  
**Hardware:** Pi4J 4.0.0 (MAX7219 SPI, LCD/OLED I2C)

**Delivered features:**
- `POST /api/v1/text` with scroll/blink/reverse/fade effects
- Schedule engine: ONESHOT, RECURRING (intervals), CRON — persisted in H2/PostgreSQL
- `POST /api/v1/schedule/{id}/cancel` — stop recurring schedule without deleting
- Display driver abstraction: MAX7219, LCD, OLED, Offline
- Health, metrics, rate limiting, Swagger UI
- Schedule management HTML page with Stop/Delete actions
- Full env var config (`${VAR:default}` for all 25 settings)
- Gradle-based Docker image build (`./gradlew publishImageToLocalRegistry`)
- 20 test classes, Kotest `should` convention, virtual-time scheduler tests

## Timeline

- No hard deadline  
- Iterative development — 5 phases shipped

## Next Steps

- `/gsd-ship` — push branch + PR
- `/gsd-complete-milestone v1.0` — archive milestone, tag v1.0
- `/gsd-new-milestone` — define next milestone (v2.0 scope)
