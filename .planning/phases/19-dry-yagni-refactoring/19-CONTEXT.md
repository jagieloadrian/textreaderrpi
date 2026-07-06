# Phase 19: DRY/YAGNI Refactoring - Context

**Gathered:** 2026-07-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Eliminate significant duplication in main code and the test suite (REF-05): consolidate the repeated history-filter query-param parsing, extract shared test setup helpers and fixture builders, and remove clear duplication and dead code found along the way. Pure refactoring — no behavior changes, no new capabilities. JaCoCo line coverage stays ≥70%; the refactoring pass introduces zero new compiler warnings.

</domain>

<decisions>
## Implementation Decisions

### Filter Parsing Consolidation (SC1)
- **D-01:** The shared query-param → `HistoryFilter` parsing lives in `HistoryValidators` (e.g. `HistoryValidators.parseFilter(parameters): HistoryFilter`). Follows the locked project rule that validation/sanitization lives in validator objects, never route handlers; `sanitizeSearchTerm` is already there. The `model` package stays framework-free (no Ktor `Parameters` import in `HistoryFilter`).
- **D-02:** `HistoryFilter` data class already exists (`model/HistoryFilter.kt`) and `HistoryRepository`/`HistoryService` already consume it. The remaining duplication is the 4-param extraction block repeated in `HistoryRoutes.kt` (twice, lines ~19-22 and ~29-32) and `HistoryUIRoutes.kt` (~23-27) — replace all three with the shared parser.
- **D-03:** The parser returns only `HistoryFilter`. `HistoryUIRoutes` keeps its own one-line `.orEmpty()` reads for form redisplay — no raw+filter holder type (YAGNI).

### Shared Test Helpers (SC2)
- **D-04:** Shape: a helper-functions file (test-utils), NOT an abstract base spec — Kotest favors composition. Core pieces: an `appTest { }` wrapper around `testApplication { application { module() } }` + the `/health` warm-up call, and a reified DI accessor replacing the repeated `dependencies.getBlocking<X>(DependencyKey<X>())` incantation. Tests stay `FunSpec`.
- **D-05:** Scope includes shared fixture builders for repeated record-insertion loops (e.g. `historyRecord(i)`, zone fixtures) per SC2's "standard fixture builders" wording — these are the biggest copy-paste blocks.
- **D-06:** SSE/WS tests (`LiveRoutesTest`, `FirmwareZoneDriverTest`) are EXEMPT and stay on `embeddedServer(Netty, port=0)` + CIO client — locked pattern from Phase 15: `testApplication` is incompatible with SSE streaming. `ProjectConfig.coroutineTestScope` stays `false`.

### Dedup Scope
- **D-07:** Beyond the two named SC targets: audit main code for duplication (route error handling, web-template repetition, validator patterns), fix anything that is a clear >5-line copy-paste win, and LIST borderline cases in the plan rather than churning them. No aggressive rewrite sweep of a working v1.2 codebase.
- **D-08:** YAGNI half: delete obviously-unused code encountered during the audit (unused helpers, orphaned models, dead config flags) — no dedicated dead-code hunt. The coverage gate backstops accidental removals.

### Warnings + Coverage Gates (SC3, SC4)
- **D-09:** SC4 enforcement: compiler-warning baseline diff — capture the warning list before refactoring, assert no new entries after, as a plan verification step. No build-config change (no `allWarningsAsErrors`).
- **D-10:** SC3 needs no new work: the JaCoCo 70% line-coverage gate already exists in `build.gradle.kts` (`jacocoTestCoverageVerification` `violationRules` `minimum = 0.70`, wired via `finalizedBy` on `test`). It must simply stay green.

### Claude's Discretion
- Test-utils file name/location and exact helper signatures.
- Which borderline duplication cases make the "listed, not fixed" cut.
- Whether repository-test setup (H2) shares helpers — apply the >5-line rule case by case.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project rules
- `CLAUDE.md` / `.claude/CLAUDE.md` (if present) — no comments in code files; validation lives in validator objects only

### Refactor targets
- `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` — duplicated param extraction ×2
- `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` — duplicated param extraction + raw-string form redisplay
- `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` — destination for `parseFilter`; `sanitizeSearchTerm` already here
- `src/main/kotlin/com/anjo/model/HistoryFilter.kt` — existing data class (do not move)
- `build.gradle.kts` — existing JaCoCo gate (lines ~98-147); do not weaken

### Locked test pattern
- `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt` and `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — embeddedServer pattern, exempt from testApplication helper migration
- `src/test/kotlin/com/anjo/ProjectConfig.kt` — `coroutineTestScope` must stay `false`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HistoryFilter` + `HistoryRepository.findPaginated(filter, page, size)` / `findAll(filter)`: consolidation target already half-done — only route-side parsing remains duplicated
- `HistoryValidators.sanitizeSearchTerm`: existing sanitization the shared parser wraps

### Established Patterns
- Kotest `FunSpec` + `testApplication { application { module() } }` across 17 route-test files, with `client.get("/health")` warm-up and `dependencies.getBlocking<T>(DependencyKey<T>())` DI access — the exact block the `appTest` helper replaces
- Validator objects per domain (`ScheduleValidators`, `HistoryValidators`, `ZoneValidators`)

### Integration Points
- New test-utils file in `src/test/kotlin/com/anjo/` consumed by route-test files
- `HistoryValidators.parseFilter` consumed by `HistoryRoutes` (2 handlers) and `HistoryUIRoutes` (1 handler)

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond the decisions above — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 19-dry-yagni-refactoring*
*Context gathered: 2026-07-06*
