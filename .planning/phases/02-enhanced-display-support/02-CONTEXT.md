# Phase 2: Enhanced Display Support - Context

**Gathered:** 2026-05-26
**Status:** Ready for planning

## Phase Boundary

Phase 2 delivers multi-display support (I2C LCD and optional OLED) using the existing layering (`routing -> service -> driver`) and configuration-driven selection, while preserving compatibility with the current MAX7219 flow.

Phase 2 is explicitly expanded to include full, responsive HTML pages as the primary browser interaction flow.

This phase clarifies HOW to implement display abstraction, switching behavior, and HTML template-based UX using Ktor HTML DSL.

## Implementation Decisions

### Display Driver Contract
- **D-01:** Use a hybrid contract in Phase 2: keep `scrollText(...)` for compatibility and add command-style methods now.
- **D-02:** New mandatory methods for Phase 2 are `clear()`, `write(text)`, and `status()`.

### Display Selection and Switching
- **D-03:** Support both startup selection (via config) and optional runtime switch behavior.
- **D-04:** Runtime switch waits for the current message to finish before switching drivers.

### Rendering Behavior Across Devices
- **D-05:** Use device-optimized rendering plus a shared baseline (do not force strict visual parity).
- **D-06:** Keep Phase 1 input validation limits across all display types.
- **D-07:** Preserve responsiveness target: first visible output should occur in under 2 seconds.
- **D-08:** No silent truncation; if truncation/wrapping occurs, expose it explicitly via status/metadata.

### Failure Policy
- **D-09:** If configured hardware is unavailable at startup, fail fast.
- **D-10:** If runtime switch target fails to initialize, retry target init and then keep current display active if retry fails.

### HTML Response and Template Strategy
- **D-11:** Phase 2 now includes full responsive HTML pages as primary business responses for browser flows.
- **D-12:** Required pages: `GET /`, `GET /status`, and `GET /settings/display`.
- **D-13:** Browser-facing 400/500 responses must render HTML error pages.
- **D-14:** Use a hybrid Ktor HTML DSL approach: shared base layout template + route-specific page content.
- **D-15:** Responsive UI is implemented with a lightweight CSS framework integrated with HTML DSL output.
- **D-16:** Existing JSON API behavior can remain for compatibility where needed, but HTML is the primary user-facing flow.

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and prior decisions
- `.planning/ROADMAP.md` - Phase 2 scope, deliverables, and success criteria
- `.planning/REQUIREMENTS.md` - Functional/non-functional constraints and acceptance expectations
- `.planning/STATE.md` - Current project status and carry-forward constraints
- `.planning/phases/01-mvp/01-CONTEXT.md` - Locked Phase 1 implementation decisions that remain applicable

### Architecture and integration context
- `.planning/codebase/ARCHITECTURE.md` - Current layered architecture and plugin composition
- `.planning/codebase/STACK.md` - Runtime/framework/tooling baseline
- `.planning/codebase/INTEGRATIONS.md` - Hardware/API integration assumptions and current coupling points

### Code anchors for implementation
- `src/main/kotlin/com/anjo/driver/DisplayDriver.kt` - Existing driver abstraction to evolve
- `src/main/kotlin/com/anjo/di/DependencyInjection.kt` - Centralized wiring and runtime composition point
- `src/main/resources/application.yaml` - Configuration source for startup display selection

### External references
- `https://ktor.io/docs/server-html-dsl.html#templates` - Ktor HTML DSL template guidance for base layouts and reusable page templates

## Existing Code Insights

### Reusable Assets
- `DisplayDriver` abstraction already exists and can be extended instead of replaced.
- `Max7219Matrix` provides a reference implementation for rendering lifecycle (`scrollText` + `stop`).
- DI composition in `DependencyInjection.kt` is already centralized and suitable for driver factory/selection logic.

### Established Patterns
- Layering is stable: route handlers call services; services call driver abstractions.
- Typed config loading is already in place and should remain the source of truth for startup behavior.
- Hardware operations are already isolated from routing modules.

### Integration Points
- Driver selection and construction should integrate through `configureDI()`.
- Display-type configuration should extend existing YAML-backed config model.
- Any display status/switch endpoint work should integrate with route modules without bypassing services.

## Specific Ideas

Use Ktor HTML DSL templates to generate responsive full HTML pages, not plain text responses.

## Deferred Ideas

None - discussion stayed within phase scope.

---

*Phase: 2-Enhanced Display Support*
*Context gathered: 2026-05-26*

