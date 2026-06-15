# Phase 10: Webhooks - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-15
**Phase:** 10-Webhooks
**Areas discussed:** Trigger condition, Fallback URL semantics, WebhookService extraction, Failure handling

---

## Trigger Condition

| Option | Description | Selected |
|--------|-------------|----------|
| Success only | Webhook fires only when displayScheduled() returns true. Consistent with Phase 9 history recording (D-02). | ✓ |
| Always on schedule trigger | Webhook fires regardless of display outcome (SKIP_NEW, failure). | |
| You decide | Claude picks. | |

**User's choice:** Success only
**Notes:** Explicitly matched to Phase 9 history recording rule — only real display events get reported.

---

## Fallback URL Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Per-schedule overrides global | Use schedule.webhookUrl; fall back to WEBHOOK_DEFAULT_URL when null. One POST per fire. | ✓ |
| Both fire in parallel | When both are set, send two POSTs — per-schedule AND global. | |
| You decide | Claude picks. | |

**User's choice:** Per-schedule overrides global

Follow-up — config location for WEBHOOK_DEFAULT_URL:

| Option | Description | Selected |
|--------|-------------|----------|
| AppConfig webhook block | Add webhooks.defaultUrl with ${WEBHOOK_DEFAULT_URL:} in application.yaml. Consistent with 25 existing settings. | ✓ |
| Read directly from env var at call site | System.getenv("WEBHOOK_DEFAULT_URL") inline. Skips AppConfig. | |
| You decide | Claude picks. | |

**User's choice:** AppConfig webhook block

Follow-up — HTTP method and auth:

| Option | Description | Selected |
|--------|-------------|----------|
| POST + application/json, no auth | Plain JSON POST, no auth header. Home-network scope, consistent with out-of-scope auth decision. | ✓ |
| POST + application/json + optional Bearer token | Add WEBHOOK_AUTH_TOKEN env var. | |
| You decide | Claude picks. | |

**User's choice:** POST + application/json, no auth

---

## WebhookService Extraction

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated WebhookService | WebhookService owns HttpClient, URL resolution, payload build, withTimeout. Testable with MockEngine. | ✓ |
| Inline in SchedulerService.fire() | Private suspendWebhook() function. Fewer files but HttpClient becomes extra SchedulerService param. | |

**User's choice:** Dedicated WebhookService

Follow-up — IOScope ownership:

| Option | Description | Selected |
|--------|-------------|----------|
| WebhookService uses its own IOScope | CoroutineScope(Dispatchers.IO + SupervisorJob()) in WebhookService. send() returns immediately. | ✓ |
| Caller passes scope | SchedulerService passes its scope to WebhookService.send(). | |
| You decide | Claude picks. | |

**User's choice:** WebhookService uses its own IOScope

Follow-up — DI wiring:

| Option | Description | Selected |
|--------|-------------|----------|
| provide{} in DependencyInjection.kt, injected into SchedulerService | Singleton. DI smoke test updated. Same pattern as effectFactory. | ✓ |
| Created inline inside SchedulerService | Avoids DI change but breaks testability. | |

**User's choice:** provide{} in DependencyInjection.kt

---

## Failure Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Log and ignore | catch exception, log.warn(...) with scheduleId + URL + status code. No retry, no DB write. | ✓ |
| Log + write schedule WARNING state | Non-2xx/timeout sets lastWebhookError in DB. Requires new column. | |
| You decide | Claude picks. | |

**User's choice:** Log and ignore

Follow-up — webhook scope (immediate vs scheduled):

| Option | Description | Selected |
|--------|-------------|----------|
| Scheduled only | Webhooks keyed to Schedule entity. POST /api/v1/text has no schedule row. | ✓ |
| Both immediate and scheduled | Would require webhookUrl param on POST /api/v1/text. Scope expansion. | |

**User's choice:** Scheduled only

Follow-up — test strategy:

| Option | Description | Selected |
|--------|-------------|----------|
| Ktor MockEngine | HttpClient injected as constructor param. Tests use MockEngine. Assert URL, method, JSON body. | ✓ |
| Integration test with embedded HTTP server | Lightweight Ktor server as webhook receiver. Heavier setup, overkill. | |
| You decide | Claude picks. | |

**User's choice:** Ktor MockEngine

---

## Claude's Discretion

None — all decisions explicitly made by user.

## Deferred Ideas

None — discussion stayed within phase scope.
