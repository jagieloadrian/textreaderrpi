# Phase 19: DRY/YAGNI Refactoring - Research

**Researched:** 2026-07-07
**Domain:** Internal Kotlin/Ktor refactoring (no new libraries) — route param consolidation + Kotest test-helper extraction
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Filter Parsing Consolidation (SC1)**
- **D-01:** The shared query-param → `HistoryFilter` parsing lives in `HistoryValidators` (e.g. `HistoryValidators.parseFilter(parameters): HistoryFilter`). Follows the locked project rule that validation/sanitization lives in validator objects, never route handlers; `sanitizeSearchTerm` is already there. The `model` package stays framework-free (no Ktor `Parameters` import in `HistoryFilter`).
- **D-02:** `HistoryFilter` data class already exists (`model/HistoryFilter.kt`) and `HistoryRepository`/`HistoryService` already consume it. The remaining duplication is the 4-param extraction block repeated in `HistoryRoutes.kt` (twice, lines ~19-22 and ~29-32) and `HistoryUIRoutes.kt` (~23-27) — replace all three with the shared parser.
- **D-03:** The parser returns only `HistoryFilter`. `HistoryUIRoutes` keeps its own one-line `.orEmpty()` reads for form redisplay — no raw+filter holder type (YAGNI).

**Shared Test Helpers (SC2)**
- **D-04:** Shape: a helper-functions file (test-utils), NOT an abstract base spec — Kotest favors composition. Core pieces: an `appTest { }` wrapper around `testApplication { application { module() } }` + the `/health` warm-up call, and a reified DI accessor replacing the repeated `dependencies.getBlocking<X>(DependencyKey<X>())` incantation. Tests stay `FunSpec`.
- **D-05:** Scope includes shared fixture builders for repeated record-insertion loops (e.g. `historyRecord(i)`, zone fixtures) per SC2's "standard fixture builders" wording — these are the biggest copy-paste blocks.
- **D-06:** SSE/WS tests (`LiveRoutesTest`, `FirmwareZoneDriverTest`) are EXEMPT and stay on `embeddedServer(Netty, port=0)` + CIO client — locked pattern from Phase 15: `testApplication` is incompatible with SSE streaming. `ProjectConfig.coroutineTestScope` stays `false`.

**Dedup Scope**
- **D-07:** Beyond the two named SC targets: audit main code for duplication (route error handling, web-template repetition, validator patterns), fix anything that is a clear >5-line copy-paste win, and LIST borderline cases in the plan rather than churning them. No aggressive rewrite sweep of a working v1.2 codebase.
- **D-08:** YAGNI half: delete obviously-unused code encountered during the audit (unused helpers, orphaned models, dead config flags) — no dedicated dead-code hunt. The coverage gate backstops accidental removals.

**Warnings + Coverage Gates (SC3, SC4)**
- **D-09:** SC4 enforcement: compiler-warning baseline diff — capture the warning list before refactoring, assert no new entries after, as a plan verification step. No build-config change (no `allWarningsAsErrors`).
- **D-10:** SC3 needs no new work: the JaCoCo 70% line-coverage gate already exists in `build.gradle.kts` (`jacocoTestCoverageVerification` `violationRules` `minimum = 0.70`, wired via `finalizedBy` on `test`). It must simply stay green.

### Claude's Discretion
- Test-utils file name/location and exact helper signatures.
- Which borderline duplication cases make the "listed, not fixed" cut.
- Whether repository-test setup (H2) shares helpers — apply the >5-line rule case by case.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.

### Canonical References (from CONTEXT.md)
- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` — duplicated param extraction ×2
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` — duplicated param extraction + raw-string form redisplay
- `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` — destination for `parseFilter`; `sanitizeSearchTerm` already here
- `src/main/kotlin/com/anjo/model/HistoryFilter.kt` — existing data class (do not move)
- `build.gradle.kts` — existing JaCoCo gate (lines ~98-147); do not weaken
- `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt` and `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — embeddedServer pattern, exempt from testApplication helper migration
- `src/test/kotlin/com/anjo/ProjectConfig.kt` — `coroutineTestScope` must stay `false`
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REF-05 | Main code and tests have no significant duplication (DRY/YAGNI pass: HistoryFilter data class, shared test base helpers, deduplication of repeated patterns) | SC1 satisfied by `HistoryValidators.parseFilter()` (see Pattern 1); SC2 satisfied by `appTest{}` + `dep<T>()` + fixture builders (see Pattern 2, 3); SC3/SC4 satisfied by existing JaCoCo gate + warning-baseline diff (see Validation Architecture, Code Examples) |
</phase_requirements>

## Summary

This phase touches no new technology. It is a scoped, behavior-preserving refactor of an existing Kotlin 2.3.21 / Ktor 3.5.0 / Kotest 6.1.11 codebase. All research in this document comes from direct inspection of the project's own source files and the official Ktor library source jars pinned in `gradle/ktor-libs.versions.toml` — there are no external packages to evaluate and no framework version questions to resolve.

The two named consolidation targets (SC1: `HistoryFilter` parsing, SC2: shared test setup) are precisely bounded: SC1 is a 4-line query-param extraction block duplicated in exactly 3 route handlers across 2 files; SC2 is a `testApplication { application { module() } }` + `/health` warm-up + `dependencies.getBlocking<T>(DependencyKey<T>())` incantation repeated across ~17 test files. Both extraction points were located and their exact current code captured below. The `HistoryFilter` data class and `HistoryRepository`/`HistoryService` consumers already exist and require no changes — only the route-side parsing is duplicated.

Two library APIs needed verification for the test-helper design (`getBlocking` signature and `ApplicationTestBuilder.application` visibility) — both were confirmed by reading the exact pinned-version Ktor source jars, not training memory, so the reified DI-accessor helper design below is HIGH confidence, not assumed.

**Primary recommendation:** Two plans, matching SC1/SC2 split already estimated in ROADMAP: Plan A = `HistoryValidators.parseFilter()` extraction (SC1) + main-code dedup audit (D-07/D-08) + warning baseline capture; Plan B = test-utils helper file (SC2) + fixture builders, migrated file-by-file across the 15 non-exempt test files, with coverage/warning gates re-verified at the end.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| History query-param → `HistoryFilter` parsing | API/Backend (validation layer) | — | Project rule: parsing/sanitization lives in validator objects (`HistoryValidators`), never in route handlers. `model/HistoryFilter.kt` stays framework-free (no Ktor `Parameters` import). |
| History UI form redisplay (raw string echo for repopulating filter dropdowns) | Frontend Server (SSR) | API/Backend (shares the same parser for the actual filter) | `HistoryUIRoutes` renders server-side HTML via Ktor HTML DSL in the same process; it needs raw un-uppercased strings for the form in addition to the parsed `HistoryFilter` — two distinct reads of the same `Parameters`, by design (D-03). |
| Shared test bootstrapping (`appTest{}`, DI accessor, fixture builders) | Test tier (not a runtime tier) | — | Kotest helper functions consumed by test classes only; no production code path. |
| Compiler-warning baseline / JaCoCo coverage gate | Build tooling (Gradle) | — | `build.gradle.kts` configuration, enforced at build time, not part of the app's request-handling tiers. |

This is a single-process Ktor monolith (SSR HTML DSL + JSON API in one server) — there is no separate browser/CDN tier for this phase; "Frontend Server (SSR)" and "API/Backend" both execute in the same JVM process.

## Standard Stack

No new dependencies. Ladder check (per project convention): the codebase already has the primitives needed —

### Core (existing, no version changes)
| Library | Version | Purpose | Why Standard (for this phase) |
|---------|---------|---------|--------------------------------|
| Kotest `FunSpec` | 6.1.11 [VERIFIED: gradle/ktor-libs.versions.toml] | Existing test style across all 27 test classes | D-04 locks scope to composition (top-level helper functions), not inheritance — Kotest's own guidance favors composition over `AbstractFunSpec` base classes |
| Ktor `ktor-server-di` | 3.5.0 [VERIFIED: gradle/ktor-libs.versions.toml] | `DependencyKey<T>()` + `DependencyResolver.getBlocking<T>()` | Already in use; the shared accessor wraps these two existing calls, does not replace them |
| Ktor `ktor-server-test-host` | 3.5.0 [VERIFIED: gradle/ktor-libs.versions.toml] | `testApplication { }`, `ApplicationTestBuilder` | Already in use across all non-exempt test files |
| JaCoCo | 0.8.14 [VERIFIED: gradle/ktor-libs.versions.toml] | Coverage gate (SC3) | Already wired in `build.gradle.kts`; no change needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Helper-functions file (test-utils) | Abstract base `FunSpec` class (e.g. `abstract class RouteTestBase : FunSpec()`) | Rejected per D-04 — Kotest community and this project favor composition; a base class also complicates the SSE-exempt files (D-06) which must NOT get the `testApplication` wrapper, creating an awkward "opt-out" subclass hierarchy instead of a simple "don't call the helper" choice |
| `HistoryValidators.parseFilter()` returning `HistoryFilter` only | A combined "raw + filter" holder type | Rejected per D-03 (YAGNI) — `HistoryUIRoutes` only needs 3 raw strings for redisplay; a holder type adds a class for a problem solvable with 3 existing one-line reads |

**Installation:** None — no new dependencies for this phase.

**Version verification:** All versions above were read directly from `gradle/ktor-libs.versions.toml` in this repo (the actual resolved versions the build uses), not looked up externally.

## Package Legitimacy Audit

**Not applicable.** This phase introduces zero new external packages (pure internal refactor). Skip the Package Legitimacy Gate.

## Architecture Patterns

### System Architecture Diagram

```
Browser/API client
      │
      ▼
GET /api/v1/history ──┐
GET /api/v1/history/export ──┤
      │                │  (HistoryRoutes.kt — JSON API)
      ▼                │
Parameters ──► HistoryValidators.parseFilter(parameters) ──► HistoryFilter
      │                                                          │
      │                                                          ▼
      │                                        HistoryService.findPaginated(filter, page, size)
      │                                        HistoryService.exportCsv(filter)
      │                                                          │
      ▼                                                          ▼
GET /history (HistoryUIRoutes.kt — SSR HTML)              HistoryRepository (Exposed)
      │
      ├─► same HistoryValidators.parseFilter(parameters) call ──► HistoryFilter (for the actual query)
      └─► separate raw parameters["effect"|"source"|"zone"] reads ──► form redisplay values (BaseLayout/historyPage template)
```

Test-side flow (SC2):

```
Kotest test { } block
      │
      ▼
appTest { ... }                         (new shared helper)
      │  internally: testApplication { application { module() }; client.get("/health") }
      ▼
block: suspend ApplicationTestBuilder.() -> Unit
      │
      ├─► dep<HistoryRepository>()      (new reified DI accessor, replaces
      │                                   application.dependencies.getBlocking<T>(DependencyKey<T>()))
      └─► historyRecord(i), zoneFixture(...) (new fixture builders, replace repeat(n) { HistoryRecord(...) } loops)
```

### Recommended Project Structure
No new directories. One new file:
```
src/test/kotlin/com/anjo/
└── TestSupport.kt      # or similar — appTest{}, dep<T>(), fixture builders (Claude's discretion on name/location per D-04's scope note)
```

### Pattern 1: Shared validator parse function (SC1)
**What:** A single `HistoryValidators.parseFilter(parameters: Parameters): HistoryFilter` replacing the 4-line block duplicated 3×.
**When to use:** Any route handler that needs a `HistoryFilter` from query parameters.
**Example (exact current duplicated logic — this is the body to move, not redesign):**
```kotlin
// Source: current src/main/kotlin/com/anjo/routing/HistoryRoutes.kt lines 19-23 (appears twice) and
// src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt lines 23-32 (equivalent logic, no uppercase)
object HistoryValidators {
    fun sanitizeSearchTerm(input: String): String = input.replace("%", "").replace("_", "")

    fun parseFilter(parameters: Parameters): HistoryFilter {
        val effect = parameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = parameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val zone = parameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
        val search = sanitizeSearchTerm(parameters["search"].orEmpty()).takeIf { it.isNotBlank() }
        return HistoryFilter(effect = effect, source = source, zone = zone, search = search)
    }
}
```
**Important — do NOT import `io.ktor.http.Parameters` into `model/HistoryFilter.kt`.** `Parameters` is imported only in `HistoryValidators.kt` (already a framework-aware layer — `ZoneValidators.kt` imports `io.ktor.server.plugins.requestvalidation.ValidationResult`, so this is consistent with the existing pattern). This satisfies D-01's "model package stays framework-free" constraint.

**Call-site change in `HistoryRoutes.kt` (both handlers):**
```kotlin
val filter = HistoryValidators.parseFilter(call.request.queryParameters)
```
`page`/`size` parsing is NOT part of `parseFilter` — it stays inline in each route because the two call sites have different bounds (API: `size` 1-200 default 20; UI: `size` 1-1000 default 20, plus `"all"` string handling via `MAX_UI_SIZE`). Folding page/size into the shared parser would either lose the differing caps or require a parameter to carry them — not worth it for 1-2 lines that already differ per caller.

**Call-site change in `HistoryUIRoutes.kt`:** replace the filter-construction lines (not the raw `effect`/`source`/`zone`/`rawSearch` reads used for form redisplay and the export link) with:
```kotlin
val filter = HistoryValidators.parseFilter(call.request.queryParameters)
```
Keep the existing separate raw reads (`effect`, `source`, `zone`, `rawSearch`) exactly as-is — they feed the template's redisplay values and `exportHref`, which is a distinct concern from the parsed filter (D-03).

### Pattern 2: Shared test bootstrap helper (SC2)
**What:** An `appTest { }` function wrapping `testApplication { application { module() } }` + the `/health` warm-up, plus a reified DI accessor.
**When to use:** Any of the 15 non-SSE/WS route/service test files (all except `LiveRoutesTest.kt` and `FirmwareZoneDriverTest.kt`, which stay on `embeddedServer`).
**Example:**
```kotlin
// New file, e.g. src/test/kotlin/com/anjo/TestSupport.kt
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
Verified against the pinned Ktor 3.5.0 source jars (see Sources):
- `getBlocking<T>(key: DependencyKey): T` is a plain (non-suspend) extension on `DependencyResolver` — `jvmMain/io/ktor/server/plugins/di/DependencyResolution.jvm.kt:15` in `ktor-server-di-jvm-3.5.0-sources.jar`.
- `DependencyKey<T>(name, qualifier)` is `public inline fun <reified T> DependencyKey(...)` — `commonMain/io/ktor/server/plugins/di/DependencyInjection.kt:306` in the same artifact.
- `ApplicationTestBuilder.application: Application` is a public property — `commonMain/io/ktor/server/testing/TestApplication.kt:404` in `ktor-server-test-host-jvm-3.5.0-sources.jar`. This is the property tests already reference as `application.dependencies...`; it is distinct from the `application { }` DSL block used to configure the module.

**Call-site change (example from `HistoryRoutesTest.kt`):**
```kotlin
test("GET /api/v1/history returns 200 with items page size total envelope") {
    appTest {
        val historyRepository = dep<HistoryRepository>()
        repeat(25) { i -> historyRepository.insert(historyRecord(i)) }
        val response = client.get("/api/v1/history")
        response.status shouldBe HttpStatusCode.OK
        ...
    }
}
```

### Pattern 3: Fixture builders (D-05)
**What:** A small function replacing the repeated `HistoryRecord(text = "...", effect = ..., source = ...)` construction inside `repeat(n) { }` loops.
**Example:**
```kotlin
fun historyRecord(i: Int, effect: String = "SCROLL", source: String = "IMMEDIATE", scheduleId: String? = null) =
    HistoryRecord(text = "text $i", effect = effect, source = source, scheduleId = scheduleId)
```
**Scope note:** Only `HistoryRoutesTest.kt` has the >5-line repeated insertion-loop pattern (2 occurrences of `repeat(25) { i -> historyRepository.insert(HistoryRecord(...)) }` plus 2 occurrences of `repeat(5) { historyRepository.insert(HistoryRecord(...)) }`). Zone-fixture JSON bodies in `ZoneRoutesTest.kt` (7 `setBody("""{...}""")` calls) are each 1 line and deliberately vary per test case (different IPs/types to hit different validation branches) — these are a **borderline case, listed not fixed** per D-07: consolidating them into a builder would either lose the intentional per-test variation or require as many builder-parameters as inline literals, netting no line reduction.

### Anti-Patterns to Avoid
- **Abstract base `FunSpec` for shared test setup:** Explicitly rejected by D-04. Also breaks the SSE-exempt-file split (D-06) cleanly, since those two files must NOT use the `testApplication` wrapper at all.
- **Uppercasing in the shared parser breaking UI redisplay:** `HistoryUIRoutes` must keep reading raw (non-uppercased) `effect`/`source`/`zone` values separately from calling `parseFilter` — do not try to derive the redisplay strings from the parsed `HistoryFilter` (whose `effect`/`source` are uppercased and `null`-cleared for `"ALL"`/empty, which would break the form's selected-option matching and the CSV export link's original casing).
- **Folding `page`/`size` into `parseFilter`:** the two callers have different valid ranges (200 vs 1000 cap, `"all"` support in UI only) — don't force a shared signature onto values that are supposed to differ.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Shared Kotest test setup | Custom `TestListener`/`BeforeSpecListener` lifecycle hooks | Plain top-level helper function (`appTest { }`) | The duplicated code is a call sequence, not a lifecycle concern — a function is simpler and composes with Kotest's existing per-`test{}` `testApplication{}` scoping (D-04) |
| Query-param → typed filter mapping | A generic reflection-based "form binder" | The existing hand-written `parseFilter` (4 explicit fields) | Only 4 fields, called from 2 files — a generic binder is the textbook YAGNI violation for this scale |

**Key insight:** Every "don't hand-roll" candidate in this phase (a DI-lookup wrapper, a param parser) already has a bounded, small, hand-written solution the codebase already uses correctly — the fix is deduplication of the *call site*, not introduction of new abstraction machinery.

## Common Pitfalls

### Pitfall 1: `application` name collision inside `testApplication { }`
**What goes wrong:** `application { module() }` (the DSL configuration block) and `application` (the `ApplicationTestBuilder.application: Application` property used for `.dependencies.getBlocking(...)`) share the same identifier. It's easy to misread or miswrite the reified helper.
**Why it happens:** Ktor's test DSL intentionally overloads the name; `application { }` is a function call, `application` (no braces) is a property read.
**How to avoid:** The `dep<T>()` helper isolates this distinction in one place — write it once, verify it compiles, then every call site just becomes `dep<T>()`.
**Warning signs:** Compiler errors like "function invocation expected" if `application` is used without `{ }` where a function was intended, or vice versa.

### Pitfall 2: Forcing the `/health` warm-up onto tests whose first call already exercises the target endpoint
**What goes wrong:** `testApplication` defers `module()` execution until the first HTTP call (existing project decision, see STATE.md). Several tests — e.g. in `ApplicationTest.kt` and `ZoneRoutesTest.kt` — never call `/health` first because their very first `client.get(...)` call already IS the endpoint under test, and `getBlocking()` is never used in that test.
**Why it happens:** The `/health` warm-up is only strictly required before a `getBlocking()` call (so DI has resolved by the time you inspect a dependency). Tests that only call HTTP endpoints don't need it.
**How to avoid:** `appTest { }` can safely ALWAYS include the `/health` warm-up (it's idempotent and cheap — this is the D-04-decided design) without breaking assertions, since tests that already assert on `/health` responses do so via their own explicit `client.get("/health")` call inside the block. Confirm this holds for `ApplicationTest.kt`'s "should start application context and serve health endpoint" test specifically if it gets migrated — the extra warm-up call happens before the test's own assertion-bearing call, which is harmless but worth a quick actual-vs-expected check.
**Warning signs:** A test whose assertions are about the *first* HTTP interaction's side effects (none currently observed in this codebase, but worth double-checking `ApplicationTest.kt` during migration).

### Pitfall 3: IDE Extract Method inserting KDoc stubs
**What goes wrong:** Using an IDE refactor action (Extract Function) to pull the duplicated blocks into `parseFilter`/`appTest` can auto-generate `/** ... */` KDoc comment stubs.
**Why it happens:** IntelliJ's Extract Function defaults to scaffolding a doc comment for public functions.
**How to avoid:** Delete any generated KDoc stub immediately — no-comments-in-code is a locked project rule (already flagged as a v1.2 pitfall in STATE.md).
**Warning signs:** A `/**` block appearing on a function you didn't intend to document.

### Pitfall 4: Coverage gate exclusions don't apply to `jacocoTestReport`, only to `jacocoTestCoverageVerification`
**What goes wrong:** `build.gradle.kts`'s `excludes` list (`com/anjo/driver/**`, `com/anjo/utils/**`, `com/anjo/config/model/**`, `com/anjo/model/**`) is applied only inside the `jacocoTestCoverageVerification` task's `classDirectories` filtering — NOT inside `jacocoTestReport`. Reading the plain HTML/XML report percentage will look much lower than the 70% gate actually measures.
**Why it happens:** Two separate task configurations in `build.gradle.kts` (lines ~115-147); only the verification task has the exclusion filter.
**How to avoid:** To check SC3 (coverage stays ≥70%), run `./gradlew jacocoTestCoverageVerification` and check for `BUILD SUCCESSFUL` / a `CoverageVerificationErrors` failure — don't eyeball the `jacocoTestReport` HTML percentage as a proxy.
**Warning signs:** Panicking over a report showing e.g. 25% overall coverage when the actual gated metric (excluding driver/model/config classes) is well above 70%.

### Pitfall 5: `LiveRoutesTest.kt` is only PARTIALLY exempt from the `appTest{}` migration
**What goes wrong:** D-06 names `LiveRoutesTest.kt` and `FirmwareZoneDriverTest.kt` as exempt "SSE/WS tests." In practice, `LiveRoutesTest.kt` has ONE test using plain `testApplication { application { module() } }` (the non-streaming "returns text/event-stream content type" check) and a SECOND test using `embeddedServer(Netty, port=0)` + CIO client for the actual SSE frame assertion. Only the second test is the SSE-incompatible one.
**Why it happens:** SSE streaming genuinely can't be driven through `testApplication`'s virtual client (locked Phase 15 finding), but not every test in that file needs SSE.
**How to avoid:** Treat this as a 2-line, below-threshold case — the first test's `testApplication { application { module() } }` block is only 2 lines duplicated, under the ">5 lines" bar (SC2's own threshold), so leaving it as-is is correct even without invoking the SSE exemption. No action needed either way; don't spend a task trying to force this one test through `appTest{}` for a 2-line save.

## Code Examples

### Compiler-warning baseline (D-09) — capture and diff command
```bash
# Source: verified by running in this repo — current baseline is exactly 2 warnings,
# both pre-existing and unrelated to this phase's scope.
./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all
```
Current baseline output (2026-07-07, before any Phase 19 changes):
```
w: .../src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:7:8 'enum class SpiChipSelect : Enum<SpiChipSelect!>' is deprecated. Deprecated in Java.
w: .../src/main/kotlin/com/anjo/driver/Max7219Matrix.kt:53:25 'enum class SpiChipSelect : Enum<SpiChipSelect!>' is deprecated. Deprecated in Java.
```
Both are in `Max7219Matrix.kt` (Pi4J SPI chip-select enum deprecation), a file **outside** this phase's refactor scope (`HistoryRoutes.kt`, `HistoryUIRoutes.kt`, `HistoryValidators.kt`, test files). SC4 verification = re-run this exact command after the refactor and confirm the warning list is unchanged (same 2 lines, same file) — no new `w:` lines from any touched file.

### JaCoCo gate check (SC3)
```bash
./gradlew jacocoTestCoverageVerification
```
Currently passes (excludes `com/anjo/driver/**`, `com/anjo/utils/**`, `com/anjo/config/model/**`, `com/anjo/model/**`; gate is 70% line coverage via `violationRules { rule { limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = 0.70 } } }` in `build.gradle.kts` lines ~124-149).

## State of the Art

Not applicable — no external ecosystem shifts relevant to this phase; this is dead code / duplication removal within a fixed, already-current stack (Kotlin 2.3.21, Ktor 3.5.0, Kotest 6.1.11).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ApplicationTest.kt` tests can safely receive the unconditional `/health` warm-up in `appTest{}` without altering test intent | Common Pitfalls #2 | Low — a redundant idempotent GET before the test's real assertions; worst case is a slightly slower test, not a false pass/fail. Recommend a quick actual-run check during migration rather than treating this as fully closed. |
| A2 | The `historyRecord(i, ...)` fixture builder default field values (`text = "text $i"`, `effect = "SCROLL"`, `source = "IMMEDIATE"`) won't collide with any test's assumption about literal string content | Pattern 3 | Low — verified the two `repeat(25)` blocks already use exactly this text pattern; the two `repeat(5)` blocks use different literal text (`"scroll-text"`, `"blink-text"`, etc.) and would need the builder's optional params or to stay inline — the plan should decide per-block whether the builder helps or whether some blocks stay as literal `HistoryRecord(...)` calls (D-05 already scopes this to "the biggest copy-paste blocks," which are the two `repeat(25)` loops). |

**If confirmed correct:** no user decisions block planning; both are low-risk implementation-detail judgment calls the planner/implementer can resolve in the two affected test blocks directly.

## Open Questions

1. **Does `ApplicationTest.kt` fall inside the "17 route-test files" migration scope, or should it be treated separately?**
   - What we know: CONTEXT.md's `code_context` section describes "Kotest FunSpec + testApplication... across 17 route-test files" and D-04/D-05 scope the helper to that set. `ApplicationTest.kt` lives at `src/test/kotlin/com/anjo/` (package `com.anjo`, not `com.anjo.routing`) and is a DI/bootstrap smoke test, not a route test — but it uses the identical `testApplication { application { module() } }` + `getBlocking` pattern (8 tests, one with 15 chained `getBlocking` calls).
   - What's unclear: Whether "17 route-test files" was meant literally (routing package only) or loosely (all `testApplication`-based files, of which there are also 17 by the file count found in this research — `grep -rl testApplication` returns exactly 17 files project-wide, including `ApplicationTest.kt`, `HistoryRecordingTest.kt`, and the 2 SSE/WS-exempt files).
   - Recommendation: Include `ApplicationTest.kt` and `HistoryRecordingTest.kt` (service package) in the migration — they have the exact same duplicated `getBlocking`/warm-up pattern the helper targets, and excluding them for no stated reason would leave duplication the phase goal explicitly targets ("test suite has no significant duplication"). Flag the DI-smoke test's "should resolve all configureDI bindings" test (15 `getBlocking` calls in a row) as the single best `dep<T>()` payoff case in the whole test suite.

2. **Should `HistoryUIRoutes.kt`'s raw redisplay reads also move into `HistoryValidators` as a second small helper, or stay inline?**
   - What we know: D-03 says the parser returns only `HistoryFilter`; the raw `.orEmpty()` reads stay in the route. This is 3 one-line reads (`effect`, `source`, `zone` — `rawSearch` is a 4th, already separately named).
   - What's unclear: Whether "stays inline" means literally zero change to those 4 lines, or whether they could still be pulled into a tiny local `data class` / destructuring for readability without violating D-03's "no raw+filter holder type" ban.
   - Recommendation: Leave as 4 separate `val` reads exactly as D-03 specifies — this is already below the 5-line duplication threshold (it appears once, not duplicated) and any grouping would be an unrequested abstraction.

## Environment Availability

Skipped — this phase has no new external tool/service dependencies. Gradle build, JDK 25 toolchain, and JaCoCo are already installed and verified working in this environment (`./gradlew compileKotlin compileTestKotlin` ran successfully during this research session).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (`FunSpec` style) + JUnit5 Platform runner |
| Config file | `src/test/kotlin/com/anjo/ProjectConfig.kt` (`coroutineTestScope = false` — must stay `false` per D-06/locked pattern) |
| Quick run command | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest" --tests "com.anjo.routing.HistoryUIRoutesTest"` (SC1 scope) or `--tests "com.anjo.*"` for broader test-helper migration checks |
| Full suite command | `./gradlew test` (auto-triggers `jacocoTestReport` + `jacocoTestCoverageVerification` via existing `finalizedBy`) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REF-05 (SC1) | `HistoryFilter` parsing consolidated, behavior unchanged | regression (existing) | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest" --tests "com.anjo.routing.HistoryUIRoutesTest"` | ✅ (14 existing tests across the 2 files) |
| REF-05 (SC2) | Test files share helpers, no >5-line copy-paste | structural check (manual/grep, not a runtime test) | `grep -c "testApplication {" src/test/kotlin/com/anjo/routing/*.kt` should drop to near-zero outside the 2 exempt files; full suite must stay green | N/A — this is a code-shape check, not a behavioral test |
| REF-05 (SC3) | JaCoCo line coverage ≥70% | build gate (existing) | `./gradlew jacocoTestCoverageVerification` | ✅ (gate already exists, must stay green) |
| REF-05 (SC4) | Zero new compiler warnings | build gate (new verification step, no new tooling) | `./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all` diffed against the 2-warning baseline captured in this research | ✅ (command verified working; baseline captured above) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest" --tests "com.anjo.routing.HistoryUIRoutesTest"` after SC1 changes; targeted `--tests` for whichever test file was just migrated after each SC2 migration step.
- **Per wave merge:** `./gradlew test` (full suite, all 27 test classes).
- **Phase gate:** Full suite green + `jacocoTestCoverageVerification` green + warning-diff clean before `/gsd-verify-work`.

### Wave 0 Gaps
None — this is a pure refactor of already-tested code. The existing 27 test classes (including the 14 History-specific tests) are the safety net; no new test files are needed or wanted (adding new test infrastructure for a refactor-only phase would itself violate YAGNI). The one new file needed is the test-utils helper file itself (`appTest{}`, `dep<T>()`, fixture builders) — that's implementation, not test-gap-filling.

## Security Domain

`security_enforcement` is not set in `.planning/config.json` (absent = enabled), but this phase introduces no new attack surface — it moves existing, already-reviewed parsing logic (`sanitizeSearchTerm` LIKE-wildcard stripping, already flagged in STATE.md pitfall #3) into a shared function without changing its behavior.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes (unchanged behavior) | `HistoryValidators.sanitizeSearchTerm` (existing `%`/`_` stripping) — must be preserved verbatim inside `parseFilter`; do not "simplify" the LIKE-wildcard stripping while moving it |
| V2/V3/V4/V6 | no | This phase has no auth, session, access-control, or crypto surface — history search/filter is unauthenticated by design (project has no auth per REQUIREMENTS.md "Out of Scope") |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL LIKE wildcard injection via `search` param | Tampering | `HistoryValidators.sanitizeSearchTerm` strips `%`/`_` before the value reaches `HistoryRepository`'s `LIKE` query — verify this call ordering is preserved exactly when consolidated into `parseFilter` (sanitize BEFORE the `takeIf { it.isNotBlank() }` check, matching current code) |

## Sources

### Primary (HIGH confidence — direct source inspection, pinned versions)
- `gradle/ktor-libs.versions.toml` (this repo) — Kotlin 2.3.21, Ktor 3.5.0, Kotest 6.1.11, JaCoCo 0.8.14 resolved versions
- `ktor-server-di-jvm-3.5.0-sources.jar` (`~/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-server-di-jvm/3.5.0/`) — `DependencyResolution.jvm.kt`, `DependencyInjection.kt`: confirms `getBlocking` is non-suspend, `DependencyKey<T>()` is `inline fun <reified T>`
- `ktor-server-test-host-jvm-3.5.0-sources.jar` (`~/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-server-test-host-jvm/3.5.0/`) — `TestApplication.kt`: confirms `ApplicationTestBuilder.application: Application` property
- Direct file reads of all refactor-target files: `HistoryRoutes.kt`, `HistoryUIRoutes.kt`, `HistoryValidators.kt`, `HistoryFilter.kt`, `ZoneValidators.kt`, `build.gradle.kts`, `ProjectConfig.kt`, and all 17 `testApplication`-using test files (or their representative samples)
- `./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode=all` executed in this repo — captured the exact 2-warning baseline

### Secondary (MEDIUM confidence)
None used — no web search was needed; every claim was verifiable directly against this repo's pinned dependencies and source code.

### Tertiary (LOW confidence)
None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; existing versions read directly from the repo's version catalog
- Architecture: HIGH — exact duplication locations and line ranges confirmed by reading the actual files, not the CONTEXT.md line-number estimates (which were close but the exact current file contents are quoted above)
- Pitfalls: HIGH — pitfalls 1, 2, and 5 were discovered by directly diffing test file patterns against each other (e.g., which files call `/health` first vs. not); pitfall 4 confirmed by reading `build.gradle.kts` gate configuration directly

**Research date:** 2026-07-07
**Valid until:** Valid for the life of this phase (internal refactor, no external drift risk) — re-verify only if Phase 19 execution is delayed past a Ktor/Kotest version bump.
