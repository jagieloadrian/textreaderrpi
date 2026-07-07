# Phase 20: Cleanup + Docs - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-07
**Phase:** 20-cleanup-docs
**Areas discussed:** docs/ salvage policy, Planning compression shape, Firmware quick-start depth, README structure

---

## docs/ Salvage Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Salvage gaps, then delete | Skim each docs/ file; migrate only content filling a required README gap; git history is the archive | ✓ |
| Straight delete | Delete without review; README rewritten from code | |
| Archive then delete | Preserve a summary outside git history first | |

**User's choice:** Salvage gaps, then delete

| Option | Description | Selected |
|--------|-------------|----------|
| Verify against code | Every salvaged claim checked against current code/config; stale content dropped | ✓ |
| Copy then fix in review | Migrate wholesale, rely on SC4 sweep | |
| You decide | | |

**User's choice:** Verify against code

| Option | Description | Selected |
|--------|-------------|----------|
| Only if it earns its place | Salvage dev/ops content only where short, current, useful | ✓ |
| Yes, add dev + ops sections | README becomes the single doc with dedicated sections | |
| No, required sections only | Dev/ops content deleted with docs/ | |

**User's choice:** Only if it earns its place

| Option | Description | Selected |
|--------|-------------|----------|
| Grep and fix all | Repo-wide search for docs/ references; fix every one | ✓ |
| Fix README only | Only README links matter for SC4 | |
| You decide | | |

**User's choice:** Grep and fix all

---

## Planning Compression Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Out of scope — SC2 only | Compress only STATE.md/MILESTONES.md; phase dirs handled at milestone close | |
| Archive phase dirs too | Move completed phase dirs to an archive | |
| Delete phase dirs | Remove completed phase dirs; git history is the archive | ✓ |

**User's choice:** Delete phase dirs — deliberate expansion beyond SC2's literal wording

| Option | Description | Selected |
|--------|-------------|----------|
| Delete 14–19 now, keep 20 | 20's dir survives until milestone archive | |
| Delete all incl. 20 at phase end | Every phase dir goes, including 20's own | ✓ |
| Keep VERIFICATION/UAT files | Delete plans/research but keep audit trail files | |

**User's choice:** Delete all incl. 20 at phase end

| Option | Description | Selected |
|--------|-------------|----------|
| Post-verification step | Plans delete 14–19; 20 verifies normally; documented final step deletes 20's dir after verification passes | ✓ |
| Defer 20's dir to milestone close | /gsd-complete-milestone deletes it later | |
| Accept broken verification | Delete in last plan task; skip formal verification | |

**User's choice:** Post-verification step
**Notes:** Chosen to resolve the sequencing conflict — the verifier needs 20's PLAN/SUMMARY files and writes 20-VERIFICATION.md into that directory.

| Option | Description | Selected |
|--------|-------------|----------|
| Rewrite to essentials | STATE.md: position + decisions + conventions; MILESTONES.md: one compact block per milestone | ✓ |
| Trim in place | Keep structure, delete only noisy lines | |
| You decide | | |

**User's choice:** Rewrite to essentials

---

## Firmware Quick-Start Depth

| Option | Description | Selected |
|--------|-------------|----------|
| Commands-only | Prerequisites as one-liners + exact build/flash commands (~20 lines/platform) | |
| Full walkthrough | Toolchain install, wiring notes, troubleshooting — complete beginner path | ✓ |
| Point to firmware/ READMEs | Short README section linking to per-platform docs | |

**User's choice:** Full walkthrough

| Option | Description | Selected |
|--------|-------------|----------|
| Linux only | Incl. Raspberry Pi OS; other OSes get a pointer to official docs | ✓ |
| Linux + Windows | | |
| Linux + macOS + Windows | | |

**User's choice:** Linux only

| Option | Description | Selected |
|--------|-------------|----------|
| Config yes, basic troubleshooting | Every user-editable config value documented + short troubleshooting list | ✓ |
| Config yes, no troubleshooting | | |
| Flash steps only | Config left to comments in config files | |

**User's choice:** Config yes, basic troubleshooting

---

## README Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Single README, strong TOC | One file stays the source of truth with a table of contents | ✓ |
| README + FIRMWARE.md | Firmware walkthrough split into its own doc | |
| You decide | | |

**User's choice:** Single README, strong TOC

| Option | Description | Selected |
|--------|-------------|----------|
| Extend existing tables + curl examples | Follow established README pattern per endpoint group | ✓ |
| Tables only | No usage examples | |
| Dedicated v1.2 section | "New in v1.2" section instead of merging | |

**User's choice:** Extend existing tables + curl examples

| Option | Description | Selected |
|--------|-------------|----------|
| Verify against routes + config | Endpoint list generated from code; deferred v1.3 items explicitly grepped | ✓ |
| Manual read-through | One top-to-bottom pass, no systematic cross-check | |
| You decide | | |

**User's choice:** Verify against routes + config

---

## Claude's Discretion

- Exact README section ordering and TOC format
- Which docs/ fragments qualify as "earning their place"
- Compressed STATE.md/MILESTONES.md exact layout

## Deferred Ideas

None — discussion stayed within phase scope.
