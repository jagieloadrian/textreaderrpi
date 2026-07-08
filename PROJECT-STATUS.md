# Project Status

**Updated:** 2026-07-08 · **Milestone:** v1.2 (closed) · **Branch:** feat/18-helm-chart

## Completed work

- v1.2 milestone closed (7/7 phases, 35/35 plans); `.planning/` compressed to essentials — detailed phase docs and pre-v1.2 research live in git history only (see `.planning/milestones/ARCHIVE.md`).
- Ponytail cleanup: removed dead `EffectRendererFactory` class, unused `Max7219Config.gpioPins`/`LcdConfig.rows`/`columns` config (+ matching env vars across `.env.example`, `docker-compose.yml`, Helm `configmap.yaml`, README), merged duplicate rate-limit installers, shrank `ConfigLoader` boilerplate.
- CI/firmware fixes (untested on real hardware — needs a green CI run + on-device check before trusting):
  - ESP32: added missing `json` (cJSON) component requirement, `esp_websocket_client` component-manager manifest.
  - Pico: fixed `config.h`/`lwipopts.h` include paths, `picowota` made optional with watchdog-reboot fallback, `pico_rand`/`hardware_watchdog` linked.
  - Pico networking rearchitected: `mongoose_config.h`/`main.c`/`ws_client.c`/`captive_portal.c` moved from manual `cyw43_arch_*` calls (which need lwIP sockets, unsupported without FreeRTOS) to mongoose's built-in `MG_ENABLE_TCPIP` + `MG_TCPIP_DRIVER_INIT` driver. Same code path for `pico_w` and `pico2_w`.
  - `ci.yml`/`firmware-ci.yml`: dropped `on: push` (was double-running with `on: pull_request`), cached `~/.espressif` to skip the ESP-IDF toolchain reinstall on cache hit.
- `.devops/` checked clean: `helm lint` + `helm template` (all 4 CI variants) pass, shell scripts syntax-check, YAML valid.

## Important decisions

- Pico OTA fallback (no `picowota` vendored) does a plain `watchdog_reboot`, not a bootloader-mode reboot — fine until `picowota` is actually added.
- Dropped the "stored WiFi creds fail → retry with `config.h` defaults" behavior on Pico; mongoose's connection is now async, so a synchronous retry isn't a fit. Revisit if the fallback is actually needed.
- `.planning/codebase/` (live architecture map) and `ui-reviews/` are a different category from phase working notes — left untouched, not compressed.

## Next steps

1. Verify firmware CI is green (esp32 + pico + pico2_w) and, ideally, flash-test the Pico networking rewrite on real hardware.
2. `/gsd-new-milestone` — v1.2 is closed, no active milestone.
3. Carry-over from v1.2: 2 Critical Helm findings still open (CrashLoop probes, PVC deletion on uninstall), `18-SECURITY.md` still missing (`/gsd-secure-phase 18`).
