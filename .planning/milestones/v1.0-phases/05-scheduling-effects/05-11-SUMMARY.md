---
plan: 05-11
status: complete
completed: 2026-05-28
key-files:
  modified:
    - src/main/kotlin/com/anjo/service/SchedulerService.kt
    - src/main/kotlin/com/anjo/routing/ScheduleRoutes.kt
    - src/main/resources/static/app.js
    - src/main/resources/application.yaml
    - build.gradle.kts
    - gradle/ktor-libs.versions.toml
    - .devops/containers/docker-compose.yml
    - .devops/containers/build-image.sh
    - .devops/containers/Dockerfile
    - docs/deployment/production-guide.md
    - docs/operations/monitoring-alerting.md
    - README.md
  created:
    - src/test/kotlin/com/anjo/ApplicationTest.kt
    - src/test/kotlin/com/anjo/service/effect/EffectRendererTest.kt
    - .env.example
    - .devops/containers/.env.example
---

# Plan 05-11 Summary: Post-Execution Manual Fixes & DevOps Hardening

## What Was Built

Eight focused improvements after Phase 5 execution: a critical scheduler bug fix, a new cancel API endpoint, a UI Stop button for recurring schedules, full test suite restructuring, dependency version updates to latest, environment variable support across the entire config, Gradle-based Docker image building (no Dockerfile), and documentation refresh.

## Changes Made

### SchedulerService bug fix (T11-01)

`schedule()` previously called `cancel(id)` which wrote `DONE` to the database for every newly created schedule. This made schedules immediately invisible to `tickLoop` (which filters by `ACTIVE` status).

**Fix:**
```kotlin
// Before (broken):
fun schedule(s: Schedule) {
    cancel(s.id)  // ← always wrote DONE to DB, even for new schedules!
    ...
}

// After (fixed):
fun schedule(s: Schedule) {
    activeJobs.remove(s.id)?.cancel()  // cancel coroutine only, no DB touch
    ...
}
// cancel() is now exclusively for external/API use
```

### Cancel endpoint (T11-02)

`POST /api/v1/schedule/{id}/cancel` added to `ScheduleRoutes.kt`:
- Stops the in-memory coroutine and writes DONE to DB
- Does **not** delete the record
- Returns 204 (idempotent)

Lifecycle table:

| Type | Stays ACTIVE until |
|---|---|
| ONESHOT | fires → auto-DONE |
| RECURRING (maxRuns/expiresAt) | limit reached → auto-DONE |
| RECURRING (no limits) | manual `/cancel` |
| CRON | manual `/cancel` |

### UI Stop button (T11-03)

`app.js` table renders:
- **Stop** link (→ `POST /cancel`) for `status=ACTIVE AND type ∈ {RECURRING,CRON}`
- **Delete** link for all schedules

### Test suite restructuring (T11-04)

- All 20 test files: names follow `should ...` convention
- `EffectRendererTest` package: `com.anjo.effect` → `com.anjo.service.effect`
- `ApplicationTest` location: root `ApplicationTest.kt` → `com/anjo/ApplicationTest.kt`
- `TextApiRouteTest`: converted from JUnit `@Test` / `assertEquals` to Kotest `FunSpec` / `shouldBe`
- New tests added for scheduler fix:
  - `"should not write DONE to repository when scheduling new job"`
  - `"should not mark DONE when rescheduling same id"`
  - `"should write DONE to repository and stop coroutine on cancel"`
- New tests added for cancel endpoint:
  - `"should return 204 when cancelling active recurring schedule"`
  - `"should return 204 idempotently for cancel on unknown id"`

### Dependency updates (T11-05)

All core dependencies updated to latest stable versions. Exposed 1.3.0 required full package migration from `org.jetbrains.exposed.sql.*` to `org.jetbrains.exposed.v1.{core,jdbc}.*`. PostgreSQL JDBC driver added as `runtimeOnly`.

### Environment variable support (T11-06)

All 25 configuration properties use `${ENV_VAR:default}` syntax in `application.yaml`. `.env.example` template created at project root and `.devops/containers/`.

### Gradle Docker build (T11-07)

Ktor Gradle plugin now owns Docker image building:

```kotlin
ktor {
    docker {
        localImageName.set("textreaderrpi")
        imageTag.set("latest")
        externalRegistry.set(DockerImageRegistry.dockerHub(...))
    }
}
```

`Dockerfile` replaced with comment. `docker-compose.yml` no longer has a `build:` section — it uses `image: textreaderrpi:latest` built via `./gradlew publishImageToLocalRegistry`.

### Documentation (T11-08)

README, production-guide, and monitoring-alerting all updated to reflect current API (cancel endpoint), env vars table (25 vars), PostgreSQL switching instructions, and Gradle Docker tasks.

## Deviations

- `pi4j` stayed at 4.0.0 (4.0.1 does not exist in Maven Central or JitPack)
- `exposed-java-time` module kept (not renamed in 1.3.0 contrary to initial assumption)

## Test Results

```
./gradlew test -x jacocoTestCoverageVerification → BUILD SUCCESSFUL
./gradlew buildFatJar → BUILD SUCCESSFUL → build/libs/textreaderrpi.jar (34MB)
```

All 20 test classes, all green, `should` naming convention throughout.

## Self-Check: PASSED

- ✅ `POST /api/v1/schedule/{id}/cancel` returns 204
- ✅ New schedules NOT marked DONE immediately in DB
- ✅ `cancel()` writes DONE only when called explicitly
- ✅ Stop button visible for ACTIVE RECURRING/CRON in UI
- ✅ All test names start with `should`
- ✅ `EffectRendererTest` in `com.anjo.service.effect`
- ✅ `ApplicationTest` in `com.anjo` with 5 startup tests
- ✅ `./gradlew buildFatJar` produces `textreaderrpi.jar`
- ✅ `./gradlew publishImageToLocalRegistry` available (Ktor plugin docker task)
- ✅ `application.yaml` uses `${VAR:default}` for all 25 settings
- ✅ `.env.example` exists at project root and `.devops/containers/`

