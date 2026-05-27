---
plan: "04-01"
phase: 4
status: complete
completed: 2026-05-27
---

# Plan 04-01: Routing Consolidation + Rate-Limit Closure — Summary

## What Was Built

- **DisplayRoutes.kt** created as `Route.displayRoutes(screenDriverService)` — feature-first module for `/api/display/status` (GET) and `/api/display/select` (POST)
- **HealthRoutes.kt** created as `Route.healthRoutes(screenDriverService)` — registers `GET /health/detail` with extended JSON payload (status, uptime, memoryUsedMb, memoryMaxMb, displayType, isActive, lastError)
- **Routing.kt** refactored to feature-first assembly: `displayRoutes`, `healthRoutes`, `metricsRoutes` called from top-level; `/api` block retains rate limiting around `textRoutes` + `displayRoutes`
- **RateLimiting.kt** extended with `fun Route.installMetricsRateLimiting(requestsPerMinute: Int)` using separate TokenBucket instance
- `application.yaml` updated with `api.metricsRateLimitPerMinute: 120`
- **RateLimitRoutesTest.kt** extended with rate-limit tests for `POST /api/text` and `GET /api/display/status`, both asserting HTTP 429 + `Retry-After` header

## Key Files Created/Modified

- `src/main/kotlin/com/anjo/routing/DisplayRoutes.kt` (new)
- `src/main/kotlin/com/anjo/routing/HealthRoutes.kt` (new)
- `src/main/kotlin/com/anjo/routing/Routing.kt` (updated)
- `src/main/kotlin/com/anjo/di/RateLimiting.kt` (updated)
- `src/main/resources/application.yaml` (updated)
- `src/test/kotlin/routing/RateLimitRoutesTest.kt` (updated)

## Self-Check: PASSED

- `./gradlew test` — BUILD SUCCESSFUL
- All rate-limit tests pass including POST /api/text and GET /api/display/status assertions
- Feature-first routing assembly verified in Routing.kt

