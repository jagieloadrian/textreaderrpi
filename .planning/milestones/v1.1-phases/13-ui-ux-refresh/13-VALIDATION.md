---
phase: 13
slug: ui-ux-refresh
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-20
updated: 2026-06-21
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest + JUnit 5 + Ktor test host |
| **Config file** | `build.gradle.kts` (tasks.test block) |
| **Quick run command** | `./gradlew test --tests "*.WebRoutesTest" --tests "*.HistoryUIRoutesTest" --tests "*.HistoryRepositoryTest" --tests "*.HistoryServiceTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds |
| **Coverage gate** | JaCoCo 70% line coverage (`:jacocoTestCoverageVerification`) |

---

## Sampling Rate

- **After every task commit:** Run quick command above
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|------|--------|
| 13-01 nav+css | 01 | 1 | UI-01, UI-03 | T-13-02 | activePath never user-controlled | integration | `./gradlew test --tests "*.WebRoutesTest"` | `WebRoutesTest.kt` | ✅ green |
| 13-01 settings 404 | 01 | 1 | Settings removed | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | `WebRoutesTest.kt` | ✅ green |
| 13-02 dark mode palette | 01 | 1 | UI-02 | — | N/A | manual | visual OS dark mode toggle | N/A | ✅ UAT pass |
| 13-02 history zone filter | 02 | 2 | UI-06 | T-13-03 | parameterized query, no concat | unit | `./gradlew test --tests "*.HistoryRepositoryTest" --tests "*.HistoryServiceTest"` | `HistoryRepositoryTest.kt`, `HistoryServiceTest.kt` | ✅ green |
| 13-02 zone URL routing | 02 | 2 | UI-05 | T-13-09 | encodeURIComponent + backend validation | integration | `./gradlew test --tests "*.WebAndDisplayRoutesTest"` | `WebAndDisplayRoutesTest.kt` | ✅ green |
| 13-02 effect preview + zone selector | 02 | 2 | UI-04, UI-05 | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | `WebRoutesTest.kt` | ✅ green |
| 13-02 schedule XSS + webhookUrl | 02 | 2 | UI-09 | T-13-04, T-13-05 | kotlinx.html auto-escape | integration | `./gradlew test --tests "*.ScheduleUIRoutesTest"` | `ScheduleUIRoutesTest.kt` | ✅ green |
| 13-03 status page skeleton | 03 | 3 | UI-08 | T-13-08 | Loading... placeholders rendered | integration | `./gradlew test --tests "*.WebRoutesTest"` | `WebRoutesTest.kt` | ✅ green |
| 13-03 zones discovered tint | 03 | 3 | UI-07 | — | N/A | integration | `./gradlew test --tests "*.ZonesUIRoutesTest"` | `ZonesUIRoutesTest.kt` | ✅ green |
| 13-03 JS escHtml + textContent | 03 | 3 | UI-09 | T-13-06, T-13-07 | escHtml() applied; .textContent not innerHTML | manual | visual schedule list + status page | N/A | ✅ UAT pass |
| 13-04 test coverage | 04 | 4 | — | T-13-10 | test-only files, no prod surface | integration | `./gradlew test` | all test files | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

All test files were in place and extended during phase execution:

- `WebRoutesTest.kt` — nav structure (5 links, no Settings), custom.css, zone selector, effect preview, status skeleton, Settings 404
- `WebAndDisplayRoutesTest.kt` — `/history?zone=X` filter, `/history?zone=unknown-zone` empty result
- `HistoryRepositoryTest.kt` — zone filter parameterized query, effect+zone compound filter
- `HistoryServiceTest.kt` — zone param forwarded end-to-end
- `HistoryUIRoutesTest.kt` — `/history` basic rendering, filter dropdowns, expand=all
- `ScheduleUIRoutesTest.kt` — `/schedule` rendering with zone selector and JS container
- `ZonesUIRoutesTest.kt` — zone status badge, discovered zone tint (seeded network zone)

**Bonus fix:** `SchedulerServiceTest.kt` compilation error fixed — `SchedulerService` constructor no longer accepts `EffectRendererFactory` (removed); all 14 test invocations updated to `(mockRepo, mockScreen, testScope, mockWebhook)`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | UAT Result |
|----------|-------------|------------|------------|
| Dark mode palette switches on OS dark mode | UI-02 | CSS `prefers-color-scheme` requires browser/OS toggle | ✅ UAT passed (test 3) |
| Hamburger nav opens/closes on mobile | UI-01, UI-03 | Requires browser resize to mobile viewport | ✅ UAT passed (test 2) |
| Effect preview animates correctly | UI-04 | CSS animation is visual | ✅ UAT passed (test 4) |
| Status page live-refresh every 10s | UI-08 | Requires running server + setInterval timing | ✅ UAT passed (test 7) |
| Zone offline badge displayed | UI-07 | Requires zone in OFFLINE state | ✅ UAT passed (test 8) |
| escHtml() in renderScheduleList | T-13-06 | JavaScript runtime behavior | ✅ UAT passed (test 9) |

---

## Validation Audit 2026-06-21

| Metric | Count |
|--------|-------|
| Gaps found | 1 (SchedulerServiceTest.kt compile failure — pre-existing, not phase 13) |
| Resolved | 1 |
| Escalated | 0 |
| Requirements auto-verified | 11 |
| Requirements manual-only | 6 |

---

## Validation Sign-Off

- [x] All tasks have automated verify or manual-only justification
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 test files all exist and assertions confirmed
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter
- [x] Full suite green (`BUILD SUCCESSFUL` including JaCoCo 70% gate)

**Approval:** verified 2026-06-21
