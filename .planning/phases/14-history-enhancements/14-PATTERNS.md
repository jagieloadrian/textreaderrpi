# Phase 14: History Enhancements - Pattern Map

**Mapped:** 2026-06-22
**Files analyzed:** 9 (3 new, 6 modified)
**Analogs found:** 9 / 9

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/anjo/model/HistoryFilter.kt` | model | transform | `src/main/kotlin/com/anjo/model/ZoneStatus.kt` | role-match |
| `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` | utility | transform | `src/main/kotlin/com/anjo/validation/RequestValidators.kt` | exact |
| `src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt` | utility | transform | `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` (FlowContent pattern) | role-match |
| `src/main/kotlin/com/anjo/db/HistoryRepository.kt` | repository | CRUD | self (existing) | exact — extend in place |
| `src/main/kotlin/com/anjo/service/HistoryService.kt` | service | CRUD | self (existing) | exact — extend in place |
| `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` | route | request-response | self (existing) | exact — extend in place |
| `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` | route | request-response | self (existing) | exact — extend in place |
| `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` | component | request-response | self (existing) | exact — extend in place |
| `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt` | test | — | `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` | role-match |
| `src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt` | test | — | `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` | role-match |

---

## Pattern Assignments

### `src/main/kotlin/com/anjo/model/HistoryFilter.kt` (model, transform)

**Analog:** `src/main/kotlin/com/anjo/model/ZoneStatus.kt`

**Imports pattern** (ZoneStatus.kt lines 1-3):
```kotlin
package com.anjo.model

import kotlinx.serialization.Serializable
```

**Core pattern** (ZoneStatus.kt lines 5-13 — plain data class, nullable fields):
```kotlin
@Serializable
data class ZoneStatus(
    val id: String,
    val type: String,
    val status: String,
    val ip: String? = null,
    val lastSeenAt: String? = null,
    val error: String? = null
)
```

**Apply for HistoryFilter:** Do NOT add `@Serializable` — `HistoryFilter` is an internal filter type, never serialized to JSON. Plain `data class` only. Four nullable String fields: `effect`, `source`, `zone`, `search`.

---

### `src/main/kotlin/com/anjo/validation/HistoryValidators.kt` (utility, transform)

**Analog:** `src/main/kotlin/com/anjo/validation/RequestValidators.kt`

**Imports pattern** (RequestValidators.kt lines 1-9):
```kotlin
package com.anjo.validation

import com.anjo.config.model.ApiConfig
import com.anjo.model.AddZoneRequest
import com.anjo.model.DisplaySelectRequest
import com.anjo.model.DisplayType
import com.anjo.model.TextRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
```

**Core pattern — `object` with pure functions** (RequestValidators.kt lines 10-49):
```kotlin
object RequestValidators {
    fun validateTextRequest(req: TextRequest, apiConfig: ApiConfig): ValidationResult {
        if (req.text.isBlank()) {
            return ValidationResult.Invalid("Text cannot be blank")
        }
        ...
        return ValidationResult.Valid
    }
    ...
}
```

**Apply for HistoryValidators:** Same `object` declaration, no imports from Ktor validation. Single function `sanitizeSearchTerm(input: String): String`. No `ValidationResult` — returns a plain `String`. No project rule requires `ValidationResult` for sanitizers (that type is used for request body validation only).

---

### `src/main/kotlin/com/anjo/web/templates/HtmlUtils.kt` (utility, transform)

**Analog:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` — `FlowContent` usage pattern

**Imports pattern** (HistoryPage.kt lines 1-28 — extract only the kotlinx.html imports relevant to the helper):
```kotlin
package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.mark
```

**Core pattern — `FlowContent` extension lambda** (HistoryPage.kt shows the receiver pattern via `fun FlowContent.historyPage(...)`):

The `highlightText` function returns `FlowContent.() -> Unit` (a lambda, not an extension function). The call site in HistoryPage invokes it with `()`. Pattern for emitting text nodes inside a `FlowContent` context:
```kotlin
// From HistoryPage.kt line 117 — the `+` operator on a String inside FlowContent
p { strong { +"Text:" }; +" ${item.text}" }

// mark {} block (same DSL pattern as div {}, span {}, etc.)
mark { +"matched" }
```

**No error handling needed** — pure string manipulation + DSL emission.

---

### `src/main/kotlin/com/anjo/db/HistoryRepository.kt` (repository, CRUD) — MODIFIED

**Analog:** self — existing file, lines 1-91

**Current imports** (lines 1-13) — add `lowerCase` and `like` when needed; both come from `org.jetbrains.exposed.v1.core.*` already imported:
```kotlin
package com.anjo.db

import com.anjo.model.HistoryRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID
```

Add import: `import com.anjo.model.HistoryFilter`

**Current `findPaginated` signature to replace** (lines 43-75) — verbose if/else chain:
```kotlin
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null,
    zone: String? = null
): Pair<List<HistoryRecord>, Long> = suspendTransaction {
    var query = HistoryTable.selectAll()
    if (effect != null) {
        query = query.where { HistoryTable.effect eq effect }
    }
    if (source != null) {
        query = if (effect != null) {
            query.andWhere { HistoryTable.displaySource eq source }
        } else {
            query.where { HistoryTable.displaySource eq source }
        }
    }
    if (zone != null) {
        query = if (effect != null || source != null) {
            query.andWhere { HistoryTable.zoneId eq zone }
        } else {
            query.where { HistoryTable.zoneId eq zone }
        }
    }
    val total = query.count()
    val items = query
        .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
        .limit(size)
        .offset(((page - 1).toLong() * size))
        .map { it.toHistoryRecord() }
    Pair(items, total)
}
```

**Target: accumulator pattern + `HistoryFilter` param** (RESEARCH.md Pattern 1, lines 208-229):
Replace the `if/else` chain with `buildList { ... }` accumulator. Also add `findAll(filter)` for export (same filter logic, omit `.limit().offset()`).

**`toHistoryRecord()` mapping** (lines 77-86) — unchanged, copy as-is:
```kotlin
private fun ResultRow.toHistoryRecord(): HistoryRecord = HistoryRecord(
    id = this[HistoryTable.id],
    text = this[HistoryTable.text],
    effect = this[HistoryTable.effect],
    source = this[HistoryTable.displaySource],
    scheduleId = this[HistoryTable.scheduleId],
    zoneId = this[HistoryTable.zoneId],
    displayedAt = this[HistoryTable.displayedAt],
    webhookStatus = this[HistoryTable.webhookStatus]
)
```

---

### `src/main/kotlin/com/anjo/service/HistoryService.kt` (service, CRUD) — MODIFIED

**Analog:** self — existing file, lines 1-15

**Current pattern** (lines 1-15) — thin pass-through:
```kotlin
package com.anjo.service

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryRecord

class HistoryService(private val repository: HistoryRepository) {

    suspend fun findPaginated(
        page: Int,
        size: Int,
        effect: String? = null,
        source: String? = null,
        zone: String? = null
    ): Pair<List<HistoryRecord>, Long> = repository.findPaginated(page, size, effect, source, zone)
}
```

**Changes:** Update `findPaginated` to accept `(filter: HistoryFilter, page: Int, size: Int)`. Add `suspend fun exportCsv(filter: HistoryFilter): String` that calls `repository.findAll(filter)` and uses `csvWriter().writeAllAsString(...)`. Add imports: `com.anjo.model.HistoryFilter`, `com.github.doyaaaaaken.kotlincsv.dsl.csvWriter`.

---

### `src/main/kotlin/com/anjo/routing/HistoryRoutes.kt` (route, request-response) — MODIFIED

**Analog:** self — existing file, lines 1-18

**Current query-param extraction pattern** (lines 11-14):
```kotlin
val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 20
val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
```

**Current `call.respond` pattern** (line 16):
```kotlin
call.respond(HistoryPageResponse(items, page, size, total))
```

**Changes to apply:**
- Add `zone` param extraction (same `.takeIf { it.isNotEmpty() && it != "ALL" }` pattern as `effect`/`source`)
- Add `search` extraction via `HistoryValidators.sanitizeSearchTerm(...)` then `.takeIf { it.isNotBlank() }`
- Build `HistoryFilter(effect, source, zone, search)` and pass to `historyService.findPaginated(filter, page, size)`
- Add new `get("/history/export") { ... }` block: extract same params → build filter → call `historyService.exportCsv(filter)` → set `Content-Disposition` header → `call.respondText(csv, ContentType.Text.Plain)`

**Export response pattern** (RESEARCH.md Pattern 4):
```kotlin
call.response.headers.append(
    HttpHeaders.ContentDisposition,
    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "history.csv").toString()
)
call.respondText(csv, ContentType.Text.Plain)
```

Add imports: `io.ktor.http.ContentDisposition`, `io.ktor.http.ContentType`, `io.ktor.http.HttpHeaders`, `io.ktor.server.response.respondText`, `com.anjo.model.HistoryFilter`, `com.anjo.validation.HistoryValidators`.

---

### `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` (route, request-response) — MODIFIED

**Analog:** self — existing file, lines 1-33

**Current filter-var extraction pattern** (lines 20-26):
```kotlin
val effect = call.request.queryParameters["effect"].orEmpty()
val source = call.request.queryParameters["source"].orEmpty()
val zone = call.request.queryParameters["zone"].orEmpty()
val expandAll = call.request.queryParameters["expand"] == "all"
val effectFilter = effect.takeIf { it.isNotEmpty() && it != "ALL" }
val sourceFilter = source.takeIf { it.isNotEmpty() && it != "ALL" }
val zoneFilter = zone.takeIf { it.isNotEmpty() && it != "ALL" }
```

**Current service call** (line 27):
```kotlin
val (items, total) = historyService.findPaginated(page, size, effectFilter, sourceFilter, zoneFilter)
```

**Current `historyPage(...)` call** (lines 28-30):
```kotlin
val html = BaseLayout.render(pageTitle = "History — TextReaderRpi", activePath = "/history") {
    historyPage(items, page, rawSize, total, expandAll, effect, source, zone, zoneRegistry.listAll())
}
```

**Changes to apply:**
- Add `search` extraction: `val rawSearch = call.request.queryParameters["search"].orEmpty()`; sanitize via `HistoryValidators`; produce `val searchFilter: String? = ...takeIf { it.isNotBlank() }`
- Build `HistoryFilter(effectFilter, sourceFilter, zoneFilter, searchFilter)` and pass to `historyService.findPaginated(filter, page, size)`
- Build export href as a local `val`: `"/api/v1/history/export?effect=${effect.urlEncode()}&source=${source.urlEncode()}&zone=${zone.urlEncode()}&search=${rawSearch.urlEncode()}"`
- Pass `search = rawSearch` and `exportHref` to `historyPage(...)` (new params on that function)

Add imports: `com.anjo.model.HistoryFilter`, `com.anjo.validation.HistoryValidators`.

---

### `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` (component, request-response) — MODIFIED

**Analog:** self — existing file, lines 1-156

**Existing import block** (lines 1-28) — `urlEncode()` private extension already present (line 29):
```kotlin
private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
```

**Existing form block** (lines 44-101) — single `<form method="get" action="/history">` to extend with search input above first label and export link below submit button.

**Existing text render** (line 117) — current plain text emit:
```kotlin
p { strong { +"Text:" }; +" ${item.text}" }
```
Replace `+" ${item.text}"` with the `highlightText(item.text, search)()` call (invoke the returned lambda).

**Existing pagination href** (line 146) — must add `&search=${search.urlEncode()}`:
```kotlin
a(href = "?page=$p&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}${if (expandAll) "&expand=all" else ""}&zone=${zone.urlEncode()}") {
```

**Function signature to extend** (lines 31-41):
```kotlin
fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String,
    zone: String,
    zones: List<ZoneStatus>
)
```
Add `search: String` and `exportHref: String` parameters. Add import: `import com.anjo.web.templates.highlightText` (same package — no import needed), `import kotlinx.html.mark` (explicit per Pitfall 4).

---

### `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt` (test) — NEW

**Analog:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` (Kotest `FunSpec` style)

**Test file structure** (HistoryRoutesTest.kt lines 1-18):
```kotlin
package com.anjo.routing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HistoryRoutesTest : FunSpec({

    test("...") {
        ...
    }
})
```

**Apply for HistoryValidatorsTest:** Package `com.anjo.validation`. Import `HistoryValidators`. No `testApplication` needed — pure unit tests calling `HistoryValidators.sanitizeSearchTerm(...)` directly. No `runTest` needed — not suspend. Use `shouldBe` assertions.

---

### `src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt` (test) — NEW

**Analog:** `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` (Kotest `FunSpec` style, unit)

**Test file structure** (HistoryRepositoryTest.kt lines 1-17):
```kotlin
package com.anjo.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HistoryRepositoryTest : FunSpec({
    ...
    test("...") { runTest { ... } }
})
```

**Apply for HtmlUtilsTest:** Package `com.anjo.web.templates`. Import `highlightText`. To assert HTML output, render the lambda into a string using `buildString { appendHTML().span { highlightText(text, term)() } }`. Use `shouldContain` and `shouldNotContain` matchers. No `runTest` — `highlightText` is not suspend.

---

## Shared Patterns

### `suspendTransaction {}` — DB operations
**Source:** `src/main/kotlin/com/anjo/db/HistoryRepository.kt` lines 20, 49
**Apply to:** `HistoryRepository.findAll(filter)` (new export-only method)
```kotlin
suspend fun findAll(filter: HistoryFilter): List<HistoryRecord> = suspendTransaction {
    // same accumulator as findPaginated, omit .limit().offset()
}
```

### Query-param extraction — route handlers
**Source:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` lines 20-26
**Apply to:** `HistoryRoutes.kt` (add `zone` and `search`), `HistoryUIRoutes.kt` (add `search`)

The established pattern: read raw string with `.orEmpty()`, apply domain-level filter with `.takeIf { it.isNotEmpty() && it != "ALL" }` for enum params. For `search`, use `HistoryValidators.sanitizeSearchTerm(rawSearch).takeIf { it.isNotBlank() }`.

### `ContentType.Text.Html` response — UI routes
**Source:** `src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt` line 31
**Apply to:** `HistoryRoutes.kt` export endpoint uses `ContentType.Text.Plain` (not `.Html`)
```kotlin
call.respondText(html, ContentType.Text.Html)   // UI routes
call.respondText(csv, ContentType.Text.Plain)    // export endpoint
```

### kotlinx.html DSL — FlowContent blocks
**Source:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` throughout
**Apply to:** `HtmlUtils.kt` (same receiver type `FlowContent`, same `+` operator for text nodes, same `mark {}` block syntax)

### Kotest `FunSpec` + `shouldBe` — unit tests
**Source:** `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` lines 1-18
**Apply to:** `HistoryValidatorsTest.kt`, `HtmlUtilsTest.kt`
No `beforeSpec`/`beforeEach` needed for pure unit tests. For `HtmlUtilsTest`, no DB setup.

---

## No Analog Found

All files have suitable analogs in the codebase. No files require falling back to RESEARCH.md-only patterns.

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/`, `src/test/kotlin/com/anjo/`
**Files scanned:** 11 source files read directly
**Pattern extraction date:** 2026-06-22
