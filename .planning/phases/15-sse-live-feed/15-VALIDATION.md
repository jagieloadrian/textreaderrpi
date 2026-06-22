---
phase: 15
slug: sse-live-feed
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-22
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 with `FunSpec` style |
| **Config file** | `src/test/kotlin/com/anjo/ProjectConfig.kt` (coroutineTestScope = true) |
| **Quick run command** | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest" --tests "com.anjo.service.DisplayEventBusTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.anjo.routing.LiveRoutesTest" --tests "com.anjo.service.DisplayEventBusTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | LIVE-01 | — | N/A | unit (HTTP) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ W0 | ⬜ pending |
| 15-01-02 | 01 | 1 | LIVE-01 | — | N/A | unit (stream) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ W0 | ⬜ pending |
| 15-01-03 | 01 | 1 | LIVE-01 | — | N/A | unit (bus) | `./gradlew test --tests "com.anjo.service.DisplayEventBusTest"` | ❌ W0 | ⬜ pending |
| 15-02-01 | 02 | 2 | LIVE-02 | — | textContent not innerHTML | unit (HTML) | `./gradlew test --tests "com.anjo.routing.WebRoutesTest"` | ✅ extend | ⬜ pending |
| 15-02-02 | 02 | 2 | LIVE-02 | — | N/A | unit (HTML) | `./gradlew test --tests "com.anjo.routing.WebRoutesTest"` | ✅ extend | ⬜ pending |
| 15-03-01 | 03 | 2 | LIVE-03 | — | N/A | unit (HTTP) | `./gradlew test --tests "com.anjo.routing.LiveRoutesTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/anjo/routing/LiveRoutesTest.kt` — stubs for LIVE-01, LIVE-03 (HTTP headers, Content-Type, SSE frame format, replay=5)
- [ ] `src/test/kotlin/com/anjo/service/DisplayEventBusTest.kt` — stubs for LIVE-01 bus emit/collect, replay=5 behavior

*Existing infrastructure (`WebRoutesTest.kt`, `ApplicationTest.kt`) covers LIVE-02 and DI assertions via extension.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `data:` event arrives within 1 second of display action | LIVE-01 | Real display hardware + timing dependency — not unit-testable | 1. Start server. 2. `curl --no-buffer http://localhost:8080/api/v1/live`. 3. POST to `/api/v1/text`. 4. Verify `event: display` frame appears within 1 second. |
| Status page live feed widget updates without reload | LIVE-02 | Browser UI behavior | 1. Open `/status` in browser. 2. POST to `/api/v1/text`. 3. Verify `#live-text` updates without page reload. |
| Proxy keep-alive via 30-second heartbeat | LIVE-03 | Requires 30-second wait | 1. Open SSE stream. 2. Wait 30 seconds without display activity. 3. Verify `: keep-alive` comment frame received. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
