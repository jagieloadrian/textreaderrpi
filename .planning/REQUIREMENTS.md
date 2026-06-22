# TextReaderRpi v1.2 Requirements

**Milestone:** v1.2 — Firmware + Features + Refactor + Ops  
**Created:** 2026-06-21  
**Status:** Active

---

## History Enhancements

- [x] **HIST-04**: User can search display history by text content (`?search=` param, case-insensitive LIKE)
- [x] **HIST-05**: User can export display history to CSV file (`GET /api/v1/history/export`, RFC 4180, Content-Disposition attachment)
- [x] **HIST-06**: Search results highlight matched term with `<mark>` in the history HTML page

## Live Feed

- [ ] **LIVE-01**: User can subscribe to a real-time SSE stream of display events (`GET /api/v1/live`, MutableSharedFlow replay=5)
- [ ] **LIVE-02**: Status page shows real-time currently-displayed text via EventSource widget (no page reload needed)
- [ ] **LIVE-03**: SSE connection stays alive through proxies via 30-second heartbeat comment frames

## Zone Management

- [ ] **ZONE-09**: User can create a new network zone via form on `/zones` page (name + IP + type, 422 for local hardware types)
- [ ] **ZONE-10**: Pico/ESP32 firmware can connect to server as a zone via inbound WebSocket (`GET /ws/zone/{id}`, FirmwareZoneDriver)

## Firmware (C/C++)

- [ ] **FW-01**: RPi Pico W / Pico 2W firmware skeleton connects via WiFi, opens WebSocket to server, receives text+effect JSON, renders on attached MAX7219 (`firmware/pico/`, C, pico-sdk)
- [ ] **FW-02**: ESP32 series firmware skeleton connects via WiFi, opens WebSocket to server, receives text+effect JSON, renders on attached MAX7219 (`firmware/esp32/`, C++, Arduino + ArduinoWebsockets)

## Kubernetes + Helm

- [ ] **OPS-01**: TextReaderRpi is deployable to Kubernetes via Helm chart in `.devops/helm/textreaderrpi/` with `hardwareAccess.enabled` toggle, `-Xmx220m`, and health probes mapped to `/health` + `/health/ready`

## Refactoring

- [ ] **REF-05**: Main code and tests have no significant duplication (DRY/YAGNI pass: HistoryFilter data class, shared test base helpers, deduplication of repeated patterns)

## Cleanup + Docs

- [ ] **CLEAN-01**: `.planning/` files compressed (STATE.md, MILESTONES.md reduced to decisions/summaries/conventions; no informational noise); `docs/` folder deleted from repo
- [ ] **DOCS-01**: README.md updated to reflect v1.2 system state (new endpoints, firmware flash instructions, K8s/Helm section)

---

## Future Requirements (deferred to v1.3+)

- Dynamic local SPI zone creation at runtime (Pi4J context binding is startup-time; requires research into runtime SPI device registration)
- Firmware OTA update mechanism
- PostgreSQL full-text search with GIN index (overkill at 1000-row cap)
- Kubernetes multi-replica with PostgreSQL backend
- Ingress / TLS in Helm chart
- WebSocket live feed (was: SSE chosen instead as strictly better for one-directional server-to-browser push)

## Out of Scope

- Kotlin Native firmware for Pico/ESP32 — no bare-metal MCU targets in Kotlin/Native 2.3.21 (KT-44498 unresolved since 2021); C/C++ is the correct approach
- Cloud sync or remote access beyond home network
- User authentication/authorization
- Mobile app
- JS framework (React, Vue, HTMX) — Ktor HTML DSL remains sufficient
- Custom font support / multilingual text rendering

---

## Traceability

| REQ-ID | Phase | Status | Plan |
|--------|-------|--------|------|
| HIST-04 | Phase 14 | Planned | 14-01, 14-02, 14-03 |
| HIST-05 | Phase 14 | Planned | 14-01, 14-02 |
| HIST-06 | Phase 14 | Planned | 14-03 |
| LIVE-01 | Phase 15 | Planned | 15-01, 15-02 |
| LIVE-02 | Phase 15 | Planned | 15-03 |
| LIVE-03 | Phase 15 | Planned | 15-02 |
| ZONE-09 | Phase 16 | Pending | — |
| ZONE-10 | Phase 16 | Pending | — |
| FW-01 | Phase 17 | Pending | — |
| FW-02 | Phase 17 | Pending | — |
| OPS-01 | Phase 18 | Pending | — |
| REF-05 | Phase 19 | Pending | — |
| CLEAN-01 | Phase 20 | Pending | — |
| DOCS-01 | Phase 20 | Pending | — |
