# Phase 1: MVP - Context & Implementation Decisions

**Phase:** 1 (MVP - Core Text Display Functionality)  
**Date:** 2025-01-25  
**Status:** Context gathered and decisions locked

---

## Domain

**Goal:** Reliable text submission and LED display rendering with >70% test coverage.

**What's in scope:** Refining the existing text→LED pipeline from proof-of-concept to production-quality. This means: validating input, handling errors gracefully, protecting resources, and testing the entire stack.

**What's NOT in scope:** Multiple display types (Phase 2), monitoring/health endpoints (Phase 3), advanced features (future phases).

---

## Decisions

### 1. Input Validation Strategy

**Text Length & Character Set:**
- **Locked:** 128 characters maximum, UTF-8 support
- **Non-ASCII handling:** Accept UTF-8 input, but document that non-ASCII may not display correctly on MAX7219
- **Validation Errors:** Return HTTP 400 Bad Request with structured JSON
- **DoS Protection:** Queue size limit (max 10) + Rate limiting (60 req/min per IP)

### 2. Error Handling & Logging

**Error Logging:** Structured logging to console/file + `/health` endpoint with error history

**SPI Error Recovery:** Automatic retry up to 3 times with exponential backoff (100ms, 200ms, 400ms)

**HTTP Error Responses:** Structured format with error codes
```json
{"error": {"code": "VAL_001", "message": "...", "timestamp": "..."}}
```

**Shutdown & Cleanup:** Clear display on application shutdown

### 3. Configuration Externalization

**Location:** YAML (application.yaml) with environment variable overrides

**Configurable Values:** Full scope - hardware (GPIO, SPI), timings (scroll, refresh), and API limits

**Organization:** Single YAML file under logical sections (display, gpio, timing, api)

**Defaults:** Sensible defaults in code, YAML/env overrides

### 4. Testing Strategy

**Scope:** Comprehensive testing (unit + integration + E2E)
- Unit tests: Services, driver, utilities
- Integration tests: Service→Driver pipeline
- E2E tests: HTTP endpoints with mocked hardware
- Error paths: Invalid input, SPI failures, timeouts, queue overflow

**Coverage Target:** 70% overall code coverage

**Hardware Mocking:** Create MockMax7219 class implementing same interface

### 5. Timeout & Resource Protection

**SPI Timeout Protection:** All blocking operations have timeouts (1000ms SPI, 500ms GPIO)

**Memory Protection:** Queue size limit (10) + JVM 256MB + memory monitor (alert at 80%)

**Rendering Loop:** Per-frame timeout (100ms) + try-catch exception handling

**Startup Safety:** Validate GPIO/SPI on startup; start in degraded mode if init fails

### 6. Dependency Management

**Locked Decision:** Use latest versions from Maven Central
- Check for updates: Ktor (3.5.0), Kotlin (2.3.21), Pi4J, Gradle, test libraries
- Upgrade at Phase 1 start
- Run full test suite after update

---

## Canonical References

- `.planning/PROJECT.md` — Project vision
- `.planning/REQUIREMENTS.md` — Feature scope
- `.planning/ROADMAP.md` — Phase breakdown
- `.planning/codebase/ARCHITECTURE.md` — System design
- `.planning/codebase/CONCERNS.md` — Quality gaps
- `.planning/codebase/TESTING.md` — Test patterns
- `.planning/codebase/CONVENTIONS.md` — Code style

---

## Code Context

**Key Files to Modify:**
- `src/main/kotlin/Application.kt` — Add startup validation
- `src/main/kotlin/routing/Routing.kt` — Add validation, error handling
- `src/main/kotlin/service/ReaderInput.kt` — Add queue limits
- `src/main/kotlin/service/ScreenDriver.kt` — Add timeouts, error handling
- `src/main/kotlin/driver/Max7219Matrix.kt` — Add timeouts, error logging
- `src/main/resources/application.yaml` — Externalize configuration

**New Files to Create:**
- `src/main/kotlin/config/DisplayConfig.kt` — Typed config loading
- `src/main/kotlin/validation/TextValidator.kt` — Reusable validation
- `src/main/kotlin/response/ErrorResponse.kt` — Standardized error format
- `src/test/kotlin/driver/MockMax7219.kt` — Hardware mock for tests
- `src/test/kotlin/service/*Test.kt` — Service unit tests
- `src/test/kotlin/*IntegrationTest.kt` — Integration tests

---

*Context captured on 2025-01-25. Ready for Phase 1 planning.*
