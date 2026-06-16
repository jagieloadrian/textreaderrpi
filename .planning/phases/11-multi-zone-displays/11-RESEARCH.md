# Phase 11: Multi-Zone Displays - Research

**Researched:** 2026-06-16
**Domain:** Multi-zone display routing, WebSocket client, UDP broadcast, mDNS discovery, Pi4J multi-SPI
**Confidence:** MEDIUM (core patterns from codebase; network-layer patterns LOW from web search)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Local Zone Configuration**
- D-01: YAML zones list — `display.zones: [{id, type, numDevices, gpioPins, ...}]`. New `ZonesConfig` data class replaces the single `DisplayConfig` block. Existing single-zone config paths are migrated.
- D-02: When `POST /api/v1/text` is called without a `?zone=` param, broadcast to ALL zones simultaneously — both local (SPI/I2C) and network. Parallel coroutines, best-effort.
- D-03: Per-zone SPI pin configuration follows Pi4J 4.0.0 SPI API constraints — each zone needs a unique `id()` string and different `SpiChipSelect`.
- D-04: A zone that fails hardware init at startup is registered as OFFLINE. Application starts normally; other zones operate.
- D-05: Zone status in `GET /api/v1/zones` is dynamic — based on last render attempt.
- D-06: `POST /api/v1/text?zone=X` where zone X is OFFLINE → `503 Service Unavailable`.
- D-07: Mixed hardware types supported in one zones list.

**Network Zones**
- D-08: All 8 ZONE requirements land in Phase 11.
- D-09: Pi is the WebSocket client connecting to external devices. External firmware is out of scope.
- D-10: Heartbeat via periodic WebSocket ping frames. No pong within timeout → zone marked OFFLINE.
- D-11: Text routed to offline external zone → 503, log, no retry.
- D-12: Manually-added zones persisted in `network_zones` DB table (H2/PostgreSQL via Exposed).
- D-13: Auto-discovered zones also persisted in `network_zones` table — single source of truth.
- D-14: No-zone broadcast targets ALL zones — local + network.

**ZoneRegistry Architecture**
- D-15: New `ZoneRegistry` service holds `Map<String, ZoneDriver>`. `ScreenDriverService` receives `ZoneRegistry` instead of single `DisplayDriver`.
- D-16: New `ZoneDriver` interface — `send(text: String, effect: Effect): Boolean`. `LocalZoneDriver` wraps `DisplayDriver`. `NetworkZoneDriver` wraps a WebSocket client.
- D-17: Every zone call goes through full `ScreenDriverService` pipeline (mutex per zone, history, metrics, retry, conflict policy). `zoneId: String` param added to `displayImmediate()` and `displayScheduled()`.
- D-18: Broadcast fires to all zones in parallel. Response: `{successful: ["main", "status"], failed: [{"kitchen", "OFFLINE"}]}`.

**Network Discovery Protocol**
- D-19: Two-track discovery: UDP broadcast scan (on-demand) + mDNS passive background listener (continuous).
- D-20: `POST /api/v1/zones/discover` — Pi broadcasts on port 54321, waits 3 seconds, registers new devices, returns newly found zones.
- D-21: UDP payload: Pi → `{"type":"TEXTREADERRPI_DISCOVER"}`. Device replies → `{"name":"kitchen","ip":"192.168.1.50","type":"MAX7219"}`.
- D-22: mDNS passive listener for `_textreaderrpi._tcp.local`. Uses `jmdns` JVM library.
- D-23: Discovery scan timeout: 3 seconds.

### Claude's Discretion
- WebSocket ping interval for heartbeat (D-10) — planner picks a reasonable default (e.g. 15s ping, 30s timeout).
- `network_zones` table schema — planner designs based on Exposed patterns from `SchedulesTable` and `HistoryTable`.
- How `jmdns` is wired into the Ktor lifecycle — follow `ApplicationStarted`/`ApplicationStopping` subscription pattern used by `SchedulerService`.

### Deferred Ideas (OUT OF SCOPE)
- External device firmware (ESP32/Arduino implementation) — v1.2+.
- UI button for discovery — Phase 13.
- Dynamic zone creation via API without restart — v1.2+.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ZONE-01 | Multiple locally-attached displays (SPI/I2C) as named zones; backward-compatible default zone | Pi4J multi-SPI: unique id() + different SpiChipSelect per zone; ZonesConfig replaces DisplayConfig |
| ZONE-02 | External displays announce via UDP broadcast or mDNS | UDP DatagramSocket broadcast scan + JmDNS 3.6.3 listener on `_textreaderrpi._tcp.local` |
| ZONE-03 | Pi auto-discovers and registers network displays | NetworkDiscoveryService with mDNS background coroutine + UDP on-demand scan; zone persisted to `network_zones` table |
| ZONE-04 | WebSocket communication with external displays | Ktor client `ktor-client-websockets` 3.5.0; NetworkZoneDriver wraps WS session; text+effect as JSON frame |
| ZONE-05 | Heartbeat / health check — Pi tracks online/offline per external zone | WS ping 15s interval; no pong within 30s → mark OFFLINE in `network_zones` table and ZoneRegistry |
| ZONE-06 | User can manually add external display by IP address | `POST /api/v1/zones/{ip}` endpoint; persist to `network_zones` with discoveryMethod=MANUAL; ZoneRegistry tries WS connect |
| ZONE-07 | `POST /api/v1/text?zone=X` routes to named zone; schedules have zoneId | TextRoutes parses `?zone=X`, passes to ScreenDriverService; `zoneId` already on Schedule model |
| ZONE-08 | `GET /api/v1/zones` — list all zones with status | ZoneRegistry.listAll() returns ZoneStatus list; endpoint serializes to JSON |
</phase_requirements>

---

## Summary

Phase 11 extends the single-display architecture into a multi-zone routing system. The core change is replacing the single `DisplayDriver` reference in `ScreenDriverService` with a `ZoneRegistry` that holds a `Map<String, ZoneDriver>`. Local SPI/I2C zones are initialized from YAML config at startup; network zones are discovered via UDP broadcast and mDNS and persisted in a new `network_zones` database table.

The Pi is a WebSocket **client** that connects outward to external display devices. Each network zone is a long-lived WebSocket connection managed by a `NetworkZoneDriver`. Heartbeat runs as a coroutine inside each `NetworkZoneDriver` using Ktor's built-in WS ping support. Zone status (`ONLINE`/`OFFLINE`) is a runtime property derived from the last render outcome, not a persisted field queried on every request.

The biggest complexity concentration is in three places: (1) the Pi4J multi-SPI initialization that must use unique `id()` strings and different `SpiChipSelect` values per zone to avoid startup collision, (2) the `ScreenDriverService` refactor to route by `zoneId` while keeping per-zone mutex isolation, and (3) the `NetworkDiscoveryService` that must manage two async discovery tracks (UDP on-demand, mDNS background) and maintain WS connections to discovered devices.

**Primary recommendation:** Build in wave order — config/registry first (ZONE-01, ZONE-07, ZONE-08), then network protocol (ZONE-02 through ZONE-06). This matches the existing wave pattern of 2 tasks per plan.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Zone config loading | Config/DI layer | — | ZonesConfig parsed by ConfigLoader, injected into ZoneRegistry at startup |
| Local zone hardware init | Driver layer | DI layer | Max7219Matrix/LcdDisplay/OledDisplay constructed in DI with per-zone Pi4J config |
| Zone routing | Service layer (ZoneRegistry) | Route layer | ZoneRegistry.route(zoneId, text, effect); TextRoutes only parses ?zone= param |
| Per-zone mutex / history / metrics | Service layer (ScreenDriverService) | — | ScreenDriverService already owns these; zoneId param plumbed through |
| WS connection to external displays | Service layer (NetworkZoneDriver) | — | Each NetworkZoneDriver owns one WS connection with reconnect loop |
| UDP broadcast scan | Service layer (NetworkDiscoveryService) | — | On-demand; called by POST /api/v1/zones/discover endpoint |
| mDNS passive listening | Service layer (NetworkDiscoveryService) | — | Background coroutine for lifetime of application |
| Zone persistence | Database layer (ZoneRepository) | — | `network_zones` table; Exposed suspendTransaction pattern |
| Zone status API | Route layer (ZoneRoutes) | Service layer | GET /api/v1/zones delegates to ZoneRegistry.listAll() |
| Manual zone add | Route layer (ZoneRoutes) | Service layer | POST /api/v1/zones/{ip} validates and delegates to ZoneRegistry |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.ktor:ktor-client-websockets` | 3.5.0 | WebSocket client for external display connections | Same Ktor version as server; CIO engine already in use; built-in ping support |
| `org.jmdns:jmdns` | 3.6.3 [ASSUMED] | mDNS passive service discovery on JVM | Established JVM mDNS library; latest stable Dec 2023; no Kotlin-native alternative with same maturity |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.net.DatagramSocket` | JDK built-in | UDP broadcast sender + receiver | UDP discovery scan — no dependency needed |
| `org.jetbrains.exposed:exposed-*` | 1.3.0 | `network_zones` table and ZoneRepository | Already in project; follow SchedulesTable pattern exactly |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `org.jmdns:jmdns` | `dns-sd-kt` (Kotlin multiplatform) | dns-sd-kt is newer and Kotlin-native but has very low download counts — SUS risk |
| `ktor-client-websockets` | OkHttp WebSocket | OkHttp not in project; would add a second HTTP client — explicitly rejected in STATE.md |

**New installation:**
```bash
# In gradle/ktor-libs.versions.toml — add:
# jmdns = "3.6.3"
# ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
# jmdns = { module = "org.jmdns:jmdns", version.ref = "jmdns" }

# In build.gradle.kts — add:
implementation(ktorLibs.ktor.client.websockets)
implementation(ktorLibs.jmdns)
```

**Version verification (run before locking):** [ASSUMED] — versions sourced from web search and GitHub releases page. Verify:
```bash
# Confirm ktor-client-websockets 3.5.0 on Maven Central:
curl -s "https://central.sonatype.com/api/v1/publisher/deployments/search?namespace=io.ktor&name=ktor-client-websockets&version=3.5.0" 2>/dev/null | head -5
# Confirm jmdns 3.6.3:
curl -s "https://central.sonatype.com/api/v1/publisher/deployments/search?namespace=org.jmdns&name=jmdns&version=3.6.3" 2>/dev/null | head -5
```

---

## Package Legitimacy Audit

> Package legitimacy seam only supports npm/pypi/crates ecosystems. Maven packages cannot be checked via the automated seam. Manual assessment applies.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `io.ktor:ktor-client-websockets` | Maven Central | ~8 yrs | Very high (JetBrains official) | github.com/ktorio/ktor | OK | Approved — official Ktor module, same version group as existing dependencies |
| `org.jmdns:jmdns` | Maven Central | ~15 yrs | High (used by Android/openHAB/etc.) | github.com/jmdns/jmdns | OK [ASSUMED] | Approved — long-established JVM mDNS library; latest release Dec 2023 |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

*Both packages are well-established. `io.ktor:ktor-client-websockets` is from JetBrains (same publisher as existing Ktor deps). `org.jmdns:jmdns` is the canonical JVM mDNS implementation used by major projects including openHAB. Legitimacy tagged `[ASSUMED]` because Maven ecosystem is outside the automated seam — planner should verify Maven Central coordinates before first build.*

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/text?zone=X
         │
         ▼
   TextRoutes.kt
   parse ?zone param
   zone=null → "broadcast"
         │
         ▼
ScreenDriverService.displayImmediate(text, effect, policy, zoneId)
   per-zone Mutex(zoneId)
   EffectRenderer.render()
   HistoryRepository.insert(zoneId=…)
         │
         ▼
   ZoneRegistry.route(zoneId, text, effect)
    ┌────────────────────────────┐
    │   Map<String, ZoneDriver>  │
    │  "main" → LocalZoneDriver  │──→ Max7219Matrix (SPI CS_0)
    │  "status" → LocalZoneDriver│──→ OledDisplay (I2C)
    │  "kitchen" → NetworkZone.. │──→ WebSocket → external ESP32
    └────────────────────────────┘
         │                   │
    Broadcast path:      Single zone path:
    parallel coroutines  direct route → driver.send()
    aggregate results


STARTUP FLOW:
ZonesConfig (YAML)
   └─ local zones list
         │
   ZoneRegistry.init()
   ├─ foreach local zone: Max7219Matrix(ctx, numDevices, zoneId=N)
   │     ├─ success → LocalZoneDriver(driver) [ONLINE]
   │     └─ fail    → LocalZoneDriver(OfflineDisplayDriver) [OFFLINE]
   └─ ZoneRepository.findAll()
         └─ foreach network zone: NetworkZoneDriver(ip, port)
               └─ connect() coroutine → WebSocket


NETWORK ZONE LIFECYCLE:
NetworkZoneDriver
   ├─ connectLoop(): while(scope.isActive) {
   │     try { client.webSocket(...) { heartbeatLoop(); receiveLoop() } }
   │     catch { delay(reconnectMs) }
   │   }
   ├─ heartbeatLoop(): WS plugin sends ping every 15s; no pong → session closes
   └─ send(text, effect): outgoing.send(Frame.Text(json))


DISCOVERY FLOW:
POST /api/v1/zones/discover
   └─ NetworkDiscoveryService.scanUdp()
         ├─ DatagramSocket.send(broadcast to 255.255.255.255:54321)
         ├─ collect replies for 3s
         └─ for each new device:
               ZoneRepository.upsert(discoveryMethod=UDP)
               ZoneRegistry.addNetworkZone(ip)

mDNS background (ApplicationStarted → ApplicationStopping):
   JmDNS.addServiceListener("_textreaderrpi._tcp.local.")
   └─ serviceResolved() → ZoneRepository.upsert(discoveryMethod=MDNS)
                        → ZoneRegistry.addNetworkZone(ip)
```

### Recommended Project Structure
```
src/main/kotlin/com/anjo/
├─ config/model/
│   ├─ ZonesConfig.kt          # NEW — replaces DisplayConfig zones block
│   ├─ ZoneConfig.kt           # NEW — per-zone: id, type, numDevices, bus, chipSelect
│   └─ ApplicationConfig.kt    # MODIFY — replace display: DisplayConfig with zones: ZonesConfig
├─ driver/
│   └─ (unchanged — Max7219Matrix, LcdDisplay, OledDisplay, OfflineDisplayDriver)
├─ zone/
│   ├─ ZoneDriver.kt           # NEW — interface: send(text, effect): Boolean; status(): ZoneStatus
│   ├─ LocalZoneDriver.kt      # NEW — wraps DisplayDriver
│   └─ NetworkZoneDriver.kt    # NEW — wraps Ktor WS client, reconnect loop
├─ service/
│   ├─ ZoneRegistry.kt         # NEW — Map<String,ZoneDriver>; route(); listAll(); addNetworkZone()
│   ├─ NetworkDiscoveryService.kt  # NEW — UDP scan + JmDNS listener
│   ├─ ScreenDriverService.kt  # MODIFY — inject ZoneRegistry; add zoneId param
│   └─ DisplaySelectionService.kt  # SUPERSEDED — replaced by ZoneRegistry
├─ db/
│   ├─ NetworkZonesTable.kt    # NEW — Exposed Table object
│   └─ ZoneRepository.kt       # NEW — insert/findAll/upsert/delete
├─ model/
│   └─ ZoneStatus.kt           # NEW — data class: id, type, status, ip?, lastSeenAt?
├─ routing/
│   ├─ TextRoutes.kt           # MODIFY — parse ?zone= param
│   └─ ZoneRoutes.kt           # NEW — GET /api/v1/zones; POST /api/v1/zones/discover; POST /api/v1/zones/{ip}
└─ di/
    └─ DependencyInjection.kt   # MODIFY — provide ZoneRegistry, NetworkDiscoveryService, ZoneRepository
```

### Pattern 1: ZoneDriver Interface
**What:** Uniform abstraction over local hardware and remote WebSocket connections
**When to use:** ZoneRegistry calls `driver.send(text, effect)` without knowing if it's local SPI or remote WS

```kotlin
// Source: derived from existing DisplayDriver interface
interface ZoneDriver {
    fun send(text: String, effect: Effect): Boolean
    fun status(): ZoneStatus
}
```

### Pattern 2: Pi4J Multi-SPI Init (per zone)
**What:** Each local MAX7219 zone gets a unique Pi4J SPI config with a distinct id() and chipSelect
**When to use:** Creating LocalZoneDriver for zones of type MAX7219

```kotlin
// Source: existing Max7219Matrix.kt + [ASSUMED] from Pi4J SPI docs
// Zone 0: CS_0 = /dev/spidev0.0
val config0 = Spi.newConfigBuilder(ctx)
    .id("max7219-zone-0")
    .name("MAX7219 SPI Zone 0")
    .bus(SpiBus.BUS_0)
    .chipSelect(SpiChipSelect.CS_0)
    .baud(1_000_000)
    .mode(SpiMode.MODE_0)
    .provider(LinuxFsSpiProviderImpl::class.java)
    .build()
// Zone 1: CS_1 = /dev/spidev0.1
val config1 = Spi.newConfigBuilder(ctx)
    .id("max7219-zone-1")   // must differ from zone-0
    .name("MAX7219 SPI Zone 1")
    .bus(SpiBus.BUS_0)
    .chipSelect(SpiChipSelect.CS_1)  // different CS
    .baud(1_000_000)
    .mode(SpiMode.MODE_0)
    .provider(LinuxFsSpiProviderImpl::class.java)
    .build()
```

### Pattern 3: NetworkZoneDriver — WebSocket connect loop
**What:** Long-lived WebSocket client connection with automatic reconnect
**When to use:** Every network zone — one NetworkZoneDriver per external device

```kotlin
// Source: [ASSUMED] from Ktor WS client docs + WebhookService scope pattern
class NetworkZoneDriver(
    private val ip: String,
    private val port: Int = 80,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val client: HttpClient,
) : ZoneDriver {
    @Volatile private var session: WebSocketSession? = null
    @Volatile private var online = false

    fun startConnect() {
        scope.launch {
            while (isActive) {
                try {
                    client.webSocket(host = ip, port = port, path = "/ws") {
                        session = this
                        online = true
                        incoming.consumeEach { /* receive-only; pings handled by plugin */ }
                    }
                } catch (e: Exception) {
                    log.warn("WS connection lost to $ip: ${e.message}")
                } finally {
                    online = false
                    session = null
                    delay(5_000)
                }
            }
        }
    }

    override fun send(text: String, effect: Effect): Boolean {
        val s = session ?: return false
        return runBlocking {
            try {
                s.send(Frame.Text("""{"text":"$text","effect":"${effect.name}"}"""))
                true
            } catch (e: Exception) { false }
        }
    }
}
```

### Pattern 4: UDP Broadcast Scan
**What:** Send broadcast, collect replies within timeout
**When to use:** `POST /api/v1/zones/discover` handler

```kotlin
// Source: [ASSUMED] from java.net standard API
suspend fun scanUdp(broadcastPort: Int = 54321, timeoutMs: Int = 3000): List<DiscoveredDevice> =
    withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredDevice>()
        DatagramSocket(0).use { socket ->
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            val payload = """{"type":"TEXTREADERRPI_DISCOVER"}""".toByteArray()
            val sendPacket = DatagramPacket(payload, payload.size,
                InetAddress.getByName("255.255.255.255"), broadcastPort)
            socket.send(sendPacket)
            val buf = ByteArray(1024)
            while (true) {
                try {
                    val recvPacket = DatagramPacket(buf, buf.size)
                    socket.receive(recvPacket)
                    val json = String(recvPacket.data, 0, recvPacket.length)
                    discovered += parseDiscoveryReply(json, recvPacket.address.hostAddress)
                } catch (_: java.net.SocketTimeoutException) { break }
            }
        }
        discovered
    }
```

### Pattern 5: JmDNS Passive Listener wired into Ktor lifecycle
**What:** Background mDNS listener that starts/stops with the application

```kotlin
// Source: [ASSUMED] from JmDNS 3.6.3 API + SchedulerService lifecycle pattern
class NetworkDiscoveryService(...) {
    private var jmdns: JmDNS? = null

    fun start() {
        jmdns = JmDNS.create(InetAddress.getLocalHost())
        jmdns?.addServiceListener("_textreaderrpi._tcp.local.", object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) { /* optional */ }
            override fun serviceRemoved(event: ServiceEvent) { /* mark offline */ }
            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                scope.launch { onDeviceDiscovered(info.hostAddresses[0], info.port, "MDNS") }
            }
        })
    }

    fun stop() {
        jmdns?.close()
        scope.coroutineContext[Job]?.cancel()
    }
}

// In DependencyInjection.kt:
monitor.subscribe(ApplicationStarted) { networkDiscoveryService.start() }
monitor.subscribe(ApplicationStopping) { networkDiscoveryService.stop() }
```

### Pattern 6: Broadcast — parallel coroutines, aggregate results
**What:** Fire send() to all zones in parallel; collect success/failure without blocking on slow zones

```kotlin
// Source: [ASSUMED] from D-18 decision + coroutines.async pattern
suspend fun broadcast(text: String, effect: Effect): BroadcastResult {
    val results = zones.map { (id, driver) ->
        id to scope.async { runCatching { driver.send(text, effect) }.getOrDefault(false) }
    }
    val resolved = results.map { (id, deferred) ->
        id to deferred.await()
    }
    return BroadcastResult(
        successful = resolved.filter { it.second }.map { it.first },
        failed = resolved.filter { !it.second }.map { FailedZone(it.first, "OFFLINE") }
    )
}
```

### Anti-Patterns to Avoid
- **Sharing one SpiChipSelect across two zones:** Two zones with same `chipSelect` value on same bus will contend on the same /dev/spidev0.0 node — data corruption. Each zone must use a different CS.
- **Sharing Pi4J id() strings:** Two Pi4J registrations with identical `id()` → startup crash `DuplicateIdException`. Always `"max7219-zone-$zoneId"`.
- **Blocking the WS session coroutine to wait for send:** Use `scope.launch { session.send() }` or a non-blocking queue; a blocking send that hangs blocks the receive loop and heartbeat.
- **Routing zone=null to first zone only:** No-zone param must broadcast to all zones (D-02/D-14), not default to zone "main".
- **Storing zone online/offline as a DB column polled per request:** Zone status is in-memory (last render outcome per D-05). DB persists identity, not status.
- **Creating a new HttpClient per NetworkZoneDriver:** Reuse a single shared HttpClient with WebSockets plugin installed — one client handles all outbound WS connections.
- **Running JmDNS.create() on Dispatchers.Default:** JmDNS.create() with getLocalHost() blocks on network I/O. Must run on Dispatchers.IO.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket client with ping/pong | Custom TCP socket + ping scheduler | `ktor-client-websockets` with `pingIntervalMillis` | Ktor WS plugin handles ping frame scheduling, frame parsing, close handshake |
| mDNS multicast listener | Raw MulticastSocket on port 5353 | `org.jmdns:jmdns` | mDNS protocol has subtleties (query/response timing, unicast fallback) that JmDNS handles correctly |
| Per-zone concurrency control | Global display mutex shared across zones | Per-zone Mutex keyed by zoneId | Zones are independent hardware; a locked zone-A should never block zone-B rendering |
| Zone persistence | In-memory map only | Exposed `NetworkZonesTable` + `ZoneRepository` | Zones must survive Pi restart; in-memory is lost on shutdown |

**Key insight:** The WebSocket protocol and mDNS protocol both have edge cases that are disproportionately expensive to implement correctly. Using established JVM libraries for both leaves implementation effort for the routing architecture, which is the actual novelty in this phase.

---

## Common Pitfalls

### Pitfall 1: Pi4J SPI ID collision on startup
**What goes wrong:** Two `Max7219Matrix` instances created with the same `id("max7219-zone-0")` — application crashes with `com.pi4j.exception.ProviderException: Duplicate Id`.
**Why it happens:** Pi4J context enforces globally unique IDs for all registered I/O providers. The current `Max7219Matrix` uses `"max7219-zone-$zoneId"` — but only if `zoneId` differs per zone. If both are initialized with `zoneId=0` the IDs collide.
**How to avoid:** ZoneConfig must include a numeric or string `id` field; `Max7219Matrix` must receive this as `zoneId: Int`. For zone 0 → `zoneId=0` (CS_0); for zone 1 → `zoneId=1` (CS_1).
**Warning signs:** Startup log `DuplicateIdException` or `ProviderException` mentioning "max7219-zone-".

### Pitfall 2: SPI chip select contention — two zones on same CS
**What goes wrong:** Zone 0 and zone 1 both configure `SpiChipSelect.CS_0`. SPI writes from zone 0 and zone 1 interleave on /dev/spidev0.0 — display shows corrupted data or only the last writer's content.
**Why it happens:** LinuxFsSpi uses the `/dev/spidevX.Y` device node determined by (bus, chipSelect). Same CS = same file descriptor path → the OS serializes writes but both zones share the chip-select line.
**How to avoid:** Zone 0 = CS_0, Zone 1 = CS_1. Raspberry Pi SPI0 exposes two CS lines out-of-the-box (/dev/spidev0.0 and /dev/spidev0.1). If a third MAX7219 zone is needed, GPIO-as-CS must be considered (out of scope for this phase).
**Warning signs:** Display with seemingly random data when two zones send simultaneously; only one zone ever showing content.

### Pitfall 3: ScreenDriverService displayImmediate — single driver assumption
**What goes wrong:** `ScreenDriverService` currently holds `private var driver: DisplayDriver` (singular). Adding a `zoneId` param to `displayImmediate()` without rerouting the internal `driver` reference causes all zones to render on whatever `driver` is currently set.
**Why it happens:** The refactor must change `driver` from a field to a lookup via `ZoneRegistry`. The `displaySelectionService` (runtime driver switching) will be superseded.
**How to avoid:** `ScreenDriverService` constructor receives `ZoneRegistry` instead of `DisplayDriver`. `displayImmediate()` calls `registry.route(zoneId, text, effect)`. The per-zone mutex map replaces the single `displayMutex`.
**Warning signs:** Test passes with a single zone but second zone never receives data.

### Pitfall 4: Broadcast response — one failed zone blocks all
**What goes wrong:** Naive `zones.map { it.send(...) }.all { it }` — if zone "kitchen" is OFFLINE and throws, the whole broadcast fails and "main" response is lost.
**Why it happens:** Sequential map with early failure.
**How to avoid:** Use `scope.async { runCatching { driver.send(...) } }` per zone; `awaitAll()` on the deferred list. Each zone's failure is captured individually. Return `BroadcastResult` shape from D-18.
**Warning signs:** `POST /api/v1/text` returns 503 when any one zone is offline, even though other zones are online.

### Pitfall 5: JmDNS blocks on getLocalHost() on slow Pi network
**What goes wrong:** `JmDNS.create(InetAddress.getLocalHost())` takes 5+ seconds if hostname resolution is slow on a Pi, hanging the `ApplicationStarted` callback on the main thread.
**Why it happens:** Hostname resolution hits DNS; slow on Pi if hostname isn't in `/etc/hosts`.
**How to avoid:** Run `JmDNS.create()` inside `scope.launch(Dispatchers.IO) {}` inside `networkDiscoveryService.start()`. The lifecycle hook returns immediately; JmDNS initialization happens off the main thread.
**Warning signs:** Application appears to hang during startup with no error log; eventually times out.

### Pitfall 6: Missing Flyway migration — `network_zones` table
**What goes wrong:** `ZoneRepository.findAll()` throws SQL exception on startup because the table doesn't exist.
**Why it happens:** `DatabaseFactory.init()` runs Flyway migrations. The new `network_zones` table must be created in a migration file, not just in `SchemaUtils.createMissingTablesAndColumns()`.
**How to avoid:** Create `V5__add_network_zones_table.sql`. Also add `NetworkZonesTable` to `SchemaUtils.createMissingTablesAndColumns()` call in `DatabaseFactory` for type-safety, but Flyway is the authoritative source.
**Warning signs:** `Table "NETWORK_ZONES" not found` in startup logs; app crashes before lifecycle hooks fire.

### Pitfall 7: WebSocket send() after session close
**What goes wrong:** `NetworkZoneDriver.send()` retrieves `session` reference, then the WS closes between the null check and the `session.send()` call — `ClosedSendChannelException`.
**Why it happens:** Concurrent session close from heartbeat timeout and incoming send call.
**How to avoid:** Wrap `session.send()` in `try/catch(Exception)` returning `false` on any exception. The connect loop will re-establish the connection on next cycle. Mark zone OFFLINE on send failure.
**Warning signs:** `ClosedSendChannelException` in logs accompanied by 503 responses for an online zone.

### Pitfall 8: HistoryRepository zoneId not plumbed through broadcast path
**What goes wrong:** Broadcast text shows `zoneId=null` in history for all zones instead of the specific zone that rendered it.
**Why it happens:** `displayImmediate(text, effect, policy, zoneId)` passes `zoneId` to history, but broadcast sends `zoneId="broadcast"` to all zones instead of the individual zone IDs.
**How to avoid:** When `zoneId` is `null`/`"broadcast"`, each parallel dispatch passes its own zone ID to `tryInsertHistory()`. Broadcast creates one history record per zone.
**Warning signs:** History page shows `zone=null` for all broadcast texts.

---

## Code Examples

### NetworkZonesTable (Exposed pattern — follow SchedulesTable exactly)
```kotlin
// Source: derived from existing SchedulesTable.kt
object NetworkZonesTable : Table("network_zones") {
    val id           = varchar("id", 64)
    val name         = varchar("name", 64)
    val ip           = varchar("ip", 64)
    val type         = varchar("type", 16).default("MAX7219")
    val discoveryMethod = varchar("discovery_method", 8)
    val createdAt    = varchar("created_at", 32)
    val lastSeenAt   = varchar("last_seen_at", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}
```

### V5 Flyway migration
```sql
-- V5__add_network_zones_table.sql
CREATE TABLE IF NOT EXISTS network_zones (
    id               VARCHAR(64)  NOT NULL,
    name             VARCHAR(64)  NOT NULL,
    ip               VARCHAR(64)  NOT NULL,
    type             VARCHAR(16)  NOT NULL DEFAULT 'MAX7219',
    discovery_method VARCHAR(8)   NOT NULL,
    created_at       VARCHAR(32)  NOT NULL,
    last_seen_at     VARCHAR(32),
    PRIMARY KEY (id)
);
```

### ZoneRoutes registration (follow Routing.kt pattern)
```kotlin
// Source: derived from existing Routing.kt
fun Application.configureRouting() {
    // ...existing...
    routing {
        route("/api/v1") {
            // ...existing routes...
            zoneRoutes(zoneRegistry, networkDiscoveryService)
        }
    }
}

fun Route.zoneRoutes(
    zoneRegistry: ZoneRegistry,
    discoveryService: NetworkDiscoveryService,
) {
    get("/zones") { /* return zoneRegistry.listAll() as JSON */ }
    post("/zones/discover") { /* discoveryService.scanUdp(); return new zones */ }
    post("/zones/{ip}") { /* manual add; validate IP; zoneRegistry.addNetworkZone(ip) */ }
}
```

### TextRoutes — adding ?zone= param (minimal change)
```kotlin
// Source: existing TextRoutes.kt + zoneId param
fun Route.textRoutes(screenDriverService: ScreenDriverService) {
    post("/text") {
        val request = call.receive<TextRequest>()
        val zoneId = call.request.queryParameters["zone"]
        val accepted = screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy, zoneId)
        // ...existing response logic...
    }
}
```

### DependencyInjection.kt additions (follow existing pattern)
```kotlin
// Source: existing DependencyInjection.kt
fun Application.configureDI() {
    // ... existing setup ...
    val zoneRepository = ZoneRepository()
    val zoneRegistry = ZoneRegistry(
        zonesConfig = appConfig.zones,
        pi4jContext = pi4jContext,
        zoneRepository = zoneRepository,
        httpClient = sharedHttpClient,
    )
    val networkDiscoveryService = NetworkDiscoveryService(zoneRegistry, zoneRepository)

    monitor.subscribe(ApplicationStarted) {
        schedulerService.start()
        networkDiscoveryService.start()
    }
    monitor.subscribe(ApplicationStopping) {
        schedulerService.stop()
        webhookService.stop()
        screenDriverService.stop()
        networkDiscoveryService.stop()
    }

    dependencies {
        // ...existing...
        provide { zoneRegistry }
        provide { zoneRepository }
        provide { networkDiscoveryService }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `DisplayDriver` in ScreenDriverService | `ZoneRegistry` with `Map<String, ZoneDriver>` | Phase 11 | All routing is zone-aware; backward-compat via default "broadcast" |
| `DisplaySelectionService` (runtime type switching) | Superseded by `ZoneRegistry` | Phase 11 | DisplaySelectionService can be removed or retired |
| Single `display:` YAML block | `display.zones:` list | Phase 11 | Config migration; old single-zone config path must still work |

**Deprecated/outdated:**
- `DisplaySelectionService`: Runtime driver switching by type was v1.0 design. ZoneRegistry supersedes it. Can be removed once ZoneRegistry is wired. The DI smoke test in ApplicationTest.kt must be updated to assert `ZoneRegistry` instead of `DisplaySelectionService`.
- `appConfig.display` (single `DisplayConfig`): Replaced by `appConfig.zones` (`ZonesConfig`). `ConfigLoader.loadConfig()` must be updated. `DisplayConfig` class can be archived or removed.

---

## Runtime State Inventory

> Phase 11 adds new DB tables and config structure but does not rename existing entities — this is a greenfield addition on top of existing data.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | Existing `schedules` and `display_history` tables already have `zone_id` columns | No migration needed — columns exist |
| Stored data | New `network_zones` table does not exist yet | V5 Flyway migration required |
| Live service config | No external services store zone configuration | None |
| OS-registered state | systemd service / install script — no zone-specific strings | None |
| Secrets/env vars | No existing env vars for zones | New: `DISCOVERY_PORT:54321` optional |
| Build artifacts | No stale build artifacts from zone-related rename | None |

**Nothing found requiring data migration** — zone columns already exist in history/schedules. The only new persistent state is `network_zones` (created fresh by V5 migration).

---

## Open Questions

1. **Pi4J SpiBus.BUS_0 + CS_1 on the Dev Machine**
   - What we know: `/dev/spidev0.1` exists on a Pi with `dtoverlay=spi0-2cs` or when SPI0 is enabled with 2 CS lines. The Pi4J `LinuxFsSpiProviderImpl` maps directly to this device node.
   - What's unclear: Whether the test Pi has CS_1 enabled in `/boot/config.txt`. If not, zone-1 init will fail and register as OFFLINE (which is acceptable per D-04).
   - Recommendation: Add a setup note in the plan — user must verify `dtoverlay=spi0-2cs` or `dtparam=spi=on` with cs1 enabled if two MAX7219 zones are needed simultaneously. The OFFLINE fallback means this does not block startup.

2. **Shared HttpClient for WebSocket connections**
   - What we know: Existing `WebhookService` creates its own `HttpClient(CIO) { install(ContentNegotiation) }`. WebSocket requires `install(WebSockets) { pingIntervalMillis = 15_000 }`.
   - What's unclear: Whether ContentNegotiation and WebSockets plugins can coexist in one HttpClient (they can per Ktor docs), or whether separate clients are needed.
   - Recommendation: Create a single shared `HttpClient(CIO) { install(WebSockets) { pingIntervalMillis = 15_000 } }` in DI for zone connections. `WebhookService` keeps its own client (ContentNegotiation). Two clients is fine — they serve different purposes.

3. **ScreenDriverService per-zone mutex design**
   - What we know: Current service has a single `displayMutex: Mutex`. D-17 requires mutex per zone.
   - What's unclear: Whether to use `ConcurrentHashMap<String, Mutex>` inside ScreenDriverService or push mutex ownership into each ZoneDriver.
   - Recommendation: `ConcurrentHashMap<String, Mutex>` in ScreenDriverService (lazy init on first use per zone). This keeps the existing rendering-pipeline logic centralized and avoids spreading mutex concern into ZoneDriver.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java JDK | Build/run | ✓ | OpenJDK 21.0.11 | — |
| Gradle | Build | ✓ | 8.1.1 | — |
| Pi4J 4.0.0 | Local zone hardware init | ✓ (in project) | 4.0.0 | OfflineDisplayDriver (D-04) |
| `/dev/spidev0.0` | Zone-0 MAX7219 | Unknown on dev machine | — | OfflineDisplayDriver fallback |
| `/dev/spidev0.1` | Zone-1 MAX7219 | Unknown — needs `dtoverlay=spi0-2cs` | — | OfflineDisplayDriver fallback |
| mDNS/multicast on Pi LAN | ZONE-02, ZONE-03 | Unknown | — | UDP fallback (D-19); manual add (D-12) |
| H2 database | DB persistence | ✓ (embedded, in project) | 2.4.240 | — |

**Missing dependencies with no fallback:** None — Pi4J hardware absence is handled by OfflineDisplayDriver (D-04); network unavailability is handled by 503 responses.

**Missing dependencies with fallback:**
- SPI CS_1: Falls back to OFFLINE zone if dtoverlay not configured
- mDNS multicast: UDP discovery and manual IP add remain functional even if mDNS is blocked by router

**Note:** JDK on dev machine is 21.0.11, but project specifies `jvmToolchain(25)`. Gradle will download JDK 25 via toolchain resolution if not present. Build will fail if no internet access and JDK 25 not cached locally.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (FunSpec, `should` convention) |
| Config file | None — JUnit5 platform via `useJUnitPlatform()` in build.gradle.kts |
| Quick run command | `./gradlew test --tests "com.anjo.zone.*" --tests "com.anjo.service.ZoneRegistry*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ZONE-01 | ZoneRegistry initializes 2 local zones from config; zone with failed init is OFFLINE | unit | `./gradlew test --tests "com.anjo.service.ZoneRegistryTest"` | ❌ Wave 0 |
| ZONE-01 | Broadcast (no zone param) reaches all local zones | unit | `./gradlew test --tests "com.anjo.service.ZoneRegistryTest"` | ❌ Wave 0 |
| ZONE-02/03 | UDP scan collects device replies within 3s timeout | unit (mock socket) | `./gradlew test --tests "com.anjo.service.NetworkDiscoveryServiceTest"` | ❌ Wave 0 |
| ZONE-03 | mDNS serviceResolved → device persisted to ZoneRepository | unit (mock JmDNS callback) | `./gradlew test --tests "com.anjo.service.NetworkDiscoveryServiceTest"` | ❌ Wave 0 |
| ZONE-04 | NetworkZoneDriver.send() serializes text+effect to WS frame | unit (MockEngine/MockWS) | `./gradlew test --tests "com.anjo.zone.NetworkZoneDriverTest"` | ❌ Wave 0 |
| ZONE-05 | Zone goes OFFLINE after WS session closes | unit | `./gradlew test --tests "com.anjo.zone.NetworkZoneDriverTest"` | ❌ Wave 0 |
| ZONE-06 | POST /api/v1/zones/{ip} persists manual zone; ZoneRegistry gets new entry | integration | `./gradlew test --tests "com.anjo.routing.ZoneRoutesTest"` | ❌ Wave 0 |
| ZONE-07 | POST /api/v1/text?zone=X routes to correct ZoneDriver | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest"` | ✅ (modify existing) |
| ZONE-07 | POST /api/v1/text?zone=OFFLINE returns 503 | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest"` | ✅ (modify existing) |
| ZONE-08 | GET /api/v1/zones returns all zones with status | integration | `./gradlew test --tests "com.anjo.routing.ZoneRoutesTest"` | ❌ Wave 0 |
| DI smoke | ZoneRegistry, ZoneRepository, NetworkDiscoveryService resolve in DI | smoke | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ (modify existing) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.anjo.zone.*" --tests "com.anjo.service.ZoneRegistry*" --tests "com.anjo.service.NetworkDiscovery*" -x jacocoTestCoverageVerification`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green (`./gradlew test`) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/anjo/service/ZoneRegistryTest.kt` — ZONE-01, broadcast routing
- [ ] `src/test/kotlin/com/anjo/service/NetworkDiscoveryServiceTest.kt` — ZONE-02, ZONE-03
- [ ] `src/test/kotlin/com/anjo/zone/NetworkZoneDriverTest.kt` — ZONE-04, ZONE-05
- [ ] `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` — ZONE-06, ZONE-08
- [ ] `src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt` — ZoneRepository CRUD (follow ScheduleRepositoryTest pattern)

*Existing `TextApiRouteTest.kt` and `ApplicationTest.kt` need modification — not creation.*

---

## Security Domain

> `security_enforcement` is absent in config — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Home network only; trusted LAN per REQUIREMENTS.md Out of Scope |
| V3 Session Management | no | No user sessions |
| V4 Access Control | no | Trusted home network |
| V5 Input Validation | yes | Zone ID from ?zone= param: validate non-empty, max 64 chars, alphanumeric+dash; IP address from POST body: validate IPv4 format before WebSocket connect |
| V6 Cryptography | no | WebSocket connections are ws:// (plain), not wss:// — external devices on same LAN |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF via POST /api/v1/zones/{ip} | Tampering | Validate IP is RFC1918 private range (192.168.x.x, 10.x.x.x, 172.16-31.x.x) before connecting; reject public IPs |
| UDP reply spoofing | Spoofing | Discovery is best-effort; a spoofed reply creates a zone that fails WS connect → falls to OFFLINE; no sensitive data exposed |
| Zone ID injection in SQL | Tampering | Exposed parameterized queries — no raw SQL; zoneId stored via Exposed DSL |

**Note:** The SSRF risk on `POST /api/v1/zones/{ip}` is the only meaningful new attack surface. Validation that the IP is a private RFC1918 address should be in `ZoneRoutes` (route handler), not in `ZoneRepository` — consistent with existing project rule that validation stays in route handlers.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `org.jmdns:jmdns:3.6.3` is available on Maven Central | Standard Stack | Build fails; use 3.5.12 or 3.5.8 instead — same API |
| A2 | `io.ktor:ktor-client-websockets:3.5.0` exists at same version as rest of Ktor | Standard Stack | Version mismatch compilation error; Ktor BOM ensures alignment |
| A3 | Two `SpiChipSelect` values (CS_0, CS_1) can be used on SpiBus.BUS_0 with LinuxFsSpiProviderImpl | Architecture / Pitfalls | Single-bus multi-CS might require kernel dtoverlay config; if not enabled, zone-1 init fails and falls to OFFLINE (acceptable) |
| A4 | JmDNS `ServiceListener.serviceResolved()` is called on a JmDNS internal thread (not a coroutine context) | Code Examples | If called on main thread, a blocking operation inside would deadlock; using `scope.launch` inside the callback is safe regardless |
| A5 | Ktor `WebSockets` plugin and `ContentNegotiation` can coexist in one `HttpClient` | Architecture | If not, two separate clients needed — low risk |
| A6 | `WebSocketSession.send(Frame.Text(...))` is safe to call from a coroutine different from the session's coroutine | Code Examples | If not thread-safe, need `Channel<Frame>` queue inside NetworkZoneDriver |

---

## Sources

### Primary (HIGH confidence — codebase, first-hand)
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — Pi4J SPI builder pattern with `id()`, `name()`, `chipSelect()` verified in project
- `src/main/kotlin/com/anjo/service/WebhookService.kt` — `CoroutineScope(Dispatchers.IO + SupervisorJob())` pattern for background I/O
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — `ApplicationStarted`/`ApplicationStopping` lifecycle subscription pattern
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` and `ScheduleRepository.kt` — Exposed `Table` + repository pattern for `ZoneRepository`
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` — Flyway + Hikari pattern; V5 migration follows same convention
- `src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt` — `${VAR:default}` config loading pattern
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — DI wiring pattern; `provide<>{}` conventions

### Secondary (MEDIUM confidence — official docs pages fetched)
- [Pi4J SPI documentation](https://www.pi4j.com/documentation/io-types/spi/) — confirms two CS lines on SPI0 via `/dev/spidev0.0` and `/dev/spidev0.1`
- [Ktor WebSocket client docs](https://ktor.io/docs/client-websockets.html) — `install(WebSockets)`, `pingIntervalMillis`, `webSocket {}`, `send()`, `incoming.consumeEach {}`
- [Pi4J LED Matrix example](https://www.pi4j.com/examples/jbang/jbang_pi4j_spi_led_matrix/) — confirms `id()`, `name()`, `bus()`, `chipSelect()` in `Spi.newConfigBuilder()`
- [JmDNS releases](https://github.com/jmdns/jmdns/releases) — 3.6.3 is latest stable (December 2023)

### Tertiary (LOW confidence — web search, training knowledge)
- [mvnrepository.com/artifact/io.ktor/ktor-client-websockets](https://mvnrepository.com/artifact/io.ktor/ktor-client-websockets) — version 3.5.0 exists
- [github.com/Pi4J/pi4j/pull/461](https://github.com/Pi4J/pi4j/pull/461) — SpiChipSelect values 0–10 supported
- UDP broadcast pattern — standard `java.net.DatagramSocket` API; no library needed

---

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — Ktor WS version confirmed via search; jmdns version from GitHub releases; both [ASSUMED] for Maven Central availability until first build
- Architecture: HIGH — derived directly from existing codebase patterns; ZoneDriver/ZoneRegistry design follows established WebhookService + SchedulerService patterns exactly
- Pi4J multi-SPI: MEDIUM — docs confirm two CS lines on SPI0; `id()` uniqueness requirement confirmed from existing Max7219Matrix code
- Pitfalls: HIGH — SPI ID collision documented in STATE.md; other pitfalls derived from first-principles analysis of the code being modified
- Network patterns (WS, UDP, mDNS): LOW — web search only; patterns are standard JVM/Ktor but not verified against running code

**Research date:** 2026-06-16
**Valid until:** 2026-07-16 (30 days — stable Ktor/jmdns versions; Pi4J API stable)
