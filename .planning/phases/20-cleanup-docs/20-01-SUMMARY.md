---
phase: 20-cleanup-docs
plan: 01
subsystem: docs
tags: [readme, api-docs, helm, configuration]

# Dependency graph
requires: []
provides:
  - "README.md Table of Contents linking every top-level section"
  - "README.md API tables covering GET /api/v1/live, GET /api/v1/history/export, WS /ws/zone/{id}"
  - "README.md Configuration table matching application.yaml (GPIO SPI defaults + 4 new vars)"
  - "README.md Deployment/Helm section with health-probe and DB-password facts, zero dead docs/ links"
affects: [20-02, 20-03, 20-04]

# Tech tracking
tech-stack:
  added: []
  patterns: ["README API table+curl convention reused for all new endpoint rows (per 20-PATTERNS.md)"]

key-files:
  created: []
  modified: ["README.md"]

key-decisions:
  - "Included the optional Monitoring subsection (D-03): 3 condensed alerting options in 2 lines, well under the 10-line budget"
  - "SSE Live Feed and firmware WS explicitly cross-referenced in prose to prevent readers conflating the two distinct WebSocket-shaped features"

patterns-established:
  - "New API endpoint rows always paired with one fenced bash curl/wscat example, matching the existing ### Text subsection convention"

requirements-completed: [DOCS-01]

coverage:
  - id: D1
    description: "README Table of Contents links every top-level section (API, Firmware, Configuration, Build and Run, Deployment, Code Layout, Notes)"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q '## Table of Contents' README.md"
        status: pass
    human_judgment: false
  - id: D2
    description: "API tables document GET /api/v1/live, GET /api/v1/history/export, and WS /ws/zone/{id} each with a usage example"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q '/api/v1/live' README.md && grep -q '/api/v1/history/export' README.md && grep -q '/ws/zone/{id}' README.md && grep -q '### Live Feed' README.md"
        status: pass
    human_judgment: false
  - id: D3
    description: "Configuration table matches application.yaml (GPIO_SPI_CE/MOSI/SCK corrected to 24/19/23; SPI_TIMEOUT_MS, GPIO_TIMEOUT_MS, API_METRICS_RATE_LIMIT, METRICS_PREFIX added; API_QUEUE_SIZE not added; dead docs/ links removed)"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q 'SPI_TIMEOUT_MS' README.md && grep -q 'METRICS_PREFIX' README.md && grep -q 'API_METRICS_RATE_LIMIT' README.md && grep -q 'GPIO_TIMEOUT_MS' README.md && ! grep -q 'API_QUEUE_SIZE' README.md && ! grep -q 'docs/deployment/production-guide.md' README.md && ! grep -q 'docs/operations/monitoring-alerting.md' README.md"
        status: pass
    human_judgment: false
  - id: D4
    description: "TOC anchors resolve correctly and section ordering matches 20-UI-SPEC's Documentation Structure Contract"
    human_judgment: true
    rationale: "Anchor resolution and visual section-order review require a human/GitHub-rendered read; deferred to Plan 03's accuracy sweep and /gsd-verify-work per this plan's own <verification> block."

duration: 10min
completed: 2026-07-08
status: complete
---

# Phase 20 Plan 01: README Reference Sections Summary

**README.md gains a Table of Contents, three new v1.2 API endpoint entries (SSE live feed, CSV history export, firmware WebSocket), corrected GPIO/config defaults matching application.yaml, and an extended Helm section — with all three pre-existing dangling docs/ links removed.**

## Performance

- **Duration:** ~10 min
- **Completed:** 2026-07-08
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Added `## Table of Contents` linking API, Firmware, Configuration, Build and Run, Deployment, Code Layout, Notes
- Documented `GET /api/v1/live` (new `### Live Feed` subsection, SSE), `GET /api/v1/history/export` (extends `### History`), and `WS /ws/zone/{id}` + FIRMWARE-type zone registration (extends `### Zones`) — each with a verified curl/wscat example
- Fixed pre-existing wrong GPIO SPI defaults (`8`/`10`/`11` → `24`/`19`/`23`) and added the 4 verified-live-but-undocumented env vars (`SPI_TIMEOUT_MS`, `GPIO_TIMEOUT_MS`, `API_METRICS_RATE_LIMIT`, `METRICS_PREFIX`)
- Extended the Helm "Key chart defaults" line with liveness/readiness probe paths+timing and DB-password auto-generation; added a 2-line Monitoring subsection (D-03, optional, included)
- Removed all three dangling `docs/` links (Configuration section's production-guide link, Deployment section's production-guide and monitoring-alerting links) — zero `docs/` references remain in README.md

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Table of Contents and extend API tables (D-11, D-12)** - `772198c` (docs)
2. **Task 2: Fix Configuration table and extend Deployment/Helm; remove dead docs/ links (D-04)** - `96428b1` (docs)

**Plan metadata:** committed in this SUMMARY's own commit (docs: complete plan)

## Files Created/Modified
- `README.md` - Table of Contents added; API section extended with Live Feed, History export, Zones FIRMWARE/WS; Display select noted as 501; Configuration table corrected and extended; Deployment/Helm section extended with probe/password facts + Monitoring subsection; 3 dead docs/ links removed

## Decisions Made
- Included the optional Monitoring subsection under Deployment (D-03) — 3 condensed alerting options (cron health check, systemd watchdog, external monitor) fit in 2 lines, well within the ≤10-line budget
- Kept the SSE live feed and firmware WebSocket explicitly distinguished in prose (per 20-UI-SPEC's Copywriting Contract) so a reader cannot conflate `/api/v1/live` with `/ws/zone/{id}`

## Deviations from Plan

None - plan executed exactly as written. All endpoint facts, GPIO defaults, env var defaults, and Helm probe/password facts were taken verbatim from 20-RESEARCH.md's verified file:line sources; no re-derivation from memory occurred, and `application.yaml` was independently re-read during execution to confirm `SPI_TIMEOUT_MS:1000`, `GPIO_TIMEOUT_MS:500`, `API_METRICS_RATE_LIMIT:120`, `METRICS_PREFIX:textreaderrpi`, and `GPIO_SPI_CE:24`/`MOSI:19`/`SCK:23` before writing them into the table.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- README's API, Configuration, and Deployment sections are now accurate against code and contain every v1.2 endpoint except the Firmware walkthrough, which Plan 02 will add as the new `## Firmware` section referenced by this plan's TOC (`[Firmware](#firmware)` bullet is already in place, pointing at a section that doesn't exist yet until Plan 02 lands — expected, per plan scope).
- Plan 03's final accuracy sweep should verify TOC anchors resolve once Firmware section exists, and re-run the D-04 grep across the full repo (this plan only verified README.md itself; the repo-wide sweep is Plan 03's/04's responsibility per phase sequencing).
- Zero dead `docs/` links remain in the sections this plan edited (Configuration, Deployment) — confirmed via `grep -n "docs/" README.md` returning no matches.

---
*Phase: 20-cleanup-docs*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: README.md
- FOUND: .planning/phases/20-cleanup-docs/20-01-SUMMARY.md
- FOUND commit: 772198c
- FOUND commit: 96428b1
- FOUND commit: 9b55825
