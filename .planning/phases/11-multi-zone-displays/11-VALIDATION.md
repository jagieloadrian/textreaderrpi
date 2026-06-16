---
phase: 11
slug: multi-zone-displays
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-16
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (FunSpec, `should` convention) + JUnit5 platform |
| **Config file** | none — JUnit5 platform via `useJUnitPlatform()` in build.gradle.kts |
| **Quick run command** | `./gradlew test --tests "com.anjo.zone.*" --tests "com.anjo.service.ZoneRegistry*" --tests "com.anjo.service.NetworkDiscovery*" -x jacocoTestCoverageVerification` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.zone.*" --tests "com.anjo.service.ZoneRegistry*" --tests "com.anjo.service.NetworkDiscovery*" -x jacocoTestCoverageVerification`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-T1 | 01 | 1 | ZONE-01 | — | N/A | compile | `./gradlew compileKotlin -x test` | N/A | ⬜ pending |
| 11-01-T2 | 01 | 1 | ZONE-01, ZONE-06 | — | N/A | unit | `./gradlew test --tests "com.anjo.db.ZoneRepositoryTest"` | ❌ W0 | ⬜ pending |
| 11-02-T1 | 02 | 1 | ZONE-01 | — | N/A | compile | `./gradlew compileKotlin -x test` | N/A | ⬜ pending |
| 11-02-T2 | 02 | 1 | ZONE-01, ZONE-04, ZONE-05 | — | N/A | unit | `./gradlew test --tests "com.anjo.zone.LocalZoneDriverTest"` | ❌ W0 | ⬜ pending |
| 11-03-T1 | 03 | 2 | ZONE-01, ZONE-02 | — | N/A | unit | `./gradlew test --tests "com.anjo.service.ZoneRegistryTest"` | ❌ W0 | ⬜ pending |
| 11-03-T2 | 03 | 2 | ZONE-07 | — | POST /api/v1/text?zone=X: zoneId validated non-empty, max 64 chars; OFFLINE → 503 | integration | `./gradlew test --tests "com.anjo.routing.TextApiRouteTest" --tests "com.anjo.ApplicationTest"` | ✅ modify | ⬜ pending |
| 11-04-T1 | 04 | 3 | ZONE-04, ZONE-05 | — | N/A | unit | `./gradlew test --tests "com.anjo.zone.NetworkZoneDriverTest"` | ❌ W0 | ⬜ pending |
| 11-04-T2 | 04 | 3 | ZONE-02, ZONE-03, ZONE-06, ZONE-08 | T-11-01 | POST /api/v1/zones/{ip} rejects non-RFC1918 IPs with 400 | integration | `./gradlew test --tests "com.anjo.service.NetworkDiscoveryServiceTest" --tests "com.anjo.routing.ZoneRoutesTest"` | ❌ W0 | ⬜ pending |
| 11-04-T3 | 04 | 3 | all | — | ZoneRegistry, ZoneRepository, NetworkDiscoveryService resolve in DI | smoke | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ modify | ⬜ pending |
| 11-05-T1 | 05 | 4 | ZONE-06, ZONE-08 | — | N/A | integration | `./gradlew test --tests "com.anjo.routing.ZonesUIRoutesTest"` | ❌ W0 | ⬜ pending |
| 11-05-T2 | 05 | 4 | ZONE-06, ZONE-08 | — | N/A | static | `node --check src/main/resources/static/app.js` | ✅ modify | ⬜ pending |
| 11-05-T3 | 05 | 4 | ZONE-06 | — | human-verify: zone page renders, scan button works, manual add persists | checkpoint | checkpoint:human-verify | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/service/ZoneRegistryTest.kt` — ZONE-01 (init from config, broadcast routing)
- [ ] `src/test/kotlin/com/anjo/service/NetworkDiscoveryServiceTest.kt` — ZONE-02, ZONE-03 (UDP scan, mDNS callback)
- [ ] `src/test/kotlin/com/anjo/zone/NetworkZoneDriverTest.kt` — ZONE-04, ZONE-05 (WS send, offline on close)
- [ ] `src/test/kotlin/com/anjo/routing/ZoneRoutesTest.kt` — ZONE-06, ZONE-08 (manual register, list zones)
- [ ] `src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt` — ZoneRepository CRUD (follow ScheduleRepositoryTest pattern)

*Existing `TextApiRouteTest.kt` and `ApplicationTest.kt` need modification — not creation.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Two SPI MAX7219 displays render independently on same Pi | ZONE-01 | Requires real Pi hardware with two CS lines | Wire two MAX7219s on SPI0 CS0 + CS1; start app; POST to each zone by name; verify each displays correct text |
| mDNS device appears in zones within 30s of power-on | ZONE-03 | Requires real mDNS-announcing device on LAN | Power on a device announcing `_textreaderrpi._tcp.local`; wait ≤30s; GET /api/v1/zones and confirm it appears |
| WebSocket heartbeat marks zone OFFLINE on disconnect | ZONE-05 | Requires live WebSocket client | Start app with a connected NetworkZoneDriver; kill the external device; wait for ping timeout; confirm OFFLINE status |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
