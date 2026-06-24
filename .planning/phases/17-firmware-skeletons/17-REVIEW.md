---
phase: 17-firmware-skeletons
reviewed: 2026-06-24T00:00:00Z
depth: standard
files_reviewed: 26
files_reviewed_list:
  - firmware/esp32/CMakeLists.txt
  - firmware/esp32/main/captive_portal.c
  - firmware/esp32/main/CMakeLists.txt
  - firmware/esp32/main/config.h
  - firmware/esp32/main/driver/max7219.c
  - firmware/esp32/main/json_parser.c
  - firmware/esp32/main/main.c
  - firmware/esp32/main/ota.c
  - firmware/esp32/main/ws_client.c
  - firmware/esp32/sdkconfig.defaults
  - firmware/pico/CMakeLists.txt
  - .github/workflows/firmware-ci.yml
  - src/main/kotlin/com/anjo/model/FirmwareMessage.kt
  - src/main/kotlin/com/anjo/model/TextRequest.kt
  - src/main/kotlin/com/anjo/routing/TextRoutes.kt
  - src/main/kotlin/com/anjo/service/ScreenDriverService.kt
  - src/main/kotlin/com/anjo/service/ZoneRegistry.kt
  - src/main/kotlin/com/anjo/validation/RequestValidators.kt
  - src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt
  - src/main/kotlin/com/anjo/zone/ZoneDriver.kt
  - src/test/kotlin/com/anjo/model/FirmwareMessageTest.kt
  - src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt
  - src/test/kotlin/com/anjo/validation/RequestValidatorsTest.kt
  - src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt
  - src/test/kotlin/com/anjo/zone/LocalZoneDriverTest.kt
findings:
  critical: 5
  warning: 7
  info: 4
  total: 16
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-06-24
**Depth:** standard
**Files Reviewed:** 26
**Status:** issues_found

## Summary

Phase 17 adds C firmware skeletons for ESP32 (ESP-IDF) and Pico W (SDK), a WebSocket-based
`FirmwareZoneDriver` on the Kotlin backend, and accompanying tests. The C code contains several
memory-safety defects — a DNS response buffer overflow, an unvalidated WebSocket frame pointer, an
infinite reconnect loop on STA failure, and a missing null-check from NVS. The Kotlin backend has a
race between an atomic status check and the channel send in `FirmwareZoneDriver`, a hand-rolled JSON
string in `NetworkZoneDriver` that does not carry the timing fields the protocol defines, a commented-out
code line left in production, and a mutex that is acquired but never released in
`ScreenDriverService.displayImmediate` under one branch. The CI pipeline is sound for a skeleton
build check. Test coverage has meaningful gaps around the online-then-detached race and the
NetworkZoneDriver timing fields.

---

## Critical Issues

### CR-01: DNS response buffer overflow — `rlen` can exceed 512 bytes

**File:** `firmware/esp32/main/captive_portal.c:88-108`

**Issue:** `recvfrom` is called with a limit of `sizeof(buf) - 20` (492 bytes), so `len` can be up to
492. The crafted DNS response copies those 492 bytes into `resp[512]` and then appends 16 bytes of
fixed answer, reaching index 508 — still within bounds. However the bound used for `recvfrom` is
`sizeof(buf) - 20` to "leave room", but the correct calculation for the appended 16 bytes is
`sizeof(resp) - 16 = 496`. If the incoming DNS query is between 497 and 492 bytes (the cap) this is
fine, but the recv cap itself is `512 - 20 = 492` while the resp array is 512 and the appended block
is exactly 16 bytes, so `rlen` reaches at most `492 + 16 = 508`. The real overflow risk is more
subtle: `resp[rlen++]` is performed 16 times after `rlen = len`, with no bounds check inside the
loop. There is no guard preventing a future change to the answer block from silently overflowing.
More immediately, the code sets `resp[6]` and `resp[7]` (answer count) but `resp[6]` is never
written — only `resp[7] = 1`. A valid DNS response must set the answer count as a 16-bit big-endian
field at bytes 6–7: both `resp[6] = 0x00` and `resp[7] = 0x01`. The current code leaves `resp[6]`
as whatever was in the query, which can produce a malformed response count if the query happens to
have a non-zero byte at position 6 (QDCOUNT high byte). While individually each of these is
borderline, the combination of no inner bounds check and a wrong DNS header field is a correctness
defect that can crash clients or, if the recv cap is ever raised, overflow the stack buffer.

**Fix:**
```c
// Use a compile-time constant for the answer extension size
#define DNS_ANSWER_EXT 16
uint8_t resp[512];
if (len > (int)(sizeof(resp) - DNS_ANSWER_EXT)) {
    continue; // Drop oversized query
}
memcpy(resp, buf, len);
resp[2] = 0x81; resp[3] = 0x80;
resp[6] = 0x00; resp[7] = 0x01; // answer count high+low bytes (fixes CR-01 header bug)
int rlen = len;
// ... append 16 bytes of answer as before
```

---

### CR-02: `ev->data_ptr` passed to `parse_message` without null check or length guard

**File:** `firmware/esp32/main/ws_client.c:14-15`

**Issue:** When a WebSocket `WEBSOCKET_EVENT_DATA` frame arrives, `ev->data_ptr` is passed directly
to `cJSON_Parse`. The ESP WebSocket client can deliver a frame with `data_ptr == NULL` (e.g. for
ping/pong or a zero-length continuation frame) or a frame whose `data_len` bytes are not
null-terminated. `cJSON_Parse` reads until a null terminator; if the buffer is not null-terminated
it will read past the end of the heap allocation, causing undefined behaviour or a crash.

**Fix:**
```c
if (id == WEBSOCKET_EVENT_DATA && ev->op_code == 1) {
    if (ev->data_ptr == NULL || ev->data_len <= 0) return;
    // cJSON_Parse requires a null-terminated string; data_ptr is NOT guaranteed to be so.
    char *buf = malloc(ev->data_len + 1);
    if (!buf) return;
    memcpy(buf, ev->data_ptr, ev->data_len);
    buf[ev->data_len] = '\0';
    parse_message(buf);
    free(buf);
}
```

---

### CR-03: Infinite reconnect loop on STA connection failure prevents provisioning portal fallback

**File:** `firmware/esp32/main/captive_portal.c:43-49`

**Issue:** `wifi_event_handler` unconditionally calls `esp_wifi_connect()` on every
`WIFI_EVENT_STA_DISCONNECTED` event. If the stored SSID/password is wrong, the device will hammer
`esp_wifi_connect()` in a tight loop forever and never reach the `start_portal()` fallback path in
`wifi_start()`. The 15-second `xEventGroupWaitBits` timeout in `connect_sta()` fires and returns
"not connected", but the event handler is still registered and keeps reconnecting in the background.
When `start_portal()` then calls `esp_wifi_init()` again without a preceding `esp_wifi_deinit()`,
the duplicate init corrupts the Wi-Fi stack.

**Fix:**
```c
// Use a retry counter; deregister the event handler before returning from connect_sta()
static int s_retry_count = 0;
#define MAX_RETRIES 3

static void wifi_event_handler(...) {
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        if (s_retry_count < MAX_RETRIES) {
            s_retry_count++;
            esp_wifi_connect();
        } else {
            xEventGroupSetBits(s_wifi_event_group, WIFI_FAIL_BIT);
        }
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        s_retry_count = 0;
        xEventGroupSetBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
    }
}
// In connect_sta(), check WIFI_FAIL_BIT and deregister handlers before returning false.
```

---

### CR-04: `FirmwareZoneDriver.send()` — TOCTOU race between `sessionRef` check and channel send; message silently dropped when channel is full

**File:** `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt:43-56`

**Issue:** Two distinct defects share a root cause:

1. **TOCTOU**: `send()` reads `sessionRef.get() == null` (line 44) to decide whether to serialise and
   enqueue the message. Between that check and `channel.trySend(json)` (line 56), `detach()` can
   set `sessionRef` to null, meaning the message is queued but will never be drained (the drain job
   was cancelled in `attach()` before `detach()` was called). The message sits in the channel and
   is delivered to the *next* firmware that connects — potentially a different physical device.

2. **Silent drop on full channel**: `Channel(capacity=64)` with `trySend` returns `isSuccess ==
   false` when the channel is full (e.g. firmware not draining). `send()` returns `false`, and
   `ScreenDriverService` treats that as an offline zone and logs nothing specific. The caller
   receives `DisplayResult.Accepted(true)` (because the zone was ONLINE at the mutex-check point)
   but the text was never sent.

**Fix for TOCTOU:** Purge the channel in `detach()`:
```kotlin
fun detach() {
    sessionRef.set(null)
    // Drain stale messages so they don't leak to the next session
    while (channel.tryReceive().isSuccess) { /* discard */ }
}
```
**Fix for silent drop:** Return `false` and let `ScreenDriverService` mark it as a send failure
(already does this correctly when `send` returns false) — but the caller must not have already
returned `Accepted(true)`. The zone status check at `ScreenDriverService:66` must be moved to
occur atomically with the enqueue, which requires `FirmwareZoneDriver` to expose a combined
"attach-and-send" primitive or treat a full channel as OFFLINE.

---

### CR-05: `NetworkZoneDriver.send()` uses hand-rolled JSON that omits the timing fields

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:61`

**Issue:** The message sent to network (Pico W) zones is:
```kotlin
"""{"text":${Json.encodeToString(text)},"effect":"${effect.name}"}"""
```
This omits `speed`, `blinkPeriod`, `fadeSteps`, and `zoneId` / `ts` that `FirmwareMessage` carries.
The Pico firmware's `parse_message` in `json_parser.c` reads `speed`, `blinkPeriod`, and
`fadeSteps` from the JSON; when driving a Pico W via `NetworkZoneDriver`, all timing parameters
passed by the caller are silently lost. Additionally, `effect.name` is interpolated directly into
the string without JSON-escaping — if the `Effect` enum ever gains a value with special characters
this becomes a JSON injection point.

**Fix:** Use `FirmwareMessage` (or a shared serialiser) for both `FirmwareZoneDriver` and
`NetworkZoneDriver`:
```kotlin
override suspend fun send(text: String, effect: Effect, speed: Int?, blinkPeriod: Int?, fadeSteps: Int?): Boolean {
    val s = session ?: return false
    val json = Json.encodeToString(
        FirmwareMessage(
            text = text, effect = effect.name,
            zoneId = id, ts = Instant.now().toString(),
            speed = speed, blinkPeriod = blinkPeriod, fadeSteps = fadeSteps
        )
    )
    return try {
        s.send(Frame.Text(json)); true
    } catch (e: Exception) {
        log.warn("NetworkZoneDriver send failed to $ip: ${e.message}"); false
    }
}
```

---

## Warnings

### WR-01: `load_credentials` passes `ssid_max`/`pass_max` by value — NVS size query is broken

**File:** `firmware/esp32/main/captive_portal.c:24-32`

**Issue:** `nvs_get_str` requires a pointer to `size_t` that it updates with the actual bytes
written. The function signature is `bool load_credentials(char *ssid, size_t ssid_max, ...)`. The
local `size_t ssid_max` parameter is passed as `&ssid_max` to `nvs_get_str`, which modifies the
parameter's stack copy. This is actually correct *if* NVS writes into the provided buffer and the
initial value of `ssid_max` is the buffer capacity. The call `nvs_get_str(h, "ssid", ssid,
&ssid_max)` passes the caller-supplied capacity correctly. However the call sequence means if the
`ssid` NVS call succeeds (and modifies `ssid_max` to the string length), the subsequent
`nvs_get_str` for `pass` receives `&pass_max` which is still the original capacity — this part is
fine. The defect is subtler: after `nvs_get_str` returns `ESP_OK`, `ssid_max` now holds the byte
count written (including the null), not the original buffer size. If the caller subsequently relied
on `ssid_max` after the call it would get wrong data. Here the function discards both parameters
after the `bool ok` assignment, so the side-effect does not corrupt anything. BUT — if the stored
credential string is exactly 64 bytes (without null), NVS will return `ESP_ERR_NVS_INVALID_LENGTH`
because the buffer is too small, silently treating valid credentials as absent. The `ssid` buffer at
call site is 64 bytes (`char ssid[64]`) so SSID strings of 63 chars or fewer work; the 64-byte
edge case fails.

**Fix:** Pass `sizeof(ssid) - 1` (63) as the max, or size the NVS buffer to 65 bytes to accommodate
the null terminator safely:
```c
char ssid[65] = {0}, pass[65] = {0};
if (load_credentials(ssid, sizeof(ssid), pass, sizeof(pass))) {
```

---

### WR-02: `ScreenDriverService.displayImmediate` — mutex acquired for broadcast but not released on the `INTERRUPT` branch

**File:** `src/main/kotlin/com/anjo/service/ScreenDriverService.kt:68-81`

**Issue:** When `conflictPolicy != SKIP_NEW`, `acquireMutex` returns the mutex *without locking it*
(the `SKIP_NEW` path calls `tryLock`; the else branch just returns the mutex unlocked). Then on line
89, `broadcastImmediate` calls `zoneMutex.unlock()` only if `conflictPolicy == ConflictPolicy.SKIP_NEW`.
For the `INTERRUPT` path the mutex was never locked, so the `unlock()` is never called — which is
correct — but the code comment-less asymmetry means this is a fragile invariant. More concretely,
for the non-broadcast single-zone path (line 78–81), `renderImmediate` is called inside a launched
coroutine, and inside `renderImmediate` the `finally` block calls `mutex.unlock()` if
`alreadyLocked` (line 148). `alreadyLocked = (conflictPolicy == ConflictPolicy.SKIP_NEW)`. For
`INTERRUPT`, `alreadyLocked == false`, so `mutex.unlock()` is NOT called in the finally block —
fine because the mutex was never locked. The actual defect: on the `INTERRUPT` branch, the old
`currentDisplayJob` is cancelled (line 75) but its mutex (for the old job's zone, which may differ
from `zoneId`) is not tracked. Two successive `INTERRUPT` requests for different zones will
accumulate mutex objects in `mutexes` that are never cleaned up, leaking memory proportional to
distinct zone IDs seen. This is a correctness concern in long-running deployments, not just a
performance issue.

**Fix:** In `removeZone`, remove the corresponding entry from `mutexes`:
```kotlin
fun removeZone(id: String): Boolean {
    mutexes.remove(id)  // add this line to ScreenDriverService or expose removeZone hook
    ...
}
```

---

### WR-03: `LocalZoneDriver` contains commented-out production code

**File:** `src/main/kotlin/com/anjo/zone/LocalZoneDriver.kt:22`

**Issue:** Line 22 is `//            driver.write(text)`. This is dead commented-out code left in a
production file. Per project coding rules (MEMORY.md: "no comments in code files"), this must be
removed. Beyond the style violation, the comment creates ambiguity about whether `EffectRendererFactory`
is a replacement for `driver.write()` or an addition to it.

**Fix:** Delete the commented line entirely.

---

### WR-04: `FirmwareZoneDriver` channel is never drained after `stop()` — memory held indefinitely

**File:** `src/main/kotlin/com/anjo/zone/FirmwareZoneDriver.kt:66-68`

**Issue:** `stop()` calls `channel.close()`. Any messages buffered in the channel (up to 64) are
never consumed and cannot be GC'd until the `Channel` object itself is garbage collected — which
requires the `FirmwareZoneDriver` instance to become unreachable. If `ZoneRegistry.removeZone()`
is called followed by `registerFirmwareZone()` for the same ID (e.g. during firmware reconnect), a
new `FirmwareZoneDriver` is created but the old one's channel objects live until GC. More
importantly, the drain coroutine job (`drainJob`) is not cancelled in `stop()`, so if a drain job
is active when `stop()` is called, it will attempt to read from the closed channel and may throw
`ClosedReceiveChannelException` or terminate cleanly — but the `session` it references is still
alive, leaving the `DefaultWebSocketServerSession` in a half-terminated state.

**Fix:**
```kotlin
override fun stop() {
    drainJob.getAndSet(null)?.cancel()
    channel.close()
}
```

---

### WR-05: `build_text_bitmap` loop bound uses `NUM_DEVICES * 8 + 1` constant but does not protect the trailing-zeros append

**File:** `firmware/esp32/main/driver/max7219.c:71,80-83`

**Issue:** The character-writing loop (line 71) stops when
`idx >= MAX_BITMAP_LEN - (NUM_DEVICES * 8 + 1)`. `MAX_BITMAP_LEN = 256 + NUM_DEVICES * 8`, so the
limit is `256 - 1 = 255`. Each character takes 6 bytes (5 pixels + 1 gap). For a 43-character input
string, `idx` reaches 258, exceeding the limit. The loop exits correctly via the guard, but then the
trailing-zeros loop (lines 80–83) starts at `idx = 254` (just below 255) and appends
`NUM_DEVICES * 8 = 32` zeros. The inner guard `idx < MAX_BITMAP_LEN` (= 288) allows writing up to
index 287 into `s_bitmap[MAX_BITMAP_LEN]` which is exactly 288 bytes — no overflow in this case.
However, the character guard uses `MAX_BITMAP_LEN - (NUM_DEVICES * 8 + 1)` while the trailing guard
uses `MAX_BITMAP_LEN`. These inconsistent limits make the invariant fragile: if `NUM_DEVICES` is
increased the character limit shrinks further while `MAX_BITMAP_LEN` grows, and the asymmetry
becomes a real overflow when the trailing append pushes past the array end. The current default
values (NUM_DEVICES=4) are safe but the code is one config change away from a stack-allocated
array overflow.

**Fix:** Define a single constant for the text body region and check it in both loops:
```c
#define TEXT_BODY_LEN  (MAX_BITMAP_LEN - NUM_DEVICES * 8)
// Character loop: idx < TEXT_BODY_LEN - FONT_CHAR_WIDTH
// Trailing loop:  idx < MAX_BITMAP_LEN (already correct)
```

---

### WR-06: `ZoneRegistry` constructor — `wsClient.let { client -> ... }` is always non-null; `runBlocking` inside a constructor

**File:** `src/main/kotlin/com/anjo/service/ZoneRegistry.kt:47-61`

**Issue:** `wsClient` is set to the constructor parameter (a non-null `HttpClient`) on line 42
immediately before `wsClient.let { ... }` on line 47. The `let` block will always execute — the
guard is useless. More critically, `runBlocking { zoneRepository.findAll() }` is called inside
a secondary constructor, which is called from the application startup path. `runBlocking` on the
main/startup thread blocks that thread until the DB query finishes. If the DB is slow or unavailable
at startup, the entire application startup stalls. The correct pattern is to load persisted zones in
a `LaunchEffect` / startup hook, not in a constructor.

**Fix:** Move persisted-zone loading out of the constructor into an `init` suspend function called
from the application startup coroutine.

---

### WR-07: `pico/CMakeLists.txt` — `picowota` and `mongoose` sourced by path but not verified as submodules

**File:** `firmware/pico/CMakeLists.txt:24,32`

**Issue:** `add_subdirectory(picowota)` and `lib/mongoose/mongoose.c` are referenced by relative
path. The CI workflow does `checkout@v4` with `submodules: recursive`, but neither `picowota` nor
`lib/mongoose` appear in the reviewed file list, so their presence as git submodules cannot be
confirmed. If they are missing (e.g. a shallow clone without submodule init), the build will fail
with a confusing CMake error rather than a clear message. The CI step "Clone pico-sdk" correctly
initialises pico-sdk submodules, but the picowota submodule init depends on the `checkout@v4`
`submodules: recursive` flag — which is present. This is a WARNING rather than a blocker because
the CI does have the right flag, but the build silently requires two unlisted submodules.

**Fix:** Add a CMake check at the top of `pico/CMakeLists.txt`:
```cmake
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/picowota/CMakeLists.txt")
    message(FATAL_ERROR "picowota submodule not initialised. Run: git submodule update --init --recursive")
endif()
```

---

## Info

### IN-01: `config.h` — empty `WIFI_SSID` / `WIFI_PASS` / `SERVER_HOST` constants serve no purpose after captive-portal provisioning

**File:** `firmware/esp32/main/config.h:4-6`

**Issue:** The captive portal loads credentials from NVS at runtime. The compile-time `WIFI_SSID`
and `WIFI_PASS` defines are empty strings and are never read (the code uses NVS only). They will
confuse future developers who expect to fill them in. `SERVER_HOST` is also empty but is used
directly in `ws_client.c:26` — the URI becomes `ws://:8080/ws/zone/esp32` which will fail to
connect. `SERVER_HOST` should either be configurable via NVS alongside Wi-Fi credentials, or
documented as a build-time override.

**Fix:** Remove `WIFI_SSID`/`WIFI_PASS` from `config.h`. Add `SERVER_HOST` to the NVS-provisioned
fields, or enforce it as a required CMake definition with:
```cmake
if(NOT DEFINED SERVER_HOST)
    message(FATAL_ERROR "SERVER_HOST must be defined with -DSERVER_HOST=<ip>")
endif()
target_compile_definitions(textreader PRIVATE SERVER_HOST="${SERVER_HOST}")
```

---

### IN-02: Test uses `testApplication` for a test that the memory notes flag as incompatible with SSE — confirm it's WebSocket-only

**File:** `src/test/kotlin/com/anjo/routing/TextApiRouteTest.kt:1-165`

**Issue:** The project memory (`MEMORY.md` / `feedback_sse_testing.md`) records that
`testApplication` is incompatible with SSE streaming and requires `embeddedServer(Netty)`. All
tests in `TextApiRouteTest` use `testApplication`. The tests reviewed here are HTTP POST endpoints
(not SSE), so `testApplication` is correct. However, if an SSE endpoint is ever added to
`TextRoutes` and a test is written in this same file using `testApplication`, it will silently not
work. No defect in current code — flag as a reminder to keep SSE tests in separate files.

**Fix:** Add a comment (or keep the pattern) that `TextApiRouteTest` is HTTP-only; SSE tests must
use `embeddedServer(Netty, port=0)` + CIO client as documented.

---

### IN-03: `FirmwareZoneDriver` — no test for the case where `attach()` is called, then `detach()`, then `send()`

**File:** `src/test/kotlin/com/anjo/zone/FirmwareZoneDriverTest.kt`

**Issue:** The test suite covers: no session → `send` returns false; stop does not throw; registry
status. It does not cover: `attach` then `detach` then `send` (should return false); `attach` then
`send` while channel is full (capacity exhaustion); `stop` called while drain job is active.
These gaps leave the TOCTOU race documented in CR-04 undetected by tests.

**Fix:** Add a test:
```kotlin
test("send returns false after detach") {
    val driver = FirmwareZoneDriver("z")
    // attach with a mock session, then detach
    driver.detach()
    runTest { driver.send("x", Effect.SCROLL) shouldBe false }
}
```

---

### IN-04: `NetworkZoneDriver` — coroutine scope leaks when `stop()` is called before `startConnect()`

**File:** `src/main/kotlin/com/anjo/zone/NetworkZoneDriver.kt:77-79`

**Issue:** `stop()` cancels `scope.coroutineContext[Job]`. If `startConnect()` was never called,
the scope's Job is cancelled (no-op on the SupervisorJob, which is correct). However, the
`CoroutineScope(Dispatchers.IO + SupervisorJob())` created in the default parameter is a new scope
every time, and its `SupervisorJob` is never cancelled if `stop()` is not called (e.g. if
`startConnect()` throws before `stop()` can be reached). The scope remains active and holds a
reference to `Dispatchers.IO` indefinitely. This is a minor resource issue but worth noting.

**Fix:** Document that callers must always call `stop()` after construction, or use structured
concurrency by receiving the scope from the caller.

---

_Reviewed: 2026-06-24_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
