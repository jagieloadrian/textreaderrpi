# Phase 7: Scheduler Schema Stabilisation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 07-scheduler-schema-stabilisation
**Areas discussed:** ConflictPolicy API surface, Schema migration approach, CRON validation point, firedAt restart guard, webhookUrl + zoneId defaults, SKIP_NEW busy detection, Flyway baseline

---

## ConflictPolicy API Surface

| Option | Description | Selected |
|--------|-------------|----------|
| Field on TextRequest | Per-request, consistent with `effect` field | ✓ |
| Global app config | One env var for all ad-hoc requests | |
| You decide | Claude picks | |

**User's choice:** Field on TextRequest

---

| Option | Description | Selected |
|--------|-------------|----------|
| Ad-hoc only | SKIP_NEW on TextRequest only; scheduled always fire | |
| Both ad-hoc and scheduled | `conflictPolicy` column on SchedulesTable too | ✓ |
| You decide | Claude picks | |

**User's choice:** Both ad-hoc and scheduled

---

| Option | Description | Selected |
|--------|-------------|----------|
| HTTP 200 with skipped:true | Silent success, matches TextResponse shape | |
| HTTP 409 Conflict | Semantically correct REST | |
| You decide | Claude picks | ✓ |

**User's choice:** You decide (Claude to pick based on existing API conventions)

---

| Option | Description | Selected |
|--------|-------------|----------|
| No — don't count skipped fires | Schedule fires 3 times successfully | ✓ |
| Yes — count as consumed | Every trigger attempt counts | |
| You decide | Claude picks | |

**User's choice:** Don't count skipped fires against maxRuns

---

## Schema Migration Approach

| Option | Description | Selected |
|--------|-------------|----------|
| SchemaUtils.createMissingTablesAndColumns | Exposed built-in, zero config | |
| Hand-rolled SQL migration file | More predictable, no new deps | ✓ |
| You decide | Claude picks | |

**User's choice:** Hand-rolled SQL migration file (then clarified: Flyway is acceptable)

---

| Option | Description | Selected |
|--------|-------------|----------|
| DatabaseFactory runs migration at startup | `schema_version` table, no Flyway | |
| Manual runOnce block in code | ALTER TABLE via Exposed exec() | |
| You decide | Claude picks | |

**User's choice:** Open to Flyway or Liquibase — whatever is easier

---

| Option | Description | Selected |
|--------|-------------|----------|
| Flyway | `org.flywaydb:flyway-core`, SQL files in resources | ✓ |
| Liquibase | XML/YAML change sets, heavier | |
| You decide | Claude picks | |

**User's choice:** Flyway

---

## CRON Validation Point

| Option | Description | Selected |
|--------|-------------|----------|
| At POST /api/v1/schedule | Validate at insert, persist ERROR, return 422 | ✓ |
| At scheduler launch time | Insert succeeds, scheduler updates to ERROR | |

**User's choice:** At POST /api/v1/schedule (insert time)

---

## firedAt Restart Guard

| Option | Description | Selected |
|--------|-------------|----------|
| In findAllActive() query | `AND firedAt IS NULL` in SQL | ✓ |
| In launchOneShot() at runtime | Runtime null check | |
| You decide | Claude picks | |

**User's choice:** In findAllActive() query

---

## webhookUrl + zoneId Defaults

| Option | Description | Selected |
|--------|-------------|----------|
| Just nullable varchar, no validation | Phase 10 adds validation | |
| Add URL validation for webhookUrl now | Basic URL pattern at insert time | ✓ |
| You decide | Claude picks | |

**User's choice:** Add URL format validation for webhookUrl now (Phase 7); zoneId is nullable varchar no validation

---

## SKIP_NEW Busy Detection

| Option | Description | Selected |
|--------|-------------|----------|
| displayMutex.isLocked | Existing mutex in ScreenDriverService | ✓ |
| Dedicated @Volatile isBusy flag | More explicit but redundant | |

**User's choice:** displayMutex.isLocked

---

## Flyway Baseline for Existing DB

| Option | Description | Selected |
|--------|-------------|----------|
| baselineOnMigrate=true | Safe for existing Pi installs | ✓ |
| Drop and recreate | Dev only, data loss risk | |
| You decide | Claude picks | |

**User's choice:** baselineOnMigrate=true, baselineVersion("1")

---

## Claude's Discretion

- HTTP response for SKIP_NEW busy path (200 vs 409) — user said "you decide"

## Deferred Ideas

None — discussion stayed within phase scope.
