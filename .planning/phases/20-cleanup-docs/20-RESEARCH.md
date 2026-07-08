# Phase 20: Cleanup + Docs - Research

**Researched:** 2026-07-07
**Domain:** Documentation accuracy, repository hygiene, in-repo salvage (no external libraries, no web research)
**Confidence:** HIGH — every claim below was verified directly against source files in this repo, not from training knowledge or docs/.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**docs/ Salvage & Deletion (CLEAN-01)**
- D-01: Salvage-then-delete — skim all 8 `docs/` files; migrate ONLY content that fills a required README gap (endpoints, deployment, quick-start). Everything else is deleted — git history is the archive.
- D-02: Accuracy rule for salvage — every migrated claim (endpoint, env var, command) must be verified against current code/config before landing in README. Stale content is dropped, not fixed.
- D-03: Dev/ops content (`docs/guides/development.md`, `docs/operations/monitoring-alerting.md`) is salvaged only if it earns its place — short, current, useful. No wholesale sections just to preserve text.
- D-04: After deleting `docs/`, run a repo-wide grep for references to the deleted path (README, AGENTS.md, CI workflows, code comments) and update or remove every one.

**.planning/ Compression (CLEAN-01)**
- D-05: STATE.md and MILESTONES.md are REWRITTEN to essentials, not trimmed in place. STATE.md keeps: current position, accumulated decisions, locked conventions — session logs and stale notes dropped. MILESTONES.md becomes one compact block per milestone (goal, outcome, key decisions).
- D-06: USER DECISION, deliberately beyond SC2's literal wording — completed phase directories 14–19 are DELETED (not archived) by this phase's plans. Git history preserves everything.
- D-07: Phase 20's own directory is also deleted, but sequenced as a documented POST-VERIFICATION step: plans delete 14–19; phase 20 executes and verifies normally (verifier needs 20's PLAN/SUMMARY files and writes 20-VERIFICATION.md); only after verification passes does a final milestone-closing commit delete `.planning/phases/20-cleanup-docs/`. The plan must document this final step explicitly (e.g. in the SUMMARY and STATE.md) so it isn't lost — it runs after the verifier, not as a plan task.

**Firmware Flash Walkthrough (DOCS-01)**
- D-08: Full walkthrough in README — not commands-only, not a pointer to firmware/ READMEs. Covers toolchain installation, wiring notes, build + flash steps, per platform.
- D-09: Host OS scope: Linux only (including Raspberry Pi OS). Other OSes get a one-line pointer to official toolchain docs.
- D-10: Configuration is documented — every user-editable config value with an example, plus a basic troubleshooting list.

**README Structure (DOCS-01)**
- D-11: Single README stays the source of truth — add a table of contents and keep section ordering tight. No FIRMWARE.md split.
- D-12: New v1.2 endpoints extend the EXISTING API tables, each with a short curl/wscat usage example, consistent with the current README pattern. No separate "New in v1.2" section.
- D-13: SC4 accuracy sweep is systematic — a plan task cross-checks every README claim against route files, `values.yaml`, and firmware config; the endpoint list is generated from code, not memory. Deferred v1.3 items must NOT appear in README as current features.

### Claude's Discretion
- Exact README section ordering and TOC format.
- Which docs/ fragments qualify as "earning their place" under D-03.
- Compressed STATE.md/MILESTONES.md exact layout, within the D-05 shape.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-------------------|
| CLEAN-01 | `.planning/` compressed (STATE.md, MILESTONES.md to decisions/summaries/conventions); `docs/` folder deleted | §.planning/ Compression Facts, §docs/ Salvage Assessment, §Repo-Wide docs/ Reference Sweep |
| DOCS-01 | README.md updated to reflect v1.2 (new endpoints, firmware flash instructions, K8s/Helm section) | §Authoritative Endpoint Inventory, §Firmware Walkthrough Facts, §Helm Deployment Facts, §Existing README Accuracy Issues |
</phase_requirements>

## Summary

This is a documentation-and-cleanup phase with zero production code changes and zero external dependencies — all research is in-repo verification, not library research. Three concrete deliverables: (1) delete `docs/` after salvaging the handful of facts it has that the README lacks, (2) rewrite `.planning/STATE.md` and `.planning/MILESTONES.md` to compact form and delete phase dirs 14–19 (352K in phase 17 alone), (3) bring `README.md` current — three brand-new v1.2 endpoints, a full Linux firmware flash walkthrough for two firmware targets, and a Helm section (which already exists and is largely accurate).

The most important research finding is that **REQUIREMENTS.md and CONTEXT.md's own factual claim about the ESP32 firmware build system is stale**: both describe it as "Arduino + PlatformIO" / "ArduinoWebsockets," but Phase 17 Plan 09 migrated the ESP32 target from PlatformIO/Arduino to native ESP-IDF (v5.2.1) CMake — confirmed by the actual `firmware/esp32/CMakeLists.txt` (`idf_component_register`), `firmware/esp32/main/config.h`, `firmware/esp32/README.md`, and `.github/workflows/firmware-ci.yml` (installs ESP-IDF, runs `idf.py`/`cmake --build`, no `pio`/PlatformIO anywhere). The planner must write the firmware walkthrough from the current ESP-IDF facts, not from REQUIREMENTS.md's FW-02 wording or CONTEXT.md D-08's "PlatformIO" mention — this is a textbook case of D-02's "verify against current code, drop stale content" rule applying to the *requirements/context documents themselves*, not just docs/.

A second significant finding: the **existing README's GPIO pin defaults are already wrong** (unrelated to v1.2) — `GPIO_SPI_CE`/`MOSI`/`SCK` defaults documented as `8`/`10`/`11` but `application.yaml` defaults are `24`/`19`/`23`. And `docs/deployment/production-guide.md`'s `API_QUEUE_SIZE` variable does not exist anywhere in current config — it must be dropped per D-02, not migrated.

**Primary recommendation:** Generate the endpoint list, firmware config tables, and Helm defaults directly from the code snapshots in this research (all already verified) rather than re-deriving them during planning/execution — every fact below has a file:line source.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Endpoint documentation (README API tables) | Docs / repo root | Backend (routing/) | README describes the API surface; routing/*.kt is the ground truth it must match |
| docs/ salvage & deletion | Docs / repo root | — | Pure content operation, no runtime tier involved |
| .planning/ compression | Planning tooling (GSD) | — | STATE.md/MILESTONES.md are GSD-managed planning artifacts, not app code |
| Firmware flash walkthrough | Docs / repo root | Firmware (firmware/pico/, firmware/esp32/) | README must mirror the existing firmware/*/README.md content (already the ground truth), not invent new facts |
| Helm/K8s deployment docs | Docs / repo root | DevOps (.devops/helm/) | values.yaml + templates/deployment.yaml are the ground truth for all deployment claims |

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| Endpoint inventory | Don't write the endpoint list from memory or from docs/api/reference.md | Read `src/main/kotlin/com/anjo/routing/*.kt` directly (list below) | docs/api/reference.md is already stale-adjacent noise once code is the source; Routing.kt shows exact mount paths (`/api/v1`, `/api/v1` again for `/live`, `/ws`) |
| Firmware walkthrough facts | Don't re-derive toolchain/wiring/config from REQUIREMENTS.md or CONTEXT.md prose | Read `firmware/pico/README.md` and `firmware/esp32/README.md` — both are already complete, accurate, code-adjacent walkthroughs | They already contain the exact content D-08/D-09/D-10 ask for; the task is compression/transposition into root README, not new research |
| Config value verification | Don't trust docs/deployment/production-guide.md's env var table wholesale | Cross-check every variable against `src/main/resources/application.yaml` (single source of truth for defaults) | Confirmed stale/wrong values exist in both docs/ and the current README (see below) |
| Helm defaults | Don't invent Helm chart facts | Read `.devops/helm/textreaderrpi/values.yaml` + `Chart.yaml` directly | README's existing Helm section already used this pattern and is 95% accurate |

**Key insight:** This phase has no framework/library layer to research — the "don't hand-roll" list here is entirely "don't hand-roll documentation facts from memory or from a doc file that might already be stale; read the code."

## Authoritative Endpoint Inventory

Generated from `src/main/kotlin/com/anjo/routing/Routing.kt` (mount points) cross-referenced with each route file. This is the exact and complete list — use it to extend README's API tables per D-12.

Mount structure (`Routing.kt`):
- Static/UI routes at root (`/`, `/status`, `/settings/display`, `/schedules`, `/history`, `/zones`) — unchanged from current README, no action needed.
- `route("/api/v1") { ... }` block 1: `textRoutes`, `displayRoutes`, `scheduleRoutes`, `historyRoutes`, `zoneRoutes` — all already in README except the two new History/Zone additions below.
- `route("/api/v1") { liveRoutes(...) }` — **second, separate** `/api/v1` block, SSE only.
- `route("/ws") { firmwareZoneRoutes(...) }` — WebSocket only.

**New/changed since current README (must be added):**

| Method | Path | File | Notes |
|--------|------|------|-------|
| `GET` | `/api/v1/history/export` | `HistoryRoutes.kt:23-31` | CSV export. Same query params as `/history` (`effect`, `source`, `zone`, `search`). Sets `Content-Disposition: attachment; filename="history.csv"` via `ContentDisposition.Attachment.withParameter(...)`. Returns `text/csv`. |
| `GET` | `/api/v1/live` | `LiveRoutes.kt:12-20` | SSE stream (`sse("/live")`). Emits `event: display` frames with JSON body = `DisplayEvent` (`id`, `text`, `effect`, `zoneId`, `displayedAt`). 30s heartbeat comment frame (`heartbeat { period = 30.seconds }`) — keeps proxies from closing idle connections. No auth. |
| `WS` | `/ws/zone/{id}` | `FirmwareZoneRoutes.kt:11-27` | Inbound WebSocket for firmware nodes. `id` must be a zone registered as type `FIRMWARE`. Closes with `1003 CANNOT_ACCEPT` if zone not found. Server pushes `FirmwareMessage` JSON frames (`text`, `effect`, `zoneId`, `ts`, optional `speed`/`blinkPeriod`/`fadeSteps`). |

**Also new/changed on the existing `/api/v1/history` endpoint** (README currently omits `search`):

| Method | Path | Query params (current, from `HistoryValidators.parseFilter`) |
|--------|------|----------------------------------------------------------------|
| `GET` | `/api/v1/history` | `page` (default 1), `size` (1–200, default 20), `effect`, `source`, `zone`, **`search`** (case-insensitive, `%`/`_` stripped in `HistoryValidators.sanitizeSearchTerm` before blank-check — SQL LIKE injection guard) |

**Also changed on `/api/v1/zones` POST** (README's current curl example only shows network-zone registration; firmware zone registration is a new, distinct path through the same endpoint):

- `AddZoneRequest` (`model/AddZoneRequest.kt`): `{ name, type, ip?, displaySubtype? }`.
- `ZoneValidators.validateAddZone` (verified): `type` in `{MAX7219, LCD, OLED, UNKNOWN}` → `422`/`400`-mapped validation error ("Local hardware types ... must be configured at startup"); `type == "FIRMWARE"` → `ip` not required, zone registered via `zoneRegistry.registerFirmwareZone(name)`; any other `type` → `ip` required and must be RFC1918 (`10.x`, `172.16-31.x`, `192.168.x`).
- Example to add to README: `curl -X POST .../api/v1/zones -d '{"name":"pico-01","type":"FIRMWARE"}'` → `201 Created`.

**Sample curl/wscat snippets for D-12 (verified against handler code, not assumed):**

```bash
# SSE live feed
curl -N http://localhost:8080/api/v1/live

# CSV export with filters
curl -o history.csv "http://localhost:8080/api/v1/history/export?effect=SCROLL&search=hello"

# Firmware WebSocket (wscat — for manual testing, not part of a client app)
wscat -c ws://localhost:8080/ws/zone/pico-01
```

## Existing README Accuracy Issues (found during D-13 sweep — not v1.2-caused, pre-existing)

These are bugs in the *current* README, uncovered while verifying claims against code as D-13 requires. The planner should fix them in the same accuracy-sweep task, since D-13 says "cross-check every README claim against route files... and firmware config" — not just the new ones.

| README claim | Actual (source) | Verdict |
|---|---|---|
| `GPIO_SPI_CE` default `8` | `application.yaml:25` → default `24` | **README wrong — fix** |
| `GPIO_SPI_MOSI` default `10` | `application.yaml:26` → default `19` | **README wrong — fix** |
| `GPIO_SPI_SCK` default `11` | `application.yaml:28` → default `23` | **README wrong — fix** |
| `GPIO_SPI_MISO` default `9` | `application.yaml:27` → default `9` | Correct, no change |
| `POST /api/v1/display/select` documented as "Switch driver" | `docs/api/reference.md:22` (salvage source) says "not implemented," and the route always returns `501 Not Implemented` per that same doc | **README's one-line description is misleading — either fix wording or leave since 501 detail is arguably out of scope; recommend adding "(reserved, returns 501)"** |
| README config table missing | `SPI_TIMEOUT_MS`, `GPIO_TIMEOUT_MS`, `API_METRICS_RATE_LIMIT`, `METRICS_PREFIX` all present in `application.yaml:43-59` but absent from README's config table | **Gap — should be added (all verified live in application.yaml)** |
| `docs/deployment/production-guide.md`'s `API_QUEUE_SIZE` var | No `queueSize`/`API_QUEUE_SIZE` key anywhere in `application.yaml` or `com/anjo/config/` | **Stale — do NOT salvage this one (D-02: drop, don't fix)** |

## docs/ Salvage Assessment

All 8 files read in full. Verdicts per D-01/D-02/D-03:

| File | Salvage value | Disposition |
|------|---------------|-------------|
| `docs/api/reference.md` (445 lines) | High — confirms exact endpoint list, status codes, request/response shapes for `/live`, `/ws/zone/{id}`, `/history/export`, and the FIRMWARE-type zone POST flow (all cross-checked against code above and found accurate) | Salvage: use as a cross-check source for the README API-table extension (already done above); do not copy the file wholesale — README uses a lighter table+curl format, not full request/response JSON blocks |
| `docs/deployment/production-guide.md` (228 lines) | Medium — the env var table has 4 stale/extra entries not in current README (`SPI_TIMEOUT_MS`, `GPIO_TIMEOUT_MS`, `API_METRICS_RATE_LIMIT`, `METRICS_PREFIX` — verified live) and 1 dead one (`API_QUEUE_SIZE` — verified stale, drop). The short "Troubleshooting" table (6 rows) is compact and useful. | Salvage: the 4 verified-live env vars into README's config table; the troubleshooting table if it "earns its place" (D-03) — likely yes, it's 6 short rows, no wholesale copy needed |
| `docs/guides/getting-started.md` (114 lines) | Low-medium — "Common Setup Issues" (JDK version, port conflict, SPI/I2C permission, H2 lock) overlaps with what README's existing Build/Run + new Troubleshooting sections should cover | Salvage: the 4 setup-issue entries are candidates for the new firmware/general troubleshooting list (D-10) if not already covered; otherwise skip — README's own quick-start is already sufficient |
| `docs/guides/development.md` (84 lines) | Low — build commands table duplicates README's existing "Build and Run" section almost exactly; "Key conventions" (no comments, validation-in-validators-only) are internal project rules, not user-facing docs | Per D-03, likely does NOT earn its place in README (it's a CLAUDE.md/AGENTS.md concern, not end-user docs) — recommend dropping entirely, confirm with a one-line note in SUMMARY |
| `docs/architecture/overview.md` | Not read in detail — README's existing "Code Layout" + "Notes" sections already cover architecture at the appropriate README depth | Recommend skimming only for any fact not already in README's Code Layout tree; low expected salvage |
| `docs/operations/monitoring-alerting.md` (178 lines) | Low-medium — has a "Log-Based Alerting" pattern table and 3 alerting options (cron health check, systemd watchdog, external monitor) not in README at all | Per D-03 "brief monitoring subsection" is explicitly called out as an example of something that could earn its place — recommend a short subsection under Deployment with the 3 alerting options condensed to ~10 lines, only if it stays short |
| `docs/configuration/overview.md` | Not read in detail — likely subset of production-guide.md's env var table | Low expected salvage beyond what's already captured above |
| `docs/testing/overview.md` | Not read in detail — test running commands; README doesn't currently have a testing section and D-01 scope is "endpoints, deployment, quick-start" | Recommend skip — out of D-01's explicit gap list |

**Recommendation for the plan:** budget one task to skim the 3 unread files (architecture/overview.md, configuration/overview.md, testing/overview.md) for any single unique fact, but expect near-zero net new salvage from them given the pattern found in the 5 files read in full.

## Repo-Wide docs/ Reference Sweep (D-04)

Full grep executed across `.md`, `.yml`, `.yaml`, `.kt`, `.kts` (excluding `docs/` itself and `.git/`). Findings:

**Real references that break when `docs/` is deleted (must fix):**
- `README.md:158` — `[docs/deployment/production-guide.md](docs/deployment/production-guide.md)` (config section)
- `README.md:261` — `[docs/deployment/production-guide.md](docs/deployment/production-guide.md)` (deployment section)
- `README.md:262` — `[docs/operations/monitoring-alerting.md](docs/operations/monitoring-alerting.md)` (deployment section)

All three are self-references that this phase's README rewrite will naturally replace (the content moves inline) — no dangling links should remain once the README task is done, but explicitly verify with a final grep.

**Non-issues (verified, no action needed):**
- `AGENTS.md` — file exists but is empty (1 line). No `docs/` references.
- `.github/workflows/*.yml` (ci.yml, helm-lint.yml, firmware-ci.yml) — grepped, zero references to `docs/`.
- `.devops/helm/textreaderrpi/README.md:273` — references `https://helm.sh/docs/` (external URL, coincidental substring match, not our `docs/` folder).
- `.planning/**` — many references (STATE.md, ROADMAP.md, REQUIREMENTS.md, phase RESEARCH/PLAN/SUMMARY files, milestones archive) — these are either (a) historical planning artifacts that describe intent to delete docs/ (correct, no fix needed), or (b) phase dirs 14-19 that are being deleted wholesale under D-06/D-05, or (c) `.planning/milestones/v1.0-phases/**` (archived, out of scope — this phase only touches STATE.md, MILESTONES.md, and phase dirs 14-20 per D-05/D-06/D-07). No action beyond what D-05/D-06 already specify.
- `PROJECT-STATUS.md:21` — mentions "delete docs/" as a task description (correct, describes this very phase, not a broken link).

**D-04 grep command for the plan's verification step (safe to reuse verbatim):**
```bash
grep -rn "docs/" --include="*.md" --include="*.yml" --include="*.yaml" --include="*.kt" --include="*.kts" . | grep -v "^\./docs/" | grep -v "/\.git/" | grep -v "^\./\.planning/"
```
Expected result after the phase completes: zero matches outside `.planning/` archive material.

## Firmware Walkthrough Facts

### Critical correction to REQUIREMENTS.md / CONTEXT.md

REQUIREMENTS.md (`FW-02`) and CONTEXT.md (D-08) describe ESP32 firmware as "Arduino + PlatformIO" / "C++, Arduino + ArduinoWebsockets." **This is stale.** Verified via `.planning/phases/17-firmware-skeletons/17-09-SUMMARY.md`: "Task 1 — ESP32 migrated from PlatformIO/Arduino to native ESP-IDF CMake." Confirmed live in code: `firmware/esp32/CMakeLists.txt` uses `include($ENV{IDF_PATH}/tools/cmake/project.cmake)`; `firmware/esp32/main/CMakeLists.txt` uses `idf_component_register`; there is no `platformio.ini` anywhere in `firmware/esp32/`; `.github/workflows/firmware-ci.yml`'s `esp32` job clones `espressif/esp-idf` v5.2.1 and runs `cmake -S firmware/esp32 -B firmware/esp32/build -DIDF_TARGET=esp32` + `cmake --build`, never `pio`.

**The README firmware walkthrough must be written from ESP-IDF facts, not PlatformIO facts.**

### Pico (RP2040/RP2350) — build system: pico-sdk 2.1.1 + CMake

Source: `firmware/pico/README.md` (already contains the full walkthrough — transpose into root README per D-08/D-11, don't re-derive).

**Toolchain (Linux):**
| Tool | Version | Install |
|------|---------|---------|
| CMake | 3.13+ | `apt install cmake` |
| arm-none-eabi-gcc | 10.3+ | `apt install gcc-arm-none-eabi` |
| pico-sdk | 2.1.1 | `git clone --branch 2.1.1 https://github.com/raspberrypi/pico-sdk`, set `PICO_SDK_PATH` |
| picotool | latest | for `--target flash` |
| picowota | git submodule | OTA bootloader — `git submodule update --init --recursive` |
| ninja-build | any | used in CI alongside cmake |

**Build:**
```bash
export PICO_SDK_PATH=/path/to/pico-sdk
cmake -S firmware/pico -B firmware/pico/build -DPICO_BOARD=pico_w   # or pico2_w for Pico 2W
cmake --build firmware/pico/build -j$(nproc)
```
Outputs: `firmware/pico/build/textreader.uf2` (OTA payload), `textreader_combined.uf2` (bootloader+app, first flash).

**Flash:**
```bash
cmake --build firmware/pico/build --target flash   # requires picotool + BOOTSEL mode
```
Manual alt: hold BOOTSEL while plugging in USB, drag `textreader_combined.uf2` onto the mounted drive.

**Config (`firmware/pico/config.h`, verified file contents):**
```c
#define WIFI_SSID       "YourSSID"
#define WIFI_PASS       "YourPassword"
#define SERVER_HOST     "192.168.1.100"
#define SERVER_PORT     8080
#define ZONE_ID         "pico-01"
#define RECONNECT_INTERVAL_MS  5000
#define NUM_DEVICES     1
```
Display driver selected at configure time via `-DDISPLAY_DRIVER=<NAME>` (default `MAX7219`; also `SSD1306`, `SSD1309`, `SSD1327`, `SH1106`, `HT16K33`, `ST7735`, `ST7789`, `ILI9225`, `PCD8544`, `SSD1680`).

**Wiring (MAX7219 default, Pico W):** GP18 (SPI0 SCK) → CLK, GP19 (SPI0 TX) → DIN, GP17 (SPI0 CSn) → LOAD/CS, 3V3 (pin 36) → VCC, GND (pin 38) → GND.

**First-boot WiFi provisioning:** connects to `TextReader-Setup` open AP → `http://192.168.4.1` captive portal → enter SSID/pass → saved to flash → reboots STA mode.

### ESP32 — build system: ESP-IDF 5.2.1 native CMake (NOT PlatformIO/Arduino)

Source: `firmware/esp32/README.md` (already contains the full walkthrough).

**Toolchain (Linux):**
| Tool | Version | Install |
|------|---------|---------|
| ESP-IDF | 5.2.1 | `git clone --branch v5.2.1 https://github.com/espressif/esp-idf`, run `./install.sh esp32`, then `. ./export.sh` per shell session |
| CMake | 3.16+ | bundled with ESP-IDF |
| Python | 3.8+ | required by ESP-IDF tooling |

Supported chips: ESP32, S2, S3, C2, C3, C6, H2 (H2 has no WiFi — provisioning won't work on H2).

**Build:**
```bash
. $IDF_PATH/export.sh
idf.py set-target esp32        # once per build dir; or esp32s3, esp32c3, etc.
idf.py build                   # default driver MAX7219
# or explicit driver:
cmake -S firmware/esp32 -B firmware/esp32/build -DDISPLAY_DRIVER=SSD1306
cmake --build firmware/esp32/build
```

**Flash:**
```bash
idf.py flash                   # or: idf.py -p /dev/ttyUSB0 flash
idf.py monitor                 # serial monitor
```

**Config (`firmware/esp32/main/config.h`, verified file contents):**
```c
#define WIFI_SSID              ""   // fallback only — captive portal is primary provisioning path
#define WIFI_PASS              ""
#define SERVER_HOST            ""
#define SERVER_PORT            8080
#define ZONE_ID                "esp32"
#define NUM_DEVICES            4
#define RECONNECT_INTERVAL_MS  5000
```
SPI/I2C pin defaults vary per chip family (compile-time `#if defined(CONFIG_IDF_TARGET_...)` block) — classic ESP32 defaults: `SPI_MOSI_PIN=23`, `SPI_CLK_PIN=18`, `SPI_CS_PIN=5`, `I2C_SDA_PIN=21`, `I2C_SCL_PIN=22`.

**Wiring (MAX7219 default, classic ESP32):** GPIO23 → DIN, GPIO18 → CLK, GPIO5 → LOAD/CS, 3.3V → VCC, GND → GND. Note: some MAX7219 modules need 5V VCC — level-shifter required if module doesn't tolerate 3.3V logic.

**First-boot WiFi provisioning:** identical captive-portal flow to Pico (`TextReader-Setup` AP → `http://192.168.4.1` → NVS storage → reboot STA).

### Both platforms — troubleshooting list for D-10 (verified against firmware source, common to both)

| Symptom | Cause / check |
|---|---|
| Board not detected over USB | Pico: check BOOTSEL mode entry; ESP32: check `idf.py -p /dev/ttyUSB0` port permission (`dialout` group on Linux) |
| WiFi provisioning fails / no captive portal | Confirm no stored credentials are blocking AP mode; erase flash to force re-provision (`idf.py erase-flash` for ESP32) |
| Zone doesn't appear on server | Confirm `SERVER_HOST`/`SERVER_PORT` in config.h match the running server; confirm zone was registered as type `FIRMWARE` via `POST /api/v1/zones` before connecting (server closes with `1003 CANNOT_ACCEPT` otherwise — verified in `FirmwareZoneRoutes.kt`) |
| Display shows nothing after connecting | Confirm `-DDISPLAY_DRIVER=<NAME>` matches the physically wired display; confirm wiring against the platform-specific wiring table |

### Non-Linux host OS (per D-09)

One-line pointer only: "Building on macOS or Windows: follow the official pico-sdk (`https://www.raspberrypi.com/documentation/pico-sdk/getting-started.html`) or ESP-IDF (`https://docs.espressif.com/projects/esp-idf/en/stable/esp32/get-started/`) installation guides for your platform — the build/flash commands above are otherwise identical." [ASSUMED: these are the standard official-docs URLs for each toolchain based on training knowledge; not fetched this session since D-09 scopes this to a one-line pointer, not a verified walkthrough.]

## Helm Deployment Facts (`.devops/helm/textreaderrpi/`)

Verified directly against `values.yaml`, `Chart.yaml`, `templates/deployment.yaml`. README's existing Helm section (lines 237-252) is already largely accurate — cross-check found no material errors, only opportunities to be more complete.

| Fact | Value | Source |
|------|-------|--------|
| Chart version / app version | `1.0.0` / `1.2` | `Chart.yaml` |
| `hardwareAccess.enabled` | `false` by default | `values.yaml:8-9` |
| Image pull policy default | `Never` (assumes pre-loaded image on node) | `values.yaml:5` |
| JVM heap | `JAVA_TOOL_OPTIONS: "-Xmx220m"` | `values.yaml:12` |
| Replicas | hardcoded `1`, not configurable (SPI/I2C + H2 DB can't be shared) | `values.yaml:15`, confirmed by STATE.md decision log (Phase 18-02) |
| Persistence | `data` (1Gi) + `logs` (500Mi), always created, `storageClass: ""` uses cluster default | `values.yaml:17-24` |
| Service | `ClusterIP`, port 8080 | `values.yaml:27-30` |
| Ingress | disabled by default (`ingress.enabled: false`) — deferred to v1.3 per REQUIREMENTS.md Future Requirements | `values.yaml:33-39` |
| RBAC / ServiceAccount | both `create: true` | `values.yaml:42-47` |
| Database credentials | `database.user: "sa"`, `database.password` auto-generated (16-char randAlphaNum) at install if unset | `values.yaml:56-58` |
| Liveness probe | `GET /health`, `initialDelaySeconds: 20`, `periodSeconds: 30` | `templates/deployment.yaml:60-66` |
| Readiness probe | `GET /health/ready`, same timing | `templates/deployment.yaml:69-75` |
| Hardware device mounts (when enabled) | `/dev/spidev0.0` (CharDevice), `/dev/i2c-1` (CharDevice) | `templates/deployment.yaml:107-113` |

README's current text already states `service.type: ClusterIP`, `persistence.data.size: 1Gi`, `replicas: 1` — all confirmed accurate. No changes strictly required here beyond what D-13's sweep formalizes; optionally add the health-probe paths/timings and the database-password auto-generation behavior since those aren't currently documented and are useful for a first-time Helm install.

## .planning/ Compression Facts (D-05, D-06, D-07)

Current sizes (verified via `wc -l` / `du -sh`):
- `.planning/STATE.md` — 284 lines (per CONTEXT.md D-05 canonical_refs; confirmed present)
- `.planning/MILESTONES.md` — 78 lines
- Phase dirs 14–19 total: 208K + 172K + 248K + 352K + 232K + 164K = **1.38 MB** across 6 directories, all with completed PLAN/SUMMARY/CONTEXT/RESEARCH/VERIFICATION files — confirmed present, safe to delete per D-06 (git history preserves everything; `git log --all -- .planning/phases/17-firmware-skeletons/` remains queryable after deletion).
- `.planning/phases/20-cleanup-docs/` currently has only `20-CONTEXT.md` and `20-DISCUSSION-LOG.md` — PLAN.md/SUMMARY.md/VERIFICATION.md will be added during this phase's own execution; D-07 requires these to survive until the verifier runs, THEN a final commit deletes the directory.

**4 decisions STATE.md's own "Key Pitfalls" section (#7) flags as must-survive compression:**
1. `displaySource` rename (Exposed `ColumnSet.source` naming collision)
2. `parseDiscoveryReply` SSRF guard (ignores JSON `"ip"` field, uses kernel-verified senderIp)
3. Material 3 dark-default CSS (`:root` dark palette, `@media (prefers-color-scheme: light)` override, no JS toggle)
4. `testApplication` first-HTTP-call guard (DI plugin defers module execution until first `client.get()`)

These 4 plus the full "Accumulated Context > Key Decisions" table (~40 rows) and the "Key Pitfalls for v1.2" list (7 items) are exactly the kind of "locked conventions" D-05 says must survive — the compression target is removing session logs, the Performance Metrics table (per-plan minute counts — pure noise for a compressed STATE.md), and the verbose "Decisions" list at the bottom (which duplicates/supersedes the "Key Decisions" table above it with lower-value entries like "[Phase ?]: test summary").

**MILESTONES.md** — not read in full this session; D-05 specifies "one compact block per milestone (goal, outcome, key decisions)" — the plan should read the current 78-line file and compress in place rather than research further; this is a mechanical rewrite task, not a research question.

**GSD tooling interaction (from CONTEXT.md code_context):** deleting phase dirs 14-19 means any GSD progress/audit command that scans `.planning/phases/` will no longer see those phases — CONTEXT.md explicitly accepts this ("history moves to git; STATE.md/ROADMAP.md remain the live records"). No further verification needed; this is a locked decision, not an open question.

## Deferred v1.3 Items (must NOT appear in README as current features)

From `.planning/REQUIREMENTS.md` §Future Requirements — verified list, use as the negative-checklist for D-13's sweep:

- Dynamic local SPI zone creation at runtime
- Firmware OTA update mechanism — **caution:** firmware code already has OTA scaffolding (`picowota` for Pico, `ArduinoOTA`/ESP-IDF OTA partition for ESP32, both firmware READMEs document an OTA section) but REQUIREMENTS.md explicitly defers "Firmware OTA update mechanism" to v1.3, and the ESP32 README itself says "Full OTA push implementation is planned for a future phase." **Recommendation:** README's firmware walkthrough should mention OTA exists as firmware-side scaffolding without presenting it as a finished, supported v1.2 feature — match the ESP32 README's own hedged wording rather than overclaiming.
- PostgreSQL full-text search with GIN index
- Kubernetes multi-replica with PostgreSQL backend
- Ingress / TLS in Helm chart — confirm README doesn't imply ingress is a ready-to-use v1.2 feature (currently it doesn't — good, no fix needed, `ingress.enabled: false` is the correct framing already)
- WebSocket live feed (superseded — SSE was chosen instead; **do not reintroduce this wording anywhere**, since `/ws/zone/{id}` is a firmware-inbound WS, not a live-feed WS — keep these two WebSocket-shaped features clearly distinguished in the README so a reader doesn't conflate "SSE live feed" with "firmware zone WebSocket")

Also confirmed **Out of Scope** (permanent, not just v1.3-deferred) — must never appear as roadmap/future items either: Kotlin Native firmware, cloud sync, user auth, mobile app, JS framework, custom font/multilingual rendering.

## Common Pitfalls

### Pitfall 1: Treating REQUIREMENTS.md/CONTEXT.md prose as ground truth for firmware facts
**What goes wrong:** Writing the README firmware section from FW-02's "Arduino + ArduinoWebsockets" wording instead of the actual ESP-IDF codebase.
**Why it happens:** REQUIREMENTS.md was written before Phase 17-09's mid-phase pivot from PlatformIO to ESP-IDF; nobody went back and corrected the requirement's own prose (only the Traceability status marks it Complete).
**How to avoid:** Always resolve firmware facts from `firmware/esp32/README.md` + `firmware/esp32/CMakeLists.txt` + `.github/workflows/firmware-ci.yml`, never from REQUIREMENTS.md/CONTEXT.md wording.
**Warning signs:** Any README draft containing the string "PlatformIO" or "platformio.ini" for ESP32 — that string should not appear anywhere in the final README.

### Pitfall 2: Salvaging docs/ content without the D-02 verification pass
**What goes wrong:** Copying docs/deployment/production-guide.md's env var table wholesale, including `API_QUEUE_SIZE` which no longer exists in `application.yaml`.
**Why it happens:** docs/ files look authoritative (well-formatted tables) but were written for an earlier state of the config.
**How to avoid:** Grep `application.yaml` for every `${VAR_NAME:default}` before adding any variable to README; the file is short (81 lines) and can be fully cross-checked in one read.
**Warning signs:** Any config variable name that doesn't appear in a `grep -n "VAR_NAME" src/main/resources/application.yaml` result.

### Pitfall 3: Fixing docs/ content instead of dropping it (violates D-02)
**What goes wrong:** Noticing `API_QUEUE_SIZE` is stale and "fixing" it by finding what it should map to now, then adding a corrected version to README.
**Why it happens:** Instinct to preserve information rather than delete it.
**How to avoid:** D-02 is explicit: "Stale content is dropped, not fixed." If a docs/ fact doesn't verify against current code, it doesn't go in README at all — full stop, no substitute research to find the "real" answer.

### Pitfall 4: Deleting phase dirs 14-19 before Phase 20 itself is verified (violates D-07 sequencing)
**What goes wrong:** A single plan task deletes phase dirs 14-20 all at once, losing 20's own PLAN/SUMMARY files before the verifier can read them.
**Why it happens:** D-06 and D-07 look like the same action (both are "delete phase dirs") but have different timing.
**How to avoid:** Plans delete ONLY 14-19. Phase 20's own directory is deleted in a separate, explicitly-labeled final commit that happens after `/gsd-verify-work` / the verifier's 20-VERIFICATION.md is written — this must be documented as a manual/deferred final step in the phase SUMMARY and in STATE.md's "Next steps," not executed as a normal plan task.
**Warning signs:** A plan task's file list includes `.planning/phases/20-cleanup-docs/` for deletion — that should never appear in a task's own scope.

## Validation Architecture

This phase has no application code changes — REQUIREMENTS.md success criteria are file-existence/content-accuracy checks, not runtime behavior, so there is no unit/integration test framework involvement. Per project convention (JaCoCo/Kotest gate applies to `src/`, untouched by this phase), the existing test suite should simply stay green (regression guard: nothing in `src/` changes).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | N/A for this phase's own changes (Kotest/JUnit unaffected — no `src/` edits) |
| Config file | — |
| Quick run command | `./gradlew test` (regression-only; confirms no accidental src/ edits broke anything) |
| Full suite command | `./gradlew build` (includes JaCoCo coverage gate — must stay green since coverage gate is % of `src/`, untouched by doc changes) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|--------------------|--------------|
| CLEAN-01 (docs/ deleted) | `docs/` directory absent from working tree | smoke | `test ! -d docs && echo PASS` | N/A — trivial shell check, no test file needed |
| CLEAN-01 (STATE.md/MILESTONES.md compressed) | No session-log/noise content remains | manual | Read `.planning/STATE.md` and `.planning/MILESTONES.md`, confirm no `## Session` block, no per-plan-minute Performance Metrics table, no duplicate "[Phase ?]" low-value decision entries | N/A — content review, not automatable |
| CLEAN-01 (phase dirs 14-19 deleted) | Directories absent | smoke | `for d in 14 15 16 17 18 19; do test ! -d .planning/phases/${d}-*; done` | N/A |
| DOCS-01 (endpoint list complete) | README API tables include `/api/v1/live`, `/api/v1/history/export`, `/ws/zone/{id}` | smoke | `grep -q '/api/v1/live' README.md && grep -q '/api/v1/history/export' README.md && grep -q '/ws/zone/{id}' README.md` | N/A |
| DOCS-01 (firmware walkthrough present) | README has toolchain + wiring + build + flash + config for both Pico and ESP32 | manual | Read README, confirm each of the 5 sub-elements present for both platforms | N/A |
| DOCS-01 (no stale docs/ links) | Zero references to deleted `docs/` path outside `.planning/` archive | automated | The D-04 grep command listed above (§Repo-Wide docs/ Reference Sweep) | N/A |
| DOCS-01 (no deferred v1.3 features presented as current) | Grep for the 6 deferred-item keywords (OTA claimed as complete, GIN index, multi-replica, Ingress/TLS as ready, "WebSocket live feed") | manual | Read README against the §Deferred v1.3 Items checklist above | N/A |

### Sampling Rate
- **Per task commit:** Run the relevant smoke grep(s) from the table above for whatever the task just changed.
- **Per wave merge:** `./gradlew test` (regression-only, confirms zero `src/` drift) + full D-04 grep sweep.
- **Phase gate:** All manual-review rows above walked through once before `/gsd-verify-work`; `./gradlew build` green (coverage gate unaffected since 0 `src/` files change).

### Wave 0 Gaps
None — no test framework work needed. All verification in this phase is grep/read-based, not unit-test-based, because there is no runtime behavior to assert against.

## Security Domain

`workflow.nyquist_validation: true` is set but this phase makes no code changes — `security_enforcement` gate is not meaningfully applicable since there is no new attack surface, no new input handling, and no new dependency. Skip ASVS category mapping: this is a documentation-only phase with zero authentication, session, input-validation, or cryptography surface changes. The only "security-adjacent" fact worth carrying into the plan is that the README's zone-registration curl examples should not encourage insecure practices (e.g., don't suggest disabling RFC1918 validation) — but this is a documentation-accuracy concern, not a new threat surface.

## Package Legitimacy Audit

N/A — this phase installs no packages (no `npm install`, `pip install`, or `cargo add` anywhere in scope). Skip section per the protocol's own trigger condition ("whenever this phase installs external packages").

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | Official pico-sdk and ESP-IDF getting-started URLs for the non-Linux one-line pointer (D-09) | §Firmware Walkthrough Facts > Non-Linux host OS | Low — a stale doc URL just needs updating later; doesn't block phase completion since D-09 only requires a one-line pointer, not verified content |

**All other claims in this research were verified directly against source files in this repository this session** (route files, config.h, values.yaml, application.yaml, CI workflows, docs/*.md, STATE.md/REQUIREMENTS.md) — no other `[ASSUMED]` tags apply.

## Open Questions

1. **Does `docs/architecture/overview.md`, `docs/configuration/overview.md`, or `docs/testing/overview.md` contain any unique fact not already covered by the 5 files read in full?**
   - What we know: The 5 files read in full (api/reference.md, deployment/production-guide.md, guides/getting-started.md, guides/development.md, operations/monitoring-alerting.md) cover essentially all D-01 gap categories (endpoints, deployment, quick-start, troubleshooting).
   - What's unclear: Whether the 3 unread files contain anything not duplicated elsewhere.
   - Recommendation: Budget a single lightweight skim task in the plan (5 min each) rather than a deep read — expect near-zero net new salvage per the pattern already observed (every file so far either duplicates code-derivable facts or duplicates another docs/ file).

## Sources

### Primary (HIGH confidence — verified this session via direct file reads)
- `src/main/kotlin/com/anjo/routing/*.kt` (all 9 route files + Routing.kt) — endpoint inventory
- `src/main/resources/application.yaml` — env var defaults ground truth
- `src/main/kotlin/com/anjo/validation/HistoryValidators.kt`, `ZoneValidators.kt` — query param and zone-type validation behavior
- `src/main/kotlin/com/anjo/model/AddZoneRequest.kt`, `FirmwareMessage.kt`, `DisplayEvent.kt` — request/response shapes
- `firmware/pico/README.md`, `firmware/pico/config.h`, `firmware/pico/CMakeLists.txt` — Pico walkthrough ground truth
- `firmware/esp32/README.md`, `firmware/esp32/main/config.h`, `firmware/esp32/CMakeLists.txt`, `firmware/esp32/main/CMakeLists.txt` — ESP32 walkthrough ground truth (confirms ESP-IDF, not PlatformIO)
- `.github/workflows/firmware-ci.yml` — confirms exact CI build commands for both platforms (cross-check for README accuracy)
- `.devops/helm/textreaderrpi/values.yaml`, `Chart.yaml`, `templates/deployment.yaml` — Helm facts
- `.planning/phases/17-firmware-skeletons/17-09-SUMMARY.md` — confirms ESP32 PlatformIO→ESP-IDF migration (the critical correction)
- `docs/api/reference.md`, `docs/deployment/production-guide.md`, `docs/guides/getting-started.md`, `docs/guides/development.md`, `docs/operations/monitoring-alerting.md` — read in full for salvage assessment
- Repo-wide grep for `docs/` references (D-04 sweep)
- `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/phases/20-cleanup-docs/20-CONTEXT.md` — project context and constraints

### Secondary (MEDIUM confidence)
- None — no web/external research performed per phase scope ("all sources are in-repo").

### Tertiary (LOW confidence)
- Non-Linux toolchain doc URLs (A1 in Assumptions Log) — not fetched this session.

## Metadata

**Confidence breakdown:**
- Endpoint inventory: HIGH — read every route file directly
- Firmware facts: HIGH — read firmware READMEs, config.h, CMakeLists, and CI workflow directly; cross-verified the PlatformIO→ESP-IDF correction against the Phase 17-09 summary
- Helm facts: HIGH — read values.yaml, Chart.yaml, deployment.yaml directly
- docs/ salvage assessment: HIGH for the 5 files read in full; MEDIUM (unread) for architecture/overview.md, configuration/overview.md, testing/overview.md
- .planning/ compression facts: HIGH — sizes and structure verified via shell; content-compression judgment calls are Claude's Discretion per CONTEXT.md, not a research question

**Research date:** 2026-07-07
**Valid until:** Effectively permanent for this phase — all facts are pinned to the current repo state at the moment of research; if any source file changes before planning, re-verify affected sections only (docs-only phase, no external ecosystem drift risk).
