# TextReaderRpi - Development Roadmap

**Last Updated:** 2026-06-12

---

## Milestones

- ✅ **v1.0 MVP** — Phases 1–5 (shipped 2026-05-28) → [Archive](milestones/v1.0-ROADMAP.md)
- 🚧 **v1.1 Refactor + Fixes + UI + New Features** — Phases 6–12 (in progress)

---

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1–5) — SHIPPED 2026-05-28</summary>

- [x] Phase 1: MVP — Core Text Display Functionality (1 plan) — completed 2026-05-26
- [x] Phase 2: Enhanced Display Support + Responsive HTML UI (7 plans) — completed 2026-05-26
- [x] Phase 3: Production Ready (5 plans) — completed 2026-05-27
- [x] Phase 4: Cleanup + Observability (5 plans) — completed 2026-05-27
- [x] Phase 5: Scheduling + Effects (11 plans) — completed 2026-05-28

</details>

### v1.1 Refactor + Fixes + UI + New Features

- [ ] **Phase 6: MAX7219 Hardware Fix** — Scroll direction corrected, driver layer refactored
- [ ] **Phase 7: Scheduler Schema Stabilisation** — ConflictPolicy, firedAt, ERROR status, webhookUrl, zoneId columns added
- [ ] **Phase 8: Refactor + Dead Code Analysis** — Application cleaned and simplified, DI smoke test in place
- [ ] **Phase 9: Display History + Audit Log** — Every display event persisted and browsable
- [ ] **Phase 10: Webhooks** — HTTP POST notifications fired on schedule trigger
- [ ] **Phase 11: Multi-Zone Displays** — Multiple local and network displays managed and routable
- [ ] **Phase 12: Observability Gap Closures** — v1.0 audit gaps closed (health/detail, metrics hardware, HTML error pages)
- [ ] **Phase 13: UI/UX Refresh** — Material 3 style, side nav, all new pages deployed

---

## Phase Details

### Phase 6: MAX7219 Hardware Fix

**Goal**: The MAX7219 chain renders text correctly left-to-right and the driver layer is free of workarounds
**Depends on**: Phase 5 (v1.0 complete)
**Requirements**: HW-01, HW-02
**Success Criteria** (what must be TRUE):

  1. Scrolling text moves from the leftmost module toward the right on a physical 2× 8×8 MAX7219 chain
  2. A single LED probe on the first module confirms SPI packet direction before full chain is wired
  3. Max7219Matrix, LCD, OLED, and OfflineDriver contain no manual workarounds or commented-out hacks
  4. Driver layer unit tests pass with no skipped assertions related to byte ordering

**Plans**: 2 plansPlans:
**Wave 1**

- [x] 06-01-PLAN.md — Fix Max7219Matrix render direction (buildPacket extraction + physicalD fix + write guards removed) + 2 unit tests

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 06-02-PLAN.md — Introduce AbstractDisplayDriver base class, refactor Max7219Matrix/LcdDisplay/OledDisplay to extend it

### Phase 7: Scheduler Schema Stabilisation

**Goal**: The scheduler database schema is extended with all columns needed by v1.1 features in one migration, and edge-case bugs are fixed
**Depends on**: Phase 6
**Requirements**: SCHED-01, SCHED-02, SCHED-03, SCHED-04
**Success Criteria** (what must be TRUE):

  1. User can select `SKIP_NEW` as ConflictPolicy when submitting text; a second submission is silently ignored while the display is busy
  2. A ONESHOT schedule that fired before a Pi crash does not fire again after restart
  3. A schedule with an invalid CRON expression is written to the database with status `ERROR` and the scheduler loop continues processing other schedules normally
  4. A schedule row persists a `webhookUrl` field and a `zoneId` field that downstream phases can populate and read

**Plans**: TBD

### Phase 8: Refactor + Dead Code Analysis

**Goal**: The codebase is simplified and production-clean with a DI smoke test guarding further changes
**Depends on**: Phase 7
**Requirements**: REF-01, REF-02, REF-03, REF-04
**Success Criteria** (what must be TRUE):

  1. A DI smoke test (`configureDI()` wiring check) runs on every test-suite execution and fails fast if a provider is missing
  2. `./gradlew test` passes with zero unreachable classes, stale constants, or dead display-configuration branches detectable by static analysis
  3. Removing any abstraction introduced during cleanup does not break existing API contract tests
  4. Line coverage gate of 70% continues to pass after refactoring

**Plans**: TBD

### Phase 9: Display History + Audit Log

**Goal**: Every text display event is recorded in the database and browsable via API and a dedicated HTML page
**Depends on**: Phase 8
**Requirements**: HIST-01, HIST-02, HIST-03
**Success Criteria** (what must be TRUE):

  1. After sending text via `POST /api/v1/text`, a corresponding record appears in `GET /api/v1/history` containing text, effect, zone, timestamp, and source
  2. `GET /api/v1/history` returns a paginated response; requesting page 2 returns a different set of records than page 1
  3. The `/history` HTML page renders a table or card list of events with working filter controls for zone and effect
  4. The history table never exceeds 1 000 rows; oldest rows are pruned automatically when the cap is reached

**Plans**: TBD

### Phase 10: Webhooks

**Goal**: When a schedule fires, an HTTP POST is sent to a configured URL as a fire-and-forget notification
**Depends on**: Phase 9
**Requirements**: HOOK-01, HOOK-02, HOOK-03
**Success Criteria** (what must be TRUE):

  1. After a schedule fires, a test HTTP listener receives a POST within 5 seconds containing text, effect, schedule ID, zone ID, and timestamp
  2. A webhook that times out (no response within 5 s) does not block or slow down the display or the scheduler loop
  3. Setting `WEBHOOK_DEFAULT_URL` environment variable causes all schedules without an explicit `webhookUrl` to POST to that fallback URL
  4. Webhook delivery status is visible in the history record for the corresponding display event

**Plans**: TBD

### Phase 11: Multi-Zone Displays

**Goal**: Multiple locally-attached and network-discovered displays are registered as named zones and can each receive routed text
**Depends on**: Phase 10
**Requirements**: ZONE-01, ZONE-02, ZONE-03, ZONE-04, ZONE-05, ZONE-06, ZONE-07, ZONE-08
**Success Criteria** (what must be TRUE):

  1. Two MAX7219 displays connected via SPI to the same Pi both render text independently when addressed by their zone names; the application starts without Pi4J ID-collision errors
  2. A network display broadcasting UDP or mDNS appears in `GET /api/v1/zones` within 30 seconds of coming online, without any manual configuration
  3. `POST /api/v1/text?zone=X` routes text to the named zone; submitting to an offline zone returns a descriptive error without crashing the server
  4. A user can manually register an external display by IP address through the web UI when auto-discovery fails
  5. `GET /api/v1/zones` lists every registered zone with its current online/offline status

**Plans**: TBD
**UI hint**: yes

### Phase 12: Observability Gap Closures

**Goal**: All three v1.0 audit gaps are closed — health/detail endpoint, metrics hardware group, and HTML error pages
**Depends on**: Phase 11
**Requirements**: OBS-01, OBS-02, OBS-03
**Success Criteria** (what must be TRUE):

  1. `GET /health/detail` returns a JSON payload containing uptime, memory, display status, and error counts
  2. `GET /metrics` response includes a hardware group with display failure counts, recovery retry counts, and resource slot utilisation
  3. Navigating a browser to an unknown path (e.g. `/does-not-exist`) renders an HTML 404 page instead of a Swagger JSON response

**Plans**: TBD

### Phase 13: UI/UX Refresh

**Goal**: The web interface is rebuilt with Material Design 3 style, side navigation, and all new v1.1 pages (history, zones, status)
**Depends on**: Phase 12
**Requirements**: UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07, UI-08
**Success Criteria** (what must be TRUE):

  1. The web interface displays a persistent side navigation panel with links to Send Text, Schedules, History, Zones, and Status; the layout adapts to mobile screen widths
  2. Dark mode activates automatically when the browser reports `prefers-color-scheme: dark` using Material 3 color tokens
  3. The send text form includes a zone selector populated from `GET /api/v1/zones`
  4. The `/zones` page lists all local and network zones with online/offline badges and a button to add a zone by IP address
  5. The `/history` page shows display event cards with zone and effect filter controls
  6. The `/status` page displays live data from `/health/detail` and `/metrics`

**Plans**: TBD
**UI hint**: yes

---

## Progress

| Phase | Name | Milestone | Plans Complete | Status | Completed |
|-------|------|-----------|----------------|--------|-----------|
| 1 | MVP — Core Text Display | v1.0 | 1/1 | ✅ Complete | 2026-05-26 |
| 2 | Enhanced Display Support | v1.0 | 7/7 | ✅ Complete | 2026-05-26 |
| 3 | Production Ready | v1.0 | 5/5 | ✅ Complete | 2026-05-27 |
| 4 | Cleanup + Observability | v1.0 | 5/5 | ✅ Complete | 2026-05-27 |
| 5 | Scheduling + Effects | v1.0 | 11/11 | ✅ Complete | 2026-05-28 |
| 6 | MAX7219 Hardware Fix | v1.1 | 1/2 | In progress | - |
| 7 | Scheduler Schema Stabilisation | v1.1 | 0/? | Not started | - |
| 8 | Refactor + Dead Code Analysis | v1.1 | 0/? | Not started | - |
| 9 | Display History + Audit Log | v1.1 | 0/? | Not started | - |
| 10 | Webhooks | v1.1 | 0/? | Not started | - |
| 11 | Multi-Zone Displays | v1.1 | 0/? | Not started | - |
| 12 | Observability Gap Closures | v1.1 | 0/? | Not started | - |
| 13 | UI/UX Refresh | v1.1 | 0/? | Not started | - |
