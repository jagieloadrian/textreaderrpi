---
phase: 15-sse-live-feed
plan: "01"
subsystem: sse-event-bus
tags: [sse, event-bus, shared-flow, kotlin-coroutines, ktor, di]
status: complete

dependency_graph:
  requires: []
  provides:
    - com.anjo.model.DisplayEvent
    - com.anjo.service.DisplayEventBus
    - DisplayEventBus DI singleton
    - ktor-server-sse Gradle dependency
  affects:
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt

tech_stack:
  added:
    - io.ktor:ktor-server-sse (Ktor 3.5.0 SSE plugin, first-party)
    - MutableSharedFlow(replay=5) — Kotlin coroutines SharedFlow as event bus
  patterns:
    - SharedFlow producer/consumer pattern for in-process eventing
    - Nullable optional DI parameter for backward-compatible service extension

key_files:
  created:
    - src/main/kotlin/com/anjo/model/DisplayEvent.kt
    - src/main/kotlin/com/anjo/service/DisplayEventBus.kt
    - src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt
    - src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt
  modified:
    - gradle/ktor-libs.versions.toml
    - build.gradle.kts
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/main/kotlin/com/anjo/di/DependencyInjection.kt

decisions:
  - "id: String in DisplayEvent (not Long) — matches HistoryRecord.id UUID String; avoids lossy conversion (RESEARCH Open Question #1)"
  - "displayEventBus: DisplayEventBus? = null as last ScreenDriverService param — keeps all existing tests compiling without modification (Pitfall 5)"
  - "emit inside existing try/catch in tryInsertHistory — failed insert silently suppresses emit, consistent with non-fatal history insert policy"
  - "capture HistoryRecord return value from historyRepository.insert() — authoritative record with populated id and displayedAt fields"

metrics:
  duration_minutes: 4
  completed_date: "2026-06-22"
  tasks_completed: 3
  tasks_total: 3
  files_created: 4
  files_modified: 4
---

# Phase 15 Plan 01: SSE Event Bus Infrastructure Summary

**One-liner:** SharedFlow(replay=5) DisplayEventBus wired into ScreenDriverService emit-after-insert, registered as DI singleton, with ktor-server-sse dependency and Wave-0 test stubs.

## What Was Built

The producer side of the SSE live feed (LIVE-01):

- `DisplayEvent` data class — `@Serializable` with fields `id: String`, `text`, `effect`, `zoneId: String?`, `displayedAt`; `id` is `String` (UUID) matching `HistoryRecord.id` exactly
- `DisplayEventBus` — `MutableSharedFlow<DisplayEvent>(replay = 5, extraBufferCapacity = 0)`, exposes `events: SharedFlow<DisplayEvent>`, `suspend fun emit(event: DisplayEvent)`
- `ScreenDriverService.tryInsertHistory` — now captures the `HistoryRecord` returned by `historyRepository?.insert(...)` and calls `displayEventBus?.emit(DisplayEvent(...))` after a successful insert; failed inserts produce no emit (consistent with existing non-fatal swallow)
- `DependencyInjection.kt` — constructs `DisplayEventBus()` singleton before `ScreenDriverService`, passes it as `displayEventBus = displayEventBus`, and adds `provide { displayEventBus }` to the DI container
- Gradle version catalog: `ktor-server-sse = { module = "io.ktor:ktor-server-sse", version.ref = "ktor" }` and `implementation(ktorLibs.ktor.server.sse)` in `build.gradle.kts`
- `DisplayEventBusTest` — 3 Kotest FunSpec tests verifying emit/collect, replay=5 drop-oldest behavior, and SharedFlow identity; all three tests pass GREEN
- `LiveRoutesTest` — placeholder-green Wave-0 stub with single `true shouldBe true` test; real assertions land in Plan 02

## Tasks Completed

| Task | Description | Commit | Result |
|------|-------------|--------|--------|
| 1 | Add ktor-server-sse dependency and Wave-0 test files | be782b2 | Compile-fail RED (expected) then resolved in Task 2 |
| 2 | Create DisplayEvent model and DisplayEventBus service | 8e684ce | GREEN — all 3 DisplayEventBusTest tests pass |
| 3 | Wire emit into ScreenDriverService and register DI | aa68a7f | GREEN — HistoryRecordingTest + ApplicationTest pass |

## Verification Results

- `./gradlew compileKotlin compileTestKotlin` — BUILD SUCCESSFUL
- `./gradlew test --tests "com.anjo.service.DisplayEventBusTest"` — BUILD SUCCESSFUL (3/3 tests pass)
- `./gradlew test --tests "com.anjo.service.HistoryRecordingTest" --tests "com.anjo.ApplicationTest"` — BUILD SUCCESSFUL
- `./gradlew test` — BUILD SUCCESSFUL (full suite, coverage maintained)

## Deviations from Plan

None — plan executed exactly as written.

The D-08 type mismatch (context says `id: Long`, plan says use `id: String`) was pre-resolved in the plan itself; `id: String` was used as specified.

## Known Stubs

None. `LiveRoutesTest` is intentionally a placeholder-green stub per plan specification (Wave-0 stub for Plan 02).

## Threat Flags

No new security-relevant surface introduced. `DisplayEventBus` is an in-process `SharedFlow` — no external trust boundary crossed. The `ktor-server-sse` dependency is a first-party Ktor artifact verified in RESEARCH Package Legitimacy Audit.

## Self-Check: PASSED

All created files exist on disk. All task commits exist in git history.

| Check | Result |
|-------|--------|
| DisplayEvent.kt exists | FOUND |
| DisplayEventBus.kt exists | FOUND |
| DisplayEventBusTest.kt exists | FOUND |
| LiveRoutesTest.kt exists | FOUND |
| Commit be782b2 (Task 1) | FOUND |
| Commit 8e684ce (Task 2) | FOUND |
| Commit aa68a7f (Task 3) | FOUND |
