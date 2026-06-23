---
phase: 15
slug: sse-live-feed
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-23
---

# Phase 15 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| display coroutine → SharedFlow | In-process emit; same JVM, no external input crosses here | DisplayEvent (derived from validated HistoryRecord) |
| browser/curl → SSE endpoint | Untrusted clients connect to GET /api/v1/live; one-directional server→client push, no request body parsed | text/event-stream frames (display text, zone, effect) |
| SSE data → browser DOM | Server-pushed event payload inserted into the page | DisplayEvent JSON fields written via textContent |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-15-01 | Tampering | DisplayEvent payload constructed from persisted HistoryRecord | accept | Fields originate from validated text already accepted by POST /api/v1/text; no new untrusted input at emit point | closed |
| T-15-02 | Denial of Service | MutableSharedFlow(replay=5, extraBufferCapacity=0) | accept | Replay buffer bounded at 5; home-lab scale (D-06); no connection limit by design | closed |
| T-15-SC | Tampering | Maven dependency `io.ktor:ktor-server-sse` | mitigate | First-party Ktor artifact using `version.ref = "ktor"` (same version catalog ref as all other Ktor deps); verified: `ktor-server-sse = { module = "io.ktor:ktor-server-sse", version.ref = "ktor" }` in ktor-libs.versions.toml | closed |
| T-15-03 | Information Disclosure | GET /api/v1/live unauthenticated stream | accept | Project-wide no-auth scope (home LAN only); stream exposes same display text already returned by /api/v1/history without auth | closed |
| T-15-04 | Denial of Service | Unbounded concurrent SSE connections | accept | Home-lab scale 1-5 subscribers; SharedFlow collectors are lightweight; no connection cap by decision (D-06) | closed |
| T-15-05 | Denial of Service | SSE registered under rate-limited /api/v1 block | mitigate | liveRoutes registered in a SEPARATE `route("/api/v1")` block (Routing.kt line 64-65) outside the `installApiRateLimiting` block (lines 55-56); verified by grep | closed |
| T-15-06 | Tampering | SSE frame data field | mitigate | `data = Json.encodeToString(event)` — typed @Serializable DisplayEvent; no string concatenation, no raw client input injected into frame; verified in LiveRoutes.kt line 21 | closed |
| T-15-07 | Tampering (XSS) | live-feed.js DOM update from SSE data | mitigate | `textEl.textContent` and `metaEl.textContent` used exclusively; no `innerHTML` in live-feed.js; grep confirms absence | closed |
| T-15-08 | Information Disclosure | live-feed.js loaded on all pages | mitigate | Script tag injected via `headExtra` in StatusPage.render() only; BaseLayout.kt does not reference live-feed.js; WebRoutesTest asserts absence on `/` | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-15-01 | T-15-01 | DisplayEvent fields sourced from same validated HistoryRecord insert; no net new attack surface | project | 2026-06-23 |
| AR-15-02 | T-15-02 | Home-lab deployment with 1-5 concurrent subscribers; bounded replay buffer prevents memory growth | project | 2026-06-23 |
| AR-15-03 | T-15-03 | Home LAN deployment; no-auth is a project-wide scope decision consistent with all other endpoints | project | 2026-06-23 |
| AR-15-04 | T-15-04 | Home-lab scale; SharedFlow collectors impose negligible memory overhead; explicit decision D-06 | project | 2026-06-23 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-23 | 9 | 9 | 0 | gsd-secure-phase (Claude Sonnet 4.6) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-23
