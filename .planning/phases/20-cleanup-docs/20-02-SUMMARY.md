---
phase: 20-cleanup-docs
plan: 02
subsystem: docs
tags: [readme, firmware, pico, esp32, esp-idf, pico-sdk, documentation]

# Dependency graph
requires:
  - phase: 20-cleanup-docs plan 01
    provides: README API/Configuration/Build/Deployment sections extended and accuracy-swept, Firmware anchor already present in Table of Contents
provides:
  - README.md '## Firmware' top-level section with complete Linux flash walkthroughs for Pico (RP2040/RP2350) and ESP32
  - Shared firmware troubleshooting table (4 verified rows, not duplicated per platform)
  - One-line non-Linux pointer to official pico-sdk/ESP-IDF getting-started docs
affects: [20-cleanup-docs plan 03 (accuracy sweep), 20-cleanup-docs plan 04]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Firmware README content transposed from firmware/*/README.md ground truth, not re-derived from stale REQUIREMENTS.md/CONTEXT.md prose"]

key-files:
  created: []
  modified: [README.md]

key-decisions:
  - "ESP32 walkthrough written strictly from ESP-IDF 5.2.1/CMake facts (idf.py, CMakeLists.txt idf_component_register, firmware-ci.yml) — the obsolete PlatformIO/Arduino wording from REQUIREMENTS.md FW-02 and CONTEXT.md D-08 was never reproduced (20-RESEARCH.md Pitfall 1)"
  - "Pico NUM_DEVICES documented as 1 (from actual firmware/pico/config.h), not the generic '4' shown in firmware/pico/README.md's constant-reference table — config.h is the higher-authority source per the task's read_first ordering"
  - "OTA (picowota for Pico, ESP-IDF OTA partition for ESP32) worded as scaffolding only, matching each firmware README's own hedged phrasing — not presented as a shipped v1.2 feature per REQUIREMENTS.md Future Requirements"

requirements-completed: [DOCS-01]

coverage:
  - id: D1
    description: "README '## Firmware' section exists with a complete Pico (RP2040/RP2350) walkthrough: toolchain table, wiring, config.h block, build (PICO_SDK_PATH + pico_w/pico2_w), flash (--target flash + manual BOOTSEL alternative)"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q '## Firmware' README.md && grep -q '### Pico' README.md && grep -q 'PICO_SDK_PATH' README.md && grep -q 'pico2_w' README.md && grep -q 'textreader_combined.uf2' README.md"
        status: pass
    human_judgment: false
  - id: D2
    description: "README '### ESP32' subsection documents ESP-IDF 5.2.1 exclusively (idf.py set-target/build/flash) with zero occurrences of the superseded PlatformIO build tool anywhere in README.md"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q '### ESP32' README.md && grep -qi 'esp-idf' README.md && grep -q 'idf.py' README.md && grep -q 'set-target esp32' README.md && ! grep -qi 'platformio' README.md"
        status: pass
    human_judgment: false
  - id: D3
    description: "A single shared Firmware Troubleshooting table (Symptom / Cause-check, 4 rows) and a one-line non-Linux pointer with pico-sdk + ESP-IDF getting-started URLs appear once at the end of the Firmware section"
    requirement: "DOCS-01"
    verification:
      - kind: other
        ref: "grep -q 'Symptom' README.md && grep -c '| Symptom | Cause / check |' README.md (expect 1)"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-07-08
status: complete
---

# Phase 20 Plan 02: Firmware Flash Walkthrough Summary

**Added a complete, code-verified `## Firmware` section to README.md covering Linux toolchain install, wiring, config.h, build, and flash for both the Pico (pico-sdk/CMake) and ESP32 (ESP-IDF/CMake) targets, plus a shared troubleshooting table and non-Linux pointer.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-07-08T06:06:00Z (approx)
- **Completed:** 2026-07-08T06:15:27Z
- **Tasks:** 2
- **Files modified:** 1 (README.md)

## Accomplishments
- New `## Firmware` top-level section inserted between `## API` and `## Configuration`, matching the 20-UI-SPEC.md ordering and table conventions exactly
- `### Pico (RP2040/RP2350)` subsection: toolchain table (CMake, arm-none-eabi-gcc, pico-sdk 2.1.1, picotool, picowota, ninja-build), MAX7219 wiring table, `config.h` code block (verified against actual file, not the firmware README's generic constant table), build/flash bash blocks referencing `PICO_SDK_PATH` and `--target flash`
- `### ESP32` subsection: toolchain table (ESP-IDF 5.2.1, CMake, Python), MAX7219 wiring table, `config.h` code block, build/flash bash blocks using `idf.py set-target`/`idf.py build`/`idf.py flash` — zero PlatformIO references anywhere in the final README
- One shared `### Firmware Troubleshooting` table with the 4 research-verified rows (USB detection, WiFi provisioning, zone-not-appearing, blank display) — not duplicated per platform
- One-line non-Linux pointer to the official pico-sdk and ESP-IDF getting-started guides, placed once at the end of the Firmware section

## Task Commits

Each task was committed atomically:

1. **Task 1: Firmware section intro + Pico (RP2040/RP2350) walkthrough** - `cef88bf` (docs)
2. **Task 2: ESP32 walkthrough, shared troubleshooting table, non-Linux pointer** - `1fe376c` (docs)

**Plan metadata:** committed as part of this summary/state commit below.

_Note: this is a doc-only plan; both tasks are `docs()` commits, no `test`/`feat` split was applicable._

## Files Created/Modified
- `README.md` - Added `## Firmware` section (140 lines): `### Pico (RP2040/RP2350)`, `### ESP32`, `### Firmware Troubleshooting`, non-Linux pointer

## Decisions Made
- Split the single logical content addition into two atomic task commits (Pico-only, then ESP32+troubleshooting+pointer) to match the plan's two-task structure exactly, even though both tasks touch the same file region — done by reverting and re-applying the Pico-only insertion first, then layering the ESP32 addition in a second edit, rather than committing the combined diff in one shot.
- Used `firmware/pico/config.h`'s actual `NUM_DEVICES 1` and `ZONE_ID "pico-01"` values (not `firmware/pico/README.md`'s own generic constant-reference table, which shows a stale default of `4`) — config.h is the ground truth per the task's read_first ordering and per D-02's "verify against current code" rule.

## Deviations from Plan

None - plan executed exactly as written. Both tasks' acceptance criteria (grep-verified) passed on the first attempt with no auto-fixes needed.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. This is a documentation-only change; nothing to install or configure to verify the README content itself (firmware toolchain installation is the walkthrough's own subject matter, not a setup requirement for this plan).

## Next Phase Readiness

- README Firmware section is complete and internally consistent with `firmware/pico/README.md` and `firmware/esp32/README.md` ground truth.
- Plan 03's accuracy sweep (per 20-RESEARCH.md §Existing README Accuracy Issues and the D-04 `docs/` reference grep) is unaffected by this plan's scope (README.md only, Firmware section only) and can proceed independently.
- No blockers.

---
*Phase: 20-cleanup-docs*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: README.md
- FOUND: .planning/phases/20-cleanup-docs/20-02-SUMMARY.md
- FOUND: cef88bf (Task 1 commit)
- FOUND: 1fe376c (Task 2 commit)
