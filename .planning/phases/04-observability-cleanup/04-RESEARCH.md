# Phase 4: Cleanup + Observability - Research

**Researched:** 2026-05-27  
**Domain:** Ktor routing architecture, metrics/observability, code organization  
**Confidence:** HIGH

## Summary

Phase 4 reorganizes the TextReaderRpi codebase into feature-first routing modules, adds a custom JSON metrics endpoint backed by the existing DropwizardMetrics `MetricRegistry`, extends health via a parallel detail route, relocates misplaced DTOs and drivers, and moves deployment artifacts into `.devops/`.

**No new dependencies required.** All libraries already present: DropwizardMetrics, KHealth, Ktor RateLimit, kotlinx-serialization.

## Key Technical Decisions

### KHealth Extension Strategy
KHealth has a fixed response schema (`{healthy: boolean, checks: [...]}`). Rather than monkey-patching its serialization, extend health information via the existing `/health` and `/health/ready` endpoints by keeping KHealth checks as the baseline and adding an extended JSON response model that wraps KHealth status plus additional fields (uptime, memory, display info). The D4-20 adapter/bridge approach means we intercept after KHealth runs checks and enrich the response.

### Metrics Endpoint Architecture
- Serialize `MetricRegistry` snapshots into a grouped JSON schema
- Three groups: `runtime` (JVM uptime/memory), `api` (request counters, 429s), `hardware` (resource slots, retries, display failures)
- Use `groups[]` array structure (D4-18) for dynamic extensibility
- Back with `ManagementFactory.getRuntimeMXBean()` for uptime and `Runtime.getRuntime()` for memory

### Rate Limiting Architecture
- Current: `installApiRateLimiting()` applied at `/api` route level — covers text + display routes
- Gap: Text routes ARE already covered by the parent `/api` rate limiter (D4-14 may already be satisfied)
- New: Dedicated metrics rate limit with milder policy (D4-19) — separate `Route.installMetricsRateLimiting()`
- Implementation: Reuse existing `TokenBucket` pattern from `RateLimiting.kt`

### Feature-First Routing
Current routing structure:
- `routing/Routing.kt` — assembles everything
- `routing/TextRoutes.kt` — text endpoint
- `web/routes/ApiRoutes.kt` — display API routes
- `web/routes/WebRoutes.kt` — HTML page routes

Target structure (D4-22):
- `routing/Routing.kt` — assembly only
- `routing/TextRoutes.kt` — `/api/text` (stays)
- `routing/DisplayRoutes.kt` — `/api/display/*` (consolidated from web/routes/ApiRoutes.kt)
- `routing/HealthRoutes.kt` — `/health`, `/health/ready`, `/health/detail`
- `routing/MetricsRoutes.kt` — `/metrics` (new)

### RecoveryPolicy Refactor
Current implementation is already well-structured. Improvements:
- Extract constants to companion object
- Add KDoc to public API
- Remove noise comments
- Keep the same test contract (existing RecoveryPolicyTest passes unchanged)

### ResourceTracker Monitoring Refactor
- Add `snapshot` property exposing current state as a data class
- Register gauges with MetricRegistry for hardware metrics
- Keep existing acquire/release API for backward compatibility
- Test extension: verify snapshot reflects state changes

## Pitfalls

1. **Import breakage on model move:** Moving DTOs from `com.anjo.api` to `com.anjo.model` requires updating all import sites
2. **Rate limit loss during extraction:** When moving routes, ensure rate limit wrapping follows
3. **Deployment path references:** README and scripts referencing `deployment/` need updating
4. **KHealth conflict:** Don't try to register custom routes on `/health` — KHealth owns those paths

## Validation Architecture

### Test Commands
- Per-task: `./gradlew test`
- Full suite: `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification`

### Test Map
| Requirement | Test Type | Exists? |
|-------------|-----------|---------|
| /metrics returns grouped JSON | integration | ❌ Create |
| /metrics rate limited separately | integration | ❌ Create |
| Health detail extended payload | integration | ❌ Create |
| Text routes rate-limited | integration | ✅ Existing |
| RecoveryPolicy behavior | unit | ✅ Existing |
| ResourceTracker behavior | unit | ✅ Existing |
| Model relocation compiles | build | ✅ Build gate |

