# Architecture

**Analysis Date:** 2024-12-19

## System Overview

```text
┌──────────────────────────────────────────────────────────────┐
│                        HTTP Routing                           │
│           `routing/Routing.kt`                               │
│  POST /api/text | GET /status | Swagger at /openapi          │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   Service Layer (async)                       │
│  ┌──────────────────────┬──────────────────────────────────┐ │
│  │ ReaderInputService   │   ScreenDriverService            │ │
│  │ (queue management)   │   (display rendering)            │ │
│  └──────────────┬───────┴──────────────┬───────────────────┘ │
│                 │                      │                      │
│  `service/`                            │                      │
└─────────────────┼──────────────────────┼──────────────────────┘
                  │                      │
                  ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│                    Driver Layer (Hardware)                    │
│           `driver/Max7219Matrix.kt`                          │
│      (LED matrix control via SPI/GPIO on Raspberry Pi)       │
└──────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| **Routing** | HTTP endpoint handling, request parsing, response serialization | `routing/Routing.kt` |
| **ReaderInputService** | Text input queue management, character buffering, display queue control | `service/ReaderInput.kt` |
| **ScreenDriverService** | Asynchronous display rendering, character-to-bitmap conversion, screen updates | `service/ScreenDriver.kt` |
| **Max7219Matrix** | Low-level hardware control, SPI communication, LED matrix addressing | `driver/Max7219Matrix.kt` |
| **Font Utilities** | Character-to-bitmap mapping, font rendering logic | `utils/Font.kt` |

## Layers

**HTTP Routing Layer:**
- Purpose: Accept HTTP POST/GET requests, validate input, delegate to services
- Location: `routing/Routing.kt`
- Contains: Route handlers, request/response mapping, CORS configuration
- Depends on: `ReaderInputService`, `ScreenDriverService`

**Service Layer (Async):**
- Purpose: Orchestrate business logic, manage state, coordinate services
- Location: `service/`
- Contains: 
  - `ReaderInputService`: input queue, character sequencing
  - `ScreenDriverService`: display rendering, screen composition
- Depends on: `Max7219Matrix`, application configuration

**Driver Layer:**
- Purpose: Encapsulate hardware interaction, abstract LED matrix control
- Location: `driver/Max7219Matrix.kt`
- Contains: SPI communication, GPIO control, matrix state management
- Depends on: Pi4J library

**Configuration Layer:**
- Purpose: HTTP server setup, Ktor plugins, DI wiring
- Location: `config/` and `Application.kt`
- Contains: HTTP module installation, request validation, serialization setup

## Data Flow

### Primary Request Path: Send Text to Display

1. **HTTP POST** → `Routing.kt` receives `/api/text` with text payload
2. **Validation** → Custom request validation in `Routing.kt` (bodies must start with "Hello")
3. **Service Call** → `ReaderInputService.displayText(text)` queues text
4. **Background Rendering** → `ScreenDriverService` periodically renders and scrolls
5. **Hardware Write** → `Max7219Matrix` writes column data via SPI
6. **Response** → HTTP 200 OK with status

### Background Flow: Continuous Display Updates

1. **ScreenDriverService launch** → Coroutine in `Dispatchers.IO` on application startup
2. **Character iteration** → Gets characters from input queue
3. **Render** → Converts character to bitmap using `Font.asciiFont`
4. **Write Hardware** → Calls driver, SPI write operation
5. **Scroll** → Shifts display columns for animation effect

## Key Abstractions

**Font Rendering:**
- Purpose: Map ASCII characters to 5-byte bitmap arrays
- Location: `utils/Font.kt` with `Font.asciiFont` constant
- Pattern: Character lookup table (character → 8x5 pixel grid)

**SPI Communication:**
- Purpose: Abstract hardware protocol details
- Location: `driver/Max7219Matrix.kt`
- Pattern: Packet-based commands to MAX7219 IC

## Entry Points

**Application Startup:**
- Location: `Application.kt` main function
- Triggers: JVM `main()` or Gradle `./gradlew run`
- Responsibilities: 
  - Initialize Pi4J context
  - Instantiate services and drivers
  - Start Ktor embedded HTTP server on port 8080
  - Launch background rendering coroutines

**HTTP Server:**
- Location: Ktor embedded server in `Application.kt`
- Triggers: HTTP requests on configured port
- Responsibilities: Route requests, validate input, serialize responses

## Architectural Constraints

- **Threading:** Coroutines with `Dispatchers.IO` for non-blocking async I/O
- **Global state:** Services are singletons; matrix driver state is local
- **Circular imports:** None - clear dependency direction: routing → services → driver
- **Hardware concurrency:** SPI writes serialized to maintain consistency
- **Blocking operations:** SPI writes block IO dispatcher (acceptable for Raspberry Pi speeds)

## Error Handling

**Strategy:** Exceptions bubble up from driver → service → routing. HTTP layer catches and returns appropriate status codes.

**Patterns:**
- **Hardware errors:** May throw exceptions, caught in service layer, logged
- **Invalid input:** Validated in routing layer, returns HTTP 400
- **Service errors:** Logged, propagated to HTTP layer as 500

## Cross-Cutting Concerns

**Validation:** 
- HTTP request validation in `Routing.kt` (custom body validation)
- Service-level validation via extension functions
- Maximum text length checks

**Logging:** Configured in `logback.xml`; follows Ktor conventions

**Authentication:** Not implemented (suitable for local network)

---

*Architecture analysis: 2024-12-19*

