---
phase: 13
slug: ui-ux-refresh
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-21
---

# Phase 13 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → /static/custom.css | Ktor `staticResources` serves static CSS; no user input crosses | Static CSS only; no secrets |
| browser → BaseLayout-rendered HTML | `activePath` is a server-supplied route constant; page titles are constants | Read-only server constants |
| browser → /history?zone=X | User-controlled `zone` query param flows to `HistoryRepository` SQL via Exposed | Operator-supplied zone ID string |
| browser → IndexPage/SchedulePage zone selector | Zone options server-rendered from `ZoneRegistry`; submitted zone id routed to Phase-11 `/api/v1/text` | Zone ID string (validated Phase 11) |
| ZoneStatus / Schedule fields → rendered HTML | Zone ids, `webhookUrl`, text reflected into HTML via kotlinx.html DSL | Operator data; auto-escaped |
| browser → /api/v1/text?zone=X | Zone id from `#zoneSelect` appended with `encodeURIComponent`; Phase-11 route validates | URL-encoded zone ID |
| /health/detail + /metrics → app.js DOM | Server JSON values injected into `span.textContent` (not `innerHTML`) | Numeric/string status values |
| schedule JSON → renderScheduleList | `s.zoneId`/`s.webhookUrl` interpolated into table `innerHTML` via `escHtml()` | Operator schedule data; escaped |
| test host → module() | Ktor test host exercises real application; no external input | Test data only |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-13-01 | Information Disclosure | `custom.css` served at `/static` | accept | CSS contains only color tokens and layout rules; no secrets. Identical exposure as `app.js` in same static dir. | closed |
| T-13-02 | Tampering | `BaseLayout` `activePath` reflected into `aria-current` | accept | `activePath` is a hardcoded route constant per call site, never user-controlled; no request input reflected. | closed |
| T-13-03 | Tampering / Injection | `HistoryRepository` zone filter SQL | mitigate | Exposed parameterized `andWhere { HistoryTable.zoneId eq zone }` — bound parameter, not string concat. Identical pattern to existing effect/source filters. | closed |
| T-13-04 | Cross-Site Scripting | `webhookUrl` / `zoneId` / schedule text in kotlinx.html | mitigate | kotlinx.html DSL auto-escapes all text nodes (`+s.webhookUrl`); no `unsafe {}` blocks added; `.take()` truncation does not bypass escaping. | closed |
| T-13-05 | Information Disclosure | `webhookUrl` shown truncated in Schedule table | accept | `webhookUrl` is operator-configured on a trusted home network; truncated to 40 chars. Acceptable per project trust model (no auth in scope per REQUIREMENTS). | closed |
| T-13-06 | Cross-Site Scripting | `renderScheduleList` `innerHTML` with `zoneId`/`webhookUrl` | mitigate | `s.zoneId` and `s.webhookUrl` cells wrapped via existing `escHtml()` helper — identical to every other interpolated cell; no raw string interpolation. | closed |
| T-13-07 | Cross-Site Scripting | Status spans from `/health/detail` and `/metrics` | mitigate | Values assigned via `.textContent` (not `innerHTML`); markup injection impossible even if a field contained HTML. | closed |
| T-13-08 | Information Disclosure | `/status` auto-polling exposes uptime/memory | accept | Endpoints already public on the trusted home network (Phase 12); `/status` only re-displays them. No new exposure surface. | closed |
| T-13-09 | Tampering | Zone id appended to `/api/v1/text` URL | mitigate | `encodeURIComponent` applied at send time; backend Phase-11 route validates zone existence (404/503); request body unchanged (`{text, effect}` only). | closed |
| T-13-10 | (test-only) | New test files (`*Test.kt`) | accept | Test code only; no production attack surface added. Existing H2 in-memory test DB pattern reused. | closed |
| T-13-SC | Tampering | npm/pip/cargo supply chain (all plans) | mitigate | Zero new dependencies introduced across all 4 plans — verified: CSS edits, Kotlin/kotlinx.html edits, vanilla JS edits, and Kotest additions all use already-declared classpath deps. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-13-01 | T-13-01 | CSS at `/static` is intentionally public; contains only design tokens, no sensitive data | gsd-secure-phase | 2026-06-21 |
| AR-13-02 | T-13-02 | `activePath` is a server-side constant per route; no user input is ever reflected | gsd-secure-phase | 2026-06-21 |
| AR-13-05 | T-13-05 | `webhookUrl` visible to trusted home network operators only; no auth in project scope (REQUIREMENTS) | gsd-secure-phase | 2026-06-21 |
| AR-13-08 | T-13-08 | `/status` re-exposes Phase-12 endpoints already public on the home network; no new surface | gsd-secure-phase | 2026-06-21 |
| AR-13-10 | T-13-10 | Test-only files; zero production attack surface | gsd-secure-phase | 2026-06-21 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-21 | 11 | 11 | 0 | gsd-secure-phase (short-circuit: register_authored_at_plan_time=true, all mitigations confirmed in SUMMARY threat flags) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-21
