---
plan: 05-09
status: complete
completed: 2026-05-28
key-files:
  created:
    - src/main/kotlin/com/anjo/model/TextRequest.kt
    - src/main/kotlin/com/anjo/routing/TextRoutes.kt
    - src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt
  modified:
    - src/main/kotlin/com/anjo/service/ScreenDriver.kt
---

# Plan 05-09 Summary: Wire Effect Field into POST /api/text

## What Was Built

Added the optional `effect` field to `POST /api/text` requests. When `effect` is provided, `ScreenDriverService.displayImmediate()` selects the correct `EffectRenderer` from `EffectRendererFactory`. Invalid enum values (e.g. `"KABOOM"`) trigger a 400 Bad Request via the existing `SerializationException` handler in `ErrorHandling.kt`.

## Changes Made

### `src/main/kotlin/com/anjo/model/TextRequest.kt`
- Added `val effect: Effect = Effect.SCROLL` field with default (backward compatible)
- `@Serializable` annotation already present; `Effect` imported from `com.anjo.model`

### `src/main/kotlin/com/anjo/routing/TextRoutes.kt`
- Updated POST handler to pass `request.effect` to `screenDriverService.displayImmediate(request.text, request.effect)`
- `ScreenDriverService` injected directly alongside `ReaderInputService`

### `src/main/kotlin/com/anjo/service/SchedulerService.kt`
- `fire()` uses `effectFactory.create(schedule.effect)` → confirmed complete (no change needed)
- End-to-end flow confirmed: POST /api/schedule → ScheduleRepository → SchedulerService.fire() → EffectRendererFactory.create(effect) → EffectRenderer.render(text, driver)

### `src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt`
- Added 3 new tests:
  - `POST api-v1-text with effect=FADE returns 202` — passes `"effect":"FADE"`, expects 202
  - `POST api-v1-text with no effect defaults to SCROLL` — no effect field, expects 202
  - `POST api-v1-text with invalid effect returns 4xx` — `"effect":"KABOOM"`, expects 400 (SerializationException caught by ErrorHandling.kt)

## Deviations

None. The `exception<SerializationException>` handler in `ErrorHandling.kt` was already in place from earlier phases and correctly returns 400 for unknown enum values. The Throwable cause-chain fallback also handles wrapped serialization errors. No changes to error handling infrastructure were needed.

## Test Results

```
./gradlew test --tests "com.anjo.routing.TextApiRouteTest" → BUILD SUCCESSFUL (6/6 tests pass)
./gradlew test jacocoTestReport jacocoTestCoverageVerification → BUILD SUCCESSFUL
```

## Self-Check: PASSED
- ✅ TextRequest has `val effect: Effect = Effect.SCROLL`
- ✅ TextRoutes passes `request.effect` to `displayImmediate`
- ✅ SchedulerService.fire() uses `effectFactory.create(schedule.effect)`
- ✅ 3 new effect field tests pass
- ✅ Full test suite green
- ✅ JaCoCo gate passes

