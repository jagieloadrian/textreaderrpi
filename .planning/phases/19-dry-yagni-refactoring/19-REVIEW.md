---
phase: 19-dry-yagni-refactoring
reviewed: 2026-07-07T14:40:23Z
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
  warning: 5
  info: 3
  total: 8
status: issues_found
---

# Phase 19: Code Review Report

**Reviewed:** 2026-07-07T14:40:23Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed the Phase 19 DRY refactor: `HistoryValidators.parseFilter` consolidation, `ZoneRoutes.buildZone` extraction, and the `TestSupport.kt` test-helper migration across six test files. The diff against `8d88a5f^` was traced line by line; both main and test sources compile clean (`compileKotlin compileTestKotlin`).

The load-bearing invariant survived: `parseFilter` sanitizes the search term (LIKE-wildcard stripping) **before** the blank check (`sanitizeSearchTerm(...).takeIf { it.isNotBlank() }`, `HistoryValidators.kt:13`), so wildcard-only input like `?search=%%%` yields a null filter instead of a blank LIKE term. The Exposed `like` on `HistoryRepository.kt:56/79` receives the term as a query parameter, so no injection path exists. `buildZone` is a faithful extraction of the two duplicated `NetworkZone` constructions, and `AddZoneRequest` remains validated via the `RequestValidation` plugin (`ZoneValidators.validateAddZone`), so no validation was lost.

However: the refactor is claimed behavior-preserving and it is **not** — the UI history route's filter semantics changed (WR-01). The new `parseFilter` function, whose ordering invariant the phase itself calls out as critical, has zero direct unit tests (WR-02). And three migrated/touched tests carry missing or vacuous assertions that cannot fail for the behavior they name (WR-03, WR-04, WR-05).

## Warnings

### WR-01: UI history filter behavior changed — parseFilter uppercases effect/source where the UI route previously did not

**File:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt:27`
**Issue:** Before this phase, the UI route built its filter from the **raw** parameter values (`effect.takeIf { it.isNotEmpty() && it != "ALL" }` — no `uppercase()`, see pre-refactor `HistoryUIRoutes.kt` in commit `8d88a5f^`). The consolidated `HistoryValidators.parseFilter` uppercases `effect` and `source` (`HistoryValidators.kt:10-11`). Two observable behavior changes on `GET /history`:
1. `?effect=scroll` previously filtered on literal `"scroll"` (matching nothing — `HistoryRepository` compares `eq` case-sensitively against stored `"SCROLL"`); it now matches SCROLL records.
2. `?effect=all` / `?source=all` (lowercase) previously produced a filter of literal `"all"` (empty result page); they now normalize to `"ALL"` and disable the filter entirely (full unfiltered page).

The API routes are unchanged, and the UI dropdowns emit uppercase values, so only hand-typed or bookmarked URLs are affected — the new behavior is arguably *more* correct. But this phase was explicitly scoped as behavior-preserving, and this delta is undocumented and untested.
**Fix:** Accept the normalization as intentional (it aligns UI with API) and pin it: add a route test asserting `GET /history?effect=scroll` renders SCROLL rows, and note the delta in the phase SUMMARY. If strict preservation is required instead, `parseFilter` needs a `normalizeCase: Boolean` distinction — not recommended (YAGNI); documenting the improvement is the smaller change.

### WR-02: parseFilter has no direct unit test — the sanitize-before-blank-check ordering invariant is unpinned

**File:** `src/main/kotlin/com/anjo/validation/HistoryValidators.kt:9-15` (missing coverage in `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt`)
**Issue:** `HistoryValidatorsTest` covers only `sanitizeSearchTerm`. The new `parseFilter` — now the single choke point for filter parsing across three routes — has no test at all. In particular, the ordering invariant this phase names as must-stay-intact (sanitize before blank-check, so `?search=%_%` → `search = null`) is not asserted anywhere. A future edit that flips it to `parameters["search"]?.takeIf { it.isNotBlank() }?.let(::sanitizeSearchTerm)` would silently produce an empty-string LIKE term for wildcard-only input, and no test would fail.
**Fix:** Add parseFilter unit tests to `HistoryValidatorsTest.kt`:
```kotlin
test("parseFilter wildcard-only search yields null search filter") {
    HistoryValidators.parseFilter(parametersOf("search", "%_%")).search shouldBe null
}
test("parseFilter effect ALL in any case yields null effect filter") {
    HistoryValidators.parseFilter(parametersOf("effect", "all")).effect shouldBe null
}
test("parseFilter lowercases-in are uppercased") {
    HistoryValidators.parseFilter(parametersOf("effect", "scroll")).effect shouldBe "SCROLL"
}
```

### WR-03: Vacuous assertion — SKIP_NEW test uses INTERRUPT policy and an always-true check

**File:** `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt:52-63`
**Issue:** The test named "dropped SKIP_NEW request produces no extra history record" (a) never uses `ConflictPolicy.SKIP_NEW` — it calls `displayImmediate(..., ConflictPolicy.INTERRUPT)`; and (b) its final assertion is `(afterTotal >= beforeTotal) shouldBe true`, which is trivially true for an append-only history table. The test cannot fail for the behavior it names; it provides false coverage confidence. Pre-existing, but it was migrated in this phase and the migration was the moment to catch it.
**Fix:** Either fix it to actually test SKIP_NEW drop semantics (start a long-running display, issue a second request with `ConflictPolicy.SKIP_NEW`, assert `DisplayResult` is the skip variant and `afterTotal == beforeTotal + 1` for the first record only), or delete it — a test that can't fail is worse than no test.

### WR-04: Missing assertion — empty-state test never asserts the empty state

**File:** `src/test/kotlin/com/anjo/routing/ZonesUIRoutesTest.kt:67-74`
**Issue:** The test named "GET /zones with no zones shows No zones registered empty state" asserts only `body shouldContain "Zones"` — which every `/zones` response satisfies (it duplicates the heading test at line 26). The "No zones registered" empty-state rendering is never asserted; the test passes even if the empty state is removed entirely. The `val body: String` declaration split across lines 69-71 is also dead ceremony.
**Fix:**
```kotlin
test("GET /zones with no zones shows No zones registered empty state") {
    appTest {
        val body = client.get("/zones").bodyAsText()
        body shouldContain "No zones registered"
    }
}
```
If the wired app always registers a local zone (making a true empty state unreachable via `appTest`), rename the test to what it actually verifies or delete it.

### WR-05: Missing assertion — health-response test never checks the application name

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:89-96`
**Issue:** "should include application name in health response" only asserts the status code is 200/503 — a byte-for-byte duplicate of the first test at lines 33-40. The application name is never read from the body. Anyone scanning test names believes the health payload content is covered; it is not.
**Fix:** Assert the body, e.g. `response.bodyAsText() shouldContain "TextReaderRpi"` (or the actual `name` field the health endpoint emits), or delete the duplicate test.

## Info

### IN-01: parseFilter blank-handling inconsistency between fields

**File:** `src/main/kotlin/com/anjo/validation/HistoryValidators.kt:10-13`
**Issue:** `effect`/`source`/`zone` use `isNotEmpty()` while `search` uses `isNotBlank()`. A whitespace-only value (`?zone=%20`) produces a filter of `" "` that matches nothing, while whitespace-only search is correctly dropped. Faithfully preserved from the pre-refactor code, but the consolidation was the natural moment to unify.
**Fix:** Use `isNotBlank()` for all four fields (note: this is another small behavior change — pair with a test).

### IN-02: appTest does not reset shared H2 state between tests

**File:** `src/test/kotlin/com/anjo/TestSupport.kt:11-15`
**Issue:** The test DB is `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` (`src/test/resources/application.yaml:43`) — one named in-memory DB shared by every `testApplication` instance in the JVM, and `DatabaseFactory` never drops tables. History rows accumulate across tests; the migrated tests currently survive only because their assertions filter within their own inserted data (e.g. `shouldNotContain "BLINK"` holds because the query filter excludes cross-test BLINK rows). Any future test asserting totals or unfiltered pages will be order-dependent. `appTest` is now the single seam where a per-test cleanup (e.g. `dep<HistoryRepository>()` truncate) could live cheaply.
**Fix:** No change required now; when the first order-dependent flake appears, add table truncation inside `appTest` before `block()`.

### IN-03: Incomplete migration to appTest

**File:** `src/test/kotlin/com/anjo/ApplicationTest.kt:42-96`, `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt:17-28`
**Issue:** Six ApplicationTest tests and the first FirmwareZoneRoutesTest test still use raw `testApplication { application { module() } }` boilerplate that `appTest` was created to eliminate. The first health test arguably must stay raw (it *is* the warm-up call `appTest` performs), but the routes/static/404 tests and the WebSocket-reject test could migrate. Inconsistency invites drift back to the old pattern.
**Fix:** Migrate the remaining eligible tests to `appTest`, keeping only the startup-behavior tests raw (with a brief rationale if desired).

---

_Reviewed: 2026-07-07T14:40:23Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
