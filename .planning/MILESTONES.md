# TextReaderRpi — Milestones

---

## v1.1 Refactor + Fixes + UI + New Features — 2026-06-21

**Phases:** 9 (6–13 incl. 11.2) | **Plans:** 32 | **Commits:** ~142
**Codebase:** 4,325 LOC main + 3,379 LOC test = 7,704 Kotlin
**Timeline:** 2026-06-12 → 2026-06-21 (10 days)
**Known deferred items at close:** 4 (see STATE.md Deferred Items)

### Delivered

Full technical debt payback plus major feature expansion: corrected MAX7219 hardware scroll direction, stabilized the scheduler schema with Flyway migrations and ConflictPolicy enum, conducted a thorough dead-code refactor with DI smoke test coverage (80.7% JaCoCo), added display history/audit log with 1000-row cap, implemented fire-and-forget webhook notifications, delivered multi-zone display routing (local SPI + network WebSocket + UDP/mDNS autodiscovery), closed all three v1.0 observability audit gaps (health/detail, metrics hardware group, HTML error pages), and shipped a Material 3 UI/UX refresh with side navigation and dark mode.

### Key Accomplishments

1. **MAX7219 hardware fix** — SPI packet direction corrected; AbstractDisplayDriver base class eliminated 15 duplicated field declarations across 3 drivers
2. **Scheduler stabilized** — ConflictPolicy enum (SKIP_NEW/INTERRUPT), ONESHOT crash-safe firedAt update, CRON ERROR persistence, Flyway 9.22.3 migrations
3. **Full refactor** — DI smoke test, dead config removed, ScreenDriverService renamed, 80.7% JaCoCo; HistoryService decouples routes from repository
4. **Display history + webhooks** — Every event persisted (1000-row cap); paginated/filterable API + HTML; webhook HTTP POST on schedule fire (5s timeout)
5. **Multi-zone displays** — ZoneRegistry routes to local SPI and network WebSocket zones; UDP/mDNS autodiscovery; RFC1918 SSRF protection
6. **Observability gaps closed** — GET /health/detail, /metrics hardware group (4 counters), HTML 404/500 pages (SwaggerUI ordering fixed)
7. **Material 3 UI** — Side nav, dark mode via prefers-color-scheme, zone selector, effect preview, all v1.1 pages rebuilt

### Archive

- Roadmap: `.planning/milestones/v1.1-ROADMAP.md`
- Requirements: `.planning/milestones/v1.1-REQUIREMENTS.md`
- Tag: `v1.1`

---

## v1.0 MVP — 2026-05-28

**Phases:** 5 | **Plans:** 29 | **Commits:** 100  
**Files changed:** 81 (+3,963 / −902) | **Codebase:** 3,895 LOC Kotlin (77 files)  
**Timeline:** 2026-04-09 → 2026-05-28 (49 days)

### Delivered

A complete, production-ready Raspberry Pi text display system. Text is submitted via a responsive web interface (Ktor HTML DSL), routed through a scheduling engine with conflict policy, rendered via an effect pipeline (SCROLL/BLINK/REVERSE/FADE), and displayed on pluggable hardware drivers (MAX7219, LCD, OLED, Offline). The system is observable (`/health`, `/health/ready`, `/metrics`), rate-limited, and fully configurable via environment variables.

### Key Accomplishments

1. **MVP Ktor server** — `POST /api/v1/text` with YAML config, `RequestValidation`, `StatusPages`, MAX7219 LED rendering
2. **Multi-display abstraction** — `DisplayDriver` interface for MAX7219, LCD, OLED, Offline; config-driven switching
3. **Responsive HTML UI** — Ktor HTML DSL with shared layout: submission form, schedule manager, display settings, status page
4. **Production hardening** — `/health`+`/health/ready` (KHealth), 60 req/min rate limiting, retry/recovery policy, deployment docs
5. **Observability** — `/metrics` JSON endpoint (runtime/API/hardware groups), extended KHealth payload
6. **Scheduling engine** — ONESHOT/RECURRING/CRON schedules, cancel endpoint, H2/PostgreSQL persistence, ConflictPolicy
7. **Effect pipeline** — SCROLL/BLINK/REVERSE/FADE via EffectRenderer strategy pattern wired to text endpoint
8. **Ops readiness** — Full env var config (25 settings), Gradle Docker image build, Kotlin 2.3.21/Ktor 3.5.0/Exposed 1.3.0 upgrades

### Known Gaps (v2.0 backlog — from `/gsd-audit-milestone` 2026-06-10)

| Gap | REQ-ID | Severity | Description |
|-----|--------|----------|-------------|
| `/health/detail` not implemented | REQ-OBS-03 | BLOCKER | `GET /health/detail` returns 404. KHealth only wires `/health` and `/health/ready`. Phase 4 VERIFICATION.md falsely claimed 7 fields. |
| HTML error pages broken | REQ-DISP-06 | PARTIAL | Ktor 3.5.0 `swaggerUI` catch-all returns 200 for unknown browser GET paths before StatusPages can render HTML 404. `xtest` regression gate in place. |
| `/metrics` hardware group absent | REQ-OBS-01 | PARTIAL | `MetricsCollector` returns only `runtimeGroup()` + `apiGroup()`; `hardwareGroup()` (display failures, recovery retries) never implemented. |
| SKIP_NEW conflict policy absent | REQ-CONFLICT-01 | PARTIAL | `ScreenDriverService.displayImmediate()` always cancels current job (CANCEL_ONGOING only). No `ConflictPolicy` enum or SKIP_NEW logic exists. |

Audit: `.planning/milestones/v1.0-v1.0-MILESTONE-AUDIT.md` | Score: 21/25 requirements, 4/5 phases, 14/15 wiring, 6/7 flows | Nyquist: 4/5 phases

### Previously Deferred Items at Close (2026-05-28)

- Phase 2 plans lack individual plan SUMMARY.md (wave summaries used instead)
- Multiple concurrent displays deferred to future milestone

### Archive

- Roadmap: `.planning/milestones/v1.0-ROADMAP.md`
- Requirements: `.planning/milestones/v1.0-REQUIREMENTS.md`
- Phases: `.planning/milestones/v1.0-phases/`
- Audit: `.planning/milestones/v1.0-v1.0-MILESTONE-AUDIT.md`
- Tag: `v1.0`

