---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Firmware + Features + Refactor + Ops
current_phase: 18
current_phase_name: kubernetes-helm
status: ready
stopped_at: Phase 18 context gathered
last_updated: "2026-06-24T16:16:35.713Z"
last_activity: 2026-06-24
last_activity_desc: Phase 17 all 9 plans complete; REVIEW.md, UAT.md, VERIFICATION.md done
progress:
  total_phases: 7
  completed_phases: 4
  total_plans: 19
  completed_plans: 19
  percent: 57
---

# Project State & Memory

**Last Updated:** 2026-06-24  
**Status:** Ready to plan Phase 18

## Current Position

Phase: 18 (kubernetes-helm) — NOT STARTED
Plan: 0 of 2
Status: Ready to plan
Last activity: 2026-06-24 — Phase 17 all 9 plans complete; REVIEW.md, UAT.md, VERIFICATION.md done

## Project Context

- **Name:** TextReaderRpi
- **Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface
- **Core value:** Simple, reliable one-way display control from any browser on the home network
- **Users:** Home lab enthusiasts, DIY electronics hobbyists
- **Timeline:** No hard deadline; iterative development

---

## Codebase Status

- **Language:** Kotlin 2.3.21, JDK 25 toolchain
- **Framework:** Ktor 3.5.0 (DI plugin + RequestValidation + StatusPages)
- **ORM:** Exposed 1.3.0 (`org.jetbrains.exposed.v1.*` packages)
- **Hardware:** Pi4J 4.0.0 + MAX7219 via SPI, LCD/OLED via I2C
- **Database:** H2 (embedded default) or PostgreSQL (via env vars)
- **Current package root:** `src/main/kotlin/com/anjo/...`
- **Test suite:** 27 test classes, Kotest `should` convention, JaCoCo ≥70% gate (actual 80.7% at v1.1)

---

## Existing Features (v1.1 complete)

- ✅ Typed YAML config with env var overrides (`${VAR:default}` for all 25 settings)
- ✅ Request validation via Ktor `RequestValidation`
- ✅ Centralized error mapping via Ktor `StatusPages`
- ✅ Text endpoint `POST /api/v1/text` with effects (SCROLL/BLINK/REVERSE/FADE)
- ✅ Display driver abstraction: MAX7219, LCD, OLED + OfflineDisplayDriver
- ✅ Health endpoints: `GET /health` (liveness) + `GET /health/ready` (readiness) + `GET /health/detail`
- ✅ `GET /metrics` — runtime/API/hardware metrics JSON
- ✅ Rate limiting (60 req/min API, 120 req/min metrics)
- ✅ Schedule CRUD API: `POST/GET/PATCH/DELETE /api/v1/schedule`
- ✅ Schedule types: ONESHOT, RECURRING, CRON; ConflictPolicy: INTERRUPT/SKIP_NEW
- ✅ Effect renderer strategy pattern (ScrollEffect/BlinkEffect/ReverseEffect/FadeEffect)
- ✅ Flyway 9.22.3 migrations (V1–V5, baselineOnMigrate=true)
- ✅ Display history: `GET /api/v1/history` (paginated, zone/effect filter) + HTML page
- ✅ Webhooks: fire-and-forget HTTP POST on schedule fire (5s timeout, fallback env var)
- ✅ Multi-zone: ZoneRegistry + local SPI zones + UDP/mDNS network zone autodiscovery
- ✅ NetworkZoneDriver: WebSocket client with reconnect + heartbeat
- ✅ Material 3 UI: side nav, dark mode, zone selector, effect preview, status/history/schedule pages

---

## v1.2 Roadmap Summary

| Phase | Name | Requirements | Plan estimate | Status |
|-------|------|--------------|---------------|--------|
| 14 | History Enhancements | HIST-04, HIST-05, HIST-06 | 3 plans | Not started |
| 15 | SSE Live Feed | LIVE-01, LIVE-02, LIVE-03 | 3 plans | Complete ✓ |
| 16 | Zone Management | ZONE-09, ZONE-10 | 4 plans | Complete ✓ |
| 17 | Firmware Skeletons | FW-01, FW-02 | 9 plans | Complete ✓ (human HW tests pending) |
| 18 | Kubernetes + Helm | OPS-01 | 2 plans | Not started |
| 19 | DRY/YAGNI Refactoring | REF-05 | 2 plans | Not started |
| 20 | Cleanup + Docs | CLEAN-01, DOCS-01 | 2 plans | Not started |

**Coverage:** 13/13 requirements mapped ✓

---

## New Dependencies (v1.2)

| Artifact | Version | Phase | Rationale |
|----------|---------|-------|-----------|
| `io.ktor:ktor-server-sse` | 3.5.0 | 15 | One-way SSE push; browser native reconnect |
| `io.ktor:ktor-server-websockets` | 3.5.0 | 16 | Server-side WS for FirmwareZoneDriver inbound sessions |
| `com.jsoizo:kotlin-csv-jvm` | 1.10.0 | 14 | RFC 4180 CSV; pure Kotlin; no transitive deps |

---

## Key Pitfalls for v1.2

1. **WS frame send from display coroutine** — `DefaultWebSocketSession.send()` not thread-safe across contexts. Use `Channel<String>` per session; display path calls `trySend()` only.
2. **Non-atomic check-then-insert in ZoneRegistry** — concurrent POSTs can both pass `isLocal` guard. Replace with `ConcurrentHashMap.compute()`.
3. **LIKE wildcard injection** — strip `%` and `_` from search term in `SearchValidators` (never in route handler per project rule).
4. **CSV Content-Disposition omitted** — browsers render inline without `Content-Disposition: attachment`. Set before `respondText`.
5. **Pi4J device mounts missing in K8s** — Pod crashes without `hostPath` for `/dev/gpiochip0` + `/dev/spidev0.0`. Helm `hardwareAccess.enabled` toggle with OFFLINE fallback.
6. **IDE Extract Method generates KDoc stubs** — violates no-comments rule; delete generated stubs manually.
7. **.planning compression losing decisions** — 4 decisions must survive: `displaySource` rename, `parseDiscoveryReply` SSRF guard, Material 3 dark-default CSS, `testApplication` first-HTTP-call guard.

---

## Accumulated Context

### Key Decisions (v1.1 planning)

| Decision | Rationale |
|----------|-----------|
| buildPacket placed in companion object (not instance method) | Tests call Max7219Matrix.buildPacket(...) without Pi4J construction — pure JVM testable |
| Per-row buildPacket(bitmap, offset, numDevices, row) signature | Called 8 times from render(); enables 2-byte per-row assertions in Kotest |
| Size guards removed from write() and displayStatic() | buildPacket handles short bitmaps safely via else false bounds check |
| isHardwareAvailable() as protected abstract hook in AbstractDisplayDriver | status() delegates to it polymorphically; drivers supply their own availability predicate |
| Max7219Matrix.isHardwareAvailable() = lastError == null | SPI always creates handle; failure stored as lastError |
| LcdDisplay/OledDisplay.isHardwareAvailable() = i2c != null && lastError == null | I2C handle is null when ctx.create() throws during init |
| Phase 6 first: fix MAX7219 before adding zones | Hardware bug amplifies across all multi-zone testing |
| Phase 7: targeted schema fixes, NOT a scheduler rewrite | SchedulerService is already coroutine-based; Flaxoos stays for HTTP rate limiting |
| Phase 8: DI smoke test is the first task | Silent DI failures block all refactor work safely |
| Phase 9: history cap MAX_ROWS=1000 in HistoryRepository.insert() | Prevents SD card fill on long-running Pi |
| Phase 10: webhooks fire in separate IOScope, withTimeout(5_000) | Does not block Dispatchers.Default (only 4 threads on Pi 4) |
| Phase 11: single shared Pi4J context, unique string IDs per zone | Avoids Pi4J SPI registration collision crash on startup |
| Phase 12: add headExtra to BaseLayout before any page-specific CSS | Prevents CSS cascade breaks in Ktor HTML DSL |
| Flyway 9.22.3 added as migration layer | baselineOnMigrate=true handles existing Pi installs; 9.x chosen for simpler community licensing |
| ONESHOT firedAt filter scoped to triggerType=ONESHOT | Avoids accidentally excluding RECURRING/CRON rows if firedAt ever set for those types |
| updateFiredAtAndDone() uses single suspendTransaction{} | Crash-safe atomic firedAt+status=DONE update prevents ONESHOT re-fire after Pi restart |
| displayMutex.isLocked (not tryLock) for SKIP_NEW | Snapshot read avoids deadlock; false negatives acceptable for drop-if-busy policy |
| fire() returns Boolean propagated from displayScheduled | launchRecurring conditionally increments runs only when display occurred |
| CRON validation moved to ScheduleRoutes POST handler | Allows persist-with-ERROR before 422 response; ScheduleValidators returns Valid for CRON |
| HTTP 202 with accepted=false for SKIP_NEW TextRoutes response | Consistent with existing 202 Accepted; client reads accepted boolean to detect skip |
| DI smoke test requires client.get() trigger before getBlocking() | testApplication defers module execution until first HTTP interaction |
| getBlocking() import explicit: io.ktor.server.plugins.di.getBlocking | Top-level extension function — not auto-imported by package membership |
| CoroutineDispatcher (not CloseableCoroutineDispatcher) for Dispatchers.IO key | Ktor DI infers declared type; key must match declared type |
| HistoryTable.displaySource (not .source) for Kotlin property name | Exposed Table inherits ColumnSet.source; naming collision causes compile error |
| Exposed 1.3.0 uses .limit(n).offset(start: Long) not .limit(n, offset) | API changed in 1.3.0; separate chained calls required for pagination offset |
| MAX_ROWS = 1000L as Long constant in HistoryRepository | selectAll().count() returns Long in Exposed 1.3.0; Long constant avoids widening comparison |
| parseDiscoveryReply ignores JSON "ip" field — always uses kernel-verified senderIp | Prevents SSRF via rogue device advertisement |
| localZoneIds set in ZoneRegistry blocks network zones from overwriting hardware zones | Prevents network re-registration from replacing local zones |
| ipIndex (ConcurrentHashMap ip→id) added to ZoneRegistry | Duplicate check in POST /zones/{ip} uses containsIp() not contains() |
| HistoryService concrete class introduced; routes typed to service not repository | HistoryRepository binding retained for test seeding |
| Material 3 CSS: dark palette in :root (default), light overrides under @media (prefers-color-scheme: light) | No data-theme attribute; fully CSS-driven; no JS toggle needed |
| SSR zone selectors (not client-fetched) | Zones available on page load without extra round-trip |
| fetchStatusData uses Promise.all([/health/detail, /metrics]) on DOMContentLoaded + setInterval(10000) | spans use .textContent (not innerHTML) — XSS-safe |
| applyEffectPreview removes all four effect classes then adds matching class | Clean class toggle without leftover state |

---

## Architecture Summary

**Layers:**

1. HTTP routes (`com/anjo/routing/*`)
2. Service layer (`com/anjo/service/*`, `com/anjo/service/effect/*`)
3. Driver abstraction + implementations (`com/anjo/driver/*`)
4. Zone layer (`com/anjo/zone/*`)
5. Database layer (`com/anjo/db/*`)
6. DI/plugin setup (`com/anjo/di/*`) + config (`com/anjo/config/*`)

**Data Flows:**

- `POST /api/v1/text` → `TextRoutes` → `ScreenDriverService.displayImmediate(text, effect)` → `EffectRenderer.render()` → `DisplayDriver` + `HistoryService.record()` + `LiveFeedService.emit()` (v1.2)
- `POST /api/v1/schedule` → `ScheduleRoutes` → `ScheduleRepository.insert()` + `SchedulerService.schedule()`
- `SchedulerService.fire()` → `EffectRendererFactory.create(effect)` → `ScreenDriverService.displayScheduled()` → `WebhookService.send()`
- `GET /api/v1/live` → SSE `MutableSharedFlow<LiveEvent>(replay=5)` (v1.2)
- `GET /ws/zone/{id}` → `FirmwareZoneDriver` registered in `ZoneRegistry` (v1.2)

---

## DevOps

- **Docker:** `./gradlew publishImageToLocalRegistry` (no Dockerfile in repo)
- **Compose:** `.devops/containers/docker-compose.yml` — full env var mapping, no build section
- **Host:** `.devops/host/` — systemd unit + install script
- **Env template:** `.env.example` at project root + `.devops/containers/.env.example`
- **Helm:** `.devops/helm/textreaderrpi/` (v1.2, Phase 18)

---

## Deferred Items

Items acknowledged and deferred at milestone close on 2026-06-21:

| Category | Item | Status |
|----------|------|--------|
| verification | Phase 08 — 08-VERIFICATION.md | human_needed |
| verification | Phase 09 — 09-VERIFICATION.md | human_needed |
| verification | Phase 11 — 11-VERIFICATION.md | human_needed |
| verification | Phase 12 — 12-VERIFICATION.md | human_needed |
| verification | Phase 15 — 15-VERIFICATION.md | human_needed |
| verification | Phase 16 — 16-VERIFICATION.md | human_needed |

*Note: All 6 are human-verify checkpoints (on-device Pi hardware testing / firmware WebSocket validation) that could not be run in CI.*

---

## Milestone Archive

- **v1.0 archived:** 2026-05-28
- **v1.1 archived:** 2026-06-21
- Roadmap archive v1.0: `.planning/milestones/v1.0-ROADMAP.md`
- Roadmap archive v1.1: `.planning/milestones/v1.1-ROADMAP.md`
- Git tags: `v1.0`, `v1.1`

## Performance Metrics

| Phase | Plan | Duration | Notes |
|-------|------|----------|-------|
| Phase 08 P03 | 12 minutes | 2 tasks | 3 files |
| Phase 08 P04 | 7 minutes | 2 tasks | 6 files |
| Phase 08 P05 | 5 minutes | 2 tasks | 0 files (sweep only) |
| Phase 09 P01 | 5 minutes | 2 tasks | 6 files |
| Phase 09 P02 | 11 minutes | 2 tasks | 8 files |
| Phase 09 P03 | 5 minutes | 2 tasks | 7 files |
| Phase 10 P01 | 22 minutes | 2 tasks | 10 files |
| Phase 10-webhooks P02 | 4 minutes | 2 tasks | 4 files |
| Phase 11 P01 | 12 minutes | 2 tasks | 8 files |
| Phase 11 P02 | 15 minutes | 2 tasks | 5 files |
| Phase 11-multi-zone-displays P03 | 18 minutes | 2 tasks | 16 files |
| Phase 11-multi-zone-displays P04 | 10 minutes | 3 tasks | 11 files |
| Phase 11-multi-zone-displays P05 | 6 minutes | 2 tasks | 6 files |
| Phase 11.2 P01 | 20 minutes | 2 tasks | 13 files |
| Phase 11.2 P02 | 12 minutes | 2 tasks | 6 files |
| Phase 11.2 P03 | 4 minutes | 2 tasks | 4 files |
| Phase 12 P02 | 4 minutes | — tasks | — files |
| Phase 12 P03 | 30 minutes | 2 tasks | 2 files |
| Phase 13 P01 | 2 minutes | 2 tasks | 2 files |
| Phase 13 P02 | 3 minutes | 2 tasks | 7 files |
| Phase 13 P03 | 6 minutes | 2 tasks | 6 files |
| Phase 16 P01 | 6min | 2 tasks | 13 files |
| Phase 16 P02 | 7min | 2 tasks | 7 files |
| Phase 16 P03 | 18min | 2 tasks | 8 files |
| Phase 16 P04 | 3min | 1 tasks | 1 files |

## Session

**Last session:** 2026-06-24T16:16:35.701Z
**Stopped at:** Phase 18 context gathered
**Resume file:** .planning/phases/18-kubernetes-helm/18-CONTEXT.md

## Decisions

- [Phase 16-01]: Null ip guard in addNetworkZone skips FIRMWARE zones until Plan 02 adds registerFirmwareZone
- [Phase 16-02]: FirmwareZoneDriver.send() uses channel.trySend() only; session.send() only in drain coroutine in attach()
- [Phase 16-02]: ZoneRegistry.addNetworkZone() migrated to compute() closing non-atomic race (D-06)
- [Phase 16-03]: ValidationResult.Invalid has no equals override — use .reasons.first() for assertions
- [Phase 16-03]: req.ip null guard via early 400 return (no !! operator) — post code review fix
- [Phase 16-03]: Zone name validated with `[a-zA-Z0-9._-]{1,64}` regex in ZoneValidators — prevents XSS via data-zone-id attribute
- [Phase 16-03]: FirmwareZoneDriver drain job tracked via AtomicReference<Job?>, cancelled on re-attach — prevents double-drain race
- [Phase 16-03]: UDP discovery reply parsed with kotlinx.json JsonObject; FIRMWARE type from UDP rejected — prevents type confusion from rogue LAN devices
- [Phase 16-04]: addZone() form.reset() on 201 + showToast() for all non-201 paths (422, 409, else) — eliminates resultDiv writes
- [Phase 17]: Use @EncodeDefault(Mode.NEVER) per-field for null omission in FirmwareMessage — Scoped to FirmwareMessage only; does not affect other kotlinx.serialization paths in the project
