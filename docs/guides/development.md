<!-- generated-by: gsd-doc-writer -->
# Development Guide

## Local Setup

**Prerequisites:** JDK 25 (Temurin recommended), Gradle 9.4+ (or use the wrapper).

```bash
git clone <repo-url>
cd TextReaderRpi

# Build (compiles + downloads dependencies)
./gradlew build -x test
```

Hardware access is not required for development. The Pi4J mock plugin is available for local runs, and hardware discovery is disabled during tests via the `discovery.enabled=false` system property set automatically by `tasks.test`.

## Build Commands

| Command | Description |
|---|---|
| `./gradlew build` | Compile, test, verify coverage gate |
| `./gradlew build -x test` | Compile only, skip tests |
| `./gradlew test` | Run tests + generate JaCoCo report |
| `./gradlew jacocoTestReport` | Generate HTML/XML coverage report (after `test`) |
| `./gradlew jacocoTestCoverageVerification` | Enforce 70% line-coverage gate |
| `./gradlew run` | Run the server locally (requires `application.yaml` config) |
| `./gradlew installDist` | Assemble a distributable under `build/install/` |
| `./gradlew buildFatJar` | Package `build/libs/textreaderrpi.jar` (single deployable JAR) |
| `./gradlew publishImageToLocalRegistry` | Build and push a local Docker image tagged `textreaderrpi:latest` |
| `./gradlew jib` | Build multi-arch Docker image (arm64 + amd64) without a local daemon |

## Code Style

The project uses the official Kotlin code style (`kotlin.code.style=official` in `gradle.properties`). There is no separate linter binary — IntelliJ / the Kotlin compiler enforces style during compilation.

**Key conventions (from project feedback):**

- No comments inside implementation files. Intent is expressed through naming.
- Input validation belongs exclusively in `src/main/kotlin/com/anjo/validation/`. Route handlers must not contain validation logic.

Run the compiler as a style check:

```bash
./gradlew compileKotlin
```

## Branch Conventions

No formal convention is documented in this repository. The working convention observed from commit history is `feat/<issue-number>-<short-description>` (e.g., `feat/18-helm-chart`).

The default/main branch is `main`.

## PR Process

- Open a pull request against `main`.
- CI runs automatically on every push (`ci.yml`): build, test, coverage gate, and a JaCoCo report uploaded as an artifact.
- The coverage gate requires **70% line coverage** (excluding `driver/`, `utils/`, `config/model/`, and `model/` packages). PRs that drop below this threshold will have a failing CI check.
- No PR template is present in `.github/` — describe the change and link the relevant issue manually.
- Review by at least one maintainer is expected before merge.

## Key Source Directories

| Path | Purpose |
|---|---|
| `src/main/kotlin/com/anjo/routing/` | HTTP route definitions |
| `src/main/kotlin/com/anjo/service/` | Business logic, schedule management |
| `src/main/kotlin/com/anjo/zone/` | Multi-zone resolution and dispatch |
| `src/main/kotlin/com/anjo/driver/` | Hardware driver abstractions (MAX7219, LCD, OLED, mock) |
| `src/main/kotlin/com/anjo/di/` | Ktor plugin installation and dependency injection |
| `src/main/kotlin/com/anjo/validation/` | All request validation logic |
| `src/main/kotlin/com/anjo/db/` | Exposed ORM tables, Flyway migrations |
| `src/main/resources/application.yaml` | Central configuration (every value overridable via env var) |
| `src/test/kotlin/com/anjo/` | Tests mirror the main package structure |

## Configuration for Local Development

Copy the defaults from `application.yaml` and override as needed. Minimum for a local non-hardware run:

```bash
DISPLAY_TYPE=MOCK PORT=8080 ./gradlew run
```

See [docs/configuration/overview.md](../configuration/overview.md) for the full environment variable reference.
