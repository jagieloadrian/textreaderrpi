# Coding Conventions

**Analysis Date:** 2024-01-20

## Naming Patterns

**Files:**
- Service files: `[ServiceName].kt` (e.g., `ReaderInput.kt`, `ScreenDriver.kt`)
- Driver files: `[HardwareName].kt` (e.g., `Max7219Matrix.kt`)
- Config files: `[Concern].kt` (e.g., `HTTP.kt`, `Serialization.kt`)
- Utility files: `[Purpose].kt` in `utils/` (e.g., `Font.kt`)

**Functions:**
- camelCase for function names
- Suspend functions for I/O: `suspend fun displayText(text: String)`
- Action verbs: `send*()`, `display*()`, `add*()`, `get*()`

**Variables:**
- camelCase for local variables and properties: `val textBuffer`, `var currentLine`
- UPPER_CASE in companion objects for constants

**Types:**
- PascalCase for classes and sealed classes
- Data classes for DTOs: `data class DisplayRequest(...)`

## Code Style

**Formatting:**
- Standard Kotlin conventions (4-space indentation)
- Extension functions for behavior: common pattern in the codebase

**Package Structure:**
- Clear responsibility boundaries: `config`, `di`, `routing`, `service`, `driver`, `utils`
- Shallow nesting (max 2 levels of subdirectories)

## Request Validation

**Location:** `routing/Routing.kt`

**Pattern:**
- Custom validation in Routing using Ktor's request body validation
- Bodies must start with "Hello" (configured in HTTP.kt or Routing.kt)
- Returns 400 Bad Request for validation failures

**Service-Level Validation:**
- Extension functions for validators (likely in service files)
- Example: `String.validate()` before processing

## Async/Await Patterns

**Location:** `service/ScreenDriver.kt`

**Pattern:**
- Suspend functions for I/O-bound operations
- Dispatchers.IO for hardware operations (SPI writes, GPIO)
- Coroutine scope management in services

**Usage:**
- Services accept `Dispatchers` for dependency injection
- Example: `withContext(Dispatchers.IO)` for SPI blocking calls

## Error Handling

**Pattern:**
- Exceptions bubble up from driver → service → routing
- HTTP layer catches exceptions and returns appropriate status codes
- Logging via logback configuration

**Service Layer:**
- Services may throw exceptions or wrap in Result types
- Errors logged before propagation

**HTTP Layer (Routing):**
- Routes handle exceptions with try-catch
- Convert to HTTP status codes (400, 500, etc.)

## Logging

**Framework:** Logback (configured in `logback.xml`)

**Pattern:**
- Kotlin standard logging or SLF4J (via logback)
- Configured levels in `logback.xml`

**Where to Log:**
- Service entry/exit points
- Error conditions
- Major state changes

## Comments and Documentation

**KDoc Style:**
- Used for public APIs
- Function parameter documentation
- Complex algorithm explanations

**When to Comment:**
- Non-obvious hardware behavior (SPI protocol, timing constraints)
- Design decisions specific to Raspberry Pi limitations
- Workarounds for known issues

## Idiomatic Kotlin Patterns

**Data Classes:**
- Used for request/response models
- Automatic copy(), equals(), hashCode(), toString()

**Extension Functions:**
- Validation logic: custom validators on String/Int types
- Domain-specific operations via extensions
- Keep related functionality grouped

**Scope Functions:**
- `let {}` for null-safe operations
- `apply {}` for builder-like patterns

**Object Singleton:**
- Used for shared instances and utility objects
- Thread-safe by design

## Dependency Injection

**Pattern:**
- Ktor's built-in DI via `provide {}` blocks in `DependencyInjection.kt`
- Constructor injection for services
- Singleton scope by default

**Service Injection Example:**
```kotlin
val screenDriver = get(DependencyKey<ScreenDriverService>())
```

## Type Safety

**Null Safety:**
- Explicit nullable types: `String?`
- Non-null types by default
- Null checks via `?.let` or `?.run`

**Generic Types:**
- Used in service interfaces
- Type-safe dependency injection

## String and Text Processing

**Character Bitmap:**
- Characters mapped to 5-byte arrays in `Font.kt`
- Each byte represents one column of pixels
- Built column-wise for scrolling animation

**Text Validation:**
- Check for valid ASCII range in `ReaderInput.kt` or service
- Maximum length constraints (configurable)

## Special Patterns

**Pi4J Context:**
- Auto-initialization with platform auto-detection
- Mock plugin available for testing
- No manual context cleanup in code (managed by Pi4J)

**Font Rendering:**
- Static font table: `Font.asciiFont`
- Character → bitmap lookup (instant conversion)
- 8-pixel high, 5-pixel wide characters

---

*Convention analysis: 2024-01-20*

