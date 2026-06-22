---
phase: 14
slug: history-enhancements
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-22
audited: 2026-06-22
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (`FunSpec` style) + JUnit 5 platform |
| **Config file** | none — configured via `tasks.test { useJUnitPlatform() }` in `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "com.anjo.*History*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds (quick) / ~90 seconds (full) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.*History*"`
- **After every plan wave:** Run `./gradlew test` (full suite, JaCoCo ≥70% gate)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| sanitize-01 | dependency+model | 1 | HIST-04 | T-14-01 | Strips `%` and `_` before LIKE query | unit | `./gradlew test --tests "com.anjo.validation.HistoryValidatorsTest"` | ✅ | ✅ green |
| sanitize-02 | dependency+model | 1 | HIST-04 | T-14-01 | Blank-after-sanitize treated as null | unit | `./gradlew test --tests "com.anjo.validation.HistoryValidatorsTest"` | ✅ | ✅ green |
| highlight-01 | html-utils | 1 | HIST-06 | — | `<mark>` wraps all occurrences | unit | `./gradlew test --tests "com.anjo.web.templates.HtmlUtilsTest"` | ✅ | ✅ green |
| highlight-02 | html-utils | 1 | HIST-06 | — | No `<mark>` when term is null | unit | `./gradlew test --tests "com.anjo.web.templates.HtmlUtilsTest"` | ✅ | ✅ green |
| repo-01 | repository | 1 | HIST-04 | — | Case-insensitive LIKE search returns matching records | unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | ✅ | ✅ green |
| repo-02 | repository | 1 | HIST-05 | — | `findAll(filter)` returns all matching rows without pagination | unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | ✅ | ✅ green |
| service-01 | service | 1 | HIST-05 | — | `exportCsv()` produces RFC 4180 CSV with correct header row | unit | `./gradlew test --tests "com.anjo.service.HistoryServiceTest"` | ✅ | ✅ green |
| service-02 | service | 1 | HIST-05 | — | CSV escapes fields containing commas | unit | `./gradlew test --tests "com.anjo.service.HistoryServiceTest"` | ✅ | ✅ green |
| api-01 | routes | 2 | HIST-04 | T-14-01 | `GET /api/v1/history?search=foo` returns only matching entries | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ | ✅ green |
| api-02 | routes | 2 | HIST-05 | — | `GET /api/v1/history/export` returns CSV with Content-Disposition header | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ | ✅ green |
| api-03 | routes | 2 | HIST-05 | — | Export CSV respects filter params | integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ✅ | ✅ green |
| ui-01 | ui-routes | 2 | HIST-04 | — | `GET /history?search=foo` HTML is filtered and search term preserved | integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | ✅ | ✅ green |
| ui-02 | ui-routes | 2 | HIST-06 | — | HTML page contains `<mark>` tags when `?search=` is set | integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/validation/HistoryValidatorsTest.kt` — stubs for HIST-04 sanitizer (strip `%`/`_`, blank→null)
- [ ] `src/test/kotlin/com/anjo/web/templates/HtmlUtilsTest.kt` — stubs for HIST-06 `highlightText()` (all occurrences, null no-op, case-insensitive, HTML-safe)

*(Existing test files for HistoryRepository, HistoryService, HistoryRoutes, HistoryUIRoutes already exist — they receive new test cases, no new file creation needed.)*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser downloads `.csv` file on "Export CSV" click | HIST-05 | Browser file download behavior cannot be tested via Ktor testApplication | Click "Export CSV" on `/history` page; verify file downloads with name `history.csv` and correct content |
| Highlighted `<mark>` text visually correct in browser | HIST-06 | Visual rendering cannot be asserted via HTML string check | Load `/history?search=hello` with records containing "hello"; verify yellow-highlighted spans appear around matched text |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** ✅ 2026-06-22 — all 13 tasks green, Wave 0 files created during execution

## Validation Audit 2026-06-22
| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 13 |
| Escalated | 0 |
