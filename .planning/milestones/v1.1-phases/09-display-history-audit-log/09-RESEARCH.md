# Phase 9: Display History + Audit Log — Research

**Researched:** 2026-06-15
**Domain:** Kotlin/Ktor + Exposed ORM + Flyway — persistent audit log with paginated JSON API and server-rendered HTML
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Recording Injection**
- D-01: `HistoryRepository` injected directly into `ScreenDriverService` as a new constructor parameter — same pattern as `RetryConfig` and `EffectRendererFactory`. No new service layer.
- D-02: History record written **after** `executeWithRecovery()` succeeds. Only successful renders are persisted.
- D-03: If `historyRepository.insert()` throws, the exception is caught and logged; `displayImmediate()`/`displayScheduled()` still returns `true`. History is observability — a DB hiccup must not break the display path.
- D-04: SKIP_NEW skipped requests are **not** recorded.
- D-05: 1000-row cap enforced synchronously inside `HistoryRepository.insert()` in the same `suspendTransaction{}`: DELETE the single oldest row when count reaches 1000, then INSERT the new row.

**History Table Schema**
- D-06: `HistoryTable` columns: `id` (VARCHAR 36, UUID PK), `text` (TEXT), `effect` (VARCHAR 16), `source` (VARCHAR 16 — `IMMEDIATE` or `SCHEDULED`), `scheduleId` (VARCHAR 36, nullable), `zoneId` (VARCHAR 64, nullable, default null), `displayedAt` (VARCHAR 32, ISO-8601).
- D-07: `displayedAt` stored as `Instant.now().toString()`.
- D-08: `zoneId` nullable with default null — ready for Phase 11 multi-zone without migration.
- D-09: Flyway migration: `V3__add_history_table.sql` — next in sequence after V1 and V2.

**GET /api/v1/history API**
- D-10: Pagination params: `?page=1&size=20` (1-indexed page).
- D-11: Response envelope: `{ items: [...], page: 1, size: 20, total: 87 }`.
- D-12: Server-side filters: `?effect=SCROLL&source=IMMEDIATE`. Supported `effect` values: all `Effect` enum values; `source`: `IMMEDIATE`, `SCHEDULED`.
- D-13: Default sort: newest first (`ORDER BY displayedAt DESC`).

**/history HTML Page**
- D-14: Cards grid layout using `<details>/<summary>` — pure browser-native, no JavaScript.
- D-15: Collapsed card: text preview (truncated), effect badge, timestamp. Expanded: full text, effect, source, scheduleId (linked to `/schedule?id=X`), zoneId (shown if non-null).
- D-16: `scheduleId` is a clickable `<a href="/schedule?id=X">`.
- D-17: Filter controls: `<select>` dropdowns for effect and source. Zone filter rendered but disabled.
- D-18: `?expand=all` query param — Ktor HTML DSL renders all `<details open>` server-side.
- D-19: Page size selector: 20 / 50 / all.
- D-20: Numbered page links footer.

**Navigation**
- D-21: "History" link added to `BaseLayout.kt` nav after "Schedule": `Home | Schedule | History | Settings | Status`.

**DI & Test Integration**
- D-22: `HistoryRepository` registered in `DependencyInjection.kt` (`provide {}` block).
- D-23: `ApplicationTest.kt` DI smoke test updated to resolve `HistoryRepository`.
- D-24: `HistoryRepository` and recording integration tested with real H2 in-memory. Tests cover: `insert()`, `findPaginated()`, pruning at row 1001, and recording after `displayImmediate()` succeeds. No mock repositories.

### Claude's Discretion

None — discussion stayed within phase scope.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

Phase 10 and Phase 11 are explicitly out of scope (no webhook logic, no zone routing).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HIST-01 | Every text display event (immediate and scheduled) is recorded — text, effect, zone, timestamp, source | `HistoryTable` + `HistoryRepository.insert()` called in both `ScreenDriverService.displayImmediate()` and `displayScheduled()` after `executeWithRecovery()` succeeds |
| HIST-02 | `GET /api/v1/history` returns paginated list of display events | `HistoryRepository.findPaginated()` + `HistoryRoutes` using Exposed `selectAll().limit().offset()` pattern |
| HIST-03 | `GET /history` HTML page with cards, pagination, zone and effect filters | `HistoryUIRoutes` + `HistoryPage` Ktor HTML DSL template using `<details>/<summary>` cards and native GET form filters |
</phase_requirements>

---

## Summary

Phase 9 adds persistent event logging for every successful text display. The implementation follows three parallel tracks: (1) a new database layer (`HistoryTable` + `HistoryRepository`) using the project's established Exposed 1.3.0 + Flyway + suspendTransaction pattern, (2) a JSON API route (`GET /api/v1/history`) with pagination and filtering, and (3) a server-rendered HTML page (`GET /history`) with `<details>/<summary>` cards, filter form, and numbered pagination.

The project already has every library needed: Exposed 1.3.0, Flyway 9.22.3, H2 2.4.240, kotlinx.html 0.12.0, Ktor 3.5.0. No new dependencies are required. The implementation is a pure code-and-migration exercise that closely mirrors the existing `SchedulesTable` / `ScheduleRepository` / `ScheduleUIRoutes` pattern. The most critical architectural decision (D-03) is that history write failures must be silently swallowed — the display path cannot be disrupted by an observability concern.

The 1000-row cap (D-05) is implemented as a delete-before-insert in the same `suspendTransaction{}` — this avoids the race condition that a separate cleanup coroutine would introduce on a single-threaded Pi environment.

**Primary recommendation:** Clone the `SchedulesTable` → `ScheduleRepository` → `ScheduleRoutes` → `ScheduleUIRoutes` pattern exactly, adjusting only schema columns, filter logic, and HTML template shape.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| History persistence (insert, cap prune) | Database / Storage | — | Pure DB concern; `HistoryRepository` owns all SQL |
| Recording injection (when to insert) | API / Backend (Service) | — | `ScreenDriverService` owns display lifecycle; it decides when insert is called |
| Paginated JSON query | API / Backend (Routes) | Database / Storage | Route reads query params, calls `HistoryRepository.findPaginated()` |
| Filter + sort | Database / Storage | — | Applied in SQL `WHERE`+`ORDER BY`, not in-memory |
| HTML page rendering | Frontend Server (SSR) | — | Ktor HTML DSL renders server-side; zero client JS |
| Filter form submission | Browser / Client | Frontend Server (SSR) | Native GET form; server reads query params and re-renders |
| Expand/collapse cards | Browser / Client | Frontend Server (SSR) | `<details>` is browser-native; `?expand=all` is server-side |

---

## Standard Stack

No new dependencies. All libraries are already declared in `gradle/ktor-libs.versions.toml`.

### Core (already present)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Exposed (`exposed-core`, `exposed-jdbc`) | 1.3.0 | `HistoryTable` definition, `suspendTransaction{}`, DSL queries | Project standard ORM — `[VERIFIED: codebase]` |
| Flyway (`flyway-core`) | 9.22.3 | `V3__add_history_table.sql` migration | Already manages V1+V2 — `[VERIFIED: codebase]` |
| H2 | 2.4.240 | Embedded DB (default) + in-memory test DB | Already configured — `[VERIFIED: codebase]` |
| kotlinx.html-jvm | 0.12.0 | `<details>/<summary>` card template in Ktor HTML DSL | Project standard for all HTML pages — `[VERIFIED: codebase]` |
| kotlinx.serialization | (bundled with Ktor 3.5.0) | JSON response envelope serialization | Already used for all API responses — `[VERIFIED: codebase]` |
| Kotest (`kotest-runner-junit5`, `kotest-assertions-core`) | 6.1.11 | `should` convention tests | Project standard test framework — `[VERIFIED: codebase]` |
| `kotlinx-coroutines-test` | 1.11.0 | `runTest {}` for suspend function tests | Already in test dependencies — `[VERIFIED: codebase]` |

### No New Dependencies Required

All packages needed for Phase 9 are already declared. The planner MUST NOT add any new dependency entries to `build.gradle.kts` or `ktor-libs.versions.toml`.

**Installation:** None required.

---

## Package Legitimacy Audit

> No new packages are introduced in Phase 9. All libraries are existing project dependencies verified in `gradle/ktor-libs.versions.toml` and the active `build.gradle.kts`. [VERIFIED: codebase]

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious (SUS):** none

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/text
        │
        ▼
  TextRoutes.textRoutes()
        │
        ▼
  ScreenDriverService.displayImmediate()
        │
        ├──► displayMutex.withLock { executeWithRecovery(text, renderer) }
        │              │ success
        │              ▼
        │    HistoryRepository.insert(record)   ◄── try/catch: swallow exception
        │              │
        │              ▼
        │         suspendTransaction {
        │           if count >= 1000: DELETE oldest row
        │           INSERT new row
        │         }
        │
        └──► return true

SchedulerService.fire()
        │
        ▼
  ScreenDriverService.displayScheduled()
        │  (same insert path as above, source="SCHEDULED", scheduleId=id)
        │

GET /api/v1/history?page=1&size=20&effect=SCROLL&source=IMMEDIATE
        │
        ▼
  HistoryRoutes.historyRoutes()
        │
        ▼
  HistoryRepository.findPaginated(page, size, effect?, source?)
        │
        ▼
  suspendTransaction {
    SELECT … WHERE effect=? AND source=?
    ORDER BY displayedAt DESC
    LIMIT size OFFSET (page-1)*size
  }
        │
        ▼
  { items: [...], page: 1, size: 20, total: N }

GET /history?page=1&size=20&effect=ALL&source=ALL&expand=all
        │
        ▼
  HistoryUIRoutes.historyUIRoutes(historyRepository)
        │
        ▼
  HistoryRepository.findPaginated(...)
        │
        ▼
  BaseLayout.render("/history") {
    historyPage(items, page, size, total, expand, effect, source)
  }
        │
        ▼
  HTML: filter form + <details>/<summary> cards + pagination footer
```

### Recommended Project Structure

```
src/main/kotlin/com/anjo/
├── db/
│   ├── HistoryTable.kt          # new — mirrors SchedulesTable.kt
│   ├── HistoryRepository.kt     # new — mirrors ScheduleRepository.kt
│   ├── DatabaseFactory.kt       # modified — add HistoryTable to SchemaUtils.create
│   ├── SchedulesTable.kt        # unchanged
│   └── ScheduleRepository.kt   # unchanged
├── model/
│   └── HistoryRecord.kt         # new — @Serializable data class + HistoryPageResponse
├── routing/
│   ├── HistoryRoutes.kt         # new — GET /api/v1/history
│   ├── Routing.kt               # modified — wire historyRoutes + historyUIRoutes
│   └── ui/
│       ├── HistoryUIRoutes.kt   # new — GET /history
│       └── WebRoutes.kt         # unchanged
├── web/templates/
│   ├── HistoryPage.kt           # new — <details>/<summary> cards, filter form, pagination
│   └── BaseLayout.kt            # modified — add History nav link (D-21)
├── di/
│   └── DependencyInjection.kt   # modified — provide { historyRepository }; inject into screenDriverService
└── service/
    └── ScreenDriverService.kt   # modified — add historyRepository constructor param; insert after executeWithRecovery

src/main/resources/db/migration/
└── V3__add_history_table.sql    # new

src/test/kotlin/com/anjo/
├── db/
│   └── HistoryRepositoryTest.kt # new — H2 in-memory: insert, findPaginated, pruning
├── routing/
│   ├── HistoryRoutesTest.kt     # new — testApplication{}: GET /api/v1/history pagination + filters
│   └── HistoryUIRoutesTest.kt   # new — testApplication{}: GET /history HTML content
├── service/
│   └── HistoryRecordingTest.kt  # new — displayImmediate records; SKIP_NEW does not; DB error does not break return
└── ApplicationTest.kt           # modified — resolve HistoryRepository in DI smoke test
```

### Pattern 1: HistoryTable — Exposed Table Object

**What:** Object extending `Table`, one `val` per column, matching Kotlin naming conventions.
**When to use:** Any new persistent entity.
**Example:**
```kotlin
// Source: SchedulesTable.kt (codebase)
import org.jetbrains.exposed.v1.core.Table

object HistoryTable : Table("display_history") {
    val id         = varchar("id", 36)
    val text       = text("text")
    val effect     = varchar("effect", 16)
    val source     = varchar("source", 16)
    val scheduleId = varchar("schedule_id", 36).nullable()
    val zoneId     = varchar("zone_id", 64).nullable()
    val displayedAt = varchar("displayed_at", 32)

    override val primaryKey = PrimaryKey(id)
}
```

### Pattern 2: HistoryRepository — suspendTransaction CRUD

**What:** Class (not object) with suspend functions, each wrapping `suspendTransaction {}`.
**When to use:** All database access. Never call repository methods from non-suspend context.
**Example — insert with 1000-row cap:**
```kotlin
// Source: ScheduleRepository.kt pattern (codebase) + D-05 decision
suspend fun insert(record: HistoryRecord): HistoryRecord {
    val newId = UUID.randomUUID().toString()
    val now   = Instant.now().toString()
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
            it[effect]      = record.effect.name
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

**Example — paginated query with optional filters:**
```kotlin
// Source: Exposed 1.3.0 DSL pattern (codebase: ScheduleRepository.findAllActive)
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null
): Pair<List<HistoryRecord>, Long> = suspendTransaction {
    var query = HistoryTable.selectAll()
    if (effect != null) query = query.where { HistoryTable.effect eq effect }
    if (source != null) query = query.andWhere { HistoryTable.source eq source }
    val total = query.count()
    val items = query
        .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
        .limit(size, offset = ((page - 1) * size).toLong())
        .map { it.toHistoryRecord() }
    Pair(items, total)
}
```

### Pattern 3: HistoryRecord model + response envelope

**What:** `@Serializable data class` for the domain entity; separate `@Serializable data class` for the paginated response envelope.
**Example:**
```kotlin
// Source: Schedule.kt + D-11 decisions
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

Note: `effect` stored as `String` in `HistoryRecord` (not `Effect` enum) so it round-trips through JSON without an `Effect.valueOf()` call in the response path. The `Effect` enum is only needed when constructing the record inside `ScreenDriverService`.

### Pattern 4: HistoryRoutes — JSON API

**What:** Suspend route function matching the `/api/v1` block pattern.
**Example:**
```kotlin
// Source: ScheduleRoutes.kt pattern + D-10, D-11, D-12, D-13
fun Route.historyRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val effect = call.request.queryParameters["effect"]?.uppercase()?.let {
            if (it == "ALL" || it.isEmpty()) null else it
        }
        val source = call.request.queryParameters["source"]?.uppercase()?.let {
            if (it == "ALL" || it.isEmpty()) null else it
        }
        val (items, total) = historyRepository.findPaginated(page, size, effect, source)
        call.respond(HistoryPageResponse(items, page, size, total))
    }
}
```

Validation note: Invalid `effect` or `source` values that don't match known enum strings result in zero rows returned (SQL `WHERE effect = 'INVALID'` simply returns empty). No 422 needed — consistent with the project's lenient filter-query pattern.

### Pattern 5: HistoryUIRoutes — HTML Page

**What:** Route function receiving `historyRepository` directly as a parameter (same as `scheduleUIRoutes`).
**Example:**
```kotlin
// Source: ScheduleUIRoutes.kt pattern + D-14 through D-20
fun Route.historyUIRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page      = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rawSize   = call.request.queryParameters["size"] ?: "20"
        val sizeAll   = rawSize.lowercase() == "all"
        val size      = if (sizeAll) Int.MAX_VALUE else rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val effect    = call.request.queryParameters["effect"].orEmpty()
        val source    = call.request.queryParameters["source"].orEmpty()
        val expandAll = call.request.queryParameters.contains("expand") &&
                        call.request.queryParameters["expand"] == "all"
        val (items, total) = historyRepository.findPaginated(
            page, if (sizeAll) total.toInt() else size,
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

### Pattern 6: HistoryPage DSL — details/summary cards

**What:** Extension function on `FlowContent` building `<details>/<summary>` cards.
**Key constraints from UI-SPEC.md:**
- Text preview: max 60 chars, `…` if longer
- Effect badge: `<span role="note">[EFFECT]</span>`
- scheduleId paragraph only when `source == "SCHEDULED"`
- zoneId paragraph only when zoneId non-null
- `?expand=all` adds `open` attribute to every `<details>`
- When `size=all`, no pagination footer
- Numbered page links with `aria-current="page"` on current page
- Filter form resets page to 1 on size change (all size submits go to page=1)

```kotlin
// Source: D-14 through D-20 + 09-UI-SPEC.md
fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String
) {
    h2 { +"Display History" }
    // Filter form — native GET, no JS
    form {
        method = FormMethod.get
        action = "/history"
        // selects for effect, source, size, zone(disabled)
        // hidden input for expand=all preservation
        button { type = ButtonType.submit; +"Apply Filters" }
        // Expand all / Collapse all links
    }
    // Cards or empty state
    if (items.isEmpty()) {
        p { +"No display events recorded yet. Send text via the home page to see history here." }
    } else {
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
                    p { strong { +"Schedule:" }
                        a(href = "/schedule?id=${item.scheduleId}") {
                            +item.scheduleId.take(8)
                        }
                    }
                }
                if (item.zoneId != null) {
                    p { strong { +"Zone:" }; +item.zoneId }
                }
                p { strong { +"Displayed at:" }; +item.displayedAt }
            }
        }
    }
    // Numbered pagination footer (omitted when size=all or total <= size)
}
```

### Pattern 7: ScreenDriverService history injection

**What:** Add `historyRepository` as last constructor parameter with default null (or as non-null — see pitfall below). Call `insert()` in a try/catch after `executeWithRecovery()` succeeds.
**Example — displayImmediate INTERRUPT path (lines 79–90 of ScreenDriverService):**
```kotlin
// Source: D-01, D-02, D-03 + ScreenDriverService.kt (codebase)
try {
    displayMutex.withLock {
        executeWithRecovery(text, effectFactory.create(effect))
        // history insert goes here — after lock, before finally
        try {
            historyRepository?.insert(
                HistoryRecord(text = text, effect = effect.name, source = "IMMEDIATE")
            )
        } catch (e: Exception) {
            log.warn("History insert failed (non-fatal): ${e.message}", e)
        }
    }
} catch (e: Exception) { … }
```

**Example — displayScheduled success path:**
```kotlin
// Source: D-02 — insert only when displaySucceeded = true
executeWithRecovery(text, renderer)
displaySucceeded = true
try {
    historyRepository?.insert(
        HistoryRecord(
            text = text,
            effect = renderer.effect.name,  // need effect on renderer, or pass effect param
            source = "SCHEDULED",
            scheduleId = scheduleId
        )
    )
} catch (e: Exception) {
    log.warn("History insert failed (non-fatal): ${e.message}", e)
}
```

### Pattern 8: Flyway migration — V3

**What:** SQL file in `src/main/resources/db/migration/` named `V3__add_history_table.sql`.
**Style from V1 + V2:**
```sql
-- Source: V1__initial_schema.sql + V2__add_scheduler_columns.sql (codebase)
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

Note: No index on `displayed_at` in this phase. On H2 with a hard cap of 1000 rows, a full-table scan for ORDER BY + LIMIT is fast enough. [ASSUMED] — no performance measurement on Pi 4 with 1000 rows; add index if query latency becomes visible in Phase 12.

### Anti-Patterns to Avoid

- **Inserting history before `executeWithRecovery()`:** Violates D-02. Ghost entries appear for failed/cancelled renders.
- **Inserting history outside the `displayMutex.withLock {}` block for INTERRUPT path:** The lock has already been released; the history record is still correct, but inserting outside the lock is a sequencing inconsistency. Keep insert inside the lock for both SKIP_NEW and INTERRUPT paths.
- **Throwing from `historyRepository.insert()` into the display path:** Violates D-03. Always wrap in try/catch and log at WARN level.
- **Calling `SchemaUtils.createMissingTablesAndColumns()` instead of `SchemaUtils.create()`:** The project uses `SchemaUtils.create()` exclusively; Flyway handles migrations. [VERIFIED: codebase — DatabaseFactory.kt]
- **Using `selectAll().count()` in a separate transaction before the DELETE+INSERT:** Creates a TOCTOU race. Both the count check and the insert must be in the same `suspendTransaction{}`. [VERIFIED: D-05]
- **Making `HistoryRepository` a singleton `object`:** All repositories in this project are `class` instances (not `object`s), so they can be injected and replaced in tests. [VERIFIED: codebase — ScheduleRepository]
- **Using `size=all` as an integer:** When `size=all`, pass the total count as the SQL LIMIT, not `Int.MAX_VALUE` as a LIMIT to the DB (H2/PostgreSQL LIMIT clause may behave unexpectedly with very large values). Fetch total first, then fetch all rows up to that count.
- **Rendering `<details open="">` with an empty string vs. attribute presence:** In kotlinx.html, use `attributes["open"] = ""` (not `attributes["open"] = "open"`) — HTML5 spec requires attribute presence, not value.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL migrations | Manual `SchemaUtils.createMissingTablesAndColumns()` on every boot | Flyway `V3__add_history_table.sql` | Idempotent, versioned, handles existing Pi installs via `baselineOnMigrate` |
| Paginated SQL | In-memory slice of `findAll()` | `query.limit(size, offset = ...)` in Exposed | H2/PostgreSQL pagination is O(offset) not O(n); avoids loading 1000 rows into memory |
| JSON serialization of response | Manual `buildJsonObject {}` | `@Serializable data class` + `call.respond()` | Consistent with all other API endpoints; automatically handles Content-Type |
| Row count | Separate non-transactional count | `count()` inside same `suspendTransaction{}` | Atomicity — avoids TOCTOU between count and insert |

**Key insight:** This phase adds zero new architectural concepts. Every mechanism (Flyway migration, suspendTransaction, @Serializable, kotlinx.html DSL, testApplication{}) is already used in Phases 6–8.

---

## Common Pitfalls

### Pitfall 1: Effect name not available in `displayScheduled()`

**What goes wrong:** `ScreenDriverService.displayScheduled()` receives an `EffectRenderer` not an `Effect` enum — so `effect.name` is not directly available to write to the history record.
**Why it happens:** The service was designed before history existed; the renderer is the concrete object, not the enum.
**How to avoid:** Either (a) add an `effect: Effect` property to `EffectRenderer` (abstract class already has `render()` — add `val effect: Effect`), or (b) pass `effect` as an additional parameter to `displayScheduled()`. Option (a) is cleaner; `SchedulerService.fire()` already has `schedule.effect` available when creating the renderer via `effectFactory.create(schedule.effect)`.
**Warning signs:** Compiler error if you try to call `.name` on `EffectRenderer`.

### Pitfall 2: DI construction order — `HistoryRepository` needed by `ScreenDriverService` before `DatabaseFactory.init()`

**What goes wrong:** `DependencyInjection.kt` currently creates `ScreenDriverService` before calling `DatabaseFactory.init()`. If `HistoryRepository` is instantiated inside the `ScreenDriverService` constructor (unlikely but possible) and immediately tries a DB call, it fails.
**Why it happens:** `DatabaseFactory.init()` calls `Database.connect()` + Flyway; until that runs, `suspendTransaction{}` throws `ExposedSQLException: No database connected`.
**How to avoid:** Instantiate `HistoryRepository` after `DatabaseFactory.init()` — same position as `ScheduleRepository` in the current `DependencyInjection.kt`. The existing pattern is correct: `ScheduleRepository()` is created after `DatabaseFactory.init(appConfig.databaseConfig)`. Follow the same order.
**Warning signs:** `ExposedSQLException` or `IllegalStateException: No database connected` in startup logs.

### Pitfall 3: `?size=all` parameter — integer parse failure

**What goes wrong:** `call.request.queryParameters["size"]?.toIntOrNull()` returns `null` for `"all"`. If the route then defaults to 20, the page size selector's "all" option silently reverts to default.
**Why it happens:** Naive integer parsing doesn't handle the string sentinel value.
**How to avoid:** Check the raw string before parsing: `val rawSize = call.request.queryParameters["size"] ?: "20"; val sizeAll = rawSize.lowercase() == "all"`. When `sizeAll`, skip pagination footer and pass total-count as the LIMIT.
**Warning signs:** "All" option in the size selector shows the same 20 items as the default.

### Pitfall 4: Filter form param preservation across pagination/expand links

**What goes wrong:** Numbered page links like `<a href="?page=2">` drop the current `effect`, `source`, `size`, and `expand` query params — clicking page 2 resets filters to defaults.
**Why it happens:** Each `<a>` must manually re-include all current query params in the URL.
**How to avoid:** Build page link URLs by assembling all current params: `?page=N&effect=$effect&source=$source&size=$rawSize${if (expandAll) "&expand=all" else ""}`.
**Warning signs:** Filter selections disappear when navigating to page 2+.

### Pitfall 5: `HistoryRepository` null-safety in `ScreenDriverService`

**What goes wrong:** If `historyRepository` is declared as nullable (`HistoryRepository?`) for backward-compatibility with existing tests that construct `ScreenDriverService` without it, every call site needs `?.insert(...)`. This is fine, but test files that construct `ScreenDriverService` directly must be verified not to pass a non-null mock when the test doesn't need recording.
**Why it happens:** The CONTEXT.md specifies no mock repositories (D-24) — but existing service tests (`ConflictPolicyTest`, `ScreenDriverRecoveryTest`, `ScreenDriverResourceTest`) construct `ScreenDriverService` directly. Adding a required non-null `historyRepository` param would break these tests unless they also pass a repository.
**How to avoid:** Use a nullable constructor param with default `null`: `private val historyRepository: HistoryRepository? = null`. Existing tests continue to work unchanged. Production DI wires the real repository.
**Warning signs:** Compilation errors in `ConflictPolicyTest.kt`, `ScreenDriverRecoveryTest.kt`, or `ScreenDriverResourceTest.kt`.

### Pitfall 6: Flyway baselineOnMigrate and existing Pi installs

**What goes wrong:** On a Pi that ran Flyway for the first time in Phase 7 (V1 + V2 applied), adding V3 should work automatically. But if the Pi is very old and was baselined at V1 (before Phase 7 added Flyway), V2 may not be in the flyway_schema_history table, causing Flyway to try to apply V2 again and fail with "duplicate column" if the columns already exist.
**Why it happens:** `baselineOnMigrate=true` + `baselineVersion="1"` means fresh installs that already had the schedules table get baselined at V1, so V2 runs normally. The failure case is only if V2 was somehow applied manually.
**How to avoid:** `V2__add_scheduler_columns.sql` uses plain `ALTER TABLE ... ADD COLUMN` without `IF NOT EXISTS`. H2 and PostgreSQL will throw if the column already exists. This is an existing Phase 7 concern, not a Phase 9 concern — `V3__add_history_table.sql` uses `CREATE TABLE IF NOT EXISTS` which is safe.

---

## Code Examples

### Complete findPaginated with filter chaining

```kotlin
// Source: Exposed 1.3.0 DSL — andWhere pattern from ScheduleRepository.findAllActive (codebase)
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

### DI registration (DependencyInjection.kt additions)

```kotlin
// Source: DependencyInjection.kt pattern (codebase) + D-01, D-22
val historyRepository = HistoryRepository()    // after DatabaseFactory.init()
val screenDriverService = ScreenDriverService(
    driver = displaySelectionService.currentDriver() ?: OfflineDisplayDriver,
    ioDispatcher = Dispatchers.IO,
    retryConfig = appConfig.retryConfig,
    displaySelectionService = displaySelectionService,
    metrics = screenDriverMetrics,
    historyRepository = historyRepository,      // new param
)

dependencies {
    // ... existing provides ...
    provide { historyRepository }               // D-22
}
```

### DatabaseFactory.kt addition

```kotlin
// Source: DatabaseFactory.kt (codebase) + D-09
transaction {
    SchemaUtils.create(SchedulesTable)
    SchemaUtils.create(HistoryTable)            // add alongside SchedulesTable
}
```

### HistoryRepository pruning test pattern

```kotlin
// Source: ScheduleRepositoryTest.kt pattern (codebase) + D-24
test("should prune oldest row when count reaches 1000") {
    runTest {
        repeat(1000) { i ->
            repository.insert(HistoryRecord(text = "msg $i", effect = "SCROLL", source = "IMMEDIATE"))
        }
        val (before, total) = repository.findPaginated(1, 1)
        total shouldBe 1000

        repository.insert(HistoryRecord(text = "row 1001", effect = "BLINK", source = "IMMEDIATE"))

        val (_, afterTotal) = repository.findPaginated(1, 1000)
        afterTotal shouldBe 1000
    }
}
```

### Routing.kt additions

```kotlin
// Source: Routing.kt (codebase) + D-22
fun Application.configureRouting() {
    // ... existing ...
    val historyRepository: HistoryRepository by dependencies   // new

    routing {
        // ... existing ...
        historyUIRoutes(historyRepository)                      // new — at top level

        route("/api/v1") {
            // ... existing ...
            historyRoutes(historyRepository)                    // new — inside /api/v1
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Exposed `org.jetbrains.exposed.*` | Exposed `org.jetbrains.exposed.v1.*` | Exposed 1.0.0 (v1 refactor) | All imports use `v1` package prefix — do not use old package names |
| Manual `SchemaUtils.create()` on every boot | Flyway migrations + `SchemaUtils.create()` for safety net | Phase 7 | V3 goes in `db/migration/` not in `DatabaseFactory.init()` only |

**Deprecated/outdated:**
- `org.jetbrains.exposed.sql.*` package: replaced by `org.jetbrains.exposed.v1.core.*`, `org.jetbrains.exposed.v1.jdbc.*`. Any autocomplete suggesting the old packages must be rejected. [VERIFIED: codebase — all existing files use v1 imports]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | No index on `displayed_at` column is sufficient for 1000-row table on Pi 4 | Architecture Patterns (Pitfall note) | ORDER BY displayedAt DESC with 1000 rows may be slow enough to notice on Pi 4 (ARM Cortex-A72, H2 on SD card). Mitigation: add `CREATE INDEX` in V3 if needed. |
| A2 | `EffectRenderer` currently has no `effect: Effect` property — adding it is the cleanest fix for Pitfall 1 | Common Pitfalls | If there is a reason the renderer should not expose its effect (interface boundary), passing `effect` as a separate parameter to `displayScheduled()` is the alternative. Inspect `EffectRenderer.kt` before deciding. |
| A3 | `select.andWhere` chaining in Exposed 1.3.0 works as shown — combining `where{}` then `andWhere{}` | Code Examples | If Exposed 1.3.0 requires a different filter-chaining API, the pattern must be adjusted. The existing `ScheduleRepository.findAllActive()` uses `where { A and B }` with inline `and`; that is also valid and may be simpler. |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed.
(Table is not empty — three low-risk assumptions listed above require implementation-time verification.)

---

## Open Questions

1. **`EffectRenderer` — does it expose the `Effect` enum?**
   - What we know: `displayScheduled()` receives an `EffectRenderer`, not an `Effect`. History record needs the effect name.
   - What's unclear: Whether `EffectRenderer` is an interface or abstract class with an accessible `effect` property.
   - Recommendation: Check `EffectRenderer.kt` and `EffectRendererFactory.kt` at implementation time. If the property is absent, add `abstract val effect: Effect` to the abstract class and implement it in each concrete renderer.

2. **`size=all` + total count: two-query vs. one-query approach**
   - What we know: `findPaginated()` returns `(items, total)`. For `size=all`, we need all rows.
   - What's unclear: Whether calling `findPaginated(1, Int.MAX_VALUE)` with a large LIMIT on H2 causes any issue.
   - Recommendation: Use a separate `findAll()` path when `sizeAll=true`, or clamp to total. Either works with H2 at 1000-row cap.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — all required tools are already in the project: H2, Flyway, Exposed, Ktor are existing Gradle dependencies; no new CLI tools, services, or runtimes required).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (FunSpec + `should` convention) |
| Config file | none — JUnit 5 runner auto-discovers via `kotest-runner-junit5-jvm` |
| Quick run command | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest" -x jacocoTestReport` |
| Full suite command | `./gradlew test jacocoTestReport` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HIST-01 | `displayImmediate()` writes record to `display_history` after success | Integration | `./gradlew test --tests "com.anjo.service.HistoryRecordingTest"` | Wave 0 |
| HIST-01 | `displayScheduled()` writes record with source=SCHEDULED | Integration | `./gradlew test --tests "com.anjo.service.HistoryRecordingTest"` | Wave 0 |
| HIST-01 | SKIP_NEW dropped request writes no record | Integration | `./gradlew test --tests "com.anjo.service.HistoryRecordingTest"` | Wave 0 |
| HIST-01 | DB error in `historyRepository.insert()` does not break display return | Integration | `./gradlew test --tests "com.anjo.service.HistoryRecordingTest"` | Wave 0 |
| HIST-01 | `insert()` persists all columns correctly (round-trip) | Unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | Wave 0 |
| HIST-01 | Pruning: 1001st insert causes table to remain at 1000 rows | Unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | Wave 0 |
| HIST-02 | `GET /api/v1/history` returns 200 with `items`, `page`, `size`, `total` | Integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | Wave 0 |
| HIST-02 | Page 2 returns different items than page 1 | Integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | Wave 0 |
| HIST-02 | `?effect=SCROLL` filter returns only SCROLL records | Integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | Wave 0 |
| HIST-02 | `?source=IMMEDIATE` filter returns only IMMEDIATE records | Integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | Wave 0 |
| HIST-03 | `GET /history` returns 200 HTML containing "Display History" | Integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | Wave 0 |
| HIST-03 | `GET /history?expand=all` renders `<details` with `open` attribute | Integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | Wave 0 |
| HIST-03 | Filter dropdowns for effect and source present in HTML | Integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | Wave 0 |
| HIST-03 | `HistoryRepository` resolves in DI smoke test | Integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | Exists (modify) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest" -x jacocoTestReport`
- **Per wave merge:** `./gradlew test jacocoTestReport`
- **Phase gate:** Full suite green + JaCoCo ≥70% before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` — covers HIST-01 repository layer
- [ ] `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` — covers HIST-01 service integration
- [ ] `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` — covers HIST-02
- [ ] `src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt` — covers HIST-03
- [ ] `src/test/kotlin/com/anjo/ApplicationTest.kt` — add `HistoryRepository` DI assertion (D-23)

---

## Security Domain

> `security_enforcement` not declared in `.planning/config.json` — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in scope (trusted home network per REQUIREMENTS.md out-of-scope) |
| V3 Session Management | no | No session management |
| V4 Access Control | no | No access control (home network scope) |
| V5 Input Validation | yes | Query param sanitization in route handlers (`toIntOrNull()`, `coerceAtLeast(1)`, whitelist filter values) |
| V6 Cryptography | no | No crypto — all data is display log, no PII |

### Known Threat Patterns for Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via `?effect=` or `?source=` filter params | Tampering | Exposed DSL uses parameterized queries — `where { column eq value }` never interpolates strings into SQL. [VERIFIED: codebase — ScheduleRepository uses same pattern] |
| Unbounded history growth (SD card fill on Pi) | Denial of Service | D-05 — 1000-row cap enforced on every insert inside `suspendTransaction{}`. [VERIFIED: D-05 decision] |
| XSS via stored text rendered in HTML | Tampering | kotlinx.html DSL escapes all string content by default when using `+text` (not `unsafe {}`) — so user-supplied text in cards is auto-escaped. [ASSUMED: kotlinx.html v0.12.0 escapes `+` operator output — verify no `unsafe {}` is used in HistoryPage.kt] |
| Integer overflow on `page * size` offset calculation | Tampering | Use `.toLong()` on offset: `((page - 1).toLong() * size)` — avoids Int overflow for large page/size combinations. |

---

## Sources

### Primary (HIGH confidence)
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` — blueprint for HistoryTable pattern [VERIFIED: codebase]
- `src/main/kotlin/com/anjo/db/ScheduleRepository.kt` — blueprint for HistoryRepository + suspendTransaction pattern [VERIFIED: codebase]
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — integration points for history recording [VERIFIED: codebase]
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — DI registration pattern [VERIFIED: codebase]
- `src/main/kotlin/com/anjo/routing/Routing.kt` — wiring pattern for new routes [VERIFIED: codebase]
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — nav link pattern, Pico CSS v2 theme [VERIFIED: codebase]
- `src/main/resources/db/migration/V1__initial_schema.sql` + `V2__add_scheduler_columns.sql` — Flyway migration style [VERIFIED: codebase]
- `gradle/ktor-libs.versions.toml` — exact library versions in use [VERIFIED: codebase]
- `.planning/phases/09-display-history-audit-log/09-CONTEXT.md` — locked decisions D-01 through D-24 [VERIFIED: upstream design doc]
- `.planning/phases/09-display-history-audit-log/09-UI-SPEC.md` — full HTML component spec [VERIFIED: upstream design doc]
- `src/test/kotlin/com/anjo/db/ScheduleRepositoryTest.kt` — H2 in-memory test pattern to replicate [VERIFIED: codebase]
- `src/test/kotlin/com/anjo/ApplicationTest.kt` — DI smoke test pattern [VERIFIED: codebase]

### Secondary (MEDIUM confidence)
- None — all claims sourced from codebase or upstream design documents.

### Tertiary (LOW confidence)
- A1–A3 in Assumptions Log above.

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all libraries verified in `ktor-libs.versions.toml`; no new dependencies
- Architecture: HIGH — directly mirrors existing Exposed + Flyway + Ktor patterns verified in codebase
- Pitfalls: HIGH — derived from reading actual source files and locked decisions; A1-A3 low-risk assumptions noted
- UI Spec: HIGH — 09-UI-SPEC.md provides complete HTML component contract

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 (library versions locked by project; no moving external targets)
