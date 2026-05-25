# Codebase Structure

**Analysis Date:** 2024-12-19

## Directory Layout

```
TextReaderRpi/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── Application.kt              # Entry point, Ktor setup
│   │   │   ├── config/
│   │   │   │   ├── HTTP.kt                 # HTTP configuration
│   │   │   │   ├── Monitoring.kt           # Monitoring/status setup
│   │   │   │   └── Serialization.kt        # JSON serialization config
│   │   │   ├── di/
│   │   │   │   └── DependencyInjection.kt  # Koin DI module setup
│   │   │   ├── routing/
│   │   │   │   └── Routing.kt              # HTTP routes (/api/text, /openapi)
│   │   │   ├── service/
│   │   │   │   ├── ReaderInput.kt          # Text queue management
│   │   │   │   └── ScreenDriver.kt         # Display rendering and control
│   │   │   ├── driver/
│   │   │   │   └── Max7219Matrix.kt        # MAX7219 hardware driver
│   │   │   └── utils/
│   │   │       └── Font.kt                 # ASCII font and rendering
│   │   └── resources/
│   │       ├── application.yaml            # Ktor server configuration
│   │       └── logback.xml                 # Logging configuration
│   └── test/
│       └── kotlin/
│           └── ApplicationTest.kt          # HTTP endpoint tests
├── build.gradle.kts                        # Gradle build configuration
├── gradle.properties                       # Gradle settings
└── README.md                                # Project documentation
```

## Directory Purposes

**`src/main/kotlin/`:**
- Purpose: All Kotlin application source code
- Contains: Entry point, configuration, services, routing, drivers, utilities
- Key files: `Application.kt`, `DependencyInjection.kt`

**`src/main/kotlin/config/`:**
- Purpose: Application configuration modules
- Contains: HTTP setup, monitoring, JSON serialization
- Key files: `HTTP.kt`, `Serialization.kt`

**`src/main/kotlin/di/`:**
- Purpose: Dependency injection and service wiring
- Contains: Koin DI module definitions
- Key files: `DependencyInjection.kt`

**`src/main/kotlin/routing/`:**
- Purpose: HTTP endpoint definitions and request routing
- Contains: Ktor route handlers for REST API
- Key files: `Routing.kt` (POST/GET `/api/text`, Swagger at `/openapi`)

**`src/main/kotlin/service/`:**
- Purpose: Core business logic and state management
- Contains: Text input service, display driver service
- Key files: 
  - `ReaderInput.kt` - text queue and input handling
  - `ScreenDriver.kt` - async display rendering and updates

**`src/main/kotlin/driver/`:**
- Purpose: Hardware-level drivers
- Contains: MAX7219 LED matrix SPI driver
- Key files: `Max7219Matrix.kt`

**`src/main/kotlin/utils/`:**
- Purpose: Shared utility functions
- Contains: Font definitions and bitmap operations
- Key files: `Font.kt` (ASCII font with 5-byte character bitmaps)

**`src/main/resources/`:**
- Purpose: Configuration files and assets
- Contains: Server settings, logging configuration
- Key files: 
  - `application.yaml` - Port, modules, debugging flags
  - `logback.xml` - Logging levels and output format

## Key File Locations

**Entry Points:**
- `src/main/kotlin/Application.kt` - Server startup, DI bootstrap, route registration

**Configuration:**
- `src/main/kotlin/config/HTTP.kt` - CORS, validation, status pages, Swagger
- `src/main/resources/application.yaml` - Ktor server port and behavior
- `src/main/resources/logback.xml` - Logging configuration

**Core Logic:**
- `src/main/kotlin/service/ReaderInput.kt` - Text input queue management
- `src/main/kotlin/service/ScreenDriver.kt` - Display rendering coordination
- `src/main/kotlin/driver/Max7219Matrix.kt` - SPI/hardware communication

**HTTP API:**
- `src/main/kotlin/routing/Routing.kt` - REST endpoints and validation

**Dependency Injection:**
- `src/main/kotlin/di/DependencyInjection.kt` - Service instance factory

## Naming Conventions

**Files:**
- **Service classes:** `[Domain].kt` (e.g., `ReaderInput.kt`, `ScreenDriver.kt`)
- **Driver classes:** `[Hardware].kt` (e.g., `Max7219Matrix.kt`)
- **Configuration classes:** `[Concern].kt` (e.g., `HTTP.kt`, `Serialization.kt`)
- **Routing classes:** `Routing.kt`
- **Utilities:** `[Purpose].kt` (e.g., `Font.kt`)

**Packages:**
- Root: `com.anjo.{config, di, routing, service, driver, utils}`

**Functions:**
- Verb-noun pattern: `displayText()`, `addToQueue()`, `sendSPI()`
- Prefixed queries: `get*()`, `is*()` for accessors

**Variables:**
- camelCase: `textBuffer`, `currentChar`, `delayMs`
- UPPER_CASE for constants in companion objects

## Where to Add New Code

**New HTTP Endpoint:**
- Add route in `src/main/kotlin/routing/Routing.kt`
- Add corresponding service method in `src/main/kotlin/service/`
- Register in DI if needed via `DependencyInjection.kt`

**New Service Feature:**
- Create new file in `src/main/kotlin/service/` (e.g., `TextProcessing.kt`)
- Wire into `DependencyInjection.kt`
- Expose via `Routing.kt` endpoints

**Hardware Driver:**
- New driver file in `src/main/kotlin/driver/`
- Service wrapper in `src/main/kotlin/service/`
- Configuration in `src/main/kotlin/config/` if needed

**Shared Utilities:**
- Add to existing or create new file in `src/main/kotlin/utils/`
- Keep utilities pure and stateless

**Configuration Additions:**
- Global settings: `DependencyInjection.kt` or dedicated config file
- Server behavior: `src/main/resources/application.yaml`
- Build settings: `build.gradle.kts`

## Special Directories

**`src/test/`:**
- Purpose: Unit and integration tests
- Contains: Test classes using Ktor `testApplication` DSL
- Committed: Yes

**`build/`:**
- Purpose: Compiled classes, build artifacts
- Generated: Yes (created by Gradle)
- Committed: No (in `.gitignore`)

**`.gradle/`:**
- Purpose: Gradle cache and daemon
- Generated: Yes
- Committed: No

---

*Structure analysis: 2024-12-19*

