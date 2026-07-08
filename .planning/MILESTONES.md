# TextReaderRpi — Milestones

## v1.2 Firmware + Features + Refactor + Ops — 2026-07-08

**Phases:** 7 (14–20) | **Plans:** 35
**Codebase:** ~8,943 LOC Kotlin (4,775 main + 4,168 test) | **Files changed:** 199 (+46,084 / −2,490)
**Timeline:** 2026-06-22 → 2026-07-08 (16 days)
**Audit:** tech_debt (no blockers) — 14/14 requirements, 7/7 phases, 14/14 integration chains, 6/6 flows

### Delivered

Extended the display ecosystem with a real-time SSE live feed, dynamic zone creation via API, and full-text history search/CSV export. Shipped microcontroller firmware skeletons for both RPi Pico (pico-sdk) and ESP32 (ESP-IDF) as WebSocket display clients, and a Kubernetes Helm chart for production deployment. Closed the milestone with a DRY/YAGNI refactoring pass and a full documentation/repository cleanup — `docs/` deleted, `.planning/` compressed, README rewritten and accuracy-swept against source for every claim.

### Key Accomplishments

1. **Full-text search + CSV export** — `GET /api/v1/history?search=` with `<mark>`-highlighted matches; RFC 4180 CSV export of filtered results (Phase 14)
2. **SSE live feed** — `GET /api/v1/live` real-time event stream of displayed text, status-page widget via EventSource, isolated from the 60 req/min API rate limit (Phase 15)
3. **Dynamic zone management** — `POST /api/v1/zones` creates zones without restart; Zones UI page with type toggle and toast feedback; regex-validated zone names close an XSS/DB-bound gap (Phase 16)
4. **Firmware skeletons** — RPi Pico (pico-sdk/CMake) and ESP32 (ESP-IDF/CMake) WebSocket display clients with reconnect/heartbeat, verified in CI; 7 physical-hardware UAT checks remain pending (deferred, tracked below) (Phase 17)
5. **Kubernetes + Helm** — Production Helm chart in `.devops/helm/textreaderrpi/` with hardware-access and resource controls (Phase 18)
6. **DRY/YAGNI refactoring** — deduplication pass across main code and tests; code-review cycle found and fixed 4 warnings (a live-zone-deletion bug, two tests that couldn't fail, and shared-DB test pollution) (Phase 19)
7. **Repository cleanup** — `docs/` deleted (8 files, history preserved), `.planning/` compressed (phase dirs 14–19 removed), README rewritten for v1.2 with a new Firmware flash-walkthrough section and a systematic accuracy sweep against source; own code-review cycle found and fixed 3 warnings (Phase 20)

### Known Gaps & Deferred Items at Close (2026-07-08)

- **Phase 17 hardware UAT** — 7/7 physical-hardware verification tests (real Pico/ESP32 boards) still pending; FW-01/FW-02 satisfied at code+CI level only. Requires physical devices, not agent-closeable.
- **Nyquist bookkeeping** — inconsistent/incomplete VALIDATION.md across phases 15, 16, 17 (frontmatter/table disagree), 18 (never created), 19 (unfilled template). Cosmetic — each phase's VERIFICATION.md independently confirms correctness by other means.
- **Note on this audit:** phase directories 14–19 were deleted by Phase 20's own cleanup before this milestone closed; their verification data was reconstructed from git history (see `.planning/milestones/v1.2-MILESTONE-AUDIT.md`) rather than read from disk.

### Archive

- Roadmap: `.planning/milestones/v1.2-ROADMAP.md`
- Requirements: `.planning/milestones/v1.2-REQUIREMENTS.md`
- Audit: `.planning/milestones/v1.2-MILESTONE-AUDIT.md`
- Tag: `v1.2`

---

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

### Known Gaps & Deferred Items at Close (2026-05-28)

Full gap list (4 items: `/health/detail`, HTML error pages, `/metrics` hardware group, SKIP_NEW policy — all closed in v1.1) and deferred items (missing per-plan Phase 2 summaries, multi-display) are in the archived audit below. Score: 21/25 requirements, 4/5 phases, 14/15 wiring, 6/7 flows | Nyquist: 4/5 phases.

### Archive

- Roadmap: `.planning/milestones/v1.0-ROADMAP.md`
- Requirements: `.planning/milestones/v1.0-REQUIREMENTS.md`
- Phases: `.planning/milestones/v1.0-phases/`
- Audit: `.planning/milestones/v1.0-v1.0-MILESTONE-AUDIT.md`
- Tag: `v1.0`
