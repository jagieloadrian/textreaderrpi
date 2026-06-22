---
phase: 15-sse-live-feed
plan: 03
status: complete
completed: 2026-06-22
commits:
  - f3db0d3
  - 5532b32
  - 96332b8
---

# Plan 15-03: Status Page Live Feed Widget

## Objective

Build the status-page live feed widget (LIVE-02): `live-feed.js` EventSource widget, "Live Feed" article at top of StatusPage, and `live-feed.js` loaded on status page only via `headExtra`.

## What Was Built

- **`live-feed.js`** — IIFE matching app.js style. Opens `new EventSource('/api/v1/live')`, registers `addEventListener('display', ...)`, updates `#live-text` via `textEl.textContent = d.text` and `#live-meta` via `metaEl.textContent = (d.zoneId ?? '—') + ' · ' + d.effect`. No `innerHTML` anywhere (XSS mitigation). Benign `onerror = () => {}` allows browser auto-reconnect.
- **`StatusPage.kt`** — Added `import kotlinx.html.script`. Modified `render()` to pass `headExtra = { script(src = "/static/live-feed.js") {} }` to `BaseLayout.render`. Added "Live Feed" `<article>` as the FIRST block in `pageContent()` with `#live-text` and `#live-meta` spans above the existing "System" article.
- **`WebRoutesTest.kt`** — New test `GET /status renders Live Feed widget and loads live-feed.js` asserts `id="live-text"`, `id="live-meta"`, `/static/live-feed.js` present on `/status` and absent on `/`.

## Key Files

- `src/main/resources/static/live-feed.js` (new)
- `src/main/kotlin/com/anjo/web/templates/StatusPage.kt` (Live Feed article + headExtra)
- `src/test/kotlin/com/anjo/routing/WebRoutesTest.kt` (Live Feed markup assertions)

## Verification

- `grep` gate: EventSource, addEventListener('display'), no innerHTML — PASSED
- `./gradlew test --tests "com.anjo.routing.WebRoutesTest"` — BUILD SUCCESSFUL (6 tests pass)

## Self-Check: PASSED
