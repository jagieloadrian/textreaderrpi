# Codebase Structure

**Analysis Date:** 2026-05-26

## Directory Layout

```text
TextReaderRpi/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/anjo/
│   │   │       ├── Application.kt
│   │   │       ├── config/
│   │   │       │   ├── loader/
│   │   │       │   │   └── ConfigLoader.kt
│   │   │       │   └── model/
│   │   │       │       ├── ApplicationConfig.kt
│   │   │       │       ├── ApiConfig.kt
│   │   │       │       ├── DisplayConfig.kt
│   │   │       │       ├── HardwareConfig.kt
│   │   │       │       ├── TimingConfig.kt
│   │   │       │       └── LoggingConfig.kt
│   │   │       ├── di/
│   │   │       │   ├── DependencyInjection.kt
│   │   │       │   ├── HTTP.kt
│   │   │       │   ├── Monitoring.kt
│   │   │       │   └── Serialization.kt
│   │   │       ├── routing/
│   │   │       │   ├── Routing.kt
│   │   │       │   ├── TextRoutes.kt
│   │   │       │   ├── RequestValidationConfig.kt
│   │   │       │   └── ErrorHandling.kt
│   │   │       ├── service/
│   │   │       │   ├── ReaderInput.kt
│   │   │       │   └── ScreenDriver.kt
│   │   │       ├── driver/
│   │   │       │   ├── DisplayDriver.kt
│   │   │       │   └── Max7219Matrix.kt
│   │   │       ├── model/
│   │   │       │   ├── TextRequest.kt
│   │   │       │   ├── TextResponse.kt
│   │   │       │   └── ErrorResponse.kt
│   │   │       ├── validation/
│   │   │       │   └── RequestValidators.kt
│   │   │       └── utils/
│   │   │           └── Font.kt
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── logback.xml
│   └── test/
│       └── kotlin/
│           ├── ApplicationTest.kt
│           └── routing/TextApiRouteTest.kt
└── README.md
```

## Ownership by Package

- `com.anjo.di`: framework-level plugin wiring (`HTTP`, `Monitoring`, `Serialization`) and dependency graph (`DependencyInjection`).
- `com.anjo.routing`: route composition, business endpoint handlers, request-validation plugin setup, status-page error mapping.
- `com.anjo.config.loader` + `com.anjo.config.model`: typed app config loaded from `application.yaml`.
- `com.anjo.service`: business/service orchestration.
- `com.anjo.driver`: hardware interface and MAX7219 implementation.
- `com.anjo.model`: API DTOs.
- `com.anjo.validation`: reusable validation rules used by RequestValidation.

## Where to Add New Code

- New business endpoint: add handler in `com/anjo/routing/*Routes.kt` and mount from `com/anjo/routing/Routing.kt`.
- New DI-provided dependency: wire in `com/anjo/di/DependencyInjection.kt`.
- New framework plugin setup: add module in `com/anjo/di/` (or `com/anjo/routing/` if endpoint-policy specific, like validation/error handlers).
- New runtime config field: extend `com/anjo/config/model/*` and parse in `com/anjo/config/loader/ConfigLoader.kt`.

## Notes

- Legacy top-level `src/main/kotlin/{config,di,routing,...}` paths are no longer source-of-truth; active code is under `src/main/kotlin/com/anjo/...`.
- API currently exposes `POST /text` (configured in `TextRoutes.kt`).

