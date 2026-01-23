# Plan: remove-box-alignment-skill

## Goal
Remove box-alignment skill entirely; commit to pre-rendering strategy for box output.

## Satisfies
None - infrastructure/simplification task

## Rationale
- Box alignment calculation was error-prone when done manually by agents
- Pre-rendering via hooks is more reliable
- "Closed-border format" terminology was meaningless (no alternative exists)
- Removing the skill simplifies the codebase

## Files Modified
- plugin/skills/box-alignment/SKILL.md - Deleted
- plugin/.claude/cat/workflows/work.md - Removed reference
- plugin/skills/stakeholder-review/SKILL.md - Removed references
- plugin/skills/token-report/SKILL.md - Removed reference
- plugin/skills/render-diff/SKILL.md - Removed reference
- plugin/skills/tdd-implementation/SKILL.md - Removed reference
- plugin/skills/shrink-doc/SKILL.md - Removed reference, replaced ASCII with emojis
- plugin/commands/config.md - Removed references
- plugin/commands/init.md - Removed banner_formats section

## Acceptance Criteria
- [x] box-alignment skill deleted
- [x] All references to box-alignment removed (8 files)
- [x] "Closed-border format" jargon removed
- [x] ASCII status indicators replaced with emojis
