---
phase: "09"
slug: display-history-audit-log
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-15
---

# Phase 09 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| HTTP → HistoryRepository | GET /api/v1/history query params (page, size, effect, source) reach Exposed DSL filter | User-controlled strings; low sensitivity |
| ScreenDriverService → HistoryRepository | displayImmediate/displayScheduled writes display text to history table | Internal — text already validated at TextRoutes entry point |
| HistoryRepository → H2 DB | SQL reads/writes for display_history table | Structured rows; home network only |
| HistoryPage → Browser | Stored display text rendered in HTML cards | Display text from trusted callers; XSS risk mitigated at render |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-09-01 | Tampering (SQL injection) | HistoryRepository.findPaginated filter args (effect/source) | mitigate | Exposed DSL `where { column eq value }` uses parameterized queries — no raw SQL in HistoryRepository.kt (grep confirms) | closed |
| T-09-02 | Denial of Service | HistoryRepository.insert unbounded table growth | mitigate | `MAX_ROWS = 1000L` cap enforced inside same `suspendTransaction{}` on every insert; oldest row deleted before insert via `deleteWhere { id eq oldest }` | closed |
| T-09-03 | Tampering (XSS) | Stored display text rendered in HistoryPage HTML cards | mitigate | kotlinx.html `+text` operator auto-escapes all string content; `grep -c "unsafe" HistoryPage.kt` returns 0 — no `unsafe {}` block present | closed |
| T-09-04 | Tampering (overflow) | Pagination offset `(page-1)*size` integer overflow + negative paging | mitigate | `coerceAtLeast(1)` on page and size in both HistoryRoutes.kt and HistoryUIRoutes.kt; `.toLong()` cast in HistoryRepository.kt line 63 | closed |
| T-09-05 | Denial of Service | historyRepository.insert() exception propagating into display path | mitigate | All 4 insert calls wrapped in `try { ... } catch (e: Exception) { log.warn(...) }` — display method returns normally even when insert throws | closed |
| T-09-06 | Tampering | Raw display text stored verbatim in history table | accept | Text stored as parameterized column value (Exposed DSL); HTML-escaped only at render time in HistoryPage.kt. Storage holds raw text intentionally for audit fidelity. No PII; trusted home network. Auth out-of-scope per REQUIREMENTS. | closed |
| T-09-07 | Information Disclosure | Full display text + scheduleId exposed via unauthenticated GET /api/v1/history and GET /history | accept | No authentication in scope per REQUIREMENTS (home network, single-user). History is intentionally browsable. | closed |
| T-09-SC | Tampering (supply chain) | New package installs | accept | No new packages introduced in Phase 9. All libraries (Exposed 1.3.0, Flyway 9.22.3, H2, Kotest) are existing project dependencies verified in `gradle/ktor-libs.versions.toml`. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-09-01 | T-09-06 | Raw text stored verbatim for audit fidelity; XSS mitigated at render layer (T-09-03). No PII. Trusted home network. Auth out-of-scope. | diether18 | 2026-06-15 |
| AR-09-02 | T-09-07 | History browsable without auth by design; home-network-only deployment. Auth tracked as future requirement. | diether18 | 2026-06-15 |
| AR-09-03 | T-09-SC | No new packages added in Phase 9. All transitive deps pre-vetted in prior phases. | diether18 | 2026-06-15 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-15 | 8 | 8 | 0 | gsd-secure-phase (register_authored_at_plan_time: true — plan-time threats verified in implementation) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-15
