# Architecture

**Analysis Date:** 2026-05-26

## System Overview

```text
HTTP Request
   │
   ▼
com.anjo.routing.TextRoutes (business endpoints)
   │
   ▼
com.anjo.service.ReaderInputService
   │
   ▼
com.anjo.service.ScreenDriverService
   │
   ▼
com.anjo.driver.DisplayDriver (implemented by Max7219Matrix)
   │
   ▼
Pi4J SPI / MAX7219 hardware
```

## Composition and Plugin Setup

- `com.anjo.Application.module()` orchestrates startup in this order:
  1. `ConfigLoader.loadConfig()`
  2. `configureHTTP()`
  3. `configureMonitoring()`
  4. `configureSerialization()`
  5. `configureDI()`
  6. `configureRequestValidation()`
  7. `configureErrorHandling()`
  8. `configureRouting()`

## Package Responsibilities

- `com.anjo.di`
  - framework-level plugins: CORS/default headers, monitoring, serialization
  - dependency graph wiring in `DependencyInjection.kt`
- `com.anjo.routing`
  - route composition in `Routing.kt`
  - business endpoint handlers in `TextRoutes.kt`
  - request validation plugin setup in `RequestValidationConfig.kt`
  - status page error mapping in `ErrorHandling.kt`
- `com.anjo.config.loader` + `com.anjo.config.model`
  - typed runtime config from `application.yaml`
- `com.anjo.service`
  - use-case orchestration and dispatcher-bound execution
- `com.anjo.driver`
  - display abstraction + MAX7219 implementation

## API Surface (current)

- `POST /text` (implemented in `TextRoutes.kt`)
- Swagger UI: `/openapi`

## Validation and Error Pipeline

- Request payload validation is handled by Ktor `RequestValidation`.
- Invalid payloads are mapped by `StatusPages` to `400` with `ErrorResponse`.
- Unexpected exceptions are mapped by `StatusPages` to `500` with `ErrorResponse`.

## Constraints / Notes

- DI uses Ktor DI plugin (`io.ktor.server.plugins.di`).
- Hardware path depends on Pi4J mock/real provider availability at runtime.
- Existing tests still include root-route expectations; current routing composition does not define `GET /` in some manual refactor states.

