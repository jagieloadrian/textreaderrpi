---
phase: 9
slug: display-history-audit-log
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-15
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (FunSpec + `should` convention) |
| **Config file** | none — JUnit 5 runner auto-discovers via `kotest-runner-junit5-jvm` |
| **Quick run command** | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest" -x jacocoTestReport` |
| **Full suite command** | `./gradlew test jacocoTestReport` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest" -x jacocoTestReport`
- **After every plan wave:** Run `./gradlew test jacocoTestReport`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 9-01-01 | 01 | 1 | HIST-01 | T-1 SQL injection | Exposed DSL parameterized query — no string interpolation | Unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | ❌ W0 | ⬜ pending |
| 9-01-02 | 01 | 1 | HIST-01 | — | N/A | Unit | `./gradlew test --tests "com.anjo.db.HistoryRepositoryTest"` | ❌ W0 | ⬜ pending |
| 9-02-01 | 02 | 1 | HIST-01 | T-2 SD card fill | 1000-row cap enforced inside suspendTransaction{} | Integration | `./gradlew test --tests "com.anjo.service.HistoryRecordingTest"` | ❌ W0 | ⬜ pending |
| 9-03-01 | 03 | 2 | HIST-02 | T-3 int overflow | `((page-1).toLong() * size)` offset prevents overflow | Integration | `./gradlew test --tests "com.anjo.routing.HistoryRoutesTest"` | ❌ W0 | ⬜ pending |
| 9-04-01 | 04 | 2 | HIST-03 | T-4 XSS | kotlinx.html `+text` operator auto-escapes HTML | Integration | `./gradlew test --tests "com.anjo.routing.HistoryUIRoutesTest"` | ❌ W0 | ⬜ pending |
| 9-05-01 | 05 | 3 | HIST-01 | — | N/A | Integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ exists (modify) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/db/HistoryRepositoryTest.kt` — stubs for HIST-01 (insert, findPaginated, pruning at row 1001)
- [ ] `src/test/kotlin/com/anjo/service/HistoryRecordingTest.kt` — stubs for HIST-01 (displayImmediate records; SKIP_NEW does not; DB error does not break return)
- [ ] `src/test/kotlin/com/anjo/routing/HistoryRoutesTest.kt` — stubs for HIST-02 (pagination, filters)
- [ ] `src/test/kotlin/com/anjo/routing/HistoryUIRoutesTest.kt` — stubs for HIST-03 (HTML content, expand=all)
- [ ] `src/test/kotlin/com/anjo/ApplicationTest.kt` — add `HistoryRepository` DI assertion (D-23, existing file)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `/history` page renders correctly with Pico CSS v2 styling | HIST-03 | Visual inspection of card layout, filter bar, pagination footer | Load `http://localhost:8080/history` in browser, verify card grid, collapsed/expanded state, filter controls |
| Zone filter rendered but disabled | HIST-03 | HTML attribute state | Inspect zone `<select>` element has `disabled` attribute and label "Multi-zone — Phase 11" |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
