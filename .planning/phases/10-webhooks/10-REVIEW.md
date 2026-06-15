---
phase: 10-webhooks
reviewed: 2026-06-16T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - src/main/kotlin/com/anjo/service/WebhookService.kt
  - src/main/kotlin/com/anjo/model/WebhookPayload.kt
  - src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt
  - src/test/kotlin/com/anjo/service/WebhookServiceTest.kt
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
  - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
  - src/main/resources/application.yaml
  - src/test/resources/application.yaml
  - src/main/kotlin/com/anjo/service/SchedulerService.kt
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
  - src/test/kotlin/com/anjo/ApplicationTest.kt
findings:
  critical: 2
  warning: 4
  info: 2
  total: 8
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-16T00:00:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Phase 10 adds a `WebhookService` that fires an HTTP POST after each successful schedule display, wired through `SchedulerService` and configured via `WebhooksConfig`. The core dispatch logic is clean and the happy-path test coverage is solid. Two blockers surface: a non-existent Pi4J version declared in the version catalog that will cause every build to fail, and an `HttpClient` resource that is never closed at shutdown. Four warnings cover a serialization gap that silently sends a raw object, an internal coroutine scope with no shutdown handle, test assertions that use time-based `delay` rather than deterministic control, and a pre-existing `ConfigLoader` type mismatch on `brightness`. Two info items flag minor quality issues in the test suite.

---

## Critical Issues

### CR-01: `pi4j = "4.0.0"` does not exist — build will fail

**File:** `gradle/ktor-libs.versions.toml:10`

**Issue:** Pi4J's latest published release on Maven Central is `3.0.2`. Version `4.0.0` does not exist. Any fresh `gradle build` (including CI) will fail to resolve `com.pi4j:pi4j-core:4.0.0`, `pi4j-ktx:4.0.0`, and the three plugin artifacts. This is a hard build blocker introduced (or left) in this phase's version catalog changes.

**Fix:** Downgrade to the current release:
```toml
pi4j = "3.0.2"
```

---

### CR-02: `HttpClient` is never closed — resource leak on shutdown

**File:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt:62-65`

**Issue:** The CIO `HttpClient` created at line 53 is not closed in the `ApplicationStopping` handler. Ktor's `HttpClient` holds a connection pool and background threads; omitting `close()` leaks those resources on every graceful restart. The `WebhookService` exposes no `close()` method of its own, so the only site to close the client is the shutdown hook in `configureDI`.

**Fix:** Add `httpClient.close()` to the `ApplicationStopping` subscriber:
```kotlin
monitor.subscribe(ApplicationStopping) {
    schedulerService.stop()
    screenDriverService.stop()
    httpClient.close()
}
```

---

## Warnings

### WR-01: `setBody(payload)` without a registered `ContentNegotiation` serializer will send a raw object

**File:** `src/main/kotlin/com/anjo/service/WebhookService.kt:35-38`

**Issue:** `WebhookService` receives an `HttpClient` as a constructor argument and calls `setBody(payload)` with a `WebhookPayload` data class. Whether the body is serialized as JSON depends entirely on the `ContentNegotiation` plugin being installed on the injected client. The production client in `DependencyInjection` does install it (line 54-56), but:

1. Nothing in `WebhookService`'s contract or constructor enforces this requirement — a caller injecting a bare `HttpClient` would silently send the object's `toString()` as the body instead of JSON.
2. If the client is replaced or reconfigured in the future the failure mode is silent: the server receives malformed data, not a client-side exception.

**Fix:** Either install `ContentNegotiation` inside `WebhookService` itself (making the service self-contained), or document and enforce the requirement at construction time with an explicit check or a factory method:
```kotlin
companion object {
    fun create(config: WebhooksConfig, scope: CoroutineScope = ...): WebhookService {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        return WebhookService(client, config, scope)
    }
}
```
This also removes the need to expose `HttpClient` as a DI binding.

---

### WR-02: `WebhookService` owns an unmanaged `CoroutineScope` with no shutdown path

**File:** `src/main/kotlin/com/anjo/service/WebhookService.kt:25`

**Issue:** When `scope` is not injected, `WebhookService` creates `CoroutineScope(Dispatchers.IO + SupervisorJob())` and never cancels it. Any in-flight webhook coroutines will continue executing after the application stops — or worse, will throw on a closed `HttpClient` (see CR-02) and silently swallow the error in the catch block. There is also no way for `DependencyInjection` to cancel this scope during the `ApplicationStopping` hook because `WebhookService` exposes no `stop()` / `close()` method.

**Fix:** Add a `stop()` method and call it during shutdown:
```kotlin
fun stop() {
    scope.coroutineContext[Job]?.cancel()
}
```
Then in `DependencyInjection`:
```kotlin
monitor.subscribe(ApplicationStopping) {
    schedulerService.stop()
    webhookService.stop()
    screenDriverService.stop()
    httpClient.close()
}
```

---

### WR-03: `WebhookServiceTest` uses `delay(200)` to synchronize async results — tests are timing-dependent

**File:** `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt:59,84,100,117,134,151,167`

**Issue:** Every test fires `WebhookService.send(...)` and then calls `delay(200)` hoping the launched coroutine completes in time. This is a real-time wall-clock wait: if the test machine is under load the 200 ms budget may expire before the coroutine finishes, producing a false failure. The `beforeSpec` warmup at line 44-45 is an anti-pattern for the same reason (delay(500) to warm up the JVM/coroutine runtime). The `@OptIn(ExperimentalCoroutinesApi::class)` annotation at line 24 is present but unused — the tests do not use `TestCoroutineScheduler` or `runTest`.

**Fix:** Inject the `CoroutineScope` into `WebhookService` (it already supports this via the default parameter) and pass a `TestScope` backed by `StandardTestDispatcher` in tests. Use `advanceUntilIdle()` or `runCurrent()` instead of `delay(N)`:
```kotlin
runTest {
    val service = WebhookService(client, config, this)
    service.send(schedule, firedAt)
    advanceUntilIdle()
    capturedData.size shouldBe 1
}
```
This eliminates the race condition and the warmup block entirely.

---

### WR-04: `ConfigLoader` parses `brightness` as `Boolean` but calls `toBooleanStrictOrNull()` on a raw YAML value that is already a boolean

**File:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt:23`

**Issue:** `application.yaml` line 18 sets `brightness: true` (an unquoted YAML boolean). Ktor's YAML config reader materialises this as the string `"true"`. `toBooleanStrictOrNull()` accepts only exactly `"true"` or `"false"` (case-sensitive), so the parsing technically works for the literal `true` written in YAML — but it silently falls back to the hardcoded default `true` for any value other than the exact strings. This is a pre-existing defect that this phase did not introduce, but `ConfigLoader` was modified in this phase to add the `webhooks` block, making it an in-scope file. A malformed value like `brightness: True` or `brightness: yes` would silently be treated as `true` rather than rejected or logged.

**Fix:** Add a warning log when `toBooleanStrictOrNull()` returns null to surface misconfiguration:
```kotlin
brightness = config.propertyOrNull("display.max7219.brightness")
    ?.getString()
    ?.toBooleanStrictOrNull()
    .also { if (it == null) log.warn("display.max7219.brightness value is not a strict boolean; defaulting to true") }
    ?: true,
```

---

## Info

### IN-01: Duplicate assertion in `ApplicationTest` — `shouldBe OK` asserted twice on same value

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:48-49`

**Issue:** The "should serve static assets" test asserts `response.status shouldBe HttpStatusCode.OK` on consecutive lines 48 and 49. The second assertion is a no-op copy-paste artifact; it tests nothing additional and obscures intent.

**Fix:** Remove line 49:
```kotlin
val response = client.get("/")
response.status shouldBe HttpStatusCode.OK
```

---

### IN-02: `@OptIn(ExperimentalCoroutinesApi::class)` on `WebhookServiceTest` is unused

**File:** `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt:24`

**Issue:** The annotation is present but no coroutines-test experimental API is used in the file. It will generate a compiler warning ("opt-in annotation is not required here") and signals an incomplete migration toward deterministic test control (see WR-03).

**Fix:** Remove the annotation unless WR-03 is addressed by migrating to `runTest`, in which case it becomes needed and this item is resolved automatically.

---

_Reviewed: 2026-06-16T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
