---
phase: 5
plan: "05-07"
subsystem: ui
tags: [schedule, html, ui, lifecycle, di]
requires: [05-04, 05-05, 05-06]
provides: [GET /schedule HTML page, complete DI/lifecycle wiring verification]
affects: [routing/Routing.kt]
tech-stack:
  patterns: [BaseLayout.render, kotlinx.html DSL, Route extension function]
key-files:
  created:
    - src/main/kotlin/com/anjo/routing/ScheduleUIRoutes.kt
    - src/test/kotlin/com/anjo/routing/ScheduleUIRoutesTest.kt
  modified:
    - src/main/kotlin/com/anjo/routing/Routing.kt
key-decisions:
  - HTML body check for content type was incorrect; fixed to check response headers instead
  - scheduleUIRoutes placed outside /api/v1 route group (it's a web page, not an API)
  - BaseLayout.render used directly in Route handler (no separate page class) to keep it simple
requirements-completed: [REQ-SCHED-01]
duration: "15 min"
completed: "2026-05-28"
---

# Phase 5 Plan 05-07: Schedule UI Page + DI Wiring + Lifecycle Verification Summary

Schedule management HTML page at GET /schedule and end-to-end DI/lifecycle verification confirming full Wave 2 integration.

**Duration:** ~15 min | **Tasks:** 5 | **Files:** 3

## What Was Built

- **`ScheduleUIRoutes.kt`** — `fun Route.scheduleUIRoutes(repository)` with:
  - `GET /schedule` — responds with full HTML using `BaseLayout.render("Schedule Manager", "/schedule")`
  - Table listing all schedules (ID, Text, Trigger, Effect, Status, Actions)
  - Form for creating new schedules (text, triggerType select, triggerValue, effect select, priority)
- **`ScheduleUIRoutesTest.kt`** — 3 Kotest FunSpec tests: 200 status, "Schedule Manager" heading, table headers
- **`Routing.kt`** updated to register `scheduleUIRoutes(scheduleRepository)` alongside web routes
- **DI + lifecycle verification**: Confirmed all 5 required items:
  1. ✅ `DatabaseFactory.init(...)` called before DI bindings
  2. ✅ `SchedulerService` bound as singleton in DI
  3. ✅ `monitor.subscribe(ApplicationStarted) { schedulerService.start() }`
  4. ✅ `monitor.subscribe(ApplicationStopping) { schedulerService.stop() }`
  5. ✅ `ScheduleRepository` in DI

## Deviations from Plan

**[Rule 1] HTML body text/html check fixed to use response header**
Found during: Task 05-07-04 | Issue: Test checked `bodyAsText() shouldContain "text/html"` but the page body doesn't contain that string — the Content-Type is in the HTTP header | Fix: Changed to `response.headers[HttpHeaders.ContentType] shouldContain "text/html"` | Impact: None.

**Total deviations:** 1 auto-fixed. **Impact:** None.

## Acceptance Criteria Verification

| Criterion | Result |
|-----------|--------|
| `ScheduleUIRoutes.kt` exists with `get("/schedule")` | ✅ PASS |
| `call.respondText(html, ContentType.Text.Html)` present | ✅ PASS |
| Table column headers: ID, Text, Trigger, Effect, Status | ✅ PASS |
| Form for schedule creation present | ✅ PASS |
| `Routing.kt` contains `scheduleUIRoutes(` | ✅ PASS |
| All DI wiring verified (DatabaseFactory, SchedulerService, lifecycle hooks) | ✅ PASS |
| `./gradlew compileKotlin` exits 0 | ✅ PASS |
| `./gradlew test` exits 0 | ✅ PASS — 69/69 tests pass, JaCoCo ≥70% |

## Self-Check: PASSED

