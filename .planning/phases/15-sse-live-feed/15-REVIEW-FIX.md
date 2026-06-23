---
phase: 15-sse-live-feed
fixed_at: 2026-06-23T12:30:00Z
iteration: 1
fix_scope: all
findings_in_scope: 7
fixed: 6
skipped: 1
status: partial
---

# Phase 15 Code Review Fix Report

## Summary

6 of 7 findings fixed. 1 skipped by design.

## Fixed Findings

### WR-01 — SharedFlow buffer capacity prevents silent event drops
- **File:** `src/main/kotlin/com/anjo/service/DisplayEventBus.kt`
- **Change:** `extraBufferCapacity = 0` → `extraBufferCapacity = 64`
- **Commit:** `fix(15): increase SharedFlow extraBufferCapacity to 64 to prevent silent event drops (WR-01)`
- **Result:** `tryEmit()` now only returns `false` when all 64 extra buffer slots + replay buffer are exhausted, making silent event drops extremely unlikely under normal load.

### WR-03 — tryEmit production code path now has test coverage
- **File:** `src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt`
- **Change:** Added `tryEmit returns true and event is visible to fresh collector via replay` test
- **Commit:** `fix(15): add tryEmit test to cover production code path in DisplayEventBus (WR-03)`
- **Result:** The actual production code path (`tryEmit` → replay buffer → collector) is now tested.

### WR-04 — Inline comments removed from HTTP.kt
- **File:** `src/main/kotlin/com/anjo/di/HTTP.kt`
- **Change:** Removed two inline `//` comments (lines 18 and 21)
- **Commit:** `fix(15): remove inline comments from HTTP.kt (WR-04)`
- **Result:** Compliant with project no-comments rule.

### IN-01 — Unused import `kotlinx.coroutines.flow.collect` removed
- **File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt`
- **Commit:** `fix(15): remove unused imports from LiveRoutes.kt (IN-01, IN-02)`

### IN-02 — Unused import `com.anjo.model.DisplayEvent` removed
- **File:** `src/main/kotlin/com/anjo/routing/LiveRoutes.kt`
- **Commit:** `fix(15): remove unused imports from LiveRoutes.kt (IN-01, IN-02)`

### IN-03 — SSE data path integration test added
- **File:** `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt`
- **Change:** Added `GET /api/v1/live streams display event as named SSE frame` test. Pre-emits a `DisplayEvent` via `DisplayEventBus.tryEmit()` (replay buffer), makes the SSE request, reads 4 lines from the channel, and asserts `event: display` and `data: ...` lines containing the payload text.
- **Commit:** `fix(15): add SSE data path integration test asserting event: display frame (IN-03)`
- **Result:** End-to-end SSE emit → wire → frame assertion now covered.

## Skipped Findings

### WR-02 — SSE endpoint outside rate limiter (by design)
- **Reason:** SSE endpoints must be outside the rate limiter — rate limiting would kill the persistent connection by rejecting keep-alive traffic. The current architecture is intentional. Adding a per-IP connection limit would require a custom plugin or middleware not warranted for a single-user Raspberry Pi deployment.
- **Status:** Skipped — accepted as design constraint.
