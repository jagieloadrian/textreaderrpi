# Phase 8: Refactor + Dead Code Analysis - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-15
**Phase:** 8-refactor-dead-code-analysis
**Areas discussed:** DI smoke test scope, Dead code candidates, Cleanup depth

---

## DI Smoke Test Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Binding resolution only | Resolve each bound type in testApplication{}, assert non-null | ✓ |
| Resolution + no-arg call | Also call a lightweight method on each service | |
| You decide | Claude picks safest approach | |

**Q: What bindings to cover?**

| Option | Description | Selected |
|--------|-------------|----------|
| All bindings in configureDI() | Every provide{} block covered | ✓ |
| Core services only | SchedulerService, ScreenDriverService, ScheduleRepository only | |
| You decide | Claude enumerates and covers all | |

**Q: Database in smoke test?**

| Option | Description | Selected |
|--------|-------------|----------|
| Real H2 in-memory | testApplication{} with real H2 | ✓ |
| Mock ScheduleRepository | Zero I/O, faster | |
| You decide | Claude picks based on existing infrastructure | |

**Q: Where does the smoke test live?**

| Option | Description | Selected |
|--------|-------------|----------|
| New file: DependencyInjectionTest.kt | Dedicated class in di/ package | |
| Inside existing ApplicationTest.kt | Avoids new file, mixes with endpoint tests | ✓ |
| You decide | Claude picks location | |

---

## Dead Code Candidates

**Q: Stale config classes (HardwareConfig, TimingConfig, LoggingConfig, ApiConfig.queueSize)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Delete all four | Remove classes, ConfigLoader entries, ApplicationConfig fields | ✓ |
| Keep as placeholder stubs | Remove loader wiring but keep classes | |
| You decide | Claude judges which are safe to delete | |

**Q: logCurrentData() in DisplaySelectionService?**

| Option | Description | Selected |
|--------|-------------|----------|
| Remove it | Delete method and both call sites | ✓ |
| Keep but downgrade to TRACE | Change log level | |
| You decide | Claude decides based on production debugging value | |

**Q: getPendingSwitches() / clearPendingSwitches() / pendingSwitches queue?**

| Option | Description | Selected |
|--------|-------------|----------|
| Remove pendingSwitches + two methods | Delete field and methods, update tests | ✓ |
| Keep — it's tested behavior | Leave as-is, may be useful in Phase 9 | |
| You decide | Claude judges workaround vs. intentional | |

**Q: Scan scope (production vs. test files)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Production code only | Focus on src/main | |
| Both production and test code | Scan and clean both | ✓ |

**Q: Include method-level duplication?**

| Option | Description | Selected |
|--------|-------------|----------|
| Include method-level duplication | If readInput() duplicates displayImmediate(), consolidate | ✓ |
| Classes and fields only | Limit to whole unused classes/fields | |
| You decide | Claude decides method-level boundary | |

**Q: readInput() wrapper in ScreenDriverService?**

| Option | Description | Selected |
|--------|-------------|----------|
| Remove readInput(), update tests | Rename to displayImmediate() calls | ✓ |
| Keep as convenience alias | Test-only API | |
| You decide | Claude judges | |

**Q: Broader unused-symbol scan before removing?**

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — scan first, then remove | ./gradlew build + grep for callers | ✓ |
| No — remove known items only | Avoid scope expansion | |
| You decide | Claude decides thoroughness | |

---

## Cleanup Depth

**Q: How deep should active cleanup go?**

| Option | Description | Selected |
|--------|-------------|----------|
| Light: dead code + obvious code smells | No structural changes to classes or APIs | ✓ |
| Medium: dead code + consolidate duplicate logic | Structural within a single class | |
| Full DRY: anything that can be simplified | Aggressive, higher risk | |

**Q: Include file/class renames for consistency?**

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — rename inconsistent files/classes | ScreenDriver.kt → ScreenDriverService.kt + all imports | ✓ |
| No — names only, not renames | Skip renames to avoid import churn | |
| You decide | Claude checks naming and renames clear mismatches | |

**Q: Font.kt bounds checking — fix now or defer?**

| Option | Description | Selected |
|--------|-------------|----------|
| Defer to Phase 9+ | Standalone defensive fix, not a refactor | |
| Fix it now as part of cleanup | Small change, prevents crashes, REF-02 covers code smells | ✓ |

**Q: Test coverage after cleanup?**

| Option | Description | Selected |
|--------|-------------|----------|
| Verify gate passes + add tests for touched paths | Run JaCoCo, add Kotest tests where needed | ✓ |
| Verify gate passes only | Cleanup removes code, coverage should go up | |

**Q: Unused imports?**

| Option | Description | Selected |
|--------|-------------|----------|
| Remove unused imports as part of cleanup | Low risk, removes compiler warnings | ✓ |
| Leave to IDE | Don't spend plan time on import cleanup | |

---

## Claude's Discretion

None — all areas had explicit user selections.

## Deferred Ideas

None — discussion stayed within phase scope.
