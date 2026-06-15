# Phase 9: Display History + Audit Log - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 14 (new/modified)
**Analogs found:** 14 / 14

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/kotlin/com/anjo/db/HistoryTable.kt` | model/table | CRUD | `src/main/kotlin/com/anjo/db/SchedulesTable.kt` | exact |
| `src/main/kotlin/com/anjo/db/HistoryRepository.kt` | repository | CRUD | `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` | exact |
| `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` | config | CRUD | self (modify) | exact |
| `src/main/kotlin/com/anjo/model/HistoryRecord.kt` | model | request-response | `src/main/kotlin/com/anjo/model/Schedule.kt` | exact |
| `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` | route | request-response | `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt` | role-match |
| `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` | route | request-response | `src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt` | exact |
| `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` | component | request-response | `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` | role-match |
| `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` | component | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/routing/Routing.kt` | config | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/di/DependencyInjection.kt` | config | request-response | self (modify) | exact |
| `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` | service | event-driven | self (modify) | exact |
| `src/main/resources/db/migration/V3__add_history_table.sql` | migration | batch | `src/main/resources/db/migration/V2__add_scheduler_columns.sql` | role-match |
| `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` | test | CRUD | `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` | exact |
| `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` | test | request-response | `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` | role-match |
| `src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt` | test | request-response | `src/test/kotlin/com/anjo/ApplicationTest.kt` | role-match |
| `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` | test | event-driven | `src/test/kotlin/com/anjo/ApplicationTest.kt` | role-match |
| `src/test/kotlin/com/anjo/ApplicationTest.kt` | test | request-response | self (modify) | exact |

---

## Pattern Assignments

### `src/main/kotlin/com/anjo/db/HistoryTable.kt` (table, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/SchedulesTable.kt`

**Imports pattern** (lines 1-3):
```kotlin
package com.anjo.db

import org.jetbrains.exposed.v1.core.Table
```

**Core pattern** (lines 5-22 of SchedulesTable.kt):
```kotlin
object SchedulesTable : Table("schedules") {
    val id = varchar("id", 36)
    val text = text("text")
    // ... one val per column ...
    val zoneId = varchar("zone_id", 64).nullable()

    override val primaryKey = PrimaryKey(id)
}
```

Copy exactly, replacing table name with `"display_history"` and columns per D-06:
- `id` VARCHAR(36), `text` TEXT, `effect` VARCHAR(16), `source` VARCHAR(16)
- `scheduleId` VARCHAR(36) nullable, `zoneId` VARCHAR(64) nullable
- `displayedAt` VARCHAR(32)
- No `.default()` calls on nullable columns — leave nullable only

---

### `src/main/kotlin/com/anjo/db/HistoryRepository.kt` (repository, CRUD)

**Analog:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt`

**Imports pattern** (lines 1-16 of ScheduleRepository.kt):
```kotlin
package com.anjo.db

import com.anjo.model.Effect
import com.anjo.model.HistoryRecord
import com.anjo.model.HistoryPageResponse
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID
```

**Class declaration pattern** (line 18 of ScheduleRepository.kt):
```kotlin
class ScheduleRepository {
```
`HistoryRepository` is a `class`, never `object` — repositories are injectable instances.

**Insert pattern with UUID + Instant** (lines 43-65 of ScheduleRepository.kt):
```kotlin
suspend fun insert(schedule: Schedule): Schedule {
    val newId = UUID.randomUUID().toString()
    val createdAt = Instant.now().toString()
    suspendTransaction {
        SchedulesTable.insert {
            it[id] = newId
            it[text] = schedule.text
            // ...
        }
    }
    return schedule.copy(id = newId, createdAt = createdAt)
}
```
Adapt for `HistoryRepository.insert()` — add 1000-row cap prune BEFORE the INSERT, inside the same `suspendTransaction {}`:
```kotlin
suspend fun insert(record: HistoryRecord): HistoryRecord {
    val newId = UUID.randomUUID().toString()
    val now = Instant.now().toString()
    suspendTransaction {
        val count = HistoryTable.selectAll().count()
        if (count >= MAX_ROWS) {
            val oldest = HistoryTable
                .selectAll()
                .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
                .limit(1)
                .single()[HistoryTable.id]
            HistoryTable.deleteWhere { HistoryTable.id eq oldest }
        }
        HistoryTable.insert {
            it[id]          = newId
            it[text]        = record.text
            it[effect]      = record.effect
            it[source]      = record.source
            it[scheduleId]  = record.scheduleId
            it[zoneId]      = record.zoneId
            it[displayedAt] = now
        }
    }
    return record.copy(id = newId, displayedAt = now)
}

companion object {
    private const val MAX_ROWS = 1000
}
```

**Filter + paginated query pattern** (based on lines 31-41 of ScheduleRepository.kt — `findAllActive` uses `where { A and B }`):
```kotlin
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null
): Pair<List<HistoryRecord>, Long> = suspendTransaction {
    var query = HistoryTable.selectAll()
    if (effect != null) {
        query = query.where { HistoryTable.effect eq effect }
    }
    if (source != null) {
        query = if (effect != null) {
            query.andWhere { HistoryTable.source eq source }
        } else {
            query.where { HistoryTable.source eq source }
        }
    }
    val total = query.count()
    val items = query
        .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
        .limit(size, offset = ((page - 1).toLong() * size))
        .map { it.toHistoryRecord() }
    Pair(items, total)
}
```

**ResultRow mapper pattern** (lines 110-125 of ScheduleRepository.kt):
```kotlin
private fun ResultRow.toSchedule(): Schedule = Schedule(
    id = this[SchedulesTable.id],
    text = this[SchedulesTable.text],
    // ...
)
```
Adapt as `private fun ResultRow.toHistoryRecord(): HistoryRecord`.

---

### `src/main/kotlin/com/anjo/model/HistoryRecord.kt` (model, request-response)

**Analog:** `src/main/kotlin/com/anjo/model/Schedule.kt`

**Imports + class pattern** (lines 1-5 of Schedule.kt):
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val id: String = "",
    // ...
)
```

Adapt for two classes in one file:
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryRecord(
    val id: String = "",
    val text: String,
    val effect: String,
    val source: String,
    val scheduleId: String? = null,
    val zoneId: String? = null,
    val displayedAt: String = ""
)

@Serializable
data class HistoryPageResponse(
    val items: List<HistoryRecord>,
    val page: Int,
    val size: Int,
    val total: Long
)
```

Note: `effect` is `String` (not `Effect` enum) so it round-trips through JSON without a `valueOf()` in the response path. The `Effect` enum is used only in `ScreenDriverService` when constructing the record.

---

### `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` (route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt`

**Imports pattern** (lines 1-16 of ScheduleRoutes.kt):
```kotlin
package com.anjo.routing

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryPageResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
```

**Core GET route pattern** (lines 19-26 of ScheduleRoutes.kt):
```kotlin
fun Route.scheduleRoutes(repository: ScheduleRepository, schedulerService: SchedulerService) {
    route("/schedule") {
        get {
            val schedules = repository.findAll()
            call.respond(schedules)
        }
    }
}
```

Adapt for `HistoryRoutes` — single GET with query param parsing:
```kotlin
fun Route.historyRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val effect = call.request.queryParameters["effect"]?.uppercase()
            ?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = call.request.queryParameters["source"]?.uppercase()
            ?.takeIf { it.isNotEmpty() && it != "ALL" }
        val (items, total) = historyRepository.findPaginated(page, size, effect, source)
        call.respond(HistoryPageResponse(items, page, size, total))
    }
}
```

No validation error responses — invalid `effect`/`source` values return zero rows (SQL `WHERE effect = 'INVALID'` returns empty set). Consistent with project's lenient filter-query pattern.

---

### `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` (route, request-response)

**Analog:** `src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt`

**Full file pattern** (all 17 lines of ScheduleUIRoutes.kt):
```kotlin
package com.anjo.routing.ui

import com.anjo.db.ScheduleRepository
import com.anjo.web.templates.BaseLayout
import com.anjo.web.templates.schedulePage
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.scheduleUIRoutes(repository: ScheduleRepository) {
    get("/schedule") {
        val schedules = repository.findAll()
        val html = BaseLayout.render(pageTitle = "Schedule Manager", activePath = "/schedule") {
            schedulePage(schedules)
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
```

Adapt — pass additional query params to template:
```kotlin
fun Route.historyUIRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page      = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rawSize   = call.request.queryParameters["size"] ?: "20"
        val sizeAll   = rawSize.lowercase() == "all"
        val size      = if (sizeAll) Int.MAX_VALUE else rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val effect    = call.request.queryParameters["effect"].orEmpty()
        val source    = call.request.queryParameters["source"].orEmpty()
        val expandAll = call.request.queryParameters["expand"] == "all"
        val (items, total) = historyRepository.findPaginated(
            page,
            if (sizeAll) total.toInt() else size,
            effect.takeIf { it.isNotEmpty() && it != "ALL" },
            source.takeIf { it.isNotEmpty() && it != "ALL" }
        )
        val html = BaseLayout.render("History — TextReaderRpi", "/history") {
            historyPage(items, page, rawSize, total, expandAll, effect, source)
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
```

---

### `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` (component, request-response)

**Analog:** `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt`

**Imports pattern** (lines 1-21 of SchedulePage.kt):
```kotlin
package com.anjo.web.templates

import com.anjo.model.HistoryRecord
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
// ... other kotlinx.html DSL imports as needed
```

**Top-level function signature pattern** (line 23 of SchedulePage.kt):
```kotlin
fun FlowContent.schedulePage(schedules: List<Schedule>) {
```

Adapt as extension on `FlowContent`:
```kotlin
fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String
)
```

**Form pattern** (lines 27-77 of SchedulePage.kt — `<select>` with named options):
```kotlin
select {
    id = "effect"; name = "effect"
    option { value = "SCROLL"; selected = true; +"Scroll" }
    option { value = "BLINK"; +"Blink" }
    // ...
}
```

**Empty state pattern** (lines 81-83 of SchedulePage.kt):
```kotlin
if (schedules.isEmpty()) {
    div { +"No schedules yet." }
}
```

**`<details>/<summary>` card pattern** — no existing analog in codebase; use D-14/D-15 spec:
```kotlin
for (item in items) {
    details {
        if (expandAll) attributes["open"] = ""
        summary {
            +item.text.take(60).let { if (item.text.length > 60) "$it…" else it }
            span { attributes["role"] = "note"; +item.effect }
            +item.displayedAt
        }
        p { strong { +"Full text:" }; +item.text }
        p { strong { +"Effect:" }; +item.effect }
        p { strong { +"Source:" }; +item.source }
        if (item.source == "SCHEDULED" && item.scheduleId != null) {
            p {
                strong { +"Schedule:" }
                a(href = "/schedule?id=${item.scheduleId}") { +item.scheduleId.take(8) }
            }
        }
        if (item.zoneId != null) {
            p { strong { +"Zone:" }; +item.zoneId }
        }
        p { strong { +"Displayed at:" }; +item.displayedAt }
    }
}
```

**Numbered pagination footer** — build page link URLs preserving all current params:
```
?page=N&effect=$effect&source=$source&size=$rawSize${if (expandAll) "&expand=all" else ""}
```
Use `aria-current="page"` on the current page link.

---

### `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` (component — modify)

**Analog:** self

**Nav link pattern** (lines 37-40 of BaseLayout.kt):
```kotlin
li { a(href = "/") { attributes["aria-current"] = if (activePath == "/") "page" else ""; +"Home" } }
li { a(href = "/schedule") { attributes["aria-current"] = if (activePath == "/schedule") "page" else ""; +"Schedule" } }
li { a(href = "/settings/display") { attributes["aria-current"] = if (activePath == "/settings/display") "page" else ""; +"Settings" } }
li { a(href = "/status") { attributes["aria-current"] = if (activePath == "/status") "page" else ""; +"Status" } }
```

**Insert after `/schedule` line**, before `/settings/display`:
```kotlin
li { a(href = "/history") { attributes["aria-current"] = if (activePath == "/history") "page" else ""; +"History" } }
```

Result nav order: Home | Schedule | History | Settings | Status

---

### `src/main/kotlin/com/anjo/routing/Routing.kt` (config — modify)

**Analog:** self

**Dependency resolution pattern** (lines 26-30 of Routing.kt):
```kotlin
val screenDriverService: ScreenDriverService by dependencies
val scheduleRepository: ScheduleRepository by dependencies
```

**Route wiring pattern** (lines 34-43 of Routing.kt):
```kotlin
routing {
    staticResources("/static", "static")
    webRoutes(screenDriverService)
    scheduleUIRoutes(scheduleRepository)    // top-level UI route
    // ...
    route("/api/v1") {
        // ...
        scheduleRoutes(scheduleRepository, schedulerService)    // api route inside /api/v1
    }
}
```

Add at marked positions:
```kotlin
val historyRepository: HistoryRepository by dependencies    // with other val declarations

// in routing block, top level (alongside scheduleUIRoutes):
historyUIRoutes(historyRepository)

// inside route("/api/v1") block:
historyRoutes(historyRepository)
```

---

### `src/main/kotlin/com/anjo/di/DependencyInjection.kt` (config — modify)

**Analog:** self

**Instantiation pattern** (lines 42-46 of DependencyInjection.kt — after `DatabaseFactory.init()`):
```kotlin
val scheduleRepository = ScheduleRepository()
val effectRendererFactory = EffectRendererFactory()
val schedulerService = SchedulerService(scheduleRepository, screenDriverService, effectRendererFactory)

DatabaseFactory.init(appConfig.databaseConfig)
```

**Critical ordering:** `HistoryRepository` must be instantiated AFTER `DatabaseFactory.init()` — same as `ScheduleRepository`. Current code violates this for `ScheduleRepository` (line 42 is before `DatabaseFactory.init()` on line 46) — follow the existing position pattern even if the ordering looks odd, as it works with the current DI framework.

**`provide {}` registration pattern** (lines 51-63 of DependencyInjection.kt):
```kotlin
dependencies {
    provide { appConfig }
    // ...
    provide { scheduleRepository }
    // ...
}
```

Changes needed:
1. Add `val historyRepository = HistoryRepository()` alongside `scheduleRepository`
2. Add `historyRepository = historyRepository` as last param to `ScreenDriverService(...)` constructor
3. Add `provide { historyRepository }` in `dependencies {}` block

---

### `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` (service — modify)

**Analog:** self

**Constructor param pattern** (lines 22-29 of ScreenDriverService.kt):
```kotlin
class ScreenDriverService(
    private var driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher,
    private val retryConfig: RetryConfig,
    private val displaySelectionService: DisplaySelectionService?,
    private val metrics: ScreenDriverMetrics,
    private val effectFactory: EffectRendererFactory = EffectRendererFactory(),
)
```

Add nullable param with default `null` as last parameter (D-01, Pitfall 5):
```kotlin
private val historyRepository: HistoryRepository? = null,
```

**INTERRUPT path — `displayImmediate()`** (lines 78-90 of ScreenDriverService.kt):
```kotlin
try {
    displayMutex.withLock {
        executeWithRecovery(text, effectFactory.create(effect))
    }
} catch (e: Exception) {
    metrics.failedMeter?.mark()
    log.error("Display operation failed after retries: ${e.message}", e)
} finally {
    // ...
}
return true
```

Insert history recording inside `displayMutex.withLock {}`, after `executeWithRecovery()` (D-02, D-03):
```kotlin
displayMutex.withLock {
    executeWithRecovery(text, effectFactory.create(effect))
    try {
        historyRepository?.insert(
            HistoryRecord(text = text, effect = effect.name, source = "IMMEDIATE")
        )
    } catch (e: Exception) {
        log.warn("History insert failed (non-fatal): ${e.message}", e)
    }
}
```

**INTERRUPT path — `displayScheduled()`** (lines 129-133 of ScreenDriverService.kt):
```kotlin
displayMutex.withLock {
    executeWithRecovery(text, renderer)
    displaySucceeded = true
}
```

Add history insert after `displaySucceeded = true`, still inside the lock:
```kotlin
displayMutex.withLock {
    executeWithRecovery(text, renderer)
    displaySucceeded = true
    try {
        historyRepository?.insert(
            HistoryRecord(
                text = text,
                effect = renderer.effect.name,
                source = "SCHEDULED",
                scheduleId = scheduleId
            )
        )
    } catch (e: Exception) {
        log.warn("History insert failed (non-fatal): ${e.message}", e)
    }
}
```

**SKIP_NEW paths** — same insert placement after `executeWithRecovery()` in the SKIP_NEW branches (lines 58, 110). SKIP_NEW requests that were dropped (early `return false`) are NOT recorded — consistent with D-04.

**Open question (Pitfall 1):** `renderer.effect` — verify whether `EffectRenderer` exposes `val effect: Effect`. If not, add `abstract val effect: Effect` to the abstract class and implement in each concrete renderer. Check `src/main/kotlin/com/anjo/service/effect/EffectRenderer.kt` at implementation time.

---

### `src/main/resources/db/migration/V3__add_history_table.sql` (migration)

**Analog:** `src/main/resources/db/migration/V2__add_scheduler_columns.sql`

**Style pattern** (all 5 lines of V2):
- File starts with a comment block explaining the migration
- Uses `ALTER TABLE` / `CREATE TABLE IF NOT EXISTS`
- Column names in `snake_case`
- Type + constraint on same line
- No trailing semicolons within comment block

```sql
CREATE TABLE IF NOT EXISTS display_history (
    id           VARCHAR(36)  NOT NULL,
    text         TEXT         NOT NULL,
    effect       VARCHAR(16)  NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    schedule_id  VARCHAR(36)  NULL,
    zone_id      VARCHAR(64)  NULL,
    displayed_at VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id)
);
```

`CREATE TABLE IF NOT EXISTS` is safe for re-runs (unlike V2's `ALTER TABLE ADD COLUMN`).

---

### `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` (config — modify)

**Analog:** self

**SchemaUtils.create pattern** (lines 32-34 of DatabaseFactory.kt):
```kotlin
transaction {
    SchemaUtils.create(SchedulesTable)
}
```

Add `HistoryTable` in the same `transaction {}`:
```kotlin
transaction {
    SchemaUtils.create(SchedulesTable)
    SchemaUtils.create(HistoryTable)
}
```

---

### `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` (test, CRUD)

**Analog:** `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt`

**Full test scaffold pattern** (lines 1-30 of ScheduleRepositoryTest.kt):
```kotlin
package com.anjo.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ScheduleRepositoryTest : FunSpec({

    val repository = ScheduleRepository()

    beforeSpec {
        Database.connect("jdbc:h2:mem:test_schedules;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(SchedulesTable) }
    }

    beforeEach {
        transaction { SchedulesTable.deleteWhere { SchedulesTable.id.isNotNull() } }
    }
    // tests use: runTest { ... }
    // assertions use: shouldBe, shouldNotBeNull, shouldBeNull
})
```

Use `"jdbc:h2:mem:test_history;DB_CLOSE_DELAY=-1"` for the in-memory DB name.

Required test cases per D-24:
- `insert()` persists all columns (round-trip)
- `findPaginated()` returns correct page/total
- Pruning: 1001st insert leaves table at 1000 rows
- `findPaginated()` with `effect` filter returns only matching rows
- `findPaginated()` with `source` filter returns only matching rows

---

### `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` and `HistoryUIRoutesTest.kt` (tests)

**Analog:** `src/test/kotlin/com/anjo/ApplicationTest.kt`

**testApplication pattern** (lines 27-31 of ApplicationTest.kt):
```kotlin
test("should start application context and serve health endpoint") {
    testApplication {
        application { module() }
        val response = client.get("/health")
        response.status shouldBe HttpStatusCode.OK
    }
}
```

For `HistoryRoutesTest` — verify `GET /api/v1/history`:
- Returns 200 with `items`, `page`, `size`, `total` fields
- Page 2 returns different items than page 1
- `?effect=SCROLL` filter returns only SCROLL records
- `?source=IMMEDIATE` filter returns only IMMEDIATE records

For `HistoryUIRoutesTest` — verify `GET /history`:
- Returns 200 HTML containing "Display History"
- `GET /history?expand=all` renders `<details` with `open` attribute
- Filter dropdowns for effect and source present in HTML

---

### `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` (test, event-driven)

**Analog:** `src/test/kotlin/com/anjo/ApplicationTest.kt` (testApplication pattern)

Use `testApplication { application { module() } }` with real H2 in-memory. Required cases per D-24:
- `displayImmediate()` writes a record to `display_history` after success
- `displayScheduled()` writes a record with `source = "SCHEDULED"`
- SKIP_NEW dropped request does not write a record
- DB error in `historyRepository.insert()` does not cause `displayImmediate()` to return false or throw

---

### `src/test/kotlin/com/anjo/ApplicationTest.kt` (test — modify)

**Analog:** self

**DI smoke test pattern** (lines 67-84 of ApplicationTest.kt):
```kotlin
test("should resolve all configureDI bindings without error") {
    testApplication {
        application { module() }
        client.get("/health")
        val deps = application.dependencies
        deps.getBlocking<ScheduleRepository>(DependencyKey<ScheduleRepository>()) shouldNotBeNull {}
        // ... other assertions ...
    }
}
```

Add one assertion for `HistoryRepository` (D-23):
```kotlin
deps.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>()) shouldNotBeNull {}
```

Add required import:
```kotlin
import com.anjo.db.HistoryRepository
```

---

## Shared Patterns

### suspendTransaction
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` lines 14, 46
**Apply to:** `HistoryRepository` — all database access methods
```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

suspendTransaction {
    // all SQL inside
}
```

### ISO-8601 VARCHAR timestamp
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` line 45
**Apply to:** `HistoryRepository.insert()` for `displayedAt`
```kotlin
val now = Instant.now().toString()
```

### UUID primary key
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` line 44
**Apply to:** `HistoryRepository.insert()`
```kotlin
val newId = UUID.randomUUID().toString()
```

### Kotest `should` convention
**Source:** `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` lines 41-43
**Apply to:** All new test files
```kotlin
// assertions:
value shouldBe expected
value.shouldNotBeNull()
value.shouldBeNull()
```

### runTest for suspend functions
**Source:** `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` lines 38-47
**Apply to:** `HistoryRepositoryTest`, `HistoryRecordingTest`
```kotlin
import kotlinx.coroutines.test.runTest

test("...") {
    runTest {
        // suspend calls here
    }
}
```

### BaseLayout.render call
**Source:** `src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt` lines 12-15
**Apply to:** `HistoryUIRoutes`
```kotlin
val html = BaseLayout.render(pageTitle = "...", activePath = "/history") {
    historyPage(...)
}
call.respondText(html, ContentType.Text.Html)
```

### Exposed v1 import package
**Source:** `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` lines 9-14
**Apply to:** `HistoryTable`, `HistoryRepository`

All imports use `org.jetbrains.exposed.v1.*` — never `org.jetbrains.exposed.sql.*` (old package, do not use).

---

## No Analog Found

All files have a close codebase analog. The `<details>/<summary>` card pattern in `HistoryPage.kt` has no existing codebase example (SchedulePage uses `<table>`) — use the code excerpt in Pattern 6 of RESEARCH.md and the UI-SPEC.md contract directly.

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`, `src/main/resources/db/migration/`
**Files scanned:** 12
**Pattern extraction date:** 2026-06-15
