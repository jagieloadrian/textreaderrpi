# Phase 10: Webhooks - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 9 new/modified files
**Analogs found:** 9 / 9

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/anjo/service/WebhookService.kt` | service | event-driven / fire-and-forget | `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` | role-match (IO scope + SupervisorJob pattern) |
| `src/main/kotlin/com/anjo/model/WebhookPayload.kt` | model | transform | `src/main/kotlin/com/anjo/model/HistoryRecord.kt` | exact |
| `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` | config | — | `src/main/kotlin/com/anjo/config/model/RetryConfig.kt` | exact |
| `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` | config | — | self (modify existing) | exact |
| `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` | config | — | self (modify existing) | exact |
| `src/main/kotlin/com/anjo/service/SchedulerService.kt` | service | CRUD / event-driven | self (modify existing) | exact |
| `src/main/kotlin/com/anjo/di/DependencyInjection.kt` | config / DI | — | self (modify existing) | exact |
| `src/main/resources/application.yaml` | config | — | self (modify existing) | exact |
| `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt` | test | request-response | `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` | role-match |
| `src/test/kotlin/com/anjo/ApplicationTest.kt` | test | — | self (modify existing) | exact |

---

## Pattern Assignments

### `src/main/kotlin/com/anjo/service/WebhookService.kt` (NEW — service, fire-and-forget)

**Analog:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`

**Imports pattern** (ScreenDriverService lines 1-26 — adapt for WebhookService):
```kotlin
package com.anjo.service

import com.anjo.config.model.WebhooksConfig
import com.anjo.model.Schedule
import com.anjo.model.WebhookPayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Instant
```

**CoroutineScope pattern** (ScreenDriverService lines 28-43 — the displayScope + SupervisorJob):

The analog for `WebhookService`'s own IO scope is `ScreenDriverService.displayScope`:
```kotlin
// ScreenDriverService.kt lines 39-40
private val displayScope = CoroutineScope(SupervisorJob() + ioDispatcher)
```
For `WebhookService` the scope is hardcoded to `Dispatchers.IO` (no injected dispatcher needed):
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

**Constructor injection pattern** (ScreenDriverService lines 28-43 — nullable optional params pattern):
```kotlin
// ScreenDriverService.kt lines 28-36 — params with nullable defaults
class ScreenDriverService(
    private var driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher,
    private val retryConfig: RetryConfig,
    private val displaySelectionService: DisplaySelectionService?,
    private val metrics: ScreenDriverMetrics,
    private val effectFactory: EffectRendererFactory = EffectRendererFactory(),
    private val historyRepository: HistoryRepository? = null,
)
```
`WebhookService` constructor: `(httpClient: HttpClient, config: WebhooksConfig, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()))` — scope is injectable for testability (Pitfall 1 Option A).

**Fire-and-forget launch pattern** (ScreenDriverService lines 59-63):
```kotlin
// ScreenDriverService.kt lines 59-63
currentDisplayJob = displayScope.launch {
    renderImmediate(text, effect, alreadyLocked = conflictPolicy == ConflictPolicy.SKIP_NEW)
}
return true
```
`WebhookService.send()` follows the same non-suspend pattern: `scope.launch { ... }` and returns immediately.

**Non-fatal error handling pattern** (ScreenDriverService lines 112-118):
```kotlin
// ScreenDriverService.kt lines 112-118
} catch (e: Exception) {
    metrics.failedMeter?.mark()
    log.error("Display operation failed after retries: ${e.message}", e)
} finally {
    metrics.inFlightCounter?.dec()
    timerContext?.stop()
```
For WebhookService, replace with `log.warn(...)` on TimeoutCancellationException and general Exception (never `log.error` — webhook failure is non-fatal).

**History tryInsert silent-failure pattern** (ScreenDriverService lines 157-167 — exact model for `send()` silent failure):
```kotlin
// ScreenDriverService.kt lines 157-167
private suspend fun tryInsertHistory(...) {
    try {
        historyRepository?.insert(...)
    } catch (e: Exception) {
        log.warn("History insert failed (non-fatal): ${e.message}", e)
    }
}
```
`WebhookService.send()` uses the same swallow-and-warn pattern inside `scope.launch {}`.

---

### `src/main/kotlin/com/anjo/model/WebhookPayload.kt` (NEW — model, transform)

**Analog:** `src/main/kotlin/com/anjo/model/HistoryRecord.kt`

**Full analog** (HistoryRecord.kt lines 1-14):
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryRecord(
    val id: String = "",
    val text: String,
    val effect: String,
    val source: String,
    val scheduleId: String? = null,
    val zoneId: String? = null,
    val displayedAt: String = ""
)
```
`WebhookPayload` follows the identical `@Serializable data class` pattern in the same package. Fields per D-11: `scheduleId: String`, `text: String`, `effect: String`, `zoneId: String?`, `firedAt: String`. No default values — all required at construction.

---

### `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` (NEW — config data class)

**Analog:** `src/main/kotlin/com/anjo/config/model/RetryConfig.kt`

**Full analog** (RetryConfig.kt lines 1-9):
```kotlin
package com.anjo.config.model

data class RetryConfig(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val factor: Double = 2.0,
)
```
`WebhooksConfig` is a plain `data class` (no `@Serializable` — config classes are not serialized to JSON in this project). Single field: `val defaultUrl: String?` with no default (nullable — absence means no fallback).

---

### `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` (MODIFY — add webhooks field)

**Self-reference** (ApplicationConfig.kt lines 1-9):
```kotlin
package com.anjo.config.model

data class ApplicationConfig(
    val display: DisplayConfig,
    val api: ApiConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig
)
```
Add `val webhooks: WebhooksConfig` as the last field. All other callers pass it positionally or by name — ConfigLoader is the only constructor call site.

---

### `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` (MODIFY — add webhooks block)

**Pattern for new config block** (ConfigLoader.kt lines 56-68 — databaseConfig block is the most recent addition):
```kotlin
// ConfigLoader.kt lines 62-68
val databaseConfig = DatabaseConfig(
    url = config.propertyOrNull("database.url")?.getString() ?: "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    driver = config.propertyOrNull("database.driver")?.getString() ?: "org.h2.Driver",
    user = config.propertyOrNull("database.user")?.getString() ?: "",
    password = config.propertyOrNull("database.password")?.getString() ?: "",
    poolSize = config.propertyOrNull("database.poolSize")?.getString()?.toIntOrNull() ?: 5
)
```
New `webhooksConfig` block follows identical `propertyOrNull("webhooks.X")?.getString()` pattern:
```kotlin
val webhooksConfig = WebhooksConfig(
    defaultUrl = config.propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }
)
```
Also update `return ApplicationConfig(...)` on line 70 to pass `webhooks = webhooksConfig`.

**Pitfall:** ConfigLoader.kt line 76 currently passes `databaseConfig` positionally without a named argument — note the existing call at lines 70-76 uses mixed positional/named style. Adding `webhooks = webhooksConfig` as a named argument at the end is safest.

---

### `src/main/kotlin/com/anjo/service/SchedulerService.kt` (MODIFY — add webhookService param + call)

**Constructor pattern** (SchedulerService.kt lines 24-29):
```kotlin
// SchedulerService.kt lines 24-29
class SchedulerService(
    private val repository: ScheduleRepository,
    private val screenService: ScreenDriverService,
    private val effectFactory: EffectRendererFactory,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
)
```
Add `private val webhookService: WebhookService? = null` as a new named param with `null` default. This avoids breaking `SchedulerServiceTest` existing call sites which pass positional args (Pitfall 4).

**fire() integration point** (SchedulerService.kt lines 170-180):
```kotlin
// SchedulerService.kt lines 170-180 — current fire()
private suspend fun fire(schedule: Schedule): Boolean {
    return try {
        log.info("Firing schedule id=${schedule.id} text='${schedule.text.take(30)}' effect=${schedule.effect}")
        val renderer = effectFactory.create(schedule.effect)
        val policy = schedule.conflictPolicy ?: ConflictPolicy.INTERRUPT
        screenService.displayScheduled(schedule.text, schedule.id, renderer, schedule.effect, policy)
    } catch (e: Exception) {
        log.error("Failed to fire schedule ${schedule.id}: ${e.message}", e)
        false
    }
}
```
Modify to capture the `displayed` boolean and call `webhookService?.send(schedule, Instant.now())` on the `true` path:
```kotlin
private suspend fun fire(schedule: Schedule): Boolean {
    return try {
        log.info("Firing schedule id=${schedule.id} text='${schedule.text.take(30)}' effect=${schedule.effect}")
        val renderer = effectFactory.create(schedule.effect)
        val policy = schedule.conflictPolicy ?: ConflictPolicy.INTERRUPT
        val displayed = screenService.displayScheduled(schedule.text, schedule.id, renderer, schedule.effect, policy)
        if (displayed) {
            webhookService?.send(schedule, Instant.now())
        }
        displayed
    } catch (e: Exception) {
        log.error("Failed to fire schedule ${schedule.id}: ${e.message}", e)
        false
    }
}
```

---

### `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (MODIFY — add HttpClient + WebhookService)

**provide{} pattern** (DependencyInjection.kt lines 56-69):
```kotlin
// DependencyInjection.kt lines 56-69
dependencies {
    provide { appConfig }
    provide { appConfig.api }
    provide { appConfig.display }
    provide { Dispatchers.IO }
    provide { metricRegistry }
    provide { displaySelectionService }
    provide { screenDriverService }
    provide { metricsCollector }
    provide { scheduleRepository }
    provide { historyRepository }
    provide { effectRendererFactory }
    provide { schedulerService }
}
```
Add `provide { httpClient }` and `provide { webhookService }` inside the `dependencies {}` block.

**Construction pattern** (DependencyInjection.kt lines 36-48 — ScreenDriverService construction as analog):
```kotlin
// DependencyInjection.kt lines 36-48
val screenDriverService = ScreenDriverService(
    driver = displaySelectionService.currentDriver() ?: OfflineDisplayDriver,
    ioDispatcher = Dispatchers.IO,
    retryConfig = appConfig.retryConfig,
    displaySelectionService = displaySelectionService,
    metrics = screenDriverMetrics,
    historyRepository = historyRepository,
)
```
New construction before `schedulerService`:
```kotlin
val httpClient = HttpClient(CIO) {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        json()
    }
}
val webhookService = WebhookService(httpClient, appConfig.webhooks)
val schedulerService = SchedulerService(scheduleRepository, screenDriverService, effectRendererFactory, webhookService = webhookService)
```

---

### `src/main/resources/application.yaml` (MODIFY — add webhooks block)

**Pattern** (application.yaml lines 41-44 — retry block as model):
```yaml
retry:
  maxAttempts: ${RETRY_MAX_ATTEMPTS:5}
  initialDelayMs: ${RETRY_INITIAL_DELAY_MS:1000}
  maxDelayMs: ${RETRY_MAX_DELAY_MS:30000}
  factor: ${RETRY_FACTOR:2.0}
```
New block to append after `database:` section:
```yaml
webhooks:
  defaultUrl: "${WEBHOOK_DEFAULT_URL:}"
```
The `:` after the var name with no default value means empty string if env var is absent — consistent with the existing `${DATABASE_PASSWORD:}` pattern on line 60.

Also update `src/test/resources/application.yaml` with the same `webhooks:` block (same empty-string default — no real URL needed in tests per Pitfall 2).

---

### `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt` (NEW — unit test)

**Analog:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt`

**Imports + spec structure** (SchedulerServiceTest.kt lines 1-25):
```kotlin
package com.anjo.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WebhookServiceTest : FunSpec({ ... })
```

**MockEngine + runTest + advanceUntilIdle pattern** (RESEARCH.md Pitfall 1 Option A — preferred approach):
```kotlin
runTest {
    val capturedRequests = mutableListOf<io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.engine.mock.MockHttpResponse>()
    // ... MockEngine captures requests
    val service = WebhookService(client, config, this)  // inject TestScope
    service.send(schedule, firedAt)
    advanceUntilIdle()
    // assert capturedRequests
}
```
Tests must assert per D-17: request URL, HTTP method POST, Content-Type `application/json`, JSON body fields `scheduleId`, `text`, `effect`, `zoneId`, `firedAt`.

**beforeEach pattern** (SchedulerServiceTest.kt lines 32-37):
```kotlin
// SchedulerServiceTest.kt lines 32-37
beforeEach {
    clearMocks(mockRepo, mockScreen, mockFactory, mockRenderer)
    coEvery { mockFactory.create(any()) } returns mockRenderer
    coEvery { mockRepo.findAllActive() } returns emptyList()
    coEvery { mockScreen.displayScheduled(any(), any(), any(), any(), any()) } returns true
}
```
`WebhookServiceTest` recreates `MockEngine` and `WebhookService` per test case inside `runTest` — no shared state needed since `MockEngine` is stateless per-construction.

---

### `src/test/kotlin/com/anjo/ApplicationTest.kt` (MODIFY — add WebhookService DI assertion)

**Pattern** (ApplicationTest.kt lines 68-86 — existing smoke test block):
```kotlin
// ApplicationTest.kt lines 68-86
test("should resolve all configureDI bindings without error") {
    testApplication {
        application { module() }
        client.get("/health")
        val deps = application.dependencies
        deps.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>()) shouldNotBeNull {}
        // ... existing assertions
        deps.getBlocking<SchedulerService>(DependencyKey<SchedulerService>()) shouldNotBeNull {}
    }
}
```
Add after the `SchedulerService` line:
```kotlin
deps.getBlocking<WebhookService>(DependencyKey<WebhookService>()) shouldNotBeNull {}
```
Also add `import com.anjo.service.WebhookService` to the import block.

---

## Shared Patterns

### CoroutineScope with SupervisorJob (IO-scoped, fire-and-forget)
**Source:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` line 39
**Apply to:** `WebhookService.kt`
```kotlin
private val displayScope = CoroutineScope(SupervisorJob() + ioDispatcher)
```
`WebhookService` version: `CoroutineScope(Dispatchers.IO + SupervisorJob())` — hardcoded IO, no injected dispatcher. Accept as optional constructor param for test injection.

### Non-fatal warn-and-continue error handling
**Source:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` lines 157-167
**Apply to:** `WebhookService.send()` internal catch blocks
```kotlin
} catch (e: Exception) {
    log.warn("History insert failed (non-fatal): ${e.message}", e)
}
```
Webhook version uses `log.warn` (not `log.error`) and includes `scheduleId`, resolved URL, and HTTP status code in message.

### Config propertyOrNull with nullable coercion
**Source:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` lines 62-68
**Apply to:** `ConfigLoader.kt` webhooksConfig block
```kotlin
url = config.propertyOrNull("database.url")?.getString() ?: "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
```
Webhooks version adds `.takeIf { it.isNotBlank() }` to coerce empty string to null (env var absent yields empty string `""`).

### Kotest FunSpec + mockk + runTest pattern
**Source:** `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` lines 1-59
**Apply to:** `WebhookServiceTest.kt`
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerServiceTest : FunSpec({
    // mockk setup
    beforeEach { clearMocks(...) }
    test("...") {
        runTest {
            val testScope = TestScope(StandardTestDispatcher(testScheduler) + Job())
            // ...
            advanceTimeBy(...)
        }
    }
})
```
`WebhookServiceTest` uses `runTest { val service = WebhookService(client, config, this); service.send(...); advanceUntilIdle() }`.

### DI provide{} singleton registration
**Source:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt` lines 56-69
**Apply to:** `DependencyInjection.kt` for `HttpClient` and `WebhookService`
```kotlin
provide { screenDriverService }
```
New entries: `provide { httpClient }` and `provide { webhookService }` — same one-liner form.

---

## Build File Changes

### `gradle/ktor-libs.versions.toml` (MODIFY — add 4 client entries)
**Pattern** (ktor-libs.versions.toml lines 31-48 — existing server entries):
```toml
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
```
New entries in `[libraries]` section:
```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```
`version.ref = "ktor"` reuses the existing `ktor = "3.5.0"` version — no new version entry needed.

### `build.gradle.kts` (MODIFY — add 4 client dependencies)
**Pattern** (existing `implementation(ktorLibs.ktor.server.core)` style):
```kotlin
implementation(ktorLibs.ktor.client.core)
implementation(ktorLibs.ktor.client.cio)
implementation(ktorLibs.ktor.client.content.negotiation)
testImplementation(ktorLibs.ktor.client.mock)
```

---

## No Analog Found

All files have close analogs. No entries in this section.

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`, `src/main/resources/`, `gradle/`
**Files scanned:** 10 source files read directly
**Pattern extraction date:** 2026-06-15
