---
phase: 20-cleanup-docs
reviewed: 2026-07-08T11:52:53Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - README.md
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-07-08T11:52:53Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed `README.md` against the current source tree (routing files, `application.yaml`, `ConfigLoader.kt`, `build.gradle.kts`/version catalog, the Pico/ESP32 firmware `config.h` and `CMakeLists.txt`, the Helm chart templates/values, and `.devops/host/install-systemd.sh`). Most of the sweep held up well: TOC anchors all resolve, API route tables match the actual Ktor routes exactly (including status codes, WS close code, SSE heartbeat), env var defaults in the Configuration table match `application.yaml` (not `.env.example`, which is stale but out of scope), Gradle task names are real Ktor-plugin tasks, GPIO wiring tables match the firmware source, and library versions (Ktor 3.5, Kotlin 2.3, Exposed 1.3, Pi4J 4.x, JVM 25, 70% JaCoCo gate) all check out. No leftover `docs/` references remain anywhere in the tree.

Three factual inaccuracies survived the sweep, all in the newly-written Firmware and Systemd sections: a `curl` example for zone registration that omits two required fields and will 400 if copy-pasted, a `picowota` "git submodule" claim that has no corresponding `.gitmodules` entry (the command documented is a no-op), and a Systemd deployment walkthrough where the build task and the install script disagree on the JAR filename, so the two-line "build then install" sequence as documented fails outright.

## Warnings

### WR-01: Systemd deploy walkthrough is broken — jar filename mismatch between documented build task and install script

**File:** `README.md:436-441`
**Issue:** The Systemd section documents:
```bash
./gradlew buildFatJar
sudo ./.devops/host/install-systemd.sh
```
`buildFatJar` (via the `shadowJar` config in `build.gradle.kts:161`, `archiveFileName.set("textreaderrpi.jar")`) produces `build/libs/textreaderrpi.jar`. But `.devops/host/install-systemd.sh:11` looks for `${PROJECT_ROOT}/build/libs/TextReaderRpi-all.jar` — a different filename that `buildFatJar` never produces (confirmed by running the task; only `textreaderrpi.jar` and an unrelated `TextReaderRpi-0.0.1.jar` from a plain `jar` task appear in `build/libs/`). Running the two documented commands in sequence exits with `Missing .../build/libs/TextReaderRpi-all.jar` from the script, contradicting the README's own error hint (`./gradlew shadowJar`, which also does not produce that filename since the shadow plugin is configured to output `textreaderrpi.jar`).
**Fix:** Either update `install-systemd.sh`'s `JAR_SOURCE` to `build/libs/textreaderrpi.jar` and update the doc to keep `buildFatJar`, or document the actual expected build command that produces `TextReaderRpi-all.jar`. Verify the fix by actually running the two documented commands end-to-end.

### WR-02: `picowota` documented as a "git submodule" but no `.gitmodules` entry exists — the documented fetch command is a no-op

**File:** `README.md:170`
**Issue:** The Pico toolchain table lists:
```
| picowota | git submodule | OTA bootloader — `git submodule update --init --recursive` |
```
There is no `.gitmodules` file anywhere in the repo (checked working tree and full git history), and no `firmware/pico/picowota` directory ships in the repo. `firmware/pico/CMakeLists.txt:24-28` treats `picowota` as optional and gracefully degrades ("picowota subdirectory not found; OTA features will be disabled") when it's absent. Consequently:
1. `git submodule update --init --recursive` does nothing on a fresh clone (no submodule is registered to fetch).
2. Because `picowota_build_combined` is only invoked `if(COMMAND picowota_build_combined)` (`CMakeLists.txt:106-110`), the `textreader_combined.uf2` output — which the README's Build section (`README.md:207`) and "Manual alternative" flash step (`README.md:215`) both depend on for first-flash — will not be produced on a default/fresh clone, since picowota is never actually fetched by the documented command.
**Fix:** Either add picowota as an actual git submodule (`git submodule add <picowota-repo> firmware/pico/picowota`) so the documented command works, or change the table entry to describe the real acquisition method (e.g., "manually clone into `firmware/pico/picowota`, optional — required only for the combined first-flash UF2 / OTA"), and note that `textreader_combined.uf2` is not produced unless picowota is present.

### WR-03: Zone-registration `curl` example omits two required JSON fields — will fail as documented

**File:** `README.md:78-81`
**Issue:**
```bash
# Manually register a remote display by IP
curl -X POST http://localhost:8080/api/v1/zones \
  -H 'Content-Type: application/json' \
  -d '{"ip":"192.168.1.42"}'
```
`AddZoneRequest` (`src/main/kotlin/com/anjo/model/AddZoneRequest.kt:6-11`) requires non-nullable `name: String` and `type: String` with no defaults. A body containing only `ip` will fail kotlinx.serialization deserialization; `ErrorHandling.kt` catches `SerializationException` and returns an error response rather than the `201 Created` implied by the surrounding text/comment (contrast with the "Register a firmware zone" example two lines below, which correctly includes `name` and `type` and is annotated `# -> 201 Created`).
**Fix:** Add the required fields, e.g.:
```bash
curl -X POST http://localhost:8080/api/v1/zones \
  -H 'Content-Type: application/json' \
  -d '{"name":"living-room","type":"NETWORK","ip":"192.168.1.42"}'
```
(adjust `type` to whatever the actual non-FIRMWARE zone type constant is).

## Info

### IN-01: `.env.example` GPIO defaults silently diverge from the documented/actual defaults

**File:** `README.md:310-313`
**Issue:** The Configuration table's GPIO defaults (`GPIO_SPI_CE=24`, `GPIO_SPI_MOSI=19`, `GPIO_SPI_SCK=23`) correctly match `application.yaml`'s interpolated defaults and are accurate. However, `.env.example` (the file the README instructs users to `cp` in the same section) ships different values (`GPIO_SPI_CE=8`, `GPIO_SPI_MOSI=10`, `GPIO_SPI_SCK=11`), so a user following `cp .env.example .env.local` without editing will silently run with different GPIO pins than the table documents as defaults. Not a README bug per se (the table itself is accurate against `application.yaml`), but worth a one-line callout since it will confuse anyone diffing the table against the file the README just told them to copy.
**Fix:** Sync `.env.example`'s GPIO values to match `application.yaml`'s defaults (24/19/9/23), or add a short note in the README that `.env.example` intentionally documents non-default sample values.

### IN-02: `API_QUEUE_SIZE` env var is dead — present in `.env.example` but read nowhere in source, and rightly omitted from the README table

**File:** `README.md:303-337`
**Issue:** Not a README defect (the table correctly does not mention it), but flagging for completeness since the accuracy sweep is meant to catch drift: `API_QUEUE_SIZE` appears in `.env.example` but is never referenced by `ConfigLoader.kt` or `application.yaml`. If a future doc pass adds it back thinking it's a real knob, it would reintroduce a documentation/reality mismatch.
**Fix:** No README change needed. Consider removing `API_QUEUE_SIZE` from `.env.example` in a follow-up cleanup pass (out of scope for this file).

---

_Reviewed: 2026-07-08T11:52:53Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
