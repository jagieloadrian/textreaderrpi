# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

---

## Milestone: v1.2 — Firmware + Features + Refactor + Ops

**Shipped:** 2026-07-08  
**Phases:** 7 (14–20) | **Plans:** 35
**Timeline:** 16 days (2026-06-22 → 2026-07-08)
**Commits:** ~199 files changed | **Codebase:** 8,943 LOC Kotlin (4,775 main + 4,168 test)

### What Was Built

- **History enhancements** — Full-text search with `<mark>` highlight, RFC 4180 CSV export
- **SSE live feed** — `GET /api/v1/live` real-time event stream, status-page EventSource widget, isolated from 60 req/min limit
- **Dynamic zone management** — `POST /api/v1/zones` without restart; regex-validated zone names close XSS/DB-bound gaps
- **Firmware skeletons** — RPi Pico (pico-sdk/CMake) and ESP32 (ESP-IDF/CMake) WebSocket display clients with reconnect/heartbeat
- **Production deployment** — Kubernetes Helm chart with hardware-access and resource controls
- **DRY/YAGNI refactoring** — Deduplication across main code and tests; code-review cycle found/fixed 4 warnings
- **Repository cleanup** — `docs/` deleted (8 files), `.planning/` compressed, README rewritten for v1.2

### What Worked

- **Auto code-review-on-phase-completion caught real bugs twice** — Phase 19 found a live-zone-deletion bug and 2 tests that couldn't fail; Phase 20 found broken systemd path and phantom git reference in README
- **Isolated worktree for code fixes survived session-limit kill** — Phase 19 fixer crashed mid-task with zero leaked changes; retry picked up cleanly from git state
- **3-source milestone audit (traceability + VERIFICATION + SUMMARY)** — Caught requirement drift that single-source checks would have missed
- **Firmware WebSocket test pattern (embeddedServer+CIO)** — Real Netty server avoids `testApplication`'s blocking behavior on drain jobs

### What Was Inefficient

- **Phase directory deletion before milestone close broke disk-scanning tooling** — Phase 20's D-06 deleted `.planning/phases/14-19/` per spec, but `phases.list`, `init.manager`, and audit tooling don't fall back to git history; `/gsd-audit-milestone` and `gsd-tools query milestone.complete` required manual `--force` flag + git-history reconstruction workarounds
- **ROADMAP.md's Progress table drifted from its own Phase checklist** — two separate sources of truth (checklist updated correctly; Progress table didn't) stayed out of sync for 16 days undetected until milestone close
- **7 physical-hardware firmware UAT checks never explicitly scheduled or declined** — unlike Phase 18's cluster gap (which got an explicit user-acceptance note), deferred items need the same accept/schedule decision every time

### Patterns Established

- **`embeddedServer(Netty, port=0)` + CIO client for long-lived tests** — avoids `testApplication`'s test-dispatcher deadlock on collect/drain jobs
- **`@EncodeDefault(Mode.NEVER)` per-field, not global** — scopes null-omission to one type instead of app-wide
- **Helm nested if-guards over `and`** — avoids Go-template short-circuit version dependency
- **Validate at every entry point to the same query** — Phase 14 calls `sanitizeSearchTerm` in both list + export handlers, not just primary path

### Key Lessons

1. **Never delete verified artifacts before the milestone that depends on them closes** — cleanup deletion + disk-scanning audits are incompatible; run audit/close before deletion, or accept git-history reconstruction workarounds for every subsequent check
2. **One source of truth per fact** — ROADMAP's checklist + Progress table drifted because only one was on the automated update path; separate bookkeeping structures silently desync
3. **Isolate agents that edit files they don't own** — worktree isolation turned a mid-task crash into a clean retry instead of a half-edited working tree
4. **Deferred human-verify items need explicit accept/schedule at milestone close, every time** — a silent "pending" is an accident waiting to happen

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 5 | 29 | Established baseline; wave-based execution from Phase 3 onwards |
| v1.1 | 9 | 32 | DI smoke test first; inserted phases (11.2); security code review on network layer |
| v1.2 | 7 | 35 | Firmware WebSocket clients + K8s/Helm; auto code-review-on-phase-completion caught real bugs twice |

### Cumulative Quality

| Milestone | Test Classes | Coverage Gate | LOC (Kotlin) |
|-----------|-------------|---------------|--------------|
| v1.0 | 20 | ≥70% (JaCoCo) | 3,895 |
| v1.1 | 30+ | ≥70% (actual 80.7%) | 7,704 |
| v1.2 | 30+ | ≥70% (JaCoCo gate held through refactor) | 8,943 |

### Top Lessons (To Carry Forward to v1.3+)

1. **Test structure mirrors production from Phase 1** — Retrofitting later costs time and creates noise
2. **Per-plan SUMMARY.md is required** — Wave summaries don't substitute
3. **Hardware abstraction (OfflineDriver pattern) from day one** — Enables full CI/local testing throughout
4. **Stub every suspend Boolean mock in `beforeEach`** — Relaxed mock `false` default causes OOM in maxRuns tests
5. **UI audit before SUMMARY.md** — Post-close audits add unplanned fix commits
6. **Security code review on all network-facing code** — Unit tests miss trust boundary violations (SSRF, spoofing, dedup)
7. **Never delete verified phase artifacts before milestone close** — disk-scanning tools have no git-history fallback
8. **One source of truth per tracked fact** — Parallel bookkeeping drifts silently when only one is automated
9. **Isolate agents that edit files they don't own** — Worktree isolation handles mid-task failures cleanly

---

## Archived Milestone Details

- **v1.0 retrospective:** Shipped 2026-05-28 (5 phases, 29 plans). See `.planning/milestones/v1.0-ROADMAP.md` for full lessons learned.
- **v1.1 retrospective:** Shipped 2026-06-21 (9 phases, 32 plans). See `.planning/milestones/v1.1-ROADMAP.md` for full lessons learned.

*Note: Earlier milestone details were condensed 2026-07-08 to keep this document focused on forward-looking lessons and current-milestone context.*
