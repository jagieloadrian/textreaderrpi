# Phase 20: Cleanup + Docs - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 5 (all edits, no new files — docs-only phase)
**Analogs found:** 5 / 5 (all self-referential — extend/rewrite existing files in place)

This is a documentation/cleanup phase (CLEAN-01, DOCS-01). There is no application code (controller/service/model) being created — every "pattern" here is a doc-editing convention: extend an existing table using its own established row/example format, or rewrite a planning file into its own target shape. No new dependency exists to import.

## File Classification

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|-----------------|----------------|
| `README.md` (API section edit) | docs (reference table) | transform (code → table row) | `README.md` itself, existing `### Text`/`### Schedules`/`### Zones` subsections | exact (same file, established convention) |
| `README.md` (Firmware walkthrough, new section) | docs (guide) | transform (source README → condensed section) | `firmware/pico/README.md`, `firmware/esp32/README.md` | exact (source-of-truth to transpose) |
| `README.md` (Deployment/Helm section edit) | docs (reference) | transform | `README.md`'s existing `### Helm (Kubernetes / K3s)` subsection | exact |
| `.planning/STATE.md` | docs (planning artifact) | transform (compress in place) | itself — current 284-line structure is the rewrite input | exact |
| `.planning/MILESTONES.md` | docs (planning artifact) | transform (compress in place) | itself — current 78-line one-block-per-milestone structure already matches D-05's target shape | exact |
| `docs/` (8 files) | docs (deleted) | N/A | — | deletion target, not a pattern source |
| `.planning/phases/{14..19}-*/` | planning artifact dirs | N/A | — | deletion target |

No controller/service/model/middleware files are touched — skip those classification rows entirely (YAGNI: this phase has zero `src/` changes per RESEARCH.md's Validation Architecture section).

## Pattern Assignments

### `README.md` — API table extension (D-12)

**Analog:** `README.md` lines 10-115 (existing `## API` section — own convention)

**Table + curl pattern to copy** (`README.md:14-31`, the `### Text` subsection is the canonical example of the row+curl format):
```markdown
### Text

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/text` | Send text immediately (`202 Accepted`) |
| `POST` | `/api/v1/text?zone={id}` | Send text to a specific zone |

\`\`\`bash
# Broadcast to all zones
curl -X POST http://localhost:8080/api/v1/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello","effect":"SCROLL"}'
\`\`\`
```

**Apply this exact shape to the 3 new endpoints** (data verified in RESEARCH.md §Authoritative Endpoint Inventory — do not re-derive):
- `GET /api/v1/live` → add as new subsection or append to an existing one; use the verified curl: `curl -N http://localhost:8080/api/v1/live`
- `GET /api/v1/history/export` → extend the existing `### History` subsection (`README.md:78-84`) with a second table row + the verified curl: `curl -o history.csv "http://localhost:8080/api/v1/history/export?effect=SCROLL&search=hello"`
- `WS /ws/zone/{id}` → new row under a firmware-facing note; verified example: `wscat -c ws://localhost:8080/ws/zone/pico-01`
- Also update `### History`'s query-param prose line (`README.md:84`) to add the missing `search` param (currently only lists `page, size, effect, source`)
- Also update `### Zones`'s POST example (`README.md:57-74`) to add the FIRMWARE-type registration curl alongside the existing IP-based one: `curl -X POST .../api/v1/zones -d '{"name":"pico-01","type":"FIRMWARE"}'`

**Accuracy fixes to make in the same pass** (RESEARCH.md §Existing README Accuracy Issues — pre-existing bugs, not v1.2-caused):
- `README.md:134-135` GPIO_SPI_CE/MOSI/SCK defaults are wrong (`8`/`10`/`11` → should be `24`/`19`/`23` per `application.yaml:25-28`)
- `README.md:91` `/api/v1/display/select` description should note it returns `501 Not Implemented`
- Config table (`README.md:127-...`) missing `SPI_TIMEOUT_MS`, `GPIO_TIMEOUT_MS`, `API_METRICS_RATE_LIMIT`, `METRICS_PREFIX` — add all 4, verified live in `application.yaml:43-59`

---

### `README.md` — Firmware flash walkthrough (new, D-08/D-09/D-10)

**Analog:** `firmware/pico/README.md` and `firmware/esp32/README.md` — both are complete, accurate, ground-truth walkthroughs already in the repo. Task is transposition/compression into root README, not new authorship.

**Structure to copy from `firmware/pico/README.md`** (toolchain table → build → flash → config block → wiring):
- Toolchain table format (Tool | Version | Install columns) — reuse verbatim style
- Config block is a fenced ```c``` snippet of `config.h` contents — reuse this snippet format for both platforms (verified contents already extracted in RESEARCH.md §Firmware Walkthrough Facts, use those directly)
- Build/flash as two separate fenced ```bash``` blocks, matching README.md's own existing `## Build and Run` bash-block convention (`README.md:167-186`)

**Critical accuracy note (do not skip):** ESP32 section MUST be written from ESP-IDF facts (`firmware/esp32/README.md`, `firmware/esp32/CMakeLists.txt`), never "PlatformIO"/"Arduino" — REQUIREMENTS.md/CONTEXT.md's own wording is stale here (RESEARCH.md Pitfall 1). The string "PlatformIO" must not appear in the final README for ESP32.

**Troubleshooting list format:** copy the compact-table style from `docs/deployment/production-guide.md`'s 6-row troubleshooting table (salvage source) — RESEARCH.md already provides the 4 verified rows to use (board not detected / WiFi provisioning fails / zone doesn't appear / display shows nothing).

---

### `README.md` — Deployment/Helm section (minor additions)

**Analog:** `README.md:237-252` (existing `### Helm (Kubernetes / K3s)` subsection — already 95% accurate per RESEARCH.md)

**Pattern:** same "install command block + key-defaults prose line" shape already used at `README.md:241-252`. Add two more facts to the "Key chart defaults" line: health-probe paths/timing (`GET /health` liveness, `GET /health/ready` readiness, `initialDelaySeconds: 20`) and DB-password auto-generation behavior. No structural change — extend the existing sentence/list.

**Also fix (D-04):** remove the two dangling `docs/` links at `README.md:261-262` — content moves inline (monitoring subsection folds into Deployment per D-03, production-guide content folds into Configuration/Deployment sections directly).

---

### `.planning/STATE.md` — compression rewrite (D-05)

**Analog:** itself. Current structure (`.planning/STATE.md:20-284`) already has the right section names; the rewrite drops entire sections rather than restructuring:

**Keep (compress in place, do not restructure):**
- `## Current Position` (line 25)
- `## Accumulated Context` → `### Key Decisions` table (line 116) — ~40 rows, this is the "locked conventions" D-05 requires to survive
- `## Key Pitfalls for v1.2` (line 102) — the 4 must-survive pitfalls RESEARCH.md flags by name (displaySource rename, parseDiscoveryReply SSRF guard, Material 3 dark-default CSS, testApplication first-HTTP-call guard) are inside this section

**Delete entirely:**
- `## Session` (line 253) — session logs
- `## Performance Metrics` (line 213) — per-plan minute counts, pure noise per RESEARCH.md
- `## Decisions` (line 259) — duplicates/supersedes the Key Decisions table with lower-value entries

---

### `.planning/MILESTONES.md` — compression rewrite (D-05)

**Analog:** itself — current structure already matches the target shape (one block per milestone: `## v1.1 ...` at line 5, `### Delivered`/`### Key Accomplishments`/`### Archive` subsections). This file needs the lightest touch of the two planning files — verify it already fits "goal, outcome, key decisions" per block and trim only if a block has grown beyond that (e.g., verbose `### Known Gaps` at line 55 under v1.0 is a candidate to compress, not the block shape itself).

---

## Shared Patterns

### Table + curl example format (applies to all README.md API additions)
**Source:** `README.md:14-31` (`### Text` subsection)
**Apply to:** every new/modified API table row across History, Zones, and the new `/api/v1/live` and `/ws/zone/{id}` entries.
```markdown
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/live` | SSE stream of display events (30s heartbeat) |

\`\`\`bash
curl -N http://localhost:8080/api/v1/live
\`\`\`
```

### Verify-before-write (applies to every migrated fact, README and STATE.md alike)
**Source:** D-02 (CONTEXT.md), reinforced by RESEARCH.md Pitfall 2/3
**Rule:** every migrated claim must have a `file:line` source in RESEARCH.md before landing in README.md. If a docs/ fact does not verify against current code (e.g. `API_QUEUE_SIZE`), drop it — do not "fix" it into something plausible.

### Deferred-feature negative-checklist (applies to README.md final pass)
**Source:** RESEARCH.md §Deferred v1.3 Items
**Rule:** grep the final README for the 6 deferred-item keywords (OTA-as-complete, GIN index, multi-replica, Ingress/TLS-as-ready, "WebSocket live feed" wording conflated with SSE) before considering the doc task done.

## No Analog Found

None — every file in scope is a doc/planning-artifact edit with a directly corresponding existing-file convention to copy or compress into. No `src/` code files are touched this phase, so no controller/service/model analog search was needed.

## Metadata

**Analog search scope:** `README.md`, `firmware/pico/README.md`, `firmware/esp32/README.md`, `.planning/STATE.md`, `.planning/MILESTONES.md`, `docs/*.md` (8 files, salvage source only), `src/main/kotlin/com/anjo/routing/*.kt` (fact source, not pattern source), `src/main/resources/application.yaml` (fact source)
**Files scanned:** 13
**Pattern extraction date:** 2026-07-07
</content>
