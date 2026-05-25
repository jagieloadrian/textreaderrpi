# External Integrations

**Analysis Date:** 2025-01-10

## APIs & External Services

**HTTP REST API:**
- Ktor Server (embeddedServer in `src/main/kotlin/Application.kt`)
  - Default port: 8080
  - Endpoints defined in `Application.kt` routing block
  - Content negotiation: JSON via `kotlinx.serialization`
  - Request/response serialization: Automatic JSON marshalling

**Text Input API:**
- REST POST endpoint for receiving text content
  - Path: Defined in `src/main/kotlin/Application.kt`
  - Input: JSON payload with text to display
  - Processing: Text flows through `ScreenDriverService` for display rendering

## Hardware Integrations

**GPIO & SPI Control:**
- Framework: Pi4J 2.6.0 (`com.pi4j:pi4j-core`)
- Provider: `com.pi4j:pi4j-plugin-raspberrypi` (Raspberry Pi platform plugin)
- Management: Handled by Max7219Matrix driver

**MAX7219 LED Matrix:**
- Library: Pi4J with custom SPI driver in `src/main/kotlin/driver/Max7219Matrix.kt`
- Interface: SPI (Serial Peripheral Interface)
- Hardware: 8x8 LED matrix with MAX7219 IC
- Control signals:
  - CS (Chip Select): GPIO pin for device selection
  - CLK (Clock): SPI clock line
  - DIN (Data In): SPI MOSI (Master Out Slave In)
- Features:
  - Brightness control via MAX7219 intensity register
  - Column-based rendering for text scrolling

**Text-to-Display Pipeline:**
- Input: HTTP request → `routing/Routing.kt` endpoint
- Service Layer: `service/ScreenDriverService.kt` coordinates rendering
- Rendering: Text conversion to column bitmaps (8-pixel high format)
- Display: `driver/Max7219Matrix.kt` writes column data via SPI to MAX7219
- Scrolling: Animated left-to-right scroll (column shifting)

## Data Storage

**In-Memory State:**
- Current display buffer: Maintained in `Max7219Matrix.kt`
- Text queue: Queue or buffer in `ReaderInputService`
- No persistent storage detected

## Authentication & Identity

**Auth Provider:**
- None detected - No authentication implemented
- API endpoints are publicly accessible
- Suitable for local network deployment (Raspberry Pi on isolated network)

## Monitoring & Observability

**Logging:**
- Ktor's built-in logging integration
- Application configuration in `src/main/resources/application.yaml`
- Console output by default

**Status Endpoint:**
- Status pages configured via Ktor plugins
- Swagger documentation at `/openapi` (if enabled)

## CI/CD & Deployment

**Build Artifacts:**
- Fat JAR with Shadow plugin: Recommended for Raspberry Pi deployment
- All dependencies included; no classpath setup required

**Deployment Process:**
- SCP/SSH fat JAR to Raspberry Pi
- Execute: `java -jar TextReaderRpi-0.0.1-all.jar`
- Server binds to HTTP port 8080 (configurable via `application.yaml`)

## Environment Configuration

**Configuration Files:**
- `src/main/resources/application.yaml` - Ktor server configuration (port, modules)
- `src/main/resources/logback.xml` - Logging configuration

**Hardcoded Configuration Locations:**
- HTTP port: Configured in `application.yaml` (port 8080 default)
- GPIO pins: Initialized in Pi4J context setup
- SPI settings: `Max7219Matrix.kt` driver class

## Dependency Injection

**Pattern:**
- Ktor's built-in DI system via `install {}` blocks
- Services instantiated and provided through Ktor's DI mechanism
- `DependencyInjection.kt` module configuration

## Port Configuration

**Default HTTP Port:**
- 8080 (configured in `src/main/resources/application.yaml`)
- Accessible on Raspberry Pi network at: `http://<pi-ip>:8080/`

## Hardware Assumptions

**GPIO/SPI Configuration:**
- BCM pin numbering convention (Broadcom chip numbering)
- Pi4J auto-initialization with available providers

**SPI Interface:**
- Default SPI bus: `/dev/spidev0.0` (typical Raspberry Pi SPI0)
- Frequency: Optimized clock speed configured in driver
- Mode: SPI mode 0 standard for MAX7219

---

*Integration audit: 2025-01-10*

