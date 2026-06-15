# Phase 10: Webhooks - Research

**Researched:** 2026-06-15
**Domain:** Ktor HTTP Client, Kotlin coroutines fire-and-forget, config extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Webhook fires only when `screenService.displayScheduled()` returns `true`. Success-only, matches Phase 9 history recording rule.
- **D-02:** Webhook does NOT fire on SKIP_NEW (display skipped) or display failure.
- **D-03:** Webhooks scoped to `SchedulerService.fire()` only. `POST /api/v1/text` immediate display is out of scope.
- **D-04:** URL resolution priority: `schedule.webhookUrl` if non-null; otherwise `AppConfig.webhooks.defaultUrl`. One POST per fire, never both.
- **D-05:** If both are null/empty, skip webhook silently (no log, no error).
- **D-06:** Global fallback via `webhooks.defaultUrl: ${WEBHOOK_DEFAULT_URL:}` in `application.yaml`. New `WebhooksConfig` data class in AppConfig. `${VAR:default}` pattern consistent with all 25 existing settings.
- **D-07:** Webhook logic in dedicated `WebhookService` class. Owns `HttpClient`, URL resolution, payload construction, `withTimeout(5_000)`.
- **D-08:** `WebhookService(httpClient: HttpClient, config: WebhooksConfig)` — `HttpClient` injected as constructor param to allow `MockEngine` in tests. Production DI wires `HttpClient(CIO)`.
- **D-09:** `WebhookService` owns its own `CoroutineScope(Dispatchers.IO + SupervisorJob())`. `send()` is a non-suspend function — returns immediately (fire-and-forget).
- **D-10:** `WebhookService` registered as singleton via `provide {}` in `DependencyInjection.kt`. Injected into `SchedulerService`. DI smoke test in `ApplicationTest.kt` updated to resolve `WebhookService`.
- **D-11:** JSON payload: `{ "scheduleId": "...", "text": "...", "effect": "SCROLL", "zoneId": null, "firedAt": "2026-06-15T20:00:00.000Z" }`
- **D-12:** `firedAt` is `Instant.now().toString()` at time of successful display.
- **D-13:** `zoneId` is `schedule.zoneId` (nullable), included even when null.
- **D-14:** HTTP POST with `Content-Type: application/json`. No auth header.
- **D-15:** On timeout or non-2xx: `log.warn(...)` with scheduleId, resolved URL, HTTP status code. No retry, no schedule state change, no DB write.
- **D-16:** Exception caught inside `WebhookService.send()` scope — never propagates to `SchedulerService`.
- **D-17:** `WebhookService` unit-tested with `HttpClient(MockEngine { ... })`. Tests assert: request URL, HTTP method, Content-Type header, JSON body fields.
- **D-18:** `SchedulerService` integration test verifies `WebhookService.send()` called after `displayScheduled()` returns `true`, NOT called when it returns `false` (SKIP_NEW).

### Claude's Discretion

None specified — all decisions locked.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HOOK-01 | After schedule fires, HTTP POST sent to `webhookUrl` (fire-and-forget, timeout 5s) | Ktor CIO client + `withTimeout(5_000)` inside `scope.launch {}` on `Dispatchers.IO` |
| HOOK-02 | Webhook payload contains: text, effect, schedule ID, zone ID, timestamp | Kotlinx serialization `@Serializable` data class posted via `setBody()` with `ContentType.Application.Json` |
| HOOK-03 | Global fallback `webhookUrl` configurable via `WEBHOOK_DEFAULT_URL` env var | `webhooks.defaultUrl: ${WEBHOOK_DEFAULT_URL:}` in application.yaml + `WebhooksConfig` data class in AppConfig |

</phase_requirements>

---

## Summary

Phase 10 adds a thin fire-and-forget HTTP notification layer to the scheduler. When `SchedulerService.fire()` succeeds (i.e., `displayScheduled()` returns `true`), a new `WebhookService` posts a JSON payload to the resolved URL and discards the result. The integration is a clean four-file addition: one new service class, one new config data class, one YAML block, and one constructor-param injection point.

The full Ktor 3.5.0 stack is already on the classpath for the server side. The three Ktor client modules (`ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`) are not yet listed in `gradle/ktor-libs.versions.toml` — they must be added at the same `ktor = "3.5.0"` version. This is the only build change required.

`Schedule.webhookUrl: String?` and `Schedule.zoneId: String?` already exist in the model and DB schema (Phase 7, SCHED-04). No migration is needed. The `CoroutineScope(Dispatchers.IO + SupervisorJob())` pattern already appears in `ScreenDriverService` and the fire-and-forget `displayImmediate` path — `WebhookService` follows the identical pattern.

**Primary recommendation:** Add three Ktor client catalog entries, create `WebhooksConfig` + `WebhookService`, wire into DI, call `webhookService.send()` in `SchedulerService.fire()` after the `true` return path.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Webhook dispatch | Service layer (`WebhookService`) | — | Isolated side-effect; no route, no DB |
| URL resolution (per-schedule vs. global fallback) | Service layer (`WebhookService`) | — | Business rule owned by the service, not the scheduler |
| Global fallback configuration | Config layer (`WebhooksConfig` + `application.yaml`) | — | Follows existing config pattern for all 25 settings |
| HTTP POST execution | Service layer (`WebhookService` via `HttpClient(CIO)`) | — | Network I/O on Dispatchers.IO scope |
| Fire-and-forget isolation | Coroutine scope (`CoroutineScope(Dispatchers.IO + SupervisorJob())`) | — | Prevents webhook from blocking scheduler's `Dispatchers.Default` thread pool |
| Failure handling | Service layer (catch inside `send()`) | — | Exception must never escape to `SchedulerService` |
| DI wiring | DI layer (`DependencyInjection.kt`) | — | Constructor injection pattern consistent with all services |
| Testing | Unit test (`WebhookServiceTest` with `MockEngine`) + integration stub in `SchedulerServiceTest` | — | MockEngine for HTTP; mockk stub for service boundary in scheduler test |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `ktor-client-core` | 3.5.0 | Ktor HTTP client abstractions (`HttpClient`, `HttpRequestBuilder`) | Same version group as server — zero conflicts [ASSUMED] |
| `ktor-client-cio` | 3.5.0 | CIO (Coroutines I/O) engine for `HttpClient` — pure Kotlin, no extra runtime | Already used for CIO server in this project; lightest engine, JVM+native [ASSUMED] |
| `ktor-client-content-negotiation` | 3.5.0 | Adds `ContentType.Application.Json` + `setBody()` serialization to client requests | Required for JSON body encoding alongside server's `ktor-serialization-kotlinx-json` [ASSUMED] |

> **Note:** All three are first-party JetBrains/Ktor libraries at the same version as the already-installed Ktor server. They are well-established and standard. Tagged `[ASSUMED]` per provenance rules — registry existence confirmed via training knowledge but not via Context7 or official docs lookup in this session.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `ktor-client-mock` | 3.5.0 | `MockEngine` for unit-testing `HttpClient` calls without a real server | `testImplementation` only — `WebhookServiceTest` [ASSUMED] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ktor-client-cio` | `ktor-client-okhttp` | OkHttp adds a Java dependency; CIO is pure Kotlin and matches the project's existing CIO server engine |
| `ktor-client-cio` | `java.net.http.HttpClient` | JDK built-in, zero deps, but requires more boilerplate for JSON and lacks native coroutine integration |

**Installation** (new entries for `gradle/ktor-libs.versions.toml` and `build.gradle.kts`):

```toml
# In [libraries] section of ktor-libs.versions.toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

```kotlin
// In build.gradle.kts dependencies block
implementation(ktorLibs.ktor.client.core)
implementation(ktorLibs.ktor.client.cio)
implementation(ktorLibs.ktor.client.content.negotiation)
testImplementation(ktorLibs.ktor.client.mock)
```

---

## Package Legitimacy Audit

> The package legitimacy seam does not support the JVM/Maven ecosystem. Manual assessment follows.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `io.ktor:ktor-client-core` | Maven Central | ~8 yrs (Ktor 1.0 2018) | Tens of millions/month | github.com/ktorio/ktor | OK | Approved |
| `io.ktor:ktor-client-cio` | Maven Central | ~8 yrs | Tens of millions/month | github.com/ktorio/ktor | OK | Approved |
| `io.ktor:ktor-client-content-negotiation` | Maven Central | ~4 yrs (Ktor 2.0 2022) | Tens of millions/month | github.com/ktorio/ktor | OK | Approved |
| `io.ktor:ktor-client-mock` | Maven Central | ~8 yrs | Tens of millions/month | github.com/ktorio/ktor | OK | Approved |

All four are first-party JetBrains modules from the same `io.ktor` group already present in the project. No slopsquatting risk. [ASSUMED] — not verified via `npm view` equivalent for Maven, but provenance from JetBrains/ktorio is unambiguous.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
SchedulerService.fire(schedule)
        |
        v
screenService.displayScheduled() ──► returns false (SKIP_NEW / failure)
        |                                    |
        | returns true                       v
        v                              [webhook skipped]
webhookService.send(schedule, Instant.now())
        |
        | (non-suspend, returns immediately)
        v
WebhookService internal scope.launch {
    withTimeout(5_000) {
        resolveUrl(schedule, config) ──► null/empty? → return silently
                |
                v
        httpClient.post(url) {
            contentType(Application.Json)
            setBody(WebhookPayload(...))
        }
        response.status.isSuccess() ──► false → log.warn(...)
    }
} ──► TimeoutCancellationException → log.warn(...)
  └──► any other Exception        → log.warn(...)
```

### Recommended Project Structure

```
src/main/kotlin/com/anjo/
├── config/
│   ├── loader/
│   │   └── ConfigLoader.kt          # add webhooksConfig block
│   └── model/
│       ├── ApplicationConfig.kt     # add webhooks: WebhooksConfig field
│       └── WebhooksConfig.kt        # NEW — data class WebhooksConfig(defaultUrl: String?)
├── service/
│   ├── SchedulerService.kt          # add webhookService param; call send() in fire()
│   └── WebhookService.kt            # NEW
└── di/
    └── DependencyInjection.kt       # add HttpClient(CIO) and WebhookService provide{}

src/main/resources/
└── application.yaml                 # add webhooks block

src/test/kotlin/com/anjo/
├── ApplicationTest.kt               # add get<WebhookService>() assertion
└── service/
    ├── SchedulerServiceTest.kt      # add webhook send/no-send assertions
    └── WebhookServiceTest.kt        # NEW — MockEngine-based unit tests
```

### Pattern 1: WebhookService with fire-and-forget scope

**What:** `send()` is a regular (non-suspend) function. Internally it launches a coroutine on `Dispatchers.IO` with `withTimeout`. The caller in `SchedulerService.fire()` needs no `launch {}` — it calls `send()` directly.

**When to use:** Any side-effect that must not block the caller's coroutine and whose failure must be isolated.

```kotlin
// Source: codebase — ScreenDriverService displayImmediate pattern (post-Phase-9)
class WebhookService(
    private val httpClient: HttpClient,
    private val config: WebhooksConfig
) {
    private val log = LoggerFactory.getLogger(WebhookService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun send(schedule: Schedule, firedAt: Instant) {
        val url = resolveUrl(schedule) ?: return
        scope.launch {
            try {
                withTimeout(5_000) {
                    val response = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(buildPayload(schedule, firedAt))
                    }
                    if (!response.status.isSuccess()) {
                        log.warn("Webhook non-2xx: scheduleId=${schedule.id} url=$url status=${response.status.value}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                log.warn("Webhook timeout: scheduleId=${schedule.id} url=$url")
            } catch (e: Exception) {
                log.warn("Webhook failed: scheduleId=${schedule.id} url=$url error=${e.message}")
            }
        }
    }

    private fun resolveUrl(schedule: Schedule): String? =
        schedule.webhookUrl?.takeIf { it.isNotBlank() }
            ?: config.defaultUrl?.takeIf { it.isNotBlank() }

    private fun buildPayload(schedule: Schedule, firedAt: Instant) = WebhookPayload(
        scheduleId = schedule.id,
        text = schedule.text,
        effect = schedule.effect.name,
        zoneId = schedule.zoneId,
        firedAt = firedAt.toString()
    )
}
```

### Pattern 2: WebhooksConfig data class

```kotlin
// Source: codebase — existing config/model/*.kt pattern
package com.anjo.config.model

data class WebhooksConfig(
    val defaultUrl: String?
)
```

**ApplicationConfig** gains a new field:

```kotlin
data class ApplicationConfig(
    val display: DisplayConfig,
    val api: ApiConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig,
    val webhooks: WebhooksConfig      // NEW
)
```

### Pattern 3: ConfigLoader extension

```kotlin
// Source: codebase — ConfigLoader.kt existing ${VAR:default} pattern
val webhooksConfig = WebhooksConfig(
    defaultUrl = config.propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }
)
```

**application.yaml addition:**

```yaml
webhooks:
  defaultUrl: "${WEBHOOK_DEFAULT_URL:}"
```

Same pattern applied to both `src/main/resources/application.yaml` and `src/test/resources/application.yaml` (test value left as empty string default — no real URL needed for tests).

### Pattern 4: DI wiring

```kotlin
// Source: codebase — DependencyInjection.kt existing provide{} pattern
val httpClient = HttpClient(CIO) {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        json()
    }
}
val webhookService = WebhookService(httpClient, appConfig.webhooks)
val schedulerService = SchedulerService(scheduleRepository, screenDriverService, effectRendererFactory, webhookService = webhookService)

// Inside dependencies {}
provide { httpClient }
provide { webhookService }
```

### Pattern 5: WebhookPayload serializable data class

```kotlin
@Serializable
data class WebhookPayload(
    val scheduleId: String,
    val text: String,
    val effect: String,
    val zoneId: String?,
    val firedAt: String
)
```

Located in `com.anjo.model` alongside `Schedule`, `HistoryRecord`, etc.

### Pattern 6: MockEngine unit test

```kotlin
// Source: codebase — Kotest FunSpec + io.mockk pattern; Ktor docs MockEngine
class WebhookServiceTest : FunSpec({

    test("should POST JSON payload to schedule webhookUrl on successful display") {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            capturedRequests.add(request)
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json() }
        }
        val service = WebhookService(client, WebhooksConfig(defaultUrl = null))
        val schedule = Schedule(
            id = "s1", text = "hello", triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL, webhookUrl = "http://localhost:9999/hook"
        )
        service.send(schedule, Instant.parse("2026-06-15T20:00:00Z"))

        // Allow coroutine to execute
        kotlinx.coroutines.delay(100)

        capturedRequests.size shouldBe 1
        capturedRequests[0].url.toString() shouldBe "http://localhost:9999/hook"
        capturedRequests[0].method shouldBe HttpMethod.Post
        capturedRequests[0].headers[HttpHeaders.ContentType] shouldContain "application/json"
        val body = capturedRequests[0].body.toByteArray().decodeToString()
        body shouldContain "\"scheduleId\":\"s1\""
        body shouldContain "\"text\":\"hello\""
        body shouldContain "\"effect\":\"SCROLL\""
        body shouldContain "\"firedAt\""
    }
})
```

> **Timing note:** `send()` is fire-and-forget. Tests need a short `delay()` or `runTest` with `advanceUntilIdle()` to let the internal coroutine execute. Prefer `runTest` + `advanceUntilIdle()` so the test does not depend on wall time. This requires injecting a `CoroutineScope` or using `TestScope` in `WebhookService` — see Pitfall 1 below.

### Anti-Patterns to Avoid

- **Catching `CancellationException` and swallowing it:** `withTimeout` throws `TimeoutCancellationException` (a subtype of `CancellationException`). Catching `Exception` misses this — always catch `TimeoutCancellationException` explicitly OR catch `Exception` before `CancellationException` is rethrown by the coroutine machinery. The coroutine is already inside `scope.launch {}` — `TimeoutCancellationException` does NOT cancel the outer `scope` because `withTimeout` throws it inside the launch block, not outside. Safe to catch as `Exception`.
- **Calling `send()` inside `scope.launch {}` in `SchedulerService`:** The `fire()` function is already a suspend function running inside a coroutine. `send()` is non-suspend and returns immediately — no additional launch is needed at the call site.
- **Sharing `SchedulerService.scope` for webhook dispatch:** Webhook IO must use `Dispatchers.IO`, not `Dispatchers.Default`. `SchedulerService.scope` uses `Dispatchers.Default` (only 4 threads on Pi 4). Webhook failures could interfere with the scheduler's `SupervisorJob` if scopes are shared.
- **Using `suspend fun send()`:** If `send()` is suspend, the caller (`fire()`) must `launch {}` separately to avoid blocking — defeating fire-and-forget. Keep it non-suspend.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP POST with JSON body | Manual `URLConnection` or socket code | `HttpClient(CIO)` + `setBody()` | Content-type negotiation, redirect handling, connection pooling, keep-alive |
| Timeout enforcement | `Thread.sleep()` or manual `Job.cancel()` after delay | `withTimeout(5_000)` | `withTimeout` is cooperative and integrates with structured concurrency; manual approach doesn't cancel blocking calls |
| Mock HTTP server in tests | Real HTTP server (`embeddedServer` on test port) | `HttpClient(MockEngine { ... })` | No port binding, deterministic, runs in same JVM process, no cleanup |
| JSON serialization of payload | `buildString { append("\"scheduleId\":") }` | `@Serializable data class` + `ContentNegotiation` | Type-safe, handles null fields, consistent with the rest of the API |

**Key insight:** The Ktor client is purpose-built for coroutine-aware HTTP; `MockEngine` eliminates the need for a real server in unit tests while testing the exact same code path used in production.

---

## Common Pitfalls

### Pitfall 1: `send()` fire-and-forget is hard to assert in tests

**What goes wrong:** `send()` returns immediately. The internal `scope.launch {}` may not have executed by the time the test's next line runs, so `capturedRequests` is empty and the assertion fails.

**Why it happens:** `fire-and-forget` by design — the coroutine is dispatched asynchronously.

**How to avoid:** Two options:

Option A — inject a test-controlled scope into `WebhookService` and use `runTest` + `advanceUntilIdle()`:

```kotlin
// WebhookService constructor gains optional scope param
class WebhookService(
    private val httpClient: HttpClient,
    private val config: WebhooksConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
)
// In tests:
runTest {
    val service = WebhookService(client, config, this)
    service.send(schedule, firedAt)
    advanceUntilIdle()
    // assert capturedRequests
}
```

Option B — use `delay(100)` after `send()` inside `runTest`. This works but is fragile on slow CI. Option A is preferred.

**Warning signs:** Test assertion `capturedRequests.size shouldBe 1` fails with size 0 even though production code looks correct.

### Pitfall 2: `WebhooksConfig` added to `ApplicationConfig` breaks ConfigLoader compilation

**What goes wrong:** `ApplicationConfig` is a data class. Adding a new `webhooks: WebhooksConfig` field without updating `ConfigLoader.loadConfig()` to pass it causes a compile error (too few arguments to constructor).

**Why it happens:** Kotlin data class constructors require all parameters.

**How to avoid:** Update `ApplicationConfig.kt`, `ConfigLoader.kt`, and both `application.yaml` files in the same commit. The test `application.yaml` needs the `webhooks:` block too (even if the value is empty).

**Warning signs:** Compile error `None of the following candidates is applicable` in ConfigLoader.

### Pitfall 3: Missing `ContentNegotiation` plugin on the `HttpClient` causes 415 Unsupported Media Type

**What goes wrong:** `setBody(payload)` without installing `ContentNegotiation` on the client sends the body as raw bytes with no `Content-Type` header. The receiver rejects it.

**Why it happens:** `HttpClient` plugins are independent of the server's plugins — `ContentNegotiation` must be installed on both server AND client separately.

**How to avoid:** Always install `ContentNegotiation { json() }` on the `HttpClient` instance used for JSON POST.

**Warning signs:** Receiver logs `415 Unsupported Media Type` or body arrives as `[B@...` (byte array toString).

### Pitfall 4: `SchedulerService` constructor change breaks `SchedulerServiceTest`

**What goes wrong:** Adding `webhookService: WebhookService` as a new constructor param to `SchedulerService` causes compilation failure in `SchedulerServiceTest` which constructs `SchedulerService(mockRepo, mockScreen, mockFactory, testScope)` positionally.

**Why it happens:** Positional argument constructors break when new params are added before default-valued params.

**How to avoid:** Either use a named + defaulted param (`webhookService: WebhookService = NoopWebhookService`) or pass a mock in all test call sites. D-18 specifies a stub — the cleanest approach is a named parameter with a default that does nothing, or an interface.

**Recommendation:** Make `webhookService` a named param with `null` default (nullable) or use a simple stub pattern (not an interface — overkill for one method). Provide a `NoopWebhookService` or make it `WebhookService? = null` and guard with `?.send()` in `fire()`. A nullable field avoids creating an extra class. Confirm with team preference.

**Warning signs:** `SchedulerServiceTest` fails to compile after the constructor change.

### Pitfall 5: `WebhookService` scope leaks in tests

**What goes wrong:** `WebhookService` creates `CoroutineScope(Dispatchers.IO + SupervisorJob())` as a field. In tests, if this scope is not cancelled after each test, launched coroutines keep running and interfere with the next test.

**Why it happens:** Leaked coroutines on `Dispatchers.IO` share the thread pool across test runs.

**How to avoid:** Use injected scope (Pitfall 1, Option A) so the test's scope is cancelled automatically by `runTest`. If using Option B (no injected scope), call `service.close()` (add a `close()` method that cancels the scope) in `afterEach`.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `kotlin.io.use {}` for HTTP (deprecated) | `HttpClient` with suspend functions | Ktor 1.0+ | Coroutine-native, no thread blocking |
| `OkHttp` as Ktor client engine | `CIO` (pure Kotlin) for server-side use | Ktor 2.0+ | No extra dependency, matches server engine |
| `HttpClient` constructed with `close()` per-request | Shared singleton `HttpClient` | Always | Connection pooling, lower overhead |

**Deprecated/outdated:**
- `HttpClient.get(url)` string shorthand: still works but `HttpClient.get { url(...) }` builder is more explicit — either is fine for this phase.
- `HttpClient.submitForm()`: for form POST, not JSON — do not use here.

---

## Code Examples

### WebhookPayload serializable model

```kotlin
// Source: codebase — com.anjo.model.HistoryRecord pattern
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val scheduleId: String,
    val text: String,
    val effect: String,
    val zoneId: String?,
    val firedAt: String
)
```

### SchedulerService.fire() integration point

```kotlin
// Source: codebase — SchedulerService.kt fire() function
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

### HttpClient construction in DI

```kotlin
// Source: codebase — DependencyInjection.kt pattern
val httpClient = HttpClient(CIO) {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        json()
    }
}
```

### SchedulerService test stub pattern (D-18)

```kotlin
// Source: codebase — SchedulerServiceTest.kt mockk pattern
val mockWebhook = mockk<WebhookService>(relaxed = true)

// After displayScheduled returns true:
verify { mockWebhook.send(any(), any()) }

// After displayScheduled returns false (SKIP_NEW):
verify(exactly = 0) { mockWebhook.send(any(), any()) }
```

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`, `ktor-client-mock` at version 3.5.0 are the correct artifact IDs on Maven Central | Standard Stack | Build fails — check actual artifact IDs at search.maven.org |
| A2 | `MockEngine` is in `ktor-client-mock` artifact | Standard Stack | Test compile fails — may need `ktor-client-test-host` or similar |
| A3 | `HttpClient(CIO)` is the preferred engine for server-side JVM outbound HTTP in Ktor 3.5.0 | Standard Stack | Minor — any engine works, CIO is lightest |
| A4 | `WebhookService` constructor with optional `scope` param (Pitfall 1 Option A) is the preferred testability approach | Common Pitfalls | Tests need `delay()` workaround instead — functional but less deterministic |

---

## Open Questions

1. **Should `WebhookService.send()` accept `null` as a no-op or should nullable webhook service be used at call site?**
   - What we know: D-09 says non-suspend `send()`; D-10 says registered as singleton; D-18 says stub in scheduler tests
   - What's unclear: Whether `SchedulerService` holds `WebhookService?` (nullable, nullable-safe call) or `WebhookService` (non-null, always wired)
   - Recommendation: Non-null `WebhookService` with internal no-op guard via `resolveUrl()` returning null — this matches D-05 ("skip silently if both null") and avoids nullable chaining at the call site

2. **Should `send()` take `(schedule: Schedule, firedAt: Instant)` or `(scheduleId, text, effect, zoneId, firedAt)` as discrete params?**
   - What we know: CONTEXT.md canonical refs say `webhookService.send(schedule, firedAt)` — Schedule object preferred
   - Recommendation: `send(schedule: Schedule, firedAt: Instant)` — Schedule is already available at call site

---

## Environment Availability

Step 2.6: SKIPPED — no new external runtime dependencies. The Ktor CIO client runs in-process on the JVM. No external daemon, port, or OS-level tool is required. The target Pi 4 already runs the Ktor server application.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (FunSpec + `should` convention) |
| Config file | `build.gradle.kts` — `useJUnitPlatform()` |
| Quick run command | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HOOK-01 | Webhook fires after successful display; 5s timeout enforced | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | Wave 0 |
| HOOK-01 | Webhook NOT fired on SKIP_NEW (displayScheduled returns false) | Integration (stub) | `./gradlew test --tests "com.anjo.service.SchedulerServiceTest"` | Exists — new test case needed |
| HOOK-02 | POST body contains scheduleId, text, effect, zoneId, firedAt | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | Wave 0 |
| HOOK-02 | Content-Type header is application/json | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | Wave 0 |
| HOOK-03 | Global fallback `WEBHOOK_DEFAULT_URL` used when schedule.webhookUrl is null | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | Wave 0 |
| HOOK-03 | DI smoke test resolves `WebhookService` | Integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | Exists — new assertion needed |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.service.WebhookServiceTest" --tests "com.anjo.service.SchedulerServiceTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green + JaCoCo ≥70% before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt` — covers HOOK-01, HOOK-02, HOOK-03
- [ ] New test case in `SchedulerServiceTest.kt` — `webhookService.send()` called on success, not called on SKIP_NEW (D-18)
- [ ] New assertion in `ApplicationTest.kt` — `get<WebhookService>()` resolves without error (D-10)

---

## Security Domain

> `security_enforcement` not set to false in config — section required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth header per D-14 (trusted home network) |
| V3 Session Management | no | Stateless outbound HTTP; no session |
| V4 Access Control | no | Outbound-only, no access decisions |
| V5 Input Validation | yes (partial) | `webhookUrl` stored in DB since Phase 7; URL format was validated on ingestion at schedule creation. No re-validation here — URL is trusted internal data. |
| V6 Cryptography | no | No crypto; no auth token per decision |

### Known Threat Patterns for Ktor Client / Outbound HTTP

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF (webhook URL points to internal service) | Tampering / Information Disclosure | Out of scope for home network; D-14 explicitly excludes auth. Note: if Phase 11+ introduces network-facing deployment, URL allowlisting should be revisited |
| Webhook URL stored as plaintext | Information Disclosure | H2/PostgreSQL on local Pi — acceptable for home network threat model |
| Slow webhook receiver causes resource exhaustion | Denial of Service | Mitigated by `withTimeout(5_000)` + `Dispatchers.IO` scope — scheduler is unaffected |

---

## Sources

### Primary (MEDIUM confidence — direct codebase inspection)
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — existing `fire()` structure and constructor pattern
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — `CoroutineScope(Dispatchers.IO + SupervisorJob())` fire-and-forget pattern
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — `provide {}` wiring pattern, constructor injection convention
- `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` — `propertyOrNull` + `${VAR:default}` config pattern
- `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` — data class structure for new `webhooks` field
- `gradle/ktor-libs.versions.toml` — confirmed Ktor version 3.5.0, existing catalog entry format
- `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — mockk + TestScope + advanceTimeBy pattern for scheduler tests
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — DI smoke test `getBlocking` assertion pattern
- `.planning/phases/10-webhooks/10-CONTEXT.md` — all locked decisions D-01 through D-18
- `.planning/STATE.md` — accumulated pitfalls and phase decisions

### Tertiary (LOW confidence — training knowledge, not verified this session)
- Ktor client artifact IDs and MockEngine API [ASSUMED]
- `ktor-client-content-negotiation` client plugin installation syntax [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Standard Stack: MEDIUM — libraries are well-known but artifact IDs not verified via official docs in this session
- Architecture: HIGH — derived entirely from existing codebase patterns
- Pitfalls: HIGH — derived from direct codebase analysis + Kotlin coroutines behavior
- Test patterns: HIGH — existing test suite provides complete blueprint

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 (Ktor 3.x is stable; 30-day window appropriate)
