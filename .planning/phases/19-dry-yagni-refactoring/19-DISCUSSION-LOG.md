# Phase 19: DRY/YAGNI Refactoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-06
**Phase:** 19-dry-yagni-refactoring
**Areas discussed:** Filter parsing home, Test helper shape, Dedup scope, Warnings + coverage

---

## Filter Parsing Home

| Option | Description | Selected |
|--------|-------------|----------|
| HistoryValidators | parseFilter(parameters): HistoryFilter — follows locked validators rule, sanitizeSearchTerm already there, model stays framework-free | ✓ |
| HistoryFilter companion | from(parameters) factory — self-contained but imports Ktor Parameters into model package | |
| You decide | Claude picks during planning | |

**User's choice:** HistoryValidators (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Share filter only | Parser returns just HistoryFilter; UI route keeps .orEmpty() reads for form redisplay | ✓ |
| Return raw + filter holder | Holder type with raw values + normalized filter, params read once | |
| You decide | Claude picks during planning | |

**User's choice:** Share filter only (Recommended)

---

## Test Helper Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Helper functions file | appTest { } wrapper + reified DI accessor; composition over inheritance, tests stay FunSpec | ✓ |
| Abstract base FunSpec | Base spec class owning bootstrap; matches SC2 wording but Kotest-unidiomatic | |
| You decide | Claude picks the shape | |

**User's choice:** Helper functions file (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Bootstrap + fixtures | Also shared fixture builders for repeated record-insertion loops | ✓ |
| Bootstrap only | Fixture loops stay per-file | |
| You decide | Per-case by the >5-line rule | |

**User's choice:** Bootstrap + fixtures (Recommended)

---

## Dedup Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Audit, fix clear wins | Fix clear >5-line wins, list borderline cases | ✓ |
| SC targets only | Only HistoryFilter parsing + test helpers | |
| Aggressive sweep | Eliminate every duplication | |

**User's choice:** Audit, fix clear wins (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Delete dead code found | Remove obviously-unused code encountered; no dedicated hunt | ✓ |
| Dedup only | No deletions | |
| Dedicated YAGNI hunt | Systematic unused-code search | |

**User's choice:** Delete dead code found (Recommended)

---

## Warnings + Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Baseline diff | Capture warning list before, assert no new entries after — plan verification step | ✓ |
| allWarningsAsErrors | Permanent build flag; breaks on pre-existing warnings | |
| You decide | Baseline diff unless build already warning-free | |

**User's choice:** Baseline diff (Recommended). JaCoCo 70% gate already enforced in build.gradle.kts — no work needed for SC3.

---

## Claude's Discretion

- Test-utils file name/location and exact helper signatures
- Which borderline duplication cases are listed rather than fixed
- Whether repository-test (H2) setup shares helpers — >5-line rule case by case

## Deferred Ideas

None — discussion stayed within phase scope.
