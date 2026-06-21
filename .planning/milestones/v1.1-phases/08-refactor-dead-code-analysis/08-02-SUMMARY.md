---
phase: 08-refactor-dead-code-analysis
plan: "02"
subsystem: config
tags: [dead-code-removal, config, refactor, kotlin]
dependency_graph:
  requires: [DI-smoke-test-baseline]
  provides: [slimmed-ApplicationConfig, slimmed-ApiConfig, no-dead-config-classes]
  affects:
    - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
    - src/main/kotlin/com/anjo/config/model/ApiConfig.kt
    - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
tech_stack:
  added: []
  patterns: [field-removal, dead-class-deletion, yaml-key-cleanup]
key_files:
  created: []
  deleted:
    - src/main/kotlin/com/anjo/config/model/HardwareConfig.kt
    - src/main/kotlin/com/anjo/config/model/TimingConfig.kt
    - src/main/kotlin/com/anjo/config/model/LoggingConfig.kt
  modified:
    - src/main/kotlin/com/anjo/config/model/ApplicationConfig.kt
    - src/main/kotlin/com/anjo/config/model/ApiConfig.kt
    - src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
decisions:
  - "Removed queueSize from both application.yaml files to satisfy acceptance grep criteria — inert YAML keys removed alongside dead Kotlin field"
metrics:
  duration: "12 minutes"
  completed: "2026-06-15"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 5
  files_deleted: 3
requirements_addressed: [REF-01]
---

# Phase 08 Plan 02: Dead Config Class Removal Summary

**One-liner:** Deleted HardwareConfig, TimingConfig, LoggingConfig; removed hardware/timing/logging fields from ApplicationConfig, queueSize from ApiConfig, and all corresponding wiring from ConfigLoader — full test suite green after removal.

## What Was Built

Executed the REF-01 dead-code removal sweep for the configuration layer:

**Files deleted (3):**
- `HardwareConfig.kt` — 2-field data class, loaded by ConfigLoader but never read by any consumer
- `TimingConfig.kt` — 2-field data class, same situation
- `LoggingConfig.kt` — 2-field data class, same situation

**ApplicationConfig.kt** — Reduced from 8 fields to 5. Removed: `hardware: HardwareConfig`, `timing: TimingConfig`, `logging: LoggingConfig`. Remaining constructor: `display`, `api`, `metrics`, `retryConfig`, `databaseConfig`.

**ApiConfig.kt** — Reduced from 4 fields to 3. Removed: `queueSize: Int`. Remaining: `maxTextLength`, `rateLimitPerMinute`, `metricsRateLimitPerMinute`.

**ConfigLoader.kt** — Removed: 3 dead imports (`HardwareConfig`, `TimingConfig`, `LoggingConfig`); 3 construction blocks (`val hardwareConfig`, `val timingConfig`, `val loggingConfig`); `queueSize` named arg inside `ApiConfig(...)` call; `hardware`, `timing`, `logging` named args inside `ApplicationConfig(...)` return.

**application.yaml (main + test)** — Removed the `queueSize` key line from both files to leave no inert dead config keys.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing cleanup] Removed queueSize from YAML files**
- **Found during:** Task 1 — acceptance criteria grep check
- **Issue:** The plan acceptance criteria required `grep -rn "queueSize" src/main src/test` to return zero matches. After removing the Kotlin field, the YAML key `queueSize: ${API_QUEUE_SIZE:10}` (main) and `queueSize: 10` (test) remained as inert dead configuration that would have failed the acceptance grep.
- **Fix:** Removed the `queueSize:` line from `src/main/resources/application.yaml` and `src/test/resources/application.yaml`.
- **Files modified:** `src/main/resources/application.yaml`, `src/test/resources/application.yaml`
- **Commit:** 9288004

## Acceptance Criteria Verification

```
$ grep -rn "HardwareConfig|TimingConfig|LoggingConfig" src/main src/test
# → No matches

$ grep -rn "queueSize" src/main src/test
# → No matches

$ ls src/main/kotlin/com/anjo/config/model/
ApiConfig.kt  ApplicationConfig.kt  DatabaseConfig.kt  DisplayConfig.kt
LcdConfig.kt  Max7219Config.kt  MetricsConfig.kt  OledConfig.kt  RetryConfig.kt

$ ./gradlew test
BUILD SUCCESSFUL in 33s
```

## Test Results

Full test suite passed — 0 failures, 0 errors, JaCoCo ≥70% gate passed.

## Known Stubs

None — this plan only removes dead code. No stubs introduced.

## Threat Flags

None — config field removal reduces attack surface; no new network endpoints or auth paths.

## Self-Check: PASSED

- [x] `HardwareConfig.kt` does not exist: confirmed
- [x] `TimingConfig.kt` does not exist: confirmed
- [x] `LoggingConfig.kt` does not exist: confirmed
- [x] `grep -rn "HardwareConfig|TimingConfig|LoggingConfig" src/main src/test` returns no matches: PASS
- [x] `grep -rn "queueSize" src/main src/test` returns no matches: PASS
- [x] `ApplicationConfig.kt` primary constructor has exactly 5 fields: display, api, metrics, retryConfig, databaseConfig
- [x] `ApiConfig.kt` has 3 fields: maxTextLength, rateLimitPerMinute, metricsRateLimitPerMinute
- [x] `./gradlew test` exits 0: BUILD SUCCESSFUL
- [x] Commit `9288004` exists: refactor(08-02): remove dead config classes and slim ApplicationConfig/ApiConfig
