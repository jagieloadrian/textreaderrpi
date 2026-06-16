---
phase: 12
slug: observability-gap-closures
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-16
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Ktor TestApplication (existing) |
| **Config file** | `build.gradle.kts` — existing test config |
| **Quick run command** | `./gradlew test --tests "*.HealthRoutesTest" --tests "*.MetricsRoutesTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~24 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick test command
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | 01 | 1 | OBS-01 | — | N/A | unit+integration | `./gradlew test --tests "*.HealthRoutesTest"` | ✅ existing | ⬜ pending |
| TBD | 02 | 1 | OBS-02 | — | N/A | unit+integration | `./gradlew test --tests "*.MetricsRoutesTest"` | ✅ existing | ⬜ pending |
| TBD | 03 | 1 | OBS-03 | — | N/A | manual | Browser navigation to unknown path | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test framework install needed.

- [ ] Update `HealthRoutesTest` — change 404 assertion to 200 + validate JSON fields (uptime, memoryUsed, memoryMax, displayStatus, totalFailures, zoneErrors)
- [ ] Update `MetricsRoutesTest` — change `shouldHaveSize 2` to `shouldHaveSize 3` and add "hardware" to groupNames list

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser renders HTML 404 page (not JSON) for unknown path | OBS-03 | Requires real browser with `Accept: text/html` header — Ktor's `prefersHtml()` checks Accept header, not easily replicated in unit tests | Navigate to `http://<host>:<port>/does-not-exist` in browser; confirm HTML page with "Error 404" heading |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
