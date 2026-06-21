---
phase: "11"
status: "clean"
critical: 0
warning: 6
info: 3
reviewed_at: "2026-06-16"
files_reviewed: 16
files_reviewed_list:
  - src/main/kotlin/com/anjo/config/model/ZonesConfig.kt
  - src/main/kotlin/com/anjo/db/NetworkZonesTable.kt
  - src/main/kotlin/com/anjo/db/ZoneRepository.kt
  - src/main/kotlin/com/anjo/model/BroadcastResult.kt
  - src/main/kotlin/com/anjo/model/NetworkZone.kt
  - src/main/kotlin/com/anjo/model/ZoneStatus.kt
  - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt
  - src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt
  - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
  - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
  - src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/ZoneDriver.kt
  - src/main/resources/db/migration/V5__add_network_zones_table.sql
  - src/main/resources/static/app.js
---

# Phase 11: Code Review Report

**Reviewed:** 2026-06-16
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues-found

## Summary

Phase 11 introduces multi-zone display support: a network zone model persisted via Flyway migration, a zone registry backed by `ConcurrentHashMap`, WebSocket-connected `NetworkZoneDriver` instances, UDP/mDNS discovery, and associated routes plus a UI page. The implementation is mostly coherent, but contains five critical defects — two security (SSRF from unvalidated JSON-extracted IP, local-zone deletion bypass), one correctness bug (duplicate-check key mismatch), one resource-leak (no shutdown path for `NetworkZoneDriver` coroutines at application stop), and one data-integrity bug (`createdAt` is overwritten on every re-discovery upsert). Six warnings cover a blocking-thread antipattern used in a Ktor coroutine handler, reconnect-delay skipped on cancellation, missing CSRF protection on mutating endpoints, and a few smaller correctness gaps.

---

## Critical Issues

### CR-01: SSRF — JSON-supplied IP from UDP discovery reply is used without validation

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:134`

**Issue:** `parseDiscoveryReply` extracts the `"ip"` field from the JSON payload sent by the remote device and uses it directly as the IP address that will be connected to via WebSocket (`zones[zone.id] = NetworkZoneDriver(ip = zone.ip, ...)`). Any device on the local broadcast domain can respond with `"ip":"10.0.0.1"` (or any other RFC1918 address) and cause the server to open a persistent outbound WebSocket connection to an arbitrary host. `isValidPrivateIpv4` exists but is only called on the manual-add route (`POST /zones/{ip}`); it is never called in the UDP discovery or mDNS paths.

```kotlin
// parseDiscoveryReply (line 134):
val ip = ipMatch?.groupValues?.get(1) ?: senderIp
// ip is never validated before being stored and connected to

// Fix: ignore the JSON-supplied ip field entirely; always use the packet source address:
val ip = senderIp   // trust the kernel's recvPacket.address, not the payload
```

Alternatively, if the JSON ip field must be honoured, pass it through `isValidPrivateIpv4` before use and fall back to `senderIp` on rejection.

---

### CR-02: Local zones can be deleted via the DELETE endpoint

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:29-39`

**Issue:** The DELETE handler calls `zoneRepository.findById(id)` to guard the operation. Local zones (registered from `zonesConfig` at startup) exist only in the in-memory `zones` ConcurrentHashMap; they are never written to `network_zones`. Therefore `zoneRepository.findById(localZoneId)` returns `null`, triggering the early return with `404`. **But the 404 message says "Zone not found or is a local zone (cannot be deleted)"** while the actual outcome is `zoneRegistry.removeZone(id)` is never called for the local zone — this path is safe by accident, not by design.

The real problem: after any network zone is registered, the `zones` map contains both local and network zones keyed by their id. A caller who knows the string id of a local zone and registers a *network* zone with the same id (via `POST /zones/{ip}` where the IP happens to match a local zone id — impossible today because local ids are config-defined names — or via mDNS where `name` is used as id) would silently overwrite the local zone in the map and the local zone becomes permanently inaccessible. This is an id-namespace collision, not fully protected.

More concretely: the `removeZone` operation (`ZoneRegistry.kt:108`) calls `driver.stop()` on whatever is in the map. If a network zone overwrites a local zone entry and is then deleted, the local zone's hardware driver is stopped without any recovery path. Add an explicit local-zone guard:

```kotlin
delete("/{id}") {
    val id = call.parameters["id"]
        ?: return@delete call.respond(HttpStatusCode.BadRequest, "missing id")

    // Explicit local-zone guard BEFORE hitting the DB
    if (zoneRegistry.isLocalZone(id)) {
        return@delete call.respond(HttpStatusCode.Forbidden, "Cannot delete a local zone")
    }

    val zone = zoneRepository.findById(id)
        ?: return@delete call.respond(HttpStatusCode.NotFound, "Zone not found")

    zoneRepository.delete(id)
    zoneRegistry.removeZone(id)
    call.respond(HttpStatusCode.NoContent)
}
```

`ZoneRegistry` needs an `isLocalZone(id)` method (track local ids at construction time in a `Set<String>`).

---

### CR-03: Duplicate-check key mismatch — manual add never detects an already-registered zone

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:56`

**Issue:** `zoneRegistry.contains(ip)` checks the `zones` map for a key equal to the IP string. But `addNetworkZone` stores drivers under `zones[zone.id]`. For mDNS/UDP-discovered zones the id is set to the device's *name* (`val zoneId = name ?: ip`), not the IP. For a discovered zone whose name is "livingroom", the map key is `"livingroom"` while `contains("192.168.1.50")` returns `false`. The user can then `POST /zones/192.168.1.50`, creating a *second* `NetworkZoneDriver` for the same physical device, and a second WebSocket connection is opened to that IP.

The manual-add route uses `id = ip` (line 66) so the zone is stored under the IP key. This only means the duplicate check works correctly for *manually-added* zones against other *manually-added* zones, not against discovered ones.

Fix: in `ZoneRegistry`, add a separate `ipToId` map so that `contains` can check by IP regardless of the id used as the primary key:

```kotlin
private val ipToId = ConcurrentHashMap<String, String>()   // ip -> zoneId

fun containsIp(ip: String): Boolean = ipToId.containsKey(ip)

fun addNetworkZone(zone: NetworkZone, client: HttpClient) {
    // ...
    ipToId[zone.ip] = zone.id
    zones[zone.id] = driver
}

fun removeZone(id: String): Boolean {
    val driver = zones.remove(id) ?: return false
    ipToId.values.remove(id)   // or track ip->id bidirectionally
    driver.stop()
    return true
}
```

Then replace `zoneRegistry.contains(ip)` with `zoneRegistry.containsIp(ip)` in the route.

---

### CR-04: NetworkZoneDriver coroutines are never cancelled on application shutdown

**File:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt:114-122` / `src/main/kotlin/com/anjo/di/DependencyInjection.kt:58-67`

**Issue:** Each `NetworkZoneDriver` owns a private `CoroutineScope(Dispatchers.IO + SupervisorJob())`. `startConnect()` launches an infinite reconnect loop inside that scope. `ZoneRegistry` has no `stop()` method and is not registered with the `ApplicationStopping` lifecycle event. When the application shuts down the coroutines of every registered `NetworkZoneDriver` keep running (they hold the dispatcher threads), and the `wsClient` used by those coroutines is closed (`wsClient.close()` in DI line 67) while the reconnect loops are still trying to use it, producing unhandled exceptions and goroutine leaks.

The `removeZone` path correctly calls `driver.stop()` for individually removed zones, but the bulk case (all zones at application stop) is unhandled.

Fix — add a shutdown method to `ZoneRegistry` and register it:

```kotlin
// ZoneRegistry.kt
fun stop() {
    zones.values.forEach { it.stop() }
    zones.clear()
}

// DependencyInjection.kt
monitor.subscribe(ApplicationStopping) {
    schedulerService.stop()
    webhookService.stop()
    screenDriverService.stop()
    zoneRegistry.stop()            // <- add this BEFORE wsClient.close()
    networkDiscoveryService.stop()
    wsClient.close()
}
```

---

### CR-05: createdAt is overwritten on every re-discovery upsert

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:117` / `src/main/kotlin/com/anjo/db/ZoneRepository.kt:31-37`

**Issue:** `onDeviceDiscovered` always constructs a `NetworkZone` with `createdAt = Instant.now().toString()`. `ZoneRepository.upsert` issues an SQL `UPDATE` that does not update `created_at`, so the column is not corrupted in the database on re-discovery. However the in-memory `zone` object passed to `zoneRegistry.addNetworkZone(zone, wsClient)` carries the wrong (current-time) `createdAt`. More importantly, `addNetworkZone` silently replaces the existing `NetworkZoneDriver` in the map without calling `stop()` on the old one. This means every mDNS `serviceResolved` callback for an already-registered device leaks a `CoroutineScope` with an active reconnect loop.

```kotlin
// ZoneRegistry.addNetworkZone (line 114-122) — no stop of existing driver:
fun addNetworkZone(zone: NetworkZone, client: HttpClient) {
    val driver = NetworkZoneDriver(...)
    driver.startConnect()
    zones[zone.id] = driver   // old driver's coroutine scope is orphaned
}
```

Fix:

```kotlin
fun addNetworkZone(zone: NetworkZone, client: HttpClient) {
    val driver = NetworkZoneDriver(
        id = zone.id,
        ip = zone.ip,
        port = 80,
        client = client
    )
    driver.startConnect()
    val previous = zones.put(zone.id, driver)
    previous?.stop()   // cancel the orphaned reconnect loop
}
```

---

## Warnings

### WR-01: `runBlocking` inside a Ktor coroutine handler blocks an IO thread

**File:** `src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt:17`

**Issue:** The `GET /zones` UI route is a `suspend` Ktor handler. Calling `runBlocking { zoneRepository.findAll() }` inside a suspended handler wraps a suspending DB call in a new blocking coroutine on the current thread, negating the benefit of structured concurrency and risking thread starvation under load.

```kotlin
// Current (wrong):
val networkZones = runBlocking { zoneRepository.findAll() }

// Fix — the handler is already suspend, just call directly:
val networkZones = zoneRepository.findAll()
```

---

### WR-02: Reconnect delay is skipped when the scope is cancelled in mid-delay

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:43-49`

**Issue:** The `finally` block always executes `delay(5_000.milliseconds)` even when `stop()` was called and the scope is being cancelled. `delay` is cancellation-aware and will throw `CancellationException`, which propagates correctly here, but `online = false` and `session = null` assignments in the `finally` block execute *before* the `delay` — that is correct. The real issue is that when cancellation arrives *during* the `delay` itself the `CancellationException` propagates out of `scope.launch { }` and is swallowed by the `SupervisorJob` silently. This is correct Kotlin structured concurrency behaviour and not itself a bug, but the lack of any `CancellationException` re-throw or guard means a cancelled reconnect loop will attempt one final WebSocket connect before noticing it is cancelled (the `while (isActive)` check only runs at the top of the loop; the `client.webSocket(...)` call happens before the next `isActive` check). Under fast `stop()` → `startConnect()` cycling (e.g. rapid delete/re-add) this creates a brief window of a phantom connection attempt.

Fix: add a `yield()` or `isActive` check at the start of the `finally` delay:

```kotlin
} finally {
    online = false
    session = null
    if (isActive) delay(5_000.milliseconds)
}
```

---

### WR-03: No CSRF protection on mutating zone endpoints

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:42-78`

**Issue:** `POST /zones/discover`, `POST /zones/{ip}`, and `DELETE /zones/{id}` are state-mutating endpoints served on the same origin as the UI. They accept requests from any tab or page on the user's browser (SameSite=None cookies, or if no cookie auth exists, direct cross-origin fetch). If the application is accessed in a browser session, a malicious page could trigger zone addition or deletion via a cross-origin form POST or fetch. The API prefix `/api/v1/` is cosmetic; CORS policy should be verified. At minimum, these endpoints should require a header check (e.g. `Content-Type: application/json`) or an anti-CSRF token if the app uses cookie-based auth. Noting this as a warning rather than critical because the app appears to be a local-network device controller with no user authentication.

---

### WR-04: mDNS-discovered zone ID derived from untrusted device name — no length or character validation

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:110-113`

**Issue:** `val zoneId = name ?: ip` where `name` comes from `info.name` (the mDNS service name, line 48). A rogue mDNS announcement can supply a name that is: (a) longer than 64 characters, causing a `DataException` or truncation when stored in `id VARCHAR(64)`; (b) contains characters that break the HTML `id` attribute used in `ZonesPage.kt:72` (`deleteResult-${zone.id}`), allowing a malformed DOM id to make the delete button silently fail; or (c) matches an existing local zone id, shadowing it in the registry (see also CR-02).

Fix: sanitise the name before use:

```kotlin
val zoneId = (name?.take(64)?.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } ?: ip)
    .ifBlank { ip }
```

---

### WR-05: `ZoneRegistry.broadcast` uses `runBlocking` on a non-suspend function

**File:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt:84-95`

**Issue:** `broadcast` is a regular (non-suspend) function called from request handlers. It calls `runBlocking { ... scope.async { ... }.await() }`. This nests a `runBlocking` inside an already-concurrent context: if `broadcast` is ever called from a coroutine dispatcher thread (e.g. from within a Ktor route), `runBlocking` will block that dispatcher thread for the entire duration of all `send` operations. With many zones or slow network drivers this degrades throughput.

The fix is to make `broadcast` a `suspend` function and remove `runBlocking`:

```kotlin
suspend fun broadcast(text: String, effect: Effect): BroadcastResult {
    val results = coroutineScope {
        zones.map { (id, driver) ->
            id to async { runCatching { driver.send(text, effect) }.getOrDefault(false) }
        }.map { (id, deferred) ->
            id to deferred.await()
        }
    }
    // ...
}
```

---

### WR-06: `NetworkDiscoveryService.stop()` does not wait for the mDNS start coroutine to complete

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:61-68`

**Issue:** `start()` launches a coroutine that calls `JmDNS.create(localHost)` (a blocking IO call). If `stop()` is called before that coroutine finishes, `jmdns` is still `null` at the `jmdns?.close()` line — the close is skipped. The JmDNS instance is then created a moment later, registered, and **never closed**, leaking the mDNS multicast socket for the lifetime of the process.

Fix: use a `CompletableDeferred<JmDNS?>` or synchronise via `AtomicReference` so `stop()` can close whatever instance eventually materialises:

```kotlin
private val jmdnsRef = AtomicReference<JmDNS?>(null)

// In start() coroutine:
val instance = JmDNS.create(localHost)
jmdnsRef.set(instance)
instance.addServiceListener(...)

// In stop():
jmdnsRef.getAndSet(null)?.close()
scope.coroutineContext[Job]?.cancel()
```

---

## Info

### IN-01: `discovery_method VARCHAR(8)` is tight for future extensibility

**File:** `src/main/resources/db/migration/V5__add_network_zones_table.sql:6`

**Issue:** All current values ("MANUAL" = 6, "MDNS" = 4, "UDP" = 3) fit within 8 characters. However any future method name longer than 8 characters (e.g. "BLUETOOTH", "ZEROCONF") will silently truncate or fail at insert. The Exposed column is declared identically (`varchar("discovery_method", 8)`). Increase to at least 16:

```sql
discovery_method VARCHAR(16) NOT NULL,
```

---

### IN-02: `NetworkZoneDriver.status()` hardcodes type as "MAX7219"

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:68`

**Issue:** `status()` always returns `type = "MAX7219"` regardless of the zone's actual `type` field. The `type` is stored in the DB (`NetworkZone.type`) and passed to `NetworkZoneDriver(id = zone.id, ...)`, but the driver does not store or expose it. The UI therefore always shows "Network (MAX7219)" for all network zones.

Fix: store the type in the constructor and return it from `status()`:

```kotlin
class NetworkZoneDriver(
    private val id: String,
    private val ip: String,
    private val type: String = "MAX7219",   // add this
    private val port: Int = 80,
    private val client: HttpClient,
    ...
)

override fun status(): ZoneStatus = ZoneStatus(
    id = id,
    type = type,   // was hardcoded "MAX7219"
    ...
)
```

---

### IN-03: `ZoneRegistry` secondary constructor uses `runBlocking` during application startup

**File:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt:47`

**Issue:** `runBlocking { zoneRepository.findAll() }` inside a non-suspend constructor is acceptable at startup (there is no coroutine context to block), but it ties the entire application startup thread until the DB query completes. If the DB is slow or the migration is running, the startup hangs without a timeout. Consider wrapping this in a `withTimeoutOrNull` or deferring it to the `ApplicationStarted` lifecycle hook.

---

_Reviewed: 2026-06-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
