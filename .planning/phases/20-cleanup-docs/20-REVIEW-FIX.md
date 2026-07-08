---
phase: 20-cleanup-docs
fixed_at: 2026-07-08T11:56:21Z
review_path: .planning/phases/20-cleanup-docs/20-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 20: Code Review Fix Report

**Fixed at:** 2026-07-08T11:56:21Z
**Source review:** .planning/phases/20-cleanup-docs/20-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Systemd deploy walkthrough is broken — jar filename mismatch between documented build task and install script

**Files modified:** `README.md`
**Commit:** `2bce9bf`
**Applied fix:** Verified `build.gradle.kts:161` (`archiveFileName.set("textreaderrpi.jar")`) and `.devops/host/install-systemd.sh:11` (`JAR_SOURCE=.../build/libs/TextReaderRpi-all.jar`) — confirmed the mismatch is real. Since this phase's scope is README.md only (install-systemd.sh is out of scope), added a `cp build/libs/textreaderrpi.jar build/libs/TextReaderRpi-all.jar` bridging step between `./gradlew buildFatJar` and the install script in the Systemd section, plus a one-line explanation of why the step exists. The documented sequence now works end-to-end without modifying the script.

### WR-02: `picowota` documented as a "git submodule" but no `.gitmodules` entry exists

**Files modified:** `README.md`
**Commit:** `da38653`
**Applied fix:** Confirmed no `.gitmodules` file exists anywhere in the repo/history, and `firmware/pico/CMakeLists.txt:24-28` treats `picowota` as an optional subdirectory that gracefully degrades when absent. Changed the Pico toolchain table row from `git submodule` / `git submodule update --init --recursive` to `manual clone, optional`, with a note that `textreader_combined.uf2` (needed for first-flash) is not built and OTA is disabled without it. Note: `firmware/pico/README.md:15` has the identical stale claim but was left untouched — out of scope per this phase's instructions (README.md only).

### WR-03: Zone-registration `curl` example omits two required JSON fields

**Files modified:** `README.md`
**Commit:** `85049bf`
**Applied fix:** Confirmed `AddZoneRequest` (`src/main/kotlin/com/anjo/model/AddZoneRequest.kt:6-11`) requires non-nullable `name` and `type`, and traced `ZoneValidators.kt`/`ZoneRoutes.kt` to confirm any `type` value other than the local hardware types (`MAX7219`, `LCD`, `OLED`, `UNKNOWN`) or `FIRMWARE` is accepted as a valid network zone type as long as `ip` is a private RFC1918 address. Updated the curl example body to `{"name":"living-room","type":"NETWORK","ip":"192.168.1.42"}`, matching the pattern of the adjacent firmware-zone example.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-08T11:56:21Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
