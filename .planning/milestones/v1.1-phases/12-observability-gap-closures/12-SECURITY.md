---
phase: 12
slug: observability-gap-closures
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-17
---

# Phase 12 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser/client → GET /metrics | Untrusted reader can scrape metric counts | Counter values only — no PII, no secrets |
| browser/client → GET /health/detail | Untrusted reader can scrape uptime, memory, zone error strings | Operational data — driver lastError short strings, no stack traces, no secrets |
| browser → unknown GET route | Untrusted path triggers StatusPages NotFound; HTML rendered via prefersHtml() | Static error copy only — no request path echo |
| browser → /openapi (SwaggerUI) | Static Swagger asset serving | API schema — no runtime secrets |
| KHealth healthChecks → ZoneRegistry | In-process read of zone status | Internal zone state — no external input surface |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-12-01 | Information Disclosure | GET /metrics hardware group | accept | Counts (failures/retries/inFlight/skipped) carry no PII; trusted home network per v1.1 out-of-scope auth decision | closed |
| T-12-02 | Denial of Service | GET /metrics scraping | mitigate | `MetricsRoutes.kt:12` — `installMetricsRateLimiting(metricsRateLimitPerMinute)` at 120 req/min | closed |
| T-12-03 | Tampering | Dropwizard Counter concurrency | accept | Dropwizard `Counter.inc()/dec()` is thread-safe by design; null-safe `?.inc()` prevents NPE when metrics disabled | closed |
| T-12-04 | Information Disclosure | GET /health/detail zoneErrors / memory | accept | Error strings are driver `lastError` messages and zone IDs; no secrets/PII; trusted home network per v1.1 out-of-scope auth decision | closed |
| T-12-05 | Denial of Service | GET /health/detail scraping | mitigate | `HealthRoutes.kt:19` — `installMetricsRateLimiting(metricsRateLimitPerMinute)` at 120 req/min, identical to /metrics | closed |
| T-12-06 | Information Disclosure | zoneErrors leaking stack traces | mitigate | `HealthRoutes.kt:30` — `zones.associate { it.id to it.error }` reads only `ZoneStatus.error` (short string / "OFFLINE"), never exception detail | closed |
| T-12-07 | Information Disclosure | HTML 404 page content | accept | `ErrorPage(404, "Page not found")` renders only static copy — no request path echo, no stack trace, no internal detail | closed |
| T-12-08 | Information Disclosure | 500 error page leaking exception detail | mitigate | `ErrorHandling.kt:90` — `exception<Throwable>` renders `ErrorPage(500, "An internal error occurred")` for HTML responses; raw exception not exposed to browser | closed |
| T-12-09 | Spoofing / Tampering | SwaggerUI catch-all intercepting routes | mitigate | `Routing.kt:62` — `swaggerUI(path = "openapi")` is last inside `routing {}` block; StatusPages NotFound resolves unknown routes before swaggerUI catch-all | closed |
| T-12-SC | Tampering | Supply chain (package installs) | mitigate | No new packages introduced in phase 12 — confirmed: zero `build.gradle.kts` changes across all 5 phase 12 commits (b140e0c, 283af97, 656d3dc, 4266051, 608e4f7) | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-12-01 | T-12-01 | Hardware metrics counters carry no PII; application runs on trusted home network; auth is out-of-scope for v1.1 per milestone decision | diether18 | 2026-06-17 |
| AR-12-02 | T-12-03 | Dropwizard Counter is documented thread-safe; null-safe increments prevent NPE without additional synchronization | diether18 | 2026-06-17 |
| AR-12-03 | T-12-04 | /health/detail zoneErrors exposes driver lastError strings and zone IDs; no secrets present; trusted home network | diether18 | 2026-06-17 |
| AR-12-04 | T-12-07 | HTML 404 page renders static copy only; no dynamic content derived from the request | diether18 | 2026-06-17 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-17 | 10 | 10 | 0 | gsd-secure-phase (orchestrator) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-17
