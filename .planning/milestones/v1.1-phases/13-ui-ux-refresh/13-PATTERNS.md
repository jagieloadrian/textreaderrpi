# Phase 13: UI/UX Refresh - Pattern Map

**Mapped:** 2026-06-20
**Files analyzed:** 13 (11 modified, 1 new, 1 deleted)
**Analogs found:** 12 / 13 (custom.css is genuinely new — no CSS file exists yet)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` | layout/template | request-response | self (modify in place) | exact |
| `src/main/kotlin/com/anjo/web/templates/IndexPage.kt` | template | request-response | `IndexPage.kt` self + `SchedulePage.kt` (zone select) | exact |
| `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt` | template | request-response | `HistoryPage.kt` (filter selects, pagination links) | role-match |
| `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt` | template | request-response | self (modify in place) | exact |
| `src/main/kotlin/com/anjo/web/templates/ZonesPage.kt` | template | request-response | self (modify in place) | exact |
| `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` | template | request-response | self (modify in place) | exact |
| `src/main/kotlin/com/anjo/web/templates/SettingsPage.kt` | template | — | — | DELETE |
| `src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt` | route | request-response | self (modify in place) | exact |
| `src/main/resources/static/app.js` | client script | event-driven | self (modify in place) | exact |
| `src/main/resources/static/custom.css` | config/style | — | none — new file | no analog |
| `src/main/kotlin/com/anjo/service/HistoryService.kt` | service | CRUD | self (modify in place) | exact |
| `src/main/kotlin/com/anjo/db/HistoryRepository.kt` | repository | CRUD | self (modify in place) | exact |

---

## Pattern Assignments

### `BaseLayout.kt` — replace top nav with side panel

**Current structure** (`src/main/kotlin/com/anjo/web/templates/BaseLayout.kt`, lines 22–52):
```kotlin
object BaseLayout {
    fun render(pageTitle: String, activePath: String, content: FlowContent.() -> Unit): String {
        return createHTML().html {
            attributes["data-theme"] = "dark"   // REMOVE: theme controlled by prefers-color-scheme
            attributes["lang"] = "en"
            head {
                title(pageTitle)
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
                // ADD: link(rel = "stylesheet", href = "/static/custom.css")
                // ADD: headExtra?.invoke(this)
            }
            body {
                header(classes = "container") {   // REPLACE with <aside> side nav + hamburger button
                    h1 { +"TextReaderRpi" }
                    nav {
                        ul {
                            li { a(href = "/") { attributes["aria-current"] = if (activePath == "/") "page" else ""; +"Home" } }
                            // ... existing nav links ...
                            li { a(href = "/settings/display") { ... } }  // REMOVE this link (D-25)
                        }
                    }
                }
                main(classes = "container") { content() }
                footer(classes = "container") { +"TextReaderRpi" }
                script(src = "/static/app.js") {}
            }
        }
    }
}
```

**Active-link pattern to preserve** (lines 37–43):
```kotlin
attributes["aria-current"] = if (activePath == "/") "page" else ""
```

**New signature with headExtra hook (D-19):**
```kotlin
fun render(
    pageTitle: String,
    activePath: String,
    headExtra: (HEAD.() -> Unit)? = null,
    content: FlowContent.() -> Unit
): String
```
All existing callers (`IndexPage`, `SchedulePage`, `HistoryPage`, `ZonesPage`, `StatusPage`, `ErrorPage`) compile unchanged — `headExtra` defaults to `null`.

**Required new imports:**
```kotlin
import kotlinx.html.aside
import kotlinx.html.button
import kotlinx.html.HEAD
```
Remove `import kotlinx.html.header` (no longer used).

**Side nav links (D-03) — copy the `aria-current` pattern for each:**
- Send Text → `/`
- Schedules → `/schedule`
- History → `/history`
- Zones → `/zones`
- Status → `/status`
(No `/settings/display` — deleted per D-25)

---

### `IndexPage.kt` — add zone selector + effect preview

**Current constructor** (`IndexPage.kt`, line 14): `class IndexPage` with no params.

**New constructor pattern** — copy from how `SchedulePage.kt` receives `List<Schedule>` (analog):
```kotlin
// SchedulePage.kt line 23 — top-level function pattern:
fun FlowContent.schedulePage(schedules: List<Schedule>) { ... }
```
Apply same approach: `IndexPage` constructor receives `zones: List<ZoneInfo>` (or `List<ZoneStatus>` — confirm with ZoneRegistry return type).

**Existing select pattern to copy** (`IndexPage.kt`, lines 44–51):
```kotlin
select {
    id = "effectSelect"
    name = "effect"
    option { value = "SCROLL"; selected = true; +"Scroll" }
    option { value = "BLINK"; +"Blink" }
    option { value = "REVERSE"; +"Reverse" }
    option { value = "FADE"; +"Fade" }
}
```
Add zone `<select>` above this using the same `option { value = zone.id; +zone.id }` pattern. Include an "All zones" option with `value = ""` as the first/selected option. Append `" (OFFLINE)"` suffix to label when `zone.status == "OFFLINE"` (D-specifics).

**Effect preview div (D-11, D-13):** Add after the effect select:
```kotlin
div {
    id = "effectPreview"
    // styled via custom.css: dark bg, monospace, fixed height ~2 lines, overflow hidden
}
```

**Card wrapping (D-30):** Wrap "Send" controls in `article {}` and "Preview" in a second `article {}`.

---

### `SchedulePage.kt` — add zone/webhookUrl columns + zone selector on create form

**Existing column pattern** (`SchedulePage.kt`, lines 85–98):
```kotlin
thead { tr { th { +"ID" }; th { +"Text" }; th { +"Trigger" }; th { +"Effect" }; th { +"Status" }; th { +"Actions" } } }
tbody {
    for (s in schedules) {
        tr {
            td { +s.id.take(8) }
            td { +s.text }
            td { +"${s.triggerType.name}: ${s.triggerValue}" }
            td { +s.effect.name }
            td { +s.status.name }
            td { a { href = "#"; attributes["data-delete-id"] = s.id; +"Delete" } }
        }
    }
}
```
Add `th { +"Zone" }` and `th { +"Webhook URL" }` after "Status". In rows add:
```kotlin
td { +(s.zoneId?.take(8) ?: "—") }
td { +(s.webhookUrl?.take(40) ?: "—") }
```

**Constructor update:** Add `zones: List<ZoneInfo>` param alongside `schedules: List<Schedule>`. Zone selector for create form reuses the same `option { value = zone.id; ... }` pattern as IndexPage.

---

### `HistoryPage.kt` — add zone filter + pagination with filter params

**Existing filter form pattern** (`HistoryPage.kt`, lines 40–82):
```kotlin
form {
    method = FormMethod.get
    action = "/history"
    label { htmlFor = "effect"; +"Effect" }
    select {
        id = "effect"; name = "effect"
        option { value = "ALL"; if (effect.isEmpty() || effect == "ALL") selected = true; +"ALL" }
        // ... option per value ...
    }
    // ... source select ...
    button { type = ButtonType.submit; +"Apply Filters" }
}
```
Add a zone `<select id="zone" name="zone">` populated SSR from `ZoneRegistry.listAll()` (same `option { value = zone.id; ... }` pattern). First option: `value = "ALL"; +"ALL"`.

**Pagination link pattern with filter params** (`HistoryPage.kt`, lines 121–122):
```kotlin
a(href = "?page=$p&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}${if (expandAll) "&expand=all" else ""}") {
```
Extend to include `&zone=${zone.urlEncode()}` in pagination links (D-27). Filter form action stays `/history` with no `?page=` (resets to page 1 on filter submit).

**Function signature extension:**
```kotlin
fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String,
    zone: String,           // ADD
    zones: List<ZoneInfo>   // ADD — for populating zone filter select SSR
)
```

---

### `ZonesPage.kt` — add auto-discovered tint, keep JS patterns unchanged

**Existing article/badge pattern** (`ZonesPage.kt`, lines 43–78):
```kotlin
article {
    p {
        strong { +zone.id }
        val badgeStyle = when (zone.status) {
            "ONLINE" -> "background: var(--pico-primary)"
            "OFFLINE" -> "background: var(--pico-color-red-500, #c0392b); color: white"
            // ...
        }
        mark { attributes["style"] = badgeStyle; +zone.status }
    }
    // ... IP, discoveryMethod, lastSeenAt ...
    button {
        type = ButtonType.button
        attributes["data-zone-id"] = zone.id
        attributes["class"] = "delete-zone-btn"
        // ...
    }
}
```
Add auto-discovered tint (D-28): when `zone.discoveryMethod` is non-null (auto-discovered), add CSS class `.zone-card--discovered` to `<article>`:
```kotlin
article {
    if (zone.discoveryMethod != null) {
        attributes["class"] = "zone-card--discovered"
    }
    // ... rest unchanged ...
}
```
The `.zone-card--discovered` class is defined in `custom.css` using `--md-sys-color-secondary-container`.

**Do NOT change** `delete-zone-btn` data attribute pattern or `addZoneForm` id — `app.js` targets these by id/class (lines 292–295).

---

### `StatusPage.kt` — strip to shell with Loading… spans

**Current pattern** (`StatusPage.kt`, lines 9–34): Constructor with 5 params, renders static values.

**New pattern — no constructor params, render shell only:**
```kotlin
object StatusPage {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Display Status", activePath = "/status") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        // Each section is an <article> card (D-30)
        article {
            h2 { +"System" }
            p { +"Uptime: "; span { id = "status-uptime"; +"Loading…" } }
            p { +"Memory Used: "; span { id = "status-memory-used"; +"Loading…" } }
            p { +"Memory Max: "; span { id = "status-memory-max"; +"Loading…" } }
        }
        article {
            h2 { +"Display" }
            p { +"Status: "; span { id = "status-display"; +"Loading…" } }
            p { +"Total Failures: "; span { id = "status-failures"; +"Loading…" } }
        }
        article {
            h2 { +"Hardware Metrics" }
            p { span { id = "status-hw-metrics"; +"Loading…" } }
        }
    }
}
```
Span IDs are the contract between `StatusPage.kt` and `app.js` — both must agree on the exact `id` values.

---

### `WebRoutes.kt` — inject ZoneRegistry, remove /settings/display

**Current route handler pattern** (`WebRoutes.kt`, lines 12–36):
```kotlin
fun Route.webRoutes(screenDriverService: ScreenDriverService) {
    get("/") {
        call.respondText(IndexPage().render(), ContentType.Text.Html)
    }
    get("/status") {
        val status = screenDriverService.status()
        call.respondText(StatusPage(...).render(), ContentType.Text.Html)
    }
    get("/settings/display") {   // DELETE this block (D-25)
        call.respondText(SettingsPage(...).render(), ContentType.Text.Html)
    }
}
```

**Changes:**
1. Remove `get("/settings/display") { ... }` entirely.
2. Add `ZoneRegistry` parameter: `fun Route.webRoutes(screenDriverService: ScreenDriverService, zoneRegistry: ZoneRegistry)`.
3. Pass `zoneRegistry.listAll()` to `IndexPage(zones = ...)` and `SchedulePage(zones = ...)` constructors.
4. Simplify `/status` to `call.respondText(StatusPage.render(), ContentType.Text.Html)` (no params).

Verify how `ZoneRegistry` is injected at the call site (likely via Koin `get()` or `application.attributes`) before implementing step 2.

---

### `app.js` — hamburger toggle, effect preview, status fetch, schedule zone column

**Existing DOMContentLoaded boot pattern** (`app.js`, lines 269–296):
```javascript
document.addEventListener("DOMContentLoaded", () => {
    const textInput = document.getElementById("textInput");
    const submitBtn = document.getElementById("submitTextBtn");
    if (textInput) {
        textInput.addEventListener("input", updateCounter);
        updateCounter();
    }
    if (submitBtn) submitBtn.addEventListener("click", e => { e.preventDefault(); submitForm(); });
    // ... per-page guards using element existence checks ...
});
```
Add new per-page guards in the same `DOMContentLoaded` block for: hamburger button, effect preview, and `/status` auto-fetch.

**Existing fetch pattern to copy for status auto-refresh** (`app.js`, lines 44–59 — `submitForm()`):
```javascript
try {
    const response = await fetch("/api/v1/text", { method: "POST", headers: {...}, body: ... });
    if (response.ok) {
        showToast("Text sent", "success");
    } else {
        const err = await response.text();
        showToast("Failed to send: " + (err || response.status), "error");
    }
} catch (_) {
    showToast("Network error", "error");
}
```
Copy this try/catch pattern for `async function fetchStatus()` that calls `GET /health/detail` and `GET /metrics`, then sets `document.getElementById("status-uptime").textContent = data.uptime` etc.

**Zone append in `submitForm()` (D-18)** — modify existing URL:
```javascript
// Current (line 45):
const response = await fetch("/api/v1/text", { ... });
// New:
const zoneSelect = document.getElementById("zoneSelect");
const zoneId = zoneSelect ? zoneSelect.value : "";
const url = zoneId ? `/api/v1/text?zone=${encodeURIComponent(zoneId)}` : "/api/v1/text";
const response = await fetch(url, { ... });
```

**renderScheduleList() extension (D-17)** — existing column pattern (`app.js`, lines 103–114):
```javascript
const rows = schedules.map(s => `
    <tr>
        <td>${escHtml(s.id.slice(0, 8))}</td>
        <td>${escHtml(s.text)}</td>
        <td>${escHtml(s.triggerType)}: ${escHtml(s.triggerValue)}</td>
        <td>${escHtml(s.effect)}</td>
        <td>${escHtml(s.status)}</td>
        <td>...</td>
    </tr>`).join("");

container.innerHTML = `
    <table>
        <thead><tr>
            <th>ID</th><th>Text</th><th>Trigger</th><th>Effect</th><th>Status</th><th>Actions</th>
        </tr></thead>
        <tbody>${rows}</tbody>
    </table>`;
```
Add `<th>Zone</th><th>Webhook</th>` to the header and `<td>${escHtml(s.zoneId || "—")}</td><td>${escHtml(s.webhookUrl || "—")}</td>` to each row. Keep all existing event delegation for `[data-stop-id]` and `[data-delete-id]` unchanged.

**createSchedule() extension (D-21)** — existing body (`app.js`, lines 147–157):
```javascript
body: JSON.stringify({ text, triggerType, triggerValue, effect, priority })
```
Extend to include:
```javascript
const zoneId = document.getElementById("zoneSelect")?.value ?? "";
body: JSON.stringify({ text, triggerType, triggerValue, effect, priority, zoneId: zoneId || null })
```

---

### `custom.css` — NEW file, no analog

**Location:** `src/main/resources/static/custom.css`

No existing CSS file to copy from. Structure from RESEARCH.md + CONTEXT.md decisions:

1. `:root` block with `--md-sys-color-*` tokens (dark palette D-07)
2. `@media (prefers-color-scheme: light)` block with light palette overrides
3. PicoCSS variable overrides mapping `--pico-background-color` → `--md-sys-color-surface` etc.
4. Side nav layout rules: `aside` (240px, `position: fixed` desktop), `body.nav-open aside` (mobile overlay), `.nav-backdrop` overlay div
5. `@keyframes scroll-preview`, `blink-preview`, `reverse-preview`, `fade-preview`
6. `.zone-card--discovered { background: var(--md-sys-color-secondary-container); }`
7. Desktop container: `@media (min-width: 960px)` with `max-width: 1200px` centered layout

---

### `HistoryService.kt` — add zone filter param

**Current signature** (`HistoryService.kt`, lines 8–13):
```kotlin
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null
): Pair<List<HistoryRecord>, Long> = repository.findPaginated(page, size, effect, source)
```

**New signature:**
```kotlin
suspend fun findPaginated(
    page: Int,
    size: Int,
    effect: String? = null,
    source: String? = null,
    zone: String? = null   // ADD
): Pair<List<HistoryRecord>, Long> = repository.findPaginated(page, size, effect, source, zone)
```

---

### `HistoryRepository.kt` — add zone andWhere filter

**Existing filter pattern** (`HistoryRepository.kt`, lines 49–66):
```kotlin
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
```

**Zone filter extension — copy the source pattern exactly:**
```kotlin
if (zone != null) {
    query = if (effect != null || source != null) {
        query.andWhere { HistoryTable.zoneId eq zone }
    } else {
        query.where { HistoryTable.zoneId eq zone }
    }
}
```
Add `zone: String? = null` param to `findPaginated()` signature.

---

## Shared Patterns

### kotlinx.html DSL imports
**Source:** All existing template files
**Apply to:** All Kotlin template files (BaseLayout, IndexPage, SchedulePage, HistoryPage, ZonesPage, StatusPage)
```kotlin
import kotlinx.html.FlowContent
import kotlinx.html.article
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.select
// ... add specific tags as needed per file
```
Convention: import only what is used (no wildcard imports in existing files).

### `BaseLayout.render()` call pattern
**Source:** `IndexPage.kt` lines 16–18, `StatusPage.kt` lines 17–18
**Apply to:** All page templates
```kotlin
fun render(): String {
    return BaseLayout.render(pageTitle = "...", activePath = "/...") {
        pageContent()
    }
}
private fun FlowContent.pageContent() { ... }
```

### PicoCSS `<article>` as MD3 card (D-30)
**Source:** `ZonesPage.kt` lines 43–78 — `article { ... }` already used for zone cards
**Apply to:** IndexPage (Send + Preview cards), SchedulePage (Create + List cards), StatusPage (metric group cards)
```kotlin
article {
    h2 { +"Section Title" }
    // ... section content ...
}
```

### JS element-existence guard pattern
**Source:** `app.js` lines 272–295
**Apply to:** All new DOMContentLoaded handlers in app.js
```javascript
const el = document.getElementById("some-id");
if (el) el.addEventListener("click", handler);
```

### JS try/catch fetch pattern
**Source:** `app.js` lines 44–59 (`submitForm`)
**Apply to:** `fetchStatus()`, any new fetch functions
```javascript
try {
    const response = await fetch(url, { method, headers, body });
    if (response.ok) { /* handle success */ }
    else { showToast("...", "error"); }
} catch (_) {
    showToast("Network error", "error");
}
```

### `escHtml()` for JS-rendered table cells
**Source:** `app.js` lines 83–89
**Apply to:** Any new JS-rendered table columns (zone, webhookUrl in renderScheduleList)
```javascript
function escHtml(str) {
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}
```

### Exposed `andWhere` filter chain
**Source:** `HistoryRepository.kt` lines 49–66
**Apply to:** `HistoryRepository.findPaginated()` zone extension
Pattern: check if prior filter already set `where` vs `andWhere` to avoid invalid query state.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/main/resources/static/custom.css` | style/config | — | No CSS file exists in the project; pure new addition |

---

## Notes on Dual-Render Divergence (Critical Risk)

`SchedulePage.kt` (SSR) and `renderScheduleList()` in `app.js` (JS re-render) must receive zone + webhookUrl columns **in the same plan wave**. The SSR table (lines 85–98 of SchedulePage.kt) and the JS template string (lines 103–122 of app.js) must have identical column structure. If a plan splits these across separate tasks, they must be in the same wave with a shared definition of what columns exist.

## Metadata

**Analog search scope:** `src/main/kotlin/com/anjo/web/templates/`, `src/main/kotlin/com/anjo/routing/ui/`, `src/main/kotlin/com/anjo/service/`, `src/main/kotlin/com/anjo/db/`, `src/main/resources/static/`
**Files scanned:** 10
**Pattern extraction date:** 2026-06-20
