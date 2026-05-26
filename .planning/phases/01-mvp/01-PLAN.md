---
phase: 01-mvp
plan: 01
type: execute
wave: 1
depends_on: []
autonomous: true
files_modified:
  - src/main/kotlin/com/anjo/di/**/*.kt
  - src/main/kotlin/com/anjo/routing/**/*.kt
  - src/main/kotlin/com/anjo/**/*.kt
  - src/test/kotlin/**/*.kt
requirements:
  - MVP-01
  - MVP-02
  - MVP-03
---

## Objective

Regenerate Phase 1 execution plan to match the current Kotlin package layout under `com.anjo`, preserve locked architecture choices, and make `/text` the canonical endpoint path with validation/error handling kept in `com.anjo.routing`.

## Locked Context Coverage

- Implement `/text` as the endpoint path (locked decision).
- Keep DI wiring in `com.anjo.di` (locked decision).
- Keep validation and error setup in `com.anjo.routing` modules (locked decision).
- Remove stale references to `src/main/kotlin` paths that omit `com/anjo`.

---

## Execution Waves

### Wave 1 (no dependencies)

#### Task 1 - Align package boundaries and stale path cleanup
- **objective:** Ensure all planning references and implementation targets use `com.anjo` package paths only.
- **read_first:**
  - `.planning/phases/01-mvp/01-CONTEXT.md`
  - `.planning/phases/01-mvp/01-RESEARCH.md`
  - `src/main/kotlin/com/anjo/**/*.kt`
  - `src/test/kotlin/**/*.kt`
- **action:**
  1. Audit package/module usage in main and test sources under `com.anjo`.
  2. Remove stale plan instructions that reference `src/main/kotlin/...` without `com/anjo`.
  3. Update all task file targets to concrete `com.anjo.*` modules.
- **acceptance_criteria:**
  - No task in the plan references stale non-`com/anjo` source paths.
  - Plan file paths map to existing package structure in current codebase.

#### Task 2 - Route, DI, and validation/error ownership enforcement
- **objective:** Keep responsibilities consistent with current architecture while standardizing endpoint behavior on `/text`.
- **read_first:**
  - `src/main/kotlin/com/anjo/di/**/*.kt`
  - `src/main/kotlin/com/anjo/routing/**/*.kt`
  - route registration entrypoints under `src/main/kotlin/com/anjo/**/*.kt`
- **action:**
  1. Set `/text` as the route path in plan instructions and remove conflicting endpoint references.
  2. Keep dependency registration/config tasks scoped to `com.anjo.di`.
  3. Keep request validation and exception/error mapping tasks scoped to `com.anjo.routing`.
  4. Explicitly avoid moving validation into DI or unrelated feature modules.
- **acceptance_criteria:**
  - Plan references only `/text` for endpoint path.
  - DI tasks point only to `com.anjo.di`.
  - Validation/error tasks point only to `com.anjo.routing`.

### Wave 2 (depends on Wave 1)

#### Task 3 - Update test plan to match `/text` and module boundaries
- **objective:** Ensure tests validate the current endpoint and package ownership contract.
- **depends_on:** Task 1, Task 2
- **read_first:**
  - `src/test/kotlin/**/*.kt`
  - route/validation files under `src/main/kotlin/com/anjo/routing/**/*.kt`
- **action:**
  1. Update/create test tasks for `/text` success and validation failure behavior.
  2. Ensure test coverage includes error response shape from routing-layer handlers.
  3. Keep test references aligned to `com.anjo` package imports and current module structure.
- **acceptance_criteria:**
  - Tests target `/text` (not legacy endpoints).
  - Test plan includes valid request and invalid request/error scenarios.
  - No test task references stale package/file locations.

---

## Dependency Summary

- Wave 1 tasks run in parallel:
  - Task 1 (path/package alignment)
  - Task 2 (route + DI/routing responsibility lock)
- Wave 2:
  - Task 3 depends on outputs of Task 1 and Task 2.

## Success Criteria

- Phase 1 plan is fully aligned to current `com.anjo` codebase structure.
- Locked context constraints are preserved explicitly.
- `/text` is the only endpoint path referenced.
- DI remains in `com.anjo.di`.
- Validation/error setup remains in `com.anjo.routing`.
- JaCoCo coverage verification is part of execution/closeout and enforces `>= 70%` total coverage.
- Every task includes: `objective`, `read_first`, `action`, `acceptance_criteria`.
- Plan is executable with clear wave/dependency sequencing.

## Verification Commands (Coverage Gate)

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
```

## PLANNING COMPLETE


