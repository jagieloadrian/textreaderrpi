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

## Milestone: v1.2 — Firmware + Features + Refactor + Ops

**Shipped:** 2026-07-08
**Phases:** 7 (14–20) | **Plans:** 35
**Timeline:** 16 days (2026-06-22 → 2026-07-08)
**Codebase:** 8,943 Kotlin LOC (4,775 main + 4,168 test)
**Audit:** tech_debt (no blockers) — 14/14 requirements, 7/7 phases, 14/14 integration chains, 6/6 flows

### What Was Built

- **History search + export** — `GET /api/v1/history?search=` with `<mark>`-highlighted matches; RFC 4180 CSV export
- **SSE live feed** — `GET /api/v1/live` real-time event stream, isolated from the 60 req/min API rate limit
- **Dynamic zone management** — `POST /api/v1/zones` without restart; regex-validated names close an XSS/DB-bound gap
- **Firmware skeletons** — RPi Pico (pico-sdk/CMake) and ESP32 (ESP-IDF/CMake) WebSocket display clients, CI-verified
- **Kubernetes + Helm** — Production chart with hardware-access and resource controls
- **DRY/YAGNI refactoring pass** — deduplication across main code and tests
- **Repository cleanup** — `docs/` deleted, `.planning/` compressed, README rewritten and accuracy-swept against source

### What Worked

- **Auto code-review-on-phase-completion caught real bugs, twice** — Phase 19's review found a live-zone-deletion bug (`ZoneRegistry.removeZone` not checking `isLocal`) plus two tests that structurally couldn't fail and one causing shared-DB test pollution; Phase 20's own review (on its own README output) found a broken systemd install path, a phantom `picowota` git-submodule reference, and a curl example that would 400. Both caught before merge, not after.
- **Isolated-worktree code-fixer survived a session-limit kill cleanly** — The Phase 19 fixer died mid-investigation on a hit session limit; because it ran in its own git worktree, zero commits and zero uncommitted changes leaked to the main tree. Retry after quota reset picked up cleanly with no reconciliation needed.
- **File-scope separation enabled safe concurrency despite sequential-mode degradation** — `origin/HEAD` was unresolved (no `worktree.baseRef` set), forcing phase-20 executors onto the main tree sequentially. A concurrent phase-19 review-fix cycle (isolated worktree, non-overlapping files: Kotlin sources vs. README/docs) ran safely alongside it without conflict — but this required explicit reasoning per dispatch, not something the tooling verified automatically.
- **3-source cross-reference in `/gsd-audit-milestone` (traceability + VERIFICATION + SUMMARY frontmatter) caught what single-source checks would have missed** — requirement checkboxes were accurate even where the ROADMAP prose Status column and Progress table were stale.

### What Was Inefficient

- **Deleting phase directories before milestone close broke every disk-scanning tool downstream** — Phase 20's own D-06 task (correctly) deleted `.planning/phases/14-19/` after their work was verified, preserving history in git. But `phases.list`, `find-phase`, and `init.manager`'s per-phase `phase_complete`/`verification_status` projection all scan `.planning/phases/` on disk — they don't fall back to git history. This meant: `/gsd-audit-milestone` would have wrongly reported 6 phases as "unverified/blocker" (worked around via a fork that reconstructed VERIFICATION.md/SUMMARY.md content from git history), and `gsd-tools query milestone.complete` refused outright ("ROADMAP lists 6 unstarted phases") until re-run with `--force`. Both were false negatives, confirmed by independent evidence (ROADMAP's own top-of-file checklist, REQUIREMENTS.md checkboxes, git-history audit) — but required manual investigation each time to be sure.
- **ROADMAP.md's `## Progress` table drifted independently of its own top-of-file phase checklist** — the checklist (`- [x] Phase N: ... (completed ...)`) was accurate for all 7 v1.2 phases throughout, but the separate `## Progress` markdown table showed 5 of 7 as "Not started" with wrong plan counts (e.g. Phase 17 showed "0/6" when it was actually 9/9; Phase 18 showed "0/2" when it was 10/10) — right up until this milestone close manually corrected it. Two sources of truth for the same fact, updated by different code paths, is what let this drift for 16 days unnoticed.
- **7 physical-hardware UAT checks (Phase 17: real Pico/ESP32 boards) were never resolved or explicitly accepted** — unlike Phase 18's equivalent live-cluster gap, which got an explicit user-acceptance note. Deferred human-verify items need the same explicit accept-or-schedule step every time, not just when someone remembers to add the note.

### Patterns Established

- **`embeddedServer(Netty, port=0)` + CIO client for WebSocket/SSE tests, not `testApplication`** — `testApplication`'s test coroutine dispatcher blocks on uncompleted child coroutines from long-lived drain/collect jobs; a real (ephemeral-port) Netty server avoids the deadlock. Established in Phase 15 (SSE), reused in Phase 17 (firmware WS tests).
- **`@EncodeDefault(Mode.NEVER)` per-field over global `explicitNulls=false`** — scopes the null-omission behavior to one serializable type (`FirmwareMessage`) instead of changing serialization for the whole app.
- **Helm nested `{{- if not $password }}` / `{{- if $existing }}` guards over `and`** — avoids a Go-template short-circuit evaluation order that varies by Helm version.
- **`sanitizeSearchTerm` called at every entry point that reaches the same query, not just the primary one** — Phase 14 applied the SQL-wildcard strip in both the list and CSV-export handlers, closing a bypass the export path would otherwise have had.

### Key Lessons

1. **Never let a cleanup task delete verified artifacts before the milestone that depends on them has closed** — if a phase's own scope includes deleting other phases' directories (as a documented decision, like this milestone's D-06), either run `/gsd-audit-milestone` and `/gsd-complete-milestone` *before* that deletion executes, or accept that every subsequent disk-scanning tool call needs a git-history-reconstruction workaround.
2. **A single source of truth per fact, or the sources will silently diverge** — ROADMAP.md carrying both a per-phase checklist line and a separate Progress table (updated by different code paths) let the table drift for over two weeks with nobody noticing until milestone close cross-referenced it against git history.
3. **Isolate any agent that edits files it doesn't own, even for zero-conflict-looking work** — the code-fixer's worktree isolation is what made a mid-task session-limit kill a non-event instead of a half-edited working tree to untangle.
4. **Deferred human-verify items need an explicit accept-or-schedule decision at milestone close, every time** — a UAT gap silently left "pending" with no note is easy to lose track of; one that's explicitly acknowledged (like Phase 18's) is a tracked decision instead of an accident.

### Cost Observations

- Model: Claude (orchestrator + gsd-executor/gsd-code-reviewer/gsd-code-fixer/gsd-verifier/gsd-integration-checker subagents), Sonnet model tier throughout
- Sessions: 1 session spanning phase 19 review-fix, phase 20 execution (4 plans + 2 review-fix cycles), and full v1.2 milestone close
- Notable: one subagent (the Phase 19 fixer) hit an account-level session/quota limit mid-task and required a clean retry after reset — no work was lost due to worktree isolation

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 5 | 29 | Established baseline; wave-based execution from Phase 3 onwards |
| v1.1 | 9 | 32 | DI smoke test first; inserted phases (11.2); security code review on network layer |
| v1.2 | 7 | 35 | Firmware WebSocket clients + K8s/Helm deployment; auto code-review-on-phase-completion caught real bugs twice; milestone close required git-history reconstruction to work around disk-scan tooling gaps from mid-milestone phase-dir deletion |

### Cumulative Quality

| Milestone | Test Classes | Coverage Gate | LOC (Kotlin) |
|-----------|-------------|---------------|--------------|
| v1.0 | 20 | ≥70% (JaCoCo) | 3,895 |
| v1.1 | 30+ | ≥70% (actual 80.7%) | 7,704 |
| v1.2 | 30+ | ≥70% (JaCoCo gate held through refactor) | 8,943 |

### Top Lessons (To Carry Forward)

1. **Test structure mirrors production structure from Phase 1** — Avoid retroactive refactors
2. **Per-plan SUMMARY.md is required** — Wave summaries don't substitute for plan-level artifacts
3. **Hardware abstraction (OfflineDriver pattern) enables CI/local testing throughout** — Design for offline from the start
4. **Stub every suspend Boolean mock in `beforeEach`** — Relaxed mock `false` default causes infinite loops and OOM in maxRuns-gated scheduler tests
5. **UI audit before SUMMARY.md** — Post-close UI audits add unplanned fix commits; resolve audit findings within the plan lifecycle
6. **Security code review on all network-facing code** — Unit tests miss trust boundary violations; SSRF/spoofing/dedup bugs require explicit review
7. **Never delete verified phase artifacts before the milestone that depends on them has closed** — disk-scanning audit/completion tooling has no git-history fallback; a cleanup phase's own deletions can break its own milestone's close
8. **One source of truth per tracked fact** — parallel bookkeeping structures (e.g. ROADMAP's checklist vs. its Progress table) drift silently when only one is updated by the automated path
9. **Isolate agents that edit files they don't own** — worktree isolation turns a mid-task crash/quota-kill into a clean retry instead of a half-edited tree

