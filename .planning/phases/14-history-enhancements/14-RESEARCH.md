# Phase 14: History Enhancements - Research

**Researched:** 2026-06-22
**Domain:** Kotlin/Ktor history search (Exposed LIKE), CSV export (kotlin-csv-jvm), server-side HTML highlighting (kotlinx.html `<mark>`)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Mark Highlight (`<mark>` tags, HIST-06)**
- D-01: Split record text at match boundaries, escape each fragment, emit via Ktor DSL `mark {}` element. No `unsafe {}` / raw HTML injection.
- D-02: Helper `highlightText(text: String, term: String?): FlowContent.() -> Unit` in a new shared file `HtmlUtils.kt` in `com.anjo.web.templates`. Not inlined in `HistoryPage.kt`.
- D-03: Same code path always called: when `term == null` the function emits plain escaped text. Call site does not branch on `term != null`.
- D-04: Case-insensitive exact substring using `String.indexOf(term, ignoreCase = true)`. Consistent with SQL LIKE behavior.
- D-05: Highlight all occurrences in a single text field value (loop until indexOf returns -1).

**CSV Export (HIST-05)**
- D-06: Endpoint `GET /api/v1/history/export` in `HistoryRoutes.kt`. Not a UI route.
- D-07: Export link href built server-side in `HistoryUIRoutes` with current filter params (`effect`, `source`, `zone`, `search`) baked in. No JS.
- D-08: Respect existing 1000-row `MAX_ROWS` DB cap; no additional export-specific limit.
- D-09: Columns: `id`, `text`, `effect`, `source`, `zoneId`, `scheduleId`, `displayedAt`, `webhookStatus`.
- D-10: CSV header row: `id, Text, Effect, Source, Zone ID, Schedule ID, Displayed At, Webhook Status`.
- D-11: Response: `Content-Type: text/csv`, `Content-Disposition: attachment; filename="history.csv"`.
- D-12: `HistoryService.exportCsv(filter: HistoryFilter): String` using `kotlin-csv-jvm` (com.jsoizo 1.10.0). Route calls it and calls `call.respondText(csv, ContentType.Text.Plain)` with the disposition header.

**Search Bar UI (HIST-04, HIST-06)**
- D-13: Full-width search input at top of filter form, above effect/source/zone dropdowns. Single `<form method="get" action="/history">`.
- D-14: "Export CSV" link-styled button alongside the "Filter" submit button at the end of the form.
- D-15: `HistoryUIRoutes` passes `search` param to `historyPage()`; input renders `value={search}`.
- D-16: Placeholder: `"Search displayed text…"`.

**Search Scope & API (HIST-04)**
- D-17: `?search=` added to both `GET /api/v1/history` (JSON) and `GET /history` (HTML). Both pass through `HistoryService.findPaginated(filter, page, size)`.
- D-18: New `com.anjo.validation.HistoryValidators` object with `sanitizeSearchTerm(input: String): String` that strips `%` and `_`. If blank after sanitize, treat as `null`. No 400 for wildcard-only input. Called in route handler before building `HistoryFilter`.
- D-19: `data class HistoryFilter(val effect: String?, val source: String?, val zone: String?, val search: String?)` in `com.anjo.model`. Both `findPaginated` and `exportCsv` accept this type. Replaces 3-nullable-param signature.
- D-20: Exposed LIKE query: `HistoryTable.text.lowerCase() like "%${term.lowercase()}%"` via `andWhere {}`. No raw SQL fragments.

### Claude's Discretion
- Exposed DSL filter chain refactoring — `findPaginated` currently has a verbose if/else chain for building `where`/`andWhere` conditions. Claude may refactor to a cleaner accumulator pattern when introducing `HistoryFilter`, as long as behavior is unchanged.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HIST-04 | User can search display history by text content (`?search=` param, case-insensitive LIKE) | Exposed DSL `lowerCase() like` pattern confirmed; `HistoryFilter` data class design documented |
| HIST-05 | User can export display history to CSV file (`GET /api/v1/history/export`, RFC 4180, Content-Disposition attachment) | kotlin-csv-jvm 1.10.0 confirmed on Maven Central; CSV API pattern documented |
| HIST-06 | Search results highlight matched term with `<mark>` in the history HTML page | kotlinx.html `mark {}` DSL element confirmed available; `highlightText()` algorithm documented |
</phase_requirements>

---

## Summary

Phase 14 extends the existing history layer with three tightly-coupled features: a search filter on `text` column via SQL LIKE, a CSV export endpoint, and server-side `<mark>` highlighting in the HTML page. All features are server-rendered — no JavaScript is introduced. The codebase already has the full layer stack (HistoryRepository → HistoryService → HistoryRoutes + HistoryUIRoutes → HistoryPage), so this phase is purely additive: new parameters, new data class, new endpoint, new helper, and one new dependency.

The primary change load is in `HistoryRepository.findPaginated()` (signature refactor + new LIKE clause) and `HistoryPage.kt` (search input, export link, `highlightText()` call). The new `HistoryFilter` data class acts as the integration point that allows both `findPaginated` and `exportCsv` to share filter state cleanly. The discretion-area accumulator refactor of the `where`/`andWhere` chain is safe and recommended.

The one net-new dependency is `com.jsoizo:kotlin-csv-jvm:1.10.0`. It is not yet in `ktor-libs.versions.toml` and must be added. Version 1.10.0 is the locked choice from STATE.md. The library is pure Kotlin with no transitive dependencies and is on Maven Central.

**Primary recommendation:** Work in four logical slices — (1) `HistoryFilter` + `HistoryValidators` + repository refactor, (2) `HistoryService.exportCsv` + `/api/v1/history/export` endpoint, (3) search param wiring in both routes + `HistoryPage` search input, (4) `HtmlUtils.highlightText` + HistoryPage integration. Each slice is testable independently.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Text search (LIKE query) | Database / Storage | — | SQL LIKE is evaluated at query time by H2/PostgreSQL; search term sanitization is pre-processing in the Validator layer |
| `HistoryFilter` data class | API / Backend (model layer) | — | Shared by repository and service; has no UI concerns |
| Search param sanitization | API / Backend (validation) | — | Rule: validation stays in Validators, never in route handlers |
| CSV generation | API / Backend (service) | — | `HistoryService.exportCsv` owns CSV formatting; route only wires response headers |
| Export endpoint | API / Backend (routing) | — | `GET /api/v1/history/export` is a new API route in `HistoryRoutes.kt` |
| Export link rendering | Frontend Server (SSR) | — | `HistoryUIRoutes` bakes current filter params into `href` server-side |
| Search input / sticky state | Frontend Server (SSR) | — | Ktor HTML DSL renders `value={search}` at request time; no client JS |
| `<mark>` highlight | Frontend Server (SSR) | — | `highlightText()` in `HtmlUtils.kt` runs at HTML render time; all occurrences emitted as DSL nodes |

---

## Standard Stack

### Core (all already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Exposed DSL | 1.3.0 | LIKE query for text search | Already in use for all DB queries; `lowerCase()` and `like` are first-class DSL functions |
| kotlinx.html JVM | 0.12.0 | Server-side HTML generation including `mark {}` element | Already in use for all server-rendered pages |
| Ktor server | 3.5.0 | Route handler wiring, `respondText` with headers | Already in use |

### New Dependency (must be added)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.jsoizo:kotlin-csv-jvm` | 1.10.0 | RFC 4180-compliant CSV generation | Pure Kotlin, zero transitive deps, Maven Central, locked in STATE.md |

**Version verification:** [CITED: central.sonatype.com/artifact/com.jsoizo/kotlin-csv-jvm] — version 1.10.0 confirmed present at `https://repo1.maven.org/maven2/com/jsoizo/kotlin-csv-jvm/1.10.0/`. Current latest on registry is 2.0.0; 1.10.0 is pinned by STATE.md decision — do not upgrade unless explicitly re-decided.

**Installation (gradle):**
```kotlin
// In ktor-libs.versions.toml [versions]
kotlin-csv = "1.10.0"

// In ktor-libs.versions.toml [libraries]
kotlin-csv-jvm = { module = "com.jsoizo:kotlin-csv-jvm", version.ref = "kotlin-csv" }

// In build.gradle.kts dependencies
implementation(ktorLibs.kotlin.csv.jvm)
```

Note: The alias `kotlin-csv-jvm` in the TOML becomes `ktorLibs.kotlin.csv.jvm` in Gradle DSL (dots map to hyphens → camelCase).

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `com.jsoizo:kotlin-csv-jvm` | Maven Central | ~5 yrs | Published on Maven Central central repo; active | [github.com/jsoizo/kotlin-csv](https://github.com/jsoizo/kotlin-csv) | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious SUS:** none

[CITED: central.sonatype.com/artifact/com.jsoizo/kotlin-csv-jvm] — confirmed on Maven Central. GitHub repository active with releases. Apache 2.0 license.

---

## Architecture Patterns

### System Architecture Diagram

```
Browser
  │
  │  GET /history?search=hello&effect=SCROLL&zone=zone-a
  ▼
HistoryUIRoutes (Ktor route)
  │  sanitizeSearchTerm() → HistoryFilter(effect, source, zone, search)
  │  buildExportHref(filter) → /api/v1/history/export?effect=SCROLL&zone=zone-a&search=hello
  ▼
HistoryService.findPaginated(filter, page, size)
  ▼
HistoryRepository.findPaginated(filter, page, size)
  │  Exposed DSL: WHERE effect=? AND zone=? AND LOWER(text) LIKE '%hello%'
  ▼
H2 / PostgreSQL
  │
  ◀─── List<HistoryRecord>, total: Long
  │
HistoryPage.historyPage(items, ..., search="hello")
  │  for each item: highlightText(item.text, "hello") → emits text + <mark>hello</mark> nodes
  ▼
HTML response to Browser

─── Export path ───────────────────────────────────────

Browser clicks "Export CSV" link
  │  GET /api/v1/history/export?effect=SCROLL&zone=zone-a&search=hello
  ▼
HistoryRoutes (Ktor route, /api/v1 prefix)
  │  sanitizeSearchTerm() → HistoryFilter
  ▼
HistoryService.exportCsv(filter): String
  │  HistoryRepository.findAll(filter) → List<HistoryRecord> (all rows, no pagination)
  │  csvWriter().writeAllAsString(header + rows)
  ▼
call.respondText(csv, ContentType.Text.Plain)
  Content-Disposition: attachment; filename="history.csv"
  ▼
Browser download dialog
```

### Recommended Project Structure (additions only)

```
src/main/kotlin/com/anjo/
├── model/
│   └── HistoryFilter.kt          # NEW: data class HistoryFilter(effect, source, zone, search)
├── validation/
│   └── HistoryValidators.kt      # NEW: object HistoryValidators { sanitizeSearchTerm() }
├── web/templates/
│   └── HtmlUtils.kt              # NEW: highlightText(text, term) FlowContent.() -> Unit
├── db/
│   └── HistoryRepository.kt      # MODIFIED: findPaginated(filter, page, size) + findAll(filter)
├── service/
│   └── HistoryService.kt         # MODIFIED: findPaginated(filter, page, size) + exportCsv(filter)
├── routing/
│   └── HistoryRoutes.kt          # MODIFIED: search param + /export endpoint
├── routing/ui/
│   └── HistoryUIRoutes.kt        # MODIFIED: search param + export href
└── web/templates/
    └── HistoryPage.kt            # MODIFIED: search input, export link, highlightText() calls
```

### Pattern 1: HistoryFilter accumulator (Exposed DSL, discretion area)

The current `findPaginated` in `HistoryRepository.kt` uses a verbose if/else chain to decide whether to call `.where {}` or `.andWhere {}`. The safe refactor is an accumulator that builds a list of Op<Boolean> and folds them with `and`.

```kotlin
// Source: [ASSUMED] - accumulator pattern consistent with Exposed DSL Op composition
suspend fun findPaginated(filter: HistoryFilter, page: Int, size: Int): Pair<List<HistoryRecord>, Long> =
    suspendTransaction {
        val conditions = buildList {
            filter.effect?.let { add(HistoryTable.effect eq it) }
            filter.source?.let { add(HistoryTable.displaySource eq it) }
            filter.zone?.let { add(HistoryTable.zoneId eq it) }
            filter.search?.let { term ->
                add(HistoryTable.text.lowerCase() like "%${term.lowercase()}%")
            }
        }
        var query = HistoryTable.selectAll()
        if (conditions.isNotEmpty()) {
            query = query.where { conditions.reduce { acc, op -> acc and op } }
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

### Pattern 2: `highlightText()` helper

```kotlin
// Source: [ASSUMED] - follows D-01 through D-05 from CONTEXT.md
fun highlightText(text: String, term: String?): FlowContent.() -> Unit = {
    if (term.isNullOrEmpty()) {
        +text
    } else {
        var start = 0
        var idx = text.indexOf(term, startIndex = start, ignoreCase = true)
        while (idx >= 0) {
            +text.substring(start, idx)
            mark { +text.substring(idx, idx + term.length) }
            start = idx + term.length
            idx = text.indexOf(term, startIndex = start, ignoreCase = true)
        }
        +text.substring(start)
    }
}
```

Call site in `HistoryPage.kt` replaces `+" ${item.text}"` with `highlightText(item.text, search)()`.

### Pattern 3: CSV generation with kotlin-csv-jvm

```kotlin
// Source: [ASSUMED] - based on kotlin-csv-jvm API; writeAllAsString available since early versions
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter

fun exportCsv(filter: HistoryFilter): String {
    val records: List<HistoryRecord> = runBlocking { repository.findAll(filter) }
    val header = listOf("id", "Text", "Effect", "Source", "Zone ID", "Schedule ID", "Displayed At", "Webhook Status")
    val rows = records.map { r ->
        listOf(r.id, r.text, r.effect, r.source, r.zoneId.orEmpty(), r.scheduleId.orEmpty(), r.displayedAt, r.webhookStatus.orEmpty())
    }
    return csvWriter().writeAllAsString(listOf(header) + rows)
}
```

**Important:** `exportCsv` is a `suspend fun` calling `repository.findAll(filter)` which is itself a `suspend fun` using `suspendTransaction`. The route handler calls `exportCsv` from within a coroutine context (`call.respond` block), so no `runBlocking` is needed — the above snippet shows the intent but the actual implementation should use `suspend` throughout.

### Pattern 4: Export endpoint response headers

```kotlin
// Source: [ASSUMED] - standard Ktor respondText with header
get("/history/export") {
    val filter = buildFilter(call)
    val csv = historyService.exportCsv(filter)
    call.response.headers.append(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "history.csv").toString()
    )
    call.respondText(csv, ContentType.Text.Plain)
}
```

`ContentType.Text.Plain` is used because `ContentType.Text.CSV` is not available in Ktor's ContentType sealed class. The browser download is driven by the `Content-Disposition: attachment` header, not the content type.

### Pattern 5: HistoryValidators (mirrors RequestValidators pattern)

```kotlin
// Source: [ASSUMED] - mirrors existing RequestValidators object structure
object HistoryValidators {
    fun sanitizeSearchTerm(input: String): String =
        input.replace("%", "").replace("_", "")
}
```

Route usage:
```kotlin
val rawSearch = call.request.queryParameters["search"].orEmpty()
val search = HistoryValidators.sanitizeSearchTerm(rawSearch).takeIf { it.isNotBlank() }
val filter = HistoryFilter(effect = effectFilter, source = sourceFilter, zone = zoneFilter, search = search)
```

### Anti-Patterns to Avoid

- **Raw string interpolation in LIKE:** Do not use `"SELECT * FROM ... WHERE text LIKE '%$term%'"`. Use Exposed DSL `HistoryTable.text.lowerCase() like "%${term.lowercase()}%"` which goes through the prepared statement mechanism. [ASSUMED]
- **`unsafe {}` for `<mark>` injection:** Do not `unsafe { +"<mark>$matched</mark>" }`. The `mark {}` DSL element is available in kotlinx.html 0.12.0 and properly escapes inner content via `+` operator. [ASSUMED]
- **Wildcard stripping in route handler:** Per project rule (STATE.md §Key Pitfalls, PROJECT.md §Key Decisions), validation/sanitization belongs in `HistoryValidators`, not in route handler bodies. [CITED: project STATE.md]
- **Pagination params in export href:** Export fetches all matching rows (capped at MAX_ROWS=1000 by insert guard). Do not include `page`/`size` in the export URL. [CITED: CONTEXT.md D-08]
- **KDoc stubs from IDE Extract Method:** If IDE is used to extract `highlightText()`, delete any generated `/** ... */` stubs — violates the no-comments rule. [CITED: STATE.md §Key Pitfalls]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| RFC 4180 CSV escaping (commas, quotes, newlines in field values) | Custom string concatenation | `com.jsoizo:kotlin-csv-jvm` | RFC 4180 requires quoting fields containing commas/double-quotes/newlines; double-quote escaping is `""` not `\"`; hand-rolled CSV breaks on any history text containing a comma |
| Case-insensitive SQL search | Kotlin-side `.filter { it.text.lowercase().contains(term) }` after fetching all rows | Exposed DSL LIKE with `lowerCase()` | Fetching 1000 rows to filter in Kotlin wastes memory and bypasses DB index; LIKE on lowercase column is standard |
| HTML escaping inside mark insertion | Manual string replace of `<`, `>`, `&` | kotlinx.html `+` operator inside `mark {}` | The `+` operator in kotlinx.html calls `HtmlEscapeContext.escape()` automatically |

**Key insight:** The kotlin-csv-jvm library handles the exact edge cases that manual CSV concatenation misses — embedded commas, double-quote escaping per RFC 4180, and CRLF line endings. History `text` field values are user-supplied strings and will contain commas.

---

## Common Pitfalls

### Pitfall 1: CSV Content-Disposition header omitted
**What goes wrong:** Browser renders CSV inline as text/plain page instead of triggering download.
**Why it happens:** `ContentType.Text.Plain` alone does not signal download intent; `Content-Disposition: attachment` is required.
**How to avoid:** Set the header before calling `call.respondText`. Use Ktor's `ContentDisposition.Attachment.withParameter(...)` builder, not a raw string.
**Warning signs:** During manual testing, clicking "Export CSV" shows raw text in browser tab instead of opening a file save dialog.

### Pitfall 2: LIKE wildcard injection via unsanitized `%` and `_`
**What goes wrong:** A search term like `%` matches every row (equivalent to no filter). `_` matches any single character. These are SQL LIKE special characters.
**Why it happens:** Exposed's `like` operator interpolates the string literally into the prepared statement value position — `%` inside the value is still a LIKE wildcard.
**How to avoid:** `HistoryValidators.sanitizeSearchTerm` strips `%` and `_` before the filter is built. This is an explicit project decision (STATE.md §Key Pitfalls item 3).
**Warning signs:** `?search=%` returns all rows; `?search=_ello` matches "hello", "bello", etc.

### Pitfall 3: `findAll` for export not added to HistoryRepository
**What goes wrong:** `exportCsv` calls a method that doesn't exist or reuses paginated method with size=MAX_INT.
**Why it happens:** Forgetting that export needs all matching rows without the `LIMIT`/`OFFSET` pagination applied.
**How to avoid:** Add `suspend fun findAll(filter: HistoryFilter): List<HistoryRecord>` to `HistoryRepository` — same filter logic as `findPaginated` minus the `.limit().offset()` chaining. The DB-level cap comes from the INSERT guard (max 1000 rows total), not from a query limit.
**Warning signs:** Export CSV contains only 20 rows (the default page size) or causes OOM from a theoretical unbounded query.

### Pitfall 4: `mark {}` import not resolved
**What goes wrong:** Compilation error: `Unresolved reference: mark`.
**Why it happens:** The `mark` DSL element is in `kotlinx.html` but is not always in the default import set of `FlowContent` extension functions. It may need an explicit import.
**How to avoid:** Add `import kotlinx.html.mark` explicitly in `HtmlUtils.kt`.
**Warning signs:** Red underline on `mark { }` call in IDE; `Unresolved reference: mark` at compile.

### Pitfall 5: Export link in `HistoryUIRoutes` missing `search` param
**What goes wrong:** Export does not filter by the current search term — user searched for "hello", clicks Export, gets all 1000 rows.
**Why it happens:** Easy to forget to add `&search=${search.urlEncode()}` to the export href template when building the link server-side.
**How to avoid:** Build the export href from the same filter variables in scope: `effect`, `source`, `zone`, `search`. Exclude only `page` and `size`.
**Warning signs:** CSV export row count does not match the filtered HTML page count.

### Pitfall 6: Pagination links lose `search` param
**What goes wrong:** User searches for "hello", navigates to page 2, search is cleared.
**Why it happens:** The existing pagination `<a href="?page=$p&effect=...&source=...&size=...&zone=...">` template does not include `search`.
**How to avoid:** Add `&search=${search.urlEncode()}` to every pagination link in `HistoryPage.kt` (the `nav { ul { for (p in start..end) { li { a(href=...) } } } }` block).
**Warning signs:** Clicking page 2 on filtered results shows all history, search input is empty.

### Pitfall 7: `HistoryFilter` placed in wrong package
**What goes wrong:** Import issues between `db`, `service`, and `routing` layers.
**Why it happens:** If `HistoryFilter` is placed in `com.anjo.db` it creates an upward dependency from service to db package; if in `routing` it creates a downward dependency.
**How to avoid:** Place in `com.anjo.model` alongside `HistoryRecord` — both db and service layers already import from `model`. [CITED: CONTEXT.md §Specifics]

---

## Code Examples

### Exposed DSL `lowerCase()` and `like`

```kotlin
// Source: [CITED: jetbrains.com/help/exposed/sql-functions.html]
// lowerCase() is a standard Exposed string function; like is an infix operator on Column<String>
HistoryTable.text.lowerCase() like "%${term.lowercase()}%"
```

This generates `LOWER(text) LIKE ?` with `%term%` bound as the parameter value. Wildcard `%` chars inside the bound value are still treated as LIKE wildcards by the DB — hence the need for sanitization before this call.

### kotlin-csv-jvm write to String

```kotlin
// Source: [ASSUMED] - writeAllAsString is the primary in-memory write API
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter

val rows: List<List<String>> = listOf(
    listOf("id", "Text", "Effect"),  // header
    listOf("abc-123", "Hello, world", "SCROLL")
)
val csv: String = csvWriter().writeAllAsString(rows)
// Result: "id,Text,Effect\r\nabc-123,\"Hello, world\",SCROLL\r\n"
```

The library automatically double-quotes fields containing commas, double quotes, or newlines per RFC 4180.

### kotlinx.html `mark {}` element

```kotlin
// Source: [ASSUMED] - mark{} is part of kotlinx.html flow content DSL
import kotlinx.html.mark

div {
    +"Text before "
    mark { +"matched term" }
    +" text after"
}
// Renders: Text before <mark>matched term</mark> text after
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 3 nullable params in `findPaginated(effect, source, zone)` | `HistoryFilter` data class | Phase 14 | Cleaner call sites, enables 4th `search` param without further combinatorial if/else growth |
| No text search | Exposed LIKE with `lowerCase()` | Phase 14 | Case-insensitive substring search without PostgreSQL full-text index (deferred to v1.3+) |
| History page shows raw text | `highlightText()` wraps matches in `<mark>` | Phase 14 | Visual feedback for search results; server-side, no JS |

**Deprecated/outdated:**
- The old `findPaginated(page, size, effect, source, zone)` 5-param signature: replaced by `findPaginated(filter: HistoryFilter, page: Int, size: Int)`. All callers must be updated (currently: `HistoryService`, both route handlers, and all tests that call the repository or service directly).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `kotlinx.html` 0.12.0 includes `mark {}` as a flow content DSL element | Code Examples / Pitfall 4 | Compile error; workaround is `unsafe { +"<mark>${escaped}</mark>" }` but that violates D-01 |
| A2 | `csvWriter().writeAllAsString(rows)` is available in kotlin-csv-jvm 1.10.0 (vs only `writeAll(rows, stream)`) | Code Examples | If absent, use `ByteArrayOutputStream` + `writeAll` + `toString(Charsets.UTF_8.name())` |
| A3 | `com.github.doyaaaaaken.kotlincsv.dsl.csvWriter` is the correct import path in 1.10.0 | Standard Stack | Import error; check jar contents after adding dependency |
| A4 | `ContentType.Text.CSV` is absent from Ktor; `ContentType.Text.Plain` is the correct fallback | Pattern 4 | If Ktor 3.5.0 added CSV content type, using Plain is still functionally correct |
| A5 | Accumulator `conditions.reduce { acc, op -> acc and op }` compiles with Exposed 1.3.0's `Op<Boolean>` | Pattern 1 | Compilation error; fallback is retaining the original if/else chain and just adding a 4th if-branch for `search` |

---

## Open Questions

1. **`mark {}` import path in kotlinx.html 0.12.0**
   - What we know: `mark` is a standard HTML5 element; kotlinx.html generates DSL functions for all standard elements.
   - What's unclear: Whether the import is `kotlinx.html.mark` or if it is auto-imported via `FlowContent` context.
   - Recommendation: Add explicit `import kotlinx.html.mark` — if redundant, the IDE will flag it; if missing, the build will fail without it.

2. **`writeAllAsString` vs `writeAll` API surface in 1.10.0**
   - What we know: `writeAllAsString` is documented in README; the JVM artifact wraps the common module.
   - What's unclear: Exact method signatures in 1.10.0 vs 2.0.0 (the locked version is 1.10.0).
   - Recommendation: After adding the dependency, inspect the jar or IDE completion before writing the service method.

---

## Environment Availability

Step 2.6: SKIPPED for hardware/service dependencies. The only external dependency change is adding `com.jsoizo:kotlin-csv-jvm:1.10.0` to the Gradle catalog — this is resolved from Maven Central at build time. No new runtime tools, services, or CLIs are required.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotest 6.1.11 (`FunSpec` style) + JUnit 5 platform |
| Config file | none (configured via `tasks.test { useJUnitPlatform() }` in `build.gradle.kts`) |
| Quick run command | `./gradlew test --tests "com.anjo.*History*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HIST-04 | `GET /api/v1/history?search=hello` returns only records with "hello" in text | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ (needs new test cases) |
| HIST-04 | `GET /history?search=hello` HTML page is filtered | integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | ✅ (needs new test cases) |
| HIST-04 | Search is case-insensitive (uppercase term matches lowercase text) | unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | ✅ (needs new test case) |
| HIST-04 | Sanitizer strips `%` and `_` from input | unit | `./gradlew test --tests "com.anjo.validation.HistoryValidatorsTest"` | ❌ Wave 0 |
| HIST-04 | Blank-after-sanitize input treated as null (no filter) | unit | `./gradlew test --tests "com.anjo.validation.HistoryValidatorsTest"` | ❌ Wave 0 |
| HIST-05 | `GET /api/v1/history/export` returns CSV with Content-Disposition header | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ (needs new test case) |
| HIST-05 | Export CSV respects filter params (search, effect, zone) | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ (needs new test case) |
| HIST-05 | Export CSV header row matches D-10 exactly | unit | `./gradlew test --tests "com.anjo.service.HistoryServiceTest"` | ✅ (needs new test case) |
| HIST-05 | CSV escapes fields containing commas | unit | `./gradlew test --tests "com.anjo.service.HistoryServiceTest"` | ✅ (needs new test case) |
| HIST-06 | `<mark>` wraps all occurrences of search term in text | unit | `./gradlew test --tests "com.anjo.web.templates.HtmlUtilsTest"` | ❌ Wave 0 |
| HIST-06 | No `<mark>` emitted when term is null | unit | `./gradlew test --tests "com.anjo.web.templates.HtmlUtilsTest"` | ❌ Wave 0 |
| HIST-06 | HTML page contains `<mark>` when `?search=` is set | integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | ✅ (needs new test case) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.anjo.*History*"`
- **Per wave merge:** `./gradlew test` (full suite, JaCoCo ≥70% gate)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt` — covers HIST-04 sanitizer (strip `%`/`_`, blank→null)
- [ ] `src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt` — covers HIST-06 `highlightText()` (all occurrences, null term no-op, case-insensitive match, HTML entities safe)

*(Existing test files for HistoryRepository, HistoryService, HistoryRoutes, HistoryUIRoutes already exist and will be extended with new test cases — no new file creation needed for those.)*

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Not applicable — no auth in scope (STATE.md: "User authentication/authorization: Out of Scope") |
| V3 Session Management | no | Not applicable |
| V4 Access Control | no | Not applicable |
| V5 Input Validation | yes | `HistoryValidators.sanitizeSearchTerm` strips SQL LIKE wildcards (`%`, `_`) before query |
| V6 Cryptography | no | Not applicable |

### Known Threat Patterns for Ktor/Exposed Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL LIKE wildcard injection (`%`, `_` in search term) | Tampering | Strip in `HistoryValidators.sanitizeSearchTerm()` before building filter; Exposed `like` uses prepared statement but `%` inside the value is still a wildcard |
| HTML injection via text field in `<mark>` context | Spoofing / XSS | kotlinx.html `+` operator auto-escapes — never use `unsafe {}` or string interpolation in DSL |
| CSV injection (formula injection: `=CMD()` in first char of field) | Elevation of privilege | Out of scope — project targets home-network internal tool with no auth; acknowledged in REQUIREMENTS.md "Out of Scope" |
| Content-Disposition missing → inline render | Information Disclosure | Always set `Content-Disposition: attachment` before `respondText` on export endpoint |

---

## Sources

### Primary (HIGH confidence)
- [CITED: jetbrains.com/help/exposed/sql-functions.html] — `lowerCase()` confirmed as standard Exposed string function
- [CITED: central.sonatype.com/artifact/com.jsoizo/kotlin-csv-jvm] — `kotlin-csv-jvm` 1.10.0 confirmed on Maven Central
- [CITED: github.com/jsoizo/kotlin-csv] — GitHub repo confirms library is active, Apache 2.0, pure Kotlin
- [CITED: project CONTEXT.md] — All decisions D-01 through D-20 are locked user decisions
- [CITED: project STATE.md] — Pitfalls, key decisions, Exposed 1.3.0 API notes
- Codebase direct reads: `HistoryRepository.kt`, `HistoryService.kt`, `HistoryRoutes.kt`, `HistoryUIRoutes.kt`, `HistoryPage.kt`, `RequestValidators.kt`, `ScheduleValidators.kt`, `HistoryTable.kt`, `HistoryRecord.kt`, `Routing.kt`, `ktor-libs.versions.toml`, `build.gradle.kts`

### Secondary (MEDIUM confidence)
- [CITED: mvnrepository.com/artifact/com.jsoizo/kotlin-csv-jvm/1.10.0] — version 1.10.0 build details

### Tertiary (LOW confidence)
- Code patterns for `highlightText()`, `csvWriter().writeAllAsString()`, accumulator LIKE pattern — [ASSUMED] from training knowledge, not verified via Context7 or official docs in this session

---

## Metadata

**Confidence breakdown:**
- Standard stack (Exposed, kotlinx.html, kotlin-csv-jvm): HIGH — all confirmed via registry/docs
- Architecture/decisions: HIGH — locked in CONTEXT.md, confirmed against codebase
- Code patterns (highlightText, csvWriter, accumulator): LOW/MEDIUM — reasonable but not API-doc verified for exact method signatures
- Pitfalls: HIGH — derived from CONTEXT.md decisions + existing STATE.md pitfall list + codebase inspection

**Research date:** 2026-06-22
**Valid until:** 2026-07-22 (Ktor/Exposed are stable tracks; kotlin-csv 1.10.0 is pinned)
