# Technology Stack

**Analysis Date:** 2025-01-10

## Languages

**Primary:**
- Kotlin 2.1.0 - All application source code and build configuration

**Secondary:**
- Java - Runtime platform (JVM-based)

## Runtime

**Environment:**
- Java 21 (specified in `gradle.properties` as `javaVersion = 21`)
- Raspberry Pi (target deployment platform)

**Package Manager:**
- Gradle 8.11.1 (specified in `gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not applicable (Gradle resolves dependencies dynamically)

## Frameworks

**Core Web:**
- Ktor 2.3.12 - HTTP server and REST API framework
  - Location: `src/main/kotlin/Application.kt` (embeddedServer setup)
  - Features: Routing, content negotiation, JSON serialization

**Testing:**
- Ktor Server Tests - Integrated test client
- Kotlin Test - Assertion framework

**Build/Dev:**
- Gradle 8.11.1 - Build automation and dependency management
- Kotlin Gradle Plugin - Kotlin compilation and build tasks
- Shadow plugin - Fat JAR generation for deployment

## Key Dependencies

**Critical:**
- `io.ktor:ktor-server-core:2.3.12` - Core Ktor server functionality
- `io.ktor:ktor-server-netty:2.3.12` - Netty engine for HTTP server
- `io.ktor:ktor-server-content-negotiation:2.3.12` - Content negotiation (JSON)
- `io.ktor:ktor-serialization-kotlinx-json:2.3.12` - Kotlinx serialization for JSON
- `com.pi4j:pi4j-core:2.6.0` - Pi4J core for GPIO and SPI control
- `com.pi4j:pi4j-plugin-raspberrypi:2.6.0` - Raspberry Pi-specific Pi4J provider plugin

**Infrastructure:**
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1` - JSON serialization/deserialization
- `org.jetbrains.kotlin:kotlin-stdlib:2.1.0` - Kotlin standard library
- `org.jetbrains.kotlin:kotlin-reflect:2.1.0` - Kotlin reflection

**Testing:**
- `io.ktor:ktor-server-tests:2.3.12` - Ktor testing utilities
- `kotlin.test:kotlin-test-junit:1.8.10` - JUnit-based Kotlin test framework

## Configuration

**Build Configuration:**
- File: `build.gradle.kts` - Kotlin DSL-based Gradle build script
- Java version: 21
- Kotlin version: 2.1.0
- Gradle version: 8.11.1

**Source Layout:**
- Main source: `src/main/kotlin/`
- Test source: `src/test/kotlin/`
- Resources: `src/main/resources/` (application configuration)

**Build Output:**
- JAR: `build/libs/TextReaderRpi-1.0-SNAPSHOT.jar` (standard JAR)
- Fat JAR: `build/libs/TextReaderRpi-1.0-SNAPSHOT-all.jar` (Shadow plugin artifact with all dependencies)

## Platform Requirements

**Development:**
- JDK 21 or compatible
- Gradle 8.11.1 (wrapper provided)
- Kotlin 2.1.0 compiler

**Production/Deployment:**
- Raspberry Pi 4 (target hardware)
- JRE 21 (Java runtime)
- GPIO and SPI hardware interface support
- MAX7219 LED matrix display (8x32 configuration assumed)

**Raspberry Pi OS Requirements:**
- Linux-based OS with GPIO kernel module support
- Pi4J requires `/sys/class/gpio` interface (or compatible GPIO backend)
- SPI enabled on Pi4J-supported pins

---

*Stack analysis: 2025-01-10*

