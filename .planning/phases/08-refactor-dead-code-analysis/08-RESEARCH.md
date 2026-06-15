# Phase 8: Refactor + Dead Code Analysis - Research

**Researched:** 2026-06-15
**Domain:** Kotlin/Ktor dead code removal, DI smoke testing, file rename, bounds fix
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**DI Smoke Test (REF-03)**
- D-01: Binding resolution only — inside a `testApplication {}` block with real H2 in-memory DB, resolve every type registered in `configureDI()` and assert each is non-null. No method calls on resolved services (avoids hardware side effects).
- D-02: All bindings in `configureDI()` must be covered: `AppConfig`, `ApiConfig`, `DisplayConfig`, `Dispatchers.IO`, `MetricRegistry`, `DisplaySelectionService`, `ScreenDriverService`, `MetricsCollector`, `ScheduleRepository`, `EffectRendererFactory`, `SchedulerService`.
- D-03: Real H2 in-memory database — catches Exposed/Flyway wiring failures that a mock would miss.
- D-04: Test lives inside the existing `ApplicationTest.kt` — no new test file.
- D-05: DI smoke test is the FIRST task in the execution plan — must exist before any refactor work starts.

**Dead Code Removal (REF-01)**
- D-06: Delete `HardwareConfig`, `TimingConfig`, `LoggingConfig` classes (src/main/kotlin/com/anjo/config/model/) and their entries in `ConfigLoader.loadConfig()` and `ApplicationConfig`. All three are loaded but never consumed by any production code.
- D-07: Remove `ApiConfig.queueSize` field — no queue exists in the current implementation.
- D-08: Remove `logCurrentData()` from `DisplaySelectionService` and both call sites (`selectDisplayAtStartup`, `selectDisplay`) — Pi4J debug workaround that logs all providers/platforms/properties/registry on every startup and driver switch.
- D-09: Remove `pendingSwitches: ConcurrentLinkedQueue`, `getPendingSwitches()`, and `clearPendingSwitches()` from `DisplaySelectionService` — the queue accumulates switch history but has no production consumer. Update `DisplaySelectionServiceTest` and `DriverIntegrationTest` to remove assertions on pending switches.
- D-10: Remove `readInput()` from `ScreenDriverService` and update `ScreenDriverRecoveryTest` and `ScreenDriverResourceTest` to call `displayImmediate()` directly. `displayImmediate()` already has `effect: Effect = Effect.SCROLL` default, so the migration is a rename with no parameter changes.
- D-11: Scan scope includes both `src/main` and `src/test` — remove dead test helpers and test methods for deleted production code.
- D-12: Executor runs `./gradlew build` first to surface Kotlin unused-symbol warnings, then cross-references with grep for callers. Any additional dead symbols found are removed in the same phase.

**Cleanup Depth (REF-02)**
- D-13: Light cleanup — dead code (as above) + obvious code smells: overly complex conditionals, misleading variable names, redundant null-checks, inconsistent patterns. No structural changes to class hierarchies or public APIs.
- D-14: Rename `ScreenDriver.kt` → `ScreenDriverService.kt` (and the class name if it differs). Update all import references. Consistent with naming convention (other services already named `*Service`).
- D-15: Fix `Font.kt` character bounds checking — add range validation for ASCII character lookup; handle unmapped characters gracefully (return empty/blank row rather than throwing).
- D-16: Remove unused imports across all production files touched during Phase 8 changes.

**Test Coverage (REF-04)**
- D-17: After all cleanup: run JaCoCo report and add Kotest (`should` convention) tests for any path touched by Phase 8 changes that falls below the 70% gate. Coverage gate must pass before phase is complete.

### Claude's Discretion

None specified — all implementation choices are locked.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REF-01 | Dead code analysis and removal — unused configs, unreferenced classes, stale constants, dead code paths | D-06 through D-12; verified targets in codebase audit below |
| REF-02 | Whole-application simplification and cleanup — code smells removed, light refactor | D-13 through D-16; Font.kt bounds, ScreenDriver.kt rename |
| REF-03 | DI smoke test verifies all providers in `configureDI()` are correctly wired (must exist before any DI refactor) | D-01 through D-05; Ktor DI resolution API documented below |
| REF-04 | New code paths covered by tests (Kotest `should` convention, coverage ≥70%) | D-17; JaCoCo gate already configured in build.gradle.kts |
</phase_requirements>

---

## Summary

Phase 8 is a targeted cleanup pass — no new features, no structural reorganisation. It has four concrete work streams: (1) add a DI smoke test in `ApplicationTest.kt` before any edits, (2) delete confirmed dead code already identified in CONTEXT.md, (3) perform light code-smell cleanup and a file rename, and (4) verify the JaCoCo ≥70% gate still passes. The phase is deliberately narrow in scope and operates entirely within existing files.

The most important architectural insight for planning is that `ScreenDriver.kt` already contains a class named `ScreenDriverService` — the rename is a file rename only, not a class rename. All import sites already use the correct type name (`com.anjo.service.ScreenDriverService`), so the rename produces no compile errors and requires no import updates beyond the file-level package declaration (which stays `package com.anjo.service` regardless of filename).

The DI smoke test in Ktor 3.5.0 is straightforward: inside a `testApplication { application { module() } }` block the `Application.dependencies` property returns the live `DependencyRegistry`. Calling `dependencies.getBlocking(DependencyKey<T>())` for each of the 11 registered types triggers real resolution and will throw `MissingDependencyException` if a binding is absent. No mocking is needed and no method calls on resolved objects are required.

**Primary recommendation:** Write the DI smoke test first (Task 1), commit it as a green baseline, then execute dead code removals one file group at a time with `./gradlew test` between groups.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| DI smoke test | Test layer | — | Lives in ApplicationTest.kt; validates the DI wiring layer at startup |
| Dead config removal | Config layer (src/main/kotlin/com/anjo/config/) | — | HardwareConfig/TimingConfig/LoggingConfig are loaded but never injected anywhere downstream |
| DisplaySelectionService cleanup | Service layer | Test layer | Removing pendingSwitches and logCurrentData; test updates follow |
| ScreenDriver.kt rename | Service layer (file system) | — | File rename only; class name already matches convention |
| Font.kt bounds fix | Utils layer | Driver layer (Max7219Matrix uses Font) | Bounds fix is self-contained but must not break Max7219Matrix's happy path |
| Coverage gate | Test layer | — | JaCoCo gate already configured; phase must not drop below 70% |

---

## Codebase Audit: Dead Code Verification

All targets below were verified by grep and file reads in this session. [VERIFIED: source file inspection]

### Config Dead Code

**Files to DELETE:**
- `src/main/kotlin/com/anjo/config/model/HardwareConfig.kt` — `data class HardwareConfig(val spiTimeoutMs: Long, val gpioTimeoutMs: Long)`. Zero callers outside `ApplicationConfig` and `ConfigLoader`.
- `src/main/kotlin/com/anjo/config/model/TimingConfig.kt` — `data class TimingConfig(val scrollSpeed: Long, val refreshRate: Int)`. Zero callers outside `ApplicationConfig` and `ConfigLoader`.
- `src/main/kotlin/com/anjo/config/model/LoggingConfig.kt` — `data class LoggingConfig(val level: String, val format: String)`. Zero callers outside `ApplicationConfig` and `ConfigLoader`.

**Files to EDIT:**
- `ApplicationConfig.kt` — remove fields: `hardware: HardwareConfig`, `timing: TimingConfig`, `logging: LoggingConfig`.
- `ConfigLoader.kt` — remove: `hardwareConfig` local val + construction, `timingConfig` local val + construction, `loggingConfig` local val + construction, all three `import` statements for deleted classes, the three corresponding positional args in the `return ApplicationConfig(...)` call.
- `ApiConfig.kt` — remove field `queueSize: Int`.
- `ConfigLoader.kt` — remove `queueSize = config.propertyOrNull("api.queueSize")?.getString()?.toIntOrNull() ?: 10,` from the `ApiConfig(...)` construction call.

Confirmed: `appConfig.hardware`, `appConfig.timing`, `appConfig.logging` have ZERO references in `src/main` outside of their own definition files. `appConfig.api.queueSize` has ZERO references anywhere in `src/main` or `src/test`. [VERIFIED: source file inspection]

### DisplaySelectionService Dead Code

**Methods to DELETE from `DisplaySelectionService.kt`:**
- `private fun logCurrentData()` — the entire 10-line method body iterates `ctx.providers()`, `ctx.platforms()`, `ctx.properties()`, `ctx.registry()` and logs each entry. This is a Pi4J debug workaround with no production value.
- `getPendingSwitches(): List<String>` — returns `pendingSwitches.toList()`. No production caller.
- `clearPendingSwitches()` — calls `pendingSwitches.clear()`. No production caller.
- The `pendingSwitches = ConcurrentLinkedQueue<String>()` field declaration.
- The `pendingSwitches.offer(normalizedType)` call inside `selectDisplay()`.
- Call to `logCurrentData()` in `selectDisplayAtStartup()`.
- Call to `logCurrentData()` in `selectDisplay()`.

**Test files requiring matching edits:**
- `DisplaySelectionServiceTest.kt` — five tests, three of which call `getPendingSwitches()` or `clearPendingSwitches()`:
  - Test "should load startup driver from display config" — line 28: `service.getPendingSwitches().isEmpty().shouldBeTrue()` — remove this assertion.
  - Test "should stop old driver and update type when switching display" — line 41: `service.getPendingSwitches() shouldContainExactly listOf("LCD")` — remove this assertion.
  - Test "should keep current driver unchanged on failed switch" — line 53: `service.getPendingSwitches().isEmpty().shouldBeTrue()` — remove this assertion.
  - Test "should empty queue after clearing pending switches" — entire test is dead once the methods are removed. DELETE the test.
  - Also remove import `io.kotest.matchers.collections.shouldContainExactly` if it becomes unused.
- `DriverIntegrationTest.kt` — line 44: `selection.getPendingSwitches() shouldContainExactly listOf("LCD", "OLED")` — remove this assertion. Remove import `io.kotest.matchers.collections.shouldContainExactly` if unused.

### ScreenDriver.kt: readInput() Dead Code

**Method to DELETE from `ScreenDriver.kt` (file will be renamed `ScreenDriverService.kt`):**
```
suspend fun readInput(input: String) {
    displayImmediate(input)
}
```
This is a trivial one-liner wrapper. All call sites are in tests only.

**Test files requiring migration** — replace `service.readInput("text")` with `service.displayImmediate("text")` in:
- `ScreenDriverRecoveryTest.kt` — 4 call sites at lines 33, 43, 52, 60.
- `ScreenDriverResourceTest.kt` — 3 call sites at lines 32, 38, 44.

Note: `ScreenDriverResourceTest.kt` also references metrics named `"textreaderrpi.screenDriver.readInput.*"`. These metric key strings are defined in `ScreenDriverMetrics.from()` inside `model/ScreenDriverMetrics.kt`. After removing `readInput()`, the metric names remain unchanged (they are hard-coded strings, not tied to the method name). The metric assertions in `ScreenDriverResourceTest.kt` remain valid.

### ScreenDriver.kt → ScreenDriverService.kt Rename

The class declaration inside `ScreenDriver.kt` is already `class ScreenDriverService(...)`. [VERIFIED: source file inspection]

The rename is a **file rename only**:
- Git operation: `git mv src/main/kotlin/com/anjo/service/ScreenDriver.kt src/main/kotlin/com/anjo/service/ScreenDriverService.kt`
- No class name changes required.
- No import updates required anywhere (all importers already use `import com.anjo.service.ScreenDriverService`).
- The `package com.anjo.service` declaration at the top of the file is unaffected by the filename.

### Font.kt Bounds Fix

Current `Font.kt` is an `object` containing `val asciiFont: Map<Char, ByteArray>`. The map covers: ASCII punctuation (0x20–0x2F), digits (0x30–0x39), uppercase A–Z, lowercase a–z, and five Polish characters (ą, ę, ł, ś, ż). Any character not in the map returns `null` from `asciiFont[char]`.

Current callers: `Max7219Matrix.kt` uses `Font.asciiFont[char]` (verify exact call site pattern during implementation). If `asciiFont[char]` returns null and the caller force-unwraps or doesn't guard, an NPE or index exception occurs. [ASSUMED — exact Max7219Matrix call site not yet inspected]

**Fix pattern (D-15, per CONTEXT.md):** Add a lookup function to `Font` that returns `ByteArray(8) { 0 }` (8 blank rows, consistent with space character rendering) for any unmapped character:

```kotlin
fun getChar(char: Char): ByteArray = asciiFont[char] ?: ByteArray(8) { 0 }
```

Or if `Max7219Matrix` already uses a safe call (`asciiFont[char] ?: ...`), the fix may be purely defensive documentation. Inspect `Max7219Matrix.kt` before writing the fix.

Note: The existing space character `' '` maps to `byteArrayOf(0,0,0,0,0)` (5 bytes). The blank row fallback `ByteArray(8) { 0 }` is 8 bytes — align with whatever width Max7219Matrix expects. [ASSUMED — byte width requirement depends on Max7219Matrix rendering logic, verify before implementing]

---

## Standard Stack

No new packages are installed in this phase. All work is within the existing stack.

| Component | Version | Already Present |
|-----------|---------|-----------------|
| Kotlin | 2.3.21 | Yes |
| Ktor | 3.5.0 (incl. `ktor-server-di`) | Yes |
| Kotest | 6.1.11 | Yes |
| JaCoCo | 0.8.14 | Yes |
| H2 | 2.4.240 | Yes (testRuntimeOnly) |

[VERIFIED: source file inspection of ktor-libs.versions.toml and build.gradle.kts]

---

## Package Legitimacy Audit

> No new packages are installed in Phase 8. This section is not applicable.

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
testApplication { module() }
        |
        v
Application.module()
  -> configureDI()           [installs DI plugin, registers 11 bindings]
  -> configureRouting()      [resolves bindings via `by dependencies`]
        |
        v
ApplicationTest DI smoke test
  -> application.dependencies.getBlocking<AppConfig>()
  -> application.dependencies.getBlocking<ApiConfig>()
  -> ... (11 types total)
  -> each shouldNotBeNull
```

Data flow for dead code removal:
```
ConfigLoader.loadConfig()
  -> currently builds HardwareConfig, TimingConfig, LoggingConfig
  -> these are stored in ApplicationConfig fields
  -> ApplicationConfig is provided to DI
  -> but .hardware / .timing / .logging are never accessed downstream
  -> safe to delete: fields, construction, class files, imports
```

### How Ktor DI Resolution Works in Tests

Ktor 3.5.0 `ktor-server-di` stores the resolved `DependencyRegistry` in `application.attributes[DependencyRegistryKey]`. The `Application.dependencies` property getter returns this registry. Inside a `testApplication { application { module() } }` block, `application { ... }` receives an `Application` receiver, making `dependencies` accessible.

Resolution API:
```kotlin
// Type-safe blocking get (synchronous, works outside coroutine scope)
val instance = application.dependencies.getBlocking<AppConfig>(DependencyKey<AppConfig>())

// Or via the inline resolve extension:
// (requires suspend context)
val instance: AppConfig = application.dependencies.resolve<AppConfig>()
```

For the smoke test the most direct approach is:
```kotlin
test("should resolve all DI bindings without error") {
    testApplication {
        application { module() }
        val deps = application.dependencies
        deps.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>()) shouldNotBeNull {}
        deps.getBlocking<ApiConfig>(DependencyKey<ApiConfig>()) shouldNotBeNull {}
        deps.getBlocking<DisplayConfig>(DependencyKey<DisplayConfig>()) shouldNotBeNull {}
        // ... remaining 8 bindings
    }
}
```

`getBlocking` throws `MissingDependencyException` if the key is absent, which fails the test — exactly the desired behaviour. [VERIFIED: Ktor DI source jar inspection — ktor-server-di-jvm-3.5.0-sources.jar]

Import needed: `import io.ktor.server.plugins.di.DependencyKey`
Import needed: `import io.ktor.util.reflect.typeInfo` (only if using `DependencyKey(typeInfo<T>())` form)

The `by dependencies` delegation syntax used in routing also works inside `testApplication` via the same registry, but the `getBlocking` form is more explicit for smoke testing.

### Kotest `should` Convention

All existing tests use `FunSpec` with `test("description") { ... }` blocks and Kotest assertion matchers. New tests follow the same pattern. No `BehaviorSpec` or `ShouldSpec` in this codebase. [VERIFIED: source file inspection]

### JaCoCo Exclusions Already in Place

`build.gradle.kts` already excludes from coverage measurement:
- `com/anjo/driver/**`
- `com/anjo/utils/**`
- `com/anjo/config/model/**`
- `com/anjo/model/**`

This means:
- Deleting `HardwareConfig`, `TimingConfig`, `LoggingConfig` from `config/model/` has no coverage impact (already excluded).
- `Font.kt` is in `utils/` — already excluded. The Font.kt bounds fix does not require new tests to satisfy the coverage gate.
- Coverage is only enforced for `routing/`, `service/`, `db/`, `di/`, `validation/`.

After removing `readInput()` and updating the two test files to call `displayImmediate()` directly, the line coverage for `ScreenDriverService` stays intact because `displayImmediate()` is already exercised by those same tests.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DI binding resolution in tests | Custom reflection scanner | `application.dependencies.getBlocking<T>(DependencyKey<T>())` | Ktor DI already stores the live registry in `attributes`; direct resolution is authoritative |
| Dead code detection | Manual grep only | `./gradlew build` Kotlin unused-symbol warnings first, then grep to confirm callers | Kotlin compiler surfaces warnings for unused `private` members; grep confirms `internal`/`public` members have no callers |
| Coverage measurement | Counting manually | `./gradlew test` runs JaCoCo automatically (configured via `finalizedBy`) | The gate is enforced by Gradle, not by manual inspection |

---

## Common Pitfalls

### Pitfall 1: DI smoke test in ApplicationTest runs `module()` which calls `Pi4J.newAutoContext()`

**What goes wrong:** `configureDI()` calls `Pi4J.newAutoContext()` unconditionally. In CI/test environments without real hardware, Pi4J mock plugin (`pi4j-plugin-mock`) is on the classpath and will be loaded, allowing context creation to succeed. However, if any Pi4J operation beyond context creation is attempted, it may fail.
**Why it happens:** Pi4J 4.0.0's `newAutoContext()` loads all plugins on the classpath including the mock plugin.
**How to avoid:** The DI smoke test only resolves bindings (D-01) — it must NOT call methods on `DisplaySelectionService`, `ScreenDriverService`, etc. Keep all assertions to `shouldNotBeNull` on the resolved instances.
**Warning signs:** Tests that were passing before start throwing `UnsatisfiedLinkError` or `Pi4JException` — means a method call snuck into the smoke test assertions.

### Pitfall 2: Removing `pendingSwitches.offer(normalizedType)` breaks `selectDisplay()` silently

**What goes wrong:** The `offer` call is inside `selectDisplay()`. If the surrounding logic is accidentally touched, the return value logic could change.
**Why it happens:** The line `pendingSwitches.offer(normalizedType)` is embedded in the success branch of `selectDisplay()`. Simple deletion is safe, but copy-paste errors during refactor can change indentation/control flow.
**How to avoid:** Delete only the single `pendingSwitches.offer(normalizedType)` line and the field declaration. Run `./gradlew test` immediately after. The remaining `selectDisplay()` logic is unchanged.

### Pitfall 3: Font.kt blank row byte width mismatch

**What goes wrong:** The fix returns `ByteArray(8) { 0 }` for unmapped chars, but `Max7219Matrix` may expect 5-byte arrays (matching the existing font entries which are `byteArrayOf(X,X,X,X,X)` — 5 bytes).
**Why it happens:** The CONTEXT.md specifies 8 blank rows but existing font values are 5-byte arrays. The 8-byte comment in CONTEXT.md likely refers to the 8-row LED matrix display height, not the glyph column width.
**How to avoid:** Read `Max7219Matrix.kt` before writing the Font.kt fix. Match the byte array length to whatever length the happy-path font entries use (currently 5 bytes for most characters).
**Warning signs:** `Max7219MatrixTest` failures after the Font.kt change.

### Pitfall 4: File rename breaks Kotlin incremental compilation cache

**What goes wrong:** After `git mv ScreenDriver.kt ScreenDriverService.kt`, IntelliJ or Gradle's incremental compilation may cache the old class location and emit "unresolved reference" for `ScreenDriverService` even though the class name is unchanged.
**Why it happens:** The Kotlin incremental compiler tracks source-to-class mappings by file path.
**How to avoid:** Run `./gradlew clean test` (not just `./gradlew test`) after the file rename to force full recompilation.

### Pitfall 5: `ApiConfig` constructor call in ConfigLoader breaks after removing `queueSize`

**What goes wrong:** `ConfigLoader.kt` constructs `ApiConfig(maxTextLength=..., queueSize=..., rateLimitPerMinute=..., metricsRateLimitPerMinute=...)`. Removing `queueSize` from `ApiConfig.kt` without removing the named argument from `ConfigLoader.kt` causes a compile error.
**Why it happens:** `ApiConfig` is a data class; its primary constructor includes `queueSize`.
**How to avoid:** Edit both `ApiConfig.kt` (field) and `ConfigLoader.kt` (argument) in the same commit.

### Pitfall 6: `ApplicationConfig` constructor call in ConfigLoader breaks after field removals

**What goes wrong:** `ConfigLoader.kt` returns `ApplicationConfig(display=..., hardware=..., api=..., timing=..., logging=..., metrics=..., retryConfig=..., databaseConfig)`. Removing three fields from `ApplicationConfig` without updating this construction call causes a compile error.
**Why it happens:** `ApplicationConfig` uses positional constructor syntax for `databaseConfig` (last arg is passed without a name) mixed with named args for others.
**How to avoid:** Edit `ApplicationConfig.kt` and `ConfigLoader.kt` together. After edit, the return becomes `ApplicationConfig(display=..., api=..., metrics=..., retryConfig=..., databaseConfig)`.

---

## Code Examples

### DI Smoke Test Pattern

```kotlin
// Source: Ktor DI source jar (ktor-server-di-jvm-3.5.0-sources.jar, DependencyRegistry.kt)
// Application.dependencies returns DependencyRegistry from application.attributes[DependencyRegistryKey]
// getBlocking<T>(DependencyKey<T>()) resolves synchronously; throws MissingDependencyException if absent

test("should resolve all configureDI bindings without error") {
    testApplication {
        application { module() }
        val deps = application.dependencies
        deps.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>()) shouldNotBeNull {}
        deps.getBlocking<ApiConfig>(DependencyKey<ApiConfig>()) shouldNotBeNull {}
        deps.getBlocking<DisplayConfig>(DependencyKey<DisplayConfig>()) shouldNotBeNull {}
        deps.getBlocking<CoroutineDispatcher>(DependencyKey<CoroutineDispatcher>()) shouldNotBeNull {}
        deps.getBlocking<MetricRegistry>(DependencyKey<MetricRegistry>()) shouldNotBeNull {}
        deps.getBlocking<DisplaySelectionService>(DependencyKey<DisplaySelectionService>()) shouldNotBeNull {}
        deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>()) shouldNotBeNull {}
        deps.getBlocking<MetricsCollector>(DependencyKey<MetricsCollector>()) shouldNotBeNull {}
        deps.getBlocking<ScheduleRepository>(DependencyKey<ScheduleRepository>()) shouldNotBeNull {}
        deps.getBlocking<EffectRendererFactory>(DependencyKey<EffectRendererFactory>()) shouldNotBeNull {}
        deps.getBlocking<SchedulerService>(DependencyKey<SchedulerService>()) shouldNotBeNull {}
    }
}
```

Note on `Dispatchers.IO` binding: `configureDI()` calls `provide { Dispatchers.IO }`. The resolved type key is `CoroutineDispatcher` (the declared type of `Dispatchers.IO`). Use `DependencyKey<CoroutineDispatcher>()` not `DependencyKey<CloseableCoroutineDispatcher>()`.

### readInput() Migration Pattern

```kotlin
// Before (in ScreenDriverRecoveryTest.kt and ScreenDriverResourceTest.kt):
service(driver).readInput("hello")
svc.readInput("text $it")

// After (direct call; displayImmediate has effect: Effect = Effect.SCROLL default):
service(driver).displayImmediate("hello")
svc.displayImmediate("text $it")
```

`displayImmediate` is a `suspend fun`, so the call sites must be inside `runTest` or a coroutine scope. Check existing test structure — `ScreenDriverRecoveryTest` uses `FunSpec` which runs tests as coroutines, so `readInput` (also `suspend`) already works; `displayImmediate` will too.

### Font.kt Bounds Fix Pattern

```kotlin
// In Font object, add a safe lookup function:
fun getChar(char: Char): ByteArray = asciiFont[char] ?: ByteArray(NUM_COLS) { 0 }
// where NUM_COLS matches the byte-array width of existing font entries (verify: currently 5)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `readInput()` wrapping `displayImmediate()` | Call `displayImmediate()` directly | Phase 8 (this phase) | One fewer indirection; metrics key strings stay unchanged |
| Loaded-but-unused `HardwareConfig`/`TimingConfig`/`LoggingConfig` | Removed entirely | Phase 8 | `ApplicationConfig` struct is smaller; `ConfigLoader` reads 3 fewer YAML sections |
| `pendingSwitches` queue accumulating switch history with no consumer | Removed | Phase 8 | `DisplaySelectionService` no longer imports `ConcurrentLinkedQueue` |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Max7219Matrix.kt` uses `Font.asciiFont[char]` in a way that can produce NPE or IndexOutOfBounds on unmapped characters | Common Pitfalls §3, Code Examples §Font | If Max7219Matrix already guards with `?: ByteArray(...)`, the fix is a no-op (harmless but unnecessary) |
| A2 | Existing font entries are 5-byte arrays; blank fallback should be `ByteArray(5) { 0 }` not `ByteArray(8) { 0 }` as stated in CONTEXT.md | Common Pitfalls §3 | If Max7219Matrix expects 8-byte columns, the 5-byte fallback produces display corruption |
| A3 | `Dispatchers.IO` binding resolves under key type `CoroutineDispatcher` (not `CloseableCoroutineDispatcher`) | Code Examples §DI Smoke Test | If Ktor DI infers a more specific type, `getBlocking<CoroutineDispatcher>()` returns null and the smoke test fails; adjust to matching type |

**If this table is empty:** N/A — assumptions are listed above.

---

## Open Questions

1. **Font.kt byte array width**
   - What we know: existing font entries use 5-byte `ByteArray`. CONTEXT.md says "return `ByteArray(8) { 0 }` (8 blank rows)". 8 rows is the MAX7219 display height, 5 columns is the glyph width.
   - What's unclear: whether the font map stores column bitmaps (5 bytes) or row bitmaps (8 bytes).
   - Recommendation: Read `Max7219Matrix.kt` (specifically how it iterates `Font.asciiFont`) before writing the bounds fix. The existing test `Max7219MatrixTest` will catch a width mismatch immediately.

2. **`Dispatchers.IO` DI key type**
   - What we know: `provide { Dispatchers.IO }` stores an instance of `Dispatchers.IO` which is typed as `CloseableCoroutineDispatcher` at runtime but declared as `CoroutineDispatcher` in the Kotlin stdlib.
   - What's unclear: which `TypeInfo` Ktor DI infers for the lambda return type at compile time.
   - Recommendation: If `getBlocking<CoroutineDispatcher>()` fails in the smoke test, try `getBlocking<CloseableCoroutineDispatcher>()`. Can be determined definitively by running the test once.

---

## Environment Availability

This phase is code/config changes only. External dependencies are limited to the existing build toolchain.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Kotlin toolchain | ✓ | Configured in build.gradle.kts | — |
| Gradle | `./gradlew test` | ✓ | Wrapper present | — |
| H2 | DI smoke test (in-memory DB) | ✓ | 2.4.240 (testRuntimeOnly) | — |

**Missing dependencies with no fallback:** none

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (FunSpec style) + JUnit 5 runner |
| Config file | `build.gradle.kts` (`useJUnitPlatform()`) |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew test jacocoTestCoverageVerification` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REF-03 | All 11 DI bindings resolve without error | integration (testApplication) | `./gradlew test --tests "com.anjo.ApplicationTest"` | Yes (test added in Task 1) |
| REF-01 | Dead code removed — build compiles cleanly | build verification | `./gradlew build` | N/A (compilation check) |
| REF-02 | File rename compiles, readInput() removed — existing tests pass | regression | `./gradlew test` | Existing tests updated |
| REF-02 | Font.kt bounds fix — unmapped char returns blank | unit | `./gradlew test --tests "com.anjo.utils.FontTest"` | No — Wave 0 gap |
| REF-04 | JaCoCo ≥70% gate passes after all changes | coverage gate | `./gradlew jacocoTestCoverageVerification` | Already configured |

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test jacocoTestCoverageVerification`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/utils/FontTest.kt` — covers Font.kt bounds fix (REF-02/REF-04). Need test for unmapped character returns blank ByteArray, and happy path for mapped character.

---

## Security Domain

Phase 8 contains no authentication, input validation changes, cryptography, or session management changes. The only ASVS-applicable category is V5 (Input Validation) via the Font.kt bounds fix, which hardens against unhandled characters. The existing `RequestValidators` (text length check) are not touched.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | partial | Font.getChar() bounds guard |
| V6 Cryptography | no | — |

---

## Project Constraints (from CLAUDE.md)

CLAUDE.md was not found at the project root. Constraints are sourced from the CONTEXT.md decisions and the established project patterns observed in the codebase:

- **No comments in code files** — enforced project rule (CONTEXT.md code_context section). Do not add explanatory comments during cleanup.
- **Validation stays in ScheduleValidators only, never in route handlers** — not directly relevant to Phase 8 (no validation changes).
- **Kotest `should` convention** — all new test assertions must use Kotest matchers (`shouldBe`, `shouldNotBeNull`, etc.) inside `FunSpec.test {}` blocks.
- **No new abstractions** — Phase 8 explicitly prohibits introducing new base classes or patterns (D-13).
- **JaCoCo ≥70% line coverage gate** — must pass after all changes (`./gradlew jacocoTestCoverageVerification`).

---

## Sources

### Primary (HIGH confidence)
- `ktor-server-di-jvm-3.5.0-sources.jar` — `DependencyInjection.kt`, `DependencyResolution.kt`, `DependencyRegistry.kt`: DI resolution API, `getBlocking`, `DependencyKey`, `Application.dependencies` property
- Source file inspection (all files listed in canonical_refs): confirmed dead code targets and their call sites via grep

### Secondary (MEDIUM confidence)
- `ktor-libs.versions.toml` + `build.gradle.kts` — confirmed library versions, JaCoCo gate configuration, test exclusions
- Existing test files — confirmed readInput() call sites, getPendingSwitches() call sites, current test style

### Tertiary (LOW confidence)
- CONTEXT.md §specifics: `ByteArray(8) { 0 }` recommendation for Font.kt — marked [ASSUMED] due to mismatch with observed 5-byte font entries

---

## Metadata

**Confidence breakdown:**
- Dead code targets: HIGH — all targets verified by grep and file reads; zero callers confirmed
- DI smoke test API: HIGH — confirmed from Ktor DI source jar
- Font.kt fix width: LOW — CONTEXT.md says 8 bytes; observed font entries are 5 bytes; requires Max7219Matrix.kt inspection before implementing
- Architecture: HIGH — full file listing and class structure inspected
- Test infrastructure: HIGH — existing test files read; framework and gate configuration confirmed

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 (stable Ktor 3.5.0 API, no dependency changes planned)
