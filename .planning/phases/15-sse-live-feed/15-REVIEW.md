---
phase: 15-sse-live-feed
reviewed: 2026-06-23T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - build.gradle.kts
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/main/kotlin/com/anjo/di/HTTP.kt
  - src/main/kotlin/com/anjo/model/DisplayEvent.kt
  - src/main/kotlin/com/anjo/routing/LiveRoutes.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/service/DisplayEventBus.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/web/templates/StatusPage.kt
  - src/main/resources/static/live-feed.js
  - src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/WebRoutesTest.kt
  - src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt
findings:
  critical: 1
  warning: 2
  info: 2
  total: 5
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-06-23T00:00:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Phase 15 introduces SSE live feed: `DisplayEventBus` (SharedFlow replay=5), `ScreenDriverService` emitting after history insert, `LiveRoutes.kt` GET `/api/v1/live` with 30s heartbeat, `HTTP.kt` installing SSE, `Routing.kt` registering liveRoutes outside rate-limited block, `live-feed.js` EventSource widget, and `StatusPage.kt` Live Feed article.

The DOM-safety of `live-feed.js` is correct: `getElementById` calls are inside the event listener callback, not at parse time, so they are safe despite the script being injected in `<head>`. The SSE heartbeat/collect ordering is correct for Ktor 3.x SSE DSL. The CORS wildcard and inline comments in `HTTP.kt` are pre-existing, not introduced by this phase.

One critical defect: `displayEventBus.emit()` is called inside a zone mutex, and `MutableSharedFlow.emit()` with `extraBufferCapacity = 0` suspends when a subscriber's receive buffer is full. A stalled or slow SSE client can cause `emit()` to suspend indefinitely, holding the zone mutex and permanently blocking all subsequent display operations for that zone.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: `displayEventBus.emit()` suspends inside zone mutex — stalled SSE client deadlocks the display pipeline

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:189`

**Issue:** `tryInsertHistory` calls `displayEventBus?.emit(...)`, which is a `suspend fun` on `MutableSharedFlow` configured with `extraBufferCapacity = 0`. Per kotlinx.coroutines semantics, `emit()` suspends until all active subscribers have received the value when no extra buffer space exists. `tryInsertHistory` is called from inside `withMutex(mutex, ...)` in both `renderImmediate` (line 136) and `runScheduledRender` (line 163). If an SSE client has an open connection but is slow or stalled, `emit()` suspends while the zone mutex is held. The mutex is never released until `emit()` returns, preventing every subsequent `displayImmediate` or `displayScheduled` call for that zone from acquiring the mutex. The display pipeline deadlocks until the stalled client disconnects.

**Fix:** Move the `emit()` call outside the mutex-protected block, or use `tryEmit()` instead of `emit()`. `tryEmit()` returns false if the value cannot be immediately placed in the buffer, rather than suspending. Since the replay cache (capacity 5) will still store the event for late collectors, dropping the emission when no subscriber can receive it is acceptable:

```kotlin
private suspend fun tryInsertHistory(
    text: String,
    effect: String,
    source: String,
    scheduleId: String? = null,
    webhookStatus: String? = null,
    zoneId: String? = null,
) {
    try {
        val record = historyRepository?.insert(
            HistoryRecord(text = text, effect = effect, source = source,
                          scheduleId = scheduleId, webhookStatus = webhookStatus, zoneId = zoneId)
        )
        if (record != null) {
            displayEventBus?.tryEmit(
                DisplayEvent(id = record.id, text = record.text,
                             effect = record.effect, zoneId = record.zoneId,
                             displayedAt = record.displayedAt)
            )
        }
    } catch (e: Exception) {
        log.warn("History insert failed (non-fatal): ${e.message}", e)
    }
}
```

Changing `emit` to `tryEmit` also allows `tryInsertHistory` to become a regular non-`suspend` function, which eliminates the suspend point entirely and removes the mutex-blocking risk regardless of client behavior. `DisplayEventBus.emit` should be kept as a `suspend fun` for callers that genuinely need backpressure, but this internal path should use `tryEmit`.

---

## Warnings

### WR-01: `JSON.parse` uncaught in `live-feed.js` event handler — malformed SSE data crashes the widget silently

**File:** `src/main/resources/static/live-feed.js:5`

**Issue:** `JSON.parse(e.data)` throws `SyntaxError` on any malformed payload (e.g., a partial flush, a heartbeat comment leaking into `data`, or a future schema change). An uncaught exception inside an `EventSource` event listener does not kill the connection but it terminates the current event callback before lines 8–9 execute, leaving the DOM stale with no indication to the developer. The `onerror` handler on line 12 will not catch this — `onerror` is for connection errors, not in-handler exceptions.

**Fix:**

```js
src.addEventListener('display', e => {
  let d;
  try {
    d = JSON.parse(e.data);
  } catch {
    return;
  }
  const textEl = document.getElementById('live-text');
  const metaEl = document.getElementById('live-meta');
  if (textEl) textEl.textContent = d.text;
  if (metaEl) metaEl.textContent = (d.zoneId ?? '—') + ' · ' + d.effect;
});
```

### WR-02: `onerror` silently swallows all SSE connection errors — reconnection state is invisible

**File:** `src/main/resources/static/live-feed.js:12`

**Issue:** `src.onerror = () => {}` is an empty function. The browser `EventSource` does reconnect automatically, but the empty handler suppresses the console error that would otherwise appear, making it impossible to diagnose connection failures during development or triage. More critically, the `onerror` callback receives the event object which includes `readyState`; when `readyState === EventSource.CLOSED` the connection will not reconnect and the handler should attempt re-creation. The empty handler silently accepts permanent failure.

**Fix:** At minimum remove the empty assignment and let the default browser behavior report the error. If the page should survive permanent server-side closure, handle `CLOSED` state:

```js
src.onerror = (e) => {
  if (src.readyState === EventSource.CLOSED) {
    setTimeout(() => {
      window.location.reload();
    }, 5000);
  }
};
```

---

## Info

### IN-01: Unused import `kotlinx.coroutines.flow.collect` in `LiveRoutes.kt`

**File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt:9`

**Issue:** In Kotlin 2.x, `collect` is a member function on `Flow`, not an extension function requiring an explicit import. The import `kotlinx.coroutines.flow.collect` on line 9 is redundant and will generate an "Unused import" warning from the Kotlin compiler.

**Fix:** Remove line 9: `import kotlinx.coroutines.flow.collect`

### IN-02: Unused import `com.anjo.model.DisplayEvent` in `LiveRoutes.kt`

**File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt:3`

**Issue:** `DisplayEvent` never appears as an identifier in the source text of `LiveRoutes.kt`. The `event` variable in `collect { event -> ... }` is typed as `DisplayEvent` by inference from `SharedFlow<DisplayEvent>`, but Kotlin does not require an explicit import for types that are only used via inference. The `Json.encodeToString(event)` call resolves the reified type from the inferred value type at the call site without needing `DisplayEvent` in lexical scope. The Kotlin compiler will report this as an unused import.

**Fix:** Remove line 3: `import com.anjo.model.DisplayEvent`

---

_Reviewed: 2026-06-23T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
