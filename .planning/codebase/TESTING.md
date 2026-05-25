# Testing Patterns

**Analysis Date:** 2024-01-20

## Test Framework

**Runner:**
- Ktor `testApplication` for HTTP endpoint testing
- Kotlin Test framework
- JUnit 5 support

**Run Commands:**
```bash
./gradlew test                # Run all tests
./gradlew test --info         # Verbose output
./gradlew testClasses         # Just compile tests
```

## Test File Organization

**Location:**
- `src/test/kotlin/ApplicationTest.kt` - primary test file

**Naming:**
- Test file: `ApplicationTest.kt` matches main `Application.kt`
- Test methods use descriptive names

## Test Structure

**Suite Organization:**

From `src/test/kotlin/ApplicationTest.kt`:
```kotlin
class ApplicationTest {
    @Test
    fun testNameHere() = testApplication {
        // Client setup and assertions
    }
}
```

**Patterns:**
- `testApplication {}` - Ktor DSL for isolated app testing
- `client` - HTTP client provided within testApplication scope
- Route testing via HTTP calls to endpoints

**Setup:**
- `testApplication { }` block sets up test app instance
- Uses actual module configuration

**Teardown:**
- Automatic resource cleanup by Ktor testApplication

**Assertion Pattern:**
```kotlin
val response = client.get("/endpoint")
assertEquals(HttpStatusCode.OK, response.status)
```

## Mocking

**Strategy:**
- Real service classes tested (not mocked) where possible
- Hardware abstraction via Pi4J mock plugin for GPIO/SPI
- Pi4J provides MockFactory for injecting mock providers

**Framework:**
- Pi4J's built-in mock support (`pi4j-plugin-mock`)
- Configured in build.gradle.kts

**What to Mock:**
- Hardware I/O operations (via Pi4J mock plugin)
- Long-running operations if testing business logic in isolation

**What NOT to Mock:**
- Service layer logic - test real implementations
- Validation logic - test real validators
- Routing layer - test real routes with real services

## Fixtures and Test Data

**Test Data:**
- Inline test data in test methods
- Example HTTP payloads created directly in tests

**No Factory Pattern:**
- Test objects created directly in test methods

## Coverage

**Requirements:**
- No explicit coverage target configured
- Coverage can be measured with Gradle plugin if needed

## Test Types

**Unit Tests:**
- Test individual services via HTTP endpoints
- Example: POST /api/text endpoint with valid input
- Scope: Service methods and logic

**Integration Tests:**
- Full HTTP request-response cycle via `testApplication`
- Test validation, service layer, and serialization together
- Example: POST /api/text with various inputs

**E2E Tests:**
- Not in automated suite (would require real hardware)
- Manual testing on Raspberry Pi

**Hardware Tests:**
- Depend on Pi4J MockFactory for GPIO/SPI mocking
- Real hardware interaction tested manually on Pi

## Common Testing Patterns

**Async Testing:**
- Suspend functions tested through HTTP endpoints
- testApplication handles coroutine execution

**Error Testing:**
- Invalid requests tested for proper error responses (HTTP 400, 500, etc.)
- Response status code assertions
- Error message validation in response body

**Testing Validation:**
- Invalid inputs sent to endpoints
- Assert HTTP 400 response for bad requests
- Assert error details in response

**Testing Services:**
- Indirectly through HTTP endpoints
- testApplication provides app context

## Test Coverage Areas

**Endpoints Tested (examples):**
1. Text display endpoint - valid input, invalid input
2. Status endpoint - current display state
3. Health check endpoint - server readiness

**Services Tested:**
- ReaderInput service: via endpoints that queue text
- ScreenDriver service: via endpoints that trigger rendering

**Validation:**
- Request body validation
- Input length and format constraints

## Test Dependencies

**From build.gradle.kts:**
- `ktor-server-test-host` - testApplication DSL and test client
- `kotlin-test` - Kotlin testing utilities
- `pi4j-plugin-mock` - Hardware mocking for GPIO/SPI testing

## Running Tests

**All Tests:**
```bash
./gradlew test
```

**Single Test Class:**
```bash
./gradlew test --tests ApplicationTest
```

**Single Test Method:**
```bash
./gradlew test --tests ApplicationTest.testNameHere
```

**With Detailed Output:**
```bash
./gradlew test --info
```

---

*Testing analysis: 2024-01-20*

