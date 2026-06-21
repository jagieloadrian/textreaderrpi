# Phase 9: Display History + Audit Log - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-15
**Phase:** 9-Display History + Audit Log
**Areas discussed:** Recording injection, History page layout, Pagination shape, Source field encoding, Default sort order, Navigation & discoverability, DI wiring & smoke test update, Test coverage strategy

---

## Recording Injection

| Option | Description | Selected |
|--------|-------------|----------|
| Direct DI injection | Add HistoryRepository as constructor parameter to ScreenDriverService — same pattern as RetryConfig | ✓ |
| HistoryRecorder wrapper service | New service wrapping ScreenDriverService; keeps it unchanged | |
| You decide | Claude picks | |

**User's choice:** Direct DI injection
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| After executeWithRecovery() succeeds | Only record events that actually completed rendering | ✓ |
| Before executeWithRecovery() starts | Record attempt immediately, even on failure | |
| Both — two-step status update | Insert PENDING, update to DONE/FAILED | |

**User's choice:** After success only
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Swallow + log | Catch DB exception, log it, return true from display | ✓ |
| Propagate the error | Failed insert returns false/throws from display call | |

**User's choice:** Swallow + log — DB hiccup must not break display path
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| No — only successful displays | SKIP_NEW skips not recorded | ✓ |
| Yes — record with SKIPPED status | Full audit including dropped requests | |

**User's choice:** No recording for SKIP_NEW skips
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Synchronous in same suspendTransaction{} | DELETE oldest + INSERT new in one transaction | ✓ |
| Separate coroutine cleanup | Background job runs periodically | |

**User's choice:** Synchronous in insert(), same transaction
**Notes:** None

---

## History Page Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Dense HTML table | Matches /schedule page; reuses existing CSS | |
| Cards grid | Visual, more whitespace, one card per event | ✓ |
| You decide | Claude picks | |

**User's choice:** Cards grid
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Text + effect + timestamp + source | Core fields | |
| Text + effect + timestamp + source + scheduleId | Core + schedule link | ✓ |
| Just text + timestamp | Minimal | |

**User's choice:** Include scheduleId, linkable to /schedule?id=X
**Notes:** User also specified: scheduleId should be a clickable link, cards should be foldable, and a "show all" toggle should be available.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — filter by effect + source | HIST-03 requires it; zone disabled until Phase 11 | ✓ |
| Yes — effect only | Simpler | |
| No filters this phase | Defer to Phase 13 | |

**User's choice:** Yes — effect + source filters; zone placeholder disabled
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Prev/Next buttons | Simple footer | |
| Numbered page links | 1 2 3 … N footer | ✓ |

**User's choice:** Numbered page links
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| HTML <details>/<summary> | Pure browser-native collapse/expand, no JS | ✓ |
| Inline JS onclick | Small JS toggle | |

**User's choice:** HTML `<details>/<summary>`
**Notes:** Pure HTML approach consistent with no-JS-framework rule

---

| Option | Description | Selected |
|--------|-------------|----------|
| ?expand=all query param | Server renders all <details open>; no JS | ✓ |
| Inline JS toggle | Small <script> block | |

**User's choice:** `?expand=all` server-side query param
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| 20 per page | Standard | |
| 10 per page | Fewer | |
| 50 per page | More | |

**User's choice:** Page size selector: 20 / 50 / all via `?size=` query param
**Notes:** User requested a dropdown selector with all three options

---

## Pagination Shape

| Option | Description | Selected |
|--------|-------------|----------|
| page + size | ?page=1&size=20, 1-indexed | ✓ |
| offset + limit | ?offset=0&limit=20, SQL-style | |

**User's choice:** page + size

---

| Option | Description | Selected |
|--------|-------------|----------|
| {items, page, size, total} | Standard envelope with total for numbered pagination | ✓ |
| {items} only | Bare array | |
| {items, page, size, total, hasNext, hasPrev} | Full helpers | |

**User's choice:** `{ items, page, size, total }`

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — effect + source server-side filters | ?effect=SCROLL&source=IMMEDIATE | ✓ |
| No — client-side only | Return all, filter in browser | |

**User's choice:** Server-side filters

---

## Source Field Encoding

| Option | Description | Selected |
|--------|-------------|----------|
| source enum + nullable scheduleId | VARCHAR 'IMMEDIATE'/'SCHEDULED' + nullable scheduleId | ✓ |
| nullable scheduleId only | null = immediate | |
| source string encoding | 'IMMEDIATE' or 'SCHEDULED:id' | |

**User's choice:** source enum column + separate nullable scheduleId column

---

| Option | Description | Selected |
|--------|-------------|----------|
| zoneId VARCHAR(64) nullable default null | Matches SchedulesTable.zoneId | ✓ |
| zone VARCHAR(64) nullable | Shorter name | |

**User's choice:** zoneId to match SchedulesTable convention

---

| Option | Description | Selected |
|--------|-------------|----------|
| ISO-8601 VARCHAR(32) | Instant.now().toString() — matches SchedulesTable | ✓ |
| BIGINT epoch millis | Compact but inconsistent | |

**User's choice:** ISO-8601 VARCHAR(32)

---

| Option | Description | Selected |
|--------|-------------|----------|
| id, text, effect, source, scheduleId, zoneId, displayedAt | All HIST-01 fields + source + link | ✓ |
| Add conflictPolicy | Extra audit column | |
| Add effectParams JSON | Future-proofing | |

**User's choice:** Minimal schema: id, text, effect, source, scheduleId, zoneId, displayedAt

---

| Option | Description | Selected |
|--------|-------------|----------|
| V3__add_history_table.sql | Next Flyway version in sequence | ✓ |
| V3__display_history.sql | Alternative naming | |

**User's choice:** V3__add_history_table.sql

---

## Default Sort Order

| Option | Description | Selected |
|--------|-------------|----------|
| Newest first | Most recent at top — natural for audit log | ✓ |
| Oldest first | Chronological | |

**User's choice:** Newest first (ORDER BY displayedAt DESC)

---

## Navigation & Discoverability

| Option | Description | Selected |
|--------|-------------|----------|
| After Schedule | Home \| Schedule \| History \| Settings \| Status | ✓ |
| Before Settings | Home \| Schedule \| Settings \| History \| Status | |
| Last | Home \| Schedule \| Settings \| Status \| History | |

**User's choice:** After Schedule — logically grouped with Schedule

---

## DI Wiring & Smoke Test Update

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — resolve HistoryRepository in smoke test | Covers all DI bindings per REF-03 intent | ✓ |
| No — leave smoke test unchanged | New binding untested | |

**User's choice:** Yes — update ApplicationTest.kt DI smoke test

---

## Test Coverage Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Real H2 in-memory | testApplication{} with real DB — catches wiring failures | ✓ |
| Mock HistoryRepository | Faster but inconsistent with Phase 8 decision | |

**User's choice:** Real H2 in-memory — consistent with Phase 8 approach

---

## Claude's Discretion

None — all areas were answered by the user.

## Deferred Ideas

None — discussion stayed within phase scope.
