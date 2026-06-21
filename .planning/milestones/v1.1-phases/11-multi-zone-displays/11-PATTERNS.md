# Phase 11: Multi-Zone Displays - Pattern Map

**Mapped:** 2026-06-16
**Files analyzed:** 18 new/modified files
**Analogs found:** 17 / 18

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/anjo/config/model/ZonesConfig.kt` | config | transform | `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` | role-match |
| `src/main/kotlin/com/anjo/config/model/ZoneConfig.kt` | config | transform | `src/main/kotlin/com/anjo/config/model/DisplayConfig.kt` | role-match |
| `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` | interface | request-response | `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` | exact |
| `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` | driver | request-response | `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` | role-match |
| `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt` | driver | event-driven | `src/main/kotlin/com/anjo/service/WebhookService.kt` | partial |
| `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` | service | request-response | `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` | role-match |
| `src/main/kotlin/com/anjo/service/NetworkDiscoveryService.kt` | service | event-driven | `src/main/kotlin/com/anjo/service/SchedulerService.kt` | role-match |
| `src/main/kotlin/com/anjo/db/NetworkZonesTable.kt` | model | CRUD | `src/main/kotlin/com/anjo/db/SchedulesTable.kt` | exact |
| `src/main/kotlin/com/anjo/db/ZoneRepository.kt` | repository | CRUD | `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` | exact |
| `src/main/kotlin/com/anjo/model/ZoneStatus.kt` | model | transform | `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` (`DisplayStatus`) | role-match |
| `src/main/kotlin/com/anjo/routing/ZoneRoutes.kt` | route | request-response | `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` | exact |
| `src/main/resources/db/migration/V5__add_network_zones_table.sql` | migration | CRUD | `src/main/resources/db/migration/` (existing V1-V4) | role-match |
| `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` (MODIFY) | service | request-response | self | n/a |
| `src/main/kotlin/com/anjo/routing/TextRoutes.kt` (MODIFY) | route | request-response | self | n/a |
| `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (MODIFY) | config | transform | self | n/a |
| `src/main/kotlin/com/anjo/routing/Routing.kt` (MODIFY) | route | request-response | self | n/a |
| `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` (MODIFY) | config | CRUD | self | n/a |
| `src/test/kotlin/com/anjo/service/ZoneRegistryTest.kt` | test | request-response | (no analog — new test) | none |

---

## Pattern Assignments

### `ZonesConfig.kt` and `ZoneConfig.kt` (config, transform)

**Analog:** `src/main/kotlin/com/anjo/config/model/WebhooksConfig.kt` and `DisplayConfig.kt`

**WebhooksConfig.kt — minimal config data class pattern** (lines 1-5):
```kotlin
package com.anjo.config.model

data class WebhooksConfig(
    val defaultUrl: String?
)
```

**DisplayConfig.kt — nested config data class with defaults** (lines 1-33):
```kotlin
package com.anjo.config.model

data class DisplayConfig(
    val type: String = "MAX7219",
    val max7219: Max7219Config = Max7219Config(),
    ...
)

data class Max7219Config(
    val numDevices: Int = 2,
    val gpioPins: Map<String, Int> = mapOf("spi_ce" to 8, ...)
)
```

**Pattern to copy for `ZonesConfig` and `ZoneConfig`:**
- `ZonesConfig` is a single wrapper: `data class ZonesConfig(val zones: List<ZoneConfig> = emptyList())`
- `ZoneConfig` carries per-zone fields with defaults: `id`, `type`, `numDevices`, `chipSelect`, `bus`
- No nullables in config unless value is genuinely optional (follow `Max7219Config` style)
- Package: `com.anjo.config.model`

---

### `ZoneDriver.kt` (interface, request-response)

**Analog:** `src/main/kotlin/com/anjo/driver/DisplayDriver.kt`

**DisplayDriver interface pattern** (lines 1-24):
```kotlin
package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

data class DisplayStatus(
    val isActive: Boolean,
    val hardwareAvailable: Boolean,
    val currentMessage: String? = null,
    val error: String? = null
)

interface DisplayDriver {
    fun scrollText(scope: CoroutineScope, text: String, speedMs: Long = 80)
    fun clear()
    fun write(text: String)
    fun status(): DisplayStatus
    fun stop()

    suspend fun setBrightness(level: Int) {}
    suspend fun displayStatic(text: String) { write(text) }
}
```

**Pattern to copy for `ZoneDriver`:**
- Define the companion data class (`ZoneStatus`) in the model package, not inline in the interface
- Package: `com.anjo.zone`
- Interface body: `fun send(text: String, effect: Effect): Boolean` and `fun status(): ZoneStatus`
- No default implementations needed — both `LocalZoneDriver` and `NetworkZoneDriver` will implement fully

---

### `LocalZoneDriver.kt` (driver, request-response)

**Analog:** `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` (delegation pattern) and `Max7219Matrix.kt` (SPI init)

**Max7219Matrix SPI init pattern** (lines 1-60, key section lines 51-60):
```kotlin
package com.anjo.driver

import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import com.pi4j.io.spi.SpiMode
import com.pi4j.plugin.linuxfs.provider.spi.LinuxFsSpiProviderImpl

class Max7219Matrix(
    private val ctx: Context,
    private val numDevices: Int = 2,
    private val zoneId: Int = 0,
) : AbstractDisplayDriver() {

    private val spi: Spi?

    init {
        spi = try {
            val config = Spi.newConfigBuilder(ctx)
                .id("max7219-zone-$zoneId")
                .name("MAX7219 SPI Zone $zoneId")
                .bus(SpiBus.BUS_0)
                .chipSelect(SpiChipSelect.CS_0)   // <-- MUST differ per zone
                .baud(1_000_000)
                .mode(SpiMode.MODE_0)
                .provider(LinuxFsSpiProviderImpl::class.java)
```

**Pattern to copy for `LocalZoneDriver`:**
- Constructor receives a `DisplayDriver` (already constructed by DI) and the runtime `ZoneStatus`
- `send()` delegates to `driver.displayStatic(text)` or `driver.scrollText()` based on `effect`
- `status()` reads from the driver's `DisplayStatus.hardwareAvailable` to return `ZoneStatus`
- Wraps any exception from `driver.*` in a `try/catch`, returns `false` on failure (OFFLINE behavior)
- Package: `com.anjo.zone`

---

### `NetworkZoneDriver.kt` (driver, event-driven)

**Analog:** `src/main/kotlin/com/anjo/service/WebhookService.kt`

**WebhookService — CoroutineScope + SupervisorJob + stop() pattern** (lines 26-58):
```kotlin
class WebhookService(
    private val httpClient: HttpClient,
    private val config: WebhooksConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    fun send(schedule: Schedule, firedAt: Instant) {
        scope.launch {
            try {
                withTimeout(5_000) {
                    val response = httpClient.post(url) { ... }
                    if (!response.status.isSuccess()) { log.warn("...") }
                }
            } catch (e: TimeoutCancellationException) {
                log.warn("Webhook timeout: ...")
            } catch (e: Exception) {
                log.warn("Webhook failed: ...")
            }
        }
    }

    fun stop() {
        scope.coroutineContext[Job]?.cancel()
        httpClient.close()
    }
}
```

**Pattern to copy for `NetworkZoneDriver`:**
- Constructor: `ip: String`, `port: Int = 80`, `httpClient: HttpClient`, `scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())`
- `@Volatile private var online = false` — status derived from this, not DB
- `startConnect()` launches a `while(scope.isActive)` loop calling `httpClient.webSocket(...) { ... }` with `try/catch/finally { online = false; delay(5_000) }`
- `send()` wraps `session?.send(Frame.Text(...))` in `try/catch(Exception)`, returns `false` on exception
- `stop()` calls `scope.coroutineContext[Job]?.cancel()` — same as `WebhookService.stop()`, no `httpClient.close()` (shared client)
- Package: `com.anjo.zone`

---

### `ZoneRegistry.kt` (service, request-response)

**Analog:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt`

**ScreenDriverService — service constructor injection and displayMutex pattern** (lines 28-43):
```kotlin
class ScreenDriverService(
    private var driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher,
    private val retryConfig: RetryConfig,
    private val displaySelectionService: DisplaySelectionService?,
    private val metrics: ScreenDriverMetrics,
    private val historyRepository: HistoryRepository? = null,
) {
    private val displayMutex = Mutex()
    private val displayScope = CoroutineScope(SupervisorJob() + ioDispatcher)
```

**SchedulerService — ConcurrentHashMap per-job pattern** (lines 24-34):
```kotlin
class SchedulerService(...) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
```

**Pattern to copy for `ZoneRegistry`:**
- `private val zones = ConcurrentHashMap<String, ZoneDriver>()` — zone map
- `private val mutexes = ConcurrentHashMap<String, Mutex>()` — per-zone mutex (lazy: `mutexes.getOrPut(id) { Mutex() }`)
- `route(zoneId: String, text: String, effect: Effect): Boolean` — single zone dispatch
- `broadcast(text: String, effect: Effect): BroadcastResult` — parallel `scope.async { runCatching { ... } }` per zone, `awaitAll()`
- `listAll(): List<ZoneStatus>` — `zones.map { (id, driver) -> driver.status() }`
- `addNetworkZone(ip: String)` — construct `NetworkZoneDriver`, call `startConnect()`, add to `zones`
- Package: `com.anjo.service`

---

### `NetworkDiscoveryService.kt` (service, event-driven)

**Analog:** `src/main/kotlin/com/anjo/service/SchedulerService.kt`

**SchedulerService — start()/stop() lifecycle pattern** (lines 35-55):
```kotlin
fun start() {
    log.info("SchedulerService starting")
    scope.launch {
        try {
            ...
        } catch (e: Exception) {
            log.error("Failed to load ...: ${e.message}", e)
        }
    }
}

fun stop() {
    log.info("SchedulerService stopping")
    scope.coroutineContext[Job]?.cancel()
    activeJobs.clear()
}
```

**SchedulerService — isActive loop pattern** (lines 83-100):
```kotlin
private suspend fun tickLoop() {
    while (scope.isActive) {
        try {
            delay(60_000L)
            ...
        } catch (_: CancellationException) {
            break
        } catch (e: Exception) {
            log.error("...", e)
        }
    }
}
```

**Pattern to copy for `NetworkDiscoveryService`:**
- `private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())` — same as `WebhookService`
- `start()` launches `scope.launch(Dispatchers.IO) { jmdns = JmDNS.create(...); jmdns?.addServiceListener(...) }` — IO dispatch required (blocks on network)
- `stop()` calls `jmdns?.close()` then `scope.coroutineContext[Job]?.cancel()`
- `suspend fun scanUdp()` uses `withContext(Dispatchers.IO)` with `DatagramSocket`
- `private suspend fun onDeviceDiscovered(ip, port, method)` is the shared callback for both UDP and mDNS; calls `zoneRepository.upsert()` then `zoneRegistry.addNetworkZone(ip)`
- Package: `com.anjo.service`

---

### `NetworkZonesTable.kt` (model, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/SchedulesTable.kt`

**SchedulesTable — Exposed Table object pattern** (lines 1-22):
```kotlin
package com.anjo.db

import org.jetbrains.exposed.v1.core.Table

object SchedulesTable : Table("schedules") {
    val id = varchar("id", 36)
    val text = text("text")
    val effect = varchar("effect", 16).default("SCROLL")
    val zoneId = varchar("zone_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)
}
```

**HistoryTable — zoneId nullable column pattern** (lines 1-17):
```kotlin
object HistoryTable : Table("display_history") {
    val zoneId = varchar("zone_id", 64).nullable()
    val displayedAt = varchar("displayed_at", 32)
    ...
}
```

**Pattern to copy for `NetworkZonesTable`:**
- `object NetworkZonesTable : Table("network_zones")`
- Import: `import org.jetbrains.exposed.v1.core.Table`
- Columns: `id varchar(64) PK`, `name varchar(64)`, `ip varchar(64)`, `type varchar(16).default("MAX7219")`, `discoveryMethod varchar(8)`, `createdAt varchar(32)`, `lastSeenAt varchar(32).nullable()`
- No `status` column — status is in-memory only (per D-05)
- `override val primaryKey = PrimaryKey(id)` — same as `SchedulesTable`
- Package: `com.anjo.db`

---

### `ZoneRepository.kt` (repository, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt`

**ScheduleRepository — suspendTransaction + ResultRow mapping pattern** (lines 1-126, key sections):

Imports (lines 1-17):
```kotlin
package com.anjo.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID
```

Insert pattern (lines 43-65):
```kotlin
suspend fun insert(schedule: Schedule): Schedule {
    val newId = UUID.randomUUID().toString()
    val createdAt = Instant.now().toString()
    suspendTransaction {
        SchedulesTable.insert {
            it[id] = newId
            it[text] = schedule.text
            ...
        }
    }
    return schedule.copy(id = newId, createdAt = createdAt)
}
```

`toSchedule()` mapping function (lines 110-126):
```kotlin
private fun ResultRow.toSchedule(): Schedule = Schedule(
    id = this[SchedulesTable.id],
    text = this[SchedulesTable.text],
    ...
    zoneId = this[SchedulesTable.zoneId]
)
```

**Pattern to copy for `ZoneRepository`:**
- All DB calls use `suspendTransaction { }` — no exceptions
- `findAll(): List<NetworkZone>` — `NetworkZonesTable.selectAll().map { it.toNetworkZone() }`
- `upsert(zone: NetworkZone)` — check existence with `selectAll().where { id eq zone.id }.singleOrNull()`, then insert or update; update `lastSeenAt` on re-discovery
- `delete(id: String): Boolean` — `NetworkZonesTable.deleteWhere { NetworkZonesTable.id eq id } > 0`
- Private `ResultRow.toNetworkZone()` extension — same pattern as `toSchedule()`
- Package: `com.anjo.db`

---

### `ZoneStatus.kt` (model, transform)

**Analog:** `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` (`DisplayStatus` data class)

**DisplayStatus pattern** (lines 5-10):
```kotlin
data class DisplayStatus(
    val isActive: Boolean,
    val hardwareAvailable: Boolean,
    val currentMessage: String? = null,
    val error: String? = null
)
```

**Pattern to copy for `ZoneStatus`:**
- Simple `data class ZoneStatus(val id: String, val type: String, val status: String, val ip: String? = null, val lastSeenAt: String? = null)`
- `status` values: `"ONLINE"`, `"OFFLINE"`, `"DEGRADED"` — stored as String, not enum (matches DB convention)
- Package: `com.anjo.model`
- Must be `@Serializable` (for JSON response in `GET /api/v1/zones`)

---

### `ZoneRoutes.kt` (route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt`

**ScheduleRoutes — Route extension + path param pattern** (lines 1-76):

Function signature pattern (lines 19-20):
```kotlin
fun Route.scheduleRoutes(repository: ScheduleRepository, schedulerService: SchedulerService) {
    route("/schedule") {
```

Path parameter extraction with early-return pattern (lines 36-40):
```kotlin
val id = call.parameters["id"]
    ?: return@get call.respond(HttpStatusCode.BadRequest, "missing id")
val schedule = repository.findById(id)
    ?: return@get call.respond(HttpStatusCode.NotFound)
```

**Pattern to copy for `ZoneRoutes`:**
- `fun Route.zoneRoutes(zoneRegistry: ZoneRegistry, discoveryService: NetworkDiscoveryService)`
- `get("/zones") { call.respond(zoneRegistry.listAll()) }`
- `post("/zones/discover") { val found = discoveryService.scanUdp(); call.respond(found) }`
- `post("/zones/{ip}") { val ip = call.parameters["ip"] ?: return@post call.respond(HttpStatusCode.BadRequest, "missing ip"); ... }`
- Validation (RFC1918 range check on `ip`) stays in the route handler — project rule: validation in route handlers, never in service or repository
- 503 response: `call.respond(HttpStatusCode.ServiceUnavailable, ...)` when zone is OFFLINE
- Import `HttpStatusCode` members explicitly — same as `ScheduleRoutes`
- Package: `com.anjo.routing`

---

### `TextRoutes.kt` (MODIFY — route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/TextRoutes.kt` (self — minimal addition)

**Current pattern** (lines 15-28):
```kotlin
fun Route.textRoutes(screenDriverService: ScreenDriverService) {
    post("/text") {
        val request = call.receive<TextRequest>()
        log.info("Text received: length=${request.text.length} effect=${request.effect} conflictPolicy=${request.conflictPolicy}")
        val accepted = screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy)
        call.respond(
            HttpStatusCode.Accepted,
            TextResponse(accepted = accepted, message = if (accepted) "Text queued for rendering" else "Display busy, request skipped")
        )
    }
}
```

**Modification — add `?zone=` param:**
- Add `val zoneId = call.request.queryParameters["zone"]` before the `displayImmediate` call
- Pass `zoneId` as new parameter: `screenDriverService.displayImmediate(request.text, request.effect, request.conflictPolicy, zoneId)`
- When zone is OFFLINE, `displayImmediate` returns `false` with 503; route must distinguish SKIP (200+false) from OFFLINE (503) — use a sealed result type or an exception thrown by `displayImmediate` for OFFLINE cases

---

### `ScreenDriverService.kt` (MODIFY — service, request-response)

**Analog:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` (self)

**Current single-driver and single-mutex pattern to replace** (lines 29-41):
```kotlin
class ScreenDriverService(
    private var driver: DisplayDriver,
    ...
) {
    private val displayMutex = Mutex()
```

**ConcurrentHashMap pattern (from SchedulerService lines 22-23):**
```kotlin
private val activeJobs = ConcurrentHashMap<String, Job>()
```

**Modification — inject ZoneRegistry, add per-zone mutex map:**
- Replace `private var driver: DisplayDriver` with `private val zoneRegistry: ZoneRegistry`
- Replace `private val displayMutex = Mutex()` with `private val mutexes = ConcurrentHashMap<String, Mutex>()`
- Add `zoneId: String? = null` param to `displayImmediate()` and `displayScheduled()`
- `null` zoneId → broadcast via `zoneRegistry.broadcast(text, effect)` → `BroadcastResult`
- Non-null zoneId → `mutexes.getOrPut(zoneId) { Mutex() }` → `zoneRegistry.route(zoneId, text, effect)`
- `tryInsertHistory` receives `zoneId: String?` — each parallel broadcast dispatch passes its own zone ID
- Remove `displaySelectionService` dependency (superseded)

---

### `DependencyInjection.kt` (MODIFY — config, transform)

**Analog:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (self)

**Current lifecycle subscription pattern** (lines 52-57):
```kotlin
monitor.subscribe(ApplicationStarted) { schedulerService.start() }
monitor.subscribe(ApplicationStopping) {
    schedulerService.stop()
    webhookService.stop()
    screenDriverService.stop()
}
```

**Current `provide {}` block pattern** (lines 59-73):
```kotlin
dependencies {
    provide { appConfig }
    provide { displaySelectionService }
    provide { screenDriverService }
    ...
}
```

**Modifications:**
- Construct `zoneRepository`, `zoneRegistry`, `networkDiscoveryService` before `ScreenDriverService`
- Pass `zoneRegistry` to `ScreenDriverService` constructor instead of `driver` + `displaySelectionService`
- Add `monitor.subscribe(ApplicationStarted) { ...; networkDiscoveryService.start() }`
- Add `networkDiscoveryService.stop()` to `ApplicationStopping` block
- Add `provide { zoneRegistry }`, `provide { zoneRepository }`, `provide { networkDiscoveryService }` to `dependencies {}` block
- Remove or retire `provide { displaySelectionService }` — superseded

---

### `Routing.kt` (MODIFY — route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/Routing.kt` (self)

**Current route registration pattern** (lines 26-57):
```kotlin
fun Application.configureRouting() {
    ...
    val screenDriverService: ScreenDriverService by dependencies

    routing {
        route("/api/v1") {
            installApiRateLimiting(apiConfig.rateLimitPerMinute)
            textRoutes(screenDriverService)
            scheduleRoutes(scheduleRepository, schedulerService)
            ...
        }
    }
}
```

**Modification:**
- Add `val zoneRegistry: ZoneRegistry by dependencies` and `val networkDiscoveryService: NetworkDiscoveryService by dependencies` to the `by dependencies` block
- Add `zoneRoutes(zoneRegistry, networkDiscoveryService)` inside `route("/api/v1") { }` block — same indent level as existing routes

---

### `DatabaseFactory.kt` (MODIFY — config, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` (self)

**Current pattern** (lines 10-31):
```kotlin
object DatabaseFactory {
    fun init(databaseConfig: DatabaseConfig) {
        ...
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(hasExistingTable)
            .baselineVersion("1")
            .load()
            .migrate()
    }
}
```

**Modification:**
- No change to `DatabaseFactory` itself — Flyway auto-discovers `V5__add_network_zones_table.sql` from `classpath:db/migration/`
- Flyway is the authoritative source; no `SchemaUtils.createMissingTablesAndColumns()` call needed for Flyway-managed tables

---

### `V5__add_network_zones_table.sql` (migration, CRUD)

**Analog:** Existing V1–V4 migrations in `src/main/resources/db/migration/`

**Pattern:** `CREATE TABLE IF NOT EXISTS network_zones (...)` — `IF NOT EXISTS` guard matches existing migration style. Filename: `V5__add_network_zones_table.sql` (double underscore after version number).

---

## Shared Patterns

### CoroutineScope + SupervisorJob (background IO services)
**Source:** `src/main/kotlin/com/anjo/service/WebhookService.kt` lines 29, 56-58
**Apply to:** `NetworkZoneDriver`, `NetworkDiscoveryService`, `ZoneRegistry` (broadcast path)
```kotlin
private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun stop() {
    scope.coroutineContext[Job]?.cancel()
}
```

### ApplicationStarted / ApplicationStopping lifecycle hooks
**Source:** `src/main/kotlin/com/anjo/di/DependencyInjection.kt` lines 52-57
**Apply to:** `NetworkDiscoveryService` wiring in `DependencyInjection.kt`
```kotlin
monitor.subscribe(ApplicationStarted) { schedulerService.start() }
monitor.subscribe(ApplicationStopping) {
    schedulerService.stop()
    webhookService.stop()
    screenDriverService.stop()
}
```

### Exposed suspendTransaction pattern
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` lines 20-22
**Apply to:** `ZoneRepository` — every DB call
```kotlin
suspend fun findAll(): List<Schedule> = suspendTransaction {
    SchedulesTable.selectAll().map { it.toSchedule() }
}
```

### Route path param extraction with early return
**Source:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` lines 36-40
**Apply to:** `ZoneRoutes` — `POST /api/v1/zones/{ip}` and any route with path params
```kotlin
val id = call.parameters["id"]
    ?: return@get call.respond(HttpStatusCode.BadRequest, "missing id")
```

### Exposed Table object structure
**Source:** `src/main/kotlin/com/anjo/db/SchedulesTable.kt` lines 1-22
**Apply to:** `NetworkZonesTable` — exact same import and `override val primaryKey` pattern
```kotlin
import org.jetbrains.exposed.v1.core.Table

object SchedulesTable : Table("schedules") {
    val id = varchar("id", 36)
    ...
    override val primaryKey = PrimaryKey(id)
}
```

### Error handling — catch + log + false return
**Source:** `src/main/kotlin/com/anjo/service/WebhookService.kt` lines 47-52
**Apply to:** `NetworkZoneDriver.send()`, `ZoneRegistry.route()`, `LocalZoneDriver.send()`
```kotlin
} catch (e: TimeoutCancellationException) {
    log.warn("Webhook timeout: ...")
} catch (e: Exception) {
    log.warn("Webhook failed: ... error=${e.message}")
}
```

### `isActive` while loop for background coroutine
**Source:** `src/main/kotlin/com/anjo/service/SchedulerService.kt` lines 83-100
**Apply to:** `NetworkZoneDriver` connect loop, `NetworkDiscoveryService` mDNS listener (indirectly — JmDNS manages the loop)
```kotlin
while (scope.isActive) {
    try {
        delay(...)
        ...
    } catch (_: CancellationException) { break }
    catch (e: Exception) { log.error(...) }
}
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/kotlin/com/anjo/service/ZoneRegistryTest.kt` | test | request-response | No service test for multi-driver registry exists yet; follow Kotest FunSpec + `should` convention from existing tests |

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/` — all packages
**Files scanned:** 14 source files read directly
**Pattern extraction date:** 2026-06-16
