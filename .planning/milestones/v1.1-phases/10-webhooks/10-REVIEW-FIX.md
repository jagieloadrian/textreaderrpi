---
phase: 10-webhooks
fixed_at: 2026-06-16T01:00:00Z
review_path: .planning/phases/10-webhooks/10-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 6
skipped: 0
status: all_fixed
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-06-16T01:00:00Z
**Source review:** .planning/phases/10-webhooks/10-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 6 (CR-01, CR-02, WR-01, WR-02, WR-03, WR-04)
- Fixed: 6
- Skipped: 0

## Fixed Issues

### CR-01: `pi4j = "4.0.0"` does not exist — build will fail

**Files modified:** `gradle/ktor-libs.versions.toml`
**Commit:** a6bdccd
**Applied fix:** Downgraded `pi4j = "4.0.0"` to `pi4j = "3.0.2"` (the current published release on Maven Central) in the version catalog.

---

### CR-02: `HttpClient` is never closed — resource leak on shutdown

**Files modified:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt`
**Commit:** b3b679a
**Applied fix:** Added `httpClient.close()` to the `ApplicationStopping` subscriber. Note: this intermediate commit was superseded by the WR-01/WR-02 commit which removed the shared `httpClient` from DI entirely (client closure now happens inside `WebhookService.stop()`).

---

### WR-01: `setBody(payload)` without a registered `ContentNegotiation` serializer will send a raw object

**Files modified:** `src/main/kotlin/com/anjo/service/WebhookService.kt`, `src/main/kotlin/com/anjo/di/DependencyInjection.kt`
**Commit:** ce2d188 (combined with WR-02)
**Applied fix:** Added a `companion object` with a `create(config: WebhooksConfig)` factory method to `WebhookService` that creates its own `HttpClient(CIO)` with `ContentNegotiation` installed. Updated `DependencyInjection.kt` to use `WebhookService.create(appConfig.webhooks)` instead of injecting the shared `httpClient`. The shared `httpClient` variable was removed from DI (and its DI provision) since the service is now self-contained. The primary constructor remains injectable for tests that pass a mock client.

---

### WR-02: `WebhookService` owns an unmanaged `CoroutineScope` with no shutdown path

**Files modified:** `src/main/kotlin/com/anjo/service/WebhookService.kt`, `src/main/kotlin/com/anjo/di/DependencyInjection.kt`
**Commit:** ce2d188 (combined with WR-01)
**Applied fix:** Added `stop()` method to `WebhookService` that cancels the coroutine scope via `scope.coroutineContext[Job]?.cancel()` and closes the `httpClient`. Updated `DependencyInjection.kt`'s `ApplicationStopping` handler to call `webhookService.stop()`, replacing the previous separate `httpClient.close()` call.

---

### WR-03: `WebhookServiceTest` uses `delay(200)` to synchronize async results — tests are timing-dependent

**Files modified:** `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt`
**Commit:** 55166c4
**Applied fix:** Removed the `beforeSpec` warmup block (delay(500) anti-pattern). Wrapped each test in `runTest { }` with `StandardTestDispatcher`. Each `WebhookService` instance is constructed with `this` (the `TestScope`) as the injected `scope`. Replaced all `delay(200)` calls with `advanceUntilIdle()` for deterministic coroutine completion. The `@OptIn(ExperimentalCoroutinesApi::class)` annotation is now properly used (required for `advanceUntilIdle()`), resolving IN-02 as a side effect. Removed the unused `delay` import.

---

### WR-04: `ConfigLoader` parses `brightness` as `Boolean` but silently ignores non-strict values

**Files modified:** `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt`
**Commit:** 9d9bf6b
**Applied fix:** Added `org.slf4j.LoggerFactory` import and a `private val log` field to the `ConfigLoader` object. Added `.also { if (it == null) log.warn(...) }` after `toBooleanStrictOrNull()` on the `brightness` property, so any non-strict value (e.g., `True`, `yes`) logs a warning before falling back to the default `true`.

---

## Skipped Issues

None — all findings were fixed.

---

_Fixed: 2026-06-16T01:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
