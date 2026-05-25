# Phase 1 MVP: Reliable Text Submission and LED Display Rendering

**Phase:** 01-mvp  
**Plan:** 01  
**Status:** Ready for Execution  
**Type:** Feature Implementation  
**Wave:** 1

---

## Executive Summary

Build a production-ready text-to-LED system on Raspberry Pi with validated input, queued rendering, structured logging, and ≥70% test coverage.

**Objective:** Establish reliable foundation with timeout protection, graceful error recovery, and comprehensive telemetry.

**Output:** Working HTTP API + LED display system, externalized configuration, MockMax7219 test infrastructure, documentation.

**Effort Estimate:** 8–12 working hours (solo developer)

---

## Locked Constraints from CONTEXT.md

All following decisions are FIXED and must be implemented exactly:

| ID | Decision | Requirement |
|----|----------|-------------|
| **D-01** | Input Validation | 128 chars max, UTF-8, HTTP 400 on violation, queue limit + rate limiting |
| **D-02** | Error Handling | Structured logs (JSON), /health endpoint, SPI auto-retry (3x exponential backoff) |
| **D-03** | Configuration | YAML file + env var overrides for GPIO, SPI path, display size, timings, API limits |
| **D-04** | Testing | 70% coverage, MockMax7219 for unit tests, integration tests for API/queue/display |
| **D-05** | Timeout & Resources | All ops timeout (no hang), queue size limit, per-frame timeout, graceful degradation |
| **D-06** | Dependencies | Ktor 3.5.0, Kotlin 2.3.21, latest Jackson, junit5, mockito-kotlin |

---

## Task Breakdown (10 Tasks, 5 Waves)

### **Wave 1 (Parallel): Foundation Setup**

#### Task 1: Upgrade Dependencies and Project Scaffolding
**Files:** `build.gradle.kts`, `src/main/kotlin/Main.kt`, `src/main/resources/logback.xml`

**What:** Update build system and logging configuration
- Kotlin 2.3.21, Ktor 3.5.0, Jackson 2.16+, junit5, mockito-kotlin
- Logback with JSON appender for structured logging (D-02)

**Success:** `./gradlew clean build` passes, versions correct

---

#### Task 2: Configuration Infrastructure (YAML Loader + Config Classes)
**Files:** `src/main/kotlin/config/Config.kt`, `ConfigLoader.kt`, `src/main/resources/config/application.yaml`, `*ConfigLoaderTest.kt`

**What:** Externalize all hardcoded values (D-03)
- Config data classes: DisplayConfig, TimingConfig, QueueConfig, ApiLimitConfig, LoggingConfig
- ConfigLoader: load YAML + override from env vars
- application.yaml with GPIO pins, SPI path, display size, timeouts, API limits
- Unit tests: valid YAML load, missing fields, env override

**Success:** `./gradlew test --tests "*ConfigLoaderTest*"` passes, coverage ≥85%

---

### **Wave 2 (Parallel): Core Components**

#### Task 3: Input Validation and Structured Logging
**Files:** `validation/InputValidator.kt`, `error/StructuredLogger.kt`, `error/ErrorHandler.kt`, `*InputValidatorTest.kt`

**What:** Validate incoming text and establish logging foundation (D-01, D-02)
- InputValidator: 128-char limit, UTF-8 check, non-empty validation
- StructuredLogger: JSON logging for submissions, queue events, errors, display events
- ErrorHandler: structured error responses for HTTP, queue, display
- Unit tests: valid/invalid input, UTF-8 boundaries, special characters

**Success:** `./gradlew test --tests "*InputValidatorTest*"` passes, coverage ≥90%

---

#### Task 4: Message Queue with Rate Limiting and Size Limits
**Files:** `queue/MessageQueue.kt`, `*MessageQueueTest.kt`

**What:** Thread-safe queue with backpressure (D-01, D-05)
- FIFO queue with enqueue/dequeue
- Rate limiting: token bucket or sliding window (60 req/min per D-01)
- Size limit: reject if queue full or memory exceeded
- Concurrent safety: synchronized or Mutex
- Unit tests: FIFO order, full queue rejection, rate limit trigger, concurrent stress (≥10 threads)

**Success:** `./gradlew test --tests "*MessageQueueTest*"` passes, coverage ≥85%, concurrent stress test passes

---

#### Task 6: Mock Max7219 and SPI Driver with Retry Logic
**Files:** `test/MockMax7219.kt`, `driver/Max7219Driver.kt`, `*Max7219DriverTest.kt`

**What:** Low-level hardware abstraction with test double (D-02, D-05)
- MockMax7219: test fixture recording all sent frames, failure injection
- Max7219Driver: write to SPI, auto-retry with exponential backoff (3x), timeout logging
- Integration tests: successful write, failure retry, all retries fail, exponential backoff timing

**Success:** `./gradlew test --tests "*Max7219DriverTest*"` passes, coverage ≥80%

---

### **Wave 3 (Parallel): API Layer**

#### Task 5: HTTP API Handlers (Text Submission + Health)
**Files:** `api/TextSubmissionHandler.kt`, `api/HealthEndpoint.kt`, `*TextSubmissionHandlerTest.kt`

**What:** User-facing REST endpoints (D-01, D-02, D-05)
- POST /api/submit: validate, enqueue, return 202 or error (400/429)
- GET /health: return status with queue depth, uptime, memory usage
- Integration tests: valid submission, validation errors, queue full (429), rate limit (429), concurrent stress

**Success:** `./gradlew test --tests "*TextSubmissionHandlerTest*"` passes, coverage ≥85%

---

#### Task 7: Display Renderer with Timeout and Graceful Degradation
**Files:** `display/DisplayRenderer.kt`, `*DisplayRendererTest.kt`

**What:** Async rendering loop with timeout protection (D-05)
- render(): convert text to bitmap, send frames via driver, timeout on exceed
- renderLoop(): dequeue messages, render in sequence, skip on timeout/failure
- Unit tests: valid render, timeout abort, loop dequeue order, graceful failure

**Success:** `./gradlew test --tests "*DisplayRendererTest*"` passes, coverage ≥80%

---

### **Wave 4: Application Bootstrap**

#### Task 8: Main Application Entry Point and Server Startup
**Files:** `Main.kt`, `application.conf`

**What:** Tie all components together (D-03, D-04, D-05)
- Load config via ConfigLoader
- Initialize: queue, Max7219Driver, DisplayRenderer, Ktor HTTP server
- Start renderLoop in background coroutine
- Graceful shutdown: SIGTERM → close display → exit
- Startup/shutdown logs

**Success:** `./gradlew run` starts, logs startup message, routes available, shutdown graceful

---

### **Wave 5 (Final): Validation and Documentation**

#### Task 9: End-to-End Integration Test and Coverage Verification
**Files:** `E2ETest.kt`, `CoverageReportTest.kt`

**What:** Full system test + coverage gate (D-04)
- E2E test: submit text → queue → render → health check
- Coverage report via JaCoCo
- Gate: fail if coverage < 70%

**Success:** `./gradlew test jacocoTestReport` → all E2E tests pass, coverage ≥70%

---

#### Task 10: Documentation and Deployment Checklist
**Files:** `README.md`, `docs/API.md`, `docs/CONFIG.md`, `docs/DEPLOYMENT.md`, `docs/TESTING.md`

**What:** User-facing documentation (D-01, D-03, D-05)
- README: overview, quick start, features
- API.md: endpoints, request/response examples, error codes, curl examples
- CONFIG.md: YAML structure, env vars, defaults, ranges
- DEPLOYMENT.md: hardware setup, GPIO wiring, SPI permissions, systemd service
- TESTING.md: test commands, coverage check, mock configuration

**Success:** All docs exist and are complete

---

## Execution Sequence and Dependencies

```
Wave 1 (Parallel):
  Task 1: Upgrade dependencies
    └─ Task 2: Config loader

Wave 2 (Parallel, depends on Wave 1):
  Task 3: Validation + logging
  Task 4: Message queue
  Task 6: Mock driver + SPI

Wave 3 (Parallel, depends on Wave 2):
  Task 5: API handlers
  Task 7: Display renderer

Wave 4 (Sequential):
  Task 8: Main application (depends on all above)

Wave 5 (Final):
  Task 9: E2E + coverage
  Task 10: Documentation
```

**Critical Path:** Task 1 → 2 → {3, 4, 6} → {5, 7} → 8 → {9, 10}

**Parallelization:** Tasks 3, 4, 6 can run simultaneously. Tasks 5, 7 can run simultaneously. Tasks 9, 10 can run simultaneously.

---

## Risk Management

| Risk | Impact | Mitigation |
|------|--------|-----------|
| SPI device unavailable | Task 6 fails | Use MockMax7219; mock SPI write to /tmp file |
| Timeout edge cases | Render hangs | Write timeout tests with mock clock; assertion-based timing |
| Race conditions (queue) | Lost messages | Synchronize queue; stress test with ≥10 concurrent clients |
| Coverage < 70% | Test gate fails | Identify gaps during Task 9; add missing tests iteratively |
| Ktor 3.5.0 breaking changes | Build fails | Task 1 validates syntax early; consult migration guide |
| UTF-8 validation bug | Invalid chars bypass | Use standard library validator; Task 3 tests all edge cases |

---

## Verification Checklist

**Phase Goal Verification:**
- [ ] HTTP POST /api/submit validates 128-char, UTF-8, rejects invalid with HTTP 400
- [ ] Valid text queued, rendered on display within 500ms
- [ ] Rate limiting: excess requests return HTTP 429
- [ ] Display timeout: render aborts gracefully, next message proceeds
- [ ] Health endpoint: GET /health returns queue depth, uptime, system status
- [ ] Structured logging: all events logged in JSON (submission, queue, error, display)
- [ ] SPI retry: auto-retry up to 3x with exponential backoff, logs each attempt
- [ ] Configuration externalized: YAML + env vars for GPIO, SPI, display size, timeouts, API limits
- [ ] Test coverage ≥70%: verified via JaCoCo
- [ ] No hanging operations: all have timeout, graceful degradation
- [ ] Queue memory limits: rejects enqueue on memory exceeded, logs warning
- [ ] Documentation complete: README, API, CONFIG, DEPLOYMENT, TESTING guides

---

## Success Criteria for Phase 1 Completion

Phase 1 is **COMPLETE** when:

1. **All tests pass:**
   - `./gradlew clean test` → all suites pass
   - `./gradlew jacocoTestReport` → coverage ≥70%
   - `./gradlew build` → no errors

2. **All locked decisions implemented (D-01 through D-06):**
   - Input validation: 128 chars, UTF-8, queue/rate limits ✓
   - Error handling: structured logs, health endpoint, retry ✓
   - Configuration: YAML + env vars ✓
   - Testing: 70% coverage, MockMax7219 ✓
   - Timeout & resources: all ops timeout, queue limits, graceful degrade ✓
   - Dependencies: Ktor 3.5.0, Kotlin 2.3.21 ✓

3. **API operational:**
   - `curl -X POST http://localhost:8080/api/submit -d '{"text":"Hello"}' -H 'Content-Type: application/json'` → HTTP 202
   - `curl http://localhost:8080/health` → HTTP 200 with queue depth, uptime

4. **Display renders text:**
   - Message queued and rendered on Max7219 within timeout
   - Timeout aborts gracefully; next message processes
   - SPI failure retries up to 3x; final failure logged with context

5. **Code committed:**
   - All changes committed with messages: `feat: ...`, `test: ...`, `docs: ...`
   - SUMMARY.md generated

---

## Next Steps After Phase 1

Upon completion, proceed to:
- **Phase 2:** Enhanced display support (LCD, OLED) via abstraction layer
- **Phase 3:** Health monitoring, metrics, graceful shutdown optimization
- **Phase 4+:** Advanced features (scheduling, multi-zone, integrations)

---

*Plan created: 2025-01-25*  
*Ready for execution via `/gsd-execute-phase 01-mvp`*

