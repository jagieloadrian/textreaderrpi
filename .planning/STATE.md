# Project State & Memory

**Last Updated:** 2026-05-26  
**Status:** Phase 2 planning complete - ready for Wave 1 execution

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
- ✅ `.planning/ROADMAP.md` (3-phase development plan)
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

## Phase 2 Status

✅ **Full Planning Complete** — 7 executable phase plans with locked decisions:
- `.planning/phases/02-enhanced-display-support/02-CONTEXT.md` — 16 locked decisions (D-01..D-16)
- `.planning/phases/02-enhanced-display-support/02-DISCUSSION-LOG.md` — Audit trail of requirements discussion
- `.planning/phases/02-enhanced-display-support/02-UI-SPEC.md` — Design contract (verified, all 6 dimensions passing)
- `.planning/phases/02-enhanced-display-support/02-01-PLAN.md` → 02-07-PLAN.md — 7 executable plans

**Wave Structure:**
- **Wave 1 (Backend Foundation):** 02-01 (DisplayDriver refactor + MAX7219), 02-02 (LCD driver), 02-03 (config selection)
- **Wave 2 (Frontend UI):** 02-04 (BaseLayout + main pages), 02-05 (settings + error pages + JS), 02-06 (API endpoints)
- **Wave 3 (Polish):** 02-07 (OLED driver + comprehensive tests)

**Key Decisions Locked:**
- D-01: DisplayDriver hybrid interface (clear/write/status + scrollText for backwards compat)
- D-02: Scroll-only on MAX7219, write-based on LCD/OLED
- D-03: I2C LCD (16x2), I2C OLED (SSD1306)
- D-04: Ktor HTML DSL templates with BaseLayout pattern + Pico CSS v2
- D-05: GET /, /status, /settings/display HTML pages
- D-06: Error pages (400/500) + async form submit + character counter
- ... (16 decisions total, all documented in CONTEXT.md)

**Scope Expansion (from discussion):**
- Original: Display driver + configuration
- Expanded: Full responsive HTML UI using Ktor HTML DSL templates + Pico CSS v2

### Next
- Execute Wave 1: `/gsd-execute-phase 02 --wave 1` (backend foundation)
- Monitor coverage: `./gradlew jacocoTestReport` after Wave 1
- Execute Wave 2: `/gsd-execute-phase 02 --wave 2` (frontend UI)
- Execute Wave 3: `/gsd-execute-phase 02 --wave 3` (OLED + testing)
- Target: >75% coverage, responsive HTML verified on mobile + desktop

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

**Status:** Phase 2 planning complete (7 plans, all locked decisions in place, UI-SPEC approved).  
**Next Action:** Execute Phase 2 via `/gsd-execute-phase 02 --wave 1` to begin backend foundation (drivers + config).

