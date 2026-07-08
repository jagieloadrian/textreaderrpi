# Phase 20: Cleanup + Docs - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

The repository is clean: `docs/` is deleted, `.planning/` is compressed to decisions only, and `README.md` accurately documents the v1.2 system (all endpoints including `/api/v1/live`, `/api/v1/history/export`, `/ws/zone/{id}`; firmware flash walkthrough for Pico and ESP32; Kubernetes/Helm deployment). Requirements: CLEAN-01, DOCS-01. No new features, no code changes beyond documentation and planning-artifact cleanup.

</domain>

<decisions>
## Implementation Decisions

### docs/ Salvage & Deletion (CLEAN-01)
- **D-01:** Salvage-then-delete: skim all 8 `docs/` files; migrate ONLY content that fills a required README gap (endpoints, deployment, quick-start). Everything else is deleted — git history is the archive.
- **D-02:** Accuracy rule for salvage: every migrated claim (endpoint, env var, command) must be verified against current code/config before landing in README. Stale content is dropped, not fixed.
- **D-03:** Dev/ops content (`docs/guides/development.md`, `docs/operations/monitoring-alerting.md`) is salvaged only if it earns its place — short, current, useful (e.g. a brief monitoring subsection). No wholesale sections just to preserve text.
- **D-04:** After deleting `docs/`, run a repo-wide grep for references to the deleted path (README, AGENTS.md, CI workflows, code comments) and update or remove every one.

### .planning/ Compression (CLEAN-01)
- **D-05:** STATE.md and MILESTONES.md are REWRITTEN to essentials, not trimmed in place. STATE.md keeps: current position, accumulated decisions, locked conventions — session logs and stale notes dropped. MILESTONES.md becomes one compact block per milestone (goal, outcome, key decisions).
- **D-06:** USER DECISION, deliberately beyond SC2's literal wording: completed phase directories 14–19 are DELETED (not archived) by this phase's plans. Git history preserves everything.
- **D-07:** Phase 20's own directory is also deleted, but sequenced as a documented POST-VERIFICATION step: plans delete 14–19; phase 20 executes and verifies normally (verifier needs 20's PLAN/SUMMARY files and writes 20-VERIFICATION.md); only after verification passes does a final milestone-closing commit delete `.planning/phases/20-cleanup-docs/`. The plan must document this final step explicitly (e.g. in the SUMMARY and STATE.md) so it isn't lost — it runs after the verifier, not as a plan task.

### Firmware Flash Walkthrough (DOCS-01)
- **D-08:** Full walkthrough in README — not commands-only, not a pointer to firmware/ READMEs. Covers toolchain installation, wiring notes, build + flash steps, per platform (Pico via pico-sdk/CMake/UF2; ESP32 via PlatformIO).
- **D-09:** Host OS scope: Linux only (including Raspberry Pi OS). Other OSes get a one-line pointer to official toolchain docs.
- **D-10:** Configuration is documented: every user-editable config value (WiFi credentials, server address, display driver selection in `config.h` / `platformio.ini`) with an example. Plus a basic troubleshooting list (board not detected, WiFi fails, zone doesn't appear on server).

### README Structure (DOCS-01)
- **D-11:** Single README stays the source of truth — add a table of contents and keep section ordering tight. No FIRMWARE.md split.
- **D-12:** New v1.2 endpoints extend the EXISTING API tables, each with a short curl/wscat usage example, consistent with the current README pattern. No separate "New in v1.2" section.
- **D-13:** SC4 accuracy sweep is systematic: a plan task cross-checks every README claim against route files, `values.yaml`, and firmware config — the endpoint list is generated from code, not memory. Deferred v1.3 items (from REQUIREMENTS.md "Future Requirements") are explicitly grepped for to ensure none are referenced as current features.

### Claude's Discretion
- Exact README section ordering and TOC format.
- Which docs/ fragments qualify as "earning their place" under D-03.
- Compressed STATE.md/MILESTONES.md exact layout, within the D-05 shape.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Cleanup targets
- `.planning/STATE.md` — 284 lines; rewrite target per D-05
- `.planning/MILESTONES.md` — 78 lines; rewrite target per D-05
- `docs/` — 8 files across 7 subdirs (api/reference.md, deployment/production-guide.md, guides/getting-started.md, guides/development.md, architecture/overview.md, operations/monitoring-alerting.md, configuration/overview.md, testing/overview.md); salvage source, then delete

### README accuracy sources (SC3/SC4 — verify claims against these, not docs/)
- `README.md` — 355 lines, current state; already has Helm section and zone architecture, missing v1.2 endpoints and firmware walkthrough
- `src/main/kotlin/com/anjo/routing/` — route files; authoritative endpoint list
- `.devops/helm/textreaderrpi/values.yaml` — Helm deployment claims
- `firmware/pico/` and `firmware/esp32/` — build systems and config files the walkthrough documents (config.h, platformio.ini)
- `.planning/REQUIREMENTS.md` §Future Requirements — deferred v1.3 items that must NOT appear in README as current features

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- README's existing API-table + curl-example pattern: extend it for the new endpoints (D-12).
- `docs/api/reference.md` and `docs/deployment/production-guide.md` are the highest-value salvage candidates for endpoint and deployment content.

### Established Patterns
- README is generated-by gsd-doc-writer (header comment) — the rewrite should keep or drop that marker consciously.
- The 17-CONTEXT/18-CONTEXT decisions (firmware stack: pico-sdk + Mongoose + cJSON; ESP32 Arduino + PlatformIO; Helm chart values) are the factual basis for the walkthrough and Helm sections.

### Integration Points
- Deleting phase dirs 14–19 (D-06) interacts with GSD tooling that scans `.planning/phases/` (progress, audits). This phase accepts that history moves to git; STATE.md/ROADMAP.md remain the live records.

</code_context>

<specifics>
## Specific Ideas

- The firmware walkthrough should read as a complete beginner path on Linux: install toolchain → wire the display → configure WiFi/server/driver → build → flash → see the zone appear on the server.
- The final milestone-closing commit (delete 20's own dir, D-07) is intentionally the last act of v1.2 planning hygiene.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 20-cleanup-docs*
*Context gathered: 2026-07-07*
