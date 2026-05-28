# TextReaderRpi - Development Roadmap

**Version:** 1.0  
**Created:** 2025-01-25  
**Phases:** 5 (MVP → Enhanced → Production Ready → Cleanup+Observability → Scheduling+Effects)

---

## Overview

```
Phase 1: MVP (2-3 weeks)
├─ Refine text submission API
├─ Optimize MAX7219 rendering
├─ Add basic error handling
└─ Initial test suite

Phase 2: Enhanced Display Support (4-6 weeks)
├─ I2C LCD driver implementation
├─ OLED driver implementation
├─ Configuration-driven display selection
├─ Multi-display testing
└─ Comprehensive test coverage

Phase 3: Production Ready (8-10 weeks)
├─ Health/status endpoints
├─ Error recovery mechanisms
├─ Performance optimization
├─ Deployment automation
├─ Documentation
└─ Production deployment

Phase 4: Cleanup + Observability (2-4 weeks)
├─ DevOps artifact/layout cleanup
├─ Health stack alignment (KHealth + extended payload)
├─ Routing and rate-limit enforcement cleanup
├─ Recovery/resource monitoring refactor
└─ JSON metrics endpoint

Phase 5: Scheduling + Effects (3-5 weeks)
├─ Scheduled text updates
├─ Effect pipeline (scroll/fade/blink)
├─ Queue and conflict policy for scheduled jobs
└─ Behavioral tests for timing and rendering
```

---

## Phase 1: MVP - Core Text Display Functionality

**Duration:** 2-3 weeks  
**Goal:** Reliable text submission and LED display rendering

### Phase 1 Requirements

| Requirement | Status | Priority |
|-------------|--------|----------|
| HTTP POST /api/text endpoint | Existing | MUST |
| Request validation (length, charset) | Partial | MUST |
| MAX7219 rendering (text scrolling) | Existing | MUST |
| Basic error responses | Partial | MUST |
| Service unit tests | Missing | MUST |
| Display driver unit tests | Missing | SHOULD |

### Phase 1 Deliverables

1. **Refined API Specification**
   - Document POST /api/text request/response schema
   - Define error codes (400, 422, 500)
   - Response examples

2. **Enhanced MAX7219 Driver**
   - Clean up hardware initialization
   - Add timeout protection for SPI operations
   - Improve rendering pipeline efficiency

3. **Request Validation**
   - Validate text length (max 256 chars)
   - Sanitize input (ASCII only)
   - Return 400 Bad Request with error details

4. **Error Handling**
   - Log hardware errors with context
   - Return appropriate HTTP status codes
   - Graceful recovery from SPI failures

5. **Test Suite**
   - HTTP endpoint tests (testApplication)
   - Service layer tests (ReaderInputService, ScreenDriverService)
   - Mock hardware tests (Pi4J mocking)
   - Target: >70% code coverage

6. **Documentation**
   - API documentation (endpoint, request/response)
   - Hardware setup guide for MAX7219
   - Deployment instructions

### Phase 1 Success Criteria

- [ ] User can submit text via /api/text and see it on LED display within 2s
- [ ] Invalid input rejected with clear error message (HTTP 400)
- [ ] Hardware errors logged and recovered gracefully
- [ ] Server runs for 24 hours without memory leaks
- [ ] Test coverage >70%, all critical paths tested
- [ ] README documents API and setup

---

## Phase 2: Enhanced Display Support + Responsive HTML UI

**Duration:** 4-6 weeks  
**Goal:** Support multiple display types (LCD, OLED) via abstraction layer and deliver responsive HTML pages as the primary interaction surface

### Phase 2 Requirements

| Requirement | Status | Priority |
|-------------|--------|----------|
| DisplayDriver interface/abstraction | Missing | MUST |
| I2C 16x2 LCD driver | Missing | MUST |
| OLED SSD1306 driver | Missing | SHOULD |
| Configuration for display selection | Missing | MUST |
| Multi-display testing | Missing | MUST |
| Comprehensive test suite | Partial | MUST |

### Phase 2 Deliverables

1. **Hardware Abstraction Layer**
   - `DisplayDriver` interface: `clear()`, `write(text)`, `status()`
   - MAX7219 driver refactored to implement interface
   - Support for pluggable display implementations

2. **I2C 16x2 LCD Driver**
   - I2C communication via Pi4J
   - Character rendering (HD44780 protocol)
   - Text wrapping for multi-line display
   - Unit tests with I2C mocking

3. **OLED Driver** (if time permits)
   - SSD1306 SPI/I2C driver
   - Bitmap rendering
   - Burn-in prevention (power saving modes)
   - Unit tests

4. **Configuration System**
   - application.yaml: `display.type = "MAX7219" | "LCD" | "OLED"`
   - Display-specific parameters (address, pins, refresh rate)
   - Runtime display type detection

5. **API Enhancement**
   - `/api/display/status` - shows current display type
   - `/api/display/type/{type}` - switch display type (optional)
   - All existing endpoints work with any display type

6. **Responsive HTML Pages (Ktor HTML DSL)**
   - Use Ktor HTML DSL templates (`Template<HTML>`) with a hybrid structure: shared base layout + route-specific page blocks
   - `GET /` - responsive main page with text submission form and operation feedback
   - `GET /status` - responsive status page showing active display type, health/status info, and latest operation outcome
   - `GET /settings/display` - responsive settings page for runtime display switching
   - Browser-oriented HTML error pages for 400/500

7. **UX/Rendering Rules for HTML Flow**
   - HTML pages become the primary business interaction flow
   - Responsive behavior validated for mobile-first layouts
   - Keep display-side readability and latency constraints aligned with phase rules

8. **Test Coverage**
   - Driver tests for each display type
   - Configuration loading tests
   - Integration tests with multiple displays
   - Route/template tests for HTML pages and error page rendering
   - Target: >75% coverage

### Phase 2 Success Criteria

- [ ] I2C LCD displays text correctly (validated on hardware)
- [ ] OLED displays text with proper rendering
- [ ] Configuration file switches display type without code changes
- [ ] All endpoints work with all display types
- [ ] Switching displays doesn't require server restart
- [ ] New driver implementations follow same patterns
- [ ] Responsive HTML pages (`/`, `/status`, `/settings/display`) are functional and mobile-friendly
- [ ] HTML templates follow base-layout + route-specific hybrid pattern
- [ ] Browser requests receive HTML error pages for 400/500
- [ ] Test coverage >75%

---

## Phase 3: Production Ready

**Duration:** 8-10 weeks  
**Goal:** Reliable, maintainable, monitored deployment

### Phase 3 Requirements

| Requirement | Status | Priority |
|-------------|--------|----------|
| Health check endpoints | Missing | MUST |
| Error recovery mechanisms | Partial | MUST |
| Memory/resource optimization | Partial | SHOULD |
| Rate limiting | Missing | SHOULD |
| Deployment guide | Missing | MUST |
| systemd service file | Missing | SHOULD |
| Monitoring/alerting | Missing | COULD |

### Phase 3 Deliverables

1. **Health & Status Endpoints**
   - `GET /health` - basic liveness check
   - `GET /health/ready` - readiness probe (hardware initialized)
   - Response includes: uptime, memory usage, hardware status
   - Used by monitoring/container orchestration

2. **Error Recovery**
   - Automatic GPIO cleanup on shutdown
   - SPI reconnection logic on failure
   - Timeout protection for all I/O operations
   - Logging of recovery attempts

3. **Performance Optimization**
   - Display buffer caching (avoid re-rendering unchanged text)
   - Memory usage profiling and optimization
   - GC pause tuning for RPi
   - Benchmark results documented

4. **Rate Limiting & Protection**
   - Token bucket rate limiting (60 req/min per IP)
   - Concurrency limits (max 3 concurrent hardware ops)
   - Request queue with backpressure
   - Graceful degradation under load

5. **Deployment Automation**
   - systemd service file for auto-start
   - Startup script with health checks
   - Graceful shutdown (cancel pending ops)
   - Log rotation configuration

6. **Comprehensive Documentation**
   - Architecture overview (system diagram)
   - API reference (all endpoints)
   - Hardware setup guide (wiring, power)
   - Deployment guide (systemd, monitoring)
   - Troubleshooting guide
   - Development guide (how to add new drivers)

7. **Advanced Monitoring** (optional)
   - Structured logging (JSON)
   - Metrics export (Prometheus format)
   - Error alerting via syslog
   - Dashboard/visualization (Grafana)

### Phase 3 Success Criteria

- [x] System runs 24h+ without intervention
- [x] Hardware errors automatically recovered
- [x] `/health` endpoint monitored by external service
- [x] Rate limiting prevents abuse
- [x] JVM memory usage <256MB under load
- [x] Graceful shutdown in <10s
- [x] All documentation complete and accurate
- [x] Deployment process documented and tested

---

## Phase 4: Cleanup + Observability

**Duration:** 2-4 weeks  
**Goal:** Consolidate architecture/runtime hygiene and expose actionable runtime metrics.

### Phase 4 Requirements

| Requirement | Status | Priority |
|-------------|--------|----------|
| DevOps artifact reorganization (`.devops`) | Planned | MUST |
| KHealth restoration with extended fields | Planned | MUST |
| Routing consolidation + policy consistency | Planned | MUST |
| Text endpoint under rate limiting | Planned | MUST |
| RecoveryPolicy readability refactor | Planned | SHOULD |
| ResourceTracker -> monitoring refactor | Planned | SHOULD |
| `GET /metrics` JSON endpoint (runtime+api+hardware) | Planned | MUST |

### Phase 4 Deliverables

1. **Cleanup and Structure Alignment**
   - Move deployment/Docker assets to `.devops/`
   - Normalize routing module organization
   - Move `displayApi` models to `model` package
   - Relocate `OfflineDisplayDriver` to the proper driver module location

2. **Health and Monitoring Alignment**
   - Keep `KHealth` installed as health core
   - Extend responses with: `uptime`, `memoryUsedMb`, `memoryMaxMb`, `displayType`, `isActive`, `lastError`
   - Preserve `/health` + `/health/ready` semantics

3. **Rate-Limit and Recovery Cleanup**
   - Ensure text routes are covered by rate limiting policy
   - Refactor `RecoveryPolicy` for readability and maintainability
   - Convert `ResourceTracker` into monitoring-oriented component/module

4. **Metrics Endpoint (Phase 4 contract)**
   - Add public `GET /metrics` endpoint returning JSON
   - Include all metric groups:
     - runtime (uptime/JVM memory)
     - API (request counters, 429 counters)
     - hardware (resource slots, retries, display failures)
   - Endpoint is rate-limited consistently with API policy

### Phase 4 Success Criteria

- [x] Routes and module boundaries are consistent and discoverable
- [x] `KHealth` is active and exposes extended health payload fields
- [x] Text submission route is protected by rate limiting
- [x] `/metrics` returns runtime + API + hardware metric groups in JSON
- [x] Existing tests pass and targeted observability tests are added

**Phase 4 Complete — 2026-05-27** ✅

---

## Phase 5: Scheduling + Effects

**Duration:** 3-5 weeks  
**Goal:** Add timed content behavior and richer display effects without destabilizing runtime reliability.

### Phase 5 Requirements

| Requirement | Status | Priority |
|-------------|--------|----------|
| Scheduled text updates | ✅ Complete | MUST |
| Effect rendering options (fade/blink/reverse/scroll variants) | ✅ Complete | SHOULD |
| Queueing/conflict policy for schedules | ✅ Complete | MUST |
| Timing-accurate behavior tests | ✅ Complete | MUST |
| Cancel endpoint for running schedules | ✅ Complete | MUST |
| Environment variable support (all config) | ✅ Complete | MUST |
| Gradle-based Docker image build | ✅ Complete | SHOULD |
| Dependency updates (Kotlin 2.3.21, Ktor 3.5.0, Exposed 1.3.0) | ✅ Complete | MUST |

**Phase 5 Complete — 2026-05-28** ✅  
**Plans:** 11 (05-01 → 05-11), all SUMMARY.md present  
**Wave 4 (post-execution):** SchedulerService bug fix, cancel endpoint, UI Stop button, test restructuring, deps update, env vars, Gradle Docker

---

## Phase 4+: Future Enhancements (Out of Scope)

Potential features for future phases (not part of initial roadmap):

- **Multi-zone:** Different text on different displays
- **Remote Access:** Secure web access from outside home network
- **Voice Control:** Integration with voice assistants
- **Templating:** Pre-defined message templates
- **Integration:** Sync with weather API, news feeds, calendar

---

## Phase Timeline & Milestones

| Phase | Start | Duration | Milestones |
|-------|-------|----------|-----------|
| **Phase 1** | Week 1 | 2-3w | MVP demo (text on LED) |
| **Phase 2** | Week 4 | 4-6w | Multi-display support |
| **Phase 3** | Week 10 | 8-10w | Production deployment |

---

## Resource Allocation

| Resource | Allocation | Notes |
|----------|-----------|-------|
| **Development** | Solo developer | Iterative phases with validation |
| **Testing** | 20% of phase time | Unit + integration tests |
| **Documentation** | 15% of phase time | API docs, deployment guide |
| **Hardware** | Raspberry Pi 4 + displays | MAX7219, optional: LCD, OLED |

---

## Risk Management

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| GPIO conflicts | Medium | High | Document pins, add sanity checks |
| Memory exhaustion | Medium | High | Heap limits, resource throttling |
| I2C bus instability | Low | Medium | Retry logic, error handling |
| SPI timing issues | Low | Medium | Timeout protection, quality testing |
| OLED burn-in | Low | Low | Power-saving modes, documentation |

---

## Success Metrics

### Phase 1 Success
- ✅ MVP works (text → LED display)
- ✅ >70% test coverage
- ✅ Zero crashes in 24h operation test
- ✅ API documented

### Phase 2 Success
- ✅ Multiple display types working
- ✅ Configuration-driven switching
- ✅ >75% test coverage
- ✅ All drivers follow same patterns

### Phase 3 Success
- ✅ 99.5% uptime achieved
- ✅ Zero manual intervention for 1 week
- ✅ All endpoints documented
- ✅ Deployment automated
- ✅ Ready for production deployment

---

## Next Steps

1. ✅ Requirements and roadmap complete
2. → Review and approve roadmap
3. → `/gsd-plan-phase 1` to create detailed phase 1 plan
4. → Execute phase 1 tasks and commit
5. → Validate phase 1 completion
6. → Move to phase 2

---

*Roadmap approved and ready for phase 1 planning.*

