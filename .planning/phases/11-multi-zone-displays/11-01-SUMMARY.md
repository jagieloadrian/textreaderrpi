---
phase: 11-multi-zone-displays
plan: "01"
subsystem: database
tags: [kotlin, exposed, flyway, ktor-client-websockets, jmdns, multi-zone]

requires:
  - phase: 10-webhooks
    provides: "Exposed 1.3.0 suspendTransaction pattern, HistoryTable/SchedulesTable column conventions"

provides:
  - "ZonesConfig + ZoneConfig data classes for YAML zones list"
  - "NetworkZonesTable Exposed Table for network_zones"
  - "NetworkZone @Serializable data class"
  - "ZoneRepository with findAll/findById/upsert/delete via suspendTransaction"
  - "V5__add_network_zones_table.sql Flyway migration"
  - "ktor-client-websockets and jmdns Maven dependencies wired"

affects: [11-02, 11-03, 11-04, 11-05]

tech-stack:
  added:
    - "ktor-client-websockets 3.5.0 (io.ktor:ktor-client-websockets)"
    - "jmdns 3.6.3 (org.jmdns:jmdns)"
  patterns:
    - "ZonesConfig/ZoneConfig follow Max7219Config defaults pattern — no nullables unless optional"
    - "NetworkZonesTable follows SchedulesTable Exposed Table object pattern exactly"
    - "ZoneRepository follows ScheduleRepository suspendTransaction + ResultRow.toX() pattern"
    - "No status column in network_zones — status is in-memory only per D-05"

key-files:
  created:
    - "gradle/ktor-libs.versions.toml (modified: added jmdns version + ktor-client-websockets + jmdns library aliases)"
    - "build.gradle.kts (modified: added implementation(ktorLibs.ktor.client.websockets) + implementation(ktorLibs.jmdns))"
    - "src/main/kotlin/com/anjo/config/model/ZonesConfig.kt"
    - "src/main/kotlin/com/anjo/db/NetworkZonesTable.kt"
    - "src/main/kotlin/com/anjo/model/NetworkZone.kt"
    - "src/main/kotlin/com/anjo/db/ZoneRepository.kt"
    - "src/main/resources/db/migration/V5__add_network_zones_table.sql"
    - "src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt"
  modified: []

key-decisions:
  - "ZoneConfig declares bus + chipSelect (not gpioPins map) per D-03 — matches Pi4J SPI API (SpiBus + SpiChipSelect)"
  - "No status column in NetworkZonesTable — zone status is runtime-only per D-05; ZoneDriver.status() computes it in memory"
  - "ZoneRepository.upsert() refreshes all writable fields on update including lastSeenAt — enables re-discovery to update IP"
  - "Flyway auto-discovers V5 migration — no SchemaUtils call needed per PATTERNS.md note on DatabaseFactory"

patterns-established:
  - "NetworkZonesTable: id varchar(64) PK, no status column, lastSeenAt nullable"
  - "ZoneRepository: all DB calls in suspendTransaction, private ResultRow.toNetworkZone() extension"

requirements-completed: [ZONE-01, ZONE-02, ZONE-03, ZONE-06]

duration: 12min
completed: 2026-06-16
---

# Phase 11 Plan 01: Multi-Zone Foundation Summary

**network_zones persistence layer with ZonesConfig model, ZoneRepository round-tripping across instances, V5 Flyway migration, and ktor-client-websockets + jmdns build dependencies**

## Performance

- **Duration:** 12 min
- **Started:** 2026-06-16T10:53:34Z
- **Completed:** 2026-06-16T11:05:34Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Two new Maven Central dependencies (ktor-client-websockets 3.5.0, jmdns 3.6.3) declared and resolving via Gradle version catalog
- `network_zones` Flyway V5 migration creates the table; ZoneRepository round-trips a zone across two separate repository instances (restart-survival, D-12/D-13)
- `ZonesConfig` / `ZoneConfig` data classes ready for Plan 03 wiring into ConfigLoader; per-zone `bus` + `chipSelect` fields align with Pi4J SPI API constraints (D-03)

## Task Commits

1. **Task 1: Add ktor-client-websockets + jmdns deps and ZonesConfig model** - `66fdbf4` (feat)
2. **Task 2: NetworkZonesTable + NetworkZone + ZoneRepository + V5 migration + test** - `24325e8` (feat)

**Plan metadata:** (see below — docs commit hash)

## Files Created/Modified

- `gradle/ktor-libs.versions.toml` - Added jmdns version key, ktor-client-websockets + jmdns library aliases
- `build.gradle.kts` - Added implementation for ktor-client-websockets and jmdns
- `src/main/kotlin/com/anjo/config/model/ZonesConfig.kt` - ZonesConfig(zones) + ZoneConfig(id, type, numDevices, bus, chipSelect)
- `src/main/kotlin/com/anjo/db/NetworkZonesTable.kt` - Exposed Table for network_zones; id PK; no status column
- `src/main/kotlin/com/anjo/model/NetworkZone.kt` - @Serializable data class matching table columns
- `src/main/kotlin/com/anjo/db/ZoneRepository.kt` - findAll/findById/upsert/delete via suspendTransaction
- `src/main/resources/db/migration/V5__add_network_zones_table.sql` - CREATE TABLE IF NOT EXISTS network_zones DDL
- `src/test/kotlin/com/anjo/db/ZoneRepositoryTest.kt` - 4 Kotest tests including restart-survival assertion

## Decisions Made

- `ZoneConfig.bus` and `ZoneConfig.chipSelect` fields match Pi4J `SpiBus`/`SpiChipSelect` API (D-03) — not a `gpioPins` map like `Max7219Config`
- No `status` column in `NetworkZonesTable` — status is in-memory derived from last render attempt (D-05)
- `upsert()` updates all writable fields (including `ip` and `name`) on re-discovery — allows IP address changes to be captured

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ZonesConfig` / `ZoneConfig` ready for Plan 03 wiring into `ApplicationConfig` and `ConfigLoader`
- `ZoneRepository` and `NetworkZone` model ready for use in `NetworkDiscoveryService` (Plan 02) and `ZoneRegistry` (Plan 03)
- V5 migration will run automatically on next application startup; no manual DB step needed
- `ktor-client-websockets` available for `NetworkZoneDriver` WebSocket client in Plan 02
- `jmdns` available for `NetworkDiscoveryService` mDNS listener in Plan 02

---
*Phase: 11-multi-zone-displays*
*Completed: 2026-06-16*

## Self-Check: PASSED

All 7 files confirmed on disk. Both task commits (66fdbf4, 24325e8) confirmed in git log.
