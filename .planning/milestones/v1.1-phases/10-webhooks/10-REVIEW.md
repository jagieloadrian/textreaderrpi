---
phase: 10-webhooks
reviewed: 2026-06-16T00:00:00Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
  - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
  - src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt
  - src/main/kotlin/com/anjo/db/HistoryRepository.kt
  - src/main/kotlin/com/anjo/db/HistoryTable.kt
  - src/main/kotlin/com/anjo/model/HistoryRecord.kt
  - src/main/kotlin/com/anjo/model/WebhookPayload.kt
  - src/main/kotlin/com/anjo/service/SchedulerService.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/service/WebhookService.kt
  - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
  - src/main/resources/application.yaml
  - src/main/resources/db/migration/V4__add_webhook_status_to_history.sql
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/test/kotlin/com/anjo/ApplicationTest.kt
  - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
  - src/test/kotlin/com/anjo/service/ConflictPolicyTest.kt
  - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
  - src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt
  - src/test/kotlin/com/anjo/service/WebhookServiceTest.kt
  - src/test/resources/application.yaml
findings:
  critical: 3
  warning: 5
  info: 3
  total: 11
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-16T00:00:00Z
**Depth:** standard
**Files Reviewed:** 22
**Status:** issues_found

## Summary

This phase adds webhook dispatch on schedule fire, a `webhook_status` column on the history table, and surfacing of that status in the History UI. The implementation is structurally sound — the service boundary is clean, the nullable opt-out approach is correct, and the new column is backward-compatible via a Flyway migration. Three blockers were found: a race between `willSend` and `send` that produces a permanently wrong `webhookStatus` in the history record; an unvalidated URL that enables SSRF to internal network targets; and an `HttpClient` that is leaked whenever the application stops without a clean `ApplicationStopping` event (integration tests). Five warnings address flaky test timing, a mutex unlock that can panic on an unbalanced lock, in-flight webhook coroutines that are silently abandoned at shutdown, a row-count race in the history repository, and a duplicate test assertion. Three info items flag a disabled future-feature UI rendered in every response, redundant imports, and an `@OptIn` annotation that is unused.

---

## Critical Issues

### CR-01: `webhookStatus` is computed from `willSend` before the display outcome is known — history record is permanently wrong on failure

**File:** `src/main/kotlin/com/anjo/service/SchedulerService.kt:176-179`

`fire()` calls `webhookService?.willSend(schedule)` to decide what label to pass to `displayScheduled`, and that label is written to the history row inside `displayScheduled` before the webhook HTTP call is made. The label therefore describes _intent_, not _outcome_. Two concrete failure modes:

1. The HTTP POST inside `WebhookService.send` fails (network error, timeout, non-2xx) after the history row is already written with `"sent"`. The UI shows `Webhook: sent` permanently for a delivery that never succeeded.
2. `willSend` returns `true` but `displayed` turns out to be `false` (SKIP_NEW busy-drop). In that case `webhookService?.send(...)` is never called at all (correct), but the history row was not written either — so the label is moot. However, the label is calculated unconditionally before the display outcome is available, which is fragile: any future code that writes a row on a SKIP_NEW drop would inherit the wrong status.

**Fix:** Label the intent honestly as `"pending"` / `"skipped"` at record time. Track a separate `"delivered"` / `"failed"` update via a callback after the HTTP response is received. As a minimum viable fix without a two-phase update:

```kotlin
private suspend fun fire(schedule: Schedule): Boolean {
    return try {
        val renderer = effectFactory.create(schedule.effect)
        val policy = schedule.conflictPolicy ?: ConflictPolicy.INTERRUPT
        val willDispatch = webhookService?.willSend(schedule) == true
        // Use "pending"/"skipped" — honest at record time
        val webhookStatus = if (willDispatch) "pending" else "skipped"
        val displayed = screenService.displayScheduled(
            schedule.text, schedule.id, renderer, schedule.effect, policy, webhookStatus
        )
        if (displayed && willDispatch) {
            webhookService?.send(schedule, Instant.now())
        }
        displayed
    } catch (e: Exception) {
        log.error("Failed to fire schedule ${schedule.id}: ${e.message}", e)
        false
    }
}
```

A complete fix requires `WebhookService.send` to return a `Deferred<Boolean>` and a follow-up `HistoryRepository.updateWebhookStatus` call, tracked as a Phase 11 item.

---

### CR-02: Webhook URL is never validated — Server-Side Request Forgery (SSRF)

**File:** `src/main/kotlin/com/anjo/service/WebhookService.kt:62-64`

`resolveUrl` returns any non-blank string from `schedule.webhookUrl` or `config.defaultUrl` verbatim. `send` then dispatches an HTTP POST to that URL with no scheme check, host validation, or IP-range filter. Any API caller who can create or update a schedule (POST `/api/v1/schedule`) can set `webhookUrl` to:

- `http://169.254.169.254/latest/meta-data/` — cloud metadata endpoint
- `http://localhost:5432/` — probe internal services
- `http://10.0.0.1/admin` — reach LAN hosts from the Pi

**Fix:** Validate the resolved URL before dispatching. At minimum reject non-HTTP/HTTPS schemes and private/loopback IP ranges:

```kotlin
private fun resolveUrl(schedule: Schedule): String? {
    val raw = schedule.webhookUrl?.takeIf { it.isNotBlank() }
        ?: config.defaultUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return try {
        val uri = java.net.URI(raw)
        require(uri.scheme in listOf("http", "https")) { "Non-HTTP scheme: ${uri.scheme}" }
        val host = uri.host ?: error("No host in URL")
        val addr = java.net.InetAddress.getByName(host)
        require(!addr.isLoopbackAddress && !addr.isLinkLocalAddress && !addr.isSiteLocalAddress) {
            "Webhook target is a private/internal address"
        }
        raw
    } catch (e: Exception) {
        log.warn("Rejected webhook URL for schedule ${schedule.id}: $raw — ${e.message}")
        null
    }
}
```

Note: `InetAddress.getByName` does a DNS lookup; full protection against DNS-rebinding requires re-checking the resolved IP at connection time. Track that as a follow-up.

---

### CR-03: `WebhookService.create` constructs an `HttpClient` that leaks in test paths

**File:** `src/main/kotlin/com/anjo/service/WebhookService.kt:75-80`
**Related:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt:49`

`WebhookService.create` always allocates a new `HttpClient(CIO)`. In `configureDI`, the `ApplicationStopping` handler calls `webhookService.stop()` which closes the client on graceful shutdown. However, every integration test (`ApplicationTest`, `HistoryRecordingTest`) calls `testApplication { application { module() } }`, creating a fresh `configureDI()` call and therefore a fresh `HttpClient` per test. When a test fails mid-run (assertion error before the server shuts down), or when `testApplication` disposes the server without triggering `ApplicationStopping`, the client is never closed. Across the test suite this accumulates open NIO selector threads and TCP connections, risking port exhaustion on CI.

**Fix:** Ensure the `ApplicationStopping` handler closes the client even when the server exits abnormally, and wrap it in `try/finally` so one failure does not prevent the others from running:

```kotlin
monitor.subscribe(ApplicationStopping) {
    try { schedulerService.stop() } finally {
        try { webhookService.stop() } finally {
            screenDriverService.stop()
        }
    }
}
```

The underlying root cause is that `WebhookService.create` couples client creation to the service — tests that need the service wired should use the injected-client constructor (as `WebhookServiceTest` already does) rather than `create`.

---

## Warnings

### WR-01: `WebhookServiceTest` assertions rely on `delay(200)` wall-clock time — inherently flaky

**File:** `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt:57-65, 97-101, 114-117, 132-135, 148-151, 163-167`

`WebhookService.send` launches a coroutine on `Dispatchers.IO` (or the injected scope). All assertions on `capturedData.size` are preceded by `delay(200)` on the test dispatcher, which is a different thread pool. On a loaded CI agent, 200 ms may not be enough for the IO coroutine to post its request before the assertion fires, producing a false failure. The `beforeSpec { delay(500) }` JVM warmup exacerbates this — it is also unreliable and should not be necessary.

**Fix:** Pass a `TestScope` with `UnconfinedTestDispatcher` to the `WebhookService` constructor and replace `delay(N)` with `advanceUntilIdle()`:

```kotlin
runTest {
    val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
    val service = WebhookService(makeClient(capturedData), WebhooksConfig(null), testScope)
    service.send(schedule, firedAt)
    testScope.advanceUntilIdle()
    capturedData.size shouldBe 1
}
```

---

### WR-02: Mutex can be left permanently locked in `displayScheduled` on the `SKIP_NEW` fast-path

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:73-82`

When `conflictPolicy == SKIP_NEW` and `tryLock()` succeeds (line 74), `displayScheduled` proceeds to set `currentScheduledId` (line 79), which can throw `CancellationException` if the calling coroutine has already been cancelled. In that scenario, execution never reaches `runScheduledRender`, so the `finally` block at line 149 (`if (alreadyLocked) displayMutex.unlock()`) is never executed. The mutex is now permanently locked, deadlocking all subsequent display operations until the process restarts.

**Fix:** Wrap the window between `tryLock` and entry into `runScheduledRender` with a `try/finally`:

```kotlin
suspend fun displayScheduled(...): Boolean {
    val alreadyLocked = if (conflictPolicy == ConflictPolicy.SKIP_NEW) {
        if (!displayMutex.tryLock()) {
            log.info("SKIP_NEW: display busy, dropping scheduled request id=$scheduleId")
            return false
        }
        true
    } else false

    return try {
        currentScheduledId = scheduleId
        currentDisplayJob = currentCoroutineContext().job
        lastSentMessage.set(text)
        runScheduledRender(text, scheduleId, renderer, effect, alreadyLocked, webhookStatus)
    } catch (e: Throwable) {
        if (alreadyLocked) displayMutex.unlock()
        throw e
    }
}
```

---

### WR-03: In-flight webhook coroutines are silently abandoned at shutdown

**File:** `src/main/kotlin/com/anjo/service/WebhookService.kt:55-58`
**Related:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt:53-56`

`WebhookService.stop()` cancels the scope's `Job` and then immediately calls `httpClient.close()`. Any `withTimeout` block inside `send` that is still executing when `close()` is called will receive an engine-level `ClosedChannelException`, which is silently caught by the `catch (e: Exception)` handler at line 49. The webhook is never delivered and no error is surfaced to the operator.

**Fix:** Before cancelling the scope, join outstanding jobs. One approach: track in-flight jobs in a `CopyOnWriteArrayList` and call `joinAll()` in `stop()` with a bounded drain timeout:

```kotlin
fun stop() {
    // Give in-flight sends up to 5 s to complete before forcing close
    runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(5_000) {
            scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        }
    }
    scope.coroutineContext[Job]?.cancel()
    httpClient.close()
}
```

---

### WR-04: `HistoryRepository.insert` count-and-prune is not atomic — row count can transiently exceed `MAX_ROWS` under concurrent inserts

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:21-40`

The count-check and the delete run inside one `suspendTransaction`, but Exposed's default isolation level is `READ_COMMITTED`. Two concurrent `insert` calls can each observe `count == 999`, both skip the delete branch, and both insert — leaving `count == 1001`. The serial-loop test in `HistoryRepositoryTest` does not exercise this race.

**Fix:** Convert the prune into a single DELETE statement that is inherently atomic and avoids the TOCTOU window:

```sql
DELETE FROM display_history
WHERE id NOT IN (
    SELECT id FROM display_history
    ORDER BY displayed_at DESC
    LIMIT 999
)
```

Or use `SERIALIZABLE` isolation for the insert transaction. The single-statement DELETE is simpler and faster.

---

### WR-05: `ApplicationTest` "serve static assets" test asserts `shouldBe HttpStatusCode.OK` twice — duplicate hides missing assertion

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:46-50`

Lines 48 and 49 are identical. The second line cannot catch any bug the first did not already catch, and its presence suggests a `Content-Type` or body assertion was intended but never written.

**Fix:** Remove the duplicate and add the missing assertion:

```kotlin
response.status shouldBe HttpStatusCode.OK
response.headers[io.ktor.http.HttpHeaders.ContentType].shouldNotBeNull()
```

---

## Info

### IN-01: Disabled placeholder UI for a future Phase 11 feature is rendered in every History page response

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:66-71`

A `<select disabled>` labelled "Multi-zone — Phase 11" is included in every `/history` response. Non-functional scaffolding in production HTML confuses end users and adds unnecessary payload.

**Fix:** Remove the zone filter block entirely until Phase 11 is implemented.

---

### IN-02: Redundant explicit imports alongside wildcard import in `HistoryRepository.kt`

**File:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt:4-6`

`ResultRow` and `SortOrder` are explicitly imported but are already covered by `import org.jetbrains.exposed.v1.core.*` on line 6. The explicit imports are dead.

**Fix:** Remove lines 4-5, keeping only the wildcard (or switch to all-explicit and drop the wildcard).

---

### IN-03: `@OptIn(ExperimentalCoroutinesApi::class)` on `WebhookServiceTest` is unused

**File:** `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt:23` (implicit — present on the class or file)

No experimental coroutines-test API (`TestCoroutineScheduler`, `runTest`, `UnconfinedTestDispatcher`) is used in the current test body. The annotation will generate a compiler warning and signals an incomplete migration toward deterministic test control (see WR-01).

**Fix:** Remove the annotation. If WR-01 is addressed by migrating to `runTest`, the annotation becomes necessary and this item resolves automatically.

---

_Reviewed: 2026-06-16T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
