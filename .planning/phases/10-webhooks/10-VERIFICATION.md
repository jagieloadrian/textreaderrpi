---
phase: 10-webhooks
verified: 2026-06-16T00:00:00Z
status: gaps_found
score: 7/8 must-haves verified
overrides_applied: 0
gaps:
  - truth: "Webhook delivery status is visible in the history record for the corresponding display event (SC-4)"
    status: failed
    reason: "HistoryTable has no webhook-related column (no webhookStatus, webhookUrl, or delivery field). HistoryRecord model is also unchanged. No later phase in ROADMAP.md addresses this. The success criterion exists only in Phase 10 and is not implemented anywhere in the codebase."
    artifacts:
      - path: "src/main/kotlin/com/anjo/db/HistoryTable.kt"
        issue: "Table schema contains only id, text, effect, displaySource, scheduleId, zoneId, displayedAt — no webhook delivery field"
      - path: "src/main/kotlin/com/anjo/model/HistoryRecord.kt"
        issue: "Model does not include a webhook delivery status field"
      - path: "src/main/kotlin/com/anjo/db/HistoryRepository.kt"
        issue: "insert() does not write webhook status; findPaginated() does not return it"
    missing:
      - "Add webhookStatus (or webhookDelivered: Boolean?) column to HistoryTable and Flyway migration"
      - "Add webhookStatus field to HistoryRecord model"
      - "Update HistoryRepository.insert() to accept and persist webhook delivery outcome"
      - "Update SchedulerService.fire() to capture the result of webhookService?.send() and pass it to HistoryRepository (requires send() to return a result or use a callback)"
      - "Surface webhookStatus in GET /api/v1/history JSON response and /history HTML page"
---

# Phase 10: Webhooks Verification Report

**Phase Goal:** When a schedule fires, an HTTP POST is sent to a configured URL as a fire-and-forget notification
**Verified:** 2026-06-16
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | After a schedule fires, a test HTTP listener receives a POST within 5s containing text, effect, scheduleId, zoneId, and timestamp (SC-1) | VERIFIED | WebhookService.send() launches POST inside withTimeout(5_000); WebhookPayload contains all five fields; WebhookServiceTest Test 1 asserts all body fields and 5s timeout does not block dispatch |
| 2 | A webhook that times out does not block or slow down display or scheduler loop (SC-2) | VERIFIED | send() is non-suspend (`fun send(`, no `suspend` keyword at line 29 of WebhookService.kt); launched on own CoroutineScope(Dispatchers.IO + SupervisorJob()); TimeoutCancellationException caught inside the launch; SchedulerServiceTest confirms fire() still returns boolean after send() |
| 3 | Setting WEBHOOK_DEFAULT_URL env var causes all schedules without explicit webhookUrl to POST to that fallback URL (SC-3 / HOOK-03) | VERIFIED | application.yaml has `defaultUrl: "${WEBHOOK_DEFAULT_URL:}"` at line 82; ConfigLoader uses propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }; WebhookServiceTest Test 3 asserts POST goes to config.defaultUrl when schedule.webhookUrl is null |
| 4 | Webhook delivery status is visible in the history record for the corresponding display event (SC-4) | FAILED | HistoryTable has no webhook column; HistoryRecord model is unchanged from Phase 9; HistoryRepository.insert() does not accept or persist webhook status; no later phase in ROADMAP.md covers this |
| 5 | WebhookService POSTs a JSON payload to schedule.webhookUrl when set (HOOK-01) | VERIFIED | resolveUrl() returns schedule.webhookUrl?.takeIf { isNotBlank() } first (line 52 WebhookService.kt); wired through SchedulerService.fire() at line 178; SchedulerServiceTest "should send webhook after schedule fires successfully" coVerify(exactly=1) |
| 6 | POST body contains scheduleId, text, effect, zoneId, firedAt with Content-Type application/json (HOOK-02) | VERIFIED | WebhookPayload @Serializable data class has all five fields; WebhookService sets header(HttpHeaders.ContentType, ContentType.Application.Json); WebhookServiceTest asserts body contains all fields and body.contentType contains "application/json" |
| 7 | WebhookService POSTs to AppConfig.webhooks.defaultUrl when schedule.webhookUrl is null/blank (HOOK-03) | VERIFIED | resolveUrl() falls through to config.defaultUrl?.takeIf { isNotBlank() } when webhookUrl is null (line 53); WebhookServiceTest Test 3 proves this path; SchedulerServiceTest passes webhookService through DI chain |
| 8 | When display is skipped or fails, WebhookService.send() is never invoked (D-01/D-02) | VERIFIED | SchedulerService.fire() guards send() with `if (displayed)` at line 177; SchedulerServiceTest "should not send webhook when display is skipped or fails" sets displayScheduled returns false and asserts coVerify(exactly=0) |

**Score:** 7/8 must-haves verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/anjo/service/WebhookService.kt` | Fire-and-forget webhook dispatch | VERIFIED | 62 lines; class WebhookService with send(), resolveUrl(), buildPayload(); withTimeout(5_000); TimeoutCancellationException before Exception catch |
| `src/main/kotlin/com/anjo/model/WebhookPayload.kt` | Serializable JSON payload | VERIFIED | @Serializable data class with scheduleId, text, effect, zoneId?, firedAt |
| `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` | Global fallback URL config | VERIFIED | data class WebhooksConfig(val defaultUrl: String?) |
| `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt` | MockEngine unit tests for HOOK-01/02/03 | VERIFIED | 6 test cases covering HOOK-01+02, zoneId, HOOK-03, D-04 priority, D-05 silent skip, D-16 failure isolation |
| `src/main/kotlin/com/anjo/service/SchedulerService.kt` | Scheduler fires webhook on successful display | VERIFIED | Constructor param `private val webhookService: WebhookService? = null`; fire() captures `val displayed`; calls `webhookService?.send(schedule, Instant.now())` inside existing try block when `if (displayed)` |
| `src/main/kotlin/com/anjo/di/DependencyInjection.kt` | HttpClient(CIO) + WebhookService DI singletons | VERIFIED | HttpClient(CIO) with ContentNegotiation at line 53; WebhookService(httpClient, appConfig.webhooks) at line 58; provide { httpClient } at line 79; provide { webhookService } at line 80; webhookService = webhookService named arg at line 59 |
| `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` | Integration tests: send called on display=true, not on false | VERIFIED | mockk<WebhookService>(relaxed=true) at line 31; clearMocks includes mockWebhook; two integration tests at lines 269–310 |
| `src/test/kotlin/com/anjo/ApplicationTest.kt` | DI smoke assertion for WebhookService | VERIFIED | `deps.getBlocking<WebhookService>(DependencyKey<WebhookService>()) shouldNotBeNull {}` at line 86 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `WebhookService.kt` | httpClient.post | Ktor client POST with setBody | VERIFIED | `httpClient.post(url) { header(...); setBody(payload) }` at lines 35–38 |
| `ConfigLoader.kt` | WebhooksConfig | propertyOrNull webhooks.defaultUrl | VERIFIED | `config.propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }` at line 72 |
| `SchedulerService.kt` | WebhookService.send | fire() success branch after displayScheduled returns true | VERIFIED | `if (displayed) { webhookService?.send(schedule, Instant.now()) }` at lines 177–179 |
| `DependencyInjection.kt` | SchedulerService | constructor named arg webhookService = webhookService | VERIFIED | `SchedulerService(..., webhookService = webhookService)` at line 59 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `WebhookService.send()` | schedule, firedAt | SchedulerService.fire() passes Schedule + Instant.now() | Yes — real Schedule from repository + live Instant | FLOWING |
| `WebhookPayload` | scheduleId, text, effect, zoneId, firedAt | buildPayload() reads schedule fields directly; firedAt is Instant parameter | Yes — all fields from actual schedule row | FLOWING |
| URL resolution | resolveUrl() result | schedule.webhookUrl or config.defaultUrl from ConfigLoader | Yes — ConfigLoader reads env var or yaml value | FLOWING |

### Behavioral Spot-Checks

All checks are test-based (not live server required):

| Behavior | Evidence | Status |
|----------|----------|--------|
| HOOK-01+02: POST to schedule.webhookUrl with correct body | WebhookServiceTest line 48–70: MockEngine captures request, asserts URL, method, contentType, body fields | PASS (test exists and passes per SUMMARY) |
| HOOK-03: POST to config.defaultUrl when webhookUrl null | WebhookServiceTest line 106–121: asserts captured URL equals fallback URL | PASS |
| D-05: zero requests when both URLs null | WebhookServiceTest line 140–153: capturedRequests.size shouldBe 0 | PASS |
| D-16: no throw on 500 response | WebhookServiceTest line 156–170: send() completes; request was still attempted | PASS |
| SC-4: webhook status in history | HistoryTable: no webhook column; HistoryRepository.insert(): no webhook param | FAIL |
| DI smoke: WebhookService resolvable | ApplicationTest line 86: getBlocking<WebhookService> shouldNotBeNull | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| HOOK-01 | 10-01-PLAN, 10-02-PLAN | WebhookService POSTs to schedule.webhookUrl when set | SATISFIED | WebhookService.resolveUrl() prefers schedule.webhookUrl; fire() calls send() on success; tests verify |
| HOOK-02 | 10-01-PLAN, 10-02-PLAN | POST body contains scheduleId, text, effect, zoneId, firedAt with Content-Type application/json | SATISFIED | WebhookPayload has all five fields; header(ContentType.Application.Json) set explicitly; WebhookServiceTest asserts each field |
| HOOK-03 | 10-01-PLAN, 10-02-PLAN | WebhookService POSTs to AppConfig.webhooks.defaultUrl when schedule.webhookUrl is null/blank | SATISFIED | ConfigLoader wires defaultUrl; resolveUrl() falls back to config.defaultUrl; WebhookServiceTest Test 3 proves it |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| No TBD/FIXME/XXX/TODO/HACK markers found in any phase-10 modified production or test file | — | — | — | — |

No inline comments found in any Kotlin source file modified by this phase (project rule compliant).

### Human Verification Required

None — all automated checks were conclusive. The gap (SC-4) is observable in the schema and code without human interaction.

### Gaps Summary

**1 BLOCKER gap — ROADMAP Success Criterion 4 not implemented.**

ROADMAP.md Phase 10 SC-4 states: "Webhook delivery status is visible in the history record for the corresponding display event." The implementation has no code that records webhook delivery outcome into history:

- `HistoryTable` (7 columns): `id`, `text`, `effect`, `displaySource`, `scheduleId`, `zoneId`, `displayedAt` — no `webhookStatus`, `webhookDelivered`, or any webhook field.
- `HistoryRecord` model: no webhook field.
- `HistoryRepository.insert()`: takes a `HistoryRecord` as parameter; no webhook status is accepted or written.
- `SchedulerService.fire()`: calls `webhookService?.send()` (fire-and-forget, returns Unit); does not link the dispatch to the history insertion that `ScreenDriverService.displayScheduled()` already performs.
- `WebhookService.send()` is void (fire-and-forget by design per D-09); it cannot return a delivery result to the caller without changing the architecture.

This gap is not deferred to any later phase. Phases 11 (Multi-Zone), 12 (Observability), and 13 (UI/UX) have no success criteria or goals that mention webhook history.

The three HOOK requirements (HOOK-01, HOOK-02, HOOK-03) are fully satisfied. The gap is specifically the SC-4 observability requirement that webhook outcome be recorded in history alongside the display event.

---

_Verified: 2026-06-16_
_Verifier: Claude (gsd-verifier)_
