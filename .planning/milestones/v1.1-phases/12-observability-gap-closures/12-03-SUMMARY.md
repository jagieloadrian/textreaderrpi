---
phase: 12-observability-gap-closures
plan: "03"
subsystem: testing
tags: [ktor, html, error-pages, content-type, status-pages, swagger]

requires:
  - phase: 12-02
    provides: "Routing.kt and ApplicationTest.kt in stable state; wave 3 file conflict avoidance"

provides:
  - "Automated content-type + body assertion for browser-Accept unknown routes returning HTML 404"
  - "Automated assertion that API paths (/api/...) retain JSON error responses"
  - "Root cause fix: respondText() calls in ErrorHandling.kt now pass the correct HTTP status code"
  - "OBS-03 satisfied: browser navigation to unknown route renders HTML 404 page, not JSON"

affects: [future-phases-using-ErrorHandling, 12-CONTEXT]

tech-stack:
  added: []
  patterns:
    - "prefersHtml() discrimination: non-API paths with Accept: text/html receive HTML error pages; API paths always receive JSON"
    - "respondText(content, contentType, status) — all three parameters required; omitting status defaults to 200 OK"

key-files:
  created: []
  modified:
    - src/test/kotlin/com/anjo/ApplicationTest.kt
    - src/main/kotlin/com/anjo/di/ErrorHandling.kt

key-decisions:
  - "Root cause of OBS-03 gap was respondText() missing status parameter in ErrorHandling.kt — not route ordering"
  - "swaggerUI(path = 'openapi') was already registered last in Routing.kt; no reorder needed"
  - "All three HTML error handlers (404/422/500) fixed atomically in a single commit to ensure consistent status code behavior"

patterns-established:
  - "respondText(content, ContentType.Text.Html, statusCode) — always pass all three args"

requirements-completed: [OBS-03]

duration: 30min
completed: 2026-06-17
---

# Phase 12 Plan 03: HTML 404 Error Page Verification Summary

**Closed OBS-03 gap by fixing missing HTTP status codes in all three HTML error handlers (404/422/500) in ErrorHandling.kt and adding two automated content-type + body assertions to ApplicationTest**

## Performance

- **Duration:** ~30 min (including human-verify checkpoint)
- **Started:** 2026-06-17
- **Completed:** 2026-06-17
- **Tasks:** 1 auto + 1 human-verify checkpoint
- **Files modified:** 2

## Accomplishments

- Discovered and fixed root cause: `respondText()` in `ErrorHandling.kt` was called without its `status` parameter across all three HTML error handlers (404, 422, 500), causing HTML error pages to return HTTP 200 OK instead of the correct status code
- Added "should return HTML 404 page for browser navigation to unknown route" test asserting `/does-not-exist` with `Accept: text/html` returns 404, content-type `text/html`, body containing "Error 404" and "Page not found"
- Added "should return JSON error for API path unknown route" test asserting `/api/v1/does-not-exist` returns 404 with a JSON body (not containing "Error 404")
- Confirmed `swaggerUI(path = "openapi")` is already last inside `routing { }` in Routing.kt — no production routing change required
- Full `ApplicationTest` suite passes; human-verify checkpoint confirmed browser renders the HTML 404 page correctly (SC-3)

## Task Commits

1. **Task 1: Add automated HTML-404 content-type assertion + confirm swaggerUI ordering** - `608e4f7` (fix — includes auto-fix of respondText status codes)

## Files Created/Modified

- `src/test/kotlin/com/anjo/ApplicationTest.kt` — two new tests: browser-Accept HTML 404 assertion and API-path JSON error discrimination assertion
- `src/main/kotlin/com/anjo/di/ErrorHandling.kt` — all three HTML error handlers (`status(NotFound)`, `status(UnprocessableEntity)`, `exception<Throwable>`) now pass the correct status code as the third argument to `respondText()`

## Decisions Made

- Root cause of OBS-03 was the missing `status` parameter in `respondText()` — not swaggerUI ordering (which was already correct per 12-RESEARCH Pitfall 1 / A1)
- All three HTML handlers fixed in the same commit since they share the same defect pattern; fixing only the 404 handler would leave 422 and 500 returning 200 OK

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] respondText() missing status code in all three HTML error handlers**
- **Found during:** Task 1 (Add automated HTML-404 content-type assertion + confirm swaggerUI ordering)
- **Issue:** `respondText(ErrorPage(...).render(), ContentType.Text.Html)` in `ErrorHandling.kt` omitted the `status` parameter. Ktor's `respondText` defaults to 200 OK when status is omitted, so HTML error pages for 404, 422, and 500 all returned HTTP 200 OK — the content-type assertion `response.status shouldBe HttpStatusCode.NotFound` would have failed without this fix
- **Fix:** Added the correct status code as the third argument to all three `respondText()` calls: `respondText(content, ContentType.Text.Html, HttpStatusCode.NotFound)`, `respondText(content, ContentType.Text.Html, HttpStatusCode.UnprocessableEntity)`, and `respondText(content, ContentType.Text.Html, HttpStatusCode.InternalServerError)`
- **Files modified:** `src/main/kotlin/com/anjo/di/ErrorHandling.kt`
- **Verification:** `./gradlew test --tests "com.anjo.ApplicationTest"` exits 0; new HTML-404 test asserts `response.status shouldBe HttpStatusCode.NotFound` and passes
- **Committed in:** `608e4f7` (same task commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 bug)
**Impact on plan:** Fix was necessary for correctness — OBS-03 satisfaction requires the HTML error page to be returned with the actual 404 status code. No scope creep; fix applied to all sibling handlers for consistency.

## Issues Encountered

None beyond the auto-fixed respondText bug above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- OBS-03 fully satisfied: browser navigation to unknown paths returns the HTML 404 error page with correct HTTP status code
- Phase 12 (all three plans) is now complete: OBS-01 (hardware metrics), OBS-02 (health detail endpoint), OBS-03 (HTML 404 page) all satisfied
- No known blockers for milestone v1.1 completion

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The fix modifies only the HTTP status code returned by existing HTML error handlers — no new trust boundary surface.

---
*Phase: 12-observability-gap-closures*
*Completed: 2026-06-17*
