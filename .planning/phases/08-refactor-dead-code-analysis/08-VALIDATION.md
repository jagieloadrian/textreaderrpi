---
phase: 8
slug: refactor-dead-code-analysis
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-15
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest 6.1.11 (FunSpec style) + JUnit 5 runner |
| **Config file** | `build.gradle.kts` (`useJUnitPlatform()`; JaCoCo gate via `finalizedBy` + `jacocoTestCoverageVerification`) |
| **Quick run command** | `./gradlew test` |
| **Full suite command** | `./gradlew test jacocoTestCoverageVerification` |
| **Estimated runtime** | ~8 seconds (incremental); `./gradlew clean test` (Plan 04 rename) is longer due to full recompile |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test` (or the task-scoped `--tests` form in each task's `<verify>`)
- **After every plan wave:** Run `./gradlew test jacocoTestCoverageVerification`
- **Before `/gsd-verify-work`:** Full suite (`./gradlew test jacocoTestCoverageVerification`) must be green
- **Max feedback latency:** 8 seconds (incremental quick run)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 8-01-01 | 01 | 1 | REF-03 | T-8-01 / Pitfall 1 | DI smoke test resolves all 11 bindings via `getBlocking`; no method calls on resolved instances (no Pi4J/hardware side effects) | integration (testApplication + H2) | `./gradlew test --tests "com.anjo.ApplicationTest"` | ✅ (test appended to existing ApplicationTest.kt) | ⬜ pending |
| 8-02-01 | 02 | 2 | REF-01 | T-8-02 / Pitfall 5,6 | No hidden consumer of `.hardware`/`.timing`/`.logging`/`.queueSize`; both constructor call sites edited together so build cannot ship a broken config | build + regression | `./gradlew test` | ✅ (edits existing config/loader files; deletes 3 class files) | ⬜ pending |
| 8-03-01 | 03 | 2 | REF-01 | T-8-03 / Pitfall 2 | Deleting `pendingSwitches.offer(...)` does not alter `selectDisplay()` control flow; success branch pinned by acceptance criterion | compile verification | `./gradlew compileKotlin` | ✅ (edits existing DisplaySelectionService.kt) | ⬜ pending |
| 8-03-02 | 03 | 2 | REF-04 | T-8-03 / — | Pending-switch assertions removed without weakening retained real-behavior assertions; coverage held by remaining `selectDisplay`/`currentDriver` paths | regression | `./gradlew test --tests "com.anjo.service.DisplaySelectionServiceTest" --tests "com.anjo.driver.DriverIntegrationTest"` | ✅ (edits existing test files) | ⬜ pending |
| 8-04-01 | 04 | 2 | REF-01, REF-02 | T-8-04 / Pitfall 4 | File rename + `readInput()` removal compiles after `clean` (incremental cache cleared); metric key strings untouched | regression (clean build) | `./gradlew clean test --tests "com.anjo.service.ScreenDriverRecoveryTest" --tests "com.anjo.service.ScreenDriverResourceTest"` | ✅ (git mv of existing file; edits existing test files) | ⬜ pending |
| 8-04-02 | 04 | 2 | REF-02, REF-04 | T-8-04 / Pitfall 3 (V5 Input Validation) | `Font.getChar()` returns 5-byte blank row for unmapped char (bounds guard); Max7219Matrix renders unmapped char without force-unwrap NPE | unit (TDD) | `./gradlew test --tests "com.anjo.utils.FontTest" --tests "com.anjo.driver.Max7219MatrixTest"` | ✅ W0 (FontTest.kt write-test-first via `tdd="true"` — see Wave 0 Requirements) | ⬜ pending |
| 8-05-01 | 05 | 3 | REF-01, REF-02 | T-8-05 / — | Build-warning sweep removes only grep-confirmed zero-caller symbols across src/main AND src/test; public API surface excluded | build verification | `./gradlew build` | ✅ (re-inspects Phase-8-touched files; edits only if warnings) | ⬜ pending |
| 8-05-02 | 05 | 3 | REF-04 | T-8-05 / — | JaCoCo ≥70% line-coverage gate passes on cleaned codebase; backfill tests added for any below-gate Phase-8-touched class | coverage gate | `./gradlew test jacocoTestCoverageVerification` | ✅ (gate already configured in build.gradle.kts) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/anjo/utils/FontTest.kt` — covers Font.kt bounds fix (REF-02/REF-04). **Resolved via TDD substitution, not a separate Wave 0 plan.** Plan 04 Task 2 carries `tdd="true"` with an explicit `<behavior>` block enumerating the three expected cases (mapped `'A'` → `byteArrayOf(126,17,17,17,126)`, unmapped high-unicode → `ByteArray(5) { 0 }`, space → `byteArrayOf(0,0,0,0,0)`). Under the TDD contract the executor writes `FontTest.kt` and runs it RED before implementing `Font.getChar()`, so the write-test-first intent of a Wave 0 stub is satisfied inside the task. A standalone `08-00-PLAN.md` would add a wave and a context switch for a single self-contained test file with no cross-plan dependency — the TDD-in-task path is the simpler option that honors the Nyquist write-test-first contract. The RESEARCH.md "Wave 0 Gaps" entry is closed by this substitution.

*All other phase requirements are covered by existing test infrastructure: the DI smoke test (8-01-01) extends the existing `ApplicationTest.kt`; config and DisplaySelectionService changes are verified by existing test suites; the coverage gate is already configured in `build.gradle.kts`.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | — |

*All phase behaviors have automated verification. Phase 8 is a code/config cleanup pass with no hardware-only or visual behaviors — every task has an `<automated>` `./gradlew` command.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — all 8 task rows carry a `./gradlew` command
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — every task is automated; no gaps
- [x] Wave 0 covers all MISSING references — the single FontTest.kt gap is resolved via the Plan 04 Task 2 `tdd="true"` substitution documented above
- [x] No watch-mode flags — all commands are single-shot `./gradlew` invocations
- [x] Feedback latency < 8s — incremental `./gradlew test` quick run
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-15
