# Project Research Summary

**Project:** TextReaderRpi
**Domain:** Embedded IoT text-display controller — Kotlin/Ktor on Raspberry Pi 4
**Researched:** 2026-06-11
**Confidence:** HIGH

## Executive Summary

TextReaderRpi v1.1 is an additive milestone on a solid v1.0 foundation. The most important pre-research finding is a **scope correction**: the "scheduler rewrite" goal is misleading — `SchedulerService` is already a pure-coroutine engine (`CoroutineScope + delay + ConcurrentHashMap<String, Job>`). Flaxoos remains only for HTTP rate limiting and must not be removed. The real scheduler work for v1.1 is surgical: add `SKIP_NEW` ConflictPolicy, add a `firedAt` column to prevent duplicate ONESHOT fires on crash-restart, add `ERROR` status to stop bad CRON expressions from spinning in the tickLoop, and add `webhookUrl` + `zoneId` to the Schedule model. Zero new libraries required for this feature.

Recommended build order: fix the MAX7219 hardware bug first (amplifies across all later multi-zone work), then stabilise the scheduler schema (all subsequent features share those new columns), then add the audit log and webhooks (low risk, high daily-use value), defer multi-zone last (highest architectural disruption). Only 3 new production jars are needed across all five v1.1 features: `ktor-client-core`, `ktor-client-cio`, and `ktor-client-content-negotiation`, all at the existing `ktor = "3.5.0"` — zero version conflicts.

---

## Stack Additions

| Artifact | Version | Scope | Purpose |
|----------|---------|-------|---------|
| `ktor-client-core` | 3.5.0 | `implementation` | Ktor HTTP client API |
| `ktor-client-cio` | 3.5.0 | `implementation` | CIO coroutine engine (~150KB, Pi-friendly) |
| `ktor-client-content-negotiation` | 3.5.0 | `implementation` | JSON body serialization for webhook POST |
| `ktor-client-mock` | 3.5.0 | `testImplementation` | Mock engine for webhook unit tests |

All four reuse the existing `ktor = "3.5.0"` version reference. All other v1.1 features are pure code changes.

**Do NOT add:** OkHttp, Quartz, JobRunr, Flyway, any JS framework.
**Do NOT remove:** Flaxoos — it provides the HTTP rate-limiting plugin, not scheduling.

---

## Feature Priorities

**Must ship in v1.1:**
- Scheduler schema cleanup (ConflictPolicy, firedAt, ERROR status, webhookUrl, zoneId) — foundational for all other features
- Display text history / audit log — high daily-use value, low complexity
- UI/UX refresh including the new history page

**Should ship in v1.1:**
- Webhooks / push notifications — clean home-automation integration, low risk once scheduler schema is stable

**Defer if time-constrained (consider v1.2):**
- Multi-zone displays — highest architectural disruption; only relevant if a second physical display is wired

**Defer indefinitely:** full-text history search, CSV export, WebSocket live feed, dynamic zone creation via API, JS frameworks, cloud sync, authentication.

---

## Architecture Approach

All v1.1 additions are additive to the existing layered architecture (routes → services → drivers → Pi4J hardware, Exposed persistence, Ktor DI).

**New components:** `DisplayHistoryTable/Repository/Service`, `HistoryRoutes`, `WebhookService/Config`, `ZoneRegistry/Config/Routes`, `ConflictPolicy` enum.

**Key integration points:**
- History recording → inside `ScreenDriverService.displayImmediate()` and `displayScheduled()` (only convergence points for both immediate and scheduled display events)
- Webhooks → inside `SchedulerService.fire()` via `scope.launch {}` (fire-and-forget)
- Multi-zone → single shared `Pi4J.newAutoContext()` with **unique IDs per zone**

---

## Critical Pitfalls

| # | Pitfall | Prevention | Phase |
|---|---------|------------|-------|
| 1 | MAX7219 SPI packet direction — `d=0` controls the physically *last* module | Fix `render()` to iterate `numDevices - 1 downTo 0`; probe single LED first | Phase 6 (hw fix) |
| 2 | Duplicate ONESHOT fire on crash-restart — no `firedAt` column | Add `firedAt` column; set atomically with `status=DONE`; skip ONESHOT if `targetMs < now` on restart | Phase 7 (scheduler) |
| 3 | Bad CRON expression spins tickLoop forever — no `ERROR` status | Write `status=ERROR` in catch; exclude ERROR from `findAllActive()`; validate CRON at creation time | Phase 7 |
| 4 | Pi4J duplicate SPI registration crashes startup for multi-zone | Each zone's `Max7219Matrix` must use unique `Spi.newConfigBuilder().id()` (`"max7219-zone-0"`, `"max7219-zone-1"`) | Phase 10 |
| 5 | Webhook blocks `Dispatchers.Default` (only 4 threads on Pi 4) | Launch in separate `IOScope`; `withTimeout(5_000)`; never retry inline | Phase 9 |
| 6 | Unbounded H2 history growth fills SD card | `MAX_ROWS=1000` cap in `HistoryRepository.insert()`; daily cleanup coroutine | Phase 8 |
| 7 | DI rewiring silent failures — Ktor DI is lazy | Add DI smoke test before any `configureDI()` changes | Phase 11 (refactor) |
| 8 | CSS cascade breaks existing forms in Ktor HTML DSL | Add `headExtra` parameter to `BaseLayout` before any page-specific CSS | Phase 11 (UI) |

---

## Recommended Phase Order

| Phase | Name | Rationale |
|-------|------|-----------|
| 6 | MAX7219 Hardware Fix | Bug amplifies across all display-layer testing; fix first |
| 7 | Scheduler Schema Stabilisation | All subsequent features share new columns — one migration now |
| 8 | Display History / Audit Log | Establishes `DisplayEvent` model; high value, low risk |
| 9 | Webhooks | Uses `DisplayEvent` from Phase 8; adds 3 Ktor client jars |
| 10 | Multi-Zone Displays | Most disruptive; requires all prior phases stable |
| 11 | UI/UX Refresh + Gap Closures | Cosmetic + v1.0 audit gaps; safe to do last |

**Phase 7 (Scheduler) scope correction:** This is targeted fixes, NOT a rewrite. Work: add `SKIP_NEW` ConflictPolicy, add `firedAt` + `ERROR` status + `webhookUrl` + `zoneId` columns, fix CRON expiry, fix `orElseThrow()` → null-safe. No Flaxoos removal.

---

## Open Decisions

| Decision | Phase | Recommendation |
|----------|-------|----------------|
| H2 history retention policy | Phase 8 | `MAX_ROWS=1000` cap enforced in `HistoryRepository.insert()` |
| Pi4J multi-zone context strategy | Phase 10 | Single shared context + unique string IDs per zone |
| Per-zone SPI bus mutex scope | Phase 10 | Shared bus `Mutex` in `ZoneRegistry` for all zones on same SPI bus |
| Webhook retry strategy | Phase 9 | 3-attempt backoff; circuit breaker after 3 consecutive failures (skip 60 s) |
| Webhook delivery logging | Phase 9 | Store delivery status in the history row (no separate table) |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct codebase analysis; all 4 new jars are trivial version-aligned additions |
| Features | HIGH | Scope correction on scheduler is code-verified; feature priorities are well-bounded |
| Architecture | HIGH | Based on direct read of all production Kotlin files; integration points are concrete |
| Pitfalls | HIGH | All pitfalls traced to actual code lines; not theoretical |

**Needs validation on physical hardware:**
- Pi4J single-context-unique-IDs approach for multi-zone (minimal two-zone spike recommended before full Phase 10 implementation)
- H2 `AUTO_SERVER=TRUE` behavior in Docker on Pi (test early in Phase 8)

---

*Research completed: 2026-06-11 | Ready for roadmap: yes*
