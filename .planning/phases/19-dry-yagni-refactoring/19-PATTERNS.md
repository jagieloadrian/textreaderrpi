# Phase 19: DRY/YAGNI Refactoring - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 3 modified (main code) + 1 new (test-utils) + 16 modified (test files)
**Analogs found:** 4 / 4 (this is a pure refactor — analogs are the files' own current bodies, since the duplication being removed lives in the codebase itself)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` (add `parseFilter`) | validator/utility | request-response (query-param transform) | `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` | exact (same package, same validator-object convention) |
| `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` (call-site simplification) | controller/route | request-response, CRUD (read) | itself — current duplicated block is the analog to remove | exact |
| `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` (call-site simplification) | controller/route (SSR) | request-response | itself + `HistoryRoutes.kt` (shares the parser) | exact |
| `src/test/kotlin/com/anjo/TestSupport.kt` (new) | test utility | request-response (test bootstrap) | `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` (8 duplicated `testApplication{}` blocks) + `src/test/kotlin/com/anjo/ApplicationTest.kt` (heaviest `getBlocking` chain) | exact — pattern extracted directly from these two files |
| 15 non-exempt test files (`HistoryRoutesTest.kt`, `HistoryUIRoutesTest.kt`, `ApplicationTest.kt`, `HistoryRecordingTest.kt`, `ZoneRoutesTest.kt`, `ScheduleRoutesTest.kt`, `ScheduleUIRoutesTest.kt`, `ZonesUIRoutesTest.kt`, `WebRoutesTest.kt`, `WebAndDisplayRoutesTest.kt`, `TextApiRouteTest.kt`, `RateLimitRoutesTest.kt`, `MetricsRoutesTest.kt`, `HealthRoutesTest.kt`, `FirmwareZoneRoutesTest.kt`) | test | request-response | `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` | exact (migrate call sites to `appTest{}`/`dep<T>()`) |
| `LiveRoutesTest.kt`, `FirmwareZoneDriverTest.kt` | test | streaming (SSE/WS) | N/A — EXEMPT, do not touch bootstrap pattern | n/a (locked exclusion, D-06) |

## Pattern Assignments

### `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` (validator, request-response)

**Analog for object shape:** `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` (lines 1-8)
```kotlin
package com.anjo.validation

import com.anjo.model.AddZoneRequest
import com.anjo.model.DisplayType
import io.ktor.server.plugins.requestvalidation.ValidationResult

object ZoneValidators {
    ...
```
Confirms the project convention: validator objects import Ktor framework types directly (`ValidationResult` here; `Parameters` for `HistoryValidators`) — this is precedent for D-01's constraint that `HistoryValidators.kt` (not `model/HistoryFilter.kt`) is the framework-aware layer.

**Current file (full, to extend) — `src/main/kotlin/com/anjo/validation/HistoryValidators.kt`:**
```kotlin
package com.anjo.validation

object HistoryValidators {
    fun sanitizeSearchTerm(input: String): String = input.replace("%", "").replace("_", "")
}
```

**Exact duplicated logic to consolidate (source of truth for the new `parseFilter` body) — `HistoryRoutes.kt` lines 19-23** (identical block repeats at lines 29-33):
```kotlin
val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
val zone = call.request.queryParameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
val search = HistoryValidators.sanitizeSearchTerm(call.request.queryParameters["search"].orEmpty()).takeIf { it.isNotBlank() }
val filter = HistoryFilter(effect = effect, source = source, zone = zone, search = search)
```

**Target signature (add to `HistoryValidators`):**
```kotlin
import io.ktor.http.Parameters
import com.anjo.model.HistoryFilter

fun parseFilter(parameters: Parameters): HistoryFilter {
    val effect = parameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
    val source = parameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
    val zone = parameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
    val search = sanitizeSearchTerm(parameters["search"].orEmpty()).takeIf { it.isNotBlank() }
    return HistoryFilter(effect = effect, source = source, zone = zone, search = search)
}
```
Preserve exact ordering (sanitize BEFORE `takeIf { isNotBlank() }`) — security-relevant per RESEARCH.md V5 note.

---

### `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` (controller, request-response)

**Current file in full** — read above, lines 1-41. Both handlers (`/history` line 16, `/history/export` line 28) each currently repeat lines 19-23 / 29-33 verbatim.

**Call-site replacement (both handlers):**
```kotlin
val filter = HistoryValidators.parseFilter(call.request.queryParameters)
```
`page`/`size` parsing (lines 17-18) stays inline — untouched, different caps not shared with UI route (D-01/research "Pattern 1" note).

**Import to drop:** `HistoryFilter` import stays only if still referenced directly (it is not, after this change — `HistoryFilter` is now only referenced via `HistoryValidators.parseFilter`'s return type, so the explicit `import com.anjo.model.HistoryFilter` at line 3 can be removed unless the compiler still needs it for type inference — verify during implementation).

---

### `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` (controller/SSR, request-response)

**Current file in full** — read above, lines 1-41.

**Keep exactly as-is (raw reads for form redisplay, D-03):** lines 23-27 (`effect`, `source`, `zone`, `expandAll`, `rawSearch` raw `.orEmpty()`/`==` reads) — these feed `historyPage(...)` template args and `exportHref` (line 35), a distinct concern from the parsed filter.

**Replace lines 28-32 (filter construction) with:**
```kotlin
val filter = HistoryValidators.parseFilter(call.request.queryParameters)
```

**Do not touch:** line 34's separate `sanitizedSearch` (used for `exportHref`, not the filter) — this is intentionally NOT derived from `filter.search` (per RESEARCH.md anti-pattern note: parsed filter is uppercased/null-cleared, would break redisplay).

---

### `src/test/kotlin/com/anjo/TestSupport.kt` (new file — test utility)

**Analog / exact duplicated source to extract from:** every `testApplication { application { module() }; client.get("/health"); ... getBlocking<T>(DependencyKey<T>()) }` block in `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` (8 occurrences, e.g. lines 22-25, 42-45, 56-59) and `src/test/kotlin/com/anjo/ApplicationTest.kt` (imports lines 27-30, first test body lines 35-38).

**Imports pattern (from `HistoryRoutesTest.kt` lines 1-17):**
```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
```

**Core repeated bootstrap block being replaced (`HistoryRoutesTest.kt` lines 22-25):**
```kotlin
testApplication {
    application { module() }
    client.get("/health")
    val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
    ...
}
```

**New helper file body (per RESEARCH.md Pattern 2, verified against pinned Ktor 3.5.0 source jars):**
```kotlin
package com.anjo

import io.ktor.client.request.get
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

fun appTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module() }
    client.get("/health")
    block()
}

inline fun <reified T> ApplicationTestBuilder.dep(): T =
    application.dependencies.getBlocking<T>(DependencyKey<T>())
```

**Fixture builder (per RESEARCH.md Pattern 3, sourced from `HistoryRoutesTest.kt` lines 26-30, 46-47):**
```kotlin
fun historyRecord(i: Int, effect: String = "SCROLL", source: String = "IMMEDIATE", scheduleId: String? = null) =
    HistoryRecord(text = "text $i", effect = effect, source = source, scheduleId = scheduleId)
```
Only apply to the two `repeat(25) { i -> ... }` blocks (`HistoryRoutesTest.kt` lines 26-30, 46-47) where the `"text $i"` pattern matches exactly. The `repeat(5)` blocks (lines 60-61, 73-74) use different literal text (`"scroll-text"`, `"blink-text"`, `"imm-text"`, `"sched-text"`) — leave those as direct `HistoryRecord(...)` calls; forcing the builder there would need as many params as literals (D-05 scope note, RESEARCH.md Assumption A2).

**Migrated call-site example (target shape for all 15 non-exempt files):**
```kotlin
test("GET /api/v1/history returns 200 with items page size total envelope") {
    appTest {
        val historyRepository = dep<HistoryRepository>()
        repeat(25) { i -> historyRepository.insert(historyRecord(i, effect = if (i % 2 == 0) "SCROLL" else "BLINK", source = if (i % 3 == 0) "SCHEDULED" else "IMMEDIATE", scheduleId = if (i % 3 == 0) "sched-$i" else null)) }
        val response = client.get("/api/v1/history")
        response.status shouldBe HttpStatusCode.OK
        ...
    }
}
```

**Heaviest single payoff case:** `ApplicationTest.kt` "should resolve all configureDI bindings" test — 15 chained `getBlocking` calls in one test body; migrate to 15 `dep<T>()` one-liners.

---

## Shared Patterns

### Validator-object convention
**Source:** `src/main/kotlin/com/anjo/validation/ZoneValidators.kt` (whole file)
**Apply to:** `HistoryValidators.kt`'s new `parseFilter` function — plain top-level `fun` inside the existing `object HistoryValidators { ... }`, no new class, no DI registration needed (it's a pure function, called directly as `HistoryValidators.parseFilter(...)`, same as `HistoryValidators.sanitizeSearchTerm(...)` is called today).

### Test bootstrap consolidation
**Source:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` (all 8 test blocks) + `src/test/kotlin/com/anjo/ApplicationTest.kt` (DI-heavy test)
**Apply to:** All 15 non-exempt test files listed above. Migration is mechanical: replace `testApplication { application { module() }; client.get("/health"); ... }` with `appTest { ... }`, and `application.dependencies.getBlocking<T>(DependencyKey<T>())` with `dep<T>()`.

### SSE/WS exemption (do not migrate)
**Source:** `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt`, `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt`
**Rule:** These stay on raw `embeddedServer(Netty, port=0)` + CIO client for their SSE-streaming test(s) — locked Phase 15 finding, `testApplication` can't drive SSE. `LiveRoutesTest.kt`'s one non-streaming test (`testApplication { application { module() } }`, no `getBlocking`) is below the 5-line threshold and can be left as-is or trivially migrated — either is correct (RESEARCH.md Pitfall 5).

## No Analog Found

None — every file in scope has a direct, current, in-repo analog (this phase deduplicates the codebase's own existing patterns; no external/greenfield pattern search was needed).

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/validation/`, `src/main/kotlin/com/anjo/routing/`, `src/main/kotlin/com/anjo/routing/ui/`, `src/main/kotlin/com/anjo/model/`, `src/test/kotlin/com/anjo/` (all `testApplication`-using files, 17 total, 15 in scope + 2 exempt)
**Files scanned:** 6 main-code files read in full; 2 test files read in full (`HistoryRoutesTest.kt`, `ApplicationTest.kt` partial) as representative analogs for the remaining 13 non-exempt test files (same mechanical pattern, confirmed present via grep)
**Pattern extraction date:** 2026-07-07
