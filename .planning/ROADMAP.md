# TextReaderRpi - Development Roadmap

**Last Updated:** 2026-06-22

---

## Milestones

- ✅ **v1.0 MVP** — Phases 1–5 (shipped 2026-05-28) → [Archive](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Refactor + Fixes + UI + New Features** — Phases 6–13 (shipped 2026-06-21) → [Archive](milestones/v1.1-ROADMAP.md)
- 📋 **v1.2 Firmware + Features + Refactor + Ops** — Phases 14–20 (planned)

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

<details>
<summary>✅ v1.1 Refactor + Fixes + UI + New Features (Phases 6–13) — SHIPPED 2026-06-21</summary>

- [x] Phase 6: MAX7219 Hardware Fix (2 plans) — completed 2026-06-12
- [x] Phase 7: Scheduler Schema Stabilisation (3 plans) — completed 2026-06-14
- [x] Phase 8: Refactor + Dead Code Analysis (5 plans) — completed 2026-06-15
- [x] Phase 9: Display History + Audit Log (3 plans) — completed 2026-06-15
- [x] Phase 10: Webhooks (3 plans) — completed 2026-06-15
- [x] Phase 11: Multi-Zone Displays (5 plans) — completed 2026-06-16
- [x] Phase 11.2: Code Quality Cleanup (3 plans) — completed 2026-06-16
- [x] Phase 12: Observability Gap Closures (3 plans) — completed 2026-06-18
- [x] Phase 13: UI/UX Refresh (4 plans) — completed 2026-06-21

</details>

### 📋 v1.2 Firmware + Features + Refactor + Ops (Planned)

- [x] **Phase 14: History Enhancements** - Full-text search and CSV export for display history (completed 2026-06-22)
- [x] **Phase 15: SSE Live Feed** - Real-time display events streamed to the browser status page (completed 2026-06-22)
- [x] **Phase 16: Zone Management** - Zone creation form + inbound firmware WebSocket endpoint (completed 2026-06-23)
- [ ] **Phase 17: Firmware Skeletons** - RPi Pico (C/pico-sdk) and ESP32 (C++/Arduino) firmware
- [ ] **Phase 18: Kubernetes + Helm** - Helm chart in `.devops/helm/textreaderrpi/` for K8s deployment
- [ ] **Phase 19: DRY/YAGNI Refactoring** - Deduplication pass across main code and tests
- [ ] **Phase 20: Cleanup + Docs** - .planning/ compression, delete docs/, update README

---

## Phase Details

### Phase 14: History Enhancements

**Goal**: Users can search display history by text content and export filtered results to CSV.
**Depends on**: Phase 13 (history page and API are complete)
**Requirements**: HIST-04, HIST-05, HIST-06
**Success Criteria** (what must be TRUE):

  1. User types a search term in the history page search box and the results list shows only entries whose text contains that term, with the matched portion highlighted in `<mark>` tags
  2. User submits `GET /api/v1/history?search=foo` and receives only matching entries (case-insensitive)
  3. User clicks "Export CSV" on the history page and the browser downloads a `.csv` file containing all currently-filtered history rows in RFC 4180 format with a `Content-Disposition: attachment` header
  4. Search terms containing SQL wildcard characters (`%`, `_`) are stripped before the LIKE query executes (no injection)

**Plans**: 3/3 plans complete

- [x] 14-01-PLAN.md — HistoryFilter, HistoryValidators sanitizer, kotlin-csv dep, repository search/findAll, service exportCsv
- [x] 14-02-PLAN.md — API: search param + GET /api/v1/history/export CSV download endpoint
- [x] 14-03-PLAN.md — UI: highlightText `<mark>` helper, search input, Export CSV link, sticky search

### Phase 15: SSE Live Feed

**Goal**: Users can watch the currently-displayed text update in real time on the status page without reloading.
**Depends on**: Phase 14
**Requirements**: LIVE-01, LIVE-02, LIVE-03
**Success Criteria** (what must be TRUE):

  1. User opens `GET /api/v1/live` in a browser or `curl --no-buffer` and receives a continuous SSE stream; each time text is sent to a display a `data:` event arrives within one second
  2. Status page shows the most recently displayed text updating live via an `EventSource` widget — no manual refresh needed
  3. An SSE connection open for more than 30 seconds without display activity receives a heartbeat comment frame (`: keep-alive`) so proxy servers do not close it
  4. A new SSE subscriber immediately receives the last five events (replay=5) without waiting for the next display action

**Plans**: 3/3 plans complete
**UI hint**: yes

Plans:
**Wave 1**

- [x] 15-01-PLAN.md — DisplayEvent model + DisplayEventBus (SharedFlow replay=5) + emit wiring in ScreenDriverService + DI + Wave-0 tests (LIVE-01)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 15-02-PLAN.md — SSE route `GET /api/v1/live` (collect + 30s heartbeat) + install(SSE) + routing outside rate-limit + route tests (LIVE-01, LIVE-03)
- [x] 15-03-PLAN.md — Status page Live Feed article + live-feed.js EventSource widget (textContent, no innerHTML) + WebRoutesTest (LIVE-02)

### Phase 16: Zone Management

**Goal**: Users can create new network zones through the UI without restarting the server, and firmware devices can connect as zones via WebSocket.
**Depends on**: Phase 15 (ktor-server-websockets installed in this phase unblocks firmware driver)
**Requirements**: ZONE-09, ZONE-10
**Success Criteria** (what must be TRUE):

  1. User fills in the "Add Zone" form on `/zones` with a name, IP, and display type and submits it; the new zone appears immediately in the zone list without a server restart
  2. Submitting the "Add Zone" form with a local hardware display type (e.g. MAX7219) returns a 422 error explaining that local hardware types must be configured at startup
  3. A Pico or ESP32 device that opens `GET /ws/zone/{id}` is registered in ZoneRegistry as a FirmwareZoneDriver; text sent to that zone ID appears in the device's serial monitor output
  4. When the firmware device disconnects, the zone transitions to OFFLINE status and subsequent sends to that zone return a graceful error rather than a crash

**Plans**: 4/4 plans complete
**UI hint**: yes

Plans:

- [x] 16-04-PLAN.md

**Wave 1**

- [x] 16-01-PLAN.md — Schema + model foundation: V6 migration (ip nullable + display_subtype), DisplayType.FIRMWARE, AddZoneRequest/NetworkZone fields, FirmwareMessage wire contract, ktor-server-websockets dep (ZONE-09, ZONE-10)

**Wave 2** *(blocked on Wave 1)*

- [x] 16-02-PLAN.md — Firmware WebSocket: FirmwareZoneDriver (Channel + AtomicReference), ZoneRegistry.registerFirmwareZone via compute(), GET /ws/zone/{id} route, install(WebSockets), Routing outside rate limiter (ZONE-10)

**Wave 3** *(blocked on Wave 2)*

- [x] 16-03-PLAN.md — Add Zone form + backend: ZoneValidators (422 local types, RFC1918), POST /zones rewrite (name-as-id, FIRMWARE/NETWORK branch), ZonesPage form + type toggle, app.js addZone (ZONE-09)

### Phase 17: Firmware Skeletons

**Goal**: Working C/C++ firmware skeletons for RPi Pico W and ESP32 connect to the server, receive text+effect JSON over WebSocket, render it on a selectable display driver, support OTA updates, and offer WiFi provisioning via captive portal.
**Depends on**: Phase 16 (server-side WebSocket endpoint `GET /ws/zone/{id}` must exist)
**Requirements**: FW-01, FW-02
**Success Criteria** (what must be TRUE):

  1. Flashing `firmware/pico/build/textreader.uf2` onto a Pico W (with correct WiFi credentials and server URL set in `config.h`) causes it to connect to the server WebSocket, receive the string "Hello", and scroll it on the attached MAX7219
  2. Building and flashing `firmware/esp32/` via PlatformIO onto an ESP32 board causes it to connect to the server WebSocket, receive a text+effect JSON payload, and render it on the attached MAX7219
  3. Both firmware projects have a `README.md` inside their directory describing the build tool, wiring diagram reference, and flash procedure
  4. If the WiFi or server connection drops, each firmware implementation attempts reconnection automatically rather than hanging
  5. OTA firmware update: a new firmware binary can be pushed to the device over WiFi without physical access (Pico: pico-sdk HTTP OTA or equivalent; ESP32: ArduinoOTA / ESP.update())
  6. WiFi captive portal provisioning: a device with no WiFi credentials stored boots into AP mode and serves a captive portal page where the user enters SSID/password; credentials are saved to flash and the device reboots into normal mode

**Plans**: 6 plans

Plans:

**Wave 1**

- [ ] 17-01-PLAN.md — Server wire protocol: FirmwareMessage + ZoneDriver.send timing fields (speed/blinkPeriod/fadeSteps), threaded through ZoneRegistry + ScreenDriverService (FW-01)

**Wave 2** *(blocked on Wave 1)*

- [ ] 17-02-PLAN.md — TextRequest timing fields + RequestValidators positive-int guards + TextRoutes forwarding + API tests (FW-01, FW-02)
- [ ] 17-03-PLAN.md — Pico W/2W firmware: pico-sdk CMake, Mongoose WS, cJSON, MAX7219 driver + 5x8 font + 4 effects, reconnect, picowota OTA, Mongoose captive portal, README (FW-01)
- [ ] 17-04-PLAN.md — ESP32 firmware: PlatformIO, ArduinoWebsockets, ArduinoJson, MD_Parola MAX7219 + 4 effects, reconnect, ArduinoOTA, WiFiManager captive portal, README (FW-02)

**Wave 3** *(blocked on Wave 2 firmware)*

- [ ] 17-05-PLAN.md — Firmware CI: firmware-ci.yml build-check pico_w + pico2_w (cmake) + esp32 (pio run) + flashable artifact upload (FW-01, FW-02)

**Wave 4** *(blocked on Wave 1-3)*

- [ ] 17-06-PLAN.md — Human-verify checkpoint: physical Pico W/2W + ESP32 flash, scroll, reconnect, OTA, captive portal (FW-01, FW-02)

### Phase 18: Kubernetes + Helm

**Goal**: TextReaderRpi can be deployed to a Kubernetes cluster using a Helm chart with hardware-access and resource controls.
**Depends on**: Phase 13 (stable Docker image with all v1.2 features is prerequisite)
**Requirements**: OPS-01
**Success Criteria** (what must be TRUE):

  1. Running `helm install textreaderrpi .devops/helm/textreaderrpi/` on a K8s cluster starts a pod that passes both `/health` and `/health/ready` liveness/readiness probes
  2. The chart's `values.yaml` exposes a `hardwareAccess.enabled` toggle; when false the pod starts without GPIO/SPI device mounts and falls back to OfflineDisplayDriver without crashing
  3. The JVM heap in the Deployment spec is capped at `-Xmx220m`, keeping memory within the 256 MB project constraint
  4. `helm lint` passes with no errors or warnings on the chart directory

**Plans**: TBD (estimated 2 plans)

### Phase 19: DRY/YAGNI Refactoring

**Goal**: Main code and test suite have no significant duplication — shared helpers extracted, repeated patterns eliminated.
**Depends on**: Phase 17 (all v1.2 features complete so refactoring scope is final)
**Requirements**: REF-05
**Success Criteria** (what must be TRUE):

  1. A `HistoryFilter` data class (or equivalent) consolidates the repeated `search`, `zone`, `effect`, `source` parameter groups that currently appear in multiple route handlers and repository calls
  2. Test files share a common base helper or companion object for repeated setup patterns (e.g. `testApplication` bootstrapping, standard fixture builders) with no copy-paste duplication longer than 5 lines across test classes
  3. JaCoCo line coverage gate remains at 70% or above after all deduplication changes
  4. The build compiles with zero warnings introduced by the refactoring pass

**Plans**: TBD (estimated 2 plans)

### Phase 20: Cleanup + Docs

**Goal**: The repository is clean — `.planning/` is compressed to decisions only, `docs/` is deleted, and README reflects the v1.2 system.
**Depends on**: Phase 19 (all features and refactoring complete before documentation is written)
**Requirements**: CLEAN-01, DOCS-01
**Success Criteria** (what must be TRUE):

  1. The `docs/` directory no longer exists in the repository (deleted and committed)
  2. `.planning/STATE.md` and `.planning/MILESTONES.md` contain only decisions, summaries, and conventions — no informational noise or raw log data from intermediate planning sessions
  3. `README.md` lists all v1.2 endpoints (including `/api/v1/live`, `/api/v1/history/export`, `/ws/zone/{id}`), includes a firmware flash quick-start section for Pico and ESP32, and has a Kubernetes/Helm deployment section
  4. `README.md` accurately reflects the current project state with no references to features that were deferred or removed

**Plans**: TBD (estimated 2 plans)

---

## Progress

| Phase | Name | Milestone | Plans Complete | Status | Completed |
|-------|------|-----------|----------------|--------|-----------|
| 1 | MVP — Core Text Display | v1.0 | 1/1 | ✅ Complete | 2026-05-26 |
| 2 | Enhanced Display Support | v1.0 | 7/7 | ✅ Complete | 2026-05-26 |
| 3 | Production Ready | v1.0 | 5/5 | ✅ Complete | 2026-05-27 |
| 4 | Cleanup + Observability | v1.0 | 5/5 | ✅ Complete | 2026-05-27 |
| 5 | Scheduling + Effects | v1.0 | 11/11 | ✅ Complete | 2026-05-28 |
| 6 | MAX7219 Hardware Fix | v1.1 | 2/2 | ✅ Complete | 2026-06-12 |
| 7 | Scheduler Schema Stabilisation | v1.1 | 3/3 | ✅ Complete | 2026-06-14 |
| 8 | Refactor + Dead Code Analysis | v1.1 | 5/5 | ✅ Complete | 2026-06-15 |
| 9 | Display History + Audit Log | v1.1 | 3/3 | ✅ Complete | 2026-06-15 |
| 10 | Webhooks | v1.1 | 3/3 | ✅ Complete | 2026-06-15 |
| 11 | Multi-Zone Displays | v1.1 | 5/5 | ✅ Complete | 2026-06-16 |
| 11.2 | Code Quality Cleanup | v1.1 | 3/3 | ✅ Complete | 2026-06-16 |
| 12 | Observability Gap Closures | v1.1 | 3/3 | ✅ Complete | 2026-06-18 |
| 13 | UI/UX Refresh | v1.1 | 4/4 | ✅ Complete | 2026-06-21 |
| 14 | History Enhancements | v1.2 | 0/3 | Not started | — |
| 15 | SSE Live Feed | v1.2 | 0/3 | Not started | — |
| 16 | Zone Management | v1.2 | 3/3 | ✅ Complete | 2026-06-23 |
| 17 | Firmware Skeletons | v1.2 | 0/6 | Not started | — |
| 18 | Kubernetes + Helm | v1.2 | 0/2 | Not started | — |
| 19 | DRY/YAGNI Refactoring | v1.2 | 0/2 | Not started | — |
| 20 | Cleanup + Docs | v1.2 | 0/2 | Not started | — |
