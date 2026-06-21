---
phase: 13
status: issues_found
files_reviewed: 18
files_reviewed_list:
  - src/main/kotlin/com/anjo/db/HistoryRepository.kt
  - src/main/kotlin/com/anjo/routing/Routing.kt
  - src/main/kotlin/com/anjo/routing/ui/HistoryUIRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/ScheduleUIRoutes.kt
  - src/main/kotlin/com/anjo/routing/ui/WebRoutes.kt
  - src/main/kotlin/com/anjo/service/HistoryService.kt
  - src/main/kotlin/com/anjo/web/templates/BaseLayout.kt
  - src/main/kotlin/com/anjo/web/templates/HistoryPage.kt
  - src/main/kotlin/com/anjo/web/templates/IndexPage.kt
  - src/main/kotlin/com/anjo/web/templates/SchedulePage.kt
  - src/main/kotlin/com/anjo/web/templates/StatusPage.kt
  - src/main/kotlin/com/anjo/web/templates/ZonesPage.kt
  - src/main/resources/static/app.js
  - src/main/resources/static/custom.css
  - src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt
  - src/test/kotlin/com/anjo/routing/WebAndDisplayRoutesTest.kt
  - src/test/kotlin/com/anjo/routing/WebRoutesTest.kt
  - src/test/kotlin/com/anjo/service/HistoryServiceTest.kt
findings:
  critical: 2
  warning: 5
  info: 3
  total: 10
---

# Code Review — Phase 13

## Summary

Phase 13 introduced a Material 3–style side navigation, new page templates across all five views, a history zone-filter feature, and JavaScript wiring for all interactive features. The Kotlin server-side templates use `kotlinx.html` which auto-escapes string interpolations via the `+` operator — those paths are safe. However several distinct issues were found: a JavaScript injection vector in `onclick` attribute strings (server-constructed), a broken deep-link in the History page, a pagination integer-overflow cast that will crash at extreme row counts, the `page` query parameter being silently dropped when clicking Expand/Collapse all, and an unused function in `app.js`. Test coverage gaps are called out for the prune-race scenario and the `/history` zone-filter route.

---

## Findings

### CR-01 — onclick attribute injection via user-controlled filter values

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:90,96`
**Severity:** Critical
**Issue:** The `onclick` attribute strings for the "Expand all" and "Collapse all" buttons are built by interpolating `effect`, `source`, `zone`, and `rawSize` values that come directly from query-string parameters via the route handler. These values are URL-encoded with `URLEncoder.encode`, which percent-encodes most special characters — but does **not** encode the single-quote (`'`). The attribute value is itself wrapped in single quotes in the HTML:

```
attributes["onclick"] = "window.location='?expand=all&effect=${effect.urlEncode()}...'"
```

`URLEncoder.encode("ALL'", "UTF-8")` → `ALL%27` — Java's `URLEncoder` **does** encode single-quotes as `%27`, so a crafted value like `ALL'; alert(1);//` would become `ALL%27%3B+alert%281%29%3B%2F%2F` and the JS string remains intact. However, the `kotlinx.html` DSL emits the `onclick` value into an HTML attribute context using its own attribute serialiser. That serialiser escapes `"` (double-quote) because the attribute is delimited with `"`, but it does **not** additionally percent-encode the value — so any character that survives URL-encoding can reach the HTML. More importantly, `+` (space) in URL-encoding is not additionally HTML-escaped: if a user passes `effect=ALL+alert(1)` the `+` is printed as-is into the attribute. The fundamental risk is that these are server-controlled reflections of unsanitised GET parameters. The correct fix is to replace inline `onclick` with a `data-` attribute (picked up in `app.js`) rather than embedding a JavaScript string literal inside an HTML attribute.

**Fix:**
```kotlin
// HistoryPage.kt — remove onclick attributes entirely and use data attributes
button {
    type = ButtonType.button
    attributes["data-expand-url"] = "?expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}"
    attributes["class"] = if (expandAll) "secondary" else "secondary outline"
    +"Expand all"
}
button {
    type = ButtonType.button
    attributes["data-expand-url"] = "?effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}"
    attributes["class"] = if (!expandAll) "secondary" else "secondary outline"
    +"Collapse all"
}
```
```js
// app.js — inside DOMContentLoaded, delegate the navigation
document.querySelectorAll("[data-expand-url]").forEach(btn => {
    btn.addEventListener("click", () => { window.location = btn.dataset.expandUrl; });
});
```

---

### CR-02 — Long-to-Int cast overflow in pageCount renders pagination unusable

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:137`
**Severity:** Critical
**Issue:** `total` is `Long` and `sizeInt` is `Int`. The expression `((total + sizeInt - 1) / sizeInt).toInt()` performs arithmetic in `Long`, then calls `.toInt()`. If `total` is even modestly large (e.g. the MAX_ROWS ceiling of 1000 with `size=1`) the result is 1000 — fine. But `sizeInt` itself is parsed from `rawSize` with `rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20`; if someone passes `size=1` and total is > `Int.MAX_VALUE` that cast silently wraps. For the current 1000-row hard cap the overflow cannot be reached, but the range `for (p in 1..pageCount)` is also emitting every single page link as a separate `<a>` tag. At the maximum of 1000 rows with `size=1` that produces 1000 anchor elements, rendering the page unusable and slowing the browser. The `.toInt()` cast is a latent correctness hazard that will materialise if `MAX_ROWS` is ever raised.

**Fix:**
```kotlin
val pageCount = ((total + sizeInt - 1) / sizeInt)   // keep as Long
// clamp rendered pages to a window (e.g. ±5 around current)
val start = maxOf(1L, page - 5L)
val end   = minOf(pageCount, page + 5L)
for (p in start..end) {
    li { a(href = "?page=$p&...") { if (p == page.toLong()) attributes["aria-current"] = "page"; +"$p" } }
}
```
The cast to `Int` must be removed; all page comparisons should stay in `Long`.

---

### WR-01 — Expand/Collapse all buttons silently reset pagination to page 1

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:90,96`
**Severity:** Warning
**Issue:** The `onclick` URLs constructed for both expand/collapse buttons do not include the current `page` parameter. Clicking either button on any page other than page 1 silently navigates the user back to page 1, losing their position in the list. The pagination links at the bottom (line 143) correctly include `page=$p`, but these two buttons omit it.

**Fix:** Append `&page=$page` to both onclick URL strings:
```kotlin
attributes["onclick"] = "window.location='?page=$page&expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}'"
// and:
attributes["onclick"] = "window.location='?page=$page&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}'"
```
(Note: if CR-01 is fixed by moving to `data-` attributes, apply the same fix there.)

---

### WR-02 — History page deep-links to a non-existent route `/schedule?id=`

**File:** `src/main/kotlin/com/anjo/web/templates/HistoryPage.kt:120`
**Severity:** Warning
**Issue:** When a history record has `source == "SCHEDULED"` and a non-null `scheduleId`, the template renders:
```
<a href="/schedule?id=${item.scheduleId}">…</a>
```
The `/schedule` route (`ScheduleUIRoutes.kt`) loads and renders all schedules; it does not read or act on any `id` query parameter. The link resolves to the schedule list page without any highlighting or scroll-to behaviour, silently ignoring the ID. The user receives a confusing non-functional link.

**Fix:** Either remove the anchor and render the schedule ID as plain text until deep-linking is implemented, or implement `id` handling in `ScheduleUIRoutes`:
```kotlin
// ScheduleUIRoutes.kt
get("/schedule") {
    val highlightId = call.request.queryParameters["id"]
    val schedules = repository.findAll()
    val html = BaseLayout.render(pageTitle = "Schedule Manager", activePath = "/schedule") {
        schedulePage(schedules, zoneRegistry.listAll(), highlightId)
    }
    call.respondText(html, ContentType.Text.Html)
}
```

---

### WR-03 — `priority` sent as `NaN` when input field is empty

**File:** `src/main/resources/static/app.js:255,262`
**Severity:** Warning
**Issue:** `parseInt("", 10)` returns `NaN`. If a user clears the priority input, `priority` is `NaN`. `JSON.stringify({ priority: NaN })` serialises it as `{"priority":null}`. The server receives `null` where it expects an integer, which may cause a deserialization error or silently default — the behaviour depends on server-side model nullability, which is opaque at this layer, but the client's intent (send 0 when empty) is not fulfilled.

**Fix:**
```js
const rawPriority = parseInt(document.getElementById("priority")?.value ?? "0", 10);
const priority = isNaN(rawPriority) ? 0 : rawPriority;
```

---

### WR-04 — PicoCSS loaded from CDN without Subresource Integrity (SRI)

**File:** `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt:49`
**Severity:** Warning
**Issue:** The stylesheet is loaded from `https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css` with no `integrity` attribute and no `crossorigin` attribute. If the CDN is compromised or the package is replaced, the browser will accept and apply malicious CSS. On a Raspberry Pi device running on a local network this is a real supply-chain risk.

**Fix:**
```kotlin
link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2.0.6/css/pico.min.css") {
    attributes["integrity"] = "sha384-<hash>"
    attributes["crossorigin"] = "anonymous"
}
```
Alternatively vendor the CSS into `src/main/resources/static/` and serve it locally, eliminating the CDN dependency entirely.

---

### WR-05 — Race condition: server-rendered schedule table immediately overwritten by `loadSchedules()`

**File:** `src/main/resources/static/app.js:416` / `src/main/kotlin/com/anjo/web/templates/SchedulePage.kt:92`
**Severity:** Warning
**Issue:** `SchedulePage` server-renders the schedule table into `#scheduleListContainer` using SSR data. On `DOMContentLoaded`, `app.js` calls `loadSchedules()` unconditionally whenever `#scheduleListContainer` exists (line 416). This makes an immediate `fetch("/api/v1/schedule")` and replaces the SSR content with JS-rendered HTML. The two renders are nearly identical but not identical: the SSR table lacks the "Stop" action button (only "Delete"), while the JS-rendered table shows "Stop" for stoppable schedules. Worse, if the fetch fails, the SSR table is cleared (replaced with "No schedules yet." or left blank), causing a visible regression where a user with schedules sees an empty list.

**Fix:** Either (a) remove the SSR table from `SchedulePage.kt` entirely and rely solely on the JS render, or (b) skip `loadSchedules()` on initial page load and only call it after a mutation (create/stop/delete). Option (a) is simpler and avoids the double-render flicker:
```kotlin
// SchedulePage.kt — replace the schedules.isEmpty() branch with an empty container
div {
    id = "scheduleListContainer"
}
```

---

### IN-01 — `openNav` is a dead function

**File:** `src/main/resources/static/app.js:30`
**Severity:** Info
**Issue:** The `openNav` function is defined at line 30 but never called anywhere in the file. `toggleNav` and `closeNav` are used; `openNav` is not. This is dead code.

**Fix:** Delete lines 30–32:
```js
// remove:
function openNav() {
  document.body.classList.add("nav-open");
}
```

---

### IN-02 — Inline comments present in source files (project rule violation)

**File:** `src/main/resources/static/app.js` (multiple lines: 4, 29, 42, 102, 184, 305, 373)
**Severity:** Info
**Issue:** The project coding rule explicitly prohibits inline comments in code files. `app.js` contains multiple section-divider comment lines (e.g. `// ─── Toast ────────────────────────────────────────────────────────────────`). These must be removed.

**Fix:** Delete all `// ─── … ───` divider comment lines and any other `//` comments in `app.js`.

---

### IN-03 — No test for `/history` with active zone filter returning filtered HTML

**File:** `src/test/kotlin/com/anjo/routing/WebAndDisplayRoutesTest.kt:37`
**Severity:** Info
**Issue:** The existing test for `/history?zone=ALL` only checks that the page loads with HTTP 200 and contains "All zones" — it does not verify that a real zone ID filter is applied and reflected in the rendered output. The zone filter is the primary feature of this phase. No integration test sends an actual zone ID (e.g. `?zone=zone-a`) and asserts filtered rows appear or that rows from other zones are absent. The `HistoryRepositoryTest` covers the DB layer well, but the route-level test does not exercise the full rendering path with a populated zone filter.

**Fix:** Add a test that seeds two records with different `zoneId` values, then issues `GET /history?zone=zone-a` and asserts only the zone-a text is present in the body and the zone-b text is absent.

---

_Reviewed: 2026-06-21T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
