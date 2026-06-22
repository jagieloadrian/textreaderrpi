# Phase 15: SSE Live Feed - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-22
**Phase:** 15-SSE Live Feed
**Areas discussed:** Event bus design, DisplayEvent payload, Status page widget scope, Replay buffer source

---

## Event bus design

| Option | Description | Selected |
|--------|-------------|----------|
| New DisplayEventBus service | Dedicated class with SharedFlow, injected into both ScreenDriverService and SSE route | ✓ |
| SharedFlow on ScreenDriverService itself | Expose liveEvents directly on ScreenDriverService | |
| You decide | Claude picks based on DI patterns | |

**User's choice:** New DisplayEventBus service
**Notes:** Keeps SSE route decoupled from the full display service.

---

| Option | Description | Selected |
|--------|-------------|----------|
| replay=5, extraBufferCapacity=0 | SharedFlow replays last 5 to new subscribers — satisfies LIVE-04 automatically | ✓ |
| replay=0, separate ring buffer | ArrayDeque(5) in addition to SharedFlow | |
| replay=0, query HistoryRepository | DB query per new SSE connection for replay | |

**User's choice:** replay=5, extraBufferCapacity=0
**Notes:** Matches LIVE-04 exactly; no extra data structure needed.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Singleton via DI | Registered in DependencyInjection.kt | ✓ |
| Created in Application.kt, passed in | Manual wiring | |

**User's choice:** Singleton via DI

---

| Option | Description | Selected |
|--------|-------------|----------|
| After historyRepository.insert() inside renderImmediate | Emit once persisted; authoritative with ID | ✓ |
| At top of displayImmediate/displayScheduled, before rendering | Faster but no history ID yet | |
| You decide | Claude picks hook point | |

**User's choice:** After historyRepository.insert() inside renderImmediate

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — both paths emit | displayImmediate and displayScheduled both emit | ✓ |
| No — immediate only | Scheduled text excluded in this phase | |

**User's choice:** Yes — both paths emit (single emit point in renderImmediate covers both)

---

## DisplayEvent payload

| Option | Description | Selected |
|--------|-------------|----------|
| Full HistoryRecord fields | text, effect, zoneId, displayedAt, id | ✓ |
| Text only | Just the displayed string | |
| text + zoneId + timestamp | Middle ground | |

**User's choice:** Full HistoryRecord fields (id, text, effect, zoneId, displayedAt)

---

| Option | Description | Selected |
|--------|-------------|----------|
| New DisplayEvent data class | Separate model decoupled from persistence | ✓ |
| Reuse HistoryRecord directly | Serialize HistoryRecord; coupled to DB schema | |

**User's choice:** New DisplayEvent data class

---

| Option | Description | Selected |
|--------|-------------|----------|
| Named event: display | Browser uses addEventListener('display', ...) | ✓ |
| Anonymous data-only frames | Browser uses onmessage | |

**User's choice:** Named event: display

---

| Option | Description | Selected |
|--------|-------------|----------|
| Exclude webhookStatus and scheduleId | Keep DisplayEvent lean | ✓ |
| Include all HistoryRecord fields | Mirror HistoryRecord fully | |

**User's choice:** Exclude — webhookStatus and scheduleId are operational metadata

---

## Status page widget scope

| Option | Description | Selected |
|--------|-------------|----------|
| New 'Live Feed' article at the top | Prominent, above System/Display/Hardware sections | ✓ |
| Inside existing 'Display' article | Compact but competes for space | |
| You decide | Claude picks layout | |

**User's choice:** New 'Live Feed' article at the top

---

| Option | Description | Selected |
|--------|-------------|----------|
| Single latest event | One prominent row; replaces content on each event | ✓ |
| Scrollable list of last 5 events | Shows last 5; adds height | |
| Single event + counter | Current text + update count | |

**User's choice:** Single latest event — always the most recent

---

| Option | Description | Selected |
|--------|-------------|----------|
| Show last replayed event immediately | EventSource replay populates widget on connect | ✓ |
| 'Waiting for display activity...' placeholder | Explicit waiting state | |
| 'Connecting...' spinner | Loading state during connection | |

**User's choice:** Show last replayed event immediately (no placeholder needed)

---

| Option | Description | Selected |
|--------|-------------|----------|
| External .js file | Separate live-feed.js in resources/static/ | ✓ |
| Inline script block in StatusPage.kt | Consistent with existing polling script | |

**User's choice:** External .js file

---

| Option | Description | Selected |
|--------|-------------|----------|
| New live-feed.js | Isolated; loaded only on /status | ✓ |
| Add to existing app.js | EventSource connects on all pages | |

**User's choice:** New live-feed.js, loaded only in StatusPage.kt

---

## Replay buffer source

| Option | Description | Selected |
|--------|-------------|----------|
| Nothing — replay resets on restart | SharedFlow starts empty on boot | ✓ |
| Warm replay buffer from HistoryRepository on startup | Survives restarts; DB coupling | |
| Query DB per-connection for replay | Authoritative but per-connection DB latency | |

**User's choice:** Replay resets on restart — history page covers past events

---

| Option | Description | Selected |
|--------|-------------|----------|
| No — all zones in one stream | Clients filter client-side if needed | ✓ |
| Yes — ?zone=X filters server-side | Targeted stream; adds per-connection filter | |

**User's choice:** No zone filter — all zones in one stream

---

| Option | Description | Selected |
|--------|-------------|----------|
| Ktor SSE plugin handles disconnect automatically | Cancels collecting coroutine on client disconnect | ✓ |
| Explicit try/catch CancellationException | Manual cleanup + logging | |

**User's choice:** Ktor handles it automatically

---

| Option | Description | Selected |
|--------|-------------|----------|
| No limit | Home lab scale; lightweight coroutines | ✓ |
| Soft limit with 503 rejection | Safety at scale | |

**User's choice:** No limit

---

## Claude's Discretion

None — all gray areas were decided explicitly.

## Deferred Ideas

- Zone-filtered SSE stream (`?zone=X`) — future phase if needed
- Per-connection SSE connection limit — overkill for home lab scale
- DB warm-up of replay buffer on restart — history page covers past events
