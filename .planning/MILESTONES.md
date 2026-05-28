# TextReaderRpi — Milestones

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

### Known Deferred Items at Close

- No formal `gsd-audit-milestone` run before archive
- Phase 2 plans lack individual plan SUMMARY.md (wave summaries used instead)
- Multiple concurrent displays deferred to future milestone

### Archive

- Roadmap: `.planning/milestones/v1.0-ROADMAP.md`
- Requirements: `.planning/milestones/v1.0-REQUIREMENTS.md`
- Phases: `.planning/milestones/v1.0-phases/`
- Tag: `v1.0`

