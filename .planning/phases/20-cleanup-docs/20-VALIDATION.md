---
phase: 20
slug: cleanup-docs
status: not_required_docs_only
nyquist_compliant: true
wave_0_complete: true
created: 2026-07-07
note: "No test framework required — this phase changes only documentation and planning artifacts (README.md, .planning/, docs/ deletion). All verification is grep/shell/manual-read per the <verify> blocks in 20-01 through 20-04 PLAN.md; feedback latency <1s per command."
---

# Phase 20 — Validation Strategy

This phase contains no application code changes. Verification lives entirely in the
`<automated>` (grep/shell) and `<human-check>` blocks of the four plan files:

- `20-01-PLAN.md` — README TOC/API/config/Helm: grep assertions on README.md
- `20-02-PLAN.md` — Firmware walkthrough: grep assertions incl. negative `platformio` guard
- `20-03-PLAN.md` — docs/ deletion + accuracy sweep: `test ! -d docs`, repo-wide D-04 grep
- `20-04-PLAN.md` — .planning compression: grep for must-survive markers, phase-dir checks

No Wave 0 test infrastructure, no test framework involvement.
