# Architecture Patterns — v1.2 Integration Analysis

**Project:** TextReaderRpi
**Milestone:** v1.2 Firmware + Features + Refactor + Ops
**Researched:** 2026-06-21
**Scope:** How v1.2 features integrate with the existing v1.1 architecture

---

## Existing Architecture (v1.1 baseline)

```
HTTP Layer          com/anjo/routing/*
                    com/anjo/routing/ui/*
         |
         v
Service Layer       com/anjo/service/*
                    com/anjo/zone/*
         |
         v
Driver Layer        com/anjo/driver/*
         |
         v
DB Layer            com/anjo/db/*

Cross-cutting:      com/anjo/di/*  com/anjo/config/*  com/anjo/model/*
```

**Central dispatch path:**
```
POST /api/v1/text
  -> TextRoutes
  -> ScreenDriverService.displayImmediate()
      -> ZoneRegistry.broadcast() or ZoneRegistry.route()
          -> ZoneDriver.send()  [LocalZoneDriver or NetworkZoneDriver]
      -> HistoryRepository.insert()  [inside tryInsertHistory()]

SchedulerService.fire()
  -> ScreenDriverService.displayScheduled()
      -> ZoneRegistry.route()
      -> HistoryRepository.insert()
  -> WebhookService.send()  [scope.launch{} fire-and-forget]
```

---

## Feature 1: WebSocket Live Feed

### Decision: WebSocket over SSE

Use Ktor's `webSocket {}` server route. SSE is simpler but WebSocket is already understood by the team (NetworkZoneDriver uses WS client). The existing `ktor-client-websockets` is a client artifact; the server-side `ktor-server-websockets` must be added to `build.gradle.kts`.

### Where the broadcast call goes

Do NOT add session management to `ScreenDriverService`. It already owns mutex management, retry, history, and metrics — four responsibilities is already on the edge.

**New component: `LiveFeedService`** (`com/anjo/service/LiveFeedService.kt`)

```kotlin
class LiveFeedService {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(id: String, session: WebSocketSession) { sessions[id] = session }
    fun unregister(id: String) { sessions.remove(id) }

    suspend fun broadcast(event: LiveFeedEvent) {
        val json = Json.encodeToString(event)
        sessions.values.toList().forEach { session ->
            runCatching { session.send(Frame.Text(json)) }
        }
    }
}

@Serializable
data class LiveFeedEvent(
    val text: String,
    val effect: String,
    val zoneId: String?,
    val source: String,       // "IMMEDIATE" or "SCHEDULED"
    val timestamp: String
)
```

`LiveFeedService` is a singleton in `configureDI()`. `ScreenDriverService` receives it as an optional constructor parameter — the same nullable pattern already used for `historyRepository` and `webhookService`. After `executeWithRecovery()` succeeds inside `renderImmediate()` and `runScheduledRender()`, fire-and-forget:

```kotlin
displayScope.launch {
    liveFeedService?.broadcast(LiveFeedEvent(text, effect.name, zoneId, source, Instant.now().toString()))
}
```

This must be a `displayScope.launch {}` — same as the history insertion pattern — never a blocking call on the display path.

### WS route

New file: `com/anjo/routing/LiveFeedRoutes.kt`

```kotlin
fun Route.liveFeedRoutes(liveFeedService: LiveFeedService) {
    webSocket("/ws/feed") {
        val id = UUID.randomUUID().toString()
        liveFeedService.register(id, this)
        try {
            for (frame in incoming) { /* ignore client frames */ }
        } finally {
            liveFeedService.unregister(id)
        }
    }
}
```

Register in `Routing.kt` **outside** the `/api/v1` rate-limited block — WebSocket upgrades must not consume API rate limit tokens.

### UI page

`GET /live` added to `WebRoutes.kt`. New `LiveFeedPage.kt` in `com/anjo/web/templates/` renders a `<pre id="feed">` and a small inline `<script>` block that opens `new WebSocket("/ws/feed")` and prepends incoming JSON as text. This is the only justified inline JS in the project — it is a pure client-push receiver, not application logic.

### DI additions required

- `provide { liveFeedService }` in `configureDI()`
- `ScreenDriverService` constructor: add `liveFeedService: LiveFeedService? = null`
- `configureRouting()` call: add `liveFeedRoutes(liveFeedService)`
- `build.gradle.kts`: add `implementation(ktorLibs.ktor.server.websockets)`
- `HTTP.kt` or `DependencyInjection.kt`: `install(WebSockets) { pingPeriodMillis = 15_000 }`

---

## Feature 2: Dynamic Zone Creation

### Current state

`ZoneRegistry.addNetworkZone(zone)` already exists and is already called from `ZoneRoutes` at runtime. `POST /api/v1/zones` creates a network zone without restart. **The network zone creation requirement is already satisfied.** No new code is needed for that path.

### Local (hardware) zone runtime creation

Adding SPI/I2C zones at runtime is architecturally feasible because `ZoneRegistry.initLocalZone()` is already a private method that creates drivers from config — it can be promoted to `internal` and called post-startup.

The blocker is `pi4jContext`: it is currently a constructor-local variable in `ZoneRegistry`, not stored as a field. Fix: make it a field.

**`ZoneRegistry` changes:**
1. Add `private val pi4jContext: Context` field (store in secondary constructor)
2. Promote `initLocalZone()` from `private` to `internal`
3. Add `fun addLocalZone(config: ZoneConfig) { initLocalZone(config, pi4jContext) }` (idempotent — check `zones.containsKey(config.id)` first)

**New route:** `POST /api/v1/zones/local` in `ZoneRoutes.kt`
Accepts a `CreateLocalZoneRequest(id, type, chipSelect, numDevices)`, validates it, persists to a new `local_zones` table, calls `zoneRegistry.addLocalZone(config)`.

**Config persistence — Flyway V6 migration:**
```sql
CREATE TABLE IF NOT EXISTS "local_zones" (
    "id" VARCHAR(64) PRIMARY KEY,
    "type" VARCHAR(16) NOT NULL,
    "chip_select" INT NOT NULL,
    "num_devices" INT NOT NULL DEFAULT 1,
    "created_at" VARCHAR(32) NOT NULL
);
```

At startup, `ZoneRegistry` secondary constructor reads `local_zones` from DB (via a new `LocalZoneRepository`) and merges them with YAML-configured zones. YAML zones remain authoritative for production hardware; DB-persisted zones are runtime additions.

### UI zone list reload

All zone selectors are SSR. After zone creation, the user refreshes the page and `zoneRegistry.listAll()` returns the new zone. No change to the SSR-only strategy. Do not add AJAX polling for zone lists — consistent with the v1.1 decision record.

---

## Feature 3: Full-Text Search in Display History

### Integration point

`HistoryRepository.findPaginated()` already accepts `effect`, `source`, `zone` filters via `andWhere` chaining. Add `search: String?` as a fourth optional filter.

**Modified signature:**
```kotlin
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null,
    zone: String? = null,
    search: String? = null   // NEW
): Pair<List<HistoryRecord>, Long>
```

**SQL approach:** `LOWER(text) LIKE '%term%'` via Exposed's `lowerCase()` extension. At the 1000-row cap this is fast enough for H2 without an index. For PostgreSQL the same query works; a `pg_trgm` index is out of scope for home use.

```kotlin
if (search != null) {
    val term = search.lowercase()
    query = query.andWhere { HistoryTable.text.lowerCase() like "%$term%" }
}
```

**Guard:** Strip `%` and `_` from the search term before building the LIKE expression to prevent wildcard injection that could cause full-table scans:

```kotlin
val term = search.replace("%", "").replace("_", "").lowercase().take(64)
```

### Route changes

`GET /api/v1/history` gains a `search` query parameter. `HistoryRoutes.kt` adds:
```kotlin
val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
```

`HistoryService.findPaginated()` adds `search: String? = null` and passes it to the repository.

`HistoryUIRoutes.kt` parses the same `search` param and passes it to `historyService.findPaginated()`. `HistoryPage.kt` renders a text `<input name="search">` in the filter form with the current value echoed as the `value` attribute.

### New components

None. Modification only: `HistoryRepository`, `HistoryService`, `HistoryRoutes`, `HistoryUIRoutes`, `HistoryPage`.

---

## Feature 4: CSV Export

### Route

`GET /api/v1/history/export` — new route, no pagination, all rows matching current filters.

```kotlin
fun Route.historyExportRoute(historyService: HistoryService) {
    get("/history/export") {
        val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it != "ALL" }
        val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it != "ALL" }
        val zone   = call.request.queryParameters["zone"]?.takeIf { it != "ALL" }
        val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
        val records = historyService.findAll(effect, source, zone, search)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "history.csv")
                .toString()
        )
        call.respondOutputStream(ContentType.Text.CSV) {
            val writer = bufferedWriter()
            writer.write("id,text,effect,source,zone_id,displayed_at,schedule_id,webhook_status\n")
            records.forEach { r ->
                writer.write(
                    "${r.id},${r.text.csvEscape()},${r.effect},${r.source}," +
                    "${r.zoneId ?: ""},${r.displayedAt},${r.scheduleId ?: ""},${r.webhookStatus ?: ""}\n"
                )
            }
            writer.flush()
        }
    }
}
```

### Service and repository additions

`HistoryService.findAll(effect, source, zone, search): List<HistoryRecord>` delegates to `HistoryRepository.findAll()`. At 1000-row cap the full result fits in memory before streaming — no chunking needed.

`HistoryRepository.findAll()` reuses the same filter-building logic as `findPaginated()` but omits `limit` and `offset`.

### UI

A "Download CSV" anchor on `HistoryPage.kt` pointing to `/api/v1/history/export` with the current filter params serialized as query string. No JS required — it is a plain `<a href>`.

### Registration

Add `historyExportRoute(historyService)` inside the `/api/v1` block in `Routing.kt`. Declare it before `historyRoutes` for reading clarity (both use `get("/history/...")` paths without conflict).

### New components

- `com/anjo/routing/HistoryExportRoute.kt` (new file)
- `HistoryService.findAll()` (new method)
- `HistoryRepository.findAll()` (new method)
- `HistoryPage.kt` gains export anchor (modification)

---

## Feature 5: Firmware Submodule (Pico / ESP32)

### Repository structure

Add Gradle subprojects under `firmware/`:

```
firmware/
  pico/
    CMakeLists.txt    (pico-sdk C build)
    src/
      main.c          (WebSocket client + display logic)
  esp32/
    platformio.ini    (Arduino/ESP-IDF build)
    src/
      main.cpp
```

**Kotlin Native is not viable here.** Kotlin/Native targets Linux userspace (`linuxArm64`, `linuxX64`), not bare-metal MCU ISAs (RP2040 Cortex-M0+, ESP32 Xtensa/RISC-V). The PROJECT.md description "Kotlin Native WebSocket receiver" is aspirational — the actual firmware must be C (pico-sdk) for Pico or C++ (Arduino/ESP-IDF) for ESP32. If a Gradle wrapper is desired for build scripting, `firmware/pico/build.gradle.kts` can invoke `cmake` via `exec {}`, but the firmware source remains C.

### How firmware connects as a NetworkZoneDriver peer

The Pico/ESP32 is the WebSocket **client**, not the server. The Ktor server must expose a WebSocket **server** endpoint that firmware connects to — the inverse of `NetworkZoneDriver` (which is an outbound client connecting to remote zones).

**New server route:** `GET /ws/zone/{id}` where `{id}` is the zone name the firmware registers as.

**New component: `FirmwareZoneDriver`** (`com/anjo/zone/FirmwareZoneDriver.kt`)

```kotlin
class FirmwareZoneDriver(
    private val id: String,
    private val session: DefaultWebSocketServerSession,
    private val type: String = "MAX7219"
) : ZoneDriver {
    override suspend fun send(text: String, effect: Effect): Boolean =
        runCatching {
            session.send(Frame.Text(
                """{"text":${Json.encodeToString(text)},"effect":"${effect.name}"}"""
            ))
        }.isSuccess

    override fun status(): ZoneStatus = ZoneStatus(
        id = id,
        type = type,
        status = if (session.isActive) "ONLINE" else "OFFLINE"
    )
}
```

**New route:** `com/anjo/routing/FirmwareRoutes.kt`

```kotlin
fun Route.firmwareRoutes(zoneRegistry: ZoneRegistry) {
    webSocket("/ws/zone/{id}") {
        val zoneId = call.parameters["id"] ?: return@webSocket close()
        val driver = FirmwareZoneDriver(zoneId, this)
        zoneRegistry.register(zoneId, driver)
        try {
            for (frame in incoming) { /* heartbeat frames from firmware */ }
        } finally {
            zoneRegistry.removeZone(zoneId)
        }
    }
}
```

**Firmware zones are in-memory only** during the session — no DB persistence. When firmware reconnects, it re-registers. This avoids stale zone rows (same rationale as ZoneStatus being in-memory per v1.1 decision D-05).

### Firmware protocol

The firmware WebSocket client connects to `ws://<server-ip>:8080/ws/zone/<zone-name>`, receives JSON frames `{"text":"...","effect":"SCROLL"}`, and drives the local hardware. The server sends frames via `FirmwareZoneDriver.send()`.

### Dependencies

`ktor-server-websockets` must be added (currently absent — only `ktor-client-websockets` is present). The WS `install(WebSockets)` call is shared between `LiveFeedRoutes` and `FirmwareRoutes`.

---

## Feature 6: Kubernetes + Helm

### Chart structure

```
.devops/
  helm/
    textreaderrpi/
      Chart.yaml
      values.yaml
      templates/
        deployment.yaml
        service.yaml
        configmap.yaml
        secret.yaml
        pvc.yaml
```

### Key design decisions for Ktor/JVM on K8s

**H2 on K8s:** H2 file-mode requires a `PersistentVolumeClaim`. Use `accessModes: [ReadWriteOnce]` and `AUTO_SERVER=FALSE` (single-pod only). Multi-replica requires switching to PostgreSQL via the existing `DATABASE_URL`/`DATABASE_DRIVER` env vars — document this in `values.yaml`.

**Pi4J / GPIO on K8s:** Pi4J requires `/dev/spidev*` and `/dev/i2c-*` access. On K8s this needs `securityContext.privileged: true` and `hostDevice` mounts. Pin the pod to the Pi node with `nodeSelector`. This chart is an operator-managed, single-node chart — not a generic cloud chart.

**ConfigMap for env vars:**
```yaml
# templates/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Release.Name }}-config
data:
  PORT: "{{ .Values.port }}"
  DATABASE_URL: "{{ .Values.database.url }}"
  DATABASE_DRIVER: "{{ .Values.database.driver }}"
  DISPLAY_TYPE: "{{ .Values.display.type }}"
  # ... remaining env vars from docker-compose.yml
```

Sensitive values (`DATABASE_PASSWORD`) go in a `Secret`, not ConfigMap.

**PVC:**
```yaml
# templates/pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ .Release.Name }}-data
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: {{ .Values.persistence.size | default "1Gi" }}
```

**Liveness/readiness probes:**
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
```

These map directly to existing endpoints — no new server code needed.

**values.yaml defaults:**
```yaml
replicaCount: 1
image:
  repository: textreaderrpi
  tag: latest
port: 8080
persistence:
  enabled: true
  size: 1Gi
database:
  url: "jdbc:h2:file:/data/schedules;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE"
  driver: org.h2.Driver
display:
  type: MAX7219
```

**No server code changes.** The existing Jib-built Docker image (arm64 + amd64) works as the K8s container image. The chart is pure ops config.

---

## Feature 7: DRY/YAGNI Refactoring

### Route layer

- `HistoryRoutes.kt` and `HistoryUIRoutes.kt` both parse the same four filter parameters. Extract a `HistoryFilter` data class:
  ```kotlin
  data class HistoryFilter(val effect: String?, val source: String?, val zone: String?, val search: String?) {
      companion object {
          fun from(call: ApplicationCall): HistoryFilter = HistoryFilter(
              effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it != "ALL" },
              source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it != "ALL" },
              zone   = call.request.queryParameters["zone"]?.takeIf { it != "ALL" },
              search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
          )
      }
  }
  ```
- `page`/`size` parsing is duplicated across history routes. A `fun ApplicationCall.pageParam()` extension in a `QueryParamExtensions.kt` utility file removes the duplication.

### Service layer

- `ScreenDriverService.tryInsertHistory()` is already well-extracted. No further deduplication needed there.
- `displayImmediate()` and `displayScheduled()` share `acquireMutex()` and `withMutex()` — already extracted correctly.
- `WebhookService` and `LiveFeedService` (new) both do fire-and-forget fan-out to a collection of clients. A shared `FireAndForgetDispatcher` wrapper would be premature — YAGNI until a third consumer emerges.

### Test utilities

- All 27 test classes use `testApplication { module() }`. A shared `ApplicationTestBase` (Kotest `FunSpec` subclass) pre-wires the test application and exposes common assertions.
- `MockEngine`/`HttpClient` setup is duplicated across webhook and zone tests. Extract a `TestHttpClientFactory` object.
- The DI smoke test assertion (`GET /health -> 200 or 503`) can be shared as a base check in `ApplicationTestBase`.

### YAGNI guard — do NOT add

- Generic event bus / pub-sub for live feed. `ConcurrentHashMap<String, WebSocketSession>` in `LiveFeedService` is sufficient for home use with <10 browser tabs.
- Abstract `FilterCriteria` hierarchy for history queries. A data class with 4 nullable fields is cleaner and more readable.
- Repository interfaces. There is exactly one implementation per repository; interfaces add no value until a second implementation (mock) is needed — and MockK handles that without interfaces.

---

## Feature 8: .planning/ Compression

### Safe to prune from STATE.md

- **`Decisions` section (lines ~234–287):** Per-plan implementation notes documenting why a specific line was written in a now-complete phase. Archive to `milestones/v1.1-decisions.md`; replace section with a one-line reference.
- **`Performance Metrics` table:** Historical phase timings. Move to milestone archive.
- **`v1.1 Roadmap Summary` table:** All rows are COMPLETE. Replace with: "v1.1 complete — see milestones/v1.1-ROADMAP.md."
- **`Accumulated Context / New in Phase 11`:** Superseded by the Architecture Summary section.

### Must retain in STATE.md

- `Codebase Status` — current versions, test suite size
- `Architecture Summary` — layers + data flows (canonical reference for v1.2 work)
- `Critical Pitfalls to Watch` — still actionable (MAX7219 SPI direction, ONESHOT duplicate fire, etc.)
- `Open Decisions` — 4 hardware validation checkpoints still pending
- `DevOps` section — how to build and run
- `Deferred Items` — the 4 human-verify items

---

## Component Boundary Summary

| Component | Status | Touches | Integration Point |
|-----------|--------|---------|-------------------|
| `ktor-server-websockets` | NEW (dep) | `build.gradle.kts` | Required for LiveFeed + Firmware WS server routes |
| `LiveFeedService` | NEW | `service/` | Called by `ScreenDriverService` post-render; manages browser WS sessions |
| `LiveFeedRoutes` | NEW | `routing/` | `GET /ws/feed`; outside `/api/v1` rate limit block |
| `LiveFeedPage` | NEW | `web/templates/` | SSR page at `GET /live`; inline JS for WS receive only |
| `FirmwareZoneDriver` | NEW | `zone/` | Inbound WS session from MCU firmware; registered in `ZoneRegistry.register()` |
| `FirmwareRoutes` | NEW | `routing/` | `GET /ws/zone/{id}`; firmware connects here |
| `HistoryExportRoute` | NEW | `routing/` | `GET /api/v1/history/export`; calls `HistoryService.findAll()` |
| `HistoryService.findAll()` | NEW method | `service/HistoryService` | Unfiltered-with-filter-params; no pagination |
| `HistoryRepository.findAll()` | NEW method | `db/HistoryRepository` | Reuses filter-builder logic from `findPaginated()` |
| `LocalZoneRepository` | NEW | `db/` | CRUD for `local_zones` table (runtime-created hardware zones) |
| `Flyway V6` | NEW | `db/` | `local_zones` DDL migration |
| `HistoryRepository.findPaginated()` | MODIFIED | `db/` | Add `search: String?` param |
| `HistoryService.findPaginated()` | MODIFIED | `service/` | Add `search: String?` param |
| `HistoryRoutes` | MODIFIED | `routing/` | Add `search` query param; link to export route |
| `HistoryUIRoutes` | MODIFIED | `routing/ui/` | Add search input; add export anchor |
| `HistoryPage` | MODIFIED | `web/templates/` | Search `<input>`; export `<a href>` |
| `ScreenDriverService` | MODIFIED | `service/` | Add `liveFeedService: LiveFeedService? = null`; fire-and-forget broadcast after render |
| `ZoneRegistry` | MODIFIED | `service/` | Store `pi4jContext` as field; add `addLocalZone()` method; promote `initLocalZone()` to internal |
| `ZoneRoutes` | MODIFIED | `routing/` | Add `POST /api/v1/zones/local` |
| `Routing.kt` | MODIFIED | `routing/` | Register `liveFeedRoutes`, `firmwareRoutes`, `historyExportRoute` |
| `DependencyInjection.kt` | MODIFIED | `di/` | Add `LiveFeedService` binding; install WebSockets plugin |
| `.devops/helm/textreaderrpi/` | NEW | ops | Helm chart; zero server code changes |
| `firmware/pico/` | NEW | firmware | C/pico-sdk WS client |
| `firmware/esp32/` | NEW | firmware | C++/Arduino WS client |

---

## Build Order — Dependency Graph

```
1. ktor-server-websockets added (build.gradle.kts + install(WebSockets))
   -> unblocks LiveFeedService, LiveFeedRoutes, FirmwareRoutes, FirmwareZoneDriver
   (everything WS-related is blocked until this lands)

2a. LiveFeedService (no deps beyond Ktor WS classes)
    -> ScreenDriverService modification (needs LiveFeedService to exist first)
    -> LiveFeedRoutes (needs LiveFeedService)
    -> LiveFeedPage (parallel with routes)

2b. FirmwareZoneDriver + FirmwareRoutes (parallel with 2a)
    (both depend on step 1; independent of LiveFeed)

3a. HistoryRepository.findPaginated() search param
    -> HistoryService.findPaginated() search param
    -> HistoryRoutes search param
    -> HistoryUIRoutes + HistoryPage search field
    (sequential top-down; fully independent of steps 1-2)

3b. HistoryRepository.findAll() + HistoryService.findAll() + HistoryExportRoute
    (parallel with 3a; same layer dependency ordering applies)

4. ZoneRegistry pi4jContext field + addLocalZone()
   -> LocalZoneRepository + Flyway V6 migration
   -> ZoneRoutes POST /zones/local
   (V6 migration must land before the route is testable end-to-end)

5. .devops/helm/ (fully independent — no server code)

6. firmware/pico/ + firmware/esp32/ (depends only on FirmwareRoutes from step 2b being deployed)

7. DRY/YAGNI refactoring (safe only after all feature code is stable)

8. .planning/ compression (editorial; last)
```

**Critical path:** `ktor-server-websockets` dep -> `LiveFeedService` -> `ScreenDriverService` modification. All other features are independent branches.

---

## Architecture Anti-Patterns to Avoid

### Anti-Pattern 1: WS session management inside ScreenDriverService
**What:** Adding `ConcurrentHashMap<String, WebSocketSession>` and broadcast logic to `ScreenDriverService`.
**Why bad:** `ScreenDriverService` already manages mutex, retry, history, and metrics. Session management is orthogonal to display rendering; testing becomes significantly harder.
**Instead:** `LiveFeedService` as a separate singleton with exactly one responsibility.

### Anti-Pattern 2: Blocking the display coroutine on WS broadcast
**What:** Calling `liveFeedService.broadcast(event)` without `displayScope.launch {}`.
**Why bad:** A slow or disconnected browser client stalls the display mutex. Scheduled text is delayed by the WebSocket timeout.
**Instead:** Always fire-and-forget via `displayScope.launch { liveFeedService?.broadcast(...) }` — same pattern as webhooks in `SchedulerService.fire()`.

### Anti-Pattern 3: H2 AUTO_SERVER=TRUE for K8s multi-replica
**What:** Enabling H2 TCP server mode so multiple pods share one H2 file.
**Why bad:** H2 TCP mode has known concurrency defects with connection pools; it is not production-safe under concurrent JPA/Exposed access.
**Instead:** Keep `replicaCount: 1` for H2 builds. Document in `values.yaml` that multi-replica requires switching to PostgreSQL via env vars.

### Anti-Pattern 4: Firmware zone persisted to network_zones on inbound WS connect
**What:** Calling `zoneRepository.upsert(zone)` when firmware connects on `/ws/zone/{id}`, using `ip = "FIRMWARE"` as a placeholder.
**Why bad:** Firmware reconnects create duplicate upsert noise; the IP placeholder is meaningless for server-initiated connections; stale rows survive restart.
**Instead:** Firmware zones are in-memory only during the session, consistent with the v1.1 D-05 decision (ZoneStatus in-memory only).

### Anti-Pattern 5: Search LIKE without wildcard sanitization
**What:** Passing user input directly as `"%${search}%"` where `search` contains `%` or `_`.
**Why bad:** `search = "100%"` becomes `"%100%%"` — a valid LIKE expression but semantically wrong. At 1000 rows it is harmless for performance but produces incorrect results.
**Instead:** Strip `%` and `_` from the search term before building the LIKE expression, or escape them with `\` and set `ESCAPE '\'` in the Exposed `like()` call.

---

## Sources

All findings are based on direct code inspection of the v1.1 codebase (confidence: HIGH — the actual source files are the source of truth).

- `ScreenDriverService.kt` — existing render pipeline, mutex model, history insertion, nullable-optional dependency pattern
- `ZoneRegistry.kt` — zone driver registry, `addNetworkZone` runtime pattern, `initLocalZone` private method
- `NetworkZoneDriver.kt` — outbound WS client model (design basis for inverting to `FirmwareZoneDriver`)
- `HistoryRepository.kt` — filter query structure, `andWhere` chaining (basis for search and `findAll`)
- `DependencyInjection.kt` — DI registration pattern, `ApplicationStopping` cleanup hooks
- `Routing.kt` — route registration structure, rate-limit block boundary
- `ZoneRoutes.kt` — existing runtime zone creation flow (confirms network zone path is complete)
- `docker-compose.yml` — full env var set (basis for Helm ConfigMap)
- `build.gradle.kts` — dependency inventory (confirms `ktor-server-websockets` is absent; only client WS is present)
- `NetworkZonesTable.kt`, `ZoneRepository.kt` — DB persistence pattern for reference when designing `local_zones`
