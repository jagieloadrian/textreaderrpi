---
plan: 10-03
phase: 10-webhooks
status: complete
completed: 2026-06-16
requirements: [HOOK-01, HOOK-02, HOOK-03]
gap_closure: true
closes: [SC-4]
---

## Summary

Closed verification gap SC-4 by adding `webhook_status` column to `display_history` (Flyway V4) and threading the status through the full history stack: `HistoryTable` → `HistoryRecord` → `HistoryRepository`. `WebhookService.willSend()` exposes the URL-resolution predicate; `SchedulerService.fire()` computes `"sent"`/`"skipped"` before calling `displayScheduled()`, which threads `webhookStatus` through `runScheduledRender()` → `tryInsertHistory()` into the SCHEDULED history row. IMMEDIATE display events retain `webhookStatus = null`. The `/history` HTML page renders the webhook status for SCHEDULED records; `GET /api/v1/history` JSON exposes it automatically via serialization.

## What Was Built

### Task 1: Flyway V4 migration + webhookStatus through history stack
- Created `V4__add_webhook_status_to_history.sql` with `ALTER TABLE display_history ADD COLUMN webhook_status VARCHAR(20)`
- Added `val webhookStatus = varchar("webhook_status", 20).nullable()` to `HistoryTable`
- Added `val webhookStatus: String? = null` to `HistoryRecord` (nullable default keeps all existing call sites compiling)
- Updated `HistoryRepository.insert()` and `toHistoryRecord()` to round-trip the new field

### Task 2: willSend() predicate + webhookStatus threading
- Added `fun willSend(schedule: Schedule): Boolean = resolveUrl(schedule) != null` to `WebhookService`
- Added `webhookStatus: String? = null` trailing param to `displayScheduled()`, `runScheduledRender()`, `tryInsertHistory()`
- `SchedulerService.fire()` computes `val webhookStatus = if (webhookService?.willSend(schedule) == true) "sent" else "skipped"` before `displayScheduled()` call
- `send()` contract unchanged: non-suspend, fire-and-forget, returns Unit (D-09 preserved)

### Task 3: HTML surfacing + test updates
- `HistoryPage.kt`: added conditional `if (item.webhookStatus != null) { p { strong { +"Webhook:" }; +item.webhookStatus } }` after zoneId row
- `HistoryRepositoryTest`: extended round-trip test for `webhookStatus = "sent"` and `null`
- `HistoryRecordingTest`: SCHEDULED test uses 6-arg `displayScheduled` with `"sent"` and asserts `record?.webhookStatus shouldBe "sent"`; IMMEDIATE test asserts `webhookStatus shouldBe null`
- `SchedulerServiceTest`: all `displayScheduled` matchers updated to 6 args; added `willSend` stubs; new `"sent"`/`"skipped"` assertion tests; existing webhook send/skip tests intact
- `ConflictPolicyTest`: `displayScheduled` stubs updated to 6 `any()` matchers

## Key Files

### Created
- `src/main/resources/db/migration/V4__add_webhook_status_to_history.sql`

### Modified
- `src/main/kotlin/com/anjo/db/HistoryTable.kt`
- `src/main/kotlin/com/anjo/model/HistoryRecord.kt`
- `src/main/kotlin/com/anjo/db/HistoryRepository.kt`
- `src/main/kotlin/com/anjo/service/WebhookService.kt`
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`
- `src/main/kotlin/com/anjo/service/SchedulerService.kt`
- `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt`
- `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt`
- `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt`
- `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt`
- `src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt`

## Decisions

- **Status reflects dispatch intent, not delivery confirmation**: `"sent"` means a webhook URL resolved and `send()` was invoked; `"skipped"` means no URL was configured. HTTP outcome is not captured (D-09 fire-and-forget preserved).
- **URL resolution computed before display**: `willSend()` is deterministic (internal config/schedule data), so the status is known before `displayScheduled()` runs and can be written in the same history row.
- **Defaulted param preserves all existing call sites**: `webhookStatus: String? = null` on `displayScheduled()` means no change needed at the `displayImmediate` path or any existing 4/5-arg call sites.

## Self-Check: PASSED

- `./gradlew compileKotlin compileTestKotlin` exits 0
- `./gradlew test -x jacocoTestCoverageVerification` BUILD SUCCESSFUL (all tests pass)
- Targeted suites: HistoryRepositoryTest, HistoryRecordingTest, SchedulerServiceTest, ConflictPolicyTest, HistoryRoutesTest, HistoryUIRoutesTest — all green
- SC-4 closed: SCHEDULED history rows carry `webhookStatus`; IMMEDIATE rows carry `null`
- No comments added to source files (project rule enforced)
- No changes to DI, config keys, or build catalog (as specified)
