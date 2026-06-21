---
phase: 10-webhooks
plan: "02"
subsystem: webhooks
tags: [scheduler, webhooks, di, integration-test]
dependency_graph:
  requires: [10-01]
  provides: [SchedulerService+webhook, WebhookService DI binding, HttpClient DI binding]
  affects: [SchedulerService, DependencyInjection, SchedulerServiceTest, ApplicationTest]
tech_stack:
  added: []
  patterns: [named-param nullable-default for backward compatibility, fire-and-forget webhook on success-only path, DI singleton registration]
key_files:
  created: []
  modified:
    - src/main/kotlin/com/anjo/service/SchedulerService.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt
    - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
    - src/test/kotlin/com/anjo/ApplicationTest.kt
decisions:
  - webhookService added as 5th named nullable param after scope so existing positional call sites in tests compile unchanged (Pitfall 4)
  - webhookService?.send() placed inside existing fire() try block so any synchronous throw is contained by existing catch without failing the display (D-16)
  - httpClient and webhookService registered as provide{} singletons before schedulerService in dependencies block
metrics:
  duration: 4 minutes
  completed: "2026-06-16"
  tasks: 2
  files: 4
---

# Phase 10 Plan 02: Webhook Scheduler Integration Summary

Wire the proven WebhookService into SchedulerService.fire() on the success-only path and register HttpClient(CIO) + WebhookService as DI singletons, proven by 2 new SchedulerServiceTest integration cases and an ApplicationTest DI smoke assertion.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wire WebhookService into SchedulerService and DI graph | 0115943 | 2 files |
| 2 | Integration tests: send on display=true, skip on display=false, DI smoke | 39799ea | 2 files |

## What Was Built

**SchedulerService changes** — New 5th constructor param `private val webhookService: WebhookService? = null` added after `scope` with a named nullable default. Inside `fire()`, the `displayScheduled(...)` return value is captured into `val displayed`, and `webhookService?.send(schedule, Instant.now())` is called inside the existing `try` block when `displayed == true`. This satisfies HOOK-01 (fires on success), D-01 (success-only), and D-02 (not on skip/failure). The webhook call is inside the existing `catch (e: Exception)` so any synchronous throw is contained.

**DependencyInjection changes** — Added imports for `HttpClient`, `CIO`, `ContentNegotiation`, `json`, and `WebhookService`. Before `schedulerService` construction: `val httpClient = HttpClient(CIO) { install(ContentNegotiation) { json() } }` and `val webhookService = WebhookService(httpClient, appConfig.webhooks)`. `SchedulerService` construction updated to pass `webhookService = webhookService` as a named arg. Both `httpClient` and `webhookService` registered via `provide {}` in the dependencies block (D-10).

**SchedulerServiceTest changes** — Added `val mockWebhook = mockk<WebhookService>(relaxed = true)` alongside existing mocks; included in `clearMocks(...)`. Two new tests:
- `"should send webhook after schedule fires successfully"`: ONESHOT fires after 60s with `displayScheduled` returning true (default stub); asserts `coVerify(exactly = 1) { mockWebhook.send(schedule, any()) }` (D-01, D-18)
- `"should not send webhook when display is skipped or fails"`: overrides stub with `returns false`; asserts `coVerify(exactly = 0) { mockWebhook.send(any(), any()) }` (D-02, D-18)

**ApplicationTest changes** — Added `import com.anjo.service.WebhookService` and `deps.getBlocking<WebhookService>(DependencyKey<WebhookService>()) shouldNotBeNull {}` inside the existing DI smoke test, proving the full DI graph resolves WebhookService in a live application context (D-10, T-10-07 mitigation).

## Deviations from Plan

None — plan executed exactly as written.

## Verification

- `./gradlew compileKotlin compileTestKotlin` — exits 0
- `./gradlew test --tests "com.anjo.service.SchedulerServiceTest"` — 13 tests, 0 failures (11 original + 2 new)
- `./gradlew test --tests "com.anjo.ApplicationTest"` — 6 tests, 0 failures
- `./gradlew test` (full suite) — BUILD SUCCESSFUL, JaCoCo coverage gate passed
- No comments in any modified Kotlin source file

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. All threats in the plan's STRIDE register are mitigated as designed (T-10-04: send() is non-suspend fire-and-forget; T-10-05: inside existing try/catch; T-10-07: ApplicationTest smoke assertion).

## Self-Check: PASSED

All modified files verified on disk. Both task commits (0115943, 39799ea) confirmed in git log.
