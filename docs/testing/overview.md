<!-- generated-by: gsd-doc-writer -->
# Testing Overview

## Test Framework and Setup

Tests use **Kotest 6.1.11** with the `FunSpec` style, running on the **JUnit 5** platform via `kotest-runner-junit5`. Mocking is provided by **MockK 1.14.9**. Coroutine testing uses `kotlinx-coroutines-test 1.11.0` with `StandardTestDispatcher` and `runTest`.

All tests require only `./gradlew test` — no separate setup step is needed. The in-memory H2 database is provisioned automatically via the test `application.yaml`.

**Key test dependencies** (from `gradle/ktor-libs.versions.toml`):

| Library | Version |
|---|---|
| kotest-runner-junit5 | 6.1.11 |
| kotest-assertions-core | 6.1.11 |
| mockk | 1.14.9 |
| kotlinx-coroutines-test | 1.11.0 |
| kotlin-test-junit5 | (Kotlin version) |
| jacoco | 0.8.14 |

## Running Tests

```bash
# Run the full test suite
./gradlew test

# Run tests, generate coverage report, and enforce the coverage gate
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification

# Run tests for a single class
./gradlew test --tests "com.anjo.routing.ScheduleRoutesTest"

# Run tests continuously (Gradle continuous build)
./gradlew test --continuous
```

JVM heap for the test task is capped at 768 MB (`-Xmx768m`). mDNS/UDP discovery is disabled in the test JVM via `-Dsystem.property discovery.enabled=false` set in `build.gradle.kts`.

## Writing New Tests

**Naming convention**: `*Test.kt` under `src/test/kotlin/com/anjo/` mirroring the production package structure. Example: a class in `com.anjo.service` has its test in `src/test/kotlin/com/anjo/service/*Test.kt`.

**Spec style**: Use `io.kotest.core.spec.style.FunSpec`. Every test file extends `FunSpec({ ... })`.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MyServiceTest : FunSpec({

    test("should do the thing") {
        // arrange / act / assert
        myService.doThing() shouldBe expected
    }
})
```

**Route tests** use Ktor's `testApplication` with `application { module() }`. This boots the full application context against the in-memory H2 database configured in `src/test/resources/application.yaml`.

```kotlin
import io.ktor.server.testing.testApplication
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

testApplication {
    application { module() }
    val response = client.get("/api/v1/schedule")
    response.status shouldBe HttpStatusCode.OK
}
```

**SSE / streaming tests** cannot use `testApplication` — it is incompatible with SSE streaming. Use `embeddedServer(Netty, port = 0)` with a CIO client instead:

```kotlin
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE

val server = embeddedServer(Netty, port = 0) { module() }.start()
val client = HttpClient(CIO) { install(SSE) }
// ... assertions ...
server.stop()
```

**Service and unit tests** use MockK for dependencies:

```kotlin
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify

val mockRepo = mockk<ScheduleRepository>(relaxed = true)
coEvery { mockRepo.findAllActive() } returns emptyList()
```

**Coroutine timing tests** use `StandardTestDispatcher` and `advanceTimeBy`:

```kotlin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy

runTest {
    val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
    // ... advance virtual time ...
    advanceTimeBy(60_000L)
}
```

## Test Configuration

`src/test/resources/application.yaml` provides the test application configuration. Key values that differ from production:

| Setting | Test value |
|---|---|
| `database.url` | `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` |
| `database.driver` | `org.h2.Driver` |
| `database.poolSize` | `2` |
| `retry.maxAttempts` | `1` |
| `retry.initialDelayMs` | `10` |
| `resources.screenDriverMaxSlots` | `10` |

The H2 database runs in PostgreSQL compatibility mode, so Flyway migrations and SQL queries execute without modification.

## Coverage Requirements

JaCoCo enforces a **70% line coverage minimum** on the CI gate (`jacocoTestCoverageVerification`). The following packages are excluded from coverage measurement because they contain hardware-facing or generated code:

- `com/anjo/driver/**` — Pi4J hardware drivers
- `com/anjo/utils/**` — low-level font/bitmap utilities
- `com/anjo/config/model/**` — plain data classes
- `com/anjo/model/**` — plain data classes and serializer companions

Coverage reports are generated to:
- HTML: `build/reports/jacoco/test/html/`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

| Counter | Threshold |
|---|---|
| LINE | 70% |

## CI Integration

Tests run in the **CI** workflow (`.github/workflows/ci.yml`) on every push and pull request to any branch.

**Job**: `Build, Test, Coverage` on `ubuntu-latest` with JDK 25 (Temurin).

**Test command run by CI**:

```bash
gradle --no-daemon clean test jacocoTestReport jacocoTestCoverageVerification
```

CI uploads three artifacts on every run (including failures):

| Artifact | Path |
|---|---|
| `test-report` | `build/reports/tests/test` |
| `jacoco-report` | `build/reports/jacoco/test/` |
| `gradle-problems-report` | `build/reports/problems/problems-report.html` |

Concurrent runs for the same branch are cancelled automatically via the `concurrency` group `ci-CI-{ref}`.
