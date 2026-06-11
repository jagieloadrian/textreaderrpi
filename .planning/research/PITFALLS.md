# Domain Pitfalls — TextReaderRpi v1.1

**Domain:** Embedded IoT / Kotlin/Ktor/Pi4J on Raspberry Pi 4
**Researched:** 2026-06-11
**Confidence:** HIGH (code-grounded — all pitfalls traced to actual code in the repo)

---

## Critical Pitfalls

Mistakes that cause rewrites, hardware damage, or silent data corruption.

---

### Pitfall 1: MAX7219 SPI Packet Layout — Wrong Daisy-Chain Direction

**Area:** MAX7219 chain order fix

**What goes wrong:**
In the current `render()` method (`Max7219Matrix.kt` lines 141–158), device index `d=0` is placed at `packet[0..1]` and `d=1` at `packet[2..3]`. The SPI MOSI line shifts data through the chain serially: the last bytes clocked in land in the physically-first module. If the physical board nearest to the Pi is module 0 but the code indexes from the far end, the display reads right-to-left. Swapping index order without also reversing the column-bitmap slice produces garbled output, not just mirrored text.

**Why it happens:**
MAX7219 daisy-chain topology means the first `spi.write()` byte reaches the last module in the physical chain. `numDevices * 2` bytes are clocked in one shot; the "first" module in the packet array actually controls the last physical device. This is counter-intuitive and the current code's comment `// d * 2` gives no hint of direction.

**Consequences:**
- Text scrolls from right to left (visually reversed at the module level)
- A naïve index-swap `(numDevices - 1 - d)` in `render()` fixes module order but leaves column-byte bit order intact, which may still produce mirrored glyphs if `columnByte` was built MSB-first for the old layout
- The bug is invisible in single-module builds; only surfaces at `numDevices >= 2`

**Warning signs:**
- Scroll animation starts on the wrong physical panel
- Static text from `displayStatic()` shows characters backwards across module boundary
- Unit tests pass (they mock `spi.write()` and check byte arrays, not physical output)

**Prevention:**
1. Before code: physically label module 0 (the one with DIN connected to the Pi SPI MOSI pin) vs module N-1 (the one with DOUT floating)
2. Write a standalone SPI probe that lights a single known LED (`register=ROW_1, data=0x80`) and observes which physical panel responds — confirms which packet index maps to which physical device
3. Change `render()` to iterate `for (d in numDevices - 1 downTo 0)` when building the packet, keeping the `globalCol` offset unchanged — do NOT touch `buildBitmap()`
4. Add a `Max7219ChainOrderTest` with a known bitmap and assert that `packet[0]` targets the last physical device (register byte) and `packet[numDevices*2-1]` targets the first

**Phase to address:** Phase 1 (MAX7219 hardware fix) — must be resolved before multi-zone work, otherwise multi-zone will amplify the wrong ordering across all chains

---

### Pitfall 2: Coroutine Scheduler — Duplicate Job Launch on App Restart

**Area:** Coroutine scheduler replacing Flaxoos JDBC

**What goes wrong:**
`SchedulerService.start()` calls `repository.findAllActive()` and schedules every ACTIVE record. If the app crashes mid-run and restarts (common on Pi after power glitch), ONESHOT schedules that fired but whose `updateStatus("DONE")` call failed before the crash still have `status=ACTIVE`. They re-fire on restart. The current code at line 80 (`filter { !activeJobs.containsKey(it.id) }`) only deduplicates in-memory; it cannot detect "already fired before crash."

**Why it happens:**
The fire-then-update pattern in `launchOneShot()` (lines 104–106) is not atomic. The display fires, then the DB update happens asynchronously. A crash between those two steps leaves the schedule looking ACTIVE.

**Consequences:**
- ONESHOT schedules play their text 2+ times after Pi reboots
- CRON schedules may have a backlog: if the Pi was off for 12 hours and a `* * * * *` CRON exists, `nextExecution()` from `ZonedDateTime.now()` will compute correctly (cron-utils skips past times), but any backfill logic added later can misfire
- RECURRING schedules restart their interval from zero, potentially firing earlier than expected

**Warning signs:**
- Logs show `"Loaded N active schedule(s)"` on restart for schedules that should have been DONE
- Display shows old text immediately on reboot with no user action
- `activeJobs` map grows beyond expected size after multiple restarts

**Prevention:**
1. Add a `firedAt` column to `SchedulesTable` — set it atomically in the same `suspendTransaction` as the `status=DONE` update inside `launchOneShot()`; on restart, treat `firedAt != null AND status = ACTIVE` as stale and skip or mark DONE
2. For ONESHOT: if `targetMs < System.currentTimeMillis()` at startup (past-due), skip re-firing and immediately mark DONE — add this guard before the `delay()` call
3. Wrap the `fire(schedule)` + `repository.updateStatus(...)` pair in a single transaction with a flag column rather than two separate suspend calls
4. Add an integration test that simulates crash-then-restart using an in-memory H2: insert ACTIVE ONESHOT with past triggerValue, call `start()`, assert fired exactly once

**Phase to address:** Phase 2 (scheduler rewrite) — this is the highest-risk correctness issue in the entire milestone

---

### Pitfall 3: Coroutine Scheduler — CRON `launchCron` Infinite Loop on Bad Expression

**Area:** Coroutine scheduler / CRON parsing edge cases

**What goes wrong:**
`launchCron()` (lines 140–151) calls `ExecutionTime.forCron(cron).nextExecution(ZonedDateTime.now()).orElseThrow()`. If `nextExecution()` returns an empty Optional (can happen with certain cron-utils edge cases — e.g. a February 30th expression, or a timezone offset that makes no valid next time calculable), `orElseThrow()` throws, the catch block `break`s the loop, and the job silently dies. The schedule remains ACTIVE in the database forever. The 60-second `tickLoop` will re-schedule it next tick, re-launch the job, it dies again — CPU spin every 60 seconds.

**Why it happens:**
The `break` on exception exits only the coroutine's while loop. The schedule is never marked DONE or ERROR. `tickLoop` finds it ACTIVE and re-schedules it.

**Consequences:**
- CPU burn: a pathological CRON expression causes a new coroutine launch + exception every 60 seconds indefinitely
- Memory: each failed launch leaks a coroutine object until GC collects it (SupervisorJob prevents propagation, so the parent scope stays alive)
- On a Pi with `<256MB` JVM heap, this can trigger GC pressure that degrades scroll animation smoothness

**Warning signs:**
- Log floods with `"Could not compute next execution for cron schedule <id>"`
- `activeJobs.size` oscillates around the same value rather than being stable
- Pi CPU load climbs slowly over hours

**Prevention:**
1. In the catch block of `launchCron`, call `repository.updateStatus(schedule.id, "ERROR")` before breaking — this prevents `tickLoop` from re-scheduling
2. Add a `status=ERROR` to `ScheduleStatus` enum and handle it in `findAllActive()` (exclude ERROR status)
3. Add a CRON validation step at schedule creation time in `ScheduleValidators.kt` — parse the expression with cron-utils and return 422 if `nextExecution()` from now returns empty
4. Test: create a schedule with `triggerValue = "0 0 31 2 *"` (Feb 31), assert it gets status ERROR, assert it is not re-scheduled after 60 seconds

**Phase to address:** Phase 2 (scheduler rewrite) — add `ERROR` status before writing any new scheduler code

---

### Pitfall 4: Multi-Zone — Pi4J Rejects Duplicate SPI Hardware Registration

**Area:** Multi-zone displays / GPIO/SPI resource conflicts

**What goes wrong:**
Pi4J 4.x maintains a hardware registry keyed by provider + bus + chip-select identifiers. If two `Max7219Matrix` instances are created for the same bus/CS combination (e.g. two zones, same SPI bus), Pi4J throws `ProviderException: I/O already registered` on the second `ctx.create(config)` call. The `DisplaySelectionService.driverCache` (line 60) already handles the single-driver case by caching on `displayType` string. Multi-zone bypasses this cache because zones are indexed differently.

**Why it happens:**
`Max7219Matrix.init` block calls `ctx.create(config)` unconditionally. The current `driverCache` key is the display type string (e.g. `"MAX7219"`), so a second zone with a different zone ID but the same hardware config hits `ctx.create()` again with identical bus/CS parameters.

**Consequences:**
- App startup crashes with Pi4J exception when zone 2 initializes
- Even if zones use different chip-selects (CS_0, CS_1), the SPI buffer is shared at the Linux kernel level — concurrent `spi.write()` calls from two coroutines interleave bytes on the wire, producing garbage on both displays
- I2C-based zones (LCD, OLED on the same bus, different addresses) do NOT have this Pi4J registration problem but still have concurrent-write corruption risk

**Warning signs:**
- `"Registration already exists"` or `"I/O already registered"` in startup logs
- Two zones display each other's partial frames (pixel flickering between zones)
- Pi4J `ctx.registry().all()` shows fewer entries than expected zones

**Prevention:**
1. Give each zone its own Pi4J context (`Pi4J.newAutoContext()` per zone) OR use a single context with unique IDs — ensure the `Spi.newConfigBuilder().id()` call uses a unique zone-scoped ID (e.g. `"max7219-zone-0"`, `"max7219-zone-1"`)
2. Each zone's `Max7219Matrix` must use a different `SpiChipSelect` (CS_0, CS_1) — physical wiring and software config must agree
3. Wrap all `spi.write()` calls in a zone-level `Mutex` — the existing `ScreenDriverService.displayMutex` protects at the service level but a multi-zone refactor may introduce multiple services, each needing their own mutex
4. For concurrent zones, serialize all SPI writes through a single `Dispatchers.IO`-backed `Mutex` shared across zone instances — never let two zones write to the same SPI bus concurrently
5. Test on dev machine with `OfflineDisplayDriver` before touching Pi hardware — add a `FakeMultiZoneSpiDevice` test double that asserts no byte interleaving

**Phase to address:** Phase 3 (multi-zone) — architectural decision (single Pi4J context vs multiple) must be made before writing any zone management code

---

### Pitfall 5: Webhook Outbound HTTP — Blocking Ktor's Event Loop with `runBlocking`

**Area:** Webhook outbound HTTP

**What goes wrong:**
Webhooks fire inside `SchedulerService.fire()` which runs in `Dispatchers.Default`. If webhook HTTP calls use Ktor's `HttpClient` correctly with `suspend` functions this is safe, but there is a common mistake: using `OkHttp` or Java's `HttpURLConnection` synchronously inside a suspend function with `withContext(Dispatchers.IO)` forgotten. Worse: if the webhook call is added directly inside `launchCron()`'s while loop after `fire(schedule)` without a timeout, a hanging remote endpoint causes the coroutine to suspend indefinitely, blocking that slot in `Dispatchers.Default`.

**Why it happens:**
The scheduler scope uses `Dispatchers.Default + SupervisorJob()`. Blocking a Default dispatcher thread for 30 seconds (default HTTP timeout) starves other scheduled display operations. On a Pi 4, `Dispatchers.Default` defaults to 4 threads (one per core) — three hung webhooks block 75% of scheduling capacity.

**Consequences:**
- Display updates stall while waiting for a misconfigured webhook URL
- If the Pi loses internet access (home network only — this is normal), every webhook-bearing schedule silently hangs until socket timeout
- A retry storm: if retry logic is added naïvely inside `fire()`, each retry multiplies the hang duration

**Warning signs:**
- Schedule fire-to-display latency grows over time
- Log shows `"Firing schedule"` but no corresponding display event for seconds
- `activeJobs.size` climbs because timed-out webhook coroutines are not completing
- Pi CPU idles while threads sit blocked on socket I/O

**Prevention:**
1. Use Ktor `HttpClient` (already in the dependency tree) with `withTimeout(5000)` wrapping every outbound webhook call — never plain Java HTTP
2. Make webhook delivery fire-and-forget: launch a separate coroutine in an `IOScope` (separate from the scheduler scope) so a failed/slow webhook never blocks display rendering
3. Validate webhook URLs at schedule creation time — reject non-HTTP/HTTPS schemes with 422 at the API layer before the schedule is persisted
4. Add a webhook-specific circuit breaker: track consecutive failures per URL in memory; after 3 failures, skip delivery for 60 seconds and log a warning
5. Test: mock the HttpClient with a 10-second delay; assert that `scrollText` on the main display fires within 1 second regardless of webhook latency

**Phase to address:** Phase 4 (webhooks) — the fire-and-forget architecture decision must be established in the service layer design before any HTTP client code is written

---

## Moderate Pitfalls

---

### Pitfall 6: Display History — Unbounded H2 File Growth on Embedded Pi

**Area:** Display history / H2 file locking

**What goes wrong:**
H2 in file mode (the default for `./data/textreader.mv.db`) has two problems at scale. First, it does not auto-vacuum — deleted rows leave dead pages. Second, H2 uses a file-level write lock via its own locking mechanism; under concurrent writes from `SchedulerService` (via `ScheduleRepository`) and a new `HistoryRepository`, H2 can throw `"Database may be already in use"` if a second JVM process (unlikely but possible during Docker restart overlap) opens the same file.

**Why it happens:**
The current `DatabaseFactory.init()` sets `isAutoCommit = false` and `maximumPoolSize` from config. If `history_events` rows are inserted on every display render (potentially every 80ms during scroll), the table grows by millions of rows per day. H2 file size on a Pi's SD card (or eMMC) is a finite, write-cycle-limited resource.

**Consequences:**
- SD card fills up silently; next H2 write throws `IOException: No space left on device`, crashing the JVM
- HikariCP pool exhaustion if history inserts are slow and pile up: pool waits block `Dispatchers.IO`, same starvation as Pitfall 5
- H2 file corruption on Pi power loss mid-write (H2 does write WAL, but SD cards with write caching enabled can drop the WAL)

**Warning signs:**
- `du -sh ./data/*.mv.db` grows by >10MB/day
- Logs show `"connection is not available"` from HikariCP
- `/health/ready` starts returning 503 as DB pool saturates

**Prevention:**
1. Insert history records only on `displayImmediate()` and scheduler `fire()` calls — not on every scroll frame tick; one row per user-visible text event (estimated ~10-50/day for home use)
2. Add a scheduled cleanup job: `DELETE FROM display_history WHERE created_at < NOW() - INTERVAL '30 days'` — run daily via a coroutine in `SchedulerService` or a separate `MaintenanceService`
3. Add a configurable `DISPLAY_HISTORY_MAX_ROWS` env var (default 1000); enforce in `HistoryRepository.insert()` with a `DELETE` of the oldest row when count exceeds the cap — cheap for H2
4. For pagination: always use `LIMIT`/`OFFSET` or keyset pagination on `created_at DESC` — never load all rows for the history UI page
5. Test: insert 10,000 history rows in-test, assert that the cleanup job reduces count to ≤ `maxRows`, assert that pagination returns the correct page

**Phase to address:** Phase 5 (display history) — define the retention policy before writing `HistoryRepository`

---

### Pitfall 7: Full App Refactor — DI Rewiring Breaks Existing Tests Silently

**Area:** Full app refactor alongside feature work

**What goes wrong:**
The project uses Ktor's built-in `io.ktor.server.plugins.di` (`dependencies { provide { ... } }` in `DependencyInjection.kt`). This DI system resolves by type at call sites. During refactor, if a service interface is extracted (e.g. splitting `SchedulerService` into `SchedulerService` + `WebhookDispatcher`) and the old concrete class is still `provide`d but the new interface is what route handlers depend on, the DI resolution fails at runtime — not at compile time. Tests that use `testApplication { }` will throw `io.ktor.server.plugins.di.DependencyNotResolvedException` only when the specific route under test is hit.

**Why it happens:**
Ktor DI resolves lazily at first call. A refactor that adds `provide<WebhookDispatcher>()` but forgets to remove or re-key the old `provide { schedulerService }` compiles cleanly and most tests pass — only routes that inject the new type fail.

**Consequences:**
- Test suite shows 70%+ coverage but 2-3 test classes fail silently (unrelated routes don't exercise the broken injection point)
- JaCoCo coverage threshold (70% LINE) can still be met with broken injection in untested routes
- Production app crashes on first hit to the new route after deploy to Pi

**Warning signs:**
- `DependencyNotResolvedException` only in specific test classes, not universally
- Green CI but runtime crash on Pi on first browser navigation to the new page
- `configureDI()` function grows beyond 20 `provide` calls — sign of untracked dependencies

**Prevention:**
1. After every DI change, run the full test suite (`./gradlew test`) — do not run individual test classes during refactor
2. Write a `DependencyInjectionSmokeTest` that starts `testApplication` and hits every registered route (GET / , GET /schedule, GET /status, GET /settings/display, POST /api/v1/text) — this catches injection failures across all routes in one test
3. Keep `configureDI()` as the single source of truth: remove old `provide` calls in the same commit that adds new ones — never leave orphaned provides
4. During refactor, mark new service interfaces with `// DI: provide<InterfaceName>()` comments in the interface file as a checklist reminder

**Phase to address:** Phase 6 (refactor) — smoke test must exist before any DI changes are made

---

### Pitfall 8: UI Refresh — Ktor HTML DSL CSS Leaks Across Pages via Shared BaseLayout

**Area:** UI refresh with Ktor HTML DSL

**What goes wrong:**
`BaseLayout.render()` inlines a CDN link to PicoCSS 2 in the `<head>` for every page. Any new CSS added during UI refresh (inline `<style>` blocks in page templates like `IndexPage`, `SchedulePage`) bleeds into other pages because `BaseLayout` is shared. PicoCSS 2's CSS custom properties (`--pico-*`) apply globally; overriding a variable in `IndexPage`'s inline style affects every element on every page that renders after it in the same browser session (CSS cascade, not server-side — but the mental model error is common).

**Why it happens:**
Ktor HTML DSL encourages inline DSL composition. Developers add `style { ... }` blocks inside page-specific lambdas without realizing those styles are emitted into the `<body>`, not scoped to a shadow DOM. PicoCSS semantic selectors (`article`, `section`, `form`) are very broad and a single rule override in one page can visually break another.

**Consequences:**
- A form style fix on `/schedule` makes the text input on `/` look broken
- The 404/500 `ErrorPage` uses `BaseLayout` indirectly (via `ErrorPage.render()` which presumably also uses the layout) — a broken CSS variable can make error pages unreadable at the exact moment they are most needed
- No automated test catches visual regressions in Ktor HTML DSL output

**Warning signs:**
- Manual browser test on one page looks fine; opening another tab with a different page shows visual breakage
- CSS changes require testing all 5 pages manually after every change

**Prevention:**
1. Scope all page-specific CSS in a `<style>` block placed inside the `<head>` via `BaseLayout` — add a `headExtra: (HEAD.() -> Unit)? = null` parameter to `BaseLayout.render()` and pass page CSS through it, not as body-level `<style>` tags
2. Use PicoCSS scoping classes rather than overriding global variables — wrap page-specific content in a `<div class="page-history">` and target `.page-history article { ... }` in the scoped CSS
3. Add a `HtmlDslRenderTest` per page that calls `IndexPage().render()`, `SchedulePage(...).render()`, etc. and asserts that the HTML string does not contain `<style` outside the `<head>` section — catches inline body-level style blocks
4. Never set `--pico-*` CSS custom properties on `:root` in page templates — only set them in `BaseLayout`'s `<head>` if needed globally

**Phase to address:** Phase 7 (UI refresh) — establish the `headExtra` pattern in `BaseLayout` before any page-specific styles are added

---

## Minor Pitfalls

---

### Pitfall 9: `ScreenDriverService.driver` Race Condition During Multi-Zone Refactor

**Area:** Multi-zone / concurrent hardware access

**What goes wrong:**
`ScreenDriverService` holds `private var driver: DisplayDriver` (line 22 in `ScreenDriver.kt`). During multi-zone work, this single `driver` field will likely be replaced with a zone map. The interim state — where `driver` is reassigned in `checkAndPerformPendingSwitch()` while `executeWithRecovery()` holds a local reference — creates a window where one zone's write uses a stale driver reference. This is not a crash; it is a silent wrong-zone write.

**Prevention:**
Introduce a `@Volatile private var driver` annotation (currently absent) as an immediate fix for the single-driver case. For multi-zone, replace the field with an immutable map snapshot fetched inside `displayMutex.withLock` — never read the zone map outside the lock.

**Phase to address:** Phase 3 (multi-zone) — add `@Volatile` to `driver` in Phase 6 (refactor) as a preparatory fix

---

### Pitfall 10: `scrollText()` Launches Coroutines Outside `ScreenDriverService.displayMutex`

**Area:** Multi-zone / concurrent hardware access

**What goes wrong:**
`DisplayDriver.scrollText(scope, text, speedMs)` accepts an external `CoroutineScope`. In `ScrollEffect.render()` and `FadeEffect.render()`, `coroutineScope { driver.scrollText(this, text) }` passes the effect's coroutine scope. When multi-zone introduces parallel rendering, two zones can both call `scrollText` and both launch coroutines that call `spi.write()` concurrently. The `displayMutex` in `ScreenDriverService` does not protect the inner coroutine launched by `scrollText` — it only protects the `executeWithRecovery` call that starts the effect.

**Prevention:**
Move the `Mutex` into `Max7219Matrix` itself — a per-driver, per-row lock. Alternatively, for multi-zone, change the `scrollText` API to be fully `suspend`-based (removing the `scope` parameter) so the mutex can wrap the entire scroll loop from outside.

**Phase to address:** Phase 3 (multi-zone) — this is an API design decision for the driver refactor

---

### Pitfall 11: `H2` File Mode Incompatible with Docker Volume Race on Pi

**Area:** Display history / H2 file locking

**What goes wrong:**
Docker restarts on Pi (e.g. `docker restart textreaderrpi`) give the old container a SIGTERM and start a new one. If the new container's JVM starts before the old container's H2 file lock is released, H2 throws `"Database may be already in use: <path>. Possible solutions: ..."`. The app starts in a degraded state with no DB.

**Prevention:**
Add `DB_CLOSE_ON_EXIT=FALSE` and `DB_CLOSE_DELAY=-1` to the H2 JDBC URL. Set Docker's `stop_grace_period` to at least 5 seconds in docker-compose.yml (or equivalent). The correct URL pattern is: `jdbc:h2:file:/data/textreader;AUTO_SERVER=TRUE` — `AUTO_SERVER=TRUE` allows the new process to reconnect to the same file even if the old lock is stale.

**Phase to address:** Phase 5 (display history) — set the JDBC URL correctly before history inserts begin writing

---

### Pitfall 12: `tickLoop` 60-Second Blind Spot for New Schedules

**Area:** Coroutine scheduler

**What goes wrong:**
When a new schedule is created via `POST /api/v1/schedule`, the route calls `schedulerService.schedule(it)` directly (bypassing `tickLoop`). This is correct. But if that direct call throws (e.g. invalid triggerValue that passed validation but fails inside `launchCron`), the schedule is persisted as ACTIVE and `tickLoop` will re-try it 60 seconds later — the user sees a silent 60-second delay before the error is logged.

**Prevention:**
Have `SchedulerService.schedule()` return a `Result<Job>` (or throw) rather than returning `null` silently. Surface scheduling errors at the API layer as a 422 or 500 immediately, rather than deferring to `tickLoop`.

**Phase to address:** Phase 2 (scheduler rewrite)

---

## Phase-Specific Warning Summary

| Phase Topic | Highest-Risk Pitfall | Mitigation Priority |
|---|---|---|
| MAX7219 chain fix (Phase 1) | Packet index swap without bit-order check | Probe single LED before full render fix |
| Scheduler rewrite (Phase 2) | Duplicate ONESHOT on crash-restart | `firedAt` column + past-due skip guard |
| Scheduler rewrite (Phase 2) | CRON infinite re-schedule loop | Add `ERROR` status, validate at creation |
| Scheduler rewrite (Phase 2) | `schedule()` silent null return | Return `Result<Job>`, surface at API |
| Multi-zone (Phase 3) | Pi4J duplicate SPI registration crash | Unique Pi4J IDs per zone before init |
| Multi-zone (Phase 3) | Concurrent `spi.write()` byte interleave | Per-driver Mutex, finalize before wiring |
| Multi-zone (Phase 3) | `driver` field not volatile | Add `@Volatile` in refactor phase |
| Webhooks (Phase 4) | Blocking `Dispatchers.Default` on slow HTTP | Fire-and-forget IOScope + `withTimeout` |
| Webhooks (Phase 4) | Retry storm on unreachable URL | Circuit breaker, validate URL at insert |
| Display history (Phase 5) | Unbounded H2 table growth | `MAX_ROWS` cap + retention cron job |
| Display history (Phase 5) | H2 Docker restart file lock | `AUTO_SERVER=TRUE` in JDBC URL |
| Refactor (Phase 6) | DI rewiring silent failures | Smoke test all routes before DI changes |
| UI refresh (Phase 7) | CSS bleeding across pages | `headExtra` parameter in BaseLayout |

## Sources

All findings are grounded in direct code inspection of the following files:
- `src/main/kotlin/com/anjo/driver/Max7219Matrix.kt` — SPI packet layout, `render()`, `sendCommand()`
- `src/main/kotlin/com/anjo/service/SchedulerService.kt` — `launchOneShot`, `launchCron`, `tickLoop`
- `src/main/kotlin/com/anjo/service/ScreenDriver.kt` — mutex model, driver field, `executeWithRecovery`
- `src/main/kotlin/com/anjo/service/DisplaySelectionService.kt` — driverCache, Pi4J context, SPI registration
- `src/main/kotlin/com/anjo/db/DatabaseFactory.kt` — HikariCP config, H2 file mode
- `src/main/kotlin/com/anjo/db/SchedulesTable.kt` — schema, no `firedAt` column, no `ERROR` status
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` — DI wiring, provide calls
- `src/main/kotlin/com/anjo/web/templates/BaseLayout.kt` — shared layout, CSS inclusion
- `src/main/kotlin/com/anjo/service/effect/EffectRenderer.kt` — scrollText scope passing pattern
- `build.gradle.kts` — Pi4J 4.x, Ktor DI, cron-utils, H2, Exposed 1.3.0 dependency versions
