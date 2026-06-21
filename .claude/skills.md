# Project Coding Rules

## Comments

Do not add comments to code files. This applies to:
- Inline comments (`// ...`)
- Block comments (`/* ... */`)
- KDoc / Javadoc on methods or classes

The only exception: `catch (_: Exception)` blank catch blocks where the intent is non-obvious and there is no other way to express it — even then, prefer restructuring the code so no comment is needed.

Test files follow the same rule — no comments above test blocks, no inline explanations inside test bodies.

## Validation Layer

Validation logic belongs in `ScheduleValidators` (or equivalent validator objects) and runs via Ktor's `RequestValidation` plugin. Route handlers must not call validator methods manually, instantiate parsers, or embed any validation or parsing logic directly.
