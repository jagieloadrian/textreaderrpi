# Phase 10: Webhooks - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

When `SchedulerService.fire()` succeeds (display rendered), send an HTTP POST to the resolved webhook URL — per-schedule `webhookUrl` if set, otherwise the global `WEBHOOK_DEFAULT_URL` from AppConfig. Fire-and-forget with 5s timeout; failures are logged and discarded. Payload: text, effect, scheduleId, zoneId, timestamp. Webhooks apply to scheduled triggers only (ONESHOT / RECURRING / CRON) — not to `POST /api/v1/text` immediate display. No retry, no DB writes for webhook state.

</domain>

<decisions>
## Implementation Decisions

### Trigger Condition

- **D-01:** Webhook fires only when `screenService.displayScheduled()` returns `true`. Consistent with Phase 9 history recording rule (D-02): only real display events are reported.
- **D-02:** Webhook does NOT fire on SKIP_NEW (display skipped) or display failure. Matches the "success-only" pattern established for history recording.
- **D-03:** Webhooks are scoped to `SchedulerService.fire()` only. `POST /api/v1/text` immediate display has no schedule row and no webhookUrl — out of scope.

### URL Resolution

- **D-04:** URL resolution priority: use `schedule.webhookUrl` if non-null; otherwise use `AppConfig.webhooks.defaultUrl`. One POST per fire — never both.
- **D-05:** If both are null/empty, skip webhook silently (no log, no error).
- **D-06:** Global fallback configured via `webhooks.defaultUrl: ${WEBHOOK_DEFAULT_URL:}` in `application.yaml`. New `WebhooksConfig` data class added to AppConfig. Consistent with existing `${VAR:default}` pattern for all 25 settings.

### WebhookService Design

- **D-07:** Webhook logic extracted to a dedicated `WebhookService` class (not inline in `SchedulerService`). Owns the `HttpClient`, URL resolution, payload construction, and `withTimeout(5_000)`.
- **D-08:** `WebhookService(httpClient: HttpClient, config: WebhooksConfig)` — `HttpClient` injected as constructor param to allow `MockEngine` in tests. Production DI wires `HttpClient(CIO)`.
- **D-09:** `WebhookService` owns its own `CoroutineScope(Dispatchers.IO + SupervisorJob())`. `send()` is a non-suspend function — returns immediately (fire-and-forget). Caller (`SchedulerService.fire()`) calls it synchronously.
- **D-10:** `WebhookService` registered as singleton via `provide {}` in `DependencyInjection.kt`. Injected into `SchedulerService` as a new constructor param alongside `effectFactory`. DI smoke test in `ApplicationTest.kt` updated to resolve `WebhookService`.

### Webhook Payload

- **D-11:** JSON payload structure:
  ```json
  {
    "scheduleId": "...",
    "text": "...",
    "effect": "SCROLL",
    "zoneId": null,
    "firedAt": "2026-06-15T20:00:00.000Z"
  }
  ```
- **D-12:** `firedAt` is `Instant.now().toString()` at time of successful display. Matches existing timestamp format in `SchedulesTable` (ISO-8601 VARCHAR).
- **D-13:** `zoneId` is `schedule.zoneId` (nullable). Included in payload even when null — receiver can distinguish "no zone" from missing field.
- **D-14:** HTTP POST with `Content-Type: application/json`. No auth header — home-network use case, consistent with out-of-scope auth decision.

### Failure Handling

- **D-15:** On timeout (`TimeoutCancellationException`) or non-2xx response: `log.warn(...)` with scheduleId, resolved URL, and HTTP status code. No retry, no schedule state change, no DB write.
- **D-16:** Exception is caught inside `WebhookService.send()` scope — never propagates to `SchedulerService`. Webhook failure must not affect the display or scheduling paths.

### Testing

- **D-17:** `WebhookService` unit-tested with `HttpClient(MockEngine { ... })`. Tests assert: request URL, HTTP method, Content-Type header, JSON body fields (scheduleId, text, effect, zoneId, firedAt).
- **D-18:** `SchedulerService` integration test verifies `WebhookService.send()` is called after `displayScheduled()` returns `true`, and NOT called when it returns `false` (SKIP_NEW path). Use a stub/fake `WebhookService` in those tests.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Integration Points
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — Add `WebhookService` constructor param; call `webhookService.send(schedule, firedAt)` inside `fire()` after `displayScheduled()` returns `true`
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — Register `WebhookService` and `HttpClient(CIO)` via `provide {}`; inject into `SchedulerService`
- `src/main/kotlin/com/anjo/config/loader/` — Add `WebhooksConfig` data class; wire `webhooks.defaultUrl` in `AppConfig`
- `src/main/resources/application.yaml` — Add `webhooks.defaultUrl: ${WEBHOOK_DEFAULT_URL:}` block
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — Update DI smoke test to resolve `WebhookService` (D-10)

### Pattern Files (follow exactly)
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — Pattern for injected constructor params and `CoroutineScope(Dispatchers.IO + SupervisorJob())` usage
- `src/main/kotlin/com/anjo/config/loader/` — Config data class pattern for new `WebhooksConfig`
- `src/main/resources/application.yaml` — `${VAR:default}` env var pattern for all 25 existing settings

### Requirements
- `.planning/REQUIREMENTS.md` §Webhooks — HOOK-01, HOOK-02, HOOK-03 (3 requirements, all must be satisfied)

### Stack Decisions (from STATE.md)
- Ktor client: `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation` — all version 3.5.0 (matches Ktor server version, zero conflicts)
- Scope: `CoroutineScope(Dispatchers.IO + SupervisorJob())` — does not block `Dispatchers.Default` (only 4 threads on Pi 4)
- Timeout: `withTimeout(5_000)` — 5 second hard limit per HOOK-01

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SchedulerService.scope` — existing `CoroutineScope(Dispatchers.Default + SupervisorJob())` for the scheduler loop; `WebhookService` gets its own separate IO scope (D-09)
- `AppConfig` + `${VAR:default}` config pattern — add `webhooks.defaultUrl` the same way as existing 25 settings
- `Schedule.webhookUrl: String?` — already persisted in DB since Phase 7 (SCHED-04); no schema migration needed
- `Schedule.zoneId: String?` — already persisted; included in webhook payload (D-13)

### Established Patterns
- Constructor injection for services — `SchedulerService(repository, screenService, effectFactory, scope)` pattern; add `webhookService` as new param
- `CoroutineScope(Dispatchers.IO + SupervisorJob())` for background IO — used by fire-and-forget display path in `ScreenDriverService` post Phase 9
- Kotest `should` convention — all test files; new `WebhookServiceTest` follows same style
- No comments in code files (project rule)
- Validation stays in route handlers only, never in service or repository

### Integration Points
- `SchedulerService.fire()` — after `screenService.displayScheduled(...)` returns `true`, call `webhookService.send(schedule, Instant.now())`
- `DependencyInjection.kt` `configureDI()` — new `provide<HttpClient> {}` binding + `provide<WebhookService> {}`
- `ApplicationTest.kt` DI smoke test — add `get<WebhookService>()` resolve assertion

</code_context>

<specifics>
## Specific Ideas

- `WebhookService.send()` is a regular (non-suspend) function — call site in `fire()` requires no coroutine ceremony
- JSON payload field names: `scheduleId`, `text`, `effect`, `zoneId`, `firedAt` — snake-case consistent with existing API responses
- `firedAt` timestamp at point of successful display (not at schedule trigger time) — most accurate for receivers
- `zoneId: null` included explicitly in payload — receiver can distinguish "no zone" from missing field

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 10-Webhooks*
*Context gathered: 2026-06-15*
