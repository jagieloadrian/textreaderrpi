---
phase: 16-zone-management
reviewed: 2026-06-23T00:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - gradle/ktor-libs.versions.toml
  - src/main/kotlin/com/anjo/db/NetworkZonesTable.kt
  - src/main/kotlin/com/anjo/db/ZoneRepository.kt
  - src/main/kotlin/com/anjo/di/HTTP.kt
  - src/main/kotlin/com/anjo/model/AddZoneRequest.kt
  - src/main/kotlin/com/anjo/model/DisplayType.kt
  - src/main/kotlin/com/anjo/model/FirmwareMessage.kt
  - src/main/kotlin/com/anjo/model/NetworkZone.kt
  - src/main/kotlin/com/anjo/routing/FirmwareZoneRoutes.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/routing/ui/ZonesUIRoutes.kt
  - src/main/kotlin/com/anjo/routing/ZoneRoutes.kt
  - src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt
  - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
  - src/main/kotlin/com/anjo/validation/RequestValidationConfig.kt
  - src/main/kotlin/com/anjo/validation/RequestValidators.kt
  - src/main/kotlin/com/anjo/validation/ZoneValidators.kt
  - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
  - src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt
  - src/main/resources/db/migration/V6__make_ip_nullable_add_display_subtype.sql
  - src/main/resources/static/app.js
  - src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt
  - src/test/kotlin/com/anjo/model/DisplayTypeTest.kt
  - src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt
  - src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt
  - src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt
findings:
  critical: 4
  warning: 5
  info: 3
  total: 12
status: issues_found
---

# Phase 16: Code Review Report

**Reviewed:** 2026-06-23T00:00:00Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Phase 16 introduces zone management: a `NetworkZonesTable`/`ZoneRepository` persistence layer, `ZoneRegistry` in-memory coordination, `FirmwareZoneDriver` for WebSocket-connected firmware devices, REST and UI routes, a validator, and UDP/mDNS discovery. The overall structure is clean and the validation logic is well-separated. However four blockers were found that can each produce silent incorrect behaviour or an exploitable surface in production.

---

## Critical Issues

### CR-01: Null-pointer crash when adding a non-FIRMWARE network zone with null IP

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:73`

The route handler for `POST /api/v1/zones` forces `req.ip!!` at line 73 without a null guard, relying entirely on the request validator having already rejected the request. Ktor's `RequestValidation` plugin produces a 422 response, but the `!!` operator executes inside the same coroutine scope as validation — if the validator is ever bypassed (e.g. the type string is not one of the known enum values, or a future refactor removes the validation step), the `NullPointerException` is unhandled and propagates as a 500 with no helpful message. The validator currently blocks `UNKNOWN` and all local hardware types, but a caller sending `{"name":"x","type":"CUSTOM_TYPE","ip":null}` bypasses every branch in `ZoneValidators.validateAddZone` (line 27: `if (req.type != DisplayType.FIRMWARE.name)` — `CUSTOM_TYPE` is neither FIRMWARE nor a local hardware type so validation reaches line 28 where `ip.isNullOrBlank()` correctly fires, but if another unknown type is added to the enum later this guard becomes the only line standing between the route and the crash). More immediately: the `!!` dereference is unnecessary and dangerous.

**Fix:**
```kotlin
val ip = req.ip
    ?: return@post call.respond(HttpStatusCode.BadRequest, "ip is required for this zone type")
```

---

### CR-02: Zone name used as zone ID — no length or character sanitisation before DB/registry insert

**File:** `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt:59,80`

The `id` and `name` fields of the persisted `NetworkZone` are set directly to `req.name` (the unsanitised user-supplied string) without any length or character validation beyond `isBlank()`. The `NetworkZonesTable.id` column is `varchar(64)`, so a name longer than 64 characters will be silently truncated by H2 or throw at the DB layer. More critically, the same string is used as the WebSocket path segment (`/ws/zone/{id}`) and as a DOM element ID (`deleteResult-${zone.id}` in `ZonesPage.kt:154`) and interpolated verbatim into HTML attributes. A name like `"><script>alert(1)</script>` would be persisted, returned via `GET /api/v1/zones` as JSON (safe), but also rendered as server-side HTML in `ZonesPage.kt` inside `strong { +zone.id }` — kotlinx.html's `+` operator does HTML-escape text content, so that specific render site is safe. However the same id is inserted into a `data-zone-id` attribute at line 148:

```kotlin
attributes["data-zone-id"] = zone.id
```

`attributes[key] = value` in kotlinx.html does NOT HTML-escape the attribute value in all versions. A zone id containing `"` would break out of the attribute and could allow DOM manipulation. The `deleteResult-${zone.id}` suffix used in `getElementById` (JS line 376) is also not escaped, though that is a JS string not an HTML injection vector at that point.

**Fix:** Add a name pattern check in `ZoneValidators.validateAddZone` and enforce max length:
```kotlin
private val ZONE_NAME_REGEX = Regex("^[a-zA-Z0-9._-]{1,64}$")

if (!ZONE_NAME_REGEX.matches(req.name)) {
    return ValidationResult.Invalid(
        "name must contain only alphanumeric characters, dots, hyphens, and underscores (max 64 chars)"
    )
}
```

---

### CR-03: `anyHost()` CORS configuration permits all origins

**File:** `src/main/kotlin/com/anjo/di/HTTP.kt:19`

`anyHost()` allows any origin to make credentialed cross-origin requests including DELETE, PUT, and PATCH. This means a malicious webpage on the local network (e.g. served from any device the user browses to) can silently delete zones or send text to displays by having the user visit it, because the browser will include the same-origin session/cookies. For a Raspberry Pi that is reachable on the LAN and has no authentication, `anyHost()` effectively removes the browser's same-origin protection entirely.

**Fix:** Restrict to the expected local network origins or the device's own host. If dynamic host detection is needed at minimum scope it to known safe schemes:
```kotlin
install(CORS) {
    allowHost("localhost")
    allowHost("raspberrypi.local")
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowHeader(HttpHeaders.Authorization)
}
```
Or document explicitly that this is an intentional local-only device and add an `anyHost()` comment acknowledging the risk — but the current code has no such acknowledgement.

---

### CR-04: UDP discovery reply parsed with hand-rolled regex — name injection from network

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:143-148`

The `parseDiscoveryReply` function uses two `Regex` patterns to extract `name` and `type` fields from a UDP payload without any JSON parsing library. This makes it vulnerable to crafted packets:

1. A payload like `{"name":"../../../etc/passwd","type":"MAX7219"}` will set the zone name to a path-traversal string. After sanitisation (`replace(Regex("[^a-zA-Z0-9._-]"), "-")`) this becomes `----..--..--..-etc-passwd` — dots are preserved. Dots in zone IDs are explicitly allowed by the sanitisation regex, so e.g. `{"name":"192.168.1.1"}` (a valid IP-looking name) round-trips cleanly and overrides the real zone's record.

2. More critically, the `type` field from the network is passed directly to `buildNetworkZone` as the `type` parameter with no validation against `DisplayType` entries. An attacker on the LAN can send `{"type":"FIRMWARE"}` in a UDP packet, causing an unsolicited device to be registered as a FIRMWARE zone (which means a WebSocket slot is not opened but a conflicting `NetworkZone` with FIRMWARE type but a non-null IP is persisted). When the server later calls `addNetworkZone`, it calls `zone.ip ?: return` (line 142 of `ZoneRegistry.kt`) — so the ip-present FIRMWARE-typed zone actually gets a `NetworkZoneDriver` created for it, not a `FirmwareZoneDriver`. This is type confusion caused by trusting an unauthenticated network source.

**Fix:** Use a proper JSON parser for UDP replies (the project already depends on `ktor-serialization-kotlinx-json`) and validate `type` against `DisplayType.fromString(type)`, rejecting `FIRMWARE` and `UNKNOWN` from discovered devices:
```kotlin
private fun parseDiscoveryReply(json: String, senderIp: String): NetworkZone? {
    return try {
        val parsed = Json.decodeFromString<JsonObject>(json)
        val name = parsed["name"]?.jsonPrimitive?.contentOrNull ?: senderIp
        val rawType = parsed["type"]?.jsonPrimitive?.contentOrNull ?: DisplayType.MAX7219.name
        val safeType = when (DisplayType.fromString(rawType)) {
            DisplayType.FIRMWARE, DisplayType.UNKNOWN -> DisplayType.MAX7219.name
            else -> rawType
        }
        buildNetworkZone(ip = senderIp, method = "UDP", name = name, type = safeType)
    } catch (e: Exception) {
        log.warn("Failed to parse discovery reply: ${e.message}")
        null
    }
}
```

---

## Warnings

### WR-01: Duplicate `validateAddZoneRequest` in `RequestValidators` is dead code

**File:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt:25-41`

`RequestValidators.validateAddZoneRequest` performs an RFC1918 check for `AddZoneRequest`. It is never called — `RequestValidationConfig.kt` delegates `AddZoneRequest` validation to `ZoneValidators.validateAddZone`. The duplicate function has a subtly different behaviour: it returns `Invalid` when `req.ip` is null (line 26), which means it would always fail for FIRMWARE zones. This dead code will mislead future maintainers and could be mistakenly wired in instead of `ZoneValidators.validateAddZone`.

**Fix:** Delete `RequestValidators.validateAddZoneRequest` entirely.

---

### WR-02: `FirmwareZoneDriver.attach` races on double-attach — old session coroutine keeps running

**File:** `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt:21-31`

`attach` replaces `sessionRef` atomically but does not cancel the coroutine that is already draining `channel` for the old session. If a second firmware device connects to the same zone ID before the first disconnects (physically possible if the device reboots and reconnects quickly while the server still holds the old session):

1. `sessionRef.set(session)` updates the reference to the new session.
2. The old coroutine continues running, reading from the shared `channel`.
3. Messages enqueued for the new session may be consumed and sent to the old (now dead) session, while the new session receives nothing.

There is no `cancel()` call on the previous coroutine job, and `session.launch { ... }` is not tracked.

**Fix:** Track the drain job and cancel it on re-attach:
```kotlin
private val drainJob = AtomicReference<Job?>(null)

fun attach(session: DefaultWebSocketServerSession) {
    drainJob.getAndSet(null)?.cancel()
    sessionRef.set(session)
    val job = session.launch {
        try {
            for (msg in channel) { session.send(Frame.Text(msg)) }
        } catch (_: ClosedReceiveChannelException) { }
    }
    drainJob.set(job)
}
```

---

### WR-03: `ZoneRegistry` uses `lateinit var wsClient` which throws `UninitializedPropertyAccessException` when no-arg constructor is used in tests without setting the field

**File:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt:35,125-127`

`ZoneRegistry()` (the no-arg constructor) leaves `wsClient` uninitialized. `addNetworkZone(zone: NetworkZone)` (line 125) calls `addNetworkZone(zone, wsClient)`, which dereferences `wsClient`. In `FirmwareZoneDriverTest`, `ZoneRegistry()` is constructed via the no-arg constructor and `registerFirmwareZone` is called — that path does not touch `wsClient` so it is fine. But any test or production path that calls the no-arg constructor and then calls `addNetworkZone(zone)` (without the explicit client argument) will crash with `UninitializedPropertyAccessException`. This is a latent crash.

**Fix:** Make `wsClient` nullable (`private var wsClient: HttpClient? = null`) and guard the call:
```kotlin
fun addNetworkZone(zone: NetworkZone) {
    val client = wsClient ?: error("ZoneRegistry: wsClient not initialized")
    addNetworkZone(zone, client)
}
```
Or consolidate to a single constructor that takes all dependencies.

---

### WR-04: `ZoneRepository.upsert` is not atomic — TOCTOU race on concurrent upserts for the same ID

**File:** `src/main/kotlin/com/anjo/db/ZoneRepository.kt:25-51`

`upsert` manually checks for an existing row with a `selectAll().where()` then branches to `update` or `insert`. Both the check and the write happen in the same `suspendTransaction` block, which is correct for serializable transactions — but only if the database is configured at the SERIALIZABLE isolation level. Exposed's default is typically READ_COMMITTED, which means two concurrent `upsert` calls for the same zone ID can both see `existing == null` and both attempt `insert`, causing a primary key violation. Under mDNS discovery, `onDeviceDiscovered` is launched from a `ServiceListener` callback (line 102) in a coroutine scope, so concurrent calls for the same device (which JmDNS can fire multiple times) are realistic.

**Fix:** Use a database-level upsert (e.g. `INSERT ... ON CONFLICT DO UPDATE` via Exposed's `upsert` DSL if available, or a `MERGE` statement in migration) or add `UNIQUE` constraint enforcement in the transaction with proper conflict handling.

---

### WR-05: `NetworkDiscoveryService.receiveUdpReplies` calls `withContext(Dispatchers.IO)` while already on `Dispatchers.IO`

**File:** `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt:67-85`

`scanUdp` is a `suspend fun` that already switches to `Dispatchers.IO` via `withContext(Dispatchers.IO)` at line 47. Inside `receiveUdpReplies`, each individual `socket.receive(recvPacket)` call wraps itself in another `withContext(Dispatchers.IO)` (line 72). This is not incorrect but adds unnecessary coroutine context switching overhead in a tight loop, and more importantly it means the suspend point for cancellation is inside an extra context switch, which can delay cooperative cancellation. The `soTimeout` on the socket means the loop terminates correctly, but the pattern is misleading and fragile.

**Fix:** Remove the inner `withContext(Dispatchers.IO)` since the entire `receiveUdpReplies` call chain is already dispatched on IO:
```kotlin
val recvPacket = DatagramPacket(buf, buf.size)
socket.receive(recvPacket)  // already on Dispatchers.IO
```

---

## Info

### IN-01: `RequestValidators.validateAddZoneRequest` error message leaks implementation detail to clients

**File:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt:26`

The function is dead code (see WR-01), but if it were ever used, it returns `"IP must be a valid RFC1918 private address"` when IP is null — which is technically incorrect (a null IP is not "an invalid RFC1918 address", it is a missing field). This would confuse API consumers. Moot if the function is deleted per WR-01.

---

### IN-02: `ZonesPage.kt` zone type label for FIRMWARE zones renders redundantly as "Firmware (FIRMWARE)"

**File:** `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt:120-121`

The label computation reads:
```kotlin
zone.type == "FIRMWARE" -> "Firmware (${zone.type})"
```
This produces the string `"Firmware (FIRMWARE)"` — the parenthetical is a verbatim echo of the enum constant. This is a display bug, not a data bug.

**Fix:**
```kotlin
zone.type == "FIRMWARE" -> "Firmware"
```

---

### IN-03: `ZoneRoutesTest` duplicate-IP test does not assert the first registration succeeded

**File:** `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt:59-71`

The test "POST /api/v1/zones for a duplicate IP returns 409" sends the first registration request but ignores its response. If the first request silently fails (e.g. due to a DB connection error), the second request will return 201 instead of 409 and the test will fail with a misleading message. The first response should be asserted to be 201.

**Fix:**
```kotlin
val firstResponse = client.post("/api/v1/zones") { ... }
firstResponse.status.value shouldBe 201
val response = client.post("/api/v1/zones") { ... }
response.status shouldBe HttpStatusCode.Conflict
```

---

_Reviewed: 2026-06-23T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
