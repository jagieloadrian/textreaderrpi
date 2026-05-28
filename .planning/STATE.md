# Project State & Memory

**Last Updated:** 2026-05-28  
**Status:** Phase 5 COMPLETE — verified 2026-05-28; all 5 phases complete

## Current State

### Project Context
- **Name:** TextReaderRpi
- **Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface
- **Users:** Home lab enthusiasts, DIY electronics hobbyists
- **Timeline:** No hard deadline; iterative development

### Codebase Status
- **Language:** Kotlin 2.x, JDK 21+ toolchain
- **Framework:** Ktor (DI plugin + RequestValidation + StatusPages)
- **Hardware:** Pi4J + MAX7219 via SPI
- **Current package root:** `src/main/kotlin/com/anjo/...`
- **Existing Features:**
  - ✅ Typed YAML config (`ConfigLoader` + `config.model.*`)
  - ✅ Request validation via Ktor `RequestValidation`
  - ✅ Centralized error mapping via Ktor `StatusPages`
  - ✅ Business text endpoint in dedicated route module (`POST /api/text`)
  - ✅ MAX7219 rendering pipeline (`ReaderInputService` -> `ScreenDriverService` -> `DisplayDriver`)

### Documentation Completed
- ✅ `.planning/codebase/` (7 docs: STACK, INTEGRATIONS, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, CONCERNS)
- ✅ `.planning/research/DISPLAY_TECHNOLOGIES.md` (Landscape analysis of display types, Pi4J ecosystem)
- ✅ `.planning/research/KTOR_PATTERNS.md` (Best practices for embedded systems)
- ✅ `.planning/PROJECT.md` (Project context and vision)
- ✅ `.planning/REQUIREMENTS.md` (Feature scope and acceptance criteria)
- ✅ `.planning/ROADMAP.md` (5-phase development plan)
- ✅ `.planning/config.json` (Workflow preferences)

## Known Quality Gaps (from CONCERNS.md)

### Critical for Phase 1
- ❌ No service unit tests (ReaderInputService, ScreenDriverService)
- ❌ No driver integration tests
- ❌ Hardcoded configuration (GPIO pins, SPI path, display size, timing)
- ❌ No input length validation
- ❌ No timeout protection for SPI operations
- ❌ No error logging in services

### Important for Phase 2+
- ❌ No multi-display support (abstraction layer missing)
- ❌ No rate limiting
- ❌ No health check endpoints
- ❌ No graceful error recovery
- ❌ No resource monitoring

## Architecture Summary

**Layers:**
1. Routing composition + endpoint modules (`com/anjo/routing/*`)
2. Service layer (`com/anjo/service/*`)
3. Driver abstraction + MAX7219 implementation (`com/anjo/driver/*`)
4. DI/plugin setup (`com/anjo/di/*`) + typed config (`com/anjo/config/*`)

**Data Flow:** HTTP Request -> `TextRoutes` -> `ReaderInputService` -> `ScreenDriverService` -> `DisplayDriver` -> MAX7219 SPI

**Async Pattern:** `Dispatchers.IO` for rendering/hardware operations

## Key Decisions

| Decision | Rationale | Validated |
|----------|-----------|-----------|
| Keep MAX7219 as MVP focus | Low cost, proven hardware, existing codebase | ✅ |
| Add abstraction for future displays | Enable I2C LCD/OLED support in Phase 2 | ✅ Research confirms pattern |
| Use DisplayDriver interface pattern | Standard for hardware abstraction, easy to test | ✅ |
| Ktor + Kotlin for embedded | Low memory footprint (CIO engine), async-first | ✅ |
| Dispatchers.IO for blocking I/O | Safe coroutine pattern for GPIO/SPI | ✅ |
| Defer authentication | Home network only; add if exposed later | ✅ |

## Success Criteria Status

| Metric | Target | Status | Notes |
|--------|--------|--------|-------|
| Functionality | Text → LED works | 🟢 Verified | Endpoint + validation flow verified |
| Flexibility | Support multiple displays | ⚪ Not started | Phase 2 work |
| Reliability | 99.5% uptime, no crashes | 🟡 Unknown | Needs 24h test |
| Test coverage | >70% | 🟢 Verified | JaCoCo gate enabled and passing (`jacocoTestCoverageVerification`) |

## Phase 4 Status

✅ **All Waves Complete — Phase VERIFIED (2026-05-27)**

- 04-01: Feature-first routing modules (DisplayRoutes, HealthRoutes, MetricsRoutes) + rate-limit closure
- 04-02: DevOps artifacts moved to .devops/; DisplayApi DTOs → com.anjo.model; com.anjo.api removed
- 04-03: KHealth alignment + GET /health/detail extended payload (7 fields)
- 04-04: GET /metrics endpoint (runtime/api/hardware groups) with dedicated rate limiting (120/min)  
- 04-05: RecoveryPolicy readability refactor; ResourceTracker snapshot + MetricRegistry gauges; ScreenDriverService extracted executeWithRecovery()

**Verification:** `.planning/phases/04-observability-cleanup/04-VERIFICATION.md` — status: PASSED  
**Test Suite:** Full suite — BUILD SUCCESSFUL, JaCoCo PASS

---

## Phase 3 Status

✅ **All Waves Complete — Phase VERIFIED**

- 03-01: HealthService + `/health` + `/health/ready` endpoints
- 03-02: RecoveryPolicy + exponential backoff + ScreenDriverService integration
- 03-03: ResourceTracker bounded slots + ScreenDriverService resource lifecycle
- 03-04: RateLimitPlugin (fixed-window, 60 req/min, HTTP 429 + Retry-After)
- 03-05: systemd service file + production guide + monitoring/alerting doc

**Verification:** `.planning/phases/03-production-ready/03-VERIFICATION.md` — status: PASSED  
**Test Suite:** 40 Phase 3 tests + full suite — BUILD SUCCESSFUL  
**JaCoCo Gate:** >70% — PASS

---

## Phase 2 Status

✅ **Wave 1 Complete**
- 02-01: DisplayDriver hybrid interface (clear/write/status) + MAX7219 impl
- 02-02: I2C LCD driver (16x2 HD44780) implementation
- 02-03: Config selection + DisplaySelectionService + DI wiring

✅ **Wave 2 Complete**
- 02-04: Base layout + HTML pages (`/`, `/status`) + static JS integration
- 02-05: Settings page (`/settings/display`) + HTML error page rendering + toast notifications
- 02-06: Display APIs (`GET /api/display/status`, `POST /api/display/select`) + switch queue in `ScreenDriverService`

**Execution Summaries:**
- `.planning/phases/02-enhanced-display-support/EXECUTION-SUMMARY-WAVE1.md`
- `.planning/phases/02-enhanced-display-support/EXECUTION-SUMMARY-WAVE2.md`

**Wave 2 Delivered:**
- Responsive HTML pages rendered with Kotlinx HTML DSL and shared base layout
- Pico CSS v2 via CDN and browser-side app.js interactions
- Runtime display management endpoints for status and driver switching
- HTML error pages for browser routes while keeping JSON errors for `/api/*`
- Full test run passing (`./gradlew test`)

**Next: Wave 3**
- 02-07: OLED driver + integration tests + coverage verification

✅ **Wave 3 Complete**
- 02-07: OLED SSD1306 driver (`OledDisplay`) implemented
- DisplaySelectionService now supports MAX7219/LCD/OLED default factory switching
- Added `OledDisplayTest` and `DriverIntegrationTest`
- Full test suite and JaCoCo report pass

**Execution Summaries:**
- `.planning/phases/02-enhanced-display-support/EXECUTION-SUMMARY-WAVE1.md`
- `.planning/phases/02-enhanced-display-support/EXECUTION-SUMMARY-WAVE2.md`
- `.planning/phases/02-enhanced-display-support/EXECUTION-SUMMARY-WAVE3.md`

## Phase 1 Status

✅ Context, planning, execution, and verification artifacts exist in `.planning/phases/01-mvp/`.

✅ Manual refactor reconciliation is complete:
- package/layout under `com.anjo`
- DI/plugin ownership aligned (`di` + `routing` modules)
- endpoint contract aligned to `POST /api/text`
- verification complete with coverage gate command pass


## Technical Decisions Pending

| Issue | Options | Impact | Timing |
|-------|---------|--------|--------|
| Configuration externalization | YAML vs. env vars vs. both | Deployment flexibility | Phase 1 |
| Error recovery strategy | Automatic retry vs. manual vs. fallback | Reliability | Phase 1 |
| Test framework | Ktor testApplication (current) vs. Kotest | Code clarity, parallelization | Phase 1 |
| Display abstraction | Interface vs. sealed class vs. trait | Extensibility | Phase 2 |

## Assumptions & Constraints

### Assumptions
- ✅ Raspberry Pi 4 (4GB) available
- ✅ MAX7219 LED matrix wired and tested
- ✅ GPIO/I2C/SPI enabled on Raspberry Pi OS
- ✅ Java 21 runtime installed
- ✅ Home network is trusted (no external access initially)

### Constraints
- ⚠️ Limited to home network (no cloud sync)
- ⚠️ Single JVM instance (no scaling)
- ⚠️ 256MB heap limit (resource constrained)
- ⚠️ SPI is single-threaded (serialize hardware ops)

## Communication & Handoff

### To Next Session/Developer
When resuming this project:
1. Read `.planning/PROJECT.md` for vision
2. Review `.planning/REQUIREMENTS.md` for scope
3. Check `.planning/ROADMAP.md` for timeline
4. Read `.planning/codebase/ARCHITECTURE.md` for current design
5. Review `.planning/research/` for domain context
6. Check this STATE.md for latest status and decisions

### For Phase 1 Verification/Closeout
- Validation command used: `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification`
- Verification artifact: `.planning/phases/01-mvp/01-VERIFICATION.md`
- Execution summary: `.planning/phases/01-mvp/EXECUTION-SUMMARY.md`

## Blockers & Risks

| Blocker | Likelihood | Mitigation | Owner |
|---------|------------|-----------|-------|
| Hardware unavailable | Low | Pre-validated existing setup | Project |
| Unclear async patterns | Low | Research completed, patterns documented | Project |
| Test framework challenges | Medium | Use existing testApplication patterns | Phase 1 |
| GPIO conflicts | Low | Document pins, add validation | Phase 1 |

## Metrics to Track

- **Code Coverage:** Target 70%+ by Phase 1 end
- **Uptime:** Test 24h+ stability (no crashes)
- **Memory:** Profile and ensure <256MB heap under load
- **API Latency:** Target <500ms for text submission
- **Display Latency:** Target <2s from submission to LED

---

**Status:** Phase 5 Wave 1 COMPLETE (2026-05-28) — Wave 2 next.  
**Next Action:** Run `/gsd-execute-phase 5 --wave 2` to execute scheduling engine plans.

## Phase 5 Status

✅ **Wave 1 Complete (2026-05-28)**

### Wave 1 — Refactoring Completion ✅
- 05-01: Health endpoint consolidation + retry replacement (D5-01, D5-02, D5-03) ✅
- 05-02: Concurrency guard Mutex + metrics config-driving + comment removal (D5-04, D5-05, D5-06) ✅
- 05-03: Route package unification + test package unification (D5-07, D5-08) ✅

### Wave 2 — Scheduling Engine (4 plans, depends on Wave 1)
- 05-04: Schedule data model + Exposed/H2 database setup [REQ-SCHED-01]
- 05-05: SchedulerService coroutine engine + conflict policy [REQ-SCHED-01, REQ-CONFLICT-01]
- 05-06: Schedule HTTP CRUD API + validation [REQ-SCHED-01]
- 05-07: Schedule UI page + DI wiring + lifecycle verification [REQ-SCHED-01]

### Wave 3 — Effects + Behavioral Tests (3 plans, depends on Wave 2)
- 05-08: Effect renderer architecture + DisplayDriver extensions [REQ-EFFECT-01]
- 05-09: Wire effect field into POST /api/text + POST /api/schedule [REQ-EFFECT-01]
- 05-10: Timing-accurate behavior tests (virtual time, no Thread.sleep) [REQ-TEST-01]

**Key library selections:** in-house retryWithBackoff, cron-utils, Exposed DSL + H2, kotlinx-coroutines-test
