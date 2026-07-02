<!-- generated-by: gsd-doc-writer -->
# Architecture Overview

## System Overview

TextReaderRpi is a Ktor-based HTTP service that renders scrolling text and scheduled messages on
one or more physical display devices (MAX7219 LED matrix, LCD, OLED) connected to a Raspberry Pi
via Pi4J. Clients submit text through a REST API or a server-rendered web UI; the service resolves
the target zone, applies the requested visual effect, and drives the hardware. Schedules persist in
H2 (default) or PostgreSQL and survive restarts. The multi-zone architecture allows a single service
instance to control locally-wired displays, remote nodes discovered automatically over the LAN via
mDNS or UDP broadcast, and firmware nodes (e.g., Raspberry Pi Pico) that connect inward over
WebSocket.

The primary architectural style is a layered, coroutine-driven server: an HTTP layer built on Ktor
Netty receives requests, delegates to service layer objects that manage concurrency and state, and
the service layer calls into a hardware abstraction layer that talks to Pi4J.

---

## Component Diagram

```
                         ┌──────────────────────────────────────────────────────────┐
                         │                     HTTP / Netty                          │
                         │  REST /api/v1  ·  Web UI  ·  /health  ·  /metrics        │
                         │  SSE /live  ·  WS /zone/{id} (firmware)                  │
                         └──────────┬───────────────────────────────────────────────┘
                                    │
                     ┌──────────────▼───────────────────────┐
                     │            Routing layer              │
                     │  TextRoutes · ScheduleRoutes          │
                     │  DisplayRoutes · ZoneRoutes           │
                     │  HistoryRoutes · HealthRoutes         │
                     │  MetricsRoutes · UI Routes            │
                     │  LiveRoutes · FirmwareZoneRoutes      │
                     └──────────────┬────────────────────────┘
                                    │
          ┌─────────────────────────▼────────────────────────────────────┐
          │                      Service layer                            │
          │                                                               │
          │  ScreenDriverService ───────────────────────────┐            │
          │     (conflict policy, retry, metrics)            │            │
          │                                                  ▼            │
          │  SchedulerService ──── fires ──► ZoneRegistry                │
          │  (ONESHOT/RECURRING/CRON coroutines)       (route/bcast)     │
          │                                                  │            │
          │  NetworkDiscoveryService ───────────────────────►│            │
          │  (mDNS + UDP broadcast, auto-register)           │            │
          │                                                  │            │
          │  DisplaySelectionService ── selects ──► DisplayDriver        │
          │  (runtime display type switch, driver cache)     │            │
          │                                                  │            │
          │  DisplayEventBus ◄──────────────────── ZoneRegistry          │
          │  (SharedFlow → SSE /live)                        │            │
          │                                                  │            │
          │  WebhookService ◄─────────────── SchedulerService            │
          │  HistoryService · MetricsCollector               │            │
          │  EffectRendererFactory                           │            │
          └──────────────────────────────────────────────────│────────────┘
                                                             │
                     ┌───────────────────────────────────────▼──────────────────┐
                     │                    Zone driver layer                      │
                     │                                                           │
                     │   LocalZoneDriver ──► DisplayDriver (interface)           │
                     │                        ├── Max7219Matrix  (SPI/Pi4J)      │
                     │                        ├── LcdDisplay     (I2C/Pi4J)      │
                     │                        ├── OledDisplay    (I2C/Pi4J)      │
                     │                        └── OfflineDisplayDriver           │
                     │                                                           │
                     │   NetworkZoneDriver ──► WebSocket → remote node           │
                     │                                                           │
                     │   FirmwareZoneDriver ◄── WebSocket ← firmware node        │
                     │   (Pi Pico etc.; device dials in, driver queues frames)   │
                     └───────────────────────────────────────────────────────────┘
                                        │
                     ┌──────────────────▼────────────────────────────────────────┐
                     │            Persistence (Exposed + Flyway)                  │
                     │   SchedulesTable · HistoryTable · NetworkZonesTable        │
                     │   H2 (default) or PostgreSQL (runtime-configurable)        │
                     │   V1–V6 migrations (V6: ip nullable, display_subtype col)  │
                     └───────────────────────────────────────────────────────────┘
```

---

## Data Flow

The following describes the lifecycle of a typical immediate-display request
(`POST /api/v1/text`):

1. **Ktor Netty** receives the HTTP request and applies the rate limiter
   (`flaxoos ktor-server-rate-limiting`, default 60 req/min).
2. **Ktor request validation** runs the configured `RequestValidationConfig` rules (text length,
   required fields) and rejects malformed requests with `400` before the route handler is reached.
3. **`TextRoutes`** deserialises the `TextRequest` body and calls
   `ScreenDriverService.displayImmediate()`.
4. **`ScreenDriverService`** checks the conflict policy (`INTERRUPT` or `SKIP_NEW`), acquires the
   per-zone `Mutex`, cancels any in-flight job if `INTERRUPT`, and launches a new coroutine on
   `Dispatchers.IO`.
5. The coroutine calls **`ZoneRegistry.broadcast()`** (no `zoneId` specified) or
   **`ZoneRegistry.route()`** (specific zone).
6. `ZoneRegistry` dispatches to the matching `ZoneDriver`:
   - **`LocalZoneDriver`** calls the concrete `DisplayDriver` (`Max7219Matrix`, `LcdDisplay`, etc.)
     which writes pixel data to hardware over SPI or I2C via Pi4J.
   - **`NetworkZoneDriver`** sends a JSON `{"text":"…","effect":"…"}` frame over an
     existing WebSocket connection to a remote TextReaderRpi node.
   - **`FirmwareZoneDriver`** enqueues a `FirmwareMessage` JSON frame onto a `Channel`; the driver
     drains the channel over the WebSocket session that the firmware device opened inward via
     `GET /zone/{id}`.
7. `ZoneRegistry` emits a `DisplayEvent` to **`DisplayEventBus`**; any SSE client connected to
   `/live` receives the event in real time.
8. On success **`HistoryRepository`** writes a record to the `history` table.
9. **`ScreenDriverMetrics`** counters and timers are updated (Dropwizard `MetricRegistry`).
10. The route handler returns `202 Accepted` to the caller; hardware rendering is non-blocking.

For a **scheduled** trigger the flow is the same from step 4 onwards. `SchedulerService` maintains
one coroutine per active schedule (ONESHOT, RECURRING, CRON) and calls
`ScreenDriverService.displayScheduled()` at the appropriate time. On each firing, if a
`webhookUrl` is set on the schedule or a `webhooks.defaultUrl` is configured, `WebhookService`
POSTs a JSON payload to that URL asynchronously.

---

## Key Abstractions

| Abstraction | File | Description |
|---|---|---|
| `DisplayDriver` | `driver/DisplayDriver.kt` | Interface for all physical display backends. Defines `scrollText`, `write`, `clear`, `setBrightness`, `displayStatic`, `status`, `stop`. |
| `AbstractDisplayDriver` | `driver/AbstractDisplayDriver.kt` | Base class providing coroutine `Job` lifecycle, `lastMessage`, and `lastError` state tracking for concrete drivers. |
| `ZoneDriver` | `zone/ZoneDriver.kt` | Thin interface over a named display zone: `send(text, effect): Boolean`, `status()`, `stop()`. Decouples zone routing from hardware details. |
| `ZoneRegistry` | `service/ZoneRegistry.kt` | Central map of `zoneId → ZoneDriver`. Supports `route()` (single zone), `broadcast()` (all zones, parallel via `async`), dynamic add/remove of network zones, and lookup of `FirmwareZoneDriver` entries. |
| `ScreenDriverService` | `service/ScreenDriverService.kt` | Orchestrates display operations: conflict policy, per-zone mutex, retry-with-backoff, metrics instrumentation, and history recording. |
| `SchedulerService` | `service/SchedulerService.kt` | Manages one coroutine per active schedule. Supports `ONESHOT` (ISO-8601 timestamp), `RECURRING` (interval string), and `CRON` (Unix cron expression via cron-utils). |
| `DisplaySelectionService` | `service/DisplaySelectionService.kt` | Runtime display-type selection and driver caching. Allows switching the active display backend (MAX7219/LCD/OLED) without a restart; uses a `ReentrantReadWriteLock` for concurrent access. |
| `DisplayEventBus` | `service/DisplayEventBus.kt` | `SharedFlow`-backed event bus (replay=5, buffer=64). `ZoneRegistry` emits `DisplayEvent` values here; `LiveRoutes` collects them and forwards to SSE clients. |
| `FirmwareZoneDriver` | `zone/FirmwareZoneDriver.kt` | `ZoneDriver` implementation for firmware nodes (e.g., Raspberry Pi Pico). The firmware device initiates the WebSocket connection to `/zone/{id}`; the driver queues `FirmwareMessage` frames and drains them over the live session. |
| `EffectRenderer` | `service/effect/EffectRenderer.kt` | Strategy interface for visual effects. Implementations: `ScrollEffect`, `BlinkEffect`, `FadeEffect`, `ReverseEffect`. Instantiated via `EffectRendererFactory`. |
| `NetworkDiscoveryService` | `service/NetworkDiscoveryService.kt` | Discovers remote nodes via mDNS (`_textreaderrpi._tcp.local.`) and UDP broadcast (port 54321). Auto-registers discovered nodes as `NetworkZoneDriver` entries in `ZoneRegistry`. |
| `WebhookService` | `service/WebhookService.kt` | Fire-and-forget HTTP POST on schedule trigger. Resolves URL from per-schedule `webhookUrl` or global `webhooks.defaultUrl`. 5-second timeout. |
| `ConfigLoader` | `config/loader/ConfigLoader.kt` | Reads Ktor `application.yaml` (with env-var substitution) into `ApplicationConfig` data classes. Single source of truth for all runtime settings. |

---

## Directory Structure Rationale

```
src/main/kotlin/com/anjo/
├── Application.kt          Entry point; installs Ktor plugins in module() order
├── config/
│   ├── loader/             Single ConfigLoader that maps YAML/env vars to model classes
│   └── model/              Immutable data classes (ApplicationConfig, DatabaseConfig, …)
├── db/                     Exposed table definitions, HikariCP pool init, Flyway migrations,
│                           and repository classes (ScheduleRepository, HistoryRepository,
│                           ZoneRepository)
├── di/                     Ktor plugin installers: DependencyInjection, HTTP, Monitoring,
│                           RateLimiting, Serialization, ErrorHandling
├── driver/                 Hardware abstraction layer — DisplayDriver interface and its
│                           concrete implementations (Max7219Matrix, LcdDisplay, OledDisplay,
│                           OfflineDisplayDriver)
├── model/                  Kotlinx serialisation data classes and enums used across layers
│                           (Schedule, Effect, NetworkZone, ConflictPolicy, DisplayEvent,
│                           FirmwareMessage, DisplayType, …)
├── routing/                Route handlers grouped by resource (/api/v1 REST + UI routes)
│   ├── FirmwareZoneRoutes  WebSocket endpoint /zone/{id} — firmware devices connect here
│   ├── LiveRoutes          SSE endpoint /live — streams DisplayEvents to browser clients
│   └── ui/                 Server-rendered HTML routes using kotlinx.html
├── service/                Business logic services (ScreenDriverService, SchedulerService,
│                           ZoneRegistry, NetworkDiscoveryService, WebhookService,
│                           DisplaySelectionService, DisplayEventBus, EffectRendererFactory, …)
│   └── effect/             EffectRenderer implementations
├── utils/                  Font bitmap data used by Max7219Matrix
├── validation/             Ktor RequestValidation configuration and validators;
│                           ScheduleValidators contain all schedule-specific validation logic
├── web/
│   └── templates/          kotlinx.html page templates (BaseLayout, IndexPage, SchedulePage, …)
└── zone/                   ZoneDriver interface and its three implementations:
                            LocalZoneDriver (wraps a DisplayDriver),
                            NetworkZoneDriver (WebSocket client to a remote node), and
                            FirmwareZoneDriver (server-side WebSocket for inbound firmware)

src/main/resources/
├── application.yaml        Default config with ${ENV_VAR:default} substitution
├── db/migration/           Flyway SQL migrations (V1–V6): schema, scheduler columns,
│                           history table, webhook status column, network zones table,
│                           ip nullable + display_subtype column
└── static/                 Static assets served at /static

.devops/
├── containers/             Dockerfile, docker-compose.yml, build-image.sh
├── helm/                   Helm chart (textreaderrpi) for Kubernetes deployment
└── host/                   Systemd unit file and install script for host deployments
```

---

## Runtime Modes

The service adapts to its environment at startup:

- **Hardware present** — `ZoneRegistry` initialises `LocalZoneDriver` entries from
  `display.zones` config; Pi4J opens SPI/I2C devices. If hardware init throws, the zone falls
  back to `OfflineDisplayDriver` rather than crashing. `DisplaySelectionService` selects the
  active driver type from `display.type` config and caches it for runtime switching.
- **No hardware (dev/CI)** — Pi4J mock plugin is available on the classpath; all zones report
  OFFLINE but the service starts and all API endpoints respond normally.
- **Multi-node** — `NetworkDiscoveryService` runs a persistent mDNS listener and exposes a UDP
  scan endpoint. Discovered remote nodes connect via WebSocket and appear as zones alongside local
  hardware zones. Discovery can be disabled via `discovery.enabled=false`.
- **Firmware nodes** — Microcontrollers (e.g., Raspberry Pi Pico) connect inward via WebSocket to
  `/zone/{id}`. `FirmwareZoneRoutes` attaches the session to a pre-registered `FirmwareZoneDriver`
  in `ZoneRegistry`; the driver queues and drains `FirmwareMessage` frames while the connection
  is live.
- **Database** — Defaults to an H2 file database at `./data/schedules`. Switch to PostgreSQL by
  changing `DATABASE_URL` and `DATABASE_DRIVER` — no code change required; Flyway runs the same
  migrations on both engines.
