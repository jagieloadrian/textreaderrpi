---
phase: 10-webhooks
plan: "01"
subsystem: webhooks
tags: [ktor-client, webhooks, tdd, config]
dependency_graph:
  requires: []
  provides: [WebhookService, WebhookPayload, WebhooksConfig]
  affects: [ApplicationConfig, ConfigLoader, application.yaml]
tech_stack:
  added: [ktor-client-core, ktor-client-cio, ktor-client-content-negotiation, ktor-client-mock]
  patterns: [fire-and-forget, MockEngine unit tests, TDD RED/GREEN]
key_files:
  created:
    - src/main/kotlin/com/anjo/service/WebhookService.kt
    - src/main/kotlin/com/anjo/model/WebhookPayload.kt
    - src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt
    - src/test/kotlin/com/anjo/service/WebhookServiceTest.kt
  modified:
    - gradle/ktor-libs.versions.toml
    - build.gradle.kts
    - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
    - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
decisions:
  - Ktor ContentNegotiation client plugin sets Content-Type on body.contentType not request.headers; test assertion adjusted to use capturedData[0].body.contentType
  - Tests use Kotest FunSpec without runTest + real delay(200ms) due to withTimeout(5_000) virtual clock conflict with TestScope schedulers
  - beforeSpec warmup warms JVM IO thread pool before first test to ensure consistent timing
  - header(HttpHeaders.ContentType, ContentType.Application.Json) required to tell ContentNegotiation which serializer to use for setBody()
metrics:
  duration: 22 minutes
  completed: "2026-06-16"
  tasks: 2
  files: 10
---

# Phase 10 Plan 01: Webhook Service Layer Summary

Ktor HTTP client stack + fire-and-forget WebhookService with 5s timeout, URL resolution priority (per-schedule over global fallback), silent skip on null URLs, and 6 MockEngine unit tests proving HOOK-01/02/03 requirements.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add Ktor client deps + webhooks config layer | 2934a12 | 7 files |
| RED | WebhookServiceTest failing (TDD RED gate) | 9a3753c | 1 file |
| 2 | WebhookPayload + WebhookService GREEN | 244f3df | 3 files |

## What Was Built

**WebhooksConfig** — plain `data class WebhooksConfig(val defaultUrl: String?)` in `com.anjo.config.model`, wired into `ApplicationConfig` and `ConfigLoader` via `propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }`.

**WebhookPayload** — `@Serializable data class` with fields `scheduleId: String`, `text: String`, `effect: String`, `zoneId: String?`, `firedAt: String`. No default values.

**WebhookService** — fire-and-forget HTTP POST service. `send()` is non-suspend; internally launches on `CoroutineScope(Dispatchers.IO + SupervisorJob())`. Inside the launch: `withTimeout(5_000)` wraps the `httpClient.post()` call. `TimeoutCancellationException` caught before generic `Exception`. All exceptions produce `log.warn` and are never propagated.

**URL resolution** — `schedule.webhookUrl?.takeIf { isNotBlank() } ?: config.defaultUrl?.takeIf { isNotBlank() }`. If null: return before launch (D-05 silent skip).

**Build** — Four `ktor-client-*` entries added to `gradle/ktor-libs.versions.toml` at `version.ref = "ktor"` (3.5.0). Three `implementation` + one `testImplementation` in `build.gradle.kts`.

**Config** — `webhooks.defaultUrl: "${WEBHOOK_DEFAULT_URL:}"` appended to `src/main/resources/application.yaml`. Plain `defaultUrl: ""` in test yaml.

**Tests** — 6 test cases in `WebhookServiceTest` (Kotest FunSpec):
- HOOK-01 + HOOK-02: POST to schedule.webhookUrl, correct body fields, Content-Type application/json
- HOOK-02 zoneId: payload includes zoneId when set and null field when absent
- HOOK-03: POST to config.defaultUrl when schedule.webhookUrl is null
- D-04: schedule.webhookUrl takes priority over config.defaultUrl
- D-05: zero requests when both URLs null
- D-16: no throw on 500 response, request still attempted

## TDD Gate Compliance

- RED commit: `9a3753c` — `test(10-01)`: compile-fail on `Unresolved reference: WebhookService`
- GREEN commit: `244f3df` — `feat(10-01)`: all 6 tests pass

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Ktor ContentNegotiation sets Content-Type on body.contentType, not request headers**
- **Found during:** Task 2 (GREEN implementation)
- **Issue:** Test assertion `capturedData[0].headers[HttpHeaders.ContentType] shouldContain "application/json"` always returned null — ContentNegotiation in Ktor 3.x sets Content-Type as `OutgoingContent.contentType` on the serialized body, not as a raw HTTP header
- **Fix:** Changed assertion to `capturedData[0].body.contentType.toString() shouldContain "application/json"`; added explicit `header(HttpHeaders.ContentType, ContentType.Application.Json)` call in WebhookService to tell ContentNegotiation which serializer to use
- **Files modified:** WebhookService.kt, WebhookServiceTest.kt

**2. [Rule 1 - Bug] Pitfall 1 Option A (TestScope injection + advanceUntilIdle) incompatible with withTimeout(5_000)**
- **Found during:** Task 2 (GREEN implementation)
- **Issue:** `withTimeout(5_000)` uses the TestScope's virtual clock; `advanceUntilIdle()` advances virtual time past 5000ms before MockEngine can respond — timeout fires first. Affected tests 1-3 on cold JVM, later tests on warm JVM
- **Fix:** Used Kotest's native suspend test body (no `runTest`) with real `delay(200)` from `kotlinx.coroutines`. Added `beforeSpec` warmup to pre-initialize `Dispatchers.IO` thread pool. Service uses default `CoroutineScope(Dispatchers.IO + SupervisorJob())` (not injected scope)
- **Files modified:** WebhookServiceTest.kt

## Verification

- `./gradlew compileKotlin compileTestKotlin` — exits 0
- `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` — 6 tests, 0 failures (JaCoCo skipped on single-class run)
- `./gradlew test` (full suite) — BUILD SUCCESSFUL, JaCoCo coverage gate passed
- No comments in any Kotlin source file

## Known Stubs

None — all implementations are complete. `WebhooksConfig.defaultUrl` defaults to null (env var absent) which is intentional behavior per D-05.

## Self-Check: PASSED

All created files exist on disk. All task commits (2934a12, 9a3753c, 244f3df) verified in git log.
