---
phase: 19-dry-yagni-refactoring
reviewed: 2026-07-08T06:11:13Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/kotlin/com/anjo/routing/HistoryRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
  - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
  - src/main/kotlin/com/anjo/validation/HistoryValidators.kt
  - src/test/kotlin/com/anjo/ApplicationTest.kt
  - src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt
  - src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt
  - src/test/kotlin/com/anjo/TestSupport.kt
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 19: Code Review Report

**Reviewed:** 2026-07-08T06:11:13Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Re-review of the Phase 19 DRY/YAGNI refactor after the five REVIEW-FIX commits (`a6cb69d`..`cee021b`). All five prior findings are verified resolved in source:

- **WR-01 (old):** UI case-normalization pinned — `HistoryUIRoutesTest` now asserts `?effect=scroll` filters and `?effect=all` disables the filter.
- **WR-02 (old):** `parseFilter` has four direct unit tests in `HistoryValidatorsTest.kt:29-43`, including the sanitize-before-blank-check ordering invariant.
- **WR-03 (old):** The SKIP_NEW test was rewritten and now genuinely exercises drop semantics — a gated `CompletableDeferred` holds the zone mutex, the second request uses `ConflictPolicy.SKIP_NEW`, and the assertion `insertedTexts shouldBe listOf("kept")` fails if a dropped request records history. Traced through `ScreenDriverService.acquireMutex`/`renderImmediate` with `Dispatchers.Unconfined`: the mutex is deterministically held at the second call; no race.
- **WR-04/WR-05 (old):** Both vacuous tests are deleted from `ZonesUIRoutesTest.kt` and `ApplicationTest.kt`.

No injection paths: `parseFilter` output reaches Exposed `eq`/`like` as query parameters, wildcards are stripped before the LIKE term is built, and all HTML/URL output goes through kotlinx.html escaping and `urlEncode`. No hardcoded secrets, no debug artifacts.

New findings from this pass: one main-source edge-case bug in `ZoneRoutes` DELETE that can stop a live local hardware zone, two more tests that cannot fail for the behavior they name (same defect class the fix round was addressing), and a test-isolation leak with active network side effects.

## Warnings

### WR-01: DELETE /zones/{id} can remove and stop a live local hardware zone

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:43-54`
**Issue:** The route's 404 message claims local zones "cannot be deleted", but the guard is only implicit: local zones are normally absent from `ZoneRepository`. If a stale persisted row shares an id with a configured local zone — e.g. a network/firmware zone named `main` is persisted, then a later config change defines local zone `main`; on startup `ZoneRegistry.addNetworkZone` logs "Ignoring network zone 'main': conflicts with a local hardware zone" and keeps the local entry, but the repo row survives — then `DELETE /zones/main` finds the row, deletes it, and `zoneRegistry.removeZone("main")` (`ZoneRegistry.kt:130-134`) unconditionally removes the entry and calls `driver.stop()` on the **local hardware driver**. A live physical display zone is destroyed at runtime by a request the route itself documents as impossible.
**Fix:** Guard on locality before removing, e.g. add `fun isLocal(id: String) = zones[id]?.isLocal == true` to `ZoneRegistry` and in the route:
```kotlin
if (zoneRegistry.isLocal(id)) {
    return@delete call.respond(HttpStatusCode.Conflict, "Local zones cannot be deleted")
}
```
(or make `removeZone` itself refuse `isLocal` entries, which fixes all callers at once).

### WR-02: Vacuous assertion — pagination test can never fail because the page number itself differs

**File:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt:37-47`
**Issue:** "page 2 returns different slice than page 1" asserts `(page1Body == page2Body) shouldBe false`. The response envelope is `HistoryPageResponse(items, page, size, total)`, so page 1's body contains `"page":1` and page 2's contains `"page":2` — the bodies differ **even if both pages return the identical slice**. If `offset` were dropped from `HistoryRepository.findPaginated`, this test would still pass. It provides false coverage for the exact behavior it names.
**Fix:** Assert on the items payload, not the whole body. E.g. seed 25 records with distinct texts and assert disjointness of the item slices:
```kotlin
val items1 = page1Body.substringAfter("\"items\":").substringBefore("],\"page\"")
val items2 = page2Body.substringAfter("\"items\":").substringBefore("],\"page\"")
(items1 == items2) shouldBe false
```
(or deserialize into `HistoryPageResponse` and compare `items` id sets).

### WR-03: Vacuous test — "insert failure" test never invokes the throwing repository

**File:** `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt:85-101`
**Issue:** "insert failure does not prevent displayImmediate from returning a result" builds `mockZoneDriver = mockk<ZoneDriver>(relaxed = true)` with **no stub for `send`**, so the relaxed mock returns `false`. `displayImmediate` with no `zoneId` takes the broadcast path (`ScreenDriverService.kt:84-91`), where `tryInsertHistory` is called only per zone in `broadcastResult.successful` — which is empty. `throwingRepo.insert` is never called; the `RuntimeException("DB down")` is never thrown; the try/catch in `tryInsertHistory` is never exercised. The assertion `result.shouldBeInstanceOf<DisplayResult.Broadcast>()` passes regardless. Delete `tryInsertHistory`'s catch block and this test still passes.
**Fix:** Stub the send to succeed and verify the insert actually fired:
```kotlin
coEvery { mockZoneDriver.send(any(), any(), any(), any(), any()) } returns true
...
val result = svc.displayImmediate("fail-insert", Effect.SCROLL, ConflictPolicy.INTERRUPT)
result.shouldBeInstanceOf<DisplayResult.Broadcast>()
coVerify(exactly = 1) { throwingRepo.insert(any()) }
```

### WR-04: Seeded network zone leaks into shared H2 and spawns reconnect loops in every subsequent test app

**File:** `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt:47-65`
**Issue:** The test upserts zone `test-zone-ui` (ip `192.168.1.99`) into the shared JVM-wide H2 database (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`, `src/test/resources/application.yaml:43`) and never deletes it. Every `testApplication`/`appTest` started afterward in the same JVM reloads it in the `ZoneRegistry` constructor (`ZoneRegistry.kt:49-56`) and calls `NetworkZoneDriver.startConnect()` — an infinite 5-second WebSocket reconnect loop against an unreachable RFC1918 address (`NetworkZoneDriver.kt:35-56`), one per app instance. Consequences: background connection attempts for the remainder of the test JVM, and every later spec's `GET /zones` (API and UI) silently includes this zone — any future test asserting zone counts or exact lists becomes order-dependent on whether `ZonesUIRoutesTest` ran first. This is the concrete, side-effecting instance of the shared-state issue noted as IN-02 in the previous review.
**Fix:** Clean up inside the test after the assertions:
```kotlin
zoneRegistry.removeZone("test-zone-ui")
zoneRepository.delete("test-zone-ui")
```
(longer term, `appTest` in `TestSupport.kt` is the single seam where per-test DB cleanup can live).

## Info

### IN-01: parseFilter blank-handling inconsistency between fields (carried forward)

**File:** `src/main/kotlin/com/anjo/validation/HistoryValidators.kt:10-13`
**Issue:** `effect`/`source`/`zone` use `isNotEmpty()` while `search` uses `isNotBlank()`. `?zone=%20` produces a `" "` filter that matches nothing, while whitespace-only search is dropped. Unchanged since the previous review (was IN-01; not addressed in the fix round, which is acceptable for Info).
**Fix:** Use `isNotBlank()` for all four fields, paired with a `parseFilter` test.

### IN-02: HistoryUIRoutes parses the same parameters twice and passes unnormalized values to the template

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:22-32`
**Issue:** The route reads `effect`/`source`/`zone`/`search` raw and *also* calls `parseFilter`, which re-reads and re-normalizes all four (`sanitizeSearchTerm` runs twice on the same input). Two observable consequences of passing raw values to `historyPage`: (1) for hand-typed `?effect=scroll` the rows are filtered (WR-01 fix behavior) but the dropdown's selected-state comparison (`HistoryPage.kt:57-61`) matches nothing, so the UI shows "All effects" while displaying a filtered page; (2) `highlightText` receives the raw search while the DB filtered on the sanitized term, so `?search=100%` matches rows containing "100" but highlights nothing. Ironic residual duplication in a DRY-consolidation phase.
**Fix:** Derive the display values from the parsed filter: `val effect = filter.effect.orEmpty()`, `val source = filter.source.orEmpty()`, `val zone = filter.zone.orEmpty()`, and pass `sanitizedSearch` to `historyPage` for highlighting (keep `rawSearch` only for the input's `value`).

### IN-03: Incomplete appTest migration plus small test hygiene leftovers (carried forward)

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:33-89`, `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt:17-28,45`
**Issue:** Six `ApplicationTest` tests and the first `FirmwareZoneRoutesTest` test still use raw `testApplication { application { module() } }` boilerplate that `appTest` was created to eliminate (previous IN-03, unaddressed). Additionally: `ApplicationTest.kt:38,85` use Kotlin `assert(...)`, which is a silent no-op unless the JVM runs with `-ea` (Gradle test default enables it, but any other runner makes these assertions vacuous) — use kotest matchers like the rest of the file; the WR-05 deletion left a double blank line at `ApplicationTest.kt:88-89`; and the test name at `FirmwareZoneRoutesTest.kt:45` contains a duplicated word ("FIRMWARE zone zone").
**Fix:** Migrate the eligible tests to `appTest`, replace `assert(x in setOf(...))` with `response.status shouldBeIn validStatuses`, drop the extra blank line, fix the test name.

### IN-04: Possible flake — OFFLINE assertion races the server-side detach

**File:** `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt:45-58`
**Issue:** The test asserts `registry.statusOf("pico-ws-offline") shouldBe "OFFLINE"` immediately after the client's `closeReason.await()` resolves. The client's close handshake completes when the server's close frame arrives; the server handler's `finally { driver.detach() }` (`FirmwareZoneRoutes.kt:23-25`) runs in a separate coroutine when the `incoming` loop terminates and is not ordered-before the client observing the close. Under testApplication's in-process scheduling this usually wins, but it is not guaranteed — a slow dispatcher can assert while the zone is still ONLINE.
**Fix:** Wrap the assertion in kotest's `eventually(1.seconds) { registry.statusOf("pico-ws-offline") shouldBe "OFFLINE" }`.

### IN-05: "size=all" correctness silently coupled to the history retention cap

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:14,21`
**Issue:** `MAX_UI_SIZE = 1000` only shows "all" rows because `HistoryRepository.MAX_ROWS` is also 1000 (`HistoryRepository.kt:103`) — two unrelated constants that must stay in lockstep. If retention grows, `size=all` silently truncates with no indication. Related: the pagination block in `HistoryPage.kt:150` recomputes size as `rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20` without the 1000 cap the route applies, so for `?size=5000` the query and pagination math disagree (currently masked by the same retention cap).
**Fix:** Reference a single shared constant (e.g. expose `HistoryRepository.MAX_ROWS` and set `MAX_UI_SIZE` from it), and pass the route's effective `size` into `historyPage` instead of re-deriving it from `rawSize`.

---

_Reviewed: 2026-07-08T06:11:13Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
