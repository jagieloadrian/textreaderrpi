---
phase: 17-firmware-skeletons
plan: 01
subsystem: api
tags: [kotlinx-serialization, websocket, zone-driver, firmware, wire-protocol]

requires: []
provides:
  - FirmwareMessage with nullable speed/blinkPeriod/fadeSteps annotated @EncodeDefault(NEVER) — null fields omitted from JSON wire frame
  - ZoneDriver.send() interface extended with optional timing params (default null)
  - LocalZoneDriver, NetworkZoneDriver, FirmwareZoneDriver updated override signatures
  - ZoneRegistry.route() and broadcast() forward timing params to driver.send()
  - ScreenDriverService.displayImmediate(), broadcastImmediate(), renderImmediate(), executeWithRecovery() thread timing end-to-end
  - FirmwareMessageTest (serialization correctness) and FirmwareZoneDriverTest (timing-forwarding via WS)
affects: [17-02, 17-03, 17-04, firmware-phases]

tech-stack:
  added: []
  patterns:
    - "@EncodeDefault(Mode.NEVER) on nullable fields to suppress null-key emission in kotlinx.serialization JSON output"
    - "Override signatures omit default values (Kotlin forbids repeating defaults from interface on override)"
    - "embeddedServer(Netty, port=0) + CIO WebSocket client for timing-sensitive WS integration tests"

key-files:
  created:
    - src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt
  modified:
    - src/main/kotlin/com/anjo/model/FirmwareMessage.kt
    - src/main/kotlin/com/anjo/zone/ZoneDriver.kt
    - src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt
    - src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt
    - src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt
    - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
    - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
    - src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt

key-decisions:
  - "Use @EncodeDefault(Mode.NEVER) per-field rather than global explicitNulls=false Json config — scoped to FirmwareMessage, does not affect other serialization paths"
  - "Override signatures drop default values (= null) — Kotlin prohibits re-specifying defaults from interface on override; interface defaults propagate automatically"
  - "embeddedServer + CIO client for timing-forwarding WS test — testApplication uses test coroutine dispatcher which blocks on uncompleted child coroutines from session.launch drain job; real Netty server avoids this"

patterns-established:
  - "WS integration tests that drive FirmwareZoneDriver: use embeddedServer(Netty, port=0) + HttpClient(CIO)+WebSockets; delay(50ms) after connect to ensure attach() races ahead of send()"

requirements-completed: [FW-01]

duration: 403min
completed: 2026-06-24
status: complete
---

# Phase 17 Plan 01: Wire Protocol Extension Summary

**Extended FirmwareMessage JSON wire contract with nullable timing fields (speed/blinkPeriod/fadeSteps) using @EncodeDefault(NEVER), threaded through ZoneDriver interface, all three driver implementations, ZoneRegistry, and ScreenDriverService with full serialization and WS integration tests**

## Performance

- **Duration:** 403 min (includes previous context session)
- **Started:** 2026-06-23T22:58:22Z
- **Completed:** 2026-06-24T05:41:47Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- FirmwareMessage gains `speed: Int?`, `blinkPeriod: Int?`, `fadeSteps: Int?` each annotated `@EncodeDefault(Mode.NEVER)` — null fields are absent from JSON, non-null fields appear as `"speed":120`
- ZoneDriver.send() interface carries three nullable timing params with interface-level defaults; all three implementations (Local, Network, Firmware) updated; FirmwareZoneDriver passes them into FirmwareMessage constructor
- ScreenDriverService threads timing from `displayImmediate` → `broadcastImmediate`/`renderImmediate` → `executeWithRecovery` → `ZoneRegistry.route`/`broadcast` → `driver.send` — existing callers remain source-compatible (default null)
- FirmwareMessageTest proves null omission and non-null serialization; FirmwareZoneDriverTest extended with embeddedServer WS integration test confirming timing-forwarding through the drain channel

## Task Commits

1. **test(17-01)** `300fdd8` — failing serialization tests for FirmwareMessage timing fields (RED)
2. **feat(17-01)** `bf668c3` — extend FirmwareMessage with nullable timing fields and EncodeDefault NEVER (GREEN)
3. **feat(17-01)** `44a1186` — thread timing params through ZoneDriver interface and all implementations

## Files Created/Modified
- `src/main/kotlin/com/anjo/model/FirmwareMessage.kt` — adds speed/blinkPeriod/fadeSteps with @EncodeDefault(NEVER) and @OptIn(ExperimentalSerializationApi)
- `src/main/kotlin/com/anjo/zone/ZoneDriver.kt` — send() now has three nullable timing params (default null)
- `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt` — override sends timing into FirmwareMessage constructor
- `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt` — override signature extended, body ignores timing
- `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt` — override signature extended, body ignores timing
- `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` — route() and broadcast() forward timing to driver.send()
- `src/main/kotlin/com/anjo/service/ScreenDriverService.kt` — displayImmediate → broadcastImmediate/renderImmediate → executeWithRecovery all carry timing params
- `src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt` — Kotest FunSpec testing null omission and non-null presence
- `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt` — extended with WS integration test for timing-forwarding

## Decisions Made
- `@EncodeDefault(Mode.NEVER)` per-field rather than a global Json config change — keeps other serialization paths unaffected
- Override signatures do NOT repeat `= null` defaults (Kotlin compiler error: "An overriding function is not allowed to specify default values for its parameters") — interface defaults propagate automatically
- Used `embeddedServer(Netty, port=0) + HttpClient(CIO)` for the timing WS test instead of `testApplication` — `testApplication` uses a test coroutine dispatcher that raises `UncompletedCoroutinesError` for the drain job launched by `attach()` inside the session scope

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Kotlin override default-value compile error**
- **Found during:** Task 2 (ZoneDriver interface threading)
- **Issue:** Plan specified override signatures with `= null` defaults; Kotlin forbids re-declaring defaults on overrides (compiler error on all three implementations)
- **Fix:** Removed `= null` from LocalZoneDriver, NetworkZoneDriver, FirmwareZoneDriver overrides; interface defaults propagate automatically per Kotlin spec
- **Files modified:** LocalZoneDriver.kt, NetworkZoneDriver.kt, FirmwareZoneDriver.kt
- **Verification:** `./gradlew compileKotlin` clean
- **Committed in:** 44a1186

**2. [Rule 1 - Bug] testApplication WS timing test caused UncompletedCoroutinesError**
- **Found during:** Task 2 (FirmwareZoneDriverTest extension)
- **Issue:** `testApplication` uses `TestCoroutineDispatcher`; the drain job started by `attach()` via `session.launch {}` became an uncompleted child coroutine, timing out after 60s
- **Fix:** Switched to `embeddedServer(Netty, port=0) + HttpClient(CIO) { install(WebSockets) }` pattern (same as LiveRoutesTest SSE test) with `delay(50ms)` after connect
- **Files modified:** FirmwareZoneDriverTest.kt
- **Verification:** All 7 FirmwareZoneDriverTest tests pass including new timing-forwarding test
- **Committed in:** 44a1186

---

**Total deviations:** 2 auto-fixed (1 Kotlin language constraint, 1 test infrastructure incompatibility)
**Impact on plan:** Both mandatory correctness fixes. No scope change.

## Issues Encountered
None beyond the two auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Wire protocol extension complete; firmware devices (Plans 17-03 and 17-04) can now rely on speed/blinkPeriod/fadeSteps fields in the JSON frame
- Plan 17-02 (TextRequest timing fields + validation) can proceed; it extends the same threading chain at the HTTP request layer
- All existing tests pass; full build clean

## Self-Check: PASSED
- `grep -c '@EncodeDefault' src/main/kotlin/com/anjo/model/FirmwareMessage.kt` → 3
- `grep -c 'speed: Int?' src/main/kotlin/com/anjo/zone/ZoneDriver.kt` → 1
- `./gradlew test --tests "com.anjo.model.FirmwareMessageTest" --tests "com.anjo.zone.*" --tests "com.anjo.service.*" -x jacocoTestCoverageVerification` → BUILD SUCCESSFUL
- `./gradlew build -x jacocoTestCoverageVerification` → BUILD SUCCESSFUL

---
*Phase: 17-firmware-skeletons*
*Completed: 2026-06-24*
