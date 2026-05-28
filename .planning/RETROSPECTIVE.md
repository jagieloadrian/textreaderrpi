# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

---

## Milestone: v1.0 — MVP

**Shipped:** 2026-05-28  
**Phases:** 5 | **Plans:** 29 | **Timeline:** 49 days (2026-04-09 → 2026-05-28)  
**Commits:** 100 | **Files:** 81 changed, 3,895 LOC Kotlin

### What Was Built

- **Text display pipeline** — `POST /api/v1/text` → EffectRenderer → DisplayDriver (MAX7219, LCD, OLED, Offline)
- **Scheduling engine** — ONESHOT/RECURRING/CRON persisted in H2/PostgreSQL with cancel endpoint and ConflictPolicy
- **Responsive HTML UI** — Ktor HTML DSL with shared layout, submission form, schedule manager, settings, status pages
- **Production observability** — `/health`, `/health/ready`, `/metrics`, rate limiting, retry/recovery, Swagger UI
- **Ops readiness** — 25-setting env var config, Gradle Docker image build, systemd service, deployment docs

### What Worked

- **Wave-based execution** — Breaking phases into waves (Backend → UI → Tests) prevented blocking and made incremental progress visible
- **Kotest `should` convention** — Self-documenting test names surfaced intent clearly; easy to review failures
- **YAML typed config** — 6 strongly-typed ConfigObjects with `${VAR:default}` pattern gave zero-friction env override without reflection magic
- **OfflineDisplayDriver** — Running hardware-independent on dev machine without mocking the entire Pi4J stack saved significant time
- **Ktor HTML DSL** — No separate build toolchain for templates; inline Kotlin = refactorable, type-safe HTML
- **Post-execution fixes tracked as Wave 4 plan (05-11)** — Atomic plan for cleanup kept git history clean and SUMMARY.md accurate

### What Was Inefficient

- **Phase 2 lacks individual plan SUMMARY.md files** — Wave execution summaries exist but per-plan summaries weren't created; makes traceability harder at milestone
- **No formal `gsd-audit-milestone` before close** — Milestone closed on developer confidence; formal requirements traceability audit was skipped
- **Flaxoos JDBC scheduler introduced late** — The switch from manual `tickLoop()` to Flaxoos was done in a "notes" commit with broad scope; should have been its own plan
- **Test package mirrors weren't established from Phase 1** — Packages were unified in Phase 5 (05-03); earlier structure would have prevented the refactor
- **`data/` directory untracked** — H2 database file bleeds into working tree; should be gitignored from Phase 4 when DB was introduced

### Patterns Established

- **`should ...` test naming** — All test names describe expected behavior; reinforced across all 20 test classes
- **Test package mirrors production package** — `com.anjo.service.effect.EffectRendererTest` mirrors `com.anjo.service.effect.EffectRenderer`
- **`${VAR:default}` env var pattern** — All config settings overridable without code changes; used for all 25 settings
- **Strategy pattern for extensibility** — `DisplayDriver` interface and `EffectRenderer` strategy both follow the same pattern; new variants require no routing/service changes
- **Atomic plan commits** — Each plan's work isolated in `feat(XX-YY): ...` commits; SUMMARY.md written immediately after

### Key Lessons

1. **Establish test package structure in Phase 1** — Retrofitting package mirrors in a later phase costs time and creates a noisy commit
2. **Gitignore runtime data dirs immediately when introduced** — Add `data/` to `.gitignore` when the DB feature lands, not after
3. **Per-plan SUMMARY.md even in wave execution** — Wave summaries are useful for reporting but plan-level summaries are necessary for milestone traceability
4. **Big-scope "notes" refactor commits obscure decisions** — Break broad rework (e.g., Flaxoos migration) into dedicated plans even if small
5. **Hardware abstraction from day one pays compound dividends** — `OfflineDisplayDriver` enabled all service/route tests to run without Pi hardware throughout all 5 phases

### Cost Observations

- Model: GitHub Copilot (Claude-based) via JetBrains IDE
- No external API cost tracking available
- Notable: Wave-based parallel agent execution (gsd-executor) reduced wall-clock time significantly for phases 3–5

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 5 | 29 | Established baseline; wave-based execution from Phase 3 onwards |

### Cumulative Quality

| Milestone | Test Classes | Coverage Gate | LOC (Kotlin) |
|-----------|-------------|---------------|--------------|
| v1.0 | 20 | ≥70% (JaCoCo) | 3,895 |

### Top Lessons (To Carry Forward)

1. **Test structure mirrors production structure from Phase 1** — Avoid retroactive refactors
2. **Per-plan SUMMARY.md is required** — Wave summaries don't substitute for plan-level artifacts
3. **Hardware abstraction (OfflineDriver pattern) enables CI/local testing throughout** — Design for offline from the start

