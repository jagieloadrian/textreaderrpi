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
| Test Coverage Gate | JaCoCo with build fail threshold | Enforce measurable quality bar before phase close |

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

### 7. DI and Routing Architecture Standards

**Ktor DI standard (locked):**
- DI wiring remains centralized in `src/main/kotlin/di/DependencyInjection.kt`
- Routing files must not build infrastructure objects directly
- Business routes receive service dependencies from application composition

**Routing separation (locked):**
- `routing/Routing.kt` is composition-only (mount routes + infrastructure endpoints like `/`/Swagger)
- Business endpoints live in dedicated files (e.g. `routing/TextRoutes.kt`)
- Error handling must not live in business route files

**Error handling separation (locked):**
- StatusPages lives in dedicated routing-side module (`routing/ErrorHandling.kt`)
- Request validation lives in dedicated routing-side module (`routing/RequestValidationConfig.kt`)

### 8. Project Structure Unification

**Locked layout:**
- Everything must live under `src/main/kotlin/com/anjo/...`
- `src/main/kotlin/com/anjo/config/loader/` -> configuration loading only
- `src/main/kotlin/com/anjo/config/model/` -> typed configuration models only
- `src/main/kotlin/com/anjo/di/` -> dependency graph composition + framework plugin setup (HTTP/Monitoring/Serialization)
- `src/main/kotlin/com/anjo/routing/` -> route composition + business endpoint files + request validation + error handling
- `src/main/kotlin/com/anjo/service/` -> business/service logic
- `src/main/kotlin/com/anjo/driver/` -> hardware adapters + abstractions
- `src/main/kotlin/com/anjo/model/` -> API DTOs
- `src/main/kotlin/com/anjo/validation/` -> validation logic only

`NoOpDisplayDriver` pattern is explicitly disallowed for this phase; test/runtime compatibility must come from proper provider selection and explicit wiring.

### 9. Coverage Gate (JaCoCo) - Locked

**Coverage tooling:** JaCoCo must be configured in Gradle for this phase.

**Threshold (hard gate):**
- Minimum overall test coverage: **70%**
- If coverage is below 70%, verification fails and phase cannot be marked complete.

**Execution policy:**
- Coverage verification must run in standard CI/local verification flow.
- `test` + `jacocoTestReport` + `jacocoTestCoverageVerification` are required for closeout evidence.

**Verification commands (required evidence):**
```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
```

**Optional report inspection:**
```bash
./gradlew jacocoTestReport
```
- HTML report path: `build/reports/jacoco/test/html/index.html`

---

*Context captured on 2025-01-25. Ready for Phase 1 planning.*
