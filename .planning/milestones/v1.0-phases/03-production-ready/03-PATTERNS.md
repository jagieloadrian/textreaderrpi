# Phase 3: Production Ready - Pattern Map
**Generated:** 2026-05-27
**Status:** Ready for planning
## Existing Patterns to Reuse
### Plugin Composition Pattern
- **Analog:** `src/main/kotlin/com/anjo/di/Monitoring.kt`
- **Rule:** Keep cross-cutting concerns as `Application.configureX()` functions in `com.anjo.di` and wire from `Application.module()`.
- **Use in Phase 3:** rate-limit plugin setup, extra monitoring/health wiring.
### Route Composition Pattern
- **Analog:** `src/main/kotlin/com/anjo/routing/Routing.kt`
- **Rule:** Route registration is centralized in `configureRouting()`, while endpoint logic stays in route modules.
- **Use in Phase 3:** add `healthRoutes(...)` and `errorRoutes(...)` modules, keep composition-only router file.
### API Route Module Pattern
- **Analog:** `src/main/kotlin/com/anjo/web/routes/ApiRoutes.kt`
- **Rule:** Route handlers validate input, delegate to service, respond with DTOs.
- **Use in Phase 3:** `/health`, `/health/ready`, `/api/errors` handlers with typed responses.
### Stateful Service Pattern
- **Analog:** `src/main/kotlin/com/anjo/service/ScreenDriver.kt`
- **Rule:** Service owns state via atomics, serializes transitions, and delegates hardware calls via abstraction.
- **Use in Phase 3:** recovery state, circuit breaker counters, background recovery scheduling.
### Config-Driven Runtime Pattern
- **Analog:** `src/main/resources/application.yaml` + `ConfigLoader` usage in DI
- **Rule:** Configuration values come from typed config loaded at startup; avoid magic constants in routes/services.
- **Use in Phase 3:** rate limits, recovery intervals, health thresholds, error queue limits.
### Testing Pattern
- **Analogs:** existing route/service tests under `src/test/kotlin/com/anjo/...`
- **Rule:** Keep tests focused by layer (route contract tests, service state tests) and verify HTTP/status payloads explicitly.
- **Use in Phase 3:** health matrix tests, rate-limit tests, recovery state transition tests.
## New Files -> Closest Analogs
| Target file | Closest analog | Notes |
|---|---|---|
| `src/main/kotlin/com/anjo/web/routes/HealthRoutes.kt` | `src/main/kotlin/com/anjo/web/routes/ApiRoutes.kt` | Same route style, typed responses, service delegation |
| `src/main/kotlin/com/anjo/web/routes/ErrorOpsRoutes.kt` | `src/main/kotlin/com/anjo/web/routes/ApiRoutes.kt` | Operational JSON endpoint pattern |
| `src/main/kotlin/com/anjo/model/HealthResponse.kt` | `src/main/kotlin/com/anjo/model/ErrorResponse.kt` | DTO conventions |
| `src/main/kotlin/com/anjo/service/RecoveryService.kt` | `src/main/kotlin/com/anjo/service/ScreenDriver.kt` | Atomic state + guarded transitions |
| `src/main/kotlin/com/anjo/service/ErrorQueueService.kt` | `src/main/kotlin/com/anjo/service/ReaderInput.kt` + `ScreenDriver.kt` | Thin service with internal bounded storage |
| `src/main/kotlin/com/anjo/di/RateLimiting.kt` | `src/main/kotlin/com/anjo/di/Monitoring.kt` | Plugin wiring as DI module |
| `src/test/kotlin/com/anjo/routing/HealthRoutesTest.kt` | `src/test/kotlin/com/anjo/routing/WebAndDisplayRoutesTest.kt` | HTTP contract tests |
## Guardrails for Planner
- Keep DI/plugin setup in `com.anjo.di`; do not instantiate infra directly in route modules.
- Keep `Routing.kt` composition-only.
- Keep business logic in `com.anjo.service`.
- Keep endpoint DTOs in `com.anjo.model`/API model package.
- Ensure new config keys are added to config models + YAML, not hardcoded.
