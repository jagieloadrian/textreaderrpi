# Phase 16: Zone Management - Research

**Researched:** 2026-06-23
**Domain:** Ktor server-side WebSocket, Kotlin coroutines (Channel / AtomicReference), ConcurrentHashMap atomics, kotlinx.serialization, Flyway schema migration
**Confidence:** HIGH (core patterns verified directly from codebase; Ktor WebSocket from official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- D-01: Zone `name` is the zone routing ID passed in `?zone=<name>`. Replaces IP-as-ID for manually added zones.
- D-02: New `DisplayType.FIRMWARE` enum value added.
- D-03: Firmware zones have no IP field (inbound). Network zones (outbound) require IP. Form adapts by type selection.
- D-04: Display subtype for firmware zones is a free-text field. Stored as-is in DB as metadata.
- D-05: 422 for `POST /zones` when `type` is MAX7219, LCD, OLED, UNKNOWN. Validation in new `ZoneValidators` object only — never inline in route handler.
- D-06: Use `ConcurrentHashMap.compute()` in `ZoneRegistry` to atomically check + insert.
- D-07: User pre-registers firmware zone via form first. Zone exists as OFFLINE before any device connects.
- D-08: `GET /ws/zone/{id}` looks up zone in ZoneRegistry; if found and FIRMWARE type — accepts WS, creates `FirmwareZoneDriver`, zone goes ONLINE. If not found or wrong type — 404.
- D-09: On disconnect, zone transitions to OFFLINE. Zone entry remains in registry (OFFLINE). Subsequent sends return graceful error.
- D-10: `FirmwareZoneDriver.send()` must use `Channel<String>` per session; `send()` calls `channel.trySend()` only; separate drain coroutine pushes to WS.
- D-11: Server pushes JSON: `{"text": "...", "effect": "SCROLL", "zoneId": "pico-salon", "ts": "2026-06-23T12:00:00Z"}` — locked wire contract.
- D-12: Three zone categories: Local (startup-only), Network (outbound WS), Firmware (inbound WS).
- D-13: Firmware zones do not participate in UDP autodiscovery.

### Claude's Discretion

- Which exact `DisplayType` values are "local-only": treat MAX7219, LCD, OLED, UNKNOWN as local-hardware-only.
- FirmwareZoneDriver concurrent connections per zone ID: 1 device per zone ID (last-wins replaces previous session).

### Deferred Ideas (OUT OF SCOPE)

- Zone-filtered SSE stream for firmware zones
- Firmware autodiscovery via UDP
- Multiple concurrent firmware connections per zone ID
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ZONE-09 | User can create a new network zone via form on `/zones` page (name + IP + type, 422 for local hardware types) | ZoneRoutes.kt extension, AddZoneRequest model change, ZoneValidators pattern, NetworkZone ip-nullable migration, ZonesPage form extension, app.js addZone function replacement |
| ZONE-10 | Pico/ESP32 firmware can connect to server as a zone via inbound WebSocket (`GET /ws/zone/{id}`, FirmwareZoneDriver) | ktor-server-websockets dependency, install(WebSockets), new firmwareZoneRoutes, FirmwareZoneDriver with Channel + AtomicReference, ZoneRegistry.registerFirmwareZone, Routing.kt registration outside rate limiter |
</phase_requirements>

---

## Summary

Phase 16 delivers two tightly coupled capabilities: (1) a reworked `POST /zones` form and route that accepts `name`+`type`+optional `ip` and creates either a network or firmware zone, and (2) a new `GET /ws/zone/{id}` endpoint that accepts inbound WebSocket connections from Pico/ESP32 devices managed by a new `FirmwareZoneDriver`.

The core server-side WebSocket work requires adding `ktor-server-websockets` (not yet in the build) and calling `install(WebSockets)` in `HTTP.kt`. The `FirmwareZoneDriver` follows the `NetworkZoneDriver` pattern structurally (same `ZoneDriver` interface) but is inverted: instead of opening an outbound WS client connection, it is handed a `DefaultWebSocketServerSession` from the Ktor route handler. Thread safety across the display coroutine and the WS session is achieved via a `Channel<String>(capacity = 64)` per session — the display path calls `channel.trySend()` and a drain coroutine within the session's scope calls `session.send()`.

The database schema change is non-trivial: the `network_zones` table currently has `ip VARCHAR(64) NOT NULL`. Firmware zones have no IP. A new Flyway migration (V6) is required to make `ip` nullable and to add a `display_subtype` column. The `NetworkZone` model and `NetworkZonesTable` Exposed object both need updates. The existing `containsIp()` guard in `ZoneRegistry` also needs to be made null-safe.

**Primary recommendation:** Implement in three waves: (1) schema + model layer (V6 migration, NetworkZone, NetworkZonesTable, DisplayType.FIRMWARE, AddZoneRequest, ZoneValidators); (2) backend routes + FirmwareZoneDriver + ZoneRegistry (ZoneRoutes, firmwareZoneRoutes, registerFirmwareZone, build config); (3) UI (ZonesPage form extension, app.js addZone replacement, zones.js type-toggle logic).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Zone creation form (UI) | Frontend Server (SSR) | — | ZonesPage.kt Ktor HTML DSL; form rendered server-side |
| Add Zone form JS logic (type toggle, fetch) | Browser / Client | — | Conditional field show/hide + fetch POST live in app.js / zones.js |
| POST /zones validation | API / Backend | — | ZoneValidators object; RequestValidation plugin enforces 422 |
| Zone persistence | Database / Storage | — | ZoneRepository.upsert() + Flyway V6 migration |
| FirmwareZoneDriver lifecycle | API / Backend | — | Ktor webSocket{} route handler + Channel drain coroutine |
| WebSocket endpoint registration | API / Backend | — | firmwareZoneRoutes() registered in Routing.kt outside rate limiter |
| Zone registry state (ONLINE/OFFLINE) | API / Backend | — | ZoneRegistry.registerFirmwareZone() with ConcurrentHashMap.compute() |

---

## Standard Stack

No new external dependencies beyond `ktor-server-websockets`. All other changes are to existing files.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.ktor:ktor-server-websockets` | 3.5.0 | Server-side WS endpoint — `install(WebSockets)` + `webSocket{}` route DSL | Part of Ktor ecosystem; version matches all other Ktor artifacts already in the build [CITED: ktor.io/docs/server-websockets.html] |
| `kotlinx.serialization` | via plugin | Serialize `FirmwareMessage` data class to JSON wire format | Already used in project via `ktor-serialization-kotlinx-json`; `@Serializable` pattern established [VERIFIED: codebase grep — NetworkZoneDriver.kt uses Json.encodeToString] |
| `ConcurrentHashMap.compute()` | JDK stdlib | Atomic check-then-insert in ZoneRegistry | Already used (`ConcurrentHashMap` in ZoneRegistry.kt); `compute()` is the standard atomic upsert primitive [VERIFIED: codebase grep — ZoneRegistry.kt line 33] |
| `Channel<String>(capacity = 64)` | kotlinx.coroutines | Per-session queue for thread-safe WS send | Prevents display coroutine from calling `DefaultWebSocketServerSession.send()` across coroutine contexts [ASSUMED] |
| `AtomicReference<DefaultWebSocketServerSession?>` | JDK stdlib | Session reference for FirmwareZoneDriver.status() check | `AtomicReference` already used in `ScreenDriverService.kt` [VERIFIED: codebase grep] |
| Flyway 9.22.3 | existing | V6 migration to make `ip` nullable + add `display_subtype` | Already installed and managing migrations V1–V5 [VERIFIED: codebase grep — V5__add_network_zones_table.sql] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.ktor:ktor-server-test-host` | 3.5.0 | WebSocket testing in `testApplication{}` | Required for `FirmwareZoneRoutesTest` — already in `testImplementation` |
| Kotest `FunSpec` | 6.1.11 | Test style — established project convention | All 42 test files use `FunSpec` + `should` convention |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Channel<String>` drain pattern | `SharedFlow` collect inside `webSocket{}` | SharedFlow is for fan-out broadcast; single-session Channel is simpler and more explicit for 1-device-per-zone model |
| `AtomicReference` for session | `@Volatile var session` | AtomicReference is more explicit about memory visibility intent; `@Volatile` also works for read-then-null pattern. Either is acceptable — project already uses `@Volatile` in `NetworkZoneDriver` |

**Installation (build.gradle.kts addition):**
```
implementation(ktorLibs.ktor.server.websockets)
```

**catalog addition (ktor-libs.versions.toml):**
```
ktor-server-websockets = { module = "io.ktor:ktor-server-websockets", version.ref = "ktor" }
```

No version pin needed — `version.ref = "ktor"` matches the 3.5.0 already declared.

---

## Package Legitimacy Audit

`io.ktor:ktor-server-websockets` is part of the official Ktor framework published by JetBrains. It is not a separate third-party package; it is a first-party Ktor module identical in provenance to `ktor-server-core`, `ktor-server-sse`, and all other `io.ktor:*` artifacts already in the project.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `io.ktor:ktor-server-websockets` | Maven Central | 7+ yrs (Ktor 1.0 era) | Millions/mo (entire Ktor ecosystem) | github.com/ktorio/ktor | OK | Approved — first-party JetBrains artifact, identical provenance to existing `ktor-server-*` dependencies [CITED: ktor.io/docs/server-websockets.html] |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious (SUS):** none

---

## Architecture Patterns

### System Architecture Diagram

```
ZONE-09 flow (Add Zone):
  Browser form POST (name, type, ip?, displaySubtype?)
      → ZoneRoutes POST /api/v1/zones
      → ZoneValidators.validateAddZone()   ← 422 if local hardware type
      → if FIRMWARE: ZoneRegistry.registerFirmwareZone(name)   [OFFLINE placeholder]
                     ZoneRepository.upsert(zone, type=FIRMWARE, ip=null)
      → if NETWORK:  ZoneRegistry.addNetworkZone(zone, wsClient)
                     ZoneRepository.upsert(zone, type=NETWORK, ip=ip)
      ← 201 Created

ZONE-10 flow (Firmware WS connect):
  Pico/ESP32  GET /ws/zone/{id}  (WS upgrade)
      → firmwareZoneRoutes: look up ZoneRegistry[id]
      → if not found or not FIRMWARE type → 404
      → webSocket{} block entered (suspend until disconnect)
          └─ FirmwareZoneDriver.attach(session)
              ├─ AtomicReference ← session
              ├─ launch drain coroutine: for msg in channel → session.send(Frame.Text(msg))
              └─ zone status → ONLINE
          └─ incoming.consumeEach { } (drain firmware frames, ignore or log)
          └─ on close/exception: FirmwareZoneDriver.detach()
              ├─ AtomicReference ← null
              └─ zone status → OFFLINE

ZONE-10 flow (Display send to firmware):
  ScreenDriverService.displayImmediate(text, effect, zoneId)
      → ZoneRegistry.route(zoneId)
      → FirmwareZoneDriver.send(text, effect)
          → channel.trySend(Json.encodeToString(FirmwareMessage(...)))
          → drain coroutine picks up → session.send(Frame.Text(json))
          ← true (buffered) or false (channel full/OFFLINE)
```

### Recommended Project Structure

New files to create:
```
src/main/kotlin/com/anjo/
├── zone/
│   └── FirmwareZoneDriver.kt          # New: inbound WS zone driver
├── routing/
│   └── FirmwareZoneRoutes.kt          # New: GET /ws/zone/{id} handler
├── validation/
│   └── ZoneValidators.kt              # New: validateAddZone()
└── model/
    └── FirmwareMessage.kt             # New: @Serializable wire contract

src/main/resources/db/migration/
└── V6__make_ip_nullable_add_display_subtype.sql   # New: Flyway migration
```

Files to modify:
```
src/main/kotlin/com/anjo/
├── model/
│   ├── DisplayType.kt                 # Add FIRMWARE enum value
│   ├── AddZoneRequest.kt              # Add name, type, ip?, displaySubtype?
│   └── NetworkZone.kt                 # Make ip nullable; add displaySubtype?
├── db/
│   ├── NetworkZonesTable.kt           # Make ip nullable; add displaySubtype column
│   └── ZoneRepository.kt             # Handle null ip in upsert/toNetworkZone
├── service/
│   └── ZoneRegistry.kt               # Add registerFirmwareZone(); use compute()
├── routing/
│   └── ZoneRoutes.kt                 # Replace POST /zones handler; branch on type
├── routing/Routing.kt                # Register firmwareZoneRoutes() outside rate limiter
├── validation/
│   └── RequestValidationConfig.kt    # Update validate<AddZoneRequest> to use ZoneValidators
├── di/
│   ├── HTTP.kt                        # install(WebSockets)
│   └── DependencyInjection.kt        # No change needed (ZoneRegistry already provided)
└── web/templates/
    └── ZonesPage.kt                  # Extend form, add ZoneInfo.displaySubtype

src/main/resources/static/
└── app.js                            # Replace addZoneByIp; add initZoneForm type toggle
```

### Pattern 1: Ktor Server WebSocket Route Handler

**What:** `webSocket("/ws/zone/{id}")` registers an inbound WS endpoint. The block is a suspend function that runs for the lifetime of the connection. `DefaultWebSocketServerSession` is both the session and a `CoroutineScope`.

**When to use:** Any server-side endpoint that accepts persistent inbound WS connections from clients.

```kotlin
// Source: ktor.io/docs/server-websockets.html
routing {
    webSocket("/ws/zone/{id}") {
        val id = call.parameters["id"] ?: return@webSocket
        val entry = zoneRegistry.getFirmwareEntry(id)
        if (entry == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Zone not found"))
            return@webSocket
        }
        entry.driver.attach(this)
        try {
            for (frame in incoming) { }
        } catch (e: ClosedReceiveChannelException) {
        } finally {
            entry.driver.detach()
        }
    }
}
```

### Pattern 2: Channel-Based Thread-Safe Send

**What:** `FirmwareZoneDriver` holds a `Channel<String>` and an `AtomicReference` to the active session. `send()` calls `channel.trySend()` (non-suspending, caller may be on Dispatchers.Default). A drain coroutine started inside `attach()` pushes messages to the actual WS session from within the session's coroutine context.

**When to use:** Whenever a ZoneDriver's `send()` must be called from a different coroutine context than the WebSocket session.

```kotlin
// Source: STATE.md pitfall #1 + ktor.io/docs/server-websockets.html
class FirmwareZoneDriver(private val id: String) : ZoneDriver {
    private val channel = Channel<String>(capacity = 64)
    private val sessionRef = AtomicReference<DefaultWebSocketServerSession?>(null)

    fun attach(session: DefaultWebSocketServerSession) {
        sessionRef.set(session)
        session.launch {
            for (msg in channel) {
                session.send(Frame.Text(msg))
            }
        }
    }

    fun detach() {
        sessionRef.set(null)
    }

    override suspend fun send(text: String, effect: Effect): Boolean {
        if (sessionRef.get() == null) return false
        val json = Json.encodeToString(
            FirmwareMessage(text = text, effect = effect.name, zoneId = id,
                ts = Instant.now().toString())
        )
        return channel.trySend(json).isSuccess
    }

    override fun status(): ZoneStatus = ZoneStatus(
        id = id, type = "FIRMWARE",
        status = if (sessionRef.get() != null) "ONLINE" else "OFFLINE"
    )

    override fun stop() { channel.close() }
}
```

### Pattern 3: ConcurrentHashMap.compute() Atomic Check-Then-Insert

**What:** `compute(key) { _, existing -> ... }` atomically reads and optionally replaces. Replaces the two-step `containsKey + put` that is vulnerable to concurrent POSTs.

**When to use:** ZoneRegistry.registerFirmwareZone() and updating addNetworkZone() to fix D-06.

```kotlin
// Source: JDK ConcurrentHashMap.compute() specification [ASSUMED]
fun registerFirmwareZone(id: String) {
    zones.compute(id) { _, existing ->
        if (existing?.isLocal == true) existing   // never overwrite a local hardware zone
        else ZoneEntry(FirmwareZoneDriver(id), isLocal = false, ip = null)
    }
}
```

### Pattern 4: ZoneValidators for AddZoneRequest

**What:** Validation object following `ScheduleValidators` / `HistoryValidators` naming pattern. Called from `RequestValidationConfig.kt` via `validate<AddZoneRequest>`.

**When to use:** Any validation of `AddZoneRequest` — name blank check, type allowed check, IP required-if-network check.

```kotlin
// Source: codebase pattern — ScheduleValidators.kt [VERIFIED: codebase grep]
private val localHardwareTypes = setOf(
    DisplayType.MAX7219.name, DisplayType.LCD.name,
    DisplayType.OLED.name, DisplayType.UNKNOWN.name
)

object ZoneValidators {
    fun validateAddZone(req: AddZoneRequest): ValidationResult {
        if (req.name.isBlank()) return ValidationResult.Invalid("name cannot be blank")
        if (req.type in localHardwareTypes)
            return ValidationResult.Invalid(
                "Local hardware types (MAX7219, LCD, OLED) must be configured at startup " +
                "and cannot be added as zones at runtime."
            )
        if (req.type != DisplayType.FIRMWARE.name && req.ip.isNullOrBlank())
            return ValidationResult.Invalid("ip is required for network zones")
        return ValidationResult.Valid
    }
}
```

### Pattern 5: Flyway V6 Migration (ip nullable + display_subtype)

**What:** `ALTER TABLE` to make `ip` nullable and add `display_subtype` column. Uses H2-compatible syntax (H2 in tests; PostgreSQL in production).

```sql
-- Source: codebase — V5__add_network_zones_table.sql [VERIFIED: codebase grep]
ALTER TABLE network_zones ALTER COLUMN ip VARCHAR(64) NULL;
ALTER TABLE network_zones ADD COLUMN display_subtype VARCHAR(64) NULL;
```

Note: H2 uses `ALTER COLUMN ip ... NULL`; PostgreSQL uses `ALTER COLUMN ip DROP NOT NULL`. The project uses H2 in tests and PostgreSQL in production. Use H2-compatible syntax in the migration file — H2 compatibility mode handles both. [ASSUMED — verify H2 vs PostgreSQL ALTER syntax compatibility before executing]

### Anti-Patterns to Avoid

- **Calling `DefaultWebSocketServerSession.send()` from display coroutine directly:** `send()` is a suspend function bound to the session's coroutine context. Calling it from `Dispatchers.Default` across a different coroutine context causes coroutine race conditions. Always use the `Channel.trySend()` boundary. [CITED: STATE.md pitfall #1]
- **Non-atomic `containsKey + put` in ZoneRegistry:** Concurrent `POST /zones` requests both pass the null-check and both insert. Use `compute()`. [CITED: STATE.md pitfall #2]
- **Registering `webSocket{}` inside the rate-limited block:** Long-lived connections under `installApiRateLimiting` cause interference. Register in a separate `route{}` block at the top level. [CITED: Routing.kt Phase 15 liveRoutes pattern — verified in codebase]
- **Adding validation logic inline in ZoneRoutes.kt:** Violates the project rule (CLAUDE.md + STATE.md). All 422 logic goes in `ZoneValidators`. Route handler calls validator, returns early on invalid. No business logic inline.
- **Adding comments to code files:** Violates CLAUDE.md rule. No inline comments, no KDoc, no block comments.
- **Storing `DisplayType.FIRMWARE` as the zone `type` column with a value wider than 16 chars:** The `type` column is `VARCHAR(16)`. `"FIRMWARE"` is 8 chars — safe. `displaySubtype` goes in the separate `display_subtype` column (`VARCHAR(64)`).
- **Using IP as zone ID for firmware zones:** D-01 changes zone ID to `name`. The old `POST /zones` used `req.ip` as both ID and name. New handler uses `req.name` as the ID. This is a behavioral break — the old ZoneRoutesTest that sends `{"ip":"192.168.1.50"}` will need updating once `AddZoneRequest` changes.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Thread-safe WS message dispatch | Custom lock/synchronized block around session.send() | `Channel<String>` + drain coroutine within session scope | Kotlinx Channels are designed exactly for cross-coroutine-context handoff; lock-based approach risks deadlock on the WS coroutine dispatcher |
| JSON serialization for wire format | String interpolation (`"{"text":"$text"}"`) | `@Serializable data class` + `Json.encodeToString()` | String interpolation breaks on text containing `"` or `\`; existing project pattern uses `Json.encodeToString()` [VERIFIED: NetworkZoneDriver.kt] |
| Schema migration | Manual DDL in startup code | Flyway V6 migration file | Project uses Flyway 9.22.3 with `baselineOnMigrate=true`; existing V1–V5 establish the pattern [VERIFIED: codebase] |
| Type-conditional form fields | Custom CSS show/hide with class toggling | `hidden` HTML attribute toggled via JS `addEventListener` | `hidden` removes the field from tab order and prevents form submission of invisible fields; existing project convention per UI-SPEC.md |

---

## Common Pitfalls

### Pitfall 1: WebSocket send from wrong coroutine context (STATE.md pitfall #1)

**What goes wrong:** `FirmwareZoneDriver.send()` is called from `Dispatchers.Default` (the display coroutine). If it calls `session.send()` directly, it crosses coroutine contexts on the WS dispatcher, causing either a `CancellationException`, a deadlock, or silent frame drops.

**Why it happens:** `DefaultWebSocketServerSession.send()` is a coroutine extension that is context-sensitive. The webSocket handler runs on Netty's I/O thread pool (or Ktor's WS dispatcher). The display path runs on `Dispatchers.Default`.

**How to avoid:** `FirmwareZoneDriver.send()` must call `channel.trySend(json)` only. The drain coroutine launched inside `attach()` within the webSocket block's scope is on the right dispatcher. [CITED: D-10, STATE.md]

**Warning signs:** `IllegalStateException: Channel is closed` or `ClosedSendChannelException` at unexpected times; display sends returning false when device appears connected.

### Pitfall 2: ip NOT NULL constraint blocks firmware zone insert

**What goes wrong:** `NetworkZonesTable.ip` is `VARCHAR(64) NOT NULL` (V5 schema). Inserting a firmware zone with `ip = null` throws a JDBC `NOT NULL` constraint violation.

**Why it happens:** V5 migration assumed all zones have an IP. Firmware zones have no IP.

**How to avoid:** V6 Flyway migration alters `ip` to nullable before any firmware zone is inserted. `NetworkZone.ip` must be changed to `String?`. `NetworkZonesTable.ip` must use `.nullable()`. `ZoneRepository.upsert()` handles null ip. [CITED: V5__add_network_zones_table.sql — verified]

**Warning signs:** `JdbcSQLIntegrityConstraintViolationException` on `POST /zones` with FIRMWARE type.

### Pitfall 3: Existing ZoneRoutesTest breaks after AddZoneRequest model change

**What goes wrong:** `ZoneRoutesTest` sends `{"ip":"192.168.1.50"}` to `POST /api/v1/zones`. After `AddZoneRequest` gains `name`, `type`, and nullable `ip`, the existing test body is missing required fields. The 201 test becomes a 422 (name blank).

**Why it happens:** The route was IP-only before; now it requires `name` and `type`.

**How to avoid:** Update `ZoneRoutesTest` to send the new JSON shape: `{"name":"kitchen","type":"NETWORK","ip":"192.168.1.50"}`. Also update `app.js` `addZoneByIp` → new `addZone` function to POST the new JSON shape. [CITED: ZoneRoutesTest.kt — verified]

**Warning signs:** Existing test suite fails on `POST /api/v1/zones` tests after `AddZoneRequest` change.

### Pitfall 4: containsIp() null-safety after ip becomes nullable

**What goes wrong:** `ZoneRegistry.containsIp(ip)` calls `zones.values.any { it.ip == ip }`. After firmware zones are registered with `ip = null`, passing a network IP to `containsIp()` still works, but the null entries are compared without NPE risk (Kotlin `==` is null-safe). However, calling `containsIp(null)` would match firmware zones. The route now only calls `containsIp` for NETWORK type zones, so this is low risk — but verify the call site is guarded by type check first.

**How to avoid:** `POST /zones` handler should call `containsIp(req.ip!!)` only in the NETWORK branch (after validating that ip is not null). [CITED: ZoneRoutes.kt — verified]

### Pitfall 5: WebSocket route registered inside rate-limited block

**What goes wrong:** `installApiRateLimiting` inside the `route("/api/v1")` block applies to all routes within it. Registering `webSocket("/ws/zone/{id}")` there would subject the long-lived connection to the rate limiter, potentially causing the upgrade to fail or the connection to be severed under load.

**How to avoid:** Register `firmwareZoneRoutes(zoneRegistry)` in a new top-level `route("/ws") { }` block in `Routing.kt`, NOT inside the `route("/api/v1") { installApiRateLimiting(...) }` block. Mirrors how `liveRoutes(displayEventBus)` is in a separate `route("/api/v1") { }` block outside the rate limiter. [CITED: Routing.kt — verified]

### Pitfall 6: H2 vs PostgreSQL ALTER COLUMN syntax in V6 migration

**What goes wrong:** `ALTER TABLE network_zones ALTER COLUMN ip SET NULL` is PostgreSQL syntax. H2 uses `ALTER TABLE network_zones ALTER COLUMN ip VARCHAR(64) NULL` (respecify the type). Using the wrong syntax breaks tests (H2) or breaks production (PostgreSQL) depending on which form is used.

**How to avoid:** H2 accepts `ALTER COLUMN ip VARCHAR(64) NULL`. PostgreSQL accepts `ALTER COLUMN ip DROP NOT NULL`. Use H2 syntax in the migration file and test. Verify against actual H2 version (2.4.240 in the project) that this syntax is accepted. [ASSUMED — verify exact H2 2.4 ALTER syntax before finalizing migration]

---

## Code Examples

### FirmwareMessage wire contract

```kotlin
// Source: D-11 (locked wire contract)
@Serializable
data class FirmwareMessage(
    val text: String,
    val effect: String,
    val zoneId: String,
    val ts: String
)
```

### install(WebSockets) in HTTP.kt

```kotlin
// Source: ktor.io/docs/server-websockets.html [CITED]
// Add alongside existing install(SSE) in HTTP.kt:
install(WebSockets)
```

Minimal configuration (no `pingPeriod`, no `timeout` overrides) is correct for firmware device sessions — the device manages reconnect itself.

### Routing.kt addition

```kotlin
// Source: Routing.kt Phase 15 liveRoutes precedent [VERIFIED: codebase]
// Add at the end of routing{} block, NOT inside the rate-limited block:
route("/ws") {
    firmwareZoneRoutes(zoneRegistry)
}
```

### ZoneRegistry.registerFirmwareZone

```kotlin
// Source: STATE.md D-06 pitfall [CITED]
fun registerFirmwareZone(id: String) {
    zones.compute(id) { _, existing ->
        if (existing?.isLocal == true) existing
        else ZoneEntry(FirmwareZoneDriver(id), isLocal = false, ip = null)
    }
}
```

### V6 Flyway migration skeleton

```sql
ALTER TABLE network_zones ALTER COLUMN ip VARCHAR(64) NULL;
ALTER TABLE network_zones ADD COLUMN IF NOT EXISTS display_subtype VARCHAR(64) NULL;
```

### ZonesPage.kt ZoneInfo update

```kotlin
// Source: ZonesPage.kt [VERIFIED: codebase] — add displaySubtype field
data class ZoneInfo(
    val id: String,
    val type: String,
    val isLocal: Boolean,
    val ipAddress: String?,
    val status: String,
    val discoveryMethod: String?,
    val lastSeenAt: String?,
    val displaySubtype: String?   // new
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| IP-only `AddZoneRequest(ip: String)` | `AddZoneRequest(name, type, ip?, displaySubtype?)` | Phase 16 | Form and route both change; existing test bodies must update |
| IP as zone ID for network zones | User-supplied `name` as zone ID | Phase 16 | `?zone=<name>` routing now uses name; old IP-keyed lookups for new zones must use name |
| `ip NOT NULL` in `network_zones` | `ip NULLABLE` via V6 migration | Phase 16 | Firmware zones store `ip = null`; network zones unchanged |
| `DisplayType` enum: MAX7219, LCD, OLED, UNKNOWN | + `FIRMWARE` | Phase 16 | `fromString("FIRMWARE")` must not return UNKNOWN; add enum value before fromString fallback |

**Deprecated/outdated in this phase:**
- `addZoneByIp()` in `app.js`: replaced by `addZone()` that submits name + type + ip/subtype as JSON body to `POST /api/v1/zones`.
- The old `POST /zones/{ip}` URL pattern was never the actual route (it's always been `POST /zones` with a body) — but the existing `addZoneByIp` incorrectly used `fetch("/api/v1/zones/" + encodeURIComponent(ip))`. The correct target is `POST /api/v1/zones` with a JSON body. The new function must fix this URL.

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies To | Impact on Phase 16 |
|-----------|-----------|---------------------|
| No comments in code files — no inline `//`, no block `/* */`, no KDoc | All new Kotlin files | `FirmwareZoneDriver.kt`, `FirmwareZoneRoutes.kt`, `ZoneValidators.kt`, `FirmwareMessage.kt` must have zero comments |
| Validation logic in `*Validators` objects only, never in route handlers | `ZoneRoutes.kt` | 422 logic for local hardware types goes in `ZoneValidators.validateAddZone()` only; `ZoneRoutes.kt` calls validator and returns early |
| Test files follow the same no-comment rule | All new test files | No `//` comments above test blocks or inside test bodies |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Channel<String>(capacity=64)` does not require importing a specific capacity-bounded factory — standard `Channel(64)` syntax is correct in kotlinx.coroutines 1.11.0 | Code Examples | Compilation error; trivial fix |
| A2 | H2 2.4.240 accepts `ALTER COLUMN ip VARCHAR(64) NULL` syntax for making a column nullable | Pitfall 6 + V6 migration | V6 migration fails in tests; need to find correct H2 ALTER syntax |
| A3 | `ConcurrentHashMap.compute()` semantics: passing `null` as the return value from the lambda removes the key | Pattern 3 | ZoneRegistry.removeZone() is unaffected (uses `remove()` directly), but if `compute()` is misused in registerFirmwareZone it could delete the entry |
| A4 | `session.launch {}` (CoroutineScope extension on DefaultWebSocketServerSession) is the correct way to start the drain coroutine within the session's scope — it cancels automatically on session close | Pattern 2 | Drain coroutine may leak if `session` is not a CoroutineScope or if `launch` is not the extension on the session |

**Note on A4:** The official Ktor WebSocket tutorial uses `launch {}` inside `webSocket{}` blocks to start parallel coroutines within the session scope. This is confirmed by Ktor source as `DefaultWebSocketServerSession` extends `CoroutineScope`. [CITED: ktor.io/docs/server-create-websocket-application.html]

---

## Open Questions

1. **H2 ALTER COLUMN nullable syntax**
   - What we know: H2 2.4.240 is the test database; PostgreSQL 42.7.7 is production
   - What's unclear: Exact SQL syntax for `ALTER COLUMN ip` to nullable that works in both H2 2.4 and PostgreSQL
   - Recommendation: Executor should test V6 migration against `./gradlew test` before committing; if H2 rejects, try `ALTER TABLE network_zones MODIFY COLUMN ip VARCHAR(64) NULL` (MySQL-compat mode) or separate the H2 and PostgreSQL migration scripts

2. **FirmwareZoneDriver stop() on channel close**
   - What we know: `ZoneDriver.stop()` is called by `ZoneRegistry.removeZone()`. `channel.close()` is the right way to terminate the drain coroutine.
   - What's unclear: Whether closing the channel while a drain coroutine is waiting on `for (msg in channel)` causes the drain coroutine to complete cleanly or throws `ClosedReceiveChannelException`.
   - Recommendation: Use `channel.close()` — this is the documented `Channel` cancellation pattern; the `for (msg in channel)` loop exits cleanly when the channel is closed.

3. **ZoneRegistry startup: firmware zones loaded from DB**
   - What we know: The `ZoneRegistry` constructor calls `zoneRepository.findAll()` and calls `addNetworkZone()` for each. After Phase 16, `findAll()` may return zones with `type="FIRMWARE"`.
   - What's unclear: Whether the constructor should call `registerFirmwareZone(zone.id)` for FIRMWARE-type zones found in DB (so they appear OFFLINE in the list on startup) or whether they should be re-added on reconnect.
   - Recommendation: Yes — load FIRMWARE zones from DB on startup as OFFLINE placeholders. The constructor should branch: FIRMWARE type → `registerFirmwareZone(zone.id)`; other types → `addNetworkZone(zone, client)`.

---

## Environment Availability

All dependencies are JVM-only (no external services, no CLI tools, no ports that need to be open). The `ktor-server-websockets` artifact is on Maven Central and downloaded by Gradle. No availability check needed beyond `./gradlew dependencies` confirming resolution.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `io.ktor:ktor-server-websockets` | ZONE-10 | To be added to build | 3.5.0 (matches Ktor version) | none needed — first-party artifact |
| H2 in-memory DB (test) | ZoneRepositoryTest, V6 migration test | Already in `testRuntimeOnly` | 2.4.240 | — |
| Flyway migrations | V6 schema change | Already configured (`baselineOnMigrate=true`) | 9.22.3 | — |

**Missing dependencies with no fallback:** none — `ktor-server-websockets` is a Maven Central artifact that resolves immediately once added to the catalog and build.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (`FunSpec` style, `should` convention) |
| Config file | `src/test/kotlin/com/anjo/ProjectConfig.kt` |
| Quick run command | `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest"` |
| Full suite command | `./gradlew test` (JaCoCo gate: ≥70% line coverage) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ZONE-09 | POST /zones with FIRMWARE type returns 201 and zone is OFFLINE in registry | integration | `./gradlew test --tests "com.anjo.routing.ZoneRoutesTest.POST /api/v1/zones with FIRMWARE type returns 201"` | ❌ Wave 0 |
| ZONE-09 | POST /zones with MAX7219 type returns 422 with local-hardware message | integration | `./gradlew test --tests "com.anjo.routing.ZoneRoutesTest.POST /api/v1/zones with local hardware type returns 422"` | ❌ Wave 0 |
| ZONE-09 | POST /zones with NETWORK type and valid private IP returns 201 | integration | `./gradlew test --tests "com.anjo.routing.ZoneRoutesTest.POST /api/v1/zones with a valid private IP returns 201"` | ✅ (must update body) |
| ZONE-09 | ZoneValidators rejects blank name | unit | `./gradlew test --tests "com.anjo.validation.ZoneValidatorsTest"` | ❌ Wave 0 |
| ZONE-09 | GET /zones shows FIRMWARE zone type badge | integration | `./gradlew test --tests "com.anjo.routing.ZonesUIRoutesTest"` | ✅ (must update seeded data) |
| ZONE-10 | FirmwareZoneDriver.send() with no session returns false | unit | `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest.send with null session returns false"` | ❌ Wave 0 |
| ZONE-10 | FirmwareZoneDriver.status() returns OFFLINE when no session | unit | `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest.status returns OFFLINE when no session attached"` | ❌ Wave 0 |
| ZONE-10 | GET /ws/zone/{id} returns 101 for known FIRMWARE zone | integration | `./gradlew test --tests "com.anjo.routing.FirmwareZoneRoutesTest.GET /ws/zone returns 101 for known firmware zone"` | ❌ Wave 0 |
| ZONE-10 | GET /ws/zone/{unknown-id} returns 404 | integration | `./gradlew test --tests "com.anjo.routing.FirmwareZoneRoutesTest.GET /ws/zone returns 404 for unknown zone"` | ❌ Wave 0 |
| ZONE-10 | ApplicationTest DI smoke test includes FirmwareZoneRoutes | integration | `./gradlew test --tests "com.anjo.ApplicationTest.should resolve all configureDI bindings without error"` | ✅ (no change needed if no new DI binding) |
| ZONE-09+10 | V6 Flyway migration: ip nullable, display_subtype added | integration | `./gradlew test` (migration runs on first DB connect in any test) | ❌ Wave 0 (migration file) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.zone.FirmwareZoneDriverTest" --tests "com.anjo.routing.ZoneRoutesTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green (`./gradlew test`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — covers ZONE-10 unit behaviors
- [ ] `src/test/kotlin/com/anjo/routing/FirmwareZoneRoutesTest.kt` — covers ZONE-10 route behaviors (101, 404)
- [ ] `src/test/kotlin/com/anjo/validation/ZoneValidatorsTest.kt` — covers ZONE-09 validation (blank name, local type, missing IP)
- [ ] `src/main/resources/db/migration/V6__make_ip_nullable_add_display_subtype.sql` — required before any test inserts a firmware zone

---

## Security Domain

`security_enforcement` is not set to `false` in config.json. Standard security review applies.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in scope (project explicitly out-of-scope per REQUIREMENTS.md) |
| V3 Session Management | no | WebSocket sessions are zone-device sessions, not user sessions |
| V4 Access Control | no | No user roles — home network only |
| V5 Input Validation | yes | `ZoneValidators.validateAddZone()` validates name, type, ip; RequestValidation plugin enforces 422 |
| V6 Cryptography | no | WS is plaintext within home network (no TLS scope until OPS-01 Phase 18) |

### Known Threat Patterns for Ktor + WebSocket

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed `name` used as zone ID routed to `?zone=<name>` in text API | Tampering | ZoneValidators blank check; name is stored and routed as-is (no shell exec) — low risk |
| Rogue device connects to `GET /ws/zone/{id}` for a legitimate zone | Spoofing | No mitigation in Phase 16 — no device authentication. Zone must be pre-registered. Acceptable for home network scope. |
| Channel overflow (64 capacity) under burst display load | Denial of Service | `trySend()` drops on full channel and returns `false` — graceful degradation, no buffer growth |
| SSRF via zone IP field (same as Phase 11 parseDiscoveryReply risk) | SSRF | `ZoneValidators` enforces RFC1918 private IP for NETWORK type — existing `validateAddZoneRequest` already handles this; new `ZoneValidators` must re-apply the same RFC1918 check |

---

## Sources

### Primary (MEDIUM confidence)

- [CITED: ktor.io/docs/server-websockets.html] — `install(WebSockets)`, `webSocket{}` handler, disconnect detection, session lifecycle
- [CITED: ktor.io/docs/server-create-websocket-application.html] — `launch{}` inside `webSocket{}` scope, session tracking patterns
- [VERIFIED: codebase] — `ZoneRegistry.kt`, `NetworkZoneDriver.kt`, `ZoneRoutes.kt`, `ZonesPage.kt`, `RequestValidationConfig.kt`, `ScheduleValidators.kt`, `NetworkZonesTable.kt`, `V5__add_network_zones_table.sql`, `Routing.kt`, `LiveRoutes.kt`, `HTTP.kt`, `ktor-libs.versions.toml`, `build.gradle.kts`, `app.js`, `ApplicationTest.kt`, all referenced test files

### Secondary (MEDIUM confidence)

- [CITED: STATE.md §Key Pitfalls] — Pitfall #1 (WS send from display coroutine) and #2 (non-atomic check-then-insert) — established decisions recorded by project author

### Tertiary (LOW confidence)

- [ASSUMED] Channel<String>(capacity = 64) exact API — standard kotlinx.coroutines pattern, not directly verified via Context7 in this session
- [ASSUMED] H2 2.4.240 exact ALTER COLUMN syntax for nullable — needs runtime verification in test suite

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `ktor-server-websockets` is a first-party Ktor artifact; version confirmed from existing catalog
- Architecture: HIGH — patterns derived directly from codebase reading of existing drivers, routes, and validators
- Pitfalls: HIGH — 4 of 6 pitfalls are directly cited from STATE.md or verified codebase constraints; 2 are architecture-derived

**Research date:** 2026-06-23
**Valid until:** 2026-07-23 (Ktor 3.5.0 is stable; no fast-moving dependencies in this phase)
