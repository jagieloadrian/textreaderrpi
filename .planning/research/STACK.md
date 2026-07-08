# Technology Stack — TextReaderRpi v1.2 (New Capabilities Only)

**Project:** TextReaderRpi
**Researched:** 2026-06-21
**Scope:** Stack additions for 6 new v1.2 capabilities. The v1.1 stack is locked and not re-evaluated here.

---

## Locked v1.1 Stack (Do Not Change)

| Library | Version | Role |
|---------|---------|------|
| Kotlin | 2.3.21 | Language |
| Ktor server | 3.5.0 | HTTP server + HTML DSL |
| Pi4J | 4.0.0 | Hardware GPIO/SPI/I2C |
| Exposed | 1.3.0 (`org.jetbrains.exposed.v1.*`) | ORM |
| H2 / PostgreSQL | 2.4.240 / 42.7.7 | Database |
| HikariCP | 7.0.2 | Connection pool |
| kotlinx-coroutines | 1.11.0 | Concurrency |
| Flyway | 9.22.3 | Schema migrations |
| JmDNS | 3.6.3 | mDNS zone discovery |
| ktor-client-cio + websockets | 3.5.0 | Outbound HTTP + WebSocket client |
| Flaxoos rate-limiting | 2.2.1 | Token bucket rate limiter |
| KHealth | 3.0.2 | Health check plugin |
| Kotest | 6.1.11 | Test framework |
| MockK | 1.14.9 | Mocking |
| logback-classic | 1.5.18 | Logging |

---

## Feature 1: WebSocket Live Feed — Server Push to Browsers

**Verdict: Use `ktor-server-sse` (SSE), not WebSockets.**

SSE is the right choice here. The use case is one-directional: the server pushes "currently displayed text changed" events to browser tabs. SSE requires only HTTP, has native browser `EventSource` reconnection (no JS library), works through all proxies, and is multiplexed under HTTP/2. WebSockets add bidirectional complexity that this feature does not need.

The `ktor-server-sse` artifact ships as part of the Ktor monorepo at the same version — confirmed from Ktor 3.5.0 release notes. Install pattern:

```kotlin
install(SSE)
routing {
    sse("/api/v1/live") {
        val sharedFlow: SharedFlow<String> = /* injected singleton */
        sharedFlow.collect { text ->
            send(ServerSentEvent(text, event = "display"))
        }
    }
}
```

The feed state is a `MutableSharedFlow<String>(replay = 1)` singleton injected via Ktor DI. Every `ScreenDriverService.displayImmediate()` and `displayScheduled()` call emits to it. The `replay = 1` ensures a newly connected browser immediately receives the last displayed text.

**New dependency:**

| Artifact | Version | Purpose | Why |
|----------|---------|---------|-----|
| `io.ktor:ktor-server-sse` | 3.5.0 | SSE plugin for live feed endpoint | Same-version Ktor artifact; zero transitive conflicts; simpler than WebSocket for unidirectional push |

**TOML addition:**
```toml
ktor-server-sse = { module = "io.ktor:ktor-server-sse", version.ref = "ktor" }
```

**What NOT to use:** `ktor-server-websockets` for this feature — already present in TOML for the WebSocket client, but adding a server-side WS route forces you to manage connection lifecycle, heartbeats, and frame encoding that SSE handles automatically. Keep WS for the existing `NetworkZoneDriver` client use only.

---

## Feature 2: Dynamic Zone Creation (No Restart)

**Verdict: No new library. `ZoneRegistry` already supports this.**

The existing `ZoneRegistry` (`ConcurrentHashMap<String, ZoneEntry>`) has `addNetworkZone()`, `register()`, and `removeZone()` methods that operate at runtime without any lock or restart. The `ConcurrentHashMap` is thread-safe for concurrent reads and writes, providing atomic bucket-level locking (Java 8+ CAS semantics).

What is missing is only the API route to expose zone creation. Add `POST /api/v1/zones` that accepts a `ZoneConfig`-shaped body and calls `ZoneRegistry.initLocalZone()` (currently `private` — promote to `internal`). For network zones, the existing `addNetworkZone(zone, client)` path already works.

**Pi4J constraint:** Local hardware zones (MAX7219/LCD/OLED) require a `Pi4J.newAutoContext()` binding at startup. Dynamically adding a *new hardware-wired* local zone at runtime will fail if its GPIO pins weren't initialized. Network zones have no such constraint. Document this clearly: dynamic creation is fully supported for network zones; local hardware zones require the zone to be declared in config at startup (or a Pi4J context extension path — out of scope for v1.2).

**New dependencies: none.**

---

## Feature 3: Full-Text Search in Display History

**Verdict: Use `LIKE`/`ILIKE` with Exposed DSL. No external FTS library.**

**Rationale:** The history table is bounded at 1,000 rows (enforced in v1.1). PostgreSQL `tsvector`/`tsquery` FTS and H2's `FullText` extension both require additional schema objects (indexes, triggers, virtual tables) and produce different SQL dialects — making a single Exposed query target both H2 (dev/default) and PostgreSQL (production) unnecessarily complex.

For 1,000 rows, a case-insensitive `LIKE '%term%'` scan is O(n) over at most 1,000 rows and completes in microseconds. This is not a performance problem.

**Exposed DSL approach (H2 + PostgreSQL compatible):**

```kotlin
HistoryTable.text.lowerCase() like "%${term.lowercase()}%"
```

`lowerCase()` is an Exposed built-in that emits `LOWER(column)` in SQL — valid in both H2 and PostgreSQL. This avoids `ILIKE` (PostgreSQL-only) and `LIKE` case sensitivity differences.

If the project ever switches to a larger dataset (>100K rows), add a GIN index on PostgreSQL and a raw SQL `@@` tsvector query via Exposed's `CustomFunction`. That is a future migration, not a v1.2 concern.

**New dependencies: none.**

---

## Feature 4: Export History to CSV (Streaming Response)

**Verdict: Use `com.jsoizo:kotlin-csv-jvm` version `2.0.0`.**

The export is a `GET /api/v1/history/export` route that streams the history table (up to 1,000 rows) as `text/csv` with `Content-Disposition: attachment; filename="history.csv"`. Ktor's `respondOutputStream` or `respondBytesWriter` enables streaming without loading all rows into a string first.

**Why kotlin-csv-jvm:**
- Pure Kotlin, zero native dependencies, fits the Pi memory budget (<50 KB jar)
- Handles quoting, commas inside values, and newlines in text fields automatically
- Apache Commons CSV and OpenCSV both pull in additional transitive dependencies (lang3, etc.)
- 2.0.0 is the current release on Maven Central (confirmed latest; project transferred from `com.github.doyaaaaaken` to `com.jsoizo` groupId)

**Integration pattern:**
```kotlin
get("/api/v1/history/export") {
    val rows = historyService.findAll()
    call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "history.csv").toString()
    )
    call.respondOutputStream(ContentType("text", "csv")) {
        val writer = csvWriter()
        writer.open(this) {
            writeRow(listOf("id", "text", "zone", "effect", "displayedAt", "webhookStatus"))
            rows.forEach { r -> writeRow(listOf(r.id, r.text, r.zone, r.effect, r.displayedAt, r.webhookStatus)) }
        }
    }
}
```

**New dependency:**

| Artifact | Version | Purpose | Why |
|----------|---------|---------|-----|
| `com.jsoizo:kotlin-csv-jvm` | `2.0.0` | CSV serialization for export | Pure Kotlin, minimal footprint, handles edge cases (quoted commas, newlines in text) |

**TOML addition:**
```toml
[versions]
kotlin-csv = "2.0.0"

[libraries]
kotlin-csv-jvm = { module = "com.jsoizo:kotlin-csv-jvm", version.ref = "kotlin-csv" }
```

---

## Feature 5: Firmware for RPi Pico and ESP32 — Kotlin Native Feasibility

### VERDICT: Kotlin Native bare-metal is NOT FEASIBLE for Pico or ESP32. Use C/MicroPython firmware skeleton.

This is the most significant finding of this research. The verdict is definitive.

**RPi Pico (RP2040 — ARM Cortex-M0+ / RP2350 — ARM Cortex-M33):**

Kotlin/Native's official supported target list (as of Kotlin 2.3.21, confirmed at `kotlinlang.org/docs/native-target-support.html`) contains **zero bare-metal or microcontroller targets**. All ARM targets are OS-backed (macOS Apple Silicon, iOS, watchOS, tvOS, Android NDK, `linuxArm64`). There is no `armv6m`, `thumbv6m`, `cortex-m0+`, or `cortex-m33` target.

A JetBrains YouTrack issue (KT-44498) requested RP2040 as a Kotlin/Native target; it remains unresolved. A community post from 2021 noted the blocker: "the Pi SDK is based on gcc, while Kotlin is based on LLVM, and there are subtle differences in their ABI." For RP2350 (ARM Cortex-M33), bare metal in C/assembly is well-established, but no Kotlin toolchain exists or is in progress.

`linuxArm64` (Kotlin/Native Tier 2) targets 64-bit ARM Linux — i.e., Raspberry Pi 4 running RPi OS, not a microcontroller. It cannot target a bare-metal microcontroller.

**ESP32 (Xtensa LX6/LX7 / RISC-V C3/C6):**

ESP32 classic uses the Tensilica Xtensa architecture. LLVM upstream has a partial Xtensa backend (Espressif contributed an RFC), but it is not merged into mainline LLVM, and Kotlin/Native does not carry it. This is a hard blocker.

ESP32-C3/C6 use RISC-V (RV32IMC). There is community proof-of-concept work (master thesis tracked in KT-43854) compiling Kotlin/Native for `linux_riscv` and running it under QEMU. This is experimental, not production-ready, not an official Ktor target, and not shipping in Kotlin 2.x. There is no path to bare-metal ESP32-C3 via Kotlin/Native in v1.2.

**Kotlin Multiplatform (KMP) shared-logic approach:**

KMP with platform wrappers would mean: shared Kotlin business logic compiled to JVM/JS, and the "firmware" platform layer written in C (for Pico SDK) or ESP-IDF (for ESP32). The Kotlin code cannot run on the microcontroller itself — it would have to run on the Pi 4 side. This architecture is not firmware; it is just the existing server with a different name. It adds no value for the stated goal of "Kotlin Native WebSocket receiver + local display rendering on Pico/ESP32."

**Confirmed approach (decision 1a):** Build a C/MicroPython firmware skeleton — not deferred.

| Platform | Language | Framework | Why |
|----------|----------|-----------|-----|
| RPi Pico W / Pico 2 W | C (Pico SDK 2.x) | lwIP WebSocket + Pico SDK | Mature, official, community-tested WebSocket client examples exist |
| ESP32 (all variants) | C++ / Arduino (ESP-IDF) | ArduinoWebsockets or esp-idf native WS | ArduinoWebsockets lib supports all ESP32 variants, active maintenance |
| RPi Pico W (alt) | MicroPython | uasyncio + websocket | Simpler, faster to prototype, adequate for this display use case |

The firmware submodule lives at `.devops/firmware/pico/` and `.devops/firmware/esp32/` using their native toolchains. The integration boundary is the WebSocket protocol: the Ktor server's `NetworkZoneDriver` already defines the message contract the firmware client must speak. The v1.2 firmware phase delivers a protocol spec + working skeleton (connect, receive text, render on local display). Full effects pipeline is a stretch goal.

**Implication for roadmap:** Sequence firmware as a late phase (after server features are stable). No JVM dependency changes. The firmware toolchain is entirely separate from the Gradle build.

---

## Feature 6: Kubernetes Manifests + Helm Chart

**Verdict: Standard Helm 3 chart in `.devops/helm/`. No new JVM dependencies.**

The Ktor application is already Docker-ready (Docker image via Gradle). The K8s/Helm work is infrastructure-only — no changes to the application code or JVM dependencies.

**Recommended tooling:**

| Tool | Version | Purpose | Why |
|------|---------|---------|-----|
| Helm | 3.x (latest) | Chart templating and packaging | Industry standard; GitOps-compatible; supports ArgoCD/Flux |
| Kubernetes manifests | API v1 / apps/v1 | Deployment, Service, ConfigMap, PVC | Standard object types for a stateful single-node JVM app |

**Chart structure for `.devops/helm/textrpirpi/`:**
```
Chart.yaml            # name, version, appVersion
values.yaml           # image, replicas, resources, env, probes
templates/
  deployment.yaml     # Deployment with env from ConfigMap
  service.yaml        # ClusterIP on port 8080
  configmap.yaml      # APP_DB_URL, DISPLAY_TYPE, ZONES_CONFIG etc.
  pvc.yaml            # H2 data directory persistence (if not PostgreSQL)
  hpa.yaml            # HorizontalPodAutoscaler (optional, single replica likely)
```

**Health probe mapping** (existing endpoints → K8s probes):

| Probe | Endpoint | Config |
|-------|----------|--------|
| `livenessProbe` | `GET /health` (200 = alive) | `initialDelaySeconds: 30`, `periodSeconds: 10` |
| `readinessProbe` | `GET /health/ready` (200 = ready) | `initialDelaySeconds: 5`, `periodSeconds: 5` |
| `startupProbe` | `GET /health` | `failureThreshold: 12`, `periodSeconds: 5` (60s window for JVM startup) |

**Pi 4 / single-node note:** The chart should set `replicas: 1` as default. Pi4J hardware access is exclusive — running two pods on the same node would conflict on SPI/I2C. If deploying to a proper K8s cluster (non-Pi), set `DISPLAY_TYPE=OFFLINE` in the values override.

**Resource requests (values.yaml defaults):**
```yaml
resources:
  requests:
    memory: "128Mi"
    cpu: "250m"
  limits:
    memory: "256Mi"
    cpu: "1000m"
```

**What NOT to add:** Helm dependency on a PostgreSQL subchart (use an external DB in production; H2 mode is fine for home use). Do not add Ingress by default (home network, no TLS). Do not add cert-manager (out of scope per project constraints).

---

## Full Delta: New Entries in `ktor-libs.versions.toml`

```toml
# ADD under [versions]:
kotlin-csv = "2.0.0"

# ADD under [libraries]:
ktor-server-sse   = { module = "io.ktor:ktor-server-sse",        version.ref = "ktor" }
kotlin-csv-jvm    = { module = "com.jsoizo:kotlin-csv-jvm",      version.ref = "kotlin-csv" }
```

No new `[plugins]` entries. No version changes to existing entries.

**`build.gradle.kts` additions:**
```kotlin
implementation(ktorLibs.ktor.server.sse)
implementation(ktorLibs.kotlin.csv.jvm)
```

---

## Complete New Dependency Summary

| Artifact | Version | Scope | Feature | Why |
|----------|---------|-------|---------|-----|
| `io.ktor:ktor-server-sse` | 3.5.0 | `implementation` | Live feed | SSE simpler than WS for server→browser push; same-version Ktor, zero conflicts |
| `com.jsoizo:kotlin-csv-jvm` | 2.0.0 | `implementation` | CSV export | Pure Kotlin, minimal footprint, handles quoting edge cases |

**Features 2 (dynamic zones), 3 (full-text search), 6 (Helm/K8s): zero new JVM dependencies.**

**Feature 5 (firmware): Kotlin Native is infeasible for Pico and ESP32. C/MicroPython skeleton confirmed for v1.2.**

---

## What NOT to Add

| What | Reason |
|------|--------|
| `ktor-server-websockets` (server-side) | SSE is sufficient for browser push; WS already present as client |
| PostgreSQL `tsvector` / H2 `FullText` extension | Overkill for ≤1,000-row table; breaks H2/PG dual-DB compatibility |
| Apache Commons CSV / OpenCSV | Pull in Apache Commons Lang or other transitive deps; kotlin-csv is self-contained |
| Elasticsearch / Meilisearch | No search engine warranted for 1,000-row bounded table |
| Redis PubSub | SharedFlow is sufficient for in-process SSE fan-out on single Pi node |
| Kotlin Multiplatform `commonMain` firmware layer | Cannot compile to bare-metal microcontrollers; adds build complexity with no runtime benefit |
| Helm postgresql subchart dependency | External DB preferred; H2 sufficient for home use |
| cert-manager / Ingress | Out of scope per home-network-only constraint |

---

## Sources

- Kotlin/Native supported targets: https://kotlinlang.org/docs/native-target-support.html
- KT-44498 (RP2040 as Kotlin/Native target, unresolved): https://youtrack.jetbrains.com/issue/KT-44498
- KT-43854 (Linux RISC-V target, experimental): https://youtrack.jetbrains.com/issue/KT-43854
- Raspberry Pi Forums — Kotlin/Native Pico discussion: https://forums.raspberrypi.com/viewtopic.php?t=299856
- Ktor SSE server docs: https://ktor.io/docs/server-server-sent-events.html
- Ktor 3.5.0 release notes (SSE enhancements confirmed): https://ktor.io/docs/whats-new-350.html
- Ktor WebSockets SharedFlow example: https://github.com/ktorio/ktor-documentation/tree/3.3.2/codeSnippets/snippets/server-websockets-sharedflow
- kotlin-csv 2.0.0 on Maven Central: https://mvnrepository.com/artifact/com.jsoizo/kotlin-csv-jvm
- Helm best practices: https://techstackguide.com/helm-charts-best-practices/
- Kubernetes health probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- ESP32 Xtensa LLVM RFC: https://discourse.llvm.org/t/rfc-tensilica-xtensa-esp32-backend/57835
- Codebase analysis: `src/main/kotlin/com/anjo/service/ZoneRegistry.kt` (ConcurrentHashMap, addNetworkZone at runtime)
