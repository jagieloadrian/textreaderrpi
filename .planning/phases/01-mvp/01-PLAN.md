# Phase 1 Plan - MVP Core Text Display with RequestValidation & YAML Config

---
phase: "01"
phase_slug: "01-mvp"
plan_id: "01"
title: "Reliable text submission and LED rendering with Ktor RequestValidation, StatusPages, YAML config"
status: "planned"
wave: 1
depends_on: []
autonomous: true
files_modified:
  - src/main/kotlin/routing/Routing.kt
  - src/main/kotlin/Application.kt
  - src/main/kotlin/service/ReaderInput.kt
  - src/main/kotlin/service/ScreenDriver.kt
  - src/main/kotlin/di/DependencyInjection.kt
  - src/main/kotlin/config/Serialization.kt
  - src/main/resources/application.yaml
  - src/test/kotlin/ApplicationTest.kt
files_created:
  - src/main/kotlin/config/ConfigLoader.kt
  - src/main/kotlin/config/DisplayConfig.kt
  - src/main/kotlin/config/HardwareConfig.kt
  - src/main/kotlin/config/ApiConfig.kt
  - src/main/kotlin/config/TimingConfig.kt
  - src/main/kotlin/config/LoggingConfig.kt
  - src/main/kotlin/config/ApplicationConfig.kt
  - src/main/kotlin/model/TextRequest.kt
  - src/main/kotlin/model/TextResponse.kt
  - src/main/kotlin/model/ErrorResponse.kt
  - src/main/kotlin/validation/RequestValidators.kt
  - src/test/kotlin/routing/TextApiRouteTest.kt
  - src/test/kotlin/service/ScreenDriverTest.kt
  - src/test/kotlin/service/ReaderInputServiceTest.kt
requirements:
  - P1-API-TEXT
  - P1-VALIDATION
  - P1-ERROR-HANDLING
  - P1-CONFIG-YAML
  - P1-SERVICE-TESTS
---

## Goal Backward Contract

**Phase goal:** reliable text submission and LED display rendering with type-safe configuration and declarative validation.

**must_haves**
- `config`: Configuration loaded from application.yaml into typed ConfigObjects (DisplayConfig, HardwareConfig, ApiConfig, TimingConfig, LoggingConfig).
- `validation`: Request validation using Ktor RequestValidation plugin with custom validators.
- `errors`: Error handling using Ktor StatusPages plugin mapping exceptions to HTTP status + JSON error payload.
- `api`: `POST /api/text` accepts TextRequest and responds with TextResponse (or ErrorResponse on failure).
- `tests`: route + service tests cover valid, invalid, and failure branches.

## Wave Plan

### Wave 1 - Configuration Infrastructure with Typed Objects

<task id="01-01" wave="1" type="execute">
<objective>Create YAML configuration structure and typed ConfigObjects as specified in CONTEXT.md.</objective>
<read_first>
- .planning/phases/01-mvp/01-CONTEXT.md (Configuration section)
- src/main/resources/application.yaml
- src/main/kotlin/config/Serialization.kt
</read_first>
<action>
1. Create `src/main/kotlin/config/DisplayConfig.kt` with `data class DisplayConfig(
   val gpioPins: Map<String, Int>,
   val numDevices: Int,
   val brightness: Boolean
)`.
2. Create `src/main/kotlin/config/HardwareConfig.kt` with `data class HardwareConfig(
   val spiTimeoutMs: Long,
   val gpioTimeoutMs: Long
)`.
3. Create `src/main/kotlin/config/ApiConfig.kt` with `data class ApiConfig(
   val maxTextLength: Int,
   val queueSize: Int,
   val rateLimitPerMinute: Int
)`.
4. Create `src/main/kotlin/config/TimingConfig.kt` with `data class TimingConfig(
   val scrollSpeed: Long,
   val refreshRate: Int
)`.
5. Create `src/main/kotlin/config/LoggingConfig.kt` with `data class LoggingConfig(
   val level: String,
   val format: String
)`.
6. Create `src/main/kotlin/config/ApplicationConfig.kt` as root container holding all above.
7. Update `src/main/resources/application.yaml` with full schema as defined in CONTEXT.md.
</action>
<acceptance_criteria>
- All 6 ConfigObjects exist and compile.
- application.yaml contains complete structure matching ConfigObjects (display, hardware, api, timing, logging).
- ConfigObjects use Kotlin `data class` for proper equals/hashCode/toString.
</acceptance_criteria>
</task>

<task id="01-02" wave="1" type="execute" depends_on="01-01">
<objective>Create ConfigLoader to deserialize YAML into typed objects and inject into Application.</objective>
<read_first>
- src/main/kotlin/config/ApplicationConfig.kt (all new config objects)
- src/main/kotlin/Application.kt
- build.gradle.kts (dependencies available)
</read_first>
<action>
1. Create `src/main/kotlin/config/ConfigLoader.kt` with:
   - `fun loadConfig(): ApplicationConfig` method
   - Uses Ktor's built-in YAML deserializer or Jackson/SnakeYAML
   - Loads from `application.yaml` in classpath
   - Returns fully hydrated ApplicationConfig ready for DI
2. Update `src/main/kotlin/Application.kt`:
   - Call `ConfigLoader.loadConfig()` in `module()` early
   - Store ApplicationConfig in application attributes or provide via DI
3. Ensure `./gradlew build` compiles without YAML deserialization errors.
</action>
<acceptance_criteria>
- ConfigLoader produces ApplicationConfig with all nested objects populated from yaml.
- Application.module() loads config without exceptions.
- `./gradlew build` succeeds.
</acceptance_criteria>
</task>

### Wave 2 - RequestValidation Plugin and Error Models

<task id="01-03" wave="2" type="execute" depends_on="01-02">
<objective>Create DTOs and RequestValidation validators as per Ktor documentation.</objective>
<read_first>
- https://ktor.io/docs/server-request-validation.html
- src/main/kotlin/config/ApiConfig.kt (to get maxTextLength constraint)
- .planning/phases/01-mvp/01-CONTEXT.md (validation rules)
</read_first>
<action>
1. Create `src/main/kotlin/model/TextRequest.kt` with:
   - `data class TextRequest(val text: String)`
   - Can be enhanced with validation annotations if desired
2. Create `src/main/kotlin/model/TextResponse.kt` with:
   - `data class TextResponse(val accepted: Boolean, val message: String)`
3. Create `src/main/kotlin/model/ErrorResponse.kt` with:
   - `data class ErrorResponse(val error: ErrorDetails)`
   - `data class ErrorDetails(val code: String, val message: String, val timestamp: String, val details: Map<String, String>? = null)`
4. Create `src/main/kotlin/validation/RequestValidators.kt` with validation logic:
   - `fun validateTextRequest(req: TextRequest, apiConfig: ApiConfig): ValidationResult`
   - Check length <= apiConfig.maxTextLength (128)
   - Check not blank
   - Return appropriate ValidationResult.Invalid or ValidationResult.Valid
</action>
<acceptance_criteria>
- TextRequest, TextResponse, ErrorResponse compile.
- RequestValidators.validateTextRequest works with configurable max length from ApiConfig.
- No hardcoded values in validator; all from config objects.
</acceptance_criteria>
</task>

<task id="01-04" wave="2" type="execute" depends_on="01-03,01-02">
<objective>Install Ktor RequestValidation and StatusPages plugins in routing with error mapping.</objective>
<read_first>
- src/main/kotlin/routing/Routing.kt (existing validation plugin)
- https://ktor.io/docs/server-request-validation.html
- .planning/phases/01-mvp/01-CONTEXT.md (error handling architecture)
- src/main/kotlin/config/ApplicationConfig.kt
</read_first>
<action>
1. Update `src/main/kotlin/routing/Routing.kt`:
   - Replace existing RequestValidation plugin with new one
   - Install custom validator for `TextRequest`:
     - get ApplicationConfig from application attributes
     - call RequestValidators.validateTextRequest
     - return ValidationResult
   - Install StatusPages plugin if not present
   - Add exception handler for RequestValidationException -> HTTP 400 with ErrorResponse JSON
   - Add generic exception handler for Throwable -> HTTP 500 with safe ErrorResponse JSON
2. Keep `GET /` and OpenAPI routes intact.
3. Do NOT yet add `POST /api/text` — that comes next task.
</action>
<acceptance_criteria>
- RequestValidation plugin validates TextRequest against rules.
- StatusPages catches validation exceptions and returns HTTP 400 + ErrorResponse JSON.
- StatusPages catches unhandled exceptions and returns HTTP 500 + safe ErrorResponse JSON.
- `./gradlew build` succeeds.
</acceptance_criteria>
</task>

### Wave 3 - API Endpoint and Service Integration

<task id="01-05" wave="3" type="execute" depends_on="01-04">
<objective>Implement POST /api/text route with DI-injected services.</objective>
<read_first>
- src/main/kotlin/routing/Routing.kt
- src/main/kotlin/di/DependencyInjection.kt
- src/main/kotlin/service/ReaderInput.kt
- src/main/kotlin/service/ScreenDriver.kt
</read_first>
<action>
1. Update `src/main/kotlin/routing/Routing.kt`:
   - Add new route: `post("/api/text") { ... }`
   - Extract TextRequest from request body (automatically validated by RequestValidation plugin)
   - Inject ReaderInputService via get(DependencyKey<...>)
   - Call `readerInputService.readInput(textRequest.text)`
   - Return HTTP `202 Accepted` with TextResponse(accepted=true, message="Text queued")
   - Validation errors and service errors are already handled by StatusPages
2. Update `src/main/kotlin/service/ReaderInput.kt`:
   - Remove `TODO()` from validate method or inline validation
   - Ensure readInput returns properly or throws exception caught by StatusPages
3. Update `src/main/kotlin/service/ScreenDriver.kt`:
   - Remove `TODO()` from validate method
   - Update readInput to use ApiConfig for text length, HardwareConfig for timeouts
</action>
<acceptance_criteria>
- `POST /api/text` route exists and accepts TextRequest
- Valid input returns HTTP 202 with JSON body
- No TODO() remains in service classes
- `./gradlew build` succeeds
</acceptance_criteria>
</task></thinking>

<task id="01-06" wave="3" type="test" depends_on="01-05">
<objective>Add API endpoint tests for POST /api/text covering success and error paths.</objective>
<read_first>
- src/test/kotlin/ApplicationTest.kt (existing test pattern)
- src/main/kotlin/routing/Routing.kt
- src/main/kotlin/model/TextRequest.kt
</read_first>
<action>
1. Create `src/test/kotlin/routing/TextApiRouteTest.kt` using testApplication:
   - Test POST /api/text with valid input -> expects HTTP 202
   - Test POST /api/text with blank input -> expects HTTP 400 + ErrorResponse with code "VAL_001"
   - Test POST /api/text with text > 128 chars -> expects HTTP 400 + ErrorResponse
   - Test GET / still returns HTTP 200 (regression)
2. Keep existing ApplicationTest.testRoot intact.
</action>
<acceptance_criteria>
- `TextApiRouteTest` class exists with 4+ test methods
- Valid request returns 202
- Invalid requests return 400 with ErrorResponse JSON
- All tests pass: `./gradlew test --tests "*TextApiRouteTest*"` exits 0
</acceptance_criteria>
</task>

### Wave 4 - Service-Level Tests

<task id="01-07" wave="4" type="test" depends_on="01-05">
<objective>Add service unit tests that work without real Pi4J hardware.</objective>
<read_first>
- src/main/kotlin/service/ReaderInput.kt
- src/main/kotlin/service/ScreenDriver.kt
- src/main/kotlin/driver/Max7219Matrix.kt
- src/main/kotlin/config/HardwareConfig.kt
</read_first>
<action>
1. Create `src/test/kotlin/service/ReaderInputServiceTest.kt`:
   - Mock/fake ScreenDriverService dependency
   - Test readInput with valid text
   - Test readInput with blank/invalid text (validation should catch before service)
2. Create `src/test/kotlin/service/ScreenDriverTest.kt`:
   - Mock/fake Max7219Matrix driver
   - Test readInput calls driver correctly
   - Test with HardwareConfig timeouts (mock delays)
   - Test retry/timeout behavior with configurable timeouts
3. Both test files should NOT instantiate Pi4J context or real hardware
</action>
<acceptance_criteria>
- Both test files exist and compile
- Tests run on development machine without Raspberry Pi hardware
- `./gradlew test --tests "*ScreenDriverTest*"` and `./gradlew test --tests "*ReaderInputServiceTest*"` pass
</acceptance_criteria>
</task>

### Wave 5 - Documentation

<task id="01-08" wave="5" type="execute" depends_on="01-07">
<objective>Update README with API documentation and configuration guide.</objective>
<read_first>
- README.md
- src/main/resources/application.yaml
- src/main/kotlin/model/TextRequest.kt
- src/main/kotlin/model/ErrorResponse.kt
</read_first>
<action>
1. Update `README.md`:
   - Add API section documenting POST /api/text
   - Include example curl request: `curl -X POST http://localhost:8080/api/text -H "Content-Type: application/json" -d '{"text":"Hello"}'`
   - Include example success response (HTTP 202 + TextResponse JSON)
   - Include example error response (HTTP 400 + ErrorResponse JSON with code)
   - Document configuration section:
     - How to set application.yaml values
     - How to override via environment variables
     - What each config section does (display, hardware, api, timing, logging)
2. Add note about hardware rendering validation done separately.
</action>
<acceptance_criteria>
- README.md contains POST /api/text endpoint documentation
- Includes curl example, request/response examples
- Configuration section explains yaml structure and env overrides
</acceptance_criteria>
</task>

## Verification Commands

Run these as part of completion evidence:

```bash
./gradlew clean build
./gradlew test --tests "*TextApiRouteTest*"
./gradlew test --tests "*ScreenDriverTest*"
./gradlew test --tests "*ReaderInputServiceTest*"
./gradlew test
```

## Dependencies and Config

**New dependency requirements:**
- Ktor RequestValidation and StatusPages already in build.gradle.kts
- YAML deserialization (use Ktor's built-in or add Jackson SnakeYAML if needed)
- No new test libraries required beyond existing JUnit/testApplication

**Config injection pattern:**
- Load ApplicationConfig in Application.module()
- Store in application attributes for access within route handlers
- Pass specific config objects to services via DI or method parameters

## Risks and Controls

- **Risk:** RequestValidation plugin doesn't support custom ApplicationConfig injection  
  **Control:** Pass ApplicationConfig via application attributes, access in validator lambda

- **Risk:** YAML deserialization fails or produces null values  
  **Control:** Add null-safety defaults in ConfigObjects, log missing config values

- **Risk:** StatusPages exception mapping doesn't catch validation exceptions properly  
  **Control:** Test explicitly with invalid payloads, inspect exception types in handler

- **Risk:** Service tests still depend on real Pi4J context  
  **Control:** Mock Max7219Matrix driver completely, avoid Pi4J.newAutoContext() in tests

---

## PLANNING COMPLETE

`C:\Development\textreaderrpi\.planning\phases\01-mvp\01-PLAN.md`

