---
phase: 19-dry-yagni-refactoring
reviewed: 2026-07-08T00:00:00Z
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

**Reviewed:** 2026-07-08T00:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed the Phase 19 DRY/YAGNI refactor of history and zone routing (`HistoryRoutes`, `HistoryUIRoutes`, `ZoneRoutes`, `HistoryValidators`) plus their test coverage. No hardcoded secrets, no `eval`/dangerous-function usage, no empty catch blocks. Validation stays correctly confined to `HistoryValidators` (the route handlers do not duplicate filter logic). `HistoryValidators.sanitizeSearchTerm` strips SQL `LIKE` wildcards (`%`, `_`) before the term reaches the repository, and all HTML/URL output paths (`historyPage`, `highlightText`, `urlEncode`) route through `kotlinx.html` auto-escaping or `URLEncoder` — no injection path found.

The main defect this pass is a real edge-case bug in `ZoneRoutes` DELETE: the guard against deleting local hardware zones is implicit (relies on local zones never appearing in `ZoneRepository`) rather than explicit, so a specific persisted-row/config-drift scenario can delete and stop a live local display driver despite the route's own error message claiming that's impossible. The remaining findings are test-quality problems: two tests whose assertions can't fail for the behavior they claim to cover, and a test that leaks a live-reconnecting network zone into the shared test database for the rest of the JVM's test run. `AddZoneRequest` also has zero server-side validation on `name`/`type` (not previously flagged) — see IN items below, kept at Info since nothing in this file set treats those fields as trusted for anything beyond a DB primary key / driver-branch string.

## Warnings

### WR-01: DELETE /zones/{id} can remove and stop a live local hardware zone

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:43-54`
**Issue:** The 404 body claims local zones "cannot be deleted", but that's not actually enforced — it's an accident of local zones normally being absent from `ZoneRepository` (`ZoneRegistry.initLocalZone` never persists them; only `zoneRoutes` POST for FIRMWARE/network zones calls `zoneRepository.upsert`). If a network or firmware zone is persisted under id `main`, and a later config change defines a local hardware zone also named `main`, `ZoneRegistry`'s constructor calls `addNetworkZone`/`registerFirmwareZone` for the persisted row, which — per `ZoneRegistry.kt:144-149` (`addNetworkZone`) — detects the local zone already occupies that id and logs `"Ignoring network zone 'main': conflicts with a local hardware zone"`, keeping the local `ZoneEntry` but **leaving the stale row in `ZoneRepository`**. From that point, `DELETE /zones/main` finds the row via `zoneRepository.findById("main")`, deletes it, and calls `zoneRegistry.removeZone("main")` (`ZoneRegistry.kt:130-134`), which unconditionally does `entry.driver.stop()` regardless of `isLocal`. A request the route's own error text says is impossible destroys a live physical display.
**Fix:** Make the locality check explicit instead of relying on DB absence:
```kotlin
// ZoneRegistry.kt
fun isLocal(id: String): Boolean = zones[id]?.isLocal == true

// ZoneRoutes.kt
delete("/{id}") {
    val id = call.parameters["id"]
        ?: return@delete call.respond(HttpStatusCode.BadRequest, "missing id")
    if (zoneRegistry.isLocal(id)) {
        return@delete call.respond(HttpStatusCode.Conflict, "Local zones cannot be deleted")
    }
    val zone = zoneRepository.findById(id)
        ?: return@delete call.respond(HttpStatusCode.NotFound, "Zone not found")
    zoneRepository.delete(id)
    zoneRegistry.removeZone(id)
    ...
}
```
(or refuse `isLocal` entries inside `removeZone` itself, which protects every caller in one place.)

### WR-02: Vacuous assertion — pagination test can never fail because the page number itself differs

**File:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt:37-47`
**Issue:** "page 2 returns different slice than page 1" asserts `(page1Body == page2Body) shouldBe false`. The response envelope is `HistoryPageResponse(items, page, size, total)`, so page 1's serialized body always contains `"page":1` and page 2's `"page":2` — the two bodies differ on that field alone, regardless of whether the `items` slice actually changed. If `offset`/`limit` handling were dropped from the underlying query entirely (both pages returning the same 10 rows), this test would still pass.
**Fix:** Compare only the `items` payload, or deserialize and compare item id sets:
```kotlin
val page1 = Json.decodeFromString<HistoryPageResponse>(page1Body)
val page2 = Json.decodeFromString<HistoryPageResponse>(page2Body)
page1.items.map { it.id } shouldNotBe page2.items.map { it.id }
```

### WR-03: Vacuous test — "insert failure" test never invokes the throwing repository

**File:** `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt:85-101`
**Issue:** "insert failure does not prevent displayImmediate from returning a result" builds `mockZoneDriver = mockk<ZoneDriver>(relaxed = true)` with no stub for `send`, so the relaxed mock returns `false`. `displayImmediate` with no `zoneId` takes the broadcast path (`ScreenDriverService.broadcastImmediate`, `ScreenDriverService.kt:84-90`), which only calls `tryInsertHistory` for ids in `broadcastResult.successful` — empty here since `send` returned `false`. `throwingRepo.insert` (stubbed to throw `RuntimeException("DB down")`) is therefore never invoked, and `tryInsertHistory`'s try/catch is never exercised. The assertion `result.shouldBeInstanceOf<DisplayResult.Broadcast>()` passes regardless of whether the catch-and-continue behavior it's meant to test exists at all.
**Fix:** Stub `send` to succeed and verify the insert actually fired and was swallowed:
```kotlin
coEvery { mockZoneDriver.send(any(), any(), any(), any(), any()) } returns true
...
val result = svc.displayImmediate("fail-insert", Effect.SCROLL, ConflictPolicy.INTERRUPT)
result.shouldBeInstanceOf<DisplayResult.Broadcast>()
coVerify(exactly = 1) { throwingRepo.insert(any()) }
```

### WR-04: Seeded network zone leaks into the shared H2 database and spawns a reconnect loop for the rest of the JVM's test run

**File:** `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt:47-65`
**Issue:** This test upserts zone `test-zone-ui` (ip `192.168.1.99`) into the shared, JVM-wide H2 instance (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`, `src/test/resources/application.yaml:43`) via `zoneRepository.upsert(zone)` and `zoneRegistry.addNetworkZone(zone)`, and never removes it. Every `appTest`/`testApplication` started afterward in the same JVM reloads persisted zones in `ZoneRegistry`'s constructor (`ZoneRegistry.kt:47-60`) and calls `addNetworkZone(zone, client)` again for it, which calls `NetworkZoneDriver.startConnect()` — an unbounded coroutine loop that retries an RFC1918 address every 5 seconds (`NetworkZoneDriver.kt:35-53`) for as long as the JVM lives. Every later spec's `GET /zones` (API and UI) also silently includes this zone, making any future assertion on zone counts or exact zone lists order-dependent on whether this test ran first.
**Fix:** Clean up after the assertions:
```kotlin
zoneRegistry.removeZone("test-zone-ui")
zoneRepository.delete("test-zone-ui")
```
Longer term, `appTest` in `TestSupport.kt` is the natural seam for per-test DB cleanup so individual tests don't have to remember it.

## Info

### IN-01: parseFilter blank-handling inconsistency between fields

**File:** `src/main/kotlin/com/anjo/validation/HistoryValidators.kt:10-13`
**Issue:** `effect`/`source`/`zone` use `isNotEmpty()` while `search` uses `isNotBlank()` (after sanitization). `?zone=%20` (a single space) produces a `" "` zone filter that matches nothing, while a whitespace-only `search` is correctly dropped to `null`. Inconsistent handling of the same "empty-ish" input class across sibling fields in the same function.
**Fix:** Use `isNotBlank()` uniformly for all four fields.

### IN-02: HistoryUIRoutes parses the same query parameters twice and passes the raw (non-normalized) values to the template

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:22-32`
**Issue:** The route reads `effect`/`source`/`zone`/`search` raw via `call.request.queryParameters[...]` and *also* calls `HistoryValidators.parseFilter`, which re-reads and re-normalizes the same four parameters (`sanitizeSearchTerm` runs twice on `search`). Two observable consequences of feeding the raw values into `historyPage` instead of the parsed ones: (1) for `?effect=scroll` the underlying rows are correctly filtered (filter uppercases), but the dropdown's selected-state comparison in `HistoryPage.kt` (`if (effect == "SCROLL") selected = true`) never matches lowercase input, so the UI shows "All effects" selected while actually displaying a filtered page; (2) `highlightText` is called with `rawSearch` while the DB query filtered on the sanitized term, so e.g. `?search=100%` filters rows containing "100" but highlights nothing (the `%` is still present in the term passed to `highlightText`, which won't be found verbatim once mixed with real text). This is duplicated normalization logic in a phase specifically about removing duplication.
**Fix:** Derive the display values from the parsed filter instead of re-reading raw parameters: `val effect = filter.effect.orEmpty()`, `val source = filter.source.orEmpty()`, `val zone = filter.zone.orEmpty()`, and pass `sanitizedSearch` (already computed on line 29) to `historyPage` for highlighting while keeping `rawSearch` only for the search `<input>`'s `value`.

### IN-03: Incomplete appTest migration plus test hygiene leftovers

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:33-89`, `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt:17-28,45`
**Issue:** Six `ApplicationTest` tests and the first `FirmwareZoneRoutesTest` test still use raw `testApplication { application { module() } }` instead of the `appTest` helper in `TestSupport.kt` that exists specifically to remove this boilerplate — inconsistent pattern usage within the same test suite that this phase otherwise DRY'd up. Additionally, `ApplicationTest.kt:38` and `:85` use bare Kotlin `assert(...)`, which is compiled out unless the JVM runs with `-ea` — Gradle's test task enables assertions by default, but the test is silently vacuous under any other runner (IDE run configs without `-ea`, some CI wrappers). The test at `FirmwareZoneRoutesTest.kt:45` also has a duplicated word in its name ("FIRMWARE zone zone returns OFFLINE...").
**Fix:** Migrate the remaining raw `testApplication` blocks to `appTest`, replace `assert(response.status in validStatuses)` with `response.status shouldBeIn validStatuses`, and fix the test name typo.

### IN-04: Possible flake — OFFLINE assertion races the server-side detach

**File:** `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt:45-58`
**Issue:** The test asserts `registry.statusOf("pico-ws-offline") shouldBe "OFFLINE"` immediately after the client's `closeReason.await()` resolves. The client observes the close handshake completing as soon as the server's close frame arrives on the wire; the server-side handler's cleanup (which detaches the driver and flips it to OFFLINE) runs in a separate coroutine triggered by the `incoming` loop terminating, and is not guaranteed to have completed before the client-side await returns. This is timing-dependent, not causally ordered.
**Fix:** Poll instead of asserting immediately, e.g. kotest's `eventually(1.seconds) { registry.statusOf("pico-ws-offline") shouldBe "OFFLINE" }`.

### IN-05: "size=all" correctness is silently coupled to the history retention cap, and pagination math doesn't share the route's size cap

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:14,21`
**Issue:** `MAX_UI_SIZE = 1000` only makes `size=all` show literally all rows because `HistoryRepository.MAX_ROWS` (`HistoryRepository.kt:103`) happens to also be `1000L` — two independently-defined constants in different files that must be kept in lockstep by convention, with nothing enforcing it. If retention is ever raised without updating `MAX_UI_SIZE`, `size=all` will silently truncate the UI history view with no error or indication to the user. Separately, `HistoryPage.kt`'s pagination block recomputes size from `rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20` without the `1..1000` clamp the route applies to the actual query, so a page rendered from `?size=5000` computes pagination math against 5000 while the query itself was clamped to 1000 rows.
**Fix:** Reference a single shared constant (e.g. expose `HistoryRepository.MAX_ROWS` publicly and derive `MAX_UI_SIZE` from it), and pass the route's already-clamped `size` value into `historyPage` instead of having the template re-derive it from `rawSize`.

---

_Reviewed: 2026-07-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
