# Phase 2: Enhanced Display Support - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `02-CONTEXT.md`; this log records alternatives considered.

**Date:** 2026-05-26
**Phase:** 2-Enhanced Display Support
**Areas discussed:** Display interface contract, driver selection and switching behavior, per-display text behavior, failure and fallback policy, phase scope expansion for responsive HTML pages

---

## Display interface contract

| Option | Description | Selected |
|--------|-------------|----------|
| A) Command-style contract | Move to `clear()`, `write(text)`, `status()` as common API; keep driver specifics internal | |
| B) Scroll-centric contract | Keep `scrollText()` as main API with minimal extras | |
| C) Hybrid contract | Keep `scrollText()` compatibility and add command-style methods now | ✓ |

**User's choice:** C) Hybrid contract
**Notes:** Mandatory methods for Phase 2 were explicitly locked as `clear()`, `write(text)`, and `status()`.

---

## Driver selection and switching behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Startup-selected only | Select by config at boot; restart required for changes | |
| Runtime switch supported | Switch display without restart | |
| Both | Startup selection plus optional runtime switching | ✓ |

**User's choice:** Both
**Notes:** For runtime switching, the selected rule is to finish the current message first, then switch.

---

## Per-display text behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Device-optimized behavior | Each display uses behavior natural to its hardware | |
| Uniform behavior | Force same behavior across all displays | |
| Device-optimized + common baseline | Keep per-device behavior but enforce cross-device baseline rules | ✓ |

**User's choice:** Device-optimized + common baseline
**Notes:** Baseline rules selected: keep Phase 1 validation limits, prioritize readability over parity, target <2s first visible output, and avoid silent truncation.

---

## Failure and fallback policy

| Option | Description | Selected |
|--------|-------------|----------|
| Fail fast | App startup fails if configured hardware is unavailable | ✓ |
| Degraded mode | App starts with controlled failures and status reporting | |
| Fallback to alternate driver | Auto-switch to backup display type at startup | |

**User's choice:** Fail fast (startup)
**Notes:** For runtime switching failure, selected behavior is retry target initialization, then keep current display active if retry fails.

---

## Scope expansion: responsive HTML pages with Ktor HTML DSL

| Option | Description | Selected |
|--------|-------------|----------|
| Keep JSON API + add some HTML pages | Compatibility-first mixed mode | |
| HTML-first business flow | Full responsive pages become primary browser interaction | |
| Full migration to HTML responses | Primary business flow through HTML pages | ✓ |

**User's choice:** Full migration to HTML responses for browser interaction.
**Notes:** Scope was intentionally expanded for Phase 2.

### Required pages

| Option | Description | Selected |
|--------|-------------|----------|
| `GET /` | Main responsive form for text submit + feedback | ✓ |
| `GET /status` | Responsive status page for active display | ✓ |
| `GET /settings/display` | Responsive runtime display switch page | ✓ |
| HTML error pages (400/500) | Browser-friendly error rendering | ✓ |

**User's choice:** All four page requirements selected.

### Responsive strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Vanilla CSS only | No CSS framework | |
| Lightweight CSS framework + HTML DSL | Fast responsive baseline | ✓ |
| Fully custom CSS system | Custom styles only | |

**User's choice:** Lightweight CSS framework + HTML DSL.

### Template strategy (Ktor)

| Option | Description | Selected |
|--------|-------------|----------|
| Per-route DSL only | Minimal abstraction | |
| Full template class hierarchy | Strong abstraction | |
| Hybrid | Shared base layout template + route-specific page DSL | ✓ |

**User's choice:** Hybrid template strategy.

**Canonical reference used:** `https://ktor.io/docs/server-html-dsl.html#templates`

---

## the agent's Discretion

None.

## Deferred Ideas

None.

