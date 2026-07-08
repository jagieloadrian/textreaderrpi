# Milestone Archives

*All detailed phase documentation (CONTEXT.md, DISCUSSION-LOG.md, PLAN.md, SUMMARY.md, etc.) for v1.0 and v1.1 has been archived. Git history preserves everything.*

## Retained per Milestone

**v1.0:**
- [`v1.0-ROADMAP.md`](v1.0-ROADMAP.md) — phase goals, deliverables, success criteria
- [`v1.0-REQUIREMENTS.md`](v1.0-REQUIREMENTS.md) — requirement checklist with traceability

**v1.1:**
- [`v1.1-ROADMAP.md`](v1.1-ROADMAP.md) — phase goals, deliverables, success criteria
- [`v1.1-REQUIREMENTS.md`](v1.1-REQUIREMENTS.md) — requirement checklist with traceability

**v1.2:**
- [`v1.2-ROADMAP.md`](v1.2-ROADMAP.md) — phase goals, deliverables, success criteria
- [`v1.2-REQUIREMENTS.md`](v1.2-REQUIREMENTS.md) — requirement checklist with traceability
- [`v1.2-MILESTONE-AUDIT.md`](v1.2-MILESTONE-AUDIT.md) — post-close audit findings

## Reconstructing Historical Details

To access detailed phase documentation (PLAN, CONTEXT, DISCUSSION-LOG, VERIFICATION, etc.) for v1.0 or v1.1, reconstruct from git history:

```bash
# List all phase commits (git history is the archive)
git log --oneline -- .planning/milestones/v1.0-phases/ .planning/milestones/v1.1-phases/

# Show specific phase PLAN
git show HEAD~N:.planning/milestones/v1.1-phases/06-max7219-hardware-fix/06-01-PLAN.md
```

Archived per Phase 20 compression principle: decisions and essentials remain in `.planning/` and milestone files; working notes and session logs move to git-only archive. See `.planning/RETROSPECTIVE.md` for forward-looking lessons from all milestones.
