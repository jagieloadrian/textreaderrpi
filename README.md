# TextReaderRpi

[![CI](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml/badge.svg)](https://github.com/jagieloadrian/textreaderrpi/actions/workflows/ci.yml)

Ktor service for sending text to a Raspberry Pi MAX7219 display via Pi4J.

## Current API

- `POST /api/text`
  - request JSON: `{"text":"Hello"}`
  - success: `202 Accepted`
  - validation errors: `400 Bad Request` (`StatusPages` + structured `ErrorResponse`)
- Swagger UI: `/openapi`

## Current Code Layout

All runtime code lives under `src/main/kotlin/com/anjo`.

```text
com/anjo/
├── Application.kt
├── config/
│   ├── loader/        # ConfigLoader
│   └── model/         # ApplicationConfig, ApiConfig, DisplayConfig, ...
├── di/
│   ├── DependencyInjection.kt
│   ├── HTTP.kt
│   ├── Monitoring.kt
│   └── Serialization.kt
├── routing/
│   ├── Routing.kt                 # route composition
│   ├── TextRoutes.kt              # business endpoint(s)
│   ├── RequestValidationConfig.kt # RequestValidation plugin setup
│   └── ErrorHandling.kt           # StatusPages setup
├── service/
├── driver/
├── model/
├── validation/
└── utils/
```

## DI and Request Flow

1. `Application.module()` calls config + DI + routing setup.
2. `DependencyInjection.kt` builds and provides app dependencies (including driver/services).
3. `RequestValidationConfig.kt` installs Ktor `RequestValidation`.
4. `ErrorHandling.kt` installs `StatusPages` and maps exceptions to API errors.
5. `TextRoutes.kt` handles business route(s), currently `POST /api/text`.

## Configuration

Runtime config is read from `src/main/resources/application.yaml` through:

- `com.anjo.config.loader.ConfigLoader`
- `com.anjo.config.model.*`

Main sections in YAML:

- `display`
- `hardware`
- `api`
- `timing`
- `logging`

## Build and Run

```bash
./gradlew compileKotlin
./gradlew test
./gradlew build
./gradlew run
```

## Notes

- Project uses Ktor DI plugin (`io.ktor.server.plugins.di`).
- Hardware integration is through Pi4J and `Max7219Matrix`.
- Integration tests use Ktor `testApplication`.

