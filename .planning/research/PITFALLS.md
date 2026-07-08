# Domain Pitfalls — TextReaderRpi v1.2

**Domain:** Kotlin/Ktor Raspberry Pi display system — adding features to shipped v1.1
**Researched:** 2026-06-21
**Scope:** Pitfalls specific to v1.2 additions in the context of the existing codebase

---

## 1. WebSocket Live Feed

### CRITICAL — Frame Send from Wrong Coroutine Context

**What goes wrong:** The display effect pipeline runs on `Dispatchers.IO` inside `displayScope` in `ScreenDriverService`. When a live feed broadcast is wired to emit a `Frame.Text` to all open WebSocket sessions, the emission happens on the IO thread. Ktor's `DefaultWebSocketSession` is not thread-safe — concurrent `send()` from multiple coroutine contexts on the same session causes `IllegalStateException: Already started` or silent frame drops under Netty.

**Why it happens:** `displayImmediate()` launches in `displayScope` (SupervisorJob + ioDispatcher). If a WebSocket broadcast is called inside that scope, it races with Ktor's own WebSocket pump which runs on a different coroutine context.

**Prevention:** All session sends must happen through a dedicated `Channel<String>` consumed by a single coroutine per session. The display path only calls `channel.trySend(text)` (non-blocking); the per-session consumer serialises all outgoing frames on its own coroutine. Never call `session.send()` directly from `displayScope`.

**Detection:** Frame drops are silent on the client. Test by sending rapid display requests while two browsers have the live feed open — missing updates reveal the race.

---

### CRITICAL — Unbounded Session Accumulation

**What goes wrong:** `DefaultWebSocketSession` objects accumulate in a `MutableList` or `ConcurrentHashMap` when browsers connect. On page refresh, the old session is not closed immediately (browser TCP FIN can be delayed). A coroutine iterating and calling `send()` on a closed session throws `ClosedSendChannelException`. If that exception is not caught per-session, the broadcast function terminates and all subsequent live updates stop for all connected clients.

**Prevention:** Store sessions in a `ConcurrentHashMap<String, DefaultWebSocketSession>` keyed by a UUID assigned on connect. Wrap each `session.send()` in `runCatching` and remove the session on failure. Call removal inside `finally` of the WebSocket handler so page refresh and closed tabs always clean up.

**Detection:** Session count should drop to 0 when browser tabs close. Add a sessions-connected metric visible at `/health/detail` or a new counter in `HardwareMetrics`.

---

### MODERATE — Live Feed Bypasses Rate Limiting

**What goes wrong:** The existing API rate limiting (`installApiRateLimiting`) applies only inside `route("/api/v1") { ... }` per `Routing.kt`. A WebSocket endpoint at `/ws` placed outside that block gets no rate limiting. A single client can hold many connections simultaneously, each receiving every display update, amplifying I/O load on the Pi's 4-thread `Dispatchers.IO` pool.

**Prevention:** Enforce a per-IP connection limit (max 3) at WebSocket upgrade time using a `ConcurrentHashMap<String, AtomicInteger>` keyed by remote IP. Reject the upgrade with `429 Too Many Requests` when the limit is exceeded. This is separate from the Flaxoos token-bucket rate limiter and must be implemented in the WebSocket route itself.

---

### MINOR — testApplication Lifecycle Order for WebSocket Tests

**What goes wrong:** Ktor's `testApplication` defers module execution until first HTTP interaction (established in v1.1 Phase 8). WebSocket tests that call `client.webSocket { ... }` before any `client.get()` encounter `MissingDependencyException` if dependencies are resolved at route installation time.

**Prevention:** Insert `client.get("/health")` before any `client.webSocket { ... }` call in tests. Use Kotest `FunSpec` native suspend (not `runTest`) to avoid virtual-clock conflicts with WebSocket ping intervals — same pattern established in `WebhookServiceTest`.

---

## 2. Dynamic Zone Creation

### CRITICAL — ConcurrentHashMap Insertion Is Not Atomic

**What goes wrong:** `ZoneRegistry.zones` is a `ConcurrentHashMap`. `addNetworkZone()` performs a read (`zones[zone.id]?.isLocal`) followed by a separate write (`zones.put(...)`). Two concurrent POST requests for the same zone ID can both pass the `isLocal` guard and both create a `NetworkZoneDriver`, resulting in two reconnect loops for the same IP. The existing `zones.put(...).stop()` idiom mitigates the second registration but the guard-then-insert window is unguarded between the two separate `ConcurrentHashMap` operations.

**Prevention:** Replace the read-then-write with `ConcurrentHashMap.compute()` for atomic check-and-set. The `addNetworkZone(zone, client)` method is short enough that the lock hold time is negligible.

---

### CRITICAL — Pi4J Context Cannot Safely Create SPI Devices After Startup for Local Zones

**What goes wrong:** `Pi4J.newAutoContext()` is called once in `configureDI()`. Pi4J 4.0.0 allows creating new `Spi` devices after startup as long as unique string IDs are used (the v1.1 decision). However, on some RPi OS kernel versions, the `gpiod` plugin throws `IOException: failed to claim GPIO lines` when a second `Spi` device is added to an already-claimed SPI bus after startup. This is a hardware-level constraint. The Pi4J mock plugin does not reproduce this — it will succeed in tests and fail silently on hardware.

**Prevention:** Scope dynamic creation to NETWORK zones only (connecting via WebSocket to a remote Pi). Local hardware zones remain static and require a restart to add. When a POST request arrives requesting a local type (`MAX7219`, `LCD`, `OLED`) as a new dynamic zone, return `422 Unprocessable Entity` with a clear explanation rather than accepting and failing at the Pi4J layer.

---

### MODERATE — DB Zone Survives Restart But Config YAML Must Not

**What goes wrong:** `ZoneRepository.upsert()` persists network zones to `network_zones` table. On restart, `ZoneRegistry` re-creates `NetworkZoneDriver` for all persisted rows. If a developer also edits `application.yaml` to add the same zone as a static entry, the YAML zone initialises first as a local zone and blocks the DB zone from loading (the `isLocal` guard in `addNetworkZone`). This creates a silent discrepancy between what the API returns and what the user configured.

**Prevention:** Dynamic zone creation via API writes only to the `network_zones` DB table. `application.yaml` is exclusively for static local hardware zones. Document this invariant in the Helm chart and README. The API response for a dynamically created zone must include a reminder that it is not reflected in `application.yaml`.

---

### MODERATE — DI Smoke Test Must Be Updated Before New Bindings Are Added

**What goes wrong:** `ApplicationTest` asserts all DI bindings by type. Adding a new DI binding (e.g., a `LiveFeedBroadcaster` service) without updating the smoke test leaves the test incomplete. Providing two instances of the same type causes Ktor DI to use the last-registered one, silently breaking the service that expected the first.

**Prevention:** Apply the Phase 8 rule: update the DI smoke test before writing any new service code. Each new service uses its own concrete class as the DI key. Never provide two instances of the same type — use a distinct wrapper class if two instances of the same base type are needed.

---

## 3. Full-Text Search

### CRITICAL — H2 Has No Native FTS; PostgreSQL-Specific DDL in Migrations Breaks H2

**What goes wrong:** H2 in PostgreSQL compatibility mode supports `LIKE '%term%'` but has no `tsvector`/`tsquery` equivalent. Implementing search as `LIKE` works in H2. If a developer adds a Flyway migration (`V6__add_fts_index.sql`) with `CREATE INDEX CONCURRENTLY` or a `GIN` index for PostgreSQL, Flyway migration fails on H2 and breaks local dev startup. The `LIKE` query on H2 performs a full table scan every call, which is acceptable at the 1000-row cap.

**Prevention:** Implement search using `HistoryTable.text.lowerCase().like("%${term.lowercase()}%")` in Exposed's database-agnostic DSL. Do not add a PostgreSQL-specific FTS migration for v1.2 — the 1000-row cap makes the scan fast enough. If PostgreSQL FTS is ever added, gate the migration with `IF NOT EXISTS` and test explicitly against both H2 and PostgreSQL in CI.

---

### MODERATE — N+1 Query When Fetching Zone Names Alongside Results

**What goes wrong:** If search results display zone name alongside text, a naive implementation calls `zoneRepository.findById(record.zoneId)` for each result row separately. With 1000 rows and a zone-filtered search returning 50 results, that is 50 DB round trips.

**Prevention:** `HistoryTable` already stores `zoneId` as a varchar column and it is already present in `HistoryRecord`. The zone ID is the zone name in this system. No join is needed for display purposes — use the `zoneId` field directly from `HistoryRecord`.

---

### MINOR — LIKE Wildcard Characters in Search Term

**What goes wrong:** `%` and `_` are LIKE wildcard characters in SQL. A search for `100%` or `test_case` matches unintended rows. Exposed does not escape LIKE wildcards automatically.

**Prevention:** Escape `%` to `\%` and `_` to `\_` in the search term before the `like()` call. Place this logic in a `SearchValidators` object that runs via `RequestValidation` plugin — never inline it in the route handler, consistent with the project rule that validation logic belongs in the validator layer.

---

## 4. CSV Export

### CRITICAL — Missing Content-Disposition Header Causes Inline Rendering

**What goes wrong:** `call.respondText(csv, ContentType.Text.Plain)` makes browsers display the CSV inline instead of prompting a download. Setting `ContentType("text", "csv")` alone is not sufficient — the `Content-Disposition: attachment; filename="history.csv"` header must be set explicitly.

**Prevention:**
```kotlin
call.response.header(
    HttpHeaders.ContentDisposition,
    ContentDisposition.Attachment
        .withParameter(ContentDisposition.Parameters.FileName, "history.csv")
        .toString()
)
call.respondText(csv, ContentType("text", "csv"))
```
The header must be set before `respondText` is called.

---

### CRITICAL — Buffer-All vs. Stream: Safe Only at MAX_ROWS=1000

**What goes wrong:** Loading all 1000 `HistoryRecord` objects into a Kotlin `List` then joining as a CSV string is acceptable at 1000 rows under the 256MB heap constraint. The pitfall is coupling — if `MAX_ROWS` is ever increased without changing the export strategy, the export silently begins buffering much larger payloads and can OOM the JVM.

**Prevention:** Add a named constant `MAX_EXPORT_ROWS = MAX_ROWS` that makes the coupling explicit in code. If `MAX_ROWS` ever increases beyond 5000, switch to `call.respondBytesWriter` with chunked cursor reads. Document this threshold in the code so the constraint is visible without needing comments.

---

### MODERATE — Non-ASCII Zone ID in Filename Causes Garbled Download Name

**What goes wrong:** Zone IDs can contain characters beyond basic ASCII after mDNS sanitisation (accented characters, dots). A `Content-Disposition` filename containing non-ASCII characters must be RFC 5987 encoded (`filename*=UTF-8''...`). Ktor's `ContentDisposition.withParameter` passes the value raw without RFC 5987 encoding, which produces garbled filenames in Chrome and Firefox.

**Prevention:** Sanitise the export filename to ASCII: `"history-${zone?.replace(Regex("[^a-zA-Z0-9-]"), "_") ?: "all"}.csv"`. This is only used as a filename — it does not need to match the zone ID exactly.

---

### MINOR — Commas and Newlines in Display Text Break CSV Rows

**What goes wrong:** Display text can contain commas and newlines (no validation prohibits them). A naive `"$text,$effect,$zone"` row breaks when text contains commas or newlines.

**Prevention:** Wrap all text fields in double-quotes and escape internal double-quotes by doubling them per RFC 4180: `"${text.replace("\"", "\"\"")"`. Do not add a CSV library dependency for 1000 rows — implement this as a small local function.

---

## 5. Kotlin Native for Firmware (Pico / ESP32)

### CRITICAL — GC Pauses Break Real-Time Display Timing

**What goes wrong:** Kotlin/Native uses a stop-the-world GC that is unsuitable for real-time display timing. On a Pico running a scrolling effect at 50Hz, a GC pause of 20ms or more causes a visible stutter. The Kotlin/Native memory model improved significantly from 1.7.20 onward but the GC is still not incremental on embedded targets as of Kotlin 2.x.

**Prevention:** Pre-allocate all display buffers at startup. Avoid object allocation in the render loop — use `ByteArray` slices, not `List<Byte>`. Avoid lambda captures in the hot path (each lambda capture boxes primitives in Kotlin/Native unless inlined). Validate GC pause behaviour during development by calling `kotlin.native.internal.GC.collect()` explicitly in a timing harness and measuring pause duration.

---

### CRITICAL — No Built-in WiFi Stack; C Interop Required

**What goes wrong:** Kotlin/Native has no built-in WiFi API. On Pico W (CYW43439), WiFi requires calling the `pico-sdk` C API (`cyw43_arch_wifi_connect_blocking()`). Kotlin/Native's C interop (`cinterop`) can generate Kotlin bindings, but the `pico-sdk` build system is CMake-based and entirely separate from Gradle. There is no unified Gradle task for this as of mid-2026.

**Prevention:** Treat firmware as a separate CMake project inside a `firmware/` subdirectory. The Gradle subproject compiles Kotlin to a `.klib` and invokes CMake via `exec { commandLine(...) }` for the final link + flash step. Do not attempt to unify the build under a single `./gradlew build`. Document the two-step build explicitly in the README.

---

### CRITICAL — Reconnect Loop Without OS Scheduler Blocks Display

**What goes wrong:** On bare-metal (no OS scheduler), Kotlin/Native coroutines use cooperative multitasking. A `while (true) { connectWebSocket(); delay(5000) }` reconnect loop requires a working `delay()` backed by a hardware timer. On Pico without RTOS, `delay()` busy-waits via `sleep_ms()`. This burns CPU and blocks the display render coroutine for the full reconnect delay interval.

**Prevention:** Use a hardware timer interrupt to drive the reconnect schedule rather than a coroutine delay loop. The display render coroutine is the main loop; network reconnect runs as a lower-priority interrupt handler. This is a firmware architecture constraint that must be decided before any firmware code is written.

---

### MODERATE — Effect Timing Requires Dedicated Hardware Timer

**What goes wrong:** The existing `EffectRenderer` on JVM uses `delay(frameMs)` (Kotlin coroutines). On bare-metal MCU, software delay is either a busy-wait or driven by the same timer as the reconnect loop. If the reconnect logic shares the same timer, timing jitter corrupts the scroll animation during reconnect attempts.

**Prevention:** Assign hardware timers per function category: Timer 0 for display refresh, Timer 1 for WebSocket heartbeat, Timer 2 for reconnect schedule. Never share timers between display and network code.

---

### MODERATE — Debugging Requires Platform-Specific Logger Binding

**What goes wrong:** `println()` in Kotlin/Native does not map to USB serial (UART0) on Pico automatically. Without debug output, firmware crashes are invisible — the Pico simply stops. Kotest does not run on bare-metal; unit tests for protocol parsing must be JVM-side, with hardware integration tests requiring physical flashing.

**Prevention:** Define a thin `Logger` interface with a JVM `Slf4j` implementation (for tests, matching the existing pattern in the main codebase) and a `PicoSerial` implementation for firmware. Only the interface lives in shared code; platform implementations are separate. This mirrors the `AbstractDisplayDriver` pattern already established in the main codebase.

---

## 6. Kubernetes + Helm

### CRITICAL — H2 Embedded DB Is Incompatible with Multiple Replicas

**What goes wrong:** H2 in file mode stores data in the container's local filesystem. In Kubernetes, a Pod restart loses all data unless backed by a PVC. With two replicas, each Pod has its own H2 file — schedules and history diverge immediately. H2's `AUTO_SERVER=TRUE` mode allows multiple processes to share one file but that file must be on a shared PVC with a `ReadWriteMany` access mode, which most Pi-local storage drivers do not support.

**Prevention:** The Helm chart must treat PostgreSQL as a hard requirement for multi-replica deployments. Set `DATABASE_DRIVER=org.postgresql.Driver` and `DATABASE_URL` pointing to a PostgreSQL StatefulSet in default `values.yaml`. Include a `postgres` subchart dependency enabled by default. Document that H2 is single-replica only.

---

### CRITICAL — Pi4J Hardware Access Requires Device Mounts in Container

**What goes wrong:** Pi4J 4.0.0 uses the `gpiod` plugin (`/dev/gpiochip0`) and the `linuxfs` plugin for SPI (`/dev/spidev0.0`, `/dev/spidev0.1`). Kubernetes containers have no host device access by default. Running with `privileged: true` works but is a security anti-pattern. Without the device files, `Pi4J.newAutoContext()` throws `IOException` at startup and the Pod crashes immediately — no health check survives.

**Prevention:** The Helm chart must expose device mounts with a toggle (`hardwareAccess.enabled`):

```yaml
volumeMounts:
  - name: spidev
    mountPath: /dev/spidev0.0
  - name: gpiochip
    mountPath: /dev/gpiochip0
volumes:
  - name: spidev
    hostPath:
      path: /dev/spidev0.0
      type: CharDevice
  - name: gpiochip
    hostPath:
      path: /dev/gpiochip0
      type: CharDevice
```

When `hardwareAccess.enabled: false` (non-Pi cluster), the app must fall back to `OfflineDisplayDriver` — which already exists. Gate Pi4J context creation on whether `/dev/gpiochip0` is present at startup and log a clear warning when hardware is unavailable rather than crashing.

---

### MODERATE — JVM Heap OOM From Missing -Xmx in Container

**What goes wrong:** The existing `jvmArgs("-Xmx768m")` in `build.gradle.kts` applies to the test JVM only. The production app has no explicit `-Xmx`. JVM defaults to ~25% of available memory. With a K8s `resources.limits.memory: 512Mi`, the JVM receives approximately 128MB heap — below the `<256MB JVM heap target` and insufficient for Ktor + Netty + Exposed + active scroll effects. The Pod OOM-kills during GC peaks and Kubernetes restarts it in a crash loop.

**Prevention:** Helm chart must set `JAVA_OPTS: "-Xms128m -Xmx220m -XX:+UseSerialGC"` in the deployment env vars. `SerialGC` is appropriate for constrained single-CPU deployment environments. `resources.limits.memory` should be 320Mi to accommodate native memory overhead above the 220MB heap.

---

### MODERATE — PVC Mount Path Must Match JDBC URL Exactly

**What goes wrong:** When PostgreSQL is unavailable and H2 file mode is used with a PVC, the PVC mount path and the JDBC URL path must agree exactly. If `DATABASE_URL=jdbc:h2:file:/data/textreaderrpi` but the PVC is mounted at `/app/data`, H2 creates the file in the container's ephemeral filesystem without error. The data appears to persist during the session but disappears on Pod restart. H2 will not warn about this mismatch.

**Prevention:** Define a single `DATA_DIR` value in Helm `values.yaml` that both the JDBC URL template and the PVC `mountPath` reference. Use Helm templating to derive both from the same source value, preventing the paths from drifting independently.

---

## 7. DRY/YAGNI Refactoring

### CRITICAL — Shared Test Utility Extraction Breaks Test Isolation

**What goes wrong:** 27 test classes have similar `testApplication { application { module() } ... }` setups. Extracting a `TestApplicationHelper` class is tempting, but if the helper internally calls `client.get("/health")` to trigger module execution, test-specific DI mocks must already be installed before the helper runs. Kotest `FunSpec` and `BehaviorSpec` have different lifecycle hooks — mixing them in a shared base causes database state to leak between specs when tests run in parallel.

**Prevention:** Extract only factory functions for creating mock objects (e.g., `mockZoneRegistry()`, `mockScreenDriverService()`). Keep each spec's `testApplication` block explicit and inline. Shared code means shared object factories, not shared application setup or lifecycle.

---

### CRITICAL — Exposed Column Alias Conflict in Shared Query Builders

**What goes wrong:** The v1.1 rename of `HistoryTable.displaySource` (from `.source`) was required because `Table` inherits `ColumnSet.source`. If a shared query builder function accepts multiple `Table` objects and performs a join across `HistoryTable` and `SchedulesTable`, Exposed 1.3.0's column disambiguation requires explicit `alias()` calls. A missed alias causes an `AmbiguousColumn` exception at runtime — not at compile time — and only on queries that actually hit both tables.

**Prevention:** Any cross-table query builder must explicitly alias both tables using `Table.alias("shortname")`. Never pass raw `Table` references into shared query builders that perform joins. Keep repository functions single-table unless join is unavoidable.

---

### MODERATE — Removing Code That Appears Unused Can Drop JaCoCo Coverage

**What goes wrong:** JaCoCo gate is `LINE >= 70%`. The `com/anjo/driver/**` package is excluded from coverage verification (see `build.gradle.kts`). During YAGNI cleanup, removing a non-driver method that "appears unused" may cause a cascade coverage drop in another class if that method was exercised indirectly. The JaCoCo exclude list only excludes classes from the minimum threshold check — all lines still count toward overall coverage.

**Prevention:** Before removing any non-driver code, run `./gradlew jacocoTestReport` and check the HTML report for the specific class. Only remove if the line coverage of the remaining scope stays above 70% after the removal.

---

### MODERATE — IDE Extract Method Automatically Adds KDoc Stubs

**What goes wrong:** DRY refactoring often uses IntelliJ's "Extract Method" action. IntelliJ generates KDoc stubs for extracted functions by default. The project rule prohibits all `//`, `/* */`, and KDoc in Kotlin files with no exceptions — including test files. IDE-generated KDoc stubs violate this rule and will be caught in review.

**Prevention:** After every "Extract Method" refactor, scan changed files for `//`, `/*`, and `/**` patterns and remove all occurrences before committing. Function names and parameter names must be self-documenting without supplementary comments.

---

## 8. .planning/ Compression

### CRITICAL — Compressing Decision Rationale That Is Not Recoverable From Code

**What goes wrong:** Compression passes focus on line count reduction. Several decisions in the current `.planning/` files produce non-obvious code shapes that look like bugs or cargo-culting without the documented rationale:

- `HistoryTable.displaySource` (not `.source`) — naming collision with `ColumnSet.source` inherited from `Table`. Removing the note makes the next developer rename it back, introducing a compile error.
- `parseDiscoveryReply` ignores JSON `"ip"` and uses `senderIp` from the kernel — removes an SSRF vector. Removing the note makes a future "cleanup" delete the variable and reintroduce the vulnerability.
- Material 3 CSS: dark palette in `:root` (default), light overrides under `@media only` — inverting this produces a broken light-mode experience that only manifests when the user has `prefers-color-scheme: light`.
- `testApplication` defers module execution until first HTTP call — without this note the `client.get("/health")` guard in the DI smoke test looks like an unnecessary request and gets removed.

**Prevention:** The compression pass must preserve a `DECISIONS.md` section or equivalent entries in `STATE.md` for every decision where the code is opaque without the rationale. The test for retention: "if I delete this note, could the next developer introduce a bug by applying the naive fix?" If yes, retain it. Delete only: implementation narratives, superseded plans, iteration drafts.

---

### MODERATE — Losing human_needed Checkpoint Items

**What goes wrong:** `STATE.md` lists 4 deferred `human_needed` items (on-device Pi hardware verification). These are not in code comments and not reproducible in CI. A compression pass targeting the deferred table could collapse these into a single line or remove items that "seem like history" — they are not, they are unresolved open items.

**Prevention:** The 4 `human_needed` items in `STATE.md`'s deferred table must survive compression verbatim. Before removing any archived phase file under `.planning/milestones/`, verify the file contains no unresolved `human_needed` checkpoint not captured in `STATE.md`.

---

## Phase-Specific Warnings Summary

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| WebSocket live feed | Frame send from IO thread to session | Channel-per-session; `trySend` from display path only |
| WebSocket live feed | Session leak on browser refresh | `ConcurrentHashMap` + `runCatching` per send + `finally` remove |
| WebSocket live feed | testApplication lifecycle order | `client.get("/health")` before `client.webSocket { }` |
| Dynamic zone creation | Concurrent addNetworkZone check-then-insert race | `ConcurrentHashMap.compute()` atomic check-and-set |
| Dynamic zone creation | Local SPI dynamic add fails silently on hardware | Restrict to network zones; return 422 for hardware types |
| Dynamic zone creation | DI binding type collision | One class per DI key; smoke test updated first |
| Full-text search | PostgreSQL DDL in H2-used migrations | LIKE only for v1.2; no GIN index migration |
| Full-text search | Unescaped LIKE wildcards | Escape in `SearchValidators`; never in route handler |
| CSV export | Missing Content-Disposition header | Set header before `respondText`; always attachment |
| CSV export | Non-ASCII zone ID in filename | ASCII-sanitise the filename string |
| CSV export | Commas and newlines in text field | RFC 4180 double-quote wrapping per field |
| Firmware | GC pauses in render loop | Pre-allocate buffers; no allocation in hot path |
| Firmware | WiFi stack not in Kotlin/Native stdlib | C interop + separate CMake build step |
| Firmware | Reconnect delay loop blocks display | Hardware timer for reconnect; display is main loop |
| K8s + Helm | H2 multi-replica divergence | PostgreSQL required; document in Helm values |
| K8s + Helm | Pi4J fails without device mounts | `hostPath` CharDevice mounts for spidev + gpiochip |
| K8s + Helm | Pod OOM from default JVM heap sizing | `JAVA_OPTS=-Xmx220m -XX:+UseSerialGC` in Helm chart |
| DRY refactoring | Shared test base breaks isolation | Factory helpers only; keep `testApplication` per spec |
| DRY refactoring | IDE adds KDoc on Extract Method | Scan for comment patterns after every refactor |
| DRY refactoring | Column alias conflict in joined queries | Explicit `Table.alias()` in cross-table query builders |
| .planning compression | Losing non-obvious code decisions | Retain every decision where code is opaque without it |
| .planning compression | Losing human_needed checkpoints | Deferred table in STATE.md must survive compression verbatim |
