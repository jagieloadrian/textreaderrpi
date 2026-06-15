# Phase 8: Refactor + Dead Code Analysis - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Perform a full dead code analysis (src/main + src/test), remove all identified dead symbols, install a DI smoke test in ApplicationTest.kt, fix Font.kt bounds checking, rename ScreenDriver.kt to ScreenDriverService.kt, clean obvious code smells and unused imports across all production files. No new features, no structural reorganization beyond naming.

</domain>

<decisions>
## Implementation Decisions

### DI Smoke Test (REF-03)

- **D-01:** Binding resolution only — inside a `testApplication {}` block with real H2 in-memory DB, resolve every type registered in `configureDI()` and assert each is non-null. No method calls on resolved services (avoids hardware side effects).
- **D-02:** All bindings in `configureDI()` must be covered: `AppConfig`, `ApiConfig`, `DisplayConfig`, `Dispatchers.IO`, `MetricRegistry`, `DisplaySelectionService`, `ScreenDriverService`, `MetricsCollector`, `ScheduleRepository`, `EffectRendererFactory`, `SchedulerService`.
- **D-03:** Real H2 in-memory database — catches Exposed/Flyway wiring failures that a mock would miss.
- **D-04:** Test lives inside the existing `ApplicationTest.kt` — no new test file.
- **D-05:** DI smoke test is the FIRST task in the execution plan — must exist before any refactor work starts.

### Dead Code Removal (REF-01)

- **D-06:** Delete `HardwareConfig`, `TimingConfig`, `LoggingConfig` classes (src/main/kotlin/com/anjo/config/model/) and their entries in `ConfigLoader.loadConfig()` and `ApplicationConfig`. All three are loaded but never consumed by any production code.
- **D-07:** Remove `ApiConfig.queueSize` field — no queue exists in the current implementation.
- **D-08:** Remove `logCurrentData()` from `DisplaySelectionService` and both call sites (`selectDisplayAtStartup`, `selectDisplay`) — Pi4J debug workaround that logs all providers/platforms/properties/registry on every startup and driver switch.
- **D-09:** Remove `pendingSwitches: ConcurrentLinkedQueue`, `getPendingSwitches()`, and `clearPendingSwitches()` from `DisplaySelectionService` — the queue accumulates switch history but has no production consumer. Update `DisplaySelectionServiceTest` and `DriverIntegrationTest` to remove assertions on pending switches.
- **D-10:** Remove `readInput()` from `ScreenDriverService` and update `ScreenDriverRecoveryTest` and `ScreenDriverResourceTest` to call `displayImmediate()` directly. `displayImmediate()` already has `effect: Effect = Effect.SCROLL` default, so the migration is a rename with no parameter changes.
- **D-11:** Scan scope includes both `src/main` and `src/test` — remove dead test helpers and test methods for deleted production code.
- **D-12:** Executor runs `./gradlew build` first to surface Kotlin unused-symbol warnings, then cross-references with grep for callers. Any additional dead symbols found are removed in the same phase.

### Cleanup Depth (REF-02)

- **D-13:** Light cleanup — dead code (as above) + obvious code smells: overly complex conditionals, misleading variable names, redundant null-checks, inconsistent patterns. No structural changes to class hierarchies or public APIs.
- **D-14:** Rename `ScreenDriver.kt` → `ScreenDriverService.kt` (and the class name if it differs). Update all import references. Consistent with naming convention (other services already named `*Service`).
- **D-15:** Fix `Font.kt` character bounds checking — add range validation for ASCII character lookup; handle unmapped characters gracefully (return empty/blank row rather than throwing).
- **D-16:** Remove unused imports across all production files touched during Phase 8 changes.

### Test Coverage (REF-04)

- **D-17:** After all cleanup: run JaCoCo report and add Kotest (`should` convention) tests for any path touched by Phase 8 changes that falls below the 70% gate. Coverage gate must pass before phase is complete.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Dead Code Targets
- `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` — remove HardwareConfig, TimingConfig, LoggingConfig wiring
- `src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt` — remove hardware, timing, logging fields
- `src/main/kotlin/com/anjo/config/model/HardwareConfig.kt` — DELETE
- `src/main/kotlin/com/anjo/config/model/TimingConfig.kt` — DELETE
- `src/main/kotlin/com/anjo/config/model/LoggingConfig.kt` — DELETE
- `src/main/kotlin/com/anjo/config/model/ApiConfig.kt` — remove queueSize field
- `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` — remove logCurrentData(), pendingSwitches, getPendingSwitches(), clearPendingSwitches()
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt` — RENAME to ScreenDriverService.kt, remove readInput()
- `src/main/kotlin/com/anjo/utils/Font.kt` — add bounds checking for character code lookup

### DI Graph (for smoke test)
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — complete list of `provide {}` bindings to cover in smoke test
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — where DI smoke test is added (D-04)

### Test Files to Update
- `src/test/kotlin/com/anjo/service/DisplaySelectionServiceTest.kt` — remove getPendingSwitches/clearPendingSwitches assertions
- `src/test/kotlin/com/anjo/driver/DriverIntegrationTest.kt` — remove getPendingSwitches assertion
- `src/test/kotlin/com/anjo/service/ScreenDriverRecoveryTest.kt` — update readInput() → displayImmediate()
- `src/test/kotlin/com/anjo/service/ScreenDriverResourceTest.kt` — update readInput() → displayImmediate()

### Requirements
- `.planning/REQUIREMENTS.md` §Refactor — REF-01 through REF-04 (4 requirements, all must be satisfied)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `testApplication {}` pattern — already used in ApplicationTest.kt; DI smoke test follows the same pattern
- `displayImmediate(text, effect = Effect.SCROLL, conflictPolicy = ConflictPolicy.INTERRUPT)` — default parameters make readInput() migration a direct rename
- Kotest `should` convention — all tests in this project use it; new tests for Font.kt bounds checking follow same style

### Established Patterns
- All class/file names in `service/` already end in `Service` except ScreenDriver.kt — rename makes naming uniform
- `AbstractDisplayDriver` (Phase 6) introduced a base class pattern; Phase 8 cleanup should not introduce new abstractions
- No comments in code files (project rule) — do not add explanatory comments during cleanup

### Integration Points
- `DependencyInjection.kt` → `ApplicationTest.kt` (smoke test resolves same bindings)
- `DisplaySelectionService` cleanup → `ScreenDriverService` (it calls `displaySelectionService.currentDriver()` and `queueDisplaySwitch` — those remain)
- `Font.kt` → `Max7219Matrix.kt` (uses Font to convert chars to bitmap rows — bounds fix must not break the happy path)

</code_context>

<specifics>
## Specific Ideas

- User's framing: "scan all code to remove unused parts, light refactor to simplify" — confirms light cleanup depth, full dead code scan
- `displayImmediate()` already has `effect: Effect = Effect.SCROLL` default — `readInput("text")` → `displayImmediate("text")` is a pure rename
- Font.kt fix: out-of-range char → return `ByteArray(8) { 0 }` (8 blank rows) consistent with how space character renders

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 8-refactor-dead-code-analysis*
*Context gathered: 2026-06-15*
