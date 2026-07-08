# Research Summary — TextReaderRpi v1.2

**Researched:** 2026-06-21  
**Confidence:** HIGH (codebase read in full; Kotlin Native infeasibility confirmed via official sources)

---

## Executive Summary

v1.2 is an additive feature release on a stable v1.1 architecture. Four of six features (search, CSV export, zone creation UX, Helm) are low-risk with minimal new dependencies. The live feed requires one new DI component and the WebSocket server dep. Firmware is the only high-complexity item — Kotlin Native is infeasible for Pico/ESP32 (confirmed via official Kotlin/Native target list + KT-44498); C/C++ is the confirmed approach.

Critical pre-work: (1) add `ktor-server-websockets` once in Phase 16 (unblocks live feed + firmware); (2) firmware language confirmed as C/C++.

---

## Stack Additions

| Artifact | Version | Feature | Rationale |
|----------|---------|---------|-----------|
| `io.ktor:ktor-server-sse` | 3.5.0 | Live feed | One-way push; native browser reconnect; version-aligned |
| `io.ktor:ktor-server-websockets` | 3.5.0 | Live feed + Firmware | Server-side WS for session mgmt + firmware inbound |
| `com.jsoizo:kotlin-csv-jvm` | 1.10.0 | CSV export | Pure Kotlin; RFC 4180; 50 KB; no transitive deps |

Features 2 (dynamic zones), 3 (full-text search), 5 (K8s/Helm) add zero new JVM dependencies.

**What NOT to add:** PostgreSQL FTS/GIN (LIKE sufficient at 1000-row cap), Apache Commons CSV, Redis, Helm PostgreSQL subchart by default.

---

## Feature Landscape

### Table Stakes (must have)
1. **Full-text search** — `?search=` param, `LOWER(text) LIKE LOWER('%term%')` via Exposed `lowerCase()`, search box on History page
2. **CSV export** — `GET /api/v1/history/export`, RFC 4180 quoting, Content-Disposition attachment, reuses filter params
3. **Dynamic zone creation UX** — extend `AddZoneRequest` with `name`+`type`, Zones page form, `DisplayType` validation, 422 for local hardware types
4. **Browser live feed** — SSE endpoint `GET /api/v1/live`, `MutableSharedFlow<LiveEvent>(replay=5)` singleton, `EventSource` widget on Status page
5. **Firmware skeleton** — `firmware/pico/` (C, pico-sdk) + `firmware/esp32/` (C++, Arduino); `FirmwareZoneDriver` + `GET /ws/zone/{id}` on server
6. **README update** — firmware flash instructions, new endpoints, K8s section; write last

### Differentiators (should have)
- 30-second SSE heartbeat, `<mark>` search highlighting, mDNS responder on firmware, effect queue on firmware

### Defer to v1.3+
- Firmware OTA, PostgreSQL FTS, dynamic local SPI zone creation, Ingress/TLS in Helm, multi-replica K8s

---

## Architecture Integration Points

**New components:**

| Component | Layer | Integration |
|-----------|-------|-------------|
| `LiveFeedService` | `service/` | Singleton; called by `ScreenDriverService` post-render via `displayScope.launch` |
| `FirmwareZoneDriver` | `zone/` | Implements `ZoneDriver`; wraps inbound `DefaultWebSocketServerSession` |
| `FirmwareRoutes` | `routing/` | `GET /ws/zone/{id}`; registers/removes `FirmwareZoneDriver` in `ZoneRegistry` |
| `HistoryExportRoute` | `routing/` | `GET /api/v1/history/export`; new `HistoryService.findAll()` |
| `firmware/pico/` | firmware | CMake + C; separate build from Gradle |
| `firmware/esp32/` | firmware | PlatformIO + C++; separate build from Gradle |
| `.devops/helm/textreaderrpi/` | ops | Pure infrastructure; zero server code changes |

**Modified:** `HistoryRepository.findPaginated()` (+search param), `ScreenDriverService` (+liveFeedService fire-and-forget), `ZoneRegistry` (+addLocalZone(), stored pi4jContext), `DependencyInjection.kt` (+LiveFeedService, install WebSockets), `Routing.kt` (+new routes).

**Critical path:** `ktor-server-websockets` dep + `install(WebSockets)` in Phase 16 unblocks both live feed and firmware driver simultaneously.

---

## Top Pitfalls

1. **WS frame send from display coroutine** — `DefaultWebSocketSession.send()` not thread-safe across contexts. Use `Channel<String>` per session; display path calls `trySend()` only.
2. **Non-atomic check-then-insert in `ZoneRegistry`** — concurrent POSTs can both pass `isLocal` guard. Replace with `ConcurrentHashMap.compute()`.
3. **LIKE wildcard injection** — strip `%` and `_` from search term in `SearchValidators` (never in route handler per project rule).
4. **CSV Content-Disposition omitted** — browsers render inline without `Content-Disposition: attachment`. Set before `respondText`.
5. **Pi4J device mounts missing in K8s** — Pod crashes without `hostPath` for `/dev/gpiochip0` + `/dev/spidev0.0`. Helm `hardwareAccess.enabled` toggle with OFFLINE fallback.
6. **H2 multi-replica on K8s** — H2 file mode is single-process; `replicaCount > 1` requires PostgreSQL. Document in `values.yaml`.
7. **IDE Extract Method generates KDoc stubs** — violates no-comments rule; delete generated stubs manually.
8. **.planning compression losing decisions** — 4 decisions must survive: `displaySource` rename, `parseDiscoveryReply` SSRF guard, Material 3 dark-default CSS, `testApplication` first-HTTP-call guard.

---

## Recommended Phase Order

| Phase | Name | Rationale |
|-------|------|-----------|
| 14 | History Enhancements | Zero new deps; search + CSV; ship fast |
| 15 | Dynamic Zone Creation UX | Backend exists; model + UI only |
| 16 | Browser Live Feed (SSE) | New DI singleton; installs WS dep (shared with Phase 17) |
| 17 | Firmware Integration | C/C++ skeletons + `FirmwareZoneDriver`; highest complexity |
| 18 | Kubernetes + Helm | Pure ops; can run in parallel after Docker stable |
| 19 | DRY/YAGNI Refactoring + .planning Compression | After all features stable |
| 20 | README Rewrite | Last; depends on all features finalised |

*Research completed: 2026-06-21 | Ready for roadmap: yes*
