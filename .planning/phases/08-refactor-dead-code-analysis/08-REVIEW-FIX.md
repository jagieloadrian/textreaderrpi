---
phase: "08"
status: partial
findings_in_scope: 8
fixed: 4
skipped: 4
iteration: 1
date: "2026-06-15"
---

# Phase 08 — Code Review Fix Report

## Fixed

| ID | Severity | Description | Commit |
|----|----------|-------------|--------|
| CR-01 | Critical | TOCTOU race in `queueDisplaySwitch` — fixed with `tryLock` | 717d6d9 |
| CR-02 | Critical | Hardcoded SPI ID `"max7219"` — added `zoneId` param to `Max7219Matrix` | dadf3a0 |
| CR-03 | Critical | `String.toBoolean()` silently swallows bad values — replaced with `toBooleanStrictOrNull()` | 4ecffb4 |
| WR-01 | Warning | `System.err.println` bypasses structured log pipeline — removed from `DisplaySelectionService` | d680d53 |

## Skipped (manual review recommended)

| ID | Severity | Description | Reason |
|----|----------|-------------|--------|
| WR-02 | Warning | `queueDisplaySwitch` returns `true` for enqueued-but-not-executed switches | Requires API contract change — out of scope for Phase 8 |
| WR-03 | Warning | Unused YAML config sections with no effect | Stale config cleanup — consider in a future phase |
| WR-04 | Warning | `clear()` calls non-blocking `stop()` then sends SPI commands immediately | Concurrency hazard — requires careful refactor |
| WR-05 | Warning | `write()` calls `stop()` then `clear()` which calls `stop()` again | Fragile double-cancel — requires design consideration |

## Verification

`./gradlew test` — BUILD SUCCESSFUL, JaCoCo ≥70% gate passes after all 4 fixes.
