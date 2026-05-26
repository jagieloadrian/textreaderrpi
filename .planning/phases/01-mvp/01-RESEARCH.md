# Phase 1 Research - MVP Core Text Display

**Phase:** 01-mvp  
**Date:** 2026-05-26  
**Status:** Ready for planning

## 1) Current Baseline (Observed in Code)

- `src/main/kotlin/Application.kt` starts Ktor and installs HTTP, monitoring, serialization, DI, and routing.
- `src/main/kotlin/routing/Routing.kt` currently exposes only `GET /` and OpenAPI UI; no `POST /api/text` yet.
- Request validation currently checks only `String.startsWith("Hello")`, which does not match phase acceptance criteria.
- `src/main/kotlin/service/ReaderInput.kt` is a thin pass-through to `ScreenDriverService`.
- `src/main/kotlin/service/ScreenDriver.kt` has `String.validate()` as `TODO()` and can fail at runtime.
- `src/main/kotlin/driver/Max7219Matrix.kt` initializes and renders scrolling text, but has no explicit timeout/retry/error reporting contract.
- Test surface is minimal: only `src/test/kotlin/ApplicationTest.kt` asserts `GET /` returns `200`.

## 2) Gap Analysis vs Phase 1 Goal

### API and Validation

Missing:
- `POST /api/text` endpoint with request body contract.
- Length/charset validation and consistent client-safe error response format.
- Explicit behavior for empty payload and oversized text.

### Service Robustness

Missing:
- Queue/backpressure strategy for incoming text requests.
- Clear error taxonomy from route -> service -> driver.
- Graceful handling when hardware calls fail.

### Driver Reliability

Missing:
- Guardrails around SPI operations (timeouts/retry strategy at service or driver boundary).
- Failure signaling that can map to API-level error responses.

### Testing

Missing:
- Unit tests for `ReaderInputService` and `ScreenDriverService`.
- Endpoint tests for valid/invalid input and failure branches.
- Driver behavior tests using controllable test doubles.

## 3) Recommended Implementation Approach

### A. Establish API Contract First

Introduce explicit DTOs and error model:
- `TextRequest(text: String)`
- `TextAcceptedResponse(accepted: Boolean, queued: Boolean)`
- `ErrorResponse(code, message, details?)`

Route behavior:
- `POST /api/text`
- `202 Accepted` for valid accepted submissions.
- `400 Bad Request` for validation errors.
- `500 Internal Server Error` for unexpected failures.

### B. Move Validation out of Route Plugins into Dedicated Logic

Keep route validation simple and deterministic by adding a dedicated validator used by service or route:
- Max length check (phase context uses 128).
- Empty/blank rejection.
- Character-set policy aligned to `01-CONTEXT.md` (UTF-8 accepted, hardware rendering caveat documented).

### C. Add Queue/Flow Control at Service Layer

`ReaderInputService` should own enqueue semantics instead of immediate direct driver invocation.
Possible shape:
- bounded queue/channel,
- non-blocking submit with explicit rejection when full,
- dedicated consumer coroutine delegating to `ScreenDriverService`.

This isolates HTTP request latency from rendering latency and supports robust API responses.

### D. Add Safe Driver Boundary

Keep low-level rendering in `Max7219Matrix`, but add a resilient boundary in `ScreenDriverService`:
- timeout around rendering invocation,
- bounded retries with small backoff,
- explicit result (`Success`/`RecoverableFailure`/`FatalFailure`) mapped to logging and HTTP status.

### E. Preserve Existing Architecture Patterns

Current structure already separates routing/services/driver. Phase 1 should harden the existing stack rather than re-architect.

## 4) Dependencies and Runtime Notes

Current build facts from `build.gradle.kts`:
- Kotlin plugin `2.3.0`
- Ktor plugin `3.4.2`
- JVM toolchain `25`
- Pi4J dependencies present

Research recommendation:
- Avoid broad version churn in this phase unless required for a concrete blocker.
- If runtime target is Raspberry Pi with JDK 21, confirm toolchain compatibility before major feature work.
- Keep dependency changes minimal and tested with `./gradlew test` before merging.

## 5) Test Strategy (Phase 1)

### Unit Tests

- `ReaderInputService`:
  - accepts valid text,
  - rejects invalid/blank/oversized text,
  - reports queue-full condition.
- `ScreenDriverService`:
  - invokes driver for valid input,
  - handles timeout/retry path,
  - surfaces failure in a predictable result.

### Routing/API Tests (`testApplication`)

- `POST /api/text` valid payload -> `202`.
- invalid payload -> `400` with structured error JSON.
- internal service failure -> `500` with safe message.

### Driver-Facing Tests

- Use mock/stub driver instead of real Pi4J hardware.
- Verify call ordering and retry count, not physical SPI behavior.

## 6) Risks and Mitigations

- **Hardware coupling in tests** -> Introduce interface seam or fake driver for service tests.
- **Queue deadlocks/leaks** -> keep bounded queue and explicit coroutine lifecycle hooks.
- **Ambiguous validation policy** -> source of truth must be `01-CONTEXT.md` + route contract tests.
- **Over-scoping (health, rate limits, config overhaul)** -> keep to MVP hardening; defer unrelated concerns.

## 7) Suggested Plan Decomposition for Planner

Wave proposal:

1. **Wave 1 - API contract and validation**
   - DTOs/error model, `POST /api/text`, baseline tests.
2. **Wave 2 - Service hardening**
   - queue/backpressure, validation integration, deterministic results.
3. **Wave 3 - Driver reliability boundary**
   - timeout/retry wrapper and failure mapping.
4. **Wave 4 - Test expansion and documentation updates**
   - service + route tests, README/API usage notes.

This sequence gives fast feedback from API tests while reducing risk before deeper driver behavior changes.

## 8) Planning Guidance

- Prioritize changes in currently existing files:
  - `src/main/kotlin/routing/Routing.kt`
  - `src/main/kotlin/service/ReaderInput.kt`
  - `src/main/kotlin/service/ScreenDriver.kt`
  - `src/main/kotlin/di/DependencyInjection.kt`
- Add only focused new files under existing package structure (`com.anjo.*`).
- Keep first execution slice compile-safe and test-backed before adding optional extras.

---

## RESEARCH COMPLETE

`C:\Development\textreaderrpi\.planning\phases\01-mvp\01-RESEARCH.md`

