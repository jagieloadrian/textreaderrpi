---
phase: 01-mvp
plan: 01
type: execute
wave: 1
depends_on: []
autonomous: true
files_modified:
  - src/main/kotlin/com/anjo/Application.kt
  - src/main/kotlin/com/anjo/di/DependencyInjection.kt
  - src/main/kotlin/com/anjo/di/HTTP.kt
  - src/main/kotlin/com/anjo/di/Monitoring.kt
  - src/main/kotlin/com/anjo/di/Serialization.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/routing/TextRoutes.kt
  - src/main/kotlin/com/anjo/routing/RequestValidationConfig.kt
  - src/main/kotlin/com/anjo/routing/ErrorHandling.kt
  - src/test/kotlin/ApplicationTest.kt
  - src/test/kotlin/routing/TextApiRouteTest.kt
  - build.gradle.kts
requirements:
  - MVP-01
  - MVP-02
  - MVP-03
---

## Objective

Execute Phase 1 against the current `com.anjo` layout with endpoint `/api/text`, DI ownership in `com.anjo.di`, validation and error handling in `com.anjo.routing`, and an enforced JaCoCo coverage gate `>=70%`.

## Locked Context Coverage

- Endpoint path remains `/api/text`.
- DI ownership remains in `com.anjo.di`.
- Request validation and error handling remain in `com.anjo.routing`.
- JaCoCo verification is required and must enforce coverage `>=70%`.

---

## Execution Waves

### Wave 1 (no dependencies)

#### Task 1 - Route contract and ownership alignment
- **objective:** Ensure route contract and architecture ownership match locked decisions.
- **read_first:**
  - `.planning/phases/01-mvp/01-CONTEXT.md`
  - `src/main/kotlin/com/anjo/routing/Routing.kt`
  - `src/main/kotlin/com/anjo/routing/TextRoutes.kt`
  - `src/main/kotlin/com/anjo/routing/RequestValidationConfig.kt`
  - `src/main/kotlin/com/anjo/routing/ErrorHandling.kt`
  - `src/main/kotlin/com/anjo/di/DependencyInjection.kt`
- **action:**
  1. Keep `POST /api/text` in route handler.
  2. Keep DI wiring in `com.anjo.di` and avoid moving route-policy concerns into DI files.
  3. Keep request validation and status-page mapping in `com.anjo.routing` modules.
- **acceptance_criteria:**
  - `TextRoutes.kt` contains `post("/api/text")`.
  - `DependencyInjection.kt` owns dependency graph provisioning.
  - `RequestValidationConfig.kt` and `ErrorHandling.kt` remain in `com.anjo.routing`.

#### Task 2 - Test contract alignment
- **objective:** Ensure test contract matches `/api/text` and expected responses.
- **read_first:**
  - `src/test/kotlin/routing/TextApiRouteTest.kt`
  - `src/test/kotlin/ApplicationTest.kt`
  - `src/main/kotlin/com/anjo/routing/TextRoutes.kt`
- **action:**
  1. Keep test requests pointed to `/api/text`.
  2. Verify valid, blank, and oversized input branches return expected statuses.
  3. Keep/adjust smoke test coverage in `ApplicationTest.kt` as needed for current route composition.
- **acceptance_criteria:**
  - Route tests target `/api/text` only.
  - Tests assert `202` success and `400` validation error paths.

### Wave 2 (depends on Wave 1)

#### Task 3 - Coverage gate enforcement and verification
- **objective:** Enforce and validate JaCoCo coverage gate `>=70%` in build flow.
- **depends_on:** Task 1, Task 2
- **read_first:**
  - `build.gradle.kts`
  - `src/test/kotlin/**/*.kt`
- **action:**
  1. Keep JaCoCo plugin and report tasks configured.
  2. Keep `jacocoTestCoverageVerification` with minimum coverage `0.70`.
  3. Run full verification command and resolve any failing tests/coverage regressions.
- **acceptance_criteria:**
  - `jacocoTestCoverageVerification` is configured and wired into verification flow.
  - `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` passes.

---

## Dependency Summary

- Wave 1 tasks can run in parallel:
  - Task 1 (contract/ownership)
  - Task 2 (tests)
- Wave 2 depends on both Wave 1 tasks.

## Success Criteria

- `/api/text` endpoint contract is implemented and tested.
- DI ownership remains in `com.anjo.di`.
- Validation and error handling remain in `com.anjo.routing`.
- All tests pass.
- JaCoCo coverage verification passes with threshold `>=70%`.

## Verification Commands

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
```

## PLANNING COMPLETE
