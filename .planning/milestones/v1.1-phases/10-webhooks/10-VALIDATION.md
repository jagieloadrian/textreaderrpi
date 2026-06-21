---
phase: 10
slug: 10-webhooks
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-15
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (FunSpec + `should` convention) |
| **Config file** | `build.gradle.kts` — `useJUnitPlatform()` |
| **Quick run command** | `./gradlew test --tests "com.anjo.service.WebhookServiceTest" --tests "com.anjo.service.SchedulerServiceTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.service.WebhookServiceTest" --tests "com.anjo.service.SchedulerServiceTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | HOOK-01 | — | Webhook fires only after successful display | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | ❌ Wave 0 | ⬜ pending |
| 10-01-02 | 01 | 1 | HOOK-01 | — | Webhook NOT fired on SKIP_NEW | Integration (stub) | `./gradlew test --tests "com.anjo.service.SchedulerServiceTest"` | ✅ (new test case) | ⬜ pending |
| 10-01-03 | 01 | 1 | HOOK-02 | — | POST body contains all 5 fields | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | ❌ Wave 0 | ⬜ pending |
| 10-01-04 | 01 | 1 | HOOK-02 | — | Content-Type header is application/json | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | ❌ Wave 0 | ⬜ pending |
| 10-01-05 | 01 | 1 | HOOK-03 | — | Global fallback URL used when scheduleUrl is null | Unit (MockEngine) | `./gradlew test --tests "com.anjo.service.WebhookServiceTest"` | ❌ Wave 0 | ⬜ pending |
| 10-01-06 | 01 | 1 | HOOK-03 | — | DI smoke test resolves WebhookService | Integration | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ (new assertion) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/service/WebhookServiceTest.kt` — stubs for HOOK-01, HOOK-02, HOOK-03
- [ ] New test case in `src/test/kotlin/com/anjo/service/SchedulerServiceTest.kt` — verifies `webhookService.send()` called on success, NOT called on SKIP_NEW (D-18)
- [ ] New assertion in `src/test/kotlin/com/anjo/ApplicationTest.kt` — `get<WebhookService>()` resolves without error (D-10)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live POST reaches external listener within 5s | HOOK-01 | Requires real HTTP server outside test environment | Run `nc -l 9999` on Pi; trigger schedule; verify POST received |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
