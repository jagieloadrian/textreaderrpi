# Phase 1: MVP - Context & Implementation Decisions

**Phase:** 1 (MVP - Core Text Display Functionality)  
**Date:** 2025-01-25  
**Updated:** 2026-05-26 with locked technical preferences
**Status:** Context gathered and decisions locked

---

## Domain

**Goal:** Reliable text submission and LED display rendering with >70% test coverage.

**What's in scope:** Refining the existing text→LED pipeline from proof-of-concept to production-quality. This means: validating input, handling errors gracefully, protecting resources, and testing the entire stack.

**What's NOT in scope:** Multiple display types (Phase 2), monitoring/health endpoints (Phase 3), advanced features (future phases).

---

## Technical Preferences (Locked)

These implementation choices were explicitly selected and are MANDATORY for Phase 1:

| Framework/Tool | Choice | Rationale |
|---|---|---|
| Request Validation | Ktor RequestValidation plugin | Native Ktor support, declarative validation rules |
| Error Handling | Ktor StatusPages plugin | Centralized error mapping, clean JSON responses |
| Configuration | YAML files with typed DataClasses | Type-safe config access, environment overrides supported |

---

## Decisions

### 1. Input Validation Strategy

**Framework:** Ktor RequestValidation plugin (https://ktor.io/docs/server-request-validation.html)

**Text Length & Character Set:**
- **Locked:** 128 characters maximum, UTF-8 support
- **Non-ASCII handling:** Accept UTF-8 input, but document that non-ASCII may not display correctly on MAX7219
- **Validation Rules:** 
  - Implement custom validators using Ktor's RequestValidation plugin
  - Reject blank/empty text
  - Enforce max length of 128 chars
  - Ensure UTF-8 compatibility
- **DoS Protection:** Queue size limit (max 10) + Rate limiting (60 req/min per IP)

### 2. Error Handling & Logging

**Framework:** Ktor StatusPages plugin for centralized error handling

**Error Handling Architecture:**
- Install StatusPages plugin in routing configuration
- Map validation exceptions to HTTP 400
- Map internal service failures to HTTP 500
- Return structured JSON error responses

**Error Response Format:**
```json
{"error": {"code": "VAL_001", "message": "...", "timestamp": "..."}}
```

**Error Logging:** Structured logging to console/file with context

**SPI Error Recovery:** Automatic retry up to 3 times with exponential backoff (100ms, 200ms, 400ms)

**Shutdown & Cleanup:** Clear display on application shutdown

### 3. Configuration Externalization

**Source:** YAML files (application.yaml) with environment variable overrides

**Strategy:**
- Each configuration domain gets a dedicated data class (ConfigObject)
- Load YAML early in Application.module()
- Create typed ConfigObjects for:
  - **DisplayConfig** — MAX7219 parameters (GPIO pins, SPI path, num devices, brightness)
  - **HardwareConfig** — hardware-specific timeouts (SPI timeout, GPIO timeout)
  - **ApiConfig** — API limits (max text length, queue size, rate limit)
  - **TimingConfig** — rendering timings (scroll speed, refresh rate)
  - **LoggingConfig** — log levels and outputs
  
**YAML Structure:**
```yaml
application:
  name: TextReaderRpi
display:
  gpio_pins:
    spi_ce: 8
    spi_mosi: 10
    spi_miso: 9
    spi_sck: 11
  numDevices: 2
  brightness: true
hardware:
  spi_timeout_ms: 1000
  gpio_timeout_ms: 500
api:
  maxTextLength: 128
  queueSize: 10
  rateLimitPerMinute: 60
timing:
  scrollSpeed: 16
  refreshRate: 60
logging:
  level: INFO
  format: json
```

**Defaults:** Sensible defaults in code, YAML/env and property-based overrides when available

**ConfigLoader:** Single responsibility class that reads YAML and creates all ConfigObject instances

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
- `src/main/kotlin/routing/Routing.kt` — add RequestValidation plugin, install StatusPages error handler
- `src/main/kotlin/Application.kt` — initialize ConfigLoader and load configuration early
- `src/main/kotlin/service/ReaderInput.kt` — use validated config objects
- `src/main/kotlin/service/ScreenDriver.kt` — use hardware config for timeouts
- `src/main/kotlin/driver/Max7219Matrix.kt` — add timeout/retry guards
- `src/main/resources/application.yaml` — define configuration schema

**New Files to Create:**
- `src/main/kotlin/config/ConfigLoader.kt` — load YAML and create ConfigObjects
- `src/main/kotlin/config/DisplayConfig.kt` — data class for display settings
- `src/main/kotlin/config/HardwareConfig.kt` — data class for hardware timeouts
- `src/main/kotlin/config/ApiConfig.kt` — data class for API limits (text length, queue, rate limit)
- `src/main/kotlin/config/TimingConfig.kt` — data class for rendering/scroll timings
- `src/main/kotlin/config/LoggingConfig.kt` — data class for logging configuration
- `src/main/kotlin/config/ApplicationConfig.kt` — root config holder combining all above
- `src/main/kotlin/model/TextRequest.kt` — request DTO for POST /api/text
- `src/main/kotlin/model/TextResponse.kt` — success response DTO
- `src/main/kotlin/model/ErrorResponse.kt` — error response DTO
- `src/main/kotlin/validation/RequestValidators.kt` — validators for RequestValidation plugin
- `src/test/kotlin/routing/TextApiRouteTest.kt` — API endpoint tests
- `src/test/kotlin/service/ScreenDriverTest.kt` — service tests
- `src/test/kotlin/service/ReaderInputServiceTest.kt` — service tests

---

*Context captured on 2025-01-25. Ready for Phase 1 planning.*
