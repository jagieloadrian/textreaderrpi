# Feature Landscape — TextReaderRpi v1.1

**Domain:** Embedded IoT text-display controller (Kotlin/Ktor, Raspberry Pi, home network)
**Researched:** 2026-06-11
**Confidence:** MEDIUM (domain knowledge; web search unavailable in this session)
**Downstream consumer:** Solo home-lab developer planning a roadmap for 5 new feature areas

---

## Context: What Already Exists (Do Not Re-Research)

- `POST /api/v1/text` with SCROLL/BLINK/REVERSE/FADE effects
- ONESHOT/RECURRING/CRON schedule CRUD + cancel
- MAX7219/LCD/OLED driver abstraction (pluggable)
- Health, metrics (runtime/API/hardware), rate limiting, Swagger UI
- Schedule management HTML page (Ktor HTML DSL, no JS framework)
- H2 default / PostgreSQL via env var (Exposed 1.3.0)

Everything below is **additive** — new surface on the existing foundation.

---

## Feature 1: Display Text History / Audit Log

### What This Is

A persistent, queryable log of every text that was rendered on the display: the message content, which effect was used, when it started and ended, and how it was triggered (immediate API call vs. a named schedule).

### Table Stakes

Features users expect from any history/audit feature. Missing = feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Persist each display event (text, effect, timestamp, source) | Core audit concept; otherwise history is ephemeral | Low | One new DB table in existing Exposed schema |
| List view with newest-first pagination | Standard for log UIs — no one wants a flat unbounded list | Low | Simple query, 20–50 rows per page is enough |
| Source label (IMMEDIATE vs schedule name) | Distinguishes manual sends from automated schedule fires | Low | Add `source` enum + `scheduleId` nullable FK to event table |
| Timestamp display in human-readable local time | Raw UTC timestamps in a home-lab UI are confusing | Low | Server-side formatting in Ktor HTML DSL |
| Effect displayed alongside text | Context for why a message looked a certain way | Low | Already stored in `DisplayRequest`; just persist it |

### Differentiators

Features that set the implementation apart without being expected.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| "Replay" button — re-send a historical message with one click | Saves time for recurring manual sends | Low-Med | POST to `/api/v1/text` with pre-filled body from history row |
| Duration column (how long the message actually ran) | Useful for ONESHOT debugging; shows if messages were interrupted | Low | Store `startedAt` + `endedAt`; null `endedAt` means still running |
| Filter by source or effect | Useful once history grows; quick UX win | Low | Server-side query param, no JS needed |

### Anti-Features (Scope Creep Traps)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Full-text search across history | Way beyond home-lab needs; adds query complexity | Use simple pagination + source/effect filter |
| Export to CSV/JSON | Nice to have but distracts from core work; zero demand signal | Use Swagger to call `GET /api/v1/history` directly |
| Retention policy / TTL auto-purge | Premature for H2 on a Pi with megabytes of data | Document manual `DELETE FROM display_events WHERE ...` as escape hatch |
| Separate history database / ELK stack | Massive over-engineering for 1 display on a home network | Single new table in existing H2 schema |
| Real-time streaming (WebSocket feed) | Adds JS complexity and a persistent connection to maintain | The history page auto-refreshes on browser reload; that's enough |

### Complexity Assessment

**Overall: Low.** One new DB table, one new route (`GET /api/v1/history` + HTML page), one event-capture call-site inside the existing display dispatch path. The hardest part is choosing exactly where to hook the write (before render? after? both?).

### Dependencies on Existing System

- Requires `DisplayEvent` capture inside `displayImmediate()` and schedule-fire code path (both already exist)
- Reuses existing Exposed DB setup — just a new `DisplayEvents` table object
- Reuses Ktor HTML DSL for list page — same pattern as schedule management page
- Interacts with scheduler rewrite (Feature 4): ensure event capture is preserved when Flaxoos is replaced

---

## Feature 2: Multi-Zone Displays

### What This Is

Configuration-driven routing so that different physical display units (e.g., two MAX7219 chains, or a MAX7219 + an OLED) can be targeted independently. A zone is a named logical display backed by one driver instance. API calls specify a zone; unzoned calls go to a default zone.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Named zones with a default | Without a default zone, all existing callers break (backward-compat requirement) | Med | Zone = name + driver type + driver config; default zone preserves v1.0 API behavior |
| Zone selection in `POST /api/v1/text` body | The core routing mechanism; `"zone": "left"` or omit for default | Low | Add optional `zone` field to `DisplayRequest` |
| Zone listing endpoint (`GET /api/v1/zones`) | Clients need to know what zones exist before targeting them | Low | Read from static config; no DB needed |
| Per-zone schedule targeting | Schedules already have all the context; zone is just another field | Med | Add `zone` field to schedule entity + schedule-fire dispatch |
| Independent effect pipelines per zone | Each zone must be independently scrollable/blinkable | Med | Each zone needs its own coroutine scope + driver instance |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Zone status in health/metrics | `GET /metrics` shows per-zone error counts and activity | Low | Extend existing metrics map with zone dimension |
| Zone shown in history | History entries tagged with which zone displayed the message | Low | Add `zone` column to `DisplayEvent` table |
| Zone-aware conflict policy | SKIP_NEW / REPLACE applies per-zone, not globally | Med | Each zone needs its own lock / running-job slot |

### Anti-Features (Scope Creep Traps)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Dynamic zone creation via API (`POST /api/v1/zones`) | Hardware wiring is physical; zones are static config, not runtime data | Define zones in env vars or YAML config file; restart to change |
| Zone groups / broadcast to all zones at once | Edge-case need; adds routing complexity | Caller can POST to each zone separately (2 requests) |
| Zone priority / preemption between zones | Turns a simple display into a scheduler of schedulers; enormous complexity | Each zone is fully independent; no cross-zone coordination |
| Display capability negotiation (does this zone support FADE?) | The driver abstraction already handles unsupported effects gracefully | Keep existing effect graceful-degradation pattern |
| Web-based zone wiring editor | Hardware wiring cannot be changed via UI anyway | YAML/env var config + docs |

### Complexity Assessment

**Overall: Medium-High.** This is the most architecturally significant feature. Requires refactoring `DisplayDriver` instantiation from singleton to a keyed registry (`Map<ZoneName, DisplayDriver>`). Every existing dispatch call-site must become zone-aware. Recommend implementing this after the scheduler rewrite (Feature 4) so the coroutine-per-zone model is clean from the start. Do not attempt in the same phase as the coroutine rewrite — too many moving parts simultaneously.

### Dependencies on Existing System

- Requires the driver abstraction (`DisplayDriver` interface) to support multiple instances — already an interface, good
- Requires per-zone coroutine scope in the new scheduler (Feature 4 dependency)
- Requires `zone` field added to `DisplayRequest`, schedule entity, and `DisplayEvent`
- The `POST /api/v1/text` endpoint must stay backward-compatible (omitted `zone` → default zone)
- History (Feature 1) should be implemented before or alongside so zone tagging is baked in from the start

---

## Feature 3: Webhooks / Push Notifications

### What This Is

When a scheduled message fires (either ONESHOT or RECURRING/CRON), the system makes an HTTP POST to a user-configured URL with a JSON payload describing the event. Useful for home automation integration (Home Assistant, n8n, Node-RED, custom scripts).

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Single global webhook URL configurable via env var | Simplest possible delivery; zero UI needed for basic use | Low | `WEBHOOK_URL` env var; blank = disabled |
| Fixed JSON payload: `{ scheduleId, scheduleName, text, effect, zone, firedAt }` | Consumers need a stable contract; minimize the surprise | Low | Build from existing `ScheduledJob` entity data |
| Fire-and-forget with timeout | Home-lab webhook receivers may be down; don't block the display | Low | `withTimeout(5_000)` + catch; log failure; move on |
| Enabled/disabled toggle | Allows temporarily disabling without removing the URL | Low | `WEBHOOK_ENABLED=true/false` env var (default false) |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Per-schedule webhook URL override | Different schedules can notify different endpoints (e.g., different HA automations) | Low-Med | Add optional `webhookUrl` field to schedule entity; falls back to global URL |
| Simple retry with exponential backoff (max 3 attempts) | Transient receiver outages are common in home networks | Med | Use Kotlin coroutines: `repeat(3) { attempt -> delay(2^attempt * 1000) }` |
| Webhook delivery log in history | Shows delivery status (success/failed/skipped) alongside display events | Low | Add `webhookStatus` column to `DisplayEvent` table |
| HMAC-SHA256 signature header | Allows webhook receiver to verify the payload came from this system | Low | `X-TextReader-Signature: sha256=<hex>` using configured secret; optional |

### Anti-Features (Scope Creep Traps)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Webhook management UI (create/edit/delete via browser) | One global URL + one per-schedule URL covers 95% of home-lab use cases without a CRUD UI | Env var + optional schedule field |
| Multiple webhook subscribers per event | Pub/sub message broker territory; far beyond home-lab scope | Forward to a hub like n8n which fans out to multiple targets |
| Webhook queue persistence (guarantee delivery after restart) | Complex; SQLite/Postgres queue needed; survivability not critical for display notifications | Accept that in-flight retries are lost on restart; log and move on |
| Webhook secret rotation / OAuth | Home network, trusted environment; HMAC optional not mandatory | Env var for secret; disabled by default |
| Separate webhook test endpoint (`POST /api/v1/webhooks/test`) | Useful but low priority; out of scope for MVP of this feature | Use curl to call the receiver directly for testing |

### Complexity Assessment

**Overall: Low-Medium.** The delivery itself (an HTTP POST from a coroutine) is trivial. The retry logic adds moderate complexity. The hard part is choosing exactly when to fire (after render confirms? after schedule trigger? before?). Recommendation: fire on schedule trigger, not on render completion — simpler, more predictable, avoids coupling to hardware timing.

### Dependencies on Existing System

- Requires schedule-fire call-site to call a `WebhookService.fire(event)` — hook is in the scheduler
- After the coroutine rewrite (Feature 4), the hook point is cleaner — recommend Feature 3 after Feature 4
- Uses Ktor's built-in `HttpClient` (already in Ktor dependency tree) for outgoing calls
- Optionally adds `webhookUrl` + `webhookStatus` fields to schedule entity and `DisplayEvent` — coordinate with Feature 1

---

## Feature 4: Coroutine Scheduler Rewrite (Replace Flaxoos JDBC)

### What This Is

Replace the Flaxoos JDBC-backed task scheduler (which uses a `task_locks` table and is designed for multi-node clustering) with a pure Kotlin coroutines implementation sized for a single-node Raspberry Pi. The new scheduler must preserve all v1.0 capabilities.

### What Must Be Preserved (Non-Negotiable Capabilities)

| Capability | Current Mechanism | New Mechanism |
|------------|------------------|---------------|
| ONESHOT — run once at a future time | Flaxoos one-shot task | `delay(until - now)` in a coroutine + `Job` reference |
| RECURRING — run every N seconds/minutes | Flaxoos recurring task | `while(active) { doWork(); delay(interval) }` coroutine loop |
| CRON — run on a cron expression | Flaxoos cron task | Use `kron` or `kotlinx-cron` to compute next-fire; `delay()` to it |
| Cancel a running schedule | `cancel(scheduleId)` endpoint | `Job.cancel()` on the coroutine job; tracked in a `ConcurrentHashMap<UUID, Job>` |
| Conflict policy (SKIP_NEW in v1.1 gap) | Not implemented; v1.1 gap | Check slot occupancy before launching new coroutine |
| Persist schedule definitions across restart | Exposed DB rows | Unchanged — DB schema remains; only execution engine changes |
| Resume schedules on startup | Load from DB and re-schedule | Query active schedules on startup; re-launch coroutines |

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| All 3 schedule types preserved | Removing types is a regression | Med | ONESHOT + RECURRING are trivial; CRON requires a library |
| Cancel still works | Core v1.0 feature | Low | `Job.cancel()` is simpler than Flaxoos cancel |
| Resume on restart | Without this, rebooting the Pi loses all schedules | Med | On startup: `SELECT * FROM schedules WHERE active = true` → re-create coroutines |
| No `task_locks` table | The whole point of the rewrite | Low | Drop Flaxoos dependency; remove migration |
| Memory bounded | Pi has limited RAM; don't spawn unbounded coroutines | Low | Each schedule = 1 suspended coroutine; cheap; but cap at e.g. 100 concurrent |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Named `CoroutineScope` per scheduler | Clean shutdown (`scope.cancel()`) on Ktor shutdown hook | Low | Use `SupervisorScope` so one failure doesn't cancel others |
| Structured concurrency — child job per schedule | `Job` hierarchy makes cancel/await safe | Low | Best practice; no extra complexity |
| CRON next-fire computed correctly at runtime | Avoids clock drift in long-running schedules | Med | Use `kotlinx-cron` or `CronUtils` for next-fire calculation |
| Jitter on startup resume | Avoid thundering-herd if many schedules all fire at once on boot | Low | Random `delay(0..2000ms)` per schedule on resume |

### Anti-Features (Scope Creep Traps)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Persistent job queue (write coroutine state to DB) | Kotlin coroutines are not serializable; impossible without a full queue library | Persist only schedule _definitions_; re-create execution on restart |
| Distributed locking / cluster-safe execution | Single-node Pi; no cluster; was the problem with Flaxoos | Remove all cluster-related code |
| Custom cron parser from scratch | Complex edge cases (L, W, #, ?) in cron syntax; not worth building | Use `CronUtils` (Java, well-tested) or `kron` Kotlin wrapper |
| Dead-letter queue for failed schedule executions | Overkill; log the failure + mark the schedule row as failed | Write `lastError` column to the schedule entity; done |
| Dynamic thread pool sizing | Coroutines are not threads; the coroutine dispatcher handles concurrency | Use `Dispatchers.Default` or a bounded IO dispatcher; don't tune manually |

### Complexity Assessment

**Overall: Medium.** The coroutine patterns are well understood. The hard parts are (a) correct CRON next-fire computation with DST awareness and (b) startup resume logic — getting the sequence right (load DB, compute next-fire, delay, execute, update DB) without race conditions. Recommend doing this phase early in v1.1 because it is a prerequisite for clean multi-zone (Feature 2) and cleaner webhook hooks (Feature 3).

### CRON Library Recommendation

Use **`com.cronutils:cron-utils`** (Java, Apache 2.0, actively maintained, used by Quartz/Spring ecosystem). It handles all cron dialects, DST, and next-fire computation. Do not write a cron parser. A Kotlin extension function wrapping `getNextValidTimeAfter()` is all that's needed.

### Dependencies on Existing System

- Exposed DB schema for schedules is unchanged — only the execution engine changes
- Ktor `ApplicationLifecycle` shutdown hook must cancel the `CoroutineScope` — use `environment.monitor.subscribe(ApplicationStopping)`
- The existing `SchedulerService` class is replaced; its API surface (methods called by routes) should stay the same
- Features 2, 3, and 1 all benefit from the scheduler being rewritten first — this is the highest-leverage phase

---

## Feature 5: UI/UX Refresh

### What This Is

Visual and interaction improvements to the Ktor HTML DSL pages. No JavaScript framework is added. Server-side rendering stays. Target: a clean, readable control panel that works on a phone browser from the couch.

### What Makes a Good Display Control UI (Home Network Context)

The primary usage pattern is: open on a phone/tablet, type a message, hit send, see it appear on the display. Secondary: glance at history or manage schedules. The UI must be fast to load (no heavy JS bundles), readable in dim light, and usable on small screens without zooming.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Mobile-first responsive layout | Home-lab users reach for phones; existing schedule page is desktop-only | Low | CSS flexbox/grid; no JS needed |
| Clear visual hierarchy — send form prominent | The send action is 80% of usage; it must be immediately obvious | Low | Large textarea, prominent button, above the fold |
| Effect selector as styled radio/select (not raw `<select>`) | The current HTML select works but feels raw; visual options feel deliberate | Low | CSS-styled radio buttons or a segmented control (pure CSS) |
| Schedule list shows status (running/paused/done) clearly | Color coding or badges — not just text labels | Low | CSS classes per status; server-side conditional |
| Error messages inline, not browser alert() | Consistent with modern web expectations | Low | Already done for API errors; apply to form validation too |
| History page (new) follows same layout patterns | Consistency — same nav, same spacing | Low | Follow the schedule page template |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Dark mode via `prefers-color-scheme` | Displays are often in dim rooms; dark UI reduces eye strain | Low | CSS media query + CSS custom properties (variables) |
| Keyboard shortcut: Ctrl+Enter to send | Power-user convenience; feels professional | Low | One `<script>` tag with `keydown` listener — tiny, no framework needed |
| Toast/flash message on successful send (HTML refresh trick) | Confirms the action without a full page change | Low | Redirect-after-POST to same page with `?success=1`; show banner if param present |
| Zone selector on send form (once multi-zone is built) | Allows targeting specific zones from the same form | Low | Add `<select name="zone">` populated from `GET /api/v1/zones` |
| Effect preview tooltip / description | Users often forget what FADE vs BLINK looks like | Low | `<details>/<summary>` HTML element per effect; pure HTML |

### Anti-Features (Scope Creep Traps)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| JavaScript framework (React, Vue, Htmx) | Adds build tooling, dependencies, and complexity to a Ktor HTML DSL project | Keep server-side rendering; one tiny vanilla JS snippet is the limit |
| Live preview of what the text will look like on display | Requires a font/pixel-map emulator in the browser; enormous scope | Document the hardware behavior; label effects clearly |
| Drag-and-drop schedule reordering | Requires JavaScript; schedules don't have a priority order anyway | Keep table with sort-by-name/time |
| User-configurable themes | Zero demand for a single-user home-lab app | Ship one dark + one light mode via media query |
| Full-page SPA navigation (no page reload) | History push + JS routing for a 3-page app is absurd complexity | Tolerate full-page reloads; they're instant on local network |
| Inline editing (click cell to edit) | JS-heavy; error-prone; edit forms are fine | Keep edit as a separate `/schedules/{id}/edit` page |

### Complexity Assessment

**Overall: Low.** All improvements are CSS and minor HTML changes within the existing Ktor HTML DSL structure. The only JS allowed is one small inline snippet (keyboard shortcut + maybe a toast). The biggest risk is CSS specificity wars if the existing styles are not organized with CSS custom properties from the start — define a design token layer (`--color-primary`, `--spacing-md`, etc.) at the top of the stylesheet to avoid this.

### Dependencies on Existing System

- Built entirely on existing Ktor HTML DSL pages — no new route infrastructure needed
- History page (Feature 1) needs to be built following the same UI patterns — align both in the same phase
- Zone selector (Feature 2) appears on the send form — UI can stub it as disabled until zone API exists
- No dependency on scheduler rewrite (Feature 4) — UI work is independent

---

## Feature Dependency Graph

```
Feature 4 (Scheduler Rewrite)
    └─→ Feature 2 (Multi-Zone)      [zone needs per-zone coroutine scope]
    └─→ Feature 3 (Webhooks)        [webhook hook is cleaner post-rewrite]

Feature 1 (History)
    └─→ Feature 5 (UI Refresh)      [history page follows same UI template]
    └─→ Feature 3 (Webhooks)        [webhook status column in DisplayEvent]

Feature 5 (UI Refresh)
    └─→ Feature 2 (Multi-Zone)      [zone selector on send form]

Feature 3 (Webhooks)                [largely standalone; needs schedule fire hook]
```

**Recommended phase order for solo developer:**

1. Feature 4 — Scheduler Rewrite (foundational; everything else is cleaner after this)
2. Feature 1 — History / Audit Log (low complexity; high visible value; informs what the event model looks like for Features 2 & 3)
3. Feature 5 — UI/UX Refresh (include history page in same sweep; zero infra needed)
4. Feature 3 — Webhooks (hook is now clean; event model exists from Feature 1)
5. Feature 2 — Multi-Zone (highest architectural complexity; do last when everything else is stable)

---

## MVP Recommendation for v1.1

**Must ship** (high value, low-medium complexity):
1. Feature 4 — Scheduler Rewrite (technical debt; unlocks clean architecture)
2. Feature 1 — History / Audit Log (visible daily-use value; low effort)
3. Feature 5 — UI/UX Refresh (same effort as writing the history page; do together)

**Should ship** (medium complexity, clear value):
4. Feature 3 — Webhooks (home automation integration; low risk)

**Defer if time-constrained** (highest complexity, niche home-lab use case):
5. Feature 2 — Multi-Zone (consider v1.2 if the Pi only has one display wired up)

**Defer indefinitely:**
- Any of the anti-features listed above

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Feature 1 (History) | HIGH | Standard audit-log pattern; well understood |
| Feature 2 (Multi-Zone) | MEDIUM | Architecture is sound; exact driver refactor depends on current code shape |
| Feature 3 (Webhooks) | HIGH | Standard outgoing webhook pattern; well understood |
| Feature 4 (Scheduler Rewrite) | HIGH | Coroutine scheduler is a known Kotlin pattern; CronUtils is proven |
| Feature 5 (UI Refresh) | HIGH | Pure CSS/HTML on existing Ktor DSL; no unknowns |

*Note: Web search was unavailable during this research session. All findings are based on domain knowledge of IoT/embedded control systems, Kotlin/Ktor ecosystem patterns, and home-automation UI conventions. The MEDIUM confidence on Feature 2 reflects that multi-zone driver architecture is the one area where the existing code shape (not yet reviewed in detail) could change the implementation approach.*

---

## Sources

- Domain knowledge: Kotlin coroutines structured concurrency patterns (kotlinx.coroutines documentation)
- Domain knowledge: IoT audit log / event sourcing patterns in embedded controller systems
- Domain knowledge: Webhook design standards (GitHub, Stripe, Home Assistant webhook conventions)
- Domain knowledge: Home-automation control panel UX (Home Assistant, Node-RED, Grafana dashboard patterns)
- Domain knowledge: CronUtils Java library (com.cronutils:cron-utils) — Apache 2.0, used by Quartz/Spring
- Project context: `.planning/PROJECT.md` — v1.0 delivered features and v1.1 requirements
