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

### Retroactive Audit Findings (2026-06-10)

Run `/gsd-audit-milestone` and `/gsd-validate-phase 1-5` retroactively revealed:

**Nyquist validation:** 4/5 phases compliant. Phase 2 non-compliant due to Ktor 3.5.0 SwaggerUI routing bug — `swaggerUI(path="openapi")` registers a catch-all GET handler that intercepts unregistered paths and returns 200, blocking the `StatusPages` HTML 404 handler. `xtest` regression gate added.

**VERIFICATION.md false claims (Phase 4):** Phase 4 VERIFICATION.md claimed `GET /health/detail` returns 7 fields — actually returns 404. Requirement was marked complete at milestone close without verifying the actual code. **Lesson: never mark a VERIFICATION.md passed unless the actual endpoint/behavior is manually tested.**

**Specification-vs-implementation drift (Phase 5):** `ConflictPolicy SKIP_NEW` was specified and claimed in Phase 5 VERIFICATION.md but never built. Only `CANCEL_ONGOING` (hardcoded) exists. **Lesson: requirements with variants need explicit test coverage per variant, not just for the default path.**

**Phase 2 VERIFICATION.md gap:** No `02-VERIFICATION.md` exists — the phase was closed using wave summaries only. The 6 Phase 2 requirements show as "partial" in the 3-source cross-reference due to missing formal verification. Functional code is correct (confirmed by integration checker), but the audit gap is a documentation debt.

### Cost Observations

- Model: GitHub Copilot (Claude-based) via JetBrains IDE
- No external API cost tracking available
- Notable: Wave-based parallel agent execution (gsd-executor) reduced wall-clock time significantly for phases 3–5

---

## Milestone: v1.1 — Refactor + Fixes + UI + New Features

**Shipped:** 2026-06-21
**Phases:** 9 (6–13, incl. 11.2) | **Plans:** 32
**Timeline:** 10 days (2026-06-12 → 2026-06-21)
**Commits:** ~142 | **Codebase:** 7,704 Kotlin LOC (4,325 main + 3,379 test)

### What Was Built

- **Hardware fix** — MAX7219 SPI packet direction corrected; AbstractDisplayDriver base class DRYed 3 drivers
- **Scheduler stabilized** — ConflictPolicy enum, ONESHOT crash-safe firedAt, CRON ERROR persistence, Flyway migrations
- **Full refactor** — DI smoke test guards 11 bindings; dead config classes removed; 80.7% JaCoCo; HistoryService layer
- **Display history + webhooks** — Every event persisted (1000-row cap); fire-and-forget HTTP POST on schedule fire
- **Multi-zone displays** — ZoneRegistry routes to local SPI + network WebSocket zones; UDP/mDNS autodiscovery
- **Observability gaps closed** — GET /health/detail, /metrics hardware group, HTML 404/500 pages fixed
- **Material 3 UI** — Side nav, dark mode via prefers-color-scheme, zone selector, effect preview, all pages rebuilt

### What Worked

- **DI smoke test first** — The Phase 8 decision to add DI smoke test before any refactoring meant zero silent binding failures across 5 phases of structural changes (11, 11.2, 12, 13 all changed DI significantly)
- **Phase-level inserted phases (11.2)** — Inserting 11.2 after Phase 11 review findings kept the original phase clean and made the quality work traceable to specific SC items, not buried in a catch-all fix
- **Wave-based parallel execution** — Plans 11-01/11-02 (disjoint files) running in parallel with a single Wave 1 call saved significant wall-clock time on the largest phase
- **Security review on Phase 11 network code** — CR-01 (senderIp not JSON ip), CR-02 (localZoneIds protection), CR-03 (ipIndex dedup) all came from code review and prevented real attack vectors before shipping
- **Flyway 9.22.3 + baselineOnMigrate** — V1–V5 migrations executed cleanly on fresh H2 and simulated existing-Pi scenarios; no migration regressions
- **HistoryRepository.insert() 1000-row cap** — Simple `selectAll().count()` guard prevents silent SD card fill; straightforward to audit

### What Was Inefficient

- **OOM in SchedulerServiceTest (Phase 7-03)** — Missing `coEvery returns true` stub for a `Boolean`-returning suspend mock caused infinite maxRuns loop; took 45 min to diagnose. Gradle reported "passed" for the OOM run, masking the issue.
- **Phase 11 network layer complexity** — NetworkZoneDriver + NetworkDiscoveryService + shared WS HttpClient took 4 waves to stabilize; the mDNS device name sanitization (CR-05) and IP dedup (CR-03) were post-plan fixes that could have been specced earlier
- **4 human-verify checkpoints not closed at milestone** — On-device Pi hardware testing (dual-SPI zones, network autodiscovery timing) was always gated on physical hardware that wasn't available during development
- **Phase 13 UI audit post-close** — F-01 through F-15 UI audit findings required 2 additional fix commits after Phase 13 was considered "complete"; the audit should happen before the plan is marked done, not after the SUMMARY.md is written

### Patterns Established

- **`coEvery { suspend Boolean mock } returns true` in `beforeEach`** — Required for any mock used in a maxRuns counting loop; false (default for Boolean relaxed mock) causes OOM
- **RFC1918 IP validation in RequestValidation plugin (not IpValidation object)** — Centralized validator prevents network zone route from accepting non-RFC1918 IPs; avoids SSRF via rogue device advertisement
- **`parseDiscoveryReply` uses kernel `senderIp`, not JSON "ip" field** — Network security lesson: trust kernel-reported source over attacker-controlled payload field
- **`headExtra` optional param in BaseLayout** — Allows per-page CSS injection without breaking all existing callers (defaults to null)
- **`displayAvailable` health check in `healthChecks {}` not `readyChecks {}`** — Hardware unavailability is a health concern, not a readiness gating event

### Key Lessons

1. **Stub every suspend Boolean mock in `beforeEach`** — A relaxed mock returns `false` (Boolean default); if that return value is used as a loop condition the test silently OOMs. Add `coEvery returns true` globally
2. **UI audit before SUMMARY.md** — Run `/gsd-ui-review` (or equivalent) before writing the plan SUMMARY.md, not after; it forces UI quality to be resolved within the plan lifecycle
3. **Reserve on-device hardware checkpoints for a UAT phase** — Human-verify items that require physical hardware should be in a separate "UAT / hardware smoke test" plan, not embedded in execution PLAN.md steps that auto-close
4. **Code review network security before merge** — Phase 11 code review found 3 exploitable issues (SSRF, zone overwrite, IP dedup) that were invisible in unit tests; network-facing code needs a review step

### Cost Observations

- Model mix: Claude Sonnet 4.6 primary throughout
- Sessions: ~20+ sessions across 10 days
- Notable: Phase 11 (largest phase, 5 plans + security review + gap closure) accounted for roughly 40% of total session time

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 5 | 29 | Established baseline; wave-based execution from Phase 3 onwards |
| v1.1 | 9 | 32 | DI smoke test first; inserted phases (11.2); security code review on network layer |

### Cumulative Quality

| Milestone | Test Classes | Coverage Gate | LOC (Kotlin) |
|-----------|-------------|---------------|--------------|
| v1.0 | 20 | ≥70% (JaCoCo) | 3,895 |
| v1.1 | 30+ | ≥70% (actual 80.7%) | 7,704 |

### Top Lessons (To Carry Forward)

1. **Test structure mirrors production structure from Phase 1** — Avoid retroactive refactors
2. **Per-plan SUMMARY.md is required** — Wave summaries don't substitute for plan-level artifacts
3. **Hardware abstraction (OfflineDriver pattern) enables CI/local testing throughout** — Design for offline from the start
4. **Stub every suspend Boolean mock in `beforeEach`** — Relaxed mock `false` default causes infinite loops and OOM in maxRuns-gated scheduler tests
5. **UI audit before SUMMARY.md** — Post-close UI audits add unplanned fix commits; resolve audit findings within the plan lifecycle
6. **Security code review on all network-facing code** — Unit tests miss trust boundary violations; SSRF/spoofing/dedup bugs require explicit review

