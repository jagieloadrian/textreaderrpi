---
phase: 19-dry-yagni-refactoring
fixed_at: 2026-07-08T00:00:00Z
review_path: .planning/phases/19-dry-yagni-refactoring/19-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 19: Code Review Fix Report

**Fixed at:** 2026-07-08T00:00:00Z
**Source review:** .planning/phases/19-dry-yagni-refactoring/19-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (all Warnings; fix_scope=critical_warning, 0 Critical)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### WR-01: DELETE /zones/{id} can remove and stop a live local hardware zone

**Files modified:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt`
**Commit:** 216d2d0
**Applied fix:** Applied the review's alternative (root-cause) option instead of the route-level check: `removeZone(id)` now reads the entry first and refuses to remove or stop it when `entry.isLocal` is true, returning `false`. This closes the hole for the single existing caller (`ZoneRoutes.kt` DELETE `/zones/{id}`) and protects any future caller in one place, since the invariant now lives in the shared function rather than being re-derived per call site. `ZoneRoutes.kt` was left untouched — its 404 body still only fires when the persisted row is absent, but a stale persisted row can no longer cause a live local driver to be stopped.

### WR-02: Vacuous assertion — pagination test can never fail because the page number itself differs

**Files modified:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt`
**Commit:** b9d3032
**Applied fix:** Replaced the raw-body inequality check with a deserialize-and-compare-ids check, exactly as suggested: both response bodies are decoded to `HistoryPageResponse` via `kotlinx.serialization.json.Json`, then `page1.items.map { it.id } shouldNotBe page2.items.map { it.id }`. Confirmed `HistoryRepository.insert` assigns a fresh `UUID` per row, so the id-set comparison is meaningful and would fail if pagination offset/limit handling broke.

### WR-03: Vacuous test — "insert failure" test never invokes the throwing repository

**Files modified:** `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt`
**Commit:** 29e757a
**Applied fix:** Stubbed `mockZoneDriver.send(...)` to return `true` so `ScreenDriverService.broadcastImmediate` actually reaches `tryInsertHistory` for the successful zone, and added `coVerify(exactly = 1) { throwingRepo.insert(any()) }` after the `displayImmediate` call. Confirmed `displayImmediate`'s null-zoneId broadcast path runs `broadcastImmediate` synchronously (no separate job launch), so the insert call completes before the assertion runs — no `awaitCurrentJob()` needed for this path.

### WR-04: Seeded network zone leaks into the shared H2 database and spawns a reconnect loop for the rest of the JVM's test run

**Files modified:** `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt`
**Commit:** d379be8
**Applied fix:** Wrapped the request/assertions in `try { ... } finally { zoneRegistry.removeZone("test-zone-ui"); zoneRepository.delete("test-zone-ui") }`. Used `try/finally` rather than a bare trailing cleanup (the review's minimal suggestion) so the seeded zone and its reconnect loop are torn down even if an assertion fails mid-test — the whole point of this finding is that a leaked zone silently pollutes every later test in the same JVM, so cleanup that only runs on the happy path would still leave that failure mode open. Verified `removeZone` correctly stops a non-local `NetworkZoneDriver` after the WR-01 fix (it only refuses `isLocal` entries).

## Verification

- Each fix verified with `./gradlew compileTestKotlin` (Tier 2 syntax/type check) before commit; no Kotlin-specific standalone syntax checker was used since none is listed for this language in the verification table beyond a full compile.
- After all four fixes, ran the three modified test classes directly (`./gradlew test --tests com.anjo.routing.HistoryRoutesTest --tests com.anjo.service.HistoryRecordingTest --tests com.anjo.routing.ZonesUIRoutesTest`): all tests passed. The run then failed only on `jacocoTestCoverageVerification` (0.45 vs 0.70 minimum), which is expected and irrelevant when running a 3-class subset instead of the full suite — not a regression from these fixes.
- WR-01 (`ZoneRegistry.kt`) has no dedicated test in this fix round; it is a logic change (locality guard) rather than a test-quality fix. Recommend a follow-up unit/integration test asserting `DELETE /zones/{id}` cannot stop a local zone even when a stale persisted row shares its id, before this phase is considered fully verified.

---

_Fixed: 2026-07-08T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
