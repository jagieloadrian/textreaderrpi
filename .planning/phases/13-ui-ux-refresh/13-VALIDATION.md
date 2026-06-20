---
phase: 13
slug: ui-ux-refresh
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-20
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest + JUnit 5 + Ktor test host |
| **Config file** | `build.gradle.kts` (tasks.test block) |
| **Quick run command** | `./gradlew test --tests "*.HistoryRepositoryTest" --tests "*.HistoryServiceTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*.HistoryRepositoryTest" --tests "*.HistoryServiceTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-??-01 | 01 | 1 | UI-01 | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | ✅ | ⬜ pending |
| 13-??-02 | 01 | 1 | UI-02 | — | N/A | manual | visual dark mode check | N/A | ⬜ pending |
| 13-??-03 | 02 | 2 | UI-03 | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | ✅ | ⬜ pending |
| 13-??-04 | 02 | 2 | UI-04 | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | ✅ | ⬜ pending |
| 13-??-05 | 02 | 2 | UI-05 | — | N/A | integration | `./gradlew test --tests "*.WebAndDisplayRoutesTest"` | ✅ | ⬜ pending |
| 13-??-06 | 02 | 2 | UI-06 | — | N/A | unit+integration | `./gradlew test --tests "*.HistoryRepositoryTest" --tests "*.HistoryServiceTest"` | ✅ | ⬜ pending |
| 13-??-07 | 03 | 3 | UI-07 | — | N/A | integration | `./gradlew test --tests "*.WebRoutesTest"` | ✅ | ⬜ pending |
| 13-??-08 | 03 | 3 | UI-08 | — | N/A | manual | status page live data check | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Key test files already exist:
- `src/test/kotlin/.../WebRoutesTest.kt` — add zone selector assertion, status page skeleton assertion
- `src/test/kotlin/.../WebAndDisplayRoutesTest.kt` — DELETE `GET /settings/display` test; ADD `GET /history?zone=X` filter assertion
- `src/test/kotlin/.../HistoryRepositoryTest.kt` — ADD zone filter test
- `src/test/kotlin/.../HistoryServiceTest.kt` — ADD zone + effect param forwarding test

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Dark mode palette switches on OS dark mode | UI-02 | CSS `prefers-color-scheme` requires browser/OS toggle | Toggle OS dark mode; verify `--md-sys-color-surface` token applied; no `data-theme` attr on `<html>` |
| Hamburger nav opens/closes on mobile | UI-01, UI-03 | Requires browser resize to mobile viewport | Resize to <960px; tap `☰`; verify overlay nav opens; tap backdrop; verify nav closes |
| Effect preview animates correctly | UI-04 | CSS animation is visual | Select each effect type; verify preview div shows correct animation class |
| Status page live-refresh every 10s | UI-08 | Requires running server + setInterval timing | Load `/status`; wait 10s; verify values update without page reload |
| Zone offline badge displayed | UI-07 | Requires zone in OFFLINE state | Disconnect a zone; verify OFFLINE badge appears on `/zones` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
