---
plan: 05-01
status: complete
commit: c82ced5
date: 2026-05-27
---

# Summary: Plan 05-01 — Health Endpoint Consolidation + Retry Replacement

## What Was Built

### RetryPolicy.kt — New in-house retry mechanism (D5-02)
- `RetryConfig` data class: `maxAttempts`, `initialDelayMs`, `maxDelayMs`, `factor`
- `retryWithBackoff()` top-level suspend function: exponential backoff with configurable params
- Replaces `RecoveryPolicy` class entirely

### application.yaml — retry.* namespace (D5-03)
- Added `retry:` block: `maxAttempts: 5`, `initialDelayMs: 1000`, `maxDelayMs: 30000`, `factor: 2.0`

### ScreenDriverService migration (D5-02)
- Constructor param changed from `RecoveryPolicy` to `RetryConfig`
- `executeWithRecovery()` now calls `retryWithBackoff(retryConfig) { ... }`
- Catch clause updated from `RecoveryPolicy.TerminalFailure` to `Exception`

### DependencyInjection.kt — wired from config (D5-03)
- `RetryConfig` constructed by reading `retry.*` from `environment.config`
- `RecoveryPolicy` construction removed

### Health endpoint cleanup (D5-01)
- Deleted `HealthRoutes.kt` (contained only `/health/detail`)
- Deleted `HealthModels.kt` (`HealthDetailResponse` only used by removed route)
- Removed `healthRoutes(screenDriverService)` from `Routing.kt`
- `GET /health/detail` now returns 404 (KHealth takes no request for this path)

## Key Decisions

- **No KHealth JSON injection**: KHealth library doesn't support arbitrary JSON field embedding in `/health`. The `/health/detail` endpoint was removed without a replacement — extended metrics remain accessible via `/api/metrics` runtime group.
- **RetryConfig defaults match old RecoveryPolicy semantics**: `maxAttempts=5`, `initialDelayMs=1000`ms — slightly more conservative than old defaults to be safer for hardware.

## Test Changes
- `RecoveryPolicyTest.kt` → deleted (class removed)
- `RetryPolicyTest.kt` → created (5 tests for `retryWithBackoff`)  
- `ScreenDriverRecoveryTest.kt` → updated to use `RetryConfig`/`retryConfig =`
- `ScreenDriverResourceTest.kt` → updated to use `RetryConfig`
- `HealthRoutesTest.kt` → `/health/detail` test changed to assert 404

## Self-Check: PASSED
- `./gradlew test` → BUILD SUCCESSFUL
- `JaCoCo` gate passes
- `RecoveryPolicy.kt` does NOT exist
- `retry:` section present in `application.yaml`
- `HealthRoutes.kt` deleted; `GET /health/detail` → 404

