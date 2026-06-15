---
phase: "08"
slug: refactor-dead-code-analysis
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-15
---

# Phase 08 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Test ↔ Application | testApplication harness calls real DI stack via HTTP; test code is the only new entry point | None — test-only; no production data path added |
| Config layer | YAML keys removed from application.yaml and test YAML; no external boundary crossed | Inert YAML removal only; no new keys ingested |

This phase adds no new network endpoints, auth paths, external integrations, or schema changes. The attack surface is unchanged or reduced (dead code removed, force-unwrap eliminated).

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-08-01 | Tampering | ApplicationTest | mitigate | assertions limited to `shouldNotBeNull {}`; acceptance grep forbids method calls on resolved instances inside new test | closed |
| T-08-02 | Info Disclosure | DI smoke test | mitigate | `CoroutineDispatcher` binding key used per A3; verified resolved successfully on first run | closed |
| T-08-03 | Info Disclosure | DI smoke test | mitigate | All 11 `provide {}` entries enumerated; acceptance criterion counts ≥11 `getBlocking` calls | closed |
| T-08-04 | Denial of Service | Coverage gate | mitigate | Test-only addition; coverage raised, not lowered; JaCoCo gate passes at 80.7% | closed |
| T-08-05 | Denial of Service | ConfigLoader / build | mitigate | Both constructor call sites (ApplicationConfig + ApiConfig) edited in same task; `./gradlew test` gates compilation | closed |
| T-08-06 | Tampering | ApplicationConfig / ApiConfig | mitigate | Acceptance grep confirms zero references to removed fields across `src/main` AND `src/test` | closed |
| T-08-07 | Info Disclosure | application.yaml | mitigate | Field-level removal; inert YAML keys removed; full suite (including ConfigLoader tests) gates regression | closed |
| T-08-08 | Denial of Service | Build (unused imports) | mitigate | Unused imports removed alongside field deletions; `./gradlew build` emits zero unused-symbol warnings | closed |
| T-08-09 | Tampering | DisplaySelectionService.selectDisplay() | mitigate | Only the single `pendingSwitches.offer()` line deleted; acceptance criterion pins exact success-branch ending; compile + test gate | closed |
| T-08-10 | Denial of Service | Coverage gate | mitigate | Retained `selectDisplay`/`currentDriver` paths still covered; final 08-05 JaCoCo gate enforces ≥70% | closed |
| T-08-11 | Tampering | DisplaySelectionService public API | mitigate | grep `src/main` confirms zero production callers of `getPendingSwitches`/`clearPendingSwitches` before removal | closed |
| T-08-12 | Tampering | DisplaySelectionServiceTest | mitigate | Non-queue assertions explicitly retained; acceptance criterion verifies three tests still present with real-behavior assertions | closed |
| T-08-13 | Denial of Service | Build (rename) | mitigate | `./gradlew clean test` forces full recompile after `git mv`; no incremental cache stale state | closed |
| T-08-14 | Tampering | ScreenDriverMetrics (metric key strings) | mitigate | `textreaderrpi.screenDriver.readInput.*` literals in ScreenDriverMetrics left unchanged; independent of method name | closed |
| T-08-15 | Tampering | Max7219Matrix / Font rendering | mitigate | Fallback width pinned to 5 bytes (matches all existing glyph entries); Max7219MatrixTest gates the wiring change | closed |
| T-08-16 | Info Disclosure | Font.getChar() coverage | mitigate | FontTest directly exercises the unmapped path; `!!` force-unwrap fully eliminated via `Font.getChar()` | closed |
| T-08-17 | Tampering | ScreenDriverService public API | mitigate | grep `src/main` confirms zero production callers of `readInput()` before deletion | closed |
| T-08-18 | Tampering | Build (symbol sweep) | mitigate | Each removal grep-confirmed zero-caller across `src/main` AND `src/test`; public API surface excluded; `./gradlew build` gates | closed |
| T-08-19 | Denial of Service | Coverage gate (cumulative) | mitigate | Final `jacocoTestCoverageVerification` enforces ≥70%; passes at 80.7% | closed |
| T-08-20 | Info Disclosure | JaCoCo exclusion | mitigate | `utils/` and `config/model/` exclusions documented; gate driven by routing/service/db/di/validation classes only | closed |
| T-08-21 | Tampering | Dead-code runtime callers | mitigate | Sweep limited to compiler-flagged unused symbols with zero static callers; behavior-bearing public methods retained | closed |
| T-08-22 | Tampering | FontTest / backfill test design | mitigate | No new abstractions introduced; tests follow existing FunSpec `should` convention | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

No accepted risks.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-15 | 22 | 22 | 0 | gsd-security-auditor (automated) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-15
